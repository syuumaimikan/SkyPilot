mod common;

use skypilot_protocol::goals::GoalPriority;
use skypilot_protocol::mod_api::{ModMaintenance, ModNewsItem, ModServerStatus, ModServiceStatus};
use skypilot_reco::providers::{Provider, today::TodayProvider};
use skypilot_reco::{Ctx, Lang, LiveSignals, Planner};

fn live(now: i64) -> LiveSignals {
    LiveSignals {
        server: Some(ModServerStatus {
            available: true,
            indicator: "none".into(),
            description: "All Systems Operational".into(),
            services: vec![ModServiceStatus { name: "SkyBlock".into(), status: "operational".into() }],
            maintenances: vec![ModMaintenance {
                id: "m1".into(),
                name: "SkyBlock v0.99".into(),
                status: "scheduled".into(),
                starts_ms: Some(now + 3 * 3_600_000),
                ends_ms: Some(now + 5 * 3_600_000),
                services: vec!["SkyBlock".into()],
                skyblock: true,
                update: None,
                link: "https://status.hypixel.net/incidents/m1".into(),
            }],
            incidents: vec![],
            recent_maintenances: vec![],
            checked_at_ms: now,
            page_url: "https://status.hypixel.net".into(),
            source: "status.hypixel.net".into(),
        }),
        news: vec![ModNewsItem { title: "SkyBlock 0.99".into(), date: "1st November 2026".into(), link: "https://hypixel.net/threads/1/".into() }],
        key_rejected: true,
    }
}

#[test]
fn today_combines_status_events_completion_and_news() {
    let world = common::world();
    let base = common::ctx(world, Lang::En);
    let signals = live(base.now_ms);
    let ctx = Ctx { live: Some(&signals), ..base };
    let r = TodayProvider.report(&ctx, &[]);
    let ids: Vec<&str> = r.sections.iter().map(|s| s.id.as_str()).collect();
    assert_eq!(ids[0], "alerts");
    assert!(ids.contains(&"focus") && ids.contains(&"events") && ids.contains(&"news"), "{ids:?}");
    let alerts = &r.sections[0].bullets;
    assert!(alerts.iter().any(|b| b.contains("maintenance in 3h") || b.contains("maintenance in 3")), "{alerts:?}");
    assert!(alerts.iter().any(|b| b.contains("403")), "{alerts:?}");
    assert!(r.summary.iter().any(|m| m.label == "Completion"));
    assert!(r.unknowns.is_empty(), "{:?}", r.unknowns);
}

#[test]
fn without_live_data_the_guide_says_so_instead_of_guessing() {
    let world = common::world();
    let ctx = common::ctx(world, Lang::Ja);
    let r = TodayProvider.report(&ctx, &[]);
    assert!(r.unknowns.iter().any(|u| u.contains("ステータスページ")));
    assert!(r.unknowns.iter().any(|u| u.contains("ニュース")));
}

#[test]
fn plans_do_not_count_today_twice_but_its_report_has_actions() {
    let world = common::world();
    let ctx = common::ctx(world, Lang::En);
    let planner = Planner::default();
    let texts = vec![("max magical power".to_string(), GoalPriority::Primary)];
    let plan = planner.plan(&ctx, &texts, 50);
    assert!(plan.actions.iter().all(|a| a.provider != "today"));
    let mut ids: Vec<&str> = plan.actions.iter().map(|a| a.id.as_str()).collect();
    let n = ids.len();
    ids.sort();
    ids.dedup();
    assert_eq!(ids.len(), n);
    let report = planner.report(&ctx, "today", &texts).unwrap();
    assert!(!report.actions.is_empty());
}
