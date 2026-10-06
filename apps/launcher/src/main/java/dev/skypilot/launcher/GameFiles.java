package dev.skypilot.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Downloads and checks everything needed to start Minecraft with Fabric directly: the version
 * file (from Mojang's version list), libraries, the client jar, assets and Mojang's own Java
 * runtime - each file against the SHA-1 published in Mojang's / Fabric's metadata, over https
 * from {@link Net#GAME_HOSTS} only. Files are shared with the official launcher's folder
 * ({@code versions}, {@code libraries}, {@code assets}); present files with the right size are
 * reused (a full SHA-1 check runs on "Repair").
 */
public final class GameFiles {
	static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	static final String RUNTIMES = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
	static final String ASSETS = "https://resources.download.minecraft.net/";
	private static final int THREADS = 8;

	public interface Progress {
		void stage(String en, String ja);

		void count(long done, long total);
	}

	/** One file to have on disk. {@code sha1} may be null only for Fabric libraries without one. */
	record Download(String url, Path target, String sha1, long size) {
	}

	/** Everything the start command needs. */
	public record Prepared(Path minecraftDir, Path javaExe, JsonObject vanilla, JsonObject fabric, List<Path> classpath, String assetIndex) {
	}

	private GameFiles() {
	}

	// ------------------------------------------------------------------ rules

	static String osName() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		return os.startsWith("windows") ? "windows" : os.startsWith("mac") ? "osx" : "linux";
	}

	static boolean is64() {
		return !"32".equals(System.getProperty("sun.arch.data.model"));
	}

	/** Mojang's rule lists: the last matching rule decides; no rules = allowed. */
	static boolean allowed(JsonArray rules, Map<String, Boolean> features) {
		if (rules == null || rules.isEmpty()) return true;
		boolean result = false;
		for (JsonElement e : rules) {
			JsonObject r = e.getAsJsonObject();
			boolean match = true;
			if (r.has("os")) {
				JsonObject os = r.getAsJsonObject("os");
				if (os.has("name") && !os.get("name").getAsString().equals(osName())) match = false;
				if (os.has("arch") && os.get("arch").getAsString().equals("x86") == is64()) match = false;
			}
			if (r.has("features")) {
				for (Map.Entry<String, JsonElement> f : r.getAsJsonObject("features").entrySet()) {
					if (f.getValue().getAsBoolean() != features.getOrDefault(f.getKey(), false)) match = false;
				}
			}
			if (match) result = "allow".equals(r.get("action").getAsString());
		}
		return result;
	}

	// ------------------------------------------------------------------ libraries

	/** "group:artifact[:version][:classifier]" → "group:artifact[:classifier]" (the version is what may differ). */
	static String libraryKey(String name) {
		String[] p = name.split(":");
		if (p.length < 2) return name;
		return p.length >= 4 ? p[0] + ":" + p[1] + ":" + p[3] : p[0] + ":" + p[1];
	}

	/** Maven path of "group:artifact:version[:classifier]". */
	static String mavenPath(String name) {
		String[] p = name.split(":");
		if (p.length < 3) throw new IllegalArgumentException("bad library name " + name);
		String file = p[1] + "-" + p[2] + (p.length >= 4 ? "-" + p[3] : "") + ".jar";
		return p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + file;
	}

	/**
	 * Libraries of the Fabric profile plus Minecraft's (for this OS), Fabric's winning when both
	 * list the same artifact (Fabric's loader needs its own ASM version).
	 */
	static List<Download> libraries(Path libDir, JsonObject vanilla, JsonObject fabric) {
		Map<String, Download> out = new LinkedHashMap<>();
		for (JsonElement e : fabric.getAsJsonArray("libraries")) {
			JsonObject l = e.getAsJsonObject();
			String name = l.get("name").getAsString();
			String base = l.has("url") ? l.get("url").getAsString() : "https://maven.fabricmc.net/";
			if (!base.endsWith("/")) base += "/";
			String path = mavenPath(name);
			String sha1 = l.has("sha1") ? l.get("sha1").getAsString() : null;
			long size = l.has("size") ? l.get("size").getAsLong() : 0;
			out.put(libraryKey(name), new Download(base + path, libDir.resolve(path), sha1, size));
		}
		for (JsonElement e : vanilla.getAsJsonArray("libraries")) {
			JsonObject l = e.getAsJsonObject();
			if (!allowed(l.has("rules") ? l.getAsJsonArray("rules") : null, Map.of())) continue;
			if (!l.has("downloads") || !l.getAsJsonObject("downloads").has("artifact")) continue;
			JsonObject a = l.getAsJsonObject("downloads").getAsJsonObject("artifact");
			out.putIfAbsent(libraryKey(l.get("name").getAsString()),
					new Download(a.get("url").getAsString(), libDir.resolve(a.get("path").getAsString()), a.get("sha1").getAsString(), a.get("size").getAsLong()));
		}
		return new ArrayList<>(out.values());
	}

	// ------------------------------------------------------------------ prepare

	private static JsonObject parse(byte[] data, String what) throws IOException {
		try {
			return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException(what + " is not valid JSON");
		}
	}

	private static JsonObject readJson(Path f) throws IOException {
		return parse(Files.readAllBytes(f), f.getFileName().toString());
	}

	/** A small metadata file: reuse it when its SHA-1 matches, else download and check it. */
	private static JsonObject metadata(String url, String sha1, Path target) throws IOException {
		if (Files.isRegularFile(target) && sha1.equalsIgnoreCase(Net.sha1(target))) return readJson(target);
		Net.downloadTo(url, target, sha1, 0);
		return readJson(target);
	}

	static String runtimePlatform() {
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		boolean arm = arch.contains("aarch64") || arch.contains("arm64");
		return switch (osName()) {
			case "windows" -> arm ? "windows-arm64" : is64() ? "windows-x64" : "windows-x86";
			case "osx" -> arm ? "mac-os-arm64" : "mac-os";
			default -> is64() ? "linux" : "linux-i386";
		};
	}

	public static Prepared prepare(Path mcDir, Path runtimeRoot, Pins pins, boolean repair, Progress progress, BooleanSupplier cancelled) throws IOException {
		// 1. version file of the pinned Minecraft version
		progress.stage("Checking Minecraft " + pins.minecraft() + "…", "Minecraft " + pins.minecraft() + " を確認中…");
		Path versionFile = mcDir.resolve("versions").resolve(pins.minecraft()).resolve(pins.minecraft() + ".json");
		JsonObject vanilla;
		try {
			JsonObject manifest = parse(Net.getGame(MANIFEST, 4 << 20), "version list");
			JsonObject entry = null;
			for (JsonElement e : manifest.getAsJsonArray("versions")) {
				if (e.getAsJsonObject().get("id").getAsString().equals(pins.minecraft())) entry = e.getAsJsonObject();
			}
			if (entry == null) throw new IOException("Minecraft " + pins.minecraft() + " is not in Mojang's version list");
			vanilla = metadata(entry.get("url").getAsString(), entry.get("sha1").getAsString(), versionFile);
		} catch (Net.RateLimited e) {
			throw e;
		} catch (IOException e) {
			if (!Files.isRegularFile(versionFile)) throw e;
			vanilla = readJson(versionFile); // offline: the file the official launcher (or we) checked before
		}
		// 2. Fabric's version file
		if (!FabricInstaller.installed(mcDir, pins)) FabricInstaller.install(mcDir, pins, url -> Net.download(url, 1 << 20));
		JsonObject fabric = readJson(FabricInstaller.versionFile(mcDir, pins));

		List<Download> files = new ArrayList<>();
		// 3. libraries and the client jar
		List<Download> libs = libraries(mcDir.resolve("libraries"), vanilla, fabric);
		files.addAll(libs);
		JsonObject client = vanilla.getAsJsonObject("downloads").getAsJsonObject("client");
		Path clientJar = versionFile.resolveSibling(pins.minecraft() + ".jar");
		files.add(new Download(client.get("url").getAsString(), clientJar, client.get("sha1").getAsString(), client.get("size").getAsLong()));
		// 4. assets
		JsonObject ai = vanilla.getAsJsonObject("assetIndex");
		String indexId = ai.get("id").getAsString();
		JsonObject index = metadata(ai.get("url").getAsString(), ai.get("sha1").getAsString(), mcDir.resolve("assets/indexes/" + indexId + ".json"));
		for (Map.Entry<String, JsonElement> e : index.getAsJsonObject("objects").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			String hash = o.get("hash").getAsString();
			if (!hash.matches("[0-9a-f]{40}")) continue;
			String rel = hash.substring(0, 2) + "/" + hash;
			files.add(new Download(ASSETS + rel, mcDir.resolve("assets/objects/" + rel), hash, o.get("size").getAsLong()));
		}
		// 5. Java runtime
		progress.stage("Checking the Java runtime…", "Java ランタイムを確認中…");
		String component = vanilla.has("javaVersion") ? vanilla.getAsJsonObject("javaVersion").get("component").getAsString() : "java-runtime-epsilon";
		if (!component.matches("[a-z0-9-]{1,40}")) throw new IOException("unexpected Java runtime name");
		Path runtimeDir = runtimeRoot.resolve(component);
		Path marker = runtimeDir.resolve(".skypilot-manifest");
		JsonObject runtimes = parse(Net.getGame(RUNTIMES, 4 << 20), "runtime list");
		JsonArray offers = runtimes.getAsJsonObject(runtimePlatform()).getAsJsonArray(component);
		if (offers == null || offers.isEmpty()) throw new IOException("Mojang has no " + component + " for " + runtimePlatform());
		JsonObject rm = offers.get(0).getAsJsonObject().getAsJsonObject("manifest");
		String rmSha = rm.get("sha1").getAsString();
		boolean runtimeOk = !repair && Files.isRegularFile(marker) && rmSha.equals(Files.readString(marker).strip());
		List<Download> runtimeFiles = new ArrayList<>();
		if (!runtimeOk) {
			JsonObject manifest = parse(Net.getGame(rm.get("url").getAsString(), 4 << 20), "runtime manifest");
			for (Map.Entry<String, JsonElement> e : manifest.getAsJsonObject("files").entrySet()) {
				JsonObject f = e.getValue().getAsJsonObject();
				Path target = runtimeDir.resolve(e.getKey()).normalize();
				if (!target.startsWith(runtimeDir)) throw new IOException("bad path in runtime manifest");
				if ("directory".equals(f.get("type").getAsString())) {
					Files.createDirectories(target);
				} else if ("file".equals(f.get("type").getAsString())) {
					JsonObject raw = f.getAsJsonObject("downloads").getAsJsonObject("raw");
					runtimeFiles.add(new Download(raw.get("url").getAsString(), target, raw.get("sha1").getAsString(), raw.get("size").getAsLong()));
				}
			}
			files.addAll(runtimeFiles);
		}
		// 6. download what is missing
		fetch(files, repair, progress, cancelled);
		if (!runtimeOk) {
			if (!osName().equals("windows")) {
				for (Download d : runtimeFiles) {
					if (d.target().getParent().getFileName().toString().equals("bin")) d.target().toFile().setExecutable(true);
				}
			}
			Files.writeString(marker, rmSha);
		}
		Path javaExe = osName().equals("windows") ? runtimeDir.resolve("bin/javaw.exe")
				: osName().equals("osx") ? runtimeDir.resolve("jre.bundle/Contents/Home/bin/java") : runtimeDir.resolve("bin/java");
		if (!Files.isRegularFile(javaExe)) throw new IOException("Java runtime is incomplete - use Repair");
		List<Path> cp = new ArrayList<>();
		for (Download d : libs) cp.add(d.target());
		cp.add(clientJar);
		return new Prepared(mcDir, javaExe, vanilla, fabric, cp, indexId);
	}

	/** Download (in parallel) every file that is missing or has the wrong size (or wrong SHA-1 when repairing). */
	static void fetch(List<Download> files, boolean repair, Progress progress, BooleanSupplier cancelled) throws IOException {
		progress.stage("Checking game files…", "ゲームファイルを確認中…");
		List<Download> todo = new ArrayList<>();
		long checked = 0;
		for (Download d : files) {
			if (cancelled.getAsBoolean()) throw new IOException("cancelled");
			if (!present(d, repair)) todo.add(d);
			if (++checked % 200 == 0) progress.count(checked, files.size());
		}
		if (todo.isEmpty()) return;
		progress.stage("Downloading " + todo.size() + " files…", todo.size() + " 個のファイルをダウンロード中…");
		AtomicLong done = new AtomicLong();
		ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
			Thread t = new Thread(r, "game-download");
			t.setDaemon(true);
			return t;
		});
		try {
			List<Future<?>> jobs = new ArrayList<>();
			for (Download d : todo) {
				jobs.add(pool.submit(() -> {
					if (cancelled.getAsBoolean()) throw new IOException("cancelled");
					IOException last = null;
					for (int attempt = 0; attempt < 3; attempt++) {
						try {
							Net.downloadTo(d.url(), d.target(), d.sha1(), d.size());
							last = null;
							break;
						} catch (Net.RateLimited e) {
							throw e; // never hammer a busy server
						} catch (IOException e) {
							last = e;
						}
					}
					if (last != null) throw last;
					progress.count(done.incrementAndGet(), todo.size());
					return null;
				}));
			}
			for (Future<?> f : jobs) {
				try {
					f.get();
				} catch (java.util.concurrent.ExecutionException e) {
					pool.shutdownNow();
					throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		} finally {
			pool.shutdownNow();
		}
	}

	private static boolean present(Download d, boolean repair) {
		try {
			if (!Files.isRegularFile(d.target())) return false;
			if (d.size() > 0 && Files.size(d.target()) != d.size()) return false;
			return !repair || d.sha1() == null || d.sha1().equalsIgnoreCase(Net.sha1(d.target()));
		} catch (IOException e) {
			return false;
		}
	}
}
