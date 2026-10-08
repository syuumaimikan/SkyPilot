//! Generic Gear Optimizer: purpose-specific stat weights, item base stats from the items
//! resource, live prices, and greedy budget allocation with slot exclusivity.

use std::collections::BTreeMap;

use skypilot_calc::roi::{Candidate, allocate_budget, gain_per_million};
use skypilot_protocol::advisor::{AdvisorReport, Cell, GearPlan, Table, UpgradeOption};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_coins};
use crate::optimizer::CandidateAction;

/// (API stat key, lore label, weight). SkyPilot heuristics per purpose.
pub fn weights(purpose: &str) -> Vec<(&'static str, &'static str, f64)> {
    match purpose {
        "tank" | "healer" => vec![("HEALTH", "Health", 1.0), ("DEFENSE", "Defense", 2.0), ("VITALITY", "Vitality", 5.0)],
        "f7_mage" | "mage" => vec![("INTELLIGENCE", "Intelligence", 1.0), ("ABILITY_DAMAGE_PERCENT", "Ability Damage", 20.0), ("HEALTH", "Health", 0.05), ("DEFENSE", "Defense", 0.05)],
        "f7_archer" | "archer" | "berserker" | "slayer" | "combat" | "dungeon" | "kuudra" => vec![
            ("DAMAGE", "Damage", 2.0),
            ("STRENGTH", "Strength", 1.0),
            ("CRITICAL_DAMAGE", "Crit Damage", 1.0),
            ("CRITICAL_CHANCE", "Crit Chance", 1.5),
            ("FEROCITY", "Ferocity", 2.0),
            ("ATTACK_SPEED", "Bonus Attack Speed", 0.5),
            ("HEALTH", "Health", 0.1),
            ("DEFENSE", "Defense", 0.1),
        ],
        "fishing" => vec![("SEA_CREATURE_CHANCE", "Sea Creature Chance", 10.0), ("FISHING_SPEED", "Fishing Speed", 1.0), ("TREASURE_CHANCE", "Treasure Chance", 5.0)],
        "foraging" => vec![("FORAGING_FORTUNE", "Foraging Fortune", 1.0), ("SWEEP", "Sweep", 5.0)],
        "farming" => vec![("FARMING_FORTUNE", "Farming Fortune", 1.0)],
        _ => vec![("MINING_SPEED", "Mining Speed", 0.5), ("MINING_FORTUNE", "Mining Fortune", 3.0)],
    }
}

const SLOTS: [&str; 19] = [
    "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET", "SWORD", "BOW", "LONGSWORD", "WAND", "FISHING_ROD", "AXE", "FARMING_TOOL", "HOE", "PICKAXE", "DRILL",
];

fn relevant_category(purpose: &str, category: &str) -> bool {
    if matches!(category, "HELMET" | "CHESTPLATE" | "LEGGINGS" | "BOOTS" | "NECKLACE" | "CLOAK" | "BELT" | "GLOVES" | "BRACELET") { return true; }
    match purpose {
        "mining" => matches!(category, "PICKAXE" | "DRILL"),
        "farming" => matches!(category, "HOE" | "AXE" | "FARMING_TOOL"),
        "fishing" => category == "FISHING_ROD",
        "foraging" => category == "AXE",
        "archer" | "f7_archer" => category == "BOW",
        _ => matches!(category, "SWORD" | "BOW" | "LONGSWORD" | "WAND"),
    }
}

fn slot_of(cat: &str) -> String {
    match cat {
        "SWORD" | "BOW" | "LONGSWORD" | "WAND" => "WEAPON".into(),
        "FISHING_ROD" => "ROD".into(),
        "AXE" | "FARMING_TOOL" | "HOE" => "TOOL".into(),
        "PICKAXE" | "DRILL" => "TOOL".into(),
        "GLOVES" | "BRACELET" => "GLOVES".into(),
        other => other.into(),
    }
}

pub fn plan(ctx: &Ctx, purpose: &str, budget: Option<f64>) -> GearPlan {
    let w = weights(purpose);
    let score_lore = |s: &BTreeMap<String, f64>| -> f64 { w.iter().map(|(_, l, k)| s.get(*l).copied().unwrap_or(0.0) * k).sum() };
    let mut owned_best: BTreeMap<String, f64> = BTreeMap::new();
    for o in ctx.inv.all_items() {
        let Some(cat) = o.item.skyblock_id.as_deref().and_then(|id| ctx.registry.item(id)).and_then(|i| i.category.clone()) else { continue };
        if !SLOTS.contains(&cat.as_str()) || !relevant_category(purpose, &cat) {
            continue;
        }
        let v = score_lore(&o.item.lore_stats());
        let e = owned_best.entry(slot_of(&cat)).or_insert(0.0);
        *e = e.max(v);
    }
    let mut options = Vec::new();
    for def in ctx.registry.items.values() {
        let Some(cat) = def.category.as_deref() else { continue };
        if !SLOTS.contains(&cat) || !relevant_category(purpose, cat) || def.neu_only || ctx.owns(&def.id) {
            continue;
        }
        let v: f64 = w.iter().map(|(k, _, wt)| def.stat(k) * wt).sum();
        if v <= 0.0 {
            continue;
        }
        let slot = slot_of(cat);
        let gain = v - owned_best.get(&slot).copied().unwrap_or(0.0);
        if gain <= 0.0 {
            continue;
        }
        let Some(q) = ctx.buy(&def.id) else { continue };
        let (met, unmet) = crate::requirements::item_requirements_met(ctx, &def.id);
        options.push(UpgradeOption {
            id: def.id.clone(),
            slot,
            name: def.name.clone(),
            cost: Some(q.price),
            stat: w.iter().map(|(_, l, _)| *l).collect::<Vec<_>>().join("+"),
            gain,
            gain_per_million: gain_per_million(gain, q.price),
            owned: false,
            resale_value: ctx.sell(&def.id).map(|s| s.price),
            tier: def.tier.clone().unwrap_or_default(),
            notes: if !ctx.inv.available { vec![ctx.t("Inventory data unavailable: cannot compare with your gear.", "インベントリデータ不足：現在の装備と比較できません。")] }
                else if met == Some(false) { vec![format!("Requires: {}", unmet.join(", "))] }
                else if met.is_none() { vec![ctx.t("Use requirements could not be verified.", "使用条件を確認できません。")] } else { vec![] },
        });
    }
    options.sort_by(|a, b| b.gain_per_million.unwrap_or(0.0).total_cmp(&a.gain_per_million.unwrap_or(0.0)));
    let budget_v = budget.unwrap_or_else(|| ctx.spendable());
    let cands: Vec<Candidate> = options
        .iter()
        .filter(|o| o.notes.is_empty())
        .map(|o| Candidate { id: o.id.clone(), slot: o.slot.clone(), cost: o.cost.unwrap_or(f64::MAX), gain: o.gain, resale: o.resale_value.unwrap_or(0.0) * 0.9 })
        .collect();
    let chosen = allocate_budget(&cands, budget_v);
    let (selected, rejected): (Vec<_>, Vec<_>) = options.into_iter().partition(|o| chosen.contains(&o.id));
    let total_cost = selected.iter().filter_map(|o| o.cost).sum();
    let mut total_gain = BTreeMap::new();
    total_gain.insert("score".into(), selected.iter().map(|o| o.gain).sum());
    GearPlan {
        purpose: purpose.into(),
        budget: Some(budget_v),
        selected,
        rejected: rejected.into_iter().take(40).collect(),
        total_cost,
        total_gain,
        notes: vec![
            "Score = weighted sum of item base stats (purpose weights are SkyPilot heuristics). Owned items use lore stats incl. upgrades, so gains are conservative.".into(),
            "Reforges, enchantments, gemstones and set bonuses of candidates are not included.".into(),
        ],
    }
}

pub fn plan_table(ctx: &Ctx, plan: &GearPlan) -> Table {
    table(
        &["Item", "Slot", "Cost", "Gain", "Gain / 1M", "Selected", "Notes"],
        plan.selected
            .iter()
            .map(|o| (o, true))
            .chain(plan.rejected.iter().take(20).map(|o| (o, false)))
            .map(|(o, sel)| {
                vec![
                    item(ctx, &o.id),
                    text(&o.slot),
                    coins_opt(o.cost),
                    num((o.gain * 10.0).round() / 10.0, None),
                    num((o.gain_per_million.unwrap_or(0.0) * 100.0).round() / 100.0, None),
                    yes_no(ctx, sel),
                    if o.notes.is_empty() { Cell::Text { text: String::new() } } else { text(o.notes.join("; ")) },
                ]
            })
            .collect(),
    )
}

pub struct GearProvider;

impl Provider for GearProvider {
    fn id(&self) -> &'static str {
        "gear"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new() // gear actions are proposed by the activity providers
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("gear", ctx.t("Gear Optimizer", "装備オプティマイザ"));
        if let Some(g) = ctx.analysis.gear.as_ref() {
            let mut s = section("equipped", ctx.t("Equipped", "装備中"));
            s.table = Some(table(
                &["Item", "Location", "Stars", "Recomb", "Value"],
                g.armor.iter().chain(&g.equipment).chain(&g.weapons).chain(&g.tools).map(|i| vec![text(&i.name), text(&i.location), num((i.stars + i.master_stars) as f64, None), yes_no(ctx, i.recombobulated), coins_opt(i.estimated_value)]).collect(),
            ));
            r.sections.push(s);
        } else {
            r.unknowns.push(ctx.t("Inventory API disabled", "インベントリAPIが無効です"));
        }
        for purpose in ["combat", "archer", "f7_mage", "tank", "mining", "farming", "fishing", "foraging"] {
            let weights = weights(purpose);
            let mut best: BTreeMap<String, (f64, &skypilot_profile::inventory::OwnedItem)> = BTreeMap::new();
            for owned in ctx.inv.all_items() {
                let Some(id) = owned.item.skyblock_id.as_deref() else { continue };
                let Some(category) = ctx.registry.item(id).and_then(|d| d.category.as_deref()) else { continue };
                if !SLOTS.contains(&category) || !relevant_category(purpose, category) { continue; }
                if crate::requirements::item_requirements_met(ctx, id).0 != Some(true) { continue; }
                let stats = owned.item.lore_stats();
                let score: f64 = weights.iter().map(|(_, label, w)| stats.get(*label).copied().unwrap_or(0.0) * w).sum();
                let slot = slot_of(category);
                if best.get(&slot).is_none_or(|(old, _)| score > *old) { best.insert(slot, (score, owned)); }
            }
            let mut owned_section = section(&format!("owned-{purpose}"), ctx.t(format!("Use your owned gear: {purpose}"), format!("所持装備の候補：{purpose}")));
            owned_section.table = Some(table(&["Slot", "Item", "Lore score"], best.iter().map(|(slot, (score, owned))| vec![text(slot), text(&owned.item.name), num(*score, None)]).collect()));
            owned_section.notes.push(ctx.t("Owned pieces with verified use requirements, ranked per slot. Scores omit set bonuses and play style; verify the complete loadout before switching.", "使用条件を確認できた所持装備をスロット別に比較。セット効果やプレイスタイルはスコアに含まないため、着替える前に構成を確認してください。"));
            owned_section.sources = vec![DataSource::HypixelProfile, DataSource::SkyPilotHeuristic];
            r.sections.push(owned_section);
            let p = plan(ctx, purpose, ctx.budget);
            let mut s = section(&format!("plan-{purpose}"), format!("{} — {} {}", skypilot_items::fingerprint::title_case(purpose), ctx.t("budget", "予算"), fmt_coins(p.budget.unwrap_or(0.0))));
            s.metrics = vec![Metric::value(ctx.t("Total cost", "合計費用"), p.total_cost, Some("coins"))];
            s.table = Some(plan_table(ctx, &p));
            s.notes = p.notes.clone();
            s.sources = vec![DataSource::HypixelResources, DataSource::Market, DataSource::SkyPilotHeuristic];
            r.sections.push(s);
        }
        r.freshness = market_freshness(ctx);
        r
    }
}
