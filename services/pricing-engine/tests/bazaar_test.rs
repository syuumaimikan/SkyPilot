mod common;

use skypilot_hypixel::{BazaarProductRaw, OrderSummary, QuickStatus};
use skypilot_pricing::bazaar::{BazaarFlipConfig, OrderSide, estimate_fill, instant_buy, instant_sell, order_flips};
use skypilot_pricing::{MarketEngine, PriceOracle, PriceSide};
use skypilot_protocol::market::BazaarFlipKind;

fn product(buy_offer: f64, sell_order: f64, weekly: f64) -> BazaarProductRaw {
    BazaarProductRaw {
        product_id: "TEST".into(),
        sell_summary: vec![OrderSummary { amount: 1000.0, price_per_unit: sell_order, orders: 3.0 }],
        buy_summary: vec![OrderSummary { amount: 1000.0, price_per_unit: buy_offer, orders: 3.0 }],
        quick_status: QuickStatus {
            sell_price: sell_order,
            sell_volume: 50_000.0,
            sell_moving_week: weekly,
            sell_orders: 50.0,
            buy_price: buy_offer,
            buy_volume: 50_000.0,
            buy_moving_week: weekly,
            buy_orders: 50.0,
        },
    }
}

#[test]
fn instant_prices_follow_hypixel_naming() {
    // buy_summary = sell offers (what instant buyers pay); sell_summary = buy orders.
    let p = product(5.0, 4.2, 1000.0);
    assert_eq!(instant_buy(&p), Some(5.0));
    assert_eq!(instant_sell(&p), Some(4.2));
}

#[test]
fn fill_time_grows_with_amount_and_shrinks_with_volume() {
    let p = product(5.0, 4.0, 1_000_000.0);
    let small = estimate_fill(&p, OrderSide::BuyOrder, 100.0, &[]).unwrap();
    let large = estimate_fill(&p, OrderSide::BuyOrder, 10_000.0, &[]).unwrap();
    assert!(large.high > small.high);
    let busy = product(5.0, 4.0, 10_000_000.0);
    assert!(estimate_fill(&busy, OrderSide::BuyOrder, 10_000.0, &[]).unwrap().high < large.high);
    assert!(estimate_fill(&product(5.0, 4.0, 0.0), OrderSide::SellOffer, 10.0, &[]).is_none());
    assert!(estimate_fill(&p, OrderSide::SellOffer, f64::NAN, &[]).is_none());
}

#[test]
fn real_bazaar_order_flips_are_net_positive_after_tax() {
    let engine = MarketEngine::new(common::registry());
    engine.ingest_bazaar(common::bazaar());
    let st = engine.state.read();
    let names = |id: &str| id.to_string();
    let flips = order_flips(&st, &BazaarFlipConfig { capital: 100_000_000.0, ..Default::default() }, &names);
    assert!(!flips.is_empty(), "live snapshot contains spreads worth flipping");
    for f in &flips {
        assert_eq!(f.kind, BazaarFlipKind::OrderFlip);
        assert!(f.sell_price > f.buy_price);
        assert!((f.profit_per_unit - (f.sell_price * (1.0 - 0.0125) - f.buy_price)).abs() < 1e-6);
        assert!(f.profit_per_unit > 0.0 && f.capital_required <= 100_000_000.0 + 1e-6);
        assert!(f.buy_fill_minutes.is_some() && f.sell_fill_minutes.is_some());
    }
}

#[test]
fn oracle_uses_bazaar_sides_correctly() {
    let engine = MarketEngine::new(common::registry());
    engine.ingest_bazaar(common::bazaar());
    let buy = engine.quote("ENCHANTED_MELON", PriceSide::Buy).unwrap();
    let sell = engine.quote("ENCHANTED_MELON", PriceSide::Sell).unwrap();
    assert_eq!(buy.source, "Bazaar");
    assert!(buy.price >= sell.price);
    // NPC fallback for items without market data
    let npc = engine.quote("ROUGH_RUBY_GEM", PriceSide::Sell).unwrap();
    assert!(npc.price > 0.0);
}

#[test]
fn bazaar_list_contains_products_and_freshness() {
    let engine = MarketEngine::new(common::registry());
    engine.ingest_bazaar(common::bazaar());
    let list = engine.bazaar_list(50_000_000.0);
    assert!(list.products.len() > 100);
    assert_eq!(list.tax_rate, 0.0125);
    assert!(list.freshness.updated_at_ms > 0);
}
