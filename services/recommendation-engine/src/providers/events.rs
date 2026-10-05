//! Event Advisor: SkyBlock calendar, current mayor/minister perks and relevance to goals.

use skypilot_protocol::advisor::{AdvisorReport, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_hours};
use crate::optimizer::CandidateAction;

pub struct EventsProvider;

impl Provider for EventsProvider {
    fn id(&self) -> &'static str {
        "events"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new()
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("events", ctx.t("Events & Mayor", "イベント・市長"));
        let cal = &ctx.registry.curated.calendar;
        let today = cal.date_at(ctx.now_ms);
        r.summary.push(Metric::text(ctx.t("SkyBlock date", "SkyBlock日付"), format!("Year {} · {} {}", today.year, cal.month_name(today.month), today.day)));
        if let Some(e) = &ctx.registry.election {
            let mut s = section("mayor", ctx.t("Current mayor", "現在の市長"));
            if let Some(m) = &e.mayor {
                s.bullets.push(format!("{} ({})", m.name, skypilot_items::fingerprint::title_case(&m.key)));
                for p in &m.perks {
                    s.bullets.push(format!("• {}: {}", p.name, skypilot_items::text::strip_formatting(&p.description)));
                }
            }
            if let Some(m) = &e.minister {
                s.bullets.push(format!("{}: {}", ctx.t("Minister", "大臣"), m.name));
                for p in &m.perks {
                    s.bullets.push(format!("• {}: {}", p.name, skypilot_items::text::strip_formatting(&p.description)));
                }
            }
            s.sources = vec![DataSource::HypixelResources];
            r.sections.push(s);
        }
        let interest: Vec<String> = goals
            .iter()
            .flat_map(|g| g.open_nodes().map(|n| n.id.clone()))
            .map(|id| match id.as_str() {
                "mp" => "accessories".to_string(),
                x if x.starts_with("skill:FARMING") => "farming".to_string(),
                _ => id,
            })
            .collect();
        let mut s = section("calendar", ctx.t("Upcoming events", "今後のイベント"));
        s.table = Some(table(
            &["Event", "Starts", "Status", "Value", "Relevant to your goals"],
            cal.upcoming(ctx.now_ms)
                .into_iter()
                .map(|(e, start, end, active)| {
                    let rel = e.progression.iter().any(|p| interest.iter().any(|i| i.contains(p.as_str())));
                    vec![
                        text(&e.name),
                        if active { badge(ctx.t("Now", "開催中"), Tone::Good) } else { text(format!("in {}", fmt_hours((start - ctx.now_ms) as f64 / 3_600_000.0))) },
                        text(if active { format!("ends in {}", fmt_hours((end - ctx.now_ms) as f64 / 3_600_000.0)) } else { String::new() }),
                        text(&e.value),
                        if rel { badge(ctx.t("Yes", "はい"), Tone::Info) } else { text("") },
                    ]
                })
                .collect(),
        ));
        s.notes.push(ctx.t("Event dates from the official wiki calendar; real-time conversion uses the SkyBlock epoch.", "イベント日程は公式Wikiのカレンダーより。実時間への換算はSkyBlockの暦を使用。"));
        s.sources = vec![DataSource::SkyPilotRegistry];
        r.sections.push(s);
        r
    }
}
