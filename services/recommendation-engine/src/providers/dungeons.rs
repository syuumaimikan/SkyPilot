//! Dungeons Advisor: floor readiness, recommended content, runs to the next Catacombs level,
//! and upgrade ROI (Magical Power vs. other upgrades).

use skypilot_data::curated::FloorDef;
use skypilot_protocol::advisor::{AdvisorReport, FloorReadiness, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_coins, fmt_hours};
use crate::optimizer::CandidateAction;

pub fn floor<'a>(ctx: &'a Ctx, id: &str) -> Option<&'a FloorDef> {
    ctx.registry.curated.dungeons.floors.iter().find(|f| f.id.eq_ignore_ascii_case(id))
}

pub fn completions(ctx: &Ctx, id: &str) -> u64 {
    ctx.analysis.dungeons.as_ref().and_then(|d| d.floors.iter().find(|f| f.floor == id)).map(|f| f.completions).unwrap_or(0)
}

pub fn unlocked(ctx: &Ctx, f: &FloorDef) -> bool {
    let cata = ctx.catacombs_level().unwrap_or(0);
    let combat = ctx.skill_level("COMBAT").unwrap_or(0);
    let prev_ok = f.requires_floor.is_empty() || completions(ctx, &f.requires_floor) > 0;
    cata >= f.required_catacombs && combat >= f.required_combat && prev_ok
}

/// Readiness (0..1) and the missing soft/hard requirements as before→after metrics.
pub fn readiness(ctx: &Ctx, f: &FloorDef) -> (f64, Vec<Metric>) {
    let mut missing = Vec::new();
    let cata = ctx.catacombs_level().unwrap_or(0) as f64;
    if cata < f.required_catacombs as f64 {
        missing.push(Metric::change("Catacombs (required)", cata, f.required_catacombs as f64, None));
    }
    let combat = ctx.skill_level("COMBAT");
    if combat.is_none() && f.required_combat > 0 {
        missing.push(Metric::text(ctx.t("Combat", "Combat"), ctx.t("Skill API unavailable", "スキル API が取得できません")));
    } else if combat.unwrap_or(0) < f.required_combat {
        missing.push(Metric::change("Combat (required)", combat.unwrap_or(0) as f64, f.required_combat as f64, None));
    }
    if !f.requires_floor.is_empty() && completions(ctx, &f.requires_floor) == 0 {
        missing.push(Metric::text(ctx.t("Complete", "クリア"), f.requires_floor.clone()));
    }
    let hard: f64 = if unlocked(ctx, f) { 1.0 } else { 0.4 };
    let Some(r) = ctx.registry.curated.dungeons.readiness.get(&f.id) else {
        missing.push(Metric::text(ctx.t("Suitability", "適性"), ctx.t("No readiness guidance is configured for this floor", "このフロアの準備度基準が未設定です")));
        return (hard.min(0.49), missing);
    };
    let mp = ctx.magical_power().unwrap_or(0) as f64;
    let ca = ctx.analysis.dungeons.as_ref().map(|d| d.class_average).unwrap_or(0.0);
    let parts = [(cata, r.catacombs as f64, "Catacombs"), (mp, r.magical_power as f64, "Magical Power"), (ca, r.class_average as f64, "Class average")];
    let mut soft = 0.0;
    for (cur, want, label) in parts {
        if want > 0.0 {
            soft += (cur / want).min(1.0) / parts.len() as f64;
            if cur < want {
                missing.push(Metric::change(label, cur.floor(), want, None));
            }
        } else {
            soft += 1.0 / parts.len() as f64;
        }
    }
    let Some(gear) = ctx.analysis.gear.as_ref() else {
        missing.push(Metric::text(ctx.t("Gear assessment", "装備判定"), ctx.t("Inventory API unavailable; suitability unknown", "インベントリ API 不足：適性を判定できません")));
        return ((hard * (0.4 + 0.6 * soft)).min(0.49), missing);
    };
    // Explicit SkyPilot heuristics. These are not entry rules or a promise of completion.
    let usable = |item: &skypilot_protocol::profile::ItemSummary| ctx.registry.item(&item.skyblock_id).is_some() && crate::requirements::item_requirements_met(ctx, &item.skyblock_id).0 == Some(true);
    let armor: Vec<_> = gear.armor.iter().filter(|item| usable(item)).collect();
    let armor_fraction = armor.len().min(4) as f64 / 4.0;
    if armor.len() < 4 { missing.push(Metric::change(ctx.t("Usable equipped armor pieces", "使用可能な装備中防具"), armor.len() as f64, 4.0, None)); }
    let weapons: Vec<_> = gear.weapons.iter().filter(|item| usable(item)).collect();
    let weapon_fraction = if weapons.is_empty() { 0.0 } else { 1.0 };
    if weapons.is_empty() { missing.push(Metric::text(ctx.t("Weapon", "武器"), ctx.t("No owned weapon with verified use requirements", "使用条件を確認できる所持武器がありません"))); }
    let wanted_stars = if f.master || f.tier >= 6 { 5.0 } else if f.tier >= 4 { 3.0 } else { 0.0 };
    let average_stars = armor.iter().map(|item| item.stars as f64).sum::<f64>() / 4.0;
    let stars_fraction = if wanted_stars > 0.0 { (average_stars / wanted_stars).min(1.0) } else { 1.0 };
    if average_stars < wanted_stars { missing.push(Metric::change(ctx.t("Armor stars (guidance)", "防具の平均スター（目安）"), average_stars, wanted_stars, None)); }
    let equipment_score = armor_fraction * 0.4 + weapon_fraction * 0.4 + stars_fraction * 0.2;
    let mut value = (hard * (0.4 + 0.6 * soft) * (0.65 + 0.35 * equipment_score)).min(1.0);
    if armor.len() < 4 || weapons.is_empty() { value = value.min(0.69); }
    (value, missing)
}

/// Best floor for Catacombs XP that the player is ready for and whose base XP is known.
pub fn best_xp_floor<'a>(ctx: &'a Ctx) -> Option<(&'a FloorDef, f64, f64)> {
    let cfg = &ctx.registry.curated.dungeons;
    cfg.floors
        .iter()
        .filter(|f| unlocked(ctx, f) && readiness(ctx, f).0 >= 0.7)
        .filter_map(|f| {
            let xp = cfg.base_catacombs_xp.get(&f.id)?;
            let mins = cfg.run_minutes.get(&f.id).copied().unwrap_or(10.0);
            Some((f, *xp, xp / mins))
        })
        .max_by(|a, b| a.2.total_cmp(&b.2))
        .map(|(f, xp, _)| (f, xp, cfg.run_minutes.get(&f.id).copied().unwrap_or(10.0)))
}

/// Runs needed (on the best floor) to gain `xp`.
pub fn runs_for_xp(ctx: &Ctx, xp: f64) -> Option<(String, f64, f64)> {
    let (f, base, mins) = best_xp_floor(ctx)?;
    let runs = (xp / base).ceil().max(1.0);
    Some((f.id.clone(), runs, runs * mins / 60.0))
}

pub fn all_readiness(ctx: &Ctx) -> Vec<FloorReadiness> {
    ctx.registry
        .curated
        .dungeons
        .floors
        .iter()
        .map(|f| {
            let (r, missing) = readiness(ctx, f);
            FloorReadiness { floor: f.id.clone(), readiness: r, unlocked: unlocked(ctx, f), completions: completions(ctx, &f.id), missing, recommended: false }
        })
        .collect()
}

pub struct DungeonsProvider;

impl Provider for DungeonsProvider {
    fn id(&self) -> &'static str {
        "dungeons"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        let Some(d) = ctx.analysis.dungeons.as_ref() else { return out };
        // Catacombs leveling
        let targets: Vec<(&GoalContext, f64)> = goals.iter().filter_map(|g| g.open("cata").and_then(|n| n.target).map(|t| (g, t))).collect();
        if let Some(target) = targets.iter().map(|(_, t)| *t).reduce(f64::max) {
            let next = (d.catacombs.level + 1).min(target as u32);
            let table = ctx.registry.neu.leveling.catacombs_table();
            let need = table.xp_remaining(d.catacombs.xp, next).unwrap_or(0.0);
            let total_need = table.xp_remaining(d.catacombs.xp, target as u32).unwrap_or(need).max(1.0);
            match runs_for_xp(ctx, need) {
                Some((floor_id, runs, hours)) => {
                    let lo = (runs * 0.7).floor().max(1.0);
                    let mut a = CandidateAction::new("dungeon-cata-next", "dungeons", "Dungeons", ctx.t(
                        format!("Run {floor_id} {}–{} times (Catacombs {} → {next})", lo as u64, runs as u64, d.catacombs.level),
                        format!("{floor_id}を{}～{}周（Catacombs {} → {next}）", lo as u64, runs as u64, d.catacombs.level),
                    ));
                    a.metrics.push(Metric::change("Catacombs", d.catacombs.level as f64, next as f64, None));
                    a.metrics.push(Metric::value(ctx.t("Estimated time", "推定時間"), hours, Some("h")));
                    a.hours = hours;
                    a.difficulty = 2.5;
                    a.risk = 1.5;
                    a.xp_return = need;
                    a.long_term_value = 0.7;
                    a.confidence = 0.6;
                    a.confidence_reasons = vec![ctx.t("Base XP per run (wiki) without daily bonuses; run time is an estimate", "1周あたりの基礎XP（Wiki）でデイリーボーナス未考慮・周回時間は推定")];
                    a.why = ctx.t(
                        format!("{floor_id} gives the most Catacombs XP per minute among floors you are ready for. The range covers score/bonus variance."),
                        format!("準備ができているフロアの中で{floor_id}が1分あたりのCatacombs XPが最も多いためです。周回数の幅はスコアやボーナスのぶれを考慮しています。"),
                    );
                    for (g, _) in &targets {
                        if let Some(i) = g.impact("cata", need / total_need) {
                            a.goal_impacts.insert(g.id.clone(), i);
                        }
                    }
                    a.link = Some("/combat/dungeons".into());
                    a.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo, DataSource::SkyPilotRegistry, DataSource::SkyPilotHeuristic];
                    out.push(a);
                }
                None => {
                    let mut a = CandidateAction::new("dungeon-cata-unknown", "dungeons", "Dungeons", ctx.t(format!("Keep running your highest comfortable floor (Catacombs {} → {next})", d.catacombs.level), format!("快適に回せる最上位フロアを周回（Catacombs {} → {next}）", d.catacombs.level)));
                    a.hours = 2.0;
                    a.confidence = 0.3;
                    a.why = ctx.t("Run counts are Unknown: base XP for your best floor is not verified.", "最適フロアの基礎XPが未確認のため、周回数は不明です。");
                    for (g, _) in &targets {
                        if let Some(i) = g.impact("cata", 0.1) {
                            a.goal_impacts.insert(g.id.clone(), i);
                        }
                    }
                    out.push(a);
                }
            }
        }
        // Floor completion goals
        for g in goals {
            for n in g.open_nodes().filter(|n| n.id.starts_with("floor:")) {
                let fid = &n.id[6..];
                let Some(f) = floor(ctx, fid) else { continue };
                let (r, missing) = readiness(ctx, f);
                if unlocked(ctx, f) && r >= 0.6 {
                    let mins = ctx.registry.curated.dungeons.run_minutes.get(fid).copied().unwrap_or(12.0);
                    let mut a = CandidateAction::new(format!("dungeon-complete-{fid}"), "dungeons", "Dungeons", ctx.t(format!("Complete {fid} (readiness {:.0}%)", r * 100.0), format!("{fid}をクリア（準備度{:.0}%）", r * 100.0)));
                    a.hours = mins / 60.0 * 2.0;
                    a.difficulty = 3.0;
                    a.risk = if r < 0.8 { 2.5 } else { 1.5 };
                    a.unlock_value = 0.9;
                    a.confidence = r;
                    a.steps = missing.iter().map(|m| format!("{}: {:?} → {:?}", m.label, m.before, m.after)).collect();
                    a.why = ctx.t("You meet the entry requirements; completing it unlocks the next step of your goal.", "入場条件を満たしています。クリアすると目標の次のステップが解放されます。");
                    if let Some(i) = g.impact(&n.id, 1.0) {
                        a.goal_impacts.insert(g.id.clone(), i);
                    }
                    a.link = Some("/combat/dungeons".into());
                    a.sources = vec![DataSource::SkyPilotRegistry];
                    out.push(a);
                }
            }
        }
        out
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("dungeons", ctx.t("Dungeons", "ダンジョン"));
        let Some(d) = ctx.analysis.dungeons.as_ref() else {
            r.unknowns.push(ctx.t("No dungeon data on this profile", "このプロフィールにダンジョンのデータがありません"));
            return r;
        };
        r.summary = vec![
            Metric::value("Catacombs", d.catacombs.level_with_progress, None),
            Metric::value(ctx.t("Class average", "クラス平均"), d.class_average, None),
            Metric::value(ctx.t("Secrets", "シークレット"), d.secrets.unwrap_or(0) as f64, None),
        ];
        let mut rd = all_readiness(ctx);
        // recommend: highest floor with readiness >= .85 (farm) and next one (push)
        let farm = rd.iter().filter(|f| f.unlocked && f.readiness >= 0.85).map(|f| f.floor.clone()).last();
        for f in rd.iter_mut() {
            f.recommended = Some(&f.floor) == farm.as_ref();
        }
        let mut s = section("floors", ctx.t("Floor readiness", "フロア準備度"));
        s.notes.push(ctx.t("Gear component checks equipped armor, usable owned weapons and stars. Health, damage, set bonuses, pet abilities, player skill and party composition are not simulated. Readiness is guidance, not a clear guarantee.", "装備要素は装備中防具・使用可能な所持武器・スターを確認します。体力・火力・セット効果・ペット能力・操作技量・パーティー構成は未計算です。準備度は目安で、クリアを保証しません。"));
        s.table = Some(table(
            &["Floor", "Unlocked", "Completions", "Readiness", "Missing"],
            rd.iter()
                .map(|f| {
                    vec![
                        if f.recommended { badge(format!("{} ★", f.floor), Tone::Good) } else { text(&f.floor) },
                        yes_no(ctx, f.unlocked),
                        num(f.completions as f64, None),
                        progress(f.readiness, format!("{:.0}%", f.readiness * 100.0)),
                        text(f.missing.iter().map(|m| match (m.before, m.after, &m.text) {
                            (Some(b), Some(a), _) => format!("{} {} → {}", m.label, b, a),
                            (_, _, Some(t)) => format!("{} {}", m.label, t),
                            _ => m.label.clone(),
                        }).collect::<Vec<_>>().join(", ")),
                    ]
                })
                .collect(),
        ));
        s.notes.push(ctx.t("Entry requirements are game rules (wiki). Readiness targets (MP, class average) are SkyPilot guidance.", "入場条件はゲームのルール（Wiki）。準備度の目安（MP・クラス平均）はSkyPilotの推奨値です。"));
        s.sources = vec![DataSource::SkyPilotRegistry, DataSource::SkyPilotHeuristic];
        r.sections.push(s);
        let mut c = section("classes", ctx.t("Classes", "クラス"));
        c.table = Some(table(&["Class", "Level", "Progress"], d.classes.iter().map(|x| vec![text(&x.name), num(x.level as f64, None), progress(x.progress, format!("{:.0}%", x.progress * 100.0))]).collect()));
        r.sections.push(c);
        let table_x = ctx.registry.neu.leveling.catacombs_table();
        if let Some(need) = table_x.progress(d.catacombs.xp).xp_to_next {
            let mut s = section("next", ctx.t("Next Catacombs level", "次のCatacombsレベル"));
            match runs_for_xp(ctx, need) {
                Some((f, runs, h)) => s.bullets.push(ctx.t(format!("{} XP needed ≈ {} runs of {} ({})", fmt_coins(need), runs, f, fmt_hours(h)), format!("必要XP {} ≈ {} {}周（{}）", fmt_coins(need), f, runs, fmt_hours(h)))),
                None => s.bullets.push(ctx.t(format!("{} XP needed. Run count Unknown (base XP for your floors not verified).", fmt_coins(need)), format!("必要XP {}。周回数は不明（フロアの基礎XP未確認）。", fmt_coins(need)))),
            }
            r.sections.push(s);
        }
        // Upgrade ROI: MP is the only readiness upgrade with a computable price.
        if let Some(target) = rd.iter().find(|f| !f.recommended && f.readiness < 0.85 && f.unlocked).and_then(|f| ctx.registry.curated.dungeons.readiness.get(&f.floor).map(|x| (f.floor.clone(), x.magical_power))) {
            let mp = ctx.magical_power().unwrap_or(0);
            if mp < target.1 {
                let (plan, cost, reached) = super::accessories::plan_to(ctx, Some(target.1), f64::MAX);
                let mut s = section("roi", ctx.t("Upgrade ROI", "強化の費用対効果"));
                s.bullets.push(ctx.t(
                    format!("Raising MP {} → {} for {} costs {} ({} accessories) and improves readiness on every floor and slayer at once.", mp, reached, target.0, fmt_coins(cost), plan.len()),
                    format!("{}向けにMPを{}→{}へ上げる費用は{}（アクセサリー{}個）で、全フロア・全スレイヤーの準備度が同時に上がります。", target.0, mp, reached, fmt_coins(cost), plan.len()),
                ));
                s.notes.push(ctx.t("Armor/weapon upgrades are compared in the Gear Optimizer with your budget.", "防具・武器の強化はGear Optimizerで予算と比較できます。"));
                r.sections.push(s);
            }
        }
        r.freshness = market_freshness(ctx);
        r
    }
}
