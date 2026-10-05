mod common;

use std::collections::HashMap;

use skypilot_pricing::craft::CraftCalculator;
use skypilot_pricing::{PriceOracle, PriceSide, Quote};
use skypilot_protocol::market::FairValue;

/// Deterministic oracle with fixed prices.
struct Fixed(HashMap<&'static str, f64>);

impl PriceOracle for Fixed {
    fn quote(&self, id: &str, _side: PriceSide) -> Option<Quote> {
        self.0.get(id).map(|p| Quote { price: *p, source: "Bazaar", confidence: 0.9, sales_per_day: 100.0 })
    }
    fn fair_value(&self, _: &str) -> Option<FairValue> {
        None
    }
    fn is_bazaar(&self, id: &str) -> bool {
        self.0.contains_key(id)
    }
}

#[test]
fn recursive_craft_cost_matches_neu_recipes() {
    let reg = common::registry();
    // Only raw melons are priced: enchanted forms must be costed through the recipe tree.
    let oracle = Fixed(HashMap::from([("MELON", 2.0)]));
    let mut calc = CraftCalculator::new(&reg, &oracle);
    let em = calc.unit_cost("ENCHANTED_MELON").expect("craftable");
    assert_eq!(em.source, "Craft");
    assert_eq!(em.unit_price, Some(320.0)); // 160 melon x 2
    let block = calc.unit_cost("ENCHANTED_MELON_BLOCK").unwrap();
    assert_eq!(block.unit_price, Some(51_200.0)); // 160 x 320
    assert_eq!(block.children[0].item_id, "ENCHANTED_MELON");
    assert_eq!(block.children[0].amount, 160.0);
}

#[test]
fn buying_wins_when_cheaper_than_crafting() {
    let reg = common::registry();
    let oracle = Fixed(HashMap::from([("MELON", 2.0), ("ENCHANTED_MELON", 300.0)]));
    let mut calc = CraftCalculator::new(&reg, &oracle);
    let em = calc.unit_cost("ENCHANTED_MELON").unwrap();
    assert_eq!(em.source, "Bazaar");
    assert_eq!(em.unit_price, Some(300.0));
}

#[test]
fn hyperion_tree_resolves_through_necron_blade() {
    let reg = common::registry();
    let oracle = Fixed(HashMap::from([("GIANT_FRAGMENT_LASER", 1_000_000.0), ("WITHER_CATALYST", 50_000.0), ("NECRON_HANDLE", 400_000_000.0)]));
    let mut calc = CraftCalculator::new(&reg, &oracle);
    let (total, count, children) = calc.craft_cost("HYPERION").expect("hyperion recipe");
    assert_eq!(count, 1.0);
    // 8 lasers + (24 catalysts + handle)
    assert_eq!(total, 8.0 * 1_000_000.0 + 24.0 * 50_000.0 + 400_000_000.0);
    assert!(children.iter().any(|c| c.item_id == "NECRON_BLADE" && c.source == "Craft"));
}

#[test]
fn unknown_ingredients_do_not_produce_a_price() {
    let reg = common::registry();
    let oracle = Fixed(HashMap::new());
    let mut calc = CraftCalculator::new(&reg, &oracle);
    assert!(calc.unit_cost("HYPERION").is_none());
    assert!(calc.craft_cost("HYPERION").is_none());
}
