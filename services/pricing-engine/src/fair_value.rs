//! Fair Value Engine. Never "lowest BIN only": blends the ask side (active BINs) with
//! realised sales (recency-weighted median after MAD outlier removal), and reports
//! liquidity, volatility, trend, expected time-to-sell and a calibrated confidence.

use skypilot_calc::stats;
use skypilot_protocol::common::{Confidence, Range};
use skypilot_protocol::market::FairValue;

use crate::state::SaleRecord;

#[derive(Debug, Clone)]
pub struct FairValueConfig {
    pub top_n: usize,
    pub outlier_z: f64,
    pub half_life_hours: f64,
    pub window_days: f64,
    pub trend_split_hours: f64,
}

impl Default for FairValueConfig {
    fn default() -> Self {
        FairValueConfig { top_n: 5, outlier_z: 3.5, half_life_hours: 24.0, window_days: 7.0, trend_split_hours: 24.0 }
    }
}

const DAY_MS: f64 = 86_400_000.0;
/// Without any realised sale, a value is derived from listings only when at least this many
/// BINs agree on a level; one or two asks are just what a seller hopes to get.
pub const MIN_ASKS_WITHOUT_SALES: usize = 3;

/// `bin_unit_prices`: active BIN unit prices for the comparable key (any order).
/// `history_since_ms`: earliest instant covered by the sales history (for sales/day).
pub fn compute(
    price_key: &str,
    item_id: &str,
    bin_unit_prices: &[f64],
    sales: &[SaleRecord],
    now_ms: i64,
    history_since_ms: i64,
    cfg: &FairValueConfig,
) -> FairValue {
    let asks = stats::clean_sorted(bin_unit_prices);
    let lowest = asks.first().copied();
    let second = asks.get(1).copied();
    let top_n: Vec<f64> = asks.iter().take(cfg.top_n).copied().collect();
    let top_n_median = stats::median(&top_n);
    let ask_anchor = stats::median(&asks.iter().take(3).copied().collect::<Vec<_>>());

    let window_ms = cfg.window_days * DAY_MS;
    let recent: Vec<SaleRecord> = sales
        .iter()
        .copied()
        .filter(|s| s.unit_price.is_finite() && s.unit_price > 0.0 && (now_ms - s.ts_ms) as f64 <= window_ms && s.ts_ms <= now_ms + 60_000)
        .collect();
    let raw_prices: Vec<f64> = recent.iter().map(|s| s.unit_price).collect();
    let kept = stats::remove_outliers(&raw_prices, cfg.outlier_z);
    let (lo, hi) = (kept.first().copied().unwrap_or(0.0), kept.last().copied().unwrap_or(f64::MAX));
    let kept_sales: Vec<SaleRecord> = recent.iter().copied().filter(|s| s.unit_price >= lo && s.unit_price <= hi).collect();
    let prices: Vec<f64> = kept_sales.iter().map(|s| s.unit_price).collect();

    let half_life_ms = cfg.half_life_hours.max(0.1) * 3_600_000.0;
    let weighted: Vec<(f64, f64)> = kept_sales
        .iter()
        .map(|s| (s.unit_price, (-(now_ms - s.ts_ms).max(0) as f64 / half_life_ms * std::f64::consts::LN_2).exp()))
        .collect();
    let sales_median = stats::median(&prices);
    let sales_weighted_median = stats::weighted_median(&weighted);
    let sales_trimmed_mean = stats::trimmed_mean(&prices, 0.1);
    let sales_24h = recent.iter().filter(|s| (now_ms - s.ts_ms) as f64 <= DAY_MS).count() as u32;
    let observed_days = ((now_ms - history_since_ms.min(now_ms)) as f64 / DAY_MS).clamp(1.0 / 24.0, cfg.window_days);
    let sales_per_day = recent.len() as f64 / observed_days;
    let volatility = stats::coefficient_of_variation(&prices);

    let split = now_ms as f64 - cfg.trend_split_hours * 3_600_000.0;
    let older: Vec<f64> = kept_sales.iter().filter(|s| (s.ts_ms as f64) < split).map(|s| s.unit_price).collect();
    let newer: Vec<f64> = kept_sales.iter().filter(|s| (s.ts_ms as f64) >= split).map(|s| s.unit_price).collect();
    let trend = match (stats::median(&older), stats::median(&newer)) {
        (Some(o), Some(n)) if o > 0.0 && older.len() >= 2 && newer.len() >= 2 => Some((n - o) / o),
        _ => None,
    };

    let sale_anchor = sales_weighted_median.or(sales_median);
    let (value, method) = match (ask_anchor, sale_anchor) {
        (Some(ask), Some(sale)) => {
            let w = (kept_sales.len() as f64 / 15.0).min(1.0);
            let capped_sale = sale.min(ask * 1.1);
            (Some(w * capped_sale + (1.0 - w) * ask), format!("blend(sales w={w:.2}, asks)"))
        }
        (None, Some(sale)) => (Some(sale), "sales only".to_string()),
        (Some(ask), None) if asks.len() >= MIN_ASKS_WITHOUT_SALES => (Some(ask * 0.97), "asks only (-3%)".to_string()),
        (Some(_), None) => (None, format!("{} listing(s) and no sales: not enough data", asks.len())),
        (None, None) => (None, "no data".to_string()),
    };

    let liquidity = 1.0 - (-sales_per_day / 8.0).exp();
    let expected_sell_minutes = match value {
        Some(v) if sales_per_day > 0.0 => {
            let competing = asks.iter().filter(|p| **p <= v).count() as f64;
            let base = 1440.0 / sales_per_day * (competing * 0.5 + 1.0);
            Some(Range { low: (base * 0.5).max(1.0), high: (base * 2.0).max(2.0) })
        }
        _ => None,
    };

    // ---- confidence
    let mut reasons = Vec::new();
    let n = kept_sales.len() as f64;
    let sample = 1.0 - (-n / 8.0).exp();
    if n < 3.0 {
        reasons.push("Insufficient sales data".to_string());
    }
    if n == 0.0 && (1..MIN_ASKS_WITHOUT_SALES).contains(&asks.len()) {
        reasons.push("Too few listings to value without sales".to_string());
    }
    let listing_factor = match asks.len() {
        0 => {
            reasons.push("No active BIN listings".to_string());
            0.0
        }
        1 => 0.5,
        2 => 0.75,
        _ => 1.0,
    };
    let cv = volatility.unwrap_or(0.5);
    if cv > 0.35 {
        reasons.push("High volatility".to_string());
    }
    let agreement = match (ask_anchor, sale_anchor, value) {
        (Some(a), Some(s), Some(v)) if v > 0.0 => {
            let gap = (a - s).abs() / v;
            if gap > 0.3 {
                reasons.push("Listings and recent sales disagree".to_string());
            }
            (1.0 - gap).clamp(0.4, 1.0)
        }
        _ => 0.7,
    };
    let score = if value.is_none() {
        0.0
    } else {
        (0.55 * sample + 0.2 * listing_factor + 0.25 * (1.0 - cv.min(1.0))) * agreement
    };

    FairValue {
        price_key: price_key.to_string(),
        item_id: item_id.to_string(),
        value,
        lowest_bin: lowest,
        second_lowest_bin: second,
        top_n_bin_median: top_n_median,
        active_listings: asks.len() as u32,
        sales_median,
        sales_weighted_median,
        sales_trimmed_mean,
        sales_24h,
        sales_per_day,
        volatility,
        liquidity,
        expected_sell_minutes,
        trend,
        comparables_used: kept_sales.len() as u32,
        method,
        confidence: Confidence::from_score(score, reasons),
    }
}
