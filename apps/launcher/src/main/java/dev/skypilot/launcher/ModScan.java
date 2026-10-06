package dev.skypilot.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Reads everything the launcher shows about a mod file from its {@code fabric.mod.json}: name,
 * description, authors, links, icon, dependencies, the mod ids it provides (including mods nested
 * inside it, as Fabric API does), its mixin configs and code packages (to name the mod in a crash
 * report). Nothing in the jar is run.
 */
public final class ModScan {
	/**
	 * @param provides  mod id → version for the mod itself, its "provides" and its nested mods
	 * @param packages  Java packages of its entrypoints and mixins (for crash reports)
	 */
	public record Meta(Path file, boolean enabled, boolean fabric, String id, String name, String version, String description, List<String> authors, String license,
			String environment, Map<String, String> links, Map<String, List<String>> depends, Map<String, List<String>> recommends, Map<String, List<String>> breaks,
			Map<String, List<String>> conflicts, Map<String, String> provides, List<String> mixinConfigs, Set<String> packages, byte[] icon, long size) {
		public String baseName() {
			String n = file.getFileName().toString();
			return n.endsWith(".disabled") ? n.substring(0, n.length() - ".disabled".length()) : n;
		}
	}

	private static final long MAX_NESTED = 64L << 20;
	private static final long MAX_ICON = 512L << 10;

	private ModScan() {
	}

	/** Every .jar and .jar.disabled in the folder, sorted by name. */
	public static List<Meta> scan(Path modsDir) {
		List<Meta> out = new ArrayList<>();
		for (Mods.ModFile m : Mods.list(modsDir)) out.add(read(m.file(), m.enabled()));
		return out;
	}

	public static Meta read(Path file, boolean enabled) {
		long size = 0;
		try {
			size = Files.size(file);
		} catch (IOException e) {
			// listed a moment ago; treat as empty
		}
		try (ZipFile zip = new ZipFile(file.toFile())) {
			JsonObject json = json(zip, "fabric.mod.json");
			String fileName = file.getFileName().toString();
			if (json == null) {
				return new Meta(file, enabled, false, "", fileName, "?", "", List.of(), "", "*", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Set.of(), null, size);
			}
			String id = str(json, "id", "");
			String version = str(json, "version", "?");
			Map<String, String> provides = new LinkedHashMap<>();
			provides.put(id, version);
			for (String p : strings(json.get("provides"))) provides.putIfAbsent(p, version);
			nested(zip, json, provides, 0);
			List<String> mixins = mixinConfigs(json);
			Set<String> packages = new LinkedHashSet<>();
			entrypointPackages(json, packages);
			for (String cfg : mixins) {
				JsonObject m = json(zip, cfg);
				if (m != null && m.has("package") && m.get("package").isJsonPrimitive()) packages.add(m.get("package").getAsString());
			}
			return new Meta(file, enabled, true, id, str(json, "name", id.isEmpty() ? fileName : id), version, str(json, "description", ""), people(json.get("authors")),
					license(json.get("license")), str(json, "environment", "*"), links(json.get("contact")), deps(json.get("depends")), deps(json.get("recommends")),
					deps(json.get("breaks")), deps(json.get("conflicts")), provides, mixins, packages, icon(zip, json), size);
		} catch (IOException | RuntimeException e) {
			return new Meta(file, enabled, false, "", file.getFileName().toString(), "?", "", List.of(), "", "*", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(),
					Set.of(), null, size);
		}
	}

	private static JsonObject json(ZipFile zip, String entry) {
		try {
			ZipEntry e = zip.getEntry(entry);
			if (e == null || e.getSize() > 1_000_000) return null;
			try (InputStream in = zip.getInputStream(e)) {
				JsonElement el = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				return el.isJsonObject() ? el.getAsJsonObject() : null;
			}
		} catch (Exception ex) {
			return null;
		}
	}

	/** Mod ids of jars nested with "jars" (Fabric API ships every module like this). */
	private static void nested(ZipFile zip, JsonObject json, Map<String, String> provides, int depth) {
		if (depth > 1 || !json.has("jars") || !json.get("jars").isJsonArray()) return;
		for (JsonElement j : json.getAsJsonArray("jars")) {
			if (!j.isJsonObject() || !j.getAsJsonObject().has("file")) continue;
			ZipEntry e = zip.getEntry(j.getAsJsonObject().get("file").getAsString());
			if (e == null || e.getSize() > MAX_NESTED) continue;
			try (InputStream in = zip.getInputStream(e)) {
				byte[] data = in.readNBytes((int) MAX_NESTED);
				JsonObject inner = jsonInJar(data, "fabric.mod.json");
				if (inner == null) continue;
				String v = str(inner, "version", "?");
				provides.putIfAbsent(str(inner, "id", ""), v);
				for (String p : strings(inner.get("provides"))) provides.putIfAbsent(p, v);
			} catch (IOException ex) {
				// unreadable nested jar: Fabric will report it if it matters
			}
		}
		provides.remove("");
	}

	private static JsonObject jsonInJar(byte[] jar, String entry) {
		try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				if (e.getName().equals(entry)) {
					JsonElement el = JsonParser.parseString(new String(zin.readNBytes(1_000_000), StandardCharsets.UTF_8));
					return el.isJsonObject() ? el.getAsJsonObject() : null;
				}
			}
		} catch (Exception ex) {
			return null;
		}
		return null;
	}

	private static byte[] icon(ZipFile zip, JsonObject json) {
		String path = null;
		JsonElement icon = json.get("icon");
		if (icon != null && icon.isJsonPrimitive()) {
			path = icon.getAsString();
		} else if (icon != null && icon.isJsonObject()) {
			int best = -1;
			for (Map.Entry<String, JsonElement> e : icon.getAsJsonObject().entrySet()) {
				try {
					int size = Integer.parseInt(e.getKey());
					// the size closest to 64 px, preferring larger
					if (best < 0 || Math.abs(size - 64) < Math.abs(best - 64)) {
						best = size;
						path = e.getValue().getAsString();
					}
				} catch (RuntimeException ex) {
					// not a size key
				}
			}
		}
		if (path == null) return null;
		try {
			ZipEntry e = zip.getEntry(path.startsWith("/") ? path.substring(1) : path);
			if (e == null || e.getSize() > MAX_ICON) return null;
			try (InputStream in = zip.getInputStream(e)) {
				return in.readNBytes((int) MAX_ICON);
			}
		} catch (IOException ex) {
			return null;
		}
	}

	private static List<String> mixinConfigs(JsonObject json) {
		List<String> out = new ArrayList<>();
		JsonElement m = json.get("mixins");
		if (m == null || !m.isJsonArray()) return out;
		for (JsonElement e : m.getAsJsonArray()) {
			if (e.isJsonPrimitive()) out.add(e.getAsString());
			else if (e.isJsonObject() && e.getAsJsonObject().has("config")) out.add(e.getAsJsonObject().get("config").getAsString());
		}
		return out;
	}

	private static void entrypointPackages(JsonObject json, Set<String> out) {
		JsonElement ep = json.get("entrypoints");
		if (ep == null || !ep.isJsonObject()) return;
		for (Map.Entry<String, JsonElement> e : ep.getAsJsonObject().entrySet()) {
			if (!e.getValue().isJsonArray()) continue;
			for (JsonElement v : e.getValue().getAsJsonArray()) {
				String cls = v.isJsonPrimitive() ? v.getAsString() : v.isJsonObject() && v.getAsJsonObject().has("value") ? v.getAsJsonObject().get("value").getAsString() : null;
				if (cls == null) continue;
				cls = cls.replaceAll("::.*$", "");
				int dot = cls.lastIndexOf('.');
				if (dot > 0) out.add(cls.substring(0, dot));
			}
		}
	}

	private static Map<String, List<String>> deps(JsonElement e) {
		Map<String, List<String>> out = new LinkedHashMap<>();
		if (e == null || !e.isJsonObject()) return out;
		for (Map.Entry<String, JsonElement> d : e.getAsJsonObject().entrySet()) out.put(d.getKey(), strings(d.getValue()));
		return out;
	}

	private static List<String> strings(JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e == null) return out;
		if (e.isJsonPrimitive()) out.add(e.getAsString());
		else if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
		return out;
	}

	private static List<String> people(JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e == null || !e.isJsonArray()) return out;
		for (JsonElement p : (JsonArray) e) {
			if (p.isJsonPrimitive()) out.add(p.getAsString());
			else if (p.isJsonObject() && p.getAsJsonObject().has("name")) out.add(p.getAsJsonObject().get("name").getAsString());
		}
		return out;
	}

	private static String license(JsonElement e) {
		List<String> l = strings(e);
		return String.join(", ", l);
	}

	/** Only http(s) links are kept (they are opened in the browser). */
	private static Map<String, String> links(JsonElement e) {
		Map<String, String> out = new LinkedHashMap<>();
		if (e == null || !e.isJsonObject()) return out;
		for (String key : new String[] {"homepage", "sources", "issues"}) {
			JsonElement v = e.getAsJsonObject().get(key);
			if (v != null && v.isJsonPrimitive() && v.getAsString().matches("https?://[^\\s\"<>]+")) out.put(key, v.getAsString());
		}
		return out;
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}
}
