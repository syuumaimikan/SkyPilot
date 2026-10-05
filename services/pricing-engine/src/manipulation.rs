//! AH manipulation / bad-data detection. Any red flag turns a flip into "DO NOT FLIP".

use std::collections::HashMap;

use skypilot_calc::stats;
use skypilot_protocol::market::{FairValue, ManipulationCheck};

use crate::normalize::Listing;
use crate::state::SaleRecord;

const DAY_MS: i64 = 86_400_000;

pub fn check(listings: &[&Listing], sales: &[SaleRecord], fair: &FairValue, buy_price: f64, now_ms: i64) -> ManipulationCheck {
    let mut flags = Vec::new();

    let week: Vec<f64> = sales.iter().filter(|s| now_ms - s.ts_ms <= 7 * DAY_MS).map(|s| s.unit_price).collect();
    let day: Vec<f64> = sales.iter().filter(|s| now_ms - s.ts_ms <= DAY_MS).map(|s| s.unit_price).collect();
    let month: Vec<f64> = sales.iter().map(|s| s.unit_price).collect();

    if week.len() < 3 {
        flags.push("Insufficient sample".to_string());
    }
    if fair.sales_per_day < 0.5 && week.len() >= 1 {
        flags.push("Very low volume".to_string());
    }
    // Artificial spike: last 24h median far above the weekly median on thin volume.
    if let (Some(d), Some(w)) = (stats::median(&day), stats::median(&week)) {
        if w > 0.0 && d / w > 1.4 && day.len() <= 5 {
            flags.push("Artificial price spike".to_string());
        }
    }
    // Historical deviation: fair value far from the 30-day median.
    if let (Some(v), Some(m)) = (fair.value, stats::median(&month)) {
        if m > 0.0 && month.len() >= 5 && ((v - m) / m).abs() > 0.5 {
            flags.push("Historical deviation".to_string());
        }
    }
    // Abnormal spread between asks and realised prices.
    if let (Some(ask), Some(sale)) = (fair.top_n_bin_median, fair.sales_weighted_median) {
        if sale > 0.0 && ask / sale > 2.0 {
            flags.push("Abnormal spread".to_string());
        }
    }
    // Listing cluster: one seller controls the cheapest listings.
    if listings.len() >= 4 {
        let mut by_seller: HashMap<&str, usize> = HashMap::new();
        for l in listings.iter().take(10) {
            *by_seller.entry(l.seller.as_str()).or_default() += 1;
        }
        let top = by_seller.values().max().copied().unwrap_or(0);
        if top >= 4 || (top as f64 / listings.len().min(10) as f64) >= 0.6 {
            flags.push("Suspicious listing cluster".to_string());
        }
    }
    // Outlier listing: the buy candidate is implausibly cheap vs sales (often bait / wrong item variant).
    if let Some(m) = stats::median(&week) {
        if m > 0.0 && buy_price / m < 0.25 && week.len() >= 3 {
            flags.push("Outlier listing".to_string());
        }
    }

    let hard = ["Artificial price spike", "Suspicious listing cluster", "Abnormal spread", "Historical deviation", "Outlier listing"];
    let suspicious = flags.iter().any(|f| hard.contains(&f.as_str())) || flags.len() >= 3;
    let verdict = if suspicious {
        "DO NOT FLIP - Possible market manipulation".to_string()
    } else if flags.is_empty() {
        "No manipulation signals".to_string()
    } else {
        "Caution: thin data".to_string()
    };
    ManipulationCheck { flags, suspicious, verdict }
}
