mod common;

use proptest::prelude::*;

#[test]
fn registry_fee_model_matches_wiki_brackets() {
    let reg = common::registry();
    let cfg = reg.curated.market.auction_house.fee_config(false);
    // 1% under 10M, 2% from 10M, 2.5% from 100M
    assert_eq!(cfg.bin_listing_fee(5_000_000.0), 50_000.0);
    assert_eq!(cfg.bin_listing_fee(50_000_000.0), 1_000_000.0);
    assert_eq!(cfg.bin_listing_fee(200_000_000.0), 5_000_000.0);
    // claim tax 1% above 1M, never taking the payout under 1M
    assert_eq!(cfg.claim_tax(900_000.0), 0.0);
    assert_eq!(cfg.claim_tax(1_000_100.0), 100.0);
    assert_eq!(cfg.claim_tax(47_800_000.0), 478_000.0);
    // Derpy quadruples only the collection fee
    let derpy = reg.curated.market.auction_house.fee_config(true);
    assert_eq!(derpy.claim_tax(47_800_000.0), 1_912_000.0);
    assert_eq!(derpy.bin_listing_fee(47_800_000.0), cfg.bin_listing_fee(47_800_000.0));
    assert_eq!(reg.curated.market.bazaar.tax_rate, 0.0125);
}

#[test]
fn max_buy_price_respects_targets() {
    let reg = common::registry();
    let cfg = reg.curated.market.auction_house.fee_config(false);
    let sell = 47_800_000.0;
    let max_buy = cfg.max_buy_price(sell, 24.0, 2_000_000.0, 0.10);
    let profit = cfg.bin_net_proceeds(sell, 24.0) - max_buy;
    assert!(profit >= 2_000_000.0 - 1e-6);
    assert!(profit / max_buy >= 0.10 - 1e-9);
}

proptest! {
    #[test]
    fn fees_are_finite_and_bounded(price in prop_oneof![Just(f64::NAN), Just(f64::INFINITY), Just(-1.0), 0.0..1e13f64], hours in prop_oneof![Just(f64::NAN), 0.0..1000.0f64]) {
        let cfg = skypilot_calc::fees::AhFeeConfig::default();
        let f = cfg.bin_fees(price, hours);
        prop_assert!(f.total().is_finite() && f.total() >= 0.0);
        if price.is_finite() && price > 0.0 {
            prop_assert!(f.claim_tax <= price);
        }
    }
}
