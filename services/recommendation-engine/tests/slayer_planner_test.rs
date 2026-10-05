mod common;

use skypilot_reco::Lang;
use skypilot_reco::providers::slayer::{gear_status, plan, unlocked};

#[test]
fn plans_to_target_level_with_boss_counts() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let def = w.registry.curated.slayer("enderman").unwrap();
    // The sample is Enderman 9 (maxed), so planning to 9 needs nothing.
    let p = plan(&ctx, def, 9).unwrap();
    assert_eq!(p.xp_remaining, 0.0);
    assert_eq!(p.bosses_needed, Some(0.0));
    assert_eq!(p.tiers.len(), 4);
    assert!(p.gear.iter().any(|g| g.slot == "weapon" && g.tier == "LUXURY"));
}

#[test]
fn boss_math_is_consistent() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    // vampire has its own XP table: plan a hypothetical target above current via a fresh slayer
    for def in &w.registry.curated.slayers {
        let table = w.registry.neu.leveling.slayer_table(&def.id).unwrap();
        let target = table.max_level();
        let p = plan(&ctx, def, target).unwrap();
        let best = p.tiers.iter().find(|t| t.tier == p.recommended_tier).unwrap();
        if p.xp_remaining > 0.0 {
            let bosses = p.bosses_needed.unwrap();
            assert!(bosses * best.xp_per_boss >= p.xp_remaining - 1e-6);
            assert_eq!(p.estimated_cost, Some(bosses * best.boss_cost));
        }
        assert!(unlocked(&ctx, def, 1), "late-game sample has every slayer unlocked: {}", def.id);
        let (tier, recs) = gear_status(&ctx, def);
        assert!(!recs.is_empty());
        let _ = tier;
    }
}

#[test]
fn long_goal_moves_up_tiers_instead_of_grinding_t1() {
    // A fresh Enderman slayer (only T1 unlocked) aiming for level 7: the current-gear plan is
    // T1 only, but the long-goal projection climbs T1 → T2 → T3 with one unlock kill each and
    // earns the bulk of the XP at the higher tier (live case, Oct 2026: "~20,000 × T1").
    let w = common::world();
    let mut analysis = w.analysis.clone();
    if let skypilot_protocol::common::Known::Known { value } = &mut analysis.slayers {
        let e = value.iter_mut().find(|s| s.id == "enderman").unwrap();
        e.level.xp = 0.0;
        e.level.level = 0;
        e.kills_by_tier = vec![0; e.kills_by_tier.len()];
        e.highest_tier_killed = None;
    }
    let analysis: &'static _ = Box::leak(Box::new(analysis));
    let ctx = common::ctx_with(w, analysis, Lang::En);
    let def = w.registry.curated.slayer("enderman").unwrap();
    let p = plan(&ctx, def, 7).unwrap();
    assert_eq!(p.recommended_tier, 1, "only T1 is unlocked right now");
    let pr = p.projection.as_ref().expect("projection");
    assert!(pr.tier > 1);
    let unlock: Vec<u8> = pr.stages.iter().filter(|s| s.tier < pr.tier).map(|s| s.tier).collect();
    assert_eq!(unlock, (1..pr.tier).collect::<Vec<_>>(), "one unlock kill per lower tier");
    assert!(pr.stages.iter().map(|s| s.xp).sum::<f64>() >= p.xp_remaining - 1e-6);
    assert!(pr.bosses < p.bosses_needed.unwrap() / 10.0, "{} vs {:?}", pr.bosses, p.bosses_needed);
    let (now, proj) = (p.estimated_hours.unwrap(), pr.hours.unwrap());
    assert!(proj.high < now.low);
    // Level 1 (10 XP) is reached by the unlock kills alone: no "move up" projection.
    assert!(plan(&ctx, def, 1).unwrap().projection.is_none());
}
