mod common;

use skypilot_calc::fees::AhFeeConfig;
use skypilot_pricing::fair_value::FairValueConfig;
use skypilot_pricing::flips::{DefaultFlipScore, FlipConfig, FlipScoreStrategy, filter, scan};
use skypilot_pricing::normalize::{Listing, Sale};
use skypilot_pricing::state::MarketState;
use skypilot_protocol::market::FlipQuery;

const NOW: i64 = 1_800_000_000_000;
const H: i64 = 3_600_000;

fn listing(id: &str, key: &str, price: f64, seller: &str) -> Listing {
    Listing {
        auction_id: id.into(),
        market_id: key.split('|').next().unwrap().into(),
        comparable_key: key.into(),
        exact_key: key.into(),
        name: "Shadow Fury".into(),
        rarity: Some("LEGENDARY".into()),
        modifiers: vec![],
        bin: true,
        price,
        count: 1,
        seller: seller.into(),
        start_ms: NOW - H,
        end_ms: NOW + 24 * H,
        soulbound: false,
    }
}

fn state_with(listings: Vec<Listing>, sale_prices: &[f64]) -> MarketState {
    let mut st = MarketState::new(NOW - 7 * 24 * H);
    let key = listings[0].comparable_key.clone();
    st.replace_listings(listings, NOW);
    let sales = sale_prices.iter().enumerate().map(|(i, p)| Sale {
        auction_id: format!("s{i}"),
        market_id: key.split('|').next().unwrap().into(),
        comparable_key: key.clone(),
        unit_price: *p,
        count: 1,
        bin: true,
        ts_ms: NOW - (i as i64 + 1) * 3 * H,
    });
    st.add_sales(sales, NOW);
    st
}

fn run(st: &mut MarketState) -> Vec<skypilot_protocol::market::FlipOpportunity> {
    let names = |id: &str| id.to_string();
    scan(st, &AhFeeConfig::default(), &FlipConfig::default(), &FairValueConfig::default(), &DefaultFlipScore, &names, NOW)
}

#[test]
fn underpriced_listing_is_found_with_net_profit_after_fees() {
    let key = "SHADOW_FURY|s5";
    let mut st = state_with(
        vec![
            listing("cheap", key, 41_200_000.0, "a"),
            listing("b", key, 48_300_000.0, "b"),
            listing("c", key, 48_900_000.0, "c"),
            listing("d", key, 49_500_000.0, "d"),
        ],
        &[47_500_000.0, 48_000_000.0, 47_800_000.0, 48_200_000.0, 47_900_000.0, 48_100_000.0, 47_700_000.0, 48_000_000.0, 47_600_000.0, 48_300_000.0],
    );
    let flips = run(&mut st);
    let f = flips.iter().find(|f| f.auction_id == "cheap").expect("flip found");
    // recommended sell undercuts the next listing and never exceeds fair value
    assert!(f.recommended_sell <= 48_300_000.0 * 0.99 + 1.0);
    let fees = AhFeeConfig::default().bin_fees(f.recommended_sell, 24.0);
    assert!((f.expected_net_profit - (f.recommended_sell - fees.total() - 41_200_000.0)).abs() < 1.0);
    assert!((f.fees.total - fees.total()).abs() < 1e-6);
    assert!(f.roi > 0.1 && f.score > 0.0);
    assert!(f.max_buy_price >= 41_200_000.0, "the buy price itself must satisfy the targets");
    assert!(!f.manipulation.suspicious);
    assert_eq!(f.view_command, "/viewauction cheap");
    // candidates are recorded for backtesting
    assert!(!st.candidates.is_empty());
}

#[test]
fn no_sales_history_means_no_flip() {
    let key = "SOME_ITEM";
    let mut st = MarketState::new(NOW);
    st.replace_listings(vec![listing("x", key, 1_000_000.0, "a"), listing("y", key, 9_000_000.0, "b")], NOW);
    assert!(run(&mut st).is_empty());
}

#[test]
fn seller_cluster_is_flagged_do_not_flip() {
    let key = "RARE_THING";
    let mut st = state_with(
        vec![
            listing("cheap", key, 10_000_000.0, "whale"),
            listing("w2", key, 30_000_000.0, "whale"),
            listing("w3", key, 30_500_000.0, "whale"),
            listing("w4", key, 31_000_000.0, "whale"),
            listing("w5", key, 31_500_000.0, "whale"),
        ],
        &[30_000_000.0, 29_000_000.0, 30_500_000.0, 31_000_000.0, 29_500_000.0],
    );
    let flips = run(&mut st);
    if let Some(f) = flips.iter().find(|f| f.auction_id == "cheap") {
        assert!(f.manipulation.suspicious);
        assert!(f.manipulation.verdict.starts_with("DO NOT FLIP"));
        assert_eq!(f.score, 0.0, "manipulated flips rank last");
    }
}

#[test]
fn filters_apply_budget_roi_and_risk() {
    let key = "SHADOW_FURY|s5";
    let mut st = state_with(
        vec![listing("cheap", key, 41_200_000.0, "a"), listing("b", key, 48_300_000.0, "b"), listing("c", key, 48_900_000.0, "c")],
        &[48_000_000.0; 12],
    );
    let flips = run(&mut st);
    assert!(!flips.is_empty());
    let q = FlipQuery { max_budget: Some(10_000_000.0), min_profit: None, min_roi: None, min_confidence: None, max_risk: None, search: None, limit: None };
    assert!(filter(&flips, &q).is_empty());
    let q = FlipQuery { max_budget: Some(100_000_000.0), min_profit: Some(2_000_000.0), min_roi: Some(0.08), min_confidence: None, max_risk: None, search: Some("fury".into()), limit: Some(5) };
    assert_eq!(filter(&flips, &q).len(), 1);
    assert_eq!(DefaultFlipScore.name(), "profit*p*conf/(capital*time*risk)");
}
