package dev.skypilot.mod.platform;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The player's installed mods: what Fabric Loader loaded, plus mod files switched off as
 * {@code *.jar.disabled}. Switching a mod renames its file in the mods folder (it takes effect
 * after a restart); config screens are the mods' own, opened through Mod Menu's public API when
 * a mod provides one. Nothing is downloaded, installed or executed by SkyPilot.
 */
final class ModManager {
	enum Kind {
		/** A jar in the mods folder (can be switched off). */
		MOD,
		/** Bundled inside another mod (jar-in-jar). */
		LIBRARY,
		/** Minecraft, Java, Fabric Loader. */
		BUILTIN,
		/** A *.jar.disabled file in the mods folder. */
		DISABLED,
		/** A *.jar in the mods folder that is not loaded (just switched on, or it failed to load). */
		NOT_LOADED,
		/** Loaded from the development classpath. */
		DEV
	}

	record Entry(String id, String name, String version, String description, List<String> authors, List<String> license, String environment,
			Map<String, String> contact, Kind kind, Path file, String parent, List<String> depends, List<String> dependents, String iconPath) {
		boolean toggleable() {
			return (kind == Kind.MOD || kind == Kind.DISABLED || kind == Kind.NOT_LOADED) && file != null;
		}
	}

	/** Files renamed this session (path after the rename) - the change applies after a restart. */
	static final Set<Path> PENDING = new HashSet<>();

	private ModManager() {
	}

	static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	static String gameVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	static List<Entry> scan() {
		FabricLoader loader = FabricLoader.getInstance();
		Collection<ModContainer> mods = loader.getAllMods();
		Map<String, List<String>> dependents = new TreeMap<>();
		for (ModContainer m : mods) {
			for (ModDependency d : m.getMetadata().getDependencies()) {
				if (d.getKind() == ModDependency.Kind.DEPENDS) dependents.computeIfAbsent(d.getModId(), k -> new ArrayList<>()).add(m.getMetadata().getId());
			}
		}
		Path modsDir = modsDir().toAbsolutePath().normalize();
		List<Entry> out = new ArrayList<>();
		for (ModContainer m : mods) {
			ModMetadata md = m.getMetadata();
			Kind kind;
			Path file = null;
			String parent = null;
			ModOrigin origin = m.getOrigin();
			if (md.getType().equals("builtin")) {
				kind = Kind.BUILTIN;
			} else if (origin.getKind() == ModOrigin.Kind.NESTED || m.getContainingMod().isPresent()) {
				kind = Kind.LIBRARY;
				parent = m.getContainingMod().map(c -> c.getMetadata().getId()).orElse(null);
			} else if (origin.getKind() == ModOrigin.Kind.PATH && origin.getPaths().size() == 1 && origin.getPaths().get(0).toString().endsWith(".jar")
					&& modsDir.equals(origin.getPaths().get(0).toAbsolutePath().normalize().getParent())) {
				kind = Kind.MOD;
				file = origin.getPaths().get(0);
			} else {
				kind = Kind.DEV;
			}
			List<String> deps = new ArrayList<>();
			for (ModDependency d : md.getDependencies()) if (d.getKind() == ModDependency.Kind.DEPENDS) deps.add(d.getModId());
			List<String> needs = new ArrayList<>(dependents.getOrDefault(md.getId(), List.of()));
			for (String p : md.getProvides()) needs.addAll(dependents.getOrDefault(p, List.of()));
			out.add(new Entry(md.getId(), md.getName(), md.getVersion().getFriendlyString(), md.getDescription(), names(md.getAuthors()), new ArrayList<>(md.getLicense()),
					md.getEnvironment().name(), new LinkedHashMap<>(md.getContact().asMap()), kind, file, parent, deps, needs.stream().distinct().toList(),
					md.getIconPath(32).orElse(null)));
		}
		java.util.Set<Path> loaded = new HashSet<>();
		for (Entry e : out) if (e.file() != null) loaded.add(e.file().toAbsolutePath().normalize());
		out.addAll(unloaded(loaded));
		out.sort((a, b) -> {
			int k = Integer.compare(order(a.kind()), order(b.kind()));
			return k != 0 ? k : a.name().compareToIgnoreCase(b.name());
		});
		return out;
	}

	private static int order(Kind k) {
		return switch (k) {
			case MOD, DEV -> 0;
			case DISABLED, NOT_LOADED -> 1;
			case LIBRARY -> 2;
			case BUILTIN -> 3;
		};
	}

	private static List<String> names(Collection<Person> people) {
		List<String> out = new ArrayList<>();
		for (Person p : people) out.add(p.getName());
		return out;
	}

	/** *.jar.disabled files and jars that are not loaded, described by their fabric.mod.json. */
	static List<Entry> unloaded(java.util.Set<Path> loaded) {
		List<Entry> out = new ArrayList<>();
		Path dir = modsDir();
		if (!Files.isDirectory(dir)) return out;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : files.filter(f -> {
				String n = f.getFileName().toString();
				return n.endsWith(".jar.disabled") || n.endsWith(".jar") && !loaded.contains(f.toAbsolutePath().normalize());
			}).toList()) {
				Kind kind = p.getFileName().toString().endsWith(".disabled") ? Kind.DISABLED : Kind.NOT_LOADED;
				JsonObject json = readModJson(p);
				String file = p.getFileName().toString();
				String id = json != null && json.has("id") ? json.get("id").getAsString() : file;
				String name = json != null && json.has("name") ? json.get("name").getAsString() : file.replace(".jar.disabled", "");
				String version = json != null && json.has("version") ? json.get("version").getAsString() : "?";
				String description = json != null && json.has("description") ? json.get("description").getAsString() : "";
				out.add(new Entry(id, name, version, description, List.of(), List.of(), "?", Map.of(), kind, p, null, List.of(), List.of(), null));
			}
		} catch (IOException e) {
			SkyPilot.LOG.warn("Could not list the mods folder: {}", e.getMessage());
		}
		return out;
	}

	private static JsonObject readModJson(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry e = zip.getEntry("fabric.mod.json");
			if (e == null || e.getSize() > 1_000_000) return null;
			try (InputStream in = zip.getInputStream(e)) {
				return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Switch a mod off (rename to *.jar.disabled) or back on. Returns null on success, else why
	 * not. Takes effect after the game is restarted.
	 */
	static String toggle(Entry e) {
		if (!e.toggleable()) return "not a mod file in the mods folder";
		Path from = e.file();
		String name = from.getFileName().toString();
		Path to = e.kind() == Kind.DISABLED ? from.resolveSibling(name.substring(0, name.length() - ".disabled".length())) : from.resolveSibling(name + ".disabled");
		if (Files.exists(to)) return to.getFileName() + " already exists";
		try {
			Files.move(from, to);
		} catch (IOException ex) {
			return ex.getClass().getSimpleName() + ": " + ex.getMessage();
		}
		if (!PENDING.remove(from)) PENDING.add(to);
		return null;
	}

	/** SHA-1 of a mod file (for the update check). */
	static String sha1(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
			return HexFormat.of().formatHex(md.digest());
		} catch (Exception ex) {
			return null;
		}
	}

	// ---------------------------------------------------------------- config screens (Mod Menu API)

	/** Mod id → Mod Menu config screen factory, from the "modmenu" entrypoints (by reflection). */
	private static Map<String, Object> factories;
	private static Method create;

	static synchronized Map<String, Object> configFactories() {
		if (factories != null) return factories;
		Map<String, Object> out = new LinkedHashMap<>();
		try {
			for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
				try {
					Object api = c.getEntrypoint();
					Class<?> apiType = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi", false, api.getClass().getClassLoader());
					Class<?> factoryType = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory", false, api.getClass().getClassLoader());
					create = factoryType.getMethod("create", Screen.class);
					Object own = apiType.getMethod("getModConfigScreenFactory").invoke(api);
					if (own != null) out.put(c.getProvider().getMetadata().getId(), own);
					Object provided = apiType.getMethod("getProvidedConfigScreenFactories").invoke(api);
					if (provided instanceof Map<?, ?> map) {
						for (var en : map.entrySet()) if (en.getKey() instanceof String k && en.getValue() != null) out.putIfAbsent(k, en.getValue());
					}
				} catch (Throwable t) {
					// Mod Menu not installed, or this mod's integration failed to load: no config button
				}
			}
		} catch (Throwable t) {
			SkyPilot.LOG.debug("No Mod Menu entrypoints: {}", t.toString());
		}
		factories = out;
		return out;
	}

	/** The mod's own config screen, or null when it has none. */
	static Screen configScreen(String modId, Screen parent) {
		Object f = configFactories().get(modId);
		if (f == null || create == null) return null;
		try {
			return (Screen) create.invoke(f, parent);
		} catch (Throwable t) {
			SkyPilot.LOG.warn("Config screen of {} failed: {}", modId, t.toString());
			return null;
		}
	}

	static boolean hasConfig(String modId) {
		return configFactories().containsKey(modId.toLowerCase(Locale.ROOT)) || configFactories().containsKey(modId);
	}
}
