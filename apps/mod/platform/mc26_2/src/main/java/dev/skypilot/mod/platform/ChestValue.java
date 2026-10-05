package dev.skypilot.mod.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Value of a chest / backpack / ender chest page: the total and every item sorted by value,
 * left of the window. Clicking a row outlines that item's slots. Reads only the items shown in
 * the window (their own custom_data) and never clicks or moves anything.
 */
final class ChestValue {
	static final int ROW_H = 18;
	private static final long DEBOUNCE_MS = 400;

	/** One item kind in the window. */
	static final class Group {
		final String key;
		final String name;
		final ItemStack stack;
		final List<Slot> slots = new ArrayList<>();
		int count;
		Double value;
		boolean unknown;

		Group(String key, String name, ItemStack stack) {
			this.key = key;
			this.name = name;
			this.stack = stack;
		}
	}

	private final AbstractContainerScreen<?> screen;
	private String signature = "";
	private String requested = "";
	private long changedAt;
	private List<Group> groups = List.of();
	private Double total;
	private String error;
	private String highlighted;
	private double scroll;
	private int x0;
	private int y0;
	private int w;
	private int h;

	ChestValue(AbstractContainerScreen<?> screen) {
		this.screen = screen;
	}

	/** Storage windows: chests, backpacks, ender chest pages, the personal vault. */
	static boolean isStorage(String title) {
		String t = title.toLowerCase(Locale.ROOT);
		return t.contains("chest") || t.contains("backpack") || t.contains("vault") || t.contains("bag") && !t.contains("menu");
	}

	private static List<Slot> containerSlots(AbstractContainerScreen<?> screen) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (!(s.container instanceof Inventory)) out.add(s);
		}
		return out;
	}

	/** Re-read the window's items; ask the server for values when they changed (debounced). */
	private void update() {
		StringBuilder sig = new StringBuilder();
		List<Slot> slots = containerSlots(screen);
		for (Slot s : slots) {
			PriceCheck.Item it = PriceCheck.item(s.getItem());
			if (it != null) sig.append(s.index).append(':').append(it.key()).append('x').append(s.getItem().getCount()).append(';');
		}
		String now = sig.toString();
		if (!now.equals(signature)) {
			signature = now;
			changedAt = System.currentTimeMillis();
		}
		if (signature.equals(requested) || System.currentTimeMillis() - changedAt < DEBOUNCE_MS) return;
		requested = signature;
		ApiClient api = SkyPilot.get().api();
		if (api == null) {
			error = SkyPilot.get().apiError();
			return;
		}
		// one request entry per slot; grouped after the answer
		JsonArray items = new JsonArray();
		List<Slot> priced = new ArrayList<>();
		for (Slot s : slots) {
			PriceCheck.Item it = PriceCheck.item(s.getItem());
			if (it == null || priced.size() >= 160) continue;
			JsonObject o = new JsonObject();
			o.add("customData", PriceCheck.json(it.tag()));
			o.addProperty("count", s.getItem().getCount());
			items.add(o);
			priced.add(s);
		}
		if (priced.isEmpty()) {
			groups = List.of();
			total = 0.0;
			return;
		}
		String forSig = signature;
		api.values(items).whenComplete((v, err) -> Minecraft.getInstance().execute(() -> {
			if (!forSig.equals(signature)) return;
			if (v == null) {
				error = SkyPilot.shortError(err);
				return;
			}
			error = null;
			apply(priced, v);
		}));
	}

	/** Group per item (same SkyBlock id and name) and sort by value. */
	void apply(List<Slot> priced, Dto.Values v) {
		Map<String, Group> byKey = new LinkedHashMap<>();
		for (int i = 0; i < priced.size() && i < v.values().size(); i++) {
			Slot s = priced.get(i);
			Dto.Value val = v.values().get(i);
			String name = s.getItem().getHoverName().getString();
			String key = (val.skyblockId() == null ? "?" : val.skyblockId()) + "|" + name;
			Group g = byKey.computeIfAbsent(key, k -> new Group(k, name, s.getItem().copy()));
			g.slots.add(s);
			g.count += s.getItem().getCount();
			if (val.value() == null) {
				g.unknown = true;
			} else {
				g.value = (g.value == null ? 0 : g.value) + val.value();
			}
		}
		List<Group> list = new ArrayList<>(byKey.values());
		list.sort((a, b) -> Double.compare(b.value == null ? -1 : b.value, a.value == null ? -1 : a.value));
		groups = list;
		total = v.total();
	}

	boolean layout() {
		if (!SkyPilot.get().config().inventory.chestValue || !isStorage(screen.getTitle().getString()) || !(screen instanceof ContainerScreenAccessor box)) return false;
		int left = box.skypilot$leftPos();
		w = Math.min(190, left - 8);
		if (w < 100) return false;
		x0 = left - w - 4;
		y0 = 4;
		h = screen.height - 8;
		return true;
	}

	void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		update();
		Lang l = SkyPilot.get().lang();
		g.fill(x0, y0, x0 + w, y0 + h, Ui.PANEL);
		g.outline(x0, y0, w, h, Ui.PANEL_BORDER);
		g.text(font, l.t("SkyPilot · value", "SkyPilot · 価値"), x0 + 5, y0 + 5, Ui.PURPLE, false);
		String totalText = total == null ? (error != null ? error : l.t("Calculating…", "計算中…")) : l.t("Total ", "合計 ") + Text.coins(total) + l.t(" (" + groups.size() + " kinds)", "（" + groups.size() + "種類）");
		g.text(font, font.plainSubstrByWidth(totalText, w - 10), x0 + 5, y0 + 17, total == null ? (error != null ? Ui.RED : Ui.GRAY) : Ui.GOLD, false);
		int listY = y0 + 30;
		int listH = h - 30 - 12;
		g.enableScissor(x0 + 1, listY, x0 + w - 1, listY + listH);
		int y = listY - (int) scroll;
		Group hovered = null;
		for (Group gr : groups) {
			if (y + ROW_H >= listY && y <= listY + listH) {
				boolean on = gr.key.equals(highlighted);
				boolean over = mouseX >= x0 && mouseX < x0 + w && mouseY >= Math.max(y, listY) && mouseY < Math.min(y + ROW_H, listY + listH);
				if (on) g.fill(x0 + 1, y, x0 + w - 1, y + ROW_H, 0x40FFAA00);
				else if (over) g.fill(x0 + 1, y, x0 + w - 1, y + ROW_H, 0x22FFFFFF);
				if (over) hovered = gr;
				g.item(gr.stack, x0 + 3, y + 1);
				String value = gr.value == null ? "?" : Text.coins(gr.value);
				int vw = font.width(value);
				g.text(font, font.plainSubstrByWidth(gr.name + (gr.count > 1 ? " ×" + gr.count : ""), w - 30 - vw), x0 + 22, y + 5, Ui.WHITE, false);
				g.text(font, value, x0 + w - 5 - vw, y + 5, gr.value == null ? Ui.DARK_GRAY : Ui.GOLD, false);
			}
			y += ROW_H;
		}
		g.disableScissor();
		g.text(font, font.plainSubstrByWidth(l.t("Click an item to outline it", "クリックで該当スロットを縁取り"), w - 10), x0 + 5, y0 + h - 11, Ui.DARK_GRAY, false);
		// outline the highlighted item's slots in the window
		if (highlighted != null && screen instanceof ContainerScreenAccessor box) {
			for (Group gr : groups) {
				if (!gr.key.equals(highlighted)) continue;
				for (Slot s : gr.slots) {
					int sx = box.skypilot$leftPos() + s.x - 1;
					int sy = box.skypilot$topPos() + s.y - 1;
					g.fill(sx, sy, sx + 18, sy + 18, 0x55FFAA00);
					g.outline(sx - 1, sy - 1, 20, 20, 0xFFFFAA00);
					g.outline(sx, sy, 18, 18, 0xFFFFDD55);
				}
			}
		}
		if (hovered != null) {
			String tip = hovered.name + (hovered.count > 1 ? " ×" + hovered.count : "") + "\n"
					+ (hovered.value == null ? l.t("No market price", "市場価格なし") : l.t("Value ", "価値 ") + Text.coins(hovered.value) + (hovered.unknown ? l.t(" (some unpriced)", "（一部価格不明）") : ""))
					+ "\n" + l.t("Click to outline its slots", "クリックでスロットを縁取り");
			g.setTooltipForNextFrame(Component.literal(tip), mouseX, mouseY);
		}
	}

	boolean contains(double mx, double my) {
		return mx >= x0 && mx < x0 + w && my >= y0 && my < y0 + h;
	}

	boolean click(double mx, double my) {
		if (!contains(mx, my)) return false;
		int listY = y0 + 30;
		int i = (int) Math.floor((my - listY + scroll) / ROW_H);
		if (my >= listY && i >= 0 && i < groups.size()) {
			String key = groups.get(i).key;
			highlighted = key.equals(highlighted) ? null : key;
		}
		return true;
	}

	boolean scroll(double mx, double my, double dy) {
		if (!contains(mx, my)) return false;
		int max = Math.max(0, groups.size() * ROW_H - (h - 42));
		scroll = Math.max(0, Math.min(max, scroll - dy * ROW_H * 2));
		return true;
	}

	/** For tests: the slots of the window's own container. */
	static List<Slot> slotsOf(AbstractContainerScreen<?> screen) {
		return containerSlots(screen);
	}

	void highlight(String key) {
		highlighted = key;
	}

	List<Group> groups() {
		return groups;
	}
}
