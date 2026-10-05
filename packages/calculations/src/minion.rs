//! Minion throughput. "Time Between Actions" comes from NEU minion item lore.
//! Most minions alternate between placing/spawning and harvesting, so only every
//! second action produces items (`actions_per_harvest` = 2).

pub fn harvests_per_day(seconds_between_actions: f64, speed_bonus_pct: f64, actions_per_harvest: f64) -> f64 {
    if !seconds_between_actions.is_finite() || seconds_between_actions <= 0.0 {
        return 0.0;
    }
    let bonus = if speed_bonus_pct.is_finite() { speed_bonus_pct.max(-99.0) } else { 0.0 };
    let effective = seconds_between_actions / (1.0 + bonus / 100.0);
    let per_harvest = if actions_per_harvest.is_finite() && actions_per_harvest >= 1.0 { actions_per_harvest } else { 2.0 };
    86_400.0 / effective / per_harvest
}

pub fn items_per_day(seconds_between_actions: f64, speed_bonus_pct: f64, drops_per_harvest: f64) -> f64 {
    let d = if drops_per_harvest.is_finite() { drops_per_harvest.max(0.0) } else { 0.0 };
    harvests_per_day(seconds_between_actions, speed_bonus_pct, 2.0) * d
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cobblestone() {
        // 14s between actions, no fuel: 86400/14/2 = 3085.7 harvests
        let h = harvests_per_day(14.0, 0.0, 2.0);
        assert!((h - 3085.714).abs() < 0.01);
        assert!((items_per_day(14.0, 25.0, 1.0) - 3857.14).abs() < 0.01);
        assert_eq!(harvests_per_day(0.0, 0.0, 2.0), 0.0);
    }
}
