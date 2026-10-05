package dev.skypilot.mod.core;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Parses the visible SkyBlock sidebar / tab list into a {@link SkyBlockContext}.
 *
 * <p>Sidebar examples (formatting already stripped): title "SKYBLOCK CO-OP", lines
 * "⏣ The Hub", "ф Wizard Tower" (Rift), "Purse: 1,234,567", "Piggy: 12,000", "Bits: 1,234".
 * Tab list example: "Area: Dwarven Mines", "Dungeon: Catacombs".
 */
public final class ContextParser {
	private ContextParser() {
	}

	public static SkyBlockContext parse(String sidebarTitle, List<String> sidebarLines, List<String> tabLines) {
		String title = Text.strip(sidebarTitle).toUpperCase(Locale.ROOT);
		if (!title.contains("SKYBLOCK")) return SkyBlockContext.NONE;
		String profileType = title.contains("CO-OP") ? "CO-OP" : title.contains("GUEST") ? "GUEST" : title.contains("☀") || title.contains("STRANDED") ? "STRANDED" : "";
		String area = "";
		OptionalLong purse = OptionalLong.empty();
		OptionalLong bits = OptionalLong.empty();
		for (String raw : sidebarLines) {
			String line = Text.strip(raw);
			if (line.startsWith("⏣") || line.startsWith("ф")) {
				area = line.substring(1).strip();
			} else if (line.startsWith("Purse:") || line.startsWith("Piggy:")) {
				purse = Text.parseLong(line.substring(line.indexOf(':') + 1));
			} else if (line.startsWith("Bits:")) {
				bits = Text.parseLong(line.substring(5));
			}
		}
		if (area.isEmpty() && tabLines != null) {
			for (String raw : tabLines) {
				String line = Text.strip(raw);
				if (line.startsWith("Area:") || line.startsWith("Dungeon:")) {
					area = line.substring(line.indexOf(':') + 1).strip();
					break;
				}
			}
		}
		return new SkyBlockContext(true, area, purse, bits, profileType);
	}
}
