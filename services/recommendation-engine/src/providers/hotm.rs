//! HOTM Planner: preset target trees costed with the NEU `hotmlayout.json` cost
//! expressions, computed from the player's *current* node levels and powder.
//!
//! Tree rules modelled: a perk row y (0 = top) requires HOTM tier `10 - y`; a perk can be
//! unlocked only when it is 4-adjacent to an unlocked perk (Mining Speed is the root);
//! unlocking a new perk costs one token.

use std::collections::{BTreeMap, HashSet, VecDeque};

use skypilot_calc::expr::{Env, Value};
use skypilot_data::neu::TreeLayout;
use skypilot_protocol::advisor::{HotmPlan, HotmUpgrade, PresetInfo};

use crate::ctx::Ctx;

pub fn required_tier(y: i32) -> u32 {
    (10 - y).max(1) as u32
}

pub struct CostModel<'a> {
    pub layout: &'a TreeLayout,
    env: Env,
}

impl<'a> CostModel<'a> {
    pub fn new(layout: &'a TreeLayout, potm: u32) -> Option<Self> {
        let mut env = Env::with_prelude(&layout.prelude).ok()?;
        env.set_num("potm", potm as f64);
        Some(CostModel { layout, env })
    }

    /// (powder type, cost) to go from `level` to `level + 1`.
    pub fn step_cost(&self, perk_id: &str, level: u32) -> Option<(String, f64)> {
        let perk = self.layout.perks.get(perk_id)?;
        let mut env = self.env.clone();
        env.set_num("level", level as f64).set_num("level0", level as f64).set_num("maxLevel", perk.max_level as f64);
        let powder = if perk.powder.starts_with('(') {
            env.eval(&perk.powder).ok()?.as_text()
        } else {
            perk.powder.clone()
        };
        let cost = match &perk.cost {
            serde_json::Value::String(s) if s.trim().is_empty() => 0.0,
            serde_json::Value::String(s) => match env.eval(s).ok()? {
                Value::Nil => 0.0,
                v => v.as_num()?,
            },
            serde_json::Value::Number(n) => n.as_f64()?,
            _ => 0.0,
        };
        Some((powder.to_ascii_uppercase(), cost.floor().max(0.0)))
    }

    /// Total powder per type to go from `from` to `to`.
    pub fn range_cost(&self, perk_id: &str, from: u32, to: u32) -> BTreeMap<String, f64> {
        let mut out = BTreeMap::new();
        for l in from.max(1)..to {
            if let Some((p, c)) = self.step_cost(perk_id, l) {
                *out.entry(p).or_insert(0.0) += c;
            }
        }
        out
    }

    /// Stat contribution of a perk at a level (for perks whose NEU layout carries a `stat` expr).
    pub fn stat(&self, perk_id: &str, stat_expr: &str, level: u32) -> Option<f64> {
        let mut env = self.env.clone();
        env.set_num("level", level as f64);
        let _ = perk_id;
        env.eval_num(stat_expr).ok().flatten()
    }
}

fn neighbors(layout: &TreeLayout, id: &str) -> Vec<String> {
    let Some(p) = layout.perks.get(id) else { return vec![] };
    layout.perks.iter().filter(|(_, q)| (q.x - p.x).abs() + (q.y - p.y).abs() == 1).map(|(k, _)| k.clone()).collect()
}

/// Shortest chain of locked perks connecting `target` to the unlocked set (BFS).
pub fn path_to(layout: &TreeLayout, unlocked: &HashSet<String>, target: &str, root: &str) -> Vec<String> {
    if unlocked.contains(target) {
        return vec![];
    }
    let starts: Vec<String> = if unlocked.is_empty() { vec![root.to_string()] } else { unlocked.iter().cloned().collect() };
    let mut prev: BTreeMap<String, String> = BTreeMap::new();
    let mut seen: HashSet<String> = starts.iter().cloned().collect();
    let mut q: VecDeque<String> = starts.into_iter().collect();
    while let Some(cur) = q.pop_front() {
        if cur == target {
            break;
        }
        for n in neighbors(layout, &cur) {
            if seen.insert(n.clone()) {
                prev.insert(n.clone(), cur.clone());
                q.push_back(n);
            }
        }
    }
    let mut path = Vec::new();
    let mut cur = target.to_string();
    while !unlocked.contains(&cur) {
        path.push(cur.clone());
        match prev.get(&cur) {
            Some(p) => cur = p.clone(),
            None => break,
        }
        if path.len() > 40 {
            break;
        }
    }
    path.reverse();
    if unlocked.is_empty() && !path.contains(&root.to_string()) {
        path.insert(0, root.to_string());
    }
    path
}

pub fn plan(ctx: &Ctx, preset_id: &str) -> Option<HotmPlan> {
    let layout = ctx.registry.neu.hotm.as_ref()?;
    let mining = ctx.analysis.mining.as_ref();
    let hotm = ctx.hotm_level().unwrap_or(0);
    let current: BTreeMap<String, u32> = mining
        .and_then(|m| m.nodes.as_ref())
        .map(|n| n.iter().map(|x| (x.id.clone(), x.level)).collect())
        .unwrap_or_default();
    let potm = current.get("core_of_the_mountain").or_else(|| current.get("special_0")).copied().unwrap_or(0);
    let model = CostModel::new(layout, potm)?;
    let presets = &ctx.registry.curated.mining.hotm_presets;
    let preset = presets.iter().find(|p| p.id == preset_id).or_else(|| presets.first())?;
    let mut available: BTreeMap<String, f64> = BTreeMap::new();
    if let Some(m) = mining {
        for (k, v) in [("MITHRIL", &m.mithril_powder), ("GEMSTONE", &m.gemstone_powder), ("GLACITE", &m.glacite_powder)] {
            if let Some(b) = v.as_ref() {
                available.insert(k.into(), b.available);
            }
        }
    }
    let mut unlocked: HashSet<String> = current.iter().filter(|(_, l)| **l > 0).map(|(k, _)| k.clone()).collect();
    let mut upgrades = Vec::new();
    let mut blocked = Vec::new();
    let mut required: BTreeMap<String, f64> = BTreeMap::new();
    let mut remaining = available.clone();
    // Order: cheaper tiers first (bottom rows), then by target size.
    let mut targets: Vec<(&String, &u32)> = preset.targets.iter().collect();
    targets.sort_by_key(|(id, _)| layout.perks.get(*id).map(|p| -p.y).unwrap_or(0));
    for (perk_id, target) in targets {
        let Some(perk) = layout.perks.get(perk_id) else { continue };
        let target = (*target).min(perk.max_level);
        let tier = required_tier(perk.y);
        if tier > hotm.max(1) {
            blocked.push(format!("{} (HOTM {})", perk.name, tier));
            continue;
        }
        // connect through the tree first (each connector to level 1)
        for step in path_to(layout, &unlocked, perk_id, "mining_speed") {
            if &step == perk_id {
                break;
            }
            let Some(sp) = layout.perks.get(&step) else { continue };
            if required_tier(sp.y) > hotm.max(1) {
                continue;
            }
            unlocked.insert(step.clone());
            upgrades.push(HotmUpgrade {
                perk_id: step.clone(),
                name: sp.name.clone(),
                from_level: 0,
                to_level: 1,
                powder: "TOKEN".into(),
                cost: 1.0,
                affordable_now: true,
                reason: ctx.t("Path to a preset perk (1 token)", "目標パークへの経路（トークン1）"),
            });
        }
        let from = current.get(perk_id).copied().unwrap_or(0);
        if from >= target {
            continue;
        }
        unlocked.insert(perk_id.clone());
        let costs = model.range_cost(perk_id, from.max(1), target);
        let (powder, cost) = costs.iter().max_by(|a, b| a.1.total_cmp(b.1)).map(|(p, c)| (p.clone(), *c)).unwrap_or(("TOKEN".into(), 0.0));
        for (p, c) in &costs {
            *required.entry(p.clone()).or_insert(0.0) += c;
        }
        let affordable = costs.iter().all(|(p, c)| remaining.get(p).copied().unwrap_or(0.0) >= *c);
        if affordable {
            for (p, c) in &costs {
                *remaining.entry(p.clone()).or_insert(0.0) -= c;
            }
        }
        upgrades.push(HotmUpgrade {
            perk_id: perk_id.clone(),
            name: perk.name.clone(),
            from_level: from,
            to_level: target,
            powder,
            cost,
            affordable_now: affordable,
            reason: if from == 0 { ctx.t("New perk for this preset", "このプリセットの新規パーク") } else { ctx.t("Level up toward preset target", "プリセット目標までレベル上げ") },
        });
    }
    // Affordable upgrades first, then by cost.
    upgrades.sort_by(|a, b| b.affordable_now.cmp(&a.affordable_now).then(a.cost.total_cmp(&b.cost)));
    Some(HotmPlan {
        preset: preset.id.clone(),
        preset_name: preset.name.clone(),
        hotm_level: hotm,
        upgrades,
        required_powder: required,
        available_powder: available,
        blocked_by_hotm_level: blocked,
        presets: presets.iter().map(|p| PresetInfo { id: p.id.clone(), name: p.name.clone(), description: p.description.clone() }).collect(),
    })
}
