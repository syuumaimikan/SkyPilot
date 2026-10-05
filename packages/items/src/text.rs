//! Formatting-code and text-component helpers.

use std::collections::BTreeMap;
use std::sync::OnceLock;

use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::Value;

/// Remove `§x` formatting codes.
pub fn strip_formatting(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            out.push(c);
        }
    }
    out
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Rarity {
    Common,
    Uncommon,
    Rare,
    Epic,
    Legendary,
    Mythic,
    Divine,
    Special,
    VerySpecial,
    Ultimate,
    Admin,
}

impl Rarity {
    pub const ALL: [Rarity; 11] = [
        Rarity::Common,
        Rarity::Uncommon,
        Rarity::Rare,
        Rarity::Epic,
        Rarity::Legendary,
        Rarity::Mythic,
        Rarity::Divine,
        Rarity::Special,
        Rarity::VerySpecial,
        Rarity::Ultimate,
        Rarity::Admin,
    ];

    pub fn as_str(self) -> &'static str {
        match self {
            Rarity::Common => "COMMON",
            Rarity::Uncommon => "UNCOMMON",
            Rarity::Rare => "RARE",
            Rarity::Epic => "EPIC",
            Rarity::Legendary => "LEGENDARY",
            Rarity::Mythic => "MYTHIC",
            Rarity::Divine => "DIVINE",
            Rarity::Special => "SPECIAL",
            Rarity::VerySpecial => "VERY_SPECIAL",
            Rarity::Ultimate => "ULTIMATE",
            Rarity::Admin => "ADMIN",
        }
    }

    pub fn parse(s: &str) -> Option<Rarity> {
        let n = s.trim().to_ascii_uppercase().replace(' ', "_");
        Rarity::ALL.iter().copied().find(|r| r.as_str() == n)
    }

    /// Next rarity for recombobulation.
    pub fn upgraded(self) -> Rarity {
        match self {
            Rarity::Common => Rarity::Uncommon,
            Rarity::Uncommon => Rarity::Rare,
            Rarity::Rare => Rarity::Epic,
            Rarity::Epic => Rarity::Legendary,
            Rarity::Legendary => Rarity::Mythic,
            Rarity::Mythic => Rarity::Divine,
            Rarity::Special => Rarity::VerySpecial,
            other => other,
        }
    }
}

/// Parse the rarity line at the bottom of SkyBlock lore, e.g. "§6§lLEGENDARY DUNGEON SWORD"
/// or a recombobulated "§d§l§ka§r §d§lMYTHIC ACCESSORY §d§l§ka".
pub fn rarity_from_lore(lore: &[String]) -> Option<(Rarity, Option<String>)> {
    for line in lore.iter().rev() {
        let clean = strip_formatting(line);
        let clean = clean.trim().trim_matches('a').trim();
        if clean.is_empty() {
            continue;
        }
        // longest names first so VERY SPECIAL wins over SPECIAL
        let candidates = [
            ("VERY SPECIAL", Rarity::VerySpecial),
            ("UNCOMMON", Rarity::Uncommon),
            ("COMMON", Rarity::Common),
            ("RARE", Rarity::Rare),
            ("EPIC", Rarity::Epic),
            ("LEGENDARY", Rarity::Legendary),
            ("MYTHIC", Rarity::Mythic),
            ("DIVINE", Rarity::Divine),
            ("SPECIAL", Rarity::Special),
            ("ULTIMATE", Rarity::Ultimate),
            ("ADMIN", Rarity::Admin),
        ];
        let upper = clean.to_ascii_uppercase();
        for (word, rarity) in candidates {
            if let Some(rest) = upper.strip_prefix(word) {
                if rest.is_empty() || rest.starts_with(' ') {
                    let category = rest.trim();
                    return Some((rarity, (!category.is_empty()).then(|| category.to_string())));
                }
            }
        }
        // Only the last non-empty line can carry the rarity.
        return None;
    }
    None
}

fn stat_regex() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"^([A-Za-z][A-Za-z' ]{1,40}?):\s*([+-]?[0-9][0-9,]*(?:\.[0-9]+)?)(%?)").unwrap())
}

/// Extract "Stat: +123" lines from lore (stops at the first blank line after stats begin,
/// mirroring how SkyBlock prints the stat block).
pub fn lore_stats(lore: &[String]) -> BTreeMap<String, f64> {
    let mut out = BTreeMap::new();
    let mut started = false;
    for line in lore {
        let clean = strip_formatting(line);
        let clean = clean.trim();
        if clean.is_empty() {
            if started {
                break;
            }
            continue;
        }
        if let Some(c) = stat_regex().captures(clean) {
            let name = c[1].trim().to_string();
            if name.eq_ignore_ascii_case("Gear Score") {
                continue;
            }
            let value: f64 = c[2].replace(',', "").parse().unwrap_or(0.0);
            if value.is_finite() {
                started = true;
                *out.entry(name).or_insert(0.0) += value;
            }
        } else if started {
            // non-stat line after stats (e.g. ability text) ends the block
            break;
        }
    }
    out
}

/// Flatten a Minecraft text component (JSON) into a legacy `§` formatted string.
pub fn component_to_legacy(v: &Value) -> String {
    let mut out = String::new();
    flatten(v, &mut out, 0);
    out
}

fn color_code(name: &str) -> Option<char> {
    Some(match name {
        "black" => '0',
        "dark_blue" => '1',
        "dark_green" => '2',
        "dark_aqua" => '3',
        "dark_red" => '4',
        "dark_purple" => '5',
        "gold" => '6',
        "gray" => '7',
        "dark_gray" => '8',
        "blue" => '9',
        "green" => 'a',
        "aqua" => 'b',
        "red" => 'c',
        "light_purple" => 'd',
        "yellow" => 'e',
        "white" => 'f',
        _ => return None,
    })
}

fn flatten(v: &Value, out: &mut String, depth: usize) {
    if depth > 32 {
        return;
    }
    match v {
        Value::String(s) => {
            // may itself be serialized JSON (1.20.4- lore) or plain text
            if (s.starts_with('{') || s.starts_with('[') || s.starts_with('"')) && s.len() > 1 {
                if let Ok(inner) = serde_json::from_str::<Value>(s) {
                    flatten(&inner, out, depth + 1);
                    return;
                }
            }
            out.push_str(s);
        }
        Value::Array(a) => {
            for x in a {
                flatten(x, out, depth + 1);
            }
        }
        Value::Object(o) => {
            if let Some(c) = o.get("color").and_then(Value::as_str).and_then(color_code) {
                out.push('§');
                out.push(c);
            }
            for (key, code) in [("bold", 'l'), ("italic", 'o'), ("underlined", 'n'), ("strikethrough", 'm'), ("obfuscated", 'k')] {
                if o.get(key).and_then(Value::as_bool) == Some(true) {
                    out.push('§');
                    out.push(code);
                }
            }
            if let Some(t) = o.get("text") {
                flatten(t, out, depth + 1);
            } else if let Some(t) = o.get("") {
                flatten(t, out, depth + 1);
            }
            if let Some(extra) = o.get("extra") {
                flatten(extra, out, depth + 1);
            }
        }
        _ => {}
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rarity_lines() {
        let lore = vec!["§7Damage: §c+260".to_string(), "".into(), "§6§lLEGENDARY DUNGEON SWORD".into()];
        assert_eq!(rarity_from_lore(&lore), Some((Rarity::Legendary, Some("DUNGEON SWORD".into()))));
        let recomb = vec!["§d§l§ka§r §d§lMYTHIC ACCESSORY §d§l§ka".to_string()];
        assert_eq!(rarity_from_lore(&recomb).map(|x| x.0), Some(Rarity::Mythic));
        assert_eq!(rarity_from_lore(&["§f§lVERY SPECIAL".to_string()]).map(|x| x.0), Some(Rarity::VerySpecial));
    }

    #[test]
    fn stats() {
        let lore = vec![
            "§7Gear Score: §d615".to_string(),
            "§7Damage: §c+260 §e(+30)".into(),
            "§7Strength: §c+150".into(),
            "§7Crit Chance: §c+10%".into(),
            "".into(),
            "§7Deals §c+50% §7damage".into(),
        ];
        let s = lore_stats(&lore);
        assert_eq!(s.get("Damage"), Some(&260.0));
        assert_eq!(s.get("Strength"), Some(&150.0));
        assert_eq!(s.get("Crit Chance"), Some(&10.0));
        assert!(!s.contains_key("Gear Score"));
    }

    #[test]
    fn component_flatten() {
        let v: Value = serde_json::json!({"text":"","extra":[{"text":"Hyperion","color":"gold","italic":false}]});
        assert_eq!(component_to_legacy(&v), "§6Hyperion");
    }
}
