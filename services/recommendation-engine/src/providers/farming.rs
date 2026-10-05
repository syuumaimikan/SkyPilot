//! Farming Advisor: crop rates from the player's farming fortune, best sell form,
//! farm designs (Farm Builder data) and step-by-step build guides.

use std::collections::BTreeMap;

use skypilot_calc::farming::{coins_per_hour, crop_yield};
use skypilot_data::farms::FarmDesignDef;
use skypilot_protocol::advisor::{AdvisorReport, CropRate, FarmBlock, FarmBuildStep, FarmDesign, FarmDesignSummary, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_coins};
use crate::market_forms;
use crate::optimizer::CandidateAction;

fn crop_fortune_stat_name(stat: &str) -> String {
    // "MELON_FORTUNE" -> "Melon Fortune" (lore label)
    skypilot_items::fingerprint::title_case(stat)
}

pub fn crop_rates(ctx: &Ctx, bps_override: Option<f64>) -> Vec<CropRate> {
    let cfg = &ctx.registry.curated.farming;
    let stats: BTreeMap<String, f64> = ctx.analysis.garden.as_ref().map(|g| g.farming_gear_stats.stats.clone()).unwrap_or_default();
    let general = stats.get("Farming Fortune").copied().unwrap_or(0.0);
    let bps = bps_override.unwrap_or(cfg.max_blocks_per_second);
    let mut out = Vec::new();
    for c in &cfg.crops {
        let specific = stats.get(&crop_fortune_stat_name(&c.fortune_stat)).copied().unwrap_or(0.0);
        let fortune = general + specific;
        let y = crop_yield(c.base_drops, fortune, bps, cfg.default_uptime);
        let best = market_forms::best_value(ctx, &c.item, Some(y.crops_per_hour));
        let mut assumptions = vec![format!("{} blocks/s, uptime {:.0}%", bps, cfg.default_uptime * 100.0), format!("fortune {:.0} (gear lore)", fortune)];
        if let Some((_, form)) = &best {
            if form != &c.item {
                assumptions.push(format!("sold as {}", ctx.display_name(form)));
            }
        }
        out.push(CropRate {
            crop: c.id.clone(),
            name: c.name.clone(),
            fortune,
            blocks_per_second: bps,
            crops_per_hour: y.crops_per_hour,
            npc_coins_per_hour: coins_per_hour(y.crops_per_hour, c.npc_price),
            bazaar_coins_per_hour: best.as_ref().map(|(v, _)| coins_per_hour(y.crops_per_hour, *v)),
            best_bazaar_form: best.map(|(_, f)| f),
            xp_per_hour: None,
            assumptions,
        });
    }
    out.sort_by(|a, b| b.bazaar_coins_per_hour.unwrap_or(b.npc_coins_per_hour).max(b.npc_coins_per_hour).total_cmp(&a.bazaar_coins_per_hour.unwrap_or(a.npc_coins_per_hour).max(a.npc_coins_per_hour)));
    out
}

pub fn best_crop(ctx: &Ctx) -> Option<CropRate> {
    if !ctx.analysis.garden.is_known() {
        return None;
    }
    crop_rates(ctx, None).into_iter().next()
}

pub fn design_summary(d: &FarmDesignDef) -> FarmDesignSummary {
    FarmDesignSummary { id: d.id.clone(), name: d.name.clone(), crops: d.crops.clone(), description: d.description.clone(), size: d.size() }
}

pub fn design(ctx: &Ctx, id: &str) -> Option<FarmDesign> {
    let d = ctx.registry.farms.iter().find(|f| f.id == id)?;
    let blocks = d.expand(400_000);
    let counts = d.block_counts();
    let mut layers: Vec<i32> = blocks.iter().map(|b| b.y).collect();
    layers.sort();
    layers.dedup();
    let mut steps = Vec::new();
    let mut idx = 1;
    steps.push(FarmBuildStep { index: idx, title: ctx.t("Prepare the plot", "プロットを準備"), detail: ctx.t(format!("Clear a {}×{} area of your Garden plot and pick a corner as origin (x=0, z=0).", d.plot[0], d.plot[1]), format!("ガーデンのプロットで{}×{}の範囲を整地し、角を原点(x=0, z=0)にします。", d.plot[0], d.plot[1])), layer_y: None, blocks: BTreeMap::new() });
    for y in &layers {
        idx += 1;
        let mut layer_counts: BTreeMap<String, u32> = BTreeMap::new();
        for b in blocks.iter().filter(|b| b.y == *y) {
            *layer_counts.entry(b.block.clone()).or_insert(0) += 1;
        }
        let summary = layer_counts.iter().map(|(k, v)| format!("{v}× {k}")).collect::<Vec<_>>().join(", ");
        steps.push(FarmBuildStep {
            index: idx,
            title: ctx.t(format!("Build layer Y+{y}"), format!("Y+{y}の層を設置")),
            detail: ctx.t(format!("Place: {summary}. Follow the top view for this layer."), format!("設置: {summary}。この層のトップビューに従ってください。")),
            layer_y: Some(*y),
            blocks: layer_counts,
        });
    }
    for extra in &d.build_order {
        idx += 1;
        steps.push(FarmBuildStep { index: idx, title: extra.clone(), detail: extra.clone(), layer_y: None, blocks: BTreeMap::new() });
    }
    idx += 1;
    steps.push(FarmBuildStep { index: idx, title: ctx.t("Farm complete", "ファーム完成"), detail: ctx.t(format!("Harvest direction: {}. Movement: {}", d.harvest_direction, d.movement.join(" → ")), format!("収穫方向: {}。移動: {}", d.harvest_direction, d.movement.join(" → "))), layer_y: None, blocks: BTreeMap::new() });
    let total_blocks: u32 = counts.values().sum();
    let material_cost = counts.iter().map(|(b, n)| ctx.buy(&b.to_ascii_uppercase()).map(|q| q.price * *n as f64)).collect::<Option<Vec<f64>>>().map(|v| v.iter().sum());
    Some(FarmDesign {
        summary: design_summary(d),
        blocks: blocks.iter().map(|b| FarmBlock { x: b.x, y: b.y, z: b.z, block: b.block.clone() }).collect(),
        block_counts: counts,
        layers,
        movement: d.movement.clone(),
        harvest_direction: d.harvest_direction.clone(),
        water_notes: d.water_notes.clone(),
        lighting_notes: d.lighting_notes.clone(),
        plot_boundary: d.plot,
        build_steps: steps,
        estimated_build_minutes: total_blocks as f64 * d.seconds_per_block / 60.0,
        estimated_material_cost: material_cost,
    })
}

pub struct FarmingProvider;

impl Provider for FarmingProvider {
    fn id(&self) -> &'static str {
        "farming"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let mut out = Vec::new();
        let Some(best) = best_crop(ctx) else { return out };
        let rate = best.bazaar_coins_per_hour.unwrap_or(0.0).max(best.npc_coins_per_hour);
        let mut a = CandidateAction::new("farming-best-crop", "farming", "Farming", ctx.t(format!("Farm {} (~{}/h)", best.name, fmt_coins(rate)), format!("{}を収穫（約{}/時）", best.name, fmt_coins(rate))));
        a.metrics.push(Metric::value(ctx.t("Coins/hour", "コイン/時"), rate, Some("coins")));
        a.metrics.push(Metric::value(ctx.t("Fortune", "幸運"), best.fortune, None));
        a.coin_return_per_hour = rate;
        a.hours = 1.0;
        a.risk = 1.0;
        a.difficulty = 1.0;
        a.long_term_value = 0.5;
        a.confidence = 0.65;
        a.confidence_reasons = vec![ctx.t("Assumes the 20 BPS cap and steady uptime", "20BPS上限と安定稼働を仮定")];
        a.why = ctx.t(format!("Highest coins/hour among crops for your current farming fortune ({:.0}).", best.fortune), format!("現在のファーミング幸運（{:.0}）で最もコイン/時が高い作物です。", best.fortune));
        a.steps = best.assumptions.clone();
        for g in goals {
            if let Some(i) = g.impact("skill:FARMING", 0.1) {
                a.goal_impacts.insert(g.id.clone(), i);
            }
        }
        a.link = Some("/farming".into());
        a.sources = vec![DataSource::HypixelProfile, DataSource::SkyPilotRegistry, DataSource::Market];
        out.push(a);
        out
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("farming", ctx.t("Farming & Garden", "ファーミング・ガーデン"));
        if let Some(l) = ctx.skill("FARMING") {
            r.summary.push(Metric::value("Farming", l.level_with_progress, None));
        }
        match ctx.analysis.garden.as_ref() {
            Some(g) => {
                if let Some(l) = g.garden_level.as_ref() {
                    r.summary.push(Metric::value("Garden", l.level_with_progress, None));
                }
                r.summary.push(Metric::value(ctx.t("Unlocked plots", "解放済みプロット"), g.unlocked_plots as f64, None));
                r.summary.push(Metric::value("Copper", g.copper.unwrap_or(0) as f64, None));
                let mut st = section("gear", ctx.t("Farming fortune (best owned setup)", "ファーミング幸運（所持品の最良構成）"));
                st.metrics = g.farming_gear_stats.stats.iter().filter(|(k, _)| k.contains("Fortune")).map(|(k, v)| Metric::value(k.clone(), *v, None)).collect();
                st.bullets = g.farming_gear_stats.sources.clone();
                st.notes.push(ctx.t("Gear lore only: crop upgrades, pets, Anita bonus and milestones are not added.", "装備の説明文のみ。作物アップグレード・ペット・Anitaボーナス・マイルストーンは未加算です。"));
                r.sections.push(st);
                let mut ms = section("milestones", ctx.t("Crop milestones & upgrades", "作物マイルストーン・アップグレード"));
                ms.table = Some(table(&["Crop", "Milestone", "Upgrade"], ctx.registry.curated.farming.crops.iter().map(|c| vec![text(&c.name), num(g.crop_milestones.get(&c.id).copied().unwrap_or(0) as f64, None), num(g.crop_upgrades.get(&c.id).copied().unwrap_or(0) as f64, None)]).collect()));
                r.sections.push(ms);
                let mut v = section("visitors", ctx.t("Visitors & composter", "訪問者・コンポスター"));
                v.bullets.push(format!("{}: {}", ctx.t("Visitors served", "対応した訪問者"), g.visitors_served.map(|x| x.to_string()).unwrap_or_else(|| "?".into())));
                v.bullets.push(format!("{}: {}", ctx.t("Unique visitors", "ユニーク訪問者"), g.unique_visitors.map(|x| x.to_string()).unwrap_or_else(|| "?".into())));
                for (k, lv) in &g.composter_upgrades {
                    v.bullets.push(format!("Composter {}: {}", skypilot_items::fingerprint::title_case(k), lv));
                }
                r.sections.push(v);
            }
            None => r.unknowns.push(ctx.t("Garden data unavailable (Garden not unlocked or API unavailable)", "ガーデンのデータがありません（未解放またはAPI不可）")),
        }
        let rates = crop_rates(ctx, None);
        let mut cr = section("rates", ctx.t("Crop income", "作物ごとの収入"));
        cr.table = Some(table(
            &["Crop", "Fortune", "Crops/h", "NPC/h", "Bazaar/h", "Best form", "XP/h"],
            rates.iter().map(|c| vec![text(&c.name), num(c.fortune, None), num(c.crops_per_hour.round(), None), coins(c.npc_coins_per_hour), coins_opt(c.bazaar_coins_per_hour), text(c.best_bazaar_form.as_ref().map(|f| ctx.display_name(f)).unwrap_or_default()), unknown("Farming XP per crop not in data")]).collect(),
        ));
        cr.notes.push(ctx.t("Base drops per crop: EliteFarmers farming-weight (MIT). Prices: live Bazaar.", "作物の基礎ドロップ: EliteFarmers farming-weight（MIT）。価格: ライブBazaar。"));
        cr.sources = vec![DataSource::SkyPilotRegistry, DataSource::Market];
        r.sections.push(cr);
        let mut fd = section("designs", ctx.t("Farm designs", "ファーム設計"));
        fd.table = Some(table(&["Design", "Crops", "Size (x,y,z)", "Description"], ctx.registry.farms.iter().map(|d| {
            let s = design_summary(d);
            vec![badge(&s.name, Tone::Info), text(s.crops.join(", ")), text(format!("{}×{}×{}", s.size[0], s.size[1], s.size[2])), text(s.description)]
        }).collect()));
        fd.notes.push(ctx.t("Open the Farm Builder for top/side/3D views, layers, block counts and the build guide. SkyPilot never places blocks for you.", "Farm Builderでトップ/サイド/3D表示・層・ブロック数・建築ガイドを確認できます。ブロックの自動設置は行いません。"));
        r.sections.push(fd);
        r.freshness = market_freshness(ctx);
        r
    }
}
