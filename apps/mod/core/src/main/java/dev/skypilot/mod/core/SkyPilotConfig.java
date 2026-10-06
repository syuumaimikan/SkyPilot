package dev.skypilot.mod.core;

/**
 * User settings, stored as JSON in {@code config/skypilot/config.json}.
 *
 * <p>Everything that sends data off the client is opt-in: chat translation is {@code OFF} by
 * default and session statistics are only uploaded when {@link Session#upload} is enabled.
 */
public final class SkyPilotConfig {
	/** SkyPilot backend (web origin). Plain http is only accepted for localhost. */
	public String apiBaseUrl = "http://localhost:3000";
	/** "auto" (follow the game language), "en" or "ja". */
	public String language = "auto";
	public Hud hud = new Hud();
	public Translation translation = new Translation();
	public Session session = new Session();
	public Market market = new Market();
	public Inventory inventory = new Inventory();
	/** Adds a SkyPilot price line to SkyBlock item tooltips (display only). */
	public boolean priceTooltip = true;
	/** On/off switch of every feature in {@link Features} without a field of its own (missing = off). */
	public java.util.Map<String, Boolean> features = new java.util.LinkedHashMap<>();
	/** Second, movable HUD box for the info features (coordinates, timers, trackers…). */
	public InfoHud infoHud = new InfoHud();
	/** Price watchlist (market.watchlist). */
	public java.util.List<Watch> watchlist = new java.util.ArrayList<>();
	/** The player's own waypoints (tools.custom_waypoints). */
	public java.util.List<SavedWaypoint> waypoints = new java.util.ArrayList<>();

	public boolean on(Features.Feature f) {
		return Features.isOn(this, f);
	}

	public static final class InfoHud {
		/** Negative x = measured from the right edge of the screen. */
		public int x = 4;
		/** Negative y = just below the SkyPilot HUD box. */
		public int y = -1;
		public double scale = 1.0;
		public double opacity = 0.5;
	}

	public static final class Watch {
		public String itemId;
		/** true: alert when the price drops below {@link #price}; false: when it rises above. */
		public boolean below = true;
		public double price;

		public Watch() {
		}

		public Watch(String itemId, boolean below, double price) {
			this.itemId = itemId;
			this.below = below;
			this.price = price;
		}
	}

	public static final class SavedWaypoint {
		public String name;
		/** Island id ("DWARVEN_MINES"), or "*" when it was saved outside SkyBlock. */
		public String island;
		public double x;
		public double y;
		public double z;

		public SavedWaypoint() {
		}

		public SavedWaypoint(String name, String island, double x, double y, double z) {
			this.name = name;
			this.island = island;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public static final class Hud {
		public boolean enabled = true;
		/** Top-left corner in GUI-scaled pixels. */
		public int x = 4;
		public int y = 4;
		public double scale = 1.0;
		/** Background opacity 0..1. */
		public double opacity = 0.6;
		public boolean showContext = true;
		public boolean showSession = true;
	}

	public static final class Translation {
		/** The original line is always shown; the translation is added below it. */
		public TranslationModeSetting mode = TranslationModeSetting.OFF;
		/** The user has seen the notice that chat text is sent to the SkyPilot server / translator. */
		public boolean disclosureAccepted = false;
		/**
		 * Adds a small "[訳]" button to chat lines; clicking it replaces that line with its
		 * translation (click "[原文]" to switch back). Only clicked lines are sent anywhere.
		 */
		public boolean clickToTranslate = false;
	}

	/** Market features in game. Display only: SkyPilot never buys, sells or clicks for you. */
	public static final class Market {
		/** Flip panel beside Hypixel's Auction House / Bazaar windows. */
		public boolean containerPanel = false;
		/** Toast + chat line for new AH flips matching your alert rules (polls your SkyPilot server). */
		public boolean alerts = false;
		public boolean alertSound = true;
	}

	/** Panels beside inventory windows. Display only; nothing is clicked or moved for you. */
	public static final class Inventory {
		/** Item list with search, recipes, drop sources and uses (right of the window). */
		public boolean itemBrowser = true;
		/** Total and per-item value of chests, backpacks and the ender chest (left of the window). */
		public boolean chestValue = true;
	}

	public static final class Session {
		public boolean enabled = true;
		/** Upload session statistics to the linked SkyPilot account when a session ends. */
		public boolean upload = false;
	}

	/** Mirrors the translation modes; kept here so the config has no dependency on the translation module. */
	public enum TranslationModeSetting {
		OFF, MANUAL, NPC_ONLY, PARTY, GUILD, AUTO
	}

	public static final int MAX_WATCHES = 20;
	public static final int MAX_WAYPOINTS = 100;

	/** Clamp values edited by hand in the JSON file into safe ranges. */
	public SkyPilotConfig sanitize() {
		if (apiBaseUrl == null || apiBaseUrl.isBlank()) apiBaseUrl = "http://localhost:3000";
		apiBaseUrl = apiBaseUrl.strip().replaceAll("/+$", "");
		if (language == null || !(language.equals("en") || language.equals("ja") || language.equals("auto"))) language = "auto";
		if (hud == null) hud = new Hud();
		if (translation == null) translation = new Translation();
		if (session == null) session = new Session();
		if (market == null) market = new Market();
		if (inventory == null) inventory = new Inventory();
		if (features == null) features = new java.util.LinkedHashMap<>();
		features.values().removeIf(v -> v == null || !v);
		if (infoHud == null) infoHud = new InfoHud();
		infoHud.scale = clamp(infoHud.scale, 0.5, 2.5);
		infoHud.opacity = clamp(infoHud.opacity, 0.0, 1.0);
		infoHud.x = (int) clamp(infoHud.x, -10_000, 10_000);
		infoHud.y = (int) clamp(infoHud.y, -1, 10_000);
		if (watchlist == null) watchlist = new java.util.ArrayList<>();
		watchlist.removeIf(w -> w == null || w.itemId == null || !w.itemId.matches("[A-Z0-9_:;]{1,64}") || !(w.price > 0) || Double.isInfinite(w.price));
		while (watchlist.size() > MAX_WATCHES) watchlist.remove(watchlist.size() - 1);
		if (waypoints == null) waypoints = new java.util.ArrayList<>();
		waypoints.removeIf(w -> w == null || w.name == null || w.name.isBlank() || w.name.length() > 32 || w.island == null
				|| !Double.isFinite(w.x) || !Double.isFinite(w.y) || !Double.isFinite(w.z));
		while (waypoints.size() > MAX_WAYPOINTS) waypoints.remove(waypoints.size() - 1);
		if (translation.mode == null) translation.mode = TranslationModeSetting.OFF;
		hud.scale = clamp(hud.scale, 0.5, 2.5);
		hud.opacity = clamp(hud.opacity, 0.0, 1.0);
		hud.x = (int) clamp(hud.x, 0, 10_000);
		hud.y = (int) clamp(hud.y, 0, 10_000);
		// An external translation mode is only active after the disclosure was shown.
		if (!translation.disclosureAccepted && translation.mode != TranslationModeSetting.OFF && translation.mode != TranslationModeSetting.MANUAL) {
			translation.mode = TranslationModeSetting.OFF;
		}
		return this;
	}

	private static double clamp(double v, double lo, double hi) {
		if (Double.isNaN(v)) return lo;
		return Math.max(lo, Math.min(hi, v));
	}
}
