package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.LoreValues;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
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
import java.util.List;
import java.util.OptionalDouble;

/**
 * Sack windows: what everything stored is worth ("Stored: 28,183/60.5k" in each item's lore ×
 * the item's Bazaar value by name), highest first, left of the window. Reading only.
 */
final class SackValue {
	private record Row(ItemStack icon, String name, double stored, Double value) {
	}

	private final AbstractContainerScreen<?> screen;
	private int x0;
	private int y0;
	private int w;

	SackValue(AbstractContainerScreen<?> screen) {
		this.screen = screen;
	}

	/** Sack windows ("Enchanted Agronomy Sack", "Fishing Sack", …). */
	static boolean isSack(String title) {
		return title.endsWith(" Sack");
	}

	boolean layout() {
		if (!Feat.on(Features.INV_SACK_VALUE) || !isSack(screen.getTitle().getString()) || !(screen instanceof ContainerScreenAccessor box)) return false;
		int left = box.skypilot$leftPos();
		w = Math.min(190, left - 8);
		if (w < 100) return false;
		x0 = left - w - 4;
		y0 = box.skypilot$topPos();
		return true;
	}

	List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) continue;
			ItemStack st = s.getItem();
			if (st.isEmpty()) continue;
			ItemLore lore = st.get(DataComponents.LORE);
			if (lore == null) continue;
			List<String> lines = new ArrayList<>();
			for (Component c : lore.lines()) lines.add(c.getString());
			OptionalDouble stored = LoreValues.stored(lines);
			if (stored.isEmpty() || stored.getAsDouble() <= 0) continue;
			String name = st.getHoverName().getString();
			NamePrices.Price p = NamePrices.get(name);
			Double value = p == null || p.value() == null ? null : p.value() * stored.getAsDouble();
			rows.add(new Row(st, name, stored.getAsDouble(), value));
		}
		rows.sort((a, b) -> Double.compare(b.value() == null ? -1 : b.value(), a.value() == null ? -1 : a.value()));
		return rows;
	}

	void render(GuiGraphicsExtractor g, Font font) {
		Lang l = SkyPilot.get().lang();
		List<Row> rows = rows();
		double total = rows.stream().filter(r -> r.value() != null).mapToDouble(Row::value).sum();
		int shown = Math.min(rows.size(), 14);
		int h = 32 + shown * 18;
		g.fill(x0, y0, x0 + w, y0 + h, Ui.PANEL);
		g.outline(x0, y0, w, h, Ui.PANEL_BORDER);
		g.text(font, l.t("SkyPilot · sack value", "SkyPilot · サックの価値"), x0 + 5, y0 + 5, Ui.PURPLE, false);
		g.text(font, font.plainSubstrByWidth(l.t("Total ", "合計 ") + Text.coins(total) + l.t(" (" + rows.size() + " kinds)", "（" + rows.size() + "種類）"), w - 10), x0 + 5, y0 + 17,
				Ui.GOLD, false);
		int y = y0 + 30;
		for (int i = 0; i < shown; i++) {
			Row r = rows.get(i);
			g.item(r.icon(), x0 + 3, y + 1);
			String value = r.value() == null ? "…" : Text.coins(r.value());
			int vw = font.width(value);
			g.text(font, font.plainSubstrByWidth(r.name() + " ×" + Text.coins(r.stored()), w - 30 - vw), x0 + 22, y + 5, Ui.WHITE, false);
			g.text(font, value, x0 + w - 5 - vw, y + 5, r.value() == null ? Ui.DARK_GRAY : Ui.GOLD, false);
			y += 18;
		}
	}
}
