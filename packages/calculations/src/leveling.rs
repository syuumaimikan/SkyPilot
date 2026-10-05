//! XP table utilities. A table stores the *cumulative* XP required to reach each level:
//! `thresholds[i]` = XP needed to reach level `i + 1`.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct XpTable {
    thresholds: Vec<f64>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LevelProgress {
    pub level: u32,
    pub max_level: u32,
    /// Progress within the current level (1.0 when maxed).
    pub progress: f64,
    /// XP left to reach the next level, `None` when maxed.
    pub xp_to_next: Option<f64>,
    pub level_with_progress: f64,
}

impl XpTable {
    /// From cumulative thresholds (Hypixel `totalExpRequired`, NEU slayer tables).
    pub fn from_cumulative(thresholds: Vec<f64>) -> Self {
        let mut t: Vec<f64> = thresholds.into_iter().map(|x| if x.is_finite() { x.max(0.0) } else { 0.0 }).collect();
        // enforce monotonicity so a corrupt table can never produce negative progress
        for i in 1..t.len() {
            if t[i] < t[i - 1] {
                t[i] = t[i - 1];
            }
        }
        XpTable { thresholds: t }
    }

    /// From per-level increments (NEU catacombs, HOTM, pets).
    pub fn from_increments(increments: &[f64]) -> Self {
        let mut acc = 0.0;
        let mut t = Vec::with_capacity(increments.len());
        for &x in increments {
            acc += if x.is_finite() { x.max(0.0) } else { 0.0 };
            t.push(acc);
        }
        XpTable { thresholds: t }
    }

    pub fn max_level(&self) -> u32 {
        self.thresholds.len() as u32
    }

    /// Truncate the table to a cap (e.g. Farming 50 until Anita upgrades).
    pub fn capped(&self, cap: u32) -> XpTable {
        XpTable { thresholds: self.thresholds.iter().copied().take(cap as usize).collect() }
    }

    /// Cumulative XP needed to reach `level` (0 for level 0).
    pub fn xp_for_level(&self, level: u32) -> Option<f64> {
        if level == 0 {
            return Some(0.0);
        }
        self.thresholds.get(level as usize - 1).copied()
    }

    pub fn progress(&self, xp: f64) -> LevelProgress {
        let xp = if xp.is_finite() { xp.max(0.0) } else { 0.0 };
        let max = self.max_level();
        let level = self.thresholds.iter().take_while(|&&t| xp >= t).count() as u32;
        if level >= max {
            return LevelProgress { level: max, max_level: max, progress: 1.0, xp_to_next: None, level_with_progress: max as f64 };
        }
        let floor = if level == 0 { 0.0 } else { self.thresholds[level as usize - 1] };
        let next = self.thresholds[level as usize];
        let span = next - floor;
        let progress = if span > 0.0 { ((xp - floor) / span).clamp(0.0, 1.0) } else { 0.0 };
        LevelProgress { level, max_level: max, progress, xp_to_next: Some((next - xp).max(0.0)), level_with_progress: level as f64 + progress }
    }

    /// XP still required to reach `target` from `xp` (0 if already reached, None if out of table).
    pub fn xp_remaining(&self, xp: f64, target: u32) -> Option<f64> {
        let need = self.xp_for_level(target)?;
        let xp = if xp.is_finite() { xp.max(0.0) } else { 0.0 };
        Some((need - xp).max(0.0))
    }
}

/// Pet XP table for a rarity: NEU `pet_levels` are increments, offset by rarity.
/// Level 1 needs 0 XP; level `n+1` needs the sum of `pet_levels[offset .. offset + n]`.
pub fn pet_table(pet_levels: &[f64], rarity_offset: usize, max_level: u32) -> XpTable {
    let mut thresholds = Vec::with_capacity(max_level as usize);
    thresholds.push(0.0); // level 1
    let mut acc = 0.0;
    for i in 0..(max_level.saturating_sub(1) as usize) {
        let inc = pet_levels.get(rarity_offset + i).copied().unwrap_or(0.0);
        acc += if inc.is_finite() { inc.max(0.0) } else { 0.0 };
        thresholds.push(acc);
    }
    XpTable::from_cumulative(thresholds)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cumulative_progress() {
        let t = XpTable::from_cumulative(vec![50.0, 175.0, 375.0]);
        let p = t.progress(100.0);
        assert_eq!(p.level, 1);
        assert!((p.progress - 0.4).abs() < 1e-9);
        assert_eq!(p.xp_to_next, Some(75.0));
        assert_eq!(t.progress(10_000.0).level, 3);
        assert_eq!(t.progress(f64::NAN).level, 0);
        assert_eq!(t.xp_remaining(100.0, 3), Some(275.0));
    }

    #[test]
    fn hotm_increments_start_at_level_one() {
        // HOTM: level 1 at 0 XP, level 2 at 3000.
        let t = XpTable::from_increments(&[0.0, 3000.0, 9000.0]);
        assert_eq!(t.progress(0.0).level, 1);
        assert_eq!(t.progress(2999.0).level, 1);
        assert_eq!(t.progress(3000.0).level, 2);
        assert_eq!(t.progress(12000.0).level, 3);
    }

    #[test]
    fn pets() {
        let levels = vec![100.0; 200];
        let t = pet_table(&levels, 20, 100);
        assert_eq!(t.progress(0.0).level, 1);
        assert_eq!(t.progress(100.0).level, 2);
        assert_eq!(t.max_level(), 100);
    }
}
