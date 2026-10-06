package dev.skypilot.mod.core;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbers read from item lore in Hypixel menus (formats as in SkyHanni's SackApi and
 * InstanceChestProfit / InstanceChestAPI): a sack's "Stored: 28,183/60.5k", and the cost of a
 * dungeon / Kuudra reward chest ("1,000,000 Coins" or "FREE").
 */
public final class LoreValues {
	private static final Pattern STORED = Pattern.compile("^Stored: ([\\d,]+)/");
	private static final Pattern COST = Pattern.compile("^([\\d,]+) Coins$");
	private static final Pattern REWARD_CHEST = Pattern.compile("^(?:Wood|Gold|Diamond|Emerald|Obsidian|Bedrock|Paid|Free)(?: Chest)?$");

	private LoreValues() {
	}

	/** Items stored in a sack, from one of its item's lore lines. */
	public static OptionalDouble stored(List<String> lore) {
		for (String raw : lore) {
			Matcher m = STORED.matcher(Text.strip(raw).strip());
			if (m.find()) {
				try {
					return OptionalDouble.of(Double.parseDouble(m.group(1).replace(",", "")));
				} catch (NumberFormatException e) {
					return OptionalDouble.empty();
				}
			}
		}
		return OptionalDouble.empty();
	}

	/** Window titles of dungeon (Wood … Bedrock) and Kuudra (Paid / Free) reward chests. */
	public static boolean isRewardChest(String title) {
		return REWARD_CHEST.matcher(Text.strip(title).strip()).matches();
	}

	/** The chest's opening cost in coins (0 for "FREE"); empty when no cost line is shown. */
	public static Optional<Double> chestCost(List<String> lore) {
		for (String raw : lore) {
			String s = Text.strip(raw).strip();
			if (s.equals("FREE")) return Optional.of(0.0);
			Matcher m = COST.matcher(s);
			if (m.matches()) {
				try {
					return Optional.of(Double.parseDouble(m.group(1).replace(",", "")));
				} catch (NumberFormatException e) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}
}
