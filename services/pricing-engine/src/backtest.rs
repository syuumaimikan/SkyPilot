//! Backtesting of the flip strategy on data SkyPilot itself collected: historical flip
//! candidates (what was buyable, at what recommended sell) and realised sales afterwards.
//! A trade exits at the target when a comparable sale at >= target occurs within the
//! holding window; otherwise it is force-closed at the median of later sales (or at a
//! 10% loss when the item never traded again) - conservative by construction.

use std::collections::HashMap;

use skypilot_calc::fees::AhFeeConfig;
use skypilot_calc::stats;
use skypilot_protocol::market::{BacktestRequest, BacktestResult, BacktestTrade};

use crate::state::{CandidateRecord, SaleRecord};

pub fn run(
    candidates: &[CandidateRecord],
    sales: &HashMap<String, Vec<SaleRecord>>,
    req: &BacktestRequest,
    fees: &AhFeeConfig,
    now_ms: i64,
) -> BacktestResult {
    let lookback_ms = req.lookback_hours as i64 * 3_600_000;
    let hold_ms = req.max_hold_hours.max(1) as i64 * 3_600_000;
    let mut notes = Vec::new();
    // one trade per (key, hour) to avoid counting the same listing on every scan
    let mut seen: HashMap<(String, i64), ()> = HashMap::new();
    let mut trades = Vec::new();
    let mut earliest = now_ms;
    for c in candidates {
        if now_ms - c.ts_ms > lookback_ms || now_ms - c.ts_ms < hold_ms {
            continue; // outside window, or too recent to know the outcome
        }
        if c.roi < req.min_roi || c.expected_profit < req.min_profit || c.sales_per_day < req.min_sales_per_day {
            continue;
        }
        if req.max_buy_price.is_some_and(|m| c.buy_price > m) || req.min_confidence.is_some_and(|m| c.confidence < m) {
            continue;
        }
        if seen.insert((c.comparable_key.clone(), c.ts_ms / 3_600_000), ()).is_some() {
            continue;
        }
        earliest = earliest.min(c.ts_ms);
        let later: Vec<&SaleRecord> = sales
            .get(&c.comparable_key)
            .map(|v| v.iter().filter(|s| s.ts_ms > c.ts_ms && s.ts_ms <= c.ts_ms + hold_ms).collect())
            .unwrap_or_default();
        let (sell, minutes, forced) = match later.iter().find(|s| s.unit_price >= c.recommended_sell) {
            Some(s) => (c.recommended_sell, (s.ts_ms - c.ts_ms) as f64 / 60_000.0, false),
            None => {
                let prices: Vec<f64> = later.iter().map(|s| s.unit_price).collect();
                (stats::median(&prices).unwrap_or(c.buy_price * 0.9), hold_ms as f64 / 60_000.0, true)
            }
        };
        let net = fees.bin_net_proceeds(sell, 24.0);
        trades.push(BacktestTrade {
            item_id: c.market_id.clone(),
            buy_price: c.buy_price,
            sell_price: sell,
            profit: net - c.buy_price,
            hold_minutes: minutes,
            bought_at_ms: c.ts_ms,
            forced_exit: forced,
        });
    }
    trades.sort_by_key(|t| t.bought_at_ms);
    let profits: Vec<f64> = trades.iter().map(|t| t.profit).collect();
    let n = trades.len();
    let wins = profits.iter().filter(|p| **p > 0.0).count();
    // equity curve drawdown
    let mut equity = 0.0f64;
    let mut peak = 0.0f64;
    let mut max_dd = 0.0f64;
    for p in &profits {
        equity += p;
        peak = peak.max(equity);
        max_dd = max_dd.max(peak - equity);
    }
    // capital: max concurrent open capital
    let mut events: Vec<(i64, f64)> = Vec::new();
    for t in &trades {
        events.push((t.bought_at_ms, t.buy_price));
        events.push((t.bought_at_ms + (t.hold_minutes * 60_000.0) as i64, -t.buy_price));
    }
    events.sort_by_key(|e| e.0);
    let (mut open, mut capital) = (0.0f64, 0.0f64);
    for (_, delta) in events {
        open += delta;
        capital = capital.max(open);
    }
    if n == 0 {
        notes.push("No historical candidates matched. SkyPilot backtests only on data it has collected; run the collector longer.".into());
    }
    let window_hours = if n > 0 { (now_ms - earliest) as f64 / 3_600_000.0 } else { 0.0 };
    BacktestResult {
        trades: n as u32,
        win_rate: if n > 0 { wins as f64 / n as f64 } else { 0.0 },
        average_profit: if n > 0 { profits.iter().sum::<f64>() / n as f64 } else { 0.0 },
        median_profit: stats::percentile_sorted(&{
            let mut s = profits.clone();
            s.sort_by(|a, b| a.total_cmp(b));
            s
        }, 0.5)
        .unwrap_or(0.0),
        median_sell_minutes: stats::median(&trades.iter().filter(|t| !t.forced_exit).map(|t| t.hold_minutes).collect::<Vec<_>>()).unwrap_or(0.0),
        max_drawdown: max_dd,
        capital_required: capital,
        total_profit: profits.iter().sum(),
        sample: trades.iter().rev().take(50).cloned().collect(),
        data_window_hours: window_hours,
        notes,
    }
}
