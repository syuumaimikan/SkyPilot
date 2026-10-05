package dev.skypilot.mod.platform;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * In-game price check: a tooltip line on SkyBlock items (from the item's own custom_data, which
 * the server sends with every item) and the {@code /skypilot price} command. Display only.
 */
final class PriceCheck {
	private static final int MAX_CACHE = 256;
	private static final long MIN_REQUEST_GAP_MS = 250;
	/** Prices shown in tooltips are refreshed after this long (the old value stays visible meanwhile). */
	private static final long REFRESH_MS = 5 * 60_000;
	private static final Map<String, Cached> RESULTS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> e) {
			return size() > MAX_CACHE;
		}
	};

	private record Cached(Dto.PriceResponse price, long at) {
	}
	private static final Map<String, Long> PENDING = new LinkedHashMap<>();
	private static long lastRequestAt;

	record Item(String skyblockId, String key, CompoundTag tag) {
	}

	private PriceCheck() {
	}

	static Item item(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return null;
		CompoundTag tag = data.copyTag();
		String id = tag.getStringOr("id", "");
		if (id.isEmpty() || !id.matches("[A-Za-z0-9_:;]{1,64}")) return null;
		String uuid = tag.getStringOr("uuid", "");
		return new Item(id, id + ":" + (uuid.isEmpty() ? Integer.toHexString(tag.hashCode()) : uuid), tag);
	}

	static JsonElement json(CompoundTag tag) {
		return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
	}

	/** Tooltip hook: append cached price lines; fetch in the background when missing. */
	static void tooltip(ItemStack stack, List<Component> lines) {
		SkyPilot sp = SkyPilot.get();
		if (sp == null || !sp.config().priceTooltip || !sp.context().inSkyBlock()) return;
		Item item = item(stack);
		if (item == null) return;
		Cached c;
		synchronized (RESULTS) {
			c = RESULTS.get(item.key());
		}
		if (c == null || System.currentTimeMillis() - c.at() > REFRESH_MS) request(sp, item);
		if (c != null) {
			lines.addAll(describe(c.price(), sp.lang()));
		} else {
			lines.add(Component.literal("SkyPilot: …").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void request(SkyPilot sp, Item item) {
		ApiClient api = sp.api();
		long now = System.currentTimeMillis();
		synchronized (RESULTS) {
			if (api == null || PENDING.containsKey(item.key()) || now - lastRequestAt < MIN_REQUEST_GAP_MS) return;
			lastRequestAt = now;
			PENDING.put(item.key(), now);
		}
		fetch(api, item).whenComplete((r, err) -> {
			synchronized (RESULTS) {
				PENDING.remove(item.key());
				if (r != null) RESULTS.put(item.key(), new Cached(r, System.currentTimeMillis()));
			}
		});
	}

	static CompletableFuture<Dto.PriceResponse> fetch(ApiClient api, Item item) {
		return api.price(item.skyblockId(), json(item.tag()));
	}

	static List<Component> describe(Dto.PriceResponse r, Lang l) {
		String value = r.estimatedValue() != null ? Text.coins(r.estimatedValue()) : l.t("no market data", "市場データなし");
		StringBuilder detail = new StringBuilder();
		if (r.bazaarBuy() != null || r.bazaarSell() != null) {
			detail.append(l.t("Bazaar buy ", "バザー買 ")).append(r.bazaarBuy() != null ? Text.coins(r.bazaarBuy()) : "?")
					.append(l.t(" / sell ", " / 売 ")).append(r.bazaarSell() != null ? Text.coins(r.bazaarSell()) : "?");
		} else {
			if (r.lowestBin() != null) detail.append(l.t("Lowest BIN ", "最安BIN ")).append(Text.coins(r.lowestBin()));
			if (r.median() != null) detail.append(detail.isEmpty() ? "" : " · ").append(l.t("median ", "中央値 ")).append(Text.coins(r.median()));
			if (r.salesPerDay() != null) detail.append(detail.isEmpty() ? "" : " · ").append(String.format(java.util.Locale.ROOT, "%.1f", r.salesPerDay())).append(l.t("/day", "/日"));
		}
		ChatFormatting conf = switch (r.confidenceLabel()) {
			case "HIGH" -> ChatFormatting.GREEN;
			case "MEDIUM" -> ChatFormatting.YELLOW;
			default -> ChatFormatting.RED;
		};
		Component head = Component.literal("SkyPilot: ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal("~" + value).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" (" + r.confidenceLabel() + ")").withStyle(conf));
		return detail.isEmpty() ? List.of(head) : List.of(head, Component.literal("  " + detail).withStyle(ChatFormatting.GRAY));
	}
}
