//! Raw Hypixel auctions -> normalised listings/sales keyed by item fingerprint.

use skypilot_data::Registry;
use skypilot_hypixel::{EndedAuction, RawAuction};
use skypilot_items::{ItemFingerprint, ParsedItem, decode_blob, item::inventory_from_tag};

#[derive(Debug, Clone, PartialEq)]
pub struct Listing {
    pub auction_id: String,
    pub market_id: String,
    pub comparable_key: String,
    pub exact_key: String,
    pub name: String,
    pub rarity: Option<String>,
    pub modifiers: Vec<String>,
    pub bin: bool,
    /// Total price of the auction (BIN price or current/starting bid).
    pub price: f64,
    pub count: u32,
    pub seller: String,
    pub start_ms: i64,
    pub end_ms: i64,
    pub soulbound: bool,
}

impl Listing {
    pub fn unit_price(&self) -> f64 {
        self.price / self.count.max(1) as f64
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Sale {
    pub auction_id: String,
    pub market_id: String,
    pub comparable_key: String,
    pub unit_price: f64,
    pub count: u32,
    pub bin: bool,
    pub ts_ms: i64,
}

/// Decode + fingerprint an item, resolving pet levels and default colours through the registry.
pub fn fingerprint_item(item: &ParsedItem, registry: &Registry) -> Option<ItemFingerprint> {
    let mut fp = ItemFingerprint::from_item(item)?;
    fp.mark_exotic(registry.item(&fp.skyblock_id).and_then(|d| d.color.as_deref()));
    if let Some(p) = fp.pet.as_mut() {
        let table = registry.neu.pets.table(&p.pet_type, &p.tier);
        p.level = Some(table.progress(p.exp).level);
    }
    Some(fp)
}

pub fn decode_first_item(item_bytes: &str) -> Option<ParsedItem> {
    let root = decode_blob(item_bytes).ok()?;
    inventory_from_tag(&root).into_iter().flatten().next()
}

pub fn normalize_auction(raw: &RawAuction, registry: &Registry) -> Option<Listing> {
    if raw.claimed || !raw.starting_bid.is_finite() || raw.starting_bid <= 0.0 {
        return None;
    }
    let item = decode_first_item(&raw.item_bytes)?;
    let fp = fingerprint_item(&item, registry)?;
    let price = if raw.bin { raw.starting_bid } else { raw.highest_bid_amount.max(raw.starting_bid) };
    if !price.is_finite() || price <= 0.0 {
        return None;
    }
    Some(Listing {
        auction_id: raw.uuid.clone(),
        market_id: fp.market_id.clone(),
        comparable_key: fp.comparable_key(),
        exact_key: fp.exact_key(),
        name: if item.name.is_empty() { raw.item_name.clone() } else { item.name.clone() },
        rarity: item.rarity.map(|r| r.as_str().to_string()).or_else(|| (!raw.tier.is_empty()).then(|| raw.tier.clone())),
        modifiers: fp.modifiers(),
        bin: raw.bin,
        price,
        count: fp.count,
        seller: raw.auctioneer.clone(),
        start_ms: raw.start,
        end_ms: raw.end,
        soulbound: fp.soulbound,
    })
}

pub fn normalize_sale(raw: &EndedAuction, registry: &Registry) -> Option<Sale> {
    if !raw.price.is_finite() || raw.price <= 0.0 {
        return None;
    }
    let item = decode_first_item(&raw.item_bytes)?;
    let fp = fingerprint_item(&item, registry)?;
    let count = fp.count.max(1);
    Some(Sale {
        auction_id: raw.auction_id.clone(),
        comparable_key: fp.comparable_key(),
        market_id: fp.market_id,
        unit_price: raw.price / count as f64,
        count,
        bin: raw.bin,
        ts_ms: raw.timestamp,
    })
}
