//! Market collector.
//!
//! * Auction House: page 0 is polled; only when Hypixel's `lastUpdated` changes is the full
//!   crawl performed (bounded concurrency). Hypixel refreshes it about once a minute and the
//!   CDN caches for 60s, so polling faster is pointless.
//! * Ended auctions (last 60s of sales) and Bazaar: polled on a fixed cadence, skipped when
//!   `lastUpdated` is unchanged.
//! * Errors back off; a 429 or open circuit pauses the loop for the advertised time.
//! * Every successful refresh is handed to a [`MarketSink`] (persistence / pub-sub) and the
//!   engine recomputes flips.

use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::time::Duration;

use async_trait::async_trait;
use futures::stream::{self, StreamExt};
use skypilot_hypixel::{BazaarResponse, HypixelClient, HypixelError, RawAuction};
use skypilot_pricing::MarketEngine;
use skypilot_pricing::normalize::Sale;
use skypilot_pricing::state::CandidateRecord;

#[async_trait]
pub trait MarketSink: Send + Sync {
    async fn on_sales(&self, _sales: &[Sale]) {}
    async fn on_bazaar(&self, _resp: &BazaarResponse) {}
    async fn on_auctions(&self, _active: usize, _last_updated: i64) {}
    async fn on_flips(&self, _new_candidates: &[CandidateRecord]) {}
}

pub struct NoSink;
#[async_trait]
impl MarketSink for NoSink {}

#[derive(Debug, Clone)]
pub struct CollectorConfig {
    pub ah_poll: Duration,
    pub ended_poll: Duration,
    pub bazaar_poll: Duration,
    pub page_concurrency: usize,
}

impl Default for CollectorConfig {
    fn default() -> Self {
        CollectorConfig { ah_poll: Duration::from_secs(20), ended_poll: Duration::from_secs(30), bazaar_poll: Duration::from_secs(30), page_concurrency: 6 }
    }
}

#[derive(Default)]
pub struct Health {
    pub healthy: AtomicBool,
    pub last_success_ms: AtomicI64,
}

pub struct Collector {
    pub engine: Arc<MarketEngine>,
    pub client: HypixelClient,
    pub sink: Arc<dyn MarketSink>,
    pub config: CollectorConfig,
    pub health: Arc<Health>,
}

fn pause_for(e: &HypixelError) -> Duration {
    match e {
        HypixelError::RateLimited { retry_after_secs } => Duration::from_secs((*retry_after_secs).max(5)),
        HypixelError::CircuitOpen { retry_in_secs } => Duration::from_secs((*retry_in_secs).max(5)),
        _ => Duration::from_secs(30),
    }
}

impl Collector {
    pub fn new(engine: Arc<MarketEngine>, client: HypixelClient, sink: Arc<dyn MarketSink>, config: CollectorConfig) -> Self {
        Collector { engine, client, sink, config, health: Arc::new(Health::default()) }
    }

    fn ok(&self) {
        self.health.healthy.store(true, Ordering::Relaxed);
        self.health.last_success_ms.store(skypilot_pricing::now_ms(), Ordering::Relaxed);
        self.engine.set_error(None);
    }

    fn fail(&self, what: &str, e: &HypixelError) {
        tracing::warn!(error = %e, what, "collector error");
        self.health.healthy.store(false, Ordering::Relaxed);
        self.engine.set_error(Some(format!("{what}: {e}")));
    }

    /// One full crawl of every AH page.
    pub async fn crawl_auctions(&self) -> Result<Option<i64>, HypixelError> {
        let first = self.client.auctions_page(0, true).await?;
        let last_updated = first.last_updated;
        if last_updated == self.engine.state.read().auctions_updated_ms {
            return Ok(None);
        }
        let total = first.total_pages.max(1);
        let mut all: Vec<RawAuction> = first.auctions;
        let pages: Vec<u32> = (1..total).collect();
        let results: Vec<Result<Vec<RawAuction>, HypixelError>> = stream::iter(pages)
            .map(|p| {
                let c = self.client.clone();
                async move { c.auctions_page(p, true).await.map(|page| page.auctions) }
            })
            .buffer_unordered(self.config.page_concurrency.max(1))
            .collect()
            .await;
        for r in results {
            match r {
                Ok(a) => all.extend(a),
                // a page that disappeared mid-crawl (404) is normal when pages shrink
                Err(HypixelError::NotFound) => {}
                Err(e) => return Err(e),
            }
        }
        let engine = self.engine.clone();
        let n = tokio::task::spawn_blocking(move || engine.ingest_auctions(all, last_updated)).await.unwrap_or(0);
        self.sink.on_auctions(n, last_updated).await;
        Ok(Some(last_updated))
    }

    pub async fn poll_ended(&self) -> Result<usize, HypixelError> {
        let ended = self.client.auctions_ended(true).await?;
        if ended.last_updated == self.engine.state.read().ended_updated_ms {
            return Ok(0);
        }
        let engine = self.engine.clone();
        let sales = tokio::task::spawn_blocking(move || engine.ingest_ended(&ended)).await.unwrap_or_default();
        self.sink.on_sales(&sales).await;
        Ok(sales.len())
    }

    pub async fn poll_bazaar(&self) -> Result<bool, HypixelError> {
        let bz = self.client.bazaar(true).await?;
        if bz.last_updated == self.engine.state.read().bazaar_updated_ms {
            return Ok(false);
        }
        self.sink.on_bazaar(&bz).await;
        self.engine.ingest_bazaar(bz);
        Ok(true)
    }

    async fn recompute(&self) {
        let before = self.engine.state.read().candidates.len();
        let engine = self.engine.clone();
        let _ = tokio::task::spawn_blocking(move || engine.recompute()).await;
        let new: Vec<CandidateRecord> = {
            let st = self.engine.state.read();
            st.candidates.iter().skip(before.min(st.candidates.len())).cloned().collect()
        };
        self.sink.on_flips(&new).await;
    }

    /// Run forever (until the task is dropped).
    pub async fn run(self: Arc<Self>) {
        let ah = {
            let me = self.clone();
            tokio::spawn(async move {
                loop {
                    match me.crawl_auctions().await {
                        Ok(Some(_)) => {
                            me.ok();
                            me.recompute().await;
                            tokio::time::sleep(me.config.ah_poll).await;
                        }
                        Ok(None) => tokio::time::sleep(me.config.ah_poll).await,
                        Err(e) => {
                            me.fail("auctions", &e);
                            tokio::time::sleep(pause_for(&e)).await;
                        }
                    }
                }
            })
        };
        let ended = {
            let me = self.clone();
            tokio::spawn(async move {
                loop {
                    match me.poll_ended().await {
                        Ok(_) => tokio::time::sleep(me.config.ended_poll).await,
                        Err(e) => {
                            me.fail("auctions_ended", &e);
                            tokio::time::sleep(pause_for(&e)).await;
                        }
                    }
                }
            })
        };
        let bazaar = {
            let me = self.clone();
            tokio::spawn(async move {
                loop {
                    match me.poll_bazaar().await {
                        Ok(changed) => {
                            if changed {
                                me.ok();
                                me.recompute().await;
                            }
                            tokio::time::sleep(me.config.bazaar_poll).await;
                        }
                        Err(e) => {
                            me.fail("bazaar", &e);
                            tokio::time::sleep(pause_for(&e)).await;
                        }
                    }
                }
            })
        };
        let _ = tokio::join!(ah, ended, bazaar);
    }
}
