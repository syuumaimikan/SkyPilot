//! Museum Advisor: cheapest donation XP, owned-but-not-donated items, and expensive
//! low-value donations.

use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct MuseumProvider;

struct Donation {
    id: String,
    xp: f64,
    price: Option<f64>,
    owned: bool,
}

fn donations(ctx: &Ctx) -> Vec<Donation> {
    let donated: Vec<String> = ctx.analysis.museum.as_ref().map(|m| m.donated_items.clone()).unwrap_or_default();
    let mut out = Vec::new();
    for def in ctx.registry.items.values() {
        let Some(xp) = def.museum_donation_xp.filter(|x| *x > 0.0) else { continue };
        if donated.iter().any(|d| d == &def.id) {
            continue;
        }
        out.push(Donation { id: def.id.clone(), xp, price: ctx.buy(&def.id).map(|q| q.price), owned: ctx.owns(&def.id) });
    }
    out
}

impl Provider for MuseumProvider {
    fn id(&self) -> &'static str {
        "museum"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        if !ctx.analysis.museum.is_known() {
            return out;
        }
        let owned: Vec<Donation> = donations(ctx).into_iter().filter(|d| d.owned).collect();
        if !owned.is_empty() && goals.iter().any(|g| g.open("sblevel").is_some()) {
            let xp: f64 = owned.iter().map(|d| d.xp).sum();
            let mut a = CandidateAction::new("museum-donate-owned", "museum", "Museum", ctx.t(format!("Donate {} items you already own to the Museum", owned.len()), format!("所持済みのアイテム{}個を博物館に寄付", owned.len())));
            a.metrics.push(Metric::value(ctx.t("Donation XP", "寄付XP"), xp, None));
            a.hours = 0.2;
            a.confidence = 0.7;
            a.why = ctx.t("Free museum progress from items already in your inventories (donated items can be borrowed back).", "所持品から無料で博物館を進められます（寄付品は借り出し可能）。");
            for g in goals {
                if let Some(i) = g.impact("sblevel", 0.05) {
                    a.goal_impacts.insert(g.id.clone(), i);
                }
            }
            a.link = Some("/profile?tab=museum".into());
            a.sources = vec![DataSource::HypixelProfile, DataSource::HypixelResources];
            out.push(a);
        }
        out
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("museum", ctx.t("Museum", "博物館"));
        let Some(m) = ctx.analysis.museum.as_ref() else {
            r.unknowns.push(ctx.t("Museum API unavailable", "博物館APIが利用できません"));
            return r;
        };
        r.summary.push(Metric::value(ctx.t("Donated items", "寄付済み"), m.donated_count as f64, None));
        if let Some(v) = m.value {
            r.summary.push(Metric::value(ctx.t("Museum value (Hypixel)", "博物館価値（Hypixel）"), v as f64, Some("coins")));
        }
        let all = donations(ctx);
        let mut owned: Vec<&Donation> = all.iter().filter(|d| d.owned).collect();
        owned.sort_by(|a, b| b.xp.total_cmp(&a.xp));
        let mut s1 = section("owned", ctx.t("Owned but not donated (free)", "所持済み・未寄付（無料）"));
        s1.table = Some(table(&["Item", "Donation XP"], owned.iter().take(50).map(|d| vec![item(ctx, &d.id), num(d.xp, None)]).collect()));
        r.sections.push(s1);
        let mut cheap: Vec<&Donation> = all.iter().filter(|d| !d.owned && d.price.is_some()).collect();
        cheap.sort_by(|a, b| (a.price.unwrap_or(f64::MAX) / a.xp).total_cmp(&(b.price.unwrap_or(f64::MAX) / b.xp)));
        let mut s2 = section("cheapest", ctx.t("Cheapest progression (coins per donation XP)", "安い順（寄付XPあたりのコイン）"));
        s2.table = Some(table(&["Item", "Price", "XP", "Coins / XP"], cheap.iter().take(50).map(|d| vec![item(ctx, &d.id), coins_opt(d.price), num(d.xp, None), coins(d.price.unwrap_or(0.0) / d.xp)]).collect()));
        r.sections.push(s2);
        let mut s3 = section("expensive", ctx.t("Expensive, low-value donations", "高額で効率の悪い寄付"));
        s3.table = Some(table(&["Item", "Price", "XP", "Coins / XP"], cheap.iter().rev().take(20).map(|d| vec![item(ctx, &d.id), coins_opt(d.price), num(d.xp, None), coins(d.price.unwrap_or(0.0) / d.xp)]).collect()));
        s3.notes.push(ctx.t("Donation XP from the Hypixel items resource (museum_data.donation_xp).", "寄付XPはHypixelのアイテムリソース（museum_data.donation_xp）より。"));
        r.sections.push(s3);
        r.freshness = market_freshness(ctx);
        r
    }
}
