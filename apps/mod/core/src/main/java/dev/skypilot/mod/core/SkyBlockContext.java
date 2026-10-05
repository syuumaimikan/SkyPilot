package dev.skypilot.mod.core;

import java.util.OptionalLong;

/**
 * What the player can currently see about their SkyBlock situation, derived only from the
 * sidebar scoreboard and tab list that the server shows to every player.
 */
public record SkyBlockContext(boolean inSkyBlock, String area, OptionalLong purse, OptionalLong bits, String profileType, String island) {
	public static final SkyBlockContext NONE = new SkyBlockContext(false, "", OptionalLong.empty(), OptionalLong.empty(), "", "");

	public SkyBlockContext(boolean inSkyBlock, String area, OptionalLong purse, OptionalLong bits, String profileType) {
		this(inSkyBlock, area, purse, bits, profileType, "");
	}

	/** SkyHanni-style island id from the tab list's "Area:" line (e.g. "DWARVEN_MINES"), or "". */
	public String islandId() {
		String id = Islands.idOf(island);
		return id == null ? "" : id;
	}

	public boolean hasArea() {
		return inSkyBlock && !area.isEmpty();
	}
}
