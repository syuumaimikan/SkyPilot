package dev.skypilot.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The "SkyPilot" profile in the official launcher's profile file(s) - {@code launcher_profiles.json}
 * and, for the Microsoft Store launcher, {@code launcher_profiles_microsoft_store.json} (the files
 * Fabric's installer writes). Only the SkyPilot entry is added or changed; everything else in the
 * file is kept, and the original is backed up once before the first change.
 */
public final class LauncherProfiles {
	public static final String KEY = "skypilot";
	public static final String NAME = "SkyPilot (SkyBlock)";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private LauncherProfiles() {
	}

	/** Profile files that exist (the official launcher creates them on its first start). */
	public static List<Path> files(Path minecraftDir) {
		List<Path> out = new ArrayList<>();
		for (String n : new String[] {"launcher_profiles.json", "launcher_profiles_microsoft_store.json"}) {
			Path p = minecraftDir.resolve(n);
			if (Files.isRegularFile(p)) out.add(p);
		}
		return out;
	}

	public static String javaArgs(int memoryMb) {
		return "-Xmx" + memoryMb + "M -Xms" + Math.min(memoryMb, 1024) + "M";
	}

	/** Add or update the SkyPilot profile. */
	public static void upsert(Path file, String versionId, Path gameDir, int memoryMb, String iconDataUri) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		Path backup = file.resolveSibling(file.getFileName() + ".skypilot-backup");
		if (!Files.exists(backup)) Files.copy(file, backup);
		JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject() ? root.getAsJsonObject("profiles") : new JsonObject();
		root.add("profiles", profiles);
		JsonObject p = profiles.has(KEY) && profiles.get(KEY).isJsonObject() ? profiles.getAsJsonObject(KEY) : new JsonObject();
		String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
		if (!p.has("created")) p.addProperty("created", now);
		if (!p.has("lastUsed")) p.addProperty("lastUsed", now);
		p.addProperty("name", NAME);
		p.addProperty("type", "custom");
		p.addProperty("lastVersionId", versionId);
		p.addProperty("gameDir", gameDir.toAbsolutePath().toString());
		p.addProperty("javaArgs", javaArgs(memoryMb));
		if (iconDataUri != null) p.addProperty("icon", iconDataUri);
		profiles.add(KEY, p);
		Path tmp = file.resolveSibling(file.getFileName() + ".skypilot-tmp");
		Files.writeString(tmp, GSON.toJson(root));
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
	}

	/** The SkyPilot profile in that file, or null. */
	public static JsonObject read(Path file) {
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (!root.has("profiles") || !root.getAsJsonObject("profiles").has(KEY)) return null;
			return root.getAsJsonObject("profiles").getAsJsonObject(KEY);
		} catch (Exception e) {
			return null;
		}
	}

	/** The profile exists and points at this Fabric version and game folder. */
	public static boolean upToDate(Path file, String versionId, Path gameDir) {
		JsonObject p = read(file);
		return p != null && versionId.equals(p.has("lastVersionId") ? p.get("lastVersionId").getAsString() : null)
				&& p.has("gameDir") && Path.of(p.get("gameDir").getAsString()).toAbsolutePath().normalize().equals(gameDir.toAbsolutePath().normalize());
	}
}
