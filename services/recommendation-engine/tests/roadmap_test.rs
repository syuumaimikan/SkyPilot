mod common;

use skypilot_protocol::common::DataSource;
use skypilot_protocol::goals::{GoalGraph, GoalNode, GoalPriority, NodeKind, NodeStatus};
use skypilot_reco::Lang;
use skypilot_reco::roadmap::{action_matches, build};

fn n(id: &str, kind: NodeKind, status: NodeStatus, progress: f64, hard: bool, children: &[&str], hours: Option<f64>) -> GoalNode {
    GoalNode {
        id: id.into(),
        kind,
        title: id.into(),
        detail: None,
        status,
        progress,
        current: None,
        target: None,
        unit: None,
        hard_requirement: hard,
        source: DataSource::SkyPilotHeuristic,
        children: children.iter().map(|c| c.to_string()).collect(),
        estimated_cost: Some(10.0),
        estimated_hours: hours,
    }
}

#[test]
fn prerequisites_come_first_and_progress_is_weighted() {
    let g = GoalGraph {
        root: "item:X".into(),
        nodes: vec![
            n("item:X", NodeKind::Item, NodeStatus::NotStarted, 0.0, true, &["floor:F7", "acquire:X"], None),
            n("floor:F7", NodeKind::Dungeon, NodeStatus::NotStarted, 0.0, true, &["cata", "mp"], None),
            n("cata", NodeKind::Dungeon, NodeStatus::InProgress, 0.5, true, &[], Some(5.0)),
            n("mp", NodeKind::MagicalPower, NodeStatus::InProgress, 0.8, false, &[], Some(0.2)),
            n("acquire:X", NodeKind::Acquisition, NodeStatus::NotStarted, 0.0, true, &["money"], None),
            n("money", NodeKind::Money, NodeStatus::InProgress, 0.3, true, &[], Some(10.0)),
        ],
        satisfied_pruned: vec![],
    };
    let r = build("g1", "X", &g, &[]);
    let order: Vec<&str> = r.steps.iter().map(|s| s.node_id.as_str()).collect();
    assert_eq!(order, vec!["cata", "mp", "floor:F7", "money", "acquire:X", "item:X"]);
    assert_eq!(r.current_step, Some(1));
    // weights: hard=2 soft=1 -> (2*.5 + 1*.8 + 2*.3) / 11
    assert!((r.overall_progress - (1.0 + 0.8 + 0.6) / 11.0).abs() < 1e-9);
    assert_eq!(r.remaining_hours, Some(5.2), "money hours are not double counted");
    assert_eq!(r.remaining_cost, Some(60.0));
}

#[test]
fn action_linking_conventions() {
    assert!(action_matches("acc-buy", "mp"));
    assert!(action_matches("slayer-enderman-next", "slayer:enderman"));
    assert!(!action_matches("slayer-zombie-next", "slayer:enderman"));
    assert!(action_matches("dungeon-cata-next", "cata"));
    assert!(action_matches("money-farming_best_crop", "money"));
}

#[test]
fn end_to_end_roadmap_for_real_profile() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::Ja);
    let out = skypilot_reco::Planner::default().plan(&ctx, &[("Hyperionを最短で入手したい".into(), GoalPriority::Primary)], 8);
    let g = &out.goals[0];
    assert_eq!(g.roadmap.steps.last().unwrap().node_id, "item:HYPERION");
    assert!(g.roadmap.overall_progress > 0.0 && g.roadmap.overall_progress <= 1.0);
    // Japanese explanations are produced when lang=ja
    assert!(g.roadmap.steps.iter().any(|s| s.title.contains("入手")));
}
