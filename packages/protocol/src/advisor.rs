//! Advisor reports. Every content provider (mining, slayer, farming, ...) returns an
//! [`AdvisorReport`] built from generic sections so the web can render new providers
//! without code changes, plus typed payloads for the interactive planners.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::common::{Confidence, DataSource, Freshness, Metric, Range, RiskLevel};
use crate::goals::RecommendedAction;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "camelCase")]
#[ts(export)]
pub enum Cell {
    Text { text: String },
    Number { value: f64, unit: Option<String>, digits: Option<u8> },
    Coins { value: f64 },
    Percent { value: f64 },
    Duration { hours: f64 },
    Progress { value: f64, label: Option<String> },
    Badge { text: String, tone: Tone },
    Item { item_id: String, name: String, rarity: Option<String> },
    Unknown { reason: String },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub enum Tone {
    Neutral,
    Good,
    Warn,
    Bad,
    Info,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Table {
    pub columns: Vec<String>,
    pub rows: Vec<Vec<Cell>>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct AdvisorSection {
    pub id: String,
    pub title: String,
    pub description: Option<String>,
    pub metrics: Vec<Metric>,
    pub table: Option<Table>,
    pub bullets: Vec<String>,
    pub notes: Vec<String>,
    pub sources: Vec<DataSource>,
}

impl AdvisorSection {
    pub fn new(id: impl Into<String>, title: impl Into<String>) -> Self {
        AdvisorSection {
            id: id.into(),
            title: title.into(),
            description: None,
            metrics: Vec::new(),
            table: None,
            bullets: Vec::new(),
            notes: Vec::new(),
            sources: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct AdvisorReport {
    pub domain: String,
    pub title: String,
    pub summary: Vec<Metric>,
    pub sections: Vec<AdvisorSection>,
    pub actions: Vec<RecommendedAction>,
    pub unknowns: Vec<String>,
    pub freshness: Vec<Freshness>,
}

// ---------------------------------------------------------------- Mining

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct HotmUpgrade {
    pub perk_id: String,
    pub name: String,
    pub from_level: u32,
    pub to_level: u32,
    pub powder: String,
    pub cost: f64,
    pub affordable_now: bool,
    pub reason: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct HotmPlan {
    pub preset: String,
    pub preset_name: String,
    pub hotm_level: u32,
    pub upgrades: Vec<HotmUpgrade>,
    /// Powder type -> total still required to finish the preset.
    pub required_powder: BTreeMap<String, f64>,
    /// Powder type -> currently available.
    pub available_powder: BTreeMap<String, f64>,
    pub blocked_by_hotm_level: Vec<String>,
    pub presets: Vec<PresetInfo>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PresetInfo {
    pub id: String,
    pub name: String,
    pub description: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PowderPlan {
    pub powder: String,
    pub current: f64,
    pub target: f64,
    pub remaining: f64,
    pub method: String,
    pub per_hour: Option<f64>,
    pub hours_remaining: Option<f64>,
    pub assumptions: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MiningTargetRate {
    pub target_id: String,
    pub name: String,
    pub block_strength: f64,
    pub ticks_per_block: f64,
    pub blocks_per_hour: f64,
    pub drops_per_hour: f64,
    pub drop_item: String,
    pub npc_coins_per_hour: Option<f64>,
    pub bazaar_coins_per_hour: Option<f64>,
    pub fuel_and_consumables_per_hour: f64,
    pub effective_coins_per_hour: Option<f64>,
    pub requirements: Vec<String>,
    pub requirements_met: bool,
    pub assumptions: Vec<String>,
    pub confidence: Confidence,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MiningCalcInput {
    pub mining_speed: f64,
    pub mining_fortune: f64,
    /// Specialised fortunes added on top of Mining Fortune for matching targets.
    #[serde(default)]
    pub gemstone_fortune: f64,
    #[serde(default)]
    pub dwarven_metal_fortune: f64,
    #[serde(default)]
    pub block_fortune: f64,
    pub pristine: f64,
    /// Fraction of time actually breaking blocks (walking, refuel, etc. excluded). 0..=1
    pub uptime: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MiningCalcResult {
    pub input: MiningCalcInput,
    pub targets: Vec<MiningTargetRate>,
    pub freshness: Freshness,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct UpgradeOption {
    pub id: String,
    pub slot: String,
    pub name: String,
    pub cost: Option<f64>,
    pub stat: String,
    pub gain: f64,
    pub gain_per_million: Option<f64>,
    pub owned: bool,
    pub resale_value: Option<f64>,
    pub tier: String,
    pub notes: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GearPlan {
    pub purpose: String,
    pub budget: Option<f64>,
    pub selected: Vec<UpgradeOption>,
    pub rejected: Vec<UpgradeOption>,
    pub total_cost: f64,
    pub total_gain: BTreeMap<String, f64>,
    pub notes: Vec<String>,
}

// ---------------------------------------------------------------- Slayer

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SlayerDropEv {
    pub item_id: String,
    pub name: String,
    pub chance: f64,
    pub amount: f64,
    pub unit_value: Option<f64>,
    pub expected_value: Option<f64>,
    pub rng_meter_eligible: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SlayerTierEconomics {
    pub tier: u8,
    pub boss_cost: f64,
    pub xp_per_boss: f64,
    pub expected_drop_value: Option<f64>,
    pub rare_drop_ev: Option<f64>,
    pub expected_net_per_boss: Option<f64>,
    pub bosses_per_hour: Option<f64>,
    pub xp_per_hour: Option<f64>,
    pub net_per_hour: Option<f64>,
    pub drops: Vec<SlayerDropEv>,
    pub readiness: Option<f64>,
    pub unlocked: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SlayerPlan {
    pub slayer_id: String,
    pub boss_name: String,
    pub current_level: u32,
    pub target_level: u32,
    pub current_xp: f64,
    pub target_xp: f64,
    pub xp_remaining: f64,
    pub recommended_tier: u8,
    pub highest_practical_tier: Option<u8>,
    pub tiers: Vec<SlayerTierEconomics>,
    pub bosses_needed: Option<f64>,
    pub estimated_hours: Option<Range>,
    pub estimated_cost: Option<f64>,
    pub expected_drop_value: Option<f64>,
    pub gear: Vec<GearTierRecommendation>,
    pub readiness: BTreeMap<String, f64>,
    /// Long-goal estimate assuming the player moves up tiers with Recommended gear (the plan
    /// fields above use the current gear only). None when the current tier is already best.
    pub projection: Option<SlayerProjection>,
    pub notes: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SlayerStage {
    pub tier: u8,
    pub bosses: f64,
    pub xp: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SlayerProjection {
    /// Tier most of the XP is earned at.
    pub tier: u8,
    /// Unlock kills of lower tiers first, then the main tier.
    pub stages: Vec<SlayerStage>,
    pub bosses: f64,
    pub hours: Option<Range>,
    pub cost: f64,
    pub expected_drop_value: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GearTierRecommendation {
    pub slot: String,
    /// "REQUIRED" | "RECOMMENDED" | "LUXURY"
    pub tier: String,
    pub items: Vec<String>,
    pub owned: bool,
    pub price: Option<f64>,
}

// ---------------------------------------------------------------- Dungeons

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FloorReadiness {
    pub floor: String,
    pub readiness: f64,
    pub unlocked: bool,
    pub completions: u64,
    pub missing: Vec<Metric>,
    pub recommended: bool,
}

// ---------------------------------------------------------------- Farming

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct CropRate {
    pub crop: String,
    pub name: String,
    pub fortune: f64,
    pub blocks_per_second: f64,
    pub crops_per_hour: f64,
    pub npc_coins_per_hour: f64,
    pub bazaar_coins_per_hour: Option<f64>,
    pub best_bazaar_form: Option<String>,
    pub xp_per_hour: Option<f64>,
    pub assumptions: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FarmDesignSummary {
    pub id: String,
    pub name: String,
    pub crops: Vec<String>,
    pub description: String,
    pub size: [u32; 3],
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FarmBlock {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub block: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FarmBuildStep {
    pub index: u32,
    pub title: String,
    pub detail: String,
    pub layer_y: Option<i32>,
    pub blocks: BTreeMap<String, u32>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FarmDesign {
    pub summary: FarmDesignSummary,
    pub blocks: Vec<FarmBlock>,
    pub block_counts: BTreeMap<String, u32>,
    pub layers: Vec<i32>,
    pub movement: Vec<String>,
    pub harvest_direction: String,
    pub water_notes: Vec<String>,
    pub lighting_notes: Vec<String>,
    pub plot_boundary: [u32; 2],
    pub build_steps: Vec<FarmBuildStep>,
    pub estimated_build_minutes: f64,
    pub estimated_material_cost: Option<f64>,
}

// ---------------------------------------------------------------- Money

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MoneyMethodEstimate {
    pub id: String,
    pub name: String,
    pub category: String,
    pub coins_per_hour: Option<Range>,
    pub variable: bool,
    pub setup_cost: Option<f64>,
    pub capital_required: Option<f64>,
    pub risk: RiskLevel,
    pub requirements: Vec<String>,
    pub requirements_met: bool,
    pub missing_requirements: Vec<String>,
    pub why: String,
    pub confidence: Confidence,
    pub link: Option<String>,
    pub score: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct LiquidationEntry {
    pub item: String,
    pub item_id: String,
    pub value: Option<f64>,
    /// "SAFE_TO_SELL" | "CONSIDER_SELLING" | "DO_NOT_SELL"
    pub verdict: String,
    pub reason: String,
}
