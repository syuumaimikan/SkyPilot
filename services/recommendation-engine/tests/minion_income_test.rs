//! Minion income must come from the minion's actual product (e.g. Melon → MELON), never
//! from an unrelated item that happens to share a name, and must stay in a plausible range.

mod common;

use skypilot_reco::Lang;
use skypilot_reco::providers::minions;

#[test]
fn minion_products_and_income_are_plausible() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let rows = minions::rows(&ctx);
    assert!(!rows.is_empty(), "NEU minion tiers loaded");
    let by_base = |b: &str| rows.iter().find(|r| r.base == b).unwrap_or_else(|| panic!("{b} minion"));
    // "Melon" is also the display name of BUILDER_MELON; the minion makes the MELON material.
    assert_eq!(by_base("MELON").product.as_deref(), Some("MELON"));
    assert_eq!(by_base("COBBLESTONE").product.as_deref(), Some("COBBLESTONE"));
    for r in &rows {
        if let Some(v) = r.next_coins_per_day {
            // One unupgraded minion produces at most a few thousand items per day; even the most
            // valuable minion products stay far below 10M coins/day.
            assert!(v.is_finite() && v >= 0.0 && v < 10_000_000.0, "{} ({:?}) {v} coins/day", r.base, r.product);
        }
    }
}
