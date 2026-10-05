#![allow(dead_code)]

use std::path::PathBuf;
use std::sync::{Arc, OnceLock};

use serde_json::Value;
use skypilot_data::{DataPaths, Registry};
use skypilot_pricing::MarketEngine;
use skypilot_profile::{AnalyzerInput, Inventories, analyze};
use skypilot_protocol::goals::PlayStyle;
use skypilot_protocol::profile::{PlayerAnalysis, PlayerRef};
use skypilot_reco::{Ctx, Lang};

pub fn root() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..")
}

pub struct World {
    pub registry: Arc<Registry>,
    pub market: MarketEngine,
    pub analysis: PlayerAnalysis,
    pub inv: Inventories,
}

fn read(p: &str) -> Vec<u8> {
    std::fs::read(root().join(p)).unwrap()
}

pub fn world() -> &'static World {
    static W: OnceLock<World> = OnceLock::new();
    W.get_or_init(|| {
        let registry = Arc::new(Registry::load(&DataPaths::fixtures(&root())).unwrap());
        let market = MarketEngine::new(registry.clone());
        market.ingest_bazaar(serde_json::from_slice(&read("fixtures/hypixel/bazaar.json")).unwrap());
        let page: skypilot_hypixel::AuctionPage = serde_json::from_slice(&read("fixtures/hypixel/auctions_page0.json")).unwrap();
        market.ingest_auctions(page.auctions, page.last_updated);
        let ended: skypilot_hypixel::EndedAuctions = serde_json::from_slice(&read("fixtures/hypixel/auctions_ended.json")).unwrap();
        market.ingest_ended(&ended);
        let profiles: Value = serde_json::from_slice(&read("fixtures/hypixel/profiles_sample.json")).unwrap();
        let museum: Value = serde_json::from_slice(&read("fixtures/hypixel/museum_sample.json")).unwrap();
        let uuid = profiles["profiles"][0]["members"].as_object().unwrap().keys().next().unwrap().clone();
        let input = AnalyzerInput {
            player: PlayerRef { username: "SampleUser".into(), uuid },
            profiles: &profiles,
            profile_id: None,
            museum: Some(&museum),
            garden: None,
            fetched_at_ms: 1_791_167_000_000,
            now_ms: 1_791_167_060_000,
        };
        let (analysis, inv) = analyze(&input, &registry, &market).unwrap();
        World { registry, market, analysis, inv }
    })
}

pub fn ctx(w: &'static World, lang: Lang) -> Ctx<'static> {
    Ctx {
        registry: &w.registry,
        oracle: &w.market,
        market: Some(&w.market),
        analysis: &w.analysis,
        inv: &w.inv,
        play_style: PlayStyle::Fastest,
        budget: None,
        now_ms: 1_791_167_060_000,
        lang,
    }
}

/// Same world, but with the analysis edited (e.g. to simulate a mid-game player).
pub fn ctx_with(w: &'static World, analysis: &'static PlayerAnalysis, lang: Lang) -> Ctx<'static> {
    Ctx { analysis, ..ctx(w, lang) }
}
