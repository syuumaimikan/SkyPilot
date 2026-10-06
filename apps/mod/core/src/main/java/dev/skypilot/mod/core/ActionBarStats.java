package dev.skypilot.mod.core;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Health / defense / mana and the latest skill XP gain from the SkyBlock action bar, e.g.
 * {@code "1500/1500❤     300❈ Defense     500/500✎ Mana"} and
 * {@code "+12.5 Farming (12,345/50,000)"}.
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

	private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");
	private static final Pattern DEFENSE = Pattern.compile("([\\d,]+)❈ Defense");
	private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)✎");
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
