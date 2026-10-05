//! Accessories Advisor & Magical Power Optimizer.

use std::collections::{HashMap, HashSet};

use skypilot_calc::accessories::{coins_per_mp, stats_multiplier};
use skypilot_items::Rarity;
use skypilot_pricing::craft::CraftCalculator;
use skypilot_protocol::advisor::{AdvisorReport, Tone};
use skypilot_protocol::common::{DataSource, Metric};
use skypilot_protocol::goals::PurchaseCandidate;

use super::*;
use crate::ctx::{Ctx, fmt_coins};
use crate::optimizer::CandidateAction;
use crate::requirements;

#[derive(Debug, Clone)]
pub struct MpCandidate {
    pub id: String,
    pub name: String,
    pub rarity: String,
    /// "NEW" | "UPGRADE" | "RECOMB" | "MOVE"
    pub kind: &'static str,
    pub replaces: Option<String>,
    pub mp_gain: u32,
    pub cost: f64,
    pub source: String,
    pub coins_per_mp: f64,
    pub requirement_met: Option<bool>,
    pub unmet: Vec<String>,
    /// Family root for de-duplication.
    pub family: String,
}

fn mp_for(ctx: &Ctx, rarity: &str, id: &str) -> u32 {
    let cfg = &ctx.registry.curated.accessories;
    cfg.mp_per_rarity.get(rarity).copied().unwrap_or(0) * cfg.mp_multiplier.get(id).copied().unwrap_or(1)
}

/// Lowest tier of an accessory family (used as family key).
fn family_root(ctx: &Ctx, id: &str) -> String {
    // NEU maps every lower tier to *all* its higher tiers, so the root is the lower tier
    // with the most upgrades.
    ctx.registry
        .neu
        .accessory_lower_tiers(id)
        .into_iter()
        .max_by_key(|l| ctx.registry.neu.accessory_upgrades(l).len())
        .unwrap_or_else(|| id.to_string())
}

pub fn candidates(ctx: &Ctx) -> Vec<MpCandidate> {
    let Some(acc) = ctx.analysis.accessories.as_ref() else { return Vec::new() };
    let cfg = &ctx.registry.curated.accessories;
    let inactive: HashSet<&String> = acc.inactive.iter().collect();
    // active accessories with their (possibly recombobulated) rarity
    let mut active: HashMap<String, (String, bool)> = HashMap::new();
    for o in &ctx.inv.accessories {
        if let Some(id) = &o.item.skyblock_id {
            if !inactive.contains(id) {
                let r = o.item.rarity.map(|r| r.as_str().to_string()).unwrap_or_default();
                active.insert(id.clone(), (r, o.item.is_recombobulated()));
            }
        }
    }
    let active_ids: HashSet<String> = active.keys().cloned().collect();
    let mut calc = CraftCalculator::new(ctx.registry, ctx.oracle);
    let mut out = Vec::new();

    // Accessories owned in storage but not in the bag: free MP.
    for o in ctx.inv.all_items() {
        let Some(id) = &o.item.skyblock_id else { continue };
        let Some(def) = ctx.registry.item(id) else { continue };
        if !def.is_accessory() || active_ids.contains(id) || ctx.registry.neu.ignored_talismans.contains(id) {
            continue;
        }
        if ctx.registry.neu.accessory_upgrades(id).iter().any(|h| active_ids.contains(h)) {
            continue;
        }
        let rarity = o.item.rarity.map(|r| r.as_str().to_string()).or_else(|| def.tier.clone()).unwrap_or_default();
        let lower_active: Option<&String> = ctx.registry.neu.accessory_lower_tiers(id).iter().find(|l| active_ids.contains(*l)).and_then(|l| active_ids.get(l));
        let old = lower_active.map(|l| mp_for(ctx, &active[l].0, l)).unwrap_or(0);
        let gain = mp_for(ctx, &rarity, id).saturating_sub(old);
        if gain > 0 {
            out.push(MpCandidate {
                id: id.clone(),
                name: def.name.clone(),
                rarity,
                kind: "MOVE",
                replaces: lower_active.cloned(),
                mp_gain: gain,
                cost: 0.0,
                source: o.location.clone(),
                coins_per_mp: 0.0,
                requirement_met: Some(true),
                unmet: vec![],
                family: family_root(ctx, id),
            });
        }
    }
    let moved: HashSet<String> = out.iter().map(|c| c.id.clone()).collect();

    for def in ctx.registry.items.values() {
        if !def.is_accessory() || def.neu_only {
            continue;
        }
        let id = &def.id;
        if active_ids.contains(id) || moved.contains(id) || ctx.registry.neu.ignored_talismans.contains(id) || cfg.extra_ignored.contains(id) {
            continue;
        }
        if ctx.registry.neu.accessory_upgrades(id).iter().any(|h| active_ids.contains(h)) {
            continue; // a higher tier is already active
        }
        let rarity = def.tier.clone().unwrap_or_default();
        let lower_active = ctx.registry.neu.accessory_lower_tiers(id).into_iter().find(|l| active_ids.contains(l));
        let old = lower_active.as_ref().map(|l| mp_for(ctx, &active[l].0, l)).unwrap_or(0);
        let gain = mp_for(ctx, &rarity, id).saturating_sub(old);
        if gain == 0 {
            continue;
        }
        let (cost, source) = match ctx.buy(id) {
            Some(q) => (q.price, q.source.to_string()),
            None => match calc.unit_cost(id).and_then(|c| c.unit_price.map(|p| (p, c.source))) {
                Some((p, s)) => (p, s),
                None => continue, // unpriceable (event/rift/soulbound) - never guessed
            },
        };
        let (met, unmet) = requirements::item_requirements_met(ctx, id);
        out.push(MpCandidate {
            id: id.clone(),
            name: def.name.clone(),
            rarity,
            kind: if lower_active.is_some() { "UPGRADE" } else { "NEW" },
            replaces: lower_active,
            mp_gain: gain,
            cost,
            source,
            coins_per_mp: coins_per_mp(cost, gain as f64).unwrap_or(f64::MAX),
            requirement_met: met,
            unmet,
            family: family_root(ctx, id),
        });
    }

    // Recombobulating active accessories.
    if let Some(recomb) = ctx.buy("RECOMBOBULATOR_3000") {
        for (id, (rarity, recombed)) in &active {
            if *recombed {
                continue;
            }
            let Some(r) = Rarity::parse(rarity) else { continue };
            let up = r.upgraded();
            if up == r {
                continue;
            }
            let gain = mp_for(ctx, up.as_str(), id).saturating_sub(mp_for(ctx, rarity, id));
            if gain == 0 {
                continue;
            }
            out.push(MpCandidate {
                id: id.clone(),
                name: ctx.display_name(id),
                rarity: up.as_str().to_string(),
                kind: "RECOMB",
                replaces: None,
                mp_gain: gain,
                cost: recomb.price,
                source: "Recombobulator 3000".into(),
                coins_per_mp: recomb.price / gain as f64,
                requirement_met: Some(true),
                unmet: vec![],
                family: format!("recomb:{id}"),
            });
        }
    }
    out.sort_by(|a, b| a.coins_per_mp.total_cmp(&b.coins_per_mp).then(b.mp_gain.cmp(&a.mp_gain)));
    out
}

/// Greedy cheapest-MP plan until `target_mp` (or budget) is reached; one pick per family.
pub fn plan_to(ctx: &Ctx, target_mp: Option<u32>, budget: f64) -> (Vec<MpCandidate>, f64, u32) {
    let current = ctx.magical_power().unwrap_or(0);
    let mut families = HashSet::new();
    let mut picked = Vec::new();
    let (mut cost, mut mp) = (0.0, current);
    for c in candidates(ctx) {
        if c.requirement_met == Some(false) || !families.insert(c.family.clone()) {
            continue;
        }
        if target_mp.is_some_and(|t| mp >= t) {
            break;
        }
        if cost + c.cost > budget {
            continue;
        }
        cost += c.cost;
        mp += c.mp_gain;
        picked.push(c);
    }
    (picked, cost, mp)
}

pub struct AccessoriesProvider;

impl Provider for AccessoriesProvider {
    fn id(&self) -> &'static str {
        "accessories"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let Some(current) = ctx.magical_power() else { return Vec::new() };
        let mut out = Vec::new();
        let max_per_mp = ctx.registry.curated.accessories.max_coins_per_mp_default;
        // Goal-driven: reach an MP target (from MP goals or readiness nodes).
        let target = goals.iter().filter_map(|g| g.open("mp").and_then(|n| n.target)).fold(None, |a: Option<f64>, t| Some(a.map_or(t, |x| x.max(t))));
        let budget = ctx.spendable();
        let (plan, _cost, final_mp) = plan_to(ctx, target.map(|t| t as u32), budget);
        let free: Vec<&MpCandidate> = plan.iter().filter(|c| c.kind == "MOVE").collect();
        if !free.is_empty() {
            let gain: u32 = free.iter().map(|c| c.mp_gain).sum();
            let mut a = CandidateAction::new("acc-move", "accessories", "Accessories", ctx.t(format!("Move {} accessories into your bag", free.len()), format!("所持中のアクセサリー{}個をバッグに入れる", free.len())));
            a.metrics.push(Metric::change("MP", current as f64, (current + gain) as f64, None));
            a.steps = free.iter().map(|c| format!("{} ({}) +{} MP", c.name, c.source, c.mp_gain)).collect();
            a.hours = 0.05;
            a.long_term_value = 0.9;
            a.confidence = 0.95;
            a.why = ctx.t("Free Magical Power: you already own these but they are not in the Accessory Bag.", "既に所持しているがアクセサリーバッグに入っていないため、無料でMPが上がります。");
            for g in goals {
                if let (Some(n), Some(t)) = (g.open("mp"), target) {
                    let gap = (t - n.current.unwrap_or(current as f64)).max(1.0);
                    if let Some(i) = g.impact("mp", gain as f64 / gap) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                }
            }
            a.link = Some("/accessories".into());
            a.sources = vec![DataSource::HypixelProfile, DataSource::SkyPilotRegistry];
            out.push(a);
        }
        let paid: Vec<&MpCandidate> = plan.iter().filter(|c| c.kind != "MOVE" && (target.is_some() || c.coins_per_mp <= max_per_mp)).take(if target.is_some() { 60 } else { 8 }).collect();
        if !paid.is_empty() {
            let gain: u32 = paid.iter().map(|c| c.mp_gain).sum();
            let total: f64 = paid.iter().map(|c| c.cost).sum();
            let title = ctx.t(format!("Buy {} accessories for Magical Power", paid.len()), format!("アクセサリーを{}個購入してMPを上げる", paid.len()));
            let mut a = CandidateAction::new("acc-buy", "accessories", "Accessories", title);
            a.metrics.push(Metric::change("MP", current as f64, (current + gain) as f64, None));
            a.metrics.push(Metric::value(ctx.t("Cost", "費用"), total, Some("coins")));
            a.metrics.push(Metric::value(ctx.t("Avg coins / MP", "1MPあたり"), total / gain.max(1) as f64, Some("coins")));
            a.cost = total;
            a.hours = 0.1 + paid.len() as f64 * 0.02;
            a.long_term_value = 0.85;
            a.confidence = 0.8;
            a.purchases = paid
                .iter()
                .map(|c| PurchaseCandidate {
                    item_id: c.id.clone(),
                    name: if c.kind == "RECOMB" { format!("Recombobulate {}", c.name) } else { c.name.clone() },
                    price: Some(c.cost),
                    price_source: Some(c.source.clone()),
                    gain: Some(c.mp_gain as f64),
                    gain_unit: Some("MP".into()),
                    coins_per_unit: Some(c.coins_per_mp),
                    max_buy_price: Some(c.cost * 1.1),
                })
                .collect();
            a.steps = paid.iter().take(10).map(|c| format!("{} +{} MP — {} ({}/MP)", c.name, c.mp_gain, fmt_coins(c.cost), fmt_coins(c.coins_per_mp))).collect();
            a.why = ctx.t(
                format!("Magical Power is the cheapest broad stat upgrade right now: {} per MP on average, and it raises the stats of your selected Power for every activity.", fmt_coins(total / gain.max(1) as f64)),
                format!("現在最も費用対効果の高い強化です（平均{}/MP）。MPは選択中のパワーの全ステータスを底上げするため、全コンテンツに効果があります。", fmt_coins(total / gain.max(1) as f64)),
            );
            for g in goals {
                if let Some(n) = g.open("mp") {
                    let t = n.target.unwrap_or(current as f64);
                    let gap = (t - n.current.unwrap_or(current as f64)).max(1.0);
                    if let Some(i) = g.impact("mp", (final_mp.min(t as u32) as f64 - current as f64) / gap) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                }
            }
            a.link = Some("/accessories".into());
            a.sources = vec![DataSource::HypixelProfile, DataSource::HypixelResources, DataSource::NeuRepo, DataSource::Market];
            out.push(a);
        }
        out
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("accessories", ctx.t("Accessories & Magical Power", "アクセサリー・マジカルパワー"));
        let Some(acc) = ctx.analysis.accessories.as_ref() else {
            r.unknowns.push(ctx.t("Accessory bag not visible (Inventory API disabled)", "アクセサリーバッグが見えません（インベントリAPIが無効）"));
            return r;
        };
        r.summary.push(Metric::value("Magical Power", acc.magical_power as f64, None));
        if let Some(api) = acc.highest_magical_power_api {
            r.summary.push(Metric::value(ctx.t("Highest MP (Hypixel)", "最高MP（Hypixel記録）"), api as f64, None));
        }
        r.summary.push(Metric::value(ctx.t("Stats multiplier", "ステータス倍率"), stats_multiplier(acc.magical_power as f64), Some("x")));
        r.summary.push(Metric::value(ctx.t("Accessories", "アクセサリー数"), acc.accessories.len() as f64, None));
        if let Some(p) = &acc.selected_power {
            r.summary.push(Metric::text(ctx.t("Selected power", "選択中のパワー"), skypilot_items::fingerprint::title_case(p)));
        }
        let all = candidates(ctx);
        let mut s = section("cheapest", ctx.t("Cheapest Magical Power", "MP単価が安い順"));
        s.description = Some(ctx.t("Ranked by coins per MP using live Bazaar/AH prices (or craft cost).", "Bazaar/AHのライブ価格（またはクラフト費用）による1MPあたりのコイン順。"));
        s.table = Some(table(
            &["Accessory", "Type", "MP", "Cost", "Coins / MP", "Source", "Requirement"],
            all.iter()
                .take(80)
                .map(|c| {
                    vec![
                        item(ctx, &c.id),
                        badge(c.kind, if c.kind == "MOVE" { Tone::Good } else { Tone::Neutral }),
                        num(c.mp_gain as f64, None),
                        coins(c.cost),
                        coins(c.coins_per_mp),
                        text(&c.source),
                        match c.requirement_met {
                            Some(true) => badge(ctx.t("Met", "達成"), Tone::Good),
                            Some(false) => badge(c.unmet.join(", "), Tone::Bad),
                            None => unknown("Not verifiable from API"),
                        },
                    ]
                })
                .collect(),
        ));
        s.sources = vec![DataSource::HypixelResources, DataSource::NeuRepo, DataSource::Market, DataSource::SkyPilotRegistry];
        r.sections.push(s);
        let target = goals.iter().filter_map(|g| g.node("mp").and_then(|n| n.target)).fold(None, |a: Option<f64>, t| Some(a.map_or(t, |x| x.max(t))));
        for (label, t) in [("+50", acc.magical_power + 50), ("+100", acc.magical_power + 100)].into_iter().chain(target.map(|t| ("goal", t as u32))) {
            let (plan, cost, final_mp) = plan_to(ctx, Some(t), f64::MAX);
            let mut s = section(&format!("plan-{label}"), ctx.t(format!("Plan: MP {} → {}", acc.magical_power, t), format!("計画: MP {} → {}", acc.magical_power, t)));
            s.metrics = vec![Metric::value(ctx.t("Total cost", "合計費用"), cost, Some("coins")), Metric::value(ctx.t("Reached MP", "到達MP"), final_mp as f64, None), Metric::value(ctx.t("Items", "アイテム数"), plan.len() as f64, None)];
            if final_mp < t {
                s.notes.push(ctx.t("Not enough priced accessories with met requirements to reach this target.", "要件を満たし価格が分かるアクセサリーだけではこの目標に届きません。"));
            }
            r.sections.push(s);
        }
        let mut power = section("power", ctx.t("Power & tuning", "パワー・チューニング"));
        power.bullets = vec![
            format!("{}: {}", ctx.t("Unlocked powers", "解放済みパワー"), acc.unlocked_powers.len()),
            format!("{}: {}", ctx.t("Bag upgrades purchased", "バッグアップグレード"), acc.bag_upgrades_purchased.map(|v| v.to_string()).unwrap_or_else(|| "?".into())),
        ];
        for (k, v) in &acc.tuning {
            power.bullets.push(format!("Tuning {}: {}", skypilot_items::fingerprint::title_case(k), v));
        }
        power.notes.push(ctx.t("MP rules: rarity values from the official wiki; Hegemony counts double; Abicase adds 1 MP per 2 Abiphone contacts; Rift Prism is counted at its rarity value (imbuement is not visible in the API).", "MP計算: レアリティ別の値は公式Wikiより。Hegemonyは2倍、Abicaseは連絡先2件ごとに+1MP、Rift Prismはレアリティ値で計上（インビュー状態はAPIで確認不可）。"));
        r.sections.push(power);
        if !acc.inactive.is_empty() {
            let mut s = section("inactive", ctx.t("Inactive accessories (no MP)", "無効なアクセサリー（MPなし）"));
            s.bullets = acc.inactive.iter().map(|i| ctx.display_name(i)).collect();
            s.notes.push(ctx.t("Duplicates, lower tiers of an owned family, or ignored accessories.", "重複・上位版所持済み・対象外のアクセサリー。"));
            r.sections.push(s);
        }
        r.actions = Vec::new();
        r.freshness = market_freshness(ctx);
        r
    }
}
