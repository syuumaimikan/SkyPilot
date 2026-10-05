//! /api/auth/*, /api/devices, /api/device/* (device authorization for the mod).

use std::net::SocketAddr;
use std::sync::Arc;

use axum::Json;
use axum::extract::{ConnectInfo, Path, Query, State};
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use chrono::{Duration, Utc};
use serde::Deserialize;
use serde_json::{Value, json};
use skypilot_protocol::auth::*;

use crate::auth::{self, sha256_hex};
use crate::error::{ApiResult, AppError};
use crate::state::AppState;
use crate::store::DeviceCodeRow;

const DEVICE_CODE_TTL_SECS: i64 = 600;
const DEVICE_POLL_INTERVAL: u32 = 5;

pub async fn me(State(state): State<Arc<AppState>>, jar: CookieJar) -> ApiResult<(CookieJar, Json<Me>)> {
    let (jar, s) = auth::ensure(&state, jar).await?;
    let devices = state.store.list_devices(s.user.id).await?;
    Ok((
        jar,
        Json(Me {
            user_id: s.user.id.to_string(),
            anonymous: s.user.login.is_none(),
            login: s.user.login.clone(),
            csrf_token: s.csrf,
            linked_devices: devices
                .into_iter()
                .map(|d| LinkedDevice { id: d.id, name: d.name, created_at_ms: d.created_at.timestamp_millis(), last_used_at_ms: d.last_used_at.map(|t| t.timestamp_millis()) })
                .collect(),
        }),
    ))
}

/// Attach a login + password to the current (anonymous) workspace.
pub async fn register(State(state): State<Arc<AppState>>, ConnectInfo(peer): ConnectInfo<SocketAddr>, jar: CookieJar, headers: HeaderMap, Json(c): Json<Credentials>) -> ApiResult<Json<Value>> {
    if state.limiters.auth.check_key(&auth::client_ip(&state, &headers, peer)).is_err() {
        return Err(AppError::too_many());
    }
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    auth::validate_login(&c.login)?;
    auth::validate_password(&c.password)?;
    if s.user.login.is_some() {
        return Err(AppError::conflict("ALREADY_REGISTERED", "this workspace already has a login"));
    }
    if state.store.find_user_by_login(&c.login).await?.is_some() {
        return Err(AppError::conflict("LOGIN_TAKEN", "that login is already taken"));
    }
    let hash = auth::hash_password(&c.password)?;
    state.store.set_credentials(s.user.id, &c.login, &hash).await?;
    Ok(Json(json!({"ok": true})))
}

pub async fn login(State(state): State<Arc<AppState>>, ConnectInfo(peer): ConnectInfo<SocketAddr>, jar: CookieJar, headers: HeaderMap, Json(c): Json<Credentials>) -> ApiResult<(CookieJar, Json<Value>)> {
    if state.limiters.auth.check_key(&auth::client_ip(&state, &headers, peer)).is_err() {
        return Err(AppError::too_many());
    }
    // CSRF: login is accepted from a session-bound token, preventing login CSRF.
    auth::require_csrf(&state, &jar, &headers).await?;
    let user = state.store.find_user_by_login(&c.login).await?;
    let ok = user.as_ref().and_then(|u| u.password_hash.as_deref()).map(|h| auth::verify_password(&c.password, h)).unwrap_or(false);
    let Some(user) = user.filter(|_| ok) else {
        return Err(AppError::new(axum::http::StatusCode::UNAUTHORIZED, "INVALID_CREDENTIALS", "invalid login or password"));
    };
    let jar = auth::end_session(&state, jar).await?;
    let (jar, csrf) = auth::start_session(&state, jar, user.id).await?;
    Ok((jar, Json(json!({"ok": true, "csrfToken": csrf}))))
}

pub async fn logout(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<(CookieJar, Json<Value>)> {
    auth::require_csrf(&state, &jar, &headers).await?;
    let jar = auth::end_session(&state, jar).await?;
    Ok((jar, Json(json!({"ok": true}))))
}

pub async fn revoke_device(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Path(id): Path<String>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    let ok = state.store.revoke_device(s.user.id, &id).await?;
    if !ok {
        return Err(AppError::not_found("device not found"));
    }
    Ok(Json(json!({"ok": true})))
}

/// Mod -> backend: start device authorization.
pub async fn device_code(State(state): State<Arc<AppState>>, ConnectInfo(peer): ConnectInfo<SocketAddr>, headers: HeaderMap, Json(req): Json<DeviceCodeRequest>) -> ApiResult<Json<DeviceCodeResponse>> {
    if state.limiters.auth.check_key(&auth::client_ip(&state, &headers, peer)).is_err() {
        return Err(AppError::too_many());
    }
    let name: String = req.device_name.chars().filter(|c| !c.is_control()).take(64).collect();
    let device_code = auth::random_token(32);
    let user_code = auth::user_code();
    state
        .store
        .create_device_code(&DeviceCodeRow {
            device_code_hash: sha256_hex(&device_code),
            user_code: user_code.clone(),
            device_name: if name.trim().is_empty() { "Minecraft".into() } else { name },
            expires_at: Utc::now() + Duration::seconds(DEVICE_CODE_TTL_SECS),
            approved_user_id: None,
            denied: false,
            last_poll_at: None,
        })
        .await?;
    let base = state.config.public_web_url.trim_end_matches('/');
    Ok(Json(DeviceCodeResponse {
        device_code,
        verification_uri: format!("{base}/link"),
        verification_uri_complete: format!("{base}/link?code={user_code}"),
        user_code,
        expires_in: DEVICE_CODE_TTL_SECS as u32,
        interval: DEVICE_POLL_INTERVAL,
    }))
}

/// Mod polls for its token.
pub async fn device_token(State(state): State<Arc<AppState>>, Json(req): Json<DeviceTokenRequest>) -> ApiResult<Json<DeviceTokenResponse>> {
    let hash = sha256_hex(&req.device_code);
    let pending = |status: &str| Json(DeviceTokenResponse { status: status.into(), access_token: None, scope: None });
    let Some(row) = state.store.device_code(&hash).await? else { return Ok(pending("expired_token")) };
    if row.expires_at < Utc::now() {
        state.store.delete_device_code(&hash).await?;
        return Ok(pending("expired_token"));
    }
    if row.denied {
        state.store.delete_device_code(&hash).await?;
        return Ok(pending("access_denied"));
    }
    let too_fast = row.last_poll_at.is_some_and(|t| (Utc::now() - t).num_milliseconds() < (DEVICE_POLL_INTERVAL as i64 * 1000 - 500));
    state.store.touch_device_code(&hash).await?;
    let Some(user_id) = row.approved_user_id else { return Ok(pending(if too_fast { "slow_down" } else { "authorization_pending" })) };
    let token = format!("spm_{}", auth::random_token(32));
    let id = auth::random_token(8);
    state.store.create_device_token(&id, &sha256_hex(&token), user_id, &row.device_name).await?;
    state.store.delete_device_code(&hash).await?;
    Ok(Json(DeviceTokenResponse { status: "ok".into(), access_token: Some(token), scope: Some("mod:read mod:session translate".into()) }))
}

#[derive(Deserialize)]
pub struct LookupQuery {
    user_code: String,
}

/// Web: show which device is asking before the user approves.
pub async fn device_lookup(State(state): State<Arc<AppState>>, jar: CookieJar, Query(q): Query<LookupQuery>) -> ApiResult<Json<Value>> {
    auth::current(&state, &jar).await?.ok_or_else(AppError::unauthorized)?;
    let code = q.user_code.trim().to_uppercase();
    let row = state.store.device_code_by_user_code(&code).await?.filter(|r| r.expires_at > Utc::now()).ok_or_else(|| AppError::not_found("code not found or expired"))?;
    Ok(Json(json!({"deviceName": row.device_name, "expiresAtMs": row.expires_at.timestamp_millis()})))
}

pub async fn device_approve(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<DeviceApproveRequest>) -> ApiResult<Json<Value>> {
    let s = auth::require_csrf(&state, &jar, &headers).await?;
    let code = req.user_code.trim().to_uppercase();
    let row = state.store.device_code_by_user_code(&code).await?.filter(|r| r.expires_at > Utc::now()).ok_or_else(|| AppError::not_found("code not found or expired"))?;
    state.store.set_device_code_decision(&row.user_code, req.approve.then_some(s.user.id), !req.approve).await?;
    Ok(Json(json!({"ok": true, "approved": req.approve})))
}
