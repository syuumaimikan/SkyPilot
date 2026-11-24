//! Per-user settings that the web and the mod's settings screen edit, starting with the
//! translation provider. A user's own API keys are stored encrypted (see `secrets`), used
//! only for that user's translations, and never returned (only "configured" + last 4 chars).

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use skypilot_protocol::mod_api::{KeyInfo, TranslationSettingsUpdate, TranslationSettingsView, TranslationTestResult, UserSettings};
use skypilot_protocol::translation::TranslateRequest;
use skypilot_translation::ProviderChoice;
use skypilot_translation::anthropic::{AnthropicClient, DEFAULT_MODEL};
use skypilot_translation::providers::{Claude, DeepL, Provider};
use uuid::Uuid;

use crate::auth;
use crate::error::{ApiResult, AppError};
use crate::state::{AppState, UserTranslation};

pub const PROVIDERS: [&str; 4] = ["server", "deepl", "claude", "none"];

/// The mod writes with its bearer token; the web with its session cookie + CSRF header.
async fn writer(state: &AppState, jar: &CookieJar, headers: &HeaderMap) -> ApiResult<Uuid> {
    if let Some((_, user)) = auth::bearer(state, headers).await? {
        return Ok(user.id);
    }
    Ok(auth::require_csrf(state, jar, headers).await?.user.id)
}

fn translation_obj(settings: &Option<Value>) -> Value {
    settings.as_ref().and_then(|s| s.get("translation")).cloned().unwrap_or_else(|| json!({}))
}

fn key_info(state: &AppState, t: &Value, field: &str) -> KeyInfo {
    match t.get(field).and_then(Value::as_str).and_then(|sealed| state.secrets.open(sealed)) {
        Some(k) => KeyInfo { configured: true, hint: Some(format!("…{}", k.chars().rev().take(4).collect::<String>().chars().rev().collect::<String>())) },
        None => KeyInfo { configured: false, hint: None },
    }
}

fn view(state: &AppState, settings: &Option<Value>) -> TranslationSettingsView {
    let t = translation_obj(settings);
    TranslationSettingsView {
        provider: t.get("provider").and_then(Value::as_str).unwrap_or("server").to_string(),
        deepl: key_info(state, &t, "deeplKey"),
        anthropic: key_info(state, &t, "anthropicKey"),
        server_provider: state.translator.status().external_provider,
        providers: PROVIDERS.iter().map(|s| s.to_string()).collect(),
    }
}

/// Builds the user's translation choice from their stored settings (cached for 10 minutes).
pub async fn user_translation(state: &AppState, user_id: Uuid) -> Arc<UserTranslation> {
    if let Some(hit) = state.user_translation.get(&user_id).await {
        return hit;
    }
    let settings = state.store.user_settings(user_id).await.ok().flatten();
    let t = translation_obj(&settings);
    let open = |field: &str| t.get(field).and_then(Value::as_str).and_then(|s| state.secrets.open(s));
    let choice = match t.get("provider").and_then(Value::as_str).unwrap_or("server") {
        "none" => UserTranslation::DictionaryOnly,
        "deepl" => match open("deeplKey") {
            Some(k) => UserTranslation::Own(Arc::new(DeepL::new(k)) as Arc<dyn Provider>),
            None => UserTranslation::Server,
        },
        "claude" => match open("anthropicKey") {
            Some(k) => {
                let model = std::env::var("ANTHROPIC_MODEL").ok().filter(|m| !m.trim().is_empty()).unwrap_or_else(|| DEFAULT_MODEL.to_string());
                UserTranslation::Own(Arc::new(Claude::new(AnthropicClient::new(k, model, true))) as Arc<dyn Provider>)
            }
            None => UserTranslation::Server,
        },
        _ => UserTranslation::Server,
    };
    let choice = Arc::new(choice);
    state.user_translation.insert(user_id, choice.clone()).await;
    choice
}

pub fn choice(t: &UserTranslation) -> ProviderChoice<'_> {
    match t {
        UserTranslation::Server => ProviderChoice::Server,
        UserTranslation::DictionaryOnly => ProviderChoice::DictionaryOnly,
        UserTranslation::Own(p) => ProviderChoice::Own(p),
    }
}

pub async fn get(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<UserSettings>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let settings = state.store.user_settings(user.id).await?;
    Ok(Json(UserSettings { translation: view(&state, &settings) }))
}

fn valid_key(k: &str) -> bool {
    (16..=200).contains(&k.len()) && k.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == ':' || c == '.')
}

pub async fn put_translation(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<TranslationSettingsUpdate>) -> ApiResult<Json<UserSettings>> {
    let user_id = writer(&state, &jar, &headers).await?;
    if !PROVIDERS.contains(&req.provider.as_str()) {
        return Err(AppError::bad_request("provider must be server, deepl, claude or none"));
    }
    let mut settings = state.store.user_settings(user_id).await?.unwrap_or_else(|| json!({}));
    if !settings.is_object() {
        settings = json!({});
    }
    let mut t = translation_obj(&Some(settings.clone()));
    if !t.is_object() {
        t = json!({});
    }
    let obj = t.as_object_mut().expect("object");
    obj.insert("provider".into(), json!(req.provider));
    for (field, value) in [("deeplKey", &req.deepl_key), ("anthropicKey", &req.anthropic_key)] {
        match value.as_deref().map(str::trim) {
            None => {}
            Some("") => {
                obj.remove(field);
            }
            Some(k) if valid_key(k) => {
                obj.insert(field.into(), json!(state.secrets.seal(k)));
            }
            Some(_) => return Err(AppError::bad_request("that does not look like an API key")),
        }
    }
    settings.as_object_mut().expect("object").insert("translation".into(), t);
    state.store.save_user_settings(user_id, &settings).await?;
    state.user_translation.invalidate(&user_id).await;
    Ok(Json(UserSettings { translation: view(&state, &Some(settings)) }))
}

/// Translates a fixed sample line with the user's current choice (not cached, not stored).
pub async fn test_translation(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<TranslationTestResult>> {
    let user_id = writer(&state, &jar, &headers).await?;
    if state.limiters.translate.check_key(&format!("test:{user_id}")).is_err() {
        return Err(AppError::too_many());
    }
    let choice_arc = user_translation(&state, user_id).await;
    let req = TranslateRequest { text: "Is anyone selling a cheap sword for my first dungeon run?".into(), source_lang: "en".into(), target_lang: "ja".into(), channel: None };
    let r = state.translator.translate_with(&req, choice(&choice_arc)).await;
    let ok = !r.unchanged;
    let message = if ok {
        "ok".to_string()
    } else {
        match &*choice_arc {
            UserTranslation::DictionaryOnly => "dictionary only: sentences outside the dictionary stay in English".into(),
            UserTranslation::Server if state.translator.status().external_provider.is_none() => "the server has no translation provider configured".into(),
            _ => format!("the provider did not answer ({})", r.provider),
        }
    };
    Ok(Json(TranslationTestResult { ok, provider: r.provider.clone(), translated: ok.then_some(r.translated), message }))
}

#[derive(Debug, Serialize, Deserialize)]
pub struct HypixelStatusView {
    pub configured: bool,
    /// False once Hypixel answered 403 for the key after its last success.
    pub valid: bool,
    /// "missing" | "unchecked" | "ok" | "rejected"
    #[serde(default)]
    pub key_state: String,
    #[serde(default)]
    pub last_ok_ms: Option<i64>,
    #[serde(default)]
    pub last_rejected_ms: Option<i64>,
    pub rate_limit_remaining: Option<i64>,
    pub rate_limit_limit: Option<i64>,
    pub hint: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct HypixelKeyUpdate {
    pub key: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SimpleResponse {
    pub ok: bool,
    pub message: String,
}

pub async fn get_hypixel_status(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<HypixelStatusView>> {
    let _user = auth::any_user(&state, &jar, &headers).await?;
    let health = state.hypixel.key_health();
    let configured = health.configured;
    let (rate_limit_remaining, rate_limit_limit) = state.hypixel.key_budget();
    let hint = state.hypixel.get_api_key().map(|k| {
        let last4: String = k.chars().rev().take(4).collect::<String>().chars().rev().collect();
        format!("…{}", last4)
    });
    Ok(Json(HypixelStatusView {
        configured,
        valid: health.usable(),
        key_state: health.state.to_string(),
        last_ok_ms: health.last_ok_ms,
        last_rejected_ms: health.last_rejected_ms,
        rate_limit_remaining,
        rate_limit_limit,
        hint,
    }))
}

pub async fn put_hypixel_key(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<HypixelKeyUpdate>) -> ApiResult<Json<SimpleResponse>> {
    let _user_id = writer(&state, &jar, &headers).await?;
    let key = req.key.trim();
    if key.is_empty() {
        state.hypixel.set_api_key(None);
        let _ = persist_hypixel_key("");
        return Ok(Json(SimpleResponse { ok: true, message: "Hypixel API key removed.".to_string() }));
    }
    if !skypilot_hypixel::is_valid_uuid(key) {
        return Err(AppError::bad_request("Key must be a valid UUID"));
    }
    let valid = state.hypixel.verify_key(key).await.map_err(|e| AppError::bad_request(format!("Hypixel verification failed: {e}")))?;
    if !valid {
        return Err(AppError::bad_request("Hypixel rejected this key (403 forbidden)."));
    }
    state.hypixel.set_api_key(Some(key.to_string()));
    let _ = persist_hypixel_key(key);
    Ok(Json(SimpleResponse { ok: true, message: "Hypixel API key verified and saved.".to_string() }))
}

fn persist_hypixel_key(key: &str) -> Result<(), std::io::Error> {
    persist_env_var("HYPIXEL_API_KEY", key)
}

#[derive(Debug, Clone, Deserialize)]
pub struct AutostartUpdate {
    pub enabled: bool,
}

pub async fn get_autostart(State(_state): State<Arc<AppState>>) -> ApiResult<Json<crate::autostart::AutostartStatus>> {
    Ok(Json(crate::autostart::status()))
}

pub async fn put_autostart(
    State(state): State<Arc<AppState>>,
    jar: CookieJar,
    headers: HeaderMap,
    axum::extract::ConnectInfo(addr): axum::extract::ConnectInfo<std::net::SocketAddr>,
    Json(req): Json<AutostartUpdate>,
) -> ApiResult<Json<crate::autostart::AutostartStatus>> {
    let is_local = addr.ip().is_loopback();
    if !is_local {
        let _user_id = writer(&state, &jar, &headers).await?;
    }
    let status = crate::autostart::set_enabled(req.enabled, &state.config.root).map_err(|e| AppError::bad_request(e.to_string()))?;
    Ok(Json(status))
}



fn persist_env_var(var: &str, val: &str) -> Result<(), std::io::Error> {
    let env_path = std::path::Path::new(".env");
    if !env_path.exists() {
        return Ok(());
    }
    let content = std::fs::read_to_string(env_path)?;
    let mut found = false;
    let mut lines = Vec::new();
    let prefix = format!("{}=", var);
    for line in content.lines() {
        if line.trim_start().starts_with(&prefix) {
            lines.push(format!("{}={}", var, val));
            found = true;
        } else {
            lines.push(line.to_string());
        }
    }
    if !found {
        lines.push(format!("{}={}", var, val));
    }
    let ending = if content.contains("\r\n") { "\r\n" } else { "\n" };
    std::fs::write(env_path, lines.join(ending) + ending)?;
    Ok(())
}

fn mask_key(k: Option<&str>) -> (bool, Option<String>) {
    match k.filter(|s| !s.trim().is_empty()) {
        Some(s) => {
            let last4: String = s.chars().rev().take(4).collect::<String>().chars().rev().collect();
            (true, Some(format!("…{}", last4)))
        }
        None => (false, None),
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerApisView {
    pub hypixel: HypixelApiView,
    pub translation: TranslationApiView,
    pub llm: LlmApiView,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HypixelApiView {
    pub configured: bool,
    pub base_url: String,
    pub hint: Option<String>,
    pub rate_limit_remaining: Option<i64>,
    pub rate_limit_limit: Option<i64>,
    /// "missing" | "unchecked" | "ok" | "rejected" (from real Hypixel answers)
    #[serde(default)]
    pub key_state: String,
    #[serde(default)]
    pub last_ok_ms: Option<i64>,
    #[serde(default)]
    pub last_rejected_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TranslationApiView {
    pub active_provider: Option<String>,
    pub deepl_configured: bool,
    pub deepl_hint: Option<String>,
    pub anthropic_configured: bool,
    pub anthropic_hint: Option<String>,
    pub anthropic_model: Option<String>,
    pub openai_configured: bool,
    pub openai_hint: Option<String>,
    pub openai_model: Option<String>,
    pub openai_base_url: Option<String>,
    pub libretranslate_url: Option<String>,
    pub ollama_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LlmApiView {
    pub provider: String,
    pub configured: bool,
    pub model: Option<String>,
    pub hint: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerApisUpdate {
    pub hypixel_key: Option<String>,
    pub hypixel_base_url: Option<String>,
    pub translation_provider: Option<String>,
    pub deepl_key: Option<String>,
    pub anthropic_key: Option<String>,
    pub anthropic_model: Option<String>,
    pub openai_key: Option<String>,
    pub openai_model: Option<String>,
    pub openai_base_url: Option<String>,
    pub libretranslate_url: Option<String>,
    pub ollama_url: Option<String>,
    pub llm_provider: Option<String>,
    pub llm_key: Option<String>,
    pub llm_model: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HypixelAppGuide {
    pub dashboard_url: String,
    pub project_name: String,
    pub category: String,
    pub target_audience: String,
    pub description: String,
    pub policy_compliance: String,
    pub requested_endpoints: Vec<String>,
    pub instructions: Vec<String>,
}

pub fn current_server_apis_view(state: &AppState) -> ServerApisView {
    let (hypixel_configured, hypixel_hint) = mask_key(state.hypixel.get_api_key().as_deref());
    let (rate_limit_remaining, rate_limit_limit) = state.hypixel.key_budget();
    let health = state.hypixel.key_health();
    let hypixel = HypixelApiView {
        configured: hypixel_configured,
        base_url: state.hypixel.get_base_url(),
        hint: hypixel_hint,
        rate_limit_remaining,
        rate_limit_limit,
        key_state: health.state.to_string(),
        last_ok_ms: health.last_ok_ms,
        last_rejected_ms: health.last_rejected_ms,
    };

    let deepl_env = std::env::var("DEEPL_API_KEY").ok();
    let (deepl_configured, deepl_hint) = mask_key(deepl_env.as_deref());
    let anthropic_env = std::env::var("ANTHROPIC_API_KEY").ok();
    let (anthropic_configured, anthropic_hint) = mask_key(anthropic_env.as_deref());
    let openai_env = std::env::var("OPENAI_API_KEY").ok();
    let (openai_configured, openai_hint) = mask_key(openai_env.as_deref());

    let translation = TranslationApiView {
        active_provider: state.translator.status().external_provider,
        deepl_configured,
        deepl_hint,
        anthropic_configured,
        anthropic_hint,
        anthropic_model: std::env::var("ANTHROPIC_MODEL").ok().filter(|s| !s.trim().is_empty()),
        openai_configured,
        openai_hint,
        openai_model: std::env::var("OPENAI_MODEL").ok().filter(|s| !s.trim().is_empty()),
        openai_base_url: std::env::var("OPENAI_BASE_URL").ok().filter(|s| !s.trim().is_empty()),
        libretranslate_url: std::env::var("LIBRETRANSLATE_URL").ok().filter(|s| !s.trim().is_empty()),
        ollama_url: std::env::var("OLLAMA_URL").ok().filter(|s| !s.trim().is_empty()),
    };

    let llm_guard = state.llm.read().unwrap();
    let (llm_configured, llm_model, llm_hint) = if let Some(client) = &*llm_guard {
        let (_, h) = mask_key(anthropic_env.as_deref());
        (true, Some(client.model.clone()), h)
    } else {
        (false, None, None)
    };
    let llm = LlmApiView {
        provider: if llm_configured { "anthropic".to_string() } else { "none".to_string() },
        configured: llm_configured,
        model: llm_model,
        hint: llm_hint,
    };

    ServerApisView { hypixel, translation, llm }
}

pub async fn get_server_apis(State(state): State<Arc<AppState>>) -> ApiResult<Json<ServerApisView>> {
    Ok(Json(current_server_apis_view(&state)))
}

pub async fn put_server_apis(
    State(state): State<Arc<AppState>>,
    jar: CookieJar,
    headers: HeaderMap,
    axum::extract::ConnectInfo(addr): axum::extract::ConnectInfo<std::net::SocketAddr>,
    Json(req): Json<ServerApisUpdate>,
) -> ApiResult<Json<ServerApisView>> {
    let is_local = addr.ip().is_loopback();
    if !is_local {
        let _user_id = writer(&state, &jar, &headers).await?;
    }

    if let Some(key) = &req.hypixel_key {
        let trimmed = key.trim();
        if trimmed.is_empty() {
            state.hypixel.set_api_key(None);
            let _ = persist_env_var("HYPIXEL_API_KEY", "");
        } else {
            if !skypilot_hypixel::is_valid_uuid(trimmed) {
                return Err(AppError::bad_request("Hypixel key must be a valid UUID"));
            }
            let valid = state.hypixel.verify_key(trimmed).await.map_err(|e| AppError::bad_request(format!("Hypixel check failed: {e}")))?;
            if !valid {
                return Err(AppError::bad_request("Hypixel rejected this key (403 forbidden)"));
            }
            state.hypixel.set_api_key(Some(trimmed.to_string()));
            let _ = persist_env_var("HYPIXEL_API_KEY", trimmed);
        }
    }

    if let Some(base) = &req.hypixel_base_url {
        let trimmed = base.trim();
        if !trimmed.is_empty() {
            state.hypixel.set_base_url(trimmed.to_string());
            let _ = persist_env_var("HYPIXEL_BASE_URL", trimmed);
        }
    }

    if let Some(p) = &req.translation_provider {
        let _ = persist_env_var("TRANSLATION_PROVIDER", p);
        unsafe { std::env::set_var("TRANSLATION_PROVIDER", p); }
    }
    if let Some(k) = &req.deepl_key {
        let _ = persist_env_var("DEEPL_API_KEY", k.trim());
        unsafe { std::env::set_var("DEEPL_API_KEY", k.trim()); }
    }
    if let Some(k) = &req.anthropic_key {
        let _ = persist_env_var("ANTHROPIC_API_KEY", k.trim());
        unsafe { std::env::set_var("ANTHROPIC_API_KEY", k.trim()); }
    }
    if let Some(m) = &req.anthropic_model {
        let _ = persist_env_var("ANTHROPIC_MODEL", m.trim());
        unsafe { std::env::set_var("ANTHROPIC_MODEL", m.trim()); }
    }
    if let Some(k) = &req.openai_key {
        let _ = persist_env_var("OPENAI_API_KEY", k.trim());
        unsafe { std::env::set_var("OPENAI_API_KEY", k.trim()); }
    }
    if let Some(m) = &req.openai_model {
        let _ = persist_env_var("OPENAI_MODEL", m.trim());
        unsafe { std::env::set_var("OPENAI_MODEL", m.trim()); }
    }
    if let Some(u) = &req.openai_base_url {
        let _ = persist_env_var("OPENAI_BASE_URL", u.trim());
        unsafe { std::env::set_var("OPENAI_BASE_URL", u.trim()); }
    }
    if let Some(u) = &req.libretranslate_url {
        let _ = persist_env_var("LIBRETRANSLATE_URL", u.trim());
        unsafe { std::env::set_var("LIBRETRANSLATE_URL", u.trim()); }
    }
    if let Some(u) = &req.ollama_url {
        let _ = persist_env_var("OLLAMA_URL", u.trim());
        unsafe { std::env::set_var("OLLAMA_URL", u.trim()); }
    }

    // Hot-reload translation provider
    let new_provider = skypilot_translation::providers::from_env();
    state.translator.set_provider(new_provider);

    if let Some(p) = &req.llm_provider {
        let _ = persist_env_var("LLM_PROVIDER", p.trim());
        unsafe { std::env::set_var("LLM_PROVIDER", p.trim()); }
    }

    // Hot-reload LLM client if configured
    if let Some(k) = &req.llm_key {
        let key_str = k.trim();
        if key_str.is_empty() {
            *state.llm.write().unwrap() = None;
        } else {
            let model = req.llm_model.as_deref().unwrap_or(skypilot_translation::anthropic::DEFAULT_MODEL);
            *state.llm.write().unwrap() = Some(AnthropicClient::new(key_str.to_string(), model.to_string(), true));
        }
    }

    Ok(Json(current_server_apis_view(&state)))
}

pub async fn get_hypixel_app_guide() -> ApiResult<Json<HypixelAppGuide>> {
    Ok(Json(HypixelAppGuide {
        dashboard_url: "https://developer.hypixel.net/dashboard".into(),
        project_name: "SkyPilot".into(),
        category: "In-game Mod / Companion Tool / SkyBlock Utility".into(),
        target_audience: "Hypixel SkyBlock players seeking helpful in-game UI hints, inventory value tracking, wardrobe cosmetics, and dungeon guides.".into(),
        description: "SkyPilot is an open-source Fabric client mod and self-hosted server companion for Hypixel SkyBlock. It displays in-game information (market values, wardrobe previews, puzzle directions, and progression roadmaps) without automating player actions. Hypixel API policy compliance: all requests are strictly read-only, aggressive client/server caching is enforced (120s+ for player profiles, 30s+ for bazaar/auctions), RateLimit headers are honored with automatic exponential backoff, and zero gameplay actions are automated.".into(),
        policy_compliance: "Strictly read-only; RateLimit-* headers honored; requests cached for 30s-120s; no key sharing or rotation; circuit breakers prevent hammering during outages; fully open-source.".into(),
        requested_endpoints: vec![
            "/v2/skyblock/profiles (Player stats & skills progression)".into(),
            "/v2/skyblock/museum (Museum item collection)".into(),
            "/v2/skyblock/garden (Garden crop milestones)".into(),
            "/v2/resources/skyblock/items (Item IDs and definitions)".into(),
            "/v2/resources/skyblock/skills (Skill XP requirements)".into(),
            "/v2/resources/skyblock/collections (Collection tier requirements)".into(),
            "/v2/resources/skyblock/election (Current mayor perks)".into(),
            "/v2/skyblock/bazaar & /v2/skyblock/auctions (Market item values)".into(),
        ],
        instructions: vec![
            "1. ブラウザで Hypixel Developer Dashboard (https://developer.hypixel.net/dashboard) にログインします。".into(),
            "2. 'Create Application' または 'My Applications' をクリックします。".into(),
            "3. プロジェクト名に 'SkyPilot'、プロジェクト説明に用意された説明文を貼り付けます（クリップボードに自動コピー済み）。".into(),
            "4. 申請を完了して発行された API Key (UUID形式) をコピーします。".into(),
            "5. SkyPilot の設定画面または申請スクリプトに API Key を貼り付けると、自動で接続テスト・保存が行われます。".into(),
        ],
    }))
}
