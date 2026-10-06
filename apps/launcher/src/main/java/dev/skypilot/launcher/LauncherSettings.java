package dev.skypilot.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The launcher's own settings ({@code <launcher dir>/launcher.json}). */
public final class LauncherSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Null = the official launcher's default .minecraft. */
	public String minecraftDir;
	/** Null = .minecraft/skypilot. */
	public String instanceDir;
	public int memoryMb = 4096;
	/** "auto", "en" or "ja". */
	public String language = "auto";

	public Path minecraft() {
		return minecraftDir == null || minecraftDir.isBlank() ? Env.minecraftDir() : Path.of(minecraftDir);
	}

	public Path instance() {
		return instanceDir == null || instanceDir.isBlank() ? Env.defaultInstanceDir(minecraft()) : Path.of(instanceDir);
	}

	public Path mods() {
		return instance().resolve("mods");
	}

	public LauncherSettings sanitize() {
		memoryMb = Math.max(2048, Math.min(32768, memoryMb));
		if (language == null || !(language.equals("en") || language.equals("ja") || language.equals("auto"))) language = "auto";
		return this;
	}

	public static LauncherSettings load(Path dir) {
		Path f = dir.resolve("launcher.json");
		try {
			if (Files.isRegularFile(f)) {
				LauncherSettings s = GSON.fromJson(Files.readString(f), LauncherSettings.class);
				if (s != null) return s.sanitize();
			}
		} catch (Exception e) {
			// unreadable: start from the defaults
		}
		return new LauncherSettings();
	}

	public void save(Path dir) throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("launcher.json"), GSON.toJson(sanitize()));
	}
}
