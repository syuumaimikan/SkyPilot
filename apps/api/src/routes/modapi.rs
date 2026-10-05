//! Endpoints consumed by the Fabric mod (bearer token from the device flow). Read-only,
//! plus session statistics upload. Responses are small and cached on the mod side.

use std::sync::Arc;

use axum::Json;
use axum::extract::{Path, Query, State};
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use serde::Deserialize;
use serde_json::{Value, json};
use skypilot_items::from_client_json;
use skypilot_pricing::{PriceOracle, PriceSide};
use skypilot_protocol::mod_api::*;
use skypilot_reco::hud;

use crate::auth;
use crate::error::{ApiResult, AppError};
use crate::state::AppState;

#[derive(Deserialize)]
pub struct HudQuery {
    pub area: Option<String>,
    pub lang: Option<String>,
}

fn fmt(v: f64) -> String {
    skypilot_reco::ctx::fmt_coins(v)
}

pub async fn hud_state(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<HudQuery>) -> ApiResult<Json<HudState>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let last = state.store.latest_analysis(user.id).await?;
    let area = q.area.as_deref().map(|a| a.chars().take(64).collect::<String>());
    Ok(Json(hud::build(last.as_ref().map(|l| &l.0), area.as_deref(), lang, AppState::now_ms(), user.minecraft_name.clone())))
}

pub async fn price(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<ModPriceRequest>) -> ApiResult<Json<ModPriceResponse>> {
    auth::any_user(&state, &jar, &headers).await?;
    if req.skyblock_id.len() > 64 || !req.skyblock_id.chars().all(|c| c.is_ascii_alphanumeric() || "_:;".contains(c)) {
        return Err(AppError::bad_request("invalid skyblock id"));
    }
    let registry = state.registry();
    // Fingerprint the selected stack when the mod sent its custom_data (26.2 data component).
    let fp = req
        .custom_data
        .as_ref()
        .and_then(|cd| from_client_json(&json!({"custom_data": cd, "count": 1})))
        .and_then(|item| skypilot_pricing::normalize::fingerprint_item(&item, &registry));
    let market_id = fp.as_ref().map(|f| f.market_id.clone()).unwrap_or_else(|| req.skyblock_id.clone());
    let key = fp.as_ref().map(|f| f.comparable_key()).unwrap_or_else(|| market_id.clone());
    let fair = state.market.fair_value(&key).filter(|f| f.value.is_some()).or_else(|| state.market.fair_value(&market_id));
    let bz = state.market.state.read().bazaar.get(&market_id).cloned();
    let estimate = fp.as_ref().and_then(|f| skypilot_pricing::networth::value_item(f, &registry, state.market.as_ref())).map(|v| v.value);
    let (ah, _, bzf) = state.market.freshness();
    let conf = fair.as_ref().map(|f| f.confidence.clone());
    Ok(Json(ModPriceResponse {
        name: registry.display_name(&market_id),
        skyblock_id: market_id.clone(),
        estimated_value: estimate.or_else(|| fair.as_ref().and_then(|f| f.value)).or_else(|| state.market.quote(&market_id, PriceSide::Sell).map(|q| q.price)),
        median: fair.as_ref().and_then(|f| f.sales_median),
        lowest_bin: fair.as_ref().and_then(|f| f.lowest_bin),
        bazaar_buy: bz.as_ref().and_then(skypilot_pricing::bazaar::instant_buy),
        bazaar_sell: bz.as_ref().and_then(skypilot_pricing::bazaar::instant_sell),
        sales_per_day: fair.as_ref().map(|f| f.sales_per_day),
        confidence: conf.as_ref().map(|c| c.score).or(bz.as_ref().map(|_| 0.95)),
        confidence_label: conf.map(|c| format!("{:?}", c.level).to_uppercase()).unwrap_or_else(|| if bz.is_some() { "HIGH".into() } else { "UNKNOWN".into() }),
        price_key: Some(key),
        age_seconds: Some(if bz.is_some() { bzf.age_seconds } else { ah.age_seconds }),
    }))
}

/// Text panels for `/skypilot <goal|next|roadmap|market|mining|slayer|farming>`.
pub async fn text(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Path(kind): Path<String>, Query(q): Query<HudQuery>) -> ApiResult<Json<ModTextResponse>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let line = |l: &str, v: String| HudLine { label: l.to_string(), value: v };
    if kind == "market" {
        let list = state.market.flips(&skypilot_protocol::market::FlipQuery { max_budget: None, min_profit: None, min_roi: None, min_confidence: Some(0.6), max_risk: Some(skypilot_protocol::common::RiskLevel::Medium), search: None, limit: Some(5) }, true);
        let mut lines: Vec<HudLine> = list.flips.iter().map(|f| line(&f.item_name, format!("{} → {} (+{}, {:.0}%)", fmt(f.buy_price), fmt(f.recommended_sell), fmt(f.expected_net_profit), f.roi * 100.0))).collect();
        if lines.is_empty() {
            lines.push(line(&lang.t("Flips", "フリップ"), lang.t("No confident opportunities right now", "現在、信頼度の高い機会はありません")));
        }
        lines.push(line(&lang.t("Updated", "更新"), format!("{}s ago", list.freshness.age_seconds)));
        return Ok(Json(ModTextResponse { title: lang.t("Live flips (view only)", "ライブフリップ（表示のみ）"), lines }));
    }
    let Some((resp, _)) = state.store.latest_analysis(user.id).await? else {
        return Ok(Json(ModTextResponse { title: "SkyPilot".into(), lines: vec![line("", lang.t("Run an analysis on the web first.", "先にWebで分析してください。"))] }));
    };
    let goal = resp.goals.first();
    let out = match kind.as_str() {
        "goal" => ModTextResponse {
            title: goal.map(|g| g.spec.title.clone()).unwrap_or_else(|| "Goal".into()),
            lines: goal.map(|g| vec![line(&lang.t("Progress", "進捗"), format!("{:.0}%", g.roadmap.overall_progress * 100.0)), line(&lang.t("Remaining cost", "残り費用"), g.roadmap.remaining_cost.map(fmt).unwrap_or_else(|| "?".into()))]).unwrap_or_default(),
        },
        "next" => ModTextResponse { title: lang.t("What should I do now?", "今何をすべき？"), lines: resp.actions.iter().take(5).enumerate().map(|(i, a)| line(&format!("{}", i + 1), a.title.clone())).collect() },
        "roadmap" => ModTextResponse {
            title: goal.map(|g| g.spec.title.clone()).unwrap_or_else(|| "Roadmap".into()),
            lines: goal
                .map(|g| g.roadmap.steps.iter().map(|s| line(&format!("{:02}", s.index), format!("{}{}", if s.status == skypilot_protocol::goals::NodeStatus::Satisfied { "✔ " } else { "" }, s.title))).collect())
                .unwrap_or_default(),
        },
        "mining" | "slayer" | "farming" => {
            let area = match kind.as_str() {
                "mining" => "Dwarven Mines",
                "slayer" => "The End",
                _ => "Garden",
            };
            let h = hud::build(Some(&resp), Some(area), lang, AppState::now_ms(), None);
            match h.context {
                Some(c) => ModTextResponse { title: c.title, lines: c.lines },
                None => ModTextResponse { title: kind.clone(), lines: vec![line("", lang.t("Not available from API", "APIから取得できません"))] },
            }
        }
        _ => return Err(AppError::not_found("unknown panel")),
    };
    Ok(Json(out))
}

/// Session statistics computed client-side from visible game info (scoreboard, chat, action bar).
pub async fn session(State(state): State<Arc<AppState>>, headers: HeaderMap, Json(v): Json<Value>) -> ApiResult<Json<Value>> {
    let (token, user) = auth::bearer(&state, &headers).await?.ok_or_else(AppError::unauthorized)?;
    let started = v.get("startedAtMs").and_then(Value::as_i64).unwrap_or(0);
    let ended = v.get("endedAtMs").and_then(Value::as_i64).unwrap_or(AppState::now_ms());
    if serde_json::to_vec(&v).map(|b| b.len()).unwrap_or(usize::MAX) > 32 * 1024 || ended < started {
        return Err(AppError::bad_request("invalid session stats"));
    }
    state.store.save_session_stats(user.id, &token.id, started, ended, &v).await?;
    Ok(Json(json!({"ok": true})))
}
