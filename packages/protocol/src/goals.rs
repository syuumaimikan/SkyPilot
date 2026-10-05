use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::common::{Confidence, DataSource, Freshness, Metric};
use crate::profile::PlayerAnalysis;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Default, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum PlayStyle {
    /// 最短
    #[default]
    Fastest,
    /// 安全重視
    Safe,
    /// 金策重視
    MoneyFocus,
    /// 戦闘中心
    Combat,
    /// Farming中心
    Farming,
    /// Mining中心
    Mining,
}

/// A structured goal parsed from natural language (or built by the UI).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "type", rename_all = "camelCase")]
#[ts(export)]
pub enum GoalKind {
    AcquireItem { item_id: String },
    SkillLevel { skill: String, level: u32 },
    SlayerLevel { slayer: String, level: u32 },
    CatacombsLevel { level: u32 },
    ClassLevel { class: String, level: u32 },
    HotmLevel { level: u32 },
    HotfLevel { level: u32 },
    MagicalPower { target: u32 },
    Coins { amount: f64 },
    Networth { amount: f64 },
    SkyblockLevel { level: u32 },
    FloorCompletion { floor: u8, master: bool },
    KuudraTier { tier: String },
    GardenLevel { level: u32 },
    PowderTarget { powder: String, amount: f64 },
    CollectionTier { collection: String, tier: u32 },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GoalSpec {
    pub kind: GoalKind,
    /// Display title, e.g. "Hyperion".
    pub title: String,
    /// Raw user text it was parsed from.
    pub raw: String,
    /// Parsing confidence (fuzzy item match quality etc.).
    pub match_confidence: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GoalParseResult {
    pub goals: Vec<GoalSpec>,
    /// Phrases that could not be mapped to a goal.
    pub unmatched: Vec<String>,
    /// Alternatives when the item match was ambiguous.
    pub suggestions: Vec<GoalSpec>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum NodeStatus {
    Satisfied,
    InProgress,
    NotStarted,
    Blocked,
    Unknown,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum NodeKind {
    Goal,
    Item,
    Requirement,
    Readiness,
    Money,
    Acquisition,
    Crafting,
    Skill,
    Slayer,
    Dungeon,
    MagicalPower,
    Hotm,
    Powder,
    Gear,
    Other,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GoalNode {
    pub id: String,
    pub kind: NodeKind,
    pub title: String,
    pub detail: Option<String>,
    pub status: NodeStatus,
    /// 0..=1
    pub progress: f64,
    pub current: Option<f64>,
    pub target: Option<f64>,
    pub unit: Option<String>,
    /// true = hard game requirement, false = SkyPilot recommendation.
    pub hard_requirement: bool,
    pub source: DataSource,
    pub children: Vec<String>,
    pub estimated_cost: Option<f64>,
    pub estimated_hours: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GoalGraph {
    pub root: String,
    pub nodes: Vec<GoalNode>,
    /// Node ids removed because the player already satisfies them.
    pub satisfied_pruned: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct RoadmapStep {
    pub index: u32,
    pub node_id: String,
    pub title: String,
    pub detail: Option<String>,
    pub status: NodeStatus,
    pub progress: f64,
    pub current: Option<f64>,
    pub target: Option<f64>,
    pub unit: Option<String>,
    pub estimated_cost: Option<f64>,
    pub estimated_hours: Option<f64>,
    pub hard_requirement: bool,
    pub source: DataSource,
    /// Recommended action ids that advance this step.
    pub action_ids: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Roadmap {
    pub goal_id: String,
    pub title: String,
    /// 0..=1 weighted by step weights.
    pub overall_progress: f64,
    pub remaining_cost: Option<f64>,
    pub remaining_hours: Option<f64>,
    pub steps: Vec<RoadmapStep>,
    pub current_step: Option<u32>,
}

/// Raw factors of the action optimizer (see recommendation-engine `ScoringStrategy`).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ActionScore {
    /// 0..=1 share of the remaining goal this action completes.
    pub goal_impact: f64,
    pub cost: f64,
    pub time_hours: f64,
    /// 1 (trivial) ..= 5 (very hard)
    pub difficulty: f64,
    /// 1 (none) ..= 5 (very risky)
    pub risk: f64,
    pub coin_return_per_hour: f64,
    pub xp_return: f64,
    /// 0..=1
    pub long_term_value: f64,
    /// 0..=1
    pub unlock_value: f64,
    pub confidence: f64,
    /// Final priority produced by the active strategy.
    pub priority: f64,
    pub strategy: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PurchaseCandidate {
    pub item_id: String,
    pub name: String,
    pub price: Option<f64>,
    /// Price source: "BIN", "Bazaar", "Craft", "NPC".
    pub price_source: Option<String>,
    pub gain: Option<f64>,
    pub gain_unit: Option<String>,
    pub coins_per_unit: Option<f64>,
    pub max_buy_price: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct RecommendedAction {
    pub id: String,
    pub provider: String,
    pub category: String,
    pub title: String,
    pub steps: Vec<String>,
    pub metrics: Vec<Metric>,
    pub estimated_cost: Option<f64>,
    pub estimated_hours: Option<f64>,
    /// Plain explanation of why this is recommended (deterministic, no LLM).
    pub why: String,
    pub score: ActionScore,
    pub confidence: Confidence,
    pub goal_ids: Vec<String>,
    pub purchases: Vec<PurchaseCandidate>,
    /// Web route with more details, e.g. "/accessories".
    pub link: Option<String>,
    pub sources: Vec<DataSource>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GoalResult {
    pub id: String,
    pub spec: GoalSpec,
    pub priority: GoalPriority,
    pub graph: GoalGraph,
    pub roadmap: Roadmap,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum GoalPriority {
    #[default]
    Primary,
    Secondary,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct AnalyzeRequest {
    pub username: String,
    pub goal: String,
    #[serde(default)]
    pub secondary_goals: Vec<String>,
    #[serde(default)]
    pub play_style: PlayStyle,
    pub profile_id: Option<String>,
    /// Optional budget limit in coins for purchase recommendations.
    pub budget: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct AnalyzeResponse {
    pub analysis: PlayerAnalysis,
    pub parse: GoalParseResult,
    pub goals: Vec<GoalResult>,
    /// "WHAT SHOULD I DO NOW?" ordered list.
    pub actions: Vec<RecommendedAction>,
    pub play_style: PlayStyle,
    pub market: Option<Freshness>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SavedGoal {
    pub id: String,
    pub text: String,
    pub priority: GoalPriority,
    #[ts(type = "number")]
    pub created_at_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct WorkspaceState {
    pub username: Option<String>,
    pub profile_id: Option<String>,
    pub play_style: PlayStyle,
    pub goals: Vec<SavedGoal>,
    pub budget: Option<f64>,
    /// Last analysis, if any (served from storage, possibly stale).
    pub last_analysis: Option<AnalyzeResponse>,
    #[ts(type = "number | null")]
    pub last_analysis_at_ms: Option<i64>,
}
