//! `ItemFingerprint`: a normalised description of everything that changes an item's value.
//! Two auctions of "Hyperion" are not comparable if one has 5 stars, a recombobulator and
//! a skin. Fingerprints produce three keys:
//!
//! * `market_id` – what the item *is* (pets by type+tier, books by enchant+level).
//! * `comparable_key` – market id + modifiers that dominate price (recomb, stars, skin,
//!   pet level bucket, ultimate enchant, attributes). Used for fair value comparables.
//! * `exact_key` – every modifier, used to deduplicate and for exact matches.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::item::ParsedItem;
use crate::nbt::Tag;
use crate::text::Rarity;

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub struct GemSlot {
    pub slot: String,
    pub gem: String,
    pub quality: String,
}

impl GemSlot {
    /// Bazaar product id of the socketed gemstone, e.g. `PERFECT_SAPPHIRE_GEM`.
    pub fn product_id(&self) -> String {
        format!("{}_{}_GEM", self.quality, self.gem)
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PetFingerprint {
    pub pet_type: String,
    pub tier: String,
    pub exp: f64,
    pub level: Option<u32>,
    pub held_item: Option<String>,
    pub candy_used: u32,
    pub skin: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ItemFingerprint {
    pub skyblock_id: String,
    pub market_id: String,
    pub rarity: Option<Rarity>,
    pub reforge: Option<String>,
    pub stars: u8,
    pub master_stars: u8,
    pub recombobulated: bool,
    pub enchantments: BTreeMap<String, u32>,
    pub ultimate_enchant: Option<(String, u32)>,
    pub gemstones: Vec<GemSlot>,
    pub unlocked_gem_slots: u8,
    pub attributes: BTreeMap<String, u32>,
    pub hot_potato_books: u8,
    pub fuming_books: u8,
    pub art_of_war: bool,
    pub art_of_peace: bool,
    pub skin: Option<String>,
    pub dye: Option<String>,
    /// Leather colour stored on the item (`ExtraAttributes.color`, normalised "r:g:b").
    pub color: Option<String>,
    /// The stored colour differs from the item's default colour: an exotic/collector piece
    /// whose price has nothing to do with the normal item. Needs the item registry
    /// (see [`ItemFingerprint::mark_exotic`]).
    pub exotic: bool,
    pub pet: Option<PetFingerprint>,
    pub edition: Option<u32>,
    pub new_year_cake_year: Option<u32>,
    pub runes: BTreeMap<String, u32>,
    pub drill_parts: Vec<String>,
    pub ability_scrolls: Vec<String>,
    pub enrichment: Option<String>,
    /// Other counted upgrades (mana disintegrators, polarvoid books, wood singularity...).
    pub misc: BTreeMap<String, i64>,
    pub soulbound: bool,
    pub count: u32,
}

/// Counted single-value upgrades whose application changes item value.
const COUNTED_UPGRADES: &[&str] = &[
    "mana_disintegrator_count",
    "jalapeno_count",
    "wood_singularity_count",
    "polarvoid",
    "divan_powder_coating",
    "ethermerge",
    "tuned_transmission",
    "farming_for_dummies_count",
    "thunder_charge",
    "magma_cube_absorber",
    "stats_book",
    "gemstone_powder_scroll",
    "pocket_sack_in_a_sack",
    "bookworm_books",
    "power_ability_scroll",
    "midas_weapon",
    "winning_bid",
    "blaze_consumer",
];

fn level_u32(t: &Tag) -> Option<u32> {
    t.as_i64().filter(|v| *v >= 0 && *v < 10_000).map(|v| v as u32)
}

fn compound_levels(t: Option<&Tag>) -> BTreeMap<String, u32> {
    t.and_then(Tag::as_compound)
        .map(|m| m.iter().filter_map(|(k, v)| level_u32(v).map(|l| (k.to_ascii_lowercase(), l))).collect())
        .unwrap_or_default()
}

const GEM_TYPES: &[&str] = &[
    "RUBY", "AMBER", "SAPPHIRE", "JADE", "AMETHYST", "TOPAZ", "JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT",
];

fn parse_gems(t: Option<&Tag>) -> (Vec<GemSlot>, u8) {
    let Some(map) = t.and_then(Tag::as_compound) else { return (Vec::new(), 0) };
    let unlocked = map.get("unlocked_slots").map(|u| u.as_list().len()).unwrap_or(0).min(255) as u8;
    let mut out = Vec::new();
    for (slot, value) in map {
        if slot.ends_with("_gem") || slot == "unlocked_slots" {
            continue;
        }
        let quality = match value {
            Tag::String(s) => s.clone(),
            Tag::Compound(_) => match value.get("quality").and_then(Tag::as_str) {
                Some(q) => q.to_string(),
                None => continue,
            },
            _ => continue,
        };
        let prefix = slot.rsplit_once('_').map(|(p, _)| p).unwrap_or(slot);
        let gem = if GEM_TYPES.contains(&prefix) {
            prefix.to_string()
        } else if let Some(g) = map.get(&format!("{slot}_gem")).and_then(Tag::as_str) {
            g.to_string()
        } else {
            continue;
        };
        out.push(GemSlot { slot: slot.clone(), gem, quality: quality.to_ascii_uppercase() });
    }
    out.sort();
    (out, unlocked)
}

impl ItemFingerprint {
    pub fn from_item(item: &ParsedItem) -> Option<ItemFingerprint> {
        let id = item.skyblock_id.clone()?;
        let a = &item.attributes;
        let upgrade = a.get("upgrade_level").or_else(|| a.get("dungeon_item_level")).and_then(Tag::as_i64).unwrap_or(0).clamp(0, 10) as u8;
        let hpb_total = a.get("hot_potato_count").and_then(Tag::as_i64).unwrap_or(0).clamp(0, 15) as u8;
        let enchantments = compound_levels(a.get("enchantments"));
        let ultimate_enchant = enchantments
            .iter()
            .find(|(k, _)| k.starts_with("ultimate_"))
            .map(|(k, v)| (k.clone(), *v));
        let (gemstones, unlocked_gem_slots) = parse_gems(a.get("gems"));
        let pet = item.pet_info().map(|p| PetFingerprint {
            pet_type: p.pet_type.to_ascii_uppercase(),
            tier: p.tier.to_ascii_uppercase(),
            exp: if p.exp.is_finite() { p.exp.max(0.0) } else { 0.0 },
            level: None,
            held_item: p.held_item,
            candy_used: p.candy_used,
            skin: p.skin,
        });
        let mut misc = BTreeMap::new();
        for key in COUNTED_UPGRADES {
            if let Some(v) = a.get(key).and_then(Tag::as_i64) {
                if v != 0 {
                    misc.insert((*key).to_string(), v);
                }
            }
        }
        let drill_parts = ["drill_part_engine", "drill_part_fuel_tank", "drill_part_upgrade_module"]
            .iter()
            .filter_map(|k| a.get(k).and_then(Tag::as_str).map(|s| s.to_ascii_uppercase()))
            .collect();
        let ability_scrolls = a
            .get("ability_scroll")
            .map(|l| l.as_list().iter().filter_map(|t| t.as_str().map(str::to_string)).collect::<Vec<_>>())
            .unwrap_or_default();

        let mut fp = ItemFingerprint {
            market_id: String::new(),
            rarity: item.rarity,
            reforge: a.get("modifier").and_then(Tag::as_str).map(|s| s.to_ascii_lowercase()),
            stars: upgrade.min(5),
            master_stars: upgrade.saturating_sub(5),
            recombobulated: item.is_recombobulated(),
            ultimate_enchant,
            enchantments,
            gemstones,
            unlocked_gem_slots,
            attributes: compound_levels(a.get("attributes")),
            hot_potato_books: hpb_total.min(10),
            fuming_books: hpb_total.saturating_sub(10),
            art_of_war: a.get("art_of_war_count").and_then(Tag::as_i64).unwrap_or(0) > 0,
            art_of_peace: a.get("art_of_peace_applied").and_then(Tag::as_i64).unwrap_or(0) > 0,
            skin: a.get("skin").and_then(Tag::as_str).map(str::to_string),
            dye: a.get("dye_item").and_then(Tag::as_str).map(str::to_string),
            color: a.get("color").and_then(Tag::as_str).and_then(normalize_color),
            exotic: false,
            pet,
            edition: a.get("edition").and_then(level_u32),
            new_year_cake_year: a.get("new_years_cake").and_then(level_u32),
            runes: compound_levels(a.get("runes")),
            drill_parts,
            ability_scrolls,
            enrichment: a.get("talisman_enrichment").and_then(Tag::as_str).map(str::to_string),
            misc,
            soulbound: item.soulbound,
            count: item.count.max(1),
            skyblock_id: id,
        };
        fp.market_id = fp.compute_market_id();
        Some(fp)
    }

    /// Flags the item as exotic when its stored colour is not `default_color` (the Hypixel
    /// items resource's `color`). Dyed items (`dye_item`) are priced as normal items.
    pub fn mark_exotic(&mut self, default_color: Option<&str>) {
        self.exotic = match (&self.color, default_color) {
            (Some(c), Some(d)) => self.dye.is_none() && normalize_color(d).as_deref() != Some(c.as_str()),
            _ => false,
        };
    }

    fn compute_market_id(&self) -> String {
        let id = self.skyblock_id.to_ascii_uppercase();
        match id.as_str() {
            "PET" => {
                if let Some(p) = &self.pet {
                    return format!("PET:{}:{}", p.pet_type, p.tier);
                }
                id
            }
            "ENCHANTED_BOOK" if self.enchantments.len() == 1 => {
                let (name, lvl) = self.enchantments.iter().next().unwrap();
                format!("ENCHANTMENT_{}_{}", name.to_ascii_uppercase(), lvl)
            }
            "RUNE" | "UNIQUE_RUNE" if self.runes.len() == 1 => {
                let (name, lvl) = self.runes.iter().next().unwrap();
                format!("RUNE:{}:{}", name.to_ascii_uppercase(), lvl)
            }
            "ATTRIBUTE_SHARD" if self.attributes.len() == 1 => {
                let (name, lvl) = self.attributes.iter().next().unwrap();
                format!("ATTRIBUTE_SHARD:{}:{}", name.to_ascii_uppercase(), lvl)
            }
            "NEW_YEAR_CAKE" => match self.new_year_cake_year {
                Some(y) => format!("NEW_YEAR_CAKE:{y}"),
                None => id,
            },
            _ => id,
        }
    }

    /// Pet level bucket used for comparables.
    pub fn pet_level_bucket(level: u32) -> &'static str {
        match level {
            0..=79 => "1-79",
            80..=98 => "80-98",
            99..=100 => "99-100",
            101..=199 => "101-199",
            _ => "200",
        }
    }

    pub fn comparable_key(&self) -> String {
        let mut parts = vec![self.market_id.clone()];
        if self.recombobulated && self.pet.is_none() {
            parts.push("recomb".into());
        }
        let total_stars = self.stars + self.master_stars;
        if total_stars > 0 {
            parts.push(format!("s{total_stars}"));
        }
        if let Some(s) = &self.skin {
            parts.push(format!("skin={s}"));
        }
        if let Some(p) = &self.pet {
            if let Some(l) = p.level {
                parts.push(format!("lvl={}", Self::pet_level_bucket(l)));
            }
            if let Some(s) = &p.skin {
                parts.push(format!("skin={s}"));
            }
            if p.held_item.as_deref() == Some("PET_ITEM_TIER_BOOST") {
                parts.push("tb".into());
            }
        }
        if let Some((name, lvl)) = &self.ultimate_enchant {
            if self.market_id == self.skyblock_id.to_ascii_uppercase() {
                parts.push(format!("{name}{lvl}"));
            }
        }
        if !self.attributes.is_empty() && !self.market_id.starts_with("ATTRIBUTE_SHARD:") {
            for (k, v) in &self.attributes {
                parts.push(format!("{k}{v}"));
            }
        }
        if let Some(e) = self.edition {
            // low editions are collectible
            if e <= 100 {
                parts.push(format!("ed<=100"));
            }
        }
        if self.exotic {
            if let Some(c) = &self.color {
                parts.push(format!("exotic={c}"));
            }
        }
        parts.join("|")
    }

    pub fn exact_key(&self) -> String {
        let mut parts = vec![self.comparable_key()];
        if let Some(r) = &self.reforge {
            parts.push(format!("ref={r}"));
        }
        if self.hot_potato_books > 0 {
            parts.push(format!("hpb{}", self.hot_potato_books));
        }
        if self.fuming_books > 0 {
            parts.push(format!("fpb{}", self.fuming_books));
        }
        if self.art_of_war {
            parts.push("aow".into());
        }
        if self.art_of_peace {
            parts.push("aop".into());
        }
        for (k, v) in &self.enchantments {
            parts.push(format!("e:{k}{v}"));
        }
        for g in &self.gemstones {
            parts.push(format!("g:{}:{}", g.gem, g.quality));
        }
        for (k, v) in &self.runes {
            parts.push(format!("r:{k}{v}"));
        }
        for p in &self.drill_parts {
            parts.push(format!("d:{p}"));
        }
        for s in &self.ability_scrolls {
            parts.push(format!("as:{s}"));
        }
        if let Some(e) = &self.enrichment {
            parts.push(format!("enr:{e}"));
        }
        for (k, v) in &self.misc {
            parts.push(format!("m:{k}={v}"));
        }
        if let Some(d) = &self.dye {
            parts.push(format!("dye={d}"));
        }
        if let Some(p) = &self.pet {
            if let Some(h) = &p.held_item {
                parts.push(format!("held={h}"));
            }
            if p.candy_used > 0 {
                parts.push(format!("candy{}", p.candy_used));
            }
        }
        parts.join("|")
    }

    /// Human readable modifier list for the UI.
    pub fn modifiers(&self) -> Vec<String> {
        let mut out = Vec::new();
        if self.stars > 0 {
            out.push(format!("{}✪", self.stars));
        }
        if self.master_stars > 0 {
            out.push(format!("Master ✪{}", self.master_stars));
        }
        if self.recombobulated {
            out.push("Recombobulated".into());
        }
        if let Some(r) = &self.reforge {
            out.push(format!("Reforge: {r}"));
        }
        if let Some((n, l)) = &self.ultimate_enchant {
            out.push(format!("{} {}", title_case(n.trim_start_matches("ultimate_")), l));
        }
        if self.hot_potato_books > 0 {
            out.push(format!("HPB {}", self.hot_potato_books));
        }
        if self.fuming_books > 0 {
            out.push(format!("Fuming {}", self.fuming_books));
        }
        if self.art_of_war {
            out.push("Art of War".into());
        }
        if !self.gemstones.is_empty() {
            out.push(format!("{} gemstones", self.gemstones.len()));
        }
        for (k, v) in &self.attributes {
            out.push(format!("{} {}", title_case(k), v));
        }
        if let Some(s) = &self.skin {
            out.push(format!("Skin: {s}"));
        }
        if let Some(p) = &self.pet {
            if let Some(l) = p.level {
                out.push(format!("Lvl {l}"));
            }
            if let Some(h) = &p.held_item {
                out.push(format!("Held: {h}"));
            }
        }
        if let Some(e) = self.edition {
            out.push(format!("Edition #{e}"));
        }
        if self.exotic {
            if let Some(c) = &self.color {
                out.push(format!("Exotic colour {c}"));
            }
        }
        out
    }
}

/// "0,0,255" / "0:0:255" -> "0:0:255"; anything that is not three 0-255 numbers -> None.
pub fn normalize_color(s: &str) -> Option<String> {
    let parts: Vec<u8> = s.split([',', ':']).map(|p| p.trim().parse::<u8>()).collect::<Result<_, _>>().ok()?;
    (parts.len() == 3).then(|| format!("{}:{}:{}", parts[0], parts[1], parts[2]))
}

pub fn title_case(s: &str) -> String {
    s.split(['_', ' '])
        .filter(|w| !w.is_empty())
        .map(|w| {
            let mut c = w.chars();
            match c.next() {
                Some(f) => f.to_uppercase().collect::<String>() + &c.as_str().to_lowercase(),
                None => String::new(),
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}
