//! The "best form to sell" must not value a farmer's whole hourly output at the top order
//! of a thin product (live Bazaar, Oct 2026: Melon Block bid 60.1 = 6.7/slice with ~375k/day
//! volume, versus ~1.95/slice for the enchanted forms).

mod common;

use skypilot_protocol::market::FairValue;
use skypilot_pricing::{PriceOracle, PriceSide, Quote};
use skypilot_reco::{Ctx, Lang};
use skypilot_reco::market_forms::best_value;

struct Fake;

impl PriceOracle for Fake {
    fn quote(&self, id: &str, _side: PriceSide) -> Option<Quote> {
        let (price, per_day) = match id {
            "MELON" => (0.5, 1e9),
            // 160 slices: 6.25/slice, but only 100k traded per day
            "ENCHANTED_MELON" => (1_000.0, 100_000.0),
            // 160 x 160 slices: ~1.95/slice, deep market
            "ENCHANTED_MELON_BLOCK" => (50_000.0, 1_000_000.0),
            _ => return None,
        };
        Some(Quote { price, source: "Bazaar", confidence: 0.95, sales_per_day: per_day })
    }
    fn fair_value(&self, _k: &str) -> Option<FairValue> {
        None
    }
    fn is_bazaar(&self, id: &str) -> bool {
        id.contains("MELON")
    }
}

#[test]
fn thin_markets_are_not_used_for_hourly_income() {
    let w = common::world();
    let ctx = Ctx { oracle: &Fake, market: None, ..common::ctx(w, Lang::En) };
    // Price only (no quantity): the best per-slice price wins.
    let (v, form) = best_value(&ctx, "MELON", None).unwrap();
    assert_eq!(form, "ENCHANTED_MELON");
    assert!((v - 6.25).abs() < 1e-9);
    // ~900k slices/hour = 5,625 Enchanted Melon Slices/hour, more than 5% of its daily volume.
    let (v, form) = best_value(&ctx, "MELON", Some(900_000.0)).unwrap();
    assert_eq!(form, "ENCHANTED_MELON_BLOCK");
    assert!((v - 50_000.0 / 25_600.0).abs() < 1e-9);
}
