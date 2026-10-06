package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.ItemInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-slot decorations in inventory windows (called from {@code SlotRenderMixin}): rarity
 * colour behind the item, a small number over it, and dimming of items that do not match the
 * item list search. Reads only the item's own tooltip data; draws only.
 */
public final class SlotDecor {
	private record Info(ItemInfo.Rarity rarity, String number, String searchText) {
	}

	/** Item stacks are replaced (not edited) when the server updates a slot, so identity caching is safe. */
	private static final Map<ItemStack, Info> CACHE = new WeakHashMap<>();
	private static String cachedTitle = "";

	private SlotDecor() {
	}

	public static void behind(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot slot) {
		if (!Feat.on(Features.INV_RARITY)) return;
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) return;
		Info info = info(screen, stack);
		if (info.rarity() == null) return;
		g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x70000000 | info.rarity().rgb);
	}

	public static void over(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot slot) {
		ItemStack stack = slot.getItem();
		boolean numbers = Feat.on(Features.INV_NUMBERS);
		String query = Feat.on(Features.INV_SEARCH_HIGHLIGHT) ? ItemBrowser.query().strip().toLowerCase(Locale.ROOT) : "";
		if (!numbers && query.isEmpty()) return;
		if (stack.isEmpty()) {
			if (!query.isEmpty()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x90101010);
			return;
		}
		Info info = info(screen, stack);
		if (!query.isEmpty()) {
			if (info.searchText().contains(query)) {
				g.outline(slot.x - 1, slot.y - 1, 18, 18, 0xFFFFDD55);
			} else {
				g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xC0101010);
			}
		}
		if (numbers && info.number() != null) {
			Font font = Minecraft.getInstance().font;
			g.pose().pushMatrix();
			g.pose().translate(slot.x, slot.y);
			g.pose().scale(0.75f, 0.75f);
			g.text(font, info.number(), 0, 0, 0xFFFFDD55, true);
			g.pose().popMatrix();
		}
	}

	private static Info info(AbstractContainerScreen<?> screen, ItemStack stack) {
		String title = screen.getTitle().getString();
		if (!title.equals(cachedTitle)) {
			// slot numbers in skill / collection menus depend on the window
			CACHE.clear();
			cachedTitle = title;
		}
		return CACHE.computeIfAbsent(stack, s -> compute(s, title));
	}

	private static Info compute(ItemStack stack, String title) {
		List<String> lore = new ArrayList<>();
		ItemLore l = stack.get(DataComponents.LORE);
		if (l != null) for (Component c : l.lines()) lore.add(c.getString());
		String name = stack.getHoverName().getString();
		String id = null;
		Map<String, Integer> enchants = null;
		Integer stars = null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && !data.isEmpty()) {
			CompoundTag tag = data.copyTag();
			String sid = tag.getStringOr("id", "");
			if (!sid.isEmpty()) id = sid;
			CompoundTag ench = tag.getCompoundOrEmpty("enchantments");
			if (!ench.isEmpty()) {
				enchants = new LinkedHashMap<>();
				for (String k : ench.keySet()) enchants.put(k, ench.getIntOr(k, 0));
			}
			int up = tag.getIntOr("upgrade_level", tag.getIntOr("dungeon_item_level", 0));
			if (up > 0) stars = up;
		}
		String number = ItemInfo.slotNumber(new ItemInfo.Facts(name, id, enchants, stars, title));
		String search = (name + " " + (id == null ? "" : id)).toLowerCase(Locale.ROOT);
		return new Info(ItemInfo.rarity(lore), number, search);
	}
}
