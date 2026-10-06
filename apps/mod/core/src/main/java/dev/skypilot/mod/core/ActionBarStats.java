package dev.skypilot.mod.core;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Health / defense / mana, Rift time and the latest skill XP gain from the SkyBlock action bar.
 * Hypixel now draws the stat icons with private-use glyphs (health U+E010, defense U+E008,
 * intelligence U+E003, Rift time U+E020 - SkyHanni ActionBarStatsData / SkyblockStat, Oct 2026);
 * the older ❤ / ❈ Defense / ✎ symbols are still accepted.
 */
public final class ActionBarStats {
	public record Stats(Integer health, Integer maxHealth, Integer defense, Integer mana, Integer maxMana) {
		public boolean any() {
			return health != null || defense != null || mana != null;
		}
	}

	/** {@code current}/{@code needed} XP toward the next level; percent-only bars have no numbers. */
	public record SkillGain(String skill, double gained, Double current, Double needed, Double percent) {
	}

	private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)(?:❤|\uE010)");
	private static final Pattern DEFENSE = Pattern.compile("([\\d,]+)(?:❈ Defense|\uE008)");
	private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)(?:✎|\uE003)");
	private static final Pattern RIFT_TIME = Pattern.compile("((?:\\d+m ?)?(?:\\d+s)?) ?(?:ф|\uE020)");
	private static final Pattern SKILL = Pattern.compile("\\+([\\d,]+(?:\\.\\d+)?) ([A-Z][a-z]+) \\((?:([\\d,]+(?:\\.\\d+)?)/([\\d,]+(?:\\.\\d+)?[kKmM]?)|([\\d.]+)%)\\)");

	private ActionBarStats() {
	}

	public static Stats stats(String actionBar) {
		String s = Text.strip(actionBar);
		Integer hp = null, max = null, def = null, mana = null, maxMana = null;
		Matcher m = HEALTH.matcher(s);
		if (m.find()) {
			hp = num(m.group(1));
			max = num(m.group(2));
		}
		m = DEFENSE.matcher(s);
		if (m.find()) def = num(m.group(1));
		m = MANA.matcher(s);
		if (m.find()) {
			mana = num(m.group(1));
			maxMana = num(m.group(2));
		}
		return new Stats(hp, max, def, mana, maxMana);
	}

	/** Rift time left in seconds ("7m 30s" before the Rift time icon), if shown. */
	public static Optional<Integer> riftSeconds(String actionBar) {
		Matcher m = RIFT_TIME.matcher(Text.strip(actionBar));
		while (m.find()) {
			String t = m.group(1).strip();
			if (t.isEmpty()) continue;
			int secs = 0;
			Matcher p = Pattern.compile("(\\d+)([ms])").matcher(t);
			boolean any = false;
			while (p.find()) {
				any = true;
				secs += Integer.parseInt(p.group(1)) * (p.group(2).equals("m") ? 60 : 1);
			}
			if (any) return Optional.of(secs);
		}
		return Optional.empty();
	}

	public static Optional<SkillGain> skill(String actionBar) {
		Matcher m = SKILL.matcher(Text.strip(actionBar));
		if (!m.find()) return Optional.empty();
		try {
			double gained = Double.parseDouble(m.group(1).replace(",", ""));
			if (m.group(5) != null) return Optional.of(new SkillGain(m.group(2), gained, null, null, Double.parseDouble(m.group(5))));
			double cur = Double.parseDouble(m.group(3).replace(",", ""));
			double need = suffixed(m.group(4));
			return Optional.of(new SkillGain(m.group(2), gained, cur, need, need > 0 ? cur * 100 / need : null));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static double suffixed(String s) {
		String t = s.replace(",", "");
		char last = Character.toLowerCase(t.charAt(t.length() - 1));
		double mult = last == 'k' ? 1e3 : last == 'm' ? 1e6 : 1;
		return Double.parseDouble(mult == 1 ? t : t.substring(0, t.length() - 1)) * mult;
	}

	private static Integer num(String s) {
		try {
			return Integer.parseInt(s.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
