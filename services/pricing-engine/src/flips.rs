//! AH Flip Scanner. Opportunities are ranked by a pluggable [`FlipScoreStrategy`]
//! (not raw profit) and every number is net of AH fees.

use skypilot_calc::fees::AhFeeConfig;
use skypilot_protocol::common::{Confidence, Range, RiskLevel};
use skypilot_protocol::market::{FeeBreakdown, FlipOpportunity, FlipQuery};

use crate::fair_value::{self, FairValueConfig};
use crate::manipulation;
use crate::state::{CandidateRecord, MarketState};

/// Pluggable ranking. Default implements
/// `profit × P(sale) × confidence / (capital × sell_time × risk)` with soft normalisers.
pub trait FlipScoreStrategy: Send + Sync {
    fn name(&self) -> &'static str;
    fn score(&self, f: &FlipOpportunity) -> f64;
}

pub struct DefaultFlipScore;

impl FlipScoreStrategy for DefaultFlipScore {
    fn name(&self) -> &'static str {
        "profit*p*conf/(capital*time*risk)"
    }
    fn score(&self, f: &FlipOpportunity) -> f64 {
        if f.manipulation.suspicious || f.expected_net_profit <= 0.0 {
            return 0.0;
        }
        let capital = 1.0 + f.buy_price / 50_000_000.0;
        let hours = 1.0 + f.expected_sell_minutes.high / 60.0;
        let s = f.expected_net_profit * f.probability_of_sale * f.confidence.score / (capital * hours * f.risk.weight());
        if s.is_finite() { s.max(0.0) } else { 0.0 }
    }
}

/// Minutes after which an underpriced BIN that is still listed becomes suspicious.
pub const STALE_BARGAIN_MINUTES: f64 = 45.0;

#[derive(Debug, Clone)]
pub struct FlipConfig {
    pub listing_hours: f64,
    pub min_profit: f64,
    pub min_roi: f64,
    /// Cheapest N listings per comparable key that are evaluated.
    pub listings_per_key: usize,
}

impl Default for FlipConfig {
    fn default() -> Self {
        FlipConfig { listing_hours: 24.0, min_profit: 500_000.0, min_roi: 0.05, listings_per_key: 3 }
    }
}

fn risk_level(volatility: f64, confidence: f64, suspicious: bool, capital: f64) -> RiskLevel {
    let mut r = volatility.min(1.0) * 1.5 + (1.0 - confidence);
    if suspicious {
        r += 2.0;
    }
    if capital > 500_000_000.0 {
        r += 0.4;
    }
    match r {
        x if x < 0.35 => RiskLevel::VeryLow,
        x if x < 0.7 => RiskLevel::Low,
        x if x < 1.1 => RiskLevel::Medium,
        x if x < 1.8 => RiskLevel::High,
        _ => RiskLevel::VeryHigh,
    }
}

/// Scan all BIN listings and return opportunities sorted by score (desc).
/// Also records candidates into `state.candidates` for later backtesting.
pub fn scan(
    state: &mut MarketState,
    fees: &AhFeeConfig,
    cfg: &FlipConfig,
    fv_cfg: &FairValueConfig,
    strategy: &dyn FlipScoreStrategy,
    names: &dyn Fn(&str) -> String,
    now_ms: i64,
) -> Vec<FlipOpportunity> {
    let mut out = Vec::new();
    let mut candidates = Vec::new();
    let keys: Vec<String> = state.bin_by_comparable.keys().cloned().collect();
    for key in keys {
        let listings = state.bin_listings(&key);
        if listings.is_empty() {
            continue;
        }
        let sales = state.sales_for(&key);
        if sales.is_empty() {
            continue; // never flip without realised-price evidence
        }
        let all_prices: Vec<f64> = listings.iter().map(|l| l.unit_price()).collect();
        for (i, listing) in listings.iter().enumerate().take(cfg.listings_per_key) {
            if listing.soulbound {
                continue;
            }
            let buy = listing.price;
            let unit_buy = listing.unit_price();
            let others: Vec<f64> = all_prices.iter().enumerate().filter(|(j, _)| *j != i).map(|(_, p)| *p).collect();
            let fair = fair_value::compute(&key, &listing.market_id, &others, &sales, now_ms, state.history_since_ms, fv_cfg);
            let Some(value) = fair.value else { continue };
            let next_other = others.iter().copied().filter(|p| *p >= unit_buy).fold(f64::INFINITY, f64::min);
            // A falling market is planned at a lower price: half of the 24h drop, at most -20%.
            let falling = fair.trend.filter(|t| *t < -0.05);
            let planned = value * (1.0 + falling.map(|t| (t * 0.5).max(-0.2)).unwrap_or(0.0));
            let unit_sell = if next_other.is_finite() { planned.min(next_other * 0.99) } else { planned };
            let count = listing.count.max(1) as f64;
            let sell_total = unit_sell * count;
            if sell_total <= buy {
                continue;
            }
            let f = fees.bin_fees(sell_total, cfg.listing_hours);
            let profit = sell_total - f.total() - buy;
            let roi = profit / buy;
            if profit < cfg.min_profit || roi < cfg.min_roi {
                continue;
            }
            let Some(sell_minutes) = fair.expected_sell_minutes else { continue };
            let position = if next_other.is_finite() && unit_sell <= next_other { 1.0 } else { 0.6 };
            let p_sale = (fair.liquidity * position * (0.5 + 0.5 * fair.confidence.score)).clamp(0.05, 0.98);
            let manipulation = manipulation::check(&listings, &sales, &fair, unit_buy, now_ms);
            let risk = risk_level(fair.volatility.unwrap_or(0.5), fair.confidence.score, manipulation.suspicious, buy);
            let mut confidence = fair.confidence.clone();
            if manipulation.suspicious {
                confidence = Confidence::from_score(confidence.score * 0.3, [confidence.reasons.clone(), manipulation.flags.clone()].concat());
            }
            if let Some(t) = falling {
                let mut reasons = confidence.reasons.clone();
                reasons.push(format!("Price falling ({:.0}% in 24h): planned lower", t * 100.0));
                confidence = Confidence::from_score(confidence.score * 0.85, reasons);
            }
            // Snipers take real bargains within minutes; a cheap BIN that sits for long usually
            // differs in something the fingerprint does not see.
            let unsold_minutes = ((now_ms - listing.start_ms).max(0) / 60_000) as f64;
            if listing.start_ms > 0 && unsold_minutes > STALE_BARGAIN_MINUTES && unit_buy < value * 0.8 {
                let mut reasons = confidence.reasons.clone();
                reasons.push(format!("Cheap listing unsold for {unsold_minutes:.0} min: check the item closely"));
                confidence = Confidence::from_score(confidence.score * 0.6, reasons);
            }
            let mut opp = FlipOpportunity {
                auction_id: listing.auction_id.clone(),
                item_id: listing.market_id.clone(),
                item_name: if listing.name.is_empty() { names(&listing.market_id) } else { listing.name.clone() },
                price_key: key.clone(),
                rarity: listing.rarity.clone(),
                modifiers: listing.modifiers.clone(),
                buy_price: buy,
                fair_value: value * count,
                recommended_sell: sell_total,
                max_buy_price: fees.max_buy_price(sell_total, cfg.listing_hours, cfg.min_profit, cfg.min_roi),
                fees: FeeBreakdown { listing_fee: f.listing_fee, duration_fee: f.duration_fee, claim_tax: f.claim_tax, total: f.total() },
                expected_net_profit: profit,
                roi,
                probability_of_sale: p_sale,
                expected_sell_minutes: Range { low: sell_minutes.low, high: sell_minutes.high },
                risk,
                manipulation,
                confidence,
                score: 0.0,
                listed_at_ms: listing.start_ms,
                seen_at_ms: now_ms,
                comparables: fair.comparables_used,
                view_command: format!("/viewauction {}", listing.auction_id),
            };
            opp.score = strategy.score(&opp);
            candidates.push(CandidateRecord {
                ts_ms: now_ms,
                comparable_key: key.clone(),
                market_id: listing.market_id.clone(),
                buy_price: buy,
                recommended_sell: sell_total,
                fair_value: value * count,
                roi,
                expected_profit: profit,
                sales_per_day: fair.sales_per_day,
                confidence: opp.confidence.score,
            });
            out.push(opp);
        }
    }
    for c in candidates {
        state.record_candidate(c);
    }
    out.sort_by(|a, b| b.score.total_cmp(&a.score).then(b.expected_net_profit.total_cmp(&a.expected_net_profit)));
    out
}

/// Apply user filters (budget, profit, ROI, confidence, risk, text search).
pub fn filter<'a>(flips: &'a [FlipOpportunity], q: &FlipQuery) -> Vec<&'a FlipOpportunity> {
    let search = q.search.as_deref().map(str::to_lowercase).filter(|s| !s.is_empty());
    let limit = q.limit.unwrap_or(100).min(500) as usize;
    flips
        .iter()
        .filter(|f| q.max_budget.is_none_or(|b| f.buy_price <= b))
        .filter(|f| q.min_profit.is_none_or(|p| f.expected_net_profit >= p))
        .filter(|f| q.min_roi.is_none_or(|r| f.roi >= r))
        .filter(|f| q.min_confidence.is_none_or(|c| f.confidence.score >= c))
        .filter(|f| q.max_risk.is_none_or(|r| f.risk <= r))
        .filter(|f| search.as_ref().is_none_or(|s| f.item_name.to_lowercase().contains(s) || f.item_id.to_lowercase().contains(s)))
        .take(limit)
        .collect()
}
