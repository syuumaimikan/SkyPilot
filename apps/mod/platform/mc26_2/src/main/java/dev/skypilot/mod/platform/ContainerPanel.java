package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Optional panel beside Hypixel's Auction House / Bazaar windows listing SkyPilot's current
 * flips. Drawn only - it reads the window title, never the window's items, and never clicks.
 */
final class ContainerPanel {
	static final int ROW_H = 20;
	private static final int MIN_W = 100;
	private static final int MAX_W = 200;

	private ContainerPanel() {
	}

	/** Which flip list belongs to a window title ("Auction House", "Auctions Browser", "Bazaar ➜ Mining"…). */
	static String kindOf(String title) {
		String t = title.toLowerCase(Locale.ROOT);
		if (t.contains("auction")) return MarketData.AH;
		if (t.contains("bazaar")) return MarketData.BAZAAR;
		return null;
	}

	static void render(Screen screen, String kind, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		SkyPilot sp = SkyPilot.get();
		if (!sp.config().market.containerPanel || !(screen instanceof ContainerScreenAccessor box)) return;
		MarketData.refresh(kind, false);
		int left = box.skypilot$leftPos();
		int w = Math.min(MAX_W, left - 8);
		if (w < MIN_W) return;
		int x = left - w - 4;
		int y = Math.max(4, box.skypilot$topPos());
		int h = Math.max(box.skypilot$imageHeight(), 120);
		draw(g, Minecraft.getInstance().font, sp.lang(), kind, x, y, w, h, mouseX, mouseY);
	}

	/** Draws the panel at (x, y) with size w×h. */
	static void draw(GuiGraphicsExtractor g, Font font, Lang l, String kind, int x, int y, int w, int h, int mouseX, int mouseY) {
		g.fill(x, y, x + w, y + h, Ui.PANEL);
		g.outline(x, y, w, h, Ui.PANEL_BORDER);
		String title = MarketData.AH.equals(kind) ? l.t("SkyPilot · AH flips", "SkyPilot · AHフリップ") : l.t("SkyPilot · Bazaar flips", "SkyPilot · バザーフリップ");
		g.text(font, title, x + 5, y + 5, Ui.PURPLE, false);
		Dto.MarketPanel panel = MarketData.get(kind);
		int rowY = y + 18;
		int footer = y + h - 12;
		if (panel == null) {
			String err = MarketData.error(kind);
			g.textWithWordWrap(font, Component.literal(err != null ? err : l.t("Loading…", "読み込み中…")), x + 5, rowY, w - 10, Ui.GRAY);
			return;
		}
		List<Dto.FlipRow> rows = panel.rows().stream().filter(r -> !r.doNotFlip()).toList();
		Dto.FlipRow hovered = null;
		for (Dto.FlipRow r : rows) {
			if (rowY + ROW_H > footer) break;
			g.item(ItemIcons.of(r.icon()), x + 4, rowY + 1);
			g.text(font, font.plainSubstrByWidth(r.name(), w - 28), x + 23, rowY + 1, Ui.rarityColor(r.rarity()), false);
			// narrow panel: profit first, the full line is in the tooltip
			String line = "+" + Text.coins(r.profit()) + " (" + Ui.pct(r.roi()) + ") · " + Text.coins(r.buy());
			g.text(font, font.plainSubstrByWidth(line, w - 28), x + 23, rowY + 10, Ui.GREEN, false);
			if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H) hovered = r;
			rowY += ROW_H;
		}
		if (rows.isEmpty()) g.text(font, l.t("No flips right now", "現在フリップはありません"), x + 5, rowY, Ui.GRAY, false);
		String age = panel.stale() ? l.t("STALE DATA", "データが古い") : l.t("data ", "データ ") + panel.ageSeconds() + l.t("s old", "秒前");
		g.text(font, font.plainSubstrByWidth(age + " · " + l.t("info only", "表示のみ"), w - 10), x + 5, footer, panel.stale() ? Ui.RED : Ui.DARK_GRAY, false);
		if (hovered != null) {
			String tip = hovered.name() + "\n" + Ui.flipLine(hovered) + "\n" + Ui.flipDetail(hovered, l) + (hovered.command() != null ? "\n" + hovered.command() : "")
					+ "\n" + l.t("SkyPilot menu: /skypilot menu", "詳細: /skypilot menu");
			g.setTooltipForNextFrame(Component.literal(tip), mouseX, mouseY);
		}
	}
}
