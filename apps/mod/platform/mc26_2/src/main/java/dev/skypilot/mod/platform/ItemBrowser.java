package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JEI-style item list to the right of inventory windows: search all SkyBlock items, filter by
 * category, hover for the description and prices, click (or R / U) for recipes, drop sources
 * and uses. The left edge can be dragged to resize the list or push it into the screen edge.
 * Our own panel - it never touches the window's slots. Search text, page, width and category
 * are kept while the game runs (width and category in the config).
 */
final class ItemBrowser {
	static final int CELL = 18;
	private static final int SEARCH_H = 14;
	private static final int CHIP_H = 11;
	private static final int FOOTER_H = 13;
	private static final int MIN_W = 2 * CELL + 8;
	private static final int TAB_W = 11;
	private static final int TAB_H = 56;
	/** Most cells the server returns in one page. */
	private static final int MAX_PAGE = 600;
	private static final long HOVER_DELAY_MS = 120;

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
	/** Hover cards (description and prices), most recently used last. */
	private static final Map<String, Dto.ItemDetail> CARDS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Dto.ItemDetail> e) {
			return size() > 300;
		}
	};
	private static final Set<String> CARD_PENDING = new HashSet<>();

	private record Chip(String id, int x, int y, int w) {
	}

	private final Screen screen;
	private boolean focused;
	private int minX;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int cols;
	private int rows;
	private int gridTop;
	private final List<Chip> chips = new ArrayList<>();
	private boolean collapsed;
	private boolean dragging;
	private double dragStartX;
	private int dragWidth;
	private String hoverId;
	private long hoverSince;

	ItemBrowser(Screen screen) {
		this.screen = screen;
	}

	private static SkyPilotConfig.Inventory cfg() {
		return SkyPilot.get().config().inventory;
	}

	/** Lay the panel out between {@code left} (right edge of the window + gap) and the screen edge. */
	boolean layout(int left) {
		SkyPilotConfig.Inventory c = cfg();
		if (!c.itemBrowser) return false;
		minX = left;
		x1 = screen.width - 4;
		y0 = 4;
		y1 = screen.height - 4;
		int avail = x1 - left;
		if (avail < MIN_W || y1 - y0 < 80) return false;
		collapsed = !dragging && c.itemBrowserCollapsed;
		int w = dragging ? dragWidth : c.itemBrowserWidth;
		if (w <= 0 || w > avail) w = avail;
		x0 = x1 - Math.max(MIN_W, w);
		layoutChips(Minecraft.getInstance().font, SkyPilot.get().lang());
		if(SkyPilot.get().config().compactInfo)y1=Math.min(y1,gridTop+12*CELL+FOOTER_H);
		cols = (x1 - x0 - 6) / CELL;
		rows = Math.max(0, (y1 - FOOTER_H - gridTop) / CELL);
		return cols >= 2 && rows >= 1;
	}

	private void layoutChips(Font font, Lang l) {
		chips.clear();
		int cx = x0 + 3;
		int cy = y0 + 3 + SEARCH_H + 3;
		for (String id : SkyPilotConfig.ITEM_CATEGORIES) {
			int w = font.width(categoryLabel(id, l)) + 6;
			if (cx + w > x1 - 3 && cx > x0 + 3) {
				cx = x0 + 3;
				cy += CHIP_H + 2;
			}
			chips.add(new Chip(id, cx, cy, w));
			cx += w + 2;
		}
		gridTop = cy + CHIP_H + 4;
	}

	static String categoryLabel(String id, Lang l) {
		return switch (id) {
			case "weapons" -> l.t("Weapons", "武器");
			case "armor" -> l.t("Armor", "防具");
			case "equipment" -> l.t("Equipment", "装備品");
			case "accessories" -> l.t("Accessories", "アクセサリー");
			case "tools" -> l.t("Tools", "道具");
			case "materials" -> l.t("Materials", "素材");
			case "minions" -> l.t("Minions", "ミニオン");
			case "pet_items" -> l.t("Pet items", "ペットアイテム");
			case "reforge_stones" -> l.t("Reforge stones", "リフォージ石");
			case "cosmetics" -> l.t("Cosmetics", "コスメ");
			case "other" -> l.t("Other", "その他");
			default -> l.t("All", "すべて");
		};
	}

	private int perPage() {
		return Math.max(1, Math.min(MAX_PAGE, cols * rows));
	}

	private int gridX() {
		return x0 + 3 + (x1 - x0 - 6 - cols * CELL) / 2;
	}

	private static String category() {
		return cfg().itemBrowserCategory;
	}

	private void request() {
		String key = query + "|" + category() + "|" + page * perPage() + "|" + perPage();
		long now = System.currentTimeMillis();
		if (frozen || key.equals(dataKey) || key.equals(requestedKey) || now - queryChangedAt < 250 || now - lastRequest < 150) return;
		ApiClient api = SkyPilot.get().api();
		if (api == null) {
			error = SkyPilot.get().apiError();
			return;
		}
		requestedKey = key;
		lastRequest = now;
		api.items(query, category(), page * perPage(), perPage()).whenComplete((p, err) -> Minecraft.getInstance().execute(() -> {
			if (!key.equals(requestedKey)) return;
			if (p != null) {
				data = p;
				dataKey = key;
				error = null;
				int pages = Math.max(1, (p.total() + perPage() - 1) / perPage());
				if (page >= pages) page = pages - 1;
			} else {
				error = SkyPilot.shortError(err);
			}
			requestedKey = null;
		}));
	}

	/** The hover card for {@code id}: cached, or requested once the pointer has rested on it. */
	private static Dto.ItemDetail card(String id, boolean request) {
		Dto.ItemDetail d = CARDS.get(id);
		if (d != null || !request || frozen || CARD_PENDING.contains(id) || CARD_PENDING.size() > 4) return d;
		ApiClient api = SkyPilot.get().api();
		if (api == null) return null;
		CARD_PENDING.add(id);
		api.itemBrief(id).whenComplete((r, err) -> Minecraft.getInstance().execute(() -> {
			CARD_PENDING.remove(id);
			if (r != null) CARDS.put(id, r);
		}));
		return null;
	}

	void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		Lang l = SkyPilot.get().lang();
		if (collapsed) {
			renderTab(g, font, mouseX, mouseY, l);
			return;
		}
		request();
		g.fill(x0, y0, x1, y1, 0xB0101018);
		g.outline(x0, y0, x1 - x0, y1 - y0, Ui.PANEL_BORDER);
		// resize grip on the left edge
		boolean onGrip = dragging || overGrip(mouseX, mouseY);
		int midY = (y0 + y1) / 2;
		g.fill(x0 - 1, midY - 14, x0 + 2, midY + 14, onGrip ? 0xFFB9A7FF : 0xFF4A3B8C);
		for (int i = -8; i <= 8; i += 4) g.fill(x0, midY + i, x0 + 1, midY + i + 2, onGrip ? Ui.WHITE : 0xFF8877CC);
		// search box
		int sx = x0 + 3;
		int sy = y0 + 3;
		int sw = x1 - x0 - 6;
		g.fill(sx, sy, sx + sw, sy + SEARCH_H, focused ? 0xFF000000 : 0xFF1A1A24);
		g.outline(sx, sy, sw, SEARCH_H, focused ? Ui.WHITE : 0xFF555566);
		String shown = query.isEmpty() && !focused ? l.t("Search items… (Ctrl+F)", "アイテム検索…（Ctrl+F）") : query + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		g.text(font, font.plainSubstrByWidth(shown, sw - 6), sx + 3, sy + 3, query.isEmpty() && !focused ? Ui.DARK_GRAY : Ui.WHITE, false);
		// categories
		for (Chip c : chips) {
			boolean sel = c.id().equals(category());
			boolean over = mouseX >= c.x() && mouseX < c.x() + c.w() && mouseY >= c.y() && mouseY < c.y() + CHIP_H;
			g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + CHIP_H, sel ? 0xFF5B47B8 : over ? 0xFF2E2A44 : 0xFF1E1C2C);
			g.text(font, categoryLabel(c.id(), l), c.x() + 3, c.y() + 2, sel ? Ui.WHITE : over ? 0xFFDDDDDD : Ui.GRAY, false);
		}
		if (dragging && dragWidth < MIN_W) {
			g.centeredText(font, l.t("Release to hide", "離すと隠します"), (x0 + x1) / 2, gridTop + 4, Ui.YELLOW);
			return;
		}
		Dto.ItemPage p = data;
		Dto.ItemEntry hovered = null;
		if (p != null) {
			int gx = gridX();
			for (int i = 0; i < p.items().size() && i < perPage(); i++) {
				Dto.ItemEntry e = p.items().get(i);
				int cx = gx + (i % cols) * CELL;
				int cy = gridTop + (i / cols) * CELL;
				boolean over = !dragging && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
				if (over) {
					g.fill(cx, cy, cx + CELL, cy + CELL, 0x50FFFFFF);
					hovered = e;
				}
				g.item(ItemIcons.of(e.icon()), cx + 1, cy + 1);
			}
			int pages = Math.max(1, (p.total() + perPage() - 1) / perPage());
			g.centeredText(font, "◀  " + (page + 1) + " / " + pages + "  ▶", (x0 + x1) / 2, y1 - 11, Ui.GRAY);
			String count = String.format(Locale.ROOT, "%,d", p.total());
			if (font.width(count) + font.width("◀  99 / 99  ▶") / 2 + 12 < (x1 - x0) / 2) g.text(font, count, x0 + 5, y1 - 11, Ui.DARK_GRAY, false);
			if (p.items().isEmpty()) g.text(font, l.t("No items found", "見つかりません"), x0 + 6, gridTop + 2, Ui.GRAY, false);
		} else {
			g.textWithWordWrap(font, Component.literal(error != null ? error : l.t("Loading…", "読み込み中…")), x0 + 6, gridTop + 2, x1 - x0 - 12, error != null ? Ui.RED : Ui.GRAY);
		}
		long now = System.currentTimeMillis();
		String id = hovered == null ? null : hovered.id();
		if (id == null || !id.equals(hoverId)) hoverSince = now;
		hoverId = id;
		if (hovered != null) Ui.overlayTooltip(g, tooltip(hovered, card(hovered.id(), now - hoverSince >= HOVER_DELAY_MS), l), mouseX, mouseY);
		else if (onGrip && !dragging) Ui.overlayTooltip(g, Component.literal(l.t("Drag to resize · drag to the edge to hide", "ドラッグで幅を変更・端まで寄せると隠せます")), mouseX, mouseY);
	}

	private void renderTab(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, Lang l) {
		int tx = x1 - TAB_W + 4;
		int ty = (y0 + y1) / 2 - TAB_H / 2;
		boolean over = overTab(mouseX, mouseY);
		g.fill(tx, ty, x1 + 4, ty + TAB_H, over ? 0xE02E2A44 : 0xC0101018);
		g.outline(tx, ty, x1 + 4 - tx, TAB_H, Ui.PANEL_BORDER);
		g.centeredText(font, "◀", tx + TAB_W / 2, ty + TAB_H / 2 - 4, over ? Ui.WHITE : Ui.GRAY);
		if (over) Ui.overlayTooltip(g, Component.literal(l.t("Item list: click or drag out", "アイテム一覧: クリックかドラッグで出す")), mouseX, mouseY);
	}

	/** The hover card: name, rarity and category, the in-game description, then prices (only the known ones). */
	static Component tooltip(Dto.ItemEntry e, Dto.ItemDetail d, Lang l) {
		List<Component> tip = new ArrayList<>();
		tip.add(Component.literal(e.name()).withStyle(Style.EMPTY.withColor(Ui.rarityColor(e.rarity()) & 0xFFFFFF)));
		String kind = ((e.rarity() == null ? "" : e.rarity().replace('_', ' ')) + " " + (d == null || d.category() == null ? "" : d.category().replace('_', ' '))).strip();
		if (!kind.isEmpty()) tip.add(gray(kind));
		if (d == null) {
			tip.add(gray(l.t("Loading details…", "詳細を読み込み中…")));
		} else {
			List<String> lore = d.lore() == null ? List.of() : d.lore();
			int shown = Math.min(lore.size(), 16);
			for (int i = 0; i < shown; i++) tip.add(Component.literal(lore.get(i)));
			if (lore.size() > shown) tip.add(gray(l.t("… " + (lore.size() - shown) + " more lines (click)", "… ほか " + (lore.size() - shown) + " 行（クリック）")));
		}
		tip.add(Component.empty());
		List<Component> prices = new ArrayList<>();
		if (d != null) {
			if (d.bazaarBuy() != null || d.bazaarSell() != null)
				prices.add(gold(l.t("Bazaar buy / sell: ", "バザー 購入 / 売却: ") + coins(d.bazaarBuy(), l) + " / " + coins(d.bazaarSell(), l)));
			if (d.lowestBin() != null) {
				String sales = d.salesPerDay() != null && d.salesPerDay() > 0 ? String.format(Locale.ROOT, l.t(" (%.1f sold/day)", "（%.1f 件/日）"), d.salesPerDay()) : "";
				prices.add(gold(l.t("Lowest BIN: ", "最安BIN: ") + Text.coins(d.lowestBin()) + sales));
			}
			if (d.npcSell() != null) prices.add(gold(l.t("NPC sell: ", "NPC売却: ") + Text.coins(d.npcSell())));
			if (d.craftCost() != null) prices.add(gold(l.t("Craft cost: ", "クラフト費用: ") + Text.coins(d.craftCost())));
		} else if (e.value() != null) {
			prices.add(gold(l.t("Value ", "価値 ") + Text.coins(e.value())));
		}
		if (prices.isEmpty()) prices.add(gray(d == null ? "" : l.t("No market price", "市場価格なし")));
		tip.addAll(prices);
		tip.add(Component.literal(l.t("Click / R: recipes · U: uses", "クリック / R: レシピ・U: 使い道")).withStyle(Style.EMPTY.withColor(Ui.DARK_GRAY & 0xFFFFFF)));
		MutableComponent joined = Component.empty();
		for (int i = 0; i < tip.size(); i++) {
			if (i > 0) joined.append("\n");
			joined.append(tip.get(i));
		}
		return joined;
	}

	private static String coins(Double v, Lang l) {
		return v == null ? l.t("unknown", "不明") : Text.coins(v);
	}

	private static Component gray(String s) {
		return Component.literal(s).withStyle(Style.EMPTY.withColor(Ui.GRAY & 0xFFFFFF));
	}

	private static Component gold(String s) {
		return Component.literal(s).withStyle(Style.EMPTY.withColor(Ui.GOLD & 0xFFFFFF));
	}

	private boolean overGrip(double mx, double my) {
		return mx >= x0 - 3 && mx <= x0 + 2 && my >= y0 && my < y1;
	}

	private boolean overTab(double mx, double my) {
		int ty = (y0 + y1) / 2 - TAB_H / 2;
		return mx >= x1 - TAB_W + 4 && my >= ty && my < ty + TAB_H;
	}

	boolean contains(double mx, double my) {
		if (collapsed) return overTab(mx, my);
		return mx >= x0 - 3 && mx < x1 && my >= y0 && my < y1;
	}

	/** Returns true when the click was ours (the window must not see it). */
	boolean click(double mx, double my, int button) {
		if (!contains(mx, my)) {
			focused = false;
			return false;
		}
		if (button == 0 && (collapsed || overGrip(mx, my))) {
			dragging = true;
			dragStartX = mx;
			dragWidth = collapsed ? 0 : x1 - x0;
			return true;
		}
		int sx = x0 + 3;
		int sy = y0 + 3;
		if (my >= sy && my < sy + SEARCH_H) {
			focused = true;
			if (button == 1) setQuery("");
			return true;
		}
		focused = false;
		for (Chip c : chips) {
			if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + CHIP_H) {
				setCategory(c.id());
				return true;
			}
		}
		if (my >= y1 - FOOTER_H) {
			if (mx < (x0 + x1) / 2.0) prevPage();
			else nextPage();
			return true;
		}
		String id = idAt(mx, my);
		if (id != null) open(id, ItemDetailScreen.Tab.RECIPES);
		return true;
	}

	private String idAt(double mx, double my) {
		Dto.ItemPage p = data;
		if (p == null || collapsed) return null;
		int col = (int) Math.floor((mx - gridX()) / CELL);
		int row = (int) Math.floor((my - gridTop) / CELL);
		if (col < 0 || col >= cols || row < 0 || row >= rows || my < gridTop) return null;
		int i = row * cols + col;
		return i < p.items().size() && i < perPage() ? p.items().get(i).id() : null;
	}

	private void open(String id, ItemDetailScreen.Tab tab) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ItemDetailScreen(screen, id, tab)));
	}

	/** Resizing by the grip (or pulling the tab out). */
	boolean drag(double mx) {
		if (!dragging) return false;
		dragWidth = (int) Math.max(0, Math.min(x1 - minX, x1 - mx));
		return true;
	}

	boolean release(double mx) {
		if (!dragging) return false;
		dragging = false;
		SkyPilotConfig.Inventory c = cfg();
		boolean moved = Math.abs(mx - dragStartX) > 3;
		if (c.itemBrowserCollapsed && !moved) {
			c.itemBrowserCollapsed = false; // a click on the tab
		} else if (dragWidth < MIN_W) {
			c.itemBrowserCollapsed = true;
		} else {
			c.itemBrowserCollapsed = false;
			c.itemBrowserWidth = dragWidth >= x1 - minX - 2 ? 0 : dragWidth;
		}
		SkyPilot.get().saveConfig();
		return true;
	}

	boolean scroll(double mx, double my, double dy) {
		if (collapsed || !contains(mx, my)) return false;
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

	private static void setCategory(String id) {
		if (id.equals(category())) return;
		cfg().itemBrowserCategory = id;
		page = 0;
		queryChangedAt = 0;
		SkyPilot.get().saveConfig();
	}

	/**
	 * While the search box is focused every key is ours (so "E" types instead of closing).
	 * Otherwise Ctrl+F focuses it, and R / U over a listed item opens its recipes / uses.
	 */
	boolean key(KeyEvent e) {
		if (!focused) {
			if (collapsed) return false;
			if (e.key() == InputConstants.KEY_F && e.hasControlDown()) {
				focused = true;
				return true;
			}
			if ((e.key() == InputConstants.KEY_R || e.key() == InputConstants.KEY_U) && hoverId != null) {
				open(hoverId, e.key() == InputConstants.KEY_R ? ItemDetailScreen.Tab.RECIPES : ItemDetailScreen.Tab.USES);
				return true;
			}
			return false;
		}
		switch (e.key()) {
			case 256, 257, 335 -> focused = false; // escape, enter, keypad enter
			case 259 -> { // backspace
				if (!query.isEmpty()) setQuery(e.hasControlDown() ? "" : query.substring(0, query.length() - 1));
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

	/** For tests. */
	static void setCard(String id, Dto.ItemDetail d) {
		if (d == null) CARDS.remove(id);
		else CARDS.put(id, d);
	}

	/** For tests: number of item cells on one page. */
	int cells() {
		return perPage();
	}

	/** For tests: the grid reaches the footer (no empty band below the items). */
	boolean fillsPanel() {
		return !collapsed && gridTop + (rows + 1) * CELL > y1 - FOOTER_H;
	}

	/** For tests: GUI coordinates of the middle of cell {@code i}. */
	int[] cellCenter(int i) {
		return new int[] {gridX() + (i % cols) * CELL + CELL / 2, gridTop + (i / cols) * CELL + CELL / 2};
	}

	/** For tests. */
	boolean collapsedNow() {
		return collapsed;
	}
}
