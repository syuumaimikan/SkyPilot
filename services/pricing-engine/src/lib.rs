//! SkyPilot market engine.
//!
//! ```text
//! Hypixel API -> Collector -> normalize (NBT + fingerprint) -> MarketState
//!            -> FairValue -> Flips / Bazaar / Craft / Forge / Kat -> API & WebSocket
//! ```

pub mod backtest;
pub mod bazaar;
pub mod craft;
pub mod fair_value;
pub mod flips;
pub mod manipulation;
pub mod networth;
pub mod normalize;
pub mod oracle;
pub mod state;
pub mod timeflip;

use std::collections::HashMap;
use std::sync::Arc;

use parking_lot::RwLock;
use skypilot_calc::fees::AhFeeConfig;
use skypilot_data::Registry;
use skypilot_hypixel::{BazaarResponse, EndedAuctions, RawAuction};
use skypilot_protocol::common::Freshness;
use skypilot_protocol::market::{
    BacktestRequest, BacktestResult, BazaarList, CraftFlip, FairValue, FlipList, FlipQuery, ItemSearchResult, MarketStatus, PriceDetail,
    PricePoint, TimeFlip,
};

pub use oracle::{NoMarket, PriceOracle, PriceSide, Quote};
pub use state::MarketState;

use crate::fair_value::FairValueConfig;
use crate::flips::{DefaultFlipScore, FlipConfig, FlipScoreStrategy};

pub fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

pub struct MarketEngine {
    pub state: RwLock<MarketState>,
    registry: RwLock<Arc<Registry>>,
    strategy: Box<dyn FlipScoreStrategy>,
    pub fv_config: FairValueConfig,
    derived: RwLock<Derived>,
}

/// Expensive derived views recomputed after each data refresh.
#[derive(Default)]
struct Derived {
    craft: Vec<CraftFlip>,
    time: Vec<TimeFlip>,
    computed_ms: i64,
}

impl MarketEngine {
    pub fn new(registry: Arc<Registry>) -> Self {
        MarketEngine {
            state: RwLock::new(MarketState::new(now_ms())),
            registry: RwLock::new(registry),
            strategy: Box::new(DefaultFlipScore),
            fv_config: FairValueConfig::default(),
            derived: RwLock::new(Derived::default()),
        }
    }

    pub fn with_strategy(mut self, strategy: Box<dyn FlipScoreStrategy>) -> Self {
        self.strategy = strategy;
        self
    }

    pub fn registry(&self) -> Arc<Registry> {
        self.registry.read().clone()
    }

    pub fn set_registry(&self, registry: Arc<Registry>) {
        *self.registry.write() = registry;
    }

    pub fn fee_config(&self) -> AhFeeConfig {
        let reg = self.registry();
        let derpy = reg.election.as_ref().and_then(|e| e.mayor_key().map(|k| k.eq_ignore_ascii_case("derpy"))).unwrap_or(false);
        reg.curated.market.auction_house.fee_config(derpy)
    }

    pub fn bazaar_tax(&self) -> f64 {
        self.registry().curated.market.bazaar.tax_rate
    }

    /// Bazaar tax after the Bazaar Flipper account upgrade (`level` 0..=max, clamped).
    pub fn bazaar_tax_for(&self, flipper_level: u32) -> f64 {
        let reg = self.registry();
        let b = &reg.curated.market.bazaar;
        (b.tax_rate - b.flipper_reduction_per_level * flipper_level.min(b.flipper_max_level) as f64).max(0.0)
    }

    // ------------------------------------------------------------------ ingestion

    /// Normalise a full AH crawl (all pages) and replace active listings.
    pub fn ingest_auctions(&self, raw: Vec<RawAuction>, last_updated_ms: i64) -> usize {
        let registry = self.registry();
        let threads = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(4).clamp(1, 16);
        let chunk = raw.len().div_ceil(threads).max(1);
        let listings: Vec<normalize::Listing> = std::thread::scope(|s| {
            let handles: Vec<_> = raw
                .chunks(chunk)
                .map(|part| {
                    let reg = &registry;
                    s.spawn(move || part.iter().filter_map(|a| normalize::normalize_auction(a, reg)).collect::<Vec<_>>())
                })
                .collect();
            handles.into_iter().flat_map(|h| h.join().unwrap_or_default()).collect()
        });
        let n = listings.len();
        self.state.write().replace_listings(listings, last_updated_ms);
        n
    }

    pub fn ingest_ended(&self, ended: &EndedAuctions) -> Vec<normalize::Sale> {
        let registry = self.registry();
        let sales: Vec<normalize::Sale> = ended.auctions.iter().filter_map(|a| normalize::normalize_sale(a, &registry)).collect();
        let mut st = self.state.write();
        st.add_sales(sales.clone(), now_ms());
        st.ended_updated_ms = ended.last_updated;
        sales
    }

    /// Seed sales loaded from persistence (no dedupe ids available -> synthetic ids).
    pub fn seed_sales(&self, sales: Vec<normalize::Sale>, history_since_ms: i64) {
        let mut st = self.state.write();
        st.add_sales(sales, now_ms());
        st.history_since_ms = st.history_since_ms.min(history_since_ms);
    }

    pub fn ingest_bazaar(&self, resp: BazaarResponse) {
        self.state.write().set_bazaar(resp.products, resp.last_updated);
    }

    /// Recompute flips (and craft/forge/kat views) from the current state.
    pub fn recompute(&self) {
        let now = now_ms();
        let registry = self.registry();
        let fees = self.fee_config();
        let defaults = &registry.curated.market.flip_defaults;
        let cfg = FlipConfig { listing_hours: defaults.listing_hours, min_profit: defaults.min_profit, min_roi: defaults.min_roi, listings_per_key: 3 };
        let names = |id: &str| registry.display_name(id);
        {
            let mut st = self.state.write();
            let flips = flips::scan(&mut st, &fees, &cfg, &self.fv_config, self.strategy.as_ref(), &names, now);
            st.flips = flips;
            st.flips_computed_ms = now;
        }
        let tax = self.bazaar_tax();
        let ah_fee = |p: f64| fees.bin_fees(p, defaults.listing_hours).total();
        let craft = craft::craft_flips(&registry, self, tax, &ah_fee, 50_000.0);
        let time = timeflip::time_flips(&registry, self, tax, &ah_fee);
        *self.derived.write() = Derived { craft, time, computed_ms: now };
    }

    // ------------------------------------------------------------------ queries

    pub fn freshness(&self) -> (Freshness, Freshness, Freshness) {
        let now = now_ms();
        let reg = self.registry();
        let f = &reg.curated.market.freshness;
        let st = self.state.read();
        (
            Freshness::compute("Auction House", st.auctions_updated_ms, now, f.auctions_stale_after),
            Freshness::compute("Ended auctions", st.ended_updated_ms, now, f.ended_auctions_stale_after),
            Freshness::compute("Bazaar", st.bazaar_updated_ms, now, f.bazaar_stale_after),
        )
    }

    pub fn flips(&self, q: &FlipQuery, collector_healthy: bool) -> FlipList {
        let (ah, _, _) = self.freshness();
        let st = self.state.read();
        FlipList {
            flips: flips::filter(&st.flips, q).into_iter().cloned().collect(),
            scanned_listings: st.active_bin_count() as u32,
            freshness: ah,
            collector_healthy,
        }
    }

    pub fn bazaar_list(&self, capital: f64) -> BazaarList {
        self.bazaar_list_for(capital, 0)
    }

    /// Bazaar flips for a player with `flipper_level` levels of the Bazaar Flipper upgrade.
    pub fn bazaar_list_for(&self, capital: f64, flipper_level: u32) -> BazaarList {
        let registry = self.registry();
        let names = |id: &str| registry.display_name(id);
        let tax = self.bazaar_tax_for(flipper_level);
        let st = self.state.read();
        let cfg = bazaar::BazaarFlipConfig { tax_rate: tax, capital: capital.max(1.0), ..Default::default() };
        let mut flips = bazaar::order_flips(&st, &cfg, &names);
        flips.extend(bazaar::npc_flips(&st, &|id| registry.npc_price(id), &names, capital.max(1.0)));
        let mut products: Vec<_> = st
            .bazaar
            .iter()
            .map(|(id, p)| {
                let h: Vec<_> = st.bazaar_history.get(id).map(|q| q.iter().copied().collect()).unwrap_or_default();
                bazaar::product_summary(id, names(id), p, &h)
            })
            .collect();
        products.sort_by(|a, b| (b.buy_moving_week * b.instant_buy.unwrap_or(0.0)).total_cmp(&(a.buy_moving_week * a.instant_buy.unwrap_or(0.0))));
        drop(st);
        let (_, _, bz) = self.freshness();
        BazaarList { flips, products, freshness: bz, tax_rate: tax }
    }

    pub fn craft_flips(&self) -> (Vec<CraftFlip>, i64) {
        let d = self.derived.read();
        (d.craft.clone(), d.computed_ms)
    }

    pub fn time_flips(&self) -> (Vec<TimeFlip>, i64) {
        let d = self.derived.read();
        (d.time.clone(), d.computed_ms)
    }

    pub fn search(&self, q: &str, limit: usize) -> Vec<ItemSearchResult> {
        let registry = self.registry();
        let st = self.state.read();
        registry
            .search_items(q, limit)
            .into_iter()
            .map(|i| ItemSearchResult {
                item_id: i.id.clone(),
                name: i.name.clone(),
                tier: i.tier.clone(),
                category: i.category.clone(),
                bazaar: st.bazaar.contains_key(&i.id),
                auctionable: st.by_market.contains_key(&i.id) || st.sales_by_market.contains_key(&i.id),
            })
            .collect()
    }

    fn fair_for(&self, key: &str) -> Option<FairValue> {
        let st = self.state.read();
        if let Some(f) = st.fair_cache.get(key) {
            return Some(f.clone());
        }
        let prices = st.bin_prices(key);
        let sales = st.sales.get(key).or_else(|| st.sales_by_market.get(key)).map(|q| q.iter().copied().collect::<Vec<_>>()).unwrap_or_default();
        if prices.is_empty() && sales.is_empty() {
            // plain market id with modifiers on every listing: fall back to all listings of that id
            // (exotic/collector pieces excluded - their prices say nothing about the item)
            let ids = st.by_market.get(key)?;
            let all: Vec<f64> =
                ids.iter().map(|i| &st.listings[*i]).filter(|l| l.bin && !l.comparable_key.contains("|exotic=")).map(|l| l.unit_price()).collect();
            if all.is_empty() {
                return None;
            }
            let fv = fair_value::compute(key, key, &all, &[], now_ms(), st.history_since_ms, &self.fv_config);
            return Some(fv);
        }
        let item_id = key.split('|').next().unwrap_or(key).to_string();
        let fv = fair_value::compute(key, &item_id, &prices, &sales, now_ms(), st.history_since_ms, &self.fv_config);
        drop(st);
        self.state.write().fair_cache.insert(key.to_string(), fv.clone());
        Some(fv)
    }

    /// In-memory price history buckets for charts (`range`: 1H, 6H, 24H, 7D, 30D, 90D).
    pub fn history_from_memory(&self, id: &str, range: &str) -> Vec<PricePoint> {
        let now = now_ms();
        let (span_ms, buckets) = range_spec(range);
        let bucket_ms = (span_ms / buckets as i64).max(60_000);
        let st = self.state.read();
        let mut out: Vec<PricePoint> = Vec::new();
        if let Some(h) = st.bazaar_history.get(id) {
            let mut map: HashMap<i64, Vec<(f64, f64, f64)>> = HashMap::new();
            for p in h.iter().filter(|p| now - p.ts_ms <= span_ms) {
                map.entry(p.ts_ms / bucket_ms * bucket_ms).or_default().push((p.buy, p.sell, p.buy_moving_week / 168.0));
            }
            let mut keys: Vec<i64> = map.keys().copied().collect();
            keys.sort();
            for k in keys {
                let v = &map[&k];
                let mids: Vec<f64> = v.iter().map(|(b, s, _)| (b + s) / 2.0).collect();
                out.push(PricePoint {
                    t: k,
                    median: skypilot_calc::stats::median(&mids),
                    low: v.iter().map(|x| x.1).reduce(f64::min),
                    high: v.iter().map(|x| x.0).reduce(f64::max),
                    volume: v.iter().map(|x| x.2).sum::<f64>() / v.len().max(1) as f64,
                });
            }
            return out;
        }
        let sales = st.sales_by_market.get(id).or_else(|| st.sales.get(id));
        if let Some(sales) = sales {
            let mut map: HashMap<i64, Vec<f64>> = HashMap::new();
            for s in sales.iter().filter(|s| now - s.ts_ms <= span_ms) {
                map.entry(s.ts_ms / bucket_ms * bucket_ms).or_default().push(s.unit_price);
            }
            let mut keys: Vec<i64> = map.keys().copied().collect();
            keys.sort();
            for k in keys {
                let v = &map[&k];
                out.push(PricePoint {
                    t: k,
                    median: skypilot_calc::stats::median(v),
                    low: skypilot_calc::stats::percentile(v, 0.1),
                    high: skypilot_calc::stats::percentile(v, 0.9),
                    volume: v.len() as f64,
                });
            }
        }
        out
    }

    pub fn price_detail(&self, id: &str, range: &str, extra_history: Option<Vec<PricePoint>>) -> PriceDetail {
        let registry = self.registry();
        let (ah, _, bz) = self.freshness();
        let bazaar = {
            let st = self.state.read();
            st.bazaar.get(id).map(|p| {
                let h: Vec<_> = st.bazaar_history.get(id).map(|q| q.iter().copied().collect()).unwrap_or_default();
                bazaar::product_summary(id, registry.display_name(id), p, &h)
            })
        };
        let fair_value = if bazaar.is_some() { None } else { self.fair_for(id) };
        let mut calc = craft::CraftCalculator::new(&registry, self);
        let craft = calc.craft_cost(id);
        let history = extra_history.filter(|h| !h.is_empty()).unwrap_or_else(|| self.history_from_memory(id, range));
        PriceDetail {
            item_id: id.to_string(),
            name: registry.display_name(id),
            tier: registry.item(id).and_then(|i| i.tier.clone()),
            fair_value,
            craft_cost: craft.as_ref().map(|(c, n, _)| c / n),
            craft_tree: craft.map(|(c, n, children)| skypilot_protocol::market::IngredientCost {
                item_id: id.to_string(),
                name: registry.display_name(id),
                amount: n,
                unit_price: Some(c / n),
                total: Some(c),
                source: "Craft".into(),
                children,
            }),
            npc_sell_price: registry.npc_price(id),
            freshness: if bazaar.is_some() { bz } else { ah },
            bazaar,
            history,
            range: range.to_string(),
        }
    }

    pub fn backtest(&self, req: &BacktestRequest) -> BacktestResult {
        let st = self.state.read();
        let candidates: Vec<_> = st.candidates.iter().cloned().collect();
        let sales: HashMap<String, Vec<state::SaleRecord>> = st.sales.iter().map(|(k, v)| (k.clone(), v.iter().copied().collect())).collect();
        drop(st);
        backtest::run(&candidates, &sales, req, &self.fee_config(), now_ms())
    }

    pub fn status(&self, circuit: &str, counters: Vec<(&'static str, u64)>) -> MarketStatus {
        let (a, e, b) = self.freshness();
        let st = self.state.read();
        MarketStatus {
            auctions: a,
            ended_auctions: e,
            bazaar: b,
            active_listings: st.listings.len() as u32,
            active_bin_listings: st.active_bin_count() as u32,
            tracked_price_keys: st.sales.len() as u32,
            sales_recorded: st.sales_recorded,
            hypixel_circuit: circuit.to_string(),
            last_error: st.last_error.clone(),
            counters: counters.into_iter().map(|(k, v)| (k.to_string(), v)).collect(),
        }
    }

    pub fn set_error(&self, e: Option<String>) {
        self.state.write().last_error = e;
    }
}

pub fn range_spec(range: &str) -> (i64, usize) {
    const H: i64 = 3_600_000;
    match range.to_ascii_uppercase().as_str() {
        "1H" => (H, 60),
        "6H" => (6 * H, 72),
        "24H" => (24 * H, 96),
        "7D" => (7 * 24 * H, 168),
        "30D" => (30 * 24 * H, 120),
        "90D" => (90 * 24 * H, 90),
        _ => (24 * H, 96),
    }
}

impl PriceOracle for MarketEngine {
    fn quote(&self, id: &str, side: PriceSide) -> Option<Quote> {
        {
            let st = self.state.read();
            if let Some(p) = st.bazaar.get(id) {
                let weekly = p.quick_status.buy_moving_week.max(p.quick_status.sell_moving_week);
                let spd = weekly / 7.0;
                let conf = if weekly > 10_000.0 { 0.95 } else if weekly > 500.0 { 0.75 } else { 0.45 };
                let price = match side {
                    PriceSide::Buy => bazaar::instant_buy(p).or_else(|| bazaar::instant_sell(p).map(|s| s * 1.1)),
                    PriceSide::Sell => bazaar::instant_sell(p).or_else(|| bazaar::instant_buy(p).map(|b| b * 0.9)),
                }?;
                return Some(Quote { price, source: "Bazaar", confidence: conf, sales_per_day: spd });
            }
        }
        if let Some(fv) = self.fair_for(id) {
            let price = match side {
                PriceSide::Buy => fv.lowest_bin.or(fv.value),
                PriceSide::Sell => fv.value,
            };
            if let Some(price) = price {
                let source = if (side == PriceSide::Buy && fv.lowest_bin.is_some()) || fv.comparables_used == 0 { "BIN" } else { "Sales" };
                return Some(Quote { price, source, confidence: fv.confidence.score, sales_per_day: fv.sales_per_day });
            }
        }
        let registry = self.registry();
        match side {
            PriceSide::Sell => registry.npc_price(id).filter(|p| *p > 0.0).map(|p| Quote { price: p, source: "NPC", confidence: 0.9, sales_per_day: 0.0 }),
            PriceSide::Buy => registry.neu.recipes.get(id).and_then(|rs| {
                rs.iter()
                    .filter_map(|r| match r {
                        skypilot_data::neu::Recipe::NpcShop { result, cost, .. } if cost.iter().all(|c| c.id == "SKYBLOCK_COIN") => {
                            Some(cost.iter().map(|c| c.amount).sum::<f64>() / result.amount.max(1.0))
                        }
                        _ => None,
                    })
                    .reduce(f64::min)
                    .map(|p| Quote { price: p, source: "NPC Shop", confidence: 0.95, sales_per_day: 0.0 })
            }),
        }
    }

    fn fair_value(&self, comparable_key: &str) -> Option<FairValue> {
        self.fair_for(comparable_key)
    }

    fn is_bazaar(&self, id: &str) -> bool {
        self.state.read().bazaar.contains_key(id)
    }
}
