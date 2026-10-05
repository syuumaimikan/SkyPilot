//! Action Optimizer. Every candidate action carries raw factors; a pluggable
//! [`ScoringStrategy`] turns them into a priority. The default is
//! `goalImpact × longTermValue × confidence ÷ (time + cost + difficulty + risk)`.

use std::collections::BTreeMap;

use skypilot_protocol::common::{Confidence, DataSource, Metric};
use skypilot_protocol::goals::{ActionScore, GoalPriority, PlayStyle, PurchaseCandidate, RecommendedAction};

#[derive(Debug, Clone)]
pub struct CandidateAction {
    pub id: String,
    pub provider: &'static str,
    pub category: String,
    pub title: String,
    pub steps: Vec<String>,
    pub metrics: Vec<Metric>,
    pub cost: f64,
    pub hours: f64,
    /// 1 (trivial) ..= 5
    pub difficulty: f64,
    /// 1 (none) ..= 5
    pub risk: f64,
    pub coin_return_per_hour: f64,
    pub xp_return: f64,
    pub long_term_value: f64,
    pub unlock_value: f64,
    pub confidence: f64,
    pub confidence_reasons: Vec<String>,
    /// goal id -> share of that goal's remaining work completed (0..=1)
    pub goal_impacts: BTreeMap<String, f64>,
    pub purchases: Vec<PurchaseCandidate>,
    pub link: Option<String>,
    pub sources: Vec<DataSource>,
    pub why: String,
}

impl CandidateAction {
    pub fn new(id: impl Into<String>, provider: &'static str, category: impl Into<String>, title: impl Into<String>) -> Self {
        CandidateAction {
            id: id.into(),
            provider,
            category: category.into(),
            title: title.into(),
            steps: Vec::new(),
            metrics: Vec::new(),
            cost: 0.0,
            hours: 0.25,
            difficulty: 1.0,
            risk: 1.0,
            coin_return_per_hour: 0.0,
            xp_return: 0.0,
            long_term_value: 0.3,
            unlock_value: 0.0,
            confidence: 0.7,
            confidence_reasons: Vec::new(),
            goal_impacts: BTreeMap::new(),
            purchases: Vec::new(),
            link: None,
            sources: Vec::new(),
            why: String::new(),
        }
    }
}

pub struct ScoringInput<'a> {
    pub style: PlayStyle,
    pub liquid_coins: f64,
    pub goal_priorities: &'a BTreeMap<String, GoalPriority>,
    /// Coins still missing across goals' money nodes (boosts money actions).
    pub coin_gap: f64,
}

pub trait ScoringStrategy: Send + Sync {
    fn name(&self) -> &'static str;
    fn priority(&self, a: &CandidateAction, input: &ScoringInput) -> f64;
}

pub struct DefaultStrategy;

impl ScoringStrategy for DefaultStrategy {
    fn name(&self) -> &'static str {
        "goalImpact*longTerm*confidence/(time+cost+difficulty+risk)"
    }

    fn priority(&self, a: &CandidateAction, s: &ScoringInput) -> f64 {
        // Multi-goal synergy: impacts on several goals add up (secondary goals at half weight).
        let mut impact: f64 = a
            .goal_impacts
            .iter()
            .map(|(g, v)| v * if s.goal_priorities.get(g) == Some(&GoalPriority::Secondary) { 0.5 } else { 1.0 })
            .sum();
        // Money actions advance every goal that is blocked on coins.
        if a.coin_return_per_hour > 0.0 && s.coin_gap > 0.0 {
            impact += (a.coin_return_per_hour * a.hours.max(1.0) / s.coin_gap).min(1.0) * 0.5;
        }
        impact = impact.max(0.02);
        let (mut w_time, mut w_cost, mut w_risk) = (1.0, 1.0, 1.0);
        let mut bonus = 1.0;
        match s.style {
            PlayStyle::Fastest => w_time = 1.6,
            PlayStyle::Safe => w_risk = 2.2,
            PlayStyle::MoneyFocus => {
                w_cost = 1.4;
                if a.coin_return_per_hour > 0.0 {
                    bonus = 1.5;
                }
            }
            PlayStyle::Combat => {
                if matches!(a.category.as_str(), "Slayer" | "Dungeons" | "Combat" | "Kuudra") {
                    bonus = 1.4;
                }
            }
            PlayStyle::Farming => {
                if a.category == "Farming" {
                    bonus = 1.4;
                }
            }
            PlayStyle::Mining => {
                if a.category == "Mining" {
                    bonus = 1.4;
                }
            }
        }
        let time = w_time * (a.hours.max(0.05) / 2.0);
        let wealth = s.liquid_coins.max(1_000_000.0);
        let mut cost = w_cost * (a.cost.max(0.0) / wealth) * 2.0;
        if a.cost > s.liquid_coins * 1.05 {
            cost += 3.0; // cannot afford now
        }
        let difficulty = (a.difficulty.clamp(1.0, 5.0) - 1.0) / 2.0;
        let risk = w_risk * (a.risk.clamp(1.0, 5.0) - 1.0) / 2.0;
        let long_term = 0.5 + a.long_term_value.clamp(0.0, 1.0) + 0.5 * a.unlock_value.clamp(0.0, 1.0);
        let p = impact * long_term * a.confidence.clamp(0.05, 1.0) * bonus / (time + cost + difficulty + risk + 0.25);
        if p.is_finite() { p } else { 0.0 }
    }
}

pub fn finalize(actions: Vec<CandidateAction>, strategy: &dyn ScoringStrategy, input: &ScoringInput, limit: usize) -> Vec<RecommendedAction> {
    let mut scored: Vec<(f64, CandidateAction)> = actions.into_iter().map(|a| (strategy.priority(&a, input), a)).collect();
    scored.sort_by(|a, b| b.0.total_cmp(&a.0));
    let mut seen = std::collections::HashSet::new();
    scored
        .into_iter()
        .filter(|(_, a)| seen.insert(a.id.clone()))
        .take(limit)
        .map(|(p, a)| RecommendedAction {
            id: a.id,
            provider: a.provider.to_string(),
            category: a.category,
            title: a.title,
            steps: a.steps,
            metrics: a.metrics,
            estimated_cost: (a.cost > 0.0).then_some(a.cost),
            estimated_hours: Some(a.hours),
            why: a.why,
            score: ActionScore {
                goal_impact: a.goal_impacts.values().sum::<f64>().min(1.0),
                cost: a.cost,
                time_hours: a.hours,
                difficulty: a.difficulty,
                risk: a.risk,
                coin_return_per_hour: a.coin_return_per_hour,
                xp_return: a.xp_return,
                long_term_value: a.long_term_value,
                unlock_value: a.unlock_value,
                confidence: a.confidence,
                priority: p,
                strategy: strategy.name().to_string(),
            },
            confidence: Confidence::from_score(a.confidence, a.confidence_reasons),
            goal_ids: a.goal_impacts.keys().cloned().collect(),
            purchases: a.purchases,
            link: a.link,
            sources: a.sources,
        })
        .collect()
}
