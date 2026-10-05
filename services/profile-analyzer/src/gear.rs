//! Best owned setup per activity, read from item lore (final stats incl. reforges/enchants).

use std::collections::BTreeMap;

use skypilot_data::Registry;
use skypilot_protocol::profile::{GearInfo, GearStats, ItemSummary};

use crate::inventory::{Inventories, OwnedItem, category, summarize};

pub const ARMOR_SLOTS: [&str; 4] = ["HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"];
pub const EQUIPMENT_SLOTS: [&str; 5] = ["NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET"];

fn stat(o: &OwnedItem, names: &[&str]) -> f64 {
    let s = o.item.lore_stats();
    names.iter().map(|n| s.get(*n).copied().unwrap_or(0.0)).sum()
}

/// Pick, for each armor/equipment slot, the owned piece maximising `score`; sum their lore stats.
pub fn best_setup(inv: &Inventories, registry: &Registry, score_stats: &[(&str, f64)]) -> (GearStats, Vec<String>) {
    let score = |o: &OwnedItem| -> f64 { score_stats.iter().map(|(n, w)| stat(o, &[n]) * w).sum() };
    let mut stats: BTreeMap<String, f64> = BTreeMap::new();
    let mut sources = Vec::new();
    for slot in ARMOR_SLOTS.iter().chain(EQUIPMENT_SLOTS.iter()) {
        let best = inv
            .all_items()
            .filter(|o| category(o, registry).as_deref() == Some(*slot))
            .map(|o| (score(o), o))
            .filter(|(s, _)| *s > 0.0)
            .max_by(|a, b| a.0.total_cmp(&b.0));
        if let Some((_, o)) = best {
            for (k, v) in o.item.lore_stats() {
                *stats.entry(k).or_insert(0.0) += v;
            }
            sources.push(o.item.name.clone());
        }
    }
    (GearStats { stats, sources: sources.clone() }, sources)
}

/// Best tool from categories, scored by stats.
pub fn best_tool<'a>(inv: &'a Inventories, registry: &Registry, categories: &[&str], score_stats: &[(&str, f64)]) -> Option<&'a OwnedItem> {
    inv.all_items()
        .filter(|o| category(o, registry).is_some_and(|c| categories.contains(&c.as_str())))
        .map(|o| (score_stats.iter().map(|(n, w)| stat(o, &[n]) * w).sum::<f64>(), o))
        .max_by(|a, b| a.0.total_cmp(&b.0))
        .map(|(_, o)| o)
}

pub fn add_tool_stats(stats: &mut GearStats, tool: Option<&OwnedItem>) {
    if let Some(t) = tool {
        for (k, v) in t.item.lore_stats() {
            *stats.stats.entry(k).or_insert(0.0) += v;
        }
        stats.sources.push(t.item.name.clone());
    }
}

pub fn gear_info(inv: &Inventories, registry: &Registry) -> GearInfo {
    let sum = |o: &OwnedItem| summarize(o, registry);
    let weapon_cats = ["SWORD", "BOW", "LONGSWORD", "WAND", "GAUNTLET"];
    let tool_cats = ["PICKAXE", "DRILL", "AXE", "HOE", "FARMING_TOOL", "FISHING_ROD", "SHEARS", "SPADE", "VACUUM", "CHISEL"];
    let pool: Vec<&OwnedItem> = inv.inventory.iter().chain(&inv.ender_chest).chain(&inv.backpacks).chain(&inv.vault).collect();
    let pick = |cats: &[&str]| -> Vec<ItemSummary> {
        let mut v: Vec<ItemSummary> = pool.iter().filter(|o| category(o, registry).is_some_and(|c| cats.contains(&c.as_str()))).map(|o| sum(o)).collect();
        v.sort_by(|a, b| a.name.cmp(&b.name));
        v.dedup_by(|a, b| a.skyblock_id == b.skyblock_id && a.name == b.name);
        v
    };
    GearInfo {
        armor: inv.armor.iter().map(sum).collect(),
        equipment: inv.equipment.iter().map(sum).collect(),
        weapons: pick(&weapon_cats),
        tools: pick(&tool_cats),
        wardrobe: inv.wardrobe_sets.iter().map(|set| set.iter().map(sum).collect()).collect(),
        equipped_wardrobe_slot: inv.wardrobe_equipped_slot,
    }
}

/// Does the player own any of these item ids (anywhere, incl. accessories and pets "PET:TYPE")?
pub fn owns_any(inv: &Inventories, pets: &[skypilot_protocol::profile::PetSummary], ids: &[String]) -> bool {
    ids.iter().any(|id| {
        if let Some(pet) = id.strip_prefix("PET:") {
            return pets.iter().any(|p| p.pet_type.eq_ignore_ascii_case(pet));
        }
        inv.all_items().chain(&inv.accessories).any(|o| o.item.skyblock_id.as_deref() == Some(id.as_str()) || o.fp.as_ref().is_some_and(|f| f.market_id == *id))
    })
}
