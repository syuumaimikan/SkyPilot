//! Item valuation for networth: base value (comparable fair value, else market id) plus
//! applied upgrades priced from the market (recombobulator, potato books, gemstones,
//! master stars, essence stars, notable enchantments, drill parts, pet items, skins).

use skypilot_data::Registry;
use skypilot_items::ItemFingerprint;

use skypilot_protocol::market::IngredientCost;

use crate::craft::CraftCalculator;
use crate::oracle::{PriceOracle, PriceSide};

#[derive(Debug, Clone, PartialEq)]
pub struct ItemValue {
    pub value: f64,
    pub base: f64,
    pub upgrades: f64,
    pub confidence: f64,
    pub liquid: bool,
    pub method: &'static str,
}

/// Share of an applied upgrade's market price that is recovered in the item's value
/// (applied upgrades rarely resell at full price). SkyPilot heuristic.
const UPGRADE_FACTOR: f64 = 0.85;
const ENCHANT_MIN_VALUE: f64 = 1_000_000.0;
const MASTER_STARS: [&str; 5] = ["FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"];

/// Ceiling for an item's value: what crafting it costs when every ingredient has a reliable
/// price (Bazaar / NPC shop). Nobody pays more than that for the plain item.
pub fn craft_ceiling(registry: &Registry, oracle: &dyn PriceOracle, id: &str) -> Option<f64> {
    fn reliable(c: &IngredientCost) -> bool {
        if c.children.is_empty() { matches!(c.source.as_str(), "Bazaar" | "NPC Shop" | "Coins") } else { c.children.iter().all(reliable) }
    }
    let (total, amount, children) = CraftCalculator::new(registry, oracle).craft_cost(id)?;
    (!children.is_empty() && children.iter().all(reliable) && total.is_finite() && total > 0.0).then(|| total / amount.max(1.0))
}

pub fn value_item(fp: &ItemFingerprint, registry: &Registry, oracle: &dyn PriceOracle) -> Option<ItemValue> {
    let count = fp.count.max(1) as f64;
    let plain = fp.comparable_key() == fp.market_id;
    // 1. comparable fair value already includes dominant modifiers
    let comparable = oracle.fair_value(&fp.comparable_key()).filter(|f| f.value.is_some() && f.confidence.score >= 0.35);
    let (mut base, base_conf, includes_mods, liquid, market) = if let Some(f) = &comparable {
        (f.value.unwrap_or(0.0), f.confidence.score, true, f.sales_per_day >= 3.0, Some(f.clone()))
    } else if let Some(q) = oracle.quote(&fp.market_id, PriceSide::Sell) {
        let market = if q.source == "BIN" || q.source == "Sales" { oracle.fair_value(&fp.market_id) } else { None };
        (q.price, q.confidence, false, q.source == "Bazaar" || q.sales_per_day >= 3.0, market)
    } else if let Some(npc) = registry.npc_price(&fp.skyblock_id) {
        (npc, 0.9, false, true, None)
    } else {
        return None;
    };
    // Valued from the auction house: an owner selling now has to undercut the cheapest
    // listing, and with fewer than 3 realised sales buyers would rather craft the item than pay
    // more than its crafting cost.
    let mut capped = false;
    if let Some(f) = &market {
        let haircut = if f.comparables_used == 0 { 0.97 } else { 1.0 };
        if let Some(lowest) = f.lowest_bin.filter(|l| *l > 0.0 && *l * haircut < base) {
            base = lowest * haircut;
            capped = true;
        }
        if f.comparables_used < 3 && (!includes_mods || plain) {
            if let Some(c) = craft_ceiling(registry, oracle, &fp.market_id).filter(|c| *c < base) {
                base = c;
                capped = true;
            }
        }
        if let Some(npc) = registry.npc_price(&fp.skyblock_id).filter(|n| *n > base) {
            base = npc;
        }
    }

    let price = |id: &str| oracle.quote(id, PriceSide::Sell).map(|q| q.price).unwrap_or(0.0);
    let mut upgrades = 0.0;
    if !includes_mods {
        if fp.recombobulated && fp.pet.is_none() {
            upgrades += price("RECOMBOBULATOR_3000");
        }
        let total_stars = fp.stars as usize;
        if total_stars > 0 {
            if let Some(cost) = registry.neu.essence_costs.get(&fp.skyblock_id) {
                let essence: f64 = cost.stars.iter().take(total_stars).sum();
                upgrades += essence * price(&format!("ESSENCE_{}", cost.essence_type));
            }
        }
        for star in MASTER_STARS.iter().take(fp.master_stars as usize) {
            upgrades += price(star);
        }
        if let Some(skin) = &fp.skin {
            upgrades += oracle.fair_value(skin).and_then(|f| f.value).unwrap_or(0.0);
        }
    }
    upgrades += price("HOT_POTATO_BOOK") * fp.hot_potato_books as f64;
    upgrades += price("FUMING_POTATO_BOOK") * fp.fuming_books as f64;
    if fp.art_of_war {
        upgrades += price("THE_ART_OF_WAR");
    }
    for g in &fp.gemstones {
        upgrades += price(&g.product_id());
    }
    for part in &fp.drill_parts {
        upgrades += price(part);
    }
    if fp.market_id == fp.skyblock_id.to_ascii_uppercase() {
        for (name, lvl) in &fp.enchantments {
            let p = price(&format!("ENCHANTMENT_{}_{}", name.to_ascii_uppercase(), lvl));
            if p >= ENCHANT_MIN_VALUE {
                upgrades += p;
            }
        }
    }
    if let Some(pet) = &fp.pet {
        if let Some(h) = &pet.held_item {
            upgrades += price(h);
        }
    }
    let upgrades = upgrades * UPGRADE_FACTOR;
    let value = (base + upgrades) * count;
    if !value.is_finite() || value < 0.0 {
        return None;
    }
    Some(ItemValue {
        value,
        base: base * count,
        upgrades: upgrades * count,
        confidence: base_conf,
        liquid,
        method: match (capped, includes_mods) {
            (true, _) => "capped by cheapest listing / craft cost + upgrades",
            (false, true) => "comparable fair value + upgrades",
            (false, false) => "base value + upgrades",
        },
    })
}
