//! Loads the registry from `data/` + `fixtures/` and validates the curated registry against
//! real upstream data so a typo'd item id can never ship.

use std::path::PathBuf;

use skypilot_data::neu::Recipe;
use skypilot_data::{DataPaths, Registry};

fn registry() -> Registry {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..");
    Registry::load(&DataPaths::fixtures(&root)).expect("registry loads")
}

#[test]
fn loads_all_layers() {
    let r = registry();
    assert!(r.items.len() > 5000, "full Hypixel items resource");
    assert!(r.items.contains_key("HYPERION"));
    assert!(r.skills.contains_key("MINING"));
    assert_eq!(r.skills["MINING"].max_level, 60);
    assert!(r.neu.hotm.as_ref().unwrap().perks.contains_key("mining_speed"));
    assert_eq!(r.curated.slayers.len(), 6);
    assert!(r.election.as_ref().unwrap().mayor.is_some());
}

#[test]
fn curated_item_ids_exist() {
    let r = registry();
    let mut missing = Vec::new();
    let mut check = |id: &str, ctx: &str| {
        if let Some(pet) = id.strip_prefix("PET:") {
            if !r.neu.pets.is_pet(pet) {
                missing.push(format!("{ctx}: pet {pet}"));
            }
        } else if !r.items.contains_key(id) {
            missing.push(format!("{ctx}: {id}"));
        }
    };
    for s in &r.curated.slayers {
        for g in &s.gear {
            for id in g.required.iter().chain(&g.recommended).chain(&g.luxury) {
                check(id, &format!("slayer {} {}", s.id, g.slot));
            }
        }
    }
    for c in &r.curated.farming.crops {
        check(&c.item, "crop item");
        for t in &c.tools {
            check(t, &format!("crop {} tool", c.id));
        }
    }
    for t in &r.curated.mining.targets {
        check(&t.drop_item, &format!("mining target {}", t.id));
    }
    for k in &r.curated.kuudra.tiers {
        check(&k.key, "kuudra key");
    }
    for (alias, id) in &r.curated.goals.item_aliases {
        check(id, &format!("alias {alias}"));
    }
    assert!(missing.is_empty(), "unknown ids in data/registry: {missing:#?}");
}

#[test]
fn slayer_definitions_match_neu_drop_tables() {
    let r = registry();
    for s in &r.curated.slayers {
        let table = r.neu.leveling.slayer_table(&s.id).unwrap_or_else(|| panic!("NEU slayer_xp for {}", s.id));
        assert!(table.max_level() >= 5);
        for tier in 1..=s.max_tier {
            let id = s.boss_drop_id(tier);
            assert!(r.neu.drops.contains_key(&id), "missing NEU drop table {id}");
        }
    }
    // Voidgloom IV includes Judgement Core with its published chance.
    let t4 = &r.neu.drops["VOIDGLOOM_SERAPH_4_BOSS"];
    let jc = t4.drops.iter().find(|d| d.id == "JUDGEMENT_CORE").unwrap();
    assert!((jc.chance - 0.000565).abs() < 1e-9);
    let null_sphere = t4.drops.iter().find(|d| d.id == "NULL_SPHERE").unwrap();
    assert_eq!(null_sphere.amount, 130.0);
}

#[test]
fn recipes_and_neu_ids_are_normalized() {
    let r = registry();
    let hyp = &r.neu.recipes["HYPERION"];
    assert!(hyp.iter().any(|rec| matches!(rec, Recipe::Crafting { inputs, .. } if inputs.iter().any(|s| s.id == "NECRON_BLADE"))));
    assert!(r.neu.recipes.values().flatten().any(|rec| matches!(rec, Recipe::Forge { .. })));
    assert!(r.neu.recipes.values().flatten().any(|rec| matches!(rec, Recipe::Kat { .. })));
    assert_eq!(r.neu.normalize_id("ENDER_DRAGON;4"), "PET:ENDER_DRAGON:LEGENDARY");
    assert_eq!(r.neu.normalize_id("SMITE;6"), "ENCHANTMENT_SMITE_6");
    assert_eq!(r.neu.normalize_id("ZOMBIE_SLAYER_RUNE;1"), "RUNE:ZOMBIE_SLAYER:1");
    let cobble = &r.neu.minions["COBBLESTONE"];
    assert_eq!(cobble[0].seconds_between_actions, 14.0);
    assert_eq!(cobble[0].product_name.as_deref(), Some("Cobblestone"));
}

#[test]
fn item_search_handles_names_and_typos() {
    let r = registry();
    assert_eq!(r.search_items("hyperion", 3)[0].id, "HYPERION");
    assert_eq!(r.search_items("Terminator", 3)[0].id, "TERMINATOR");
    assert!(r.search_items("divan's drill", 5).iter().any(|i| i.id == "DIVAN_DRILL"));
    assert!(r.search_items("", 5).is_empty());
}

#[test]
fn terminator_requirements_come_from_api() {
    let r = registry();
    let term = r.item("TERMINATOR").unwrap();
    assert!(term.requirements.iter().any(|q| matches!(q, skypilot_data::Requirement::Slayer { slayer, level: 7 } if slayer == "enderman")));
    let hyp = r.item("HYPERION").unwrap();
    assert!(hyp.requirements.iter().any(|q| matches!(q, skypilot_data::Requirement::DungeonTier { tier: 7, .. })));
}

#[test]
fn farm_designs_are_consistent() {
    use std::collections::HashSet;
    let r = registry();
    assert_eq!(r.farms.len(), 8, "one design per referenced layout");
    for c in &r.curated.farming.crops {
        let d = r.farms.iter().find(|f| f.id == c.design).unwrap_or_else(|| panic!("crop {} references missing design {}", c.id, c.design));
        assert!(d.crops.contains(&c.id), "design {} should list crop {}", d.id, c.id);
    }
    for d in &r.farms {
        let blocks = d.expand(2_000_000);
        assert!(d.crop_blocks() > 1000, "{} has harvestable blocks", d.id);
        let size = d.size();
        assert!(size[0] <= d.plot[0] && size[2] <= d.plot[1], "{} fits its plot", d.id);
        assert!(blocks.iter().all(|b| !b.block.starts_with("unknown:")), "{} palette complete", d.id);
        let at: std::collections::HashMap<(i32, i32, i32), &str> = blocks.iter().map(|b| ((b.x, b.y, b.z), b.block.as_str())).collect();
        for b in &blocks {
            let side = [(1, 0), (-1, 0), (0, 1), (0, -1)].map(|(dx, dz)| at.get(&(b.x + dx, b.y, b.z + dz)).copied());
            match b.block.as_str() {
                // vanilla: cactus breaks when any horizontal neighbour is a block
                "cactus" => assert!(side.iter().all(Option::is_none), "{}: cactus at {:?} touches a block", d.id, (b.x, b.y, b.z)),
                // vanilla: sugar cane needs water next to the block it stands on
                "sugar_cane" if at.get(&(b.x, b.y - 1, b.z)) == Some(&"sand") => {
                    let below = [(1, 0), (-1, 0), (0, 1), (0, -1)].map(|(dx, dz)| at.get(&(b.x + dx, b.y - 1, b.z + dz)).copied());
                    assert!(below.contains(&Some("water")), "{}: cane at {:?} has no water", d.id, (b.x, b.y, b.z));
                }
                "cocoa" => assert!(side.contains(&Some("jungle_log")), "{}: cocoa must touch a jungle log", d.id),
                _ => {}
            }
        }
        let crops: HashSet<&str> = blocks.iter().map(|b| b.block.as_str()).collect();
        assert!(!crops.is_empty());
    }
}

#[test]
fn item_icons_for_the_mod_gui() {
    let r = registry();
    let lapis = r.icon("LAPIS_ARMOR_LEGGINGS");
    assert_eq!(lapis.item, "minecraft:leather_leggings");
    assert_eq!(lapis.color, Some(0x0000FF));
    let book = r.icon("ENCHANTED_DIAMOND");
    assert_eq!(book.item, "minecraft:diamond");
    assert!(book.glint);
    assert_eq!(r.icon("HYPERION|recomb|s5").item, "minecraft:iron_sword", "price keys resolve to their item");
    // player heads carry their texture (Hypixel items resource: SKULL_ITEM + skin.value)
    let head = r.items.values().find(|d| d.material.as_deref() == Some("SKULL_ITEM") && d.skin.is_some()).expect("a textured head");
    let icon = r.icon(&head.id);
    assert_eq!(icon.item, "minecraft:player_head");
    assert!(icon.texture.as_deref().is_some_and(|t| t.len() > 40));
    // pets come from NEU (ENDER_DRAGON;4 = legendary)
    let pet = r.icon("PET:ENDER_DRAGON:LEGENDARY");
    assert_eq!(pet.item, "minecraft:player_head");
    assert!(pet.texture.is_some());
    assert_eq!(r.icon("NOT_AN_ITEM").item, "minecraft:paper");
}

#[test]
fn guide_waypoints_resolve_to_skyhanni_locations() {
    let r = registry();
    assert!(r.locations.len() > 300, "fixture island graphs load: {}", r.locations.len());
    assert!(r.locations.find("DWARVEN_MINES", "Emissary Wilson").is_some());
    assert!(r.locations.on("HUB").iter().all(|l| !l.tags.iter().any(|t| t == "fairy_soul" || t == "hoppity")), "collectibles are never waypoints");
    assert!(!r.curated.guides.is_empty());
    let mut ids = std::collections::HashSet::new();
    for g in &r.curated.guides {
        assert!(ids.insert(g.id.clone()), "duplicate guide id {}", g.id);
        assert!(g.source.starts_with("https://hypixelskyblock.minecraft.wiki/"), "{} cites the wiki", g.id);
        for s in &g.steps {
            if let Some(name) = &s.location {
                let island = s.island.as_deref().unwrap_or(&g.island);
                assert!(r.locations.find(island, name).is_some(), "guide {}: {name} not found on {island}", g.id);
            }
            if let Some(done) = &s.done {
                assert!(done.contains(">="), "guide {}: done must be a requirement expression", g.id);
            }
        }
    }
}

#[test]
fn sea_creatures_from_skyhanni_repo() {
    let r = registry();
    assert!(r.sea_creatures.all.len() >= 80, "{}", r.sea_creatures.all.len());
    let squid = r.sea_creatures.by_message("Pitch darkness reveals a Night Squid.").expect("Night Squid");
    assert_eq!(squid.rarity, "COMMON");
    assert!(r.sea_creatures.all.iter().any(|c| c.rarity == "MYTHIC"));
}
