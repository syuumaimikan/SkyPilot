//! Standalone collector process. Normally the collector runs embedded in `skypilot-api`
//! (COLLECTOR_MODE=embedded); this binary runs it alone for diagnostics or to warm the
//! market state, logging a summary after every refresh.
//!
//!   cargo run -p skypilot-collector -- --once     # one crawl of AH + ended + bazaar, then exit

use std::sync::Arc;

use async_trait::async_trait;
use skypilot_collector::{Collector, CollectorConfig, MarketSink};
use skypilot_data::{DataPaths, Registry, find_repo_root};
use skypilot_hypixel::{BazaarResponse, ClientConfig, HypixelClient};
use skypilot_pricing::MarketEngine;
use skypilot_pricing::normalize::Sale;
use skypilot_pricing::state::CandidateRecord;

struct LogSink;

#[async_trait]
impl MarketSink for LogSink {
    async fn on_sales(&self, sales: &[Sale]) {
        tracing::info!(count = sales.len(), "ended auctions ingested");
    }
    async fn on_bazaar(&self, resp: &BazaarResponse) {
        tracing::info!(products = resp.products.len(), "bazaar ingested");
    }
    async fn on_auctions(&self, active: usize, last_updated: i64) {
        tracing::info!(active, last_updated, "auction house crawled");
    }
    async fn on_flips(&self, new: &[CandidateRecord]) {
        tracing::info!(candidates = new.len(), "flips recomputed");
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt().with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into())).init();
    let once = std::env::args().any(|a| a == "--once");
    let root = std::env::var("SKYPILOT_ROOT").map(std::path::PathBuf::from).ok().or_else(|| find_repo_root(&std::env::current_dir().ok()?)).ok_or_else(|| anyhow::anyhow!("cannot find repository root (set SKYPILOT_ROOT)"))?;
    let paths = DataPaths::from_root(&root);
    if skypilot_data::sync::needs_sync(&paths, std::time::Duration::from_secs(24 * 3600)) {
        skypilot_data::sync::sync_hypixel_resources(&paths).await?;
        skypilot_data::sync::sync_neu(&paths).await?;
    }
    let registry = Arc::new(Registry::load(&paths)?);
    let engine = Arc::new(MarketEngine::new(registry));
    let client = HypixelClient::new(ClientConfig::default())?;
    let collector = Arc::new(Collector::new(engine.clone(), client, Arc::new(LogSink), CollectorConfig::default()));
    if once {
        collector.crawl_auctions().await?;
        collector.poll_ended().await?;
        collector.poll_bazaar().await?;
        let e = engine.clone();
        tokio::task::spawn_blocking(move || e.recompute()).await?;
        let st = engine.state.read();
        println!("active listings: {}, bin: {}, bazaar products: {}, flips: {}", st.listings.len(), st.active_bin_count(), st.bazaar.len(), st.flips.len());
        for f in st.flips.iter().take(10) {
            println!("  {:<40} buy {:>14.0} sell {:>14.0} profit {:>12.0} roi {:>5.1}% conf {:.2} {}", f.item_name, f.buy_price, f.recommended_sell, f.expected_net_profit, f.roi * 100.0, f.confidence.score, f.manipulation.verdict);
        }
        return Ok(());
    }
    collector.run().await;
    Ok(())
}
