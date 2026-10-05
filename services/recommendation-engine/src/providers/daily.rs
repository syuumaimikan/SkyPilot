//! Daily Advisor: daily task catalog filtered by unlocks and ordered by the player's goals.

use skypilot_protocol::advisor::{AdvisorReport, Tone};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

pub struct DailyProvider;

fn goal_tags(goals: &[GoalContext]) -> Vec<&'static str> {
    let mut tags = vec!["goal"];
    for g in goals {
        for n in g.open_nodes() {
            let t: &[&'static str] = match n.id.as_str() {
                "mp" => &["accessories"],
                "cata" => &["dungeons", "catacombs"],
                "hotm" => &["mining", "hotm"],
                "money" => &["money"],
                id if id.starts_with("slayer:") => &["combat"],
                id if id.starts_with("floor:") => &["dungeons"],
                id if id.starts_with("kuudra:") => &["kuudra", "crimson"],
                id if id.starts_with("powder:") => &["mining"],
                "skill:FARMING" | "garden" => &["farming", "garden"],
                "skill:MINING" => &["mining"],
                "skill:ENCHANTING" => &["enchanting"],
                _ => &[],
            };
            tags.extend_from_slice(t);
        }
    }
    tags
}

impl Provider for DailyProvider {
    fn id(&self) -> &'static str {
        "daily"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new()
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("daily", ctx.t("Daily tasks", "デイリータスク"));
        let tags = goal_tags(goals);
        let mut tasks: Vec<(&skypilot_data::curated::DailyTask, usize, Option<bool>)> = ctx
            .registry
            .curated
            .daily
            .iter()
            .map(|t| (t, t.tags.iter().filter(|x| tags.contains(&x.as_str())).count(), ctx.check(&t.requires)))
            .filter(|(_, _, ok)| *ok != Some(false))
            .collect();
        tasks.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.minutes.total_cmp(&b.0.minutes)));
        let mut s = section("tasks", ctx.t("Today", "今日のタスク"));
        s.table = Some(table(
            &["#", "Task", "Details", "Minutes", "Goal relevance"],
            tasks.iter().enumerate().map(|(i, (t, rel, _))| vec![num((i + 1) as f64, None), text(&t.name), text(&t.detail), num(t.minutes, None), if *rel > 0 { badge(ctx.t("Helps your goal", "目標に有効"), Tone::Good) } else { text("") }]).collect(),
        ));
        r.sections.push(s);
        r
    }
}
