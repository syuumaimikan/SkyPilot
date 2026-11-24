//! Flip calibration from SkyPilot's own record: every flip candidate the scanner produced is
//! checked against the comparable sales that happened afterwards. Per comparable key this gives
//! how often a flip like it really sold at the recommended price within the holding window, how
//! long that took and what it actually earned. The scanner blends these outcomes into its
//! probability of sale, sell time and confidence, so keys that keep failing sink and keys that
//! keep selling rise. Nothing here is guessed: without outcomes the model value stays as is.

use std::collections::{HashMap, HashSet, VecDeque};

use skypilot_calc::fees::AhFeeConfig;
use skypilot_calc::stats;
use skypilot_protocol::market::FlipTrackRecord;

use crate::state::{CandidateRecord, SaleRecord};

/// How long a flip may take to sell before it counts as a miss.
pub const HOLD_MS: i64 = 24 * 3_600_000;
/// Outcomes older than this are ignored (the market moves on).
pub const WINDOW_MS: i64 = 14 * 24 * 3_600_000;
/// Recompute at most this often.
pub const REFRESH_MS: i64 = 15 * 60_000;
/// Weight of the model's own estimate, in "virtual trials" (Beta prior strength).
pub const PRIOR_TRIALS: f64 = 4.0;

#[derive(Debug, Clone, Default, PartialEq)]
pub struct KeyOutcome {
    pub trials: u32,
    pub hits: u32,
    /// Minutes until the first comparable sale at or above target, per hit.
    pub hit_minutes: Vec<f64>,
    /// Net profit actually reached per trial (hit at target, otherwise forced exit).
    pub realized_profit: Vec<f64>,
    /// Sum of the model's own p(sale) over trials that recorded it, and how many did.
    pub model_p_sum: f64,
    pub model_p_n: u32,
}

#[derive(Debug, Clone, Default)]
pub struct Calibration {
    pub by_key: HashMap<String, KeyOutcome>,
    /// Observed hits / model-expected hits over all keys (1.0 = the model is right on average).
    pub global_ratio: Option<f64>,
    pub global_trials: u32,
    pub global_hits: u32,
    pub computed_ms: i64,
    pub window_hours: f64,
}

impl KeyOutcome {
    pub fn hit_rate(&self) -> f64 {
        if self.trials == 0 { 0.0 } else { self.hits as f64 / self.trials as f64 }
    }
}

/// Outcome of one candidate: (sold at target?, minutes to that sale, realised net profit).
fn outcome(c: &CandidateRecord, later: &[SaleRecord], fees: &AhFeeConfig) -> (bool, f64, f64) {
    let count = c.count.max(1) as f64;
    let unit_target = c.recommended_sell / count;
    match later.iter().find(|s| s.unit_price >= unit_target * 0.999) {
        Some(s) => (true, (s.ts_ms - c.ts_ms) as f64 / 60_000.0, fees.bin_net_proceeds(c.recommended_sell, 24.0) - c.buy_price),
        None => {
            let prices: Vec<f64> = later.iter().map(|s| s.unit_price).collect();
            // never traded again: assume a 10% loss to get rid of it (conservative)
            let exit = stats::median(&prices).map(|m| m * count).unwrap_or(c.buy_price * 0.9);
            (false, HOLD_MS as f64 / 60_000.0, fees.bin_net_proceeds(exit, 24.0) - c.buy_price)
        }
    }
}

/// Builds the calibration from recorded candidates and the sales history.
pub fn compute(candidates: &VecDeque<CandidateRecord>, sales: &HashMap<String, VecDeque<SaleRecord>>, fees: &AhFeeConfig, now_ms: i64) -> Calibration {
    let mut sorted: HashMap<&str, Vec<SaleRecord>> = HashMap::new();
    let mut seen: HashSet<(&str, i64)> = HashSet::new();
    let mut by_key: HashMap<String, KeyOutcome> = HashMap::new();
    let mut earliest = now_ms;
    for c in candidates {
        let age = now_ms - c.ts_ms;
        if age < HOLD_MS || age > WINDOW_MS {
            continue; // outcome not known yet, or too old
        }
        // the same listing shows up on every scan: one trial per key and hour
        if !seen.insert((c.comparable_key.as_str(), c.ts_ms / 3_600_000)) {
            continue;
        }
        let list = sorted.entry(c.comparable_key.as_str()).or_insert_with(|| {
            let mut v: Vec<SaleRecord> = sales.get(&c.comparable_key).map(|q| q.iter().copied().collect()).unwrap_or_default();
            v.sort_by_key(|s| s.ts_ms);
            v
        });
        let start = list.partition_point(|s| s.ts_ms <= c.ts_ms);
        let end = list.partition_point(|s| s.ts_ms <= c.ts_ms + HOLD_MS);
        let (hit, minutes, profit) = outcome(c, &list[start..end], fees);
        let o = by_key.entry(c.comparable_key.clone()).or_default();
        o.trials += 1;
        if hit {
            o.hits += 1;
            o.hit_minutes.push(minutes);
        }
        if profit.is_finite() {
            o.realized_profit.push(profit);
        }
        if let Some(p) = c.p_sale.filter(|p| p.is_finite()) {
            o.model_p_sum += p;
            o.model_p_n += 1;
        }
        earliest = earliest.min(c.ts_ms);
    }
    let (trials, hits) = by_key.values().fold((0u32, 0u32), |a, o| (a.0 + o.trials, a.1 + o.hits));
    // hits observed vs hits the model expected, over trials that recorded the model's p
    let (expected, observed) = by_key.values().fold((0.0, 0.0), |a, o| {
        if o.model_p_n == o.trials && o.trials > 0 { (a.0 + o.model_p_sum, a.1 + o.hits as f64) } else { a }
    });
    let global_ratio = (expected >= 20.0).then(|| (observed / expected).clamp(0.3, 1.5));
    Calibration {
        by_key,
        global_ratio,
        global_trials: trials,
        global_hits: hits,
        computed_ms: now_ms,
        window_hours: if trials > 0 { (now_ms - earliest) as f64 / 3_600_000.0 } else { 0.0 },
    }
}

/// What the scanner applies to one opportunity.
#[derive(Debug, Clone, PartialEq)]
pub struct Adjustment {
    pub p_sale: f64,
    /// (low, high) minutes from observed hits, when there are enough of them.
    pub sell_minutes: Option<(f64, f64)>,
    /// Multiplier for the confidence score and the reason to show.
    pub confidence_factor: f64,
    pub reason: Option<String>,
    pub track: Option<FlipTrackRecord>,
}

impl Calibration {
    /// Blend the model's p(sale) with the key's observed outcomes (Beta prior of
    /// [`PRIOR_TRIALS`] virtual trials at the model value, itself scaled by the global ratio).
    pub fn adjust(&self, key: &str, model_p: f64) -> Adjustment {
        let prior = (model_p * self.global_ratio.unwrap_or(1.0)).clamp(0.02, 0.98);
        let Some(o) = self.by_key.get(key).filter(|o| o.trials > 0) else {
            return Adjustment { p_sale: prior, sell_minutes: None, confidence_factor: 1.0, reason: None, track: None };
        };
        let n = o.trials as f64;
        let p = ((o.hits as f64 + PRIOR_TRIALS * prior) / (n + PRIOR_TRIALS)).clamp(0.02, 0.98);
        let mut minutes = o.hit_minutes.clone();
        minutes.sort_by(|a, b| a.total_cmp(b));
        let sell_minutes = (minutes.len() >= 3).then(|| {
            (stats::percentile_sorted(&minutes, 0.25).unwrap_or(minutes[0]), stats::percentile_sorted(&minutes, 0.75).unwrap_or(minutes[minutes.len() - 1]))
        });
        let avg_profit = if o.realized_profit.is_empty() { None } else { Some(o.realized_profit.iter().sum::<f64>() / o.realized_profit.len() as f64) };
        // confidence follows the record once there are a few trials: 0.55x for a key that never
        // sells at target, up to 1.15x for one that always does
        let weight = (n / (n + PRIOR_TRIALS)).min(1.0);
        let confidence_factor = 1.0 + weight * (o.hit_rate() * 0.6 - 0.45);
        let reason = Some(if avg_profit.is_some_and(|p| p < 0.0) && o.trials >= 3 {
            format!("Track record: {}/{} similar flips sold at target in 24h; on average they lost coins", o.hits, o.trials)
        } else {
            format!("Track record: {}/{} similar flips sold at target in 24h (SkyPilot's own data)", o.hits, o.trials)
        });
        Adjustment {
            p_sale: p,
            sell_minutes,
            confidence_factor: confidence_factor.clamp(0.5, 1.15),
            reason,
            track: Some(FlipTrackRecord {
                trials: o.trials,
                hits: o.hits,
                hit_rate: o.hit_rate(),
                median_sell_minutes: stats::median(&o.hit_minutes),
                average_realized_profit: avg_profit,
                window_hours: self.window_hours,
            }),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cand(key: &str, ts: i64, buy: f64, sell: f64, count: u32, p: Option<f64>) -> CandidateRecord {
        CandidateRecord {
            ts_ms: ts,
            comparable_key: key.into(),
            market_id: key.into(),
            buy_price: buy,
            recommended_sell: sell,
            fair_value: sell,
            roi: 0.2,
            expected_profit: sell - buy,
            sales_per_day: 10.0,
            confidence: 0.8,
            count,
            p_sale: p,
        }
    }

    fn sale(price: f64, ts: i64) -> SaleRecord {
        SaleRecord { unit_price: price, ts_ms: ts, bin: true }
    }

    #[test]
    fn hits_misses_and_stacks_are_judged_per_unit() {
        let h = 3_600_000;
        let now = 100 * h;
        let mut cands = VecDeque::new();
        // A: sells at target 2h later
        cands.push_back(cand("A", 10 * h, 1_000_000.0, 1_500_000.0, 1, Some(0.6)));
        // same key, same hour: counted once
        cands.push_back(cand("A", 10 * h + 60_000, 1_000_000.0, 1_500_000.0, 1, Some(0.6)));
        // B: only sells below target -> miss, exit at median
        cands.push_back(cand("B", 20 * h, 1_000_000.0, 2_000_000.0, 1, Some(0.6)));
        // C: a stack of 4 at 400k each; a unit sale at 400k is a hit
        cands.push_back(cand("C", 30 * h, 1_000_000.0, 1_600_000.0, 4, None));
        // D: too recent to judge
        cands.push_back(cand("D", now - h, 1.0, 2.0, 1, None));
        let mut sales: HashMap<String, VecDeque<SaleRecord>> = HashMap::new();
        sales.insert("A".into(), VecDeque::from(vec![sale(1_500_000.0, 12 * h)]));
        sales.insert("B".into(), VecDeque::from(vec![sale(1_100_000.0, 21 * h), sale(1_300_000.0, 22 * h)]));
        sales.insert("C".into(), VecDeque::from(vec![sale(400_000.0, 31 * h)]));
        let cal = compute(&cands, &sales, &AhFeeConfig::default(), now);
        assert_eq!(cal.global_trials, 3);
        assert_eq!(cal.global_hits, 2);
        let a = &cal.by_key["A"];
        assert_eq!((a.trials, a.hits), (1, 1));
        assert_eq!(a.hit_minutes, vec![120.0]);
        let b = &cal.by_key["B"];
        assert_eq!((b.trials, b.hits), (1, 0));
        assert!(b.realized_profit[0] > 0.0 && b.realized_profit[0] < 1_000_000.0);
        assert_eq!(cal.by_key["C"].hits, 1);
        assert!(!cal.by_key.contains_key("D"));
    }

    #[test]
    fn record_moves_probability_and_confidence() {
        let mut cal = Calibration::default();
        cal.by_key.insert("GOOD".into(), KeyOutcome { trials: 12, hits: 12, hit_minutes: vec![10.0; 12], realized_profit: vec![500_000.0; 12], ..Default::default() });
        cal.by_key.insert("BAD".into(), KeyOutcome { trials: 12, hits: 0, realized_profit: vec![-200_000.0; 12], ..Default::default() });
        let good = cal.adjust("GOOD", 0.5);
        let bad = cal.adjust("BAD", 0.5);
        let none = cal.adjust("NEW", 0.5);
        assert!(good.p_sale > 0.85, "{good:?}");
        assert!(bad.p_sale < 0.15, "{bad:?}");
        assert_eq!(none.p_sale, 0.5);
        assert!(good.confidence_factor > 1.0 && bad.confidence_factor < 0.7);
        assert_eq!(good.sell_minutes, Some((10.0, 10.0)));
        assert!(bad.reason.unwrap().contains("lost"));
        assert_eq!(good.track.unwrap().hits, 12);
        // a model that is too optimistic everywhere is scaled down for unseen keys too
        cal.global_ratio = Some(0.5);
        assert_eq!(cal.adjust("NEW", 0.6).p_sale, 0.3);
    }
}
