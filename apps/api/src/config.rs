//! Configuration from environment variables (see `.env.example`). Secrets (Hypixel key,
//! Anthropic key, DeepL key, database password) are only ever read from the environment /
//! secret manager - never from files committed to the repository.

use std::net::SocketAddr;
use std::path::PathBuf;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CollectorMode {
    Embedded,
    Disabled,
}

#[derive(Debug, Clone)]
pub struct Config {
    pub bind: SocketAddr,
    pub root: PathBuf,
    pub database_url: Option<String>,
    pub redis_url: Option<String>,
    pub hypixel_api_key: Option<String>,
    pub public_web_url: String,
    pub secure_cookies: bool,
    pub trust_proxy: bool,
    pub collector: CollectorMode,
    pub data_refresh_hours: u64,
    pub warm_start_days: i64,
    pub skip_data_sync: bool,
    pub vapid_private_key: Option<String>,
    /// Base64 32-byte key for encrypting user API keys (generated into data/cache when unset).
    pub secret_key: Option<String>,
    pub vapid_subject: String,
    pub web_push: bool,
    /// Development only: serve the anonymised fixture profile for the name "SampleUser".
    pub dev_sample_profile: bool,
    /// Launcher update feed: installer + latest.json + latest.json.sig (from `gradlew :launcher:launcherRelease`).
    pub launcher_releases_dir: PathBuf,
}

fn env(key: &str) -> Option<String> {
    std::env::var(key).ok().map(|v| v.trim().to_string()).filter(|v| !v.is_empty())
}

impl Config {
    pub fn from_env() -> anyhow::Result<Config> {
        let root = match env("SKYPILOT_ROOT") {
            Some(r) => PathBuf::from(r),
            None => skypilot_data::find_repo_root(&std::env::current_dir()?).ok_or_else(|| anyhow::anyhow!("cannot locate repository root; set SKYPILOT_ROOT"))?,
        };
        let public_web_url = env("PUBLIC_WEB_URL").unwrap_or_else(|| "http://localhost:3000".into());
        let launcher_releases_dir = env("LAUNCHER_RELEASES_DIR").map(PathBuf::from).unwrap_or_else(|| root.join("data").join("launcher-releases"));
        Ok(Config {
            bind: env("API_BIND").unwrap_or_else(|| "0.0.0.0:8080".into()).parse()?,
            root,
            database_url: env("DATABASE_URL"),
            redis_url: env("REDIS_URL"),
            hypixel_api_key: env("HYPIXEL_API_KEY"),
            secure_cookies: env("COOKIE_SECURE").map(|v| v == "1" || v == "true").unwrap_or_else(|| public_web_url.starts_with("https://")),
            trust_proxy: env("TRUST_PROXY").is_some_and(|v| v == "1" || v == "true"),
            collector: match env("COLLECTOR_MODE").as_deref() {
                Some("disabled") | Some("off") => CollectorMode::Disabled,
                _ => CollectorMode::Embedded,
            },
            data_refresh_hours: env("DATA_REFRESH_HOURS").and_then(|v| v.parse().ok()).unwrap_or(24),
            warm_start_days: env("WARM_START_DAYS").and_then(|v| v.parse().ok()).unwrap_or(7),
            skip_data_sync: env("SKIP_DATA_SYNC").is_some_and(|v| v == "1" || v == "true"),
            vapid_private_key: env("VAPID_PRIVATE_KEY"),
            secret_key: env("SKYPILOT_SECRET_KEY"),
            vapid_subject: env("VAPID_SUBJECT").unwrap_or_else(|| public_web_url.clone()),
            web_push: !env("WEB_PUSH").is_some_and(|v| v == "0" || v == "false"),
            dev_sample_profile: env("SKYPILOT_DEV_SAMPLE_PROFILE").is_some_and(|v| v == "1" || v == "true"),
            launcher_releases_dir,
            public_web_url,
        })
    }
}
