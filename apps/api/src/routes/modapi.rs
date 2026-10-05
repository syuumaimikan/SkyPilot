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

// ---------------------------------------------------------------- in-game GUI

fn enum_name<T: serde::Serialize>(v: &T) -> String {
    serde_json::to_value(v).ok().and_then(|x| x.as_str().map(str::to_string)).unwrap_or_default()
}

fn icon_of(registry: &skypilot_data::Registry, id: &str) -> ItemIcon {
    let i = registry.icon(id);
    ItemIcon { item: i.item, texture: i.texture, color: i.color, glint: i.glint }
}

fn minutes(r: &skypilot_protocol::common::Range) -> String {
    format!("{}–{}", skypilot_reco::ctx::fmt_hours(r.low / 60.0), skypilot_reco::ctx::fmt_hours(r.high / 60.0))
}

fn confidence_label(c: &skypilot_protocol::common::Confidence) -> String {
    format!("{:?}", c.level).to_uppercase()
}

fn ah_row(registry: &skypilot_data::Registry, f: &skypilot_protocol::market::FlipOpportunity) -> ModFlipRow {
    ModFlipRow {
        id: f.auction_id.clone(),
        kind: "AH".into(),
        item_id: f.item_id.clone(),
        name: f.item_name.clone(),
        rarity: f.rarity.clone(),
        modifiers: f.modifiers.clone(),
        buy: f.buy_price,
        sell: f.recommended_sell,
        profit: f.expected_net_profit,
        roi: f.roi,
        profit_per_hour: None,
        amount: None,
        confidence: f.confidence.score,
        confidence_label: confidence_label(&f.confidence),
        risk: enum_name(&f.risk),
        sell_time: Some(minutes(&f.expected_sell_minutes)),
        do_not_flip: f.manipulation.suspicious,
        notes: if f.manipulation.suspicious { f.manipulation.flags.clone() } else { f.confidence.reasons.clone() },
        command: Some(f.view_command.clone()),
        icon: icon_of(registry, &f.price_key),
    }
}

fn bazaar_row(registry: &skypilot_data::Registry, f: &skypilot_protocol::market::BazaarFlip) -> ModFlipRow {
    ModFlipRow {
        id: f.product_id.clone(),
        kind: "BAZAAR".into(),
        item_id: f.product_id.clone(),
        name: f.name.clone(),
        rarity: None,
        modifiers: Vec::new(),
        buy: f.buy_price,
        sell: f.sell_price,
        profit: f.expected_profit,
        roi: f.roi,
        profit_per_hour: f.profit_per_hour,
        amount: Some(f.suggested_amount),
        confidence: f.confidence.score,
        confidence_label: confidence_label(&f.confidence),
        risk: enum_name(&f.risk),
        sell_time: f.sell_fill_minutes.as_ref().map(minutes),
        do_not_flip: false,
        notes: f.notes.clone(),
        command: Some(format!("/bz {}", f.name)),
        icon: icon_of(registry, &f.product_id),
    }
}

#[derive(Deserialize)]
pub struct MarketQuery {
    pub kind: Option<String>,
    pub limit: Option<usize>,
    pub lang: Option<String>,
}

/// AH / Bazaar flips for the in-game GUI (public market data, no account needed).
pub async fn market(State(state): State<Arc<AppState>>, Query(q): Query<MarketQuery>) -> ApiResult<Json<ModMarketPanel>> {
    let lang = skypilot_reco::Lang::parse(q.lang.as_deref());
    let limit = q.limit.unwrap_or(20).clamp(1, 50);
    let registry = state.registry();
    let note = lang.t(
        "Information only - SkyPilot never buys or sells. Commands are put into your chat box; you decide whether to send them.",
        "情報表示のみです。SkyPilotは売買しません。コマンドはチャット欄に入力されるだけで、送信するかはご自身で判断してください。",
    );
    let panel = match q.kind.as_deref().unwrap_or("ah") {
        "bazaar" | "bz" => {
            let list = state.market.bazaar_list(f64::MAX);
            ModMarketPanel {
                kind: "BAZAAR".into(),
                title: lang.t("Bazaar order flips", "バザーの注文フリップ"),
                rows: list.flips.iter().take(limit).map(|f| bazaar_row(&registry, f)).collect(),
                age_seconds: list.freshness.age_seconds as i64,
                stale: list.freshness.stale,
                note,
            }
        }
        "ah" => {
            let query = skypilot_protocol::market::FlipQuery { max_budget: None, min_profit: None, min_roi: None, min_confidence: None, max_risk: None, search: None, limit: Some(limit as u32) };
            let list = state.market.flips(&query, true);
            ModMarketPanel {
                kind: "AH".into(),
                title: lang.t("Auction House flips", "オークションのフリップ"),
                rows: list.flips.iter().map(|f| ah_row(&registry, f)).collect(),
                age_seconds: list.freshness.age_seconds as i64,
                stale: list.freshness.stale,
                note,
            }
        }
        _ => return Err(AppError::bad_request("kind must be ah or bazaar")),
    };
    Ok(Json(panel))
}

#[derive(Deserialize)]
pub struct AlertQuery {
    pub since: Option<i64>,
    pub lang: Option<String>,
}

/// New AH flips (seen after `since`) matching the player's notification rules, or a
/// conservative default rule when they have none. Manipulation-flagged flips never alert.
pub async fn alerts(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AlertQuery>) -> ApiResult<Json<ModAlerts>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let now = AppState::now_ms();
    let since = q.since.unwrap_or(now - 120_000).clamp(now - 3_600_000, now);
    let mut rules: Vec<skypilot_protocol::market::NotificationRule> = state.store.notification_rules(user.id).await?.into_iter().filter(|r| r.enabled).collect();
    if rules.is_empty() {
        rules.push(skypilot_protocol::market::NotificationRule {
            id: "default".into(),
            name: lang.t("Default: profit ≥ 1M, ROI ≥ 10%, confidence ≥ 70%", "標準: 利益100万以上・ROI 10%以上・信頼度70%以上"),
            max_budget: None,
            min_profit: Some(1_000_000.0),
            min_roi: Some(0.10),
            min_confidence: Some(0.7),
            max_risk: Some(skypilot_protocol::common::RiskLevel::Medium),
            enabled: true,
        });
    }
    let all = state.market.flips(&skypilot_protocol::market::FlipQuery { max_budget: None, min_profit: None, min_roi: None, min_confidence: None, max_risk: None, search: None, limit: Some(500) }, true);
    let registry = state.registry();
    let mut seen = std::collections::HashSet::new();
    let mut rows = Vec::new();
    for rule in &rules {
        for f in crate::push::matching(rule, &all.flips) {
            if f.seen_at_ms > since && seen.insert(f.auction_id.clone()) {
                rows.push(ah_row(&registry, f));
            }
        }
    }
    rows.sort_by(|a, b| b.profit.total_cmp(&a.profit));
    rows.truncate(5);
    Ok(Json(ModAlerts { now_ms: now, rows, rules: rules.into_iter().map(|r| r.name).collect() }))
}

fn metric_line(m: &skypilot_protocol::common::Metric) -> HudLine {
    let coins = m.unit.as_deref() == Some("coins");
    let num = |v: f64| if coins { fmt(v) } else if v.fract() == 0.0 { format!("{v:.0}") } else { format!("{v:.1}") };
    let unit = match m.unit.as_deref() {
        Some(u) if u != "coins" => format!(" {u}"),
        _ => String::new(),
    };
    let value = match (&m.text, m.before, m.after, m.value) {
        (Some(t), _, _, _) => t.clone(),
        (_, Some(b), Some(a), _) => format!("{} → {}{unit}", num(b), num(a)),
        (_, _, _, Some(v)) => format!("{}{unit}", num(v)),
        _ => "?".into(),
    };
    HudLine { label: m.label.clone(), value }
}

/// "What should I do now?" and the roadmaps from the last analysis, sized for the GUI.
pub async fn plan(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<HudQuery>) -> ApiResult<Json<ModPlan>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let Some((resp, at)) = state.store.latest_analysis(user.id).await? else {
        return Ok(Json(ModPlan {
            profile: None,
            analyzed_at_ms: 0,
            goals: Vec::new(),
            actions: Vec::new(),
            warnings: Vec::new(),
            message: Some(lang.t("Analyse your profile on the SkyPilot web first.", "先にSkyPilotのWebでプロフィールを分析してください。")),
        }));
    };
    let registry = state.registry();
    let goals = resp
        .goals
        .iter()
        .map(|g| ModPlanGoal {
            title: g.roadmap.title.clone(),
            progress: g.roadmap.overall_progress,
            remaining_cost: g.roadmap.remaining_cost,
            remaining_hours: g.roadmap.remaining_hours,
            steps: g
                .roadmap
                .steps
                .iter()
                .map(|s| ModPlanStep { title: s.title.clone(), detail: s.detail.clone(), status: enum_name(&s.status), progress: s.progress, cost: s.estimated_cost, hours: s.estimated_hours })
                .collect(),
        })
        .collect();
    let actions = resp
        .actions
        .iter()
        .take(10)
        .map(|a| ModPlanAction {
            title: a.title.clone(),
            category: a.category.clone(),
            why: a.why.clone(),
            steps: a.steps.clone(),
            metrics: a.metrics.iter().map(metric_line).collect(),
            confidence: a.confidence.score,
            cost: a.estimated_cost,
            hours: a.estimated_hours,
            items: a.purchases.iter().take(4).map(|p| ModItemRef { item_id: p.item_id.clone(), name: p.name.clone(), price: p.price, icon: icon_of(&registry, &p.item_id) }).collect(),
        })
        .collect();
    Ok(Json(ModPlan {
        profile: Some(format!("{} · {}", resp.analysis.player.username, resp.analysis.cute_name)),
        analyzed_at_ms: at,
        goals,
        actions,
        warnings: resp.warnings.clone(),
        message: None,
    }))
}

#[derive(Deserialize)]
pub struct IconQuery {
    pub ids: String,
}

/// Item icons by id (comma separated, at most 64). Static game data.
pub async fn icons(State(state): State<Arc<AppState>>, Query(q): Query<IconQuery>) -> ApiResult<Json<std::collections::BTreeMap<String, ItemIcon>>> {
    let ids: Vec<&str> = q.ids.split(',').map(str::trim).filter(|s| !s.is_empty()).collect();
    if ids.len() > 64 || ids.iter().any(|s| s.len() > 96 || !s.chars().all(|c| c.is_ascii_alphanumeric() || "_:;|=-".contains(c))) {
        return Err(AppError::bad_request("ids: up to 64 item ids"));
    }
    let registry = state.registry();
    Ok(Json(ids.into_iter().map(|id| (id.to_string(), icon_of(&registry, id))).collect()))
}
