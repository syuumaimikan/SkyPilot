package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.LongConsumer;

/**
 * Launcher updates from your SkyPilot server ({@code /api/launcher/latest}): a release
 * description signed with Ed25519 and the Windows installer it names. An update is installed
 * only when the signature matches the public key built into this launcher and the downloaded
 * installer has the SHA-256 and size the signed description gives - so neither the server nor
 * the network can slip in another program. Nothing is installed without the player's click.
 */
public final class Updater {
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

	public record Release(String version, String file, String sha256, long size, String publishedAt) {
	}

	private Updater() {
	}

	/** The update-signing public key built into this launcher, or null in builds without one. */
	static PublicKey publicKey() {
		try (InputStream in = Updater.class.getResourceAsStream("/update-key.pub")) {
			if (in == null) return null;
			byte[] der = Base64.getDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII).strip());
			return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
		} catch (Exception e) {
			return null;
		}
	}

	/** Check {@code manifest} against {@code signatureB64}; returns the release, or throws. */
	public static Release verify(String manifest, String signatureB64, PublicKey key) throws IOException {
		if (key == null) throw new IOException("this launcher build has no update key");
		try {
			Signature sig = Signature.getInstance("Ed25519");
			sig.initVerify(key);
			sig.update(manifest.getBytes(StandardCharsets.UTF_8));
			if (!sig.verify(Base64.getDecoder().decode(signatureB64.strip()))) throw new IOException("the update's signature does not match");
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("the update's signature could not be checked", e);
		}
		JsonObject o = JsonParser.parseString(manifest).getAsJsonObject();
		Release r = new Release(o.get("version").getAsString(), o.get("file").getAsString(), o.get("sha256").getAsString(), o.get("size").getAsLong(),
				o.has("publishedAt") ? o.get("publishedAt").getAsString() : "");
		if (!r.version().matches("\\d{1,4}(\\.\\d{1,6}){0,3}") || !r.file().matches("[A-Za-z0-9._-]{1,80}\\.(exe|msi)") || !r.sha256().matches("[0-9a-f]{64}")
				|| r.size() <= 0 || r.size() > 1L << 30) {
			throw new IOException("the update description is malformed");
		}
		return r;
	}

	/** Numeric version order ("0.10.0" &gt; "0.9.2"); "dev" builds are never older. */
	public static boolean newer(String candidate, String current) {
		if (current == null || !current.matches("\\d+(\\.\\d+)*")) return false;
		String[] a = candidate.split("\\.");
		String[] b = current.split("\\.");
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			long x = i < a.length ? Long.parseLong(a[i]) : 0;
			long y = i < b.length ? Long.parseLong(b[i]) : 0;
			if (x != y) return x > y;
		}
		return false;
	}

	private static URI api(String server, String path) throws IOException {
		if (!Instance.validServerUrl(server)) throw new IOException("the SkyPilot server address is not valid");
		return URI.create(server.replaceAll("/+$", "") + path);
	}

	/** The newest signed release on the server, or null when there is none (or no newer one). */
	public static Release check(String server) throws IOException {
		HttpRequest req = HttpRequest.newBuilder(api(server, "/api/launcher/latest")).timeout(Duration.ofSeconds(15)).version(HttpClient.Version.HTTP_1_1)
				.header("User-Agent", "SkyPilot-Launcher/" + Net.versionName()).GET().build();
		try {
			HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() == 404) return null;
			if (resp.statusCode() == 429) throw new Net.RateLimited(req.uri().getHost());
			if (resp.statusCode() != 200) throw new IOException("update check: HTTP " + resp.statusCode());
			JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
			Release r = verify(o.get("manifest").getAsString(), o.get("signature").getAsString(), publicKey());
			return newer(r.version(), Net.versionName()) ? r : null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		} catch (RuntimeException e) {
			throw new IOException("unexpected update answer", e);
		}
	}

	/** Download the installer into {@code dir} and check it against the signed SHA-256 and size. */
	public static Path download(String server, Release r, Path dir, LongConsumer bytes) throws IOException {
		HttpRequest req = HttpRequest.newBuilder(api(server, "/api/launcher/files/" + r.file())).timeout(Duration.ofMinutes(10)).version(HttpClient.Version.HTTP_1_1)
				.header("User-Agent", "SkyPilot-Launcher/" + Net.versionName()).GET().build();
		Files.createDirectories(dir);
		Path target = dir.resolve(r.file());
		Path tmp = dir.resolve(r.file() + ".part");
		try {
			HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = resp.body(); var out = Files.newOutputStream(tmp)) {
				if (resp.statusCode() != 200) throw new IOException("update download: HTTP " + resp.statusCode());
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				byte[] buf = new byte[1 << 16];
				long total = 0;
				for (int n; (n = in.read(buf)) > 0; ) {
					total += n;
					if (total > r.size()) throw new IOException("the update is larger than announced");
					md.update(buf, 0, n);
					out.write(buf, 0, n);
					bytes.accept(total);
				}
				if (total != r.size() || !HexFormat.of().formatHex(md.digest()).equals(r.sha256())) throw new IOException("the downloaded update failed its checksum");
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/**
	 * Run the verified installer (per-user, no administrator rights) and start the new launcher
	 * when it has finished. The caller exits right after, so the installer can replace the files.
	 */
	public static void install(Path installer) throws IOException {
		Path script = installer.resolveSibling("skypilot-update.cmd");
		Path app = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "SkyPilot Launcher", "SkyPilot Launcher.exe");
		Files.writeString(script, String.join("\r\n",
				"@echo off",
				"timeout /t 2 /nobreak >nul",
				"start \"\" /wait \"" + installer + "\"",
				"if exist \"" + app + "\" start \"\" \"" + app + "\"",
				"del \"%~f0\"") + "\r\n", StandardCharsets.UTF_8);
		// start "" /min <script>: an empty window title, then the script in a minimised console
		new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
	}
}
