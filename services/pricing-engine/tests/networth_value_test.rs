//! Networth must not count asking prices nobody has paid: with no realised sale an owned item
//! is worth at most the cheapest listing and at most what crafting it from Bazaar items costs.

mod common;

use serde_json::json;
use skypilot_items::from_client_json;
use skypilot_pricing::fair_value::{FairValueConfig, compute};
use skypilot_pricing::networth::{craft_ceiling, value_item};
use skypilot_pricing::normalize::fingerprint_item;
use skypilot_pricing::{PriceOracle, PriceSide, Quote};
use skypilot_protocol::market::FairValue;

const NOW: i64 = 1_800_000_000_000;

/// ENCHANTED_MELON_BLOCK is treated as an auction item listed at 1M / 10M / 10M / 10M with no
/// sales; its ingredient (160 Enchanted Melon Slices) trades on the Bazaar at 300.
struct Fake {
    asks: Vec<f64>,
}

impl Fake {
    fn fv(&self) -> FairValue {
        compute("ENCHANTED_MELON_BLOCK", "ENCHANTED_MELON_BLOCK", &self.asks, &[], NOW, NOW - 3_600_000, &FairValueConfig::default())
    }
}

impl PriceOracle for Fake {
    fn quote(&self, id: &str, side: PriceSide) -> Option<Quote> {
        match (id, side) {
            ("ENCHANTED_MELON", _) => Some(Quote { price: 300.0, source: "Bazaar", confidence: 0.95, sales_per_day: 1e7 }),
            ("ENCHANTED_MELON_BLOCK", PriceSide::Sell) => self.fv().value.map(|v| Quote { price: v, source: "BIN", confidence: 0.2, sales_per_day: 0.0 }),
            ("ENCHANTED_MELON_BLOCK", PriceSide::Buy) => self.fv().lowest_bin.map(|v| Quote { price: v, source: "BIN", confidence: 0.2, sales_per_day: 0.0 }),
            _ => None,
        }
    }
    fn fair_value(&self, key: &str) -> Option<FairValue> {
        (key == "ENCHANTED_MELON_BLOCK").then(|| self.fv())
    }
    fn is_bazaar(&self, id: &str) -> bool {
        id == "ENCHANTED_MELON"
    }
}

#[test]
fn asking_prices_are_capped_by_cheapest_listing_and_craft_cost() {
    let reg = common::registry();
    let item = from_client_json(&json!({"custom_data": {"id": "ENCHANTED_MELON_BLOCK"}, "count": 1})).unwrap();
    let fp = fingerprint_item(&item, &reg).unwrap();

    let fake = Fake { asks: vec![1_000_000.0, 10_000_000.0, 10_000_000.0, 10_000_000.0] };
    assert!((fake.fv().value.unwrap() - 9_700_000.0).abs() < 1.0, "fair value engine keeps its anti-bait median");
    assert_eq!(craft_ceiling(&reg, &fake, "ENCHANTED_MELON_BLOCK"), Some(48_000.0));
    let v = value_item(&fp, &reg, &fake).unwrap();
    // capped to the craft cost, but never below what an NPC pays (160 x 160 x 2 = 51,200)
    assert_eq!(reg.npc_price("ENCHANTED_MELON_BLOCK"), Some(51_200.0));
    assert_eq!(v.base, 51_200.0);
    assert!(v.method.contains("capped"));

    // Expensive ingredient: the cheapest listing is the binding cap.
    struct Pricey(Fake);
    impl PriceOracle for Pricey {
        fn quote(&self, id: &str, side: PriceSide) -> Option<Quote> {
            if id == "ENCHANTED_MELON" {
                return Some(Quote { price: 100_000.0, source: "Bazaar", confidence: 0.95, sales_per_day: 1e7 });
            }
            self.0.quote(id, side)
        }
        fn fair_value(&self, key: &str) -> Option<FairValue> {
            self.0.fair_value(key)
        }
        fn is_bazaar(&self, id: &str) -> bool {
            self.0.is_bazaar(id)
        }
    }
    let v = value_item(&fp, &reg, &Pricey(Fake { asks: fake.asks.clone() })).unwrap();
    assert!((v.base - 970_000.0).abs() < 1e-6, "undercut the cheapest listing, got {}", v.base);
}

/// Live case (Oct 2026): plain Lapis leggings sold once at 100k with a 100k listing, while
/// two 6M listings kept the blended fair value at 5.6M. Owned items are capped at the
/// cheapest listing.
#[test]
fn one_sale_and_hopeful_listings_do_not_inflate_networth() {
    struct OneSale;
    impl OneSale {
        fn fv() -> FairValue {
            let sale = skypilot_pricing::state::SaleRecord { unit_price: 100_000.0, ts_ms: NOW - 3_600_000, bin: true };
            compute("LAPIS_ARMOR_LEGGINGS", "LAPIS_ARMOR_LEGGINGS", &[100_000.0, 6_000_000.0, 6_500_000.0], &[sale], NOW, NOW - 7_200_000, &FairValueConfig::default())
        }
    }
    impl PriceOracle for OneSale {
        fn quote(&self, id: &str, _side: PriceSide) -> Option<Quote> {
            (id == "LAPIS_ARMOR_LEGGINGS").then(|| {
                let f = Self::fv();
                Quote { price: f.value.unwrap(), source: "Sales", confidence: f.confidence.score, sales_per_day: f.sales_per_day }
            })
        }
        fn fair_value(&self, key: &str) -> Option<FairValue> {
            (key == "LAPIS_ARMOR_LEGGINGS").then(Self::fv)
        }
        fn is_bazaar(&self, _id: &str) -> bool {
            false
        }
    }
    assert!(OneSale::fv().value.unwrap() > 5_000_000.0, "blend is dominated by the 6M asks");
    let reg = common::registry();
    let item = from_client_json(&json!({"custom_data": {"id": "LAPIS_ARMOR_LEGGINGS"}, "count": 1})).unwrap();
    let fp = fingerprint_item(&item, &reg).unwrap();
    let v = value_item(&fp, &reg, &OneSale).unwrap();
    assert_eq!(v.base, 100_000.0);
}
