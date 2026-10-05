package dev.skypilot.mod.core;

import java.util.Locale;
import java.util.Map;

/** Island names as Hypixel shows them in the tab list ("Area: Dwarven Mines") ↔ SkyHanni island ids. */
public final class Islands {
	private static final Map<String, String> IDS = Map.ofEntries(
			Map.entry("hub", "HUB"),
			Map.entry("private island", "PRIVATE_ISLAND"),
			Map.entry("garden", "GARDEN"),
			Map.entry("the farming islands", "THE_FARMING_ISLANDS"),
			Map.entry("the park", "THE_PARK"),
			Map.entry("galatea", "GALATEA"),
			Map.entry("spider's den", "SPIDER_DEN"),
			Map.entry("the end", "THE_END"),
			Map.entry("crimson isle", "CRIMSON_ISLE"),
			Map.entry("gold mine", "GOLD_MINES"),
			Map.entry("deep caverns", "DEEP_CAVERNS"),
			Map.entry("dwarven mines", "DWARVEN_MINES"),
			Map.entry("crystal hollows", "CRYSTAL_HOLLOWS"),
			Map.entry("glacite tunnels", "GLACITE_TUNNELS"),
			Map.entry("dungeon hub", "DUNGEON_HUB"),
			Map.entry("jerry's workshop", "WINTER"),
			Map.entry("the rift", "THE_RIFT"),
			Map.entry("backwater bayou", "BACKWATER_BAYOU"));

	private static final Map<String, String[]> NAMES = Map.ofEntries(
			Map.entry("HUB", new String[]{"Hub", "ハブ"}),
			Map.entry("GARDEN", new String[]{"Garden", "ガーデン"}),
			Map.entry("THE_FARMING_ISLANDS", new String[]{"The Farming Islands", "農業の島"}),
			Map.entry("THE_PARK", new String[]{"The Park", "パーク"}),
			Map.entry("GALATEA", new String[]{"Galatea", "ガラテア"}),
			Map.entry("SPIDER_DEN", new String[]{"Spider's Den", "スパイダーの巣"}),
			Map.entry("THE_END", new String[]{"The End", "ジ・エンド"}),
			Map.entry("CRIMSON_ISLE", new String[]{"Crimson Isle", "クリムゾン島"}),
			Map.entry("GOLD_MINES", new String[]{"Gold Mine", "金鉱山"}),
			Map.entry("DEEP_CAVERNS", new String[]{"Deep Caverns", "ディープ洞窟"}),
			Map.entry("DWARVEN_MINES", new String[]{"Dwarven Mines", "ドワーフ鉱山"}),
			Map.entry("CRYSTAL_HOLLOWS", new String[]{"Crystal Hollows", "クリスタル洞窟"}),
			Map.entry("GLACITE_TUNNELS", new String[]{"Glacite Tunnels", "グレイサイトトンネル"}),
			Map.entry("DUNGEON_HUB", new String[]{"Dungeon Hub", "ダンジョンハブ"}),
			Map.entry("WINTER", new String[]{"Jerry's Workshop", "ジェリーの工房"}),
			Map.entry("THE_RIFT", new String[]{"The Rift", "リフト"}),
			Map.entry("BACKWATER_BAYOU", new String[]{"Backwater Bayou", "バックウォーター湿地"}));

	private Islands() {
	}

	/** SkyHanni island id for a tab-list island name, or null when unknown. */
	public static String idOf(String tabName) {
		if (tabName == null) return null;
		return IDS.get(Text.strip(tabName).strip().toLowerCase(Locale.ROOT));
	}

	/** Display name of an island id. */
	public static String name(String id, Lang lang) {
		String[] n = NAMES.get(id);
		return n == null ? id : lang.t(n[0], n[1]);
	}
}
