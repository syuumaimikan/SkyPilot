package dev.skypilot.mod.core;

/**
 * User settings, stored as JSON in {@code config/skypilot/config.json}.
 *
 * <p>Everything that sends data off the client is opt-in: chat translation is {@code OFF} by
 * default and session statistics are only uploaded when {@link Session#upload} is enabled.
 */
public final class SkyPilotConfig {
    public boolean dungeonSolver = true;
	/**
	 * Keep the bundled Skyblocker on SkyPilot's pinned build automatically (launcher start and
	 * "update all"). Off pins the installed copy; the explicit "Update Skyblocker" button still works.
	 * Shared by the launcher and the mod.
	 */
	public boolean skyblockerAutoUpdate = true;
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
	public java.util.Map<String, UiPosition> uiPositions = new java.util.LinkedHashMap<>();
	public boolean compactInfo = true;
	public boolean hudInContainers = false;
	public boolean blazeDescending = false;
	public static final class UiPosition { public int dx,dy; public double scale=1.0; public boolean hidden=false; }
	public static final java.util.Set<String> VALID_UI_ELEMENTS = java.util.Set.of(
			"scoreboard", "actionbar", "bossbar", "hotbar", "effects",
			"media", "garden", "dungeon", "solver",
			"skypilot_hud", "info_hud", "utility_hud",
			"skyblocker_solver", "skyblocker_dungeon", "skyblocker_puzzle", "skyblocker_hud", "skyblocker_party"
	);

	public static final class SlotTransform {
		/** Offset in Blockbench pixels (-8.0 .. +8.0; 16 px = 1 block). */
		public double offsetX = 0.0;
		public double offsetY = 0.0;
		public double offsetZ = 0.0;
		/** Uniform scale multiplier (0.4 .. 2.0). */
		public double scale = 1.0;
		/** Pitch / Yaw / Roll tilt in degrees. */
		public double rotX = 0.0;
		public double rotY = 0.0;
		public double rotZ = 0.0;
		/** Optional per-slot custom dye tint &amp; live Rainbow Chroma mode (surpasses Lunar Chroma!). */
		public boolean tintEnabled = false;
		public int tintColor = 0xFFFFFF;
		public boolean chroma = false;

		public boolean isIdentity() {
			return Math.abs(offsetX) < 1e-4 && Math.abs(offsetY) < 1e-4 && Math.abs(offsetZ) < 1e-4
					&& Math.abs(scale - 1.0) < 1e-4
					&& Math.abs(rotX) < 1e-4 && Math.abs(rotY) < 1e-4 && Math.abs(rotZ) < 1e-4
					&& !tintEnabled && !chroma;
		}

		public SlotTransform copy() {
			SlotTransform t = new SlotTransform();
			t.offsetX = offsetX;
			t.offsetY = offsetY;
			t.offsetZ = offsetZ;
			t.scale = scale;
			t.rotX = rotX;
			t.rotY = rotY;
			t.rotZ = rotZ;
			t.tintEnabled = tintEnabled;
			t.tintColor = tintColor & 0xFFFFFF;
			t.chroma = chroma;
			return t;
		}

		public void reset() {
			offsetX = offsetY = offsetZ = 0.0;
			scale = 1.0;
			rotX = rotY = rotZ = 0.0;
			tintEnabled = false;
			tintColor = 0xFFFFFF;
			chroma = false;
		}

		public void sanitize() {
			offsetX = clampFinite(offsetX, -8.0, 8.0, 0.0);
			offsetY = clampFinite(offsetY, -8.0, 8.0, 0.0);
			offsetZ = clampFinite(offsetZ, -8.0, 8.0, 0.0);
			scale = clampFinite(scale <= 0 ? 1.0 : scale, 0.4, 2.0, 1.0);
			rotX = clampFinite(rotX, -60.0, 60.0, 0.0);
			rotY = clampFinite(rotY, -180.0, 180.0, 0.0);
			rotZ = clampFinite(rotZ, -60.0, 60.0, 0.0);
			tintColor &= 0xFFFFFF;
		}
	}

	public static final class VfxSettings {
		public String attack = "none";
		public String kill = "none";
		public String move = "none";
		public String jump = "none";
		public String dash = "none";
		public String projectile = "none";
		public String ambient = "none";
		/** Uniform VFX size multiplier (0.5 .. 2.0). */
		public double scale = 1.0;
		/** Density level: 1 = Low, 2 = Normal, 3 = High. */
		public int intensity = 2;
		/** Whether to show custom VFX in 1st-person view. */
		public boolean firstPersonVfx = true;
		/** Whether to render synced custom VFX on other Wardrobe mod players. */
		public boolean otherPlayerVfx = true;
		/** Optional custom color tint override instead of theme colors. */
		public boolean customColorEnabled = false;
		public int customColor = 0x55D6D2;

		public VfxSettings copy() {
			VfxSettings v = new VfxSettings();
			v.attack = attack;
			v.kill = kill;
			v.move = move;
			v.jump = jump;
			v.dash = dash;
			v.projectile = projectile;
			v.ambient = ambient;
			v.scale = scale;
			v.intensity = intensity;
			v.firstPersonVfx = firstPersonVfx;
			v.otherPlayerVfx = otherPlayerVfx;
			v.customColorEnabled = customColorEnabled;
			v.customColor = customColor;
			return v;
		}

		public void sanitize() {
			if (!WardrobeVfxCatalog.validId(attack)) attack = "none";
			if (!WardrobeVfxCatalog.validId(kill)) kill = "none";
			if (!WardrobeVfxCatalog.validId(move)) move = "none";
			if (!WardrobeVfxCatalog.validId(jump)) jump = "none";
			if (!WardrobeVfxCatalog.validId(dash)) dash = "none";
			if (!WardrobeVfxCatalog.validId(projectile)) projectile = "none";
			if (!WardrobeVfxCatalog.validId(ambient)) ambient = "none";
			scale = clampFinite(scale <= 0 ? 1.0 : scale, 0.5, 2.0, 1.0);
			intensity = (int) clampFinite(intensity, 1, 3, 2);
			customColor &= 0xFFFFFF;
		}
	}

	public static final class Appearance {
		public boolean richMenus = true;
		public boolean skin3d = false;
		public String cape = "none";
		public String accessory = "none"; // Legacy slot; migrated on load.
		public String hat = "none";
		public String back = "none";
		public String face = "none";
		public String shoulder = "none";
		public String waist = "none";
		public String feet = "none";
		public String aura = "none";
		public String pet = "none";
		public String companion = "none";
		public double wingScale = 1.0;
		public boolean cosmeticGlow = true;
		/** Client-side ambient particles trailing equipped wings, cloaks, auras, and pets. */
		public boolean cosmeticParticles = true;
		/** Granular first-person visibility toggles per cosmetic slot. */
		public boolean firstPersonPet = true;
		public boolean firstPersonCompanion = true;
		public boolean firstPersonAura = false;
		public boolean firstPersonWings = false;
		public boolean firstPersonCape = false;
		public boolean firstPersonHat = false;
		public boolean firstPersonFace = false;
		public boolean firstPersonShoulder = false;
		public boolean firstPersonWaist = false;
		public boolean firstPersonFeet = true;
		/** Standalone multiplayer sync settings (zero SkyPilot server dependency; zero MC packets). */
		public boolean showOtherPlayers = true;
		public boolean shareWithOthers = true;
		public boolean otherPlayerPets = true;
		public boolean otherPlayerParticles = true;
		/** "lan" (zero-config built-in UDP LAN auto-sync), "offline" (local only), or "relay" (custom HTTP relay). */
		public String wardrobeSyncMode = "lan";
		public String wardrobeSyncUrl = "";
		/** Per-slot fine transform adjustments (offset X/Y/Z, scale, tilt X/Y/Z). */
		public java.util.Map<String, SlotTransform> slotTransforms = new java.util.LinkedHashMap<>();
		/** Custom 3D VFX settings across 7 trigger slots (attack, kill, move, jump, dash, projectile, ambient). */
		public VfxSettings vfx = new VfxSettings();
		/** Persisted pet & companion bonding progress (total affection XP + per-pet bond points). */
		public int petAffection = 0;
		public java.util.Map<String, Integer> petBonds = new java.util.LinkedHashMap<>();
		/** Studio preview environment backdrop: 0=Midnight, 1=Neon Cyber, 2=Golden Sunset, 3=Bright Day. */
		public int studioBackdrop = 0;
		public int color = 0xA38BFA;
		public java.util.List<String> favorites = new java.util.ArrayList<>();
		public java.util.List<WardrobeTools.Outfit> outfits = new java.util.ArrayList<>();

		public int bondOf(String cosmeticId) {
			if (petBonds == null || cosmeticId == null || "none".equals(cosmeticId)) return 0;
			return Math.max(0, petBonds.getOrDefault(cosmeticId, 0));
		}

		public int addBond(String cosmeticId, int delta) {
			if (cosmeticId == null || "none".equals(cosmeticId) || delta <= 0) return bondLevel(bondOf(cosmeticId));
			if (petBonds == null) petBonds = new java.util.LinkedHashMap<>();
			int next = Math.min(999_999, bondOf(cosmeticId) + delta);
			petBonds.put(cosmeticId, next);
			petAffection = Math.min(9_999_999, Math.max(0, petAffection) + delta);
			return bondLevel(next);
		}

		private static final int[] BOND_THRESHOLDS = {0, 10, 25, 50, 90, 150, 230, 330, 450, 600};

		public static int bondLevel(int points) {
			int p = Math.max(0, points);
			int lv = 1;
			for (int i = 1; i < BOND_THRESHOLDS.length; i++) {
				if (p >= BOND_THRESHOLDS[i]) lv = i + 1;
				else break;
			}
			return lv;
		}

		public static int bondPrevThreshold(int level) {
			int idx = Math.max(0, Math.min(BOND_THRESHOLDS.length - 1, level - 1));
			return BOND_THRESHOLDS[idx];
		}

		public static int bondNextThreshold(int level) {
			if (level >= BOND_THRESHOLDS.length) return BOND_THRESHOLDS[BOND_THRESHOLDS.length - 1];
			return BOND_THRESHOLDS[Math.max(1, level)];
		}

		public SlotTransform transformOf(String slot) {
			if (slotTransforms == null || slot == null) return new SlotTransform();
			SlotTransform t = slotTransforms.get(slot);
			return t == null ? new SlotTransform() : t;
		}

		public SlotTransform editTransform(String slot) {
			if (slotTransforms == null) slotTransforms = new java.util.LinkedHashMap<>();
			return slotTransforms.computeIfAbsent(slot, k -> new SlotTransform());
		}

		public void resetTransform(String slot) {
			if (slotTransforms != null) slotTransforms.remove(slot);
		}

		public void resetAllTransforms() {
			if (slotTransforms != null) slotTransforms.clear();
			wingScale = 1.0;
		}

		public boolean firstPersonFor(String slot) {
			if (slot == null) return false;
			return switch (slot) {
				case "pet" -> firstPersonPet;
				case "companion" -> firstPersonCompanion;
				case "aura" -> firstPersonAura;
				case "back" -> firstPersonWings;
				case "cape" -> firstPersonCape;
				case "hat" -> firstPersonHat;
				case "face" -> firstPersonFace;
				case "shoulder" -> firstPersonShoulder;
				case "waist" -> firstPersonWaist;
				case "feet" -> firstPersonFeet;
				default -> false;
			};
		}

		public void setFirstPersonFor(String slot, boolean value) {
			if (slot == null) return;
			switch (slot) {
				case "pet" -> firstPersonPet = value;
				case "companion" -> firstPersonCompanion = value;
				case "aura" -> firstPersonAura = value;
				case "back" -> firstPersonWings = value;
				case "cape" -> firstPersonCape = value;
				case "hat" -> firstPersonHat = value;
				case "face" -> firstPersonFace = value;
				case "shoulder" -> firstPersonShoulder = value;
				case "waist" -> firstPersonWaist = value;
				case "feet" -> firstPersonFeet = value;
			}
		}
	}


	public static final class Performance {
		public int backgroundFps = 15;
		public int menuFps = 60;
		public int entityDistance = 48;
		public int playerDistance = 32;
		public int particlesPerTick = 150;
		public int particleDistance = 32;
		public int targetFps = 60;
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
		if(uiPositions==null)uiPositions=new java.util.LinkedHashMap<>();
		uiPositions.entrySet().removeIf(e->e.getValue()==null||(!VALID_UI_ELEMENTS.contains(e.getKey())&&!e.getKey().startsWith("mod_")));
		for(var p:uiPositions.values()){p.dx=(int)clamp(p.dx,-10000,10000);p.dy=(int)clamp(p.dy,-10000,10000);p.scale=Double.isFinite(p.scale)?clamp(p.scale,.5,2):1;}
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
		if (!CosmeticCatalog.valid("face", appearance.face)) appearance.face = "none";
		if (!CosmeticCatalog.valid("shoulder", appearance.shoulder)) appearance.shoulder = "none";
		if (!CosmeticCatalog.valid("waist", appearance.waist)) appearance.waist = "none";
		if (!CosmeticCatalog.valid("feet", appearance.feet)) appearance.feet = "none";
		if (!CosmeticCatalog.valid("aura", appearance.aura)) appearance.aura = "none";
		if (!CosmeticCatalog.valid("pet", appearance.pet)) appearance.pet = "none";
		if (!CosmeticCatalog.valid("companion", appearance.companion)) appearance.companion = "none";
		CosmeticCatalog.migrate(appearance);
		appearance.wingScale = clamp(appearance.wingScale <= 0 ? 1.0 : appearance.wingScale, 0.6, 1.5);
		appearance.color &= 0xFFFFFF;
		if (appearance.wardrobeSyncMode == null
				|| !java.util.List.of("offline", "lan", "relay").contains(appearance.wardrobeSyncMode)) {
			appearance.wardrobeSyncMode = "lan";
		}
		if (appearance.wardrobeSyncUrl == null || "http://localhost:8080".equals(appearance.wardrobeSyncUrl.strip())) {
			appearance.wardrobeSyncUrl = "";
		} else {
			appearance.wardrobeSyncUrl = appearance.wardrobeSyncUrl.strip().replaceAll("/+$", "");
		}
		if (appearance.slotTransforms == null) appearance.slotTransforms = new java.util.LinkedHashMap<>();
		appearance.slotTransforms.entrySet().removeIf(e -> e.getKey() == null || !CosmeticCatalog.SLOTS.contains(e.getKey()) || e.getValue() == null);
		for (var tr : appearance.slotTransforms.values()) tr.sanitize();
		appearance.slotTransforms.entrySet().removeIf(e -> e.getValue().isIdentity());
		if (appearance.vfx == null) appearance.vfx = new VfxSettings();
		appearance.vfx.sanitize();
		appearance.petAffection = Math.max(0, Math.min(9_999_999, appearance.petAffection));
		appearance.studioBackdrop = Math.floorMod(appearance.studioBackdrop, 4);
		if (appearance.petBonds == null) appearance.petBonds = new java.util.LinkedHashMap<>();
		appearance.petBonds.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getKey().length() > 64 || e.getValue() == null || e.getValue() <= 0);
		appearance.petBonds.replaceAll((k, v) -> Math.min(999_999, Math.max(0, v)));
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
		perf.particleDistance = (int) clamp(perf.particleDistance, 8, 128);
		perf.targetFps = (int) clamp(perf.targetFps, 30, 240);
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

	private static double clampFinite(double v, double lo, double hi, double fallback) {
		if (!Double.isFinite(v)) return fallback;
		return Math.max(lo, Math.min(hi, v));
	}
}
