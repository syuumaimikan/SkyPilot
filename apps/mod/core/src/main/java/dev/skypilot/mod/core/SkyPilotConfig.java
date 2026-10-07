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
	public Afk afk = new Afk();
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
	/** Words that mark a chat line (chat.keywords). */
	public java.util.List<String> keywords = new java.util.ArrayList<>();
	/** The player's to-do list (tools.todo). */
	public java.util.List<Todo> todos = new java.util.ArrayList<>();
	/** Items worth at least this much are outlined (inventory.valuable_highlight). */
	public double valuableThreshold = 1_000_000;
	/** Numbers for the performance features and the saved video settings. */
	public Performance perf = new Performance();
	/** Local appearance only; no account, purchase or server communication. */
	public Appearance appearance = new Appearance();
	public String deskNotes = "";
	public int focusMinutes = 25;
	public int tileBest = 0;
	public VisualSettings visuals = new VisualSettings();
	public UtilitySettings utility = new UtilitySettings();

	public static final class Appearance {
		public boolean richMenus = true;
		public boolean skin3d = false;
		public String cape = "none";
		public String accessory = "none"; // Legacy slot; migrated on load.
		public String hat = "none";
		public String back = "none";
		public int color = 0xA38BFA;
		public java.util.List<String> favorites = new java.util.ArrayList<>();
		public java.util.List<WardrobeTools.Outfit> outfits = new java.util.ArrayList<>();
	}


	public static final class Performance {
		public int backgroundFps = 15;
		public int menuFps = 60;
		public int entityDistance = 48;
		public int playerDistance = 32;
		public int particlesPerTick = 150;
		/** The player's own video settings before "lightweight video settings" (null = not applied). */
		public java.util.Map<String, String> videoBackup;
	}

	public static final class Todo {
		public String text;
		public boolean done;

		public Todo() {
		}

		public Todo(String text) {
			this.text = text;
		}
	}

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
		/** A "ding" when a line is clicked for translation and again when its translation arrives. */
		public boolean sound = true;
	}

	/** Market features in game. Display only: SkyPilot never buys, sells or clicks for you. */
	public static final class Market {
		/** Flip panel beside Hypixel's Auction House / Bazaar windows. */
		public boolean containerPanel = false;
		/** Toast + chat line for new AH flips matching your alert rules (polls your SkyPilot server). */
		public boolean alerts = false;
		public boolean alertSound = true;
		/** Flip panel order: score (default), profit, roi or hour. */
		public String flipSort = "score";
		/** Flip panel: only flips your purse can pay for (AH) / amounts sized to it (Bazaar). */
		public boolean affordableOnly = false;
		/** Your Bazaar Flipper account upgrade level (0-2): lowers the Bazaar tax in the numbers. */
		public int bazaarFlipper = 0;
	}

	public static final java.util.List<String> FLIP_SORTS = java.util.List.of("score", "profit", "roi", "hour");

	/** Panels beside inventory windows. Display only; nothing is clicked or moved for you. */
	public static final class Inventory {
		/** Item list with search, recipes, drop sources and uses (right of the window). */
		public boolean itemBrowser = true;
		/** Total and per-item value of chests, backpacks and the ender chest (left of the window). */
		public boolean chestValue = true;
		/** Item list width in GUI pixels, dragged by its left edge (0 = all the space right of the window). */
		public int itemBrowserWidth = 0;
		/** Item list pushed into the screen edge (a small tab brings it back). */
		public boolean itemBrowserCollapsed = false;
		/** Item list category: all, weapons, armor, equipment, accessories, tools, materials, minions, pet_items, reforge_stones, cosmetics, other. */
		public String itemBrowserCategory = "all";
	}

	/** AFK guard settings (afk.* features). */
	public static final class Afk {
		/** Minutes without keyboard or mouse input before you count as AFK. */
		public int minutes = 3;
		/** Frame cap while AFK (afk.low_fps). */
		public int fps = 10;
	}

	public static final java.util.List<String> ITEM_CATEGORIES = java.util.List.of("all", "weapons", "armor", "equipment", "accessories", "tools", "materials",
			"minions", "pet_items", "reforge_stones", "cosmetics", "other");

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
		inventory.itemBrowserWidth = inventory.itemBrowserWidth <= 0 ? 0 : (int) clamp(inventory.itemBrowserWidth, 40, 4000);
		if (!ITEM_CATEGORIES.contains(inventory.itemBrowserCategory)) inventory.itemBrowserCategory = "all";
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
		if (keywords == null) keywords = new java.util.ArrayList<>();
		keywords.removeIf(k -> k == null || k.isBlank() || k.length() > 32);
		while (keywords.size() > 20) keywords.remove(keywords.size() - 1);
		if (todos == null) todos = new java.util.ArrayList<>();
		todos.removeIf(t -> t == null || t.text == null || t.text.isBlank() || t.text.length() > 80);
		while (todos.size() > 30) todos.remove(todos.size() - 1);
		if (!(valuableThreshold > 0) || Double.isInfinite(valuableThreshold)) valuableThreshold = 1_000_000;
		if (!FLIP_SORTS.contains(market.flipSort)) market.flipSort = "score";
		market.bazaarFlipper = (int) clamp(market.bazaarFlipper, 0, 2);
		if (afk == null) afk = new Afk();
		afk.minutes = (int) clamp(afk.minutes, 1, 30);
		afk.fps = (int) clamp(afk.fps, 5, 30);
		if (appearance == null) appearance = new Appearance();
		if (!CosmeticCatalog.valid("cape", appearance.cape)) appearance.cape = "none";
		if (!java.util.List.of("none", "crown", "halo", "wings").contains(appearance.accessory == null ? "" : appearance.accessory)) appearance.accessory = "none";
		if (!CosmeticCatalog.valid("hat", appearance.hat)) appearance.hat = "none";
		if (!CosmeticCatalog.valid("back", appearance.back)) appearance.back = "none";
		CosmeticCatalog.migrate(appearance);
		appearance.color &= 0xFFFFFF;
		WardrobeTools.sanitize(appearance);
		if(deskNotes==null)deskNotes="";
		deskNotes=deskNotes.substring(0,Math.min(1400,deskNotes.length()));
		focusMinutes=(int)clamp(focusMinutes,1,180);
		tileBest=Math.max(0,tileBest);
		if(utility==null)utility=new UtilitySettings();
		if(visuals==null)visuals=new VisualSettings();
		visuals.sanitize();
		if (perf == null) perf = new Performance();
		perf.backgroundFps = (int) clamp(perf.backgroundFps, 5, 60);
		perf.menuFps = (int) clamp(perf.menuFps, 15, 240);
		perf.entityDistance = (int) clamp(perf.entityDistance, 8, 128);
		perf.playerDistance = (int) clamp(perf.playerDistance, 4, 128);
		perf.particlesPerTick = (int) clamp(perf.particlesPerTick, 20, 2000);
		if (perf.videoBackup != null) {
			perf.videoBackup.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getKey().length() > 40 || e.getValue().length() > 40);
			if (perf.videoBackup.isEmpty()) perf.videoBackup = null;
		}
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
