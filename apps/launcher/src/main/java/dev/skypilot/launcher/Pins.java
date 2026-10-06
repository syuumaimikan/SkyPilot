package dev.skypilot.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Versions and hashes this launcher build installs, generated at build time
 * ({@code bundled/pins.properties}): Minecraft, Fabric Loader, the bundled SkyPilot mod jar
 * (SHA-256) and the exact Fabric API file (URL, size, SHA-512).
 */
public record Pins(String minecraft, String loader, String modVersion, String modFile, String modSha256, String fabricApi, String fabricApiUrl, String fabricApiSha512,
		long fabricApiSize) {

	/** The Fabric version id the official launcher shows, e.g. "fabric-loader-0.19.5-26.2". */
	public String versionId() {
		return "fabric-loader-" + loader + "-" + minecraft;
	}

	public String fabricMetaUrl() {
		return "https://meta.fabricmc.net/v2/versions/loader/" + minecraft + "/" + loader + "/profile/json";
	}

	public static Pins load() {
		try (InputStream in = Pins.class.getResourceAsStream("/bundled/pins.properties")) {
			if (in == null) throw new IllegalStateException("bundled/pins.properties is missing from the launcher");
			Properties p = new Properties();
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			return new Pins(p.getProperty("minecraft"), p.getProperty("loader"), p.getProperty("modVersion"), p.getProperty("mod.file"), p.getProperty("mod.sha256"),
					p.getProperty("fabricApi"), p.getProperty("fabricApiUrl"), p.getProperty("fabricApiSha"), Long.parseLong(p.getProperty("fabricApiSize")));
		} catch (IOException e) {
			throw new IllegalStateException("cannot read the launcher's pins", e);
		}
	}

	/** The SkyPilot mod jar bundled with this launcher. */
	public static byte[] bundledMod() throws IOException {
		try (InputStream in = Pins.class.getResourceAsStream("/bundled/skypilot-mod.jar")) {
			if (in == null) throw new IOException("the SkyPilot mod jar is missing from the launcher");
			return in.readAllBytes();
		}
	}
}
