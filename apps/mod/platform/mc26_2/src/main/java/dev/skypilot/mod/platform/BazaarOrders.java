package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.BazaarOrderRules;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bazaar order helper (market.bazaar_order_helper): in "Your Bazaar Orders" each order item gets
 * a coloured frame - green best price, red outbid, gold filled - and a summary line above the
 * window. Reads the items' names and lore only; nothing is clicked.
 */
final class BazaarOrders {
	private static final long REFRESH_MS = 20_000;
	private final AbstractContainerScreen<?> screen;
	private Map<String, Dto.BazaarQuote> quotes = Map.of();
	private String requested = "";
	private long requestedAt;

	BazaarOrders(AbstractContainerScreen<?> screen) {
		this.screen = screen;
	}

	private boolean active() {
		return Feat.on(Features.MARKET_ORDER_HELPER) && BazaarOrderRules.isOrdersWindow(screen.getTitle().getString());
	}

	static BazaarOrderRules.Order order(ItemStack stack) {
		if (stack.isEmpty()) return null;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return null;
		List<String> lines = new ArrayList<>();
		for (Component c : lore.lines()) lines.add(c.getString());
		return BazaarOrderRules.parse(stack.getHoverName().getString(), lines);
	}

	void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		if (!active() || !(screen instanceof ContainerScreenAccessor box)) return;
		Map<Slot, BazaarOrderRules.Order> orders = new HashMap<>();
		Set<String> names = new LinkedHashSet<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) continue;
			BazaarOrderRules.Order o = order(s.getItem());
			if (o == null) continue;
			orders.put(s, o);
			if (!o.filled()) names.add(o.product());
		}
		update(names);
		Lang l = SkyPilot.get().lang();
		int best = 0, outbid = 0, filled = 0;
		String hoverText = null;
		for (Map.Entry<Slot, BazaarOrderRules.Order> e : orders.entrySet()) {
			Slot s = e.getKey();
			BazaarOrderRules.Order o = e.getValue();
			Dto.BazaarQuote q = quotes.get(o.product());
			BazaarOrderRules.Verdict v = BazaarOrderRules.judge(o, q == null ? null : q.instantBuy(), q == null ? null : q.instantSell());
			int color = switch (v.status()) {
				case BEST -> 0xFF55FF55;
				case OUTBID -> 0xFFFF5555;
				case FILLED -> 0xFFFFAA00;
				case UNKNOWN -> 0;
			};
			switch (v.status()) {
				case BEST -> best++;
				case OUTBID -> outbid++;
				case FILLED -> filled++;
				default -> {
				}
			}
			if (color == 0) continue;
			int x = box.skypilot$leftPos() + s.x - 1;
			int y = box.skypilot$topPos() + s.y - 1;
			g.outline(x, y, 18, 18, color);
			g.outline(x + 1, y + 1, 16, 16, color & 0x80FFFFFF);
			if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) hoverText = BazaarOrderRules.describe(v, o, l);
		}
		if (orders.isEmpty()) return;
		String summary = l.t("SkyPilot: ", "SkyPilot: ") + best + l.t(" best · ", " 最良・") + outbid + l.t(" outbid · ", " 上回られ・") + filled + l.t(" filled", " 約定済み");
		int top = box.skypilot$topPos();
		int left = box.skypilot$leftPos();
		int w = font.width(summary) + 8;
		int y = Math.max(2, top - 13);
		g.fill(left, y, left + w, y + 12, 0xD0101018);
		g.text(font, summary, left + 4, y + 2, outbid > 0 ? 0xFFFF7777 : 0xFF77FF77, false);
		if (hoverText != null) {
			Ui.overlayTooltip(g, Component.literal(hoverText + "\n" + l.t("Reading only - SkyPilot never touches your orders.", "表示のみ - 注文の操作はしません。")), mouseX, mouseY - 18);
		}
	}

	private void update(Set<String> names) {
		if (names.isEmpty()) return;
		String key = String.join("|", names);
		long now = System.currentTimeMillis();
		if (key.equals(requested) && now - requestedAt < REFRESH_MS) return;
		ApiClient api = SkyPilot.get().api();
		if (api == null) return;
		requested = key;
		requestedAt = now;
		List<String> list = new ArrayList<>(names);
		if (list.size() > 40) list = list.subList(0, 40);
		api.bazaarQuotes(list).whenComplete((r, err) -> Minecraft.getInstance().execute(() -> {
			if (r == null || !key.equals(requested)) return;
			Map<String, Dto.BazaarQuote> m = new HashMap<>();
			for (Dto.BazaarQuote q : r.quotes()) m.put(q.name(), q);
			quotes = m;
		}));
	}

	/** For tests. */
	void setQuotes(Map<String, Dto.BazaarQuote> q) {
		quotes = q;
		requested = "test";
		requestedAt = Long.MAX_VALUE / 2;
	}
}
