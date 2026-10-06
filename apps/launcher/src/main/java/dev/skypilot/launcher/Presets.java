package dev.skypilot.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Mod presets: named sets of which mod files are switched on. Applying one only renames files
 * (.jar ↔ .jar.disabled); files the preset doesn't know are left as they are.
 */
public final class Presets {
	public record Preset(List<String> on, List<String> off) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String BEFORE_SAFE_MODE = "before-safe-mode";

	private Presets() {
	}

	private static Path file(Path launcherDir) {
		return launcherDir.resolve("presets.json");
	}

	/** Presets of one game folder, by name. */
	public static Map<String, Preset> load(Path launcherDir, Path instance) {
		Map<String, Map<String, Preset>> all = all(launcherDir);
		return new LinkedHashMap<>(all.getOrDefault(key(instance), Map.of()));
	}

	private static Map<String, Map<String, Preset>> all(Path launcherDir) {
		try {
			Path f = file(launcherDir);
			if (Files.isRegularFile(f)) {
				Map<String, Map<String, Preset>> m = GSON.fromJson(Files.readString(f), new TypeToken<Map<String, Map<String, Preset>>>() {
				}.getType());
				if (m != null) return m;
			}
		} catch (Exception e) {
			// unreadable: start empty
		}
		return new LinkedHashMap<>();
	}

	private static String key(Path instance) {
		return instance.toAbsolutePath().normalize().toString();
	}

	public static void save(Path launcherDir, Path instance, String name, Preset preset) throws IOException {
		if (name == null || name.isBlank() || name.length() > 40) throw new IOException("preset names are 1-40 characters");
		Map<String, Map<String, Preset>> all = all(launcherDir);
		all.computeIfAbsent(key(instance), k -> new LinkedHashMap<>()).put(name.strip(), preset);
		write(launcherDir, all);
	}

	public static void delete(Path launcherDir, Path instance, String name) throws IOException {
		Map<String, Map<String, Preset>> all = all(launcherDir);
		Map<String, Preset> mine = all.get(key(instance));
		if (mine != null && mine.remove(name) != null) write(launcherDir, all);
	}

	private static void write(Path launcherDir, Map<String, Map<String, Preset>> all) throws IOException {
		Files.createDirectories(launcherDir);
		Path tmp = file(launcherDir).resolveSibling("presets.json.tmp");
		Files.writeString(tmp, GSON.toJson(all));
		Files.move(tmp, file(launcherDir), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** The current on/off state of the folder's mods. */
	public static Preset capture(List<Mods.ModFile> mods) {
		TreeSet<String> on = new TreeSet<>();
		TreeSet<String> off = new TreeSet<>();
		for (Mods.ModFile m : mods) (m.enabled() ? on : off).add(base(m));
		return new Preset(new ArrayList<>(on), new ArrayList<>(off));
	}

	static String base(Mods.ModFile m) {
		String n = m.file().getFileName().toString();
		return n.endsWith(".disabled") ? n.substring(0, n.length() - ".disabled".length()) : n;
	}

	/** Switch files on/off to match; returns how many changed. */
	public static int apply(Path modsDir, Preset p) throws IOException {
		int n = 0;
		for (Mods.ModFile m : Mods.list(modsDir)) {
			String b = base(m);
			boolean wantOn = p.on().contains(b);
			boolean wantOff = p.off().contains(b);
			if (m.enabled() && wantOff || !m.enabled() && wantOn) {
				Mods.toggle(m);
				n++;
			}
		}
		return n;
	}

	/**
	 * Troubleshooting: only SkyPilot and Fabric API stay on. The previous state is saved as the
	 * preset {@value #BEFORE_SAFE_MODE} first, so it can be switched back.
	 */
	public static int safeMode(Path launcherDir, Path instance, Path modsDir) throws IOException {
		save(launcherDir, instance, BEFORE_SAFE_MODE, capture(Mods.list(modsDir)));
		int n = 0;
		for (Mods.ModFile m : Mods.list(modsDir)) {
			boolean keep = m.id().equals("skypilot") || m.id().equals("fabric-api");
			if (m.enabled() && !keep) {
				Mods.toggle(m);
				n++;
			}
		}
		return n;
	}
}
