//! Profile completion in % (web and mod), computed on demand from the latest analysis.

use crate::{auth, error::ApiResult, state::AppState};
use axum::{Json, extract::State, http::HeaderMap};
use axum_extra::extract::cookie::CookieJar;
use skypilot_protocol::profile::CompletionReport;
use std::sync::Arc;

/// Completion of the signed-in player's last analysed profile. Empty (overall = null) until a
/// profile has been analysed; the server never fills gaps with guesses.
pub async fn completion(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap) -> ApiResult<Json<CompletionReport>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    Ok(Json(match state.store.latest_analysis(user.id).await? {
        Some((resp, at)) => {
            let mut r = skypilot_profile::completion::compute(&resp.analysis, &state.registry());
            r.analyzed_at_ms = Some(at);
            r
        }
        None => CompletionReport::default(),
    }))
}
