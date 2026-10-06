//! Typed views of `data/registry/*.toml` (SkyPilot curated registry).

use std::collections::BTreeMap;
use std::path::Path;

use anyhow::Context;
use serde::Deserialize;
use skypilot_calc::fees::{AhFeeConfig, DurationFee, FeeBracket};

fn load<T: for<'de> Deserialize<'de>>(dir: &Path, file: &str) -> anyhow::Result<T> {
    let path = dir.join(file);
    let text = std::fs::read_to_string(&path).with_context(|| format!("reading {}", path.display()))?;
    toml::from_str(&text).with_context(|| format!("parsing {}", path.display()))
}

// ------------------------------------------------------------------ market

#[derive(Debug, Clone, Deserialize)]
pub struct MarketConfig {
    pub auction_house: AuctionHouseCfg,
    pub bazaar: BazaarCfg,
    pub flip_defaults: FlipDefaults,
    pub freshness: FreshnessCfg,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AuctionHouseCfg {
    pub source: String,
    pub auction_creation_rate: f64,
    pub claim_tax_rate: f64,
    pub claim_tax_threshold: f64,
    pub derpy_claim_tax_multiplier: f64,
    pub bin_brackets: Vec<FeeBracket>,
    pub duration_fees: Vec<DurationFee>,
}

impl AuctionHouseCfg {
    pub fn fee_config(&self, derpy: bool) -> AhFeeConfig {
        AhFeeConfig {
            bin_brackets: self.bin_brackets.clone(),
            auction_creation_rate: self.auction_creation_rate,
            duration_fees: self.duration_fees.clone(),
            claim_tax_rate: self.claim_tax_rate,
            claim_tax_threshold: self.claim_tax_threshold,
            claim_tax_multiplier: if derpy { self.derpy_claim_tax_multiplier } else { 1.0 },
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct BazaarCfg {
    pub source: String,
    pub tax_rate: f64,
    pub flipper_reduction_per_level: f64,
    pub flipper_max_level: u32,
    pub max_orders: u32,
    pub max_orders_with_flipper: u32,
    pub order_expiry_days: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FlipDefaults {
    pub listing_hours: f64,
    pub min_profit: f64,
    pub min_roi: f64,
    pub max_results: usize,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FreshnessCfg {
    pub auctions_stale_after: i64,
    pub ended_auctions_stale_after: i64,
    pub bazaar_stale_after: i64,
    pub resources_stale_after: i64,
    pub profile_stale_after: i64,
}

// ------------------------------------------------------------------ accessories

#[derive(Debug, Clone, Deserialize)]
pub struct AccessoriesConfig {
    pub source: String,
    pub mp_per_rarity: BTreeMap<String, u32>,
    #[serde(default)]
    pub mp_multiplier: BTreeMap<String, u32>,
    #[serde(default)]
    pub fixed_mp: Vec<FixedMp>,
    #[serde(default)]
    pub extra_ignored: Vec<String>,
    pub max_coins_per_mp_default: f64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FixedMp {
    pub id: String,
    pub mp: u32,
    pub condition: String,
    pub applied: bool,
}

// ------------------------------------------------------------------ slayers

#[derive(Debug, Clone, Deserialize)]
pub struct SlayersFile {
    pub slayers: Vec<SlayerDefinition>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct KillMinutes {
    pub required: Vec<f64>,
    pub recommended: Vec<f64>,
    pub luxury: Vec<f64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GearSlot {
    pub slot: String,
    #[serde(default)]
    pub required: Vec<String>,
    #[serde(default)]
    pub recommended: Vec<String>,
    #[serde(default)]
    pub luxury: Vec<String>,
}

/// Data-driven slayer definition. New slayers are added in `slayers.toml` only.
#[derive(Debug, Clone, Deserialize)]
pub struct SlayerDefinition {
    pub id: String,
    pub name: String,
    pub short: String,
    #[serde(default)]
    pub aliases: Vec<String>,
    pub neu_boss_prefix: String,
    pub max_tier: u8,
    pub area: String,
    pub unlock: String,
    #[serde(default)]
    pub unlock_slayer: String,
    #[serde(default)]
    pub unlock_tier: u8,
    #[serde(default)]
    pub tier_xp: Vec<f64>,
    #[serde(default)]
    pub tier_costs: Vec<f64>,
    #[serde(default = "coins")]
    pub cost_currency: String,
    #[serde(default)]
    pub recommended_combat: Vec<u32>,
    #[serde(default)]
    pub recommended_mp: Vec<u32>,
    #[serde(default)]
    pub spawn_combat_xp: Vec<f64>,
    pub kill_minutes: KillMinutes,
    #[serde(default)]
    pub gear: Vec<GearSlot>,
}

fn coins() -> String {
    "coins".into()
}

impl SlayerDefinition {
    /// NEU drop-table id for a tier (1-based), e.g. `VOIDGLOOM_SERAPH_4_BOSS`.
    pub fn boss_drop_id(&self, tier: u8) -> String {
        format!("{}_{}_BOSS", self.neu_boss_prefix, tier)
    }
}

// ------------------------------------------------------------------ dungeons / kuudra

#[derive(Debug, Clone, Deserialize)]
pub struct DungeonsConfig {
    pub source: String,
    pub floors: Vec<FloorDef>,
    #[serde(default)]
    pub readiness: BTreeMap<String, FloorReadinessDef>,
    #[serde(default)]
    pub run_minutes: BTreeMap<String, f64>,
    #[serde(default)]
    pub base_catacombs_xp: BTreeMap<String, f64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FloorDef {
    pub id: String,
    pub tier: u8,
    pub master: bool,
    pub boss: String,
    pub required_catacombs: u32,
    pub required_combat: u32,
    pub requires_floor: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FloorReadinessDef {
    pub catacombs: u32,
    pub magical_power: u32,
    pub class_average: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct KuudraConfig {
    pub source: String,
    pub tiers: Vec<KuudraTierDef>,
    #[serde(default)]
    pub readiness: BTreeMap<String, u32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct KuudraTierDef {
    pub id: String,
    pub name: String,
    pub order: u8,
    pub required_reputation: i64,
    pub requires_tier: String,
    pub key: String,
    pub skyblock_xp: u32,
}

// ------------------------------------------------------------------ mining

#[derive(Debug, Clone, Deserialize)]
pub struct MiningConfig {
    pub source: String,
    pub default_uptime: f64,
    pub targets: Vec<MiningTargetDef>,
    pub areas: Vec<MiningAreaDef>,
    pub hotm_presets: Vec<HotmPreset>,
    pub powder_methods: Vec<PowderMethod>,
    pub powder_buff_perk: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct MiningTargetDef {
    pub id: String,
    pub name: String,
    pub block_strength: f64,
    pub instant_factor: f64,
    pub drop_item: String,
    pub base_drops: f64,
    pub pristine_ratio: f64,
    pub fortune_stat: String,
    #[serde(default)]
    pub mithril_powder_per_block: f64,
    pub areas: Vec<String>,
    #[serde(default)]
    pub requirements: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct MiningAreaDef {
    pub id: String,
    pub name: String,
    pub required_hotm: u32,
    #[serde(default)]
    pub required_mining: u32,
    pub description: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct HotmPreset {
    pub id: String,
    pub name: String,
    pub description: String,
    pub ability: String,
    pub targets: BTreeMap<String, u32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PowderMethod {
    pub id: String,
    pub powder: String,
    pub name: String,
    pub kind: String,
    #[serde(default)]
    pub target: String,
    #[serde(default)]
    pub powder_per_block: f64,
    #[serde(default)]
    pub per_hour: f64,
    pub verified: bool,
    pub note: String,
}

// ------------------------------------------------------------------ farming

#[derive(Debug, Clone, Deserialize)]
pub struct FarmingConfig {
    pub source: String,
    pub max_blocks_per_second: f64,
    pub default_uptime: f64,
    pub crops: Vec<CropDef>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CropDef {
    pub id: String,
    pub name: String,
    pub item: String,
    pub base_drops: f64,
    pub npc_price: f64,
    pub fortune_stat: String,
    pub tools: Vec<String>,
    pub design: String,
}

// ------------------------------------------------------------------ goals / money / progression / misc

#[derive(Debug, Clone, Deserialize)]
pub struct GoalVocabulary {
    pub item_aliases: BTreeMap<String, String>,
    pub skill_aliases: BTreeMap<String, String>,
    pub class_aliases: BTreeMap<String, String>,
    pub keywords: BTreeMap<String, Vec<String>>,
    pub magnitudes: BTreeMap<String, f64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct MoneyMethodsFile {
    pub methods: Vec<MoneyMethodDef>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct MoneyMethodDef {
    pub id: String,
    pub name: String,
    pub category: String,
    pub estimator: String,
    pub risk: String,
    #[serde(default)]
    pub requirements: Vec<String>,
    pub link: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProgressionConfig {
    pub areas: Vec<ProgressionArea>,
    pub stages: StageThresholds,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProgressionArea {
    pub id: String,
    pub name: String,
    pub weight: f64,
    pub metrics: Vec<ProgressionMetric>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProgressionMetric {
    pub metric: String,
    pub anchors: Vec<[f64; 2]>,
    #[serde(default = "one")]
    pub weight: f64,
}

fn one() -> f64 {
    1.0
}

#[derive(Debug, Clone, Deserialize)]
pub struct StageThresholds {
    pub mid_game: f64,
    pub late_game: f64,
    pub end_game: f64,
    pub strong_threshold: f64,
    pub weak_threshold: f64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CalendarConfig {
    pub epoch_ms: i64,
    pub day_ms: i64,
    pub days_per_month: u32,
    pub months: Vec<String>,
    pub events: Vec<CalendarEvent>,
    #[serde(default)]
    pub recurring: Vec<RecurringEvent>,
}

/// An event that repeats every `every_days` SkyBlock days (on days where
/// `day % every_days == offset_days`, counted from the epoch).
#[derive(Debug, Clone, Deserialize)]
pub struct RecurringEvent {
    pub id: String,
    pub name: String,
    pub every_days: u32,
    pub offset_days: u32,
    pub duration_days: u32,
    pub source: String,
    pub checked: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct SbDate {
    pub month: u32,
    pub day: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CalendarEvent {
    pub id: String,
    pub name: String,
    pub start: SbDate,
    pub end: SbDate,
    pub value: String,
    #[serde(default)]
    pub progression: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DailyFile {
    pub tasks: Vec<DailyTask>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DailyTask {
    pub id: String,
    pub name: String,
    pub detail: String,
    #[serde(default)]
    pub requires: String,
    #[serde(default)]
    pub tags: Vec<String>,
    pub minutes: f64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct TranslationDictionaryFile {
    pub version: String,
    pub protected: Vec<String>,
    pub phrases: BTreeMap<String, String>,
    #[serde(default)]
    pub patterns: Vec<PatternDef>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PatternDef {
    pub regex: String,
    pub template: String,
}

// ------------------------------------------------------------------ guides

#[derive(Debug, Clone, Deserialize)]
pub struct GuidesFile {
    pub guides: Vec<GuideDef>,
}

/// A "how to progress" guide for an island. Step locations name places in SkyHanni-REPO's
/// island graphs (MIT); `done` uses the same requirement syntax as money methods.
#[derive(Debug, Clone, Deserialize)]
pub struct GuideDef {
    pub id: String,
    /// SkyHanni island id, e.g. "DWARVEN_MINES".
    pub island: String,
    pub title: String,
    pub title_ja: String,
    pub source: String,
    pub checked: String,
    pub steps: Vec<GuideStepDef>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GuideStepDef {
    pub title: String,
    pub title_ja: String,
    pub text: String,
    pub text_ja: String,
    /// Island of the waypoint when it differs from the guide's island.
    pub island: Option<String>,
    /// Named location (NPC / area / warp) shown as the waypoint.
    pub location: Option<String>,
    /// Requirement expression that marks the step as done, e.g. "skill:MINING>=12".
    pub done: Option<String>,
}

#[derive(Debug, Clone)]
pub struct Curated {
    pub market: MarketConfig,
    pub accessories: AccessoriesConfig,
    pub slayers: Vec<SlayerDefinition>,
    pub dungeons: DungeonsConfig,
    pub kuudra: KuudraConfig,
    pub mining: MiningConfig,
    pub farming: FarmingConfig,
    pub goals: GoalVocabulary,
    pub money_methods: Vec<MoneyMethodDef>,
    pub progression: ProgressionConfig,
    pub calendar: CalendarConfig,
    pub daily: Vec<DailyTask>,
    pub translation: TranslationDictionaryFile,
    pub guides: Vec<GuideDef>,
}

impl Curated {
    pub fn load(registry_dir: &Path, translation_dir: &Path) -> anyhow::Result<Curated> {
        Ok(Curated {
            market: load(registry_dir, "market.toml")?,
            accessories: load(registry_dir, "accessories.toml")?,
            slayers: load::<SlayersFile>(registry_dir, "slayers.toml")?.slayers,
            dungeons: load(registry_dir, "dungeons.toml")?,
            kuudra: load(registry_dir, "kuudra.toml")?,
            mining: load(registry_dir, "mining.toml")?,
            farming: load(registry_dir, "farming.toml")?,
            goals: load(registry_dir, "goals.toml")?,
            money_methods: load::<MoneyMethodsFile>(registry_dir, "money_methods.toml")?.methods,
            progression: load(registry_dir, "progression.toml")?,
            calendar: load(registry_dir, "calendar.toml")?,
            daily: load::<DailyFile>(registry_dir, "daily.toml")?.tasks,
            translation: load(translation_dir, "dictionary.toml")?,
            guides: load::<GuidesFile>(registry_dir, "guides.toml")?.guides,
        })
    }

    pub fn slayer(&self, id: &str) -> Option<&SlayerDefinition> {
        let id = id.to_ascii_lowercase();
        self.slayers.iter().find(|s| s.id == id || s.aliases.iter().any(|a| a.to_lowercase() == id))
    }
}
