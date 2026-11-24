//! Profile completion in %: what is done against a maximum that comes from data — level caps
//! and collection tiers from Hypixel resources, minion tiers / museum items / fairy souls from
//! NEU-REPO constants. A part without a known maximum is reported as unknown, never estimated.

use skypilot_data::Registry;
use skypilot_protocol::common::{DataSource, Known};
use skypilot_protocol::profile::{CompletionPart, CompletionReport, LevelInfo, PlayerAnalysis, UnknownCompletion};

fn round1(v: f64) -> f64 {
    (v * 10.0).round() / 10.0
}

fn part(id: &str, name: &str, done: f64, total: f64, basis: String, source: DataSource) -> Option<CompletionPart> {
    if !(total > 0.0) || !done.is_finite() {
        return None;
    }
    let done = done.clamp(0.0, total);
    Some(CompletionPart { id: id.into(), name: name.into(), done: round1(done), total, percent: round1(done / total * 100.0), basis, source })
}

/// Sum of fractional levels (capped at each max) against the sum of max levels.
fn levels<'a>(list: impl IntoIterator<Item = &'a LevelInfo>) -> (f64, f64) {
    list.into_iter()
        .filter(|l| l.max_level > 0)
        .fold((0.0, 0.0), |(d, t), l| (d + l.level_with_progress.min(l.max_level as f64).max(0.0), t + l.max_level as f64))
}

fn fmt(v: f64) -> String {
    if (v - v.round()).abs() < 0.05 { format!("{}", v.round() as i64) } else { format!("{v:.1}") }
}

pub fn compute(a: &PlayerAnalysis, registry: &Registry) -> CompletionReport {
    let mut parts = Vec::new();
    let mut unknown = Vec::new();
    let mut missing = |id: &str, name: &str, reason: &str| unknown.push(UnknownCompletion { id: id.into(), name: name.into(), reason: reason.into() });

    match &a.skills {
        Known::Known { value } => {
            let (d, t) = levels(value);
            parts.extend(part("skills", "Skills", d, t, format!("skill levels {} / {}", fmt(d), fmt(t)), DataSource::HypixelProfile));
        }
        Known::Unknown { reason } => missing("skills", "Skills", reason),
    }
    match &a.dungeons {
        Known::Known { value } => {
            let (d, t) = levels([&value.catacombs]);
            parts.extend(part("catacombs", "Catacombs", d, t, format!("level {} / {}", fmt(d), fmt(t)), DataSource::HypixelProfile));
            let (d, t) = levels(&value.classes);
            parts.extend(part("classes", "Dungeon classes", d, t, format!("class levels {} / {}", fmt(d), fmt(t)), DataSource::HypixelProfile));
        }
        Known::Unknown { reason } => {
            missing("catacombs", "Catacombs", reason);
            missing("classes", "Dungeon classes", reason);
        }
    }
    match &a.slayers {
        Known::Known { value } => {
            let (d, t) = levels(value.iter().map(|s| &s.level));
            parts.extend(part("slayers", "Slayers", d, t, format!("slayer levels {} / {}", fmt(d), fmt(t)), DataSource::HypixelProfile));
        }
        Known::Unknown { reason } => missing("slayers", "Slayers", reason),
    }
    match a.mining.as_ref().map(|m| &m.hotm) {
        Some(Known::Known { value }) => {
            let (d, t) = levels([value]);
            parts.extend(part("hotm", "Heart of the Mountain", d, t, format!("tier {} / {}", fmt(d), fmt(t)), DataSource::HypixelProfile));
        }
        Some(Known::Unknown { reason }) => missing("hotm", "Heart of the Mountain", reason),
        None => missing("hotm", "Heart of the Mountain", "Mining data not available from API"),
    }
    match a.garden.as_ref().map(|g| &g.garden_level) {
        Some(Known::Known { value }) => {
            let (d, t) = levels([value]);
            parts.extend(part("garden", "Garden", d, t, format!("level {} / {}", fmt(d), fmt(t)), DataSource::HypixelProfile));
        }
        Some(Known::Unknown { reason }) => missing("garden", "Garden", reason),
        None => missing("garden", "Garden", "Garden API unavailable"),
    }
    match &a.collections {
        Known::Known { value } => {
            let d: f64 = value.iter().map(|c| c.tier.min(c.max_tier) as f64).sum();
            let t: f64 = value.iter().map(|c| c.max_tier as f64).sum();
            let maxed = value.iter().filter(|c| c.max_tier > 0 && c.tier >= c.max_tier).count();
            parts.extend(part("collections", "Collections", d, t, format!("tiers {} / {} · {maxed} maxed", fmt(d), fmt(t)), DataSource::HypixelResources));
        }
        Known::Unknown { reason } => missing("collections", "Collections", reason),
    }
    let minion_total: u32 = registry.neu.minion_max_tiers.values().sum();
    match (&a.minions, minion_total) {
        (_, 0) => missing("minions", "Minion tiers", "Minion tier list not loaded (NEU-REPO)"),
        (Known::Known { value }, t) => {
            parts.extend(part("minions", "Minion tiers", value.unique_crafted as f64, t as f64, format!("crafted {} / {t}", value.unique_crafted), DataSource::NeuRepo));
        }
        (Known::Unknown { reason }, _) => missing("minions", "Minion tiers", reason),
    }
    let museum_total: usize = registry.neu.museum_items.values().map(Vec::len).sum();
    match (&a.museum, museum_total) {
        (_, 0) => missing("museum", "Museum", "Museum item list not loaded (NEU-REPO)"),
        (Known::Known { value }, t) => {
            let listed: std::collections::HashSet<&str> = registry.neu.museum_items.values().flatten().map(String::as_str).collect();
            let d = value.donated_items.iter().filter(|i| listed.contains(i.as_str())).count();
            parts.extend(part("museum", "Museum", d as f64, t as f64, format!("donated {d} / {t} (without special items)"), DataSource::NeuRepo));
        }
        (Known::Unknown { reason }, _) => missing("museum", "Museum", reason),
    }
    match (a.fairy_souls, registry.neu.fairy_souls_max) {
        (Some(d), Some(t)) => parts.extend(part("fairy_souls", "Fairy souls", d as f64, t as f64, format!("found {d} / {t}"), DataSource::NeuRepo)),
        (None, _) => missing("fairy_souls", "Fairy souls", "Not available from API"),
        (_, None) => missing("fairy_souls", "Fairy souls", "Fairy soul total not loaded (NEU-REPO)"),
    }

    let overall = if parts.is_empty() { None } else { Some(round1(parts.iter().map(|p| p.percent).sum::<f64>() / parts.len() as f64)) };
    CompletionReport { parts, overall, unknown, analyzed_at_ms: None, profile_name: Some(a.cute_name.clone()) }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lvl(id: &str, l: f64, max: u32) -> LevelInfo {
        LevelInfo { id: id.into(), name: id.into(), level: l as u32, max_level: max, xp: 0.0, progress: l.fract(), xp_to_next: None, level_with_progress: l }
    }

    #[test]
    fn levels_are_capped_and_zero_max_is_ignored() {
        let (d, t) = levels(&[lvl("a", 25.5, 50), lvl("b", 70.0, 60), lvl("c", 3.0, 0)]);
        assert_eq!(d, 85.5);
        assert_eq!(t, 110.0);
    }

    #[test]
    fn parts_need_a_known_total() {
        assert!(part("x", "X", 5.0, 0.0, String::new(), DataSource::NeuRepo).is_none());
        let p = part("x", "X", 15.0, 10.0, String::new(), DataSource::NeuRepo).unwrap();
        assert_eq!(p.percent, 100.0);
        assert_eq!(p.done, 10.0);
        assert_eq!(part("x", "X", 1.0, 3.0, String::new(), DataSource::NeuRepo).unwrap().percent, 33.3);
    }
}
