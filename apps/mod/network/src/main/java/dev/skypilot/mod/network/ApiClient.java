package dev.skypilot.mod.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Client for the SkyPilot backend. Every call is asynchronous with a hard timeout, guarded by
 * a circuit breaker and (for reads) a short TTL cache, so it can never stall the render thread.
 *
 * <p>The mod only ever talks to the configured SkyPilot server. It never contacts Hypixel,
 * Mojang or Microsoft and never handles Minecraft/Microsoft credentials.
 */
public final class ApiClient {
	private static final Gson GSON = new Gson();
	private static final Duration TIMEOUT = Duration.ofSeconds(6);

	private final Transport transport;
	private final URI base;
	private final Supplier<String> token;
	private final LongSupplier clock;
	private final CircuitBreaker breaker;
	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	private record CacheEntry(long expiresAt, Object value) {
	}

	public ApiClient(Transport transport, String baseUrl, Supplier<String> token, LongSupplier clock) {
		this.transport = transport;
		this.base = validateBaseUrl(baseUrl);
		this.token = token;
		this.clock = clock;
		this.breaker = new CircuitBreaker(4, 30_000, clock);
	}

	/** https everywhere; plain http only for a server on this machine. */
	public static URI validateBaseUrl(String url) {
		URI u;
		try {
			u = new URI(url.strip().replaceAll("/+$", ""));
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("invalid SkyPilot server URL: " + url);
		}
		String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
		String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
		boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
		if (!(scheme.equals("https") || (scheme.equals("http") && local))) {
			throw new IllegalArgumentException("SkyPilot server URL must use https (http is only allowed for localhost)");
		}
		if (u.getUserInfo() != null || u.getQuery() != null || u.getFragment() != null) {
			throw new IllegalArgumentException("SkyPilot server URL must not contain credentials, a query or a fragment");
		}
		return u;
	}

	public URI base() {
		return base;
	}

	public CircuitBreaker.State circuit() {
		return breaker.state();
	}

	public void clearCache() {
		cache.clear();
	}

	public static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- endpoints

	public CompletableFuture<Dto.HudState> hud(String area, String lang) {
		String q = "?lang=" + enc(lang) + (area == null || area.isEmpty() ? "" : "&area=" + enc(area));
		return getCached("/api/mod/hud" + q, Dto.HudState.class, 20_000);
	}

	public CompletableFuture<Dto.TextResponse> text(String kind, String lang) {
		return getCached("/api/mod/text/" + enc(kind) + "?lang=" + enc(lang), Dto.TextResponse.class, 15_000);
	}

	/** Price for a SkyBlock item; {@code customData} is the stack's custom_data as JSON (may be null). */
	public CompletableFuture<Dto.PriceResponse> price(String skyblockId, JsonElement customData) {
		JsonObject body = new JsonObject();
		body.addProperty("skyblockId", skyblockId);
		if (customData != null) body.add("customData", customData);
		String json = GSON.toJson(body);
		String key = "price:" + Integer.toHexString(json.hashCode()) + ":" + skyblockId;
		CacheEntry hit = cache.get(key);
		if (hit != null && hit.expiresAt > clock.getAsLong()) return CompletableFuture.completedFuture((Dto.PriceResponse) hit.value);
		return request("POST", "/api/mod/price", json, false).thenApply(r -> {
			Dto.PriceResponse p = parse(r, Dto.PriceResponse.class);
			cache.put(key, new CacheEntry(clock.getAsLong() + 60_000, p));
			return p;
		});
	}

	public CompletableFuture<Void> uploadSession(JsonObject stats) {
		return request("POST", "/api/mod/session", GSON.toJson(stats), true).thenApply(r -> null);
	}

	public CompletableFuture<Dto.TranslateResponse> translate(String text, String sourceLang, String targetLang, String channel) {
		JsonObject body = new JsonObject();
		body.addProperty("text", text);
		body.addProperty("sourceLang", sourceLang);
		body.addProperty("targetLang", targetLang);
		if (channel != null) body.addProperty("channel", channel);
		return request("POST", "/api/translate", GSON.toJson(body), true).thenApply(r -> parse(r, Dto.TranslateResponse.class));
	}

	public CompletableFuture<Dto.TranslationDictionary> dictionary() {
		return getCached("/api/translate/dictionary", Dto.TranslationDictionary.class, 6 * 3600_000L);
	}

	public CompletableFuture<Dto.DeviceCodeResponse> deviceCode(String deviceName) {
		JsonObject body = new JsonObject();
		body.addProperty("deviceName", deviceName);
		return request("POST", "/api/device/code", GSON.toJson(body), false).thenApply(r -> parse(r, Dto.DeviceCodeResponse.class));
	}

	public CompletableFuture<Dto.DeviceTokenResponse> deviceToken(String deviceCode) {
		JsonObject body = new JsonObject();
		body.addProperty("deviceCode", deviceCode);
		return request("POST", "/api/device/token", GSON.toJson(body), false).thenApply(r -> parse(r, Dto.DeviceTokenResponse.class));
	}

	/** AH ({@code "ah"}) or Bazaar ({@code "bazaar"}) flips - public market data, no account needed. */
	public CompletableFuture<Dto.MarketPanel> market(String kind, int limit, String lang) {
		return getPublic("/api/mod/market?kind=" + enc(kind) + "&limit=" + limit + "&lang=" + enc(lang), Dto.MarketPanel.class, 20_000);
	}

	public CompletableFuture<Dto.Plan> plan(String lang) {
		return getCached("/api/mod/plan?lang=" + enc(lang), Dto.Plan.class, 30_000);
	}

	/** Advisor report, e.g. {@code mining}, {@code slayer}, {@code farming} (same as the web). */
	public CompletableFuture<Dto.Report> report(String domain, String lang) {
		return getCached("/api/advisor/report/" + enc(domain) + "?lang=" + enc(lang), Dto.Report.class, 60_000);
	}

	/** New flips matching the player's alert rules since {@code sinceMs} (not cached). */
	public CompletableFuture<Dto.Alerts> alerts(long sinceMs, String lang) {
		return request("GET", "/api/mod/alerts?since=" + sinceMs + "&lang=" + enc(lang), null, true).thenApply(r -> parse(r, Dto.Alerts.class));
	}

	/** Icons for up to 64 item ids. */
	public CompletableFuture<Map<String, Dto.ItemIcon>> icons(java.util.Collection<String> ids) {
		String joined = String.join(",", ids);
		return request("GET", "/api/mod/icons?ids=" + enc(joined), null, false).thenApply(r -> {
			try {
				Map<String, Dto.ItemIcon> out = new HashMap<>();
				JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
				for (Map.Entry<String, JsonElement> e : o.entrySet()) out.put(e.getKey(), GSON.fromJson(e.getValue(), Dto.ItemIcon.class));
				return out;
			} catch (RuntimeException e) {
				throw new ApiException(r.status(), "DECODE", "unexpected response from SkyPilot server");
			}
		});
	}

	/** Item list for the browser: everything by name ({@code query} empty) or search results. */
	public CompletableFuture<Dto.ItemPage> items(String query, int offset, int limit) {
		return getPublic("/api/mod/items?q=" + enc(query) + "&offset=" + offset + "&limit=" + limit, Dto.ItemPage.class, 120_000);
	}

	/** Recipes, drop sources, uses, value and description of one item. */
	public CompletableFuture<Dto.ItemDetail> item(String id) {
		return getPublic("/api/mod/item/" + enc(id), Dto.ItemDetail.class, 60_000);
	}

	/** Values of item stacks: each element is {@code {customData, count}}. */
	public CompletableFuture<Dto.Values> values(com.google.gson.JsonArray items) {
		JsonObject body = new JsonObject();
		body.add("items", items);
		String json = GSON.toJson(body);
		String key = "values:" + Integer.toHexString(json.hashCode()) + ":" + json.length();
		CacheEntry hit = cache.get(key);
		if (hit != null && hit.expiresAt > clock.getAsLong()) return CompletableFuture.completedFuture((Dto.Values) hit.value);
		return request("POST", "/api/mod/values", json, false).thenApply(r -> {
			Dto.Values v = parse(r, Dto.Values.class);
			cache.put(key, new CacheEntry(clock.getAsLong() + 30_000, v));
			return v;
		});
	}

	/** The linked account's server-side settings (translation provider; keys are never returned). */
	public CompletableFuture<Dto.UserSettings> userSettings() {
		return request("GET", "/api/me/settings", null, true).thenApply(r -> parse(r, Dto.UserSettings.class));
	}

	/**
	 * Save the translation provider. A key argument of null keeps the stored key, "" deletes it.
	 * Keys go only to the player's own SkyPilot server, which stores them encrypted.
	 */
	public CompletableFuture<Dto.UserSettings> saveTranslationSettings(String provider, String deeplKey, String anthropicKey) {
		JsonObject body = new JsonObject();
		body.addProperty("provider", provider);
		if (deeplKey != null) body.addProperty("deeplKey", deeplKey);
		if (anthropicKey != null) body.addProperty("anthropicKey", anthropicKey);
		return request("PUT", "/api/me/settings/translation", GSON.toJson(body), true).thenApply(r -> parse(r, Dto.UserSettings.class));
	}

	public CompletableFuture<Dto.TranslationTest> testTranslation() {
		return request("POST", "/api/me/settings/translation/test", "{}", true).thenApply(r -> parse(r, Dto.TranslationTest.class));
	}

	/** Class / floor / slayer fit and the path to each goal (needs a linked account and an analysis). */
	public CompletableFuture<Dto.Fit> fit(String lang) {
		return getCached("/api/mod/fit?lang=" + enc(lang), Dto.Fit.class, 60_000);
	}

	/** Tiered loadouts with what the player owns and the cost to complete each tier. */
	public CompletableFuture<Dto.Builds> builds(String lang) {
		return getCached("/api/mod/builds?lang=" + enc(lang), Dto.Builds.class, 60_000);
	}

	/** Money-making methods ranked for the player, with where to do them. */
	public CompletableFuture<Dto.Money> money(String lang) {
		return getCached("/api/mod/money?lang=" + enc(lang), Dto.Money.class, 60_000);
	}

	/** Dungeon puzzle rules (public, cached 1 hour). */
	public CompletableFuture<Dto.Puzzle[]> puzzles(String lang) {
		return getPublic("/api/mod/puzzles?lang=" + enc(lang), Dto.Puzzle[].class, 3_600_000);
	}

	/** Sea creatures and their chat lines (public data, cached 1 hour). */
	public CompletableFuture<Dto.SeaCreatures> seaCreatures() {
		return getPublic("/api/mod/sea-creatures", Dto.SeaCreatures.class, 3_600_000);
	}

	/** SkyBlock calendar for the clock and event timers (public data, cached 10 minutes). */
	public CompletableFuture<Dto.Calendar> calendar() {
		return getPublic("/api/mod/calendar", Dto.Calendar.class, 600_000);
	}

	/** Progression guides with waypoints; marks steps done when this client is linked. */
	public CompletableFuture<Dto.Guides> guides(String lang) {
		String path = "/api/mod/guides?lang=" + enc(lang);
		String t = token.get();
		boolean auth = t != null && !t.isEmpty();
		return getCachedAuth(path, Dto.Guides.class, 60_000, auth);
	}

	/** Recommended gear for the goals and per activity (needs a linked account and an analysis). */
	public CompletableFuture<Dto.Gear> gear(String lang) {
		return getCached("/api/mod/gear?lang=" + enc(lang), Dto.Gear.class, 60_000);
	}

	public CompletableFuture<Dto.FarmSummary[]> farms() {
		return getPublic("/api/farms", Dto.FarmSummary[].class, 3_600_000);
	}

	public CompletableFuture<Dto.FarmDesign> farm(String id, String lang) {
		return getPublic("/api/farms/" + enc(id) + "?lang=" + enc(lang), Dto.FarmDesign.class, 3_600_000);
	}

	public CompletableFuture<Dto.Waypoint[]> locations(String island, String query) {
		return getPublic("/api/mod/locations?island=" + enc(island) + "&q=" + enc(query), Dto.Waypoint[].class, 300_000);
	}

	// ---------------------------------------------------------------- plumbing

	private <T> CompletableFuture<T> getCachedAuth(String path, Class<T> type, long ttlMillis, boolean authenticated) {
		long now = clock.getAsLong();
		String key = (authenticated ? "auth:" : "anon:") + path;
		CacheEntry hit = cache.get(key);
		if (hit != null && hit.expiresAt > now) return CompletableFuture.completedFuture(type.cast(hit.value));
		return request("GET", path, null, authenticated).thenApply(r -> {
			T v = parse(r, type);
			cache.put(key, new CacheEntry(clock.getAsLong() + ttlMillis, v));
			return v;
		});
	}

	private <T> CompletableFuture<T> getPublic(String path, Class<T> type, long ttlMillis) {
		long now = clock.getAsLong();
		CacheEntry hit = cache.get(path);
		if (hit != null && hit.expiresAt > now) return CompletableFuture.completedFuture(type.cast(hit.value));
		return request("GET", path, null, false).thenApply(r -> {
			T v = parse(r, type);
			cache.put(path, new CacheEntry(clock.getAsLong() + ttlMillis, v));
			return v;
		});
	}

	private <T> CompletableFuture<T> getCached(String path, Class<T> type, long ttlMillis) {
		long now = clock.getAsLong();
		CacheEntry hit = cache.get(path);
		if (hit != null && hit.expiresAt > now) return CompletableFuture.completedFuture(type.cast(hit.value));
		return request("GET", path, null, true).thenApply(r -> {
			T v = parse(r, type);
			cache.put(path, new CacheEntry(clock.getAsLong() + ttlMillis, v));
			return v;
		});
	}

	private CompletableFuture<Transport.Response> request(String method, String path, String body, boolean authenticated) {
		if (!breaker.allow()) {
			return CompletableFuture.failedFuture(new ApiException(0, "CIRCUIT_OPEN", "SkyPilot server unavailable; retrying in " + (breaker.retryInMillis() / 1000 + 1) + "s"));
		}
		Map<String, String> headers = new HashMap<>();
		if (authenticated) {
			String t = token.get();
			if (t == null || t.isEmpty()) {
				return CompletableFuture.failedFuture(new ApiException(401, "NOT_LINKED", "Not linked - run /skypilot link"));
			}
			headers.put("Authorization", "Bearer " + t);
		}
		URI uri = base.resolve(base.getPath() + path);
		return transport.send(method, uri, headers, body, TIMEOUT).handle((r, err) -> {
			if (err != null) {
				breaker.failure();
				Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
				throw new ApiException(0, "NETWORK", "SkyPilot server unreachable (" + cause.getClass().getSimpleName() + ")");
			}
			if (r.status() >= 500 || r.status() == 429) {
				breaker.failure();
			} else {
				breaker.success();
			}
			if (r.status() >= 200 && r.status() < 300) return r;
			throw toError(r);
		});
	}

	private static ApiException toError(Transport.Response r) {
		String code = "HTTP_" + r.status();
		String message = "request failed (" + r.status() + ")";
		try {
			JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
			if (o.has("code")) code = o.get("code").getAsString();
			if (o.has("message")) message = o.get("message").getAsString();
		} catch (RuntimeException ignored) {
			// not a JSON error body
		}
		return new ApiException(r.status(), code, message);
	}

	private static <T> T parse(Transport.Response r, Class<T> type) {
		try {
			T v = GSON.fromJson(r.body(), type);
			if (v == null) throw new ApiException(r.status(), "DECODE", "empty response");
			return v;
		} catch (JsonParseException e) {
			throw new ApiException(r.status(), "DECODE", "unexpected response from SkyPilot server");
		}
	}
}
