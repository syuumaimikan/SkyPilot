package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * The player's head for the launcher's sidebar, from the public Mojang profile of the name the
 * player typed in Settings (no sign-in, no account data): name → UUID (api.mojang.com), UUID →
 * skin URL (sessionserver.mojang.com), skin PNG (textures.minecraft.net). The face (8×8 at 8,8)
 * and the hat layer (8×8 at 40,8) are cut out of the skin. Cached for a day.
 */
public final class Avatar {
	private static final long DAY = 86_400_000L;

	@FunctionalInterface
	public interface Fetch {
		byte[] get(String url) throws IOException;
	}

	private Avatar() {
	}

	public static boolean validName(String name) {
		return name != null && name.matches("[A-Za-z0-9_]{3,16}");
	}

	static Path cacheFile(Path launcherDir, String name) {
		return launcherDir.resolve("cache").resolve("head-" + name.toLowerCase(java.util.Locale.ROOT) + ".png");
	}

	/** The cached head (any age), or null. */
	public static BufferedImage cached(Path launcherDir, String name) {
		if (!validName(name)) return null;
		try {
			Path f = cacheFile(launcherDir, name);
			return Files.isRegularFile(f) ? ImageIO.read(f.toFile()) : null;
		} catch (IOException e) {
			return null;
		}
	}

	public static boolean fresh(Path launcherDir, String name) {
		Path f = cacheFile(launcherDir, name);
		return Files.isRegularFile(f) && System.currentTimeMillis() - f.toFile().lastModified() < DAY;
	}

	/** Fetch the head (null when the name doesn't exist). */
	public static BufferedImage fetch(Path launcherDir, String name, Fetch fetch) throws IOException {
		if (!validName(name)) throw new IOException("invalid name");
		byte[] profile = fetch.get("https://api.mojang.com/users/profiles/minecraft/" + name);
		if (profile == null) return null;
		String uuid = JsonParser.parseString(new String(profile, StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();
		if (!uuid.matches("[0-9a-fA-F]{32}")) throw new IOException("unexpected profile id");
		byte[] session = fetch.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
		if (session == null) return null;
		String skinUrl = skinUrl(new String(session, StandardCharsets.UTF_8));
		if (skinUrl == null) return null;
		byte[] skin = fetch.get(skinUrl);
		if (skin == null) return null;
		BufferedImage head = head(ImageIO.read(new ByteArrayInputStream(skin)));
		if (head != null) {
			Path f = cacheFile(launcherDir, name);
			Files.createDirectories(f.getParent());
			ImageIO.write(head, "png", f.toFile());
		}
		return head;
	}

	/** The skin URL from a session profile (textures property, base64 JSON), as https. */
	static String skinUrl(String sessionJson) {
		JsonObject root = JsonParser.parseString(sessionJson).getAsJsonObject();
		for (var p : root.getAsJsonArray("properties")) {
			JsonObject o = p.getAsJsonObject();
			if (!"textures".equals(o.get("name").getAsString())) continue;
			JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(o.get("value").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject skin = tex.getAsJsonObject("textures").getAsJsonObject("SKIN");
			if (skin == null) return null;
			String url = skin.get("url").getAsString().replaceFirst("^http://", "https://");
			return url.startsWith("https://textures.minecraft.net/texture/") ? url : null;
		}
		return null;
	}

	/** 8×8 face with the hat layer drawn over it. */
	static BufferedImage head(BufferedImage skin) {
		if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
		BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
		g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
		g.dispose();
		return out;
	}
}
