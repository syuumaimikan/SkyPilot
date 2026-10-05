//! Farming math. Base drops per harvested block come from the data registry
//! (EliteFarmers farming-weight constants, MIT). Farming Fortune grants +1% drops per
//! point (wiki: 260 fortune = +2 guaranteed drops and a 60% chance of a third).

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CropYield {
    pub blocks_per_hour: f64,
    pub crops_per_hour: f64,
}

pub fn crop_yield(base_drops: f64, fortune: f64, blocks_per_second: f64, uptime: f64) -> CropYield {
    let clean = |x: f64| if x.is_finite() { x.max(0.0) } else { 0.0 };
    let uptime = if uptime.is_finite() { uptime.clamp(0.0, 1.0) } else { 0.0 };
    let blocks_per_hour = clean(blocks_per_second) * 3600.0 * uptime;
    let crops_per_hour = blocks_per_hour * clean(base_drops) * (1.0 + clean(fortune) / 100.0);
    CropYield { blocks_per_hour, crops_per_hour }
}

/// Coins/hour when selling `units_per_hour` at `unit_price`.
pub fn coins_per_hour(units_per_hour: f64, unit_price: f64) -> f64 {
    let r = units_per_hour * unit_price;
    if r.is_finite() { r.max(0.0) } else { 0.0 }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn melon() {
        // 20 BPS, melon 5 base drops, 1500 fortune => 5 * 16 = 80 per block
        let y = crop_yield(5.0, 1500.0, 20.0, 1.0);
        assert_eq!(y.blocks_per_hour, 72_000.0);
        assert!((y.crops_per_hour - 5_760_000.0).abs() < 1e-6);
        assert_eq!(crop_yield(f64::NAN, -1.0, 20.0, 2.0).crops_per_hour, 0.0);
    }
}
