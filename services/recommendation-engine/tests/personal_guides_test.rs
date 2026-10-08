mod common;

use skypilot_profile::Inventories;
use skypilot_protocol::common::Known;
use skypilot_reco::{Ctx, Lang};
use skypilot_reco::providers::{Provider, dungeons, gear, money, pets};

#[test]
fn missing_inventory_never_proposes_purchases_or_claims_dungeon_readiness() {
    let world = common::world();
    let mut analysis = world.analysis.clone();
    analysis.gear = Known::unknown("Inventory API disabled");
    let inv = Inventories::default();
    let ctx = Ctx { analysis: &analysis, inv: &inv, ..common::ctx(world, Lang::Ja) };
    let plan = gear::plan(&ctx, "combat", Some(1e12));
    assert!(plan.selected.is_empty());
    for floor in &ctx.registry.curated.dungeons.floors {
        assert!(dungeons::readiness(&ctx, floor).0 <= 0.49);
    }
}

#[test]
fn empty_equipped_armour_and_no_weapons_cap_readiness() {
    let world = common::world();
    let mut analysis = world.analysis.clone();
    if let Known::Known { value } = &mut analysis.gear {
        value.armor.clear(); value.weapons.clear();
    }
    let ctx = Ctx { analysis: &analysis, ..common::ctx(world, Lang::En) };
    for floor in &ctx.registry.curated.dungeons.floors {
        assert!(dungeons::readiness(&ctx, floor).0 <= 0.69);
    }
}

#[test]
fn market_methods_are_not_eligible_for_restricted_modes() {
    let world = common::world();
    for mode in ["ironman", "bingo", "stranded"] {
        let mut analysis = world.analysis.clone(); analysis.game_mode = Some(mode.into());
        let ctx = Ctx { analysis: &analysis, ..common::ctx(world, Lang::En) };
        for method in money::rank(&ctx).iter().filter(|method| method.category == "Market") {
            assert!(!method.requirements_met, "{}: {}", mode, method.id);
            assert_eq!(method.score, 0.0);
        }
        assert!(money::rank(&ctx).iter().all(|method| method.coins_per_hour.is_none()));
    }
}

#[test]
fn gear_candidates_use_activity_appropriate_categories() {
    let world = common::world(); let ctx = common::ctx(world, Lang::En);
    for (purpose, forbidden) in [("archer", vec!["SWORD", "FISHING_ROD", "DRILL"]), ("mining", vec!["SWORD", "BOW", "FISHING_ROD"])] {
        let plan = gear::plan(&ctx, purpose, Some(1e12));
        for option in plan.selected.iter().chain(&plan.rejected) {
            let category = ctx.registry.item(&option.id).unwrap().category.as_deref().unwrap();
            assert!(!forbidden.contains(&category), "{}: {}", purpose, option.id);
        }
    }
}

#[test]
fn pet_guides_cover_activities_without_requiring_a_slayer_goal() {
    let world = common::world();
    let ctx = common::ctx(world, Lang::Ja);
    let report = pets::PetsProvider.report(&ctx, &[]);
    assert_eq!(report.sections.iter().filter(|s| s.id.starts_with("activity-")).count(), 8);
}
