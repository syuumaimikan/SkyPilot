use std::collections::HashMap;
use std::path::PathBuf;

use proptest::prelude::*;
use serde_json::{Value, json};
use skypilot_items::{ItemFingerprint, decode_blob, from_client_json, item::inventory_from_tag};

fn auctions() -> Vec<Value> {
    let p = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../fixtures/hypixel/auctions_page0.json");
    let v: Value = serde_json::from_slice(&std::fs::read(p).unwrap()).unwrap();
    v["auctions"].as_array().unwrap().clone()
}

fn fp(custom: Value) -> ItemFingerprint {
    let item = from_client_json(&json!({"custom_data": custom, "name": "x", "lore": [], "count": 1})).unwrap();
    ItemFingerprint::from_item(&item).unwrap()
}

#[test]
fn exotic_colours_get_their_own_key() {
    // Live AH, Oct 2026: plain Lapis leggings sell for ~2k by bid while exotic-dyed ones are
    // listed at 30M-550M. Default colour from the Hypixel items resource: "0,0,255".
    let default = Some("0,0,255");
    let mut plain = fp(json!({"id": "LAPIS_ARMOR_LEGGINGS"}));
    let mut legacy = fp(json!({"id": "LAPIS_ARMOR_LEGGINGS", "color": "0:0:255", "originTag": "UNKNOWN"}));
    let mut exotic = fp(json!({"id": "LAPIS_ARMOR_LEGGINGS", "color": "102:128:55", "originTag": "UNKNOWN"}));
    let mut dyed = fp(json!({"id": "LAPIS_ARMOR_LEGGINGS", "color": "102:128:55", "dye_item": "DYE_JADE"}));
    for f in [&mut plain, &mut legacy, &mut exotic, &mut dyed] {
        f.mark_exotic(default);
    }
    assert!(!plain.exotic && !legacy.exotic && !dyed.exotic && exotic.exotic);
    assert_eq!(plain.comparable_key(), legacy.comparable_key());
    assert_eq!(exotic.comparable_key(), "LAPIS_ARMOR_LEGGINGS|exotic=102:128:55");
    assert!(exotic.modifiers().iter().any(|m| m.contains("Exotic")));
    // without a known default colour nothing is flagged
    let mut unknown = fp(json!({"id": "SOME_ARMOR", "color": "1:2:3"}));
    unknown.mark_exotic(None);
    assert!(!unknown.exotic);
    assert_eq!(skypilot_items::fingerprint::normalize_color(" 0, 0 ,255"), Some("0:0:255".into()));
    assert_eq!(skypilot_items::fingerprint::normalize_color("0:0:256"), None);
    assert_eq!(skypilot_items::fingerprint::normalize_color("0:0"), None);
}

#[test]
fn modifiers_change_comparable_key() {
    let plain = fp(json!({"id": "HYPERION"}));
    let starred = fp(json!({"id": "HYPERION", "upgrade_level": 5}));
    let master = fp(json!({"id": "HYPERION", "upgrade_level": 8}));
    let recomb = fp(json!({"id": "HYPERION", "rarity_upgrades": 1}));
    let skinned = fp(json!({"id": "HYPERION", "skin": "SOME_SKIN"}));
    let keys = [&plain, &starred, &master, &recomb, &skinned].map(|f| f.comparable_key());
    for i in 0..keys.len() {
        for j in (i + 1)..keys.len() {
            assert_ne!(keys[i], keys[j]);
        }
    }
    assert_eq!(master.stars, 5);
    assert_eq!(master.master_stars, 3);
}

#[test]
fn reforge_and_hpb_only_affect_exact_key() {
    let a = fp(json!({"id": "HYPERION", "modifier": "heroic", "hot_potato_count": 15}));
    let b = fp(json!({"id": "HYPERION"}));
    assert_eq!(a.comparable_key(), b.comparable_key());
    assert_ne!(a.exact_key(), b.exact_key());
    assert_eq!(a.hot_potato_books, 10);
    assert_eq!(a.fuming_books, 5);
}

#[test]
fn books_pets_and_gems_get_market_ids() {
    let book = fp(json!({"id": "ENCHANTED_BOOK", "enchantments": {"ultimate_wise": 5}}));
    assert_eq!(book.market_id, "ENCHANTMENT_ULTIMATE_WISE_5");
    let pet = fp(json!({"id": "PET", "petInfo": "{\"type\":\"ENDER_DRAGON\",\"tier\":\"LEGENDARY\",\"exp\":25353230.0,\"heldItem\":\"PET_ITEM_TIER_BOOST\",\"candyUsed\":0}"}));
    assert_eq!(pet.market_id, "PET:ENDER_DRAGON:LEGENDARY");
    assert!(pet.comparable_key().contains("tb"));
    let gems = fp(json!({"id": "DIVAN_DRILL", "gems": {"JADE_0": "PERFECT", "MINING_0": "FLAWLESS", "MINING_0_gem": "TOPAZ", "unlocked_slots": ["MINING_0"]}}));
    let ids: Vec<String> = gems.gemstones.iter().map(|g| g.product_id()).collect();
    assert!(ids.contains(&"PERFECT_JADE_GEM".to_string()));
    assert!(ids.contains(&"FLAWLESS_TOPAZ_GEM".to_string()));
    assert_eq!(gems.unlocked_gem_slots, 1);
}

#[test]
fn real_auctions_fingerprint_and_group() {
    let mut groups: HashMap<String, usize> = HashMap::new();
    for a in auctions() {
        let root = decode_blob(a["item_bytes"].as_str().unwrap()).unwrap();
        let item = inventory_from_tag(&root).remove(0).unwrap();
        let f = ItemFingerprint::from_item(&item).expect("fingerprint");
        assert!(!f.market_id.is_empty());
        if f.skyblock_id == "PET" {
            assert!(f.market_id.starts_with("PET:"), "{}", f.market_id);
        }
        *groups.entry(f.comparable_key()).or_default() += 1;
    }
    // The fixture intentionally contains repeated BIN items -> some groups have many members.
    assert!(groups.values().any(|&n| n >= 5));
}

proptest! {
    #[test]
    fn fingerprint_never_panics(upgrade in any::<i64>(), hpb in any::<i64>(), name in "[A-Z_]{1,20}", lvl in any::<i64>()) {
        let f = fp(json!({"id": name, "upgrade_level": upgrade, "hot_potato_count": hpb, "enchantments": {"sharpness": lvl}}));
        prop_assert!(f.stars <= 5 && f.master_stars <= 5);
        prop_assert!(f.hot_potato_books <= 10 && f.fuming_books <= 5);
        let _ = f.exact_key();
    }
}
