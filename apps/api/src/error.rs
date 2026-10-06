use axum::Json;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use skypilot_hypixel::HypixelError;
use skypilot_protocol::ApiError;

#[derive(Debug)]
pub struct AppError {
    pub status: StatusCode,
    pub code: &'static str,
    pub message: String,
    pub last_valid_snapshot_age_seconds: Option<i64>,
}

pub type ApiResult<T> = Result<T, AppError>;

impl AppError {
    pub fn new(status: StatusCode, code: &'static str, message: impl Into<String>) -> Self {
        AppError { status, code, message: message.into(), last_valid_snapshot_age_seconds: None }
    }
    pub fn bad_request(message: impl Into<String>) -> Self {
        Self::new(StatusCode::BAD_REQUEST, "BAD_REQUEST", message)
    }
    pub fn unauthorized() -> Self {
        Self::new(StatusCode::UNAUTHORIZED, "UNAUTHORIZED", "authentication required")
    }
    pub fn forbidden(message: impl Into<String>) -> Self {
        Self::new(StatusCode::FORBIDDEN, "FORBIDDEN", message)
    }
    pub fn not_found(message: impl Into<String>) -> Self {
        Self::new(StatusCode::NOT_FOUND, "NOT_FOUND", message)
    }
    pub fn conflict(code: &'static str, message: impl Into<String>) -> Self {
        Self::new(StatusCode::CONFLICT, code, message)
    }
    /// A service this server relies on (e.g. Modrinth) failed.
    pub fn upstream(message: impl Into<String>) -> Self {
        Self::new(StatusCode::BAD_GATEWAY, "UPSTREAM", message)
    }
    pub fn too_many() -> Self {
        Self::new(StatusCode::TOO_MANY_REQUESTS, "RATE_LIMITED", "too many requests - please slow down")
    }
    pub fn internal(e: impl std::fmt::Display) -> Self {
        tracing::error!(error = %e, "internal error");
        Self::new(StatusCode::INTERNAL_SERVER_ERROR, "INTERNAL", "internal error")
    }
}

impl From<anyhow::Error> for AppError {
    fn from(e: anyhow::Error) -> Self {
        AppError::internal(e)
    }
}

impl From<HypixelError> for AppError {
    fn from(e: HypixelError) -> Self {
        match e {
            HypixelError::MissingKey => AppError::new(
                StatusCode::SERVICE_UNAVAILABLE,
                "HYPIXEL_KEY_MISSING",
                "This SkyPilot server has no Hypixel API key configured (HYPIXEL_API_KEY). Profile analysis is unavailable; market features still work.",
            ),
            HypixelError::InvalidKey => AppError::new(StatusCode::SERVICE_UNAVAILABLE, "HYPIXEL_KEY_INVALID", "The server's Hypixel API key was rejected."),
            HypixelError::NotFound => AppError::not_found("player or profile not found"),
            HypixelError::InvalidInput(m) => AppError::bad_request(m),
            HypixelError::RateLimited { retry_after_secs } => AppError::new(StatusCode::SERVICE_UNAVAILABLE, "HYPIXEL_UNAVAILABLE", format!("Hypixel API rate limit reached; retry in {retry_after_secs}s.")),
            HypixelError::CircuitOpen { retry_in_secs } => AppError::new(StatusCode::SERVICE_UNAVAILABLE, "HYPIXEL_UNAVAILABLE", format!("Hypixel API temporarily unavailable; retry in {retry_in_secs}s.")),
            other => AppError::new(StatusCode::BAD_GATEWAY, "HYPIXEL_UNAVAILABLE", format!("Hypixel API temporarily unavailable ({other}).")),
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let body = ApiError { code: self.code.to_string(), message: self.message, last_valid_snapshot_age_seconds: self.last_valid_snapshot_age_seconds };
        (self.status, Json(body)).into_response()
    }
}
