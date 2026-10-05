//! SkyPilot API server.
//!
//!   skypilot-api            serve (default)
//!   skypilot-api migrate    run database migrations and exit
//!   skypilot-api sync-data  download Hypixel resources + NEU-REPO into data/cache and exit
//!   skypilot-api vapid-keys print a new VAPID key pair for Web Push (VAPID_PRIVATE_KEY)
//!   skypilot-api healthcheck  exit 0 when the local server answers /api/health (containers)

mod auth;
mod bus;
mod config;
mod error;
mod jobs;
mod push;
mod routes;
mod secrets;
mod state;
mod store;

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use skypilot_data::{DataPaths, Registry};
use skypilot_hypixel::{ClientConfig, HypixelClient};
use skypilot_pricing::MarketEngine;
use skypilot_translation::{Dictionary, Translator};

use crate::config::{CollectorMode, Config};
use crate::state::AppState;
use crate::store::Store;

fn dictionary(registry: &Registry) -> Dictionary {
    let t = &registry.curated.translation;
    Dictionary::new(
        t.version.clone(),
        registry.protected_terms(),
        t.phrases.iter().map(|(k, v)| (k.clone(), v.clone())).collect(),
        t.patterns.iter().map(|p| (p.regex.clone(), p.template.clone())).collect(),
    )
}

async fn open_store(config: &Config) -> anyhow::Result<Arc<dyn Store>> {
    match &config.database_url {
        Some(url) => {
            let pg = store::pg::PgStore::connect(url).await?;
            pg.migrate(&config.root.join("migrations")).await?;
            tracing::info!("connected to PostgreSQL; migrations applied");
            Ok(Arc::new(pg))
        }
        None => {
            tracing::warn!("DATABASE_URL not set: using in-memory store (data is lost on restart)");
            Ok(Arc::new(store::memory::MemoryStore::default()))
        }
    }
}

/// Reads `KEY=VALUE` lines from `<repo>/.env` (or `$SKYPILOT_ROOT/.env`) into the process
/// environment. Variables that are already set win, so real environment / secret-manager
/// values are never overridden.
fn load_dotenv() {
    let root = std::env::var("SKYPILOT_ROOT").map(std::path::PathBuf::from).ok().or_else(|| std::env::current_dir().ok().and_then(|d| skypilot_data::find_repo_root(&d)));
    let Some(text) = root.and_then(|r| std::fs::read_to_string(r.join(".env")).ok()) else { return };
    for line in text.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else { continue };
        let (key, value) = (key.trim(), value.trim().trim_matches('"'));
        if key.is_empty() || value.is_empty() || std::env::var_os(key).is_some() {
            continue;
        }
        // SAFETY: called at the very start of `main`, before the async runtime or any other
        // thread exists, so nothing can read the environment concurrently.
        unsafe { std::env::set_var(key, value) };
    }
}

fn main() -> anyhow::Result<()> {
    load_dotenv();
    tokio::runtime::Builder::new_multi_thread().enable_all().build()?.block_on(run())
}

async fn run() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info,tower_http=warn,sqlx=warn".into()))
        .init();
    if std::env::args().nth(1).as_deref() == Some("healthcheck") {
        let bind = std::env::var("API_BIND").unwrap_or_else(|_| "0.0.0.0:8080".into());
        let port = bind.rsplit(':').next().unwrap_or("8080");
        let ok = reqwest::Client::new().get(format!("http://127.0.0.1:{port}/api/health")).timeout(Duration::from_secs(3)).send().await.map(|r| r.status().is_success()).unwrap_or(false);
        std::process::exit(if ok { 0 } else { 1 });
    }
    let config = Config::from_env()?;
    let paths = DataPaths::from_root(&config.root);
    let cmd = std::env::args().nth(1).unwrap_or_else(|| "serve".into());

    if cmd == "sync-data" {
        skypilot_data::sync::sync_hypixel_resources(&paths).await?;
        skypilot_data::sync::sync_neu(&paths).await?;
        skypilot_data::sync::sync_skyhanni(&paths).await?;
        println!("data synced into {}", config.root.join("data/cache").display());
        return Ok(());
    }
    if cmd == "vapid-keys" {
        use base64::Engine;
        use web_push_native::jwt_simple::algorithms::{ECDSAP256PublicKeyLike, ES256KeyPair};
        let k = ES256KeyPair::generate();
        let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD;
        println!("VAPID_PRIVATE_KEY={}", b64.encode(k.to_bytes()));
        println!("# public key (served at /api/notifications/push/key): {}", b64.encode(k.public_key().public_key().to_bytes_uncompressed()));
        return Ok(());
    }
    if cmd == "migrate" {
        let url = config.database_url.as_deref().ok_or_else(|| anyhow::anyhow!("DATABASE_URL is required for migrate"))?;
        store::pg::PgStore::connect(url).await?.migrate(&config.root.join("migrations")).await?;
        println!("migrations applied");
        return Ok(());
    }

    if !config.skip_data_sync && skypilot_data::sync::needs_sync(&paths, Duration::from_secs(config.data_refresh_hours * 3600)) {
        tracing::info!("syncing Hypixel resources and NEU-REPO (first start or cache older than {}h)", config.data_refresh_hours);
        if let Err(e) = async {
            skypilot_data::sync::sync_hypixel_resources(&paths).await?;
            skypilot_data::sync::sync_neu(&paths).await
        }
        .await
        {
            tracing::warn!(error = %e, "data sync failed; using existing cache if present");
        }
    }
    if !config.skip_data_sync && skypilot_data::sync::needs_skyhanni(&paths) {
        if let Err(e) = skypilot_data::sync::sync_skyhanni(&paths).await {
            tracing::warn!(error = %e, "SkyHanni location data unavailable; guides show no waypoints");
        }
    }
    let registry = Arc::new(tokio::task::spawn_blocking({
        let p = paths.clone();
        move || Registry::load(&p)
    })
    .await??);

    let hypixel = HypixelClient::new(ClientConfig { api_key: config.hypixel_api_key.clone(), ..Default::default() })?;
    if config.dev_sample_profile {
        tracing::warn!("SKYPILOT_DEV_SAMPLE_PROFILE is on: the name \"SampleUser\" returns the anonymised fixture profile (development only)");
    }
    if !hypixel.has_key() {
        tracing::warn!("HYPIXEL_API_KEY not set: profile analysis disabled (market features still work)");
    }
    let store = open_store(&config).await?;
    let market = Arc::new(MarketEngine::new(registry.clone()));
    let translator = Translator::new(dictionary(&registry), skypilot_translation::providers::from_env());
    let llm = skypilot_translation::anthropic::AnthropicClient::from_env();
    if let Some(l) = &llm {
        tracing::info!(model = %l.model, fallbacks = l.fallbacks, "assistant LLM phrasing enabled");
    }
    let bus = match &config.redis_url {
        Some(url) => match bus::Bus::with_redis(url).await {
            Ok(b) => {
                tracing::info!("redis event bus connected");
                b
            }
            Err(e) => {
                tracing::warn!(error = %e, "redis unavailable; using in-process event bus");
                bus::Bus::local_only()
            }
        },
        None => bus::Bus::local_only(),
    };
    let push = if config.web_push {
        match push::WebPush::load(config.vapid_private_key.as_deref(), &config.root.join("data/cache"), config.vapid_subject.clone()) {
            Ok(p) => Some(p),
            Err(e) => {
                tracing::warn!(error = %e, "Web Push disabled");
                None
            }
        }
    } else {
        None
    };
    let secrets = secrets::SecretBox::load(config.secret_key.as_deref(), &config.root.join("data/cache"))?;
    let state = Arc::new(AppState::new(config.clone(), registry, market, hypixel, store, translator, llm, bus, push, secrets));

    jobs::warm_start(&state).await;
    if config.collector == CollectorMode::Embedded {
        jobs::spawn_collector(state.clone());
    }
    jobs::spawn_data_refresh(state.clone(), paths);

    let app = routes::router(state.clone());
    let listener = tokio::net::TcpListener::bind(config.bind).await?;
    tracing::info!(addr = %config.bind, "SkyPilot API listening");
    axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>()).with_graceful_shutdown(async {
        let _ = tokio::signal::ctrl_c().await;
    })
    .await?;
    Ok(())
}
