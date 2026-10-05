//! Roadmap: ordered steps from a goal graph (prerequisites first), overall progress,
//! remaining cost and time, and the actions that advance each step.

use std::collections::HashSet;

use skypilot_protocol::goals::{GoalGraph, GoalNode, NodeKind, NodeStatus, RecommendedAction, Roadmap, RoadmapStep};

fn post_order<'a>(g: &'a GoalGraph, id: &str, seen: &mut HashSet<String>, out: &mut Vec<&'a GoalNode>) {
    if !seen.insert(id.to_string()) {
        return;
    }
    let Some(n) = g.nodes.iter().find(|x| x.id == id) else { return };
    let mut kids: Vec<&GoalNode> = n.children.iter().filter_map(|c| g.nodes.iter().find(|x| &x.id == c)).collect();
    // hard requirements first, then shorter/cheaper work first; money last (it funds acquisition)
    kids.sort_by(|a, b| {
        (a.kind == NodeKind::Money)
            .cmp(&(b.kind == NodeKind::Money))
            .then(b.hard_requirement.cmp(&a.hard_requirement))
            .then(a.estimated_hours.unwrap_or(0.0).total_cmp(&b.estimated_hours.unwrap_or(0.0)))
    });
    for k in kids {
        post_order(g, &k.id, seen, out);
    }
    out.push(n);
}

/// Which action ids advance which node (provider id conventions).
pub fn action_matches(action_id: &str, node_id: &str) -> bool {
    match node_id {
        "mp" => action_id.starts_with("acc-"),
        "cata" => action_id.starts_with("dungeon-cata"),
        "hotm" => action_id.starts_with("mining-hotm"),
        "money" => action_id.starts_with("money-") || action_id.starts_with("liquidate"),
        "rep" => action_id.starts_with("kuudra-rep"),
        n if n.starts_with("slayer:") => action_id.starts_with(&format!("slayer-{}", &n[7..])),
        n if n.starts_with("slayer-unlock:") => action_id.starts_with(&format!("slayer-unlock-{}", &n[14..])),
        n if n.starts_with("floor:") => action_id == format!("dungeon-complete-{}", &n[6..]),
        n if n.starts_with("powder:") => action_id == format!("mining-powder-{}", &n[7..]),
        n if n.starts_with("kuudra:") => action_id == format!("kuudra-run-{}", &n[7..]),
        n if n.starts_with("collection:") => action_id.eq_ignore_ascii_case(&format!("collection-{}", &n[11..])),
        "skill:FARMING" => action_id.starts_with("farming-"),
        "skill:MINING" => action_id.starts_with("mining-"),
        "skill:FISHING" => action_id.starts_with("fishing-"),
        "skill:FORAGING" => action_id.starts_with("foraging-"),
        _ => false,
    }
}

pub fn build(goal_id: &str, title: &str, g: &GoalGraph, actions: &[RecommendedAction]) -> Roadmap {
    let mut order = Vec::new();
    post_order(g, &g.root, &mut HashSet::new(), &mut order);
    // weighted progress over every node (hard = 2, soft = 1)
    let (mut wsum, mut psum) = (0.0, 0.0);
    for n in &g.nodes {
        let w = if n.hard_requirement { 2.0 } else { 1.0 };
        wsum += w;
        psum += w * if n.status == NodeStatus::Satisfied { 1.0 } else { n.progress };
    }
    let open: Vec<&&GoalNode> = order.iter().filter(|n| n.status != NodeStatus::Satisfied).collect();
    let remaining_cost: f64 = open.iter().filter_map(|n| n.estimated_cost).sum();
    let remaining_hours: f64 = open.iter().filter(|n| n.kind != NodeKind::Money).filter_map(|n| n.estimated_hours).sum();
    let steps: Vec<RoadmapStep> = order
        .iter()
        .enumerate()
        .map(|(i, n)| RoadmapStep {
            index: i as u32 + 1,
            node_id: n.id.clone(),
            title: n.title.clone(),
            detail: n.detail.clone(),
            status: n.status,
            progress: n.progress,
            current: n.current,
            target: n.target,
            unit: n.unit.clone(),
            estimated_cost: n.estimated_cost,
            estimated_hours: n.estimated_hours,
            hard_requirement: n.hard_requirement,
            source: n.source,
            action_ids: actions.iter().filter(|a| action_matches(&a.id, &n.id)).map(|a| a.id.clone()).collect(),
        })
        .collect();
    let current_step = steps.iter().find(|s| s.status != NodeStatus::Satisfied).map(|s| s.index);
    Roadmap {
        goal_id: goal_id.to_string(),
        title: title.to_string(),
        overall_progress: if wsum > 0.0 { (psum / wsum).clamp(0.0, 1.0) } else { 0.0 },
        remaining_cost: (remaining_cost > 0.0).then_some(remaining_cost),
        remaining_hours: (remaining_hours > 0.0).then_some(remaining_hours),
        steps,
        current_step,
    }
}
