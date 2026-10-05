//! SkyBlock item extraction from the three layouts SkyPilot encounters:
//!
//! 1. **Legacy tag** – `{id: short, Count, tag: {display: {Name, Lore}, ExtraAttributes: {...}}}`
//!    (Hypixel API inventories).
//! 2. **Hybrid** – Hypixel API blobs since the modern-version migration carry an extra
//!    `components` compound (`minecraft:item_model`, `minecraft:tooltip_style`, ...) *next to*
//!    the legacy tag. `minecraft:item_model` is a resource-pack model id such as
//!    `hypixel_skyblock:item/uncategorized/hyperion` – it is **not** the SkyBlock item id.
//! 3. **Data components** – Minecraft 26.2 client stacks (sent by the mod) where SkyBlock
//!    data lives in `minecraft:custom_data` and name/lore are text components.
//!
//! The SkyBlock id is always taken from `ExtraAttributes.id` / `custom_data.id`.

use std::io::Read;

use base64::Engine;
use flate2::read::GzDecoder;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use thiserror::Error;

use crate::nbt::{self, NbtError, Tag};
use crate::text::{self, Rarity};

#[derive(Debug, Error)]
pub enum DecodeError {
    #[error("base64: {0}")]
    Base64(String),
    #[error("gzip: {0}")]
    Gzip(String),
    #[error("nbt: {0}")]
    Nbt(#[from] NbtError),
    #[error("decompressed data too large")]
    TooLarge,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ItemFormat {
    LegacyTag,
    Hybrid,
    DataComponents,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ParsedItem {
    pub skyblock_id: Option<String>,
    /// Display name with formatting codes removed.
    pub name: String,
    pub raw_name: String,
    pub lore: Vec<String>,
    pub count: u32,
    /// Resource-pack model id (display only; never used for identification).
    pub item_model: Option<String>,
    pub tooltip_style: Option<String>,
    pub vanilla_id: Option<String>,
    /// `ExtraAttributes` / `minecraft:custom_data` compound.
    pub attributes: Tag,
    pub rarity: Option<Rarity>,
    pub category: Option<String>,
    pub soulbound: bool,
    pub format: ItemFormat,
}

const MAX_DECOMPRESSED: u64 = 32 * 1024 * 1024;

/// Decode a Hypixel `data` blob (base64 of gzipped NBT). The API may contain JSON unicode
/// escapes that were already decoded by the JSON parser, so only standard base64 is handled here.
pub fn decode_blob(b64: &str) -> Result<Tag, DecodeError> {
    let cleaned: String = b64.chars().filter(|c| !c.is_whitespace()).collect();
    let raw = base64::engine::general_purpose::STANDARD
        .decode(cleaned.as_bytes())
        .map_err(|e| DecodeError::Base64(e.to_string()))?;
    decode_gzip_nbt(&raw)
}

pub fn decode_gzip_nbt(raw: &[u8]) -> Result<Tag, DecodeError> {
    let bytes = if raw.starts_with(&[0x1f, 0x8b]) {
        let mut out = Vec::new();
        GzDecoder::new(raw)
            .take(MAX_DECOMPRESSED + 1)
            .read_to_end(&mut out)
            .map_err(|e| DecodeError::Gzip(e.to_string()))?;
        if out.len() as u64 > MAX_DECOMPRESSED {
            return Err(DecodeError::TooLarge);
        }
        out
    } else {
        raw.to_vec()
    };
    Ok(nbt::read_named(&bytes)?.1)
}

/// Decode an inventory blob into slots (None = empty slot).
pub fn decode_inventory(b64: &str) -> Result<Vec<Option<ParsedItem>>, DecodeError> {
    let root = decode_blob(b64)?;
    Ok(inventory_from_tag(&root))
}

pub fn inventory_from_tag(root: &Tag) -> Vec<Option<ParsedItem>> {
    let list = root.get("i").map(Tag::as_list).unwrap_or(&[]);
    list.iter().map(parse_item_tag).collect()
}

fn lore_from_component(v: Option<&Tag>) -> Option<Vec<String>> {
    let tag = v?;
    let list = tag.as_list();
    if list.is_empty() {
        return None;
    }
    Some(
        list.iter()
            .map(|t| match t {
                Tag::String(s) => text::component_to_legacy(&Value::String(s.clone())),
                other => text::component_to_legacy(&other.to_json()),
            })
            .collect(),
    )
}

fn name_from_component(v: Option<&Tag>) -> Option<String> {
    let tag = v?;
    Some(match tag {
        Tag::String(s) => text::component_to_legacy(&Value::String(s.clone())),
        other => text::component_to_legacy(&other.to_json()),
    })
}

/// Parse one item compound from an inventory list.
pub fn parse_item_tag(tag: &Tag) -> Option<ParsedItem> {
    let compound = tag.as_compound()?;
    if compound.is_empty() {
        return None;
    }
    let components = tag.get("components");
    let legacy = tag.get("tag");
    let custom_data = components.and_then(|c| c.get("minecraft:custom_data"));
    let extra = legacy.and_then(|t| t.get("ExtraAttributes")).or(custom_data).cloned().unwrap_or(Tag::Compound(Default::default()));

    let raw_name = legacy
        .and_then(|t| t.path(&["display", "Name"]))
        .and_then(Tag::as_str)
        .map(str::to_string)
        .or_else(|| name_from_component(components.and_then(|c| c.get("minecraft:custom_name"))))
        .or_else(|| name_from_component(components.and_then(|c| c.get("minecraft:item_name"))))
        .unwrap_or_default();

    let lore: Vec<String> = legacy
        .and_then(|t| t.path(&["display", "Lore"]))
        .map(|l| l.as_list().iter().filter_map(|x| x.as_str().map(str::to_string)).collect::<Vec<_>>())
        .filter(|l| !l.is_empty())
        .or_else(|| lore_from_component(components.and_then(|c| c.get("minecraft:lore"))))
        .unwrap_or_default();

    let count = tag
        .get("Count")
        .or_else(|| tag.get("count"))
        .and_then(Tag::as_i64)
        .unwrap_or(1)
        .clamp(0, 1_000_000) as u32;

    let vanilla_id = match tag.get("id") {
        Some(Tag::String(s)) => Some(s.clone()),
        Some(other) => other.as_i64().map(|n| format!("legacy:{n}")),
        None => None,
    };

    let format = match (components.is_some(), legacy.is_some()) {
        (true, true) => ItemFormat::Hybrid,
        (true, false) => ItemFormat::DataComponents,
        _ => ItemFormat::LegacyTag,
    };

    Some(build(extra, raw_name, lore, count, components, vanilla_id, format))
}

fn build(
    attributes: Tag,
    raw_name: String,
    lore: Vec<String>,
    count: u32,
    components: Option<&Tag>,
    vanilla_id: Option<String>,
    format: ItemFormat,
) -> ParsedItem {
    let skyblock_id = attributes.get("id").and_then(Tag::as_str).map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    let rarity_info = text::rarity_from_lore(&lore);
    let soulbound = lore.iter().any(|l| text::strip_formatting(l).contains("Soulbound"))
        || attributes.get("donated_museum").and_then(Tag::as_i64) == Some(1);
    ParsedItem {
        skyblock_id,
        name: text::strip_formatting(&raw_name).trim().to_string(),
        raw_name,
        lore,
        count,
        item_model: components.and_then(|c| c.get("minecraft:item_model")).and_then(Tag::as_str).map(str::to_string),
        tooltip_style: components.and_then(|c| c.get("minecraft:tooltip_style")).and_then(Tag::as_str).map(str::to_string),
        vanilla_id,
        attributes,
        rarity: rarity_info.as_ref().map(|r| r.0),
        category: rarity_info.and_then(|r| r.1),
        soulbound,
        format,
    }
}

/// Build an item from the JSON the mod sends for a 26.2 `ItemStack`:
/// `{ "custom_data": {...}, "name": "§6Hyperion", "lore": ["..."], "count": 1, "item_model": "..." }`.
pub fn from_client_json(v: &Value) -> Option<ParsedItem> {
    let custom = v.get("custom_data").or_else(|| v.get("customData"))?;
    let attributes = Tag::from_json(custom);
    let raw_name = v.get("name").and_then(Value::as_str).unwrap_or_default().to_string();
    let lore = v
        .get("lore")
        .and_then(Value::as_array)
        .map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect())
        .unwrap_or_default();
    let count = v.get("count").and_then(Value::as_u64).unwrap_or(1).min(1_000_000) as u32;
    let mut comps = std::collections::BTreeMap::new();
    if let Some(m) = v.get("item_model").and_then(Value::as_str) {
        comps.insert("minecraft:item_model".to_string(), Tag::String(m.to_string()));
    }
    let comps = Tag::Compound(comps);
    Some(build(attributes, raw_name, lore, count, Some(&comps), v.get("id").and_then(Value::as_str).map(str::to_string), ItemFormat::DataComponents))
}

/// Parsed `petInfo` JSON string stored in pet item attributes.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PetInfo {
    #[serde(rename = "type")]
    pub pet_type: String,
    #[serde(default)]
    pub tier: String,
    #[serde(default)]
    pub exp: f64,
    #[serde(default, rename = "heldItem")]
    pub held_item: Option<String>,
    #[serde(default, rename = "candyUsed")]
    pub candy_used: u32,
    #[serde(default)]
    pub skin: Option<String>,
    #[serde(default)]
    pub active: bool,
}

impl ParsedItem {
    pub fn attr(&self, key: &str) -> Option<&Tag> {
        self.attributes.get(key)
    }

    pub fn attr_i64(&self, key: &str) -> Option<i64> {
        self.attr(key).and_then(Tag::as_i64)
    }

    pub fn attr_str(&self, key: &str) -> Option<&str> {
        self.attr(key).and_then(Tag::as_str)
    }

    pub fn pet_info(&self) -> Option<PetInfo> {
        let raw = self.attr("petInfo")?;
        match raw {
            Tag::String(s) => serde_json::from_str(s).ok(),
            Tag::Compound(_) => serde_json::from_value(raw.to_json()).ok(),
            _ => None,
        }
    }

    pub fn lore_stats(&self) -> std::collections::BTreeMap<String, f64> {
        text::lore_stats(&self.lore)
    }

    pub fn is_recombobulated(&self) -> bool {
        self.attr_i64("rarity_upgrades").unwrap_or(0) > 0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_client_item() {
        let v = serde_json::json!({
            "custom_data": {"id": "HYPERION", "upgrade_level": 5, "rarity_upgrades": 1},
            "name": "§6Heroic Hyperion ✪✪✪✪✪",
            "lore": ["§7Damage: §c+260", "§d§l§ka§r §d§lMYTHIC DUNGEON SWORD §d§l§ka"],
            "count": 1,
            "item_model": "hypixel_skyblock:item/uncategorized/hyperion"
        });
        let item = from_client_json(&v).unwrap();
        assert_eq!(item.skyblock_id.as_deref(), Some("HYPERION"));
        assert_eq!(item.rarity, Some(Rarity::Mythic));
        assert!(item.is_recombobulated());
        assert_eq!(item.format, ItemFormat::DataComponents);
    }
}
