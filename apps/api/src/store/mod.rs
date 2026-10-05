//! Persistence. `PgStore` (PostgreSQL/TimescaleDB via SQLx, migrations in /migrations) is the
//! production backend; `MemoryStore` is a real in-process implementation used when
//! DATABASE_URL is unset (development/tests) - data then lives only for the process lifetime.

pub mod memory;
pub mod pg;

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use skypilot_hypixel::BazaarResponse;
use skypilot_pricing::normalize::Sale;
use skypilot_pricing::state::{BazaarPoint, CandidateRecord};
use skypilot_protocol::goals::{AnalyzeResponse, PlayStyle, SavedGoal};
use skypilot_protocol::market::{NotificationRule, PricePoint};
use skypilot_protocol::profile::ProgressSnapshot;
use uuid::Uuid;

pub type StoreResult<T> = anyhow::Result<T>;

#[derive(Debug, Clone)]
pub struct UserRow {
    pub id: Uuid,
    pub login: Option<String>,
    pub password_hash: Option<String>,
    pub minecraft_uuid: Option<String>,
    pub minecraft_name: Option<String>,
    pub profile_id: Option<String>,
    pub play_style: PlayStyle,
    pub budget: Option<f64>,
    pub lang: String,
}

#[derive(Debug, Clone)]
pub struct SessionRow {
    pub user_id: Uuid,
    pub csrf_token: String,
    pub expires_at: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct DeviceCodeRow {
    pub device_code_hash: String,
    pub user_code: String,
    pub device_name: String,
    pub expires_at: DateTime<Utc>,
    pub approved_user_id: Option<Uuid>,
    pub denied: bool,
    pub last_poll_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone)]
pub struct DeviceTokenRow {
    pub id: String,
    pub user_id: Uuid,
    pub name: String,
    pub created_at: DateTime<Utc>,
    pub last_used_at: Option<DateTime<Utc>>,
    pub revoked: bool,
}

#[derive(Debug, Clone)]
pub struct PushSubscription {
    pub endpoint: String,
    pub p256dh: String,
    pub auth: String,
}

/// Raw Hypixel payloads of the last analysed profile (gzip-compressed JSON).
#[derive(Debug, Clone)]
pub struct RawProfile {
    pub minecraft_uuid: String,
    pub username: String,
    pub profile_id: Option<String>,
    pub fetched_at_ms: i64,
    pub profiles: Vec<u8>,
    pub museum: Option<Vec<u8>>,
    pub garden: Option<Vec<u8>>,
}

#[async_trait]
pub trait Store: Send + Sync {
    fn kind(&self) -> &'static str;

    // users & sessions
    async fn create_user(&self) -> StoreResult<Uuid>;
    async fn get_user(&self, id: Uuid) -> StoreResult<Option<UserRow>>;
    async fn find_user_by_login(&self, login: &str) -> StoreResult<Option<UserRow>>;
    async fn set_credentials(&self, id: Uuid, login: &str, password_hash: &str) -> StoreResult<()>;
    async fn update_prefs(&self, id: Uuid, mc_uuid: Option<&str>, mc_name: Option<&str>, profile_id: Option<&str>, style: PlayStyle, budget: Option<f64>, lang: &str) -> StoreResult<()>;
    async fn create_session(&self, id_hash: &str, user_id: Uuid, csrf: &str, expires_at: DateTime<Utc>) -> StoreResult<()>;
    async fn get_session(&self, id_hash: &str) -> StoreResult<Option<SessionRow>>;
    async fn delete_session(&self, id_hash: &str) -> StoreResult<()>;

    // goals & analyses
    async fn save_goals(&self, user_id: Uuid, goals: &[SavedGoal]) -> StoreResult<()>;
    async fn load_goals(&self, user_id: Uuid) -> StoreResult<Vec<SavedGoal>>;
    async fn save_analysis(&self, user_id: Uuid, resp: &AnalyzeResponse, snapshot: &ProgressSnapshot) -> StoreResult<()>;
    async fn latest_analysis(&self, user_id: Uuid) -> StoreResult<Option<(AnalyzeResponse, i64)>>;
    async fn snapshots(&self, minecraft_uuid: &str, profile_id: &str) -> StoreResult<Vec<ProgressSnapshot>>;
    async fn save_raw_profile(&self, user_id: Uuid, raw: &RawProfile) -> StoreResult<()>;
    async fn load_raw_profile(&self, user_id: Uuid) -> StoreResult<Option<RawProfile>>;

    // device authorization
    async fn create_device_code(&self, row: &DeviceCodeRow) -> StoreResult<()>;
    async fn device_code_by_user_code(&self, user_code: &str) -> StoreResult<Option<DeviceCodeRow>>;
    async fn device_code(&self, hash: &str) -> StoreResult<Option<DeviceCodeRow>>;
    async fn set_device_code_decision(&self, user_code: &str, user_id: Option<Uuid>, denied: bool) -> StoreResult<()>;
    async fn touch_device_code(&self, hash: &str) -> StoreResult<()>;
    async fn delete_device_code(&self, hash: &str) -> StoreResult<()>;
    async fn create_device_token(&self, id: &str, token_hash: &str, user_id: Uuid, name: &str) -> StoreResult<()>;
    async fn device_token(&self, token_hash: &str) -> StoreResult<Option<DeviceTokenRow>>;
    async fn touch_device_token(&self, id: &str) -> StoreResult<()>;
    async fn list_devices(&self, user_id: Uuid) -> StoreResult<Vec<DeviceTokenRow>>;
    async fn revoke_device(&self, user_id: Uuid, id: &str) -> StoreResult<bool>;

    // market history
    async fn store_sales(&self, sales: &[Sale]) -> StoreResult<()>;
    async fn store_bazaar(&self, resp: &BazaarResponse) -> StoreResult<()>;
    async fn store_candidates(&self, c: &[CandidateRecord]) -> StoreResult<()>;
    async fn load_sales_since(&self, since_ms: i64) -> StoreResult<Vec<Sale>>;
    async fn load_bazaar_since(&self, since_ms: i64) -> StoreResult<Vec<(String, BazaarPoint)>>;
    async fn load_candidates_since(&self, since_ms: i64) -> StoreResult<Vec<CandidateRecord>>;
    async fn price_history(&self, item_id: &str, since_ms: i64, bucket_ms: i64) -> StoreResult<Vec<PricePoint>>;

    // notifications & mod sessions
    async fn notification_rules(&self, user_id: Uuid) -> StoreResult<Vec<NotificationRule>>;
    async fn all_notification_rules(&self) -> StoreResult<Vec<(Uuid, NotificationRule)>>;
    async fn save_notification_rule(&self, user_id: Uuid, rule: &NotificationRule) -> StoreResult<()>;
    async fn delete_notification_rule(&self, user_id: Uuid, id: &str) -> StoreResult<()>;
    async fn save_push_subscription(&self, user_id: Uuid, sub: &PushSubscription) -> StoreResult<()>;
    async fn push_subscriptions(&self, user_id: Uuid) -> StoreResult<Vec<PushSubscription>>;
    async fn delete_push_subscription(&self, endpoint: &str) -> StoreResult<()>;
    async fn save_session_stats(&self, user_id: Uuid, device_id: &str, started_ms: i64, ended_ms: i64, stats: &serde_json::Value) -> StoreResult<()>;
}

pub fn ms_to_dt(ms: i64) -> DateTime<Utc> {
    DateTime::<Utc>::from_timestamp_millis(ms).unwrap_or_else(Utc::now)
}

pub fn style_str(s: PlayStyle) -> &'static str {
    match s {
        PlayStyle::Fastest => "FASTEST",
        PlayStyle::Safe => "SAFE",
        PlayStyle::MoneyFocus => "MONEY_FOCUS",
        PlayStyle::Combat => "COMBAT",
        PlayStyle::Farming => "FARMING",
        PlayStyle::Mining => "MINING",
    }
}

pub fn parse_style(s: &str) -> PlayStyle {
    match s {
        "SAFE" => PlayStyle::Safe,
        "MONEY_FOCUS" => PlayStyle::MoneyFocus,
        "COMBAT" => PlayStyle::Combat,
        "FARMING" => PlayStyle::Farming,
        "MINING" => PlayStyle::Mining,
        _ => PlayStyle::Fastest,
    }
}
