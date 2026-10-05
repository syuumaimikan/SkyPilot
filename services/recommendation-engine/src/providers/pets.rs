//! Pets Advisor.

use skypilot_protocol::advisor::{AdvisorReport, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct PetsProvider;

impl Provider for PetsProvider {
    fn id(&self) -> &'static str {
        "pets"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new()
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("pets", ctx.t("Pets", "ペット"));
        let Some(pets) = ctx.analysis.pets.as_ref() else {
            r.unknowns.push(ctx.t("No pet data", "ペットのデータがありません"));
            return r;
        };
        r.summary.push(Metric::value(ctx.t("Pets", "ペット数"), pets.len() as f64, None));
        r.summary.push(Metric::value(ctx.t("Level 100+", "Lv100以上"), pets.iter().filter(|p| p.level >= 100).count() as f64, None));
        if let Some(active) = pets.iter().find(|p| p.active) {
            r.summary.push(Metric::text(ctx.t("Active", "使用中"), format!("[Lvl {}] {}", active.level, active.name)));
        }
        let mut s = section("list", ctx.t("Your pets", "所持ペット"));
        s.table = Some(table(
            &["Pet", "Rarity", "Level", "Held item", "Value"],
            pets.iter()
                .map(|p| {
                    let key = format!("PET:{}:{}|lvl={}", p.pet_type, p.tier, skypilot_items::ItemFingerprint::pet_level_bucket(p.level));
                    let v = ctx.oracle.fair_value(&key).and_then(|f| f.value);
                    vec![
                        if p.active { badge(&p.name, Tone::Good) } else { text(&p.name) },
                        text(&p.tier),
                        progress(p.level as f64 / p.max_level.max(1) as f64, format!("{}/{}", p.level, p.max_level)),
                        text(p.held_item.as_deref().map(|h| ctx.display_name(h)).unwrap_or_default()),
                        coins_opt(v),
                    ]
                })
                .collect(),
        ));
        s.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo, DataSource::Market];
        r.sections.push(s);
        // Pets recommended by the slayer gear tiers for goal slayers.
        let mut wanted = Vec::new();
        for g in goals {
            for n in g.graph.nodes.iter().filter_map(|n| n.id.strip_prefix("slayer:")) {
                if let Some(def) = ctx.registry.curated.slayer(n) {
                    for slot in def.gear.iter().filter(|s| s.slot == "pet") {
                        for id in slot.recommended.iter().chain(&slot.luxury) {
                            let owned = ctx.owns(id);
                            let base = id.trim_start_matches("PET:");
                            let price = ctx.buy(&format!("PET:{base}:LEGENDARY")).map(|q| q.price);
                            wanted.push(vec![text(&def.name), text(skypilot_items::fingerprint::title_case(base)), yes_no(ctx, owned), coins_opt(price)]);
                        }
                    }
                }
            }
        }
        if !wanted.is_empty() {
            let mut w = section("goal-pets", ctx.t("Pets for your goals", "目標に役立つペット"));
            w.table = Some(table(&["For", "Pet", "Owned", "Legendary price"], wanted));
            r.sections.push(w);
        }
        r.freshness = market_freshness(ctx);
        r
    }
}
