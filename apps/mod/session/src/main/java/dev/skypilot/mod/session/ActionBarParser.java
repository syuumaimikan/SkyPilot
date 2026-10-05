package dev.skypilot.mod.session;

import dev.skypilot.mod.core.Text;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads skill XP gains from the action bar, e.g. {@code "+12.5 Mining (1,234/5,000)"}. */
public final class ActionBarParser {
	public record XpGain(String skill, double amount) {
	}

	private static final Pattern GAIN = Pattern.compile("\\+([\\d,]+(?:\\.\\d+)?) (Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social|Hunting) \\(");

	private ActionBarParser() {
	}

	public static Optional<XpGain> parse(String actionBar) {
		Matcher m = GAIN.matcher(Text.strip(actionBar));
		if (!m.find()) return Optional.empty();
		try {
			double v = Double.parseDouble(m.group(1).replace(",", ""));
			return v > 0 && v < 1e7 ? Optional.of(new XpGain(m.group(2), v)) : Optional.empty();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
