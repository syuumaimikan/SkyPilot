package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Mods' own icons (the image their fabric.mod.json names), loaded once from the mod's files. */
final class ModIcons {
	record Icon(Identifier id, int width, int height) {
	}

	private static final Map<String, Icon> ICONS = new HashMap<>();

	private ModIcons() {
	}

	/** Null when the mod has no icon or it cannot be read. */
	static Icon get(ModManager.Entry e) {
		if (e.iconPath() == null) return null;
		if (ICONS.containsKey(e.id())) return ICONS.get(e.id());
		Icon icon = null;
		try {
			Path path = FabricLoader.getInstance().getModContainer(e.id()).flatMap(c -> c.findPath(e.iconPath())).orElse(null);
			if (path != null && Files.size(path) < 2_000_000) {
				try (InputStream in = Files.newInputStream(path)) {
					NativeImage img = NativeImage.read(in);
					Identifier id = Identifier.fromNamespaceAndPath("skypilot", "modicon/" + e.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
					int w = img.getWidth();
					int h = img.getHeight();
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "skypilot mod icon " + e.id(), img));
					icon = new Icon(id, w, h);
				}
			}
		} catch (Exception ex) {
			SkyPilot.LOG.debug("No icon for {}: {}", e.id(), ex.toString());
		}
		ICONS.put(e.id(), icon);
		return icon;
	}
}
