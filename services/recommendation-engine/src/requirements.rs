//! Evaluate item requirements (from the Hypixel items resource) against a player.

use skypilot_data::Requirement;

use crate::ctx::Ctx;

/// (current, target) for numeric requirements; None when the API does not expose it.
pub fn progress(ctx: &Ctx, req: &Requirement) -> Option<(f64, f64)> {
    match req {
        Requirement::Skill { skill, level } => ctx.skill_level(skill).map(|c| (c as f64, *level as f64)),
        Requirement::Slayer { slayer, level } => ctx.slayer(slayer).map(|s| (s.level.level as f64, *level as f64)).or(Some((0.0, *level as f64))),
        Requirement::DungeonSkill { level, .. } => ctx.catacombs_level().map(|c| (c as f64, *level as f64)),
        Requirement::DungeonTier { tier, .. } => {
            let done = ctx.analysis.dungeons.as_ref().map(|d| d.floors.iter().any(|f| !f.master && f.tier as u32 == *tier && f.completions > 0));
            done.map(|d| (if d { 1.0 } else { 0.0 }, 1.0))
        }
        Requirement::HeartOfTheMountain { tier } => Some((ctx.hotm_level().unwrap_or(0) as f64, *tier as f64)),
        Requirement::Collection { collection, tier } => ctx
            .analysis
            .collections
            .as_ref()
            .and_then(|c| c.iter().find(|x| x.id.eq_ignore_ascii_case(collection)))
            .map(|c| (c.tier as f64, *tier as f64)),
        Requirement::GardenLevel { level } => Some((
            ctx.analysis.garden.as_ref().and_then(|g| g.garden_level.as_ref()).map(|l| l.level as f64).unwrap_or(0.0),
            *level as f64,
        )),
        Requirement::KuudraCompletion { tier } => ctx.analysis.kuudra.as_ref().map(|k| (if k.completions.get(tier).copied().unwrap_or(0) > 0 { 1.0 } else { 0.0 }, 1.0)),
        Requirement::CrimsonReputation { faction, reputation } => ctx.analysis.kuudra.as_ref().map(|k| {
            let rep = if faction.starts_with("mage") { k.mage_reputation } else { k.barbarian_reputation };
            (rep.unwrap_or(0) as f64, *reputation as f64)
        }),
        Requirement::ChocolateFactory { level } => ctx.analysis.chocolate_factory.as_ref().map(|c| (c.prestige_level as f64, *level as f64)),
        Requirement::AnyOf(list) => {
            let best = list.iter().filter_map(|r| progress(ctx, r)).max_by(|a, b| (a.0 / a.1.max(1.0)).total_cmp(&(b.0 / b.1.max(1.0))));
            best
        }
        Requirement::TrophyFishing { .. } | Requirement::Other { .. } => None,
    }
}

pub fn met(ctx: &Ctx, req: &Requirement) -> Option<bool> {
    progress(ctx, req).map(|(c, t)| c >= t)
}

/// All requirements of an item: Some(true) if all met, Some(false) if any unmet, None if unknown.
pub fn item_requirements_met(ctx: &Ctx, item_id: &str) -> (Option<bool>, Vec<String>) {
    let Some(item) = ctx.registry.item(item_id) else { return (Some(true), vec![]) };
    let mut all = Some(true);
    let mut unmet = Vec::new();
    for r in item.requirements.iter().chain(&item.catacombs_requirements) {
        match met(ctx, r) {
            Some(true) => {}
            Some(false) => {
                all = Some(false);
                unmet.push(r.describe());
            }
            None => {
                if all == Some(true) {
                    all = None;
                }
            }
        }
    }
    (all, unmet)
}
