package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a widget of the SkyBlock tab list: the header line ("Commissions:", "Powders:" - widget
 * names as in SkyHanni's TabWidget list) followed by its indented entries. Only what the tab list
 * already shows to the player.
 */
public final class TabSections {
	private static final Pattern NUMBER = Pattern.compile("(-?[\\d,]+(?:\\.\\d+)?)\\s*([kKmMbB])?");

	private TabSections() {
	}

	/** Entries below {@code header} (exact match after stripping), until a blank line or the next header. */
	public static List<String> section(List<String> tabLines, String header) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < tabLines.size(); i++) {
			if (!Text.strip(tabLines.get(i)).strip().equals(header)) continue;
			for (int j = i + 1; j < tabLines.size(); j++) {
				String raw = Text.strip(tabLines.get(j));
				// a blank cell ends the widget, so does the next widget's header ("Powders:")
				if (raw.isBlank() || !raw.startsWith(" ") && raw.strip().endsWith(":")) break;
				out.add(raw.strip());
				if (out.size() >= 12) break;
			}
			break;
		}
		return out;
	}

	/** "Mithril: 1,234,567" → 1234567; "Gemstone: 12.5k" → 12500. */
	public static OptionalDouble valueAfterColon(String entry) {
		int colon = entry.indexOf(':');
		if (colon < 0) return OptionalDouble.empty();
		Matcher m = NUMBER.matcher(entry.substring(colon + 1).strip());
		if (!m.lookingAt()) return OptionalDouble.empty();
		try {
			double v = Double.parseDouble(m.group(1).replace(",", ""));
			if (m.group(2) != null) {
				v *= switch (Character.toLowerCase(m.group(2).charAt(0))) {
					case 'k' -> 1e3;
					case 'm' -> 1e6;
					default -> 1e9;
				};
			}
			return OptionalDouble.of(v);
		} catch (NumberFormatException e) {
			return OptionalDouble.empty();
		}
	}

	/** Label before the colon ("Mithril Miner" for "Mithril Miner: 45%"). */
	public static String label(String entry) {
		int colon = entry.indexOf(':');
		return colon < 0 ? entry : entry.substring(0, colon).strip();
	}
}
