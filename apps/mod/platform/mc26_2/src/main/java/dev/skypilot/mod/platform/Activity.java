package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ChatRules;
import dev.skypilot.mod.core.DungeonInfo;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Second set of session trackers: fishing (sea creatures, treasure, trophy fish), dungeon runs,
 * treasure chests, purse changes, speed, play time, tab list widgets and the stopwatch. Fed by
 * chat, the sidebar, the tab list and the player's own position; in memory only.
 */
final class Activity {
	private static final Set<String> ALERT_RARITIES = Set.of("LEGENDARY", "MYTHIC");

	// fishing
	static final Map<String, Integer> SEA_CREATURES = new LinkedHashMap<>();
	static int seaCreatureTotal;
	static int treasureCatches;
	static final Map<String, Integer> TROPHIES = new LinkedHashMap<>();
	static long fishingSince;
	private static Map<String, Dto.SeaCreature> byMessage = Map.of();
	private static long creaturesAt;
	private static long creaturesTriedAt;
	static long castAt;

	// dungeons
	static DungeonInfo dungeon;
	static long bossAt;
	static Integer bossElapsed;
	static String bossName;
	static Integer lastRunSeconds;
	static String lastRunFloor;

	/** Boss / phase name → elapsed run seconds at its first [BOSS] line, in order. */
	static final Map<String, Integer> BOSS_SPLITS = new LinkedHashMap<>();

	// rift
	static Integer riftSeconds;
	static long riftAt;
	static Long motes;
	private static int riftAlerted = Integer.MAX_VALUE;

	// mining chests
	static int chests;
	static long chestsSince;
	static final Map<String, Integer> CHEST_POWDER = new LinkedHashMap<>();

	// purse, speed, play time
	private static OptionalLong lastPurse = OptionalLong.empty();
	static long purseDelta;
	static long purseDeltaAt;
	static long joinedAt;
	private static final Deque<double[]> POSITIONS = new ArrayDeque<>(); // {time, x, z}

	// tab list (read once per second while a tab feature is on)
	static List<String> tab = List.of();

	// stopwatch
	static long stopwatchStart;
	static long stopwatchElapsed;
	static boolean stopwatchRunning;
	static final java.util.List<Long> LAPS = new java.util.ArrayList<>();

	private Activity() {
	}

	/** Every server chat line (also the ones a filter hides). */
	static void chat(String plain) {
		long now = System.currentTimeMillis();
		Lang l = SkyPilot.get().lang();
		if (Feat.on(Features.FISHING_TRACKER) || Feat.on(Features.FISHING_SEA_CREATURE)) {
			Dto.SeaCreature c = byMessage.get(plain.strip());
			if (c != null) {
				if (Feat.on(Features.FISHING_TRACKER)) {
					if (fishingSince == 0) fishingSince = now;
					SEA_CREATURES.merge(c.name(), 1, Integer::sum);
					seaCreatureTotal++;
				}
				if (Feat.on(Features.FISHING_SEA_CREATURE) && ALERT_RARITIES.contains(c.rarity())) {
					Feat.title(c.name(), c.rarity().equals("MYTHIC") ? 0xFFFF55FF : 0xFFFFAA00, l.t(c.rarity() + " sea creature", c.rarity() + " の海の生物"));
					Feat.ping(0.7f);
				}
			}
			if (Feat.on(Features.FISHING_TRACKER) && ChatRules.treasureCatch(plain).isPresent()) {
				if (fishingSince == 0) fishingSince = now;
				treasureCatches++;
			}
		}
		ChatRules.trophy(plain).ifPresent(t -> {
			if (Feat.on(Features.FISHING_TRACKER)) TROPHIES.merge(t.tier(), 1, Integer::sum);
			if (Feat.on(Features.FISHING_TROPHY_ALERT) && (t.tier().equals("GOLD") || t.tier().equals("DIAMOND"))) {
				Feat.title(t.fish(), t.tier().equals("DIAMOND") ? 0xFF55FFFF : 0xFFFFAA00, t.tier() + " TROPHY");
				Feat.ping(1.8f);
			}
		});
		ChatRules.boss(plain).ifPresent(name -> {
			if (dungeon != null && !BOSS_SPLITS.containsKey(name) && BOSS_SPLITS.size() < 12) {
				BOSS_SPLITS.put(name, dungeon.elapsedSeconds() == null ? -1 : dungeon.elapsedSeconds());
			}
			if (dungeon == null || bossAt != 0) return;
			bossAt = now;
			bossName = name;
			bossElapsed = dungeon.elapsedSeconds();
			if (Feat.on(Features.DUNGEONS_BOSS)) {
				Feat.title(l.t("Boss room", "ボス部屋"), 0xFFFF5555, name);
				Feat.ping(0.9f);
			}
		});
		if (Feat.on(Features.MINING_CHEST_TRACKER)) {
			if (ChatRules.chestOpened(plain)) {
				if (chestsSince == 0) chestsSince = now;
				chests++;
			}
			ChatRules.chestPowder(plain).ifPresent(p -> CHEST_POWDER.merge(p.type(), p.amount(), Integer::sum));
		}
	}

	/** Every client tick: positions for the speed display, the fishing hook for the bobber timer. */
	static void tick(Minecraft mc) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		if (Feat.on(Features.HUD_SPEED)) {
			Vec3 p = mc.player.position();
			POSITIONS.addLast(new double[] {now, p.x, p.z});
			while (POSITIONS.size() > 2 && now - POSITIONS.peekFirst()[0] > 1000) POSITIONS.pollFirst();
		}
		if (Feat.on(Features.FISHING_BOBBER)) {
			boolean fishing = mc.player.fishing != null;
			if (fishing && castAt == 0) castAt = now;
			if (!fishing) castAt = 0;
		}
	}

	/** Once per second. */
	static void tickSecond(Minecraft mc) {
		if (mc.level == null) return;
		long now = System.currentTimeMillis();
		SkyPilot sp = SkyPilot.get();
		if (joinedAt == 0) joinedAt = now;
		// purse changes from the sidebar
		OptionalLong purse = sp.context().purse();
		if (purse.isPresent() && lastPurse.isPresent() && purse.getAsLong() != lastPurse.getAsLong()) {
			purseDelta = purse.getAsLong() - lastPurse.getAsLong();
			purseDeltaAt = now;
		}
		if (purse.isPresent()) lastPurse = purse;
		// dungeon run from the sidebar
		if (Feat.on(Features.DUNGEONS_TIMER) || Feat.on(Features.DUNGEONS_BOSS)) {
			DungeonInfo d = DungeonInfo.parse(Sidebar.lines(mc));
			if (d == null && dungeon != null && dungeon.elapsedSeconds() != null) {
				lastRunSeconds = dungeon.elapsedSeconds();
				lastRunFloor = dungeon.floor();
			}
			if (d == null || dungeon == null || d.elapsedSeconds() != null && dungeon.elapsedSeconds() != null && d.elapsedSeconds() < dungeon.elapsedSeconds()) {
				bossAt = 0;
				bossElapsed = null;
				bossName = null;
				if (d != null) BOSS_SPLITS.clear();
			}
			dungeon = d;
		}
		if (tabWanted()) {
			tab = Sidebar.tabLines(mc);
			garden(sp.lang());
		}
		// Motes (sidebar "Motes: 137,242", Rift only)
		motes = null;
		if (Feat.on(Features.RIFT_TIMER)) {
			for (String line : Sidebar.lines(mc)) {
				String s = dev.skypilot.mod.core.Text.strip(line);
				if (s.startsWith("Motes:")) {
					var v = dev.skypilot.mod.core.Text.parseLong(s.substring(6));
					if (v.isPresent()) motes = v.getAsLong();
				}
			}
		}
		// sea creature names (SkyHanni-REPO, via the SkyPilot server)
		ApiClient api = sp.api();
		boolean fishing = Feat.on(Features.FISHING_TRACKER) || Feat.on(Features.FISHING_SEA_CREATURE);
		if (fishing && api != null && now - creaturesAt > 3_600_000 && now - creaturesTriedAt > 60_000) {
			creaturesTriedAt = now;
			api.seaCreatures().whenComplete((s, err) -> mc.execute(() -> {
				if (s != null) setSeaCreatures(s.creatures());
			}));
		}
	}

	/** The action bar: Rift time left, with alerts at 60 and 20 seconds. */
	static void actionBar(String text) {
		dev.skypilot.mod.core.ActionBarStats.riftSeconds(text).ifPresent(secs -> {
			riftSeconds = secs;
			riftAt = System.currentTimeMillis();
			if (!Feat.on(Features.RIFT_ALERT)) return;
			Lang l = SkyPilot.get().lang();
			for (int limit : new int[] {20, 60}) {
				if (secs <= limit && riftAlerted > limit) {
					riftAlerted = limit;
					Feat.title(l.t(limit + "s of Rift time left", "リフト時間 残り" + limit + "秒"), 0xFFFF5555, l.t("Collect orbs or leave the Rift", "オーブを集めるかリフトを出ましょう"));
					Feat.ping(0.6f);
					break;
				}
			}
			if (secs > 60) riftAlerted = Integer.MAX_VALUE;
			else if (secs > 20 && riftAlerted < 60) riftAlerted = 60;
		});
	}

	private static java.util.Set<String> visitors;
	private static String fullTraps;

	/** Garden tab widgets: new visitors and traps that filled up. */
	static void garden(Lang l) {
		List<String> names = dev.skypilot.mod.core.TabSections.sectionStartingWith(tab, "Visitors:");
		java.util.Set<String> now = new java.util.LinkedHashSet<>();
		for (String n : names) if (!n.startsWith("Visitors:")) now.add(n);
		if (!names.isEmpty()) {
			if (visitors != null && Feat.on(Features.FARMING_VISITOR_ALERT)) {
				List<String> arrived = new java.util.ArrayList<>();
				for (String n : now) if (!visitors.contains(n)) arrived.add(n);
				if (!arrived.isEmpty()) {
					Feat.toast(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), l.t("Visitor arrived", "訪問者が到着"), 0xFF55FF55, String.join(", ", arrived));
					Feat.ping(1.5f);
				}
			}
			visitors = now;
		}
		String full = dev.skypilot.mod.core.TabSections.line(tab, "Full Traps: ");
		if (full != null) {
			String traps = full.substring("Full Traps: ".length()).strip();
			if (fullTraps != null && !traps.equals(fullTraps) && !traps.equals("None") && Feat.on(Features.FARMING_TRAPS)) {
				Feat.toast(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBWEB), l.t("Pest trap full", "害虫トラップが満杯"), 0xFFFFAA00, traps);
				Feat.ping(1.1f);
			}
			fullTraps = traps;
		}
	}

	static void setSeaCreatures(List<Dto.SeaCreature> list) {
		Map<String, Dto.SeaCreature> m = new LinkedHashMap<>();
		for (Dto.SeaCreature c : list) m.put(c.message(), c);
		byMessage = m;
		creaturesAt = System.currentTimeMillis();
	}

	private static boolean tabWanted() {
		return Feat.on(Features.HUD_PET) || Feat.on(Features.HUD_PROFILE) || Feat.on(Features.MINING_FORGES) || Feat.on(Features.MINING_CRYSTALS)
				|| Feat.on(Features.FARMING_GARDEN) || Feat.on(Features.FARMING_CONTEST_TAB) || Feat.on(Features.FISHING_TROPHY_TAB) || Feat.on(Features.DUNGEONS_PUZZLES)
				|| Feat.on(Features.HUD_EFFECTS) || Feat.on(Features.HUD_TIMERS) || Feat.on(Features.HUD_DAILY) || Feat.on(Features.HUD_ESSENCE)
				|| Feat.on(Features.HUD_COLLECTION) || Feat.on(Features.HUD_SKILLS_TAB) || Feat.on(Features.COMBAT_SLAYER_TAB) || Feat.on(Features.COMBAT_BESTIARY)
				|| Feat.on(Features.FARMING_VISITOR_ALERT) || Feat.on(Features.FARMING_TRAPS) || Feat.on(Features.FARMING_MILESTONES);
	}

	/** Horizontal blocks per second over the last second; NaN without samples. */
	static double speed() {
		if (POSITIONS.size() < 2) return Double.NaN;
		double[] a = POSITIONS.peekFirst();
		double[] b = POSITIONS.peekLast();
		double dt = (b[0] - a[0]) / 1000.0;
		if (dt < 0.2) return Double.NaN;
		return Math.hypot(b[1] - a[1], b[2] - a[2]) / dt;
	}

	static long stopwatchMs() {
		return stopwatchElapsed + (stopwatchRunning ? System.currentTimeMillis() - stopwatchStart : 0);
	}

	static void stopwatch(String action) {
		long now = System.currentTimeMillis();
		switch (action) {
			case "start" -> {
				if (!stopwatchRunning) {
					stopwatchRunning = true;
					stopwatchStart = now;
				}
			}
			case "stop" -> {
				if (stopwatchRunning) {
					stopwatchElapsed += now - stopwatchStart;
					stopwatchRunning = false;
				}
			}
			case "lap" -> {
				LAPS.add(stopwatchMs());
				while (LAPS.size() > 10) LAPS.remove(0);
			}
			default -> {
				stopwatchRunning = false;
				stopwatchElapsed = 0;
				LAPS.clear();
			}
		}
	}

	static void reset() {
		SEA_CREATURES.clear();
		seaCreatureTotal = 0;
		treasureCatches = 0;
		TROPHIES.clear();
		fishingSince = 0;
		chests = 0;
		chestsSince = 0;
		CHEST_POWDER.clear();
		lastRunSeconds = null;
		lastRunFloor = null;
	}

	/** On disconnect: play time and purse start again with the next join. */
	static void disconnected() {
		joinedAt = 0;
		lastPurse = OptionalLong.empty();
		dungeon = null;
		bossAt = 0;
		BOSS_SPLITS.clear();
		riftSeconds = null;
		castAt = 0;
		visitors = null;
		fullTraps = null;
		tab = List.of();
	}
}
