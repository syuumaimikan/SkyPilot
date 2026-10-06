package dev.skypilot.mod.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "1h30m", "90s", "2d", "45" (minutes) → milliseconds; at most 7 days. */
public final class Durations {
	private static final Pattern PART = Pattern.compile("(\\d{1,5})([dhms]?)");
	public static final long MAX_MS = 7L * 24 * 3600_000;

	private Durations() {
	}

	public static long parse(String text) {
		String s = text.strip().toLowerCase(Locale.ROOT).replace(" ", "");
		if (s.isEmpty() || s.length() > 20) throw new IllegalArgumentException("duration expected, e.g. 30m or 1h30m");
		Matcher m = PART.matcher(s);
		int at = 0;
		long total = 0;
		while (at < s.length()) {
			if (!m.find(at) || m.start() != at) throw new IllegalArgumentException("duration expected, e.g. 30m or 1h30m");
			long n = Long.parseLong(m.group(1));
			total += n * switch (m.group(2)) {
				case "d" -> 24 * 3600_000L;
				case "h" -> 3600_000L;
				case "s" -> 1000L;
				default -> 60_000L;
			};
			at = m.end();
		}
		if (total <= 0 || total > MAX_MS) throw new IllegalArgumentException("between 1 second and 7 days");
		return total;
	}
}
