//! Typed views of the Hypixel `resources` endpoints.

use std::collections::BTreeMap;

use serde::Deserialize;
use serde_json::Value;
use skypilot_calc::leveling::XpTable;

/// A requirement attached to an item (`requirements` / `catacombs_requirements`).
#[derive(Debug, Clone, PartialEq)]
pub enum Requirement {
    Skill { skill: String, level: u32 },
    Slayer { slayer: String, level: u32 },
    DungeonSkill { dungeon: String, level: u32 },
    /// Must have completed this floor tier.
    DungeonTier { dungeon: String, tier: u32 },
    HeartOfTheMountain { tier: u32 },
    Collection { collection: String, tier: u32 },
    GardenLevel { level: u32 },
    KuudraCompletion { tier: String },
    CrimsonReputation { faction: String, reputation: i64 },
    TrophyFishing { trophy: String, reward: String },
    ChocolateFactory { level: u32 },
    AnyOf(Vec<Requirement>),
    Other { kind: String, raw: Value },
}

fn u(v: &Value, k: &str) -> u32 {
    v.get(k).and_then(Value::as_u64).unwrap_or(0).min(10_000) as u32
}
fn s(v: &Value, k: &str) -> String {
    v.get(k).and_then(Value::as_str).unwrap_or_default().to_string()
}

impl Requirement {
    pub fn parse(v: &Value) -> Requirement {
        let kind = s(v, "type");
        match kind.as_str() {
            "SKILL" => Requirement::Skill { skill: s(v, "skill").to_ascii_uppercase(), level: u(v, "level") },
            "SLAYER" => Requirement::Slayer { slayer: s(v, "slayer_boss_type").to_ascii_lowercase(), level: u(v, "level") },
            "DUNGEON_SKILL" => Requirement::DungeonSkill { dungeon: s(v, "dungeon_type").to_ascii_uppercase(), level: u(v, "level") },
            "DUNGEON_TIER" => Requirement::DungeonTier { dungeon: s(v, "dungeon_type").to_ascii_uppercase(), tier: u(v, "tier") },
            "HEART_OF_THE_MOUNTAIN" => Requirement::HeartOfTheMountain { tier: u(v, "tier") },
            "COLLECTION" => Requirement::Collection { collection: s(v, "collection"), tier: u(v, "tier") },
            "GARDEN_LEVEL" => Requirement::GardenLevel { level: u(v, "level") },
            "KUUDRA_COMPLETION" => Requirement::KuudraCompletion { tier: s(v, "kuudra_tier").to_ascii_lowercase() },
            "CRIMSON_ISLE_REPUTATION" => Requirement::CrimsonReputation {
                faction: s(v, "faction").to_ascii_lowercase(),
                reputation: v.get("reputation").and_then(Value::as_i64).unwrap_or(0),
            },
            "TROPHY_FISHING" => Requirement::TrophyFishing { trophy: s(v, "trophy_type"), reward: s(v, "reward") },
            "CHOCOLATE_FACTORY" => Requirement::ChocolateFactory { level: u(v, "level") },
            "ANY_OF" | "ONE_OF" => Requirement::AnyOf(
                v.get("requirements").and_then(Value::as_array).map(|a| a.iter().map(Requirement::parse).collect()).unwrap_or_default(),
            ),
            _ => Requirement::Other { kind, raw: v.clone() },
        }
    }

    pub fn describe(&self) -> String {
        match self {
            Requirement::Skill { skill, level } => format!("{} {}", title(skill), level),
            Requirement::Slayer { slayer, level } => format!("{} Slayer {}", title(slayer), level),
            Requirement::DungeonSkill { level, .. } => format!("Catacombs {level}"),
            Requirement::DungeonTier { tier, .. } => format!("Complete Floor {tier}"),
            Requirement::HeartOfTheMountain { tier } => format!("HOTM {tier}"),
            Requirement::Collection { collection, tier } => format!("{} Collection {}", title(collection), tier),
            Requirement::GardenLevel { level } => format!("Garden {level}"),
            Requirement::KuudraCompletion { tier } => format!("Complete Kuudra ({})", title(tier)),
            Requirement::CrimsonReputation { faction, reputation } => format!("{} reputation {}", title(faction), reputation),
            Requirement::TrophyFishing { trophy, reward } => format!("Trophy {} {}", title(trophy), title(reward)),
            Requirement::ChocolateFactory { level } => format!("Chocolate Factory {level}"),
            Requirement::AnyOf(list) => list.iter().map(Requirement::describe).collect::<Vec<_>>().join(" or "),
            Requirement::Other { kind, .. } => title(kind),
        }
    }
}

pub fn title(s: &str) -> String {
    skypilot_items::fingerprint::title_case(s)
}

#[derive(Debug, Clone)]
pub struct ItemDef {
    pub id: String,
    pub name: String,
    pub material: Option<String>,
    pub tier: Option<String>,
    pub category: Option<String>,
    pub npc_sell_price: Option<f64>,
    /// Upper-cased stat keys (the API mixes "MINING_SPEED" and "mining_speed").
    pub stats: BTreeMap<String, f64>,
    pub requirements: Vec<Requirement>,
    pub catacombs_requirements: Vec<Requirement>,
    pub soulbound: Option<String>,
    pub museum: bool,
    pub dungeon_item: bool,
    pub can_auction: Option<bool>,
    pub gemstone_slots: Vec<String>,
    /// Museum donation XP (`museum_data.donation_xp`).
    pub museum_donation_xp: Option<f64>,
    /// Museum category (`museum_data.category`), e.g. "WEAPONS".
    pub museum_category: Option<String>,
    /// Default leather colour as "r:g:b" (the resource's `color`, e.g. "0,0,255").
    pub color: Option<String>,
    /// Item exists in NEU but not in the Hypixel items resource.
    pub neu_only: bool,
}

impl ItemDef {
    pub fn from_value(v: &Value) -> Option<ItemDef> {
        let id = v.get("id")?.as_str()?.to_string();
        let mut stats = BTreeMap::new();
        if let Some(obj) = v.get("stats").and_then(Value::as_object) {
            for (k, x) in obj {
                if let Some(n) = x.as_f64().filter(|n| n.is_finite()) {
                    *stats.entry(k.to_ascii_uppercase()).or_insert(0.0) += n;
                }
            }
        }
        let reqs = |key: &str| -> Vec<Requirement> {
            v.get(key).and_then(Value::as_array).map(|a| a.iter().map(Requirement::parse).collect()).unwrap_or_default()
        };
        Some(ItemDef {
            name: v.get("name").and_then(Value::as_str).map(skypilot_items::text::strip_formatting).unwrap_or_else(|| id.clone()),
            material: v.get("material").and_then(Value::as_str).map(str::to_string),
            tier: v.get("tier").and_then(Value::as_str).map(str::to_string),
            category: v.get("category").and_then(Value::as_str).map(str::to_string),
            npc_sell_price: v.get("npc_sell_price").and_then(Value::as_f64).filter(|p| p.is_finite() && *p >= 0.0),
            stats,
            requirements: reqs("requirements"),
            catacombs_requirements: reqs("catacombs_requirements"),
            soulbound: v.get("soulbound").and_then(Value::as_str).map(str::to_string),
            museum: v.get("museum").and_then(Value::as_bool).unwrap_or(false) || v.get("museum_data").is_some(),
            dungeon_item: v.get("dungeon_item").and_then(Value::as_bool).unwrap_or(false),
            can_auction: v.get("can_auction").and_then(Value::as_bool),
            gemstone_slots: v
                .get("gemstone_slots")
                .and_then(Value::as_array)
                .map(|a| a.iter().filter_map(|s| s.get("slot_type").and_then(Value::as_str).map(str::to_string)).collect())
                .unwrap_or_default(),
            museum_donation_xp: v.get("museum_data").and_then(|m| m.get("donation_xp")).and_then(Value::as_f64),
            museum_category: v.get("museum_data").and_then(|m| m.get("category")).and_then(Value::as_str).map(str::to_string),
            color: v.get("color").and_then(Value::as_str).and_then(skypilot_items::fingerprint::normalize_color),
            neu_only: false,
            id,
        })
    }

    pub fn is_accessory(&self) -> bool {
        self.category.as_deref() == Some("ACCESSORY")
    }

    /// Stat lookup tolerant to API casing.
    pub fn stat(&self, key: &str) -> f64 {
        self.stats.get(&key.to_ascii_uppercase()).copied().unwrap_or(0.0)
    }
}

#[derive(Debug, Clone)]
pub struct SkillDef {
    pub id: String,
    pub name: String,
    pub max_level: u32,
    pub table: XpTable,
}

pub fn parse_skills(v: &Value) -> BTreeMap<String, SkillDef> {
    let mut out = BTreeMap::new();
    if let Some(skills) = v.get("skills").and_then(Value::as_object) {
        for (id, def) in skills {
            let levels: Vec<f64> = def
                .get("levels")
                .and_then(Value::as_array)
                .map(|a| a.iter().filter_map(|l| l.get("totalExpRequired").and_then(Value::as_f64)).collect())
                .unwrap_or_default();
            out.insert(
                id.to_ascii_uppercase(),
                SkillDef {
                    id: id.to_ascii_uppercase(),
                    name: def.get("name").and_then(Value::as_str).unwrap_or(id).to_string(),
                    max_level: def.get("maxLevel").and_then(Value::as_u64).unwrap_or(levels.len() as u64) as u32,
                    table: XpTable::from_cumulative(levels),
                },
            );
        }
    }
    out
}

#[derive(Debug, Clone)]
pub struct CollectionDef {
    pub id: String,
    pub name: String,
    pub category: String,
    /// Cumulative amounts per tier.
    pub tiers: Vec<u64>,
    pub max_tier: u32,
}

pub fn parse_collections(v: &Value) -> Vec<CollectionDef> {
    let mut out = Vec::new();
    if let Some(cats) = v.get("collections").and_then(Value::as_object) {
        for (cat, def) in cats {
            let cat_name = def.get("name").and_then(Value::as_str).unwrap_or(cat).to_string();
            if let Some(items) = def.get("items").and_then(Value::as_object) {
                for (id, it) in items {
                    let tiers: Vec<u64> = it
                        .get("tiers")
                        .and_then(Value::as_array)
                        .map(|a| a.iter().filter_map(|t| t.get("amountRequired").and_then(Value::as_u64)).collect())
                        .unwrap_or_default();
                    out.push(CollectionDef {
                        id: id.clone(),
                        name: it.get("name").and_then(Value::as_str).unwrap_or(id).to_string(),
                        category: cat_name.clone(),
                        max_tier: it.get("maxTiers").and_then(Value::as_u64).unwrap_or(tiers.len() as u64) as u32,
                        tiers,
                    });
                }
            }
        }
    }
    out
}

#[derive(Debug, Clone, Deserialize)]
pub struct Perk {
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub minister: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Mayor {
    pub key: String,
    pub name: String,
    #[serde(default)]
    pub perks: Vec<Perk>,
}

#[derive(Debug, Clone)]
pub struct Election {
    pub mayor: Option<Mayor>,
    pub minister: Option<Mayor>,
    pub year: Option<u32>,
    pub last_updated: i64,
}

pub fn parse_election(v: &Value) -> Election {
    let mayor = v.get("mayor").and_then(|m| serde_json::from_value::<Mayor>(m.clone()).ok());
    let minister = v.get("mayor").and_then(|m| m.get("minister")).and_then(|m| {
        let key = m.get("key")?.as_str()?.to_string();
        let name = m.get("name")?.as_str()?.to_string();
        let perk = m.get("perk").and_then(|p| serde_json::from_value::<Perk>(p.clone()).ok());
        Some(Mayor { key, name, perks: perk.into_iter().collect() })
    });
    Election {
        mayor,
        minister,
        year: v.get("mayor").and_then(|m| m.get("election")).and_then(|e| e.get("year")).and_then(Value::as_u64).map(|y| y as u32),
        last_updated: v.get("lastUpdated").and_then(Value::as_i64).unwrap_or(0),
    }
}

impl Election {
    pub fn has_perk(&self, perk_name: &str) -> bool {
        [&self.mayor, &self.minister]
            .iter()
            .filter_map(|m| m.as_ref())
            .any(|m| m.perks.iter().any(|p| p.name.eq_ignore_ascii_case(perk_name)))
    }
    pub fn mayor_key(&self) -> Option<&str> {
        self.mayor.as_ref().map(|m| m.key.as_str())
    }
}
