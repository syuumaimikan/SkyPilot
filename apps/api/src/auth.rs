//! Web sessions (HttpOnly cookie + double-submit CSRF token), optional login (argon2id),
//! and bearer tokens for the mod issued through the device-authorization flow.
//! The mod never sees Microsoft/Minecraft credentials or session tokens.

use std::net::{IpAddr, SocketAddr};

use argon2::password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString, rand_core::OsRng};
use argon2::Argon2;
use axum::http::HeaderMap;
use axum_extra::extract::cookie::{Cookie, CookieJar, SameSite};
use chrono::{Duration, Utc};
use rand::Rng;
use sha2::{Digest, Sha256};

use crate::error::{ApiResult, AppError};
use crate::state::AppState;
use crate::store::{DeviceTokenRow, UserRow};

pub const SESSION_COOKIE: &str = "sp_session";
pub const CSRF_HEADER: &str = "x-csrf-token";

pub fn random_token(bytes: usize) -> String {
    let mut b = vec![0u8; bytes];
    rand::thread_rng().fill(&mut b[..]);
    hex::encode(b)
}

pub fn sha256_hex(s: &str) -> String {
    hex::encode(Sha256::digest(s.as_bytes()))
}

/// Constant-time string comparison.
pub fn ct_eq(a: &str, b: &str) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.bytes().zip(b.bytes()).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

pub fn hash_password(pw: &str) -> anyhow::Result<String> {
    let salt = SaltString::generate(&mut OsRng);
    Ok(Argon2::default().hash_password(pw.as_bytes(), &salt).map_err(|e| anyhow::anyhow!(e.to_string()))?.to_string())
}

pub fn verify_password(pw: &str, hash: &str) -> bool {
    PasswordHash::new(hash).map(|h| Argon2::default().verify_password(pw.as_bytes(), &h).is_ok()).unwrap_or(false)
}

/// Unambiguous user code like "WDJB-MJHT" (RFC 8628 recommends a consonant alphabet).
pub fn user_code() -> String {
    const ALPHA: &[u8] = b"BCDFGHJKLMNPQRSTVWXZ";
    let mut rng = rand::thread_rng();
    let s: String = (0..8).map(|_| ALPHA[rng.gen_range(0..ALPHA.len())] as char).collect();
    format!("{}-{}", &s[..4], &s[4..])
}

pub struct SessionCtx {
    pub user: UserRow,
    pub csrf: String,
}

fn session_cookie(state: &AppState, value: String) -> Cookie<'static> {
    let mut c = Cookie::new(SESSION_COOKIE, value);
    c.set_http_only(true);
    c.set_secure(state.config.secure_cookies);
    c.set_same_site(SameSite::Lax);
    c.set_path("/");
    c.set_max_age(time_dur(60));
    c
}

fn time_dur(days: i64) -> cookie::time::Duration {
    cookie::time::Duration::days(days)
}

/// Existing session, if any.
pub async fn current(state: &AppState, jar: &CookieJar) -> ApiResult<Option<SessionCtx>> {
    let Some(c) = jar.get(SESSION_COOKIE) else { return Ok(None) };
    let Some(s) = state.store.get_session(&sha256_hex(c.value())).await? else { return Ok(None) };
    let Some(user) = state.store.get_user(s.user_id).await? else { return Ok(None) };
    Ok(Some(SessionCtx { user, csrf: s.csrf_token }))
}

/// Existing session or a new anonymous workspace (no sign-up needed to use SkyPilot).
pub async fn ensure(state: &AppState, jar: CookieJar) -> ApiResult<(CookieJar, SessionCtx)> {
    if let Some(s) = current(state, &jar).await? {
        return Ok((jar, s));
    }
    let user_id = state.store.create_user().await?;
    let (jar, csrf) = start_session(state, jar, user_id).await?;
    let user = state.store.get_user(user_id).await?.ok_or_else(|| AppError::internal("user vanished"))?;
    Ok((jar, SessionCtx { user, csrf }))
}

pub async fn start_session(state: &AppState, jar: CookieJar, user_id: uuid::Uuid) -> ApiResult<(CookieJar, String)> {
    let token = random_token(32);
    let csrf = random_token(16);
    state.store.create_session(&sha256_hex(&token), user_id, &csrf, Utc::now() + Duration::days(60)).await?;
    Ok((jar.add(session_cookie(state, token)), csrf))
}

pub async fn end_session(state: &AppState, jar: CookieJar) -> ApiResult<CookieJar> {
    if let Some(c) = jar.get(SESSION_COOKIE) {
        state.store.delete_session(&sha256_hex(c.value())).await?;
    }
    Ok(jar.remove(Cookie::build(SESSION_COOKIE).path("/")))
}

/// Session + CSRF check for state-changing requests.
pub async fn require_csrf(state: &AppState, jar: &CookieJar, headers: &HeaderMap) -> ApiResult<SessionCtx> {
    let s = current(state, jar).await?.ok_or_else(AppError::unauthorized)?;
    let sent = headers.get(CSRF_HEADER).and_then(|v| v.to_str().ok()).unwrap_or_default();
    if !ct_eq(sent, &s.csrf) {
        return Err(AppError::forbidden("missing or invalid CSRF token"));
    }
    Ok(s)
}

/// Bearer token issued to the mod (scope: read + session stats + translate).
pub async fn bearer(state: &AppState, headers: &HeaderMap) -> ApiResult<Option<(DeviceTokenRow, UserRow)>> {
    let Some(h) = headers.get(axum::http::header::AUTHORIZATION).and_then(|v| v.to_str().ok()) else { return Ok(None) };
    let Some(token) = h.strip_prefix("Bearer ").map(str::trim) else { return Ok(None) };
    if token.len() < 32 || token.len() > 200 {
        return Err(AppError::unauthorized());
    }
    let Some(row) = state.store.device_token(&sha256_hex(token)).await? else { return Err(AppError::unauthorized()) };
    if row.revoked {
        return Err(AppError::unauthorized());
    }
    let user = state.store.get_user(row.user_id).await?.ok_or_else(AppError::unauthorized)?;
    let id = row.id.clone();
    let store = state.store.clone();
    tokio::spawn(async move {
        let _ = store.touch_device_token(&id).await;
    });
    Ok(Some((row, user)))
}

/// User from either a mod bearer token or a web session (GET endpoints).
pub async fn any_user(state: &AppState, jar: &CookieJar, headers: &HeaderMap) -> ApiResult<UserRow> {
    if let Some((_, u)) = bearer(state, headers).await? {
        return Ok(u);
    }
    current(state, jar).await?.map(|s| s.user).ok_or_else(AppError::unauthorized)
}

pub fn client_ip(state: &AppState, headers: &HeaderMap, peer: SocketAddr) -> IpAddr {
    if state.config.trust_proxy {
        if let Some(ip) = headers
            .get("x-forwarded-for")
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.split(',').next())
            .and_then(|v| v.trim().parse::<IpAddr>().ok())
        {
            return ip;
        }
    }
    peer.ip()
}

pub fn validate_login(login: &str) -> ApiResult<()> {
    if !(3..=32).contains(&login.len()) || !login.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-' || c == '.') {
        return Err(AppError::bad_request("login must be 3-32 characters of letters, digits, _ - ."));
    }
    Ok(())
}

pub fn validate_password(pw: &str) -> ApiResult<()> {
    if pw.len() < 10 || pw.len() > 200 {
        return Err(AppError::bad_request("password must be 10-200 characters"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn primitives() {
        assert!(ct_eq("abc", "abc"));
        assert!(!ct_eq("abc", "abd"));
        assert!(!ct_eq("abc", "abcd"));
        let h = hash_password("correct horse battery").unwrap();
        assert!(verify_password("correct horse battery", &h));
        assert!(!verify_password("wrong", &h));
        let c = user_code();
        assert_eq!(c.len(), 9);
        assert!(c.chars().all(|ch| ch == '-' || "BCDFGHJKLMNPQRSTVWXZ".contains(ch)));
        assert_eq!(random_token(32).len(), 64);
    }
}
