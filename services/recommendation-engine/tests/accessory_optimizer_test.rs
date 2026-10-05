mod common;

use std::collections::HashSet;

use skypilot_reco::Lang;
use skypilot_reco::providers::accessories::{candidates, plan_to};

#[test]
fn candidates_are_ranked_by_coins_per_mp_and_exclude_owned() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let owned: HashSet<String> = w.inv.accessories.iter().filter_map(|o| o.item.skyblock_id.clone()).collect();
    let c = candidates(&ctx);
    for x in &c {
        assert!(x.mp_gain > 0);
        if x.kind == "NEW" {
            assert!(!owned.contains(&x.id), "{} already in the bag", x.id);
        }
    }
    assert!(c.windows(2).all(|p| p[0].coins_per_mp <= p[1].coins_per_mp), "sorted by coins/MP");
}

#[test]
fn plan_reaches_target_greedily_one_per_family() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let current = ctx.magical_power().unwrap();
    let (plan, cost, reached) = plan_to(&ctx, Some(current + 30), f64::MAX);
    let families: HashSet<&String> = plan.iter().map(|p| &p.family).collect();
    assert_eq!(families.len(), plan.len(), "never two tiers of the same family");
    assert_eq!(reached, current + plan.iter().map(|p| p.mp_gain).sum::<u32>());
    assert!((cost - plan.iter().map(|p| p.cost).sum::<f64>()).abs() < 1e-6);
    if !plan.is_empty() {
        assert!(reached >= current + 30 || plan.len() == candidates(&ctx).len());
    }
    // budget is respected
    let (_, c2, _) = plan_to(&ctx, Some(current + 1000), 1_000_000.0);
    assert!(c2 <= 1_000_000.0);
}
