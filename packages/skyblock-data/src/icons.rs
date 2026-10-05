//! Item icons for the mod's GUI: the vanilla item a SkyBlock item is drawn with, plus the
//! player-head texture, leather colour and enchantment glint. Hypixel's items resource still
//! names materials the 1.8 Bukkit way (`SKULL_ITEM` + durability 3) and NEU uses 1.8 ids
//! (`minecraft:skull` + damage 3); both are mapped to current ids (`minecraft:player_head`).
//! Ids the client does not know fall back to paper there.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct IconSpec {
    /// Current item id, e.g. "minecraft:player_head".
    pub item: String,
    /// Base64 `textures` property value for player heads.
    pub texture: Option<String>,
    /// RGB colour for dyed leather.
    pub color: Option<u32>,
    pub glint: bool,
}

const COLORS: [&str; 16] = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
];
const WOODS: [&str; 6] = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"];
const DYES: [&str; 16] = [
    "ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
    "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal",
];

const TOOLS_AND_ARMOR: [&str; 9] = ["sword", "axe", "hoe", "pickaxe", "spade", "helmet", "chestplate", "leggings", "boots"];

fn pick<'a>(list: &[&'a str], i: u32) -> &'a str {
    list.get(i as usize).copied().unwrap_or(list[0])
}

/// Bukkit 1.8 material names → 1.8 namespaced ids (only where they differ).
fn bukkit_alias(name: &str) -> &str {
    match name {
        "skull_item" => "skull",
        "ink_sack" => "dye",
        "stained_clay" => "stained_hardened_clay",
        "hard_clay" => "hardened_clay",
        "red_rose" => "red_flower",
        "raw_fish" => "fish",
        "wood" => "planks",
        "wood_step" => "wooden_slab",
        "step" => "stone_slab",
        "log_2" => "log2",
        "leaves_2" => "leaves2",
        "long_grass" => "tallgrass",
        "smooth_brick" => "stonebrick",
        "sugar_cane" => "reeds",
        "carrot_item" => "carrot",
        "potato_item" => "potato",
        "nether_stalk" => "nether_wart",
        "sulphur" => "gunpowder",
        "seeds" => "wheat_seeds",
        "firework" => "fireworks",
        "monster_egg" => "spawn_egg",
        "exp_bottle" => "experience_bottle",
        "snow_ball" => "snowball",
        "watch" => "clock",
        "book_and_quill" => "writable_book",
        "empty_map" => "map",
        "map" => "filled_map",
        "workbench" => "crafting_table",
        "enchantment_table" => "enchanting_table",
        "ender_stone" => "end_stone",
        "ender_portal_frame" => "end_portal_frame",
        "eye_of_ender" => "ender_eye",
        "mycel" => "mycelium",
        "thin_glass" => "glass_pane",
        "iron_fence" => "iron_bars",
        "nether_fence" => "nether_brick_fence",
        "nether_brick" => "nether_bricks_block",
        "nether_brick_item" => "netherbrick",
        "clay_brick" => "brick",
        "brick" => "brick_block",
        "clay" => "clay_block",
        "cobble_wall" => "cobblestone_wall",
        "smooth_stairs" => "stone_brick_stairs",
        "water_lily" => "waterlily",
        "huge_mushroom_1" => "brown_mushroom_block",
        "huge_mushroom_2" => "red_mushroom_block",
        "grilled_pork" => "cooked_porkchop",
        "pork" => "porkchop",
        "raw_beef" => "beef",
        "raw_chicken" => "chicken",
        "mushroom_soup" => "mushroom_stew",
        "leash" => "lead",
        "command" => "command_block",
        "diode" => "repeater",
        "redstone_comparator" => "comparator",
        "redstone_lamp_off" => "redstone_lamp",
        "redstone_torch_on" => "redstone_torch",
        "piston_base" => "piston",
        "piston_sticky_base" => "sticky_piston",
        "rails" => "rail",
        "powered_rail" => "golden_rail",
        "powered_minecart" => "furnace_minecart",
        "storage_minecart" => "chest_minecart",
        "explosive_minecart" => "tnt_minecart",
        "cauldron_item" => "cauldron",
        "brewing_stand_item" => "brewing_stand",
        "flower_pot_item" => "flower_pot",
        "mob_spawner" => "spawner",
        "quartz_ore" => "quartz_ore",
        "carrot_stick" => "carrot_on_a_stick",
        "speckled_melon" => "speckled_melon",
        "gold_record" => "record_13",
        "green_record" => "record_cat",
        "record_3" => "record_blocks",
        "record_4" => "record_chirp",
        "record_5" => "record_far",
        "record_6" => "record_mall",
        "record_7" => "record_mellohi",
        "record_8" => "record_stal",
        "record_9" => "record_strad",
        "record_10" => "record_ward",
        "record_12" => "record_wait",
        "wood_door" => "wooden_door",
        "wood_plate" => "wooden_pressure_plate",
        "wood_button" => "wooden_button",
        "wood_stairs" => "oak_stairs",
        "trap_door" => "trapdoor",
        "gold_plate" => "light_weighted_pressure_plate",
        "iron_plate" => "heavy_weighted_pressure_plate",
        "stone_plate" => "stone_pressure_plate",
        "gold_barding" => "golden_horse_armor",
        "iron_barding" => "iron_horse_armor",
        "diamond_barding" => "diamond_horse_armor",
        other => other,
    }
}

/// Current item id (without namespace) for a 1.8 Bukkit material or 1.8 namespaced id.
pub fn modern_id(legacy: &str, damage: u32) -> String {
    let legacy = legacy.trim();
    let raw = legacy.trim_start_matches("minecraft:").to_ascii_lowercase();
    // Bukkit names and 1.8 ids disagree on a few words (Bukkit MAP = filled map, 1.8 map = empty)
    let name = if legacy.starts_with("minecraft:") { raw.as_str() } else { bukkit_alias(&raw) };
    let s: String = match name {
        "skull" => pick(&["skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head"], damage).into(),
        "dye" => pick(&DYES, damage).into(),
        "wool" | "carpet" | "stained_glass" | "stained_glass_pane" => format!("{}_{name}", pick(&COLORS, damage)),
        "stained_hardened_clay" => format!("{}_terracotta", pick(&COLORS, damage)),
        "hardened_clay" => "terracotta".into(),
        "banner" => format!("{}_banner", pick(&COLORS, 15u32.saturating_sub(damage.min(15)))),
        "log" => format!("{}_log", pick(&WOODS[..4], damage)),
        "log2" => format!("{}_log", pick(&WOODS[4..], damage)),
        "leaves" => format!("{}_leaves", pick(&WOODS[..4], damage)),
        "leaves2" => format!("{}_leaves", pick(&WOODS[4..], damage)),
        "planks" => format!("{}_planks", pick(&WOODS, damage)),
        "wooden_slab" => format!("{}_slab", pick(&WOODS, damage)),
        "sapling" => format!("{}_sapling", pick(&WOODS, damage)),
        "stone_slab" => pick(&["smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab"], damage).into(),
        "red_flower" => pick(&["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"], damage).into(),
        "yellow_flower" => "dandelion".into(),
        "double_plant" => pick(&["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"], damage).into(),
        "tallgrass" => if damage == 2 { "fern" } else { "short_grass" }.into(),
        "deadbush" => "dead_bush".into(),
        "fish" => pick(&["cod", "salmon", "tropical_fish", "pufferfish"], damage).into(),
        "cooked_fish" => pick(&["cooked_cod", "cooked_salmon"], damage).into(),
        "coal" => if damage == 1 { "charcoal" } else { "coal" }.into(),
        "golden_apple" => if damage == 1 { "enchanted_golden_apple" } else { "golden_apple" }.into(),
        "sand" => if damage == 1 { "red_sand" } else { "sand" }.into(),
        "dirt" => pick(&["dirt", "coarse_dirt", "podzol"], damage).into(),
        "sponge" => if damage == 1 { "wet_sponge" } else { "sponge" }.into(),
        "stone" => pick(&["stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite"], damage).into(),
        "stonebrick" => pick(&["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"], damage).into(),
        "sandstone" => pick(&["sandstone", "chiseled_sandstone", "cut_sandstone"], damage).into(),
        "red_sandstone" => pick(&["red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone"], damage).into(),
        "prismarine" => pick(&["prismarine", "prismarine_bricks", "dark_prismarine"], damage).into(),
        "quartz_block" => pick(&["quartz_block", "chiseled_quartz_block", "quartz_pillar"], damage).into(),
        "potion" => if damage & 16384 != 0 { "splash_potion" } else { "potion" }.into(),
        "spawn_egg" => "pig_spawn_egg".into(),
        "reeds" => "sugar_cane".into(),
        "melon" => "melon_slice".into(),
        "melon_block" => "melon".into(),
        "speckled_melon" => "glistering_melon_slice".into(),
        "lit_pumpkin" => "jack_o_lantern".into(),
        "fireworks" => "firework_rocket".into(),
        "firework_charge" => "firework_star".into(),
        "web" => "cobweb".into(),
        "waterlily" => "lily_pad".into(),
        "noteblock" => "note_block".into(),
        "golden_rail" => "powered_rail".into(),
        "wooden_door" => "oak_door".into(),
        "fence" => "oak_fence".into(),
        "fence_gate" => "oak_fence_gate".into(),
        "trapdoor" => "oak_trapdoor".into(),
        "wooden_pressure_plate" => "oak_pressure_plate".into(),
        "wooden_button" => "oak_button".into(),
        "sign" => "oak_sign".into(),
        "bed" => "red_bed".into(),
        "boat" => "oak_boat".into(),
        "quartz_ore" => "nether_quartz_ore".into(),
        "netherbrick" => "nether_brick".into(),
        "nether_bricks_block" | "nether_brick_block" => "nether_bricks".into(),
        "brick_block" => "bricks".into(),
        "clay_block" => "clay".into(),
        "grass" => "grass_block".into(),
        "snow" => "snow_block".into(),
        "lit_redstone_lamp" => "redstone_lamp".into(),
        "mob_spawner" => "spawner".into(),
        "map" => "map".into(),
        "filled_map" => "filled_map".into(),
        n if n.starts_with("record_") => format!("music_disc_{}", &n[7..]),
        n if n.starts_with("gold_") && TOOLS_AND_ARMOR.contains(&&n[5..]) => format!("golden_{}", &n[5..]).replace("spade", "shovel"),
        n if n.starts_with("wood_") && TOOLS_AND_ARMOR.contains(&&n[5..]) => format!("wooden_{}", &n[5..]).replace("spade", "shovel"),
        n if n.ends_with("_spade") => n.replace("_spade", "_shovel"),
        n if n.ends_with("_door_item") => n.trim_end_matches("_item").to_string(),
        n if n.ends_with("_wood_stairs") => n.replace("_wood_stairs", "_stairs"),
        n => n.to_string(),
    };
    format!("minecraft:{s}")
}

/// "r,g,b" or "r:g:b" → 0xRRGGBB.
pub fn parse_rgb(s: &str) -> Option<u32> {
    let p: Vec<u32> = s.split([',', ':']).map(|x| x.trim().parse::<u8>().map(u32::from)).collect::<Result<_, _>>().ok()?;
    (p.len() == 3).then(|| (p[0] << 16) | (p[1] << 8) | p[2])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_legacy_names() {
        assert_eq!(modern_id("SKULL_ITEM", 3), "minecraft:player_head");
        assert_eq!(modern_id("minecraft:skull", 3), "minecraft:player_head");
        assert_eq!(modern_id("INK_SACK", 3), "minecraft:cocoa_beans");
        assert_eq!(modern_id("minecraft:dye", 4), "minecraft:lapis_lazuli");
        assert_eq!(modern_id("STAINED_GLASS_PANE", 14), "minecraft:red_stained_glass_pane");
        assert_eq!(modern_id("STAINED_CLAY", 11), "minecraft:blue_terracotta");
        assert_eq!(modern_id("GOLD_SWORD", 0), "minecraft:golden_sword");
        assert_eq!(modern_id("GOLD_SPADE", 0), "minecraft:golden_shovel");
        assert_eq!(modern_id("WOOD_AXE", 0), "minecraft:wooden_axe");
        assert_eq!(modern_id("DIAMOND_SPADE", 0), "minecraft:diamond_shovel");
        assert_eq!(modern_id("RAW_FISH", 3), "minecraft:pufferfish");
        assert_eq!(modern_id("LOG_2", 1), "minecraft:dark_oak_log");
        assert_eq!(modern_id("WOOD", 5), "minecraft:dark_oak_planks");
        assert_eq!(modern_id("MELON", 0), "minecraft:melon_slice");
        assert_eq!(modern_id("MELON_BLOCK", 0), "minecraft:melon");
        assert_eq!(modern_id("EMPTY_MAP", 0), "minecraft:map");
        assert_eq!(modern_id("MAP", 0), "minecraft:filled_map");
        assert_eq!(modern_id("minecraft:map", 0), "minecraft:map");
        assert_eq!(modern_id("GOLD_INGOT", 0), "minecraft:gold_ingot");
        assert_eq!(modern_id("GOLD_BLOCK", 2), "minecraft:gold_block");
        assert_eq!(modern_id("GOLD_NUGGET", 0), "minecraft:gold_nugget");
        assert_eq!(modern_id("BRICK", 0), "minecraft:bricks");
        assert_eq!(modern_id("CLAY_BRICK", 0), "minecraft:brick");
        assert_eq!(modern_id("minecraft:brick", 0), "minecraft:brick");
        assert_eq!(modern_id("SULPHUR", 0), "minecraft:gunpowder");
        assert_eq!(modern_id("RECORD_11", 0), "minecraft:music_disc_11");
        assert_eq!(modern_id("GOLD_RECORD", 0), "minecraft:music_disc_13");
        assert_eq!(modern_id("JUNGLE_DOOR_ITEM", 0), "minecraft:jungle_door");
        assert_eq!(modern_id("BIRCH_WOOD_STAIRS", 0), "minecraft:birch_stairs");
        assert_eq!(modern_id("BANNER", 15), "minecraft:white_banner");
        assert_eq!(modern_id("LEATHER_CHESTPLATE", 0), "minecraft:leather_chestplate");
        assert_eq!(modern_id("minecraft:iron_sword", 0), "minecraft:iron_sword");
        assert_eq!(modern_id("SKULL_ITEM", 99), "minecraft:skeleton_skull", "out-of-range damage falls back to the first variant");
        assert_eq!(parse_rgb("0,0,255"), Some(255));
        assert_eq!(parse_rgb("255:0:0"), Some(0xFF0000));
        assert_eq!(parse_rgb("bad"), None);
    }
}
