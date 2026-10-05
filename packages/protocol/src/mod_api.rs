//! Payloads consumed by the Fabric mod. Kept intentionally small and stable; the mod
//! ignores unknown fields so the backend can extend them.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct HudLine {
    pub label: String,
    pub value: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct HudContextBlock {
    /// "MINING" | "SLAYER" | "GARDEN" | "DUNGEON" | "FISHING" | "FORAGING" | "RIFT" | "KUUDRA" | "GENERAL"
    pub context: String,
    pub title: String,
    pub lines: Vec<HudLine>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct HudState {
    pub linked: bool,
    pub username: Option<String>,
    pub goal_title: Option<String>,
    pub step_index: Option<u32>,
    pub step_count: Option<u32>,
    pub step_title: Option<String>,
    pub step_progress: Option<f64>,
    pub step_current: Option<f64>,
    pub step_target: Option<f64>,
    pub step_unit: Option<String>,
    pub next_title: Option<String>,
    pub estimated_cost: Option<f64>,
    pub overall_progress: Option<f64>,
    pub context: Option<HudContextBlock>,
    pub actions: Vec<String>,
    #[ts(type = "number")]
    pub updated_at_ms: i64,
    pub stale: bool,
    pub message: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModPriceRequest {
    pub skyblock_id: String,
    /// Optional custom_data JSON of the selected stack (enchantments, stars...).
    pub custom_data: Option<serde_json::Value>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModPriceResponse {
    pub skyblock_id: String,
    pub name: String,
    pub estimated_value: Option<f64>,
    pub median: Option<f64>,
    pub lowest_bin: Option<f64>,
    pub bazaar_buy: Option<f64>,
    pub bazaar_sell: Option<f64>,
    pub sales_per_day: Option<f64>,
    pub confidence: Option<f64>,
    pub confidence_label: String,
    pub price_key: Option<String>,
    pub age_seconds: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModTextResponse {
    pub title: String,
    pub lines: Vec<HudLine>,
}
