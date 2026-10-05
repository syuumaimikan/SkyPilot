//! Minimal Anthropic Messages API client over raw HTTP (Rust has no official SDK).
//!
//! Shape per the Claude API reference: `POST /v1/messages` with `x-api-key` and
//! `anthropic-version: 2023-06-01`. Defaults to `claude-opus-5-5` (override with
//! `ANTHROPIC_MODEL`), `output_config.effort = "low"` for these short, simple tasks, and
//! server-side refusal fallbacks (`fallbacks: "default"`, beta
//! `server-side-fallback-2026-07-01`) so a classifier decline is retried on Anthropic's
//! recommended fallback model instead of failing. `stop_reason` is checked before reading
//! content; only `text` blocks are used (thinking blocks are ignored).

use std::time::Duration;

use serde_json::{Value, json};
use thiserror::Error;

pub const DEFAULT_MODEL: &str = "claude-opus-5-5";
const API_URL: &str = "https://api.anthropic.com/v1/messages";

#[derive(Debug, Error)]
pub enum LlmError {
    #[error("HTTP {status}: {message}")]
    Http { status: u16, message: String },
    #[error("request failed: {0}")]
    Network(String),
    #[error("model declined the request ({category})")]
    Refusal { category: String },
    #[error("response truncated (max_tokens)")]
    Truncated,
    #[error("unexpected response: {0}")]
    Decode(String),
}

#[derive(Clone)]
pub struct AnthropicClient {
    http: reqwest::Client,
    api_key: String,
    pub model: String,
    pub fallbacks: bool,
    base_url: String,
}

impl AnthropicClient {
    /// Build from `ANTHROPIC_API_KEY` (+ optional `ANTHROPIC_MODEL`, `ANTHROPIC_FALLBACKS=0`).
    pub fn from_env() -> Option<Self> {
        let key = std::env::var("ANTHROPIC_API_KEY").ok().filter(|k| !k.trim().is_empty())?;
        let model = std::env::var("ANTHROPIC_MODEL").ok().filter(|m| !m.trim().is_empty()).unwrap_or_else(|| DEFAULT_MODEL.to_string());
        let fallbacks = std::env::var("ANTHROPIC_FALLBACKS").map(|v| v != "0" && v.to_lowercase() != "false").unwrap_or(true);
        Some(Self::new(key, model, fallbacks))
    }

    pub fn new(api_key: String, model: String, fallbacks: bool) -> Self {
        AnthropicClient {
            http: reqwest::Client::builder().timeout(Duration::from_secs(60)).build().expect("reqwest client"),
            api_key,
            model,
            fallbacks,
            base_url: API_URL.to_string(),
        }
    }

    pub fn with_base_url(mut self, url: impl Into<String>) -> Self {
        self.base_url = url.into();
        self
    }

    /// Single-turn request; returns the concatenated text blocks.
    pub async fn complete(&self, system: &str, user: &str, max_tokens: u32, effort: &str, timeout: Duration) -> Result<String, LlmError> {
        let mut body = json!({
            "model": self.model,
            "max_tokens": max_tokens,
            "system": system,
            "output_config": {"effort": effort},
            "messages": [{"role": "user", "content": user}],
        });
        let mut req = self
            .http
            .post(&self.base_url)
            .timeout(timeout)
            .header("x-api-key", &self.api_key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json");
        if self.fallbacks {
            body["fallbacks"] = json!("default");
            req = req.header("anthropic-beta", "server-side-fallback-2026-07-01");
        }
        let resp = req.json(&body).send().await.map_err(|e| LlmError::Network(e.to_string()))?;
        let status = resp.status().as_u16();
        let v: Value = resp.json().await.map_err(|e| LlmError::Decode(e.to_string()))?;
        if status != 200 {
            let message = v.pointer("/error/message").and_then(Value::as_str).unwrap_or("error").to_string();
            return Err(LlmError::Http { status, message });
        }
        match v.get("stop_reason").and_then(Value::as_str) {
            Some("refusal") => {
                let category = v.pointer("/stop_details/category").and_then(Value::as_str).unwrap_or("unspecified").to_string();
                return Err(LlmError::Refusal { category });
            }
            Some("max_tokens") => return Err(LlmError::Truncated),
            _ => {}
        }
        let text: String = v
            .get("content")
            .and_then(Value::as_array)
            .map(|blocks| blocks.iter().filter(|b| b.get("type").and_then(Value::as_str) == Some("text")).filter_map(|b| b.get("text").and_then(Value::as_str)).collect::<Vec<_>>().join(""))
            .unwrap_or_default();
        if text.trim().is_empty() {
            return Err(LlmError::Decode("no text content".into()));
        }
        Ok(text)
    }
}
