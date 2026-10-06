package dev.skypilot.mod.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises Hypixel chat lines (colour codes already stripped). The message texts were checked
 * against SkyHanni's current filters (github.com/hannibal002/SkyHanni, ChatFilter.kt /
 * RareDropMessages.kt / SlayerApi.kt, beta branch, Oct 2026) - SkyPilot only uses them to hide
 * lines, highlight them or count them for the player's own trackers.
 */
public final class ChatRules {
	/** Groups of lines the player can choose to hide. */
	public enum Hide {
		LOBBY, WARPING, PROFILE, COMBO, ABILITY, GEXP
	}

	public record Drop(String kind, String item) {
	}

	public enum SlayerEvent {
		STARTED, COMPLETE, FAILED
	}

	private static final List<Pattern> LOBBY = List.of(
			Pattern.compile("^(?:>>> )?.* (?:joined|spooked into|slid into) the lobby!(?: <<<)?$"),
			Pattern.compile("^✦ .* found (?:a|an) .* (?:in a (?:Holiday )?)?Mystery Box!$"),
			Pattern.compile("^✦ You earned \\d+ Mystery Dust!$"));
	private static final List<Pattern> WARPING = List.of(
			Pattern.compile("^Sending to server .*\\.\\.\\.$"),
			Pattern.compile("^Request join for (?:Hub|Dungeon Hub #).*\\.\\.\\.$"),
			Pattern.compile("^Warping\\.\\.\\.$|^Warping you to your SkyBlock island\\.\\.\\.$|^Warping using transfer token\\.\\.\\.$"),
			Pattern.compile("^Finding player\\.\\.\\.$|^Sending a visit request\\.\\.\\.$"));
	private static final List<Pattern> PROFILE = List.of(
			Pattern.compile("^You are playing on profile: .*$"),
			Pattern.compile("^Profile ID: [0-9a-f-]{36}$"));
	private static final List<Pattern> COMBO = List.of(
			Pattern.compile("^\\+\\d+ Kill Combo.*$"),
			Pattern.compile("^Your Kill Combo has expired! You reached a \\d+ Kill Combo!$"));
	private static final List<Pattern> ABILITY = List.of(
			Pattern.compile("^Your (?:Implosion|Molten Wave|Spirit Sceptre) hit .* for [\\d,.]+ damage\\.$"),
			Pattern.compile("^There are blocks in the way!$"));
	private static final List<Pattern> GEXP = List.of(
			Pattern.compile("^You earned [\\d,]+ (?:GEXP|Event EXP) (?:\\+ [\\d,]+ Event EXP )?from playing SkyBlock!$"));

	private static final Pattern DROP = Pattern.compile("^(RARE DROP!|VERY RARE DROP!|CRAZY RARE DROP!|PET DROP!) (.+?)(?: \\(\\+[\\d,.]+%? ✯ Magic Find\\)| \\(\\+[\\d,.]+☘\\))?$");
	private static final Pattern ABILITY_READY = Pattern.compile("^([A-Z][A-Za-z' ]{2,40}) is now available!$");
	private static final Pattern COORDS = Pattern.compile("(?i)\\bx:\\s*(-?\\d{1,7})(?:\\.\\d+)?\\s*,?\\s*y:\\s*(-?\\d{1,4})(?:\\.\\d+)?\\s*,?\\s*z:\\s*(-?\\d{1,7})(?:\\.\\d+)?");

	private ChatRules() {
	}

	public static Optional<Hide> hideGroup(String plain) {
		String s = plain.strip();
		if (s.isEmpty()) return Optional.empty();
		if (any(LOBBY, s)) return Optional.of(Hide.LOBBY);
		if (any(WARPING, s)) return Optional.of(Hide.WARPING);
		if (any(PROFILE, s)) return Optional.of(Hide.PROFILE);
		if (any(COMBO, s)) return Optional.of(Hide.COMBO);
		if (any(ABILITY, s)) return Optional.of(Hide.ABILITY);
		if (any(GEXP, s)) return Optional.of(Hide.GEXP);
		return Optional.empty();
	}

	private static boolean any(List<Pattern> ps, String s) {
		for (Pattern p : ps) if (p.matcher(s).matches()) return true;
		return false;
	}

	/** "RARE DROP! Enchanted Book (+208% ✯ Magic Find)" → (RARE DROP, Enchanted Book). */
	public static Optional<Drop> drop(String plain) {
		Matcher m = DROP.matcher(plain.strip());
		if (!m.matches()) return Optional.empty();
		String item = m.group(2).strip();
		// slayer drops are written as "RARE DROP! (Item)"
		if (item.startsWith("(") && item.endsWith(")")) item = item.substring(1, item.length() - 1).strip();
		if (item.isEmpty() || item.length() > 60) return Optional.empty();
		return Optional.of(new Drop(m.group(1).replace("!", ""), item));
	}

	public static Optional<SlayerEvent> slayer(String plain) {
		return switch (plain.strip()) {
			case "SLAYER QUEST STARTED!" -> Optional.of(SlayerEvent.STARTED);
			case "SLAYER QUEST COMPLETE!" -> Optional.of(SlayerEvent.COMPLETE);
			case "SLAYER QUEST FAILED!" -> Optional.of(SlayerEvent.FAILED);
			default -> Optional.empty();
		};
	}

	/** "Mining Speed Boost is now available!" → "Mining Speed Boost". */
	public static Optional<String> abilityReady(String plain) {
		Matcher m = ABILITY_READY.matcher(plain.strip());
		return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
	}

	/** "x: 12, y: 70, z: -40" anywhere in the line. */
	public static Optional<int[]> coords(String plain) {
		Matcher m = COORDS.matcher(plain);
		if (!m.find()) return Optional.empty();
		try {
			int y = Integer.parseInt(m.group(2));
			if (y < -64 || y > 512) return Optional.empty();
			return Optional.of(new int[] {Integer.parseInt(m.group(1)), y, Integer.parseInt(m.group(3))});
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	/**
	 * A line from someone else that contains {@code name} as a word. The part before the first
	 * ": " is the sender (with rank / channel prefix); a line whose sender part is the player
	 * themself is never a mention.
	 */
	public static boolean mentions(String plain, String name) {
		if (name == null || name.length() < 3) return false;
		int colon = plain.indexOf(": ");
		if (colon <= 0) return false; // system lines are not mentions
		String sender = plain.substring(0, colon);
		String body = plain.substring(colon + 2);
		Pattern word = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
		return !word.matcher(sender).find() && word.matcher(body).find();
	}

	/** Two lines count as the same message when they match ignoring case and surrounding space. */
	public static String compactKey(String plain) {
		return plain.strip().toLowerCase(Locale.ROOT);
	}
}
