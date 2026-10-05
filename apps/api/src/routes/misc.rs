//! Health/status, assistant, translation and notification endpoints.

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use axum::Json;
use axum::extract::{ConnectInfo, Path, State};
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use serde::Deserialize;
use serde_json::{Value, json};
use skypilot_protocol::market::NotificationRule;
use skypilot_protocol::translation::{TranslateRequest, TranslateResponse, TranslationDictionary, TranslationStatus};
use skypilot_reco::{Lang, assistant};

use crate::auth;
use crate::store::PushSubscription;
use crate::error::{ApiResult, AppError};
use crate::state::AppState;

pub async fn health() -> Json<Value> {
    Json(json!({"ok": true, "service": "skypilot-api", "version": env!("CARGO_PKG_VERSION")}))
}

pub async fn status(State(state): State<Arc<AppState>>) -> Json<Value> {
    let reg = state.registry();
    let (remaining, limit) = state.hypixel.key_budget();
    let (ah, ended, bz) = state.market.freshness();
    Json(json!({
        "hypixel": {
            "keyConfigured": state.hypixel.has_key(),
            "circuit": state.hypixel.breaker_state().as_str(),
            "rateLimitRemaining": remaining,
            "rateLimitLimit": limit,
            "metrics": state.hypixel.metrics().snapshot().into_iter().collect::<std::collections::BTreeMap<_, _>>(),
        },
        "market": {"auctions": ah, "endedAuctions": ended, "bazaar": bz},
        "registry": {
            "items": reg.items.len(),
            "neuFiles": reg.meta.neu_files,
            "skyblockVersion": reg.meta.skyblock_version,
            "itemsLastUpdated": reg.meta.items_last_updated,
            "loadedAtMs": reg.meta.loaded_at_ms,
        },
        "translation": state.translator.status(),
        "llm": {"enabled": state.llm.is_some(), "model": state.llm.as_ref().map(|c| c.model.clone())},
        "store": state.store.kind(),
        "eventBus": state.bus.kind(),
        "webPush": state.push.is_some(),
        "collector": format!("{:?}", state.config.collector),
    }))
}

#[derive(Deserialize)]
pub struct ChatRequest {
    pub message: String,
    pub lang: Option<String>,
}

/// Grounded assistant: deterministic answer from structured data; the LLM (optional) only
/// rephrases those facts and is instructed to say "確認できません" when they are insufficient.
pub async fn chat(State(state): State<Arc<AppState>>, ConnectInfo(peer): ConnectInfo<SocketAddr>, jar: CookieJar, headers: HeaderMap, Json(req): Json<ChatRequest>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    if state.limiters.general.check_key(&auth::client_ip(&state, &headers, peer)).is_err() {
        return Err(AppError::too_many());
    }
    let msg: String = req.message.chars().take(500).collect();
    if msg.trim().is_empty() {
        return Err(AppError::bad_request("empty message"));
    }
    let lang = AppState::lang_of(&s.user, req.lang.as_deref());
    let last = state.store.latest_analysis(s.user.id).await?;
    let registry = state.registry();
    let market = state.market.clone();
    let price_lookup = |q: &str| -> Option<String> {
        let item = registry.search_items(q, 1).into_iter().next()?;
        let d = market.price_detail(&item.id, "24H", None);
        let v = d.bazaar.as_ref().and_then(|b| b.instant_buy).or(d.fair_value.as_ref().and_then(|f| f.value))?;
        let age = d.freshness.age_seconds;
        Some(lang.t(
            format!("{}: about {} (updated {}s ago, source {}).", item.name, skypilot_reco::ctx::fmt_coins(v), age, if d.bazaar.is_some() { "Bazaar instant-buy" } else { "AH fair value" }),
            format!("{}: 約{}（{}秒前更新、{}）。", item.name, skypilot_reco::ctx::fmt_coins(v), age, if d.bazaar.is_some() { "Bazaar即時購入" } else { "AH適正価格" }),
        ))
    };
    let req_lookup = |q: &str| -> Option<String> {
        let item = registry.search_items(q, 1).into_iter().next()?;
        let reqs: Vec<String> = item.requirements.iter().map(|r| r.describe()).collect();
        let dungeon: Vec<String> = item.catacombs_requirements.iter().map(|r| r.describe()).collect();
        Some(lang.t(
            format!("{} requirements: {}{}", item.name, if reqs.is_empty() { "none".into() } else { reqs.join(", ") }, if dungeon.is_empty() { String::new() } else { format!("; in dungeons: {}", dungeon.join(", ")) }),
            format!("{}の条件: {}{}", item.name, if reqs.is_empty() { "なし".into() } else { reqs.join("、") }, if dungeon.is_empty() { String::new() } else { format!("（ダンジョン内: {}）", dungeon.join("、")) }),
        ))
    };
    let ans = tokio::task::block_in_place(|| assistant::answer(&msg, last.as_ref().map(|l| &l.0), lang, &price_lookup, &req_lookup));
    let mut text = ans.text.clone();
    let mut llm_used = false;
    if let (Some(llm), true) = (&state.llm, ans.grounded) {
        let system = format!(
            "You are SkyPilot, a Hypixel SkyBlock progression assistant. Rewrite the DRAFT answer to the user's question as a short, friendly reply in {}. Use ONLY the facts in DRAFT and FACTS: do not add or change any number, price, requirement, recipe, drop rate or XP value, and do not add advice that is not in them. If they do not answer the question, reply exactly with \"{}\".",
            if lang == Lang::Ja { "Japanese" } else { "English" },
            lang.t("I can't confirm that from the available data.", "確認できません。")
        );
        let user_msg = format!("QUESTION: {msg}\n\nDRAFT: {}\n\nFACTS:\n{}", ans.text, ans.facts.join("\n"));
        match llm.complete(&system, &user_msg, 2048, "low", Duration::from_secs(30)).await {
            Ok(t) => {
                text = t.trim().to_string();
                llm_used = true;
            }
            Err(e) => tracing::warn!(error = %e, "assistant LLM unavailable; using deterministic answer"),
        }
    }
    Ok(Json(json!({"answer": text, "deterministicAnswer": ans.text, "intent": ans.intent, "grounded": ans.grounded, "facts": ans.facts, "llmUsed": llm_used})))
}

pub async fn translate(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<TranslateRequest>) -> ApiResult<Json<TranslateResponse>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    if state.limiters.translate.check_key(&user.id.to_string()).is_err() {
        return Err(AppError::too_many());
    }
    if req.text.chars().count() > 600 {
        return Err(AppError::bad_request("text too long"));
    }
    Ok(Json(state.translator.translate(&req).await))
}

pub async fn dictionary(State(state): State<Arc<AppState>>) -> Json<TranslationDictionary> {
    Json(state.translator.dictionary().to_protocol())
}

pub async fn translation_status(State(state): State<Arc<AppState>>) -> Json<TranslationStatus> {
    Json(state.translator.status())
}

pub async fn rules(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<Vec<NotificationRule>>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    Ok(Json(state.store.notification_rules(user.id).await?))
}

pub async fn save_rule(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(mut rule): Json<NotificationRule>) -> ApiResult<Json<NotificationRule>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    if rule.name.chars().count() > 64 {
        return Err(AppError::bad_request("name too long"));
    }
    if rule.id.is_empty() {
        rule.id = auth::random_token(6);
    }
    if state.store.notification_rules(s.user.id).await?.len() >= 20 && !state.store.notification_rules(s.user.id).await?.iter().any(|r| r.id == rule.id) {
        return Err(AppError::bad_request("at most 20 rules"));
    }
    state.store.save_notification_rule(s.user.id, &rule).await?;
    Ok(Json(rule))
}

pub async fn delete_rule(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Path(id): Path<String>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    state.store.delete_notification_rule(s.user.id, &id).await?;
    Ok(Json(json!({"ok": true})))
}

#[derive(Deserialize)]
pub struct PushKeys {
    pub p256dh: String,
    pub auth: String,
}

#[derive(Deserialize)]
pub struct PushSubscribeRequest {
    pub endpoint: String,
    pub keys: PushKeys,
}

#[derive(Deserialize)]
pub struct PushUnsubscribeRequest {
    pub endpoint: String,
}

pub async fn push_key(State(state): State<Arc<AppState>>) -> Json<Value> {
    Json(json!({"enabled": state.push.is_some(), "publicKey": state.push.as_ref().map(|p| p.public_key().to_string())}))
}

pub async fn push_subscribe(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<PushSubscribeRequest>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    if state.push.is_none() {
        return Err(AppError::conflict("PUSH_DISABLED", "Web Push is not enabled on this server."));
    }
    let sub = PushSubscription { endpoint: req.endpoint, p256dh: req.keys.p256dh, auth: req.keys.auth };
    crate::push::validate_subscription(&sub).map_err(AppError::bad_request)?;
    if state.store.push_subscriptions(s.user.id).await?.len() >= 10 && !state.store.push_subscriptions(s.user.id).await?.iter().any(|x| x.endpoint == sub.endpoint) {
        return Err(AppError::bad_request("at most 10 push subscriptions"));
    }
    state.store.save_push_subscription(s.user.id, &sub).await?;
    Ok(Json(json!({"ok": true})))
}

pub async fn push_unsubscribe(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<PushUnsubscribeRequest>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    if state.store.push_subscriptions(s.user.id).await?.iter().any(|x| x.endpoint == req.endpoint) {
        state.store.delete_push_subscription(&req.endpoint).await?;
    }
    Ok(Json(json!({"ok": true})))
}

/// Sends a test notification to the caller's own subscriptions.
pub async fn push_test(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    let push = state.push.as_ref().ok_or_else(|| AppError::conflict("PUSH_DISABLED", "Web Push is not enabled on this server."))?;
    let lang = AppState::lang_of(&s.user, None);
    let body = json!({"title": "SkyPilot", "body": lang.t("Test notification - flip alerts will look like this.", "テスト通知です。フリップ通知はこのように表示されます。"), "url": "/settings", "tag": "skypilot-test"});
    let mut delivered = 0;
    for sub in state.store.push_subscriptions(s.user.id).await? {
        match push.send(&sub, &body).await {
            Ok(true) => delivered += 1,
            Ok(false) => state.store.delete_push_subscription(&sub.endpoint).await?,
            Err(e) => tracing::debug!(error = %e, "test push failed"),
        }
    }
    Ok(Json(json!({"delivered": delivered})))
}
