"""Generates data/farms/*.json reference layouts (SkyPilot-authored, vanilla placement rules)."""
import json, os, sys

root = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
out = os.path.join(root, "data", "farms")
os.makedirs(out, exist_ok=True)

COMMON_NOTE = ("SkyPilot reference layout derived from vanilla block-placement rules; it is not a copy of any "
               "community design. Check the Garden rules in-game before committing materials.")

designs = [
    {
        "id": "wheat_horizontal",
        "name": "Horizontal lanes (wheat / flowers)",
        "crops": ["WHEAT", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE"],
        "description": "Two stacked layers of 96-block lanes: two crop rows on each side of a walking path, with a water channel every 6 blocks. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 16,
        "palette": {"W": "water", "F": "farmland", "P": "stone_bricks", "C": "wheat", "G": "glass"},
        "crop_codes": ["C"],
        "levels": [
            {"y": 0, "cross_section": ["WCC.CC", "WFFPFF"]},
            {"y": 4, "cross_section": ["WCC.CC", "WFFPFF", "GGGGGG"]},
        ],
        "movement": ["Walk along +X on the path", "At the end, step one path over (+Z by 6)", "Walk back along -X", "Repeat; drop to the lower layer when the top layer is done"],
        "harvest_direction": "Along X, breaking the two rows on each side of the path",
        "water_notes": ["Water channels at z = 0, 6, 12, ... keep every farmland block within 4 blocks of water (vanilla hydration rule)."],
        "lighting_notes": [],
        "build_order": ["Place the glass ceiling of the lower layer before the upper layer's farmland so water cannot flow down."],
    },
    {
        "id": "carrot_potato_horizontal",
        "name": "Horizontal lanes (carrot / potato)",
        "crops": ["CARROT_ITEM", "POTATO_ITEM"],
        "description": "Same lane geometry as the wheat layout, planted with carrots or potatoes. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 16,
        "palette": {"W": "water", "F": "farmland", "P": "stone_bricks", "C": "carrots", "G": "glass"},
        "crop_codes": ["C"],
        "levels": [
            {"y": 0, "cross_section": ["WCC.CC", "WFFPFF"]},
            {"y": 4, "cross_section": ["WCC.CC", "WFFPFF", "GGGGGG"]},
        ],
        "movement": ["Walk along +X on the path", "Step to the next path (+Z by 6)", "Walk back along -X"],
        "harvest_direction": "Along X, both sides of the path",
        "water_notes": ["One water channel per 6 blocks keeps all farmland hydrated (vanilla rule)."],
        "lighting_notes": [],
        "build_order": [],
    },
    {
        "id": "melon_pumpkin_lanes",
        "name": "Stem lanes (melon / pumpkin)",
        "crops": ["MELON", "PUMPKIN"],
        "description": "Stem rows on farmland with a free dirt block on both sides for the fruit; the path runs between two stem rows. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 12,
        "palette": {"W": "water", "D": "dirt", "F": "farmland", "S": "melon_stem", "M": "melon", "P": "stone_bricks"},
        "crop_codes": ["M"],
        "levels": [
            {"y": 0, "cross_section": ["WMSM.MSM", "WDFDPDFD"]},
        ],
        "movement": ["Walk along +X on the path", "Move to the next path (+Z by 8)", "Walk back along -X"],
        "harvest_direction": "Along X, breaking the fruit next to the path on both sides",
        "water_notes": ["Water at z = 0, 8, 16, ... keeps every stem's farmland within 2 blocks of water."],
        "lighting_notes": [],
        "build_order": ["Fruit only grows onto the free dirt blocks next to each stem: keep them empty."],
    },
    {
        "id": "cane_rows",
        "name": "Water-channel rows (sugar cane)",
        "crops": ["SUGAR_CANE"],
        "description": "Sand rows on both sides of each water channel (sugar cane must touch water); paths between pairs. Break the upper block and keep the base. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 24,
        "palette": {"S": "sand", "W": "water", "P": "stone_bricks", "c": "sugar_cane", "C": "sugar_cane"},
        "crop_codes": ["C"],
        "levels": [
            {"y": 0, "cross_section": ["C.C.", "c.c.", "SWSP"]},
        ],
        "movement": ["Walk along +X on the path", "Move to the next path (+Z by 4)", "Walk back along -X"],
        "harvest_direction": "Along X; break the top cane block, the base regrows",
        "water_notes": ["Every sand block touches the water channel next to it (required for sugar cane)."],
        "lighting_notes": [],
        "build_order": [],
    },
    {
        "id": "cactus_layers",
        "name": "Checkerboard layers (cactus)",
        "crops": ["CACTUS"],
        "description": "Cactus on sand in a checkerboard so no cactus touches another block horizontally (vanilla rule); a path every third row, two layers with a glass separator. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 32,
        "palette": {"S": "sand", "c": "cactus", "C": "cactus", "P": "stone_bricks", "G": "glass"},
        "crop_codes": ["C"],
        "checker": ["c", "C"],
        "levels": [
            {"y": 0, "cross_section": ["CC.", "cc.", "SSP"]},
            {"y": 5, "cross_section": ["CC.", "cc.", "SSP", "GGG"]},
        ],
        "movement": ["Walk along +X on the path", "Move to the next path (+Z by 3)", "Walk back along -X", "Drop to the lower layer when the top is done"],
        "harvest_direction": "Along X; break the upper cactus block, keep the base",
        "water_notes": [],
        "lighting_notes": [],
        "build_order": ["Place every cactus on a checkerboard: skip each block whose neighbour (X or Z) already has a cactus."],
    },
    {
        "id": "cocoa_walls",
        "name": "Log walls (cocoa beans)",
        "crops": ["INK_SACK:3"],
        "description": "Three-high jungle-log walls with cocoa on both faces; the player walks between two walls. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 24,
        "palette": {"L": "jungle_log", "K": "cocoa", "P": "stone_bricks"},
        "crop_codes": ["K"],
        "levels": [
            {"y": 0, "cross_section": ["KLK.", "KLK.", "KLK.", "PLPP"]},
        ],
        "movement": ["Walk along +X between two walls", "Move to the next gap (+Z by 4)", "Walk back along -X"],
        "harvest_direction": "Along X; cocoa on both walls next to you",
        "water_notes": [],
        "lighting_notes": [],
        "build_order": ["Cocoa can only be placed on the side of a jungle log: build the walls first."],
    },
    {
        "id": "mushroom_terraces",
        "name": "Mycelium terraces (mushrooms)",
        "crops": ["MUSHROOM_COLLECTION"],
        "description": "Two mushroom rows on mycelium per path (mushrooms stay on mycelium at any light level in vanilla). " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 32,
        "palette": {"M": "mycelium", "R": "red_mushroom", "P": "stone_bricks"},
        "crop_codes": ["R"],
        "levels": [
            {"y": 0, "cross_section": ["RR.", "MMP"]},
        ],
        "movement": ["Walk along +X on the path", "Move to the next path (+Z by 3)", "Walk back along -X"],
        "harvest_direction": "Along X; both mushroom rows next to the path",
        "water_notes": [],
        "lighting_notes": ["Mycelium lets mushrooms stay at any light level (vanilla rule), so no lighting changes are needed."],
        "build_order": ["Alternate red and brown mushrooms by row if you farm both."],
    },
    {
        "id": "wart_horizontal",
        "name": "Horizontal lanes (nether wart)",
        "crops": ["NETHER_STALK"],
        "description": "Nether wart on soul sand, two rows each side of the path, two stacked layers. No water is needed. " + COMMON_NOTE,
        "plot": [96, 96], "length": 96, "repeat": 19,
        "palette": {"S": "soul_sand", "N": "nether_wart", "P": "stone_bricks", "G": "glass"},
        "crop_codes": ["N"],
        "levels": [
            {"y": 0, "cross_section": ["NN.NN", "SSPSS"]},
            {"y": 4, "cross_section": ["NN.NN", "SSPSS", "GGGGG"]},
        ],
        "movement": ["Walk along +X on the path", "Move to the next path (+Z by 5)", "Walk back along -X", "Drop to the lower layer when the top is done"],
        "harvest_direction": "Along X, both sides of the path",
        "water_notes": ["Nether wart does not need water."],
        "lighting_notes": [],
        "build_order": [],
    },
]

for d in designs:
    d.setdefault("seconds_per_block", 0.35)
    with open(os.path.join(out, d["id"] + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write("\n")
print(len(designs), "designs written")
