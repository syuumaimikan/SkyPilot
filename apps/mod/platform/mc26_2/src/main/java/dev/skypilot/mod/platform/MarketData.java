package dev.skypilot.mod.platform;

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
	private static final long REFRESH_MS = 30_000;
	private static final Map<String, Dto.MarketPanel> PANELS = new HashMap<>();
	private static final Map<String, Long> FETCHED = new HashMap<>();
	private static final Map<String, Long> REQUESTED = new HashMap<>();
	private static final Map<String, String> ERRORS = new HashMap<>();

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
		long last = Math.max(FETCHED.getOrDefault(kind, 0L), REQUESTED.getOrDefault(kind, 0L));
		if (!force && now - last < REFRESH_MS) return;
		REQUESTED.put(kind, now);
		if (force) api.clearCache();
		api.market(kind, 30, SkyPilot.get().lang().code()).whenComplete((panel, err) -> Minecraft.getInstance().execute(() -> {
			if (panel != null) {
				set(kind, panel);
			} else {
				ERRORS.put(kind, SkyPilot.shortError(err));
			}
		}));
	}

	static void set(String kind, Dto.MarketPanel panel) {
		PANELS.put(kind, panel);
		FETCHED.put(kind, System.currentTimeMillis());
		ERRORS.remove(kind);
	}
}
