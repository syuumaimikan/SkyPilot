//! Minion Optimizer: per-minion throughput from NEU tier stats, income at current prices,
//! upgrade cost and payback time.

use skypilot_calc::minion::items_per_day;
use skypilot_calc::roi::payback_days;
use skypilot_pricing::craft::CraftCalculator;
use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct MinionRow {
    pub base: String,
    pub name: String,
    pub crafted_tier: u32,
    pub max_tier: u32,
    pub product: Option<String>,
    pub coins_per_day: Option<f64>,
    pub next_coins_per_day: Option<f64>,
    pub upgrade_cost: Option<f64>,
    pub payback_days: Option<f64>,
}

/// The material a minion produces. NEU lore names the block being mined ("Lapis Lazuli Ore"),
/// not the drop, so the main collection ingredient of the tier-I crafting recipe is used (Lapis
/// Minion I is crafted from INK_SACK:4, Emerald Minion I from EMERALD...). Otherwise the lore
/// name is used only when it is itself a Bazaar-traded item; anything else stays unknown.
fn product_id(ctx: &Ctx, base: &str, lore_name: Option<&str>) -> Option<String> {
    let from_recipe = ctx.registry.neu.recipes.get(&format!("{base}_GENERATOR_1")).and_then(|rs| {
        rs.iter().find_map(|r| match r {
            skypilot_data::neu::Recipe::Crafting { inputs, .. } => {
                let mut totals: std::collections::BTreeMap<&str, f64> = std::collections::BTreeMap::new();
                for s in inputs {
                    *totals.entry(s.id.as_str()).or_insert(0.0) += s.amount;
                }
                // Only collection materials are minion outputs (Tarantula Minion I is crafted
                // from Tarantula Web but produces String/Spider Eyes, so it stays unknown).
                totals
                    .into_iter()
                    .filter(|(id, n)| *n > 1.0 && ctx.registry.collections.iter().any(|c| c.id == *id))
                    .max_by(|a, b| a.1.total_cmp(&b.1))
                    .map(|(id, _)| id.to_string())
            }
            _ => None,
        })
    });
    if from_recipe.is_some() {
        return from_recipe;
    }
    let name = lore_name?;
    let lower = name.to_lowercase();
    let singular = lower.trim_end_matches('s').to_string();
    let on_bazaar = |id: &str| ctx.sell(id).is_some_and(|q| q.source == "Bazaar");
    for candidate in [name.to_uppercase().replace(' ', "_"), singular.to_uppercase().replace(' ', "_")] {
        if ctx.registry.items.contains_key(&candidate) && on_bazaar(&candidate) {
            return Some(candidate);
        }
    }
    let mut matches: Vec<&str> = ctx
        .registry
        .items
        .values()
        .filter(|i| {
            let n = i.name.to_lowercase();
            (n == lower || n == singular) && on_bazaar(&i.id)
        })
        .map(|i| i.id.as_str())
        .collect();
    matches.sort();
    matches.first().map(|id| id.to_string())
}

/// Minion output is sold to the Bazaar or an NPC - never valued at Auction House prices.
fn product_price(ctx: &Ctx, id: &str) -> Option<f64> {
    ctx.sell(id).filter(|q| q.source == "Bazaar").map(|q| q.price).or_else(|| ctx.registry.npc_price(id))
}

pub fn rows(ctx: &Ctx) -> Vec<MinionRow> {
    let crafted = ctx.analysis.minions.as_ref().map(|m| m.crafted_tiers.clone()).unwrap_or_default();
    let mut calc = CraftCalculator::new(ctx.registry, ctx.oracle);
    let mut out = Vec::new();
    for (base, tiers) in &ctx.registry.neu.minions {
        let crafted_tier = crafted.get(&format!("{base}")).copied().unwrap_or(0);
        let max_tier = tiers.last().map(|t| t.tier).unwrap_or(0);
        let product = product_id(ctx, base, tiers.first().and_then(|t| t.product_name.as_deref()));
        let price = product.as_deref().and_then(|p| product_price(ctx, p));
        let at = |tier: u32| tiers.iter().find(|t| t.tier == tier);
        let income = |tier: u32| -> Option<f64> { Some(items_per_day(at(tier)?.seconds_between_actions, 0.0, 1.0) * price?) };
        let next = (crafted_tier + 1).min(max_tier);
        let upgrade_cost = if crafted_tier < max_tier { at(next).and_then(|t| calc.craft_cost(&t.id).map(|(c, n, _)| c / n)) } else { None };
        let cur = if crafted_tier > 0 { income(crafted_tier) } else { None };
        let nxt = income(next);
        let pb = match (upgrade_cost, cur, nxt) {
            (Some(c), Some(a), Some(b)) if b > a => payback_days(c, b - a),
            (Some(c), None, Some(b)) => payback_days(c, b),
            _ => None,
        };
        out.push(MinionRow {
            name: ctx.display_name(&format!("{base}_GENERATOR_1")).trim_end_matches(" I").to_string(),
            base: base.clone(),
            crafted_tier,
            max_tier,
            product,
            coins_per_day: cur,
            next_coins_per_day: nxt,
            upgrade_cost,
            payback_days: pb,
        });
    }
    out.sort_by(|a, b| a.payback_days.unwrap_or(f64::MAX).total_cmp(&b.payback_days.unwrap_or(f64::MAX)));
    out
}

pub struct MinionsProvider;

impl Provider for MinionsProvider {
    fn id(&self) -> &'static str {
        "minions"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new()
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("minions", ctx.t("Minions", "ミニオン"));
        if let Some(m) = ctx.analysis.minions.as_ref() {
            r.summary.push(Metric::value(ctx.t("Unique minions crafted", "クラフト済みミニオン"), m.unique_crafted as f64, None));
            r.summary.push(Metric::value(ctx.t("Minion slot community upgrades", "ミニオン枠のコミュニティ強化"), m.slots_from_community_upgrades as f64, None));
        } else {
            r.unknowns.push(ctx.t("No minion data", "ミニオンのデータがありません"));
        }
        let mut s = section("payback", ctx.t("Upgrade payback (1 minion, no fuel/upgrades)", "アップグレード回収期間（1体・燃料/強化なし）"));
        s.table = Some(table(
            &["Minion", "Crafted tier", "Product", "Coins/day now", "Coins/day next tier", "Upgrade cost", "Payback"],
            rows(ctx)
                .iter()
                .filter(|m| m.product.is_some())
                .take(60)
                .map(|m| {
                    vec![
                        text(&m.name),
                        text(format!("{}/{}", m.crafted_tier, m.max_tier)),
                        m.product.as_ref().map(|p| item(ctx, p)).unwrap_or(unknown("Product unknown")),
                        coins_opt(m.coins_per_day),
                        coins_opt(m.next_coins_per_day),
                        coins_opt(m.upgrade_cost),
                        m.payback_days.map(|d| num((d * 10.0).round() / 10.0, Some("days"))).unwrap_or(unknown("Not computable")),
                    ]
                })
                .collect(),
        ));
        s.notes.push(ctx.t(
            "Speed from NEU \"Time Between Actions\"; one drop per harvest is assumed (multi-drop minions are underestimated). Placed minions are not visible in the API.",
            "速度はNEUの「Time Between Actions」。1回の収穫につき1ドロップと仮定（複数ドロップのミニオンは過小評価）。設置中のミニオンはAPIで確認できません。",
        ));
        s.sources = vec![DataSource::NeuRepo, DataSource::Market];
        r.sections.push(s);
        r.freshness = market_freshness(ctx);
        r
    }
}
