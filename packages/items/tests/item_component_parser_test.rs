//! Parses real Auction House `item_bytes` (Hypixel API, SkyBlock 0.27, modern client era).
//! Those blobs are *hybrid*: a `components` compound with 26.x data components
//! (`minecraft:item_model`, `minecraft:tooltip_style`) next to the legacy tag.

use std::path::PathBuf;

use serde_json::Value;
use skypilot_items::{ItemFormat, Rarity, decode_blob, item::inventory_from_tag};

fn fixture(name: &str) -> Value {
    let p = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../fixtures/hypixel").join(name);
    serde_json::from_slice(&std::fs::read(p).expect("fixture")).expect("json")
}

#[test]
fn every_auction_blob_decodes_with_a_skyblock_id() {
    let doc = fixture("auctions_page0.json");
    let auctions = doc["auctions"].as_array().unwrap();
    assert!(auctions.len() > 100);
    let mut hybrid = 0;
    for a in auctions {
        let root = decode_blob(a["item_bytes"].as_str().unwrap()).expect("decode");
        let items = inventory_from_tag(&root);
        assert_eq!(items.len(), 1, "auctions contain exactly one stack");
        let item = items[0].as_ref().expect("non-empty slot");
        let id = item.skyblock_id.as_deref().expect("ExtraAttributes.id present");
        assert!(!id.is_empty());
        // The display name in the blob matches the API's item_name (modulo formatting).
        assert_eq!(item.name, a["item_name"].as_str().unwrap().trim(), "name for {id}");
        if item.format == ItemFormat::Hybrid {
            hybrid += 1;
        }
    }
    assert!(hybrid > 0, "modern hybrid component layout must be recognised");
}

#[test]
fn resource_pack_model_is_not_confused_with_skyblock_id() {
    let doc = fixture("auctions_page0.json");
    let mut checked = 0;
    for a in doc["auctions"].as_array().unwrap() {
        let root = decode_blob(a["item_bytes"].as_str().unwrap()).unwrap();
        let item = inventory_from_tag(&root).remove(0).unwrap();
        if let Some(model) = &item.item_model {
            checked += 1;
            // e.g. "hypixel_skyblock:item/uncategorized/promising_shovel" vs id "PROMISING_SPADE"
            assert!(model.contains(':'), "model ids are namespaced: {model}");
            assert_ne!(Some(model.as_str()), item.skyblock_id.as_deref());
        }
    }
    assert!(checked > 0);
}

#[test]
fn rarity_and_tier_match_api_metadata() {
    let doc = fixture("auctions_page0.json");
    let mut matched = 0;
    for a in doc["auctions"].as_array().unwrap() {
        let root = decode_blob(a["item_bytes"].as_str().unwrap()).unwrap();
        let item = inventory_from_tag(&root).remove(0).unwrap();
        let tier = a["tier"].as_str().unwrap();
        if let Some(r) = item.rarity {
            // Pets in the AH report their pet tier; everything else must match exactly.
            if item.skyblock_id.as_deref() != Some("PET") {
                assert_eq!(r, Rarity::parse(tier).unwrap(), "{:?}", item.skyblock_id);
                matched += 1;
            }
        }
    }
    assert!(matched > 50);
}

#[test]
fn profile_inventories_decode() {
    let doc = fixture("profiles_sample.json");
    let member = doc["profiles"][0]["members"].as_object().unwrap().values().next().unwrap();
    let inv = member["inventory"]["inv_armor"]["data"].as_str().unwrap();
    let armor = skypilot_items::decode_inventory(inv).unwrap();
    assert_eq!(armor.len(), 4);
    let talismans = member["inventory"]["bag_contents"]["talisman_bag"]["data"].as_str().unwrap();
    let bag = skypilot_items::decode_inventory(talismans).unwrap();
    let count = bag.iter().flatten().count();
    assert!(count > 50, "late-game sample has a large accessory bag ({count})");
}

#[test]
fn garbage_input_is_an_error_not_a_panic() {
    assert!(decode_blob("not base64 !!!").is_err());
    assert!(decode_blob("H4sIAAAAAAAAAA==").is_err());
    assert!(decode_blob("").is_err());
}
