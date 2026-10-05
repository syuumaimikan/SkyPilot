//! Collection Advisor: collections gating the player's goals first, then the cheapest
//! remaining tiers.

use skypilot_protocol::advisor::{AdvisorReport, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct CollectionsProvider;

impl Provider for CollectionsProvider {
    fn id(&self) -> &'static str {
        "collections"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        let Some(cols) = ctx.analysis.collections.as_ref() else { return out };
        for g in goals {
            for n in g.open_nodes().filter(|n| n.id.starts_with("collection:")) {
                let cid = &n.id[11..];
                let Some(c) = cols.iter().find(|c| c.id.eq_ignore_ascii_case(cid)) else { continue };
                let target_tier = n.target.unwrap_or(c.tier as f64) as u32;
                let def = ctx.registry.collections.iter().find(|d| d.id == c.id);
                let need = def.and_then(|d| d.tiers.get(target_tier.saturating_sub(1) as usize)).map(|t| t.saturating_sub(c.amount)).unwrap_or(0);
                let buy_cost = ctx.buy(&c.id).map(|q| q.price * need as f64);
                let mut a = CandidateAction::new(format!("collection-{}", c.id), "collections", "Collections", ctx.t(format!("{} collection tier {} ({} more)", c.name, target_tier, need), format!("{}コレクション Tier {}（あと{}）", c.name, target_tier, need)));
                a.metrics.push(Metric::change(c.name.clone(), c.amount as f64, (c.amount + need) as f64, None));
                a.cost = 0.0;
                a.hours = 1.0;
                a.unlock_value = 1.0;
                a.confidence = 0.7;
                a.why = ctx.t(
                    format!("This collection tier gates your goal. Collections only count items you gather yourself (buying {} would not count; it would cost {}).", c.name, buy_cost.map(crate::ctx::fmt_coins).unwrap_or_else(|| "?".into())),
                    format!("このコレクションTierが目標の条件です。コレクションは自分で採集した分のみ加算されます（購入しても加算されません）。"),
                );
                if let Some(i) = g.impact(&n.id, 1.0) {
                    a.goal_impacts.insert(g.id.clone(), i);
                }
                a.link = Some("/profile?tab=collections".into());
                a.sources = vec![DataSource::HypixelProfile, DataSource::HypixelResources];
                out.push(a);
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("collections", ctx.t("Collections", "コレクション"));
        let Some(cols) = ctx.analysis.collections.as_ref() else {
            r.unknowns.push(ctx.t("Collections API disabled", "コレクションAPIが無効です"));
            return r;
        };
        let maxed = cols.iter().filter(|c| c.tier >= c.max_tier).count();
        r.summary.push(Metric::value(ctx.t("Maxed collections", "最大のコレクション"), maxed as f64, None));
        r.summary.push(Metric::value(ctx.t("Total collections", "コレクション総数"), cols.len() as f64, None));
        let goal_ids: Vec<String> = goals.iter().flat_map(|g| g.graph.nodes.iter().filter_map(|n| n.id.strip_prefix("collection:").map(str::to_string))).collect();
        let mut rows: Vec<_> = cols.iter().filter(|c| c.tier < c.max_tier).collect();
        rows.sort_by(|a, b| {
            let ga = goal_ids.iter().any(|g| g.eq_ignore_ascii_case(&a.id));
            let gb = goal_ids.iter().any(|g| g.eq_ignore_ascii_case(&b.id));
            gb.cmp(&ga).then_with(|| {
                let ra = a.next_tier_amount.map(|n| n.saturating_sub(a.amount)).unwrap_or(u64::MAX) as f64 / a.next_tier_amount.unwrap_or(1).max(1) as f64;
                let rb = b.next_tier_amount.map(|n| n.saturating_sub(b.amount)).unwrap_or(u64::MAX) as f64 / b.next_tier_amount.unwrap_or(1).max(1) as f64;
                ra.total_cmp(&rb)
            })
        });
        let mut s = section("next", ctx.t("Closest next tiers", "次のTierまで近い順"));
        s.table = Some(table(
            &["Collection", "Category", "Tier", "Amount", "Next tier at", "Goal"],
            rows.iter().take(60).map(|c| {
                vec![
                    text(&c.name),
                    text(&c.category),
                    text(format!("{}/{}", c.tier, c.max_tier)),
                    num(c.amount as f64, None),
                    c.next_tier_amount.map(|n| num(n as f64, None)).unwrap_or(text("—")),
                    if goal_ids.iter().any(|g| g.eq_ignore_ascii_case(&c.id)) { badge(ctx.t("Goal", "目標"), Tone::Info) } else { text("") },
                ]
            }).collect(),
        ));
        r.sections.push(s);
        r
    }
}
