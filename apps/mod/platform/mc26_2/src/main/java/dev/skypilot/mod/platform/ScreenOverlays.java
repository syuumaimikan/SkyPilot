package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import net.minecraft.world.inventory.Slot;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Attaches SkyPilot's panels to inventory windows: the item browser on the right, the chest
 * value on the left (storage windows) and the flip panel on the left (AH / Bazaar). Clicks,
 * scrolls and typing inside a panel are consumed so the window never sees them - a click next
 * to a chest can't drop the item on the cursor.
 */
final class ScreenOverlays {
	private record Panels(ItemBrowser browser, ChestValue chest, VisitorPanel visitor, String flipKind) {
	}

	private static final Map<Screen, Panels> PANELS = new WeakHashMap<>();

	private ScreenOverlays() {
	}

	static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || !(screen instanceof ContainerScreenAccessor)) return;
			Panels p = new Panels(new ItemBrowser(screen), new ChestValue(container), new VisitorPanel(container), ContainerPanel.kindOf(screen.getTitle().getString()));
			PANELS.put(screen, p);
			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
				ContainerScreenAccessor box = (ContainerScreenAccessor) s;
				var font = Minecraft.getInstance().font;
				if (p.browser().layout(box.skypilot$leftPos() + box.skypilot$imageWidth() + 6)) p.browser().render(g, font, mouseX, mouseY);
				if (p.flipKind() != null) {
					ContainerPanel.render(s, p.flipKind(), g, mouseX, mouseY);
				} else if (p.chest().layout()) {
					p.chest().render(g, font, mouseX, mouseY);
				} else if (p.visitor().layout()) {
					p.visitor().render(g, font);
				}
				ItemIcons.flush();
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				boolean browser = p.browser().layout(((ContainerScreenAccessor) s).skypilot$leftPos() + ((ContainerScreenAccessor) s).skypilot$imageWidth() + 6)
						&& p.browser().click(event.x(), event.y(), event.button());
				boolean chest = !browser && p.flipKind() == null && p.chest().layout() && p.chest().click(event.x(), event.y());
				return !(browser || chest);
			});
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, sx, sy) -> {
				boolean used = p.browser().scroll(mx, my, sy) || (p.flipKind() == null && p.chest().layout() && p.chest().scroll(mx, my, sy));
				return !used;
			});
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !p.browser().key(event) && !lookupKey(container, event.key()));
			ScreenKeyboardEvents.allowCharType(screen).register((s, event) -> !p.browser().charTyped(event));
		});
	}

	/**
	 * R / U over a SkyBlock item opens its page on the recipe / "used in" tab (JEI style). Only
	 * while the item list's search box is not being typed in; opening our screen leaves the
	 * window open on the server, and closing our screen returns to it.
	 */
	static boolean lookupKey(AbstractContainerScreen<?> screen, int key) {
		if ((key != InputConstants.KEY_R && key != InputConstants.KEY_U) || !Feat.on(Features.INV_RECIPE_KEYS)) return false;
		Slot hovered = ((ContainerScreenAccessor) screen).skypilot$hoveredSlot();
		PriceCheck.Item item = hovered == null ? null : PriceCheck.item(hovered.getItem());
		if (item == null) return false;
		ItemDetailScreen.Tab tab = key == InputConstants.KEY_R ? ItemDetailScreen.Tab.RECIPES : ItemDetailScreen.Tab.USES;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ItemDetailScreen(screen, item.skyblockId(), tab)));
		return true;
	}

	/** For tests. */
	static ChestValue chestOf(Screen screen) {
		Panels p = PANELS.get(screen);
		return p == null ? null : p.chest();
	}
}
