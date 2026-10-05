//! Profile analysis, workspace, goals and progress snapshots.

use std::net::SocketAddr;
use std::sync::Arc;

use axum::Json;
use axum::extract::{ConnectInfo, Path, Query, State};
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use serde::Deserialize;
use serde_json::{Value, json};
use skypilot_hypixel::HypixelError;
use skypilot_profile::{AnalyzerInput, analyze, list_profiles, snapshot};
use skypilot_protocol::goals::{AnalyzeRequest, AnalyzeResponse, GoalPriority, SavedGoal, WorkspaceState};
use skypilot_protocol::profile::{PlayerRef, ProfileListEntry, ProgressSnapshot};
use skypilot_reco::Lang;

use crate::auth;
use crate::error::{ApiResult, AppError};
use crate::state::{AppState, LoadedPlayer, gzip};
use crate::store::RawProfile;

#[derive(Deserialize)]
pub struct LangQuery {
    pub lang: Option<String>,
}

pub async fn profiles(State(state): State<Arc<AppState>>, Path(name): Path<String>) -> ApiResult<Json<Vec<ProfileListEntry>>> {
    if state.config.dev_sample_profile && name.eq_ignore_ascii_case(SAMPLE_USER) {
        let (_, _, v, _, _, _) = load_sample_profile(&state, None)?;
        return Ok(Json(list_profiles(&v)));
    }
    let (uuid, _) = state.hypixel.resolve_username(&name).await?;
    let v = state.hypixel.profiles(&uuid).await?;
    Ok(Json(list_profiles(&v)))
}

/// Reserved name served from `fixtures/` when SKYPILOT_DEV_SAMPLE_PROFILE=1 (development and
/// end-to-end tests without a Hypixel API key). Disabled by default.
pub const SAMPLE_USER: &str = "SampleUser";

type ProfileBundle = (String, String, Value, String, Option<Value>, Option<Value>);

fn load_sample_profile(state: &AppState, profile_id: Option<&str>) -> ApiResult<ProfileBundle> {
    let dir = state.config.root.join("fixtures/hypixel");
    let read = |name: &str| -> ApiResult<Value> { serde_json::from_slice(&std::fs::read(dir.join(name)).map_err(AppError::internal)?).map_err(AppError::internal) };
    let profiles = read("profiles_sample.json")?;
    let museum = read("museum_sample.json").ok();
    let selected = skypilot_profile::select_profile(&profiles, profile_id).map_err(|e| AppError::bad_request(e.to_string()))?;
    let profile_id = selected.get("profile_id").and_then(Value::as_str).unwrap_or_default().to_string();
    let uuid = selected.get("members").and_then(Value::as_object).and_then(|m| m.keys().next().cloned()).ok_or_else(|| AppError::internal("sample profile has no member"))?;
    Ok((uuid, SAMPLE_USER.to_string(), profiles, profile_id, museum, None))
}

fn goal_id(text: &str) -> String {
    auth::sha256_hex(&text.to_lowercase())[..12].to_string()
}

pub async fn analyze_handler(
    State(state): State<Arc<AppState>>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    jar: CookieJar,
    headers: HeaderMap,
    Query(q): Query<LangQuery>,
    Json(req): Json<AnalyzeRequest>,
) -> ApiResult<Json<AnalyzeResponse>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    if state.limiters.analyze.check_key(&auth::client_ip(&state, &headers, peer)).is_err() {
        return Err(AppError::too_many());
    }
    let goal = req.goal.trim();
    if goal.is_empty() || goal.chars().count() > 300 || req.secondary_goals.len() > 5 || req.secondary_goals.iter().any(|g| g.chars().count() > 300) {
        return Err(AppError::bad_request("goal must be 1-300 characters (max 5 secondary goals)"));
    }
    if req.budget.is_some_and(|b| !b.is_finite() || b < 0.0) {
        return Err(AppError::bad_request("budget must be a positive number"));
    }
    let lang = Lang::parse(q.lang.as_deref().or(Some(s.user.lang.as_str())));
    let user_id = s.user.id;
    let sample = state.config.dev_sample_profile && req.username.eq_ignore_ascii_case(SAMPLE_USER);
    let (uuid, canonical, profiles, profile_id, museum, garden) = if sample {
        load_sample_profile(&state, req.profile_id.as_deref())?
    } else {
        let (uuid, canonical) = state.hypixel.resolve_username(&req.username).await?;
        // Fetch (user-initiated only - SkyPilot never polls player data in the background).
        let fetched = match state.hypixel.profiles(&uuid).await {
            Ok(v) => Ok(v),
            Err(e @ (HypixelError::RateLimited { .. } | HypixelError::CircuitOpen { .. } | HypixelError::Network(_) | HypixelError::Upstream { .. })) => Err(e),
            Err(e) => return Err(e.into()),
        };
        let profiles = match fetched {
            Ok(v) => v,
            Err(e) => {
                // Hypixel is down: serve the last valid snapshot for this player, clearly labelled.
                if let Some((mut prev, _)) = state.store.latest_analysis(user_id).await? {
                    if prev.analysis.player.uuid == uuid {
                        let age = AppState::last_response_age(&prev);
                        prev.warnings.insert(0, format!("Hypixel API temporarily unavailable. Last valid snapshot: {}m {}s ago", age / 60, age % 60));
                        return Ok(Json(prev));
                    }
                }
                return Err(AppError::from(e));
            }
        };
        let selected = skypilot_profile::select_profile(&profiles, req.profile_id.as_deref()).map_err(|e| AppError::bad_request(e.to_string()))?;
        let profile_id = selected.get("profile_id").and_then(Value::as_str).unwrap_or_default().to_string();
        let museum = state.hypixel.museum(&profile_id).await.ok();
        let garden = state.hypixel.garden(&profile_id).await.ok();
        (uuid, canonical, profiles, profile_id, museum, garden)
    };
    let fetched_at = AppState::now_ms();

    let registry = state.registry();
    let player = PlayerRef { username: canonical.clone(), uuid: uuid.clone() };
    let loaded = tokio::task::block_in_place(|| -> ApiResult<LoadedPlayer> {
        let input = AnalyzerInput { player, profiles: &profiles, profile_id: Some(&profile_id), museum: museum.as_ref(), garden: garden.as_ref(), fetched_at_ms: fetched_at, now_ms: fetched_at };
        let (analysis, inv) = analyze(&input, &registry, state.market.as_ref()).map_err(|e| AppError::bad_request(e.to_string()))?;
        Ok(LoadedPlayer { analysis, inv, fetched_at_ms: fetched_at })
    })?;

    // Persist preferences + goals before planning (planner reads them).
    state.store.update_prefs(user_id, Some(&uuid), Some(&canonical), Some(&profile_id), req.play_style, req.budget, if lang == Lang::Ja { "ja" } else { "en" }).await?;
    let now = AppState::now_ms();
    let mut goals = vec![SavedGoal { id: goal_id(goal), text: goal.to_string(), priority: GoalPriority::Primary, created_at_ms: now }];
    for g in req.secondary_goals.iter().map(|g| g.trim()).filter(|g| !g.is_empty()) {
        goals.push(SavedGoal { id: goal_id(g), text: g.to_string(), priority: GoalPriority::Secondary, created_at_ms: now });
    }
    state.store.save_goals(user_id, &goals).await?;
    let user = state.store.get_user(user_id).await?.ok_or_else(AppError::unauthorized)?;
    let texts: Vec<(String, GoalPriority)> = goals.iter().map(|g| (g.text.clone(), g.priority)).collect();

    let resp = tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &loaded, &user, lang);
        let out = state.planner.plan(&ctx, &texts, 8);
        let mut warnings = loaded.analysis.warnings.clone();
        if sample {
            warnings.insert(0, lang.t("Development sample profile (anonymised test fixture) - not live Hypixel data.", "開発用サンプルプロフィール（匿名化したテストデータ）です。Hypixelの最新データではありません。"));
        }
        if !out.parse.unmatched.is_empty() {
            warnings.push(lang.t(format!("Could not understand: {}", out.parse.unmatched.join(", ")), format!("理解できなかった目標: {}", out.parse.unmatched.join("、"))));
        }
        let (ah, _, _) = state.market.freshness();
        if ah.stale {
            warnings.push(lang.t("Market data is stale or still loading - prices may be missing.", "市場データが古いか読み込み中のため、価格が欠けている可能性があります。"));
        }
        AnalyzeResponse { analysis: loaded.analysis.clone(), parse: out.parse, goals: out.goals, actions: out.actions, play_style: req.play_style, market: Some(ah), warnings }
    });

    let snap = snapshot(&resp.analysis, fetched_at);
    state.store.save_analysis(user_id, &resp, &snap).await?;
    let raw = RawProfile {
        minecraft_uuid: uuid,
        username: canonical,
        profile_id: Some(profile_id),
        fetched_at_ms: fetched_at,
        profiles: gzip(&serde_json::to_vec(&profiles).unwrap_or_default()),
        museum: museum.as_ref().map(|m| gzip(&serde_json::to_vec(m).unwrap_or_default())),
        garden: garden.as_ref().map(|g| gzip(&serde_json::to_vec(g).unwrap_or_default())),
    };
    state.store.save_raw_profile(user_id, &raw).await?;
    state.players.insert(user_id, Arc::new(loaded)).await;
    Ok(Json(resp))
}

pub async fn workspace(State(state): State<Arc<AppState>>, jar: CookieJar) -> ApiResult<(CookieJar, Json<WorkspaceState>)> {
    let (jar, s) = auth::ensure(&state, jar).await?;
    let last = state.store.latest_analysis(s.user.id).await?;
    Ok((
        jar,
        Json(WorkspaceState {
            username: s.user.minecraft_name.clone(),
            profile_id: s.user.profile_id.clone(),
            play_style: s.user.play_style,
            goals: state.store.load_goals(s.user.id).await?,
            budget: s.user.budget,
            last_analysis_at_ms: last.as_ref().map(|l| l.1),
            last_analysis: last.map(|l| l.0),
        }),
    ))
}

#[derive(Deserialize)]
pub struct GoalInput {
    pub text: String,
    pub priority: GoalPriority,
}

/// Replace goals and re-plan from the stored profile (no Hypixel call).
pub async fn set_goals(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<LangQuery>, Json(input): Json<Vec<GoalInput>>) -> ApiResult<Json<AnalyzeResponse>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    if input.is_empty() || input.len() > 6 || input.iter().any(|g| g.text.trim().is_empty() || g.text.chars().count() > 300) {
        return Err(AppError::bad_request("1-6 goals of 1-300 characters"));
    }
    let now = AppState::now_ms();
    let goals: Vec<SavedGoal> = input.iter().map(|g| SavedGoal { id: goal_id(&g.text), text: g.text.trim().to_string(), priority: g.priority, created_at_ms: now }).collect();
    state.store.save_goals(s.user.id, &goals).await?;
    replan(&state, s.user.id, q.lang.as_deref()).await.map(Json)
}

pub async fn replan(state: &Arc<AppState>, user_id: uuid::Uuid, lang_q: Option<&str>) -> ApiResult<AnalyzeResponse> {
    let user = state.store.get_user(user_id).await?.ok_or_else(AppError::unauthorized)?;
    let player = state.player(user_id).await?;
    let lang = AppState::lang_of(&user, lang_q);
    let texts = state.goal_texts(user_id).await;
    let registry = state.registry();
    let prev = state.store.latest_analysis(user_id).await?;
    let resp = tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &player, &user, lang);
        let out = state.planner.plan(&ctx, &texts, 8);
        let (ah, _, _) = state.market.freshness();
        AnalyzeResponse {
            analysis: player.analysis.clone(),
            parse: out.parse,
            goals: out.goals,
            actions: out.actions,
            play_style: user.play_style,
            market: Some(ah),
            warnings: prev.as_ref().map(|p| p.0.analysis.warnings.clone()).unwrap_or_default(),
        }
    });
    // Same underlying data as the last fetch: keep the snapshot timestamp of that fetch so
    // progress history is not inflated by re-planning.
    let snap = snapshot(&resp.analysis, player.fetched_at_ms);
    state.store.save_analysis(user_id, &resp, &snap).await?;
    Ok(resp)
}

pub async fn snapshots(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<Vec<ProgressSnapshot>>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let (Some(uuid), Some(profile)) = (user.minecraft_uuid.as_deref(), user.profile_id.as_deref()) else { return Ok(Json(Vec::new())) };
    let mut snaps = state.store.snapshots(uuid, profile).await?;
    snaps.dedup_by(|a, b| a.taken_at_ms == b.taken_at_ms);
    Ok(Json(snaps))
}

pub async fn prefs(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(v): Json<Value>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    let style = v.get("playStyle").and_then(|x| serde_json::from_value(x.clone()).ok()).unwrap_or(s.user.play_style);
    let budget = match v.get("budget") {
        Some(Value::Null) => None,
        Some(b) => b.as_f64().filter(|x| x.is_finite() && *x >= 0.0),
        None => s.user.budget,
    };
    let lang = match v.get("lang").and_then(Value::as_str) {
        Some("en") => "en",
        Some("ja") => "ja",
        _ => s.user.lang.as_str(),
    }
    .to_string();
    state.store.update_prefs(s.user.id, None, None, None, style, budget, &lang).await?;
    Ok(Json(json!({"ok": true})))
}
