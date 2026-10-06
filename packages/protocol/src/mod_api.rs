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
    /// NPC sell price from Hypixel's item list.
    pub npc_sell: Option<f64>,
    /// Cost to craft one from Bazaar/NPC-priced ingredients.
    pub craft_cost: Option<f64>,
    /// Bazaar sales tax (curated, Hypixel SkyBlock Wiki) for Bazaar products.
    pub bazaar_tax_rate: Option<f64>,
}

/// A sea creature and the chat line announcing it (SkyHanni-REPO, MIT).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModSeaCreature {
    pub name: String,
    pub area: String,
    pub message: String,
    pub rarity: String,
    pub fishing_xp: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModSeaCreatures {
    pub creatures: Vec<ModSeaCreature>,
    pub attribution: String,
}

/// SkyBlock calendar for the mod's clock and event timers (constants from the curated registry).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModCalendar {
    pub epoch_ms: i64,
    pub day_ms: i64,
    pub days_per_month: u32,
    pub months: Vec<String>,
    /// Next occurrence of each recurring and seasonal event, soonest first.
    pub events: Vec<ModCalendarEvent>,
    pub now_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModCalendarEvent {
    pub id: String,
    pub name: String,
    pub start_ms: i64,
    pub end_ms: i64,
    pub active: bool,
    /// Repeats every this many SkyBlock days (recurring events only).
    pub every_days: Option<u32>,
    pub offset_days: Option<u32>,
    pub duration_days: Option<u32>,
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

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModItemEntry {
    pub id: String,
    pub name: String,
    pub rarity: Option<String>,
    pub icon: ItemIcon,
    /// What it sells for now (Bazaar instant sell / fair value / NPC), when known.
    pub value: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModItemPage {
    pub total: u32,
    pub offset: u32,
    pub items: Vec<ModItemEntry>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModStack {
    pub id: String,
    pub name: String,
    pub amount: f64,
    pub icon: ItemIcon,
    /// Cheapest way to get one unit now (Bazaar instant buy / lowest BIN / NPC shop).
    pub unit_price: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModRecipe {
    /// "CRAFTING" | "FORGE" | "KAT" | "NPC_SHOP"
    pub kind: String,
    pub output: ModStack,
    pub inputs: Vec<ModStack>,
    /// Crafting only: the 3×3 grid (A1..C3), empty slots as null.
    pub grid: Vec<Option<ModStack>>,
    pub duration_secs: Option<f64>,
    pub npc: Option<String>,
    pub coins: Option<f64>,
    /// Sum of the inputs at current prices (None when an input has no price).
    pub cost: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModDropSource {
    pub source: String,
    pub chance: f64,
    pub amount: f64,
}

/// JEI-style item page: value, description, recipes, drop sources and uses.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModItemDetail {
    pub item: ModItemEntry,
    pub category: Option<String>,
    /// In-game description with § formatting codes (NEU-REPO).
    pub lore: Vec<String>,
    pub value_source: Option<String>,
    pub lowest_bin: Option<f64>,
    pub sales_per_day: Option<f64>,
    pub bazaar_buy: Option<f64>,
    pub bazaar_sell: Option<f64>,
    pub npc_sell: Option<f64>,
    pub craft_cost: Option<f64>,
    pub recipes: Vec<ModRecipe>,
    pub drops: Vec<ModDropSource>,
    pub used_in: Vec<ModItemEntry>,
    pub wiki: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModValueItem {
    /// The stack's custom_data (26.2 data component) as JSON.
    pub custom_data: serde_json::Value,
    pub count: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModValuesRequest {
    pub items: Vec<ModValueItem>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModValue {
    pub skyblock_id: Option<String>,
    pub name: String,
    /// Value of the whole stack.
    pub value: Option<f64>,
    pub confidence: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModValues {
    pub values: Vec<ModValue>,
    pub total: f64,
}

/// A fixed public place to walk to (from SkyHanni-REPO's island graphs).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModWaypoint {
    /// SkyHanni island id, e.g. "DWARVEN_MINES".
    pub island: String,
    pub name: String,
    pub x: f64,
    pub y: f64,
    pub z: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModGuideStep {
    pub title: String,
    pub text: String,
    /// From the last analysed profile; None when it cannot be told.
    pub done: Option<bool>,
    pub waypoint: Option<ModWaypoint>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModGuide {
    pub id: String,
    pub island: String,
    pub title: String,
    pub source: String,
    pub steps: Vec<ModGuideStep>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModGuides {
    pub guides: Vec<ModGuide>,
    /// Attribution for the waypoint data.
    pub attribution: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModGearItem {
    pub id: String,
    pub name: String,
    pub icon: ItemIcon,
    pub slot: String,
    /// "REQUIRED" | "RECOMMENDED" | "LUXURY" for goal gear, the item tier for upgrades.
    pub tier: String,
    pub owned: bool,
    /// Cheapest way to get it now (Bazaar / lowest BIN / NPC shop / craft).
    pub price: Option<f64>,
    pub note: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModGearSection {
    pub title: String,
    pub items: Vec<ModGearItem>,
}

/// Recommended gear: tiers for the goal's slayers and the best-value upgrades per activity.
/// Each item opens its recipe ("how to make it") in the item page.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ModGear {
    pub sections: Vec<ModGearSection>,
    pub message: Option<String>,
}

/// Whether a key is stored (never the key itself; `hint` shows its last 4 characters).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct KeyInfo {
    pub configured: bool,
    pub hint: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslationSettingsView {
    /// "server" (the server's provider) | "deepl" | "claude" | "none" (dictionary only)
    pub provider: String,
    pub deepl: KeyInfo,
    pub anthropic: KeyInfo,
    /// What "server" means on this server (None = dictionary only).
    pub server_provider: Option<String>,
    pub providers: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct UserSettings {
    pub translation: TranslationSettingsView,
}

/// `deeplKey` / `anthropicKey`: omitted = keep, "" = delete, otherwise the new key.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslationSettingsUpdate {
    pub provider: String,
    pub deepl_key: Option<String>,
    pub anthropic_key: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslationTestResult {
    pub ok: bool,
    pub provider: String,
    pub translated: Option<String>,
    pub message: String,
}
