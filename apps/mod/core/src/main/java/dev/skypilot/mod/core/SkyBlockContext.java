package dev.skypilot.mod.core;

import java.util.OptionalLong;

/**
 * What the player can currently see about their SkyBlock situation, derived only from the
 * sidebar scoreboard and tab list that the server shows to every player.
 */
public record SkyBlockContext(boolean inSkyBlock, String area, OptionalLong purse, OptionalLong bits, String profileType) {
	public static final SkyBlockContext NONE = new SkyBlockContext(false, "", OptionalLong.empty(), OptionalLong.empty(), "");

	public boolean hasArea() {
		return inSkyBlock && !area.isEmpty();
	}
}
