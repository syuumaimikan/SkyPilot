//! Named places per island (NPCs, areas, warps, points of interest) from SkyHanni-REPO's
//! island graphs (MIT, https://github.com/hannibal002/SkyHanni-REPO). Used for guide
//! waypoints. Collectibles (fairy souls, Hoppity eggs, Enigma souls) and developer-only nodes
//! are left out on purpose: SkyPilot points at public places, it is not a finder.

use std::collections::{BTreeMap, HashMap};
use std::path::Path;

use serde_json::Value;

/// SkyHanni island graph files (`constants/island_graphs/<ISLAND>.json`).
pub const ISLANDS: &[&str] = &[
    "BACKWATER_BAYOU", "CRIMSON_ISLE", "CRYSTAL_HOLLOWS", "DEEP_CAVERNS", "DUNGEON_HUB", "DWARVEN_MINES", "GALATEA", "GLACITE_TUNNELS", "GOLD_MINES", "HUB",
    "LOTUS_ATOLL", "SAFARI", "SPIDER_DEN", "THE_END", "THE_FARMING_ISLANDS", "THE_PARK", "THE_RIFT", "TORRHUS_CANYON", "WINTER",
];

/// Tags of nodes that are never shown (collectibles and editor-only nodes).
pub const EXCLUDED_TAGS: &[&str] = &["fairy_soul", "hoppity", "rift_enigma"];

#[derive(Debug, Clone, PartialEq)]
pub struct Location {
    pub island: String,
    pub name: String,
    pub tags: Vec<String>,
    pub x: f64,
    pub y: f64,
    pub z: f64,
}

#[derive(Debug, Default)]
pub struct Locations {
    by_island: HashMap<String, Vec<Location>>,
}

/// "x:y:z" → (x, y, z)
fn position(s: &str) -> Option<(f64, f64, f64)> {
    let p: Vec<f64> = s.split(':').map(|v| v.trim().parse::<f64>()).collect::<Result<_, _>>().ok()?;
    (p.len() == 3 && p.iter().all(|v| v.is_finite())).then(|| (p[0], p[1], p[2]))
}

/// Keep only named, public nodes of a raw island graph (`{id: {Name, Tags, Position, …}}`).
pub fn named_nodes(graph: &Value) -> Vec<(String, Vec<String>, (f64, f64, f64))> {
    let Some(obj) = graph.as_object() else { return Vec::new() };
    obj.values()
        .filter_map(|n| {
            let name = n.get("Name")?.as_str()?.trim();
            if name.is_empty() || name == "no_area" {
                return None;
            }
            let tags: Vec<String> = n.get("Tags").and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect()).unwrap_or_default();
            if tags.iter().any(|t| EXCLUDED_TAGS.contains(&t.as_str())) || (!tags.is_empty() && tags.iter().all(|t| t == "dev")) {
                return None;
            }
            Some((name.to_string(), tags, position(n.get("Position")?.as_str()?)?))
        })
        .collect()
}

impl Locations {
    /// Loads `<dir>/<ISLAND>.json` files; a missing directory gives an empty set.
    pub fn load(dir: &Path) -> Locations {
        let mut by_island = HashMap::new();
        let Ok(entries) = std::fs::read_dir(dir) else { return Locations::default() };
        for e in entries.flatten() {
            let path = e.path();
            if path.extension().and_then(|x| x.to_str()) != Some("json") {
                continue;
            }
            let Some(island) = path.file_stem().and_then(|s| s.to_str()).map(str::to_string) else { continue };
            let Ok(bytes) = std::fs::read(&path) else { continue };
            let Ok(graph) = serde_json::from_slice::<Value>(&bytes) else {
                tracing::warn!(file = %path.display(), "unreadable island graph");
                continue;
            };
            by_island.insert(island.clone(), group(&island, named_nodes(&graph)));
        }
        Locations { by_island }
    }

    pub fn find(&self, island: &str, name: &str) -> Option<&Location> {
        self.by_island.get(island)?.iter().find(|l| l.name == name)
    }

    pub fn on(&self, island: &str) -> &[Location] {
        self.by_island.get(island).map(Vec::as_slice).unwrap_or(&[])
    }

    pub fn islands(&self) -> Vec<&str> {
        let mut v: Vec<&str> = self.by_island.keys().map(String::as_str).collect();
        v.sort();
        v
    }

    pub fn len(&self) -> usize {
        self.by_island.values().map(Vec::len).sum()
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }
}

/// Areas are many nodes with one name: keep the node closest to their centre.
fn group(island: &str, nodes: Vec<(String, Vec<String>, (f64, f64, f64))>) -> Vec<Location> {
    let mut by_name: BTreeMap<String, Vec<(Vec<String>, (f64, f64, f64))>> = BTreeMap::new();
    for (name, tags, pos) in nodes {
        by_name.entry(name).or_default().push((tags, pos));
    }
    by_name
        .into_iter()
        .map(|(name, list)| {
            let n = list.len() as f64;
            let c = list.iter().fold((0.0, 0.0, 0.0), |a, (_, p)| (a.0 + p.0 / n, a.1 + p.1 / n, a.2 + p.2 / n));
            let d = |p: &(f64, f64, f64)| (p.0 - c.0).powi(2) + (p.1 - c.1).powi(2) + (p.2 - c.2).powi(2);
            let pos = list.iter().map(|(_, p)| *p).min_by(|a, b| d(a).total_cmp(&d(b))).unwrap_or_default();
            let mut all_tags: Vec<String> = list.into_iter().flat_map(|(t, _)| t).collect();
            all_tags.sort();
            all_tags.dedup();
            Location { island: island.to_string(), name, tags: all_tags, x: pos.0, y: pos.1, z: pos.2 }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keeps_public_named_nodes_only() {
        let g: Value = serde_json::json!({
            "0": {"Name": "Rhys", "Tags": ["npc"], "Position": "-1.0:12.0:3.0"},
            "1": {"Name": "Fairy Soul", "Tags": ["fairy_soul"], "Position": "1:1:1"},
            "2": {"Position": "5:5:5"},
            "3": {"Name": "Porhtal Eye", "Tags": ["dev"], "Position": "1:1:1"},
            "4": {"Name": "Black Lagoon", "Tags": ["area"], "Position": "0:0:0"},
            "5": {"Name": "Black Lagoon", "Tags": ["area"], "Position": "10:0:0"},
            "6": {"Name": "Black Lagoon", "Tags": ["area"], "Position": "4:0:0"},
            "7": {"Name": "no_area", "Tags": ["area"], "Position": "4:0:0"}
        });
        let locs = group("X", named_nodes(&g));
        assert_eq!(locs.iter().map(|l| l.name.as_str()).collect::<Vec<_>>(), vec!["Black Lagoon", "Rhys"]);
        let lagoon = locs.iter().find(|l| l.name == "Black Lagoon").unwrap();
        assert_eq!(lagoon.x, 4.0, "area point closest to the centre");
        assert_eq!(position("1.5:2:-3"), Some((1.5, 2.0, -3.0)));
        assert_eq!(position("bad"), None);
    }
}
