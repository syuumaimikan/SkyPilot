package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ActionBarStats;
import dev.skypilot.mod.core.ChatRules;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.TabSections;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Session trackers fed by what the client already shows: chat (drops, slayer quests), the
 * action bar (health / mana, skill XP), the sidebar ("Slay the boss!"), the tab list
 * (commissions, powders) and the crops the player breaks. In memory only.
 */
final class Trackers {
	private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "nether_wart", "cocoa", "sugar_cane", "cactus", "melon", "pumpkin", "red_mushroom",
			"brown_mushroom");

	// drops
	static final Map<String, Integer> DROPS = new LinkedHashMap<>();
	static int dropTotal;

	// slayer
	static int slayerKills;
	static int slayerFails;
	static long questStartedAt;
	static long questTotalMs;
	static long slayerSessionStart;
	private static boolean bossAlive;

	// action bar
	static ActionBarStats.Stats stats;
	static long statsAt;
	static ActionBarStats.SkillGain skill;
	static long skillAt;
	private static final Map<String, double[]> SKILL_RATE = new LinkedHashMap<>(); // skill → {firstAt, gainedSince}

	// farming
	private static final Deque<Long> BREAKS = new ArrayDeque<>();

	// mining (tab list)
	static List<String> commissions = List.of();
	static List<String> powders = List.of();
	private static final Map<String, double[]> POWDER_START = new LinkedHashMap<>(); // type → {firstAt, firstValue}

	private Trackers() {
	}

	// ---------------------------------------------------------------- inputs

	static void drop(String item) {
		if (!Feat.on(Features.COMBAT_DROP_TRACKER)) return;
		DROPS.merge(item, 1, Integer::sum);
		dropTotal++;
	}

	static void slayer(ChatRules.SlayerEvent e) {
		long now = System.currentTimeMillis();
		if (slayerSessionStart == 0) slayerSessionStart = now;
		switch (e) {
			case STARTED -> questStartedAt = now;
			case COMPLETE -> {
				slayerKills++;
				if (questStartedAt > 0) questTotalMs += now - questStartedAt;
				questStartedAt = 0;
				bossAlive = false;
			}
			case FAILED -> {
				slayerFails++;
				questStartedAt = 0;
				bossAlive = false;
			}
		}
	}

	static void actionBar(String text) {
		ActionBarStats.Stats s = ActionBarStats.stats(text);
		long now = System.currentTimeMillis();
		if (s.any()) {
			stats = s;
			statsAt = now;
		}
		ActionBarStats.skill(text).ifPresent(g -> {
			skill = g;
			skillAt = now;
			double[] r = SKILL_RATE.computeIfAbsent(g.skill(), k -> new double[] {now, 0});
			r[1] += g.gained();
		});
	}

	static void blockBroken(BlockState state) {
		if (!Feat.on(Features.FARMING_BPS)) return;
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (!CROPS.contains(id)) return;
		long now = System.currentTimeMillis();
		BREAKS.addLast(now);
		while (BREAKS.size() > 400 || !BREAKS.isEmpty() && now - BREAKS.peekFirst() > 5_000) BREAKS.pollFirst();
	}

	/** Once per second on the game thread. */
	static void tickSecond(Minecraft mc) {
		if (mc.level == null) return;
		long now = System.currentTimeMillis();
		if (Feat.on(Features.COMBAT_SLAYER_TRACKER) || Feat.on(Features.COMBAT_SLAYER_ALERT)) {
			boolean alive = Sidebar.lines(mc).stream().anyMatch(l -> l.strip().equals("Slay the boss!"));
			if (alive && !bossAlive && Feat.on(Features.COMBAT_SLAYER_ALERT)) {
				Feat.title(SkyPilot.get().lang().t("Boss spawned!", "ボス出現！"), 0xFFFF5555, null);
				Feat.ping(0.8f);
			}
			bossAlive = alive;
		}
		boolean comm = Feat.on(Features.MINING_COMMISSIONS);
		boolean powder = Feat.on(Features.MINING_POWDER);
		if (comm || powder) {
			List<String> tab = Sidebar.tabLines(mc);
			if (comm) commissions = TabSections.section(tab, "Commissions:");
			if (powder) {
				powders = TabSections.section(tab, "Powders:");
				for (String p : powders) {
					OptionalDouble v = TabSections.valueAfterColon(p);
					if (v.isPresent()) POWDER_START.putIfAbsent(TabSections.label(p), new double[] {now, v.getAsDouble()});
				}
			}
		}
	}

	// ---------------------------------------------------------------- derived values

	/** Crops broken per second over the last 3 seconds; NaN when idle. */
	static double bps() {
		long now = System.currentTimeMillis();
		if (BREAKS.isEmpty() || now - BREAKS.peekLast() > 2_000) return Double.NaN;
		long n = BREAKS.stream().filter(t -> now - t <= 3_000).count();
		return n / 3.0;
	}

	/** XP per hour of a skill since its first gain this session. */
	static double skillPerHour(String name) {
		double[] r = SKILL_RATE.get(name);
		if (r == null) return 0;
		double hours = Math.max(1.0 / 60, (System.currentTimeMillis() - r[0]) / 3_600_000.0);
		return r[1] / hours;
	}

	/** Powder gained per hour of {@code entry} ("Mithril: 1,234"), or NaN before a minute passed. */
	static double powderPerHour(String entry) {
		double[] start = POWDER_START.get(TabSections.label(entry));
		OptionalDouble now = TabSections.valueAfterColon(entry);
		if (start == null || now.isEmpty()) return Double.NaN;
		double hours = (System.currentTimeMillis() - start[0]) / 3_600_000.0;
		return hours < 1.0 / 60 ? Double.NaN : (now.getAsDouble() - start[1]) / hours;
	}

	static boolean inventoryFull(Minecraft mc) {
		if (mc.player == null) return false;
		var items = mc.player.getInventory().getNonEquipmentItems();
		for (var s : items) if (s.isEmpty()) return false;
		return !items.isEmpty();
	}

	static void reset() {
		DROPS.clear();
		dropTotal = 0;
		slayerKills = 0;
		slayerFails = 0;
		questStartedAt = 0;
		questTotalMs = 0;
		slayerSessionStart = 0;
		SKILL_RATE.clear();
		BREAKS.clear();
		POWDER_START.clear();
		skill = null;
		stats = null;
	}

	/** For tests: pretend the player broke a crop {@code n} times just now. */
	static void simulateBreaks(int n) {
		long now = System.currentTimeMillis();
		for (int i = n - 1; i >= 0; i--) BREAKS.addLast(now - i * 100L);
	}
}
