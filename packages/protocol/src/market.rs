use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::common::{Confidence, Freshness, Range, RiskLevel};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FairValue {
    pub price_key: String,
    pub item_id: String,
    /// Final blended fair value (None when there is not enough data).
    pub value: Option<f64>,
    pub lowest_bin: Option<f64>,
    pub second_lowest_bin: Option<f64>,
    pub top_n_bin_median: Option<f64>,
    pub active_listings: u32,
    pub sales_median: Option<f64>,
    pub sales_weighted_median: Option<f64>,
    pub sales_trimmed_mean: Option<f64>,
    /// Sales observed in the last 24h.
    pub sales_24h: u32,
    pub sales_per_day: f64,
    /// Coefficient of variation of recent sale prices.
    pub volatility: Option<f64>,
    /// 0..=1 (1 = sells instantly).
    pub liquidity: f64,
    /// Expected minutes to sell at fair value.
    pub expected_sell_minutes: Option<Range>,
    /// Relative price change over the trend window (e.g. +0.05 = +5%).
    pub trend: Option<f64>,
    pub comparables_used: u32,
    pub method: String,
    pub confidence: Confidence,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FeeBreakdown {
    pub listing_fee: f64,
    pub duration_fee: f64,
    pub claim_tax: f64,
    pub total: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ManipulationCheck {
    pub flags: Vec<String>,
    pub suspicious: bool,
    pub verdict: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FlipOpportunity {
    pub auction_id: String,
    pub item_id: String,
    pub item_name: String,
    pub price_key: String,
    pub rarity: Option<String>,
    pub modifiers: Vec<String>,
    pub buy_price: f64,
    pub fair_value: f64,
    pub recommended_sell: f64,
    /// Highest price at which the flip still meets the minimum profit/ROI target.
    pub max_buy_price: f64,
    pub fees: FeeBreakdown,
    pub expected_net_profit: f64,
    pub roi: f64,
    pub probability_of_sale: f64,
    pub expected_sell_minutes: Range,
    pub risk: RiskLevel,
    pub manipulation: ManipulationCheck,
    pub confidence: Confidence,
    pub score: f64,
    #[ts(type = "number")]
    pub listed_at_ms: i64,
    #[ts(type = "number")]
    pub seen_at_ms: i64,
    pub comparables: u32,
    /// "/viewauction <uuid>" helper for copy-paste (SkyPilot never executes it).
    pub view_command: String,
    /// How flips like this one (same comparable key) actually did in SkyPilot's own record.
    #[serde(default)]
    pub track_record: Option<FlipTrackRecord>,
}

/// One flip in a snowball basket.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SnowballPick {
    pub auction_id: String,
    pub item_id: String,
    pub item_name: String,
    pub price_key: String,
    pub buy_price: f64,
    pub recommended_sell: f64,
    pub expected_net_profit: f64,
    pub probability_of_sale: f64,
    /// p(sale) x profit minus (1 - p) x the cost of a miss (fees + a 3% markdown to relist).
    pub expected_value: f64,
    /// Expected hours until it sells (upper end of the sell-time range).
    pub sell_hours: f64,
    pub view_command: String,
    pub track_record: Option<FlipTrackRecord>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SnowballDay {
    pub day: u32,
    pub capital: f64,
    pub profit: f64,
    /// Coins that could be put to work that day (capped by what the market offers).
    pub deployed: f64,
}

/// A capital level and what it opens up in today's market.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SnowballMilestone {
    pub capital: f64,
    /// Projected day the capital is reached (None = not within the projection).
    pub day: Option<u32>,
    /// Current flips affordable at this capital (one per comparable key, at most half of it each).
    pub affordable_flips: u32,
    pub best_expected_value: f64,
}

/// Compounding plan for AH flipping: a diversified basket for the current capital and a
/// projection of reinvesting the expected value. A model, not a promise.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SnowballPlan {
    pub capital: f64,
    pub hours_per_day: f64,
    pub days: u32,
    pub picks: Vec<SnowballPick>,
    /// Coins spent on the basket and kept back for listing fees.
    pub deployed: f64,
    pub reserve: f64,
    pub expected_profit_per_cycle: f64,
    /// expected_profit_per_cycle / deployed
    pub return_per_cycle: f64,
    pub cycle_hours: f64,
    pub cycles_per_day: f64,
    /// Total buy value of all qualifying flips listed right now (one per key): how much the
    /// market can absorb per cycle.
    pub market_capacity: f64,
    pub projection: Vec<SnowballDay>,
    pub milestones: Vec<SnowballMilestone>,
    /// SkyPilot's record of past candidates (hits / trials) the probabilities are calibrated on.
    pub calibration_trials: u32,
    pub calibration_hits: u32,
    pub assumptions: Vec<String>,
    pub freshness: Freshness,
}

/// Outcome of earlier flip candidates with the same comparable key: did a comparable item sell
/// at the recommended price within 24 hours after the candidate was seen?
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FlipTrackRecord {
    pub trials: u32,
    pub hits: u32,
    pub hit_rate: f64,
    pub median_sell_minutes: Option<f64>,
    /// Mean net profit actually reached (hit at target, otherwise a forced exit).
    pub average_realized_profit: Option<f64>,
    /// Hours of history the record covers.
    pub window_hours: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FlipQuery {
    pub max_budget: Option<f64>,
    pub min_profit: Option<f64>,
    pub min_roi: Option<f64>,
    pub min_confidence: Option<f64>,
    pub max_risk: Option<RiskLevel>,
    pub search: Option<String>,
    pub limit: Option<u32>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FlipList {
    pub flips: Vec<FlipOpportunity>,
    pub scanned_listings: u32,
    pub freshness: Freshness,
    pub collector_healthy: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BazaarOrderLevel {
    pub price: f64,
    pub amount: f64,
    pub orders: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BazaarProduct {
    pub product_id: String,
    pub name: String,
    /// Price you pay to instantly buy (lowest sell offer).
    pub instant_buy: Option<f64>,
    /// Price you receive when instantly selling (highest buy order).
    pub instant_sell: Option<f64>,
    pub spread: Option<f64>,
    pub spread_pct: Option<f64>,
    pub buy_volume: f64,
    pub sell_volume: f64,
    pub buy_moving_week: f64,
    pub sell_moving_week: f64,
    pub buy_orders: u32,
    pub sell_orders: u32,
    /// Relative change of the mid price over the trend window.
    pub trend: Option<f64>,
    pub volatility: Option<f64>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum BazaarFlipKind {
    /// Place buy order, then sell offer.
    OrderFlip,
    /// Instant buy then sell offer.
    InstantFlip,
    /// Buy ingredients and craft into a more valuable product.
    CraftFlip,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BazaarFlip {
    pub kind: BazaarFlipKind,
    pub product_id: String,
    pub name: String,
    pub buy_price: f64,
    pub sell_price: f64,
    pub tax: f64,
    pub profit_per_unit: f64,
    pub roi: f64,
    /// Suggested amount respecting volume & capital.
    pub suggested_amount: f64,
    pub expected_profit: f64,
    pub capital_required: f64,
    pub buy_fill_minutes: Option<Range>,
    pub sell_fill_minutes: Option<Range>,
    pub profit_per_hour: Option<f64>,
    pub risk: RiskLevel,
    pub confidence: Confidence,
    pub score: f64,
    pub notes: Vec<String>,
    /// Prices far from their usual level (pushed offers or a crash): do not flip.
    #[serde(default)]
    pub manipulated: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BazaarList {
    pub flips: Vec<BazaarFlip>,
    pub products: Vec<BazaarProduct>,
    pub freshness: Freshness,
    pub tax_rate: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct IngredientCost {
    pub item_id: String,
    pub name: String,
    pub amount: f64,
    pub unit_price: Option<f64>,
    pub total: Option<f64>,
    /// "Bazaar", "BIN", "Craft", "NPC", "Unknown"
    pub source: String,
    pub children: Vec<IngredientCost>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct CraftFlip {
    pub item_id: String,
    pub name: String,
    pub output_count: f64,
    pub material_cost: f64,
    pub craft_cost: f64,
    pub sale_price: f64,
    pub sale_source: String,
    pub fees: f64,
    pub expected_profit: f64,
    pub roi: f64,
    pub sales_per_day: f64,
    pub ingredients: Vec<IngredientCost>,
    pub confidence: Confidence,
    pub requirement: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum TimeFlipKind {
    Forge,
    Kat,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TimeFlip {
    pub kind: TimeFlipKind,
    pub output_id: String,
    pub name: String,
    pub input_summary: String,
    pub capital: f64,
    pub processing_hours: f64,
    pub final_value: f64,
    pub fees: f64,
    pub profit: f64,
    pub profit_per_day: f64,
    pub roi: f64,
    pub ingredients: Vec<IngredientCost>,
    pub confidence: Confidence,
    pub requirement: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PricePoint {
    #[ts(type = "number")]
    pub t: i64,
    pub median: Option<f64>,
    pub low: Option<f64>,
    pub high: Option<f64>,
    pub volume: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ItemSearchResult {
    pub item_id: String,
    pub name: String,
    pub tier: Option<String>,
    pub category: Option<String>,
    pub bazaar: bool,
    pub auctionable: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PriceDetail {
    pub item_id: String,
    pub name: String,
    pub tier: Option<String>,
    pub fair_value: Option<FairValue>,
    pub bazaar: Option<BazaarProduct>,
    pub craft_cost: Option<f64>,
    pub craft_tree: Option<IngredientCost>,
    pub npc_sell_price: Option<f64>,
    pub history: Vec<PricePoint>,
    pub range: String,
    pub freshness: Freshness,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BacktestRequest {
    pub min_roi: f64,
    pub min_profit: f64,
    pub min_sales_per_day: f64,
    pub max_buy_price: Option<f64>,
    pub min_confidence: Option<f64>,
    /// Lookback window in hours.
    pub lookback_hours: u32,
    /// Hours a position may stay open before it counts as a forced sale at the then fair value.
    pub max_hold_hours: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BacktestTrade {
    pub item_id: String,
    pub buy_price: f64,
    pub sell_price: f64,
    pub profit: f64,
    pub hold_minutes: f64,
    #[ts(type = "number")]
    pub bought_at_ms: i64,
    pub forced_exit: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct BacktestResult {
    pub trades: u32,
    pub win_rate: f64,
    pub average_profit: f64,
    pub median_profit: f64,
    pub median_sell_minutes: f64,
    pub max_drawdown: f64,
    pub capital_required: f64,
    pub total_profit: f64,
    pub sample: Vec<BacktestTrade>,
    pub data_window_hours: f64,
    pub notes: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MarketStatus {
    pub auctions: Freshness,
    pub ended_auctions: Freshness,
    pub bazaar: Freshness,
    pub active_listings: u32,
    pub active_bin_listings: u32,
    pub tracked_price_keys: u32,
    #[ts(type = "number")]
    pub sales_recorded: u64,
    pub hypixel_circuit: String,
    pub last_error: Option<String>,
    pub counters: BTreeMap<String, u64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct NotificationRule {
    pub id: String,
    pub name: String,
    pub max_budget: Option<f64>,
    pub min_profit: Option<f64>,
    pub min_roi: Option<f64>,
    pub min_confidence: Option<f64>,
    pub max_risk: Option<RiskLevel>,
    pub enabled: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MarketEvent {
    pub kind: String,
    pub flips: Vec<FlipOpportunity>,
    pub freshness: Freshness,
    pub matched_rules: Vec<String>,
}
