//! Combat Advisor: gear stats from lore (partial - skills, pets and accessories are not
//! summed), EHP = Health × (1 + Defense / 100), and purpose-specific gear plans.

use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct CombatProvider;

const STATS: [&str; 12] = ["Damage", "Strength", "Crit Chance", "Crit Damage", "Bonus Attack Speed", "Ferocity", "Health", "Defense", "Intelligence", "Magic Find", "True Defense", "Ability Damage"];

impl Provider for CombatProvider {
    fn id(&self) -> &'static str {
        "combat"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new()
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("combat", ctx.t("Combat", "戦闘"));
        if let Some(l) = ctx.skill("COMBAT") {
            r.summary.push(Metric::value("Combat", l.level_with_progress, None));
        }
        if let Some(mp) = ctx.magical_power() {
            r.summary.push(Metric::value("Magical Power", mp as f64, None));
        }
        let Some(gear) = ctx.analysis.gear.as_ref() else {
            r.unknowns.push(ctx.t("Inventory API disabled", "インベントリAPIが無効です"));
            return r;
        };
        let mut totals = std::collections::BTreeMap::new();
        for i in gear.armor.iter().chain(&gear.equipment) {
            for (k, v) in &i.lore_stats {
                *totals.entry(k.clone()).or_insert(0.0) += v;
            }
        }
        let health = totals.get("Health").copied().unwrap_or(0.0);
        let defense = totals.get("Defense").copied().unwrap_or(0.0);
        let mut s = section("stats", ctx.t("Equipped armor & equipment stats", "装備中の防具・装備ステータス"));
        s.metrics = STATS.iter().filter_map(|k| totals.get(*k).map(|v| Metric::value(*k, *v, None))).collect();
        s.metrics.push(Metric::value(ctx.t("EHP (gear only)", "EHP（装備のみ）"), health * (1.0 + defense / 100.0), None));
        s.notes.push(ctx.t("Partial: base stats from skills, accessories, pets and potions are not summed by SkyPilot.", "部分値: スキル・アクセサリー・ペット・ポーションによる基礎ステータスは合算していません。"));
        s.sources = vec![DataSource::HypixelProfile];
        r.sections.push(s);
        let mut w = section("weapons", ctx.t("Weapons owned", "所持武器"));
        w.table = Some(table(&["Weapon", "Damage", "Strength", "Crit Damage", "Value"], gear.weapons.iter().take(30).map(|x| vec![text(&x.name), num(x.lore_stats.get("Damage").copied().unwrap_or(0.0), None), num(x.lore_stats.get("Strength").copied().unwrap_or(0.0), None), num(x.lore_stats.get("Crit Damage").copied().unwrap_or(0.0), None), coins_opt(x.estimated_value)]).collect()));
        r.sections.push(w);
        for (purpose, label) in [("slayer", "Slayer"), ("dungeon", "Dungeon"), ("kuudra", "Kuudra")] {
            let p = super::gear::plan(ctx, purpose, ctx.budget);
            let mut g = section(&format!("plan-{purpose}"), format!("{label} — {}", ctx.t("gear upgrades per coin", "費用対効果の高い装備強化")));
            g.table = Some(super::gear::plan_table(ctx, &p));
            g.notes = p.notes.clone();
            r.sections.push(g);
        }
        r.freshness = market_freshness(ctx);
        r
    }
}
