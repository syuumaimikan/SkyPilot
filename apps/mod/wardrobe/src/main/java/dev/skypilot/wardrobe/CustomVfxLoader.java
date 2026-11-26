package dev.skypilot.wardrobe;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypilot.mod.core.WardrobeVfxCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads user-authored custom VFX PNG sprites from {@code config/skypilot/vfx/*.png}, caches their
 * bytes during mod init, uploads them lazily as {@link DynamicTexture} on the render thread, and
 * registers them into {@link WardrobeVfxCatalog} so players can drop their own glowing artwork for
 * Attack, Kill, Move, Jump, Dash, Projectile, and Ambient VFX!
 */
final class CustomVfxLoader {
    private static final Map<String, Identifier> REGISTERED = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PENDING_PNG = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CustomVfxLoader() {}

    static Path ensureDir(Path configDir) throws IOException {
        Path dir = configDir.resolve("vfx");
        Files.createDirectories(dir);
        return dir;
    }

    static Path exportSampleTemplates(Path configDir) throws IOException {
        Path dir = ensureDir(configDir);
        String[] samples = {"sakura_petal", "magic_circle", "heart_glow", "star_spark", "butterfly"};
        for (String name : samples) {
            Path dst = dir.resolve("sample_" + name + ".png");
            if (!Files.exists(dst)) {
                try (InputStream in = CustomVfxLoader.class.getResourceAsStream("/assets/skypilot/textures/vfx/" + name + ".png")) {
                    if (in != null) Files.write(dst, in.readAllBytes());
                }
            }
        }
        return dir;
    }

    static List<WardrobeVfxCatalog.VfxStyle> reload(Path configDir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null && mc.isSameThread()) {
            for (Identifier id : REGISTERED.values()) {
                try { mc.getTextureManager().release(id); } catch (Exception ignored) {}
            }
        }
        REGISTERED.clear();
        PENDING_PNG.clear();
        FAILED.clear();
        if (configDir == null) {
            WardrobeVfxCatalog.setCustomStyles(List.of());
            return List.of();
        }
        Path dir = configDir.resolve("vfx");
        if (!Files.isDirectory(dir)) {
            WardrobeVfxCatalog.setCustomStyles(List.of());
            return List.of();
        }

        List<WardrobeVfxCatalog.VfxStyle> styles = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .limit(64)
                    .toList();
            for (Path file : files) {
                String fname = file.getFileName().toString();
                String stem = fname.substring(0, fname.length() - 4);
                String safe = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                if (safe.isEmpty()) continue;
                String id = "custom_vfx_" + safe;
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    if (bytes.length < 16 || bytes.length > 4 * 1024 * 1024) continue;
                    PENDING_PNG.put(id, bytes);
                } catch (Exception e) {
                    WardrobeRuntime.LOG.warn("Failed to read custom VFX {}: {}", fname, e.getMessage());
                    continue;
                }
                String display = stem.replace('_', ' ');
                styles.add(new WardrobeVfxCatalog.VfxStyle(
                        id, display, "自作: " + display,
                        "Custom user VFX sprite loaded from config/skypilot/vfx/" + fname,
                        "config/skypilot/vfx/" + fname + " から読み込んだ自作エフェクト",
                        0xFFFFFF, 0x55D6D2, id, "shockwave_ring", "custom", true
                ));
            }
        } catch (Exception e) {
            WardrobeRuntime.LOG.warn("Could not scan custom VFX directory: {}", e.getMessage());
        }
        WardrobeVfxCatalog.setCustomStyles(styles);
        return styles;
    }

    static Identifier resolveTexture(String texKey) {
        if (texKey == null || texKey.isEmpty()) {
            return Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/star_spark.png");
        }
        Identifier uploaded = REGISTERED.get(texKey);
        if (uploaded != null) return uploaded;
        byte[] pending = PENDING_PNG.get(texKey);
        if (pending != null && !FAILED.contains(texKey)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null && mc.isSameThread()) {
                try (InputStream in = new ByteArrayInputStream(pending)) {
                    NativeImage img = NativeImage.read(in);
                    String safe = texKey.replaceFirst("^custom_vfx_", "");
                    Identifier texId = Identifier.fromNamespaceAndPath("skypilot_wardrobe", "custom_vfx/" + safe);
                    mc.getTextureManager().register(texId, new DynamicTexture(() -> "Wardrobe custom VFX " + safe, img));
                    REGISTERED.put(texKey, texId);
                    PENDING_PNG.remove(texKey);
                    return texId;
                } catch (Exception e) {
                    FAILED.add(texKey);
                    WardrobeRuntime.LOG.warn("Failed to upload custom VFX {}: {}", texKey, e.getMessage());
                }
            }
            return Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/star_spark.png");
        }
        if (texKey.startsWith("custom_vfx_")) {
            return Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/star_spark.png");
        }
        return Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/" + texKey + ".png");
    }
}
