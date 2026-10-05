package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ConfigStore;
import dev.skypilot.mod.core.ContextParser;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyBlockContext;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.ApiException;
import dev.skypilot.mod.network.DeviceFlow;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.network.HttpTransport;
import dev.skypilot.mod.network.TokenStore;
import dev.skypilot.mod.session.SessionTracker;
import dev.skypilot.mod.translation.TranslationMode;
import dev.skypilot.mod.translation.TranslationService;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Client-side state of the mod. All network work is asynchronous; results are applied on the
 * game thread through {@link Minecraft#execute}. Nothing here sends input to the game.
 */
public final class SkyPilot {
	public static final Logger LOG = LoggerFactory.getLogger("SkyPilot");
	private static SkyPilot instance;

	private final ConfigStore configStore;
	private final TokenStore tokens;
	private final HttpTransport transport;
	private final ScheduledExecutorService scheduler;
	private final SessionTracker session = new SessionTracker();
	private final TranslationService translator;
	private SkyPilotConfig config;
	private volatile ApiClient api;
	private volatile String apiError;
	private DeviceFlow deviceFlow;

	private volatile SkyBlockContext context = SkyBlockContext.NONE;
	private volatile Dto.HudState hudState;
	private volatile String hudError;
	private long lastHudRequest;
	private String lastHudArea = "";
	private long dictionaryLoadedAt;

	private SkyPilot(Path dir) {
		this.configStore = new ConfigStore(dir);
		this.config = configStore.load();
		this.tokens = new TokenStore(dir);
		this.transport = new HttpTransport("SkyPilot-Mod/" + version() + " (Minecraft 26.2)");
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "SkyPilot-Scheduler");
			t.setDaemon(true);
			return t;
		});
		rebuildApi();
		this.translator = new TranslationService((text, channel) -> {
			ApiClient a = api;
			if (a == null) return CompletableFuture.failedFuture(new IllegalStateException(apiError));
			return a.translate(text, "en", "ja", channel.name()).thenApply(r -> r.unchanged() ? null : r.translated());
		}, task -> Minecraft.getInstance().execute(task), System::currentTimeMillis);
	}

	static void init(Path dir) {
		instance = new SkyPilot(dir);
	}

	public static SkyPilot get() {
		return instance;
	}

	private static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("skypilot").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	// ---------------------------------------------------------------- accessors

	public SkyPilotConfig config() {
		return config;
	}

	public void saveConfig() {
		config.sanitize();
		try {
			configStore.save(config);
		} catch (IOException e) {
			LOG.warn("Could not save SkyPilot config: {}", e.getMessage());
		}
	}

	public Lang lang() {
		Minecraft mc = Minecraft.getInstance();
		return Lang.resolve(config.language, mc.options == null ? null : mc.options.languageCode);
	}

	public ApiClient api() {
		return api;
	}

	public String apiError() {
		return apiError;
	}

	public TokenStore tokens() {
		return tokens;
	}

	public boolean linked() {
		return tokens.linked();
	}

	public SessionTracker session() {
		return session;
	}

	public TranslationService translator() {
		return translator;
	}

	public TranslationMode translationMode() {
		return TranslationMode.valueOf(config.translation.mode.name());
	}

	public SkyBlockContext context() {
		return context;
	}

	public Dto.HudState hudState() {
		return hudState;
	}

	public String hudError() {
		return hudError;
	}

	public ScheduledExecutorService scheduler() {
		return scheduler;
	}

	public DeviceFlow deviceFlow() {
		return deviceFlow;
	}

	/** Re-create the API client after the server URL changed. */
	public void rebuildApi() {
		try {
			api = new ApiClient(transport, config.apiBaseUrl, tokens::get, System::currentTimeMillis);
			apiError = null;
			deviceFlow = new DeviceFlow(api, tokens, scheduler, System::currentTimeMillis);
		} catch (IllegalArgumentException e) {
			api = null;
			apiError = e.getMessage();
			deviceFlow = null;
		}
		hudState = null;
		lastHudRequest = 0;
	}

	public void unlink() {
		try {
			tokens.clear();
		} catch (IOException e) {
			LOG.warn("Could not delete SkyPilot token: {}", e.getMessage());
		}
		hudState = null;
		if (api != null) api.clearCache();
	}

	// ---------------------------------------------------------------- periodic work (game thread)

	/** Called once per second from the client tick. */
	void tickSecond(Minecraft mc) {
		long now = System.currentTimeMillis();
		SkyBlockContext ctx = mc.level == null ? SkyBlockContext.NONE : ContextParser.parse(Sidebar.title(mc), Sidebar.lines(mc), Sidebar.tabLines(mc));
		context = ctx;
		if (config.session.enabled) session.observe(ctx, now);
		refreshHud(now, false);
		if (linked() && now - dictionaryLoadedAt > 6 * 3600_000L) loadDictionary(now);
	}

	/** Fetch HUD state: every 60 s, or 5 s after the area changed. */
	public void refreshHud(long now, boolean force) {
		ApiClient a = api;
		if (a == null || !linked() || !config.hud.enabled) return;
		String area = context.hasArea() ? context.area() : "";
		boolean areaChanged = !area.equals(lastHudArea);
		long interval = areaChanged ? 5_000 : 60_000;
		if (!force && now - lastHudRequest < interval) return;
		lastHudRequest = now;
		lastHudArea = area;
		a.hud(area, lang().code()).whenComplete((s, err) -> Minecraft.getInstance().execute(() -> {
			if (err == null) {
				hudState = s;
				hudError = null;
			} else {
				hudError = shortError(err);
				if (err.getCause() instanceof ApiException e && e.unauthorized() && !"NOT_LINKED".equals(e.code())) {
					// Token revoked on the web.
					unlink();
				}
			}
		}));
	}

	private void loadDictionary(long now) {
		ApiClient a = api;
		if (a == null) return;
		dictionaryLoadedAt = now;
		a.dictionary().whenComplete((d, err) -> {
			if (err != null || d == null) return;
			Map<String, String> phrases = new HashMap<>();
			if (d.phrases() != null) {
				for (Dto.DictionaryEntry e : d.phrases()) {
					if (e.translation() != null) phrases.put(e.term(), e.translation());
				}
			}
			translator.setDictionary(d.protectedTerms() == null ? java.util.List.of() : d.protectedTerms(), phrases);
		});
	}

	/** Upload the finished session (only when the player enabled it) and reset the tracker. */
	void endSession() {
		SessionTracker.Snapshot s = session.snapshot();
		ApiClient a = api;
		if (config.session.upload && linked() && a != null && s.activeMs() > 60_000) {
			a.uploadSession(SessionTracker.toJson(s, System.currentTimeMillis())).exceptionally(err -> {
				LOG.info("Session upload skipped: {}", shortError(err));
				return null;
			});
		}
		session.reset();
	}

	void shutdown() {
		endSession();
		scheduler.shutdown();
		try {
			scheduler.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		transport.close();
	}

	public static String shortError(Throwable err) {
		Throwable c = err.getCause() != null ? err.getCause() : err;
		String m = c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
		return m.length() > 80 ? m.substring(0, 77) + "..." : m;
	}
}
