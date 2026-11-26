package dev.skypilot.mod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Standalone persistence for {@code config/skypilot/wardrobe.json} with seamless bidirectional
 * fallback/mirroring to {@code config/skypilot/config.json} when SkyPilot Mod or SkyPilot Launcher
 * is also installed.
 */
public final class WardrobeConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path wardrobeFile;
    private final Path skyPilotConfigFile;

    public WardrobeConfigStore(Path configDir) {
        this.wardrobeFile = configDir.resolve("wardrobe.json");
        this.skyPilotConfigFile = configDir.resolve("config.json");
    }

    public Path file() {
        return wardrobeFile;
    }

    /**
     * Loads the newest appearance state between {@code wardrobe.json} and {@code config.json}
     * (so edits made in the Launcher, SkyPilot Mod, or standalone Wardrobe Mod all stay in sync).
     */
    public SkyPilotConfig.Appearance load() {
        boolean hasWardrobe = Files.isRegularFile(wardrobeFile);
        boolean hasSkyPilot = Files.isRegularFile(skyPilotConfigFile);
        SkyPilotConfig.Appearance loaded = null;

        if (hasWardrobe && hasSkyPilot) {
            try {
                long wTime = Files.getLastModifiedTime(wardrobeFile).toMillis();
                long sTime = Files.getLastModifiedTime(skyPilotConfigFile).toMillis();
                loaded = sTime > wTime + 1500 ? readFromSkyPilotConfig() : readWardrobeFile();
            } catch (Exception ignored) {
                loaded = readWardrobeFile();
            }
        } else if (hasWardrobe) {
            loaded = readWardrobeFile();
        } else if (hasSkyPilot) {
            loaded = readFromSkyPilotConfig();
        }

        if (loaded == null) loaded = new SkyPilotConfig.Appearance();
        SkyPilotConfig wrap = new SkyPilotConfig();
        wrap.appearance = loaded;
        wrap.sanitize();
        return wrap.appearance;
    }

    /** Saves to {@code wardrobe.json} and mirrors into {@code config.json} if it exists. */
    public void save(SkyPilotConfig.Appearance appearance) throws IOException {
        SkyPilotConfig wrap = new SkyPilotConfig();
        wrap.appearance = appearance == null ? new SkyPilotConfig.Appearance() : appearance;
        wrap.sanitize();
        writeAtomic(wardrobeFile, GSON.toJson(wrap.appearance));

        if (Files.isRegularFile(skyPilotConfigFile)) {
            try {
                ConfigStore cs = new ConfigStore(skyPilotConfigFile.getParent());
                SkyPilotConfig existing = cs.load();
                existing.appearance = wrap.appearance;
                cs.save(existing);
            } catch (Exception ignored) {
                // Standalone Wardrobe save already succeeded.
            }
        }
    }

    private SkyPilotConfig.Appearance readWardrobeFile() {
        try {
            String raw = Files.readString(wardrobeFile, StandardCharsets.UTF_8);
            return GSON.fromJson(raw, SkyPilotConfig.Appearance.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private SkyPilotConfig.Appearance readFromSkyPilotConfig() {
        try {
            ConfigStore cs = new ConfigStore(skyPilotConfigFile.getParent());
            return cs.load().appearance;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeAtomic(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

