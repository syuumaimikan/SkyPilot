//! Deterministic natural-language goal parser (English + Japanese).
//!
//! "Enderman Slayer 7にしてTerminatorを入手したい" ->
//!   [SlayerLevel{enderman, 7}, AcquireItem{TERMINATOR}]
//!
//! Items are resolved against the live items resource (fuzzy) plus community aliases from
//! `data/registry/goals.toml`. No LLM is involved, so a goal can never be invented.

use std::sync::OnceLock;

use regex::Regex;
use skypilot_data::Registry;
use skypilot_protocol::goals::{GoalKind, GoalParseResult, GoalSpec};
use unicode_normalization::UnicodeNormalization;

fn number_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"(?i)(\d+(?:[.,]\d+)?)\s*(k|m|b|万|億)?").unwrap())
}

fn floor_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"(?i)\b([fm])\s?([1-7])\b|(?:floor|フロア)\s*([1-7])").unwrap())
}

/// Words removed before item matching (particles, verbs, filler).
const FILLER: &[&str] = &[
    "を入手したい", "を手に入れたい", "を使えるようにしたい", "を使いたい", "を作りたい", "を買いたい", "が欲しい", "がほしい", "欲しい", "ほしい",
    "入手したい", "手に入れたい", "使えるようにしたい", "したい", "にしたい", "最短で", "最速で", "早く", "安く", "効率よく", "なるべく", "を", "が", "に", "で",
    "の", "まで", "目標", "i want to", "i want", "want to", "want", "get a", "get an", "get", "obtain", "buy", "craft", "unlock", "reach", "a ", "an ", "the ",
    "my ", "fastest", "asap", "please", "to use",
];

fn normalize(text: &str) -> String {
    text.nfkc().collect::<String>().to_lowercase()
}

fn split_clauses(text: &str) -> Vec<String> {
    let t = normalize(text);
    // "採掘60と農業50": 「と」 directly after a number joins two goals.
    let t = Regex::new(r"(\d)\s*と").unwrap().replace_all(&t, "$1、").to_string();
    // Japanese connectors after a number/word: "7にして", "して", "と", "、"
    let re = Regex::new(r"(?:にして|してから|して|、|。|,|;|\band\b|\bthen\b|&|\+|・|そして|それから|あと)").unwrap();
    re.split(&t).map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect()
}

fn first_number(s: &str, magnitudes: &std::collections::BTreeMap<String, f64>) -> Option<(f64, bool)> {
    let c = number_re().captures(s)?;
    let n: f64 = c[1].replace(',', ".").parse().ok()?;
    let mag = c.get(2).map(|m| m.as_str().to_lowercase());
    match mag {
        Some(m) => Some((n * magnitudes.get(&m).copied().unwrap_or(1.0), true)),
        None => Some((n, false)),
    }
}

fn contains_any(s: &str, words: &[String]) -> bool {
    words.iter().any(|w| {
        let w = w.to_lowercase();
        if w.chars().all(|c| c.is_ascii_alphanumeric() || c == ' ') && w.len() <= 3 {
            // short latin keywords must match as whole words ("mp" not in "temple")
            Regex::new(&format!(r"\b{}\b", regex::escape(&w))).map(|r| r.is_match(s)).unwrap_or(false)
        } else {
            s.contains(&w)
        }
    })
}

pub struct GoalParser<'a> {
    registry: &'a Registry,
}

impl<'a> GoalParser<'a> {
    pub fn new(registry: &'a Registry) -> Self {
        GoalParser { registry }
    }

    fn kw(&self, key: &str) -> Vec<String> {
        self.registry.curated.goals.keywords.get(key).cloned().unwrap_or_default()
    }

    pub fn parse(&self, text: &str) -> GoalParseResult {
        let mut goals: Vec<GoalSpec> = Vec::new();
        let mut unmatched = Vec::new();
        let mut suggestions = Vec::new();
        for clause in split_clauses(text) {
            match self.parse_clause(&clause) {
                ClauseResult::Goal(g) => {
                    if !goals.iter().any(|x| x.kind == g.kind) {
                        goals.push(g);
                    }
                }
                ClauseResult::Ambiguous(best, alts) => {
                    if !goals.iter().any(|x| x.kind == best.kind) {
                        goals.push(best);
                    }
                    suggestions.extend(alts);
                }
                ClauseResult::None => unmatched.push(clause),
            }
        }
        GoalParseResult { goals, unmatched, suggestions }
    }

    fn spec(kind: GoalKind, title: String, raw: &str, conf: f64) -> GoalSpec {
        GoalSpec { kind, title, raw: raw.to_string(), match_confidence: conf }
    }

    fn parse_clause(&self, clause: &str) -> ClauseResult {
        let vocab = &self.registry.curated.goals;
        let num = first_number(clause, &vocab.magnitudes);
        let level = num.filter(|(n, mag)| !mag && *n >= 0.0 && *n < 100_000.0).map(|(n, _)| n as u32);

        // Slayer (alias or "slayer" keyword + level)
        let slayer = self.registry.curated.slayers.iter().find(|s| {
            contains_any(clause, &s.aliases) || clause.contains(&s.id) || clause.contains(&s.name.to_lowercase())
        });
        if let (Some(s), Some(l)) = (slayer, level) {
            if contains_any(clause, &self.kw("slayer")) || l <= 9 {
                return ClauseResult::Goal(Self::spec(GoalKind::SlayerLevel { slayer: s.id.clone(), level: l.min(9) }, format!("{} Slayer {}", s.short, l), clause, 0.95));
            }
        }
        // Catacombs
        if contains_any(clause, &self.kw("catacombs")) {
            if let Some(l) = level.filter(|l| *l <= 50) {
                return ClauseResult::Goal(Self::spec(GoalKind::CatacombsLevel { level: l }, format!("Catacombs {l}"), clause, 0.95));
            }
        }
        // Dungeon class
        for (alias, class) in &vocab.class_aliases {
            if clause.contains(&alias.to_lowercase()) {
                if let Some(l) = level.filter(|l| *l <= 50) {
                    return ClauseResult::Goal(Self::spec(GoalKind::ClassLevel { class: class.clone(), level: l }, format!("{} {}", skypilot_items::fingerprint::title_case(class), l), clause, 0.9));
                }
            }
        }
        // HOTM / HOTF
        if contains_any(clause, &self.kw("hotm")) {
            if let Some(l) = level.filter(|l| *l <= 10) {
                return ClauseResult::Goal(Self::spec(GoalKind::HotmLevel { level: l }, format!("HOTM {l}"), clause, 0.95));
            }
        }
        if contains_any(clause, &self.kw("hotf")) {
            if let Some(l) = level.filter(|l| *l <= 10) {
                return ClauseResult::Goal(Self::spec(GoalKind::HotfLevel { level: l }, format!("HOTF {l}"), clause, 0.95));
            }
        }
        // Powder target ("gemstone powder 8m")
        if contains_any(clause, &self.kw("powder")) {
            if let Some((n, _)) = num {
                let powder = if clause.contains("gem") || clause.contains("ジェム") || clause.contains("宝石") {
                    "GEMSTONE"
                } else if clause.contains("glacite") || clause.contains("グレイサイト") || clause.contains("グラサイト") || clause.contains("氷河") {
                    "GLACITE"
                } else {
                    "MITHRIL"
                };
                return ClauseResult::Goal(Self::spec(GoalKind::PowderTarget { powder: powder.into(), amount: n }, format!("{} Powder {}", skypilot_items::fingerprint::title_case(powder), crate::ctx::fmt_coins(n)), clause, 0.85));
            }
        }
        // Magical power
        if contains_any(clause, &self.kw("magical_power")) {
            if let Some((n, _)) = num.filter(|(n, _)| *n > 0.0 && *n < 10_000.0) {
                return ClauseResult::Goal(Self::spec(GoalKind::MagicalPower { target: n as u32 }, format!("Magical Power {}", n as u32), clause, 0.95));
            }
        }
        // SkyBlock level (before skills: "skyblock level 300")
        if contains_any(clause, &self.kw("skyblock_level")) {
            if let Some(l) = level {
                return ClauseResult::Goal(Self::spec(GoalKind::SkyblockLevel { level: l }, format!("SkyBlock Level {l}"), clause, 0.95));
            }
        }
        // Garden level
        if contains_any(clause, &self.kw("garden")) {
            if let Some(l) = level.filter(|l| *l <= 50) {
                return ClauseResult::Goal(Self::spec(GoalKind::GardenLevel { level: l }, format!("Garden {l}"), clause, 0.9));
            }
        }
        // Kuudra tier
        if contains_any(clause, &self.kw("kuudra")) {
            let tiers = &self.registry.curated.kuudra.tiers;
            let named = tiers.iter().find(|t| clause.contains(&t.name.to_lowercase()) || clause.contains(&t.id));
            let by_num = level.and_then(|l| tiers.iter().find(|t| t.order as u32 == l));
            if let Some(t) = named.or(by_num) {
                return ClauseResult::Goal(Self::spec(GoalKind::KuudraTier { tier: t.id.clone() }, format!("Kuudra {}", t.name), clause, 0.9));
            }
        }
        // Skill level
        for (alias, skill) in &vocab.skill_aliases {
            if contains_any(clause, std::slice::from_ref(alias)) {
                if let Some(l) = level.filter(|l| *l <= 60) {
                    return ClauseResult::Goal(Self::spec(GoalKind::SkillLevel { skill: skill.clone(), level: l }, format!("{} {}", skypilot_items::fingerprint::title_case(skill), l), clause, 0.95));
                }
            }
        }
        // Floor completion ("f7", "m5", "floor 7")
        if let Some(c) = floor_re().captures(clause) {
            let (master, tier) = match (c.get(1), c.get(2), c.get(3)) {
                (Some(k), Some(t), _) => (k.as_str() == "m", t.as_str().parse::<u8>().unwrap_or(1)),
                (_, _, Some(t)) => (contains_any(clause, &self.kw("master")), t.as_str().parse::<u8>().unwrap_or(1)),
                _ => (false, 1),
            };
            // only when the clause is about completing/clearing, not an item name containing "m5"
            if clause.len() <= 24 || clause.contains("clear") || clause.contains("complete") || clause.contains("クリア") {
                let title = format!("{}{} completion", if master { "M" } else { "F" }, tier);
                return ClauseResult::Goal(Self::spec(GoalKind::FloorCompletion { floor: tier, master }, title, clause, 0.85));
            }
        }
        // Coins / networth
        if let Some((n, has_mag)) = num {
            if contains_any(clause, &self.kw("networth")) {
                return ClauseResult::Goal(Self::spec(GoalKind::Networth { amount: n }, format!("Networth {}", crate::ctx::fmt_coins(n)), clause, 0.9));
            }
            if contains_any(clause, &self.kw("coins")) || (has_mag && n >= 100_000.0) {
                return ClauseResult::Goal(Self::spec(GoalKind::Coins { amount: n }, format!("{} coins", crate::ctx::fmt_coins(n)), clause, 0.85));
            }
        }
        // Item acquisition
        self.match_item(clause)
    }

    fn match_item(&self, clause: &str) -> ClauseResult {
        let mut cleaned = format!(" {clause} ");
        for f in FILLER {
            cleaned = cleaned.replace(f, " ");
        }
        let cleaned = cleaned.split_whitespace().collect::<Vec<_>>().join(" ");
        let cleaned = cleaned.trim_matches(|c: char| !c.is_alphanumeric() && c != '\'').to_string();
        if cleaned.chars().count() < 2 {
            return ClauseResult::None;
        }
        let vocab = &self.registry.curated.goals;
        // aliases: exact first, then longest alias contained in the clause
        if let Some(id) = vocab.item_aliases.get(&cleaned) {
            return ClauseResult::Goal(Self::spec(GoalKind::AcquireItem { item_id: id.clone() }, self.registry.display_name(id), clause, 0.98));
        }
        let mut aliases: Vec<(&String, &String)> = vocab.item_aliases.iter().collect();
        aliases.sort_by(|a, b| b.0.len().cmp(&a.0.len()));
        for (alias, id) in aliases {
            if alias.chars().count() >= 3 && cleaned.contains(alias.as_str()) {
                return ClauseResult::Goal(Self::spec(GoalKind::AcquireItem { item_id: id.clone() }, self.registry.display_name(id), clause, 0.9));
            }
        }
        let hits = self.registry.search_items(&cleaned, 4);
        let Some(best) = hits.first() else { return ClauseResult::None };
        let name = best.name.to_lowercase();
        let conf = if name == cleaned {
            0.99
        } else if name.contains(&cleaned) || cleaned.contains(&name) {
            0.85
        } else {
            strsim::jaro_winkler(&name, &cleaned) * 0.9
        };
        // Fuzzy-only matches must be strong: a wrong item goal is worse than asking again.
        if conf < 0.75 {
            return ClauseResult::None;
        }
        let goal = Self::spec(GoalKind::AcquireItem { item_id: best.id.clone() }, best.name.clone(), clause, conf);
        if conf < 0.9 && hits.len() > 1 {
            let alts = hits[1..].iter().map(|h| Self::spec(GoalKind::AcquireItem { item_id: h.id.clone() }, h.name.clone(), clause, conf * 0.9)).collect();
            return ClauseResult::Ambiguous(goal, alts);
        }
        ClauseResult::Goal(goal)
    }
}

enum ClauseResult {
    Goal(GoalSpec),
    Ambiguous(GoalSpec, Vec<GoalSpec>),
    None,
}
