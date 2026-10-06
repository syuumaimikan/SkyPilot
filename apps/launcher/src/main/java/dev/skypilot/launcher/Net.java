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
	public static final Set<String> HOSTS = Set.of("meta.fabricmc.net", "cdn.modrinth.com");
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

	/** True when the SkyPilot server answers its health check. */
	public static boolean healthy(String baseUrl) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/api/health")).timeout(Duration.ofSeconds(4)).GET().build();
			return CLIENT.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	public static String sha256(byte[] data) {
		return hex("SHA-256", data);
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
