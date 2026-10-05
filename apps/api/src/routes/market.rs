//! Market endpoints + live WebSocket. No endpoint ever buys/sells/lists anything.

use std::sync::Arc;

use axum::Json;
use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::{Path, Query, State};
use axum::response::Response;
use serde::Deserialize;
use serde_json::{Value, json};
use skypilot_protocol::common::RiskLevel;
use skypilot_protocol::market::*;

use crate::error::{ApiResult, AppError};
use crate::state::AppState;

pub async fn status(State(state): State<Arc<AppState>>) -> Json<MarketStatus> {
    let mut counters: Vec<(&'static str, u64)> = state.hypixel.metrics().snapshot();
    counters.push(("ws_subscribers", state.bus.subscribers() as u64));
    Json(state.market.status(state.hypixel.breaker_state().as_str(), counters))
}

#[derive(Deserialize)]
pub struct FlipParams {
    pub max_budget: Option<f64>,
    pub min_profit: Option<f64>,
    pub min_roi: Option<f64>,
    pub min_confidence: Option<f64>,
    pub max_risk: Option<RiskLevel>,
    pub search: Option<String>,
    pub limit: Option<u32>,
}

fn healthy(state: &AppState) -> bool {
    state.collector_health.read().as_ref().map(|h| h.healthy.load(std::sync::atomic::Ordering::Relaxed)).unwrap_or(false)
}

pub async fn flips(State(state): State<Arc<AppState>>, Query(p): Query<FlipParams>) -> ApiResult<Json<FlipList>> {
    if p.search.as_ref().is_some_and(|s| s.len() > 64) {
        return Err(AppError::bad_request("search too long"));
    }
    let q = FlipQuery { max_budget: p.max_budget, min_profit: p.min_profit, min_roi: p.min_roi, min_confidence: p.min_confidence, max_risk: p.max_risk, search: p.search, limit: p.limit };
    Ok(Json(state.market.flips(&q, healthy(&state))))
}

#[derive(Deserialize)]
pub struct CapitalParams {
    pub capital: Option<f64>,
}

pub async fn bazaar(State(state): State<Arc<AppState>>, Query(p): Query<CapitalParams>) -> Json<BazaarList> {
    let capital = p.capital.filter(|c| c.is_finite() && *c > 0.0).unwrap_or(50_000_000.0).min(1e12);
    Json(state.market.bazaar_list(capital))
}

pub async fn craft(State(state): State<Arc<AppState>>) -> Json<Value> {
    let (flips, at) = state.market.craft_flips();
    let (_, _, bz) = state.market.freshness();
    Json(json!({"flips": flips.into_iter().take(300).collect::<Vec<_>>(), "computedAtMs": at, "freshness": bz}))
}

pub async fn time_flips(State(state): State<Arc<AppState>>) -> Json<Value> {
    let (flips, at) = state.market.time_flips();
    let (_, _, bz) = state.market.freshness();
    Json(json!({"flips": flips.into_iter().take(300).collect::<Vec<_>>(), "computedAtMs": at, "freshness": bz}))
}

#[derive(Deserialize)]
pub struct SearchParams {
    pub q: String,
}

pub async fn search(State(state): State<Arc<AppState>>, Query(p): Query<SearchParams>) -> ApiResult<Json<Vec<ItemSearchResult>>> {
    if p.q.len() > 64 {
        return Err(AppError::bad_request("query too long"));
    }
    Ok(Json(state.market.search(&p.q, 20)))
}

#[derive(Deserialize)]
pub struct RangeParams {
    pub range: Option<String>,
}

pub async fn item(State(state): State<Arc<AppState>>, Path(id): Path<String>, Query(p): Query<RangeParams>) -> ApiResult<Json<PriceDetail>> {
    if id.len() > 128 || !id.chars().all(|c| c.is_ascii_alphanumeric() || "_:;|=-.".contains(c)) {
        return Err(AppError::bad_request("invalid item id"));
    }
    let range = p.range.unwrap_or_else(|| "24H".into()).to_uppercase();
    let (span, buckets) = skypilot_pricing::range_spec(&range);
    let since = AppState::now_ms() - span;
    let history = state.store.price_history(&id, since, span / buckets as i64).await.ok();
    Ok(Json(tokio::task::block_in_place(|| state.market.price_detail(&id, &range, history))))
}

pub async fn backtest(State(state): State<Arc<AppState>>, Json(req): Json<BacktestRequest>) -> ApiResult<Json<BacktestResult>> {
    if req.lookback_hours == 0 || req.lookback_hours > 24 * 90 || req.max_hold_hours == 0 || req.max_hold_hours > 24 * 14 {
        return Err(AppError::bad_request("lookback 1..2160h, max hold 1..336h"));
    }
    Ok(Json(tokio::task::block_in_place(|| state.market.backtest(&req))))
}

pub async fn ws(State(state): State<Arc<AppState>>, upgrade: WebSocketUpgrade) -> Response {
    upgrade.on_upgrade(move |socket| stream(socket, state))
}

async fn stream(mut socket: WebSocket, state: Arc<AppState>) {
    let mut rx = state.bus.subscribe();
    // initial snapshot
    let initial = state.market.flips(&FlipQuery { max_budget: None, min_profit: None, min_roi: None, min_confidence: None, max_risk: None, search: None, limit: Some(50) }, healthy(&state));
    if let Ok(s) = serde_json::to_string(&json!({"kind": "flips", "data": initial})) {
        if socket.send(Message::Text(s.into())).await.is_err() {
            return;
        }
    }
    loop {
        tokio::select! {
            msg = rx.recv() => match msg {
                Ok(text) => {
                    if socket.send(Message::Text(text.into())).await.is_err() {
                        break;
                    }
                }
                Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => continue,
                Err(_) => break,
            },
            incoming = socket.recv() => match incoming {
                Some(Ok(Message::Close(_))) | None | Some(Err(_)) => break,
                _ => {}
            }
        }
    }
}
