use serde::{Deserialize, Serialize};
use ts_rs::TS;

/// A value that may not be obtainable from the Hypixel API (API toggle disabled,
/// field never populated, endpoint unavailable...). SkyPilot never guesses these.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "status", rename_all = "camelCase")]
#[ts(export)]
pub enum Known<T> {
    Known { value: T },
    Unknown { reason: String },
}

impl<T> Known<T> {
    pub fn known(value: T) -> Self {
        Known::Known { value }
    }
    pub fn unknown(reason: impl Into<String>) -> Self {
        Known::Unknown { reason: reason.into() }
    }
    pub fn as_ref(&self) -> Option<&T> {
        match self {
            Known::Known { value } => Some(value),
            Known::Unknown { .. } => None,
        }
    }
    pub fn into_option(self) -> Option<T> {
        match self {
            Known::Known { value } => Some(value),
            Known::Unknown { .. } => None,
        }
    }
    pub fn is_known(&self) -> bool {
        matches!(self, Known::Known { .. })
    }
    pub fn map<U>(self, f: impl FnOnce(T) -> U) -> Known<U> {
        match self {
            Known::Known { value } => Known::Known { value: f(value) },
            Known::Unknown { reason } => Known::Unknown { reason },
        }
    }
}

impl<T> From<Option<T>> for Known<T> {
    fn from(value: Option<T>) -> Self {
        match value {
            Some(v) => Known::known(v),
            None => Known::unknown("Not available from API"),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum ConfidenceLevel {
    Low,
    Medium,
    High,
}

/// Confidence attached to every recommendation and every market number.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Confidence {
    pub level: ConfidenceLevel,
    /// 0.0 ..= 1.0
    pub score: f64,
    /// Human readable reasons, e.g. "Insufficient sales data".
    pub reasons: Vec<String>,
}

impl Confidence {
    pub fn from_score(score: f64, reasons: Vec<String>) -> Self {
        let score = if score.is_finite() { score.clamp(0.0, 1.0) } else { 0.0 };
        let level = if score >= 0.8 {
            ConfidenceLevel::High
        } else if score >= 0.55 {
            ConfidenceLevel::Medium
        } else {
            ConfidenceLevel::Low
        };
        Confidence { level, score, reasons }
    }
}

/// How fresh a piece of data is. `stale` is computed against the source's expected cadence.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Freshness {
    pub source: String,
    /// Unix epoch milliseconds of the upstream `lastUpdated` (or fetch time).
    #[ts(type = "number")]
    pub updated_at_ms: i64,
    #[ts(type = "number")]
    pub age_seconds: i64,
    pub stale: bool,
}

impl Freshness {
    pub fn compute(source: impl Into<String>, updated_at_ms: i64, now_ms: i64, stale_after_seconds: i64) -> Self {
        let age_seconds = ((now_ms - updated_at_ms) / 1000).max(0);
        Freshness {
            source: source.into(),
            updated_at_ms,
            age_seconds,
            stale: updated_at_ms <= 0 || age_seconds > stale_after_seconds,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum RiskLevel {
    VeryLow,
    Low,
    Medium,
    High,
    VeryHigh,
}

impl RiskLevel {
    /// Numeric weight used by scoring strategies (1 = very low risk).
    pub fn weight(self) -> f64 {
        match self {
            RiskLevel::VeryLow => 1.0,
            RiskLevel::Low => 1.25,
            RiskLevel::Medium => 1.75,
            RiskLevel::High => 2.75,
            RiskLevel::VeryHigh => 4.5,
        }
    }
}

/// Where a fact used in a recommendation came from. Shown in the UI so users can
/// tell game rules apart from SkyPilot heuristics.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum DataSource {
    /// Read from the player's Hypixel API profile.
    HypixelProfile,
    /// Hypixel `resources` endpoints (items, skills, collections...).
    HypixelResources,
    /// NotEnoughUpdates-REPO community data (recipes, constants, drop tables).
    NeuRepo,
    /// SkyPilot curated registry (data/registry) - game facts with documented sources.
    SkyPilotRegistry,
    /// SkyPilot heuristic / recommendation guidance (not a game rule).
    SkyPilotHeuristic,
    /// Live market data (Auction House / Bazaar).
    Market,
    /// Data reported by the SkyPilot client mod from the game client.
    ClientMod,
}

/// A labelled before/after metric for cards like "MP 437 → 481".
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Metric {
    pub label: String,
    pub before: Option<f64>,
    pub after: Option<f64>,
    pub value: Option<f64>,
    pub unit: Option<String>,
    pub text: Option<String>,
}

/// -0.0 (e.g. a sum of negated zeros) would render as "-0".
fn no_neg_zero(v: f64) -> f64 {
    if v == 0.0 { 0.0 } else { v }
}

impl Metric {
    pub fn change(label: impl Into<String>, before: f64, after: f64, unit: Option<&str>) -> Self {
        Metric {
            label: label.into(),
            before: Some(no_neg_zero(before)),
            after: Some(no_neg_zero(after)),
            value: None,
            unit: unit.map(str::to_string),
            text: None,
        }
    }
    pub fn value(label: impl Into<String>, value: f64, unit: Option<&str>) -> Self {
        Metric {
            label: label.into(),
            before: None,
            after: None,
            value: Some(no_neg_zero(value)),
            unit: unit.map(str::to_string),
            text: None,
        }
    }
    pub fn text(label: impl Into<String>, text: impl Into<String>) -> Self {
        Metric {
            label: label.into(),
            before: None,
            after: None,
            value: None,
            unit: None,
            text: Some(text.into()),
        }
    }
}

/// Range estimate (e.g. expected sell time 7-18 minutes).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Range {
    pub low: f64,
    pub high: f64,
}

/// Standard error body returned by the API.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ApiError {
    pub code: String,
    pub message: String,
    /// When the upstream (Hypixel) is unavailable, the age of the last valid snapshot we can serve.
    #[ts(type = "number | null")]
    pub last_valid_snapshot_age_seconds: Option<i64>,
}
