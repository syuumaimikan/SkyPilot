package dev.skypilot.mod.platform;

import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit value of an item by its display name, for menus whose items carry no SkyBlock id
 * (Garden visitor offers, sacks): an exact name match in the SkyPilot item list, cached for
 * 10 minutes and shared by all windows.
 */
final class NamePrices {
	record Price(Double value, long at) {
	}

	private static final Map<String, Price> PRICES = new ConcurrentHashMap<>();
	private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();

	private NamePrices() {
	}

	/** Cached price (value null = not found); null while the first lookup is running. */
	static Price get(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Price p = PRICES.get(key);
		long now = System.currentTimeMillis();
		if (p != null && now - p.at() < 600_000) return p;
		ApiClient api = SkyPilot.get().api();
		if (api != null && PENDING.size() < 40 && PENDING.putIfAbsent(key, now) == null) {
			api.items(name, 0, 8).whenComplete((page, err) -> {
				PENDING.remove(key);
				if (page == null) return;
				Double value = null;
				for (Dto.ItemEntry e : page.items()) {
					if (e.name() != null && e.name().equalsIgnoreCase(name)) {
						value = e.value();
						break;
					}
				}
				PRICES.put(key, new Price(value, System.currentTimeMillis()));
			});
		}
		return p;
	}

	/** For tests. */
	static void set(String name, Double value) {
		PRICES.put(name.toLowerCase(Locale.ROOT), new Price(value, System.currentTimeMillis()));
	}
}
