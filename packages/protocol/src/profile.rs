use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::common::{Freshness, Known};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PlayerRef {
    pub username: String,
    /// Undashed Minecraft UUID.
    pub uuid: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ProfileListEntry {
    pub profile_id: String,
    pub cute_name: String,
    pub selected: bool,
    pub game_mode: Option<String>,
    pub member_count: u32,
}

/// Level information for any XP-table based progression (skills, catacombs, slayer, HOTM, pets...).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct LevelInfo {
    pub id: String,
    pub name: String,
    pub level: u32,
    pub max_level: u32,
    /// Total accumulated XP.
    pub xp: f64,
    /// Progress inside the current level, 0..=1 (1 when maxed).
    pub progress: f64,
    /// XP still needed for the next level (None when maxed).
    pub xp_to_next: Option<f64>,
    /// Fractional level, e.g. 27.43 - useful for averages and charts.
    pub level_with_progress: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct SlayerInfo {
    pub id: String,
    pub boss_name: String,
    pub level: LevelInfo,
    /// Kills per tier, index 0 = Tier I.
    pub kills_by_tier: Vec<u64>,
    pub highest_tier_killed: Option<u8>,
    pub max_tier: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FloorStats {
    /// "E", "F1".."F7", "M1".."M7"
    pub floor: String,
    pub tier: u8,
    pub master: bool,
    pub completions: u64,
    pub best_score: Option<u32>,
    #[ts(type = "number | null")]
    pub fastest_s_plus_ms: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DungeonInfo {
    pub catacombs: LevelInfo,
    pub classes: Vec<LevelInfo>,
    pub class_average: f64,
    pub selected_class: Option<String>,
    pub floors: Vec<FloorStats>,
    #[ts(type = "number | null")]
    pub secrets: Option<u64>,
    /// Highest normal floor completed (0 = entrance only / none).
    pub highest_floor_completed: Option<u8>,
    pub highest_master_floor_completed: Option<u8>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PowderBalance {
    pub available: f64,
    pub spent: f64,
    /// available + spent
    pub total: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TreeNodeLevel {
    pub id: String,
    pub level: u32,
    pub enabled: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MiningInfo {
    pub hotm: Known<LevelInfo>,
    pub mithril_powder: Known<PowderBalance>,
    pub gemstone_powder: Known<PowderBalance>,
    pub glacite_powder: Known<PowderBalance>,
    pub tokens_available: Known<u32>,
    pub tokens_spent: Known<u32>,
    pub nodes: Known<Vec<TreeNodeLevel>>,
    pub selected_ability: Option<String>,
    pub crystals_found: Vec<String>,
    #[ts(type = "number | null")]
    pub mineshafts_entered: Option<u64>,
    #[ts(type = "number | null")]
    pub fossil_dust: Option<u64>,
    pub fossils_donated: u32,
    /// Stats parsed from the lore of equipped/owned mining gear.
    pub gear_stats: GearStats,
    pub best_tool: Option<ItemSummary>,
}

#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GearStats {
    /// Stat name (e.g. "Mining Speed") -> summed value from lore lines of the considered items.
    pub stats: BTreeMap<String, f64>,
    /// Items the stats were read from.
    pub sources: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ItemSummary {
    pub location: String,
    pub skyblock_id: String,
    pub name: String,
    pub rarity: Option<String>,
    pub count: u32,
    pub reforge: Option<String>,
    pub stars: u8,
    pub master_stars: u8,
    pub recombobulated: bool,
    pub hot_potato_books: u8,
    pub enchantments: BTreeMap<String, u32>,
    pub gemstones: Vec<String>,
    pub soulbound: bool,
    pub pet: Option<PetSummary>,
    /// Stats parsed from lore ("Strength" -> 150).
    pub lore_stats: BTreeMap<String, f64>,
    pub estimated_value: Option<f64>,
    pub value_confidence: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PetSummary {
    pub pet_type: String,
    pub name: String,
    pub tier: String,
    pub level: u32,
    pub max_level: u32,
    pub exp: f64,
    pub held_item: Option<String>,
    pub active: bool,
    pub candy_used: u32,
    pub skin: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct AccessoryInfo {
    pub accessories: Vec<ItemSummary>,
    /// MP computed by SkyPilot from the accessory bag contents.
    pub magical_power: u32,
    /// `highest_magical_power` stored by Hypixel (includes things SkyPilot may not model).
    pub highest_magical_power_api: Option<u32>,
    pub selected_power: Option<String>,
    pub unlocked_powers: Vec<String>,
    pub tuning: BTreeMap<String, u32>,
    pub bag_upgrades_purchased: Option<u32>,
    /// Accessories that do not count toward MP (duplicates / lower tiers of a family / inactive).
    pub inactive: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GearInfo {
    pub armor: Vec<ItemSummary>,
    pub equipment: Vec<ItemSummary>,
    pub weapons: Vec<ItemSummary>,
    pub tools: Vec<ItemSummary>,
    pub wardrobe: Vec<Vec<ItemSummary>>,
    pub equipped_wardrobe_slot: Option<u32>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct EconomyInfo {
    pub purse: f64,
    pub bank: Known<f64>,
    pub personal_bank: Known<f64>,
    pub motes: Option<f64>,
    pub essence: BTreeMap<String, u64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct NetworthBreakdown {
    pub total: f64,
    pub liquid: f64,
    pub semi_liquid: f64,
    pub illiquid: f64,
    pub soulbound: f64,
    pub by_category: BTreeMap<String, f64>,
    /// Top valued items.
    pub top_items: Vec<ItemSummary>,
    /// Items that could not be priced (no market data).
    pub unpriced: Vec<String>,
    pub confidence: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct GardenInfo {
    pub garden_level: Known<LevelInfo>,
    pub crop_milestones: BTreeMap<String, u32>,
    pub crop_upgrades: BTreeMap<String, u32>,
    pub unlocked_plots: u32,
    pub visitors_served: Option<u32>,
    pub unique_visitors: Option<u32>,
    #[ts(type = "number | null")]
    pub copper: Option<u64>,
    pub composter_upgrades: BTreeMap<String, u32>,
    pub farming_gear_stats: GearStats,
    pub best_tools: Vec<ItemSummary>,
    pub jacob_medals: BTreeMap<String, u32>,
    pub personal_bests: BTreeMap<String, f64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct CollectionEntry {
    pub id: String,
    pub name: String,
    pub category: String,
    #[ts(type = "number")]
    pub amount: u64,
    pub tier: u32,
    pub max_tier: u32,
    #[ts(type = "number | null")]
    pub next_tier_amount: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct KuudraInfo {
    /// tier id ("none","hot","burning","fiery","infernal") -> completions
    pub completions: BTreeMap<String, u64>,
    pub highest_tier_completed: Option<String>,
    pub selected_faction: Option<String>,
    pub mage_reputation: Option<i64>,
    pub barbarian_reputation: Option<i64>,
    pub dojo_points: BTreeMap<String, i64>,
    pub minibosses_killed: Vec<String>,
    pub attribute_stacks: BTreeMap<String, u32>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct RiftInfo {
    pub motes_purse: Option<f64>,
    pub enigma_souls: u32,
    pub timecharms: Vec<String>,
    pub vampire_slayer_level: Option<u32>,
    pub completed_areas: Vec<String>,
    pub dead_cats_found: u32,
    pub montezuma_unlocked: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ChocolateFactoryInfo {
    #[ts(type = "number")]
    pub chocolate: u64,
    #[ts(type = "number")]
    pub total_chocolate: u64,
    #[ts(type = "number")]
    pub chocolate_since_prestige: u64,
    pub prestige_level: u32,
    pub employees: BTreeMap<String, u32>,
    pub unique_rabbits: u32,
    pub time_tower_level: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct FishingInfo {
    #[ts(type = "number | null")]
    pub treasures_caught: Option<u64>,
    #[ts(type = "number | null")]
    pub trophy_fish_caught: Option<u64>,
    pub trophy_fish: BTreeMap<String, u64>,
    pub gear_stats: GearStats,
    pub best_rod: Option<ItemSummary>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ForagingInfo {
    pub hotf: Known<LevelInfo>,
    pub forest_whispers: Known<f64>,
    pub nodes: Known<Vec<TreeNodeLevel>>,
    pub tree_gifts: BTreeMap<String, u64>,
    pub agatha_perks: BTreeMap<String, u32>,
    pub gear_stats: GearStats,
    pub best_axe: Option<ItemSummary>,
    pub shards_owned: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MinionInfo {
    pub crafted_tiers: BTreeMap<String, u32>,
    pub unique_crafted: u32,
    pub slots_from_community_upgrades: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct MuseumInfo {
    pub appraised: bool,
    #[ts(type = "number | null")]
    pub value: Option<u64>,
    pub donated_items: Vec<String>,
    pub donated_count: u32,
}

/// 0-100 score per area plus strong/weak lists.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ProgressionScores {
    pub areas: Vec<AreaScore>,
    pub overall: f64,
    pub stage: GameStage,
    pub strong_areas: Vec<String>,
    pub weak_areas: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct AreaScore {
    pub id: String,
    pub name: String,
    pub score: f64,
    /// Inputs that produced the score (for transparency).
    pub basis: Vec<String>,
    pub known: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[ts(export)]
pub enum GameStage {
    EarlyGame,
    MidGame,
    LateGame,
    EndGame,
}

/// API toggles that the player disabled; drives "Not available from API" labels.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ApiAccess {
    pub inventory: bool,
    pub banking: bool,
    pub collections: bool,
    pub skills: bool,
    pub museum: bool,
    pub garden: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct PlayerAnalysis {
    pub player: PlayerRef,
    pub profile_id: String,
    pub cute_name: String,
    pub game_mode: Option<String>,
    pub fetched: Freshness,
    pub api_access: ApiAccess,
    pub skyblock_level: Known<f64>,
    pub skills: Known<Vec<LevelInfo>>,
    pub skill_average: Known<f64>,
    pub dungeons: Known<DungeonInfo>,
    pub slayers: Known<Vec<SlayerInfo>>,
    pub mining: Known<MiningInfo>,
    pub garden: Known<GardenInfo>,
    pub accessories: Known<AccessoryInfo>,
    pub pets: Known<Vec<PetSummary>>,
    pub gear: Known<GearInfo>,
    pub economy: EconomyInfo,
    pub networth: Known<NetworthBreakdown>,
    pub collections: Known<Vec<CollectionEntry>>,
    pub museum: Known<MuseumInfo>,
    pub kuudra: Known<KuudraInfo>,
    pub rift: Known<RiftInfo>,
    pub chocolate_factory: Known<ChocolateFactoryInfo>,
    pub fishing: Known<FishingInfo>,
    pub foraging: Known<ForagingInfo>,
    pub minions: Known<MinionInfo>,
    #[ts(type = "number | null")]
    pub bestiary_kills: Option<u64>,
    pub fairy_souls: Option<u32>,
    pub sacks: Known<BTreeMap<String, u64>>,
    pub progression: ProgressionScores,
    pub warnings: Vec<String>,
}

/// A stored progress snapshot (created only when the user runs an analysis).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct ProgressSnapshot {
    #[ts(type = "number")]
    pub taken_at_ms: i64,
    pub networth: Option<f64>,
    pub skyblock_level: Option<f64>,
    pub skill_average: Option<f64>,
    pub magical_power: Option<f64>,
    pub catacombs: Option<f64>,
    pub slayer_xp_total: Option<f64>,
    pub mithril_powder_total: Option<f64>,
    pub gemstone_powder_total: Option<f64>,
    pub hotm: Option<f64>,
    pub garden_level: Option<f64>,
}
