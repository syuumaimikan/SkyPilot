mod common;

use std::collections::BTreeMap;

use skypilot_reco::Lang;
use skypilot_reco::providers::mining::powder_plans;

#[test]
fn remaining_and_time_are_computed_from_current_powder() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let mining = w.analysis.mining.as_ref().unwrap();
    let current_gem = mining.gemstone_powder.as_ref().unwrap().available;
    let mut targets = BTreeMap::new();
    targets.insert("GEMSTONE".to_string(), current_gem + 8_000_000.0);
    targets.insert("MITHRIL".to_string(), 0.0);
    let plans = powder_plans(&ctx, &targets);
    let gem = plans.iter().find(|p| p.powder == "GEMSTONE").unwrap();
    assert_eq!(gem.current, current_gem);
    assert_eq!(gem.remaining, 8_000_000.0);
    let rate = gem.per_hour.unwrap();
    assert!((gem.hours_remaining.unwrap() - 8_000_000.0 / rate).abs() < 1e-6);
    assert!(gem.assumptions.iter().any(|a| a.contains("LOW")), "unverified rates must be labelled");
    let mith = plans.iter().find(|p| p.powder == "MITHRIL").unwrap();
    assert_eq!(mith.remaining, 0.0, "target below current => nothing remaining");
}
