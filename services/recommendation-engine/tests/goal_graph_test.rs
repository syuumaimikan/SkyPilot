mod common;

use skypilot_protocol::goals::{GoalKind, GoalPriority, NodeStatus};
use skypilot_reco::Lang;
use skypilot_reco::goal_parser::GoalParser;
use skypilot_reco::graph::{GraphBuilder, prune};

#[test]
fn parses_english_and_japanese_goals() {
    let w = common::world();
    let p = GoalParser::new(&w.registry);
    let r = p.parse("Enderman Slayer 7にしてTerminatorを入手したい");
    assert_eq!(r.goals.len(), 2, "{:?}", r);
    assert_eq!(r.goals[0].kind, GoalKind::SlayerLevel { slayer: "enderman".into(), level: 7 });
    assert_eq!(r.goals[1].kind, GoalKind::AcquireItem { item_id: "TERMINATOR".into() });

    let r = p.parse("Hyperionを最短で入手したい");
    assert_eq!(r.goals[0].kind, GoalKind::AcquireItem { item_id: "HYPERION".into() });
    assert_eq!(p.parse("hype").goals[0].kind, GoalKind::AcquireItem { item_id: "HYPERION".into() });
    assert_eq!(p.parse("Mining 60").goals[0].kind, GoalKind::SkillLevel { skill: "MINING".into(), level: 60 });
    assert_eq!(p.parse("HOTM 7").goals[0].kind, GoalKind::HotmLevel { level: 7 });
    assert_eq!(p.parse("MP 1000").goals[0].kind, GoalKind::MagicalPower { target: 1000 });
    assert_eq!(p.parse("cata 30").goals[0].kind, GoalKind::CatacombsLevel { level: 30 });
    assert_eq!(p.parse("500m coins").goals[0].kind, GoalKind::Coins { amount: 500_000_000.0 });
    assert_eq!(p.parse("採掘60と農業50").goals.len(), 2);
    assert_eq!(p.parse("Kuudra infernal").goals[0].kind, GoalKind::KuudraTier { tier: "infernal".into() });
    assert!(p.parse("qwertyuiop zxcv").goals.is_empty(), "nonsense must not invent a goal");

    let r = p.parse("HOTM 10と宝石パウダー2000万");
    assert_eq!(r.goals[0].kind, GoalKind::HotmLevel { level: 10 });
    assert_eq!(r.goals[1].kind, GoalKind::PowderTarget { powder: "GEMSTONE".into(), amount: 20_000_000.0 });
    assert_eq!(p.parse("glacite powder 5m").goals[0].kind, GoalKind::PowderTarget { powder: "GLACITE".into(), amount: 5_000_000.0 });
    assert_eq!(p.parse("ミスリルパウダー1000万").goals[0].kind, GoalKind::PowderTarget { powder: "MITHRIL".into(), amount: 10_000_000.0 });
}

#[test]
fn hyperion_graph_has_requirement_readiness_and_acquisition() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let spec = GoalParser::new(&w.registry).parse("Hyperion").goals.remove(0);
    let g = GraphBuilder::build(&ctx, &spec);
    assert_eq!(g.root, "item:HYPERION");
    let ids: Vec<&str> = g.nodes.iter().map(|n| n.id.as_str()).collect();
    // DUNGEON_TIER 7 requirement from the Hypixel items resource
    assert!(ids.contains(&"floor:F7"), "{ids:?}");
    assert!(ids.contains(&"acquire:HYPERION"));
    let f7 = g.nodes.iter().find(|n| n.id == "floor:F7").unwrap();
    assert!(f7.hard_requirement);
    // The sample player has M7 completions => F7 is satisfied and pruned
    assert_eq!(f7.status, NodeStatus::Satisfied);
    let pruned = prune(g);
    assert!(pruned.satisfied_pruned.contains(&"floor:F7".to_string()));
    assert!(!pruned.nodes.iter().any(|n| n.id == "floor:F7"));
}

#[test]
fn terminator_graph_uses_slayer_requirement_from_api() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::Ja);
    let spec = GoalParser::new(&w.registry).parse("Terminator").goals.remove(0);
    let g = GraphBuilder::build(&ctx, &spec);
    let slayer = g.nodes.iter().find(|n| n.id == "slayer:enderman").expect("Enderman Slayer 7 requirement");
    assert_eq!(slayer.target, Some(7.0));
    assert!(slayer.hard_requirement);
    // catacombs_requirements (dungeon use) are soft
    let cata = g.nodes.iter().find(|n| n.id == "cata").unwrap();
    assert!(!cata.hard_requirement);
    let root = g.nodes.iter().find(|n| n.id == g.root).unwrap();
    assert!(root.children.contains(&"slayer:enderman".to_string()));
}

#[test]
fn planner_dedupes_shared_requirements_across_goals() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let planner = skypilot_reco::Planner::default();
    let out = planner.plan(&ctx, &[("MP 3000".into(), GoalPriority::Primary), ("Kuudra infernal".into(), GoalPriority::Secondary)], 10);
    assert_eq!(out.goals.len(), 2);
    assert_eq!(out.goals[0].priority, GoalPriority::Primary);
    assert!(!out.actions.is_empty());
    // MP 3000 is above the sample's MP -> an accessory action must exist and reference the goal
    let acc = out.actions.iter().find(|a| a.id == "acc-buy").expect("accessory action");
    assert!(acc.goal_ids.contains(&out.goals[0].id));
    assert!(acc.purchases.len() >= 2);
}
