package dev.skypilot.mod.core;

/**
 * The companion builds SkyPilot itself installs or updates. Every download is checked against
 * the pinned size and SHA-512 before it replaces anything; a mismatch installs nothing.
 *
 * <p>The values must equal the launcher's {@code companion-mods.json} (a launcher test checks it).
 */
public final class Companions {
	private Companions() {
	}

	public record Pin(String id, String version, String file, String url, String sha512, long size) {
	}

	/** Skyblocker for Minecraft 26.2 (Modrinth, LGPL-3.0-only). */
	public static final Pin SKYBLOCKER = new Pin(
			"skyblocker",
			"v6.10.4+26.2",
			"skyblocker-6.10.4+26.2.jar",
			"https://cdn.modrinth.com/data/y6DuFGwJ/versions/Kegf578l/skyblocker-6.10.4%2B26.2.jar",
			"6c11fa897495b27f7bc686c2d15e890a13f37267d523ca81a5b0613ad9267debe684335b89be5c06bea7c705266da6a48174101500da77abbbfe86280bfe1edb",
			19_980_109L);

	/** True when {@code bytes} is exactly the pinned build. */
	public static boolean verified(Pin pin, byte[] bytes) {
		if (bytes == null || bytes.length != pin.size()) return false;
		try {
			byte[] d = java.security.MessageDigest.getInstance("SHA-512").digest(bytes);
			return java.util.HexFormat.of().formatHex(d).equalsIgnoreCase(pin.sha512());
		} catch (java.security.NoSuchAlgorithmException e) {
			return false;
		}
	}
}
