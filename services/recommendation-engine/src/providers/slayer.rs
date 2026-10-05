//! Slayer Advisor: analyzer, goal planner, gear tiers and profit calculator (expected value).
//! Everything is driven by `SlayerDefinition`s (data/registry/slayers.toml) + NEU drop tables.

use std::collections::BTreeMap;

use skypilot_calc::slayer::{DropInput, boss_economics, bosses_needed};
use skypilot_data::curated::SlayerDefinition;
use skypilot_protocol::advisor::{AdvisorReport, GearTierRecommendation, SlayerDropEv, SlayerPlan, SlayerProjection, SlayerStage, SlayerTierEconomics, Tone};
use skypilot_protocol::common::{DataSource, Metric, Range};
use skypilot_protocol::goals::PurchaseCandidate;

use super::*;
use crate::ctx::{Ctx, fmt_coins, fmt_hours};
use crate::optimizer::CandidateAction;

pub const GEAR_TIERS: [&str; 3] = ["REQUIRED", "RECOMMENDED", "LUXURY"];

fn tier_cost(ctx: &Ctx, def: &SlayerDefinition, tier: u8) -> f64 {
    let base = def.tier_costs.get(tier as usize - 1).copied().or_else(|| ctx.registry.neu.slayer_cost.get(tier as usize - 1).copied()).unwrap_or(0.0);
    let discount = ctx.registry.election.as_ref().is_some_and(|e| e.has_perk("SLASHED Pricing") || e.has_perk("Slashed Pricing"));
    if discount { base * 0.5 } else { base }
}

fn tier_xp(ctx: &Ctx, def: &SlayerDefinition, tier: u8) -> f64 {
    let base = def.tier_xp.get(tier as usize - 1).copied().or_else(|| ctx.registry.neu.leveling.boss_xp(&def.id).get(tier as usize - 1).copied()).unwrap_or(0.0);
    let buff = ctx.registry.election.as_ref().is_some_and(|e| e.has_perk("Slayer XP Buff"));
    if buff { base * 1.25 } else { base }
}

/// Highest gear tier the player owns for each slot, and the overall tier (weakest slot).
pub fn gear_status(ctx: &Ctx, def: &SlayerDefinition) -> (Option<usize>, Vec<GearTierRecommendation>) {
    let pets = ctx.analysis.pets.as_ref().map(Vec::as_slice).unwrap_or(&[]);
    let mut recs = Vec::new();
    let mut overall: Option<usize> = Some(3);
    for slot in &def.gear {
        let lists = [&slot.required, &slot.recommended, &slot.luxury];
        let mut slot_tier: Option<usize> = None;
        for (i, list) in lists.iter().enumerate() {
            let owned = skypilot_profile::gear::owns_any(ctx.inv, pets, list);
            if owned {
                slot_tier = Some(i + 1);
            }
            let price = list.iter().filter(|id| !id.starts_with("PET:")).filter_map(|id| ctx.buy(id).map(|q| q.price)).reduce(f64::min);
            recs.push(GearTierRecommendation { slot: slot.slot.clone(), tier: GEAR_TIERS[i].to_string(), items: list.iter().map(|id| ctx.display_name(id)).collect(), owned, price });
        }
        if slot.slot != "pet" {
            overall = match (overall, slot_tier) {
                (Some(o), Some(s)) => Some(o.min(s)),
                _ => None,
            };
        }
    }
    (overall, recs)
}

fn kill_minutes(def: &SlayerDefinition, gear: Option<usize>, tier: u8) -> Option<f64> {
    let i = tier as usize - 1;
    match gear {
        Some(3) => def.kill_minutes.luxury.get(i).copied(),
        Some(2) => def.kill_minutes.recommended.get(i).copied(),
        Some(_) => def.kill_minutes.required.get(i).copied(),
        None => def.kill_minutes.required.get(i).map(|m| m * 1.6),
    }
}

pub fn unlocked(ctx: &Ctx, def: &SlayerDefinition, tier: u8) -> bool {
    let me = ctx.slayer(&def.id);
    if tier > 1 {
        return me.is_some_and(|s| s.kills_by_tier.get(tier as usize - 2).copied().unwrap_or(0) > 0 || s.highest_tier_killed.is_some_and(|h| h + 1 >= tier));
    }
    if def.unlock_slayer.is_empty() {
        return true;
    }
    if me.is_some_and(|s| s.level.xp > 0.0) {
        return true;
    }
    ctx.slayer(&def.unlock_slayer).is_some_and(|p| p.highest_tier_killed.is_some_and(|h| h >= def.unlock_tier))
}

pub fn readiness(ctx: &Ctx, def: &SlayerDefinition, tier: u8, gear: Option<usize>) -> f64 {
    let i = tier as usize - 1;
    let combat = ctx.skill_level("COMBAT").unwrap_or(0) as f64;
    let rc = def.recommended_combat.get(i).copied().unwrap_or(0) as f64;
    let rm = def.recommended_mp.get(i).copied().unwrap_or(0) as f64;
    let c = if rc > 0.0 { (combat / rc).min(1.0) } else { 1.0 };
    let m = if rm > 0.0 { ctx.magical_power().map(|mp| (mp as f64 / rm).min(1.0)).unwrap_or(0.5) } else { 1.0 };
    let g = match gear {
        Some(3) => 1.0,
        Some(2) => 0.9,
        Some(_) => 0.7,
        None => 0.3,
    };
    let r = 0.3 * c + 0.3 * m + 0.4 * g;
    if unlocked(ctx, def, tier) { r } else { r * 0.5 }
}

pub fn tier_economics(ctx: &Ctx, def: &SlayerDefinition, tier: u8, gear: Option<usize>) -> SlayerTierEconomics {
    let cost = tier_cost(ctx, def, tier);
    let xp = tier_xp(ctx, def, tier);
    let table = ctx.registry.neu.drops.get(&def.boss_drop_id(tier));
    let mut drops = Vec::new();
    let mut inputs = Vec::new();
    for d in table.map(|t| t.drops.as_slice()).unwrap_or(&[]) {
        let price = ctx.sell(&d.id).map(|q| q.price);
        inputs.push(DropInput { chance: d.chance, amount: d.amount, unit_price: price });
        drops.push(SlayerDropEv {
            item_id: d.id.clone(),
            name: ctx.display_name(&d.id),
            chance: d.chance,
            amount: d.amount,
            unit_value: price,
            expected_value: price.map(|p| p * d.chance * d.amount),
            rng_meter_eligible: d.chance < 0.01,
        });
    }
    drops.sort_by(|a, b| b.expected_value.unwrap_or(0.0).total_cmp(&a.expected_value.unwrap_or(0.0)));
    let econ = boss_economics(&inputs, cost, 0.01);
    let priced = table.is_some() && econ.priced_share > 0.5;
    let minutes = kill_minutes(def, gear, tier);
    let per_hour = minutes.map(|m| 60.0 / m.max(0.1));
    SlayerTierEconomics {
        tier,
        boss_cost: cost,
        xp_per_boss: xp,
        expected_drop_value: priced.then_some(econ.expected_drop_value),
        rare_drop_ev: priced.then_some(econ.rare_drop_ev),
        expected_net_per_boss: priced.then_some(econ.expected_net),
        bosses_per_hour: per_hour,
        xp_per_hour: per_hour.map(|b| b * xp),
        net_per_hour: match (per_hour, priced) {
            (Some(b), true) => Some(b * econ.expected_net),
            _ => None,
        },
        drops,
        readiness: Some(readiness(ctx, def, tier, gear)),
        unlocked: unlocked(ctx, def, tier),
    }
}

pub fn plan(ctx: &Ctx, def: &SlayerDefinition, target_level: u32) -> Option<SlayerPlan> {
    let table = ctx.registry.neu.leveling.slayer_table(&def.id)?;
    let current = ctx.slayer(&def.id);
    let xp = current.map(|s| s.level.xp).unwrap_or(0.0);
    let cur_level = table.progress(xp).level;
    let target_level = target_level.min(table.max_level());
    let target_xp = table.xp_for_level(target_level)?;
    let remaining = (target_xp - xp).max(0.0);
    let (gear, recs) = gear_status(ctx, def);
    let tiers: Vec<SlayerTierEconomics> = (1..=def.max_tier).map(|t| tier_economics(ctx, def, t, gear)).collect();
    let practical = tiers.iter().filter(|t| t.unlocked && t.readiness.unwrap_or(0.0) >= 0.75).map(|t| t.tier).max();
    // Best tier: most XP/hour while levelling, most expected coins/hour once the target is
    // reached. Tiers the player is not ready for are discounted by readiness².
    let score = |t: &SlayerTierEconomics| {
        let r = t.readiness.unwrap_or(0.0);
        let base = if remaining > 0.0 { t.xp_per_hour } else { t.net_per_hour };
        base.unwrap_or(0.0) * if r >= 0.75 { 1.0 } else { r * r }
    };
    let best = tiers.iter().filter(|t| t.unlocked).max_by(|a, b| score(a).total_cmp(&score(b))).or(tiers.first())?;
    let bosses = bosses_needed(remaining, best.xp_per_boss, 1.0);
    let hours = match (bosses, best.bosses_per_hour) {
        (Some(b), Some(per)) if per > 0.0 => Some(Range { low: b / per * 0.8, high: b / per * 1.3 }),
        _ => None,
    };
    let mut notes = vec![
        ctx.t("Drop values are expected values (chance × market price), not guaranteed income.", "ドロップ価値は期待値（確率×市場価格）であり、確実な利益ではありません。"),
        ctx.t("Kill times and readiness are SkyPilot estimates by gear tier.", "討伐時間と準備度は装備ティア別のSkyPilot推定値です。"),
    ];
    if ctx.registry.election.as_ref().is_some_and(|e| e.has_perk("Slayer XP Buff")) {
        notes.push(ctx.t("Mayor perk active: +25% Slayer XP applied.", "市長パーク有効: Slayer XP +25% を適用。"));
    }
    let readiness_map: BTreeMap<String, f64> = tiers.iter().map(|t| (format!("T{}", t.tier), t.readiness.unwrap_or(0.0))).collect();
    let projection = project(ctx, def, remaining, best.tier, hours.map(|h| h.low / 0.8));
    if let Some(p) = &projection {
        notes.push(ctx.t(
            format!("Long-goal totals assume Recommended gear and moving up to T{} (see projection).", p.tier),
            format!("長期目標の合計は、推奨装備に揃えてT{}へ移行する前提です（見通し参照）。", p.tier),
        ));
    }
    Some(SlayerPlan {
        slayer_id: def.id.clone(),
        boss_name: def.name.clone(),
        current_level: cur_level,
        target_level,
        current_xp: xp,
        target_xp,
        xp_remaining: remaining,
        recommended_tier: best.tier,
        highest_practical_tier: practical,
        bosses_needed: bosses,
        estimated_hours: hours,
        estimated_cost: bosses.map(|b| b * best.boss_cost),
        expected_drop_value: match (bosses, best.expected_drop_value) {
            (Some(b), Some(ev)) => Some(b * ev),
            _ => None,
        },
        tiers,
        gear: recs,
        readiness: readiness_map,
        projection,
        notes,
    })
}

/// Staged estimate for a long XP goal: one unlock kill of each lower tier that is not unlocked
/// yet, then the tier with the most XP/hour on Recommended gear. Only returned when most of the
/// XP is earned at that tier and it clearly beats grinding the current best tier (`now_hours` =
/// central estimate at current gear).
fn project(ctx: &Ctx, def: &SlayerDefinition, remaining: f64, now_tier: u8, now_hours: Option<f64>) -> Option<SlayerProjection> {
    if remaining <= 0.0 {
        return None;
    }
    let econ: Vec<SlayerTierEconomics> = (1..=def.max_tier).map(|t| tier_economics(ctx, def, t, Some(2))).collect();
    let main = econ.iter().filter(|e| e.xp_per_boss > 0.0 && e.xp_per_hour.is_some()).max_by(|a, b| a.xp_per_hour.unwrap_or(0.0).total_cmp(&b.xp_per_hour.unwrap_or(0.0)))?;
    if main.tier <= now_tier {
        return None;
    }
    let mut stages = Vec::new();
    let mut xp_left = remaining;
    for e in econ.iter().filter(|e| e.tier < main.tier) {
        if !unlocked(ctx, def, e.tier + 1) && xp_left > 0.0 {
            stages.push(SlayerStage { tier: e.tier, bosses: 1.0, xp: e.xp_per_boss });
            xp_left -= e.xp_per_boss;
        }
    }
    if xp_left <= 0.0 {
        // the unlock kills alone reach the target: nothing is earned at the main tier
        return None;
    }
    let n = (xp_left / main.xp_per_boss).ceil();
    stages.push(SlayerStage { tier: main.tier, bosses: n, xp: n * main.xp_per_boss });
    let by_tier = |t: u8| &econ[t as usize - 1];
    let bosses: f64 = stages.iter().map(|s| s.bosses).sum();
    let hours: Option<f64> = stages.iter().map(|s| by_tier(s.tier).bosses_per_hour.filter(|b| *b > 0.0).map(|b| s.bosses / b)).sum();
    let cost: f64 = stages.iter().map(|s| s.bosses * by_tier(s.tier).boss_cost).sum();
    let drops: Option<f64> = stages.iter().map(|s| by_tier(s.tier).expected_drop_value.map(|v| v * s.bosses)).sum();
    if let (Some(now), Some(h)) = (now_hours, hours) {
        // only worth showing when it saves a meaningful share of the time
        if h >= now * 0.67 {
            return None;
        }
    }
    Some(SlayerProjection { tier: main.tier, stages, bosses, hours: hours.map(|h| Range { low: h * 0.8, high: h * 1.3 }), cost, expected_drop_value: drops })
}

pub struct SlayerProvider;

impl Provider for SlayerProvider {
    fn id(&self) -> &'static str {
        "slayer"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        for def in &ctx.registry.curated.slayers {
            let node_id = format!("slayer:{}", def.id);
            let targets: Vec<(&GoalContext, u32)> = goals.iter().filter_map(|g| g.open(&node_id).and_then(|n| n.target).map(|t| (g, t as u32))).collect();
            let Some(target) = targets.iter().map(|(_, t)| *t).max() else { continue };
            if !unlocked(ctx, def, 1) {
                if let Some(pre) = ctx.registry.curated.slayer(&def.unlock_slayer) {
                    let mut a = CandidateAction::new(format!("slayer-unlock-{}", def.id), "slayer", "Slayer", ctx.t(format!("Unlock {}: defeat {} Tier {}", def.name, pre.name, def.unlock_tier), format!("{}を解放: {} T{}を討伐", def.name, pre.name, def.unlock_tier)));
                    a.hours = 0.5;
                    a.unlock_value = 1.0;
                    a.difficulty = 2.0;
                    a.why = def.unlock.clone();
                    for (g, _) in &targets {
                        if let Some(i) = g.impact(&node_id, 0.05) {
                            a.goal_impacts.insert(g.id.clone(), i);
                        }
                    }
                    a.sources = vec![DataSource::SkyPilotRegistry];
                    a.link = Some("/combat/slayer".into());
                    out.push(a);
                }
                continue;
            }
            let Some(p) = plan(ctx, def, target) else { continue };
            let Some(best) = p.tiers.iter().find(|t| t.tier == p.recommended_tier) else { continue };
            // Concrete next step: reach the next level.
            let table = ctx.registry.neu.leveling.slayer_table(&def.id).unwrap();
            let next_level = (p.current_level + 1).min(target);
            let next_xp = table.xp_for_level(next_level).unwrap_or(p.target_xp);
            let to_next = (next_xp - p.current_xp).max(0.0);
            let bosses = bosses_needed(to_next, best.xp_per_boss, 1.0).unwrap_or(0.0);
            let hours = best.bosses_per_hour.map(|b| bosses / b.max(0.01)).unwrap_or(bosses * 0.08);
            let title = ctx.t(
                format!("{} T{} × {} ({} Slayer {} → {})", def.name, best.tier, bosses as u64, def.short, p.current_level, next_level),
                format!("{} T{}を{}体（{}スレイヤー {} → {}）", def.name, best.tier, bosses as u64, def.short, p.current_level, next_level),
            );
            let mut a = CandidateAction::new(format!("slayer-{}-next", def.id), "slayer", "Slayer", title);
            a.metrics.push(Metric::change(format!("{} Slayer", def.short), p.current_level as f64, next_level as f64, None));
            a.metrics.push(Metric::value(ctx.t("Bosses", "ボス数"), bosses, None));
            a.metrics.push(Metric::value(ctx.t("Boss cost", "召喚費用"), bosses * best.boss_cost, Some("coins")));
            if let Some(ev) = best.expected_drop_value {
                a.metrics.push(Metric::value(ctx.t("Expected drops (EV)", "ドロップ期待値"), bosses * ev, Some("coins")));
            }
            a.cost = bosses * best.boss_cost;
            a.hours = hours.max(0.1);
            a.difficulty = 1.5 + best.tier as f64 * 0.5;
            a.risk = if best.readiness.unwrap_or(0.0) < 0.85 { 2.5 } else { 1.5 };
            a.coin_return_per_hour = best.net_per_hour.unwrap_or(0.0).max(0.0);
            a.xp_return = to_next;
            a.long_term_value = 0.6;
            a.confidence = 0.65;
            a.confidence_reasons = vec![ctx.t("Kill time is an estimate for your gear tier", "討伐時間は装備ティアからの推定")];
            a.steps = vec![
                ctx.t(format!("Go to {}", def.area), format!("{}へ移動", def.area)),
                ctx.t(format!("Start Tier {} quests ({} coins each)", best.tier, fmt_coins(best.boss_cost)), format!("T{}クエストを開始（1回{}）", best.tier, fmt_coins(best.boss_cost))),
                ctx.t(format!("About {} per boss incl. spawn", fmt_hours(1.0 / best.bosses_per_hour.unwrap_or(1.0))), format!("1体あたり約{}（湧き含む）", fmt_hours(1.0 / best.bosses_per_hour.unwrap_or(1.0)))),
            ];
            a.why = ctx.t(
                format!("T{} gives the most Slayer XP per hour that your current gear can handle safely (readiness {:.0}%).", best.tier, best.readiness.unwrap_or(0.0) * 100.0),
                format!("現在の装備で安全に回せる中で、T{}が時間あたりのSlayer XPが最も高いためです（準備度{:.0}%）。", best.tier, best.readiness.unwrap_or(0.0) * 100.0),
            );
            for (g, _) in &targets {
                let frac = if p.xp_remaining > 0.0 { to_next / p.xp_remaining } else { 1.0 };
                if let Some(i) = g.impact(&node_id, frac) {
                    a.goal_impacts.insert(g.id.clone(), i);
                }
            }
            a.link = Some(format!("/combat/slayer?slayer={}", def.id));
            a.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo, DataSource::Market, DataSource::SkyPilotHeuristic];
            out.push(a);

            // Gear upgrade when readiness for the next tier is limited by gear.
            let (gear_tier, recs) = gear_status(ctx, def);
            if gear_tier.unwrap_or(0) < 2 {
                let want = gear_tier.map(|t| t + 1).unwrap_or(1).min(3);
                if let Some(rec) = recs.iter().filter(|r| r.slot != "pet" && r.tier == GEAR_TIERS[want - 1] && !r.owned).filter(|r| r.price.is_some()).min_by(|a, b| a.price.unwrap_or(f64::MAX).total_cmp(&b.price.unwrap_or(f64::MAX))) {
                    let slot = def.gear.iter().find(|s| s.slot == rec.slot);
                    let ids = slot.map(|s| match want {
                        1 => s.required.clone(),
                        2 => s.recommended.clone(),
                        _ => s.luxury.clone(),
                    }).unwrap_or_default();
                    let price = rec.price.unwrap_or(0.0);
                    let mut g = CandidateAction::new(format!("slayer-gear-{}", def.id), "slayer", "Slayer", ctx.t(format!("Upgrade {} {} for {}", rec.slot, rec.items.join(" / "), def.name), format!("{}用に{}（{}）を購入", def.name, rec.items.join(" / "), rec.slot)));
                    g.cost = price;
                    g.hours = 0.1;
                    g.unlock_value = 0.6;
                    g.long_term_value = 0.7;
                    g.confidence = 0.6;
                    g.purchases = ids.iter().filter(|i| !i.starts_with("PET:")).take(1).map(|i| PurchaseCandidate { item_id: i.clone(), name: ctx.display_name(i), price: Some(price), price_source: Some("BIN".into()), gain: None, gain_unit: None, coins_per_unit: None, max_buy_price: Some(price * 1.05) }).collect();
                    g.why = ctx.t(format!("Your {} gear tier limits which boss tiers you can farm efficiently.", rec.slot), format!("{}の装備ティアが周回できるボスTierを制限しています。", rec.slot));
                    for (gc, _) in &targets {
                        if let Some(i) = gc.impact(&node_id, 0.15) {
                            g.goal_impacts.insert(gc.id.clone(), i);
                        }
                    }
                    g.sources = vec![DataSource::SkyPilotHeuristic, DataSource::Market];
                    g.link = Some(format!("/combat/slayer?slayer={}", def.id));
                    out.push(g);
                }
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("slayer", ctx.t("Slayer", "スレイヤー"));
        let total: f64 = ctx.analysis.slayers.as_ref().map(|s| s.iter().map(|x| x.level.xp).sum()).unwrap_or(0.0);
        r.summary.push(Metric::value(ctx.t("Total Slayer XP", "Slayer XP合計"), total, None));
        let mut overview = section("overview", ctx.t("Overview", "概要"));
        let mut rows = Vec::new();
        for def in &ctx.registry.curated.slayers {
            let s = ctx.slayer(&def.id);
            let (gear, _) = gear_status(ctx, def);
            let practical = (1..=def.max_tier).filter(|t| unlocked(ctx, def, *t) && readiness(ctx, def, *t, gear) >= 0.75).max();
            rows.push(vec![
                text(&def.name),
                num(s.map(|x| x.level.level as f64).unwrap_or(0.0), None),
                num(s.map(|x| x.level.xp).unwrap_or(0.0), Some("XP")),
                s.and_then(|x| x.level.xp_to_next).map(|v| num(v, Some("XP"))).unwrap_or(text("MAX")),
                text(s.map(|x| x.kills_by_tier.iter().enumerate().map(|(i, k)| format!("T{}:{}", i + 1, k)).collect::<Vec<_>>().join(" ")).unwrap_or_default()),
                practical.map(|t| badge(format!("T{t}"), Tone::Good)).unwrap_or(badge("—", Tone::Warn)),
                text(gear.map(|g| GEAR_TIERS[g - 1]).unwrap_or("NONE")),
            ]);
        }
        overview.table = Some(table(&["Slayer", "Level", "XP", "To next", "Kills", "Highest practical tier", "Gear tier"], rows));
        overview.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo, DataSource::SkyPilotHeuristic];
        r.sections.push(overview);
        let focus: Vec<&SlayerDefinition> = {
            let goal_ids: Vec<String> = goals.iter().flat_map(|g| g.graph.nodes.iter().filter_map(|n| n.id.strip_prefix("slayer:").map(str::to_string))).collect();
            ctx.registry.curated.slayers.iter().filter(|d| goal_ids.is_empty() || goal_ids.contains(&d.id)).collect()
        };
        for def in focus {
            let cur = ctx.slayer(&def.id).map(|s| s.level.level).unwrap_or(0);
            let target = goals.iter().filter_map(|g| g.node(&format!("slayer:{}", def.id)).and_then(|n| n.target)).map(|t| t as u32).max().unwrap_or((cur + 1).min(9));
            let Some(p) = plan(ctx, def, target) else { continue };
            let mut s = section(&format!("slayer-{}", def.id), format!("{} — {} {} → {}", def.name, ctx.t("Level", "レベル"), p.current_level, p.target_level));
            s.metrics = vec![
                Metric::value(ctx.t("XP remaining", "残りXP"), p.xp_remaining, None),
                Metric::value(ctx.t("Recommended tier", "推奨Tier"), p.recommended_tier as f64, None),
                Metric::value(ctx.t("Bosses needed", "必要ボス数"), p.bosses_needed.unwrap_or(0.0), None),
                Metric::value(ctx.t("Estimated cost", "推定費用"), p.estimated_cost.unwrap_or(0.0), Some("coins")),
            ];
            if let Some(h) = p.estimated_hours {
                s.metrics.push(Metric::text(ctx.t("Estimated time", "推定時間"), format!("{} – {}", fmt_hours(h.low), fmt_hours(h.high))));
            }
            if let Some(ev) = p.expected_drop_value {
                s.metrics.push(Metric::value(ctx.t("Expected drop value (EV)", "ドロップ期待値"), ev, Some("coins")));
            }
            if let Some(pr) = &p.projection {
                let stages = pr.stages.iter().map(|st| format!("T{}×{}", st.tier, st.bosses)).collect::<Vec<_>>().join(" → ");
                let hours = pr.hours.map(|h| format!("{} – {}", fmt_hours(h.low), fmt_hours(h.high))).unwrap_or_else(|| "?".into());
                s.bullets.push(ctx.t(
                    format!("Long-goal outlook with Recommended gear: {stages} ({hours}), boss cost {}. The figures above use your current gear.", fmt_coins(pr.cost)),
                    format!("推奨装備での長期の見通し: {stages}（{hours}）、召喚費用{}。上の数値は現在の装備での計算です。", fmt_coins(pr.cost)),
                ));
            }
            s.table = Some(table(
                &["Tier", "Unlocked", "Readiness", "Boss cost", "XP", "EV / boss", "Rare EV", "Net / boss", "Bosses/h", "XP/h", "Net/h"],
                p.tiers
                    .iter()
                    .map(|t| {
                        vec![
                            text(format!("T{}", t.tier)),
                            yes_no(ctx, t.unlocked),
                            progress(t.readiness.unwrap_or(0.0), format!("{:.0}%", t.readiness.unwrap_or(0.0) * 100.0)),
                            coins(t.boss_cost),
                            num(t.xp_per_boss, None),
                            coins_opt(t.expected_drop_value),
                            coins_opt(t.rare_drop_ev),
                            coins_opt(t.expected_net_per_boss),
                            t.bosses_per_hour.map(|b| num((b * 10.0).round() / 10.0, None)).unwrap_or(unknown("No estimate")),
                            t.xp_per_hour.map(|x| num(x.round(), None)).unwrap_or(unknown("No estimate")),
                            coins_opt(t.net_per_hour),
                        ]
                    })
                    .collect(),
            ));
            s.notes = p.notes.clone();
            s.sources = vec![DataSource::NeuRepo, DataSource::Market, DataSource::SkyPilotHeuristic];
            r.sections.push(s);
            if let Some(best) = p.tiers.iter().find(|t| t.tier == p.recommended_tier) {
                let mut d = section(&format!("drops-{}", def.id), ctx.t(format!("{} T{} drops (expected value)", def.name, best.tier), format!("{} T{} ドロップ（期待値）", def.name, best.tier)));
                d.table = Some(table(
                    &["Drop", "Chance", "Amount", "Unit value", "EV / boss"],
                    best.drops.iter().take(20).map(|x| vec![item(ctx, &x.item_id), pct(x.chance), num(x.amount, None), coins_opt(x.unit_value), coins_opt(x.expected_value)]).collect(),
                ));
                d.notes.push(ctx.t("RNG drops are shown as expected value only.", "レアドロップは期待値としてのみ表示しています。"));
                r.sections.push(d);
            }
            let mut g = section(&format!("gear-{}", def.id), ctx.t(format!("{} gear", def.name), format!("{} 装備", def.name)));
            g.table = Some(table(
                &["Slot", "Tier", "Items", "Owned", "Cheapest"],
                p.gear.iter().map(|x| vec![text(&x.slot), badge(&x.tier, match x.tier.as_str() { "LUXURY" => Tone::Info, "RECOMMENDED" => Tone::Good, _ => Tone::Neutral }), text(x.items.join(", ")), yes_no(ctx, x.owned), coins_opt(x.price)]).collect(),
            ));
            g.notes.push(ctx.t("Gear tiers are SkyPilot guidance (Required / Recommended / Luxury), not game rules.", "装備ティア（必須/推奨/贅沢）はSkyPilotの目安でありゲームのルールではありません。"));
            r.sections.push(g);
        }
        r.freshness = market_freshness(ctx);
        r
    }
}
