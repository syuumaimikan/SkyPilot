//! "Best form to sell": a raw drop may be worth more compacted (MELON -> ENCHANTED_MELON
//! -> ENCHANTED_MELON_BLOCK, ROUGH -> FLAWED gem). Derived from NEU crafting recipes whose
//! only input is the raw item, so no compaction ratio is hard-coded.

use skypilot_data::neu::Recipe;

use crate::ctx::Ctx;

/// (product id, units of `raw` per product)
pub fn compactions(ctx: &Ctx, raw: &str) -> Vec<(String, f64)> {
    let mut out = Vec::new();
    for (out_id, recipes) in &ctx.registry.neu.recipes {
        for r in recipes {
            if let Recipe::Crafting { output, inputs } = r {
                if inputs.len() == 1 && inputs[0].id == raw && output.amount > 0.0 {
                    out.push((out_id.clone(), inputs[0].amount / output.amount));
                }
            }
        }
    }
    out
}

/// Selling more per hour than this share of a product's daily volume would move its price,
/// so that form is not used for an income estimate (SkyPilot heuristic).
pub const MAX_HOURLY_SHARE_OF_DAILY_VOLUME: f64 = 0.05;

/// Best per-unit sell value for a raw item via itself or up to two compaction steps.
/// `units_per_hour` (raw units) excludes forms whose market is too thin to absorb that
/// output at the quoted price. Returns (value per raw unit, form id).
pub fn best_value(ctx: &Ctx, raw: &str, units_per_hour: Option<f64>) -> Option<(f64, String)> {
    let absorbs = |q: &skypilot_pricing::Quote, per_raw: f64| match units_per_hour {
        Some(u) if u > 0.0 => u / per_raw <= MAX_HOURLY_SHARE_OF_DAILY_VOLUME * q.sales_per_day,
        _ => true,
    };
    let mut best: Option<(f64, String)> =
        ctx.sell(raw).filter(|q| (q.source == "Bazaar" || q.source == "Sales") && absorbs(q, 1.0)).map(|q| (q.price, raw.to_string()));
    for (p1, n1) in compactions(ctx, raw) {
        if let Some(q) = ctx.sell(&p1).filter(|q| q.source == "Bazaar" && absorbs(q, n1)) {
            let v = q.price / n1;
            if best.as_ref().is_none_or(|b| v > b.0) {
                best = Some((v, p1.clone()));
            }
        }
        for (p2, n2) in compactions(ctx, &p1) {
            if let Some(q) = ctx.sell(&p2).filter(|q| q.source == "Bazaar" && absorbs(q, n1 * n2)) {
                let v = q.price / (n1 * n2);
                if best.as_ref().is_none_or(|b| v > b.0) {
                    best = Some((v, p2.clone()));
                }
            }
        }
    }
    best
}
