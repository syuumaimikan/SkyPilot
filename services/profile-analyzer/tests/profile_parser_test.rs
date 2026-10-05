//! Parses the anonymised real late-game profile fixture end to end.

use std::path::PathBuf;
use std::sync::Arc;

use serde_json::Value;
use skypilot_data::{DataPaths, Registry};
use skypilot_pricing::{MarketEngine, NoMarket};
use skypilot_profile::{AnalyzeError, AnalyzerInput, analyze, list_profiles, snapshot};
use skypilot_protocol::profile::{GameStage, PlayerRef};

fn root() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..")
}

fn registry() -> Arc<Registry> {
    Arc::new(Registry::load(&DataPaths::fixtures(&root())).unwrap())
}

fn profiles() -> Value {
    serde_json::from_slice(&std::fs::read(root().join("fixtures/hypixel/profiles_sample.json")).unwrap()).unwrap()
}

fn museum() -> Value {
    serde_json::from_slice(&std::fs::read(root().join("fixtures/hypixel/museum_sample.json")).unwrap()).unwrap()
}

fn member_uuid(p: &Value) -> String {
    p["profiles"][0]["members"].as_object().unwrap().keys().next().unwrap().clone()
}

fn input<'a>(p: &'a Value, m: Option<&'a Value>) -> AnalyzerInput<'a> {
    AnalyzerInput {
        player: PlayerRef { username: "SampleUser".into(), uuid: member_uuid(p) },
        profiles: p,
        profile_id: None,
        museum: m,
        garden: None,
        fetched_at_ms: 1_791_167_000_000,
        now_ms: 1_791_167_060_000,
    }
}

#[test]
fn analyzes_real_profile() {
    let reg = registry();
    let p = profiles();
    let (a, inv) = analyze(&input(&p, None), &reg, &NoMarket).expect("analysis");
    assert_eq!(a.cute_name, "Blueberry");
    assert!(inv.available);
    // Skills come from the Hypixel skills resource tables
    let skills = a.skills.as_ref().unwrap();
    let combat = skills.iter().find(|s| s.id == "COMBAT").unwrap();
    assert_eq!(combat.level, 60, "6.6B combat xp is maxed");
    assert!(a.skill_average.as_ref().unwrap() > &50.0);
    // Dungeons via NEU catacombs table
    let d = a.dungeons.as_ref().unwrap();
    assert_eq!(d.catacombs.level, 50);
    assert_eq!(d.highest_master_floor_completed, Some(7));
    assert!(d.floors.iter().any(|f| f.floor == "M7" && f.completions == 1260));
    // Slayers via NEU slayer_xp tables
    let sl = a.slayers.as_ref().unwrap();
    let zombie = sl.iter().find(|s| s.id == "zombie").unwrap();
    assert_eq!(zombie.level.level, 9);
    assert_eq!(zombie.kills_by_tier[4], 3861);
    let vamp = sl.iter().find(|s| s.id == "vampire").unwrap();
    assert_eq!(vamp.level.level, 5);
    // Mining powders are read, not guessed
    let mining = a.mining.as_ref().unwrap();
    assert_eq!(mining.mithril_powder.as_ref().unwrap().available, 22_899_793.0);
    assert!(mining.crystals_found.len() >= 5);
    // Accessories: MP computed from the bag and compared with Hypixel's stored value
    let acc = a.accessories.as_ref().unwrap();
    assert!(acc.accessories.len() > 50);
    assert!(acc.magical_power > 1000, "MP {}", acc.magical_power);
    let api = acc.highest_magical_power_api.unwrap() as f64;
    assert!((acc.magical_power as f64 - api).abs() / api < 0.15, "computed {} vs api {}", acc.magical_power, api);
    // Pets with levels from NEU tables
    let pets = a.pets.as_ref().unwrap();
    assert_eq!(pets.len(), 89);
    assert!(pets.iter().any(|p| p.level == 100));
    // Economy
    assert!(a.economy.purse > 3_000_000_000.0);
    assert!(a.economy.bank.is_known());
    // Kuudra / rift / chocolate
    assert_eq!(a.kuudra.as_ref().unwrap().highest_tier_completed.as_deref(), Some("infernal"));
    assert!(a.rift.as_ref().unwrap().enigma_souls > 40);
    assert!(a.chocolate_factory.as_ref().unwrap().unique_rabbits > 300);
    // Progression is multi-dimensional and recognises an end-game account
    assert!(matches!(a.progression.stage, GameStage::LateGame | GameStage::EndGame));
    assert!(a.progression.areas.iter().filter(|x| x.known).count() >= 8);
    // Without market data networth is still computed from purse/bank; items are unpriced
    let nw = a.networth.as_ref().unwrap();
    assert!(nw.liquid > 50_000_000_000.0);
    // Garden endpoint not supplied -> explicit Unknown instead of a guess
    assert!(!a.garden.is_known());
}

#[test]
fn museum_and_snapshot() {
    let reg = registry();
    let p = profiles();
    let m = museum();
    let (a, _) = analyze(&input(&p, Some(&m)), &reg, &NoMarket).unwrap();
    assert!(a.museum.is_known());
    assert!(a.museum.as_ref().unwrap().donated_count > 0);
    let s = snapshot(&a, 1);
    assert!(s.catacombs.unwrap() >= 50.0);
    assert!(s.magical_power.is_some());
}

#[test]
fn market_engine_prices_networth_items() {
    let reg = registry();
    let engine = MarketEngine::new(reg.clone());
    let bz: skypilot_hypixel::BazaarResponse = serde_json::from_slice(&std::fs::read(root().join("fixtures/hypixel/bazaar.json")).unwrap()).unwrap();
    engine.ingest_bazaar(bz);
    let p = profiles();
    let (a, _) = analyze(&input(&p, None), &reg, &engine).unwrap();
    let nw = a.networth.as_ref().unwrap();
    assert!(nw.by_category["Sacks"] > 0.0, "sacks priced from the bazaar");
    assert!(nw.total >= nw.liquid);
}

#[test]
fn disabled_inventory_api_is_reported_not_guessed() {
    let reg = registry();
    let mut p = profiles();
    let uuid = member_uuid(&p);
    p["profiles"][0]["members"][&uuid].as_object_mut().unwrap().remove("inventory");
    p["profiles"][0].as_object_mut().unwrap().remove("banking");
    let (a, _) = analyze(&input(&p, None), &reg, &NoMarket).unwrap();
    assert!(!a.accessories.is_known());
    assert!(!a.networth.is_known());
    assert!(!a.economy.bank.is_known());
    assert!(a.warnings.iter().any(|w| w.contains("Inventory API")));
}

#[test]
fn skill_tree_schema_without_hotm_is_locked_not_unknown() {
    // Shape of a live profile (Oct 2026) that never unlocked HOTM: no `mining_core`, and
    // `skill_tree` only carries foraging data.
    let reg = registry();
    let mut p = profiles();
    let uuid = member_uuid(&p);
    let m = p["profiles"][0]["members"][&uuid].as_object_mut().unwrap();
    m.remove("mining_core");
    m.insert(
        "skill_tree".into(),
        serde_json::json!({
            "nodes": {"mining": {}, "foraging": {"center_of_the_forest": 0, "toggle_center_of_the_forest": true}, "mining_2": {"core_of_the_mountain": 0}},
            "selected_ability": {}, "experience": {"foraging": 3320.0}, "tokens_spent": {"forest": 0}, "refund_ability_free": true
        }),
    );
    let (a, _) = analyze(&input(&p, None), &reg, &NoMarket).unwrap();
    let mining = a.mining.as_ref().unwrap();
    assert_eq!(mining.hotm.as_ref().unwrap().level, 0);
    assert_eq!(mining.mithril_powder.as_ref().unwrap().total, 0.0);
    assert_eq!(*mining.tokens_spent.as_ref().unwrap(), 0);
    assert_eq!(mining.nodes.as_ref().unwrap().len(), 0);

    // Unlocked on the new schema: XP from skill_tree, powder from mining_core.
    let m = p["profiles"][0]["members"][&uuid].as_object_mut().unwrap();
    m["skill_tree"]["experience"]["mining"] = serde_json::json!(15_000.0);
    m["skill_tree"]["nodes"]["mining"] = serde_json::json!({"mining_speed": 20, "toggle_mining_speed": false});
    m["skill_tree"]["tokens_spent"]["mountain"] = serde_json::json!(4);
    m.insert("mining_core".into(), serde_json::json!({"powder_mithril": 1200, "powder_spent_mithril": 800}));
    let (a, _) = analyze(&input(&p, None), &reg, &NoMarket).unwrap();
    let mining = a.mining.as_ref().unwrap();
    assert_eq!(mining.hotm.as_ref().unwrap().level, 3, "15k HOTM XP is tier 3");
    assert_eq!(mining.mithril_powder.as_ref().unwrap().total, 2000.0);
    assert_eq!(mining.gemstone_powder.as_ref().unwrap().total, 0.0);
    assert_eq!(*mining.tokens_spent.as_ref().unwrap(), 4);
    let node = &mining.nodes.as_ref().unwrap()[0];
    assert!(node.id == "mining_speed" && node.level == 20 && !node.enabled);
}

#[test]
fn profile_selection_errors() {
    let reg = registry();
    let p = profiles();
    assert_eq!(list_profiles(&p).len(), 1);
    let mut i = input(&p, None);
    i.profile_id = Some("00000000000000000000000000000000");
    assert!(matches!(analyze(&i, &reg, &NoMarket).unwrap_err(), AnalyzeError::ProfileNotFound(_)));
    let empty = serde_json::json!({"success": true, "profiles": null});
    let mut i2 = input(&p, None);
    i2.profiles = &empty;
    assert_eq!(analyze(&i2, &reg, &NoMarket).unwrap_err(), AnalyzeError::NoProfiles);
    let mut i3 = input(&p, None);
    i3.player.uuid = "ffffffffffffffffffffffffffffffff".into();
    assert_eq!(analyze(&i3, &reg, &NoMarket).unwrap_err(), AnalyzeError::NotAMember);
}
