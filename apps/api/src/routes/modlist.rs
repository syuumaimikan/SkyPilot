//! Update check for the player's other mods: the mod sends the SHA-1 of its mod files (opt-in),
//! this server asks Modrinth which version is the latest for each file and returns version info
//! and a link only. Nothing is downloaded or installed - updating stays the player's choice.

use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

use axum::Json;
use axum::extract::State;
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use serde::Deserialize;
use serde_json::{Value, json};
use skypilot_protocol::mod_api::{ModUpdate, ModUpdates};

use crate::auth;
use crate::error::{ApiResult, AppError};
use crate::state::AppState;

const MODRINTH: &str = "https://api.modrinth.com/v2/version_files/update";
const TTL_MS: i64 = 3_600_000;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModUpdatesRequest {
    pub hashes: Vec<String>,
    pub game_version: String,
}

fn valid(req: &ModUpdatesRequest) -> bool {
    !req.hashes.is_empty()
        && req.hashes.len() <= 300
        && req.hashes.iter().all(|h| h.len() == 40 && h.chars().all(|c| c.is_ascii_hexdigit()))
        && (1..=12).contains(&req.game_version.len())
        && req.game_version.chars().all(|c| c.is_ascii_digit() || c == '.')
}

/// Modrinth's answer → one entry per file it knows (files it does not know are left out).
pub fn parse_modrinth(v: &Value) -> Vec<ModUpdate> {
    let Some(map) = v.as_object() else { return Vec::new() };
    let mut out: Vec<ModUpdate> = map
        .iter()
        .filter_map(|(hash, ver)| {
            let project = ver.get("project_id")?.as_str()?.to_string();
            let id = ver.get("id")?.as_str()?.to_string();
            let latest_hashes: Vec<String> = ver
                .get("files")
                .and_then(Value::as_array)
                .map(|fs| fs.iter().filter_map(|f| f.pointer("/hashes/sha1").and_then(Value::as_str).map(str::to_lowercase)).collect())
                .unwrap_or_default();
            Some(ModUpdate {
                hash: hash.to_lowercase(),
                up_to_date: latest_hashes.contains(&hash.to_lowercase()),
                url: format!("https://modrinth.com/project/{project}/version/{id}"),
                project_id: project,
                version_id: id,
                version_number: ver.get("version_number").and_then(Value::as_str).unwrap_or("").to_string(),
                name: ver.get("name").and_then(Value::as_str).unwrap_or("").to_string(),
                published: ver.get("date_published").and_then(Value::as_str).map(str::to_string),
            })
        })
        .collect();
    out.sort_by(|a, b| a.hash.cmp(&b.hash));
    out
}

pub async fn mod_updates(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Json(req): Json<ModUpdatesRequest>) -> ApiResult<Json<ModUpdates>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    if !valid(&req) {
        return Err(AppError::bad_request("hashes must be 1-300 SHA-1 hex strings and gameVersion like 26.2"));
    }
    if state.limiters.translate.check_key(&format!("modupdates:{}", user.id)).is_err() {
        return Err(AppError::too_many());
    }
    static CACHE: OnceLock<Mutex<HashMap<String, (i64, Vec<ModUpdate>)>>> = OnceLock::new();
    let cache = CACHE.get_or_init(|| Mutex::new(HashMap::new()));
    let mut hashes: Vec<String> = req.hashes.iter().map(|h| h.to_lowercase()).collect();
    hashes.sort();
    hashes.dedup();
    let key = format!("{}|{}", req.game_version, hashes.join(","));
    let now = AppState::now_ms();
    if let Some((at, hit)) = cache.lock().expect("mod update cache").get(&key).cloned() {
        if now - at < TTL_MS {
            return Ok(Json(ModUpdates { updates: hit, checked_at_ms: at, source: "Modrinth".into() }));
        }
    }
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .user_agent(concat!("SkyPilot-server/", env!("CARGO_PKG_VERSION"), " (self-hosted SkyBlock companion; mod update check)"))
        .build()
        .map_err(|e| AppError::internal(e.to_string()))?;
    let body = json!({"hashes": hashes, "algorithm": "sha1", "loaders": ["fabric"], "game_versions": [req.game_version]});
    let resp = client.post(MODRINTH).json(&body).send().await.map_err(|e| AppError::upstream(format!("Modrinth unavailable: {e}")))?;
    if resp.status().as_u16() == 429 {
        // never retried here; the player can check again later
        return Err(AppError::too_many());
    }
    if !resp.status().is_success() {
        return Err(AppError::upstream(format!("Modrinth answered {}", resp.status())));
    }
    let v: Value = resp.json().await.map_err(|e| AppError::upstream(e.to_string()))?;
    let updates = parse_modrinth(&v);
    let mut c = cache.lock().expect("mod update cache");
    if c.len() > 200 {
        c.clear();
    }
    c.insert(key, (now, updates.clone()));
    Ok(Json(ModUpdates { updates, checked_at_ms: now, source: "Modrinth".into() }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_modrinth_latest_versions() {
        let v = json!({
            "aaaa000000000000000000000000000000000001": {"id": "v2", "project_id": "P1", "version_number": "1.2.0", "name": "Mod 1.2.0",
                "date_published": "2026-09-30T10:00:00Z", "files": [{"url": "https://cdn/x.jar", "hashes": {"sha1": "bbbb000000000000000000000000000000000002"}}]},
            "cccc000000000000000000000000000000000003": {"id": "v9", "project_id": "P2", "version_number": "3.0", "name": "Other",
                "files": [{"hashes": {"sha1": "CCCC000000000000000000000000000000000003"}}]}
        });
        let u = parse_modrinth(&v);
        assert_eq!(u.len(), 2);
        assert!(!u[0].up_to_date, "a newer file exists");
        assert_eq!(u[0].url, "https://modrinth.com/project/P1/version/v2");
        assert!(u[1].up_to_date, "same file (hash case-insensitive)");
        assert!(valid(&ModUpdatesRequest { hashes: vec!["a".repeat(40)], game_version: "26.2".into() }));
        assert!(!valid(&ModUpdatesRequest { hashes: vec!["zz".into()], game_version: "26.2".into() }));
        assert!(!valid(&ModUpdatesRequest { hashes: vec!["a".repeat(40)], game_version: "26.2; rm".into() }));
    }
}
