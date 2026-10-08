package dev.skypilot.mod.core;

import java.util.regex.Pattern;

/**
 * The Minecraft-independent parts of the performance features: which armor stands are damage
 * numbers, the frame-rate cap, and the per-tick particle budget.
 */
public final class PerfRules {
	/**
	 * A damage number's name once colour codes and thousands separators are removed: digits with
	 * optional marks, e.g. "✧1234✧", "1234❤" (format as matched by SkyHanni's damage indicator,
	 * checked 2026-10-06).
	 */
	private static final Pattern DAMAGE = Pattern.compile("[✧✯]?\\d+[⚔+✧❤♞☄✷ﬗ✯]*");

	private PerfRules() {
	}

	/** One adjustment per second, with a dead band to prevent oscillation. */
	public static int adaptiveBudget(int current, int maximum, int fps, int target) {
		maximum = Math.max(20, maximum);
		current = Math.max(20, Math.min(maximum, current));
		if (fps <= 0 || target <= 0) return current;
		if (fps < target * .85) return Math.max(20, current - Math.max(5, current / 8));
		if (fps > target * 1.1) return Math.min(maximum, current + Math.max(5, maximum / 20));
		return current;
	}

	/** Damage numbers are short-lived armor stands, so old ones (over 15 s) are never matched. */
	public static boolean isDamageSplash(String plainName, int ageTicks) {
		if (plainName == null || plainName.isEmpty() || ageTicks > 300) return false;
		return DAMAGE.matcher(Text.strip(plainName).replace(",", "")).matches();
	}

	/**
	 * The frame-rate limit with SkyPilot's caps applied on top of Minecraft's own limit (which
	 * already lowers it when minimised or idle).
	 */
	public static int fpsLimit(int vanilla, boolean focused, boolean menuOpenInWorld, int backgroundCap, int menuCap) {
		int limit = vanilla;
		if (backgroundCap > 0 && !focused) limit = Math.min(limit, backgroundCap);
		if (menuCap > 0 && menuOpenInWorld) limit = Math.min(limit, menuCap);
		return Math.max(1, limit);
	}

	/** At most {@code max} new particles per tick. */
	public static final class Budget {
		private int used;

		public void reset() {
			used = 0;
		}

		public boolean take(int max) {
			if (used >= max) return false;
			used++;
			return true;
		}

		public int used() {
			return used;
		}
	}

	/**
	 * Particles alive, from Minecraft's debug text "&lt;group&gt; &lt;count&gt; … T &lt;emitters&gt;"
	 * ({@code ParticleEngine.countParticles} in 26.2); the "T" (tracking emitters) number is not a particle.
	 */
	public static int particleTotal(String debug) {
		if (debug == null) return 0;
		String[] t = debug.strip().split("\\s+");
		int total = 0;
		for (int i = 0; i + 1 < t.length; i++) {
			if (t[i].equals("T") || !t[i + 1].matches("\\d{1,9}")) continue;
			total += Integer.parseInt(t[i + 1]);
			i++;
		}
		return total;
	}

	/** "RAM 1.8 / 4.0 GB (45%)". */
	public static String memory(long usedBytes, long maxBytes) {
		double gb = 1024.0 * 1024 * 1024;
		int pct = maxBytes <= 0 ? 0 : (int) Math.round(usedBytes * 100.0 / maxBytes);
		return String.format(java.util.Locale.ROOT, "RAM %.1f / %.1f GB (%d%%)", usedBytes / gb, maxBytes / gb, pct);
	}
}
