//! Domain advisors and interactive planners (all computed from the user's last analysed
//! profile + live market data).

use std::collections::BTreeMap;
use std::sync::Arc;

use axum::Json;
use axum::extract::{Path, Query, State};
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use serde::Deserialize;
use skypilot_protocol::advisor::*;
use skypilot_reco::providers;

use crate::auth;
use crate::error::{ApiResult, AppError};
use crate::state::AppState;

#[derive(Deserialize, Default)]
pub struct AdvisorQuery {
    pub lang: Option<String>,
    pub preset: Option<String>,
    pub purpose: Option<String>,
    pub budget: Option<f64>,
    pub slayer: Option<String>,
    pub target: Option<u32>,
    pub speed: Option<f64>,
    pub fortune: Option<f64>,
    pub gemstone_fortune: Option<f64>,
    pub dwarven_metal_fortune: Option<f64>,
    pub block_fortune: Option<f64>,
    pub pristine: Option<f64>,
    pub uptime: Option<f64>,
    pub fuel: Option<f64>,
    pub bps: Option<f64>,
    pub mithril: Option<f64>,
    pub gemstone: Option<f64>,
    pub glacite: Option<f64>,
}

fn finite(v: Option<f64>, lo: f64, hi: f64) -> Option<f64> {
    v.filter(|x| x.is_finite()).map(|x| x.clamp(lo, hi))
}

macro_rules! with_ctx {
    ($state:expr, $jar:expr, $headers:expr, $q:expr, |$ctx:ident, $user:ident| $body:expr) => {{
        let $user = auth::any_user(&$state, &$jar, &$headers).await?;
        let player = $state.player($user.id).await?;
        let registry = $state.registry();
        let lang = AppState::lang_of(&$user, $q.lang.as_deref());
        tokio::task::block_in_place(|| {
            let mut $ctx = $state.ctx(&registry, &player, &$user, lang);
            if let Some(b) = finite($q.budget, 0.0, 1e13) {
                $ctx.budget = Some(b);
            }
            $body
        })
    }};
}

pub async fn report(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Path(domain): Path<String>, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<AdvisorReport>> {
    if !state.planner.domains().contains(&domain.as_str()) {
        return Err(AppError::not_found(format!("unknown advisor domain '{domain}'")));
    }
    let user = auth::any_user(&state, &jar, &headers).await?;
    let texts = state.goal_texts(user.id).await;
    let player = state.player(user.id).await?;
    let registry = state.registry();
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let r = tokio::task::block_in_place(|| {
        let mut ctx = state.ctx(&registry, &player, &user, lang);
        if let Some(b) = finite(q.budget, 0.0, 1e13) {
            ctx.budget = Some(b);
        }
        state.planner.report(&ctx, &domain, &texts)
    });
    r.map(Json).ok_or_else(|| AppError::not_found("unknown domain"))
}

pub async fn domains(State(state): State<Arc<AppState>>) -> Json<Vec<&'static str>> {
    Json(state.planner.domains())
}

pub async fn mining_calc(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<MiningCalcResult>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| {
        let mut input = providers::mining::default_input(&ctx);
        if let Some(v) = finite(q.speed, 0.0, 1e6) {
            input.mining_speed = v;
        }
        if let Some(v) = finite(q.fortune, 0.0, 1e5) {
            input.mining_fortune = v;
        }
        if let Some(v) = finite(q.gemstone_fortune, 0.0, 1e5) {
            input.gemstone_fortune = v;
        }
        if let Some(v) = finite(q.dwarven_metal_fortune, 0.0, 1e5) {
            input.dwarven_metal_fortune = v;
        }
        if let Some(v) = finite(q.block_fortune, 0.0, 1e5) {
            input.block_fortune = v;
        }
        if let Some(v) = finite(q.pristine, 0.0, 100.0) {
            input.pristine = v;
        }
        if let Some(v) = finite(q.uptime, 0.0, 1.0) {
            input.uptime = v;
        }
        Ok(Json(providers::mining::calculate(&ctx, &input, finite(q.fuel, 0.0, 1e12).unwrap_or(0.0))))
    })
}

pub async fn hotm(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<HotmPlan>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| {
        providers::hotm::plan(&ctx, q.preset.as_deref().unwrap_or("progression")).map(Json).ok_or_else(|| AppError::not_found("HOTM layout unavailable"))
    })
}

pub async fn powder(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<Vec<PowderPlan>>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| {
        let mut targets: BTreeMap<String, f64> = providers::hotm::plan(&ctx, q.preset.as_deref().unwrap_or("progression")).map(|p| p.required_powder).unwrap_or_default();
        for (k, v) in [("MITHRIL", q.mithril), ("GEMSTONE", q.gemstone), ("GLACITE", q.glacite)] {
            if let Some(x) = finite(v, 0.0, 1e12) {
                targets.insert(k.to_string(), x);
            }
        }
        Ok(Json(providers::mining::powder_plans(&ctx, &targets)))
    })
}

pub async fn gear(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<GearPlan>> {
    let purpose = q.purpose.clone().unwrap_or_else(|| "combat".into());
    if purpose.len() > 32 {
        return Err(AppError::bad_request("purpose too long"));
    }
    with_ctx!(state, jar, headers, q, |ctx, _user| {
        let budget = ctx.budget;
        Ok(Json(if ["mining", "powder", "commission", "gemstone", "money", "progression"].contains(&purpose.as_str()) {
            providers::mining::gear_plan(&ctx, &purpose, budget)
        } else {
            providers::gear::plan(&ctx, &purpose, budget)
        }))
    })
}

pub async fn slayer_plan(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<SlayerPlan>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| {
        let id = q.slayer.clone().unwrap_or_else(|| "zombie".into());
        let def = ctx.registry.curated.slayer(&id).ok_or_else(|| AppError::not_found("unknown slayer"))?;
        let cur = ctx.slayer(&def.id).map(|s| s.level.level).unwrap_or(0);
        let target = q.target.unwrap_or(cur + 1).min(9);
        providers::slayer::plan(&ctx, def, target).map(Json).ok_or_else(|| AppError::not_found("slayer table unavailable"))
    })
}

pub async fn money(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<Vec<MoneyMethodEstimate>>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| Ok(Json(providers::money::rank(&ctx))))
}

pub async fn floors(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<Vec<FloorReadiness>>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| Ok(Json(providers::dungeons::all_readiness(&ctx))))
}

pub async fn crops(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<Vec<CropRate>>> {
    with_ctx!(state, jar, headers, q, |ctx, _user| Ok(Json(providers::farming::crop_rates(&ctx, finite(q.bps, 0.1, 20.0)))))
}

pub async fn liquidation(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<Vec<LiquidationEntry>>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let texts = state.goal_texts(user.id).await;
    let player = state.player(user.id).await?;
    let registry = state.registry();
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    Ok(Json(tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &player, &user, lang);
        let (_, specs) = state.planner.parse(&ctx, &texts);
        let goals = state.planner.contexts(&ctx, &specs);
        providers::liquidation::classify(&ctx, &goals)
    })))
}

// Farm designs do not need a profile.
pub async fn farms(State(state): State<Arc<AppState>>) -> Json<Vec<FarmDesignSummary>> {
    Json(state.registry().farms.iter().map(providers::farming::design_summary).collect())
}

pub async fn farm(State(state): State<Arc<AppState>>, Path(id): Path<String>, Query(q): Query<AdvisorQuery>) -> ApiResult<Json<FarmDesign>> {
    let registry = state.registry();
    let lang = skypilot_reco::Lang::parse(q.lang.as_deref());
    // Material prices only need the market; use an empty analysis context.
    let empty = empty_analysis();
    let inv = skypilot_profile::Inventories::default();
    let ctx = skypilot_reco::Ctx { registry: &registry, oracle: state.market.as_ref(), market: Some(state.market.as_ref()), analysis: &empty, inv: &inv, play_style: Default::default(), budget: None, now_ms: AppState::now_ms(), lang };
    providers::farming::design(&ctx, &id).map(Json).ok_or_else(|| AppError::not_found("unknown farm design"))
}

pub fn empty_analysis() -> skypilot_protocol::profile::PlayerAnalysis {
    use skypilot_protocol::common::{Freshness, Known};
    use skypilot_protocol::profile::*;
    fn u<T>() -> Known<T> {
        Known::unknown("No profile")
    }
    PlayerAnalysis {
        player: PlayerRef { username: String::new(), uuid: String::new() },
        profile_id: String::new(),
        cute_name: String::new(),
        game_mode: None,
        fetched: Freshness { source: "none".into(), updated_at_ms: 0, age_seconds: 0, stale: true },
        api_access: ApiAccess { inventory: false, banking: false, collections: false, skills: false, museum: false, garden: false },
        skyblock_level: u(),
        skills: u(),
        skill_average: u(),
        dungeons: u(),
        slayers: u(),
        mining: u(),
        garden: u(),
        accessories: u(),
        pets: u(),
        gear: u(),
        economy: EconomyInfo { purse: 0.0, bank: u(), personal_bank: u(), motes: None, essence: Default::default() },
        networth: u(),
        collections: u(),
        museum: u(),
        kuudra: u(),
        rift: u(),
        chocolate_factory: u(),
        fishing: u(),
        foraging: u(),
        minions: u(),
        bestiary_kills: None,
        fairy_souls: None,
        sacks: u(),
        progression: ProgressionScores { areas: vec![], overall: 0.0, stage: GameStage::EarlyGame, strong_areas: vec![], weak_areas: vec![] },
        warnings: vec![],
    }
}
