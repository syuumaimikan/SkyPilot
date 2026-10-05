//! Fishing Advisor.

use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct FishingProvider;

impl Provider for FishingProvider {
    fn id(&self) -> &'static str {
        "fishing"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        if goals.iter().any(|g| g.open("skill:FISHING").is_some()) {
            let plan = super::gear::plan(ctx, "fishing", None);
            if let Some(best) = plan.selected.first() {
                let mut a = CandidateAction::new("fishing-gear", "fishing", "Fishing", ctx.t(format!("Fishing gear: {}", best.name), format!("釣り装備: {}", best.name)));
                a.cost = best.cost.unwrap_or(0.0);
                a.hours = 0.1;
                a.long_term_value = 0.6;
                a.confidence = 0.5;
                a.why = ctx.t("Best Sea Creature Chance / Fishing Speed gain per coin.", "シークリーチャー確率・釣り速度の費用対効果が最も高い強化です。");
                for g in goals {
                    if let Some(i) = g.impact("skill:FISHING", 0.08) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                }
                a.link = Some("/fishing".into());
                a.sources = vec![DataSource::HypixelResources, DataSource::Market];
                out.push(a);
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("fishing", ctx.t("Fishing", "釣り"));
        if let Some(l) = ctx.skill("FISHING") {
            r.summary.push(Metric::value("Fishing", l.level_with_progress, None));
        }
        let Some(f) = ctx.analysis.fishing.as_ref() else { return r };
        r.summary.push(Metric::value(ctx.t("Treasures caught", "トレジャー"), f.treasures_caught.unwrap_or(0) as f64, None));
        r.summary.push(Metric::value(ctx.t("Trophy fish caught", "トロフィーフィッシュ"), f.trophy_fish_caught.unwrap_or(0) as f64, None));
        let mut g = section("gear", ctx.t("Fishing stats (best owned setup)", "釣りステータス（所持品の最良構成）"));
        g.metrics = f.gear_stats.stats.iter().filter(|(k, _)| k.contains("Sea Creature") || k.contains("Fishing") || k.contains("Treasure") || k.contains("Trophy") || k.contains("Magic Find")).map(|(k, v)| Metric::value(k.clone(), *v, None)).collect();
        g.bullets = f.gear_stats.sources.clone();
        r.sections.push(g);
        let mut purposes = section("purposes", ctx.t("By purpose", "目的別"));
        purposes.bullets = vec![
            ctx.t("XP: maximise Fishing Speed and Sea Creature Chance; fish where creatures you can kill quickly spawn.", "XP: 釣り速度とシークリーチャー確率を最大化し、素早く倒せる場所で釣る。"),
            ctx.t("Coins: compare sea-creature drops on the Bazaar/AH in Price Explorer before committing.", "金策: シークリーチャーのドロップ価格をPrice Explorerで確認してから選ぶ。"),
            ctx.t("Bestiary: target creatures with the lowest remaining kills (see Bestiary in-game).", "Bestiary: 残り討伐数が少ない生物を狙う。"),
            ctx.t("Trophy fish: Crimson Isle lava fishing; track per-fish tiers below.", "トロフィーフィッシュ: クリムゾン島の溶岩釣り。下の表で魚ごとの進捗を確認。"),
        ];
        r.sections.push(purposes);
        let mut t = section("trophy", ctx.t("Trophy fish", "トロフィーフィッシュ"));
        t.table = Some(table(&["Fish", "Caught"], f.trophy_fish.iter().map(|(k, v)| vec![text(skypilot_items::fingerprint::title_case(k)), num(*v as f64, None)]).collect()));
        r.sections.push(t);
        let plan = super::gear::plan(ctx, "fishing", None);
        let mut gs = section("gear-optimizer", ctx.t("Fishing gear optimizer", "釣り装備オプティマイザ"));
        gs.table = Some(super::gear::plan_table(ctx, &plan));
        gs.notes = plan.notes.clone();
        r.sections.push(gs);
        r.freshness = market_freshness(ctx);
        r
    }
}
