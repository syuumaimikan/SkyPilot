//! Magical Power math. Per-rarity values are data (registry `accessories.toml`).

/// Stats multiplier of the selected accessory power (wiki "Magical Power"):
/// `29.97 * ln(0.0019 * MP + 1)^1.2`
pub fn stats_multiplier(magical_power: f64) -> f64 {
    if !magical_power.is_finite() || magical_power <= 0.0 {
        return 0.0;
    }
    29.97 * (0.0019 * magical_power + 1.0).ln().powf(1.2)
}

/// Coins per magical power point; `None` if the upgrade adds no MP.
pub fn coins_per_mp(cost: f64, mp_gain: f64) -> Option<f64> {
    if !cost.is_finite() || cost < 0.0 || !mp_gain.is_finite() || mp_gain <= 0.0 {
        return None;
    }
    Some(cost / mp_gain)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn multiplier_monotonic() {
        assert_eq!(stats_multiplier(0.0), 0.0);
        let a = stats_multiplier(500.0);
        let b = stats_multiplier(1000.0);
        assert!(a > 0.0 && b > a);
        assert_eq!(coins_per_mp(340_000.0, 5.0), Some(68_000.0));
        assert_eq!(coins_per_mp(1.0, 0.0), None);
    }
}
