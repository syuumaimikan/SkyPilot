//! Decodes every inventory blob of a profile member into typed items.

use std::collections::BTreeMap;

use serde_json::Value;
use skypilot_data::Registry;
use skypilot_items::{ItemFingerprint, ParsedItem, decode_inventory};
use skypilot_pricing::normalize::fingerprint_item;
use skypilot_protocol::profile::{ItemSummary, PetSummary};

#[derive(Debug, Clone)]
pub struct OwnedItem {
    pub location: String,
    pub item: ParsedItem,
    pub fp: Option<ItemFingerprint>,
}

#[derive(Debug, Default, Clone)]
pub struct Inventories {
    /// false when the player disabled the Inventory API (no `inv_contents`).
    pub available: bool,
    pub armor: Vec<OwnedItem>,
    pub equipment: Vec<OwnedItem>,
    pub inventory: Vec<OwnedItem>,
    pub ender_chest: Vec<OwnedItem>,
    pub backpacks: Vec<OwnedItem>,
    pub wardrobe_sets: Vec<Vec<OwnedItem>>,
    pub wardrobe_equipped_slot: Option<u32>,
    pub accessories: Vec<OwnedItem>,
    pub vault: Vec<OwnedItem>,
    pub bags: Vec<OwnedItem>,
    pub sacks: BTreeMap<String, u64>,
    pub decode_errors: Vec<String>,
}

impl Inventories {
    /// Every owned item except accessories (accessories are handled separately).
    pub fn all_items(&self) -> impl Iterator<Item = &OwnedItem> {
        self.armor
            .iter()
            .chain(&self.equipment)
            .chain(&self.inventory)
            .chain(&self.ender_chest)
            .chain(&self.backpacks)
            .chain(self.wardrobe_sets.iter().flatten())
            .chain(&self.vault)
            .chain(&self.bags)
    }
}

fn decode(v: Option<&Value>, location: &str, registry: &Registry, errors: &mut Vec<String>) -> Vec<Option<OwnedItem>> {
    let Some(data) = v.and_then(|x| x.get("data")).and_then(Value::as_str) else { return Vec::new() };
    match decode_inventory(data) {
        Ok(slots) => slots
            .into_iter()
            .map(|s| {
                s.map(|item| {
                    let fp = fingerprint_item(&item, registry);
                    OwnedItem { location: location.to_string(), item, fp }
                })
            })
            .collect(),
        Err(e) => {
            errors.push(format!("{location}: {e}"));
            Vec::new()
        }
    }
}

pub fn load(member: &Value, registry: &Registry) -> Inventories {
    let inv = member.get("inventory");
    let mut errors = Vec::new();
    let mut out = Inventories { available: inv.and_then(|i| i.get("inv_contents")).is_some(), ..Default::default() };
    let Some(inv) = inv else { return out };
    let flat = |slots: Vec<Option<OwnedItem>>| slots.into_iter().flatten().collect::<Vec<_>>();
    // inv_armor is stored boots -> helmet; present helmet first.
    let mut armor = flat(decode(inv.get("inv_armor"), "Armor", registry, &mut errors));
    armor.reverse();
    out.armor = armor;
    out.equipment = flat(decode(inv.get("equipment_contents"), "Equipment", registry, &mut errors));
    out.inventory = flat(decode(inv.get("inv_contents"), "Inventory", registry, &mut errors));
    out.ender_chest = flat(decode(inv.get("ender_chest_contents"), "Ender Chest", registry, &mut errors));
    out.vault = flat(decode(inv.get("personal_vault_contents"), "Personal Vault", registry, &mut errors));
    if let Some(bps) = inv.get("backpack_contents").and_then(Value::as_object) {
        for (i, bp) in bps {
            let loc = format!("Backpack {}", i.parse::<u32>().map(|n| n + 1).unwrap_or(0));
            out.backpacks.extend(flat(decode(Some(bp), &loc, registry, &mut errors)));
        }
    }
    if let Some(bags) = inv.get("bag_contents") {
        out.accessories = flat(decode(bags.get("talisman_bag"), "Accessory Bag", registry, &mut errors));
        for (key, name) in [("potion_bag", "Potion Bag"), ("fishing_bag", "Fishing Bag"), ("quiver", "Quiver"), ("sacks_bag", "Sack of Sacks")] {
            out.bags.extend(flat(decode(bags.get(key), name, registry, &mut errors)));
        }
    }
    // Wardrobe: pages of 36 slots, column-major sets (helmet, chest, legs, boots rows of 9).
    let wardrobe = decode(inv.get("wardrobe_contents"), "Wardrobe", registry, &mut errors);
    for page in wardrobe.chunks(36) {
        for set in 0..9 {
            let pieces: Vec<OwnedItem> = (0..4).filter_map(|row| page.get(row * 9 + set).cloned().flatten()).collect();
            if !pieces.is_empty() {
                out.wardrobe_sets.push(pieces.into_iter().map(|mut p| {
                    p.location = "Wardrobe".into();
                    p
                }).collect());
            }
        }
    }
    out.wardrobe_equipped_slot = inv.get("wardrobe_equipped_slot").and_then(Value::as_u64).map(|v| v as u32);
    if let Some(s) = inv.get("sacks_counts").and_then(Value::as_object) {
        out.sacks = s.iter().filter_map(|(k, v)| v.as_u64().filter(|n| *n > 0).map(|n| (k.clone(), n))).collect();
    }
    out.decode_errors = errors;
    out
}

pub fn pet_summary(p: &skypilot_items::PetInfo, registry: &Registry) -> PetSummary {
    let table = registry.neu.pets.table(&p.pet_type, &p.tier);
    let prog = table.progress(p.exp);
    let name = registry
        .neu
        .pets
        .display_names
        .get(&p.pet_type)
        .map(|n| skypilot_items::text::strip_formatting(n))
        .unwrap_or_else(|| skypilot_items::fingerprint::title_case(&p.pet_type));
    PetSummary {
        pet_type: p.pet_type.clone(),
        name,
        tier: p.tier.clone(),
        level: prog.level,
        max_level: prog.max_level,
        exp: p.exp,
        held_item: p.held_item.clone(),
        active: p.active,
        candy_used: p.candy_used,
        skin: p.skin.clone(),
    }
}

/// Item summary for the API (value filled in by the networth pass).
pub fn summarize(o: &OwnedItem, registry: &Registry) -> ItemSummary {
    let fp = o.fp.as_ref();
    ItemSummary {
        location: o.location.clone(),
        skyblock_id: o.item.skyblock_id.clone().unwrap_or_default(),
        name: o.item.name.clone(),
        rarity: o.item.rarity.map(|r| r.as_str().to_string()),
        count: o.item.count,
        reforge: fp.and_then(|f| f.reforge.clone()),
        stars: fp.map(|f| f.stars).unwrap_or(0),
        master_stars: fp.map(|f| f.master_stars).unwrap_or(0),
        recombobulated: o.item.is_recombobulated(),
        hot_potato_books: fp.map(|f| f.hot_potato_books + f.fuming_books).unwrap_or(0),
        enchantments: fp.map(|f| f.enchantments.clone().into_iter().collect()).unwrap_or_default(),
        gemstones: fp.map(|f| f.gemstones.iter().map(|g| format!("{} {}", g.quality, g.gem)).collect()).unwrap_or_default(),
        soulbound: o.item.soulbound,
        pet: o.item.pet_info().map(|p| pet_summary(&p, registry)),
        lore_stats: o.item.lore_stats(),
        estimated_value: None,
        value_confidence: None,
    }
}

/// Category from the items resource ("HELMET", "DRILL", ...).
pub fn category(o: &OwnedItem, registry: &Registry) -> Option<String> {
    o.item.skyblock_id.as_deref().and_then(|id| registry.item(id)).and_then(|i| i.category.clone())
}
