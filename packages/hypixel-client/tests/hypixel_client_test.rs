//! Exercises the client against a local stand-in for api.hypixel.net to prove the
//! politeness guarantees: cache hits, 429 = stop (no hammering), retries only on 5xx,
//! circuit breaker opening, and the key travelling in the `API-Key` header only.

use std::sync::Arc;
use std::sync::atomic::{AtomicU32, Ordering};
use std::time::Duration;

use axum::Router;
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use axum::response::IntoResponse;
use axum::routing::get;
use skypilot_hypixel::{BreakerState, ClientConfig, HypixelClient, HypixelError};

#[derive(Default)]
struct Counters {
    bazaar: AtomicU32,
    profiles: AtomicU32,
    flaky: AtomicU32,
}

async fn bazaar(State(c): State<Arc<Counters>>) -> impl IntoResponse {
    c.bazaar.fetch_add(1, Ordering::SeqCst);
    (StatusCode::OK, r#"{"success":true,"lastUpdated":1,"products":{}}"#)
}

async fn profiles(State(c): State<Arc<Counters>>, headers: HeaderMap) -> impl IntoResponse {
    c.profiles.fetch_add(1, Ordering::SeqCst);
    if headers.get("API-Key").and_then(|v| v.to_str().ok()) != Some("test-key") {
        return (StatusCode::FORBIDDEN, HeaderMap::new(), r#"{"success":false,"cause":"Invalid API key"}"#.to_string());
    }
    let mut h = HeaderMap::new();
    h.insert("RateLimit-Remaining", "0".parse().unwrap());
    h.insert("RateLimit-Reset", "120".parse().unwrap());
    (StatusCode::TOO_MANY_REQUESTS, h, r#"{"success":false,"cause":"Key throttle","throttle":true}"#.to_string())
}

async fn flaky(State(c): State<Arc<Counters>>) -> impl IntoResponse {
    c.flaky.fetch_add(1, Ordering::SeqCst);
    (StatusCode::BAD_GATEWAY, "upstream down")
}

async fn serve() -> (String, Arc<Counters>) {
    let counters = Arc::new(Counters::default());
    let app = Router::new()
        .route("/v2/skyblock/bazaar", get(bazaar))
        .route("/v2/skyblock/profiles", get(profiles))
        .route("/v2/skyblock/news", get(flaky))
        .with_state(counters.clone());
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });
    (format!("http://{addr}/v2"), counters)
}

fn client(base: &str, key: Option<&str>) -> HypixelClient {
    HypixelClient::new(ClientConfig {
        api_key: key.map(str::to_string),
        hypixel_base: base.to_string(),
        max_retries: 2,
        min_keyed_interval: Duration::from_millis(1),
        ..Default::default()
    })
    .unwrap()
}

#[tokio::test]
async fn responses_are_cached() {
    let (base, counters) = serve().await;
    let c = client(&base, None);
    c.bazaar(false).await.unwrap();
    c.bazaar(false).await.unwrap();
    assert_eq!(counters.bazaar.load(Ordering::SeqCst), 1);
    c.bazaar(true).await.unwrap(); // explicit refresh
    assert_eq!(counters.bazaar.load(Ordering::SeqCst), 2);
}

#[tokio::test]
async fn throttling_stops_further_keyed_requests() {
    let (base, counters) = serve().await;
    let c = client(&base, Some("test-key"));
    let uuid = "0123456789abcdef0123456789abcdef";
    let e = c.profiles(uuid).await.unwrap_err();
    assert!(matches!(e, HypixelError::RateLimited { retry_after_secs: 120 }));
    // A second call must not reach the server while blocked.
    let e2 = c.profiles(uuid).await.unwrap_err();
    assert!(matches!(e2, HypixelError::RateLimited { .. }));
    assert_eq!(counters.profiles.load(Ordering::SeqCst), 1, "429 must not be retried or re-sent");
}

#[tokio::test]
async fn missing_key_never_calls_upstream() {
    let (base, counters) = serve().await;
    let c = client(&base, None);
    assert_eq!(c.profiles("0123456789abcdef0123456789abcdef").await.unwrap_err(), HypixelError::MissingKey);
    assert_eq!(counters.profiles.load(Ordering::SeqCst), 0);
    assert!(matches!(c.profiles("not-a-uuid").await.unwrap_err(), HypixelError::InvalidInput(_)));
}

#[tokio::test]
async fn retries_5xx_then_opens_circuit() {
    let (base, counters) = serve().await;
    let c = client(&base, None);
    let e = c.news().await.unwrap_err();
    assert!(matches!(e, HypixelError::Upstream { status: 502, .. }));
    assert_eq!(counters.flaky.load(Ordering::SeqCst), 3, "1 try + 2 retries");
    let _ = c.news().await;
    // 5 consecutive failures open the circuit; further calls are rejected locally.
    let before = counters.flaky.load(Ordering::SeqCst);
    assert_eq!(c.breaker_state(), BreakerState::Open);
    assert!(matches!(c.news().await.unwrap_err(), HypixelError::CircuitOpen { .. }));
    assert_eq!(counters.flaky.load(Ordering::SeqCst), before);
}

#[test]
fn username_validation() {
    assert!(skypilot_hypixel::is_valid_username("player1k4n"));
    assert!(!skypilot_hypixel::is_valid_username("bad name"));
    assert!(!skypilot_hypixel::is_valid_username("waytoolongusername123"));
}
