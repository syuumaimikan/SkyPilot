package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Latest AH / Bazaar flip lists from the SkyPilot server, shared by the menu and the panel
 * beside Hypixel's AH/Bazaar windows. Market data comes from the SkyPilot server only (it
 * collects it from Hypixel's public API); the mod never contacts Hypixel.
 */
final class MarketData {
	static final String AH = "ah";
	static final String BAZAAR = "bazaar";
	static final String CRAFT = "craft";
	private static final long REFRESH_MS = 30_000;
	private static final Map<String, Dto.MarketPanel> PANELS = new HashMap<>();
	private static final Map<String, Long> FETCHED = new HashMap<>();
	private static final Map<String, Long> REQUESTED = new HashMap<>();
	private static final Map<String, String> ERRORS = new HashMap<>();
	/** Sort / budget / Bazaar Flipper level each list was fetched with. */
	private static final Map<String, String> OPTIONS = new HashMap<>();

	private MarketData() {
	}

	static Dto.MarketPanel get(String kind) {
		return PANELS.get(kind);
	}

	static String error(String kind) {
		return ERRORS.get(kind);
	}

	/** Seconds since the panel was received by the mod (not the market data age). */
	static long receivedSecondsAgo(String kind) {
		Long at = FETCHED.get(kind);
		return at == null ? -1 : (System.currentTimeMillis() - at) / 1000;
	}

	/** Fetch when missing or older than 30 s (or when {@code force}); game thread. */
	static void refresh(String kind, boolean force) {
		long now = System.currentTimeMillis();
		ApiClient api = SkyPilot.get().api();
		if (api == null) {
			ERRORS.put(kind, SkyPilot.get().apiError());
			return;
		}
		SkyPilotConfig.Market m = SkyPilot.get().config().market;
		long budget = m.affordableOnly ? budget() : 0;
		String opts = options();
		long last = Math.max(FETCHED.getOrDefault(kind, 0L), REQUESTED.getOrDefault(kind, 0L));
		if (!force && now - last < REFRESH_MS && opts.equals(OPTIONS.get(kind))) return;
		REQUESTED.put(kind, now);
		OPTIONS.put(kind, opts);
		if (force) api.clearCache();
		api.market(kind, 30, SkyPilot.get().lang().code(), m.flipSort, budget, m.bazaarFlipper).whenComplete((panel, err) -> Minecraft.getInstance().execute(() -> {
			if (!opts.equals(OPTIONS.get(kind))) return;
			if (panel != null) {
				set(kind, panel);
			} else {
				ERRORS.put(kind, SkyPilot.shortError(err));
			}
		}));
	}

	/** Purse from the sidebar, rounded down to 3 significant digits (so small changes don't refetch); 0 when unknown. */
	static long budget() {
		java.util.OptionalLong purse = SkyPilot.get().context().purse();
		if (purse.isEmpty() || purse.getAsLong() <= 0) return 0;
		long p = purse.getAsLong();
		long unit = (long) Math.pow(10, Math.max(0, (int) Math.log10(p) - 2));
		return p / unit * unit;
	}

	/** The sort / budget / Bazaar Flipper settings a list is fetched with. */
	private static String options() {
		SkyPilotConfig.Market m = SkyPilot.get().config().market;
		return m.flipSort + "|" + (m.affordableOnly ? budget() : 0) + "|" + m.bazaarFlipper;
	}

	static void set(String kind, Dto.MarketPanel panel) {
		OPTIONS.put(kind, options());
		PANELS.put(kind, panel);
		FETCHED.put(kind, System.currentTimeMillis());
		ERRORS.remove(kind);
	}
}
