//! Per-user settings that the web and the mod's settings screen edit, starting with the
//! translation provider. A user's own API keys are stored encrypted (see `secrets`), used
//! only for that user's translations, and never returned (only "configured" + last 4 chars).

use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
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
