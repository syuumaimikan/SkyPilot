package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CustomCosmetics;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeConfigStore;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Runtime state for the standalone SkyPilot Wardrobe Fabric mod.
 * Works completely independently of the SkyPilot SkyBlock mod while automatically
 * sharing {@code config/skypilot/wardrobe.json} (and mirroring {@code config.json} when present).
 */
public final class WardrobeRuntime {
    public static final Logger LOG = LoggerFactory.getLogger("SkyPilotWardrobe");
    private static WardrobeRuntime instance;

    private final Path configDir;
    private final WardrobeConfigStore store;
    private SkyPilotConfig.Appearance appearance;

    private WardrobeRuntime(Path configDir) {
        this.configDir = configDir;
        CustomCosmetics.reload(configDir);
        CustomVfxLoader.reload(configDir);
        this.store = new WardrobeConfigStore(configDir);
        this.appearance = store.load();
    }

    static void init(Path configDir) {
        instance = new WardrobeRuntime(configDir);
    }

    public static WardrobeRuntime get() {
        return instance;
    }

    public Path configDir() {
        return configDir;
    }

    public SkyPilotConfig.Appearance appearance() {
        return appearance;
    }

    public void setAppearance(SkyPilotConfig.Appearance updated) {
        if (updated != null) {
            this.appearance = updated;
            save();
        }
    }

    public void save() {
        try {
            store.save(appearance);
        } catch (IOException e) {
            LOG.warn("Could not save wardrobe.json: {}", e.getMessage());
        }
        RemoteWardrobeSync.markDirty();
    }

    public Lang lang() {
        Minecraft mc = Minecraft.getInstance();
        String gameLang = (mc != null && mc.options != null) ? mc.options.languageCode : null;
        return Lang.resolve("auto", gameLang);
    }

    public boolean japanese() {
        return "ja".equals(lang().t("en", "ja"));
    }
}

