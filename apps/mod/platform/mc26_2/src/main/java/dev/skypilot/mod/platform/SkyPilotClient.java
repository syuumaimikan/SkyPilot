package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Fabric entrypoint for Minecraft 26.2.
 *
 * <p>Safety: SkyPilot only reads what the client already displays (scoreboard, tab list, chat,
 * item tooltips) and draws its own overlay. It sends no packets, simulates no input and has no
 * automation of any kind (mining, farming, combat, movement, GUI clicks, auction/bazaar actions).
 */
public final class SkyPilotClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skypilot", "main"));
	private int ticks;

	@Override
	public void onInitializeClient() {
		SkyPilot.init(FabricLoader.getInstance().getConfigDir().resolve("skypilot"));

		KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skypilot.settings", InputConstants.UNKNOWN.getValue(), CATEGORY));
		KeyMapping editHud = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skypilot.edit_hud", InputConstants.UNKNOWN.getValue(), CATEGORY));
		KeyMapping openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skypilot.menu", InputConstants.KEY_J, CATEGORY));

		HudElementRegistry.attachElementAfter(VanillaHudElements.SCOREBOARD, Identifier.fromNamespaceAndPath("skypilot", "hud"), HudRenderer::render);
		// after the SkyPilot HUD, so the info box can sit below it
		HudElementRegistry.attachElementAfter(Identifier.fromNamespaceAndPath("skypilot", "hud"), Identifier.fromNamespaceAndPath("skypilot", "info"), InfoHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SCOREBOARD, Identifier.fromNamespaceAndPath("skypilot", "guide"), (g, delta) -> {
			Waypoints.hud(g, mc().font, g.guiWidth());
			FarmBuilder.hud(g, mc().font, g.guiWidth(), Waypoints.target() != null ? 17 : 2);
		});
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			Waypoints.render();
			FarmBuilder.render();
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> Commands.register(dispatcher));
		ClientReceiveMessageEvents.ALLOW_GAME.register(ChatFeatures::allow);
		ClientReceiveMessageEvents.MODIFY_GAME.register(ChatFeatures::modify);
		ClientReceiveMessageEvents.GAME.register(ChatHook::onGameMessage);
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> PriceCheck.tooltip(stack, lines));
		ScreenOverlays.register();
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> Trackers.blockBroken(state));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openSettings.consumeClick()) mc.gui.setScreen(new SettingsScreen(null));
			while (editHud.consumeClick()) mc.gui.setScreen(new HudEditorScreen(null));
			while (openMenu.consumeClick()) mc.gui.setScreen(new MenuScreen(null, null));
			FarmBuilder.tick(mc);
			Activity.tick(mc);
			if (++ticks % 20 == 0) {
				SkyPilot.get().tickSecond(mc);
				FlipAlerts.tickSecond(mc);
				Trackers.tickSecond(mc);
				Activity.tickSecond(mc);
				Timers.tickSecond(mc);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> SkyPilot.get().refreshHud(System.currentTimeMillis(), true));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			SkyPilot.get().endSession();
			ClickTranslate.clear();
			ChatFeatures.clear();
			Activity.disconnected();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> SkyPilot.get().shutdown());

		SkyPilot.LOG.info("SkyPilot loaded for Minecraft 26.2 (server: {})", SkyPilot.get().config().apiBaseUrl);
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
