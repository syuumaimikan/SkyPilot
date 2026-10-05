use skypilot_pricing::fair_value::{FairValueConfig, compute};
use skypilot_pricing::manipulation::check;
use skypilot_pricing::normalize::Listing;
use skypilot_pricing::state::SaleRecord;

const NOW: i64 = 1_800_000_000_000;
const H: i64 = 3_600_000;

fn l(price: f64, seller: &str) -> Listing {
    Listing {
        auction_id: format!("{seller}{price}"),
        market_id: "X".into(),
        comparable_key: "X".into(),
        exact_key: "X".into(),
        name: "X".into(),
        rarity: None,
        modifiers: vec![],
        bin: true,
        price,
        count: 1,
        seller: seller.into(),
        start_ms: NOW,
        end_ms: NOW,
        soulbound: false,
    }
}

fn s(p: f64, hours_ago: i64) -> SaleRecord {
    SaleRecord { unit_price: p, ts_ms: NOW - hours_ago * H, bin: true }
}

#[test]
fn healthy_market_has_no_flags() {
    let listings = [l(100.0, "a"), l(101.0, "b"), l(102.0, "c")];
    let refs: Vec<&Listing> = listings.iter().collect();
    let sales: Vec<SaleRecord> = (1..20).map(|i| s(100.0 + (i % 3) as f64, i * 6)).collect();
    let fair = compute("X", "X", &[100.0, 101.0, 102.0], &sales, NOW, NOW - 7 * 24 * H, &FairValueConfig::default());
    let c = check(&refs, &sales, &fair, 95.0, NOW);
    assert!(c.flags.is_empty(), "{:?}", c.flags);
    assert!(!c.suspicious);
}

#[test]
fn spike_on_thin_volume_is_flagged() {
    let listings = [l(300.0, "a"), l(310.0, "b")];
    let refs: Vec<&Listing> = listings.iter().collect();
    let mut sales: Vec<SaleRecord> = (30..60).map(|i| s(100.0, i * 4)).collect();
    sales.extend([s(250.0, 2), s(260.0, 5)]);
    let fair = compute("X", "X", &[300.0, 310.0], &sales, NOW, NOW - 14 * 24 * H, &FairValueConfig::default());
    let c = check(&refs, &sales, &fair, 120.0, NOW);
    assert!(c.flags.iter().any(|f| f == "Artificial price spike"), "{:?}", c.flags);
    assert!(c.suspicious);
}

#[test]
fn insufficient_sample_and_low_volume() {
    let listings = [l(100.0, "a")];
    let refs: Vec<&Listing> = listings.iter().collect();
    let sales = vec![s(100.0, 100)];
    let fair = compute("X", "X", &[100.0], &sales, NOW, NOW - 7 * 24 * H, &FairValueConfig::default());
    let c = check(&refs, &sales, &fair, 90.0, NOW);
    assert!(c.flags.iter().any(|f| f == "Insufficient sample"));
    assert!(c.flags.iter().any(|f| f == "Very low volume"));
}

#[test]
fn outlier_listing_and_cluster() {
    let listings = [l(10.0, "w"), l(100.0, "w"), l(101.0, "w"), l(102.0, "w"), l(103.0, "z")];
    let refs: Vec<&Listing> = listings.iter().collect();
    let sales: Vec<SaleRecord> = (1..15).map(|i| s(100.0, i * 5)).collect();
    let fair = compute("X", "X", &[100.0, 101.0, 102.0, 103.0], &sales, NOW, NOW - 7 * 24 * H, &FairValueConfig::default());
    let c = check(&refs, &sales, &fair, 10.0, NOW);
    assert!(c.flags.iter().any(|f| f == "Outlier listing"));
    assert!(c.flags.iter().any(|f| f == "Suspicious listing cluster"));
    assert!(c.verdict.starts_with("DO NOT FLIP"));
}
