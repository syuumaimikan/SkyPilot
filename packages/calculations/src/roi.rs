//! Return-on-investment helpers shared by every upgrade comparison.

use std::collections::HashSet;

pub fn roi(profit: f64, capital: f64) -> Option<f64> {
    crate::safe_div(profit, capital).filter(|_| capital > 0.0)
}

/// Days until `cost` is recovered by `income_per_day`.
pub fn payback_days(cost: f64, income_per_day: f64) -> Option<f64> {
    if !cost.is_finite() || cost < 0.0 || !income_per_day.is_finite() || income_per_day <= 0.0 {
        return None;
    }
    Some(cost / income_per_day)
}

/// Performance gained per million coins spent.
pub fn gain_per_million(gain: f64, cost: f64) -> Option<f64> {
    if !gain.is_finite() || !cost.is_finite() || cost < 0.0 {
        return None;
    }
    if cost == 0.0 {
        return if gain > 0.0 { Some(f64::MAX) } else { None };
    }
    Some(gain / (cost / 1_000_000.0))
}

/// An upgrade option for budget allocation.
#[derive(Debug, Clone, PartialEq)]
pub struct Candidate {
    pub id: String,
    /// Options sharing a slot are mutually exclusive (e.g. two helmets).
    pub slot: String,
    pub cost: f64,
    /// Value gained (any single comparable unit, e.g. Mining Fortune, % performance).
    pub gain: f64,
    /// Value recovered on resale (reduces the effective cost).
    pub resale: f64,
}

/// Greedy allocation by marginal gain per effective coin with slot exclusivity.
/// Upgrading within a slot replaces the previously chosen option (marginal = Δgain/Δcost).
pub fn allocate_budget(candidates: &[Candidate], budget: f64) -> Vec<String> {
    let budget = if budget.is_finite() { budget.max(0.0) } else { 0.0 };
    let valid: Vec<&Candidate> = candidates
        .iter()
        .filter(|c| c.cost.is_finite() && c.cost >= 0.0 && c.gain.is_finite() && c.gain > 0.0)
        .collect();
    let mut chosen: std::collections::HashMap<String, &Candidate> = std::collections::HashMap::new();
    let mut spent = 0.0;
    let mut exhausted: HashSet<String> = HashSet::new();
    loop {
        let mut best: Option<(&Candidate, f64, f64)> = None; // (candidate, efficiency, delta_cost)
        for c in &valid {
            if exhausted.contains(&c.id) {
                continue;
            }
            let current = chosen.get(&c.slot);
            let (dc, dg) = match current {
                Some(cur) if cur.id == c.id => continue,
                Some(cur) => (effective_cost(c) - effective_cost(cur), c.gain - cur.gain),
                None => (effective_cost(c), c.gain),
            };
            if dg <= 0.0 {
                continue;
            }
            let real_delta = c.cost - current.map(|cur| cur.cost).unwrap_or(0.0);
            if spent + real_delta.max(0.0) > budget + 1e-6 {
                continue;
            }
            let eff = if dc <= 0.0 { f64::MAX } else { dg / dc };
            if best.map(|(_, e, _)| eff > e).unwrap_or(true) {
                best = Some((c, eff, real_delta));
            }
        }
        match best {
            Some((c, _, delta)) => {
                spent += delta.max(0.0);
                exhausted.insert(c.id.clone());
                chosen.insert(c.slot.clone(), c);
            }
            None => break,
        }
    }
    let mut ids: Vec<String> = chosen.values().map(|c| c.id.clone()).collect();
    ids.sort();
    ids
}

fn effective_cost(c: &Candidate) -> f64 {
    let resale = if c.resale.is_finite() { c.resale.clamp(0.0, c.cost) } else { 0.0 };
    (c.cost - resale).max(0.0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prefers_efficient_upgrade() {
        let c = vec![
            Candidate { id: "A".into(), slot: "chest".into(), cost: 6_000_000.0, gain: 37.0, resale: 0.0 },
            Candidate { id: "B".into(), slot: "chest".into(), cost: 120_000_000.0, gain: 43.0, resale: 0.0 },
        ];
        assert_eq!(allocate_budget(&c, 10_000_000.0), vec!["A".to_string()]);
        // With a large budget the greedy upgrade path may still step up to B.
        assert_eq!(allocate_budget(&c, 200_000_000.0), vec!["B".to_string()]);
        assert_eq!(payback_days(14_200_000.0, 820_000.0).map(|d| (d * 10.0).round() / 10.0), Some(17.3));
    }
}
