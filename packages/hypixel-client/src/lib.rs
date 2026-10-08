//! Hypixel Public API v2 + Mojang profile lookup client.
//!
//! Policy-driven design (https://developer.hypixel.net/policies):
//! * one key, never rotated to evade limits; the key is only ever read from server config;
//! * aggressive caching of every response;
//! * `RateLimit-*` headers are honoured; a 429 stops keyed traffic until the reset time
//!   (no hammering) and is surfaced to callers;
//! * exponential backoff with jitter for transient 5xx/network errors;
//! * a circuit breaker so an outage does not cascade into the rest of SkyPilot.

pub mod breaker;
pub mod model;

use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use bytes::Bytes;
use moka::future::Cache;
use parking_lot::Mutex;
use rand::Rng;
use serde::de::DeserializeOwned;
use thiserror::Error;

pub use breaker::{BreakerState, CircuitBreaker};
pub use model::*;

#[derive(Debug, Error, Clone, PartialEq)]
pub enum HypixelError {
    #[error("Hypixel API key is not configured on this server")]
    MissingKey,
    #[error("Hypixel rejected the API key")]
    InvalidKey,
    #[error("rate limited by Hypixel; retry in {retry_after_secs}s")]
    RateLimited { retry_after_secs: u64 },
    #[error("not found")]
    NotFound,
    #[error("Hypixel API temporarily unavailable (circuit open, retry in {retry_in_secs}s)")]
    CircuitOpen { retry_in_secs: u64 },
    #[error("upstream error: HTTP {status}: {message}")]
    Upstream { status: u16, message: String },
    #[error("network error: {0}")]
    Network(String),
    #[error("could not decode response: {0}")]
    Decode(String),
    #[error("invalid input: {0}")]
    InvalidInput(String),
}

impl HypixelError {
    /// Errors that indicate Hypixel itself is unhealthy (feed the circuit breaker).
    fn is_upstream_failure(&self) -> bool {
        matches!(self, HypixelError::Network(_) | HypixelError::Upstream { status: 500..=599, .. })
    }
}

#[derive(Debug, Clone)]
pub struct ClientConfig {
    pub api_key: Option<String>,
    pub hypixel_base: String,
    pub mojang_base: String,
    pub user_agent: String,
    pub request_timeout: Duration,
    pub max_retries: u32,
    /// Minimum spacing between keyed requests (local politeness limiter).
    pub min_keyed_interval: Duration,
}

impl Default for ClientConfig {
    fn default() -> Self {
        ClientConfig {
            api_key: None,
            hypixel_base: "https://api.hypixel.net/v2".into(),
            mojang_base: "https://api.minecraftservices.com/minecraft/profile/lookup/name".into(),
            user_agent: concat!("SkyPilot/", env!("CARGO_PKG_VERSION")).into(),
            request_timeout: Duration::from_secs(20),
            max_retries: 3,
            min_keyed_interval: Duration::from_millis(250),
        }
    }
}

#[derive(Debug, Default)]
pub struct Metrics {
    pub requests: AtomicU64,
    pub cache_hits: AtomicU64,
    pub errors: AtomicU64,
    pub rate_limited: AtomicU64,
    pub retries: AtomicU64,
    pub circuit_rejections: AtomicU64,
}

impl Metrics {
    pub fn snapshot(&self) -> Vec<(&'static str, u64)> {
        vec![
            ("hypixel_requests", self.requests.load(Ordering::Relaxed)),
            ("hypixel_cache_hits", self.cache_hits.load(Ordering::Relaxed)),
            ("hypixel_errors", self.errors.load(Ordering::Relaxed)),
            ("hypixel_rate_limited", self.rate_limited.load(Ordering::Relaxed)),
            ("hypixel_retries", self.retries.load(Ordering::Relaxed)),
            ("hypixel_circuit_rejections", self.circuit_rejections.load(Ordering::Relaxed)),
        ]
    }
}

#[derive(Debug, Default)]
struct KeyBudget {
    /// When set, keyed requests are refused until this instant (after a 429 / exhausted budget).
    blocked_until: Option<Instant>,
    remaining: Option<i64>,
    limit: Option<i64>,
    last_request: Option<Instant>,
}

#[derive(Clone)]
pub struct HypixelClient {
    inner: Arc<Inner>,
}

struct Inner {
    http: reqwest::Client,
    config: ClientConfig,
    cache: Cache<String, Bytes>,
    budget: Mutex<KeyBudget>,
    pub metrics: Metrics,
    breaker: CircuitBreaker,
}

/// Cache TTLs per endpoint family.
fn ttl_for(path: &str) -> Duration {
    match path {
        p if p.starts_with("skyblock/auctions_ended") => Duration::from_secs(30),
        p if p.starts_with("skyblock/auctions") => Duration::from_secs(30),
        p if p.starts_with("skyblock/bazaar") => Duration::from_secs(20),
        p if p.starts_with("skyblock/profiles") || p.starts_with("skyblock/profile") => Duration::from_secs(120),
        p if p.starts_with("skyblock/museum") || p.starts_with("skyblock/garden") => Duration::from_secs(300),
        p if p.starts_with("resources/skyblock/election") => Duration::from_secs(300),
        p if p.starts_with("resources/") => Duration::from_secs(3600),
        p if p.starts_with("mojang:") => Duration::from_secs(86_400),
        _ => Duration::from_secs(60),
    }
}

pub fn is_valid_username(name: &str) -> bool {
    !name.is_empty() && name.len() <= 16 && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}

pub fn is_valid_uuid(id: &str) -> bool {
    let s: String = id.chars().filter(|c| *c != '-').collect();
    s.len() == 32 && s.chars().all(|c| c.is_ascii_hexdigit())
}

impl HypixelClient {
    pub fn new(config: ClientConfig) -> Result<Self, HypixelError> {
        let http = reqwest::Client::builder()
            .user_agent(config.user_agent.clone())
            .timeout(config.request_timeout)
            .gzip(true)
            .build()
            .map_err(|e| HypixelError::Network(e.to_string()))?;
        Ok(HypixelClient {
            inner: Arc::new(Inner {
                http,
                cache: Cache::builder()
                    .max_capacity(256 * 1024 * 1024)
                    .weigher(|_k: &String, v: &Bytes| v.len().min(u32::MAX as usize) as u32)
                    .expire_after(TtlExpiry)
                    .build(),
                budget: Mutex::new(KeyBudget::default()),
                metrics: Metrics::default(),
                breaker: CircuitBreaker::new(5, Duration::from_secs(30)),
                config,
            }),
        })
    }

    pub fn has_key(&self) -> bool {
        self.inner.config.api_key.as_deref().is_some_and(|k| !k.trim().is_empty())
    }

    pub fn metrics(&self) -> &Metrics {
        &self.inner.metrics
    }

    pub fn breaker_state(&self) -> BreakerState {
        self.inner.breaker.state()
    }

    /// Key budget as (remaining, limit) from the last response headers.
    pub fn key_budget(&self) -> (Option<i64>, Option<i64>) {
        let b = self.inner.budget.lock();
        (b.remaining, b.limit)
    }

    async fn get_bytes(&self, path: &str, keyed: bool) -> Result<Bytes, HypixelError> {
        if let Some(hit) = self.inner.cache.get(path).await {
            self.inner.metrics.cache_hits.fetch_add(1, Ordering::Relaxed);
            return Ok(hit);
        }
        let result = self.fetch_with_retry(path, keyed).await;
        match &result {
            Ok(bytes) => {
                self.inner.cache.insert(path.to_string(), bytes.clone()).await;
            }
            Err(_) => {
                self.inner.metrics.errors.fetch_add(1, Ordering::Relaxed);
            }
        }
        result
    }

    async fn fetch_with_retry(&self, path: &str, keyed: bool) -> Result<Bytes, HypixelError> {
        let mut attempt = 0;
        loop {
            if let Err(retry_in) = self.inner.breaker.allow() {
                self.inner.metrics.circuit_rejections.fetch_add(1, Ordering::Relaxed);
                return Err(HypixelError::CircuitOpen { retry_in_secs: retry_in.as_secs().max(1) });
            }
            let res = self.fetch_once(path, keyed).await;
            match &res {
                Ok(_) => {
                    self.inner.breaker.record_success();
                    return res;
                }
                Err(e) if e.is_upstream_failure() => {
                    self.inner.breaker.record_failure();
                    if attempt >= self.inner.config.max_retries {
                        return res;
                    }
                    attempt += 1;
                    self.inner.metrics.retries.fetch_add(1, Ordering::Relaxed);
                    let base = 400u64 * 2u64.pow(attempt);
                    let jitter = rand::thread_rng().gen_range(0..=base / 2);
                    tracing::warn!(path, attempt, error = %e, "retrying Hypixel request");
                    tokio::time::sleep(Duration::from_millis(base + jitter)).await;
                }
                Err(_) => return res,
            }
        }
    }

    async fn fetch_once(&self, path: &str, keyed: bool) -> Result<Bytes, HypixelError> {
        let url = if let Some(mojang) = path.strip_prefix("mojang:") {
            format!("{}/{}", self.inner.config.mojang_base, mojang)
        } else {
            format!("{}/{}", self.inner.config.hypixel_base, path)
        };
        let mut req = self.inner.http.get(&url);
        if keyed {
            let key = self.inner.config.api_key.clone().filter(|k| !k.trim().is_empty()).ok_or(HypixelError::MissingKey)?;
            // politeness + respect previous throttling
            let wait = {
                let mut b = self.inner.budget.lock();
                let now = Instant::now();
                if let Some(until) = b.blocked_until {
                    if until > now {
                        let secs = (until - now).as_secs().max(1);
                        self.inner.metrics.rate_limited.fetch_add(1, Ordering::Relaxed);
                        return Err(HypixelError::RateLimited { retry_after_secs: secs });
                    }
                    b.blocked_until = None;
                }
                let wait = b
                    .last_request
                    .map(|t| self.inner.config.min_keyed_interval.saturating_sub(now.duration_since(t)))
                    .unwrap_or_default();
                b.last_request = Some(now + wait);
                wait
            };
            if !wait.is_zero() {
                tokio::time::sleep(wait).await;
            }
            req = req.header("API-Key", key);
        }
        self.inner.metrics.requests.fetch_add(1, Ordering::Relaxed);
        let resp = req.send().await.map_err(|e| HypixelError::Network(e.to_string()))?;
        let status = resp.status();
        let header_i64 = |name: &str| resp.headers().get(name).and_then(|v| v.to_str().ok()).and_then(|v| v.trim().parse::<i64>().ok());
        let remaining = header_i64("RateLimit-Remaining");
        let limit = header_i64("RateLimit-Limit");
        let reset = header_i64("RateLimit-Reset").or_else(|| header_i64("Retry-After"));
        if keyed {
            let mut b = self.inner.budget.lock();
            if remaining.is_some() {
                b.remaining = remaining;
            }
            if limit.is_some() {
                b.limit = limit;
            }
            if remaining == Some(0) {
                b.blocked_until = Some(Instant::now() + Duration::from_secs(reset.unwrap_or(60).clamp(1, 600) as u64));
            }
        }
        match status.as_u16() {
            200 => resp.bytes().await.map_err(|e| HypixelError::Network(e.to_string())),
            403 => Err(HypixelError::InvalidKey),
            404 | 204 => Err(HypixelError::NotFound),
            429 => {
                let secs = reset.unwrap_or(60).clamp(1, 600) as u64;
                self.inner.metrics.rate_limited.fetch_add(1, Ordering::Relaxed);
                if keyed {
                    self.inner.budget.lock().blocked_until = Some(Instant::now() + Duration::from_secs(secs));
                }
                Err(HypixelError::RateLimited { retry_after_secs: secs })
            }
            s => {
                let body = resp.text().await.unwrap_or_default();
                let message = serde_json::from_str::<serde_json::Value>(&body)
                    .ok()
                    .and_then(|v| v.get("cause").and_then(|c| c.as_str()).map(str::to_string))
                    .unwrap_or_else(|| body.chars().take(200).collect());
                Err(HypixelError::Upstream { status: s, message })
            }
        }
    }

    async fn get_json<T: DeserializeOwned>(&self, path: &str, keyed: bool) -> Result<T, HypixelError> {
        let bytes = self.get_bytes(path, keyed).await?;
        let parsed: T = serde_json::from_slice(&bytes).map_err(|e| HypixelError::Decode(e.to_string()))?;
        Ok(parsed)
    }

    /// Bypass the response cache (used by the collector, which tracks `lastUpdated` itself).
    pub async fn invalidate(&self, path: &str) {
        self.inner.cache.invalidate(path).await;
    }

    // ------------------------------------------------------------- Mojang

    /// Username -> (uuid without dashes, canonical name).
    pub async fn resolve_username(&self, username: &str) -> Result<(String, String), HypixelError> {
        let name = username.trim();
        if !is_valid_username(name) {
            return Err(HypixelError::InvalidInput("Minecraft usernames are 1-16 characters of A-Z, 0-9 and _".into()));
        }
        let v: serde_json::Value = self.get_json(&format!("mojang:{}", name.to_ascii_lowercase()), false).await?;
        let id = v.get("id").and_then(|x| x.as_str()).ok_or(HypixelError::NotFound)?;
        let canonical = v.get("name").and_then(|x| x.as_str()).unwrap_or(name);
        Ok((id.replace('-', ""), canonical.to_string()))
    }

    // ------------------------------------------------------------- keyed endpoints

    pub async fn profiles(&self, uuid: &str) -> Result<serde_json::Value, HypixelError> {
        if !is_valid_uuid(uuid) {
            return Err(HypixelError::InvalidInput("invalid uuid".into()));
        }
        let v: serde_json::Value = self.get_json(&format!("skyblock/profiles?uuid={}", uuid.replace('-', "")), true).await?;
        if v.get("success").and_then(|s| s.as_bool()) != Some(true) {
            return Err(HypixelError::Upstream { status: 200, message: v.get("cause").and_then(|c| c.as_str()).unwrap_or("success=false").into() });
        }
        Ok(v)
    }

    pub async fn player_auctions(&self, uuid: &str) -> Result<serde_json::Value, HypixelError> {
        if !is_valid_uuid(uuid) { return Err(HypixelError::InvalidInput("invalid uuid".into())); }
        let v: serde_json::Value = self.get_json(&format!("skyblock/auction?player={}",uuid.replace('-',"")),true).await?;
        if v.get("success").and_then(|s|s.as_bool())!=Some(true) { return Err(HypixelError::Upstream{status:200,message:"auction API unavailable".into()}); }
        Ok(v)
    }

    pub async fn museum(&self, profile_id: &str) -> Result<serde_json::Value, HypixelError> {
        if !is_valid_uuid(profile_id) {
            return Err(HypixelError::InvalidInput("invalid profile id".into()));
        }
        self.get_json(&format!("skyblock/museum?profile={}", profile_id.replace('-', "")), true).await
    }

    pub async fn garden(&self, profile_id: &str) -> Result<serde_json::Value, HypixelError> {
        if !is_valid_uuid(profile_id) {
            return Err(HypixelError::InvalidInput("invalid profile id".into()));
        }
        self.get_json(&format!("skyblock/garden?profile={}", profile_id.replace('-', "")), true).await
    }

    // ------------------------------------------------------------- keyless endpoints

    pub async fn auctions_page(&self, page: u32, fresh: bool) -> Result<AuctionPage, HypixelError> {
        let path = format!("skyblock/auctions?page={page}");
        if fresh {
            self.invalidate(&path).await;
        }
        self.get_json(&path, false).await
    }

    pub async fn auctions_ended(&self, fresh: bool) -> Result<EndedAuctions, HypixelError> {
        let path = "skyblock/auctions_ended".to_string();
        if fresh {
            self.invalidate(&path).await;
        }
        self.get_json(&path, false).await
    }

    pub async fn bazaar(&self, fresh: bool) -> Result<BazaarResponse, HypixelError> {
        let path = "skyblock/bazaar".to_string();
        if fresh {
            self.invalidate(&path).await;
        }
        self.get_json(&path, false).await
    }

    pub async fn election(&self) -> Result<serde_json::Value, HypixelError> {
        self.get_json("resources/skyblock/election", false).await
    }

    pub async fn firesales(&self) -> Result<serde_json::Value, HypixelError> {
        self.get_json("skyblock/firesales", false).await
    }

    pub async fn news(&self) -> Result<serde_json::Value, HypixelError> {
        self.get_json("skyblock/news", false).await
    }
}

/// Per-entry TTL based on the endpoint path.
struct TtlExpiry;

impl moka::Expiry<String, Bytes> for TtlExpiry {
    fn expire_after_create(&self, key: &String, _value: &Bytes, _created_at: std::time::Instant) -> Option<Duration> {
        Some(ttl_for(key))
    }
}

/// Milliseconds since epoch.
pub fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}
