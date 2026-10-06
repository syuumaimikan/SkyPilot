package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
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
	private static final int CHIP_Y = 17;
	private static final int CHIP_H = 11;
	/** Where the last panel was drawn (clicks are matched against it). */
	private static int lastX, lastY, lastW, lastH;
	private static String lastKind;
	private static List<Dto.FlipRow> lastRows = List.of();

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

	static String sortLabel(String sort, Lang l) {
		return switch (sort) {
			case "profit" -> l.t("Profit", "利益");
			case "roi" -> l.t("ROI", "利益率");
			case "hour" -> l.t("Per hour", "時給");
			default -> l.t("Best", "おすすめ");
		};
	}

	private static String affordLabel(Lang l) {
		return l.t("Purse only", "所持金内");
	}

	/**
	 * A click on the panel: the sort chip cycles the order, the purse chip limits flips to what
	 * you can pay for, and a flip row puts its command (/viewauction …, /bz …) into the chat box
	 * - sending it stays your choice. Returns true when the click was ours.
	 */
	static boolean click(String kind, double mx, double my) {
		if (lastKind == null || !lastKind.equals(kind) || mx < lastX || mx >= lastX + lastW || my < lastY || my >= lastY + lastH) return false;
		SkyPilot sp = SkyPilot.get();
		SkyPilotConfig.Market m = sp.config().market;
		Font font = Minecraft.getInstance().font;
		Lang l = sp.lang();
		int sortW = font.width(sortLabel(m.flipSort, l) + " ▾") + 6;
		if (my >= lastY + CHIP_Y && my < lastY + CHIP_Y + CHIP_H) {
			if (mx < lastX + 4 + sortW) {
				int i = SkyPilotConfig.FLIP_SORTS.indexOf(m.flipSort);
				m.flipSort = SkyPilotConfig.FLIP_SORTS.get((i + 1) % SkyPilotConfig.FLIP_SORTS.size());
			} else {
				m.affordableOnly = !m.affordableOnly;
			}
			sp.saveConfig();
			MarketData.refresh(kind, false);
			Feat.ping(1.4f);
			return true;
		}
		int rowY = lastY + CHIP_Y + CHIP_H + 4;
		int i = (int) Math.floor((my - rowY) / ROW_H);
		if (my >= rowY && i >= 0 && i < lastRows.size() && lastRows.get(i).command() != null) Ui.suggest(lastRows.get(i).command());
		return true;
	}

	/** Draws the panel at (x, y) with size w×h. */
	static void draw(GuiGraphicsExtractor g, Font font, Lang l, String kind, int x, int y, int w, int h, int mouseX, int mouseY) {
		g.fill(x, y, x + w, y + h, Ui.PANEL);
		g.outline(x, y, w, h, Ui.PANEL_BORDER);
		String title = MarketData.AH.equals(kind) ? l.t("SkyPilot · AH flips", "SkyPilot · AHフリップ") : l.t("SkyPilot · Bazaar flips", "SkyPilot · バザーフリップ");
		g.text(font, title, x + 5, y + 5, Ui.PURPLE, false);
		SkyPilotConfig.Market m = SkyPilot.get().config().market;
		String sortText = sortLabel(m.flipSort, l) + " ▾";
		int sortW = font.width(sortText) + 6;
		chip(g, font, sortText, x + 4, y + CHIP_Y, sortW, true, mouseX, mouseY);
		String purse = affordLabel(l);
		chip(g, font, purse, x + 6 + sortW, y + CHIP_Y, font.width(purse) + 6, m.affordableOnly, mouseX, mouseY);
		lastX = x;
		lastY = y;
		lastW = w;
		lastH = h;
		lastKind = kind;
		lastRows = List.of();
		Dto.MarketPanel panel = MarketData.get(kind);
		int rowY = y + CHIP_Y + CHIP_H + 4;
		int footer = y + h - 12;
		if (panel == null) {
			String err = MarketData.error(kind);
			g.textWithWordWrap(font, Component.literal(err != null ? err : l.t("Loading…", "読み込み中…")), x + 5, rowY, w - 10, Ui.GRAY);
			return;
		}
		List<Dto.FlipRow> rows = panel.rows().stream().filter(r -> !r.doNotFlip()).toList();
		lastRows = rows;
		Dto.FlipRow hovered = null;
		for (Dto.FlipRow r : rows) {
			if (rowY + ROW_H > footer) break;
			g.item(ItemIcons.of(r.icon()), x + 4, rowY + 1);
			g.text(font, font.plainSubstrByWidth(r.name(), w - 28), x + 23, rowY + 1, Ui.rarityColor(r.rarity()), false);
			// narrow panel: profit first, the full line is in the tooltip
			String line = r.profitPerHour() != null && "hour".equals(m.flipSort)
					? "+" + Text.coins(r.profitPerHour()) + l.t("/h", "/時") + " · ×" + Text.coins(r.amount() == null ? 0 : r.amount())
					: "+" + Text.coins(r.profit()) + " (" + Ui.pct(r.roi()) + ") · " + Text.coins(r.buy());
			g.text(font, font.plainSubstrByWidth(line, w - 28), x + 23, rowY + 10, Ui.GREEN, false);
			if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H) hovered = r;
			rowY += ROW_H;
		}
		if (rows.isEmpty()) {
			String empty = m.affordableOnly && MarketData.budget() <= 0 ? l.t("Purse not visible on the sidebar", "サイドバーに所持金が見えません")
					: l.t("No flips right now", "現在フリップはありません");
			g.textWithWordWrap(font, Component.literal(empty), x + 5, rowY, w - 10, Ui.GRAY);
		}
		String age = panel.stale() ? l.t("STALE DATA", "データが古い") : l.t("data ", "データ ") + panel.ageSeconds() + l.t("s old", "秒前");
		g.text(font, font.plainSubstrByWidth(age + " · " + l.t("info only", "表示のみ"), w - 10), x + 5, footer, panel.stale() ? Ui.RED : Ui.DARK_GRAY, false);
		if (hovered != null) {
			String notes = hovered.notes() == null || hovered.notes().isEmpty() ? "" : "\n" + String.join("\n", hovered.notes());
			String tip = hovered.name() + "\n" + Ui.flipLine(hovered) + "\n" + Ui.flipDetail(hovered, l) + notes
					+ (hovered.command() != null ? "\n" + l.t("Click: put ", "クリック: ") + hovered.command() + l.t(" into the chat box", " をチャット欄に入力") : "")
					+ "\n" + l.t("SkyPilot menu: /skypilot menu", "詳細: /skypilot menu");
			Ui.overlayTooltip(g, Component.literal(tip), mouseX, mouseY);
		}
	}

	private static void chip(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, boolean on, int mouseX, int mouseY) {
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CHIP_H;
		g.fill(x, y, x + w, y + CHIP_H, on ? 0xFF5B47B8 : over ? 0xFF2E2A44 : 0xFF1E1C2C);
		g.text(font, text, x + 3, y + 2, on ? Ui.WHITE : Ui.GRAY, false);
	}
}
