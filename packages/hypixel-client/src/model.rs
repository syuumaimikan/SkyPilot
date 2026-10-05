//! Typed Hypixel market payloads (fields SkyPilot uses; unknown fields ignored).

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AuctionPage {
    #[serde(default)]
    pub success: bool,
    #[serde(default)]
    pub page: u32,
    #[serde(rename = "totalPages", default)]
    pub total_pages: u32,
    #[serde(rename = "totalAuctions", default)]
    pub total_auctions: u32,
    #[serde(rename = "lastUpdated", default)]
    pub last_updated: i64,
    #[serde(default)]
    pub auctions: Vec<RawAuction>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct RawAuction {
    pub uuid: String,
    #[serde(default)]
    pub auctioneer: String,
    #[serde(default)]
    pub profile_id: String,
    #[serde(default)]
    pub start: i64,
    #[serde(default)]
    pub end: i64,
    #[serde(default)]
    pub item_name: String,
    #[serde(default)]
    pub tier: String,
    #[serde(default)]
    pub category: String,
    #[serde(default)]
    pub starting_bid: f64,
    #[serde(default)]
    pub item_bytes: String,
    #[serde(default)]
    pub bin: bool,
    #[serde(default)]
    pub claimed: bool,
    #[serde(default)]
    pub highest_bid_amount: f64,
    #[serde(default)]
    pub last_updated: i64,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct EndedAuctions {
    #[serde(default)]
    pub success: bool,
    #[serde(rename = "lastUpdated", default)]
    pub last_updated: i64,
    #[serde(default)]
    pub auctions: Vec<EndedAuction>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct EndedAuction {
    pub auction_id: String,
    #[serde(default)]
    pub seller: String,
    #[serde(default)]
    pub buyer: String,
    #[serde(default)]
    pub timestamp: i64,
    #[serde(default)]
    pub price: f64,
    #[serde(default)]
    pub bin: bool,
    #[serde(default)]
    pub item_bytes: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct BazaarResponse {
    #[serde(default)]
    pub success: bool,
    #[serde(rename = "lastUpdated", default)]
    pub last_updated: i64,
    #[serde(default)]
    pub products: HashMap<String, BazaarProductRaw>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct BazaarProductRaw {
    pub product_id: String,
    /// Buy orders (what instant-sellers receive), best (highest) first.
    #[serde(default)]
    pub sell_summary: Vec<OrderSummary>,
    /// Sell offers (what instant-buyers pay), best (lowest) first.
    #[serde(default)]
    pub buy_summary: Vec<OrderSummary>,
    pub quick_status: QuickStatus,
}

#[derive(Debug, Clone, Copy, Deserialize, Serialize)]
pub struct OrderSummary {
    #[serde(default)]
    pub amount: f64,
    #[serde(rename = "pricePerUnit", default)]
    pub price_per_unit: f64,
    #[serde(default)]
    pub orders: f64,
}

#[derive(Debug, Clone, Copy, Deserialize, Serialize)]
pub struct QuickStatus {
    #[serde(rename = "sellPrice", default)]
    pub sell_price: f64,
    #[serde(rename = "sellVolume", default)]
    pub sell_volume: f64,
    #[serde(rename = "sellMovingWeek", default)]
    pub sell_moving_week: f64,
    #[serde(rename = "sellOrders", default)]
    pub sell_orders: f64,
    #[serde(rename = "buyPrice", default)]
    pub buy_price: f64,
    #[serde(rename = "buyVolume", default)]
    pub buy_volume: f64,
    #[serde(rename = "buyMovingWeek", default)]
    pub buy_moving_week: f64,
    #[serde(rename = "buyOrders", default)]
    pub buy_orders: f64,
}
