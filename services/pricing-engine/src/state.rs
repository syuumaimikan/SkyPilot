//! In-memory market state shared by the collector (writer) and the API (readers).

use std::collections::{HashMap, HashSet, VecDeque};

use skypilot_hypixel::BazaarProductRaw;
use skypilot_protocol::market::{FairValue, FlipOpportunity};

use crate::normalize::{Listing, Sale};

pub const MAX_SALES_PER_KEY: usize = 600;
pub const SALES_MAX_AGE_MS: i64 = 30 * 24 * 3600 * 1000;
pub const BAZAAR_POINT_INTERVAL_MS: i64 = 15 * 60 * 1000;
pub const BAZAAR_HISTORY_MS: i64 = 7 * 24 * 3600 * 1000;
const MAX_SEEN_SALES: usize = 250_000;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SaleRecord {
    pub unit_price: f64,
    pub ts_ms: i64,
    pub bin: bool,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BazaarPoint {
    pub ts_ms: i64,
    /// Instant-buy price (lowest sell offer).
    pub buy: f64,
    /// Instant-sell price (highest buy order).
    pub sell: f64,
    pub buy_volume: f64,
    pub sell_volume: f64,
    pub buy_moving_week: f64,
    pub sell_moving_week: f64,
}

/// A recorded flip candidate (for backtesting on collected data).
#[derive(Debug, Clone, PartialEq)]
pub struct CandidateRecord {
    pub ts_ms: i64,
    pub comparable_key: String,
    pub market_id: String,
    pub buy_price: f64,
    pub recommended_sell: f64,
    pub fair_value: f64,
    pub roi: f64,
    pub expected_profit: f64,
    pub sales_per_day: f64,
    pub confidence: f64,
}

#[derive(Debug, Default)]
pub struct MarketState {
    pub listings: Vec<Listing>,
    /// comparable key -> listing indices (BIN only), sorted by unit price ascending.
    pub bin_by_comparable: HashMap<String, Vec<usize>>,
    pub by_market: HashMap<String, Vec<usize>>,
    pub sales: HashMap<String, VecDeque<SaleRecord>>,
    pub sales_by_market: HashMap<String, VecDeque<SaleRecord>>,
    seen_sales: HashSet<String>,
    seen_order: VecDeque<String>,
    pub bazaar: HashMap<String, BazaarProductRaw>,
    pub bazaar_history: HashMap<String, VecDeque<BazaarPoint>>,
    pub auctions_updated_ms: i64,
    pub ended_updated_ms: i64,
    pub bazaar_updated_ms: i64,
    /// Earliest point in time covered by the sales history (collector start or DB warm-up).
    pub history_since_ms: i64,
    pub fair_cache: HashMap<String, FairValue>,
    pub flips: Vec<FlipOpportunity>,
    pub flips_computed_ms: i64,
    pub candidates: VecDeque<CandidateRecord>,
    pub sales_recorded: u64,
    pub last_error: Option<String>,
    pub collector_started_ms: i64,
}

impl MarketState {
    pub fn new(now_ms: i64) -> Self {
        MarketState { history_since_ms: now_ms, collector_started_ms: now_ms, ..Default::default() }
    }

    /// Replace all active listings (after a full AH crawl).
    pub fn replace_listings(&mut self, listings: Vec<Listing>, last_updated_ms: i64) {
        let mut bin_by: HashMap<String, Vec<usize>> = HashMap::new();
        let mut by_market: HashMap<String, Vec<usize>> = HashMap::new();
        for (i, l) in listings.iter().enumerate() {
            by_market.entry(l.market_id.clone()).or_default().push(i);
            if l.bin {
                bin_by.entry(l.comparable_key.clone()).or_default().push(i);
            }
        }
        for idx in bin_by.values_mut() {
            idx.sort_by(|a, b| listings[*a].unit_price().total_cmp(&listings[*b].unit_price()));
        }
        self.listings = listings;
        self.bin_by_comparable = bin_by;
        self.by_market = by_market;
        self.auctions_updated_ms = last_updated_ms;
        self.fair_cache.clear();
    }

    /// Ingest ended auctions; duplicates (same auction id) are ignored.
    pub fn add_sales(&mut self, sales: impl IntoIterator<Item = Sale>, now_ms: i64) -> usize {
        let mut added = 0;
        for s in sales {
            if !s.unit_price.is_finite() || s.unit_price <= 0.0 || !self.seen_sales.insert(s.auction_id.clone()) {
                continue;
            }
            self.seen_order.push_back(s.auction_id.clone());
            if self.seen_order.len() > MAX_SEEN_SALES {
                if let Some(old) = self.seen_order.pop_front() {
                    self.seen_sales.remove(&old);
                }
            }
            let rec = SaleRecord { unit_price: s.unit_price, ts_ms: s.ts_ms, bin: s.bin };
            for (map, key) in [(&mut self.sales, s.comparable_key.clone()), (&mut self.sales_by_market, s.market_id.clone())] {
                let q = map.entry(key).or_default();
                q.push_back(rec);
                while q.len() > MAX_SALES_PER_KEY || q.front().is_some_and(|f| now_ms - f.ts_ms > SALES_MAX_AGE_MS) {
                    q.pop_front();
                }
            }
            added += 1;
            self.sales_recorded += 1;
        }
        if added > 0 {
            self.fair_cache.clear();
        }
        added
    }

    pub fn set_bazaar(&mut self, products: HashMap<String, BazaarProductRaw>, last_updated_ms: i64) {
        for (id, p) in &products {
            let point = BazaarPoint {
                ts_ms: last_updated_ms,
                buy: p.buy_summary.first().map(|o| o.price_per_unit).unwrap_or(p.quick_status.buy_price),
                sell: p.sell_summary.first().map(|o| o.price_per_unit).unwrap_or(p.quick_status.sell_price),
                buy_volume: p.quick_status.buy_volume,
                sell_volume: p.quick_status.sell_volume,
                buy_moving_week: p.quick_status.buy_moving_week,
                sell_moving_week: p.quick_status.sell_moving_week,
            };
            let q = self.bazaar_history.entry(id.clone()).or_default();
            if q.back().is_none_or(|b| point.ts_ms - b.ts_ms >= BAZAAR_POINT_INTERVAL_MS) {
                q.push_back(point);
            } else if let Some(last) = q.back_mut() {
                // keep the newest observation inside the current bucket
                *last = BazaarPoint { ts_ms: last.ts_ms, ..point };
            }
            while q.front().is_some_and(|f| last_updated_ms - f.ts_ms > BAZAAR_HISTORY_MS) {
                q.pop_front();
            }
        }
        self.bazaar = products;
        self.bazaar_updated_ms = last_updated_ms;
    }

    /// Seed history from persistence on startup.
    pub fn seed_bazaar_point(&mut self, product: &str, point: BazaarPoint) {
        let q = self.bazaar_history.entry(product.to_string()).or_default();
        if q.back().is_none_or(|b| point.ts_ms - b.ts_ms >= BAZAAR_POINT_INTERVAL_MS) {
            q.push_back(point);
        }
    }

    pub fn bin_prices(&self, comparable_key: &str) -> Vec<f64> {
        self.bin_by_comparable
            .get(comparable_key)
            .map(|idx| idx.iter().map(|i| self.listings[*i].unit_price()).collect())
            .unwrap_or_default()
    }

    pub fn bin_listings(&self, comparable_key: &str) -> Vec<&Listing> {
        self.bin_by_comparable.get(comparable_key).map(|idx| idx.iter().map(|i| &self.listings[*i]).collect()).unwrap_or_default()
    }

    pub fn sales_for(&self, comparable_key: &str) -> Vec<SaleRecord> {
        self.sales.get(comparable_key).map(|q| q.iter().copied().collect()).unwrap_or_default()
    }

    pub fn record_candidate(&mut self, c: CandidateRecord) {
        self.candidates.push_back(c);
        while self.candidates.len() > 200_000 {
            self.candidates.pop_front();
        }
    }

    pub fn active_bin_count(&self) -> usize {
        self.listings.iter().filter(|l| l.bin).count()
    }
}
