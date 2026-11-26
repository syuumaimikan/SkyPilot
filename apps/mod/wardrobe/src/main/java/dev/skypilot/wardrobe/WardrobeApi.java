package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Clean public integration surface exposed by the standalone {@code skypilot_wardrobe} Fabric mod
 * so companion mods (such as SkyPilot) can open Wardrobe screens or notify attack animations
 * without a hard compile-time dependency.
 */
public final class WardrobeApi {
    private WardrobeApi() {}

    public static Screen createWardrobeScreen(Screen parent) {
        return new AppearanceScreen(parent);
    }

    public static Screen createOutfitScreen(Screen parent) {
        return new OutfitScreen(parent);
    }

    public static Screen createAdjustScreen(Screen parent) {
        return new WardrobeAdjustScreen(parent, "cape");
    }

    public static Screen createSettingsScreen(Screen parent) {
        return new WardrobeSettingsScreen(parent);
    }

    public static Screen createVfxScreen(Screen parent) {
        return new WardrobeVfxScreen(parent);
    }

    public static void openWardrobe(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new AppearanceScreen(parent));
    }

    public static void openOutfits(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new OutfitScreen(parent));
    }

    public static void openSettings(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new WardrobeSettingsScreen(parent));
    }

    public static void openVfx(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new WardrobeVfxScreen(parent));
    }

    public static void onPlayerAttack(Entity target) {
        PetWorldPhysics.onPlayerAttack(target);
        WardrobeVfxEngine.onPlayerAttack(target);
    }

    public static void randomizeAndSave() {
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null) return;
        WardrobeTools.randomize(rt.appearance(), ThreadLocalRandom.current());
        rt.save();
    }

    public static void syncFromExternal(SkyPilotConfig.Appearance external) {
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null || external == null) return;
        rt.setAppearance(external);
    }

    public static SkyPilotConfig.Appearance currentAppearance() {
        WardrobeRuntime rt = WardrobeRuntime.get();
        return rt == null ? new SkyPilotConfig.Appearance() : rt.appearance();
    }

    public static int submittedVoxels() {
        return CosmeticLayer.submittedVoxels;
    }
}

