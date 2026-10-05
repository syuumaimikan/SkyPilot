//! Goal Graph builder: GoalSpec -> DAG of requirements with the player's current progress.
//! Hard requirements come from the Hypixel items resource / curated registry; readiness
//! nodes are SkyPilot heuristics and are marked `hard_requirement = false`.

use skypilot_data::Requirement;
use skypilot_protocol::common::DataSource;
use skypilot_protocol::goals::{GoalGraph, GoalKind, GoalNode, GoalSpec, NodeKind, NodeStatus};

use crate::ctx::{Ctx, fmt_coins, fmt_hours};
use crate::providers::{accessories, dungeons, money, slayer};

pub fn status_of(current: Option<f64>, target: Option<f64>) -> (NodeStatus, f64) {
    match (current, target) {
        (Some(c), Some(t)) if t <= 0.0 || c >= t => (NodeStatus::Satisfied, 1.0),
        (Some(c), Some(t)) if c > 0.0 => (NodeStatus::InProgress, (c / t).clamp(0.0, 0.999)),
        (Some(_), Some(_)) => (NodeStatus::NotStarted, 0.0),
        _ => (NodeStatus::Unknown, 0.0),
    }
}

pub struct GraphBuilder<'c, 'a> {
    ctx: &'c Ctx<'a>,
    nodes: Vec<GoalNode>,
    depth: usize,
    /// Coins needed per purpose (purchases, quest costs); the money node needs their sum.
    money_needs: Vec<(String, f64)>,
}

#[allow(clippy::too_many_arguments)]
fn node(id: &str, kind: NodeKind, title: String, current: Option<f64>, target: Option<f64>, unit: Option<&str>, hard: bool, source: DataSource) -> GoalNode {
    let (status, progress) = status_of(current, target);
    GoalNode {
        id: id.to_string(),
        kind,
        title,
        detail: None,
        status,
        progress,
        current,
        target,
        unit: unit.map(str::to_string),
        hard_requirement: hard,
        source,
        children: Vec::new(),
        estimated_cost: None,
        estimated_hours: None,
    }
}

impl<'c, 'a> GraphBuilder<'c, 'a> {
    pub fn new(ctx: &'c Ctx<'a>) -> Self {
        GraphBuilder { ctx, nodes: Vec::new(), depth: 0, money_needs: Vec::new() }
    }

    /// Insert or merge (same id = same requirement; keep the stricter target).
    fn upsert(&mut self, n: GoalNode) -> String {
        if let Some(existing) = self.nodes.iter_mut().find(|x| x.id == n.id) {
            if let (Some(a), Some(b)) = (existing.target, n.target) {
                if b > a {
                    existing.target = Some(b);
                    existing.title = n.title.clone();
                    let (s, p) = status_of(existing.current, existing.target);
                    existing.status = s;
                    existing.progress = p;
                    existing.hard_requirement |= n.hard_requirement;
                    if n.detail.is_some() {
                        existing.detail = n.detail.clone();
                    }
                    if n.estimated_hours.is_some() {
                        existing.estimated_hours = n.estimated_hours;
                    }
                }
            }
            existing.hard_requirement |= n.hard_requirement;
            return existing.id.clone();
        }
        let id = n.id.clone();
        self.nodes.push(n);
        id
    }

    fn link(&mut self, parent: &str, child: &str) {
        if parent == child {
            return;
        }
        if let Some(p) = self.nodes.iter_mut().find(|x| x.id == parent) {
            if !p.children.iter().any(|c| c == child) {
                p.children.push(child.to_string());
            }
        }
    }

    fn set(&mut self, id: &str, f: impl FnOnce(&mut GoalNode)) {
        if let Some(n) = self.nodes.iter_mut().find(|x| x.id == id) {
            f(n);
        }
    }

    pub fn build(ctx: &'c Ctx<'a>, spec: &GoalSpec) -> GoalGraph {
        let mut b = GraphBuilder::new(ctx);
        let root = b.goal(&spec.kind, &spec.title);
        GoalGraph { root, nodes: b.nodes, satisfied_pruned: Vec::new() }
    }

    fn goal(&mut self, kind: &GoalKind, title: &str) -> String {
        self.depth += 1;
        let id = match kind {
            GoalKind::AcquireItem { item_id } => self.item(item_id),
            GoalKind::SkillLevel { skill, level } => self.skill(skill, *level),
            GoalKind::SlayerLevel { slayer, level } => self.slayer(slayer, *level, true),
            GoalKind::CatacombsLevel { level } => self.catacombs(*level, None),
            GoalKind::ClassLevel { class, level } => {
                let cur = self.ctx.analysis.dungeons.as_ref().and_then(|d| d.classes.iter().find(|c| c.id == *class)).map(|c| c.level_with_progress);
                self.upsert(node(&format!("class:{class}"), NodeKind::Dungeon, title.to_string(), cur, Some(*level as f64), Some("level"), true, DataSource::HypixelProfile))
            }
            GoalKind::HotmLevel { level } => self.hotm(*level),
            GoalKind::HotfLevel { level } => {
                let cur = self.ctx.analysis.foraging.as_ref().and_then(|f| f.hotf.as_ref()).map(|h| h.level as f64);
                self.upsert(node("hotf", NodeKind::Skill, title.to_string(), cur.or(Some(0.0)), Some(*level as f64), Some("level"), true, DataSource::HypixelProfile))
            }
            GoalKind::MagicalPower { target } => self.mp(*target, true),
            GoalKind::Coins { amount } => self.money(*amount, title),
            GoalKind::Networth { amount } => {
                let cur = self.ctx.analysis.networth.as_ref().map(|n| n.total);
                let id = self.upsert(node("networth", NodeKind::Money, title.to_string(), cur, Some(*amount), Some("coins"), true, DataSource::Market));
                let m = self.money_child((amount - cur.unwrap_or(0.0)).max(0.0) + self.ctx.liquid_coins(), title);
                self.link(&id, &m);
                id
            }
            GoalKind::SkyblockLevel { level } => {
                let cur = self.ctx.analysis.skyblock_level.as_ref().copied();
                self.upsert(node("sblevel", NodeKind::Skill, title.to_string(), cur, Some(*level as f64), Some("level"), true, DataSource::HypixelProfile))
            }
            GoalKind::FloorCompletion { floor, master } => self.floor(&format!("{}{}", if *master { "M" } else { "F" }, floor), true),
            GoalKind::KuudraTier { tier } => self.kuudra(tier),
            GoalKind::GardenLevel { level } => self.garden(*level),
            GoalKind::PowderTarget { powder, amount } => {
                let cur = self.ctx.analysis.mining.as_ref().and_then(|m| match powder.as_str() {
                    "GEMSTONE" => m.gemstone_powder.as_ref(),
                    "GLACITE" => m.glacite_powder.as_ref(),
                    _ => m.mithril_powder.as_ref(),
                }).map(|p| p.available);
                self.upsert(node(&format!("powder:{powder}"), NodeKind::Powder, title.to_string(), cur.or(Some(0.0)), Some(*amount), Some("powder"), true, DataSource::HypixelProfile))
            }
            GoalKind::CollectionTier { collection, tier } => self.collection(collection, *tier),
        };
        self.depth -= 1;
        id
    }

    fn item(&mut self, item_id: &str) -> String {
        let ctx = self.ctx;
        let name = ctx.display_name(item_id);
        let owned = ctx.owns(item_id);
        let id = self.upsert(node(&format!("item:{item_id}"), NodeKind::Item, name.clone(), Some(if owned { 1.0 } else { 0.0 }), Some(1.0), None, true, DataSource::HypixelResources));
        let def = ctx.registry.item(item_id).cloned();
        if let Some(def) = &def {
            for r in &def.requirements {
                let c = self.requirement(r, true);
                self.link(&id, &c);
            }
            for r in &def.catacombs_requirements {
                let c = self.requirement(r, false);
                self.set(&c, |n| {
                    n.detail = Some(ctx.t("Needed to use this item inside dungeons", "ダンジョン内で使用するために必要"));
                });
                self.link(&id, &c);
            }
        }
        // Acquisition: cheapest of buying now vs crafting.
        let buy = ctx.buy(item_id);
        let mut calc = skypilot_pricing::craft::CraftCalculator::new(ctx.registry, ctx.oracle);
        let craft = calc.craft_cost(item_id).map(|(c, n, ch)| (c / n, ch));
        let (cost, how) = match (&buy, &craft) {
            (Some(b), Some((c, _))) if *c < b.price * 0.97 => (Some(*c), ctx.t(format!("Craft from components (~{})", fmt_coins(*c)), format!("素材からクラフト（約{}）", fmt_coins(*c)))),
            (Some(b), _) => (Some(b.price), ctx.t(format!("Buy ({} ~{})", b.source, fmt_coins(b.price)), format!("購入（{} 約{}）", b.source, fmt_coins(b.price)))),
            (None, Some((c, _))) => (Some(*c), ctx.t(format!("Craft from components (~{})", fmt_coins(*c)), format!("素材からクラフト（約{}）", fmt_coins(*c)))),
            (None, None) => (None, ctx.t("Acquisition price Unknown (no market data)", "入手価格は不明（市場データなし）")),
        };
        let liquid = ctx.liquid_coins();
        let acq = self.upsert(GoalNode {
            detail: Some(match &craft {
                Some((_, ch)) => format!("{how}. {}: {}", ctx.t("Components", "素材"), ch.iter().map(|c| format!("{}× {}", c.amount, c.name)).collect::<Vec<_>>().join(", ")),
                None => how.clone(),
            }),
            estimated_cost: cost,
            ..node(&format!("acquire:{item_id}"), NodeKind::Acquisition, ctx.t(format!("Acquire {name}"), format!("{name}を入手")), if owned { Some(1.0) } else { cost.map(|c| (liquid / c).min(1.0)) }, Some(1.0), None, true, DataSource::Market)
        });
        if owned {
            self.set(&acq, |n| {
                n.status = NodeStatus::Satisfied;
                n.progress = 1.0;
            });
        }
        self.link(&id, &acq);
        if let Some(c) = cost {
            if !owned && liquid < c {
                let m = self.money_child(c, &name);
                self.link(&acq, &m);
            }
        }
        id
    }

    /// Coins needed for `purpose`. Separate purposes add up (buying an item and paying for
    /// quests both spend coins); the same purpose keeps its larger amount.
    fn money_child(&mut self, target: f64, purpose: &str) -> String {
        match self.money_needs.iter_mut().find(|(p, _)| p == purpose) {
            Some((_, t)) => *t = t.max(target),
            None => self.money_needs.push((purpose.to_string(), target)),
        }
        let total: f64 = self.money_needs.iter().map(|(_, t)| t).sum();
        let title = match self.money_needs.as_slice() {
            [(p, t)] => self.ctx.t(format!("{} liquid coins for {p}", fmt_coins(*t)), format!("{p}用の資金 {}", fmt_coins(*t))),
            many => {
                let parts = many.iter().map(|(p, t)| format!("{p} {}", fmt_coins(*t))).collect::<Vec<_>>().join(" + ");
                self.ctx.t(format!("{} liquid coins ({parts})", fmt_coins(total)), format!("資金 {}（{parts}）", fmt_coins(total)))
            }
        };
        self.money(total, &title)
    }

    fn money(&mut self, target: f64, title: &str) -> String {
        let ctx = self.ctx;
        let liquid = ctx.liquid_coins();
        let mut n = node("money", NodeKind::Money, title.to_string(), Some(liquid), Some(target), Some("coins"), true, DataSource::HypixelProfile);
        let gap = (target - liquid).max(0.0);
        if gap > 0.0 {
            if let Some(best) = money::rank(ctx).into_iter().find(|m| m.requirements_met && m.coins_per_hour.is_some_and(|r| r.low > 0.0)) {
                let r = best.coins_per_hour.unwrap();
                let mid = (r.low + r.high) / 2.0;
                n.estimated_hours = Some(gap / mid);
                n.detail = Some(ctx.t(
                    format!("{} missing. Best method for you: {} (~{}/h, ≈{})", fmt_coins(gap), best.name, fmt_coins(mid), fmt_hours(gap / mid)),
                    format!("不足 {}。おすすめ金策: {}（約{}/時、約{}）", fmt_coins(gap), best.name, fmt_coins(mid), fmt_hours(gap / mid)),
                ));
            } else {
                n.detail = Some(ctx.t(format!("{} missing.", fmt_coins(gap)), format!("不足 {}。", fmt_coins(gap))));
            }
        }
        self.upsert(n)
    }

    fn requirement(&mut self, r: &Requirement, hard: bool) -> String {
        let ctx = self.ctx;
        match r {
            Requirement::Skill { skill, level } => {
                let id = self.skill(skill, *level);
                self.set(&id, |n| n.hard_requirement = hard);
                id
            }
            Requirement::Slayer { slayer, level } => self.slayer(slayer, *level, hard),
            Requirement::DungeonSkill { level, .. } => self.catacombs(*level, Some(hard)),
            Requirement::DungeonTier { tier, .. } => self.floor(&format!("F{tier}"), hard),
            Requirement::HeartOfTheMountain { tier } => self.hotm(*tier),
            Requirement::Collection { collection, tier } => self.collection(collection, *tier),
            Requirement::GardenLevel { level } => self.garden(*level),
            Requirement::KuudraCompletion { tier } => self.kuudra(tier),
            Requirement::CrimsonReputation { faction, reputation } => {
                let cur = crate::requirements::progress(ctx, r).map(|x| x.0);
                self.upsert(node("rep", NodeKind::Requirement, format!("{} reputation {}", skypilot_items::fingerprint::title_case(faction), reputation), cur, Some(*reputation as f64), Some("rep"), hard, DataSource::HypixelResources))
            }
            other => {
                let p = crate::requirements::progress(ctx, other);
                let id = format!("req:{}", other.describe().to_lowercase().replace(' ', "_"));
                let mut n = node(&id, NodeKind::Requirement, other.describe(), p.map(|x| x.0), p.map(|x| x.1).or(Some(1.0)), None, hard, DataSource::HypixelResources);
                if p.is_none() {
                    n.status = NodeStatus::Unknown;
                    n.detail = Some(ctx.t("Not verifiable from the API", "APIでは確認できません"));
                }
                self.upsert(n)
            }
        }
    }

    fn skill(&mut self, skill: &str, level: u32) -> String {
        let ctx = self.ctx;
        let s = ctx.skill(skill);
        let name = s.map(|x| x.name.clone()).unwrap_or_else(|| skypilot_items::fingerprint::title_case(skill));
        let mut n = node(&format!("skill:{}", skill.to_ascii_uppercase()), NodeKind::Skill, format!("{name} {level}"), s.map(|x| x.level as f64), Some(level as f64), Some("level"), true, DataSource::HypixelProfile);
        if let (Some(s), Some(def)) = (s, ctx.registry.skill_table(skill)) {
            if let Some(rem) = def.table.xp_remaining(s.xp, level) {
                n.detail = Some(ctx.t(format!("{} XP remaining", fmt_coins(rem)), format!("残り {} XP", fmt_coins(rem))));
            }
        }
        if s.is_none() {
            n.status = NodeStatus::Unknown;
            n.detail = Some(ctx.t("Skills API disabled", "スキルAPIが無効"));
        }
        self.upsert(n)
    }

    fn slayer(&mut self, slayer_id: &str, level: u32, hard: bool) -> String {
        let ctx = self.ctx;
        let Some(def) = ctx.registry.curated.slayer(slayer_id).cloned() else {
            return self.upsert(node(&format!("slayer:{slayer_id}"), NodeKind::Slayer, format!("{slayer_id} Slayer {level}"), None, Some(level as f64), Some("level"), hard, DataSource::HypixelResources));
        };
        let cur = ctx.slayer(&def.id).map(|s| s.level.level as f64).unwrap_or(0.0);
        let mut n = node(&format!("slayer:{}", def.id), NodeKind::Slayer, format!("{} Slayer {}", def.short, level), Some(cur), Some(level as f64), Some("level"), hard, DataSource::HypixelResources);
        let plan = slayer::plan(ctx, &def, level);
        if let Some(p) = &plan {
            n.estimated_cost = p.estimated_cost;
            n.estimated_hours = p.estimated_hours.map(|h| (h.low + h.high) / 2.0);
            if let (Some(pr), true) = (&p.projection, p.xp_remaining > 0.0) {
                // Long goal: totals follow the staged plan (Recommended gear, higher tier).
                n.estimated_cost = Some(pr.cost);
                n.estimated_hours = pr.hours.map(|h| (h.low + h.high) / 2.0);
                let stages = pr.stages.iter().map(|s| format!("T{}×{}", s.tier, s.bosses)).collect::<Vec<_>>().join(" → ");
                let hours = pr.hours.map(|h| format!("{}–{}", fmt_hours(h.low), fmt_hours(h.high))).unwrap_or_else(|| "?".into());
                let now = p.estimated_hours.map(|h| fmt_hours((h.low + h.high) / 2.0)).unwrap_or_else(|| "?".into());
                let ev = pr.expected_drop_value.map(|v| fmt_coins(v)).unwrap_or_else(|| "?".into());
                n.detail = Some(ctx.t(
                    format!(
                        "{} XP left. With Recommended gear move up to T{}: {stages} ({hours}), boss cost {}, expected drops {ev}. At your current gear (T{}) it would take ~{now}.",
                        fmt_coins(p.xp_remaining), pr.tier, fmt_coins(pr.cost), p.recommended_tier
                    ),
                    format!(
                        "残り{} XP。推奨装備に揃えてT{}へ移行: {stages}（{hours}）、召喚費用{}、ドロップ期待値{ev}。現在の装備（T{}）のままだと約{now}。",
                        fmt_coins(p.xp_remaining), pr.tier, fmt_coins(pr.cost), p.recommended_tier
                    ),
                ));
            } else if p.xp_remaining > 0.0 {
                n.detail = Some(ctx.t(
                    format!("{} XP left → about {} × T{} {} ({}), boss cost {}{}", fmt_coins(p.xp_remaining), p.bosses_needed.unwrap_or(0.0), p.recommended_tier, def.name, p.estimated_hours.map(|h| format!("{}–{}", fmt_hours(h.low), fmt_hours(h.high))).unwrap_or_else(|| "?".into()), fmt_coins(p.estimated_cost.unwrap_or(0.0)), p.expected_drop_value.map(|ev| format!(", expected drops {}", fmt_coins(ev))).unwrap_or_default()),
                    format!("残り{} XP → T{} {}を約{}体（{}）、召喚費用{}{}", fmt_coins(p.xp_remaining), p.recommended_tier, def.name, p.bosses_needed.unwrap_or(0.0), p.estimated_hours.map(|h| format!("{}～{}", fmt_hours(h.low), fmt_hours(h.high))).unwrap_or_else(|| "?".into()), fmt_coins(p.estimated_cost.unwrap_or(0.0)), p.expected_drop_value.map(|ev| format!("、ドロップ期待値{}", fmt_coins(ev))).unwrap_or_default()),
                ));
            }
        }
        let id = self.upsert(n);
        if cur >= level as f64 || self.depth > 4 {
            return id;
        }
        // Unlock prerequisite
        if !slayer::unlocked(ctx, &def, 1) {
            if let Some(pre) = ctx.registry.curated.slayer(&def.unlock_slayer).cloned() {
                let killed = ctx.slayer(&pre.id).and_then(|s| s.highest_tier_killed).unwrap_or(0) as f64;
                let u = self.upsert(node(&format!("slayer-unlock:{}", def.id), NodeKind::Requirement, ctx.t(format!("Defeat {} Tier {}", pre.name, def.unlock_tier), format!("{} T{}を討伐", pre.name, def.unlock_tier)), Some(killed), Some(def.unlock_tier as f64), Some("tier"), true, DataSource::SkyPilotRegistry));
                self.link(&id, &u);
            }
        }
        // Readiness: MP for the recommended tier (soft)
        if let Some(p) = &plan {
            let rec_mp = def.recommended_mp.get(p.recommended_tier.max(1) as usize).or(def.recommended_mp.last()).copied().unwrap_or(0);
            let want_tier = (p.recommended_tier + 1).min(def.max_tier);
            let rec_mp_next = def.recommended_mp.get(want_tier as usize - 1).copied().unwrap_or(rec_mp);
            if rec_mp_next > 0 && ctx.magical_power().is_some_and(|mp| mp < rec_mp_next) {
                let m = self.mp(rec_mp_next, false);
                self.set(&m, |n| n.detail = Some(ctx.t(format!("Recommended for {} Tier {}", def.name, want_tier), format!("{} T{}の推奨値", def.name, want_tier))));
                self.link(&id, &m);
            }
            if let Some(cost) = p.projection.as_ref().map(|pr| pr.cost).or(p.estimated_cost) {
                if cost > ctx.liquid_coins() {
                    let mny = self.money_child(cost, &format!("{} quests", def.name));
                    self.link(&id, &mny);
                }
            }
        }
        id
    }

    fn catacombs(&mut self, level: u32, hard: Option<bool>) -> String {
        let ctx = self.ctx;
        let d = ctx.analysis.dungeons.as_ref();
        let cur = d.map(|d| d.catacombs.level as f64);
        let mut n = node("cata", NodeKind::Dungeon, format!("Catacombs {level}"), cur.or(Some(0.0)), Some(level as f64), Some("level"), hard.unwrap_or(true), DataSource::HypixelProfile);
        if let Some(d) = d {
            let need = ctx.registry.neu.leveling.catacombs_table().xp_remaining(d.catacombs.xp, level).unwrap_or(0.0);
            if need > 0.0 {
                match dungeons::runs_for_xp(ctx, need) {
                    Some((f, runs, h)) => {
                        n.estimated_hours = Some(h);
                        n.detail = Some(ctx.t(format!("{} XP left ≈ {} runs of {} ({})", fmt_coins(need), runs, f, fmt_hours(h)), format!("残り{} XP ≈ {} {}周（{}）", fmt_coins(need), f, runs, fmt_hours(h))));
                    }
                    None => n.detail = Some(ctx.t(format!("{} XP left", fmt_coins(need)), format!("残り{} XP", fmt_coins(need)))),
                }
            }
        }
        self.upsert(n)
    }

    fn hotm(&mut self, level: u32) -> String {
        let ctx = self.ctx;
        // HOTM not unlocked at all => level 0; unlocked but XP missing from the API => Unknown.
        let cur = match ctx.analysis.mining.as_ref() {
            None => Some(0.0),
            Some(m) => m.hotm.as_ref().map(|h| h.level as f64),
        };
        let mut n = node("hotm", NodeKind::Hotm, format!("HOTM {level}"), cur, Some(level as f64), Some("tier"), true, DataSource::HypixelProfile);
        if cur.is_none() {
            n.detail = Some(ctx.t("HOTM level not available from API", "HOTMレベルはAPIから取得できません"));
        }
        if let Some(h) = ctx.analysis.mining.as_ref().and_then(|m| m.hotm.as_ref()) {
            if let Some(rem) = ctx.registry.neu.leveling.hotm_table().xp_remaining(h.xp, level) {
                if rem > 0.0 {
                    n.detail = Some(ctx.t(format!("{} HOTM XP left (commissions)", fmt_coins(rem)), format!("残り{} HOTM XP（コミッション）", fmt_coins(rem))));
                }
            }
        }
        self.upsert(n)
    }

    fn mp(&mut self, target: u32, hard: bool) -> String {
        let ctx = self.ctx;
        let cur = ctx.magical_power().map(|m| m as f64);
        let mut n = node("mp", NodeKind::MagicalPower, format!("Magical Power {target}"), cur, Some(target as f64), Some("MP"), hard, if hard { DataSource::HypixelProfile } else { DataSource::SkyPilotHeuristic });
        if cur.is_some_and(|c| c < target as f64) {
            let (plan, cost, reached) = accessories::plan_to(ctx, Some(target), f64::MAX);
            n.estimated_cost = Some(cost);
            n.estimated_hours = Some(0.1 + plan.len() as f64 * 0.02);
            n.detail = Some(ctx.t(
                format!("{} cheapest accessories → MP {} for {}", plan.len(), reached, fmt_coins(cost)),
                format!("安いアクセサリー{}個でMP {}（{}）", plan.len(), reached, fmt_coins(cost)),
            ));
        }
        if cur.is_none() {
            n.status = NodeStatus::Unknown;
            n.detail = Some(ctx.t("Accessory bag not visible (Inventory API disabled)", "アクセサリーバッグが見えません（インベントリAPI無効）"));
        }
        self.upsert(n)
    }

    fn floor(&mut self, floor_id: &str, hard: bool) -> String {
        let ctx = self.ctx;
        let Some(f) = dungeons::floor(ctx, floor_id).cloned() else {
            return self.upsert(node(&format!("floor:{floor_id}"), NodeKind::Dungeon, format!("{floor_id} completion"), None, Some(1.0), None, hard, DataSource::SkyPilotRegistry));
        };
        let done = dungeons::completions(ctx, &f.id) > 0;
        let id = self.upsert(node(&format!("floor:{}", f.id), NodeKind::Dungeon, ctx.t(format!("Complete {} ({})", f.id, f.boss), format!("{}をクリア（{}）", f.id, f.boss)), Some(if done { 1.0 } else { 0.0 }), Some(1.0), None, hard, DataSource::SkyPilotRegistry));
        if done || self.depth > 6 {
            return id;
        }
        let readiness = ctx.registry.curated.dungeons.readiness.get(&f.id).cloned();
        let cata_target = readiness.as_ref().map(|r| r.catacombs.max(f.required_catacombs)).unwrap_or(f.required_catacombs);
        let c = self.catacombs(cata_target, Some(true));
        self.set(&c, |n| {
            n.hard_requirement = true;
            let extra = ctx.t(format!(" (entry requires {}, comfortable at {})", f.required_catacombs, cata_target), format!("（入場条件{}、推奨{}）", f.required_catacombs, cata_target));
            n.detail = Some(n.detail.clone().unwrap_or_default() + &extra);
        });
        self.link(&id, &c);
        if !f.requires_floor.is_empty() {
            self.depth += 1;
            let p = self.floor(&f.requires_floor.clone(), true);
            self.depth -= 1;
            self.link(&id, &p);
        }
        if let Some(r) = readiness {
            if r.magical_power > 0 {
                let m = self.mp(r.magical_power, false);
                self.link(&id, &m);
            }
            if r.class_average > 0 {
                let ca = ctx.analysis.dungeons.as_ref().map(|d| d.class_average);
                let n = self.upsert(node("class-avg", NodeKind::Dungeon, format!("Class average {}", r.class_average), ca, Some(r.class_average as f64), Some("level"), false, DataSource::SkyPilotHeuristic));
                self.link(&id, &n);
            }
        }
        id
    }

    fn kuudra(&mut self, tier: &str) -> String {
        let ctx = self.ctx;
        let Some(t) = ctx.registry.curated.kuudra.tiers.iter().find(|x| x.id == tier).cloned() else {
            return self.upsert(node(&format!("kuudra:{tier}"), NodeKind::Requirement, format!("Kuudra {tier}"), None, Some(1.0), None, true, DataSource::SkyPilotRegistry));
        };
        let done = ctx.analysis.kuudra.as_ref().is_some_and(|k| k.completions.get(&t.id).copied().unwrap_or(0) > 0);
        let id = self.upsert(node(&format!("kuudra:{}", t.id), NodeKind::Requirement, ctx.t(format!("Complete {} Kuudra", t.name), format!("{} Kuudraをクリア", t.name)), Some(if done { 1.0 } else { 0.0 }), Some(1.0), None, true, DataSource::SkyPilotRegistry));
        if done || self.depth > 6 {
            return id;
        }
        if t.required_reputation > 0 {
            let rep = ctx.analysis.kuudra.as_ref().map(|k| k.mage_reputation.unwrap_or(0).max(k.barbarian_reputation.unwrap_or(0)) as f64);
            let r = self.upsert(node("rep", NodeKind::Requirement, ctx.t(format!("Faction reputation {}", t.required_reputation), format!("派閥の評判 {}", t.required_reputation)), rep.or(Some(0.0)), Some(t.required_reputation as f64), Some("rep"), true, DataSource::SkyPilotRegistry));
            self.link(&id, &r);
        }
        if !t.requires_tier.is_empty() {
            self.depth += 1;
            let p = self.kuudra(&t.requires_tier.clone());
            self.depth -= 1;
            self.link(&id, &p);
        }
        if let Some(mp) = ctx.registry.curated.kuudra.readiness.get(&t.id).copied().filter(|m| *m > 0) {
            let m = self.mp(mp, false);
            self.link(&id, &m);
        }
        id
    }

    fn garden(&mut self, level: u32) -> String {
        let cur = self.ctx.analysis.garden.as_ref().and_then(|g| g.garden_level.as_ref()).map(|l| l.level as f64);
        self.upsert(node("garden", NodeKind::Skill, format!("Garden {level}"), cur.or(Some(0.0)), Some(level as f64), Some("level"), true, DataSource::HypixelProfile))
    }

    fn collection(&mut self, collection: &str, tier: u32) -> String {
        let ctx = self.ctx;
        let c = ctx.analysis.collections.as_ref().and_then(|c| c.iter().find(|x| x.id.eq_ignore_ascii_case(collection)));
        let name = c.map(|c| c.name.clone()).unwrap_or_else(|| skypilot_items::fingerprint::title_case(collection));
        let mut n = node(&format!("collection:{collection}"), NodeKind::Requirement, format!("{name} collection {tier}"), c.map(|c| c.tier as f64), Some(tier as f64), Some("tier"), true, DataSource::HypixelResources);
        if c.is_none() {
            n.status = NodeStatus::Unknown;
            n.detail = Some(ctx.t("Collections API disabled", "コレクションAPIが無効"));
        }
        self.upsert(n)
    }
}

/// Remove satisfied nodes (except the root) for display; keeps their ids in `satisfied_pruned`.
pub fn prune(mut g: GoalGraph) -> GoalGraph {
    let pruned: Vec<String> = g.nodes.iter().filter(|n| n.status == NodeStatus::Satisfied && n.id != g.root).map(|n| n.id.clone()).collect();
    g.nodes.retain(|n| !pruned.contains(&n.id));
    for n in g.nodes.iter_mut() {
        n.children.retain(|c| !pruned.contains(c));
    }
    g.satisfied_pruned = pruned;
    g
}
