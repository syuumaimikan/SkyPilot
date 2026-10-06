package dev.skypilot.mod.core;

import java.util.List;

/**
 * SkyBlock calendar arithmetic. The constants (epoch, day length, month names) come from the
 * SkyPilot server's curated {@code data/registry/calendar.toml} (Hypixel SkyBlock Wiki, checked
 * against the live election endpoint); nothing is hardcoded in the mod.
 */
public record SkyBlockTime(long epochMs, long dayMs, int daysPerMonth, List<String> months) {
	public record Date(int year, int month, int day, int hour, int minute) {
	}

	public Date at(long nowMs) {
		long elapsed = Math.max(0, nowMs - epochMs);
		long dayIndex = elapsed / dayMs;
		long yearDays = (long) daysPerMonth * months.size();
		int year = (int) (dayIndex / yearDays) + 1;
		int dayOfYear = (int) (dayIndex % yearDays);
		long intoDay = elapsed % dayMs;
		int minutesOfDay = (int) (intoDay * 24 * 60 / dayMs);
		return new Date(year, dayOfYear / daysPerMonth + 1, dayOfYear % daysPerMonth + 1, minutesOfDay / 60, minutesOfDay % 60);
	}

	/** e.g. "Late Spring 14th, Year 412 · 6:40 pm" (minutes in steps of 10 like the game's clock). */
	public String format(long nowMs) {
		Date d = at(nowMs);
		String month = d.month() - 1 < months.size() ? months.get(d.month() - 1) : "?";
		int h12 = d.hour() % 12 == 0 ? 12 : d.hour() % 12;
		return month + " " + d.day() + ordinal(d.day()) + ", Year " + d.year() + " · " + h12 + ":" + String.format("%02d", d.minute() / 10 * 10) + (d.hour() < 12 ? " am" : " pm");
	}

	static String ordinal(int n) {
		if (n % 100 >= 11 && n % 100 <= 13) return "th";
		return switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	/**
	 * Next start of an event that repeats every {@code everyDays} SkyBlock days, starting on days
	 * where {@code dayIndex % everyDays == offsetDays} (e.g. the Dark Auction: every 3 days at
	 * midnight, offset 0). Returns the current start while it is still running for {@code durationMs}.
	 */
	public long nextRecurring(long nowMs, int everyDays, int offsetDays, long durationMs) {
		long period = everyDays * dayMs;
		long first = epochMs + offsetDays * dayMs;
		long k = Math.floorDiv(nowMs - first, period);
		long start = first + k * period;
		if (nowMs < start + Math.max(0, durationMs)) return start;
		return start + period;
	}
}
