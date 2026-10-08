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
 * server (the loader's version file) and Modrinth's CDN (pinned Fabric API and opt-in companions). Every
 * download has a size limit, and files are checked against the pinned hash before use.
 */
public final class Net {
	/** Files the setup installs (checked against pinned hashes). */
	public static final Set<String> HOSTS = Set.of("meta.fabricmc.net", "cdn.modrinth.com");
	/** Information only (JSON / skin images); nothing from here is run or installed. */
	public static final Set<String> INFO_HOSTS = Set.of("api.modrinth.com", "api.mojang.com", "sessionserver.mojang.com", "textures.minecraft.net");
	/** Minecraft's own game files and Java runtime (each checked against the SHA-1 in Mojang's / Fabric's metadata). */
	public static final Set<String> GAME_HOSTS = Set.of("piston-meta.mojang.com", "piston-data.mojang.com", "launchermeta.mojang.com", "libraries.minecraft.net",
			"resources.download.minecraft.net", "maven.fabricmc.net", "meta.fabricmc.net");
	/** Microsoft sign-in (device code) and the Xbox Live / Minecraft services that turn it into a game session. */
	public static final Set<String> AUTH_HOSTS = Set.of("login.microsoftonline.com", "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "api.minecraftservices.com");
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
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[1 << 16];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
			return HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static URI allowed(String url, Set<String> hosts) throws IOException {
		URI u = URI.create(url);
		if (!"https".equals(u.getScheme()) || u.getHost() == null || !hosts.contains(u.getHost().toLowerCase(Locale.ROOT))) {
			throw new IOException("request to " + u.getHost() + " is not allowed");
		}
		return u;
	}

	/** Small JSON from a game-file host (version lists, version files, asset indexes). */
	public static byte[] getGame(String url, long maxBytes) throws IOException {
		byte[] b = send(HttpRequest.newBuilder(allowed(url, GAME_HOSTS)).GET(), maxBytes);
		if (b == null) throw new IOException(URI.create(url).getHost() + " has no such file");
		return b;
	}

	/**
	 * Stream a game file to {@code target}: written to a temporary file, checked against
	 * {@code sha1} (and {@code size} when &gt; 0), then moved into place. A file that fails the
	 * check is deleted and never used.
	 */
	public static void downloadTo(String url, Path target, String sha1, long size) throws IOException {
		URI u = allowed(url, GAME_HOSTS);
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		HttpRequest req = HttpRequest.newBuilder(u).timeout(Duration.ofMinutes(5)).header("User-Agent", "SkyPilot-Launcher/" + version()).GET().build();
		try {
			HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = resp.body()) {
				if (resp.statusCode() == 429) throw new RateLimited(u.getHost());
				if (resp.statusCode() != 200) throw new IOException(u.getHost() + " answered HTTP " + resp.statusCode() + " for " + target.getFileName());
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				long limit = size > 0 ? size : 1L << 31;
				long total = 0;
				try (var out = Files.newOutputStream(tmp)) {
					byte[] buf = new byte[1 << 16];
					for (int n; (n = in.read(buf)) > 0; ) {
						total += n;
						if (total > limit) throw new IOException(target.getFileName() + " is larger than expected");
						md.update(buf, 0, n);
						out.write(buf, 0, n);
					}
				}
				String got = HexFormat.of().formatHex(md.digest());
				if ((size > 0 && total != size) || (sha1 != null && !sha1.equalsIgnoreCase(got))) {
					throw new IOException(target.getFileName() + " failed its checksum");
				}
				Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/** Status and body of a sign-in request (error answers carry codes the caller explains). */
	public record Reply(int status, byte[] body) {
		public String text() {
			return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	/**
	 * A request to a sign-in host. Tokens travel only in bodies and headers built by the caller;
	 * nothing here logs them. HTTP 429 is not retried.
	 */
	public static Reply auth(String url, String contentType, String body, String bearer) throws IOException {
		HttpRequest.Builder b = HttpRequest.newBuilder(allowed(url, AUTH_HOSTS)).timeout(Duration.ofSeconds(20))
				.header("User-Agent", "SkyPilot-Launcher/" + version()).header("Accept", "application/json");
		if (bearer != null) b.header("Authorization", "Bearer " + bearer);
		if (body == null) b.GET();
		else b.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
		try {
			HttpResponse<InputStream> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = resp.body()) {
				if (resp.statusCode() == 429) throw new RateLimited(URI.create(url).getHost());
				byte[] data = in.readNBytes(1 << 20);
				return new Reply(resp.statusCode(), data);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
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
