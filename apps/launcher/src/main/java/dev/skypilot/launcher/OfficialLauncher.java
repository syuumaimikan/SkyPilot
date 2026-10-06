package dev.skypilot.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Opens the official Minecraft Launcher (fixed commands only). Signing in with Microsoft and
 * pressing Play happen there - SkyPilot never sees an account, password or token.
 */
public final class OfficialLauncher {
	/** App id of the Microsoft Store / Xbox app "Minecraft Launcher" (Get-StartApps). */
	static final String STORE_APP = "Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft";

	private OfficialLauncher() {
	}

	/** The command that would start the launcher on this computer, or null when unknown. */
	public static List<String> command() {
		switch (Env.os()) {
			case WINDOWS -> {
				for (String base : new String[] {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")}) {
					if (base == null) continue;
					Path exe = Path.of(base, "Minecraft Launcher", "MinecraftLauncher.exe");
					if (Files.isRegularFile(exe)) return List.of(exe.toString());
				}
				return List.of("explorer.exe", "shell:AppsFolder\\" + STORE_APP);
			}
			case MAC -> {
				return List.of("open", "-a", "Minecraft");
			}
			default -> {
				return List.of("minecraft-launcher");
			}
		}
	}

	/** Null on success, else the error. */
	public static String start() {
		try {
			new ProcessBuilder(command()).inheritIO().start();
			return null;
		} catch (Exception e) {
			return e.getMessage();
		}
	}
}
