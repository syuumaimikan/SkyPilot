//! Foraging Advisor (Galatea-era foraging: Heart of the Forest, Forest Whispers, Agatha perks).

use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct ForagingProvider;

impl Provider for ForagingProvider {
    fn id(&self) -> &'static str {
        "foraging"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        if goals.iter().any(|g| g.open("skill:FORAGING").is_some()) {
            let plan = super::gear::plan(ctx, "foraging", None);
            if let Some(best) = plan.selected.first() {
                let mut a = CandidateAction::new("foraging-gear", "foraging", "Foraging", ctx.t(format!("Foraging gear: {}", best.name), format!("伐採装備: {}", best.name)));
                a.cost = best.cost.unwrap_or(0.0);
                a.hours = 0.1;
                a.long_term_value = 0.6;
                a.confidence = 0.5;
                a.why = ctx.t("Best Foraging Fortune / Sweep gain per coin.", "伐採幸運・Sweepの費用対効果が最も高い強化です。");
                for g in goals {
                    if let Some(i) = g.impact("skill:FORAGING", 0.08) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                }
                a.link = Some("/foraging".into());
                a.sources = vec![DataSource::HypixelResources, DataSource::Market];
                out.push(a);
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("foraging", ctx.t("Foraging", "伐採"));
        if let Some(l) = ctx.skill("FORAGING") {
            r.summary.push(Metric::value("Foraging", l.level_with_progress, None));
        }
        let Some(f) = ctx.analysis.foraging.as_ref() else { return r };
        match f.hotf.as_ref() {
            Some(h) => r.summary.push(Metric::value("Heart of the Forest", h.level_with_progress, None)),
            None => r.unknowns.push(ctx.t("Heart of the Forest: Not available from API", "Heart of the Forest: APIから取得できません")),
        }
        if let Some(w) = f.forest_whispers.as_ref() {
            r.summary.push(Metric::value("Forest Whispers", *w, None));
        }
        r.summary.push(Metric::value(ctx.t("Shards owned", "所持シャード"), f.shards_owned as f64, None));
        let mut g = section("gear", ctx.t("Foraging stats (best owned setup)", "伐採ステータス（所持品の最良構成）"));
        g.metrics = f.gear_stats.stats.iter().filter(|(k, _)| k.contains("Foraging") || k.contains("Sweep")).map(|(k, v)| Metric::value(k.clone(), *v, None)).collect();
        g.bullets = f.gear_stats.sources.clone();
        r.sections.push(g);
        if let Some(nodes) = f.nodes.as_ref() {
            let mut n = section("hotf", ctx.t("Heart of the Forest perks", "Heart of the Forest パーク"));
            n.table = Some(table(&["Perk", "Level", "Enabled"], nodes.iter().map(|x| vec![text(skypilot_items::fingerprint::title_case(&x.id)), num(x.level as f64, None), yes_no(ctx, x.enabled)]).collect()));
            r.sections.push(n);
        }
        let mut gifts = section("gifts", ctx.t("Tree gifts & Agatha perks", "ツリーギフト・Agathaパーク"));
        gifts.bullets = f.tree_gifts.iter().filter(|(k, _)| *k != "milestone_tier_claimed").map(|(k, v)| format!("{} gifts: {}", skypilot_items::fingerprint::title_case(k), v)).collect();
        gifts.bullets.extend(f.agatha_perks.iter().map(|(k, v)| format!("{}: {}", skypilot_items::fingerprint::title_case(k), v)));
        gifts.notes.push(ctx.t("Foraging mechanics change often; SkyPilot reads whatever the API exposes and labels the rest Unknown.", "伐採の仕様は頻繁に変わるため、APIで取得できる値のみを表示し、それ以外は不明と表示します。"));
        r.sections.push(gifts);
        let plan = super::gear::plan(ctx, "foraging", None);
        let mut gs = section("gear-optimizer", ctx.t("Foraging gear optimizer", "伐採装備オプティマイザ"));
        gs.table = Some(super::gear::plan_table(ctx, &plan));
        gs.notes = plan.notes.clone();
        r.sections.push(gs);
        r.freshness = market_freshness(ctx);
        r
    }
}
