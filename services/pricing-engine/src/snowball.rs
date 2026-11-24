//! Snowball plan for AH flipping: pick a diversified basket of today's flips that fits the
//! capital, then project reinvesting the expected value cycle after cycle. Probabilities are the
//! calibrated ones (see [`crate::calibration`]); the market's depth caps how much capital can be
//! put to work, so the curve flattens instead of growing forever. It is a model with stated
//! assumptions, never a promise, and it never buys or lists anything.

use std::collections::HashSet;

use skypilot_protocol::common::{Freshness, RiskLevel};
use skypilot_protocol::market::{FlipOpportunity, SnowballDay, SnowballMilestone, SnowballPick, SnowballPlan};

/// Largest share of the capital one flip may take.
pub const MAX_SHARE: f64 = 0.5;
/// Kept back for listing fees (paid when listing, before the sale).
pub const RESERVE_SHARE: f64 = 0.1;
/// Of the coins the market offers per cycle, the share one player can realistically buy
/// (snipers and other flippers take the rest).
pub const MARKET_SHARE: f64 = 0.15;
/// Time per cycle beyond the sale itself: finding, buying, listing, collecting.
pub const OVERHEAD_HOURS: f64 = 0.5;
pub const MIN_CONFIDENCE: f64 = 0.45;

pub const MILESTONES: [f64; 9] = [1e6, 5e6, 10e6, 25e6, 50e6, 100e6, 250e6, 500e6, 1e9];

/// Flips worth putting money in: not suspicious, at most high risk, some confidence, positive EV.
fn eligible(f: &FlipOpportunity) -> bool {
    !f.manipulation.suspicious && f.risk <= RiskLevel::High && f.confidence.score >= MIN_CONFIDENCE && expected_value(f) > 0.0
}

/// p x profit - (1 - p) x the cost of a miss: the fees already paid plus a 3% markdown to relist.
pub fn expected_value(f: &FlipOpportunity) -> f64 {
    let p = f.probability_of_sale.clamp(0.0, 1.0);
    let miss = f.fees.listing_fee + f.fees.duration_fee + f.buy_price * 0.03;
    p * f.expected_net_profit - (1.0 - p) * miss
}

fn sell_hours(f: &FlipOpportunity) -> f64 {
    (f.expected_sell_minutes.high / 60.0).clamp(0.1, 48.0)
}

/// Expected coins per coin and hour, the order in which the basket is filled.
fn efficiency(f: &FlipOpportunity) -> f64 {
    expected_value(f) / (f.buy_price.max(1.0) * (sell_hours(f) + OVERHEAD_HOURS))
}

/// Greedy basket: best expected value per coin-hour first, one flip per comparable key, each at
/// most [`MAX_SHARE`] of the capital, [`RESERVE_SHARE`] kept back.
pub fn basket(flips: &[FlipOpportunity], capital: f64) -> Vec<&FlipOpportunity> {
    let mut list: Vec<&FlipOpportunity> = flips.iter().filter(|f| eligible(f) && f.buy_price <= capital * MAX_SHARE).collect();
    list.sort_by(|a, b| efficiency(b).total_cmp(&efficiency(a)));
    let mut budget = capital * (1.0 - RESERVE_SHARE);
    let mut keys = HashSet::new();
    let mut out = Vec::new();
    for f in list {
        if f.buy_price <= budget && keys.insert(f.price_key.as_str()) {
            budget -= f.buy_price;
            out.push(f);
        }
    }
    out
}

/// Cycles a player can run per day: one needs to be online to buy and to relist, the sale
/// itself can happen offline (one overnight cycle).
pub fn cycles_per_day(cycle_hours: f64, hours_per_day: f64) -> f64 {
    if cycle_hours <= 0.0 {
        return 0.0;
    }
    (hours_per_day / cycle_hours + 1.0).min(24.0 / cycle_hours).max(0.0)
}

pub struct Inputs<'a> {
    pub flips: &'a [FlipOpportunity],
    pub capital: f64,
    pub hours_per_day: f64,
    pub days: u32,
    pub calibration_trials: u32,
    pub calibration_hits: u32,
    pub freshness: Freshness,
}

pub fn plan(i: Inputs) -> SnowballPlan {
    let capital = i.capital.max(0.0);
    let picks = basket(i.flips, capital);
    let deployed: f64 = picks.iter().map(|f| f.buy_price).sum();
    let ev: f64 = picks.iter().map(|f| expected_value(f)).sum();
    let return_per_cycle = if deployed > 0.0 { ev / deployed } else { 0.0 };
    // capital-weighted cycle length
    let cycle_hours = if deployed > 0.0 { picks.iter().map(|f| f.buy_price * (sell_hours(f) + OVERHEAD_HOURS)).sum::<f64>() / deployed } else { 0.0 };
    let cycles = cycles_per_day(cycle_hours, i.hours_per_day.clamp(0.0, 24.0));

    // what the market can absorb per cycle: all eligible flips listed now, one per key
    let mut seen = HashSet::new();
    let market_capacity: f64 = {
        let mut all: Vec<&FlipOpportunity> = i.flips.iter().filter(|f| eligible(f)).collect();
        all.sort_by(|a, b| efficiency(b).total_cmp(&efficiency(a)));
        all.into_iter().filter(|f| seen.insert(f.price_key.as_str())).map(|f| f.buy_price).sum()
    };
    let market_rate = {
        // return of the whole market (used once the player's capital outgrows today's basket)
        let mut seen = HashSet::new();
        let (v, c) = i.flips.iter().filter(|f| eligible(f) && seen.insert(f.price_key.as_str())).fold((0.0, 0.0), |a, f| (a.0 + expected_value(f), a.1 + f.buy_price));
        if c > 0.0 { v / c } else { 0.0 }
    };

    let days = i.days.clamp(1, 365);
    let mut projection = Vec::with_capacity(days as usize);
    let mut cap = capital;
    for day in 1..=days {
        let deploy = (cap * (1.0 - RESERVE_SHARE)).min(market_capacity * MARKET_SHARE).max(0.0);
        // today's basket earns its own return; coins beyond it go into the rest of the market,
        // which on average earns less
        let per_cycle = if deploy <= deployed { deploy * return_per_cycle } else { deployed * return_per_cycle + (deploy - deployed) * market_rate.min(return_per_cycle) };
        let profit = per_cycle * cycles;
        cap += profit;
        projection.push(SnowballDay { day, capital: cap, profit, deployed: deploy });
    }

    let milestones = MILESTONES
        .iter()
        .filter(|m| **m > capital * 0.5)
        .map(|&m| {
            let b = basket(i.flips, m);
            SnowballMilestone {
                capital: m,
                day: if m <= capital { Some(0) } else { projection.iter().find(|d| d.capital >= m).map(|d| d.day) },
                affordable_flips: i.flips.iter().filter(|f| eligible(f) && f.buy_price <= m * MAX_SHARE).map(|f| f.price_key.as_str()).collect::<HashSet<_>>().len() as u32,
                best_expected_value: b.iter().map(|f| expected_value(f)).fold(0.0, f64::max),
            }
        })
        .collect();

    let mut assumptions = vec![
        format!("One flip per item key, each at most {:.0}% of your coins; {:.0}% kept for listing fees.", MAX_SHARE * 100.0, RESERVE_SHARE * 100.0),
        "Expected value = P(sale) x net profit - (1 - P) x (fees + 3% markdown to relist). Net profit is after AH fees and claim tax.".into(),
        format!("Cycle = expected sell time + {OVERHEAD_HOURS}h to buy, list and collect; you play {:.1}h a day plus one overnight cycle.", i.hours_per_day),
        format!("You can buy about {:.0}% of what the market offers per cycle (others snipe the rest), so growth slows as coins pile up.", MARKET_SHARE * 100.0),
        "Prices, competition and Hypixel's AH rules change; this is a projection, not a guarantee. SkyPilot never buys or lists for you.".into(),
    ];
    if i.calibration_trials == 0 {
        assumptions.push("No track record yet: probabilities are the model's estimate only. They are calibrated automatically once SkyPilot has seen how past candidates sold.".into());
    }
    SnowballPlan {
        capital,
        hours_per_day: i.hours_per_day,
        days,
        picks: picks
            .iter()
            .map(|f| SnowballPick {
                auction_id: f.auction_id.clone(),
                item_id: f.item_id.clone(),
                item_name: f.item_name.clone(),
                price_key: f.price_key.clone(),
                buy_price: f.buy_price,
                recommended_sell: f.recommended_sell,
                expected_net_profit: f.expected_net_profit,
                probability_of_sale: f.probability_of_sale,
                expected_value: expected_value(f),
                sell_hours: sell_hours(f),
                view_command: f.view_command.clone(),
                track_record: f.track_record.clone(),
            })
            .collect(),
        deployed,
        reserve: capital - deployed,
        expected_profit_per_cycle: ev,
        return_per_cycle,
        cycle_hours,
        cycles_per_day: cycles,
        market_capacity,
        projection,
        milestones,
        calibration_trials: i.calibration_trials,
        calibration_hits: i.calibration_hits,
        assumptions,
        freshness: i.freshness,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use skypilot_protocol::common::{Confidence, Range};
    use skypilot_protocol::market::{FeeBreakdown, ManipulationCheck};

    fn flip(key: &str, buy: f64, profit: f64, p: f64, minutes: f64) -> FlipOpportunity {
        FlipOpportunity {
            auction_id: format!("{key}-{buy}"),
            item_id: key.into(),
            item_name: key.into(),
            price_key: key.into(),
            rarity: None,
            modifiers: vec![],
            buy_price: buy,
            fair_value: buy + profit,
            recommended_sell: buy + profit * 1.1,
            max_buy_price: buy,
            fees: FeeBreakdown { listing_fee: buy * 0.01, duration_fee: 1000.0, claim_tax: buy * 0.01, total: buy * 0.02 + 1000.0 },
            expected_net_profit: profit,
            roi: profit / buy,
            probability_of_sale: p,
            expected_sell_minutes: Range { low: minutes / 2.0, high: minutes },
            risk: RiskLevel::Low,
            manipulation: ManipulationCheck { suspicious: false, flags: vec![], verdict: String::new() },
            confidence: Confidence::from_score(0.8, vec![]),
            score: 0.0,
            listed_at_ms: 0,
            seen_at_ms: 0,
            comparables: 10,
            view_command: String::new(),
            track_record: None,
        }
    }

    #[test]
    fn basket_diversifies_and_respects_capital() {
        let flips = vec![
            flip("A", 4_000_000.0, 1_000_000.0, 0.9, 60.0),
            flip("A", 4_100_000.0, 1_000_000.0, 0.9, 60.0), // same key: skipped
            flip("B", 3_000_000.0, 500_000.0, 0.8, 120.0),
            flip("C", 6_000_000.0, 3_000_000.0, 0.9, 60.0), // > 50% of 10M
            flip("D", 1_000_000.0, 50_000.0, 0.1, 60.0),    // negative EV
        ];
        let b = basket(&flips, 10_000_000.0);
        let keys: Vec<&str> = b.iter().map(|f| f.price_key.as_str()).collect();
        assert_eq!(keys, ["A", "B"]);
        assert!(b.iter().map(|f| f.buy_price).sum::<f64>() <= 9_000_000.0);
    }

    #[test]
    fn projection_compounds_but_is_capped_by_the_market() {
        let flips: Vec<_> = (0..20).map(|i| flip(&format!("K{i}"), 2_000_000.0, 300_000.0, 0.8, 60.0)).collect();
        let p = plan(Inputs { flips: &flips, capital: 10_000_000.0, hours_per_day: 3.0, days: 60, calibration_trials: 0, calibration_hits: 0, freshness: Freshness::compute("AH", 0, 0, 60) });
        assert!(!p.picks.is_empty());
        assert!(p.return_per_cycle > 0.0);
        let last = p.projection.last().unwrap();
        assert!(last.capital > 10_000_000.0);
        // deployment never exceeds the share of the market one player can take
        assert!(p.projection.iter().all(|d| d.deployed <= p.market_capacity * MARKET_SHARE + 1.0));
        // growth is linear once capped: the last day's profit is not larger than the cap allows
        assert!(last.profit <= p.market_capacity * MARKET_SHARE * p.return_per_cycle.max(0.0) * p.cycles_per_day + 1.0);
        assert!(p.milestones.iter().any(|m| m.capital == 25e6));
        assert!(p.assumptions.iter().any(|a| a.contains("No track record")));
    }

    #[test]
    fn cycles_need_the_player_online() {
        assert_eq!(cycles_per_day(2.0, 0.0), 1.0);
        assert_eq!(cycles_per_day(2.0, 4.0), 3.0);
        assert_eq!(cycles_per_day(2.0, 24.0), 12.0);
        assert_eq!(cycles_per_day(0.0, 4.0), 0.0);
    }
}
