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
	private record Panels(ItemBrowser browser, ChestValue chest, VisitorPanel visitor, AuctionHighlight auction, SackValue sack, BazaarOrders orders, String flipKind) {
	}

	private static final Map<Screen, Panels> PANELS = new WeakHashMap<>();
	private static final Map<Screen, ExperimentOverlay> EXPERIMENTS = new WeakHashMap<>();

	private ScreenOverlays() {
	}

	static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || !(screen instanceof ContainerScreenAccessor)) return;
			Panels p = new Panels(new ItemBrowser(screen), new ChestValue(container), new VisitorPanel(container), new AuctionHighlight(container),
					new SackValue(container), new BazaarOrders(container), ContainerPanel.kindOf(screen.getTitle().getString()));
			PANELS.put(screen, p);
			ExperimentOverlay experiments = new ExperimentOverlay(container);
			EXPERIMENTS.put(screen, experiments);
			TerminalOverlay terminal = new TerminalOverlay(container);
			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
				if (TerminalOverlay.active(container)) { terminal.render(g,mouseX,mouseY); return; }
				ContainerScreenAccessor box = (ContainerScreenAccessor) s;
				var font = Minecraft.getInstance().font;
				if (p.browser().layout(box.skypilot$leftPos() + box.skypilot$imageWidth() + 6)) p.browser().render(g, font, mouseX, mouseY);
				if (p.flipKind() != null) {
					ContainerPanel.render(s, p.flipKind(), g, mouseX, mouseY);
				} else if (p.chest().layout()) {
					p.chest().render(g, font, mouseX, mouseY);
				} else if (p.visitor().layout()) {
					p.visitor().render(g, font);
				} else if (p.sack().layout()) {
					p.sack().render(g, font);
				}
				p.auction().render(g);
				p.orders().render(g, font, mouseX, mouseY);
				OrderSync.observe(container);
				experiments.render(g);
				ItemIcons.flush();
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (TerminalOverlay.active(container)) return true;
				boolean browser = p.browser().layout(((ContainerScreenAccessor) s).skypilot$leftPos() + ((ContainerScreenAccessor) s).skypilot$imageWidth() + 6)
						&& p.browser().click(event.x(), event.y(), event.button());
				boolean chest = !browser && p.flipKind() == null && p.chest().layout() && p.chest().click(event.x(), event.y());
				boolean flips = !browser && p.flipKind() != null && SkyPilot.get().config().market.containerPanel && ContainerPanel.click(p.flipKind(), event.x(), event.y());
				return !(browser || chest || flips);
			});
			ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> !p.browser().drag(event.x()));
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !p.browser().release(event.x()));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, sx, sy) -> {
				boolean used = p.browser().scroll(mx, my, sy) || (p.flipKind() == null && p.chest().layout() && p.chest().scroll(mx, my, sy));
				return !used;
			});
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !p.browser().key(event) && !lookupKey(container, event.key())
					&& !(event.key() == InputConstants.KEY_C && event.hasControlDown() && copyId(container)));
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

	/** Ctrl+C over a SkyBlock item copies its id (inventory.copy_id). */
	static boolean copyId(AbstractContainerScreen<?> screen) {
		if (!Feat.on(Features.INV_COPY_ID)) return false;
		Slot hovered = ((ContainerScreenAccessor) screen).skypilot$hoveredSlot();
		PriceCheck.Item item = hovered == null ? null : PriceCheck.item(hovered.getItem());
		if (item == null) return false;
		Minecraft mc = Minecraft.getInstance();
		mc.keyboardHandler.setClipboard(item.skyblockId());
		McText.info(SkyPilot.get().lang().t("Copied ", "コピーしました: ") + item.skyblockId());
		return true;
	}

	/** For tests. */
	static ExperimentOverlay experimentsOf(Screen screen) { return EXPERIMENTS.get(screen); }
	/** For tests. */
	static AuctionHighlight auctionOf(Screen screen) {
		Panels p = PANELS.get(screen);
		return p == null ? null : p.auction();
	}

	/** For tests. */
	static ItemBrowser browserOf(Screen screen) {
		Panels p = PANELS.get(screen);
		return p == null ? null : p.browser();
	}

	/** For tests. */
	static BazaarOrders ordersOf(Screen screen) {
		Panels p = PANELS.get(screen);
		return p == null ? null : p.orders();
	}

	/** For tests. */
	static ChestValue chestOf(Screen screen) {
		Panels p = PANELS.get(screen);
		return p == null ? null : p.chest();
	}
}
