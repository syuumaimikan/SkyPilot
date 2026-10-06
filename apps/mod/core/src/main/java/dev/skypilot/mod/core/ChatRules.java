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
		LOBBY, WARPING, PROFILE, COMBO, ABILITY, GEXP, SACKS, SEPARATOR, MINING_CHEST, DUNGEON, WELCOME, USELESS_DROP, NOTICE, WARNING, SKYMALL, FIRE_SALE,
		ACHIEVEMENT, PARKOUR, TELEPORT_PAD, SOLO_CLASS, FAIRY
	}

	public record Trophy(String fish, String tier) {
	}

	public record Powder(String type, int amount) {
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

	private static final List<Pattern> MINING_CHEST = List.of(
			Pattern.compile("^You uncovered a treasure chest!$"),
			Pattern.compile("^You have successfully picked the lock on this chest!$"),
			Pattern.compile("^This chest has already been looted\\.$"),
			Pattern.compile("^You need a tool with a Breaking Power of \\d+ to mine .+"));
	/** Dungeon noise (SkyHanni DungeonChatFilter); never applied to lines that look like player chat. */
	private static final List<Pattern> DUNGEON = List.of(
			Pattern.compile("^Your Guided Sheep hit .* enem(?:y|ies) for .* damage\\.$"),
			Pattern.compile("^BUFF! You were splashed by .* with Healing VIII!$"),
			Pattern.compile("^You were healed for .* health by .*!$"),
			Pattern.compile("^You gained .* HP worth of absorption for 3s from .*!$"),
			Pattern.compile("^.* picked up your .* Orb!$"),
			Pattern.compile("^This ability is on cooldown for .*s\\.$"),
			Pattern.compile("^.* healed you for .* health!$"),
			Pattern.compile("^Your bone plating reduced the damage you took by .*!$"),
			Pattern.compile("^.* formed a tether with you!$"),
			Pattern.compile("^Your tether with .* healed you for .* health\\.$"),
			Pattern.compile("^Your Spirit Pet (?:healed|hit) .*$"),
			Pattern.compile("^You need at least .* mana to activate this!$"),
			Pattern.compile("^Your fairy healed .* for .* health!$|^.* fairy healed you for .* health!$"),
			Pattern.compile("^.* used .* on you!$"),
			Pattern.compile("^The .* (?:struck|hit) you for .* damage!$"),
			Pattern.compile("^.* (?:struck|hit) you for .* (?:true )?damage\\.$"),
			Pattern.compile("^.* exploded, hitting you for .* damage\\.$"),
			Pattern.compile("^Your .* hit .* (?:enemy|enemies) for .* damage\\.$"),
			Pattern.compile("^.* is ready to use! Press DROP to activate it!$"));
	private static final List<Pattern> USELESS_DROP = List.of(
			Pattern.compile("^RARE DROP! (?:Enchanted Ender Pearl|Enchanted End Stone|Crystal Fragment)(?: \\(.*\\))?$"));
	private static final List<Pattern> NOTICE = List.of(
			Pattern.compile("^Your previous Plasmaflux Power Orb was removed!$"),
			Pattern.compile("^You used your Mining Speed Boost Pickaxe Ability!$|^Your Mining Speed Boost has expired!$"),
			Pattern.compile("^(?:You have just received|Since you've been away you earned) 0 coins as interest in your (?:personal|co-op) bank account!$"),
			Pattern.compile("^You tipped \\d+ players? in \\d+(?: different)? games?!$"));
	private static final List<Pattern> WARNING = List.of(
			Pattern.compile("^You are sending commands too fast! Please slow down\\.$|^You can't use this while in combat!$|^You can not modify your equipped armor set!$"),
			Pattern.compile("^Please wait a few seconds between refreshing!$|^Whoa! Slow down there!$|^Wait a moment before confirming!$"),
			Pattern.compile("^This item is not salvageable!$|^Place a Dungeon weapon or armor piece above the anvil to salvage it!$"),
			Pattern.compile("^You cannot open the SkyBlock menu while in combat!$"),
			Pattern.compile("^Your radio is weak\\. Find another enjoyer to boost it\\.$|^Your radio signal is strong!$|^Your radio lost signal\\. There's too many enjoyers on this channel\\.$"));
	private static final List<Pattern> SKYMALL = List.of(
			Pattern.compile("^New day! Your (?:Sky Mall|Lottery) buff changed!$"),
			Pattern.compile("^You can disable this messaging by toggling (?:Sky Mall in your /hotm|Lottery in your /hotf)!$"));
	private static final List<Pattern> FIRE_SALE = List.of(
			Pattern.compile("^♨ Fire Sales for .* are starting soon!$"),
			Pattern.compile("^. FIRE SALE .$"),
			Pattern.compile("^♨ Selling multiple items for a limited time!$"));
	private static final List<Pattern> ACHIEVEMENT = List.of(Pattern.compile("^.?>> +Achievement Unlocked: .* +<<.?$"));
	private static final List<Pattern> PARKOUR = List.of(Pattern.compile("^Started parkour .*$"), Pattern.compile("^Cancelled parkour!.*$"));
	private static final List<Pattern> TELEPORT_PAD = List.of(Pattern.compile("^Warped from the .*$"), Pattern.compile("^This Teleport Pad does not have a destination set!$"));
	private static final List<Pattern> SOLO_CLASS = List.of(
			Pattern.compile("^Your (?:Healer|Mage|Berserk|Archer|Tank) stats are doubled because you are the only player using this class!$"));
	private static final List<Pattern> FAIRY = List.of(
			Pattern.compile("^[\\w']+ the Fairy: You killed me! Take this Revive Stone so that my death is not in vain!$"));
	/** "GROSS! A … Pest has appeared in Plot - S 4!" / "YUCK! 4 … Pest have spawned in The Barn!" (SkyHanni PestSpawn). */
	private static final Pattern PEST = Pattern.compile("^(?:GROSS|YUCK)! .*Pests? (?:has appeared|have spawned) in (.+)!$");

	private static final Pattern TROPHY = Pattern.compile("^TROPHY FISH! You caught an? (.+) (BRONZE|SILVER|GOLD|DIAMOND)!$");
	private static final Pattern POWDER = Pattern.compile("^(Mithril|Gemstone|Glacite) Powder x([\\d,]+)$");

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
		if (s.startsWith("[Sacks] ")) return Optional.of(Hide.SACKS);
		if (s.length() >= 20 && s.chars().allMatch(c -> c == '▬' || c == '-' || c == '=' || c == '—')) return Optional.of(Hide.SEPARATOR);
		if (any(MINING_CHEST, s)) return Optional.of(Hide.MINING_CHEST);
		// lines with "name: text" are player chat and are never treated as dungeon noise
		if (!s.contains(": ") && any(DUNGEON, s)) return Optional.of(Hide.DUNGEON);
		if (s.equals("Welcome to Hypixel SkyBlock!")) return Optional.of(Hide.WELCOME);
		if (any(USELESS_DROP, s)) return Optional.of(Hide.USELESS_DROP);
		if (any(NOTICE, s)) return Optional.of(Hide.NOTICE);
		if (any(WARNING, s)) return Optional.of(Hide.WARNING);
		if (any(SKYMALL, s)) return Optional.of(Hide.SKYMALL);
		if (any(FIRE_SALE, s)) return Optional.of(Hide.FIRE_SALE);
		if (any(ACHIEVEMENT, s)) return Optional.of(Hide.ACHIEVEMENT);
		if (any(PARKOUR, s)) return Optional.of(Hide.PARKOUR);
		if (any(TELEPORT_PAD, s)) return Optional.of(Hide.TELEPORT_PAD);
		if (any(SOLO_CLASS, s)) return Optional.of(Hide.SOLO_CLASS);
		if (any(FAIRY, s)) return Optional.of(Hide.FAIRY);
		return Optional.empty();
	}

	/** Where pests appeared ("Plot - S 4", "The Barn", "Plots 12, 9 and 3"). */
	public static Optional<String> pest(String plain) {
		Matcher m = PEST.matcher(plain.strip());
		if (m.matches()) return Optional.of(m.group(1).strip());
		String s = plain.strip();
		int i = s.indexOf(" Pest spawned in ");
		if ((s.startsWith("GROSS! While you were offline,") || s.startsWith("YUCK! While you were offline,")) && i > 0 && s.endsWith("!")) {
			return Optional.of(s.substring(i + " Pest spawned in ".length(), s.length() - 1));
		}
		return Optional.empty();
	}

	/** "GOOD CATCH! You found a Fish Bait." → "GOOD CATCH". */
	public static Optional<String> treasureCatch(String plain) {
		String s = plain.strip();
		if (s.startsWith("GOOD CATCH! ")) return Optional.of("GOOD CATCH");
		if (s.startsWith("GREAT CATCH! ")) return Optional.of("GREAT CATCH");
		return Optional.empty();
	}

	/** "TROPHY FISH! You caught a Lavahorse GOLD!" → (Lavahorse, GOLD). */
	public static Optional<Trophy> trophy(String plain) {
		Matcher m = TROPHY.matcher(plain.strip());
		return m.matches() ? Optional.of(new Trophy(m.group(1), m.group(2))) : Optional.empty();
	}

	/** Name of the boss in a "[BOSS] Name: text" line. */
	public static Optional<String> boss(String plain) {
		String s = plain.strip();
		if (!s.startsWith("[BOSS] ")) return Optional.empty();
		int colon = s.indexOf(':');
		return Optional.of((colon > 7 ? s.substring(7, colon) : s.substring(7)).strip());
	}

	public static boolean chestOpened(String plain) {
		return plain.strip().equals("You have successfully picked the lock on this chest!");
	}

	/** A treasure chest reward line: "Gemstone Powder x537". */
	public static Optional<Powder> chestPowder(String plain) {
		Matcher m = POWDER.matcher(plain.strip());
		if (!m.matches()) return Optional.empty();
		try {
			return Optional.of(new Powder(m.group(1), Integer.parseInt(m.group(2).replace(",", ""))));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	/** The first of {@code keywords} found in the line as a word (case-insensitive), if any. */
	public static Optional<String> keyword(String plain, List<String> keywords) {
		for (String k : keywords) {
			if (k == null || k.isBlank()) continue;
			Pattern p = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(k.strip()) + "(?![A-Za-z0-9_])");
			if (p.matcher(plain).find()) return Optional.of(k.strip());
		}
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
