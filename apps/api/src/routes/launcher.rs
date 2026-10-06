//! Launcher update feed. The files come from `gradlew :launcher:launcherRelease` (installer,
//! `latest.json`, `latest.json.sig`) placed in `LAUNCHER_RELEASES_DIR`. The server only serves
//! them; the launcher checks the Ed25519 signature and the installer's SHA-256 itself, so a
//! changed file on the server is refused, not installed.

use std::sync::Arc;

use axum::Json;
use axum::body::Body;
use axum::extract::{Path, State};
use axum::http::header;
use axum::response::{IntoResponse, Response};
use serde_json::{Value, json};

use crate::error::{ApiResult, AppError};
use crate::state::AppState;

/// File names the feed may serve (no paths, no hidden files).
pub fn valid_file_name(name: &str) -> bool {
    !name.is_empty() && name.len() <= 80 && !name.starts_with('.') && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
}

/// `{ manifest, signature }`: the signed release description, or 404 when no release is published.
pub async fn latest(State(state): State<Arc<AppState>>) -> ApiResult<Json<Value>> {
    let dir = state.config.launcher_releases_dir.clone();
    let manifest = tokio::fs::read_to_string(dir.join("latest.json")).await.map_err(|_| AppError::not_found("no launcher release published"))?;
    let signature = tokio::fs::read_to_string(dir.join("latest.json.sig")).await.map_err(|_| AppError::not_found("the launcher release is not signed"))?;
    if manifest.len() > 4096 || signature.len() > 512 {
        return Err(AppError::not_found("no valid launcher release"));
    }
    Ok(Json(json!({ "manifest": manifest, "signature": signature.trim() })))
}

/// The installer named in the release description.
pub async fn file(State(state): State<Arc<AppState>>, Path(name): Path<String>) -> ApiResult<Response> {
    if !valid_file_name(&name) || name.starts_with("latest.json") {
        return Err(AppError::bad_request("invalid file name"));
    }
    let path = state.config.launcher_releases_dir.join(&name);
    let file = tokio::fs::File::open(&path).await.map_err(|_| AppError::not_found("no such file"))?;
    let len = file.metadata().await.map(|m| m.len()).unwrap_or(0);
    let stream = tokio_util::io::ReaderStream::new(file);
    Ok((
        [(header::CONTENT_TYPE, "application/octet-stream".to_string()), (header::CONTENT_LENGTH, len.to_string()), (header::CACHE_CONTROL, "no-store".to_string())],
        Body::from_stream(stream),
    )
        .into_response())
}

#[cfg(test)]
mod tests {
    use super::valid_file_name;

    #[test]
    fn only_plain_file_names() {
        assert!(valid_file_name("SkyPilot-Launcher-0.2.0.exe"));
        assert!(!valid_file_name("../secret"));
        assert!(!valid_file_name("a/b.exe"));
        assert!(!valid_file_name(".env"));
        assert!(!valid_file_name(""));
        assert!(!valid_file_name("a\\b.exe"));
    }
}
