package dev.skypilot.mod.session;

import com.google.gson.JsonObject;
import dev.skypilot.mod.core.SkyBlockContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-session statistics from information the game already shows the player (sidebar purse,
 * action-bar XP gains, current area). It only observes; it never sends input to the game.
 */
public final class SessionTracker {
	/** Gaps longer than this (AFK, menus, loading) are not counted as active time. */
	private static final long MAX_GAP_MS = 60_000;
	/** Ignore purse jumps larger than this between two observations (bank transfers, trades). */
	private static final long MAX_PURSE_STEP = 50_000_000;

	public record Snapshot(long startedAtMs, long activeMs, long purseEarned, double coinsPerHour, Map<String, Double> xp, Map<String, Double> xpPerHour,
			Map<String, Long> areaMs) {
	}

	private long startedAt = -1;
	private long lastObserved = -1;
	private long activeMs;
	private Long lastPurse;
	private long purseEarned;
	private String area = "";
	private final Map<String, Double> xp = new TreeMap<>();
	private final Map<String, Long> areaMs = new LinkedHashMap<>();

	public synchronized void reset() {
		startedAt = -1;
		lastObserved = -1;
		activeMs = 0;
		lastPurse = null;
		purseEarned = 0;
		area = "";
		xp.clear();
		areaMs.clear();
	}

	public synchronized boolean started() {
		return startedAt >= 0;
	}

	/** Called periodically (about once per second) with the parsed sidebar context. */
	public synchronized void observe(SkyBlockContext ctx, long now) {
		if (!ctx.inSkyBlock()) {
			lastObserved = -1;
			return;
		}
		if (startedAt < 0) startedAt = now;
		if (lastObserved >= 0) {
			long gap = now - lastObserved;
			if (gap > 0 && gap <= MAX_GAP_MS) {
				activeMs += gap;
				if (!area.isEmpty()) areaMs.merge(area, gap, Long::sum);
			}
		}
		lastObserved = now;
		area = ctx.area();
		if (ctx.purse().isPresent()) {
			long p = ctx.purse().getAsLong();
			if (lastPurse != null) {
				long d = p - lastPurse;
				if (Math.abs(d) <= MAX_PURSE_STEP) purseEarned += d;
			}
			lastPurse = p;
		}
	}

	public synchronized void onActionBar(String text) {
		if (startedAt < 0) return;
		ActionBarParser.parse(text).ifPresent(g -> xp.merge(g.skill(), g.amount(), Double::sum));
	}

	public synchronized Snapshot snapshot() {
		double hours = activeMs / 3_600_000.0;
		Map<String, Double> perHour = new TreeMap<>();
		if (hours > 0) xp.forEach((k, v) -> perHour.put(k, v / hours));
		return new Snapshot(startedAt, activeMs, purseEarned, hours > 0 ? purseEarned / hours : 0, Collections.unmodifiableMap(new TreeMap<>(xp)),
				Collections.unmodifiableMap(perHour), Collections.unmodifiableMap(new LinkedHashMap<>(areaMs)));
	}

	/** JSON body for {@code POST /api/mod/session} (only when the player enabled uploads). */
	public static JsonObject toJson(Snapshot s, long endedAtMs) {
		JsonObject o = new JsonObject();
		o.addProperty("startedAtMs", s.startedAtMs());
		o.addProperty("endedAtMs", endedAtMs);
		o.addProperty("activeMs", s.activeMs());
		o.addProperty("purseEarned", s.purseEarned());
		o.addProperty("coinsPerHour", Math.round(s.coinsPerHour()));
		JsonObject xp = new JsonObject();
		s.xp().forEach((k, v) -> xp.addProperty(k, Math.round(v * 10) / 10.0));
		o.add("xp", xp);
		JsonObject areas = new JsonObject();
		s.areaMs().entrySet().stream().limit(30).forEach(e -> areas.addProperty(e.getKey(), e.getValue()));
		o.add("areaMs", areas);
		return o;
	}
}
