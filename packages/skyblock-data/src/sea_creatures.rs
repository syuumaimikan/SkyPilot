//! Sea creatures from SkyHanni-REPO `constants/SeaCreatures.json` (MIT): the chat line Hypixel
//! shows when one is fished up, its rarity and fishing XP. The mod uses them to name the
//! creature and alert on rare ones; nothing about where creatures are is involved.

use std::path::Path;

use serde_json::Value;

#[derive(Debug, Clone, PartialEq)]
pub struct SeaCreature {
    pub name: String,
    /// Fishing area group in the source file (e.g. "WATER", "LAVA", "WINTER_ISLAND").
    pub area: String,
    /// The chat line, colour codes stripped (e.g. "Pitch darkness reveals a Night Squid.").
    pub message: String,
    pub rarity: String,
    pub fishing_xp: Option<f64>,
}

#[derive(Debug, Clone, Default)]
pub struct SeaCreatures {
    pub all: Vec<SeaCreature>,
}

fn strip_codes(s: &str) -> String {
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

impl SeaCreatures {
    pub fn parse(json: &Value) -> SeaCreatures {
        let mut all = Vec::new();
        if let Some(areas) = json.as_object() {
            for (area, v) in areas {
                let Some(list) = v.get("sea_creatures").and_then(Value::as_object) else { continue };
                for (name, c) in list {
                    let Some(message) = c.get("chat_message").and_then(Value::as_str) else { continue };
                    let message = strip_codes(message).trim().to_string();
                    if message.is_empty() {
                        continue;
                    }
                    all.push(SeaCreature {
                        name: name.clone(),
                        area: area.clone(),
                        message,
                        rarity: c.get("rarity").and_then(Value::as_str).unwrap_or("COMMON").to_string(),
                        fishing_xp: c.get("fishing_experience").and_then(Value::as_f64),
                    });
                }
            }
        }
        all.sort_by(|a, b| a.name.cmp(&b.name).then(a.area.cmp(&b.area)));
        SeaCreatures { all }
    }

    pub fn load(file: &Path) -> SeaCreatures {
        match std::fs::read(file).ok().and_then(|b| serde_json::from_slice::<Value>(&b).ok()) {
            Some(v) => SeaCreatures::parse(&v),
            None => SeaCreatures::default(),
        }
    }

    /// The creature announced by a chat line (exact match after stripping colours).
    pub fn by_message(&self, line: &str) -> Option<&SeaCreature> {
        let line = strip_codes(line);
        let line = line.trim();
        self.all.iter().find(|c| c.message == line)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_skyhanni_shape() {
        let v = serde_json::json!({
            "PARK": {"chat_color": "§2", "sea_creatures": {
                "Night Squid": {"chat_message": "§2Pitch darkness reveals a Night Squid.", "fishing_experience": 270, "rarity": "COMMON"}
            }},
            "WATER": {"chat_color": "§b", "sea_creatures": {
                "Sea Emperor": {"chat_message": "The Sea Emperor arises from the depths.", "fishing_experience": 2000, "rarity": "LEGENDARY", "rare": true}
            }}
        });
        let s = SeaCreatures::parse(&v);
        assert_eq!(s.all.len(), 2);
        let c = s.by_message("Pitch darkness reveals a Night Squid.").unwrap();
        assert_eq!((c.name.as_str(), c.area.as_str(), c.rarity.as_str()), ("Night Squid", "PARK", "COMMON"));
        assert_eq!(s.by_message("§bThe Sea Emperor arises from the depths.").unwrap().rarity, "LEGENDARY");
        assert!(s.by_message("Steve: Pitch darkness reveals a Night Squid.").is_none());
    }
}
