package dev.skypilot.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads, only over https and only from the two hosts the launcher needs: Fabric's metadata
 * server (the loader's version file) and Modrinth's CDN (the pinned Fabric API file). Every
 * download has a size limit, and files are checked against the pinned hash before use.
 */
public final class Net {
	/** Files the setup installs (checked against pinned hashes). */
	public static final Set<String> HOSTS = Set.of("meta.fabricmc.net", "cdn.modrinth.com");
	/** Information only (JSON / skin images); nothing from here is run or installed. */
	public static final Set<String> INFO_HOSTS = Set.of("api.modrinth.com", "api.mojang.com", "sessionserver.mojang.com", "textures.minecraft.net");
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

	private Net() {
	}

	/** Fetch from an allowed host; at most {@code maxBytes}. */
	public static byte[] download(String url, long maxBytes) throws IOException {
		URI u = URI.create(url);
		if (!"https".equals(u.getScheme()) || u.getHost() == null || !HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT))) {
			throw new IOException("download from " + u.getHost() + " is not allowed");
		}
		HttpRequest req = HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(60)).header("User-Agent", "SkyPilot-Launcher (sets up SkyPilot for the official Minecraft Launcher)").GET().build();
		try {
			HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (resp.statusCode() != 200) throw new IOException(u.getHost() + " answered HTTP " + resp.statusCode());
			try (InputStream in = resp.body()) {
				byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
				if (data.length > maxBytes) throw new IOException("download is larger than expected");
				return data;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	/** The service asked us to slow down (HTTP 429): the request is not repeated. */
	public static final class RateLimited extends IOException {
		public RateLimited(String host) {
			super(host + " is busy (rate limited) - try again later");
		}
	}

	private static URI info(String url) throws IOException {
		URI u = URI.create(url);
		if (!"https".equals(u.getScheme()) || u.getHost() == null || !INFO_HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT))) {
			throw new IOException("request to " + u.getHost() + " is not allowed");
		}
		return u;
	}

	/** GET from an information host (Modrinth API, Mojang profile/skin); at most {@code maxBytes}; 404 = null. */
	public static byte[] getInfo(String url, long maxBytes) throws IOException {
		return send(HttpRequest.newBuilder(info(url)).GET(), maxBytes);
	}

	/** POST JSON to an information host. */
	public static byte[] postInfo(String url, String json, long maxBytes) throws IOException {
		return send(HttpRequest.newBuilder(info(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)), maxBytes);
	}

	private static byte[] send(HttpRequest.Builder b, long maxBytes) throws IOException {
		HttpRequest req = b.timeout(Duration.ofSeconds(20)).header("User-Agent", "SkyPilot-Launcher/" + version() + " (SkyBlock companion; information only)").build();
		try {
			HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = resp.body()) {
				if (resp.statusCode() == 429) throw new RateLimited(req.uri().getHost());
				if (resp.statusCode() == 404 || resp.statusCode() == 204) return null;
				if (resp.statusCode() != 200) throw new IOException(req.uri().getHost() + " answered HTTP " + resp.statusCode());
				byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
				if (data.length > maxBytes) throw new IOException("answer is larger than expected");
				return data;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	static String version() {
		return versionName();
	}

	/** The launcher's version (from the jar manifest; "dev" when run from the source tree). */
	public static String versionName() {
		String v = Net.class.getPackage().getImplementationVersion();
		return v == null ? "dev" : v;
	}

	/** True when the SkyPilot server answers its health check. */
	public static boolean healthy(String baseUrl) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/api/health")).timeout(Duration.ofSeconds(4)).version(HttpClient.Version.HTTP_1_1).GET().build();
			return CLIENT.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	public static String sha256(byte[] data) {
		return hex("SHA-256", data);
	}

	public static String sha1(Path file) throws IOException {
		return hex("SHA-1", Files.readAllBytes(file));
	}

	public static String sha512(byte[] data) {
		return hex("SHA-512", data);
	}

	public static String sha512(Path file) throws IOException {
		return sha512(Files.readAllBytes(file));
	}

	private static String hex(String algorithm, byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
