//! Event bus for live market updates.
//!
//! Events always go to the in-process broadcast channel that feeds WebSocket clients. When
//! REDIS_URL is set they are also published on a Redis channel so every API replica streams
//! the same events, and notification deliveries are de-duplicated across replicas
//! (`claim_once`). Without Redis the de-duplication falls back to an in-process cache.

use std::time::Duration;

use futures::StreamExt;
use moka::sync::Cache;
use redis::aio::ConnectionManager;
use tokio::sync::broadcast;

const CHANNEL: &str = "skypilot:events";

pub struct Bus {
    pub local: broadcast::Sender<String>,
    instance: String,
    redis: Option<ConnectionManager>,
    seen: Cache<String, ()>,
}

impl Bus {
    pub fn local_only() -> Bus {
        let (local, _) = broadcast::channel(256);
        Bus { local, instance: crate::auth::random_token(8), redis: None, seen: Cache::builder().max_capacity(200_000).time_to_live(Duration::from_secs(24 * 3600)).build() }
    }

    /// Connect to Redis and start forwarding events published by other replicas.
    pub async fn with_redis(url: &str) -> anyhow::Result<Bus> {
        let mut bus = Bus::local_only();
        let client = redis::Client::open(url)?;
        bus.redis = Some(ConnectionManager::new(client.clone()).await?);
        let local = bus.local.clone();
        let me = bus.instance.clone();
        tokio::spawn(async move {
            let mut backoff = Duration::from_secs(1);
            loop {
                match client.get_async_pubsub().await {
                    Ok(mut ps) => {
                        if ps.subscribe(CHANNEL).await.is_ok() {
                            backoff = Duration::from_secs(1);
                            let mut stream = ps.on_message();
                            while let Some(msg) = stream.next().await {
                                let Ok(payload) = msg.get_payload::<String>() else { continue };
                                if let Some((origin, body)) = payload.split_once('\n') {
                                    if origin != me {
                                        let _ = local.send(body.to_string());
                                    }
                                }
                            }
                        }
                        tracing::warn!("redis subscription ended; reconnecting");
                    }
                    Err(e) => tracing::warn!(error = %e, "redis subscribe failed"),
                }
                tokio::time::sleep(backoff).await;
                backoff = (backoff * 2).min(Duration::from_secs(30));
            }
        });
        Ok(bus)
    }

    pub fn kind(&self) -> &'static str {
        if self.redis.is_some() { "redis" } else { "local" }
    }

    pub fn subscribers(&self) -> usize {
        self.local.receiver_count()
    }

    pub fn subscribe(&self) -> broadcast::Receiver<String> {
        self.local.subscribe()
    }

    pub fn publish(&self, kind: &str, payload: &impl serde::Serialize) {
        let Ok(body) = serde_json::to_string(&serde_json::json!({"kind": kind, "data": payload})) else { return };
        let _ = self.local.send(body.clone());
        if let Some(conn) = &self.redis {
            let mut conn = conn.clone();
            let msg = format!("{}\n{}", self.instance, body);
            tokio::spawn(async move {
                if let Err(e) = redis::cmd("PUBLISH").arg(CHANNEL).arg(msg).query_async::<i64>(&mut conn).await {
                    tracing::debug!(error = %e, "redis publish failed");
                }
            });
        }
    }

    /// True exactly once per key within `ttl` (across replicas when Redis is configured).
    pub async fn claim_once(&self, key: &str, ttl: Duration) -> bool {
        if let Some(conn) = &self.redis {
            let mut conn = conn.clone();
            let r = redis::cmd("SET").arg(format!("skypilot:once:{key}")).arg(1).arg("NX").arg("EX").arg(ttl.as_secs().max(1)).query_async::<Option<String>>(&mut conn).await;
            match r {
                Ok(v) => return v.is_some(),
                Err(e) => tracing::debug!(error = %e, "redis claim failed; using local de-duplication"),
            }
        }
        let entry = self.seen.entry(key.to_string()).or_insert(());
        entry.is_fresh()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn local_publish_and_claim() {
        let bus = Bus::local_only();
        let mut rx = bus.subscribe();
        bus.publish("status", &serde_json::json!({"ok": true}));
        let got = rx.recv().await.unwrap();
        assert!(got.contains("\"kind\":\"status\""));
        assert!(bus.claim_once("a", Duration::from_secs(60)).await);
        assert!(!bus.claim_once("a", Duration::from_secs(60)).await);
        assert!(bus.claim_once("b", Duration::from_secs(60)).await);
    }
}
