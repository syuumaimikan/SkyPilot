package dev.skypilot.mod.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auction browser windows: listings whose "Buy it now" price is 10%+ below SkyPilot's value get
 * a green outline, 50%+ above a red one. Values come from the items' own custom_data via the
 * SkyPilot server; buying stays the player's click.
 */
final class AuctionHighlight {
	private static final long DEBOUNCE_MS = 400;
	private final AbstractContainerScreen<?> screen;
	private String signature = "";
	private String requested = "";
	private long changedAt;
	private Map<Integer, Double> values = Map.of();

	AuctionHighlight(AbstractContainerScreen<?> screen) {
		this.screen = screen;
	}

	/** Hypixel's auction browser and search result windows ("Auctions Browser", "Auctions: \"…\""). */
	static boolean isBrowser(String title) {
		return title.startsWith("Auctions");
	}

	/** -1 good deal, 1 overpriced, 0 neither (or unknown). */
	static int verdict(double bin, double value) {
		if (value <= 0) return 0;
		double r = bin / value;
		return r <= 0.9 ? -1 : r >= 1.5 ? 1 : 0;
	}

	void render(GuiGraphicsExtractor g) {
		if (!Feat.on(Features.INV_AH_HIGHLIGHT) || !isBrowser(screen.getTitle().getString()) || !(screen instanceof ContainerScreenAccessor box)) return;
		update();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) continue;
			Double v = values.get(s.index);
			if (v == null) continue;
			Double bin = bin(s);
			if (bin == null) continue;
			int verdict = verdict(bin, v);
			if (verdict == 0) continue;
			int x = box.skypilot$leftPos() + s.x - 1;
			int y = box.skypilot$topPos() + s.y - 1;
			int color = verdict < 0 ? 0xFF55FF55 : 0xFFFF5555;
			g.outline(x, y, 18, 18, color);
			g.outline(x + 1, y + 1, 16, 16, color & 0x80FFFFFF);
		}
	}

	private static Double bin(Slot s) {
		ItemLore lore = s.getItem().get(DataComponents.LORE);
		if (lore == null) return null;
		List<Component> lines = new ArrayList<>(lore.lines());
		return PriceCheck.binPrice(lines);
	}

	private void update() {
		StringBuilder sig = new StringBuilder();
		List<Slot> priced = new ArrayList<>();
		for (Slot s : screen.getMenu().slots) {
			if (s.container instanceof Inventory) continue;
			PriceCheck.Item it = PriceCheck.item(s.getItem());
			if (it == null || bin(s) == null) continue;
			sig.append(s.index).append(':').append(it.key()).append(';');
			priced.add(s);
		}
		String now = sig.toString();
		if (!now.equals(signature)) {
			signature = now;
			changedAt = System.currentTimeMillis();
		}
		if (signature.equals(requested) || System.currentTimeMillis() - changedAt < DEBOUNCE_MS || priced.isEmpty()) return;
		requested = signature;
		ApiClient api = SkyPilot.get().api();
		if (api == null) return;
		JsonArray items = new JsonArray();
		for (Slot s : priced) {
			JsonObject o = new JsonObject();
			o.add("customData", PriceCheck.json(PriceCheck.item(s.getItem()).tag()));
			o.addProperty("count", s.getItem().getCount());
			items.add(o);
		}
		String forSig = signature;
		api.values(items).whenComplete((v, err) -> Minecraft.getInstance().execute(() -> {
			if (v == null || !forSig.equals(signature)) return;
			apply(priced, v);
		}));
	}

	void apply(List<Slot> priced, Dto.Values v) {
		Map<Integer, Double> m = new HashMap<>();
		for (int i = 0; i < priced.size() && i < v.values().size(); i++) {
			Double value = v.values().get(i).value();
			if (value != null) m.put(priced.get(i).index, value);
		}
		values = m;
	}

	/** For tests: values per slot index. */
	void setValues(Map<Integer, Double> v) {
		values = v;
		requested = signature = "test";
	}
}
