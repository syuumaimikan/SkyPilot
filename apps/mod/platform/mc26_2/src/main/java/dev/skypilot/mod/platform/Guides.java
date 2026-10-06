package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Guide lines to where things are done: "Guide" buttons in the menu, the next action of the
 * plan (optionally followed automatically), the Rift guide on entering the Rift, and the dungeon
 * puzzle rules. Points at fixed public places only; the player walks, and a warp is only put into
 * the chat box for the player to send.
 */
final class Guides {
	/** The plan's top action (refreshed while a guide feature is on). */
	static Dto.PlanAction next;
	private static long nextAt;
	private static long nextTriedAt;
	/** The place the automatic line was last pointed at (so clearing it is respected). */
	private static String autoKey;
	private static String lastIsland = "";
	private static Map<String, Dto.Puzzle> puzzles = Map.of();
	private static long puzzlesTriedAt;

	private Guides() {
	}

	/** Point the guide line at a place; off the island, offer the closest warp in the chat box. */
	static void go(Dto.Place p, boolean closeScreen) {
		if (p == null || p.waypoint() == null) return;
		Minecraft mc = Minecraft.getInstance();
		Lang l = SkyPilot.get().lang();
		Waypoints.set(p.waypoint());
		McText.chat(McText.prefix().append(Component.literal(l.t("Guide: ", "ガイド: ")).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(p.waypoint().name() + " (" + island(p.waypoint().island()) + ")").withStyle(ChatFormatting.WHITE)));
		if (p.note() != null && !p.note().isBlank()) McText.chat(Component.literal("  " + p.note()).withStyle(ChatFormatting.GRAY));
		boolean there = Waypoints.onTargetIsland();
		if (!there && p.warp() != null) {
			McText.chat(Component.literal(l.t("  Not on that island: ", "  別の島です: ") + p.warp() + l.t(" is in your chat box - press Enter to warp.", " をチャット欄に入れました。Enterで移動します。"))
					.withStyle(ChatFormatting.AQUA));
			mc.execute(() -> mc.gui.setScreen(new ChatScreen(p.warp(), false)));
		} else if (closeScreen) {
			mc.execute(() -> mc.gui.setScreen(null));
		}
	}

	static String island(String id) {
		if (id == null) return "";
		String n = dev.skypilot.mod.core.Islands.name(id, SkyPilot.get().lang());
		return n == null || n.isEmpty() ? id.replace('_', ' ').toLowerCase(Locale.ROOT) : n;
	}

	/** Once per second. */
	static void tickSecond(Minecraft mc) {
		SkyPilot sp = SkyPilot.get();
		ApiClient api = sp.api();
		long now = System.currentTimeMillis();
		boolean wantNext = Feat.on(Features.GUIDE_NEXT) || Feat.on(Features.GUIDE_AUTO_LINE);
		if (wantNext && api != null && sp.linked() && now - nextAt > 300_000 && now - nextTriedAt > 60_000) {
			nextTriedAt = now;
			api.plan(sp.lang().code()).whenComplete((p, err) -> mc.execute(() -> {
				if (p != null) {
					next = p.actions() == null || p.actions().isEmpty() ? null : p.actions().get(0);
					nextAt = System.currentTimeMillis();
				}
			}));
		}
		if (Feat.on(Features.GUIDE_AUTO_LINE) && next != null && next.place() != null && Waypoints.target() == null && Waypoints.guide() == null && !FarmBuilder.active()) {
			String key = next.title() + "@" + next.place().waypoint().name();
			if (!key.equals(autoKey)) {
				autoKey = key;
				Waypoints.set(next.place().waypoint());
			}
		}
		// the Rift guide on entering the Rift
		String island = Waypoints.currentIsland();
		if (island == null) island = "";
		if (!island.equals(lastIsland)) {
			lastIsland = island;
			if (island.equals("THE_RIFT") && Feat.on(Features.RIFT_AUTO_GUIDE) && Waypoints.guide() == null && api != null) {
				api.guides(sp.lang().code()).whenComplete((g, err) -> mc.execute(() -> {
					if (g == null) return;
					for (Dto.Guide guide : g.guides()) {
						if (!guide.id().equals("the_rift")) continue;
						int first = 0;
						for (int i = 0; i < guide.steps().size(); i++) {
							if (!Boolean.TRUE.equals(guide.steps().get(i).done())) {
								first = i;
								break;
							}
						}
						Waypoints.follow(guide, first);
					}
				}));
			}
		}
		// puzzle rules for the dungeon puzzle list
		if (Feat.on(Features.DUNGEONS_PUZZLES) && puzzles.isEmpty() && api != null && now - puzzlesTriedAt > 60_000) {
			puzzlesTriedAt = now;
			api.puzzles(sp.lang().code()).whenComplete((list, err) -> mc.execute(() -> {
				if (list != null) setPuzzles(list);
			}));
		}
	}

	static void setPuzzles(Dto.Puzzle[] list) {
		Map<String, Dto.Puzzle> m = new HashMap<>();
		for (Dto.Puzzle p : list) m.put(p.name().toLowerCase(Locale.ROOT), p);
		puzzles = m;
	}

	/** The rules of a puzzle named in the tab list ("Three Weirdos: [✔]" → Three Weirdos). */
	static Dto.Puzzle puzzle(String tabEntry) {
		String name = dev.skypilot.mod.core.TabSections.label(tabEntry).toLowerCase(Locale.ROOT);
		return puzzles.get(name);
	}
}
