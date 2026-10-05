//! Kuudra & Crimson Isle Advisor.

use skypilot_protocol::advisor::{AdvisorReport, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

fn reputation(ctx: &Ctx) -> i64 {
    ctx.analysis.kuudra.as_ref().map(|k| k.mage_reputation.unwrap_or(0).max(k.barbarian_reputation.unwrap_or(0))).unwrap_or(0)
}

fn completed(ctx: &Ctx, tier: &str) -> bool {
    ctx.analysis.kuudra.as_ref().is_some_and(|k| k.completions.get(tier).copied().unwrap_or(0) > 0)
}

pub fn tier_unlocked(ctx: &Ctx, tier_id: &str) -> bool {
    let Some(t) = ctx.registry.curated.kuudra.tiers.iter().find(|t| t.id == tier_id) else { return false };
    reputation(ctx) >= t.required_reputation && (t.requires_tier.is_empty() || completed(ctx, &t.requires_tier))
}

pub struct KuudraProvider;

impl Provider for KuudraProvider {
    fn id(&self) -> &'static str {
        "kuudra"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        for g in goals {
            for n in g.open_nodes().filter(|n| n.id.starts_with("kuudra:")) {
                let tid = &n.id[7..];
                let Some(t) = ctx.registry.curated.kuudra.tiers.iter().find(|x| x.id == tid) else { continue };
                let rep = reputation(ctx);
                if rep < t.required_reputation {
                    let mut a = CandidateAction::new(format!("kuudra-rep-{tid}"), "kuudra", "Kuudra", ctx.t(format!("Crimson Isle reputation {rep} → {} (daily quests)", t.required_reputation), format!("クリムゾン島の評判 {rep} → {}（デイリークエスト）", t.required_reputation)));
                    a.metrics.push(Metric::change(ctx.t("Reputation", "評判"), rep as f64, t.required_reputation as f64, None));
                    a.hours = 0.5;
                    a.unlock_value = 0.8;
                    a.confidence = 0.8;
                    a.why = ctx.t(format!("{} Kuudra requires {} faction reputation.", t.name, t.required_reputation), format!("{} Kuudraには派閥の評判{}が必要です。", t.name, t.required_reputation));
                    if let Some(i) = g.impact(&n.id, 0.2) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                    a.link = Some("/combat/kuudra".into());
                    a.sources = vec![DataSource::SkyPilotRegistry, DataSource::HypixelProfile];
                    out.push(a);
                } else if tier_unlocked(ctx, tid) {
                    let mut a = CandidateAction::new(format!("kuudra-run-{tid}"), "kuudra", "Kuudra", ctx.t(format!("Complete {} Kuudra", t.name), format!("{} Kuudraをクリア", t.name)));
                    a.hours = 0.5;
                    a.difficulty = 2.0 + t.order as f64 * 0.5;
                    a.risk = 2.0;
                    a.confidence = 0.6;
                    a.why = ctx.t("Requirements met; a completion advances your goal.", "条件は満たしています。クリアで目標が進みます。");
                    if let Some(i) = g.impact(&n.id, 1.0) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                    a.link = Some("/combat/kuudra".into());
                    out.push(a);
                }
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("kuudra", ctx.t("Kuudra & Crimson Isle", "Kuudra・クリムゾン島"));
        let Some(k) = ctx.analysis.kuudra.as_ref() else {
            r.unknowns.push(ctx.t("Crimson Isle not visited", "クリムゾン島未訪問"));
            return r;
        };
        r.summary.push(Metric::text(ctx.t("Faction", "派閥"), k.selected_faction.clone().unwrap_or_else(|| "?".into())));
        r.summary.push(Metric::value(ctx.t("Mage reputation", "メイジ評判"), k.mage_reputation.unwrap_or(0) as f64, None));
        r.summary.push(Metric::value(ctx.t("Barbarian reputation", "バーバリアン評判"), k.barbarian_reputation.unwrap_or(0) as f64, None));
        let mp = ctx.magical_power().unwrap_or(0);
        let mut t = section("tiers", ctx.t("Tiers", "ティア"));
        let next = ctx.registry.curated.kuudra.tiers.iter().find(|x| !completed(ctx, &x.id));
        t.table = Some(table(
            &["Tier", "Completions", "Unlocked", "Reputation needed", "MP readiness", "Key"],
            ctx.registry
                .curated
                .kuudra
                .tiers
                .iter()
                .map(|x| {
                    let want = ctx.registry.curated.kuudra.readiness.get(&x.id).copied().unwrap_or(0);
                    vec![
                        if Some(&x.id) == next.map(|n| &n.id) { badge(format!("{} (next)", x.name), Tone::Info) } else { text(&x.name) },
                        num(k.completions.get(&x.id).copied().unwrap_or(0) as f64, None),
                        yes_no(ctx, tier_unlocked(ctx, &x.id)),
                        num(x.required_reputation as f64, None),
                        progress(if want > 0 { (mp as f64 / want as f64).min(1.0) } else { 1.0 }, format!("{mp}/{want}")),
                        coins_opt(ctx.buy(&x.key).map(|q| q.price).or_else(|| {
                            let mut c = skypilot_pricing::craft::CraftCalculator::new(ctx.registry, ctx.oracle);
                            c.unit_cost(&x.key).and_then(|v| v.unit_price)
                        })),
                    ]
                })
                .collect(),
        ));
        t.notes.push(ctx.t("Unlock rules from the wiki; MP readiness targets are SkyPilot guidance.", "解放条件はWiki、MPの目安はSkyPilotの推奨値です。"));
        r.sections.push(t);
        let mut c = section("crimson", ctx.t("Crimson Isle", "クリムゾン島"));
        c.bullets.push(format!("{}: {}", ctx.t("Minibosses defeated", "討伐済みミニボス"), k.minibosses_killed.join(", ")));
        for (d, p) in &k.dojo_points {
            c.bullets.push(format!("Dojo {}: {} pts", skypilot_items::fingerprint::title_case(d), p));
        }
        if !k.attribute_stacks.is_empty() {
            c.bullets.push(format!("{}: {}", ctx.t("Attribute stacks", "アトリビュートスタック"), k.attribute_stacks.len()));
        }
        r.sections.push(c);
        r.freshness = market_freshness(ctx);
        r
    }
}
