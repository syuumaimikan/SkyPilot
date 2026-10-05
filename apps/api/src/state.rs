use std::io::{Read, Write};
use std::net::IpAddr;
use std::num::NonZeroU32;
use std::sync::Arc;
use std::time::Duration;

use governor::{DefaultKeyedRateLimiter, Quota, RateLimiter};
use moka::future::Cache;
use parking_lot::RwLock;
use serde_json::Value;
use skypilot_collector::Health;
use skypilot_data::Registry;
use skypilot_hypixel::HypixelClient;
use skypilot_pricing::MarketEngine;
use skypilot_profile::{AnalyzerInput, Inventories, analyze};
use skypilot_protocol::goals::{AnalyzeResponse, GoalPriority};
use skypilot_protocol::profile::{PlayerAnalysis, PlayerRef};
use skypilot_reco::{Ctx, Lang, Planner};
use skypilot_translation::Translator;
use skypilot_translation::anthropic::AnthropicClient;
use uuid::Uuid;

use crate::bus::Bus;
use crate::config::Config;
use crate::push::WebPush;
use crate::error::{ApiResult, AppError};
use crate::store::{RawProfile, Store, UserRow};

pub struct Limiters {
    pub general: DefaultKeyedRateLimiter<IpAddr>,
    pub analyze: DefaultKeyedRateLimiter<IpAddr>,
    pub auth: DefaultKeyedRateLimiter<IpAddr>,
    pub translate: DefaultKeyedRateLimiter<String>,
}

impl Limiters {
    pub fn new() -> Self {
        let q = |n: u32| Quota::per_minute(NonZeroU32::new(n).unwrap());
        Limiters {
            general: RateLimiter::keyed(q(240)),
            analyze: RateLimiter::keyed(q(6)),
            auth: RateLimiter::keyed(q(10)),
            translate: RateLimiter::keyed(q(240)),
        }
    }
}

impl Default for Limiters {
    fn default() -> Self {
        Self::new()
    }
}

/// Analysis + decoded inventories of a user's last analysed profile.
pub struct LoadedPlayer {
    pub analysis: PlayerAnalysis,
    pub inv: Inventories,
    pub fetched_at_ms: i64,
}

pub struct AppState {
    pub config: Config,
    registry: RwLock<Arc<Registry>>,
    pub market: Arc<MarketEngine>,
    pub hypixel: HypixelClient,
    pub store: Arc<dyn Store>,
    pub planner: Planner,
    pub translator: Translator,
    pub llm: Option<AnthropicClient>,
    pub bus: Bus,
    pub push: Option<WebPush>,
    pub collector_health: RwLock<Option<Arc<Health>>>,
    pub limiters: Limiters,
    pub players: Cache<Uuid, Arc<LoadedPlayer>>,
    pub secrets: crate::secrets::SecretBox,
    /// Each user's own translation provider choice (built from their encrypted settings).
    pub user_translation: Cache<Uuid, Arc<UserTranslation>>,
}

/// Which translator a user's chat lines go through.
pub enum UserTranslation {
    /// The server's provider from the environment (default).
    Server,
    /// Dictionary only - nothing is sent to an external service.
    DictionaryOnly,
    /// The user's own provider with their own API key.
    Own(Arc<dyn skypilot_translation::providers::Provider>),
}

pub fn gzip(bytes: &[u8]) -> Vec<u8> {
    let mut e = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
    let _ = e.write_all(bytes);
    e.finish().unwrap_or_default()
}

pub fn gunzip(bytes: &[u8]) -> anyhow::Result<Vec<u8>> {
    let mut out = Vec::new();
    flate2::read::GzDecoder::new(bytes).take(64 * 1024 * 1024).read_to_end(&mut out)?;
    Ok(out)
}

impl AppState {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        config: Config,
        registry: Arc<Registry>,
        market: Arc<MarketEngine>,
        hypixel: HypixelClient,
        store: Arc<dyn Store>,
        translator: Translator,
        llm: Option<AnthropicClient>,
        bus: Bus,
        push: Option<WebPush>,
        secrets: crate::secrets::SecretBox,
    ) -> Self {
        AppState {
            config,
            registry: RwLock::new(registry),
            market,
            hypixel,
            store,
            planner: Planner::default(),
            translator,
            llm,
            bus,
            push,
            collector_health: RwLock::new(None),
            limiters: Limiters::new(),
            players: Cache::builder().max_capacity(512).time_to_live(Duration::from_secs(1800)).build(),
            secrets,
            user_translation: Cache::builder().max_capacity(2048).time_to_live(Duration::from_secs(600)).build(),
        }
    }

    pub fn registry(&self) -> Arc<Registry> {
        self.registry.read().clone()
    }

    pub fn set_registry(&self, r: Arc<Registry>) {
        *self.registry.write() = r.clone();
        self.market.set_registry(r);
    }

    pub fn now_ms() -> i64 {
        chrono::Utc::now().timestamp_millis()
    }

    /// Analyse a stored raw profile (no Hypixel call).
    pub fn analyze_raw(&self, raw: &RawProfile) -> ApiResult<LoadedPlayer> {
        let registry = self.registry();
        let profiles: Value = serde_json::from_slice(&gunzip(&raw.profiles)?).map_err(AppError::internal)?;
        let museum: Option<Value> = raw.museum.as_ref().and_then(|m| gunzip(m).ok()).and_then(|b| serde_json::from_slice(&b).ok());
        let garden: Option<Value> = raw.garden.as_ref().and_then(|m| gunzip(m).ok()).and_then(|b| serde_json::from_slice(&b).ok());
        let input = AnalyzerInput {
            player: PlayerRef { username: raw.username.clone(), uuid: raw.minecraft_uuid.clone() },
            profiles: &profiles,
            profile_id: raw.profile_id.as_deref(),
            museum: museum.as_ref(),
            garden: garden.as_ref(),
            fetched_at_ms: raw.fetched_at_ms,
            now_ms: Self::now_ms(),
        };
        let (analysis, inv) = analyze(&input, &registry, self.market.as_ref()).map_err(|e| AppError::bad_request(e.to_string()))?;
        Ok(LoadedPlayer { analysis, inv, fetched_at_ms: raw.fetched_at_ms })
    }

    /// The user's last analysed player (memory cache -> stored raw profile).
    pub async fn player(&self, user_id: Uuid) -> ApiResult<Arc<LoadedPlayer>> {
        if let Some(p) = self.players.get(&user_id).await {
            return Ok(p);
        }
        let raw = self.store.load_raw_profile(user_id).await?.ok_or_else(|| AppError::conflict("NO_ANALYSIS", "Run an analysis first (username + goal)."))?;
        let loaded = Arc::new(tokio::task::block_in_place(|| self.analyze_raw(&raw))?);
        self.players.insert(user_id, loaded.clone()).await;
        Ok(loaded)
    }

    pub fn ctx<'a>(&'a self, registry: &'a Registry, p: &'a LoadedPlayer, user: &UserRow, lang: Lang) -> Ctx<'a> {
        Ctx {
            registry,
            oracle: self.market.as_ref(),
            market: Some(self.market.as_ref()),
            analysis: &p.analysis,
            inv: &p.inv,
            play_style: user.play_style,
            budget: user.budget,
            now_ms: Self::now_ms(),
            lang,
        }
    }

    pub async fn goal_texts(&self, user_id: Uuid) -> Vec<(String, GoalPriority)> {
        self.store.load_goals(user_id).await.unwrap_or_default().into_iter().map(|g| (g.text, g.priority)).collect()
    }

    pub fn publish(&self, kind: &str, payload: &impl serde::Serialize) {
        self.bus.publish(kind, payload);
    }

    pub fn lang_of(user: &UserRow, q: Option<&str>) -> Lang {
        Lang::parse(q.or(Some(user.lang.as_str())))
    }

    pub fn last_response_age(resp: &AnalyzeResponse) -> i64 {
        ((Self::now_ms() - resp.analysis.fetched.updated_at_ms) / 1000).max(0)
    }
}
