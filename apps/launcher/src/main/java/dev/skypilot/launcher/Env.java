package dev.skypilot.launcher;

import java.nio.file.Path;
import java.util.Locale;

/** Where Minecraft and the launcher keep their files on this computer. */
public final class Env {
	public enum Os {
		WINDOWS, MAC, LINUX
	}

	private Env() {
	}

	public static Os os() {
		String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (n.contains("win")) return Os.WINDOWS;
		if (n.contains("mac")) return Os.MAC;
		return Os.LINUX;
	}

	private static Path home() {
		return Path.of(System.getProperty("user.home"));
	}

	/** The official launcher's game folder (.minecraft). */
	public static Path minecraftDir() {
		return switch (os()) {
			case WINDOWS -> {
				String appData = System.getenv("APPDATA");
				yield (appData != null ? Path.of(appData) : home().resolve("AppData/Roaming")).resolve(".minecraft");
			}
			case MAC -> home().resolve("Library/Application Support/minecraft");
			case LINUX -> home().resolve(".minecraft");
		};
	}

	/** The launcher's own settings folder. */
	public static Path launcherDir() {
		return switch (os()) {
			case WINDOWS -> {
				String appData = System.getenv("APPDATA");
				yield (appData != null ? Path.of(appData) : home().resolve("AppData/Roaming")).resolve("SkyPilot");
			}
			case MAC -> home().resolve("Library/Application Support/SkyPilot");
			case LINUX -> home().resolve(".config/skypilot");
		};
	}

	/** SkyPilot's own game folder (mods, config, logs) inside .minecraft. */
	public static Path defaultInstanceDir(Path minecraftDir) {
		return minecraftDir.resolve("skypilot");
	}
}
