//! Background jobs: data sync, market collector (embedded) with persistence, warm start.
//! None of these touch player data - player profiles are only fetched on user request.

use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use skypilot_collector::{Collector, CollectorConfig, MarketSink};
use skypilot_data::{DataPaths, Registry};
use skypilot_hypixel::BazaarResponse;
use skypilot_pricing::normalize::Sale;
use skypilot_pricing::state::CandidateRecord;
use skypilot_protocol::market::FlipQuery;

use crate::state::AppState;

/// Persists market history and pushes live updates to WebSocket subscribers.
pub struct StoreSink {
    pub state: Arc<AppState>,
}

#[async_trait]
impl MarketSink for StoreSink {
    async fn on_sales(&self, sales: &[Sale]) {
        if let Err(e) = self.state.store.store_sales(sales).await {
            tracing::warn!(error = %e, "failed to persist sales");
        }
    }
    async fn on_bazaar(&self, resp: &BazaarResponse) {
        if let Err(e) = self.state.store.store_bazaar(resp).await {
            tracing::warn!(error = %e, "failed to persist bazaar snapshot");
        }
    }
    async fn on_flips(&self, new: &[CandidateRecord]) {
        if let Err(e) = self.state.store.store_candidates(new).await {
            tracing::warn!(error = %e, "failed to persist flip candidates");
        }
        let q = FlipQuery { max_budget: None, min_profit: None, min_roi: None, min_confidence: None, max_risk: None, search: None, limit: Some(100) };
        let list = self.state.market.flips(&q, true);
        self.state.publish("flips", &list);
        self.state.publish("status", &self.state.market.status(self.state.hypixel.breaker_state().as_str(), vec![]));
        tokio::spawn(crate::push::evaluate_rules(self.state.clone()));
    }
}

/// Load recent history from the database so fair values are meaningful right after a restart.
pub async fn warm_start(state: &Arc<AppState>) {
    let since = AppState::now_ms() - state.config.warm_start_days * 86_400_000;
    match state.store.load_sales_since(since).await {
        Ok(sales) if !sales.is_empty() => {
            let earliest = sales.iter().map(|s| s.ts_ms).min().unwrap_or(since);
            let n = sales.len();
            state.market.seed_sales(sales, earliest);
            tracing::info!(n, "warm start: sales loaded");
        }
        Ok(_) => {}
        Err(e) => tracing::warn!(error = %e, "warm start: sales unavailable"),
    }
    if let Ok(points) = state.store.load_bazaar_since(since).await {
        let mut st = state.market.state.write();
        for (id, p) in points {
            st.seed_bazaar_point(&id, p);
        }
    }
    if let Ok(c) = state.store.load_candidates_since(since).await {
        let mut st = state.market.state.write();
        for x in c {
            st.record_candidate(x);
        }
    }
}

pub fn spawn_collector(state: Arc<AppState>) {
    let collector = Arc::new(Collector::new(state.market.clone(), state.hypixel.clone(), Arc::new(StoreSink { state: state.clone() }), CollectorConfig::default()));
    *state.collector_health.write() = Some(collector.health.clone());
    tokio::spawn(collector.run());
}

/// Refresh Hypixel resources + NEU-REPO periodically and hot-swap the registry.
pub fn spawn_data_refresh(state: Arc<AppState>, paths: DataPaths) {
    let hours = state.config.data_refresh_hours.max(1);
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_secs(hours * 3600)).await;
            let ok = skypilot_data::sync::sync_hypixel_resources(&paths).await.and(skypilot_data::sync::sync_neu(&paths).await);
            if let Err(e) = skypilot_data::sync::sync_skyhanni(&paths).await {
                tracing::warn!(error = %e, "SkyHanni location data not refreshed");
            }
            match ok {
                Ok(()) => match tokio::task::spawn_blocking({
                    let p = paths.clone();
                    move || Registry::load(&p)
                })
                .await
                {
                    Ok(Ok(r)) => {
                        state.set_registry(Arc::new(r));
                        tracing::info!("registry refreshed");
                    }
                    Ok(Err(e)) => tracing::warn!(error = %e, "registry reload failed; keeping previous"),
                    Err(e) => tracing::warn!(error = %e, "registry reload panicked"),
                },
                Err(e) => tracing::warn!(error = %e, "data sync failed; keeping previous data"),
            }
        }
    });
}
