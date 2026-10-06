package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.core.VisitorOffer;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Garden visitor menus: what the requested items cost on the Bazaar (looked up by name in the
 * SkyPilot item list) and the copper reward, left of the window. Reading the offer item's lore
 * only; accepting or refusing stays the player's click.
 */
final class VisitorPanel {
	private record Price(Double value, long at) {
	}

	/** Unit value by item name (null = not found); shared by all windows for 10 minutes. */
	private static final Map<String, Price> PRICES = new ConcurrentHashMap<>();
	private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();

	private final AbstractContainerScreen<?> screen;
	private VisitorOffer offer;
	private String offerKey = "";
	private int x0;
	private int y0;
	private int w;

	VisitorPanel(AbstractContainerScreen<?> screen) {
		this.screen = screen;
	}

	/** The offer in the open window, if it shows one. */
	static VisitorOffer find(AbstractContainerScreen<?> screen) {
		for (Slot s : screen.getMenu().slots) {
			ItemStack st = s.getItem();
			if (st.isEmpty()) continue;
			ItemLore lore = st.get(DataComponents.LORE);
			if (lore == null || lore.lines().size() < 3) continue;
			List<String> lines = new ArrayList<>();
			for (Component c : lore.lines()) lines.add(c.getString());
			VisitorOffer o = VisitorOffer.parse(lines);
			if (o != null) return o;
		}
		return null;
	}

	boolean layout() {
		if (!Feat.on(Features.FARMING_VISITOR) || !(screen instanceof ContainerScreenAccessor box)) return false;
		VisitorOffer o = find(screen);
		if (o == null) return false;
		String key = o.toString();
		if (!key.equals(offerKey)) {
			offer = o;
			offerKey = key;
		}
		int left = box.skypilot$leftPos();
		w = Math.min(190, left - 8);
		if (w < 100) return false;
		x0 = left - w - 4;
		y0 = box.skypilot$topPos();
		return true;
	}

	void render(GuiGraphicsExtractor g, Font font) {
		Lang l = SkyPilot.get().lang();
		List<String[]> rows = new ArrayList<>();
		double total = 0;
		boolean complete = true;
		for (VisitorOffer.Need n : offer.needs()) {
			Price p = price(n.name());
			String cost;
			if (p == null) {
				cost = "…";
				complete = false;
			} else if (p.value() == null) {
				cost = "?";
				complete = false;
			} else {
				total += p.value() * n.amount();
				cost = Text.coins(p.value() * n.amount());
			}
			rows.add(new String[] {n.name(), " ×" + n.amount(), cost});
		}
		int h = 32 + rows.size() * 11 + (offer.copper().isPresent() ? 24 : 0);
		g.fill(x0, y0, x0 + w, y0 + h, Ui.PANEL);
		g.outline(x0, y0, w, h, Ui.PANEL_BORDER);
		g.text(font, l.t("SkyPilot · visitor cost", "SkyPilot · 訪問者のコスト"), x0 + 5, y0 + 5, Ui.PURPLE, false);
		int y = y0 + 18;
		for (String[] r : rows) {
			int cw = font.width(r[2]);
			int aw = font.width(r[1]);
			String name = font.plainSubstrByWidth(r[0], w - 14 - cw - aw);
			g.text(font, name, x0 + 5, y, Ui.WHITE, false);
			g.text(font, r[1], x0 + 5 + font.width(name), y, Ui.GRAY, false);
			g.text(font, r[2], x0 + w - 5 - cw, y, Ui.GOLD, false);
			y += 11;
		}
		String totalText = l.t("Total ", "合計 ") + Text.coins(total) + (complete ? "" : l.t(" (some unknown)", "（一部不明）"));
		g.text(font, font.plainSubstrByWidth(totalText, w - 10), x0 + 5, y + 2, complete ? Ui.GOLD : Ui.GRAY, false);
		if (offer.copper().isPresent()) {
			double copper = offer.copper().getAsDouble();
			g.text(font, l.t("Copper +", "コッパー +") + Text.coins(copper), x0 + 5, y + 14, 0xFFFF8855, false);
			if (copper > 0 && complete) {
				String per = Text.coins(total / copper) + l.t(" coins/copper", " コイン/コッパー");
				g.text(font, font.plainSubstrByWidth(per, w - 10), x0 + 5, y + 25, Ui.GRAY, false);
			}
		}
	}

	/** Cached unit value of an item name; starts a lookup when unknown. */
	private static Price price(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Price p = PRICES.get(key);
		long now = System.currentTimeMillis();
		if (p != null && now - p.at() < 600_000) return p;
		ApiClient api = SkyPilot.get().api();
		if (api != null && PENDING.putIfAbsent(key, now) == null) {
			api.items(name, 0, 8).whenComplete((page, err) -> {
				PENDING.remove(key);
				if (page == null) return;
				Double value = null;
				for (Dto.ItemEntry e : page.items()) {
					if (e.name() != null && e.name().equalsIgnoreCase(name)) {
						value = e.value();
						break;
					}
				}
				PRICES.put(key, new Price(value, System.currentTimeMillis()));
			});
		}
		return p;
	}

	/** For tests. */
	static void setPrice(String name, Double value) {
		PRICES.put(name.toLowerCase(Locale.ROOT), new Price(value, System.currentTimeMillis()));
	}
}
