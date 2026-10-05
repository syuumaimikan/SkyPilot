use proptest::prelude::*;
use skypilot_pricing::fair_value::{FairValueConfig, compute};
use skypilot_pricing::state::SaleRecord;

const NOW: i64 = 1_800_000_000_000;
const H: i64 = 3_600_000;

fn sales(prices: &[f64], spacing_h: i64) -> Vec<SaleRecord> {
    prices.iter().enumerate().map(|(i, p)| SaleRecord { unit_price: *p, ts_ms: NOW - (i as i64 + 1) * spacing_h * H, bin: true }).collect()
}

#[test]
fn not_lowest_bin_only() {
    // one bait listing at 1M while everything trades at ~10M
    let asks = [1_000_000.0, 10_200_000.0, 10_400_000.0, 10_500_000.0];
    let s = sales(&[10_000_000.0, 9_900_000.0, 10_100_000.0, 10_050_000.0, 9_950_000.0, 10_000_000.0, 10_020_000.0, 9_980_000.0], 6);
    let fv = compute("X", "X", &asks, &s, NOW, NOW - 7 * 24 * H, &FairValueConfig::default());
    let v = fv.value.unwrap();
    assert!(v > 9_000_000.0 && v < 10_600_000.0, "fair value {v} must ignore the bait listing");
    assert_eq!(fv.lowest_bin, Some(1_000_000.0));
    assert_eq!(fv.second_lowest_bin, Some(10_200_000.0));
    assert!(fv.sales_median.is_some() && fv.sales_trimmed_mean.is_some() && fv.sales_weighted_median.is_some());
}

#[test]
fn outlier_sales_are_removed() {
    let mut p = vec![5_000_000.0; 12];
    p.push(500_000_000.0); // fat-finger sale
    let fv = compute("X", "X", &[5_100_000.0], &sales(&p, 2), NOW, NOW - 3 * 24 * H, &FairValueConfig::default());
    assert!(fv.value.unwrap() < 6_000_000.0);
}

#[test]
fn thin_data_has_low_confidence() {
    let fv = compute("X", "X", &[3_000_000.0], &sales(&[2_900_000.0], 1), NOW, NOW - 24 * H, &FairValueConfig::default());
    assert_eq!(fv.confidence.level, skypilot_protocol::common::ConfidenceLevel::Low);
    assert!(fv.confidence.reasons.iter().any(|r| r.contains("Insufficient sales data")));
}

#[test]
fn liquidity_and_sell_time_scale_with_volume() {
    let busy = compute("X", "X", &[100.0, 101.0], &sales(&vec![100.0; 200], 0), NOW, NOW - 24 * H, &FairValueConfig::default());
    let slow = compute("Y", "Y", &[100.0, 101.0], &sales(&[100.0, 99.0, 101.0], 48), NOW, NOW - 7 * 24 * H, &FairValueConfig::default());
    assert!(busy.liquidity > slow.liquidity);
    assert!(busy.expected_sell_minutes.unwrap().high < slow.expected_sell_minutes.unwrap().high);
}

#[test]
fn one_or_two_asks_without_sales_are_not_a_value() {
    // A lone 30M listing of an item that normally sells for ~2k must not become its value.
    let one = compute("X", "X", &[30_000_000.0], &[], NOW, NOW - H, &FairValueConfig::default());
    assert!(one.value.is_none());
    assert_eq!(one.lowest_bin, Some(30_000_000.0));
    assert!(one.confidence.reasons.iter().any(|r| r.contains("Too few listings")));
    let two = compute("X", "X", &[30_000_000.0, 75_000_000.0], &[], NOW, NOW - H, &FairValueConfig::default());
    assert!(two.value.is_none());
    let three = compute("X", "X", &[100.0, 102.0, 105.0], &[], NOW, NOW - H, &FairValueConfig::default());
    assert!((three.value.unwrap() - 102.0 * 0.97).abs() < 1e-9);
    assert_eq!(three.method, "asks only (-3%)");
}

#[test]
fn empty_inputs_yield_no_value() {
    let fv = compute("X", "X", &[], &[], NOW, NOW, &FairValueConfig::default());
    assert!(fv.value.is_none());
    assert_eq!(fv.confidence.score, 0.0);
}

proptest! {
    // Property-based: NaN / negative / huge / overflow-prone samples never panic and never
    // produce a non-finite or negative fair value.
    #[test]
    fn hostile_numbers_never_break_fair_value(
        asks in proptest::collection::vec(prop_oneof![Just(f64::NAN), Just(f64::INFINITY), Just(-1.0), Just(f64::MAX), Just(0.0), 1.0..1e12f64], 0..30),
        sale_prices in proptest::collection::vec(prop_oneof![Just(f64::NAN), Just(f64::NEG_INFINITY), Just(-5e9), Just(1e300), 1.0..1e12f64], 0..60),
        ts_offsets in proptest::collection::vec(any::<i64>(), 0..60),
    ) {
        let sales: Vec<SaleRecord> = sale_prices.iter().zip(ts_offsets.iter().chain(std::iter::repeat(&0)))
            .map(|(p, o)| SaleRecord { unit_price: *p, ts_ms: NOW.wrapping_sub(o.rem_euclid(30 * 24 * H)), bin: true }).collect();
        let fv = compute("K", "K", &asks, &sales, NOW, NOW - 7 * 24 * H, &FairValueConfig::default());
        if let Some(v) = fv.value {
            prop_assert!(v.is_finite() && v >= 0.0);
        }
        prop_assert!(fv.confidence.score.is_finite());
        prop_assert!(fv.liquidity.is_finite());
    }
}
