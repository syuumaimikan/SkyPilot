package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/**
 * The signed-in Microsoft account. On disk ({@code account.json} in the launcher folder): the
 * player name and UUID, and the Microsoft refresh token encrypted with Windows Data Protection
 * (only this Windows user on this PC can decrypt it). The Minecraft access token is never
 * written anywhere; it lives in memory for the session. Nothing here is sent to SkyPilot's
 * server. Sign-out deletes the file.
 */
public final class AccountStore {
	public static final String FILE = "account.json";

	/** What the UI shows (no secrets). */
	public record Saved(String name, String uuid) {
	}

	private AccountStore() {
	}

	public static Saved load(Path launcherDir) {
		try {
			JsonObject o = JsonParser.parseString(Files.readString(launcherDir.resolve(FILE), StandardCharsets.UTF_8)).getAsJsonObject();
			String name = o.get("name").getAsString();
			String uuid = o.get("uuid").getAsString();
			return Avatar.validName(name) && uuid.matches("[0-9a-fA-F]{32}") ? new Saved(name, uuid) : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** The saved refresh token, or null (none, another PC / user, or damaged). */
	public static String refreshToken(Path launcherDir) {
		if (!Dpapi.available()) return null;
		try {
			JsonObject o = JsonParser.parseString(Files.readString(launcherDir.resolve(FILE), StandardCharsets.UTF_8)).getAsJsonObject();
			if (!o.has("refresh")) return null;
			return new String(Dpapi.unprotect(Base64.getDecoder().decode(o.get("refresh").getAsString())), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Remember the account. The refresh token is kept only when Windows Data Protection is
	 * available; otherwise only the name is saved and you sign in again next time.
	 */
	public static boolean save(Path launcherDir, String name, String uuid, String refreshToken) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.addProperty("uuid", uuid);
		boolean kept = false;
		if (refreshToken != null && !refreshToken.isEmpty() && Dpapi.available()) {
			o.addProperty("refresh", Base64.getEncoder().encodeToString(Dpapi.protect(refreshToken.getBytes(StandardCharsets.UTF_8))));
			o.addProperty("protection", "Windows DPAPI (current user)");
			kept = true;
		}
		Files.createDirectories(launcherDir);
		Path tmp = launcherDir.resolve(FILE + ".tmp");
		Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
		Files.move(tmp, launcherDir.resolve(FILE), StandardCopyOption.REPLACE_EXISTING);
		return kept;
	}

	public static void clear(Path launcherDir) throws IOException {
		Files.deleteIfExists(launcherDir.resolve(FILE));
	}
}
