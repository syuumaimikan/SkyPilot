//! Parametric farm designs (`data/farms/*.json`).
//!
//! A design is a repeating cross-section (rows = Y from top to bottom, columns = Z within
//! one period) extruded along X. Levels stack cross-sections vertically. This keeps the
//! JSON small while producing the full block list for the web Farm Builder.

use std::collections::BTreeMap;
use std::path::Path;

use anyhow::Context;
use serde::Deserialize;

#[derive(Debug, Clone, Deserialize)]
pub struct FarmLevel {
    /// Y offset of the bottom row of this level.
    pub y: i32,
    /// Rows from top to bottom; each char is a palette code, one char per Z within a period.
    pub cross_section: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FarmDesignDef {
    pub id: String,
    pub name: String,
    pub crops: Vec<String>,
    pub description: String,
    /// Plot footprint [x, z] (a Garden plot is 96 x 96).
    pub plot: [u32; 2],
    /// Blocks along X.
    pub length: u32,
    /// Number of times the period repeats along Z.
    pub repeat: u32,
    pub palette: BTreeMap<String, String>,
    /// Codes that are harvested.
    pub crop_codes: Vec<String>,
    pub levels: Vec<FarmLevel>,
    pub movement: Vec<String>,
    pub harvest_direction: String,
    #[serde(default)]
    pub water_notes: Vec<String>,
    #[serde(default)]
    pub lighting_notes: Vec<String>,
    /// Seconds to place one block when building by hand (for time estimates).
    #[serde(default = "default_place_seconds")]
    pub seconds_per_block: f64,
    #[serde(default)]
    pub build_order: Vec<String>,
    /// Codes placed only where (x + z) is even, e.g. cactus, which must not touch another
    /// block horizontally.
    #[serde(default)]
    pub checker: Vec<String>,
}

fn default_place_seconds() -> f64 {
    0.35
}

#[derive(Debug, Clone, PartialEq)]
pub struct PlacedBlock {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub block: String,
    pub code: char,
}

impl FarmDesignDef {
    pub fn period(&self) -> usize {
        self.levels.iter().flat_map(|l| l.cross_section.iter()).map(|r| r.chars().count()).max().unwrap_or(0)
    }

    /// Expand to individual blocks (air '.' omitted). `max_blocks` bounds memory.
    pub fn expand(&self, max_blocks: usize) -> Vec<PlacedBlock> {
        let mut out = Vec::new();
        let period = self.period().max(1);
        for level in &self.levels {
            let rows = level.cross_section.len() as i32;
            for (ri, row) in level.cross_section.iter().enumerate() {
                let y = level.y + (rows - 1 - ri as i32);
                for (zi, code) in row.chars().enumerate() {
                    if code == '.' || code == ' ' {
                        continue;
                    }
                    let block = self.palette.get(&code.to_string()).cloned().unwrap_or_else(|| format!("unknown:{code}"));
                    let checker = self.checker.iter().any(|c| c.starts_with(code));
                    for rep in 0..self.repeat {
                        let z = (rep as usize * period + zi) as i32;
                        if z as u32 >= self.plot[1] {
                            continue;
                        }
                        for x in 0..self.length.min(self.plot[0]) as i32 {
                            if checker && (x + z) % 2 != 0 {
                                continue;
                            }
                            out.push(PlacedBlock { x, y, z, block: block.clone(), code });
                            if out.len() >= max_blocks {
                                return out;
                            }
                        }
                    }
                }
            }
        }
        out
    }

    pub fn block_counts(&self) -> BTreeMap<String, u32> {
        let mut counts = BTreeMap::new();
        for b in self.expand(2_000_000) {
            *counts.entry(b.block).or_insert(0) += 1;
        }
        counts
    }

    /// Number of harvestable crop blocks.
    pub fn crop_blocks(&self) -> u32 {
        self.expand(2_000_000).iter().filter(|b| self.crop_codes.iter().any(|c| c.starts_with(b.code))).count() as u32
    }

    pub fn size(&self) -> [u32; 3] {
        let blocks = self.expand(2_000_000);
        let max_y = blocks.iter().map(|b| b.y).max().unwrap_or(0);
        let min_y = blocks.iter().map(|b| b.y).min().unwrap_or(0);
        let max_z = blocks.iter().map(|b| b.z).max().unwrap_or(0);
        [self.length.min(self.plot[0]), (max_y - min_y + 1) as u32, (max_z + 1) as u32]
    }
}

pub fn load_dir(dir: &Path) -> anyhow::Result<Vec<FarmDesignDef>> {
    let mut out = Vec::new();
    if !dir.exists() {
        return Ok(out);
    }
    for entry in std::fs::read_dir(dir)? {
        let path = entry?.path();
        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }
        let text = std::fs::read_to_string(&path)?;
        let d: FarmDesignDef = serde_json::from_str(&text).with_context(|| format!("parsing {}", path.display()))?;
        out.push(d);
    }
    out.sort_by(|a, b| a.id.cmp(&b.id));
    Ok(out)
}
