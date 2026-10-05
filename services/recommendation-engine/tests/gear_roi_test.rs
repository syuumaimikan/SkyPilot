mod common;

use skypilot_calc::roi::{Candidate, allocate_budget, gain_per_million};
use skypilot_reco::Lang;
use skypilot_reco::providers::gear::plan;

#[test]
fn cheap_upgrade_beats_expensive_marginal_one() {
    // Spec example: 10M for +8% vs 200M for +11%
    let c = vec![
        Candidate { id: "u1".into(), slot: "armor".into(), cost: 10_000_000.0, gain: 8.0, resale: 0.0 },
        Candidate { id: "u2".into(), slot: "armor".into(), cost: 200_000_000.0, gain: 11.0, resale: 0.0 },
    ];
    assert_eq!(allocate_budget(&c, 100_000_000.0), vec!["u1".to_string()]);
    assert!(gain_per_million(8.0, 10_000_000.0).unwrap() > gain_per_million(11.0, 200_000_000.0).unwrap());
}

#[test]
fn resale_value_lowers_effective_cost() {
    let c = vec![
        Candidate { id: "a".into(), slot: "x".into(), cost: 50.0, gain: 10.0, resale: 0.0 },
        Candidate { id: "b".into(), slot: "y".into(), cost: 100.0, gain: 10.0, resale: 90.0 },
    ];
    // b's effective cost is 10 -> it is picked first; both fit the budget
    assert_eq!(allocate_budget(&c, 150.0), vec!["a".to_string(), "b".to_string()]);
}

#[test]
fn gear_plan_respects_budget_and_slots() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    for purpose in ["mining", "farming", "fishing", "combat", "f7_mage"] {
        let p = plan(&ctx, purpose, Some(200_000_000.0));
        assert!(p.total_cost <= 200_000_000.0 + 1.0, "{purpose}");
        let mut slots = std::collections::HashSet::new();
        for s in &p.selected {
            assert!(slots.insert(s.slot.clone()), "one item per slot ({purpose})");
            assert!(s.gain > 0.0);
        }
    }
}
