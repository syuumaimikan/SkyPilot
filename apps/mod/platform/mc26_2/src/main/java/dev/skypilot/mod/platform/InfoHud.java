package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ActionBarStats;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyBlockTime;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.hud.HudLayout;
import dev.skypilot.mod.hud.HudRow;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The second HUD box: one line (or bar) per enabled info feature. Movable in the HUD editor;
 * anchored to the right edge when its x is negative. Also draws the low-health border.
 */
final class InfoHud {
	private InfoHud() {
	}

	static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		SkyPilot sp = SkyPilot.get();
		Minecraft mc = Minecraft.getInstance();
		if (sp == null || mc.player == null || mc.gui.screen() instanceof HudEditorScreen) return;
		lowHealth(g, mc);
		List<HudRow> rows = rows(sp, mc, System.currentTimeMillis());
		if (!rows.isEmpty()) draw(g, rows, sp.config().infoHud, HudRenderer.lastBox, g.guiWidth(), g.guiHeight(), false);
	}

	static HudLayout.Box draw(GuiGraphicsExtractor g, List<HudRow> rows, SkyPilotConfig.InfoHud cfg, HudLayout.Box mainHud, int screenW, int screenH, boolean outline) {
		var font = Minecraft.getInstance().font;
		int width = 0;
		for (HudRow r : rows) width = Math.max(width, font.width(r.text()));
		width = Math.min(width, 260) + HudLayout.PADDING * 2;
		int x = cfg.x >= 0 ? cfg.x : screenW - (int) Math.round(width * cfg.scale) + cfg.x;
		int y = cfg.y >= 0 ? cfg.y : mainHud != null ? mainHud.y() + mainHud.height() + 4 : 4;
		return HudRenderer.draw(g, font, rows, Math.max(0, x), y, cfg.scale, cfg.opacity, 260, screenW, screenH, outline);
	}

	/** Lines of every enabled info feature, in a fixed order. */
	static List<HudRow> rows(SkyPilot sp, Minecraft mc, long now) {
		Lang l = sp.lang();
		List<HudRow> out = new ArrayList<>();
		if (Feat.on(Features.HUD_COORDS)) {
			var p = mc.player;
			out.add(HudRow.text(String.format(Locale.ROOT, "XYZ %.0f / %.0f / %.0f  %s", p.getX(), p.getY(), p.getZ(), facing(p.getYRot(), l)), HudRow.WHITE));
		}
		if (Feat.on(Features.HUD_FPS)) {
			PlayerInfo me = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
			out.add(HudRow.text(mc.getFps() + " FPS" + (me != null ? "  " + me.getLatency() + " ms" : ""), HudRow.MUTED));
		}
		if (Feat.on(Features.HUD_CLOCK)) {
			LocalTime t = LocalTime.now();
			SkyBlockTime sb = Timers.time();
			out.add(HudRow.text(String.format("%02d:%02d", t.getHour(), t.getMinute()) + (sb != null ? "  ·  " + sb.format(now) : ""), HudRow.WHITE));
		}
		if (Feat.on(Features.HUD_EVENTS)) {
			List<Timers.Upcoming> next = Timers.upcoming(now);
			if (next.isEmpty()) out.add(HudRow.text(l.t("Events: loading…", "イベント: 読み込み中…"), HudRow.MUTED));
			int n = 0;
			for (Timers.Upcoming u : next) {
				if (n++ >= 4) break;
				boolean active = u.active(now);
				out.add(HudRow.text(u.name() + ": " + (active ? l.t("now (", "開催中（残り ") + eta(u.endMs() - now) + l.t(" left)", "）") : eta(u.startMs() - now)),
						active ? HudRow.GOOD : HudRow.MUTED));
			}
		}
		if (Feat.on(Features.HUD_STATS) && Trackers.stats != null && now - Trackers.statsAt < 10_000) {
			ActionBarStats.Stats s = Trackers.stats;
			if (s.health() != null && s.maxHealth() != null && s.maxHealth() > 0) {
				out.add(HudRow.bar("❤ " + Text.coins(s.health()) + " / " + Text.coins(s.maxHealth()) + (s.defense() != null ? "   ❈ " + s.defense() : ""), 0xFFFF5555,
						(double) s.health() / s.maxHealth()));
			}
			if (s.mana() != null && s.maxMana() != null && s.maxMana() > 0) {
				out.add(HudRow.bar("✎ " + Text.coins(s.mana()) + " / " + Text.coins(s.maxMana()), 0xFF55FFFF, (double) s.mana() / s.maxMana()));
			}
		}
		if (Feat.on(Features.HUD_SKILL) && Trackers.skill != null && now - Trackers.skillAt < 120_000) {
			ActionBarStats.SkillGain s = Trackers.skill;
			double perHour = Trackers.skillPerHour(s.skill());
			String text = s.skill() + (s.percent() != null ? String.format(Locale.ROOT, " %.1f%%", s.percent()) : "") + "  " + Text.coins(perHour) + l.t(" XP/h", " XP/時");
			if (s.current() != null && s.needed() != null && perHour > 0 && s.needed() > s.current()) {
				text += l.t("  next in ", "  次まで ") + eta((long) ((s.needed() - s.current()) / perHour * 3_600_000));
			}
			out.add(s.percent() != null ? HudRow.bar(text, 0xFF55FFFF, s.percent() / 100) : HudRow.text(text, 0xFF55FFFF));
		}
		if (Feat.on(Features.HUD_INV_FULL) && Trackers.inventoryFull(mc)) out.add(HudRow.text(l.t("⚠ Inventory full", "⚠ インベントリが満杯"), HudRow.BAD));
		if (Feat.on(Features.MINING_COMMISSIONS) && !Trackers.commissions.isEmpty()) {
			out.add(HudRow.text(l.t("Commissions", "コミッション"), HudRow.ACCENT));
			for (String c : Trackers.commissions) out.add(HudRow.text(" " + c, c.endsWith("DONE") ? HudRow.GOOD : HudRow.WHITE));
		}
		if (Feat.on(Features.MINING_POWDER) && !Trackers.powders.isEmpty()) {
			for (String p : Trackers.powders) {
				double rate = Trackers.powderPerHour(p);
				out.add(HudRow.text(p + (Double.isNaN(rate) ? "" : "  (+" + Text.coins(rate) + l.t("/h)", "/時)")), 0xFF55FFFF));
			}
		}
		if (Feat.on(Features.FARMING_BPS)) {
			double bps = Trackers.bps();
			if (!Double.isNaN(bps)) out.add(HudRow.text(String.format(Locale.ROOT, "BPS %.1f", bps), bps >= 19 ? HudRow.GOOD : bps >= 15 ? HudRow.WARN : HudRow.WHITE));
		}
		if (Feat.on(Features.COMBAT_SLAYER_TRACKER) && (Trackers.slayerKills > 0 || Trackers.questStartedAt > 0)) {
			String avg = Trackers.slayerKills > 0 ? eta(Trackers.questTotalMs / Trackers.slayerKills) : "-";
			double hours = Math.max(1.0 / 60, (now - Trackers.slayerSessionStart) / 3_600_000.0);
			out.add(HudRow.text(l.t("Slayer: ", "スレイヤー: ") + Trackers.slayerKills + l.t(" bosses · avg ", "体 · 平均 ") + avg + " · "
					+ String.format(Locale.ROOT, "%.1f", Trackers.slayerKills / hours) + l.t("/h", "/時"), 0xFFFF5555));
		}
		if (Feat.on(Features.COMBAT_DROP_TRACKER) && Trackers.dropTotal > 0) {
			out.add(HudRow.text(l.t("Drops: ", "ドロップ: ") + Trackers.dropTotal, HudRow.WARN));
			Trackers.DROPS.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(4)
					.forEach(e -> out.add(HudRow.text(" " + e.getKey() + " ×" + e.getValue(), HudRow.WHITE)));
		}
		if (Feat.on(Features.TOOLS_REMINDERS) && !Timers.REMINDERS.isEmpty()) {
			Timers.Reminder r = Timers.REMINDERS.get(0);
			out.add(HudRow.text("⏰ " + eta(r.dueAt() - now) + "  " + r.text(), HudRow.MUTED));
		}
		return out;
	}

	private static void lowHealth(GuiGraphicsExtractor g, Minecraft mc) {
		if (!Feat.on(Features.HUD_LOW_HEALTH) || mc.player.getMaxHealth() <= 0) return;
		ActionBarStats.Stats s = Trackers.stats;
		double ratio = s != null && s.health() != null && s.maxHealth() != null && s.maxHealth() > 0 && System.currentTimeMillis() - Trackers.statsAt < 5_000
				? (double) s.health() / s.maxHealth() : mc.player.getHealth() / mc.player.getMaxHealth();
		if (ratio >= 0.25) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int edge = 0x90FF0000;
		g.fillGradient(0, 0, w, 24, edge, 0x00FF0000);
		g.fillGradient(0, h - 24, w, h, 0x00FF0000, edge);
		g.fill(0, 0, 6, h, 0x60FF0000);
		g.fill(w - 6, 0, w, h, 0x60FF0000);
	}

	static String facing(float yaw, Lang l) {
		float y = ((yaw % 360) + 360) % 360;
		String[] en = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		String[] ja = {"南", "南西", "西", "北西", "北", "北東", "東", "南東"};
		int i = Math.round(y / 45f) % 8;
		return l.t(en[i], ja[i]);
	}

	/** "2d 4h", "1h 05m", "4m 09s", "12s". */
	static String eta(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s >= 86_400) return s / 86_400 + "d " + (s % 86_400) / 3600 + "h";
		if (s >= 3600) return s / 3600 + "h " + String.format("%02dm", (s % 3600) / 60);
		if (s >= 60) return s / 60 + "m " + String.format("%02ds", s % 60);
		return s + "s";
	}
}
