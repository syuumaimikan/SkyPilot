package dev.skypilot.mod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and atomically saves {@link SkyPilotConfig}. A broken file falls back to defaults. */
public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final Path file;

	public ConfigStore(Path dir) {
		this.file = dir.resolve("config.json");
	}

	public Path file() {
		return file;
	}

	public SkyPilotConfig load() {
		if (!Files.exists(file)) return new SkyPilotConfig().sanitize();
		try {
			SkyPilotConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SkyPilotConfig.class);
			return (c == null ? new SkyPilotConfig() : c).sanitize();
		} catch (IOException | JsonParseException e) {
			return new SkyPilotConfig().sanitize();
		}
	}

	public void save(SkyPilotConfig config) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(config.sanitize()), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
