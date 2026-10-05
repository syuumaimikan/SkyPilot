package dev.skypilot.mod.core;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Plain-text helpers shared by every module. */
public final class Text {
	private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
	// Hypixel pads scoreboard lines with emoji/format-only team suffixes; keep printable text.
	private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\p{Co}\\x{10000}-\\x{10FFFF}]");

	private Text() {
	}

	/** Remove legacy formatting codes and invisible padding characters. */
	public static String strip(String s) {
		if (s == null) return "";
		return INVISIBLE.matcher(FORMATTING.matcher(s).replaceAll("")).replaceAll("").strip();
	}

	/** Parse "1,234,567" / "1.2k" style numbers that appear on the scoreboard. */
	public static OptionalLong parseLong(String s) {
		if (s == null) return OptionalLong.empty();
		String t = s.replace(",", "").strip();
		int end = 0;
		while (end < t.length() && (Character.isDigit(t.charAt(end)) || t.charAt(end) == '.')) end++;
		if (end == 0) return OptionalLong.empty();
		try {
			double v = Double.parseDouble(t.substring(0, end));
			char suffix = end < t.length() ? Character.toLowerCase(t.charAt(end)) : ' ';
			v *= switch (suffix) {
				case 'k' -> 1e3;
				case 'm' -> 1e6;
				case 'b' -> 1e9;
				default -> 1;
			};
			return OptionalLong.of(Math.round(v));
		} catch (NumberFormatException e) {
			return OptionalLong.empty();
		}
	}

	public static String coins(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
		double a = Math.abs(v);
		String sign = v < 0 ? "-" : "";
		if (a >= 1e9) return sign + String.format(Locale.ROOT, "%.2fB", a / 1e9);
		if (a >= 1e6) return sign + String.format(Locale.ROOT, "%.2fM", a / 1e6);
		if (a >= 1e3) return sign + String.format(Locale.ROOT, "%.1fk", a / 1e3);
		return sign + String.format(Locale.ROOT, "%.0f", a);
	}

	public static String duration(long millis) {
		long s = Math.max(0, millis / 1000);
		long h = s / 3600;
		long m = (s % 3600) / 60;
		return h > 0 ? h + "h " + m + "m" : m + "m " + (s % 60) + "s";
	}
}
