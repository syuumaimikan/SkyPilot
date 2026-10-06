package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI-style item list to the right of inventory windows: search all SkyBlock items, see values
 * and open an item's recipes / drop sources / uses. Our own panel - it never touches the
 * window's slots. Search text and page are kept while the game runs.
 */
final class ItemBrowser {
	static final int CELL = 18;
	private static final int SEARCH_H = 14;
	private static String query = "";
	private static int page;
	private static Dto.ItemPage data;
	private static String dataKey;
	private static String requestedKey;
	private static long lastRequest;
	private static long queryChangedAt;
	private static String error;
	/** Tests show fixed data and must not be overwritten by a live request. */
	private static boolean frozen;

	private final Screen screen;
	private boolean focused;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int cols;
	private int rows;

	ItemBrowser(Screen screen) {
		this.screen = screen;
	}

	/** Lay the panel out between {@code left} (right edge of the window + gap) and the screen edge. */
	boolean layout(int left) {
		x0 = left;
		x1 = screen.width - 4;
		y0 = 4;
		y1 = screen.height - 4;
		cols = (x1 - x0 - 6) / CELL;
		rows = (y1 - y0 - SEARCH_H - 26) / CELL;
		return SkyPilot.get().config().inventory.itemBrowser && cols >= 2 && rows >= 2;
	}

	private int perPage() {
		return Math.max(1, cols * rows);
	}

	private int gridX() {
		return x0 + 3 + (x1 - x0 - 6 - cols * CELL) / 2;
	}

	private int gridY() {
		return y0 + SEARCH_H + 8;
	}

	private void request() {
		String key = query + "|" + page * perPage() + "|" + perPage();
		long now = System.currentTimeMillis();
		if (frozen || key.equals(dataKey) || key.equals(requestedKey) || now - queryChangedAt < 250 || now - lastRequest < 150) return;
		ApiClient api = SkyPilot.get().api();
		if (api == null) {
			error = SkyPilot.get().apiError();
			return;
		}
		requestedKey = key;
		lastRequest = now;
		api.items(query, page * perPage(), perPage()).whenComplete((p, err) -> Minecraft.getInstance().execute(() -> {
			if (!key.equals(requestedKey)) return;
			if (p != null) {
				data = p;
				dataKey = key;
				error = null;
			} else {
				error = SkyPilot.shortError(err);
			}
			requestedKey = null;
		}));
	}

	void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		request();
		Lang l = SkyPilot.get().lang();
		g.fill(x0, y0, x1, y1, 0xB0101018);
		g.outline(x0, y0, x1 - x0, y1 - y0, Ui.PANEL_BORDER);
		// search box
		int sx = x0 + 3;
		int sy = y0 + 3;
		int sw = x1 - x0 - 6;
		g.fill(sx, sy, sx + sw, sy + SEARCH_H, focused ? 0xFF000000 : 0xFF1A1A24);
		g.outline(sx, sy, sw, SEARCH_H, focused ? Ui.WHITE : 0xFF555566);
		String shown = query.isEmpty() && !focused ? l.t("Search items…", "アイテム検索…") : query + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		g.text(font, font.plainSubstrByWidth(shown, sw - 6), sx + 3, sy + 3, query.isEmpty() && !focused ? Ui.DARK_GRAY : Ui.WHITE, false);
		Dto.ItemPage p = data;
		Dto.ItemEntry hovered = null;
		if (p != null) {
			int gx = gridX();
			int gy = gridY();
			for (int i = 0; i < p.items().size() && i < perPage(); i++) {
				Dto.ItemEntry e = p.items().get(i);
				int cx = gx + (i % cols) * CELL;
				int cy = gy + (i / cols) * CELL;
				boolean over = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
				if (over) {
					g.fill(cx, cy, cx + CELL, cy + CELL, 0x50FFFFFF);
					hovered = e;
				}
				g.item(ItemIcons.of(e.icon()), cx + 1, cy + 1);
			}
			int pages = Math.max(1, (p.total() + perPage() - 1) / perPage());
			String footer = "◀  " + (page + 1) + " / " + pages + "  ▶";
			g.centeredText(font, footer, (x0 + x1) / 2, y1 - 12, Ui.GRAY);
			if (p.items().isEmpty()) g.text(font, l.t("No items found", "見つかりません"), x0 + 6, gridY() + 2, Ui.GRAY, false);
		} else {
			g.textWithWordWrap(font, Component.literal(error != null ? error : l.t("Loading…", "読み込み中…")), x0 + 6, gridY() + 2, x1 - x0 - 12, error != null ? Ui.RED : Ui.GRAY);
		}
		if (hovered != null) {
			List<Component> tip = new ArrayList<>();
			tip.add(Component.literal(hovered.name()).withStyle(Style.EMPTY.withColor(Ui.rarityColor(hovered.rarity()) & 0xFFFFFF)));
			tip.add(Component.literal(hovered.value() != null ? l.t("Value ", "価値 ") + Text.coins(hovered.value()) : l.t("No market price", "市場価格なし")).withStyle(Style.EMPTY.withColor(Ui.GOLD & 0xFFFFFF)));
			tip.add(Component.literal(l.t("Click: recipes, drops, uses", "クリック: レシピ・入手方法・使い道")).withStyle(Style.EMPTY.withColor(Ui.GRAY & 0xFFFFFF)));
			net.minecraft.network.chat.MutableComponent joined = Component.empty();
			for (int i = 0; i < tip.size(); i++) {
				if (i > 0) joined.append("\n");
				joined.append(tip.get(i));
			}
			Ui.tooltip(g, joined, mouseX, mouseY);
		}
	}

	boolean contains(double mx, double my) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	/** Returns true when the click was ours (the window must not see it). */
	boolean click(double mx, double my, int button) {
		if (!contains(mx, my)) {
			focused = false;
			return false;
		}
		int sx = x0 + 3;
		int sy = y0 + 3;
		if (my >= sy && my < sy + SEARCH_H) {
			focused = true;
			if (button == 1) setQuery("");
			return true;
		}
		focused = false;
		if (my >= y1 - 14) {
			if (mx < (x0 + x1) / 2.0) prevPage();
			else nextPage();
			return true;
		}
		Dto.ItemPage p = data;
		if (p == null) return true;
		int col = (int) Math.floor((mx - gridX()) / CELL);
		int row = (int) Math.floor((my - gridY()) / CELL);
		if (col < 0 || col >= cols || row < 0 || row >= rows) return true;
		int i = row * cols + col;
		if (i < p.items().size()) {
			String id = p.items().get(i).id();
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> mc.gui.setScreen(new ItemDetailScreen(screen, id)));
		}
		return true;
	}

	boolean scroll(double mx, double my, double dy) {
		if (!contains(mx, my)) return false;
		if (dy > 0) prevPage();
		else if (dy < 0) nextPage();
		return true;
	}

	private void prevPage() {
		if (page > 0) page--;
	}

	private void nextPage() {
		Dto.ItemPage p = data;
		int pages = p == null ? 1 : Math.max(1, (p.total() + perPage() - 1) / perPage());
		if (page + 1 < pages) page++;
	}

	/** The current search text (shared by all windows). */
	static String query() {
		return query;
	}

	private static void setQuery(String q) {
		query = q;
		page = 0;
		queryChangedAt = System.currentTimeMillis();
	}

	/** While the search box is focused every key is ours (so "E" types instead of closing). */
	boolean key(KeyEvent e) {
		if (!focused) return false;
		switch (e.key()) {
			case 256, 257, 335 -> focused = false; // escape, enter, keypad enter
			case 259 -> { // backspace
				if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
			}
			default -> {
			}
		}
		return true;
	}

	boolean charTyped(CharacterEvent e) {
		if (!focused) return false;
		if (e.isAllowedChatCharacter() && query.length() < 40) setQuery(query + e.codepointAsString());
		return true;
	}

	/** For tests. */
	static void setData(Dto.ItemPage p, String q) {
		query = q;
		page = 0;
		data = p;
		dataKey = null;
		requestedKey = null;
		frozen = p != null;
	}
}
