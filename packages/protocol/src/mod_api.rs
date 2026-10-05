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

/// How the mod draws an item: a vanilla item plus head texture / leather colour / glint.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ItemIcon {
    /// Current item id, e.g. "minecraft:player_head" (unknown ids are drawn as paper).
    pub item: String,
    /// Base64 `textures` property for player heads.
    pub texture: Option<String>,
    /// RGB colour for leather armour.
    pub color: Option<u32>,
    pub glint: bool,
}

/// One AH or Bazaar flip for the in-game GUI and alerts. Information only: `command` is put
/// into the chat box for the player to send themselves, never executed by the mod.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModFlipRow {
    /// Auction uuid (AH) or product id (Bazaar).
    pub id: String,
    /// "AH" | "BAZAAR"
    pub kind: String,
    pub item_id: String,
    pub name: String,
    pub rarity: Option<String>,
    pub modifiers: Vec<String>,
    /// AH: BIN price; Bazaar: buy-order price per unit.
    pub buy: f64,
    /// AH: suggested listing price; Bazaar: sell-offer price per unit.
    pub sell: f64,
    /// Expected net profit after fees/tax (per flip for AH, for `amount` units for Bazaar).
    pub profit: f64,
    pub roi: f64,
    pub profit_per_hour: Option<f64>,
    /// Bazaar: suggested amount.
    pub amount: Option<f64>,
    pub confidence: f64,
    /// "HIGH" | "MEDIUM" | "LOW"
    pub confidence_label: String,
    /// "LOW" | "MEDIUM" | "HIGH" | "VERY_HIGH"
    pub risk: String,
    /// Expected time to sell, e.g. "12m–1h".
    pub sell_time: Option<String>,
    /// Manipulation suspected: shown as DO NOT FLIP, never alerted.
    pub do_not_flip: bool,
    pub notes: Vec<String>,
    pub command: Option<String>,
    pub icon: ItemIcon,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModMarketPanel {
    pub kind: String,
    pub title: String,
    pub rows: Vec<ModFlipRow>,
    #[ts(type = "number")]
    pub age_seconds: i64,
    pub stale: bool,
    pub note: String,
}

/// New AH flips matching the player's notification rules since `sinceMs`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModAlerts {
    #[ts(type = "number")]
    pub now_ms: i64,
    pub rows: Vec<ModFlipRow>,
    /// Names of the rules used (a default rule when the player has none).
    pub rules: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModItemRef {
    pub item_id: String,
    pub name: String,
    pub price: Option<f64>,
    pub icon: ItemIcon,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModPlanAction {
    pub title: String,
    pub category: String,
    pub why: String,
    pub steps: Vec<String>,
    pub metrics: Vec<HudLine>,
    pub confidence: f64,
    pub cost: Option<f64>,
    pub hours: Option<f64>,
    pub items: Vec<ModItemRef>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModPlanStep {
    pub title: String,
    pub detail: Option<String>,
    /// "SATISFIED" | "IN_PROGRESS" | "NOT_STARTED" | "BLOCKED" | "UNKNOWN"
    pub status: String,
    pub progress: f64,
    pub cost: Option<f64>,
    pub hours: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModPlanGoal {
    pub title: String,
    pub progress: f64,
    pub remaining_cost: Option<f64>,
    pub remaining_hours: Option<f64>,
    pub steps: Vec<ModPlanStep>,
}

/// "What should I do now?" + roadmaps from the player's last analysis, sized for the GUI.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModPlan {
    pub profile: Option<String>,
    #[ts(type = "number")]
    pub analyzed_at_ms: i64,
    pub goals: Vec<ModPlanGoal>,
    pub actions: Vec<ModPlanAction>,
    pub warnings: Vec<String>,
    /// Set when there is no analysis yet.
    pub message: Option<String>,
}
