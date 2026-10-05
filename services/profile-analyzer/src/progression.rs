//! Progression Score: per-area 0-100 scores from the curated model. The game stage is
//! derived from the weighted overall score, never from networth alone.

use std::collections::BTreeMap;

use skypilot_data::Registry;
use skypilot_protocol::profile::{AreaScore, GameStage, PlayerAnalysis, ProgressionScores};

/// Piecewise-linear interpolation over `[value, score]` anchors.
pub fn interpolate(anchors: &[[f64; 2]], x: f64) -> f64 {
    if anchors.is_empty() || !x.is_finite() {
        return 0.0;
    }
    if x <= anchors[0][0] {
        return anchors[0][1];
    }
    for w in anchors.windows(2) {
        let (a, b) = (w[0], w[1]);
        if x <= b[0] {
            let t = if b[0] > a[0] { (x - a[0]) / (b[0] - a[0]) } else { 1.0 };
            return a[1] + t * (b[1] - a[1]);
        }
    }
    anchors.last().map(|a| a[1]).unwrap_or(0.0)
}

/// Metric values available for scoring.
pub fn metrics(a: &PlayerAnalysis) -> BTreeMap<String, f64> {
    let mut m = BTreeMap::new();
    if let Some(skills) = a.skills.as_ref() {
        for s in skills {
            m.insert(format!("skill:{}", s.id), s.level_with_progress);
        }
    }
    if let Some(d) = a.dungeons.as_ref() {
        m.insert("catacombs".into(), d.catacombs.level_with_progress);
        m.insert("class_average".into(), d.class_average);
    }
    if let Some(mi) = a.mining.as_ref() {
        if let Some(h) = mi.hotm.as_ref() {
            m.insert("hotm".into(), h.level_with_progress);
        }
    }
    if let Some(g) = a.garden.as_ref() {
        if let Some(l) = g.garden_level.as_ref() {
            m.insert("garden".into(), l.level_with_progress);
        }
    }
    if let Some(s) = a.slayers.as_ref() {
        m.insert("slayer_xp_total".into(), s.iter().map(|x| x.level.xp).sum());
    }
    if let Some(acc) = a.accessories.as_ref() {
        m.insert("magical_power".into(), acc.magical_power as f64);
    }
    if let Some(n) = a.networth.as_ref() {
        m.insert("networth".into(), n.total);
    }
    if let Some(p) = a.pets.as_ref() {
        // pet score approximation: unique pet types weighted by rarity (Hypixel's own
        // highest_pet_score is used when present via the analyzer)
        let rarity = |t: &str| match t {
            "MYTHIC" => 6.0,
            "LEGENDARY" => 5.0,
            "EPIC" => 4.0,
            "RARE" => 3.0,
            "UNCOMMON" => 2.0,
            _ => 1.0,
        };
        let mut best: BTreeMap<&str, f64> = BTreeMap::new();
        for pet in p {
            let e = best.entry(pet.pet_type.as_str()).or_insert(0.0);
            *e = e.max(rarity(&pet.tier) + if pet.level >= 100 { 1.0 } else { 0.0 });
        }
        m.entry("pet_score".into()).or_insert(best.values().sum());
    }
    if let Some(r) = a.rift.as_ref() {
        m.insert("rift_progress".into(), r.enigma_souls as f64 * 0.5 + r.timecharms.len() as f64 * 3.0 + r.completed_areas.len() as f64 * 2.0 + if r.montezuma_unlocked { 5.0 } else { 0.0 });
    }
    if let Some(k) = a.kuudra.as_ref() {
        let order = ["none", "hot", "burning", "fiery", "infernal"];
        let t = k.highest_tier_completed.as_deref().and_then(|h| order.iter().position(|o| *o == h)).map(|i| i as f64 + 1.0).unwrap_or(0.0);
        m.insert("kuudra_tier".into(), t);
    }
    m
}

pub fn score(a: &PlayerAnalysis, registry: &Registry, pet_score_override: Option<f64>) -> ProgressionScores {
    let cfg = &registry.curated.progression;
    let mut m = metrics(a);
    if let Some(p) = pet_score_override {
        m.insert("pet_score".into(), p);
    }
    let mut areas = Vec::new();
    for area in &cfg.areas {
        let mut total = 0.0;
        let mut weight = 0.0;
        let mut basis = Vec::new();
        for metric in &area.metrics {
            if let Some(v) = m.get(&metric.metric) {
                let s = interpolate(&metric.anchors, *v);
                total += s * metric.weight;
                weight += metric.weight;
                basis.push(format!("{} = {}", metric.metric, format_num(*v)));
            }
        }
        let known = weight > 0.0;
        areas.push(AreaScore { id: area.id.clone(), name: area.name.clone(), score: if known { (total / weight).round() } else { 0.0 }, basis, known });
    }
    let (sum, wsum) = cfg.areas.iter().zip(&areas).filter(|(_, s)| s.known).fold((0.0, 0.0), |acc, (c, s)| (acc.0 + s.score * c.weight, acc.1 + c.weight));
    let overall = if wsum > 0.0 { sum / wsum } else { 0.0 };
    let st = &cfg.stages;
    let stage = if overall >= st.end_game {
        GameStage::EndGame
    } else if overall >= st.late_game {
        GameStage::LateGame
    } else if overall >= st.mid_game {
        GameStage::MidGame
    } else {
        GameStage::EarlyGame
    };
    let known: Vec<&AreaScore> = areas.iter().filter(|a| a.known).collect();
    let mean = if known.is_empty() { 0.0 } else { known.iter().map(|a| a.score).sum::<f64>() / known.len() as f64 };
    let mut sorted = known.clone();
    sorted.sort_by(|a, b| b.score.total_cmp(&a.score));
    // Strong/weak are judged both absolutely and relative to the player's own average,
    // so an end-game player still sees their relatively weakest areas.
    let strong: Vec<String> = sorted.iter().filter(|a| a.score >= st.strong_threshold || a.score >= mean + 15.0).take(4).map(|a| a.name.clone()).collect();
    let weak: Vec<String> = sorted
        .iter()
        .rev()
        .filter(|a| (a.score <= st.weak_threshold || a.score <= mean - 15.0) && !strong.contains(&a.name))
        .take(4)
        .map(|a| a.name.clone())
        .collect();
    ProgressionScores { areas, overall: overall.round(), stage, strong_areas: strong, weak_areas: weak }
}

fn format_num(v: f64) -> String {
    if v.abs() >= 1_000_000.0 {
        format!("{:.1}M", v / 1_000_000.0)
    } else if v.abs() >= 1000.0 {
        format!("{:.1}k", v / 1000.0)
    } else {
        format!("{:.1}", v)
    }
}

#[cfg(test)]
mod tests {
    use super::interpolate;

    #[test]
    fn anchors() {
        let a = [[0.0, 0.0], [10.0, 50.0], [20.0, 100.0]];
        assert_eq!(interpolate(&a, -5.0), 0.0);
        assert_eq!(interpolate(&a, 5.0), 25.0);
        assert_eq!(interpolate(&a, 15.0), 75.0);
        assert_eq!(interpolate(&a, 99.0), 100.0);
        assert_eq!(interpolate(&a, f64::NAN), 0.0);
    }
}
