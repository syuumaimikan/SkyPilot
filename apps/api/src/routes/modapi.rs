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
use skypilot_protocol::advisor::UpgradeOption;
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

/// Price of one item for tooltips. Market data is public, so no account is needed.
pub async fn price(State(state): State<Arc<AppState>>, Json(req): Json<ModPriceRequest>) -> ApiResult<Json<ModPriceResponse>> {
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
        npc_sell: registry.npc_price(&market_id),
        craft_cost: skypilot_pricing::craft::CraftCalculator::new(&registry, state.market.as_ref()).craft_cost(&market_id).map(|(c, n, _)| c / n),
        bazaar_tax_rate: bz.as_ref().map(|_| registry.curated.market.bazaar.tax_rate),
    }))
}

/// Sea creatures and the chat lines announcing them (public data, SkyHanni-REPO).
pub async fn sea_creatures(State(state): State<Arc<AppState>>) -> ApiResult<Json<ModSeaCreatures>> {
    let registry = state.registry();
    Ok(Json(ModSeaCreatures {
        creatures: registry
            .sea_creatures
            .all
            .iter()
            .map(|c| ModSeaCreature { name: c.name.clone(), area: c.area.clone(), message: c.message.clone(), rarity: c.rarity.clone(), fishing_xp: c.fishing_xp })
            .collect(),
        attribution: "Sea creature data: SkyHanni-REPO (MIT) - github.com/hannibal002/SkyHanni-REPO".into(),
    }))
}

/// SkyBlock calendar constants and the next events (public, no player data).
pub async fn calendar(State(state): State<Arc<AppState>>) -> ApiResult<Json<ModCalendar>> {
    let registry = state.registry();
    let cal = &registry.curated.calendar;
    let now = AppState::now_ms();
    let mut events: Vec<ModCalendarEvent> = cal
        .recurring
        .iter()
        .map(|e| {
            let (start, end) = cal.next_recurring(e, now);
            ModCalendarEvent {
                id: e.id.clone(),
                name: e.name.clone(),
                start_ms: start,
                end_ms: end,
                active: start <= now && now < end,
                every_days: Some(e.every_days),
                offset_days: Some(e.offset_days),
                duration_days: Some(e.duration_days),
            }
        })
        .collect();
    events.extend(cal.upcoming(now).into_iter().map(|(e, start, end, active)| ModCalendarEvent {
        id: e.id,
        name: e.name,
        start_ms: start,
        end_ms: end,
        active,
        every_days: None,
        offset_days: None,
        duration_days: None,
    }));
    events.sort_by_key(|e| e.start_ms);
    Ok(Json(ModCalendar { epoch_ms: cal.epoch_ms, day_ms: cal.day_ms, days_per_month: cal.days_per_month, months: cal.months.clone(), events, now_ms: now }))
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

// ---------------------------------------------------------------- guides

#[derive(Deserialize)]
pub struct GuidesQuery {
    pub lang: Option<String>,
}

/// Progression guides with waypoints. Works without an account; with a linked account and an
/// analysis, steps are marked done from the profile.
pub async fn guides(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<GuidesQuery>) -> ApiResult<Json<ModGuides>> {
    let user = auth::any_user(&state, &jar, &headers).await.ok();
    let lang = match &user {
        Some(u) => AppState::lang_of(u, q.lang.as_deref()),
        None => skypilot_reco::Lang::parse(q.lang.as_deref()),
    };
    let player = match &user {
        Some(u) => state.player(u.id).await.ok(),
        None => None,
    };
    let registry = state.registry();
    let guides = tokio::task::block_in_place(|| {
        let ctx = match (&user, &player) {
            (Some(u), Some(p)) => Some(state.ctx(&registry, p, u, lang)),
            _ => None,
        };
        registry
            .curated
            .guides
            .iter()
            .map(|g| ModGuide {
                id: g.id.clone(),
                island: g.island.clone(),
                title: lang.t(&g.title, &g.title_ja),
                source: g.source.clone(),
                steps: g
                    .steps
                    .iter()
                    .map(|s| {
                        let island = s.island.clone().unwrap_or_else(|| g.island.clone());
                        ModGuideStep {
                            title: lang.t(&s.title, &s.title_ja),
                            text: lang.t(&s.text, &s.text_ja),
                            done: s.done.as_deref().and_then(|d| ctx.as_ref().and_then(|c| c.check(d))),
                            waypoint: s.location.as_deref().and_then(|name| registry.locations.find(&island, name)).map(|l| ModWaypoint {
                                island: l.island.clone(),
                                name: l.name.clone(),
                                x: l.x,
                                y: l.y,
                                z: l.z,
                            }),
                        }
                    })
                    .collect(),
            })
            .collect()
    });
    Ok(Json(ModGuides { guides, attribution: "Waypoints: SkyHanni-REPO (MIT). Facts: Hypixel SkyBlock Wiki.".into() }))
}

#[derive(Deserialize)]
pub struct LocationsQuery {
    pub island: String,
    pub q: Option<String>,
}

/// Named public places on an island (NPCs, areas, warps) to set a waypoint to.
pub async fn locations(State(state): State<Arc<AppState>>, Query(q): Query<LocationsQuery>) -> ApiResult<Json<Vec<ModWaypoint>>> {
    if q.island.len() > 40 || !q.island.chars().all(|c| c.is_ascii_uppercase() || c == '_') {
        return Err(AppError::bad_request("invalid island"));
    }
    let needle = q.q.as_deref().unwrap_or("").trim().to_lowercase();
    let registry = state.registry();
    let mut out: Vec<ModWaypoint> = registry
        .locations
        .on(&q.island)
        .iter()
        .filter(|l| needle.is_empty() || l.name.to_lowercase().contains(&needle))
        .map(|l| ModWaypoint { island: l.island.clone(), name: l.name.clone(), x: l.x, y: l.y, z: l.z })
        .collect();
    out.truncate(200);
    Ok(Json(out))
}

// ---------------------------------------------------------------- recommended gear

/// Recommended gear for the player's goals (slayer gear tiers) and the best-value upgrades for
/// combat, mining and farming. Clicking an item in game opens its recipe.
pub async fn gear(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<GuidesQuery>) -> ApiResult<Json<ModGear>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let Some((resp, _)) = state.store.latest_analysis(user.id).await? else {
        return Ok(Json(ModGear { sections: Vec::new(), message: Some(lang.t("Analyse your profile on the SkyPilot web first.", "先にSkyPilotのWebでプロフィールを分析してください。")) }));
    };
    let player = state.player(user.id).await?;
    let registry = state.registry();
    let sections = tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &player, &user, lang);
        let pets = ctx.analysis.pets.as_ref().map(Vec::as_slice).unwrap_or(&[]);
        let mut sections = Vec::new();
        let goal_slayers: Vec<String> = resp.goals.iter().flat_map(|g| g.graph.nodes.iter().filter_map(|n| n.id.strip_prefix("slayer:").map(str::to_string))).collect();
        for def in registry.curated.slayers.iter().filter(|d| goal_slayers.contains(&d.id)) {
            let mut items = Vec::new();
            for slot in &def.gear {
                for (tier, list) in [("REQUIRED", &slot.required), ("RECOMMENDED", &slot.recommended), ("LUXURY", &slot.luxury)] {
                    for id in list.iter().filter(|id| !id.starts_with("PET:")) {
                        items.push(ModGearItem {
                            id: id.clone(),
                            name: registry.display_name(id),
                            icon: icon_of(&registry, id),
                            slot: slot.slot.clone(),
                            tier: tier.into(),
                            owned: skypilot_profile::gear::owns_any(ctx.inv, pets, std::slice::from_ref(id)),
                            price: ctx.buy(id).map(|q| q.price),
                            note: None,
                        });
                    }
                }
            }
            sections.push(ModGearSection { title: lang.t(format!("{} gear", def.name), format!("{}用の装備", def.name)), items });
        }
        for (purpose, en, ja) in [("combat", "Combat upgrades (best value first)", "戦闘の装備強化（費用対効果順）"), ("mining", "Mining upgrades", "採掘の装備強化"), ("farming", "Farming upgrades", "農業の装備強化")] {
            let plan = if purpose == "mining" { skypilot_reco::providers::mining::gear_plan(&ctx, purpose, None) } else { skypilot_reco::providers::gear::plan(&ctx, purpose, None) };
            let mut opts: Vec<&UpgradeOption> = plan.selected.iter().chain(plan.rejected.iter()).collect();
            opts.sort_by(|a, b| b.gain_per_million.unwrap_or(0.0).total_cmp(&a.gain_per_million.unwrap_or(0.0)));
            let ready: Vec<&UpgradeOption> = opts.iter().copied().filter(|o| o.notes.is_empty()).take(6).collect();
            let locked: Vec<&UpgradeOption> = opts.iter().copied().filter(|o| !o.notes.is_empty()).take(2).collect();
            let items = ready
                .into_iter()
                .chain(locked)
                .map(|o| ModGearItem {
                    id: o.id.clone(),
                    name: o.name.clone(),
                    icon: icon_of(&registry, &o.id),
                    slot: o.slot.clone(),
                    tier: o.tier.clone(),
                    owned: o.owned,
                    price: o.cost,
                    note: o.notes.first().cloned().or_else(|| Some(format!("+{:.0} {}", o.gain, o.stat))),
                })
                .collect::<Vec<_>>();
            if !items.is_empty() {
                sections.push(ModGearSection { title: lang.t(en, ja), items });
            }
        }
        sections
    });
    Ok(Json(ModGear { sections, message: None }))
}

// ---------------------------------------------------------------- item browser & values

fn entry(state: &AppState, registry: &skypilot_data::Registry, id: &str) -> ModItemEntry {
    ModItemEntry {
        id: id.to_string(),
        name: registry.display_name(id),
        rarity: registry.item(id).and_then(|d| d.tier.clone()),
        icon: icon_of(registry, id),
        value: state.market.quote(id, PriceSide::Sell).map(|q| q.price),
    }
}

fn stack(state: &AppState, registry: &skypilot_data::Registry, s: &skypilot_data::neu::Stack) -> ModStack {
    ModStack {
        id: s.id.clone(),
        name: registry.display_name(&s.id),
        amount: s.amount,
        icon: icon_of(registry, &s.id),
        unit_price: if s.id == "SKYBLOCK_COIN" { Some(1.0) } else { state.market.quote(&s.id, PriceSide::Buy).map(|q| q.price) },
    }
}

fn cost_of(inputs: &[ModStack]) -> Option<f64> {
    inputs.iter().map(|s| s.unit_price.map(|p| p * s.amount)).sum()
}

fn valid_item_id(id: &str) -> bool {
    id.len() <= 96 && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == ':' || c == ';' || c == '-')
}

#[derive(Deserialize)]
pub struct ItemsQuery {
    pub q: Option<String>,
    pub offset: Option<usize>,
    pub limit: Option<usize>,
}

/// Item list for the in-game browser: all items by name, or search results (static data + prices).
pub async fn items(State(state): State<Arc<AppState>>, Query(q): Query<ItemsQuery>) -> ApiResult<Json<ModItemPage>> {
    let registry = state.registry();
    let query = q.q.as_deref().unwrap_or("").trim();
    if query.chars().count() > 64 {
        return Err(AppError::bad_request("query too long"));
    }
    let ids: Vec<String> = if query.is_empty() {
        let mut all: Vec<(&String, &skypilot_data::hypixel::ItemDef)> = registry.items.iter().collect();
        all.sort_by(|a, b| a.1.name.to_lowercase().cmp(&b.1.name.to_lowercase()).then_with(|| a.0.cmp(b.0)));
        all.into_iter().map(|(id, _)| id.clone()).collect()
    } else {
        registry.search_items(query, 400).into_iter().map(|d| d.id.clone()).collect()
    };
    let offset = q.offset.unwrap_or(0).min(ids.len());
    let limit = q.limit.unwrap_or(60).clamp(1, 120);
    let items = ids[offset..(offset + limit).min(ids.len())].iter().map(|id| entry(&state, &registry, id)).collect();
    Ok(Json(ModItemPage { total: ids.len() as u32, offset: offset as u32, items }))
}

/// One item: value, description, recipes (crafting grid / forge / Kat / NPC shop), drop sources and uses.
pub async fn item(State(state): State<Arc<AppState>>, Path(id): Path<String>) -> ApiResult<Json<ModItemDetail>> {
    if !valid_item_id(&id) {
        return Err(AppError::bad_request("invalid item id"));
    }
    let registry = state.registry();
    if registry.item(&id).is_none() && !registry.neu.display_names.contains_key(&id) && !registry.neu.recipes.contains_key(&id) {
        return Err(AppError::not_found("unknown item"));
    }
    use skypilot_data::neu::{Recipe, Stack};
    let one = |s: &str| Stack { id: s.to_string(), amount: 1.0 };
    let recipes: Vec<ModRecipe> = registry
        .neu
        .recipes
        .get(&id)
        .map(|rs| {
            rs.iter()
                .map(|r| match r {
                    Recipe::Crafting { output, inputs, grid } => {
                        let inputs: Vec<ModStack> = inputs.iter().map(|s| stack(&state, &registry, s)).collect();
                        ModRecipe {
                            kind: "CRAFTING".into(),
                            output: stack(&state, &registry, output),
                            cost: cost_of(&inputs),
                            inputs,
                            grid: grid.iter().map(|g| g.as_ref().map(|s| stack(&state, &registry, s))).collect(),
                            duration_secs: None,
                            npc: None,
                            coins: None,
                        }
                    }
                    Recipe::Forge { output, inputs, duration_secs } => {
                        let inputs: Vec<ModStack> = inputs.iter().map(|s| stack(&state, &registry, s)).collect();
                        ModRecipe {
                            kind: "FORGE".into(),
                            output: stack(&state, &registry, output),
                            cost: cost_of(&inputs),
                            inputs,
                            grid: Vec::new(),
                            duration_secs: Some(*duration_secs),
                            npc: None,
                            coins: None,
                        }
                    }
                    Recipe::Kat { input, output, coins, duration_secs, items } => {
                        let mut inputs: Vec<ModStack> = vec![stack(&state, &registry, &one(input))];
                        inputs.extend(items.iter().map(|s| stack(&state, &registry, s)));
                        let cost = cost_of(&inputs).map(|c| c + coins);
                        ModRecipe {
                            kind: "KAT".into(),
                            output: stack(&state, &registry, &one(output)),
                            inputs,
                            grid: Vec::new(),
                            duration_secs: Some(*duration_secs),
                            npc: Some("Kat".into()),
                            coins: Some(*coins),
                            cost,
                        }
                    }
                    Recipe::NpcShop { npc, result, cost } => {
                        let inputs: Vec<ModStack> = cost.iter().map(|s| stack(&state, &registry, s)).collect();
                        ModRecipe {
                            kind: "NPC_SHOP".into(),
                            output: stack(&state, &registry, result),
                            cost: cost_of(&inputs),
                            inputs,
                            grid: Vec::new(),
                            duration_secs: None,
                            npc: Some(registry.display_name(npc)),
                            coins: None,
                        }
                    }
                })
                .collect()
        })
        .unwrap_or_default();
    let mut drops: Vec<ModDropSource> = registry
        .neu
        .drops
        .values()
        .flat_map(|t| t.drops.iter().filter(|d| d.id == id).map(move |d| ModDropSource { source: t.name.clone(), chance: d.chance, amount: d.amount }))
        .collect();
    drops.sort_by(|a, b| b.chance.total_cmp(&a.chance));
    drops.truncate(30);
    let uses = |r: &Recipe| match r {
        Recipe::Crafting { inputs, .. } | Recipe::Forge { inputs, .. } => inputs.iter().any(|s| s.id == id),
        Recipe::Kat { input, items, .. } => input == &id || items.iter().any(|s| s.id == id),
        Recipe::NpcShop { cost, .. } => cost.iter().any(|s| s.id == id),
    };
    let mut used: Vec<String> = registry.neu.recipes.iter().filter(|(out, rs)| *out != &id && rs.iter().any(uses)).map(|(out, _)| out.clone()).collect();
    used.sort();
    used.truncate(40);
    let fair = state.market.fair_value(&id);
    let bz = state.market.state.read().bazaar.get(&id).cloned();
    let sell = state.market.quote(&id, PriceSide::Sell);
    let mut calc = skypilot_pricing::craft::CraftCalculator::new(&registry, state.market.as_ref());
    Ok(Json(ModItemDetail {
        item: entry(&state, &registry, &id),
        category: registry.item(&id).and_then(|d| d.category.clone()),
        lore: registry.neu.lore.get(&id).cloned().unwrap_or_default(),
        value_source: sell.map(|q| q.source.to_string()),
        lowest_bin: fair.as_ref().and_then(|f| f.lowest_bin),
        sales_per_day: fair.as_ref().map(|f| f.sales_per_day),
        bazaar_buy: bz.as_ref().and_then(skypilot_pricing::bazaar::instant_buy),
        bazaar_sell: bz.as_ref().and_then(skypilot_pricing::bazaar::instant_sell),
        npc_sell: registry.npc_price(&id),
        craft_cost: calc.craft_cost(&id).map(|(c, n, _)| c / n),
        recipes,
        drops,
        used_in: used.iter().map(|u| entry(&state, &registry, u)).collect(),
        wiki: registry.neu.wiki_urls.get(&id).cloned(),
    }))
}

/// Values of item stacks (e.g. a chest's contents), priced like networth items: fingerprint
/// from custom_data (stars, recombobulator, enchantments…) and live market data.
pub async fn values(State(state): State<Arc<AppState>>, Json(req): Json<ModValuesRequest>) -> ApiResult<Json<ModValues>> {
    if req.items.len() > 160 {
        return Err(AppError::bad_request("at most 160 items"));
    }
    let registry = state.registry();
    let mut total = 0.0;
    let values = req
        .items
        .iter()
        .map(|it| {
            let fp = from_client_json(&json!({"custom_data": it.custom_data, "count": it.count.clamp(1, 1_000_000)}))
                .and_then(|item| skypilot_pricing::normalize::fingerprint_item(&item, &registry));
            match fp {
                Some(fp) => {
                    let v = skypilot_pricing::networth::value_item(&fp, &registry, state.market.as_ref());
                    if let Some(v) = &v {
                        total += v.value;
                    }
                    ModValue {
                        skyblock_id: Some(fp.skyblock_id.clone()),
                        name: registry.display_name(&fp.market_id),
                        value: v.as_ref().map(|v| v.value),
                        confidence: v.map(|v| v.confidence),
                    }
                }
                None => ModValue { skyblock_id: None, name: String::new(), value: None, confidence: None },
            }
        })
        .collect();
    Ok(Json(ModValues { values, total }))
}
