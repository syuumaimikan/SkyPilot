package dev.skypilot.launcher;

import dev.skypilot.mod.core.ConfigStore;
import dev.skypilot.mod.core.SkyPilotConfig;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The SkyPilot game folder: the mod's config (same file the mod reads), whether the mod is linked
 * (the mod's token file exists - its content is never read here), and logs / crash reports.
 */
public final class Instance {
	private Instance() {
	}

	public static Path configDir(Path instance) {
		return instance.resolve("config").resolve("skypilot");
	}

	public static SkyPilotConfig config(Path instance) {
		return new ConfigStore(configDir(instance)).load();
	}

	/** https everywhere; plain http only for a server on this computer (same rule as the mod). */
	public static boolean validServerUrl(String url) {
		try {
			URI u = new URI(url.strip());
			String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
			String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
			boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
			return (scheme.equals("https") || scheme.equals("http") && local) && u.getUserInfo() == null && u.getQuery() == null && u.getFragment() == null;
		} catch (Exception e) {
			return false;
		}
	}

	public static void setServer(Path instance, String url) throws IOException {
		if (!validServerUrl(url)) throw new IOException("the server URL must use https (http only for localhost)");
		ConfigStore store = new ConfigStore(configDir(instance));
		SkyPilotConfig c = store.load();
		c.apiBaseUrl = url.strip();
		c.sanitize();
		store.save(c);
	}

	public static boolean linked(Path instance) {
		try {
			Path token = configDir(instance).resolve("token.json");
			return Files.isRegularFile(token) && Files.size(token) > 2;
		} catch (IOException e) {
			return false;
		}
	}

	/** The last {@code lines} lines of logs/latest.log. */
	public static List<String> latestLog(Path instance, int lines) {
		Path f = instance.resolve("logs").resolve("latest.log");
		try {
			if (!Files.isRegularFile(f)) return List.of();
			List<String> all = Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8);
			return all.subList(Math.max(0, all.size() - lines), all.size());
		} catch (Exception e) {
			return List.of("(cannot read " + f + ": " + e.getMessage() + ")");
		}
	}

	/** Crash reports, newest first. */
	public static List<Path> crashReports(Path instance) {
		Path dir = instance.resolve("crash-reports");
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(dir)) return out;
		try (Stream<Path> s = Files.list(dir)) {
			s.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed()).forEach(out::add);
		} catch (IOException e) {
			return out;
		}
		return out;
	}
}
