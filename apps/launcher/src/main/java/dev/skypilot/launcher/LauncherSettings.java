package dev.skypilot.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The launcher's own settings ({@code <launcher dir>/launcher.json}). */
public final class LauncherSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Null = the official launcher's default .minecraft. */
	public String minecraftDir;
	/** Null = .minecraft/skypilot. */
	public String instanceDir;
	public int memoryMb = 4096;
	/** "auto", "en" or "ja". */
	public String language = "auto";
	/** "dark" or "light". */
	public String theme = "dark";
	/** Extra Java tuning flags for the profile (checked by {@link JvmArgs}). */
	public String extraJvmArgs = "";
	/** Game window size in the profile; 0 = the official launcher's default. */
	public int width;
	public int height;
	/** Close the launcher after handing over to the official launcher. */
	public boolean closeAfterPlay;
	/** Optional public Minecraft name, only for the head picture in the sidebar. */
	public String playerName = "";
	/** Window placement (0 = centred default). */
	public int windowX, windowY, windowW, windowH;
	public String lastPage = "home";
	/**
	 * Azure application (client) ID for Microsoft sign-in, registered by you and approved by
	 * Mojang (https://aka.ms/mce-reviewappid). Empty = sign-in unavailable, play through the
	 * official launcher.
	 */
	public String msClientId = "";
	/** "direct" (start the game from this launcher when signed in) or "official". */
	public String launchMode = "direct";
	/** Join Hypixel right after the game starts (Minecraft's Quick Play). */
	public boolean joinHypixel = true;
	/** Check for launcher updates on start. */
	public boolean autoUpdateCheck = true;

	/** True when the next Play starts the game here (signed in, direct mode). */
	public boolean direct(boolean signedIn) {
		return signedIn && "direct".equals(launchMode) && MicrosoftAuth.validClientId(msClientId);
	}

	public LauncherProfiles.Options profileOptions() {
		return new LauncherProfiles.Options(memoryMb, JvmArgs.problems(extraJvmArgs).isEmpty() ? JvmArgs.clean(extraJvmArgs) : "", width, height);
	}

	public Path minecraft() {
		return minecraftDir == null || minecraftDir.isBlank() ? Env.minecraftDir() : Path.of(minecraftDir);
	}

	public Path instance() {
		return instanceDir == null || instanceDir.isBlank() ? Env.defaultInstanceDir(minecraft()) : Path.of(instanceDir);
	}

	public Path mods() {
		return instance().resolve("mods");
	}

	public LauncherSettings sanitize() {
		memoryMb = Math.max(2048, Math.min(32768, memoryMb));
		if (language == null || !(language.equals("en") || language.equals("ja") || language.equals("auto"))) language = "auto";
		if (theme == null || !(theme.equals("dark") || theme.equals("light"))) theme = "dark";
		if (extraJvmArgs == null) extraJvmArgs = "";
		extraJvmArgs = JvmArgs.clean(extraJvmArgs);
		if (width < 0 || width > 7680 || height < 0 || height > 4320 || (width == 0) != (height == 0)) {
			width = 0;
			height = 0;
		}
		if (width > 0 && (width < 640 || height < 360)) {
			width = 0;
			height = 0;
		}
		if (playerName == null || !(playerName.isEmpty() || Avatar.validName(playerName))) playerName = "";
		if (lastPage == null || !lastPage.matches("[a-z]{2,16}")) lastPage = "home";
		msClientId = msClientId == null ? "" : msClientId.strip();
		if (!msClientId.isEmpty() && !MicrosoftAuth.validClientId(msClientId)) msClientId = "";
		if (!"official".equals(launchMode)) launchMode = "direct";
		windowW = Math.max(0, Math.min(windowW, 10_000));
		windowH = Math.max(0, Math.min(windowH, 10_000));
		return this;
	}

	public static LauncherSettings load(Path dir) {
		Path f = dir.resolve("launcher.json");
		try {
			if (Files.isRegularFile(f)) {
				LauncherSettings s = GSON.fromJson(Files.readString(f), LauncherSettings.class);
				if (s != null) return s.sanitize();
			}
		} catch (Exception e) {
			// unreadable: start from the defaults
		}
		return new LauncherSettings();
	}

	public void save(Path dir) throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("launcher.json"), GSON.toJson(sanitize()));
	}
}
