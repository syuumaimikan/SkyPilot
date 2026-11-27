package dev.skypilot.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.WardrobeTools;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import java.util.concurrent.ThreadLocalRandom;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Standalone Fabric 26.2 client entrypoint for SkyPilot Wardrobe ({@code skypilot_wardrobe}).
 *
 * <p>Strictly client-side visuals, interactive pet bonding, and zero-server LAN auto-sync:
 * sends ZERO custom packets to any Minecraft game server.
 */
public final class WardrobeModClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skypilot_wardrobe", "main"));

    private static boolean wasUseKeyDown;

    @Override
    public void onInitializeClient() {
        WardrobeRuntime.init(FabricLoader.getInstance().getConfigDir().resolve("skypilot"));

        KeyMapping openWardrobe = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.skypilot_wardrobe.open", InputConstants.KEY_K, CATEGORY));
        KeyMapping openAdjust = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.skypilot_wardrobe.adjust", InputConstants.UNKNOWN.getValue(), CATEGORY));
        KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.skypilot_wardrobe.settings", InputConstants.UNKNOWN.getValue(), CATEGORY));
        KeyMapping openVfx = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.skypilot_wardrobe.vfx", InputConstants.UNKNOWN.getValue(), CATEGORY));
        KeyMapping petInteract = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.skypilot_wardrobe.pet_interact", InputConstants.KEY_H, CATEGORY));
        KeyMapping randomOutfit = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.skypilot_wardrobe.random", InputConstants.UNKNOWN.getValue(), CATEGORY));

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new CosmeticLayer(avatar));
            }
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(CosmeticWorldRenderer::render);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && player == Minecraft.getInstance().player) {
                PetWorldPhysics.onPlayerAttack(entity);
                WardrobeVfxEngine.onPlayerAttack(entity);
            }
            return InteractionResult.PASS;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            var root = literal("wardrobe")
                    .executes(c -> {
                        openLater(() -> new AppearanceScreen(null));
                        return 1;
                    })
                    .then(literal("adjust").executes(c -> {
                        openLater(() -> new WardrobeAdjustScreen(null, "cape"));
                        return 1;
                    }))
                    .then(literal("settings").executes(c -> {
                        openLater(() -> new WardrobeSettingsScreen(null));
                        return 1;
                    }))
                    .then(literal("vfx").executes(c -> {
                        openLater(() -> new WardrobeVfxScreen(null));
                        return 1;
                    }))
                    .then(literal("outfits").executes(c -> {
                        openLater(() -> new OutfitScreen(null));
                        return 1;
                    }))
                    .then(literal("play").executes(c -> {
                        openLater(() -> new PetInteractScreen(null));
                        return 1;
                    }))
                    .then(literal("pat").executes(c -> {
                        Minecraft.getInstance().execute(() -> PetWorldPhysics.performAction(PetWorldPhysics.Action.PAT, null));
                        return 1;
                    }))
                    .then(literal("treat").executes(c -> {
                        Minecraft.getInstance().execute(() -> PetWorldPhysics.performAction(PetWorldPhysics.Action.TREAT, null));
                        return 1;
                    }))
                    .then(literal("fetch").executes(c -> {
                        Minecraft.getInstance().execute(() -> PetWorldPhysics.performAction(PetWorldPhysics.Action.FETCH, null));
                        return 1;
                    }))
                    .then(literal("trick").executes(c -> {
                        Minecraft.getInstance().execute(() -> PetWorldPhysics.performAction(PetWorldPhysics.Action.TRICK, null));
                        return 1;
                    }))
                    .then(literal("sit").executes(c -> {
                        Minecraft.getInstance().execute(() -> PetWorldPhysics.performAction(PetWorldPhysics.Action.TOGGLE_STAY, null));
                        return 1;
                    }))
                    .then(literal("perch").executes(c -> {
                        Minecraft.getInstance().execute(() -> PetWorldPhysics.performAction(PetWorldPhysics.Action.TOGGLE_PERCH, null));
                        return 1;
                    }))
                    .then(literal("sync").executes(c -> {
                        RemoteWardrobeSync.markDirty();
                        var rt = WardrobeRuntime.get();
                        String msg = rt != null ? RemoteWardrobeSync.statusSummary(rt.japanese()) : "OK";
                        c.getSource().sendFeedback(Component.literal("[Wardrobe] " + msg));
                        return 1;
                    }));
            dispatcher.register(root);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            PetWorldPhysics.tick(mc);
            CosmeticParticles.tick(mc);
            WardrobeVfxEngine.tick(mc);
            RemoteWardrobeSync.tick(mc);

            boolean inGame = mc.player != null && mc.gui.screen() == null;

            // Direct empty-hand Right-Click petting while looking at your pet or companion!
            boolean useDown = inGame && mc.options.keyUse.isDown();
            if (useDown && !wasUseKeyDown && mc.player.getMainHandItem().isEmpty()) {
                PetWorldPhysics.tryDirectPetClick(mc);
            }
            wasUseKeyDown = useDown;

            while (openWardrobe.consumeClick()) {
                if (inGame) mc.gui.setScreen(new AppearanceScreen(null));
            }
            while (openAdjust.consumeClick()) {
                if (inGame) mc.gui.setScreen(new WardrobeAdjustScreen(null, "cape"));
            }
            while (openSettings.consumeClick()) {
                if (inGame) mc.gui.setScreen(new WardrobeSettingsScreen(null));
            }
            while (openVfx.consumeClick()) {
                if (inGame) mc.gui.setScreen(new WardrobeVfxScreen(null));
            }
            while (petInteract.consumeClick()) {
                if (inGame) {
                    if (mc.player.isShiftKeyDown()) {
                        mc.gui.setScreen(new PetInteractScreen(null));
                    } else {
                        String aimed = PetWorldPhysics.aimedPetSlot(mc.player, 5.0);
                        PetWorldPhysics.performAction(PetWorldPhysics.Action.PAT, aimed);
                    }
                }
            }
            while (randomOutfit.consumeClick()) {
                if (inGame) {
                    var rt = WardrobeRuntime.get();
                    if (rt != null) {
                        WardrobeTools.randomize(rt.appearance(), ThreadLocalRandom.current());
                        rt.save();
                        mc.gui.hud.setOverlayMessage(Component.literal(
                                rt.japanese() ? "ランダムコーデを適用しました" : "Random outfit applied"), false);
                    }
                }
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> RemoteWardrobeSync.markDirty());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            PetWorldPhysics.reset();
            WardrobeVfxEngine.reset();
            CapePhysics.clear();
            RemoteWardrobeSync.onDisconnect();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> RemoteWardrobeSync.shutdown());

        WardrobeRuntime.LOG.info("SkyPilot Wardrobe standalone mod loaded ({} bundled 3D cosmetics)", CosmeticCatalog.ITEMS.size());
    }

    private static void openLater(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(factory.get()));
    }
}
