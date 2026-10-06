package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs the Fabric Loader version file into the official launcher's {@code versions} folder,
 * the same way Fabric's own installer does: {@code versions/<id>/<id>.json} from Fabric's
 * metadata server. The official launcher then downloads (and checks) the libraries it lists and
 * the Minecraft version it inherits from.
 */
public final class FabricInstaller {
	@FunctionalInterface
	public interface Fetch {
		byte[] get(String url) throws IOException;
	}

	private FabricInstaller() {
	}

	public static Path versionFile(Path minecraftDir, Pins pins) {
		return minecraftDir.resolve("versions").resolve(pins.versionId()).resolve(pins.versionId() + ".json");
	}

	public static boolean installed(Path minecraftDir, Pins pins) {
		Path f = versionFile(minecraftDir, pins);
		if (!Files.isRegularFile(f)) return false;
		try {
			JsonObject json = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			return pins.versionId().equals(json.get("id").getAsString());
		} catch (Exception e) {
			return false;
		}
	}

	/** Fetch, check and write the version file. */
	public static void install(Path minecraftDir, Pins pins, Fetch fetch) throws IOException {
		byte[] data = fetch.get(pins.fabricMetaUrl());
		JsonObject json;
		try {
			json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Fabric metadata is not JSON");
		}
		String id = json.has("id") ? json.get("id").getAsString() : "";
		String inherits = json.has("inheritsFrom") ? json.get("inheritsFrom").getAsString() : "";
		if (!id.equals(pins.versionId()) || !inherits.equals(pins.minecraft()) || !json.has("libraries") || !json.has("mainClass")) {
			throw new IOException("unexpected Fabric metadata (id " + id + ", inheritsFrom " + inherits + ")");
		}
		Path file = versionFile(minecraftDir, pins);
		Files.createDirectories(file.getParent());
		Files.deleteIfExists(file.resolveSibling(pins.versionId() + ".jar"));
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, json.toString());
		Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}
}
