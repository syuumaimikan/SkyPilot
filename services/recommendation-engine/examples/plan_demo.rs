//! Prints a full plan for the anonymised fixture profile using fixture market data.
//!
//! cargo run -p skypilot-reco --example plan_demo -- "Enderman Slayer 7にしてTerminatorを入手したい" ja

use std::path::PathBuf;
use std::sync::Arc;

use serde_json::Value;
use skypilot_data::{DataPaths, Registry};
use skypilot_pricing::MarketEngine;
use skypilot_profile::{AnalyzerInput, analyze};
use skypilot_protocol::goals::{GoalPriority, PlayStyle};
use skypilot_protocol::profile::PlayerRef;
use skypilot_reco::{Ctx, Lang, Planner};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let goal = args.get(1).cloned().unwrap_or_else(|| "Hyperion".into());
    let lang = Lang::parse(args.get(2).map(String::as_str));
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..");
    let read = |p: &str| std::fs::read(root.join(p)).unwrap();
    let registry = Arc::new(Registry::load(&DataPaths::fixtures(&root)).unwrap());
    let market = MarketEngine::new(registry.clone());
    market.ingest_bazaar(serde_json::from_slice(&read("fixtures/hypixel/bazaar.json")).unwrap());
    let page: skypilot_hypixel::AuctionPage = serde_json::from_slice(&read("fixtures/hypixel/auctions_page0.json")).unwrap();
    market.ingest_auctions(page.auctions, page.last_updated);
    market.ingest_ended(&serde_json::from_slice(&read("fixtures/hypixel/auctions_ended.json")).unwrap());
    let profiles: Value = serde_json::from_slice(&read("fixtures/hypixel/profiles_sample.json")).unwrap();
    let uuid = profiles["profiles"][0]["members"].as_object().unwrap().keys().next().unwrap().clone();
    let input = AnalyzerInput { player: PlayerRef { username: "SampleUser".into(), uuid }, profiles: &profiles, profile_id: None, museum: None, garden: None, fetched_at_ms: 0, now_ms: 0 };
    let (analysis, inv) = analyze(&input, &registry, &market).unwrap();
    let ctx = Ctx { registry: &registry, oracle: &market, market: Some(&market), analysis: &analysis, inv: &inv, play_style: PlayStyle::Fastest, budget: None, now_ms: 1_791_167_060_000, lang };
    let out = Planner::default().plan(&ctx, &[(goal, GoalPriority::Primary)], 8);
    println!("parsed: {:?}", out.parse.goals.iter().map(|g| &g.title).collect::<Vec<_>>());
    for g in &out.goals {
        println!("\n== {} — {:.0}% (remaining cost {:?}, hours {:?})", g.spec.title, g.roadmap.overall_progress * 100.0, g.roadmap.remaining_cost.map(|c| c.round()), g.roadmap.remaining_hours);
        for s in &g.roadmap.steps {
            println!("  {:>2}. [{:?}] {}{}", s.index, s.status, s.title, s.detail.as_ref().map(|d| format!(" — {d}")).unwrap_or_default());
        }
    }
    println!("\nWHAT SHOULD I DO NOW?");
    for (i, a) in out.actions.iter().enumerate() {
        println!("{}. {} (priority {:.3}, cost {:?}, {:.2}h)\n   why: {}", i + 1, a.title, a.score.priority, a.estimated_cost.map(|c| c.round()), a.score.time_hours, a.why);
    }
    println!("\nprogression: stage {:?}, strong {:?}, weak {:?}", analysis.progression.stage, analysis.progression.strong_areas, analysis.progression.weak_areas);
}
