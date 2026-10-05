mod common;

use std::collections::HashSet;

use skypilot_pricing::MarketEngine;
use skypilot_pricing::normalize::{normalize_auction, normalize_sale};

#[test]
fn real_auctions_normalize_to_listings() {
    let reg = common::registry();
    let page = common::auctions();
    let listings: Vec<_> = page.auctions.iter().filter_map(|a| normalize_auction(a, &reg)).collect();
    assert!(listings.len() as f64 >= page.auctions.len() as f64 * 0.95, "almost every auction normalises");
    for l in &listings {
        assert!(l.price > 0.0 && l.unit_price() > 0.0);
        assert!(!l.market_id.is_empty());
        assert!(l.comparable_key.starts_with(&l.market_id));
        if l.market_id.starts_with("PET:") {
            // pet level is resolved through the NEU pet tables
            assert!(l.comparable_key.contains("lvl="), "{}", l.comparable_key);
        }
    }
    let ids: HashSet<_> = listings.iter().map(|l| &l.auction_id).collect();
    assert_eq!(ids.len(), listings.len(), "auction ids are unique");
}

#[test]
fn ended_auctions_become_sales_and_deduplicate() {
    let reg = common::registry();
    let ended = common::ended();
    let sales: Vec<_> = ended.auctions.iter().filter_map(|a| normalize_sale(a, &reg)).collect();
    assert!(!sales.is_empty());
    let engine = MarketEngine::new(reg);
    let first = engine.ingest_ended(&ended).len();
    assert_eq!(first, sales.len());
    // same payload again: no double counting
    engine.ingest_ended(&ended);
    assert_eq!(engine.state.read().sales_recorded as usize, sales.len());
}

#[test]
fn engine_indexes_bin_listings_by_comparable_key() {
    let reg = common::registry();
    let engine = MarketEngine::new(reg);
    let page = common::auctions();
    let n = engine.ingest_auctions(page.auctions, page.last_updated);
    assert!(n > 100);
    let st = engine.state.read();
    for idx in st.bin_by_comparable.values() {
        let prices: Vec<f64> = idx.iter().map(|i| st.listings[*i].unit_price()).collect();
        assert!(prices.windows(2).all(|w| w[0] <= w[1]), "sorted ascending");
        assert!(idx.iter().all(|i| st.listings[*i].bin));
    }
}
