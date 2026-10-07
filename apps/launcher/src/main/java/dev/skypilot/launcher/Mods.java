package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The SkyPilot game folder's mods: installing the bundled SkyPilot mod and the pinned Fabric API
 * (hash-checked), listing mod files, switching them on / off and adding a local mod file the
 * player picked. Mods are never deleted - replaced files are switched off instead.
 */
public final class Mods {
	public record ModFile(Path file, String id, String name, String version, boolean enabled) {
	}

	private Mods() {
	}

	public static List<ModFile> list(Path modsDir) {
		List<ModFile> out = new ArrayList<>();
		if (!Files.isDirectory(modsDir)) return out;
		try (Stream<Path> files = Files.list(modsDir)) {
			for (Path p : files.sorted().toList()) {
				String n = p.getFileName().toString();
				boolean enabled = n.endsWith(".jar");
				if (!enabled && !n.endsWith(".jar.disabled")) continue;
				JsonObject json = modJson(p);
				out.add(new ModFile(p, str(json, "id", ""), str(json, "name", n), str(json, "version", "?"), enabled));
			}
		} catch (IOException e) {
			return out;
		}
		return out;
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	/** fabric.mod.json of a mod file, or null when it is not a Fabric mod. */
	public static JsonObject modJson(Path jar) {
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

	/** Switch a mod file on (.jar) or off (.jar.disabled). */
	public static Path toggle(ModFile m) throws IOException {
		String n = m.file().getFileName().toString();
		Path to = m.enabled() ? m.file().resolveSibling(n + ".disabled") : m.file().resolveSibling(n.substring(0, n.length() - ".disabled".length()));
		if (Files.exists(to)) throw new IOException(to.getFileName() + " already exists");
		Files.move(m.file(), to);
		return to;
	}

	/** Copy a mod file the player picked into the mods folder (Fabric mods only). */
	public static Path add(Path modsDir, Path source) throws IOException {
		if (!source.getFileName().toString().endsWith(".jar")) throw new IOException("not a .jar file");
		if (modJson(source) == null) throw new IOException("not a Fabric mod (no fabric.mod.json)");
		Files.createDirectories(modsDir);
		Path target = modsDir.resolve(source.getFileName().toString());
		if (Files.exists(target)) throw new IOException(target.getFileName() + " is already in the mods folder");
		return Files.copy(source, target);
	}

	/** Switch off every other enabled file of the same mod id (never deletes). */
	private static void switchOffOthers(Path modsDir, String id, Path keep) throws IOException {
		for (ModFile m : list(modsDir)) {
			if (m.enabled() && m.id().equals(id) && !m.file().equals(keep)) toggle(m);
		}
	}

	/** The bundled SkyPilot mod is installed (byte-identical). */
	public static boolean skyPilotInstalled(Path modsDir, Pins pins) {
		Path f = modsDir.resolve(pins.modFile());
		try {
			return Files.isRegularFile(f) && Net.sha256(Files.readAllBytes(f)).equals(pins.modSha256());
		} catch (IOException e) {
			return false;
		}
	}

	public static void installSkyPilot(Path modsDir, Pins pins, byte[] bundled) throws IOException {
		if (!Net.sha256(bundled).equals(pins.modSha256())) throw new IOException("the bundled SkyPilot mod does not match its checksum");
		Files.createDirectories(modsDir);
		Path target = modsDir.resolve(pins.modFile());
		Path tmp = modsDir.resolve(pins.modFile() + ".part");
		Files.write(tmp, bundled);
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		switchOffOthers(modsDir, "skypilot", target);
	}

	/** The pinned API is enabled, either nested in the trusted SkyPilot jar or as a separate file. */
	public static boolean fabricApiInstalled(Path modsDir, Pins pins) {
		if (bundledFabricApiInstalled(modsDir, pins)) return true;
		for (ModFile m : list(modsDir)) {
			try {
				if (m.enabled() && m.id().equals("fabric-api") && Files.size(m.file()) == pins.fabricApiSize() && Net.sha512(m.file()).equals(pins.fabricApiSha512())) return true;
			} catch (IOException e) {
				// unreadable file: not the pinned one
			}
		}
		return false;
	}

	private static boolean bundledFabricApiInstalled(Path modsDir, Pins pins) {
		if (!skyPilotInstalled(modsDir, pins) || pins.fabricApiSize() <= 0 || pins.fabricApiSize() > 8_000_000) return false;
		Path mod = modsDir.resolve(pins.modFile());
		JsonObject metadata = modJson(mod);
		if (metadata == null || !metadata.has("jars") || !metadata.get("jars").isJsonArray()) return false;
		try (ZipFile zip = new ZipFile(mod.toFile())) {
			for (var nested : metadata.getAsJsonArray("jars")) {
				if (!nested.isJsonObject()) continue;
				String file = str(nested.getAsJsonObject(), "file", "");
				ZipEntry entry = zip.getEntry(file);
				if (entry == null || entry.getSize() != pins.fabricApiSize()) continue;
				try (InputStream in = zip.getInputStream(entry)) {
					byte[] bytes = in.readNBytes((int) pins.fabricApiSize() + 1);
					if (bytes.length == pins.fabricApiSize() && Net.sha512(bytes).equals(pins.fabricApiSha512())) return true;
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// A broken or mismatched bundle still needs the separately verified API.
		}
		return false;
	}

	public static void installFabricApi(Path modsDir, Pins pins, FabricInstaller.Fetch fetch) throws IOException {
		if (fabricApiInstalled(modsDir, pins)) return;
		byte[] data = fetch.get(pins.fabricApiUrl());
		if (data.length != pins.fabricApiSize() || !Net.sha512(data).equals(pins.fabricApiSha512())) {
			throw new IOException("the downloaded Fabric API does not match the pinned SHA-512 - not installed");
		}
		Files.createDirectories(modsDir);
		Path target = modsDir.resolve("fabric-api-" + pins.fabricApi() + ".jar");
		Path tmp = modsDir.resolve(target.getFileName() + ".part");
		Files.write(tmp, data);
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		switchOffOthers(modsDir, "fabric-api", target);
	}
}
