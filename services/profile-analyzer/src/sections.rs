//! One function per profile area. Each returns `Known::Unknown` with a reason instead of
//! guessing when the API does not expose the data.

use std::collections::BTreeMap;

use serde_json::Value;
use skypilot_calc::leveling::XpTable;
use skypilot_data::Registry;
use skypilot_protocol::common::Known;
use skypilot_protocol::profile::*;

use crate::gear;
use crate::inventory::{Inventories, pet_summary, summarize};

pub fn f(v: &Value, path: &[&str]) -> Option<f64> {
    path.iter().try_fold(v, |cur, k| cur.get(k))?.as_f64().filter(|x| x.is_finite())
}
pub fn u(v: &Value, path: &[&str]) -> Option<u64> {
    path.iter().try_fold(v, |cur, k| cur.get(k)).and_then(|x| x.as_u64().or_else(|| x.as_f64().filter(|f| *f >= 0.0).map(|f| f as u64)))
}
pub fn get<'a>(v: &'a Value, path: &[&str]) -> Option<&'a Value> {
    path.iter().try_fold(v, |cur, k| cur.get(k))
}

pub fn level_info(id: &str, name: &str, xp: f64, table: &XpTable) -> LevelInfo {
    let p = table.progress(xp);
    LevelInfo {
        id: id.to_string(),
        name: name.to_string(),
        level: p.level,
        max_level: p.max_level,
        xp: if xp.is_finite() { xp.max(0.0) } else { 0.0 },
        progress: p.progress,
        xp_to_next: p.xp_to_next,
        level_with_progress: p.level_with_progress,
    }
}

/// Skills that count toward Skill Average (Hypixel's in-game definition excludes
/// Runecrafting and Social; newer skills are shown but not averaged).
pub const AVERAGED_SKILLS: [&str; 9] = ["FARMING", "MINING", "COMBAT", "FORAGING", "FISHING", "ENCHANTING", "ALCHEMY", "TAMING", "CARPENTRY"];

pub fn skills(member: &Value, registry: &Registry) -> (Known<Vec<LevelInfo>>, Known<f64>) {
    let Some(exp) = get(member, &["player_data", "experience"]).and_then(Value::as_object) else {
        return (Known::unknown("Skills API disabled"), Known::unknown("Skills API disabled"));
    };
    let farming_cap = 50 + u(member, &["jacobs_contest", "perks", "farming_level_cap"]).unwrap_or(0).min(10) as u32;
    let taming_cap = 50 + get(member, &["pets_data", "pet_care", "pet_types_sacrificed"]).and_then(Value::as_array).map(|a| a.len()).unwrap_or(0).min(10) as u32;
    let mut out = Vec::new();
    for (id, def) in &registry.skills {
        let xp = exp.get(&format!("SKILL_{id}")).and_then(Value::as_f64).unwrap_or(0.0);
        let cap = match id.as_str() {
            "FARMING" => farming_cap,
            "TAMING" => taming_cap,
            _ => def.max_level,
        };
        out.push(level_info(id, &def.name, xp, &def.table.capped(cap)));
    }
    out.sort_by(|a, b| a.name.cmp(&b.name));
    let averaged: Vec<f64> = out.iter().filter(|s| AVERAGED_SKILLS.contains(&s.id.as_str())).map(|s| s.level as f64).collect();
    let avg = if averaged.is_empty() { Known::unknown("No skill data") } else { Known::known(averaged.iter().sum::<f64>() / averaged.len() as f64) };
    (Known::known(out), avg)
}

pub fn dungeons(member: &Value, registry: &Registry) -> Known<DungeonInfo> {
    let Some(d) = member.get("dungeons") else { return Known::unknown("No dungeon data on this profile") };
    let table = registry.neu.leveling.catacombs_table();
    let cata_xp = f(d, &["dungeon_types", "catacombs", "experience"]).unwrap_or(0.0);
    let catacombs = level_info("catacombs", "Catacombs", cata_xp, &table);
    let mut classes = Vec::new();
    for c in ["healer", "mage", "berserk", "archer", "tank"] {
        let xp = f(d, &["player_classes", c, "experience"]).unwrap_or(0.0);
        classes.push(level_info(c, &skypilot_items::fingerprint::title_case(c), xp, &table));
    }
    let class_average = classes.iter().map(|c| c.level as f64).sum::<f64>() / classes.len() as f64;
    let mut floors = Vec::new();
    for (master, key) in [(false, "catacombs"), (true, "master_catacombs")] {
        let t = get(d, &["dungeon_types", key]);
        for tier in 0u8..=7 {
            if master && tier == 0 {
                continue;
            }
            let ts = tier.to_string();
            let completions = t.and_then(|t| u(t, &["tier_completions", &ts])).unwrap_or(0);
            floors.push(FloorStats {
                floor: if tier == 0 { "E".into() } else if master { format!("M{tier}") } else { format!("F{tier}") },
                tier,
                master,
                completions,
                best_score: t.and_then(|t| u(t, &["best_score", &ts])).map(|v| v as u32),
                fastest_s_plus_ms: t.and_then(|t| u(t, &["fastest_time_s_plus", &ts])),
            });
        }
    }
    let highest = |master: bool| floors.iter().filter(|f| f.master == master && f.completions > 0).map(|f| f.tier).max();
    Known::known(DungeonInfo {
        catacombs,
        class_average,
        selected_class: d.get("selected_dungeon_class").and_then(Value::as_str).map(str::to_string),
        secrets: u(d, &["secrets"]),
        highest_floor_completed: highest(false),
        highest_master_floor_completed: highest(true),
        classes,
        floors,
    })
}

pub fn slayers(member: &Value, registry: &Registry) -> Known<Vec<SlayerInfo>> {
    let Some(bosses) = get(member, &["slayer", "slayer_bosses"]) else { return Known::known(Vec::new()) };
    let mut out = Vec::new();
    for def in &registry.curated.slayers {
        let b = bosses.get(&def.id);
        let xp = b.and_then(|b| f(b, &["xp"])).unwrap_or(0.0);
        let table = registry.neu.leveling.slayer_table(&def.id).unwrap_or_else(|| XpTable::from_cumulative(vec![]));
        let kills: Vec<u64> = (0..def.max_tier).map(|t| b.and_then(|b| u(b, &[&format!("boss_kills_tier_{t}")])).unwrap_or(0)).collect();
        let highest = kills.iter().enumerate().filter(|(_, k)| **k > 0).map(|(i, _)| i as u8 + 1).max();
        out.push(SlayerInfo {
            id: def.id.clone(),
            boss_name: def.name.clone(),
            level: level_info(&def.id, &format!("{} Slayer", def.short), xp, &table),
            kills_by_tier: kills,
            highest_tier_killed: highest,
            max_tier: def.max_tier,
        });
    }
    Known::known(out)
}

fn tree_nodes(v: Option<&Value>) -> Vec<TreeNodeLevel> {
    let Some(obj) = v.and_then(Value::as_object) else { return Vec::new() };
    let mut out: Vec<TreeNodeLevel> = obj
        .iter()
        .filter(|(k, _)| !k.starts_with("toggle_") && !k.starts_with("special_"))
        .filter_map(|(k, v)| {
            let level = v.as_u64()? as u32;
            let enabled = obj.get(&format!("toggle_{k}")).and_then(Value::as_bool).unwrap_or(true);
            Some(TreeNodeLevel { id: k.clone(), level, enabled })
        })
        .collect();
    out.sort_by(|a, b| a.id.cmp(&b.id));
    out
}

pub fn mining(member: &Value, registry: &Registry, inv: &Inventories) -> Known<MiningInfo> {
    let core = member.get("mining_core");
    let tree = member.get("skill_tree");
    if core.is_none() && tree.is_none() {
        return Known::unknown("Player has not unlocked the Heart of the Mountain");
    }
    // Current API (2025+): HOTM lives in `skill_tree` (experience.mining, nodes.mining,
    // tokens_spent.mountain) and powder in `mining_core`; the API omits zero values, so with
    // `skill_tree` present a missing field means 0. HOTM counts as unlocked once there is
    // mining XP, a mining node or a `mining_core` object.
    let tree_xp = tree.and_then(|t| f(t, &["experience", "mining"]));
    let tree_nodes_any = tree.and_then(|t| get(t, &["nodes", "mining"])).and_then(Value::as_object).is_some_and(|m| !m.is_empty());
    let unlocked = core.is_some() || tree_xp.is_some() || tree_nodes_any;
    let hotm_table = registry.neu.leveling.hotm_table();
    let hotm_xp = tree_xp.or_else(|| core.and_then(|c| f(c, &["experience"]))).or_else(|| tree.map(|_| 0.0));
    let hotm = match hotm_xp {
        _ if tree.is_some() && !unlocked => Known::known(LevelInfo {
            id: "hotm".into(),
            name: "Heart of the Mountain".into(),
            level: 0,
            max_level: hotm_table.max_level(),
            xp: 0.0,
            progress: 0.0,
            xp_to_next: Some(0.0),
            level_with_progress: 0.0,
        }),
        Some(xp) => Known::known(level_info("hotm", "Heart of the Mountain", xp, &hotm_table)),
        None => Known::unknown("HOTM experience not available from API"),
    };
    let powder = |p: &str| -> Known<PowderBalance> {
        let avail = core.and_then(|c| f(c, &[&format!("powder_{p}")]));
        if avail.is_none() && tree.is_none() {
            return Known::unknown("Not available from API");
        }
        let avail = avail.unwrap_or(0.0);
        let spent = core.and_then(|c| f(c, &[&format!("powder_spent_{p}")])).unwrap_or(0.0);
        Known::known(PowderBalance { available: avail, spent, total: avail + spent })
    };
    let nodes_v = tree.and_then(|t| get(t, &["nodes", "mining"])).or_else(|| core.and_then(|c| c.get("nodes")));
    let nodes = match nodes_v {
        Some(v) => Known::known(tree_nodes(Some(v))),
        None => Known::unknown("HOTM tree not available from API"),
    };
    let crystals_found = core
        .and_then(|c| c.get("crystals"))
        .and_then(Value::as_object)
        .map(|m| {
            m.iter()
                .filter(|(_, v)| v.get("total_found").and_then(Value::as_u64).unwrap_or(0) > 0 || v.get("state").and_then(Value::as_str) == Some("FOUND"))
                .map(|(k, _)| skypilot_items::fingerprint::title_case(k))
                .collect()
        })
        .unwrap_or_default();
    let glacite = member.get("glacite_player_data");
    let (mut stats, _) = gear::best_setup(inv, registry, &[("Mining Speed", 1.0), ("Mining Fortune", 3.0), ("Gemstone Fortune", 2.0), ("Pristine", 50.0)]);
    let tool = gear::best_tool(inv, registry, &["DRILL", "PICKAXE", "GAUNTLET"], &[("Mining Speed", 1.0), ("Mining Fortune", 3.0), ("Gemstone Fortune", 2.0), ("Pristine", 100.0)]);
    gear::add_tool_stats(&mut stats, tool);
    Known::known(MiningInfo {
        hotm,
        mithril_powder: powder("mithril"),
        gemstone_powder: powder("gemstone"),
        glacite_powder: powder("glacite"),
        tokens_available: core
            .and_then(|c| u(c, &["tokens"]))
            .or_else(|| (tree.is_some() && !unlocked).then_some(0))
            .map(|v| Known::known(v as u32))
            .unwrap_or_else(|| Known::unknown("Not available from API")),
        tokens_spent: tree
            .and_then(|t| u(t, &["tokens_spent", "mountain"]))
            .or_else(|| core.and_then(|c| u(c, &["tokens_spent"])))
            .or_else(|| tree.map(|_| 0))
            .map(|v| Known::known(v as u32))
            .unwrap_or_else(|| Known::unknown("Not available from API")),
        nodes,
        selected_ability: tree
            .and_then(|t| get(t, &["selected_ability", "mining"]))
            .or_else(|| core.and_then(|c| c.get("selected_pickaxe_ability")))
            .and_then(Value::as_str)
            .map(str::to_string),
        crystals_found,
        mineshafts_entered: glacite.and_then(|g| u(g, &["mineshafts_entered"])),
        fossil_dust: glacite.and_then(|g| u(g, &["fossil_dust"])),
        fossils_donated: glacite.and_then(|g| g.get("fossils_donated")).and_then(Value::as_array).map(|a| a.len() as u32).unwrap_or(0),
        gear_stats: stats,
        best_tool: tool.map(|t| summarize(t, registry)),
    })
}

/// Garden resource id -> NEU crop milestone key.
pub fn milestone_key(resource: &str) -> Option<&'static str> {
    Some(match resource {
        "WHEAT" => "WHEAT",
        "CARROT_ITEM" => "CARROT",
        "POTATO_ITEM" => "POTATO",
        "MELON" => "MELON",
        "PUMPKIN" => "PUMPKIN",
        "SUGAR_CANE" => "SUGAR_CANE",
        "INK_SACK:3" => "COCOA_BEANS",
        "CACTUS" => "CACTUS",
        "MUSHROOM_COLLECTION" => "MUSHROOM",
        "NETHER_STALK" => "NETHER_WART",
        "MOONFLOWER" => "MOONFLOWER",
        "DOUBLE_PLANT" => "SUNFLOWER",
        "WILD_ROSE" => "WILD_ROSE",
        _ => return None,
    })
}

pub fn garden(member: &Value, garden: Option<&Value>, registry: &Registry, inv: &Inventories) -> Known<GardenInfo> {
    let g = garden.and_then(|g| g.get("garden").or(Some(g)));
    let Some(g) = g.filter(|g| g.get("garden_experience").is_some() || g.get("unlocked_plots_ids").is_some()) else {
        return Known::unknown("Garden not unlocked or Garden API unavailable");
    };
    let neu = &registry.neu.garden;
    let garden_level = match f(g, &["garden_experience"]) {
        Some(xp) => {
            // NEU garden_exp: increments with level 1 at 0
            Known::known(level_info("garden", "Garden", xp, &XpTable::from_increments(&neu.garden_exp_increments)))
        }
        None => Known::unknown("Not available from API"),
    };
    let mut crop_milestones = BTreeMap::new();
    if let Some(res) = g.get("resources_collected").and_then(Value::as_object) {
        for (k, v) in res {
            if let (Some(key), Some(amount)) = (milestone_key(k), v.as_f64()) {
                if let Some(incs) = neu.crop_milestones.get(key) {
                    crop_milestones.insert(k.clone(), XpTable::from_increments(incs).progress(amount).level);
                }
            }
        }
    }
    let crop_upgrades = g
        .get("crop_upgrade_levels")
        .and_then(Value::as_object)
        .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|n| (k.clone(), n as u32))).collect())
        .unwrap_or_default();
    let composter_upgrades = get(g, &["composter_data", "upgrades"])
        .and_then(Value::as_object)
        .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|n| (k.clone(), n as u32))).collect())
        .unwrap_or_default();
    let (mut stats, _) = gear::best_setup(inv, registry, &[("Farming Fortune", 1.0)]);
    let tool = gear::best_tool(inv, registry, &["FARMING_TOOL", "HOE", "AXE"], &[("Farming Fortune", 1.0)]);
    gear::add_tool_stats(&mut stats, tool);
    let tools: Vec<ItemSummary> = registry
        .curated
        .farming
        .crops
        .iter()
        .flat_map(|c| c.tools.iter())
        .filter_map(|tid| inv.all_items().find(|o| o.item.skyblock_id.as_deref() == Some(tid.as_str())))
        .map(|o| summarize(o, registry))
        .collect();
    let jc = member.get("jacobs_contest");
    Known::known(GardenInfo {
        garden_level,
        crop_milestones,
        crop_upgrades,
        unlocked_plots: g.get("unlocked_plots_ids").and_then(Value::as_array).map(|a| a.len() as u32).unwrap_or(0),
        visitors_served: u(g, &["commission_data", "total_completed"]).map(|v| v as u32),
        unique_visitors: u(g, &["commission_data", "unique_npcs_served"]).map(|v| v as u32),
        copper: u(member, &["garden_player_data", "copper"]),
        composter_upgrades,
        farming_gear_stats: stats,
        best_tools: tools,
        jacob_medals: jc
            .and_then(|j| j.get("medals_inv"))
            .and_then(Value::as_object)
            .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|n| (k.clone(), n as u32))).collect())
            .unwrap_or_default(),
        personal_bests: jc
            .and_then(|j| j.get("personal_bests"))
            .and_then(Value::as_object)
            .map(|m| m.iter().filter_map(|(k, v)| v.as_f64().map(|n| (k.clone(), n))).collect())
            .unwrap_or_default(),
    })
}

/// MP rules from the curated registry; inactive = ignored / duplicates / superseded family tiers.
pub fn accessories(member: &Value, registry: &Registry, inv: &Inventories) -> Known<AccessoryInfo> {
    let storage = member.get("accessory_bag_storage");
    if !inv.available {
        return Known::unknown("Inventory API disabled - accessory bag not visible");
    }
    let cfg = &registry.curated.accessories;
    let owned_ids: Vec<String> = inv.accessories.iter().filter_map(|o| o.item.skyblock_id.clone()).collect();
    let mut seen = std::collections::HashSet::new();
    let mut inactive = Vec::new();
    let mut mp = 0u32;
    let contacts = get(member, &["nether_island_player_data", "abiphone", "active_contacts"]).and_then(Value::as_array).map(|a| a.len()).unwrap_or(0);
    for o in &inv.accessories {
        let Some(id) = o.item.skyblock_id.clone() else { continue };
        let ignored = registry.neu.ignored_talismans.contains(&id) || cfg.extra_ignored.contains(&id);
        let superseded = registry.neu.accessory_upgrades(&id).iter().any(|higher| owned_ids.contains(higher));
        if ignored || superseded || !seen.insert(id.clone()) {
            inactive.push(id);
            continue;
        }
        let rarity = o.item.rarity.map(|r| r.as_str().to_string()).or_else(|| registry.item(&id).and_then(|i| i.tier.clone())).unwrap_or_default();
        let base = cfg.mp_per_rarity.get(&rarity).copied().unwrap_or(0);
        let mult = cfg.mp_multiplier.get(&id).copied().unwrap_or(1);
        mp += base * mult;
        if id.starts_with("ABICASE") {
            mp += (contacts / 2) as u32;
        }
    }
    let tuning = storage
        .and_then(|s| get(s, &["tuning", "slot_0"]))
        .and_then(Value::as_object)
        .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().filter(|n| *n > 0).map(|n| (k.clone(), n as u32))).collect())
        .unwrap_or_default();
    Known::known(AccessoryInfo {
        accessories: inv.accessories.iter().map(|o| summarize(o, registry)).collect(),
        magical_power: mp,
        highest_magical_power_api: storage.and_then(|s| u(s, &["highest_magical_power"])).map(|v| v as u32),
        selected_power: storage.and_then(|s| s.get("selected_power")).and_then(Value::as_str).map(str::to_string),
        unlocked_powers: storage
            .and_then(|s| s.get("unlocked_powers"))
            .and_then(Value::as_array)
            .map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect())
            .unwrap_or_default(),
        tuning,
        bag_upgrades_purchased: storage.and_then(|s| u(s, &["bag_upgrades_purchased"])).map(|v| v as u32),
        inactive,
    })
}

pub fn pets(member: &Value, registry: &Registry) -> Known<Vec<PetSummary>> {
    let Some(list) = get(member, &["pets_data", "pets"]).and_then(Value::as_array) else { return Known::unknown("No pet data") };
    let mut out: Vec<PetSummary> = list
        .iter()
        .filter_map(|p| serde_json::from_value::<skypilot_items::PetInfo>(p.clone()).ok())
        .map(|p| pet_summary(&p, registry))
        .collect();
    out.sort_by(|a, b| b.active.cmp(&a.active).then(b.level.cmp(&a.level)));
    Known::known(out)
}

pub fn collections(member: &Value, registry: &Registry) -> Known<Vec<CollectionEntry>> {
    let Some(c) = member.get("collection").and_then(Value::as_object) else { return Known::unknown("Collections API disabled") };
    let mut out = Vec::new();
    for def in &registry.collections {
        let amount = c.get(&def.id).and_then(Value::as_u64).unwrap_or(0);
        let tier = def.tiers.iter().filter(|t| amount >= **t).count() as u32;
        out.push(CollectionEntry {
            id: def.id.clone(),
            name: def.name.clone(),
            category: def.category.clone(),
            amount,
            tier,
            max_tier: def.max_tier,
            next_tier_amount: def.tiers.get(tier as usize).copied(),
        });
    }
    Known::known(out)
}

pub fn kuudra(member: &Value) -> Known<KuudraInfo> {
    let Some(n) = member.get("nether_island_player_data") else { return Known::unknown("Crimson Isle not visited") };
    let order = ["none", "hot", "burning", "fiery", "infernal"];
    let completions: BTreeMap<String, u64> = n
        .get("kuudra_completed_tiers")
        .and_then(Value::as_object)
        .map(|m| m.iter().filter(|(k, _)| order.contains(&k.as_str())).filter_map(|(k, v)| v.as_u64().map(|x| (k.clone(), x))).collect())
        .unwrap_or_default();
    let highest = order.iter().rev().find(|t| completions.get(**t).copied().unwrap_or(0) > 0).map(|t| t.to_string());
    Known::known(KuudraInfo {
        highest_tier_completed: highest,
        completions,
        selected_faction: n.get("selected_faction").and_then(Value::as_str).map(str::to_string),
        mage_reputation: n.get("mages_reputation").and_then(Value::as_i64),
        barbarian_reputation: n.get("barbarians_reputation").and_then(Value::as_i64),
        dojo_points: n
            .get("dojo")
            .and_then(Value::as_object)
            .map(|m| m.iter().filter(|(k, _)| k.starts_with("dojo_points_")).filter_map(|(k, v)| v.as_i64().map(|x| (k.trim_start_matches("dojo_points_").to_string(), x))).collect())
            .unwrap_or_default(),
        minibosses_killed: get(n, &["quests", "miniboss_data"]).and_then(Value::as_object).map(|m| m.keys().cloned().collect()).unwrap_or_default(),
        attribute_stacks: get(member, &["attributes", "stacks"])
            .and_then(Value::as_object)
            .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|x| (k.clone(), x as u32))).collect())
            .unwrap_or_default(),
    })
}

pub fn rift(member: &Value, slayers: &Known<Vec<SlayerInfo>>) -> Known<RiftInfo> {
    let Some(r) = member.get("rift") else { return Known::unknown("Rift not visited") };
    let mut completed = Vec::new();
    let checks: [(&str, &[&str]); 8] = [
        ("Wizard Tower", &["wizard_tower", "wizard_quest_step"]),
        ("Black Lagoon", &["black_lagoon", "completed_step"]),
        ("Village Plaza (murder)", &["village_plaza", "murder", "step_index"]),
        ("West Village glyphs", &["west_village", "glyphs", "current_glyph"]),
        ("Wyld Woods", &["wyld_woods", "bughunter_step"]),
        ("Castle", &["castle", "fairy_step"]),
        ("Dreadfarm", &["dreadfarm", "shania_stage"]),
        ("Mirrorverse", &["west_village", "mirrorverse", "claimed_reward"]),
    ];
    for (name, path) in checks {
        let v = get(r, path);
        let done = v.map(|v| v.as_bool().unwrap_or(false) || v.as_u64().unwrap_or(0) > 0).unwrap_or(false);
        if done {
            completed.push(name.to_string());
        }
    }
    Known::known(RiftInfo {
        motes_purse: f(member, &["currencies", "motes_purse"]),
        enigma_souls: get(r, &["enigma", "found_souls"]).and_then(Value::as_array).map(|a| a.len() as u32).unwrap_or(0),
        timecharms: get(r, &["gallery", "secured_trophies"])
            .and_then(Value::as_array)
            .map(|a| a.iter().filter_map(|t| t.get("type").and_then(Value::as_str)).map(skypilot_items::fingerprint::title_case).collect())
            .unwrap_or_default(),
        vampire_slayer_level: slayers.as_ref().and_then(|s| s.iter().find(|x| x.id == "vampire")).map(|v| v.level.level),
        completed_areas: completed,
        dead_cats_found: get(r, &["dead_cats", "found_cats"]).and_then(Value::as_array).map(|a| a.len() as u32).unwrap_or(0),
        montezuma_unlocked: get(r, &["dead_cats", "unlocked_pet"]).and_then(Value::as_bool).unwrap_or(false),
    })
}

pub fn chocolate(member: &Value) -> Known<ChocolateFactoryInfo> {
    let Some(e) = get(member, &["events", "easter"]) else { return Known::unknown("Chocolate Factory not started") };
    let rabbits = e.get("rabbits").and_then(Value::as_object).map(|m| m.keys().filter(|k| !k.starts_with("collected_")).count() as u32).unwrap_or(0);
    Known::known(ChocolateFactoryInfo {
        chocolate: u(e, &["chocolate"]).unwrap_or(0),
        total_chocolate: u(e, &["total_chocolate"]).unwrap_or(0),
        chocolate_since_prestige: u(e, &["chocolate_since_prestige"]).unwrap_or(0),
        prestige_level: u(e, &["chocolate_level"]).unwrap_or(1) as u32,
        employees: e
            .get("employees")
            .and_then(Value::as_object)
            .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|x| (k.clone(), x as u32))).collect())
            .unwrap_or_default(),
        unique_rabbits: rabbits,
        time_tower_level: u(e, &["time_tower", "level"]).unwrap_or(0) as u32,
    })
}

pub fn fishing(member: &Value, registry: &Registry, inv: &Inventories) -> Known<FishingInfo> {
    let (mut stats, _) = gear::best_setup(inv, registry, &[("Sea Creature Chance", 10.0), ("Fishing Speed", 1.0)]);
    let rod = gear::best_tool(inv, registry, &["FISHING_ROD", "FISHING_WEAPON"], &[("Sea Creature Chance", 10.0), ("Fishing Speed", 1.0), ("Damage", 0.01)]);
    gear::add_tool_stats(&mut stats, rod);
    let trophy: BTreeMap<String, u64> = member
        .get("trophy_fish")
        .and_then(Value::as_object)
        .map(|m| {
            m.iter()
                .filter(|(k, _)| !k.ends_with("_bronze") && !k.ends_with("_silver") && !k.ends_with("_gold") && !k.ends_with("_diamond") && *k != "total_caught" && *k != "rewards" && *k != "last_caught")
                .filter_map(|(k, v)| v.as_u64().map(|x| (k.clone(), x)))
                .collect()
        })
        .unwrap_or_default();
    Known::known(FishingInfo {
        treasures_caught: u(member, &["player_data", "fishing_treasure_caught"]),
        trophy_fish_caught: u(member, &["trophy_fish", "total_caught"]),
        trophy_fish: trophy,
        gear_stats: stats,
        best_rod: rod.map(|r| summarize(r, registry)),
    })
}

pub fn foraging(member: &Value, registry: &Registry, inv: &Inventories) -> Known<ForagingInfo> {
    let tree = member.get("skill_tree");
    let core = member.get("foraging_core");
    let hotf = match tree.and_then(|t| f(t, &["experience", "foraging"])) {
        Some(xp) => Known::known(level_info("hotf", "Heart of the Forest", xp, &registry.neu.leveling.hotf_table())),
        None => Known::unknown("Heart of the Forest not unlocked / not in API"),
    };
    let whispers = core
        .and_then(|c| f(c, &["forests_whispers"]).or_else(|| f(c, &["whispers", "forest", "total"])))
        .map(Known::known)
        .unwrap_or_else(|| Known::unknown("Not available from API"));
    let nodes = match tree.and_then(|t| get(t, &["nodes", "foraging"])) {
        Some(v) => Known::known(tree_nodes(Some(v))),
        None => Known::unknown("Not available from API"),
    };
    let (mut stats, _) = gear::best_setup(inv, registry, &[("Foraging Fortune", 1.0), ("Sweep", 5.0)]);
    let axe = gear::best_tool(inv, registry, &["AXE"], &[("Foraging Fortune", 1.0), ("Sweep", 5.0)]);
    gear::add_tool_stats(&mut stats, axe);
    let agatha = get(member, &["player_data", "perks"])
        .and_then(Value::as_object)
        .map(|m| m.iter().filter(|(k, _)| k.starts_with("agatha_") || *k == "lumberjack" || *k == "forest_training").filter_map(|(k, v)| v.as_u64().map(|x| (k.clone(), x as u32))).collect())
        .unwrap_or_default();
    Known::known(ForagingInfo {
        hotf,
        forest_whispers: whispers,
        nodes,
        tree_gifts: get(member, &["foraging", "tree_gifts"])
            .and_then(Value::as_object)
            .map(|m| m.iter().filter_map(|(k, v)| v.as_u64().map(|x| (k.clone(), x))).collect())
            .unwrap_or_default(),
        agatha_perks: agatha,
        gear_stats: stats,
        best_axe: axe.map(|a| summarize(a, registry)),
        shards_owned: get(member, &["shards", "owned"]).and_then(Value::as_array).map(|a| a.len() as u32).unwrap_or(0),
    })
}

pub fn minions(member: &Value, profile: &Value) -> Known<MinionInfo> {
    let Some(list) = get(member, &["player_data", "crafted_generators"]).and_then(Value::as_array) else {
        return Known::unknown("No minion data");
    };
    let mut crafted: BTreeMap<String, u32> = BTreeMap::new();
    for g in list.iter().filter_map(Value::as_str) {
        if let Some((base, tier)) = g.rsplit_once('_') {
            if let Ok(t) = tier.parse::<u32>() {
                let e = crafted.entry(base.to_string()).or_insert(0);
                *e = (*e).max(t);
            }
        }
    }
    let slots = get(profile, &["community_upgrades", "upgrade_states"])
        .and_then(Value::as_array)
        .map(|a| a.iter().filter(|u| u.get("upgrade").and_then(Value::as_str) == Some("minion_slots")).count() as u32)
        .unwrap_or(0);
    Known::known(MinionInfo { unique_crafted: list.len() as u32, crafted_tiers: crafted, slots_from_community_upgrades: slots })
}

pub fn museum(museum: Option<&Value>, member_uuid: &str) -> Known<MuseumInfo> {
    let Some(m) = museum else { return Known::unknown("Museum API unavailable") };
    let member = m.get("members").and_then(|ms| ms.get(member_uuid)).or_else(|| m.get("items").map(|_| m));
    let Some(member) = member else { return Known::unknown("Museum not unlocked or API disabled") };
    let items: Vec<String> = member.get("items").and_then(Value::as_object).map(|o| o.keys().cloned().collect()).unwrap_or_default();
    Known::known(MuseumInfo {
        appraised: member.get("appraisal").and_then(Value::as_bool).unwrap_or(false),
        value: u(member, &["value"]),
        donated_count: items.len() as u32,
        donated_items: items,
    })
}
