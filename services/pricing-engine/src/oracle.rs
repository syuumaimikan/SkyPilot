//! Price lookups used by every other engine (profile networth, recommendations, crafts).

use skypilot_protocol::market::FairValue;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PriceSide {
    /// Coins needed to acquire the item now (instant buy / lowest BIN).
    Buy,
    /// Coins realistically received when selling now (instant sell / fair value).
    Sell,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Quote {
    pub price: f64,
    /// "Bazaar", "BIN", "Sales", "NPC", "NPC Shop", "Craft"
    pub source: &'static str,
    pub confidence: f64,
    pub sales_per_day: f64,
}

pub trait PriceOracle: Send + Sync {
    fn quote(&self, id: &str, side: PriceSide) -> Option<Quote>;
    fn fair_value(&self, comparable_key: &str) -> Option<FairValue>;
    /// True when this id trades on the Bazaar.
    fn is_bazaar(&self, id: &str) -> bool;
}

/// Oracle with no market data (used when the collector has not produced data yet).
pub struct NoMarket;

impl PriceOracle for NoMarket {
    fn quote(&self, _id: &str, _side: PriceSide) -> Option<Quote> {
        None
    }
    fn fair_value(&self, _k: &str) -> Option<FairValue> {
        None
    }
    fn is_bazaar(&self, _id: &str) -> bool {
        false
    }
}
