//! HUD state for the Fabric mod (Roadmap HUD, Progress HUD, Context Advisor).

use skypilot_protocol::goals::{AnalyzeResponse, NodeStatus};
use skypilot_protocol::mod_api::{HudContextBlock, HudLine, HudState};

use crate::ctx::{Lang, fmt_coins, fmt_hours};

/// Map an in-game area/location string (scoreboard "⏣ Dwarven Mines", tab "Area: Garden")
/// to a context.
pub fn context_of(area: &str) -> &'static str {
    let a = area.to_lowercase();
    if ["dwarven", "crystal hollows", "glacite", "mineshaft", "gold mine", "deep caverns", "mines"].iter().any(|k| a.contains(k)) {
        "MINING"
    } else if a.contains("garden") || a.contains("plot") {
        "GARDEN"
    } else if a.contains("catacombs") || a.contains("dungeon") {
        "DUNGEON"
    } else if a.contains("kuudra") || a.contains("crimson") || a.contains("scarleton") || a.contains("dragontail") {
        "KUUDRA"
    } else if a.contains("rift") {
        "RIFT"
    } else if ["spider", "the end", "void sepulture", "graveyard", "howling", "smoldering", "zealot"].iter().any(|k| a.contains(k)) {
        "SLAYER"
    } else if a.contains("galatea") || a.contains("park") || a.contains("forest") {
        "FORAGING"
    } else if a.contains("backwater") || a.contains("bayou") || a.contains("fishing") {
        "FISHING"
    } else {
        "GENERAL"
    }
}

pub fn build(resp: Option<&AnalyzeResponse>, area: Option<&str>, lang: Lang, now_ms: i64, username: Option<String>) -> HudState {
    let mut s = HudState {
        linked: true,
        username,
        goal_title: None,
        step_index: None,
        step_count: None,
        step_title: None,
        step_progress: None,
        step_current: None,
        step_target: None,
        step_unit: None,
        next_title: None,
        estimated_cost: None,
        overall_progress: None,
        context: None,
        actions: Vec::new(),
        updated_at_ms: now_ms,
        stale: false,
        message: None,
    };
    let Some(r) = resp else {
        s.message = Some(lang.t("Open SkyPilot on the web and run an analysis to see your plan here.", "WebのSkyPilotで分析すると、ここに計画が表示されます。"));
        return s;
    };
    s.updated_at_ms = r.analysis.fetched.updated_at_ms;
    s.stale = now_ms - r.analysis.fetched.updated_at_ms > 6 * 3_600_000;
    if let Some(g) = r.goals.first() {
        s.goal_title = Some(g.spec.title.clone());
        s.overall_progress = Some(g.roadmap.overall_progress);
        s.step_count = Some(g.roadmap.steps.len() as u32);
        let open: Vec<_> = g.roadmap.steps.iter().filter(|x| x.status != NodeStatus::Satisfied).collect();
        if let Some(cur) = open.first() {
            s.step_index = Some(cur.index);
            s.step_title = Some(cur.title.clone());
            s.step_progress = Some(cur.progress);
            s.step_current = cur.current;
            s.step_target = cur.target;
            s.step_unit = cur.unit.clone();
            s.estimated_cost = cur.estimated_cost;
        }
        s.next_title = open.get(1).map(|x| x.title.clone());
    }
    s.actions = r.actions.iter().take(3).map(|a| a.title.clone()).collect();
    let ctx_id = area.map(context_of).unwrap_or("GENERAL");
    let a = &r.analysis;
    let block = match ctx_id {
        "MINING" => a.mining.as_ref().map(|m| {
            let mut lines = Vec::new();
            if let Some(h) = m.hotm.as_ref() {
                lines.push(HudLine { label: "HOTM".into(), value: format!("{} ({:.0}%)", h.level, h.progress * 100.0) });
            }
            for (label, p) in [("Mithril", &m.mithril_powder), ("Gemstone", &m.gemstone_powder), ("Glacite", &m.glacite_powder)] {
                if let Some(b) = p.as_ref() {
                    lines.push(HudLine { label: format!("{label} Powder"), value: fmt_coins(b.available) });
                }
            }
            if let Some(next) = r.actions.iter().find(|x| x.category == "Mining") {
                lines.push(HudLine { label: lang.t("Next", "次"), value: next.title.clone() });
            }
            HudContextBlock { context: "MINING".into(), title: lang.t("Mining Goal", "採掘の目標"), lines }
        }),
        "SLAYER" => {
            let slayer_action = r.actions.iter().find(|x| x.category == "Slayer");
            a.slayers.as_ref().map(|list| {
                let focus = slayer_action
                    .and_then(|act| list.iter().find(|s| act.id.contains(&s.id)))
                    .or_else(|| list.iter().max_by(|x, y| x.level.xp.total_cmp(&y.level.xp)));
                let mut lines = Vec::new();
                if let Some(s) = focus {
                    lines.push(HudLine { label: format!("{} XP", s.boss_name), value: fmt_coins(s.level.xp) });
                    if let Some(n) = s.level.xp_to_next {
                        lines.push(HudLine { label: lang.t("To next level", "次のレベルまで"), value: fmt_coins(n) });
                    }
                }
                if let Some(act) = slayer_action {
                    lines.push(HudLine { label: lang.t("Plan", "計画"), value: act.title.clone() });
                    if let Some(h) = act.estimated_hours {
                        lines.push(HudLine { label: lang.t("Est. time", "推定時間"), value: fmt_hours(h) });
                    }
                }
                HudContextBlock { context: "SLAYER".into(), title: focus.map(|s| format!("{} Slayer", s.boss_name)).unwrap_or_else(|| "Slayer".into()), lines }
            })
        }
        "GARDEN" => a.garden.as_ref().map(|g| {
            let mut lines = Vec::new();
            if let Some(l) = g.garden_level.as_ref() {
                lines.push(HudLine { label: "Garden".into(), value: format!("{}", l.level) });
            }
            if let Some(ff) = g.farming_gear_stats.stats.get("Farming Fortune") {
                lines.push(HudLine { label: "Farming Fortune".into(), value: format!("{ff:.0}") });
            }
            if let Some(act) = r.actions.iter().find(|x| x.category == "Farming") {
                lines.push(HudLine { label: lang.t("Current estimated rate", "推定効率"), value: act.title.clone() });
            }
            HudContextBlock { context: "GARDEN".into(), title: lang.t("Farming Goal", "ファーミングの目標"), lines }
        }),
        "DUNGEON" => a.dungeons.as_ref().map(|d| {
            let mut lines = vec![HudLine { label: "Catacombs".into(), value: format!("{} ({:.0}%)", d.catacombs.level, d.catacombs.progress * 100.0) }];
            if let Some(act) = r.actions.iter().find(|x| x.category == "Dungeons") {
                lines.push(HudLine { label: lang.t("Plan", "計画"), value: act.title.clone() });
            }
            HudContextBlock { context: "DUNGEON".into(), title: lang.t("Dungeons", "ダンジョン"), lines }
        }),
        _ => None,
    };
    s.context = block;
    s
}
