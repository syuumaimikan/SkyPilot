//! Bazaar engine: product summaries, order flips with a fill-time estimator, and
//! NPC <-> Bazaar arbitrage ("instant" flips that need no order queue).

use skypilot_calc::stats;
use skypilot_hypixel::BazaarProductRaw;
use skypilot_protocol::common::{Confidence, Range, RiskLevel};
use skypilot_protocol::market::{BazaarFlip, BazaarFlipKind, BazaarProduct};

use crate::state::{BazaarPoint, MarketState};

pub fn instant_buy(p: &BazaarProductRaw) -> Option<f64> {
    p.buy_summary.first().map(|o| o.price_per_unit).filter(|x| x.is_finite() && *x > 0.0)
}

pub fn instant_sell(p: &BazaarProductRaw) -> Option<f64> {
    p.sell_summary.first().map(|o| o.price_per_unit).filter(|x| x.is_finite() && *x > 0.0)
}

fn mids(history: &[BazaarPoint]) -> Vec<f64> {
    history.iter().filter(|h| h.buy > 0.0 && h.sell > 0.0).map(|h| (h.buy + h.sell) / 2.0).collect()
}

pub fn product_summary(id: &str, name: String, p: &BazaarProductRaw, history: &[BazaarPoint]) -> BazaarProduct {
    let buy = instant_buy(p);
    let sell = instant_sell(p);
    let spread = match (buy, sell) {
        (Some(b), Some(s)) => Some(b - s),
        _ => None,
    };
    let m = mids(history);
    BazaarProduct {
        product_id: id.to_string(),
        name,
        instant_buy: buy,
        instant_sell: sell,
        spread,
        spread_pct: match (spread, sell) {
            (Some(sp), Some(s)) if s > 0.0 => Some(sp / s),
            _ => None,
        },
        buy_volume: p.quick_status.buy_volume,
        sell_volume: p.quick_status.sell_volume,
        buy_moving_week: p.quick_status.buy_moving_week,
        sell_moving_week: p.quick_status.sell_moving_week,
        buy_orders: p.quick_status.buy_orders as u32,
        sell_orders: p.quick_status.sell_orders as u32,
        trend: stats::trend(&m),
        volatility: stats::coefficient_of_variation(&m),
    }
}

/// Which order book side an order waits on.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OrderSide {
    /// Buy order: filled by players instant-selling (throughput = sellMovingWeek).
    BuyOrder,
    /// Sell offer: filled by players instant-buying (throughput = buyMovingWeek).
    SellOffer,
}

/// Expected minutes until an order of `amount` placed at the top of the book fills.
/// Uses weekly throughput, order-book depth at the top (re-undercut pressure),
/// recent movement and volatility to widen the range.
pub fn estimate_fill(p: &BazaarProductRaw, side: OrderSide, amount: f64, history: &[BazaarPoint]) -> Option<Range> {
    let (weekly, top_depth) = match side {
        OrderSide::BuyOrder => (p.quick_status.sell_moving_week, p.sell_summary.first().map(|o| o.amount).unwrap_or(0.0)),
        OrderSide::SellOffer => (p.quick_status.buy_moving_week, p.buy_summary.first().map(|o| o.amount).unwrap_or(0.0)),
    };
    if !weekly.is_finite() || weekly <= 0.0 || !amount.is_finite() || amount <= 0.0 {
        return None;
    }
    let per_minute = weekly / (7.0 * 24.0 * 60.0);
    // Competitors re-undercut: assume a quarter of the current top level ends up ahead of us.
    let queue = amount + 0.25 * top_depth.max(0.0);
    let base = queue / per_minute;
    let m = mids(history);
    let cv = stats::coefficient_of_variation(&m).unwrap_or(0.1).min(1.0);
    let movement = stats::trend(&m).map(f64::abs).unwrap_or(0.0).min(1.0);
    let spread_factor = 0.5 + cv + movement;
    Some(Range { low: (base * (1.0 - 0.5 * spread_factor).max(0.2)).max(0.1), high: base * (1.0 + spread_factor) })
}

#[derive(Debug, Clone)]
pub struct BazaarFlipConfig {
    pub tax_rate: f64,
    pub capital: f64,
    /// Share of hourly throughput one trader can realistically take.
    pub market_share: f64,
    /// Units traded per week on each side (an order should fill more than once a day).
    pub min_weekly_volume: f64,
    /// Coins traded per week on each side: cheap items need many units, expensive ones few.
    pub min_weekly_coins: f64,
    pub min_profit_per_hour: f64,
}

impl Default for BazaarFlipConfig {
    fn default() -> Self {
        BazaarFlipConfig {
            tax_rate: 0.0125,
            capital: 50_000_000.0,
            market_share: 0.1,
            min_weekly_volume: 300.0,
            min_weekly_coins: 150_000_000.0,
            min_profit_per_hour: 50_000.0,
        }
    }
}

/// How the current top of the book compares with the recent history of one product.
#[derive(Debug, Clone, PartialEq)]
pub struct PriceCheck {
    /// Sell price to plan with: the current best offer, but never far above its usual level.
    pub planned_sell: f64,
    /// The price looks pushed (offers far above / orders far below their usual level).
    pub manipulated: bool,
    pub notes: Vec<String>,
}

/// Points used for "usual level": the last day of 15-minute samples.
const USUAL_POINTS: usize = 96;
/// Fewer samples than this and no history check is made.
const MIN_POINTS: usize = 8;

/// Compare the planned buy-order / sell-offer prices with their recent medians. A sell offer
/// well above its usual level is not planned at face value (it may be a pushed price that
/// collapses before the offer fills), and a buy price well below the usual instant-sell
/// level means someone is dumping.
pub fn price_check(buy: f64, sell: f64, history: &[BazaarPoint]) -> PriceCheck {
    let recent: Vec<&BazaarPoint> = history.iter().rev().take(USUAL_POINTS).filter(|h| h.buy > 0.0 && h.sell > 0.0).collect();
    if recent.len() < MIN_POINTS {
        return PriceCheck { planned_sell: sell, manipulated: false, notes: Vec::new() };
    }
    let usual_offer = stats::median(&recent.iter().map(|h| h.buy).collect::<Vec<_>>()).unwrap_or(sell);
    let usual_order = stats::median(&recent.iter().map(|h| h.sell).collect::<Vec<_>>()).unwrap_or(buy);
    let mut notes = Vec::new();
    let mut manipulated = false;
    let mut planned_sell = sell;
    if usual_offer > 0.0 && sell > usual_offer * 1.35 {
        manipulated = true;
        planned_sell = sell.min(usual_offer * 1.05);
        notes.push(format!("Sell offers are {:.0}% above their usual level: planned at the usual price", (sell / usual_offer - 1.0) * 100.0));
    }
    if usual_order > 0.0 && buy < usual_order * 0.65 {
        manipulated = true;
        notes.push(format!("Buy orders are {:.0}% below their usual level: the price may be crashing", (1.0 - buy / usual_order) * 100.0));
    }
    PriceCheck { planned_sell, manipulated, notes }
}

pub fn order_flips(state: &MarketState, cfg: &BazaarFlipConfig, names: &dyn Fn(&str) -> String) -> Vec<BazaarFlip> {
    let mut out = Vec::new();
    for (id, p) in &state.bazaar {
        let (Some(best_order), Some(best_offer)) = (instant_sell(p), instant_buy(p)) else { continue };
        let buy = best_order + 0.1;
        let sell = best_offer - 0.1;
        if sell <= buy {
            continue;
        }
        let qs = &p.quick_status;
        if qs.buy_moving_week < cfg.min_weekly_volume || qs.sell_moving_week < cfg.min_weekly_volume {
            continue;
        }
        if qs.buy_moving_week * sell < cfg.min_weekly_coins || qs.sell_moving_week * buy < cfg.min_weekly_coins {
            continue;
        }
        let history: Vec<BazaarPoint> = state.bazaar_history.get(id).map(|q| q.iter().copied().collect()).unwrap_or_default();
        let check = price_check(buy, sell, &history);
        let sell = check.planned_sell;
        if sell <= buy {
            continue;
        }
        let tax = sell * cfg.tax_rate;
        let per_unit = sell - tax - buy;
        if per_unit <= 0.0 {
            continue;
        }
        let hourly = qs.buy_moving_week.min(qs.sell_moving_week) / 168.0;
        let amount = (hourly * cfg.market_share).min(cfg.capital / buy).floor();
        if amount < 1.0 {
            continue;
        }
        let buy_fill = estimate_fill(p, OrderSide::BuyOrder, amount, &history);
        let sell_fill = estimate_fill(p, OrderSide::SellOffer, amount, &history);
        let cycle_minutes = match (buy_fill, sell_fill) {
            (Some(a), Some(b)) => (a.high + b.high).max(1.0),
            _ => continue,
        };
        let expected_profit = per_unit * amount;
        let profit_per_hour = expected_profit / (cycle_minutes / 60.0);
        if profit_per_hour < cfg.min_profit_per_hour {
            continue;
        }
        let m = mids(&history);
        let cv = stats::coefficient_of_variation(&m).unwrap_or(0.15);
        let spread_pct = (best_offer - best_order) / best_order;
        let mut notes = check.notes.clone();
        let falling = stats::trend(&m).filter(|t| *t < -0.15);
        if let Some(t) = falling {
            notes.push(format!("Price trending down ({:.0}%)", t * 100.0));
        }
        let risk = if check.manipulated {
            RiskLevel::VeryHigh
        } else if spread_pct > 0.6 && hourly < 500.0 {
            notes.push("Wide spread on thin volume: price can be moved by a few orders".into());
            RiskLevel::High
        } else if cv > 0.25 {
            RiskLevel::Medium
        } else if cv > 0.08 {
            RiskLevel::Low
        } else {
            RiskLevel::VeryLow
        };
        let history_factor = (m.len() as f64 / 96.0).min(1.0); // ~1 day of 15-min points
        if history_factor < 0.3 {
            notes.push("Little price history collected yet".into());
        }
        let mut score_c = (0.4 + 0.4 * (1.0 - cv.min(1.0)) + 0.2 * history_factor).clamp(0.0, 1.0);
        let mut reasons: Vec<String> = if history_factor < 0.3 { vec!["Limited history".into()] } else { vec![] };
        if check.manipulated {
            score_c *= 0.4;
            reasons.push("Possible price manipulation".into());
        }
        if falling.is_some() {
            score_c *= 0.8;
            reasons.push("Falling price".into());
        }
        let confidence = Confidence::from_score(score_c, reasons);
        let score = profit_per_hour * confidence.score / risk.weight();
        out.push(BazaarFlip {
            kind: BazaarFlipKind::OrderFlip,
            product_id: id.clone(),
            name: names(id),
            buy_price: buy,
            sell_price: sell,
            tax,
            profit_per_unit: per_unit,
            roi: per_unit / buy,
            suggested_amount: amount,
            expected_profit,
            capital_required: buy * amount,
            buy_fill_minutes: buy_fill,
            sell_fill_minutes: sell_fill,
            profit_per_hour: Some(profit_per_hour),
            risk,
            confidence,
            score,
            notes,
            manipulated: check.manipulated,
        });
    }
    out.sort_by(|a, b| b.score.total_cmp(&a.score));
    out
}

/// Bazaar -> NPC arbitrage: instantly buy below the NPC sell price (no tax on NPC sales).
pub fn npc_flips(state: &MarketState, npc_price: &dyn Fn(&str) -> Option<f64>, names: &dyn Fn(&str) -> String, capital: f64) -> Vec<BazaarFlip> {
    let mut out = Vec::new();
    for (id, p) in &state.bazaar {
        let (Some(npc), Some(buy)) = (npc_price(id), instant_buy(p)) else { continue };
        if npc <= buy {
            continue;
        }
        let available: f64 = p.buy_summary.iter().take_while(|o| o.price_per_unit < npc).map(|o| o.amount).sum();
        let amount = available.min(capital / buy).floor();
        if amount < 1.0 {
            continue;
        }
        let per_unit = npc - buy;
        out.push(BazaarFlip {
            kind: BazaarFlipKind::InstantFlip,
            product_id: id.clone(),
            name: names(id),
            buy_price: buy,
            sell_price: npc,
            tax: 0.0,
            profit_per_unit: per_unit,
            roi: per_unit / buy,
            suggested_amount: amount,
            expected_profit: per_unit * amount,
            capital_required: buy * amount,
            buy_fill_minutes: Some(Range { low: 0.0, high: 1.0 }),
            sell_fill_minutes: Some(Range { low: 0.0, high: 1.0 }),
            profit_per_hour: None,
            risk: RiskLevel::VeryLow,
            confidence: Confidence::from_score(0.9, vec!["NPC sell limits per day may apply".into()]),
            score: per_unit * amount,
            notes: vec!["Instant-buy on the Bazaar, sell to an NPC merchant. NPC daily sell limits are not modelled.".into()],
            manipulated: false,
        });
    }
    out.sort_by(|a, b| b.expected_profit.total_cmp(&a.expected_profit));
    out
}
