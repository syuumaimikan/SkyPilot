package dev.skypilot.mod.core;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rarity and the small slot number of an item, from what its tooltip and custom_data show. */
public final class ItemInfo {
	public enum Rarity {
		COMMON(0xFFFFFF), UNCOMMON(0x55FF55), RARE(0x5555FF), EPIC(0xAA00AA), LEGENDARY(0xFFAA00), MYTHIC(0xFF55FF), DIVINE(0x55FFFF), SPECIAL(0xFF5555),
		VERY_SPECIAL(0xFF5555), ULTIMATE(0xAA0000), ADMIN(0xAA0000);

		public final int rgb;

		Rarity(int rgb) {
			this.rgb = rgb;
		}
	}

	/** Item facts the platform reads from the stack (custom_data and display name). */
	public record Facts(String name, String skyblockId, Map<String, Integer> enchantments, Integer stars, String windowTitle) {
	}

	private static final Pattern RARITY = Pattern.compile("\\b(VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|ADMIN)\\b");
	private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl (\\d{1,3})\\]");
	private static final Pattern MINION = Pattern.compile("^[A-Z_]+_GENERATOR_(\\d{1,2})$");
	private static final Pattern TRAILING_LEVEL = Pattern.compile("^(.+?) ([IVXLC]+|\\d{1,3})$");

	private ItemInfo() {
	}

	/** The rarity line is one of the last lore lines ("LEGENDARY SWORD", "a MYTHIC DUNGEON SWORD a"). */
	public static Rarity rarity(List<String> lore) {
		int checked = 0;
		for (int i = lore.size() - 1; i >= 0 && checked < 3; i--) {
			String line = Text.strip(lore.get(i)).strip();
			if (line.isEmpty()) continue;
			checked++;
			Matcher m = RARITY.matcher(line);
			// the rarity word starts the line (after the 1-char obfuscated marker of recombobulated
			// items); the pattern is case-sensitive, so ordinary lore ("a rare drop") never matches
			if (m.find() && m.start() <= 2) return Rarity.valueOf(m.group(1).replace(' ', '_'));
		}
		return null;
	}

	/** Book level, pet level, minion tier, dungeon stars, or a skill / collection level in those menus. */
	public static String slotNumber(Facts f) {
		String name = f.name() == null ? "" : Text.strip(f.name()).strip();
		String id = f.skyblockId() == null ? "" : f.skyblockId();
		if (id.equals("ENCHANTED_BOOK") && f.enchantments() != null && f.enchantments().size() == 1) {
			return String.valueOf(f.enchantments().values().iterator().next());
		}
		Matcher pet = PET_LEVEL.matcher(name);
		if (pet.find()) return pet.group(1);
		Matcher minion = MINION.matcher(id);
		if (minion.matches()) return minion.group(1);
		if (f.stars() != null && f.stars() > 0) return f.stars() + "✪";
		String title = f.windowTitle() == null ? "" : Text.strip(f.windowTitle());
		if (id.isEmpty() && (title.equals("Your Skills") || title.endsWith("Collections") || title.endsWith("Collection"))) {
			Matcher m = TRAILING_LEVEL.matcher(name);
			if (m.matches()) {
				int n = m.group(2).chars().allMatch(Character::isDigit) ? Integer.parseInt(m.group(2)) : roman(m.group(2));
				if (n > 0) return String.valueOf(n);
			}
		}
		return null;
	}

	public static int roman(String s) {
		int total = 0;
		int prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> -1;
			};
			if (v < 0) return -1;
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}
}
