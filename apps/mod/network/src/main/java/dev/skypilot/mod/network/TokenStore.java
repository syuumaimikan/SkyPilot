package dev.skypilot.mod.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Stores the SkyPilot device token (issued by the device-authorization flow and revocable on the
 * web). This is a SkyPilot-only credential - Microsoft/Minecraft tokens are never read or stored.
 */
public final class TokenStore {
	private final Path file;
	private volatile String token;

	public TokenStore(Path dir) {
		this.file = dir.resolve("token.json");
		this.token = read();
	}

	public String get() {
		return token;
	}

	public boolean linked() {
		return token != null && !token.isEmpty();
	}

	public void set(String value) throws IOException {
		Files.createDirectories(file.getParent());
		JsonObject o = new JsonObject();
		o.addProperty("accessToken", value);
		Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
		try {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException | IOException ignored) {
			// non-POSIX file system (Windows): the file lives in the user's profile directory
		}
		token = value;
	}

	public void clear() throws IOException {
		token = null;
		Files.deleteIfExists(file);
	}

	private String read() {
		try {
			if (!Files.exists(file)) return null;
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			String t = o.has("accessToken") ? o.get("accessToken").getAsString() : null;
			return t != null && t.startsWith("spm_") ? t : null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
