package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.SkyPilotConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Optional soft bridge from the core SkyPilot mod to the standalone {@code skypilot_wardrobe}
 * Fabric mod. When {@code skypilot_wardrobe} is installed, opens its 3D Wardrobe screens and
 * keeps appearance configs synchronized; if removed, degrades gracefully with zero crashes.
 */
final class WardrobeBridge {
    private static final String API_CLASS = "dev.skypilot.wardrobe.WardrobeApi";

    private WardrobeBridge() {}

    static boolean isInstalled() {
        return FabricLoader.getInstance().isModLoaded("skypilot_wardrobe");
    }

    static Screen createWardrobeScreen(Screen parent) {
        Screen s = invokeScreen("createWardrobeScreen", parent);
        if (s != null) return s;
        notifyMissing();
        return parent;
    }

    static Screen createOutfitScreen(Screen parent) {
        Screen s = invokeScreen("createOutfitScreen", parent);
        if (s != null) return s;
        notifyMissing();
        return parent;
    }

    static Screen createSettingsScreen(Screen parent) {
        Screen s = invokeScreen("createSettingsScreen", parent);
        if (s != null) return s;
        notifyMissing();
        return parent;
    }

    static void openWardrobe(Screen parent) {
        Screen s = invokeScreen("createWardrobeScreen", parent);
        if (s != null) Minecraft.getInstance().gui.setScreen(s);
        else notifyMissing();
    }

    static void openSettings(Screen parent) {
        Screen s = invokeScreen("createSettingsScreen", parent);
        if (s != null) Minecraft.getInstance().gui.setScreen(s);
        else notifyMissing();
    }

    static void onPlayerAttack(Entity target) {
        if (!isInstalled() || target == null) return;
        try {
            Class.forName(API_CLASS).getMethod("onPlayerAttack", Entity.class).invoke(null, target);
        } catch (Throwable ignored) {}
    }

    static void syncFromSkyPilot(SkyPilotConfig.Appearance appearance) {
        if (!isInstalled() || appearance == null) return;
        try {
            Class.forName(API_CLASS).getMethod("syncFromExternal", SkyPilotConfig.Appearance.class).invoke(null, appearance);
        } catch (Throwable ignored) {}
    }

    static int submittedVoxels() {
        if (!isInstalled()) return 0;
        try {
            return (Integer) Class.forName(API_CLASS).getMethod("submittedVoxels").invoke(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Screen invokeScreen(String method, Screen parent) {
        if (!isInstalled()) return null;
        try {
            return (Screen) Class.forName(API_CLASS).getMethod(method, Screen.class).invoke(null, parent);
        } catch (Throwable e) {
            SkyPilot.LOG.warn("Wardrobe bridge call {} failed: {}", method, e.getMessage());
            return null;
        }
    }

    private static void notifyMissing() {
        var mc = Minecraft.getInstance();
        var sp = SkyPilot.get();
        String msg = sp != null
                ? sp.lang().t("SkyPilot Wardrobe mod is not installed.", "SkyPilot ワードローブMODが未導入です。")
                : "SkyPilot Wardrobe mod is not installed.";
        if (mc != null && mc.gui != null && mc.gui.hud != null) {
            mc.gui.hud.setOverlayMessage(Component.literal(msg), false);
        }
    }
}

