//! NotEnoughUpdates-REPO (MIT) loader: recipes, drop tables, forge/kat processes,
//! NPC shops, minion stats and progression constants.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::path::Path;

use anyhow::Context;
use serde::Deserialize;
use serde_json::Value;
use skypilot_calc::leveling::XpTable;
use skypilot_calc::slayer::{parse_amount, parse_chance, strip_formatting};

pub const PET_RARITIES: [&str; 6] = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"];

#[derive(Debug, Clone, PartialEq)]
pub struct Stack {
    pub id: String,
    pub amount: f64,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Recipe {
    Crafting { output: Stack, inputs: Vec<Stack> },
    Forge { output: Stack, inputs: Vec<Stack>, duration_secs: f64 },
    Kat { input: String, output: String, coins: f64, duration_secs: f64, items: Vec<Stack> },
    NpcShop { npc: String, result: Stack, cost: Vec<Stack> },
}

impl Recipe {
    pub fn output_id(&self) -> &str {
        match self {
            Recipe::Crafting { output, .. } | Recipe::Forge { output, .. } => &output.id,
            Recipe::Kat { output, .. } => output,
            Recipe::NpcShop { result, .. } => &result.id,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Drop {
    pub id: String,
    pub chance: f64,
    pub amount: f64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct DropTable {
    pub source_id: String,
    pub name: String,
    pub drops: Vec<Drop>,
    pub slayer_xp: Option<f64>,
    pub combat_xp: Option<f64>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MinionTier {
    pub id: String,
    pub base: String,
    pub tier: u32,
    pub seconds_between_actions: f64,
    pub max_storage: Option<u32>,
    /// Item name parsed from "generating and mining X!".
    pub product_name: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct TreePerk {
    pub name: String,
    pub x: i32,
    pub y: i32,
    #[serde(rename = "maxLevel")]
    pub max_level: u32,
    /// Literal powder name or an expression.
    #[serde(default)]
    pub powder: String,
    /// Cost expression in terms of `level` (current level).
    #[serde(default)]
    pub cost: Value,
    /// Stat expression (e.g. Mining Speed `(* level 20)`), when the perk grants a stat.
    #[serde(default)]
    pub stat: Option<String>,
}

#[derive(Debug, Clone)]
pub struct TreeLayout {
    pub prelude: Vec<String>,
    pub perks: BTreeMap<String, TreePerk>,
}

#[derive(Debug, Clone, Default)]
pub struct Leveling {
    pub skill_increments: Vec<f64>,
    pub runecrafting: Vec<f64>,
    pub slayer_xp: BTreeMap<String, Vec<f64>>,
    pub slayer_boss_xp: Vec<f64>,
    pub slayer_boss_xp_type: BTreeMap<String, Vec<f64>>,
    pub catacombs: Vec<f64>,
    pub hotm: Vec<f64>,
    pub hotf: Vec<f64>,
    pub social: Vec<f64>,
    pub caps: BTreeMap<String, u32>,
}

impl Leveling {
    /// NEU lists overflow levels past the real cap; `leveling_caps` holds the in-game maximum.
    fn capped(&self, table: XpTable, cap_key: &str) -> XpTable {
        match self.caps.get(cap_key) {
            Some(cap) => table.capped(*cap),
            None => table,
        }
    }
    pub fn catacombs_table(&self) -> XpTable {
        self.capped(XpTable::from_increments(&self.catacombs), "CATACOMBS")
    }
    pub fn hotm_table(&self) -> XpTable {
        self.capped(XpTable::from_increments(&self.hotm), "HOTM")
    }
    pub fn hotf_table(&self) -> XpTable {
        self.capped(XpTable::from_increments(&self.hotf), "HOTF")
    }
    pub fn slayer_table(&self, slayer: &str) -> Option<XpTable> {
        self.slayer_xp.get(slayer).map(|t| XpTable::from_cumulative(t.clone()))
    }
    pub fn boss_xp(&self, slayer: &str) -> Vec<f64> {
        self.slayer_boss_xp_type.get(slayer).cloned().unwrap_or_else(|| self.slayer_boss_xp.clone())
    }
}

#[derive(Debug, Clone, Default)]
pub struct PetConstants {
    pub rarity_offset: BTreeMap<String, usize>,
    pub levels: Vec<f64>,
    /// pet type -> (custom increments, max level)
    pub custom: BTreeMap<String, (Vec<f64>, u32)>,
    pub pet_types: BTreeMap<String, String>,
    pub display_names: BTreeMap<String, String>,
}

impl PetConstants {
    pub fn table(&self, pet_type: &str, tier: &str) -> XpTable {
        let offset = self.rarity_offset.get(&tier.to_ascii_uppercase()).copied().unwrap_or(0);
        if let Some((levels, max)) = self.custom.get(&pet_type.to_ascii_uppercase()) {
            // custom leveling extends the normal table past level 100
            let mut all = self.levels.clone();
            all.extend(levels.iter().copied());
            return skypilot_calc::leveling::pet_table(&all, offset, *max);
        }
        skypilot_calc::leveling::pet_table(&self.levels, offset, 100)
    }
    pub fn is_pet(&self, t: &str) -> bool {
        self.pet_types.contains_key(&t.to_ascii_uppercase())
    }
}

#[derive(Debug, Clone, Default)]
pub struct GardenConstants {
    pub garden_exp_increments: Vec<f64>,
    pub crop_milestones: BTreeMap<String, Vec<f64>>,
}

#[derive(Debug, Clone, Default)]
pub struct EssenceCost {
    pub essence_type: String,
    /// cost per star 1..=N
    pub stars: Vec<f64>,
}

#[derive(Debug, Default)]
pub struct NeuData {
    pub recipes: HashMap<String, Vec<Recipe>>,
    pub drops: HashMap<String, DropTable>,
    pub minions: BTreeMap<String, Vec<MinionTier>>,
    pub display_names: HashMap<String, String>,
    pub wiki_urls: HashMap<String, String>,
    pub leveling: Leveling,
    pub pets: PetConstants,
    pub hotm: Option<TreeLayout>,
    pub hotf: Option<TreeLayout>,
    /// lower tier -> higher tiers of the same accessory family
    pub talisman_upgrades: HashMap<String, Vec<String>>,
    pub ignored_talismans: HashSet<String>,
    pub slayer_cost: Vec<f64>,
    pub minion_max_tiers: BTreeMap<String, u32>,
    pub essence_costs: HashMap<String, EssenceCost>,
    pub garden: GardenConstants,
    pub enchant_names: HashSet<String>,
    pub file_count: usize,
}

fn read_json(path: &Path) -> anyhow::Result<Value> {
    let bytes = std::fs::read(path).with_context(|| format!("reading {}", path.display()))?;
    serde_json::from_slice(&bytes).with_context(|| format!("parsing {}", path.display()))
}

fn f64s(v: Option<&Value>) -> Vec<f64> {
    v.and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_f64).collect()).unwrap_or_default()
}

impl NeuData {
    /// Normalise a NEU item reference (`ENDER_DRAGON;4`, `SMITE;6`, `ZOMBIE_SLAYER_RUNE;1`)
    /// to SkyPilot market ids (see `ItemFingerprint::market_id`).
    pub fn normalize_id(&self, raw: &str) -> String {
        let Some((base, level)) = raw.split_once(';') else { return raw.to_string() };
        let up = base.to_ascii_uppercase();
        if self.pets.is_pet(&up) {
            if let Some(r) = level.parse::<usize>().ok().and_then(|i| PET_RARITIES.get(i)) {
                return format!("PET:{up}:{r}");
            }
        }
        if let Some(rune) = up.strip_suffix("_RUNE") {
            return format!("RUNE:{rune}:{level}");
        }
        if let Some(attr) = up.strip_prefix("ATTRIBUTE_SHARD_") {
            return format!("ATTRIBUTE_SHARD:{attr}:{level}");
        }
        if self.enchant_names.contains(&base.to_ascii_lowercase()) {
            return format!("ENCHANTMENT_{up}_{level}");
        }
        raw.replace(';', ":")
    }

    fn stack(&self, s: &str) -> Option<Stack> {
        let s = s.trim();
        if s.is_empty() {
            return None;
        }
        // "ID:count" - but ids like "INK_SACK:3" also contain ':'; NEU always appends count last.
        let (id, amount) = match s.rsplit_once(':') {
            Some((id, n)) if n.parse::<f64>().is_ok() && !id.is_empty() => (id, n.parse::<f64>().unwrap_or(1.0)),
            _ => (s, 1.0),
        };
        Some(Stack { id: self.normalize_id(id), amount: if amount.is_finite() && amount > 0.0 { amount } else { 1.0 } })
    }

    pub fn load(dir: &Path) -> anyhow::Result<NeuData> {
        let mut neu = NeuData::default();
        let c = dir.join("constants");

        // enchant names first (needed by normalize_id)
        if let Ok(e) = read_json(&c.join("enchants.json")) {
            if let Some(pools) = e.get("enchants").and_then(Value::as_object) {
                for list in pools.values() {
                    for n in list.as_array().into_iter().flatten().filter_map(Value::as_str) {
                        neu.enchant_names.insert(n.to_ascii_lowercase());
                    }
                }
            }
        }

        let lv = read_json(&c.join("leveling.json"))?;
        neu.leveling = Leveling {
            skill_increments: f64s(lv.get("leveling_xp")),
            runecrafting: f64s(lv.get("runecrafting_xp")),
            slayer_xp: lv
                .get("slayer_xp")
                .and_then(Value::as_object)
                .map(|m| m.iter().map(|(k, v)| (k.clone(), f64s(Some(v)))).collect())
                .unwrap_or_default(),
            slayer_boss_xp: f64s(lv.get("slayer_boss_xp")),
            slayer_boss_xp_type: lv
                .get("slayer_boss_xp_type")
                .and_then(Value::as_object)
                .map(|m| m.iter().map(|(k, v)| (k.clone(), f64s(Some(v)))).collect())
                .unwrap_or_default(),
            catacombs: f64s(lv.get("catacombs")),
            hotm: f64s(lv.get("HOTM")),
            hotf: f64s(lv.get("HOTF")),
            social: f64s(lv.get("social")),
            caps: lv
                .get("leveling_caps")
                .and_then(Value::as_object)
                .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|n| (k.to_ascii_uppercase(), n as u32))).collect())
                .unwrap_or_default(),
        };

        let pets = read_json(&c.join("pets.json"))?;
        neu.pets = PetConstants {
            rarity_offset: pets
                .get("pet_rarity_offset")
                .and_then(Value::as_object)
                .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|n| (k.clone(), n as usize))).collect())
                .unwrap_or_default(),
            levels: f64s(pets.get("pet_levels")),
            custom: pets
                .get("custom_pet_leveling")
                .and_then(Value::as_object)
                .map(|m| {
                    m.iter()
                        .map(|(k, v)| (k.clone(), (f64s(v.get("pet_levels")), v.get("max_level").and_then(Value::as_u64).unwrap_or(100) as u32)))
                        .collect()
                })
                .unwrap_or_default(),
            pet_types: pets
                .get("pet_types")
                .and_then(Value::as_object)
                .map(|m| m.iter().map(|(k, v)| (k.clone(), v.as_str().unwrap_or_default().to_string())).collect())
                .unwrap_or_default(),
            display_names: pets
                .get("id_to_display_name")
                .and_then(Value::as_object)
                .map(|m| m.iter().map(|(k, v)| (k.clone(), v.as_str().unwrap_or_default().to_string())).collect())
                .unwrap_or_default(),
        };

        for (file, slot) in [("hotmlayout.json", "hotm"), ("hotflayout.json", "hotf")] {
            if let Ok(v) = read_json(&c.join(file)) {
                let prelude: Vec<String> = v
                    .get("prelude")
                    .and_then(Value::as_array)
                    .map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect())
                    .unwrap_or_default();
                let perks: BTreeMap<String, TreePerk> = v
                    .get(slot)
                    .and_then(|t| t.get("perks"))
                    .and_then(Value::as_object)
                    .map(|m| m.iter().filter_map(|(k, p)| serde_json::from_value(p.clone()).ok().map(|p| (k.clone(), p))).collect())
                    .unwrap_or_default();
                let layout = TreeLayout { prelude, perks };
                if slot == "hotm" {
                    neu.hotm = Some(layout);
                } else {
                    neu.hotf = Some(layout);
                }
            }
        }

        if let Ok(misc) = read_json(&c.join("misc.json")) {
            neu.talisman_upgrades = misc
                .get("talisman_upgrades")
                .and_then(Value::as_object)
                .map(|m| {
                    m.iter()
                        .map(|(k, v)| (k.clone(), v.as_array().map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect()).unwrap_or_default()))
                        .collect()
                })
                .unwrap_or_default();
            neu.ignored_talismans = misc
                .get("ignored_talisman")
                .and_then(Value::as_array)
                .map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect())
                .unwrap_or_default();
            neu.slayer_cost = f64s(misc.get("slayer_cost"));
            neu.minion_max_tiers = misc
                .get("minions")
                .and_then(Value::as_object)
                .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|n| (k.clone(), n as u32))).collect())
                .unwrap_or_default();
        }

        if let Ok(ess) = read_json(&c.join("essencecosts.json")) {
            if let Some(m) = ess.as_object() {
                for (id, v) in m {
                    let mut stars = Vec::new();
                    for i in 1..=15 {
                        match v.get(i.to_string()).and_then(Value::as_f64) {
                            Some(n) => stars.push(n),
                            None => break,
                        }
                    }
                    neu.essence_costs.insert(
                        id.clone(),
                        EssenceCost { essence_type: v.get("type").and_then(Value::as_str).unwrap_or_default().to_ascii_uppercase(), stars },
                    );
                }
            }
        }

        if let Ok(g) = read_json(&c.join("garden.json")) {
            neu.garden = GardenConstants {
                garden_exp_increments: f64s(g.get("garden_exp")),
                crop_milestones: g
                    .get("crop_milestones")
                    .and_then(Value::as_object)
                    .map(|m| m.iter().map(|(k, v)| (k.clone(), f64s(Some(v)))).collect())
                    .unwrap_or_default(),
            };
        }

        let items_dir = dir.join("items");
        let entries = std::fs::read_dir(&items_dir).with_context(|| format!("listing {}", items_dir.display()))?;
        let minion_re = regex::Regex::new(r"^(.+)_GENERATOR_(\d+)$").unwrap();
        let product_re = regex::Regex::new(r"(?:generating and \w+|generating|harvesting|chopping|mining|killing|fishing for|catching|gathering)\s+(.+?)!").unwrap();
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|e| e.to_str()) != Some("json") {
                continue;
            }
            let Ok(item) = read_json(&path) else { continue };
            neu.file_count += 1;
            let Some(internal) = item.get("internalname").and_then(Value::as_str) else { continue };
            let id = neu.normalize_id(internal);
            if let Some(name) = item.get("displayname").and_then(Value::as_str) {
                neu.display_names.insert(id.clone(), strip_formatting(name));
            }
            if let Some(url) = item.get("info").and_then(Value::as_array).and_then(|a| a.iter().filter_map(Value::as_str).find(|u| u.contains("hypixelskyblock.minecraft.wiki"))) {
                neu.wiki_urls.insert(id.clone(), url.to_string());
            }
            let lore: Vec<String> = item
                .get("lore")
                .and_then(Value::as_array)
                .map(|a| a.iter().filter_map(Value::as_str).map(strip_formatting).collect())
                .unwrap_or_default();

            // legacy single crafting recipe
            if let Some(r) = item.get("recipe").and_then(Value::as_object) {
                let inputs = grid_inputs(&neu, r);
                if !inputs.is_empty() {
                    neu.recipes.entry(id.clone()).or_default().push(Recipe::Crafting { output: Stack { id: id.clone(), amount: 1.0 }, inputs });
                }
            }
            for r in item.get("recipes").and_then(Value::as_array).into_iter().flatten() {
                match r.get("type").and_then(Value::as_str).unwrap_or_default() {
                    "crafting" => {
                        if let Some(obj) = r.as_object() {
                            let inputs = grid_inputs(&neu, obj);
                            let out_id = r.get("overrideOutputId").and_then(Value::as_str).map(|s| neu.normalize_id(s)).unwrap_or_else(|| id.clone());
                            let count = r.get("count").and_then(Value::as_f64).unwrap_or(1.0).max(1.0);
                            if !inputs.is_empty() {
                                neu.recipes.entry(out_id.clone()).or_default().push(Recipe::Crafting { output: Stack { id: out_id, amount: count }, inputs });
                            }
                        }
                    }
                    "forge" => {
                        let inputs: Vec<Stack> =
                            r.get("inputs").and_then(Value::as_array).into_iter().flatten().filter_map(Value::as_str).filter_map(|s| neu.stack(s)).collect();
                        let out_id = r.get("overrideOutputId").and_then(Value::as_str).map(|s| neu.normalize_id(s)).unwrap_or_else(|| id.clone());
                        let count = r.get("count").and_then(Value::as_f64).unwrap_or(1.0).max(1.0);
                        let duration = r.get("duration").and_then(Value::as_f64).unwrap_or(0.0);
                        neu.recipes.entry(out_id.clone()).or_default().push(Recipe::Forge { output: Stack { id: out_id, amount: count }, inputs, duration_secs: duration });
                    }
                    "katgrade" => {
                        let input = r.get("input").and_then(Value::as_str).map(|s| neu.normalize_id(s)).unwrap_or_default();
                        let output = r.get("output").and_then(Value::as_str).map(|s| neu.normalize_id(s)).unwrap_or_else(|| id.clone());
                        let items: Vec<Stack> =
                            r.get("items").and_then(Value::as_array).into_iter().flatten().filter_map(Value::as_str).filter_map(|s| neu.stack(s)).collect();
                        neu.recipes.entry(output.clone()).or_default().push(Recipe::Kat {
                            input,
                            output,
                            coins: r.get("coins").and_then(Value::as_f64).unwrap_or(0.0),
                            duration_secs: r.get("time").and_then(Value::as_f64).unwrap_or(0.0),
                            items,
                        });
                    }
                    "npc_shop" => {
                        let Some(result) = r.get("result").and_then(Value::as_str).and_then(|s| neu.stack(s)) else { continue };
                        let cost: Vec<Stack> =
                            r.get("cost").and_then(Value::as_array).into_iter().flatten().filter_map(Value::as_str).filter_map(|s| neu.stack(s)).collect();
                        neu.recipes.entry(result.id.clone()).or_default().push(Recipe::NpcShop { npc: id.clone(), result, cost });
                    }
                    "drops" => {
                        let drops: Vec<Drop> = r
                            .get("drops")
                            .and_then(Value::as_array)
                            .into_iter()
                            .flatten()
                            .filter_map(|d| {
                                let raw = d.get("id")?.as_str()?;
                                let st = neu.stack(raw)?;
                                let chance = d.get("chance").and_then(Value::as_str).and_then(parse_chance)?;
                                let extra: Vec<String> =
                                    d.get("extra").and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect()).unwrap_or_default();
                                let amount = if extra.iter().any(|e| strip_formatting(e).contains('x')) { parse_amount(&extra) } else { st.amount };
                                Some(Drop { id: st.id, chance, amount })
                            })
                            .collect();
                        neu.drops.insert(
                            id.clone(),
                            DropTable {
                                source_id: id.clone(),
                                name: r.get("name").and_then(Value::as_str).map(strip_formatting).unwrap_or_default(),
                                drops,
                                slayer_xp: r.get("xp").and_then(Value::as_f64),
                                combat_xp: r.get("combat_xp").and_then(Value::as_f64),
                            },
                        );
                    }
                    _ => {}
                }
            }

            if let Some(c) = minion_re.captures(internal) {
                let secs = lore.iter().find_map(|l| l.strip_prefix("Time Between Actions: ").and_then(|v| v.trim_end_matches('s').trim().parse::<f64>().ok()));
                if let Some(secs) = secs {
                    let storage = lore.iter().find_map(|l| l.strip_prefix("Max Storage: ").and_then(|v| v.replace(',', "").trim().parse::<u32>().ok()));
                    let joined = lore.join(" ");
                    let product = product_re.captures(&joined).map(|m| m[1].trim().to_string());
                    neu.minions.entry(c[1].to_string()).or_default().push(MinionTier {
                        id: internal.to_string(),
                        base: c[1].to_string(),
                        tier: c[2].parse().unwrap_or(1),
                        seconds_between_actions: secs,
                        max_storage: storage,
                        product_name: product,
                    });
                }
            }
        }
        for tiers in neu.minions.values_mut() {
            tiers.sort_by_key(|t| t.tier);
        }
        Ok(neu)
    }

    /// Highest tiers of an accessory family (used to know if an owned accessory is superseded).
    pub fn accessory_upgrades(&self, id: &str) -> &[String] {
        self.talisman_upgrades.get(id).map(Vec::as_slice).unwrap_or(&[])
    }

    /// Lower tiers of an accessory family that `id` supersedes.
    pub fn accessory_lower_tiers(&self, id: &str) -> Vec<String> {
        self.talisman_upgrades.iter().filter(|(_, ups)| ups.iter().any(|u| u == id)).map(|(k, _)| k.clone()).collect()
    }
}

fn grid_inputs(neu: &NeuData, obj: &serde_json::Map<String, Value>) -> Vec<Stack> {
    let mut agg: BTreeMap<String, f64> = BTreeMap::new();
    for key in ["A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"] {
        if let Some(st) = obj.get(key).and_then(Value::as_str).and_then(|s| neu.stack(s)) {
            *agg.entry(st.id).or_insert(0.0) += st.amount;
        }
    }
    agg.into_iter().map(|(id, amount)| Stack { id, amount }).collect()
}
