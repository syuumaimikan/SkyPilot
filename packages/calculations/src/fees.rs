//! Auction House and Bazaar fee models. Rates come from `data/registry/market.toml`
//! (sourced from the official SkyBlock wiki) and are passed in via [`AhFeeConfig`].

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FeeBracket {
    /// Applies when price >= this threshold.
    pub min_price: f64,
    pub rate: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct DurationFee {
    pub hours: f64,
    pub fee: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AhFeeConfig {
    /// BIN creation fee brackets, sorted by min_price ascending.
    pub bin_brackets: Vec<FeeBracket>,
    /// Normal (bid) auction creation fee rate on the starting bid.
    pub auction_creation_rate: f64,
    pub duration_fees: Vec<DurationFee>,
    /// Collection tax applied when collecting coins above `claim_tax_threshold`.
    pub claim_tax_rate: f64,
    pub claim_tax_threshold: f64,
    /// Multiplier on the claim tax while a mayor perk (e.g. Derpy) is active.
    #[serde(default = "one")]
    pub claim_tax_multiplier: f64,
}

fn one() -> f64 {
    1.0
}

impl Default for AhFeeConfig {
    fn default() -> Self {
        AhFeeConfig {
            bin_brackets: vec![
                FeeBracket { min_price: 0.0, rate: 0.01 },
                FeeBracket { min_price: 10_000_000.0, rate: 0.02 },
                FeeBracket { min_price: 100_000_000.0, rate: 0.025 },
            ],
            auction_creation_rate: 0.05,
            duration_fees: vec![
                DurationFee { hours: 1.0, fee: 20.0 },
                DurationFee { hours: 6.0, fee: 45.0 },
                DurationFee { hours: 12.0, fee: 100.0 },
                DurationFee { hours: 24.0, fee: 350.0 },
                DurationFee { hours: 48.0, fee: 1200.0 },
                DurationFee { hours: 336.0, fee: 55_200.0 },
            ],
            claim_tax_rate: 0.01,
            claim_tax_threshold: 1_000_000.0,
            claim_tax_multiplier: 1.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct AhFees {
    pub listing_fee: f64,
    pub duration_fee: f64,
    pub claim_tax: f64,
}

impl AhFees {
    pub fn total(&self) -> f64 {
        self.listing_fee + self.duration_fee + self.claim_tax
    }
}

fn sane(price: f64) -> f64 {
    if price.is_finite() { price.max(0.0) } else { 0.0 }
}

impl AhFeeConfig {
    pub fn bin_rate(&self, price: f64) -> f64 {
        let price = sane(price);
        self.bin_brackets.iter().filter(|b| price >= b.min_price).map(|b| b.rate).last().unwrap_or(0.0)
    }

    pub fn bin_listing_fee(&self, price: f64) -> f64 {
        sane(price) * self.bin_rate(price)
    }

    pub fn auction_listing_fee(&self, starting_bid: f64) -> f64 {
        sane(starting_bid) * self.auction_creation_rate
    }

    /// Fee for the smallest configured duration >= `hours` (or the largest available).
    pub fn duration_fee(&self, hours: f64) -> f64 {
        let hours = sane(hours);
        let mut sorted = self.duration_fees.clone();
        sorted.sort_by(|a, b| a.hours.total_cmp(&b.hours));
        sorted.iter().find(|d| d.hours >= hours).or(sorted.last()).map(|d| d.fee).unwrap_or(0.0)
    }

    /// Claim tax: `rate` of the sale, never bringing the collected amount below the threshold.
    pub fn claim_tax(&self, sale_price: f64) -> f64 {
        let p = sane(sale_price);
        if p <= self.claim_tax_threshold {
            return 0.0;
        }
        let tax = p * self.claim_tax_rate * self.claim_tax_multiplier.max(0.0);
        tax.min(p - self.claim_tax_threshold).max(0.0)
    }

    /// Total fees when listing as BIN at `sale_price` for `hours` and collecting.
    pub fn bin_fees(&self, sale_price: f64, hours: f64) -> AhFees {
        AhFees {
            listing_fee: self.bin_listing_fee(sale_price),
            duration_fee: self.duration_fee(hours),
            claim_tax: self.claim_tax(sale_price),
        }
    }

    /// Coins the seller ends up with after all fees.
    pub fn bin_net_proceeds(&self, sale_price: f64, hours: f64) -> f64 {
        sane(sale_price) - self.bin_fees(sale_price, hours).total()
    }

    /// Largest purchase price for which reselling at `sale_price` still yields at least
    /// `min_profit` and `min_roi` (ROI measured on the purchase price).
    pub fn max_buy_price(&self, sale_price: f64, hours: f64, min_profit: f64, min_roi: f64) -> f64 {
        let net = self.bin_net_proceeds(sale_price, hours);
        let by_profit = net - sane(min_profit);
        let by_roi = net / (1.0 + sane(min_roi));
        by_profit.min(by_roi).max(0.0)
    }
}

/// Bazaar: coins received after tax when selling `amount` at `price`.
pub fn bazaar_net(price: f64, amount: f64, tax_rate: f64) -> f64 {
    let rate = if tax_rate.is_finite() { tax_rate.clamp(0.0, 1.0) } else { 0.0 };
    sane(price) * sane(amount) * (1.0 - rate)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn brackets() {
        let c = AhFeeConfig::default();
        assert_eq!(c.bin_rate(5_000_000.0), 0.01);
        assert_eq!(c.bin_rate(10_000_000.0), 0.02);
        assert_eq!(c.bin_rate(150_000_000.0), 0.025);
        assert_eq!(c.claim_tax(900_000.0), 0.0);
        assert_eq!(c.claim_tax(1_000_500.0), 500.0); // capped so we keep 1M
        assert_eq!(c.claim_tax(50_000_000.0), 500_000.0);
        assert_eq!(c.duration_fee(2.0), 45.0);
        assert_eq!(c.duration_fee(1000.0), 55_200.0);
    }
}
