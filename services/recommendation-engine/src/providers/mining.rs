//! Mining Advisor: HOTM planner, powder planner, money calculator, gear optimizer and
//! route guide. All rates are computed from the player's own stats and live prices.

use std::collections::BTreeMap;

use skypilot_calc::mining::{blocks_per_hour, break_time, drops_per_block};
use skypilot_calc::roi::{Candidate, allocate_budget, gain_per_million};
use skypilot_protocol::advisor::{AdvisorReport, GearPlan, MiningCalcInput, MiningCalcResult, MiningTargetRate, PowderPlan, Tone, UpgradeOption};
use skypilot_protocol::common::{Confidence, DataSource, Freshness, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_coins, fmt_hours};
use crate::market_forms;
use crate::optimizer::CandidateAction;
use crate::providers::hotm;

pub const MINING_STATS: [&str; 6] = ["Mining Speed", "Mining Fortune", "Gemstone Fortune", "Dwarven Metal Fortune", "Block Fortune", "Pristine"];

fn gem_target(id: &str) -> bool {
    matches!(id, "ruby" | "amber" | "amethyst" | "jade" | "sapphire" | "opal" | "topaz" | "jasper" | "onyx" | "aquamarine" | "citrine" | "peridot")
}

/// Player mining stats: best owned setup (lore) + HOTM perk stats (NEU stat expressions).
pub fn player_stats(ctx: &Ctx) -> BTreeMap<String, f64> {
    let mut stats: BTreeMap<String, f64> = ctx.analysis.mining.as_ref().map(|m| m.gear_stats.stats.clone()).unwrap_or_default();
    if let (Some(layout), Some(nodes)) = (ctx.registry.neu.hotm.as_ref(), ctx.analysis.mining.as_ref().and_then(|m| m.nodes.as_ref())) {
        if let Some(model) = hotm::CostModel::new(layout, 0) {
            for (perk, stat_name) in [("mining_speed", "Mining Speed"), ("mining_fortune", "Mining Fortune")] {
                let level = nodes.iter().find(|n| n.id == perk && n.enabled).map(|n| n.level).unwrap_or(0);
                if let (Some(expr), true) = (layout.perks.get(perk).and_then(|p| p.stat.clone()), level > 0) {
                    if let Some(v) = model.stat(perk, &expr, level) {
                        *stats.entry(stat_name.to_string()).or_insert(0.0) += v;
                    }
                }
            }
        }
    }
    stats
}

pub fn default_input(ctx: &Ctx) -> MiningCalcInput {
    let s = player_stats(ctx);
    MiningCalcInput {
        mining_speed: s.get("Mining Speed").copied().unwrap_or(0.0),
        mining_fortune: s.get("Mining Fortune").copied().unwrap_or(0.0),
        gemstone_fortune: s.get("Gemstone Fortune").copied().unwrap_or(0.0),
        dwarven_metal_fortune: s.get("Dwarven Metal Fortune").copied().unwrap_or(0.0),
        block_fortune: s.get("Block Fortune").copied().unwrap_or(0.0),
        pristine: s.get("Pristine").copied().unwrap_or(0.0),
        uptime: ctx.registry.curated.mining.default_uptime,
    }
}

pub fn calculate(ctx: &Ctx, input: &MiningCalcInput, fuel_per_hour: f64) -> MiningCalcResult {
    let hotm = ctx.hotm_level().unwrap_or(0);
    let mining_level = ctx.skill_level("MINING").unwrap_or(0);
    let mut targets = Vec::new();
    for t in &ctx.registry.curated.mining.targets {
        let specialised = if gem_target(&t.id) {
            input.gemstone_fortune
        } else if t.id == "hard_stone" {
            input.block_fortune
        } else {
            input.dwarven_metal_fortune
        };
        let fortune = input.mining_fortune + specialised;
        let Some(bt) = break_time(t.block_strength, input.mining_speed, t.instant_factor) else {
            continue;
        };
        let bph = blocks_per_hour(bt.ticks, input.uptime);
        let pristine = if t.pristine_ratio > 0.0 { input.pristine } else { 0.0 };
        let dpb = drops_per_block(t.base_drops, fortune, pristine, t.pristine_ratio);
        let dph = bph * dpb;
        let npc = ctx.registry.npc_price(&t.drop_item).map(|p| p * dph);
        let best = market_forms::best_value(ctx, &t.drop_item, Some(dph));
        let bazaar = best.as_ref().map(|(v, _)| v * dph);
        let gross = match (npc, bazaar) {
            (Some(a), Some(b)) => Some(a.max(b)),
            (a, b) => a.or(b),
        };
        let areas: Vec<&skypilot_data::curated::MiningAreaDef> = ctx.registry.curated.mining.areas.iter().filter(|a| t.areas.iter().any(|x| x.starts_with(&a.name))).collect();
        let reqs: Vec<String> = areas.iter().filter(|a| a.required_hotm > 0 || a.required_mining > 0).map(|a| format!("{}: HOTM {}{}", a.name, a.required_hotm, if a.required_mining > 0 { format!(", Mining {}", a.required_mining) } else { String::new() })).collect();
        let met = areas.is_empty() || areas.iter().any(|a| hotm >= a.required_hotm && mining_level >= a.required_mining);
        let mut assumptions = vec![format!("{} ticks/block{}", bt.ticks, if bt.soft_capped { " (4-tick soft cap)" } else { "" })];
        assumptions.push(format!("uptime {:.0}%", input.uptime * 100.0));
        if let Some((_, form)) = &best {
            if form != &t.drop_item {
                assumptions.push(format!("sold as {}", ctx.display_name(form)));
            }
        }
        if pristine > 0.0 {
            assumptions.push(format!("pristine {pristine} (procs give Flawed = {} rough)", t.pristine_ratio));
        }
        targets.push(MiningTargetRate {
            target_id: t.id.clone(),
            name: t.name.clone(),
            block_strength: t.block_strength,
            ticks_per_block: bt.ticks,
            blocks_per_hour: bph,
            drops_per_hour: dph,
            drop_item: t.drop_item.clone(),
            npc_coins_per_hour: npc,
            bazaar_coins_per_hour: bazaar,
            fuel_and_consumables_per_hour: fuel_per_hour,
            effective_coins_per_hour: gross.map(|g| g - fuel_per_hour),
            requirements: reqs,
            requirements_met: met,
            assumptions,
            confidence: Confidence::from_score(if input.mining_speed > 0.0 { 0.6 } else { 0.2 }, vec!["Uptime and route efficiency are assumptions".into()]),
        });
    }
    targets.sort_by(|a, b| b.effective_coins_per_hour.unwrap_or(0.0).total_cmp(&a.effective_coins_per_hour.unwrap_or(0.0)));
    let fresh = ctx.market.map(|m| m.freshness().2).unwrap_or(Freshness { source: "Bazaar".into(), updated_at_ms: 0, age_seconds: 0, stale: true });
    MiningCalcResult { input: input.clone(), targets, freshness: fresh }
}

pub fn best_money_target(ctx: &Ctx) -> Option<MiningTargetRate> {
    let r = calculate(ctx, &default_input(ctx), 0.0);
    r.targets.into_iter().find(|t| t.requirements_met && t.effective_coins_per_hour.unwrap_or(0.0) > 0.0)
}

fn powder_buff(ctx: &Ctx) -> f64 {
    let perk = &ctx.registry.curated.mining.powder_buff_perk;
    ctx.analysis.mining.as_ref().and_then(|m| m.nodes.as_ref()).and_then(|n| n.iter().find(|x| &x.id == perk).map(|x| x.level as f64)).unwrap_or(0.0)
}

pub fn powder_plans(ctx: &Ctx, targets: &BTreeMap<String, f64>) -> Vec<PowderPlan> {
    let mining = ctx.analysis.mining.as_ref();
    let input = default_input(ctx);
    let buff = 1.0 + powder_buff(ctx) / 100.0;
    let mut out = Vec::new();
    for m in &ctx.registry.curated.mining.powder_methods {
        let current = mining
            .and_then(|mi| match m.powder.as_str() {
                "MITHRIL" => mi.mithril_powder.as_ref(),
                "GEMSTONE" => mi.gemstone_powder.as_ref(),
                _ => mi.glacite_powder.as_ref(),
            })
            .map(|b| b.available)
            .unwrap_or(0.0);
        let target = targets.get(&m.powder).copied().unwrap_or(current);
        let remaining = (target - current).max(0.0);
        let mut assumptions = vec![m.note.clone()];
        let per_hour = if m.kind == "per_block" {
            let t = ctx.registry.curated.mining.targets.iter().find(|t| t.id == m.target);
            t.and_then(|t| break_time(t.block_strength, input.mining_speed, t.instant_factor)).map(|bt| {
                assumptions.push(format!("{:.0} blocks/h at {} Mining Speed", blocks_per_hour(bt.ticks, input.uptime), input.mining_speed));
                blocks_per_hour(bt.ticks, input.uptime) * m.powder_per_block * buff
            })
        } else {
            Some(m.per_hour * buff)
        };
        if buff > 1.0 {
            assumptions.push(format!("Powder Buff +{:.0}%", (buff - 1.0) * 100.0));
        }
        if !m.verified {
            assumptions.push("SkyPilot assumption (LOW confidence) - edit the rate to your observed value".into());
        }
        out.push(PowderPlan {
            powder: m.powder.clone(),
            current,
            target,
            remaining,
            method: m.name.clone(),
            per_hour,
            hours_remaining: per_hour.filter(|p| *p > 0.0).map(|p| remaining / p),
            assumptions,
        });
    }
    out
}

/// Mining Gear Optimizer: performance per coin from item base stats (items resource) vs the
/// player's best owned piece (lore, incl. upgrades - conservative).
pub fn gear_plan(ctx: &Ctx, purpose: &str, budget: Option<f64>) -> GearPlan {
    let weights: &[(&str, &str, f64)] = match purpose {
        "powder" | "commission" => &[("MINING_SPEED", "Mining Speed", 1.0)],
        "gemstone" | "money" => &[("MINING_SPEED", "Mining Speed", 0.4), ("GEMSTONE_FORTUNE", "Gemstone Fortune", 3.0), ("MINING_FORTUNE", "Mining Fortune", 3.0), ("PRISTINE", "Pristine", 150.0)],
        _ => &[("MINING_SPEED", "Mining Speed", 0.5), ("MINING_FORTUNE", "Mining Fortune", 3.0)],
    };
    let slots = ["DRILL", "PICKAXE", "GAUNTLET", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET"];
    let slot_of = |cat: &str| -> String { if matches!(cat, "DRILL" | "PICKAXE" | "GAUNTLET") { "TOOL".into() } else { cat.to_string() } };
    let mut owned_best: BTreeMap<String, f64> = BTreeMap::new();
    for o in ctx.inv.all_items() {
        let Some(cat) = o.item.skyblock_id.as_deref().and_then(|id| ctx.registry.item(id)).and_then(|i| i.category.clone()) else { continue };
        if !slots.contains(&cat.as_str()) {
            continue;
        }
        let s = o.item.lore_stats();
        let v: f64 = weights.iter().map(|(_, lore, w)| s.get(*lore).copied().unwrap_or(0.0) * w).sum();
        let e = owned_best.entry(slot_of(&cat)).or_insert(0.0);
        *e = e.max(v);
    }
    let mut options = Vec::new();
    for def in ctx.registry.items.values() {
        let Some(cat) = def.category.as_deref() else { continue };
        if !slots.contains(&cat) {
            continue;
        }
        let v: f64 = weights.iter().map(|(k, _, w)| def.stat(k) * w).sum();
        if v <= 0.0 {
            continue;
        }
        let slot = slot_of(cat);
        let base = owned_best.get(&slot).copied().unwrap_or(0.0);
        let gain = v - base;
        if gain <= 0.0 {
            continue;
        }
        let Some(q) = ctx.buy(&def.id) else { continue };
        let (met, unmet) = crate::requirements::item_requirements_met(ctx, &def.id);
        let mut notes = Vec::new();
        if met == Some(false) {
            notes.push(format!("Requires: {}", unmet.join(", ")));
        }
        options.push(UpgradeOption {
            id: def.id.clone(),
            slot,
            name: def.name.clone(),
            cost: Some(q.price),
            stat: weights.iter().map(|(_, l, _)| *l).collect::<Vec<_>>().join("+"),
            gain,
            gain_per_million: gain_per_million(gain, q.price),
            owned: false,
            resale_value: ctx.sell(&def.id).map(|s| s.price),
            tier: def.tier.clone().unwrap_or_default(),
            notes,
        });
    }
    options.sort_by(|a, b| b.gain_per_million.unwrap_or(0.0).total_cmp(&a.gain_per_million.unwrap_or(0.0)));
    let budget_v = budget.unwrap_or_else(|| ctx.spendable());
    let cands: Vec<Candidate> = options
        .iter()
        .filter(|o| o.notes.is_empty())
        .map(|o| Candidate { id: o.id.clone(), slot: o.slot.clone(), cost: o.cost.unwrap_or(f64::MAX), gain: o.gain, resale: o.resale_value.unwrap_or(0.0) * 0.9 })
        .collect();
    let chosen = allocate_budget(&cands, budget_v);
    let (selected, rejected): (Vec<UpgradeOption>, Vec<UpgradeOption>) = options.into_iter().partition(|o| chosen.contains(&o.id));
    let total_cost = selected.iter().filter_map(|o| o.cost).sum();
    let mut total_gain = BTreeMap::new();
    total_gain.insert("score".to_string(), selected.iter().map(|o| o.gain).sum());
    GearPlan {
        purpose: purpose.to_string(),
        budget: Some(budget_v),
        selected,
        rejected: rejected.into_iter().take(40).collect(),
        total_cost,
        total_gain,
        notes: vec![
            "Candidates use base item stats; your owned pieces use lore stats (with reforges/enchants), so gains are conservative.".into(),
            "Score weights per purpose are SkyPilot heuristics.".into(),
        ],
    }
}

pub struct MiningProvider;

impl Provider for MiningProvider {
    fn id(&self) -> &'static str {
        "mining"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        let Some(m) = ctx.analysis.mining.as_ref() else { return out };
        let hotm_label = match ctx.hotm_level() {
            Some(0) => ctx.t("locked", "未解放"),
            Some(h) => h.to_string(),
            None => "?".into(),
        };
        // HOTM upgrades that are affordable right now.
        let wants_mining = goals.iter().any(|g| g.open_nodes().any(|n| n.id == "hotm" || n.id.starts_with("powder:") || n.id == "skill:MINING"))
            || ctx.play_style == skypilot_protocol::goals::PlayStyle::Mining;
        if let Some(plan) = hotm::plan(ctx, "progression") {
            let affordable: Vec<_> = plan.upgrades.iter().filter(|u| u.affordable_now && u.cost > 0.0).take(3).collect();
            if !affordable.is_empty() && wants_mining {
                let mut a = CandidateAction::new("mining-hotm-upgrades", "mining", "Mining", ctx.t(format!("Spend powder on {} HOTM upgrades", affordable.len()), format!("パウダーでHOTMを{}項目強化", affordable.len())));
                a.steps = affordable.iter().map(|u| format!("{} {} → {} ({} {})", u.name, u.from_level, u.to_level, fmt_coins(u.cost), skypilot_items::fingerprint::title_case(&u.powder))).collect();
                a.hours = 0.1;
                a.long_term_value = 0.8;
                a.confidence = 0.85;
                a.why = ctx.t("You already have the powder: these perks raise your mining speed/fortune permanently at no coin cost.", "必要なパウダーは既に所持しています。コインを使わずに採掘速度・幸運を恒久的に上げられます。");
                a.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo, DataSource::SkyPilotHeuristic];
                a.link = Some("/mining?tab=hotm".into());
                for g in goals {
                    if let Some(i) = g.impact("skill:MINING", 0.05) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                }
                out.push(a);
            }
        }
        // HOTM level goal: XP remaining (commissions).
        for g in goals {
            if let Some(n) = g.open("hotm") {
                let target = n.target.unwrap_or(0.0) as u32;
                let xp_left = m.hotm.as_ref().map(|h| ctx.registry.neu.leveling.hotm_table().xp_remaining(h.xp, target).unwrap_or(0.0)).unwrap_or(0.0);
                let mut a = CandidateAction::new("mining-hotm-xp", "mining", "Mining", ctx.t(format!("Complete commissions for HOTM XP (HOTM {hotm_label} → {target})"), format!("コミッションでHOTM XPを稼ぐ（HOTM {hotm_label} → {target}）")));
                if let Some(h) = ctx.hotm_level() {
                    a.metrics.push(Metric::change("HOTM", h as f64, target as f64, None));
                }
                a.metrics.push(Metric::value(ctx.t("HOTM XP left", "残りHOTM XP"), xp_left, None));
                a.hours = 2.0;
                a.difficulty = 1.5;
                a.long_term_value = 0.8;
                a.confidence = 0.5;
                a.confidence_reasons = vec![ctx.t("HOTM XP per commission is not modelled", "1コミッションあたりのHOTM XPは未モデル化")];
                a.why = ctx.t("HOTM tiers are gated by HOTM XP, earned mainly from Dwarven/Glacite commissions.", "HOTMの段階はHOTM XPで解放され、主にドワーフ鉱山/グレイサイトのコミッションで獲得します。");
                if let Some(i) = g.impact("hotm", 0.2) {
                    a.goal_impacts.insert(g.id.clone(), i);
                }
                a.link = Some("/mining?tab=hotm".into());
                a.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo];
                out.push(a);
            }
            for n in g.open_nodes().filter(|n| n.id.starts_with("powder:")) {
                let powder = &n.id[7..];
                let mut t = BTreeMap::new();
                t.insert(powder.to_string(), n.target.unwrap_or(0.0));
                if let Some(p) = powder_plans(ctx, &t).into_iter().find(|p| p.powder == powder) {
                    let mut a = CandidateAction::new(format!("mining-powder-{powder}"), "mining", "Mining", ctx.t(format!("{} for {} Powder ({} left)", p.method, skypilot_items::fingerprint::title_case(powder), fmt_coins(p.remaining)), format!("{}で{}パウダー獲得（残り{}）", p.method, skypilot_items::fingerprint::title_case(powder), fmt_coins(p.remaining))));
                    a.hours = p.hours_remaining.unwrap_or(2.0).min(3.0);
                    a.confidence = if p.assumptions.iter().any(|x| x.contains("LOW")) { 0.35 } else { 0.6 };
                    a.why = p.assumptions.join("; ");
                    let frac = p.per_hour.map(|r| (r * a.hours / p.remaining.max(1.0)).min(1.0)).unwrap_or(0.1);
                    if let Some(i) = g.impact(&n.id, frac) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                    a.link = Some("/mining?tab=powder".into());
                    out.push(a);
                }
            }
        }
        // Best gear upgrade per coin.
        if wants_mining {
            let plan = gear_plan(ctx, "progression", None);
            if let Some(best) = plan.selected.first() {
                let mut a = CandidateAction::new("mining-gear", "mining", "Mining", ctx.t(format!("Mining gear: {} ({} slot)", best.name, best.slot), format!("採掘装備: {}（{}）", best.name, best.slot)));
                a.cost = best.cost.unwrap_or(0.0);
                a.hours = 0.1;
                a.long_term_value = 0.7;
                a.confidence = 0.55;
                a.why = ctx.t(format!("Best mining performance per coin among affordable upgrades ({:.1} score per 1M).", best.gain_per_million.unwrap_or(0.0)), format!("購入可能な強化の中で採掘性能あたりの費用対効果が最も高いです（1Mあたり{:.1}）。", best.gain_per_million.unwrap_or(0.0)));
                a.link = Some("/mining?tab=gear".into());
                a.sources = vec![DataSource::HypixelResources, DataSource::Market, DataSource::SkyPilotHeuristic];
                out.push(a);
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("mining", ctx.t("Mining", "マイニング"));
        let Some(m) = ctx.analysis.mining.as_ref() else {
            r.unknowns.push(ctx.t("Heart of the Mountain not unlocked", "Heart of the Mountain が未解放です"));
            return r;
        };
        if let Some(l) = ctx.skill("MINING") {
            r.summary.push(Metric::value("Mining", l.level_with_progress, None));
        }
        match m.hotm.as_ref() {
            Some(h) if h.level == 0 => r.summary.push(Metric::text("HOTM", ctx.t("Locked", "未解放"))),
            Some(h) => r.summary.push(Metric::value("HOTM", h.level_with_progress, None)),
            None => r.unknowns.push("HOTM".into()),
        }
        for (label, p) in [("Mithril Powder", &m.mithril_powder), ("Gemstone Powder", &m.gemstone_powder), ("Glacite Powder", &m.glacite_powder)] {
            if let Some(b) = p.as_ref() {
                r.summary.push(Metric::value(label, b.available, None));
            }
        }
        let stats = player_stats(ctx);
        let mut st = section("stats", ctx.t("Your mining stats (best owned setup)", "採掘ステータス（所持品の最良構成）"));
        st.metrics = MINING_STATS.iter().map(|s| Metric::value(*s, stats.get(*s).copied().unwrap_or(0.0), None)).collect();
        st.bullets = m.gear_stats.sources.clone();
        st.notes.push(ctx.t("Read from item lore; pets, potions and temporary buffs are not included.", "アイテム説明文から読み取り。ペット・ポーション・一時バフは含みません。"));
        r.sections.push(st);
        // Money calculator
        let calc = calculate(ctx, &default_input(ctx), 0.0);
        let mut mc = section("money", ctx.t("Mining money calculator", "採掘金策計算"));
        mc.table = Some(table(
            &["Target", "Ticks", "Blocks/h", "Drops/h", "NPC/h", "Bazaar/h", "Effective/h", "Access"],
            calc.targets
                .iter()
                .map(|t| {
                    vec![
                        text(&t.name),
                        num(t.ticks_per_block, None),
                        num(t.blocks_per_hour.round(), None),
                        num(t.drops_per_hour.round(), None),
                        coins_opt(t.npc_coins_per_hour),
                        coins_opt(t.bazaar_coins_per_hour),
                        coins_opt(t.effective_coins_per_hour),
                        if t.requirements_met { badge(ctx.t("OK", "OK"), Tone::Good) } else { badge(t.requirements.join("; "), Tone::Warn) },
                    ]
                })
                .collect(),
        ));
        mc.notes.push(ctx.t("Coins/hour are computed live from your stats and current prices; fuel/consumables can be entered in the calculator.", "コイン/時は現在のステータスとライブ価格から計算。燃料・消耗品は計算機で入力できます。"));
        mc.sources = vec![DataSource::SkyPilotRegistry, DataSource::Market, DataSource::HypixelProfile];
        r.sections.push(mc);
        // HOTM planner
        if let Some(plan) = hotm::plan(ctx, "progression") {
            let mut hs = section("hotm", ctx.t(format!("HOTM planner — {}", plan.preset_name), format!("HOTMプランナー — {}", plan.preset_name)));
            hs.table = Some(table(
                &["Perk", "Level", "Powder", "Cost", "Affordable now"],
                plan.upgrades.iter().take(25).map(|u| vec![text(&u.name), text(format!("{} → {}", u.from_level, u.to_level)), text(skypilot_items::fingerprint::title_case(&u.powder)), num(u.cost, None), yes_no(ctx, u.affordable_now)]).collect(),
            ));
            for (p, c) in &plan.required_powder {
                hs.metrics.push(Metric::change(format!("{} Powder", skypilot_items::fingerprint::title_case(p)), plan.available_powder.get(p).copied().unwrap_or(0.0), *c, None));
            }
            if !plan.blocked_by_hotm_level.is_empty() {
                hs.notes.push(format!("{}: {}", ctx.t("Needs higher HOTM", "上位HOTMが必要"), plan.blocked_by_hotm_level.join(", ")));
            }
            hs.notes.push(ctx.t("Costs come from the NEU-REPO HOTM layout formulas; presets are SkyPilot guidance.", "費用はNEU-REPOのHOTMレイアウト式から計算。プリセットはSkyPilotの推奨です。"));
            r.sections.push(hs);
        }
        // Powder planner
        let mut targets = BTreeMap::new();
        if let Some(plan) = hotm::plan(ctx, "progression") {
            targets = plan.required_powder.clone();
        }
        for g in goals {
            for n in g.graph.nodes.iter().filter(|n| n.id.starts_with("powder:")) {
                targets.insert(n.id[7..].to_string(), n.target.unwrap_or(0.0));
            }
        }
        let mut ps = section("powder", ctx.t("Powder planner", "パウダープランナー"));
        ps.table = Some(table(
            &["Powder", "Current", "Target", "Remaining", "Method", "Per hour", "Time"],
            powder_plans(ctx, &targets)
                .iter()
                .map(|p| vec![text(&p.powder), num(p.current, None), num(p.target, None), num(p.remaining, None), text(&p.method), p.per_hour.map(|x| num(x.round(), None)).unwrap_or(unknown("No rate")), p.hours_remaining.map(hours).unwrap_or(unknown("No rate"))])
                .collect(),
        ));
        ps.notes.push(ctx.t("Per-block mithril powder is from the wiki; chest/commission rates are editable assumptions.", "ミスリルの1ブロックあたりパウダーはWiki値。チェスト/コミッションの効率は編集可能な仮定値です。"));
        r.sections.push(ps);
        // Gear optimizer
        let plan = gear_plan(ctx, "progression", None);
        let mut gs = section("gear", ctx.t("Mining gear optimizer (performance per coin)", "採掘装備オプティマイザ（費用対効果）"));
        gs.table = Some(table(
            &["Item", "Slot", "Cost", "Score gain", "Gain / 1M", "Selected"],
            plan.selected.iter().map(|o| (o, true)).chain(plan.rejected.iter().take(15).map(|o| (o, false))).map(|(o, sel)| vec![item(ctx, &o.id), text(&o.slot), coins_opt(o.cost), num((o.gain * 10.0).round() / 10.0, None), num((o.gain_per_million.unwrap_or(0.0) * 100.0).round() / 100.0, None), yes_no(ctx, sel)]).collect(),
        ));
        gs.notes = plan.notes.clone();
        r.sections.push(gs);
        // Route guide (areas)
        let mut rg = section("routes", ctx.t("Mining areas", "採掘エリア"));
        let hotm_lvl = ctx.hotm_level().unwrap_or(0);
        let mining_lvl = ctx.skill_level("MINING").unwrap_or(0);
        rg.table = Some(table(
            &["Area", "Requirement", "Access", "Best target here", "Est. coins/h"],
            ctx.registry
                .curated
                .mining
                .areas
                .iter()
                .map(|a| {
                    let best = calc.targets.iter().find(|t| ctx.registry.curated.mining.targets.iter().any(|d| d.id == t.target_id && d.areas.iter().any(|x| x.starts_with(&a.name))));
                    vec![
                        text(&a.name),
                        text(format!("HOTM {}{}", a.required_hotm, if a.required_mining > 0 { format!(", Mining {}", a.required_mining) } else { String::new() })),
                        yes_no(ctx, hotm_lvl >= a.required_hotm && mining_lvl >= a.required_mining),
                        best.map(|t| text(&t.name)).unwrap_or(text("—")),
                        best.map(|t| coins_opt(t.effective_coins_per_hour)).unwrap_or(text("—")),
                    ]
                })
                .collect(),
        ));
        rg.notes.push(ctx.t("Route guidance only - SkyPilot never reveals hidden blocks or moves you.", "ルート案内のみ。隠しブロックの表示や自動移動は行いません。"));
        r.sections.push(rg);
        let _ = fmt_hours;
        r.freshness = market_freshness(ctx);
        r
    }
}
