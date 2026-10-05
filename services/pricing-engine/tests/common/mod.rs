#![allow(dead_code)]

use std::path::PathBuf;
use std::sync::{Arc, OnceLock};

use skypilot_data::{DataPaths, Registry};
use skypilot_hypixel::{AuctionPage, BazaarResponse, EndedAuctions};

pub fn root() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..")
}

pub fn registry() -> Arc<Registry> {
    static REG: OnceLock<Arc<Registry>> = OnceLock::new();
    REG.get_or_init(|| Arc::new(Registry::load(&DataPaths::fixtures(&root())).expect("registry"))).clone()
}

pub fn auctions() -> AuctionPage {
    serde_json::from_slice(&std::fs::read(root().join("fixtures/hypixel/auctions_page0.json")).unwrap()).unwrap()
}

pub fn ended() -> EndedAuctions {
    serde_json::from_slice(&std::fs::read(root().join("fixtures/hypixel/auctions_ended.json")).unwrap()).unwrap()
}

pub fn bazaar() -> BazaarResponse {
    serde_json::from_slice(&std::fs::read(root().join("fixtures/hypixel/bazaar.json")).unwrap()).unwrap()
}
