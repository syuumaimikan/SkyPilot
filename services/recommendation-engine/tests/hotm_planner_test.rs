mod common;

use std::collections::HashSet;

use skypilot_reco::Lang;
use skypilot_reco::providers::hotm::{CostModel, path_to, plan, required_tier};

#[test]
fn neu_cost_expressions_match_known_values() {
    let w = common::world();
    let layout = w.registry.neu.hotm.as_ref().unwrap();
    let m = CostModel::new(layout, 0).unwrap();
    // Mining Speed: (level+2)^3 -> level 1->2 costs 27 mithril
    assert_eq!(m.step_cost("mining_speed", 1), Some(("MITHRIL".into(), 27.0)));
    // Core of the Mountain: list lookup with powder type switching by level
    assert_eq!(m.step_cost("core_of_the_mountain", 1), Some(("MITHRIL".into(), 50_000.0)));
    assert_eq!(m.step_cost("core_of_the_mountain", 4).unwrap().0, "GEMSTONE");
    let total = m.range_cost("mining_speed", 1, 50);
    let expected: f64 = (1..50).map(|l| ((l + 2) as f64).powi(3)).sum();
    assert_eq!(total["MITHRIL"], expected);
}

#[test]
fn tree_rules() {
    assert_eq!(required_tier(9), 1);
    assert_eq!(required_tier(0), 10);
    let w = common::world();
    let layout = w.registry.neu.hotm.as_ref().unwrap();
    let unlocked: HashSet<String> = ["mining_speed".to_string()].into_iter().collect();
    let p = path_to(layout, &unlocked, "efficient_miner", "mining_speed");
    assert_eq!(p.last().map(String::as_str), Some("efficient_miner"));
    // every step is adjacent to the previous one
    let mut prev = layout.perks.get("mining_speed").unwrap();
    for step in &p {
        let cur = layout.perks.get(step).unwrap();
        assert_eq!((cur.x - prev.x).abs() + (cur.y - prev.y).abs(), 1, "{step}");
        prev = cur;
    }
}

#[test]
fn plan_uses_current_tree_and_powder() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    for preset in ["progression", "powder", "gemstone", "mithril", "glacite", "commission", "money"] {
        let p = plan(&ctx, preset).expect(preset);
        assert_eq!(p.preset, preset);
        assert_eq!(p.presets.len(), 7);
        for u in &p.upgrades {
            assert!(u.to_level > u.from_level);
            assert!(u.cost >= 0.0);
        }
        // the sample has tens of millions of each powder: some upgrades must be affordable or the tree maxed
        assert!(p.upgrades.is_empty() || p.upgrades.iter().any(|u| u.affordable_now));
    }
}
