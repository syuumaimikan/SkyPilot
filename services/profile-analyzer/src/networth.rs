//! Networth: liquid / semi-liquid / illiquid / soulbound, priced from live market data.

use std::collections::BTreeMap;

use skypilot_data::Registry;
use skypilot_pricing::networth::value_item;
use skypilot_pricing::{PriceOracle, PriceSide};
use skypilot_protocol::common::Known;
use skypilot_protocol::profile::{EconomyInfo, ItemSummary, NetworthBreakdown, PetSummary};

use crate::inventory::{Inventories, summarize};

pub struct NetworthResult {
    pub breakdown: Known<NetworthBreakdown>,
    /// (location, skyblock_id, name) -> value for item summaries
    pub values: Vec<(String, String, f64, f64)>,
    /// Items worth ≥1M valued with low confidence (no or very few realised sales): (name, value).
    pub low_evidence: Vec<(String, f64)>,
}

/// Item valuations below this confidence rest on listings with (almost) no realised sales.
pub const LOW_CONFIDENCE: f64 = 0.35;

pub fn compute(inv: &Inventories, pets: &[PetSummary], economy: &EconomyInfo, registry: &Registry, oracle: &dyn PriceOracle) -> NetworthResult {
    if !inv.available {
        return NetworthResult { breakdown: Known::unknown("Inventory API disabled - networth cannot be computed"), values: Vec::new(), low_evidence: Vec::new() };
    }
    let mut liquid = economy.purse + economy.bank.as_ref().copied().unwrap_or(0.0) + economy.personal_bank.as_ref().copied().unwrap_or(0.0);
    let mut semi = 0.0;
    let mut illiquid = 0.0;
    let mut soulbound = 0.0;
    let mut by_category: BTreeMap<String, f64> = BTreeMap::new();
    by_category.insert("Purse & Bank".into(), liquid);
    let mut top: Vec<ItemSummary> = Vec::new();
    let mut unpriced = Vec::new();
    let mut values = Vec::new();
    let mut low_evidence = Vec::new();
    let mut weighted_conf = 0.0;
    let mut priced_total = 0.0;

    let groups: Vec<(&str, Vec<&crate::inventory::OwnedItem>)> = vec![
        ("Armor & Equipment", inv.armor.iter().chain(&inv.equipment).collect()),
        ("Wardrobe", inv.wardrobe_sets.iter().flatten().collect()),
        ("Inventory", inv.inventory.iter().collect()),
        ("Ender Chest", inv.ender_chest.iter().collect()),
        ("Storage", inv.backpacks.iter().chain(&inv.vault).chain(&inv.bags).collect()),
        ("Accessories", inv.accessories.iter().collect()),
    ];
    for (cat, items) in groups {
        let mut cat_total = 0.0;
        for o in items {
            let Some(fp) = &o.fp else { continue };
            match value_item(fp, registry, oracle) {
                Some(v) => {
                    cat_total += v.value;
                    weighted_conf += v.confidence * v.value;
                    priced_total += v.value;
                    if o.item.soulbound {
                        soulbound += v.value;
                    } else if v.liquid {
                        semi += v.value;
                    } else {
                        illiquid += v.value;
                    }
                    values.push((o.location.clone(), fp.skyblock_id.clone(), v.value, v.confidence));
                    if v.value >= 1_000_000.0 && v.confidence < LOW_CONFIDENCE {
                        low_evidence.push((o.item.name.clone(), v.value));
                    }
                    if v.value >= 1_000_000.0 {
                        let mut s = summarize(o, registry);
                        s.estimated_value = Some(v.value);
                        s.value_confidence = Some(v.confidence);
                        top.push(s);
                    }
                }
                None => unpriced.push(o.item.name.clone()),
            }
        }
        by_category.insert(cat.to_string(), cat_total);
    }

    // Pets: priced by PET:TYPE:TIER fair value with the comparable level bucket.
    let mut pet_total = 0.0;
    for p in pets {
        let level_bucket = skypilot_items::ItemFingerprint::pet_level_bucket(p.level);
        let base_key = format!("PET:{}:{}", p.pet_type, p.tier);
        let comparable = format!("{base_key}|lvl={level_bucket}");
        let fv = oracle.fair_value(&comparable).and_then(|f| f.value.map(|v| (v, f.confidence.score, f.sales_per_day)));
        let fv = fv.or_else(|| oracle.quote(&base_key, PriceSide::Sell).map(|q| (q.price, q.confidence * 0.7, q.sales_per_day)));
        match fv {
            Some((v, c, spd)) => {
                pet_total += v;
                weighted_conf += c * v;
                priced_total += v;
                if spd >= 3.0 { semi += v } else { illiquid += v }
            }
            None => unpriced.push(format!("{} pet", p.name)),
        }
    }
    by_category.insert("Pets".into(), pet_total);

    // Sacks and essence: bazaar instant-sell value.
    let mut sacks_total = 0.0;
    for (id, n) in &inv.sacks {
        if let Some(q) = oracle.quote(id, PriceSide::Sell) {
            sacks_total += q.price * *n as f64;
        }
    }
    semi += sacks_total;
    by_category.insert("Sacks".into(), sacks_total);
    let mut essence_total = 0.0;
    for (t, n) in &economy.essence {
        if let Some(q) = oracle.quote(&format!("ESSENCE_{}", t.to_ascii_uppercase()), PriceSide::Sell) {
            essence_total += q.price * *n as f64;
        }
    }
    semi += essence_total;
    by_category.insert("Essence".into(), essence_total);

    liquid = liquid.max(0.0);
    top.sort_by(|a, b| b.estimated_value.unwrap_or(0.0).total_cmp(&a.estimated_value.unwrap_or(0.0)));
    top.truncate(25);
    unpriced.sort();
    unpriced.dedup();
    let total = liquid + semi + illiquid + soulbound;
    NetworthResult {
        breakdown: Known::known(NetworthBreakdown {
            total,
            liquid,
            semi_liquid: semi,
            illiquid,
            soulbound,
            by_category,
            top_items: top,
            unpriced,
            confidence: if priced_total > 0.0 { weighted_conf / priced_total } else { 0.0 },
        }),
        values,
        low_evidence,
    }
}
