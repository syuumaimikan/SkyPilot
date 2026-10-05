//! Liquidation Advisor: what can be sold to fund a goal - never the gear the player uses.

use std::collections::HashSet;

use skypilot_protocol::advisor::{AdvisorReport, LiquidationEntry, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_coins};
use crate::optimizer::CandidateAction;

pub fn classify(ctx: &Ctx, goals: &[GoalContext]) -> Vec<LiquidationEntry> {
    let Some(nw) = ctx.analysis.networth.as_ref() else { return Vec::new() };
    let mut protected: HashSet<String> = HashSet::new();
    if let Some(g) = ctx.analysis.gear.as_ref() {
        for i in g.armor.iter().chain(&g.equipment) {
            protected.insert(i.skyblock_id.clone());
        }
    }
    for src in [ctx.analysis.mining.as_ref().and_then(|m| m.best_tool.as_ref()), ctx.analysis.fishing.as_ref().and_then(|f| f.best_rod.as_ref()), ctx.analysis.foraging.as_ref().and_then(|f| f.best_axe.as_ref())].into_iter().flatten() {
        protected.insert(src.skyblock_id.clone());
    }
    for t in ctx.analysis.garden.as_ref().map(|g| g.best_tools.as_slice()).unwrap_or(&[]) {
        protected.insert(t.skyblock_id.clone());
    }
    // gear recommended for goal slayers
    for g in goals {
        for id in g.graph.nodes.iter().filter_map(|n| n.id.strip_prefix("slayer:")) {
            if let Some(def) = ctx.registry.curated.slayer(id) {
                for s in &def.gear {
                    protected.extend(s.required.iter().chain(&s.recommended).chain(&s.luxury).cloned());
                }
            }
        }
        for n in &g.graph.nodes {
            if let Some(i) = n.id.strip_prefix("item:") {
                protected.insert(i.to_string());
            }
        }
    }
    let inactive: HashSet<String> = ctx.analysis.accessories.as_ref().map(|a| a.inactive.iter().cloned().collect()).unwrap_or_default();
    let mut out = Vec::new();
    for i in &nw.top_items {
        let (verdict, reason) = if protected.contains(&i.skyblock_id) || i.location == "Armor" || i.location == "Equipment" {
            ("DO_NOT_SELL", ctx.t("Equipped or needed for your goals", "装備中、または目標に必要"))
        } else if i.soulbound {
            ("DO_NOT_SELL", ctx.t("Soulbound - cannot be sold", "ソウルバウンドのため売却不可"))
        } else if inactive.contains(&i.skyblock_id) {
            ("SAFE_TO_SELL", ctx.t("Inactive accessory (duplicate or superseded)", "無効なアクセサリー（重複・上位版所持）"))
        } else if i.location.starts_with("Backpack") || i.location == "Ender Chest" || i.location == "Personal Vault" {
            ("CONSIDER_SELLING", ctx.t("Stored, not part of an equipped or goal setup", "保管中で、装備・目標構成に含まれない"))
        } else if i.location == "Wardrobe" {
            ("CONSIDER_SELLING", ctx.t("Alternate wardrobe set - keep if you switch activities", "ワードローブの別セット（用途を切り替えるなら保持）"))
        } else {
            ("CONSIDER_SELLING", ctx.t("Check whether you still use it", "まだ使うか確認してください"))
        };
        let reason = if i.value_confidence.is_some_and(|c| c < skypilot_profile::networth::LOW_CONFIDENCE) {
            format!("{reason}{}", ctx.t(" · price unconfirmed (few or no recorded sales)", "・価格は取引実績が少なく未確認"))
        } else {
            reason
        };
        out.push(LiquidationEntry { item: i.name.clone(), item_id: i.skyblock_id.clone(), value: i.estimated_value, verdict: verdict.into(), reason });
    }
    out
}

pub struct LiquidationProvider;

impl Provider for LiquidationProvider {
    fn id(&self) -> &'static str {
        "liquidation"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let gap: f64 = goals.iter().filter_map(|g| g.open("money").and_then(|n| Some(n.target? - n.current.unwrap_or(0.0)))).fold(0.0, f64::max);
        if gap <= 0.0 {
            return Vec::new();
        }
        let safe: Vec<LiquidationEntry> = classify(ctx, goals).into_iter().filter(|e| e.verdict == "SAFE_TO_SELL").collect();
        let value: f64 = safe.iter().filter_map(|e| e.value).sum();
        if value <= 0.0 {
            return Vec::new();
        }
        let mut a = CandidateAction::new("liquidate-safe", "liquidation", "Money", ctx.t(format!("Sell {} unused items (~{})", safe.len(), fmt_coins(value)), format!("不要アイテム{}個を売却（約{}）", safe.len(), fmt_coins(value))));
        a.steps = safe.iter().take(10).map(|e| format!("{} — {}", e.item, e.value.map(fmt_coins).unwrap_or_default())).collect();
        a.hours = 0.3;
        a.confidence = 0.6;
        a.risk = 1.5;
        a.why = ctx.t("These items do not contribute to your Magical Power, equipped setups or goal gear.", "これらはMP・装備構成・目標装備のいずれにも寄与していません。");
        for g in goals {
            if let Some(n) = g.open("money") {
                let need = (n.target.unwrap_or(0.0) - n.current.unwrap_or(0.0)).max(1.0);
                if let Some(i) = g.impact("money", value / need) {
                    a.goal_impacts.insert(g.id.clone(), i);
                }
            }
        }
        a.link = Some("/profile?tab=liquidation".into());
        a.sources = vec![DataSource::HypixelProfile, DataSource::Market];
        vec![a]
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("liquidation", ctx.t("Liquidation Advisor", "売却アドバイザー"));
        let entries = classify(ctx, goals);
        for v in ["SAFE_TO_SELL", "CONSIDER_SELLING", "DO_NOT_SELL"] {
            let total: f64 = entries.iter().filter(|e| e.verdict == v).filter_map(|e| e.value).sum();
            r.summary.push(Metric::value(v.replace('_', " "), total, Some("coins")));
        }
        let mut s = section("items", ctx.t("Valuable items", "高額アイテム"));
        s.table = Some(table(
            &["Item", "Value", "Verdict", "Reason"],
            entries
                .iter()
                .map(|e| {
                    vec![text(&e.item), coins_opt(e.value), badge(e.verdict.replace('_', " "), match e.verdict.as_str() { "SAFE_TO_SELL" => Tone::Good, "DO_NOT_SELL" => Tone::Bad, _ => Tone::Warn }), text(&e.reason)]
                })
                .collect(),
        ));
        s.notes.push(ctx.t("SkyPilot never sells for you. Equipped gear, goal gear and soulbound items are always DO NOT SELL.", "SkyPilotは自動売却を行いません。装備中・目標用・ソウルバウンドの品は常に「売却しない」です。"));
        s.sources = vec![DataSource::HypixelProfile, DataSource::Market];
        r.sections.push(s);
        r
    }
}
