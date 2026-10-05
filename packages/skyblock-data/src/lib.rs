//! SkyBlock data registry.
//!
//! Three layers, all loaded from disk (never from model memory):
//! 1. Hypixel `resources` endpoints (items, skills, collections, election) - cached JSON.
//! 2. NotEnoughUpdates-REPO (MIT): recipes, drop tables, constants.
//! 3. SkyPilot curated registry (`data/registry/*.toml`): sourced game facts + labelled heuristics.

pub mod calendar;
pub mod curated;
pub mod farms;
pub mod hypixel;
pub mod icons;
pub mod locations;
pub mod neu;
#[cfg(feature = "sync")]
pub mod sync;

use std::collections::{BTreeMap, HashMap};
use std::io::Read;
use std::path::{Path, PathBuf};

use anyhow::Context;
use serde_json::Value;

pub use curated::Curated;
pub use farms::FarmDesignDef;
pub use hypixel::{CollectionDef, Election, ItemDef, Requirement, SkillDef};
pub use neu::NeuData;

#[derive(Debug, Clone)]
pub struct DataPaths {
    pub registry_dir: PathBuf,
    pub translation_dir: PathBuf,
    pub farms_dir: PathBuf,
    /// Directory with Hypixel resource snapshots (`resources_items.json[.gz]`, ...).
    pub hypixel_dir: PathBuf,
    /// Extracted NEU-REPO root (contains `constants/` and `items/`).
    pub neu_dir: PathBuf,
    /// SkyHanni-REPO data (`island_graphs/<ISLAND>.json`, named nodes only).
    pub skyhanni_dir: PathBuf,
}

impl DataPaths {
    /// Standard layout under a repository root: `data/` for curated files and
    /// `data/cache/{hypixel,neu}` for synced upstream data.
    pub fn from_root(root: &Path) -> DataPaths {
        DataPaths {
            registry_dir: root.join("data/registry"),
            translation_dir: root.join("data/translation"),
            farms_dir: root.join("data/farms"),
            hypixel_dir: root.join("data/cache/hypixel"),
            neu_dir: root.join("data/cache/neu"),
            skyhanni_dir: root.join("data/cache/skyhanni"),
        }
    }

    /// Layout used by tests: curated data from `data/`, upstream data from `fixtures/`.
    pub fn fixtures(root: &Path) -> DataPaths {
        DataPaths {
            registry_dir: root.join("data/registry"),
            translation_dir: root.join("data/translation"),
            farms_dir: root.join("data/farms"),
            hypixel_dir: root.join("fixtures/hypixel"),
            neu_dir: root.join("fixtures/neu"),
            skyhanni_dir: root.join("fixtures/skyhanni"),
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct RegistryMeta {
    pub items_last_updated: i64,
    pub skyblock_version: Option<String>,
    pub neu_files: usize,
    pub loaded_at_ms: i64,
}

#[derive(Debug)]
pub struct Registry {
    pub items: HashMap<String, ItemDef>,
    pub skills: BTreeMap<String, SkillDef>,
    pub collections: Vec<CollectionDef>,
    pub election: Option<Election>,
    pub neu: NeuData,
    pub curated: Curated,
    pub farms: Vec<FarmDesignDef>,
    /// Named places per island (SkyHanni-REPO) for guide waypoints.
    pub locations: locations::Locations,
    pub meta: RegistryMeta,
    name_index: Vec<(String, String)>,
}

/// Read `<dir>/<name>.json`, falling back to `<dir>/<name>.json.gz`.
pub fn read_json_maybe_gz(dir: &Path, name: &str) -> anyhow::Result<Value> {
    let plain = dir.join(format!("{name}.json"));
    if plain.exists() {
        let bytes = std::fs::read(&plain).with_context(|| format!("reading {}", plain.display()))?;
        return serde_json::from_slice(&bytes).with_context(|| format!("parsing {}", plain.display()));
    }
    let gz = dir.join(format!("{name}.json.gz"));
    let file = std::fs::File::open(&gz).with_context(|| format!("missing {} (and {})", plain.display(), gz.display()))?;
    let mut out = Vec::new();
    flate2::read::GzDecoder::new(file).read_to_end(&mut out)?;
    serde_json::from_slice(&out).with_context(|| format!("parsing {}", gz.display()))
}

impl Registry {
    pub fn load(paths: &DataPaths) -> anyhow::Result<Registry> {
        let curated = Curated::load(&paths.registry_dir, &paths.translation_dir)?;
        let farms = farms::load_dir(&paths.farms_dir)?;
        let neu = NeuData::load(&paths.neu_dir).context("loading NEU-REPO data")?;

        let items_json = read_json_maybe_gz(&paths.hypixel_dir, "resources_items")?;
        let mut items: HashMap<String, ItemDef> = items_json
            .get("items")
            .and_then(Value::as_array)
            .map(|a| a.iter().filter_map(ItemDef::from_value).map(|i| (i.id.clone(), i)).collect())
            .unwrap_or_default();
        // NEU-only items (keys, some NPC-only items) so names/recipes resolve.
        for (id, name) in &neu.display_names {
            if !items.contains_key(id) && !id.contains(':') {
                items.insert(
                    id.clone(),
                    ItemDef {
                        id: id.clone(),
                        name: name.clone(),
                        material: None,
                        tier: None,
                        category: None,
                        npc_sell_price: None,
                        stats: BTreeMap::new(),
                        requirements: Vec::new(),
                        catacombs_requirements: Vec::new(),
                        soulbound: None,
                        museum: false,
                        dungeon_item: false,
                        can_auction: None,
                        gemstone_slots: Vec::new(),
                        museum_donation_xp: None,
                        museum_category: None,
                        color: None,
                        durability: 0,
                        skin: None,
                        glowing: false,
                        neu_only: true,
                    },
                );
            }
        }

        let skills_json = read_json_maybe_gz(&paths.hypixel_dir, "resources_skills")?;
        let skills = hypixel::parse_skills(&skills_json);
        let collections = read_json_maybe_gz(&paths.hypixel_dir, "resources_collections").map(|v| hypixel::parse_collections(&v)).unwrap_or_default();
        let election = read_json_maybe_gz(&paths.hypixel_dir, "resources_election").ok().map(|v| hypixel::parse_election(&v));

        let mut name_index: Vec<(String, String)> = items.values().map(|i| (i.name.to_lowercase(), i.id.clone())).collect();
        name_index.sort();

        let meta = RegistryMeta {
            items_last_updated: items_json.get("lastUpdated").and_then(Value::as_i64).unwrap_or(0),
            skyblock_version: skills_json.get("version").and_then(Value::as_str).map(str::to_string),
            neu_files: neu.file_count,
            loaded_at_ms: chrono::Utc::now().timestamp_millis(),
        };
        tracing::info!(items = items.len(), neu_files = neu.file_count, recipes = neu.recipes.len(), "registry loaded");
        let locations = locations::Locations::load(&paths.skyhanni_dir.join("island_graphs"));
        Ok(Registry { items, skills, collections, election, neu, curated, farms, locations, meta, name_index })
    }

    pub fn item(&self, id: &str) -> Option<&ItemDef> {
        self.items.get(id)
    }

    /// How the mod's GUI draws an item: Hypixel items resource first (material, skin,
    /// colour, glint), then NEU (pets, books, runes), else paper. `id` may be a price key.
    pub fn icon(&self, id: &str) -> icons::IconSpec {
        let market = id.split('|').next().unwrap_or(id);
        if let Some(d) = self.items.get(market).filter(|d| !d.neu_only) {
            if let Some(m) = &d.material {
                return icons::IconSpec {
                    item: icons::modern_id(m, d.durability),
                    texture: d.skin.clone(),
                    color: d.color.as_deref().and_then(icons::parse_rgb),
                    glint: d.glowing,
                };
            }
        }
        if let Some(n) = self.neu.icons.get(market) {
            return icons::IconSpec { item: icons::modern_id(&n.itemid, n.damage), texture: n.texture.clone(), color: None, glint: n.glint };
        }
        let item = if market.starts_with("ENCHANTMENT_") { "minecraft:enchanted_book" } else { "minecraft:paper" };
        icons::IconSpec { item: item.into(), texture: None, color: None, glint: false }
    }

    /// Display name for any market id (items, pets, books).
    pub fn display_name(&self, id: &str) -> String {
        if let Some(i) = self.items.get(id) {
            return i.name.clone();
        }
        if let Some(n) = self.neu.display_names.get(id) {
            return n.clone();
        }
        if let Some(rest) = id.strip_prefix("PET:") {
            let mut parts = rest.split(':');
            let t = parts.next().unwrap_or_default();
            let r = parts.next().unwrap_or_default();
            return format!("[{}] {}", hypixel::title(r), hypixel::title(t));
        }
        if let Some(rest) = id.strip_prefix("ENCHANTMENT_") {
            if let Some((name, lvl)) = rest.rsplit_once('_') {
                return format!("{} {} (Book)", hypixel::title(name.trim_start_matches("ULTIMATE_")), lvl);
            }
        }
        hypixel::title(id)
    }

    /// Fuzzy item search by display name or id. Exact > prefix > substring > similarity.
    pub fn search_items(&self, query: &str, limit: usize) -> Vec<&ItemDef> {
        let q = query.trim().to_lowercase();
        if q.is_empty() {
            return Vec::new();
        }
        let q_id = q.to_ascii_uppercase().replace(' ', "_");
        let mut scored: Vec<(f64, &ItemDef)> = Vec::new();
        for (name, id) in &self.name_index {
            let Some(item) = self.items.get(id) else { continue };
            let score = if *name == q || *id == q_id {
                100.0
            } else if name.starts_with(&q) {
                80.0 - (name.len() as f64 - q.len() as f64) * 0.1
            } else if name.contains(&q) || id.contains(&q_id) {
                60.0 - (name.len() as f64 - q.len() as f64) * 0.1
            } else {
                let s = strsim::jaro_winkler(name, &q);
                if s < 0.85 {
                    continue;
                }
                s * 50.0
            };
            // Prefer obtainable, non-NEU-only entries slightly.
            let bonus = if item.neu_only { -1.0 } else { 0.0 };
            scored.push((score + bonus, item));
        }
        scored.sort_by(|a, b| b.0.total_cmp(&a.0).then_with(|| a.1.name.len().cmp(&b.1.name.len())));
        scored.into_iter().take(limit).map(|(_, i)| i).collect()
    }

    pub fn skill_table(&self, skill: &str) -> Option<&SkillDef> {
        self.skills.get(&skill.to_ascii_uppercase())
    }

    /// NPC sell price from the items resource.
    pub fn npc_price(&self, id: &str) -> Option<f64> {
        self.items.get(id).and_then(|i| i.npc_sell_price)
    }

    /// Protected (never translated) terms: curated jargon + every item name with 4+ chars.
    pub fn protected_terms(&self) -> Vec<String> {
        let mut terms: Vec<String> = self.curated.translation.protected.clone();
        for item in self.items.values() {
            if item.name.chars().count() >= 4 && !item.neu_only {
                terms.push(item.name.clone());
            }
        }
        terms.sort_by(|a, b| b.len().cmp(&a.len()).then(a.cmp(b)));
        terms.dedup();
        terms
    }
}

/// Locate the repository root (directory containing `data/registry`) from a start dir.
pub fn find_repo_root(start: &Path) -> Option<PathBuf> {
    let mut cur = Some(start);
    while let Some(dir) = cur {
        if dir.join("data/registry/market.toml").exists() {
            return Some(dir.to_path_buf());
        }
        cur = dir.parent();
    }
    None
}
