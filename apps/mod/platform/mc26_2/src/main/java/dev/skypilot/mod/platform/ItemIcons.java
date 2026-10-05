package dev.skypilot.mod.platform;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ItemStacks used only to draw SkyBlock items in SkyPilot's GUI: the vanilla item with the
 * head texture, leather colour and glint the server describes ({@link Dto.ItemIcon}). Unknown
 * item ids fall back to paper. These stacks never enter any inventory.
 */
final class ItemIcons {
	private static final int MAX_CACHE = 2048;
	private static final Map<Dto.ItemIcon, ItemStack> BY_SPEC = new HashMap<>();
	private static final Map<String, ItemStack> BY_ID = new HashMap<>();
	private static final Set<String> QUEUED = new LinkedHashSet<>();
	private static final Set<String> REQUESTED = new java.util.HashSet<>();
	private static long lastFetch;

	private ItemIcons() {
	}

	/** Stack for a server-provided icon (game thread). */
	static ItemStack of(Dto.ItemIcon icon) {
		if (icon == null) return new ItemStack(Items.PAPER);
		if (BY_SPEC.size() > MAX_CACHE) BY_SPEC.clear();
		return BY_SPEC.computeIfAbsent(icon, ItemIcons::build);
	}

	/** Stack for an item id; paper until the icon has been fetched (batched, game thread). */
	static ItemStack forId(String id) {
		ItemStack s = BY_ID.get(id);
		if (s != null) return s;
		if (!REQUESTED.contains(id)) QUEUED.add(id);
		return new ItemStack(Items.PAPER);
	}

	/** Called from screens while they render: sends queued id lookups at most twice a second. */
	static void flush() {
		long now = System.currentTimeMillis();
		ApiClient api = SkyPilot.get().api();
		if (QUEUED.isEmpty() || api == null || now - lastFetch < 500) return;
		lastFetch = now;
		List<String> batch = new ArrayList<>();
		for (String id : QUEUED) {
			if (batch.size() >= 64) break;
			batch.add(id);
		}
		batch.forEach(QUEUED::remove);
		REQUESTED.addAll(batch);
		api.icons(batch).whenComplete((map, err) -> Minecraft.getInstance().execute(() -> {
			if (map == null) return;
			if (BY_ID.size() > MAX_CACHE) BY_ID.clear();
			map.forEach((id, icon) -> BY_ID.put(id, build(icon)));
		}));
	}

	static ItemStack build(Dto.ItemIcon icon) {
		Identifier id = icon.item() == null ? null : Identifier.tryParse(icon.item());
		Item item = id == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
		ItemStack stack = new ItemStack(item);
		if (item == Items.PLAYER_HEAD && icon.texture() != null && !icon.texture().isBlank()) {
			PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", icon.texture())));
			UUID uuid = UUID.nameUUIDFromBytes(icon.texture().getBytes(StandardCharsets.UTF_8));
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(uuid, "SkyPilotIcon", props)));
		}
		if (icon.color() != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(icon.color()));
		if (icon.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}
}
