//! Mining math (formulas from the official SkyBlock wiki, "Mining Speed" article):
//! `ticks = round(block_strength * 30 / mining_speed)`, soft-capped at 4 ticks;
//! instant break when mining speed exceeds `instant_factor * block_strength`.

pub const TICKS_PER_SECOND: f64 = 20.0;
pub const SOFT_CAP_TICKS: f64 = 4.0;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BreakTime {
    pub ticks: f64,
    pub instant: bool,
    pub soft_capped: bool,
}

/// `instant_factor` is 30 for most blocks and 60 for ores, gemstones and dwarven metals.
pub fn break_time(block_strength: f64, mining_speed: f64, instant_factor: f64) -> Option<BreakTime> {
    if !block_strength.is_finite() || !mining_speed.is_finite() || block_strength <= 0.0 || mining_speed <= 0.0 {
        return None;
    }
    if instant_factor.is_finite() && instant_factor > 0.0 && mining_speed > instant_factor * block_strength {
        return Some(BreakTime { ticks: 1.0, instant: true, soft_capped: false });
    }
    let raw = (block_strength * 30.0 / mining_speed).round();
    if raw <= SOFT_CAP_TICKS {
        Some(BreakTime { ticks: SOFT_CAP_TICKS, instant: false, soft_capped: true })
    } else {
        Some(BreakTime { ticks: raw, instant: false, soft_capped: false })
    }
}

/// Mining speed needed to hit the 4-tick soft cap: ceil(20 * strength / 3).
pub fn soft_cap_speed(block_strength: f64) -> f64 {
    if !block_strength.is_finite() || block_strength <= 0.0 {
        return 0.0;
    }
    (20.0 * block_strength / 3.0).ceil()
}

/// Blocks per hour while continuously mining; `uptime` (0..=1) accounts for walking,
/// ability cooldowns, refueling and server lag.
pub fn blocks_per_hour(ticks_per_block: f64, uptime: f64) -> f64 {
    if !ticks_per_block.is_finite() || ticks_per_block <= 0.0 {
        return 0.0;
    }
    let uptime = if uptime.is_finite() { uptime.clamp(0.0, 1.0) } else { 0.0 };
    TICKS_PER_SECOND * 3600.0 / ticks_per_block * uptime
}

/// Expected drops per broken block. Fortune adds +1% drops per point.
/// `pristine` (percent) converts a drop into a higher tier worth `pristine_ratio` base drops.
pub fn drops_per_block(base_drops: f64, fortune: f64, pristine: f64, pristine_ratio: f64) -> f64 {
    let base = if base_drops.is_finite() { base_drops.max(0.0) } else { 0.0 };
    let fortune = if fortune.is_finite() { fortune.max(0.0) } else { 0.0 };
    let mut drops = base * (1.0 + fortune / 100.0);
    if pristine.is_finite() && pristine > 0.0 && pristine_ratio.is_finite() && pristine_ratio > 1.0 {
        let p = (pristine / 100.0).clamp(0.0, 1.0);
        drops *= 1.0 + p * (pristine_ratio - 1.0);
    }
    drops
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wiki_examples() {
        // Ruby 2300 strength at 3000 speed: round(2300*30/3000) = 23 ticks
        let t = break_time(2300.0, 3000.0, 60.0).unwrap();
        assert_eq!(t.ticks, 23.0);
        // soft cap
        assert_eq!(break_time(500.0, 10_000.0, 60.0).unwrap().ticks, 4.0);
        assert_eq!(soft_cap_speed(3000.0), 20000.0);
        // instant break for weak block
        assert!(break_time(50.0, 4000.0, 30.0).unwrap().instant);
        assert!(break_time(0.0, 100.0, 30.0).is_none());
        assert_eq!(blocks_per_hour(20.0, 1.0), 3600.0);
        assert!((drops_per_block(1.0, 100.0, 0.0, 80.0) - 2.0).abs() < 1e-9);
    }
}
