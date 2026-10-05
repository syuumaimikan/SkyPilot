mod common;

use skypilot_reco::Lang;
use skypilot_reco::providers::slayer::tier_economics;

#[test]
fn voidgloom_t4_ev_is_built_from_neu_chances_and_market_prices() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let def = w.registry.curated.slayer("enderman").unwrap();
    let e = tier_economics(&ctx, def, 4, Some(3));
    assert_eq!(e.boss_cost, 50_000.0);
    assert_eq!(e.xp_per_boss, 500.0);
    let jc = e.drops.iter().find(|d| d.item_id == "JUDGEMENT_CORE").unwrap();
    assert!((jc.chance - 0.000565).abs() < 1e-12);
    assert!(jc.rng_meter_eligible);
    // every priced drop's EV = chance x amount x unit value
    for d in &e.drops {
        if let (Some(u), Some(ev)) = (d.unit_value, d.expected_value) {
            assert!((ev - d.chance * d.amount * u).abs() < 1e-6, "{}", d.item_id);
        }
    }
    if let (Some(ev), Some(net)) = (e.expected_drop_value, e.expected_net_per_boss) {
        assert!((net - (ev - e.boss_cost)).abs() < 1e-6);
        let rare = e.rare_drop_ev.unwrap();
        assert!(rare <= ev + 1e-9);
    }
    assert!(e.bosses_per_hour.unwrap() > 0.0);
}

#[test]
fn xp_per_hour_scales_with_tier_xp() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let def = w.registry.curated.slayer("zombie").unwrap();
    let t1 = tier_economics(&ctx, def, 1, Some(3));
    let t5 = tier_economics(&ctx, def, 5, Some(3));
    assert_eq!(t1.xp_per_boss, 5.0);
    assert_eq!(t5.xp_per_boss, 1500.0);
    assert!(t5.xp_per_hour.unwrap() > t1.xp_per_hour.unwrap());
}
