package dev.skypilot.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starts Minecraft with Fabric directly (no official launcher): the command line is built from
 * the version files exactly as their argument lists describe, with the player's own session.
 * The session token goes only to the game process; it is never logged or written to disk
 * ({@link #describe} prints the command with it masked).
 */
public final class GameLauncher {
	private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z_]+)}");
	public static final String HYPIXEL = "mc.hypixel.net";

	/** What to start and how. */
	public record Options(int memoryMb, String extraJvmArgs, int width, int height, boolean joinHypixel) {
	}

	private GameLauncher() {
	}

	static String substitute(String s, Map<String, String> vars) {
		Matcher m = VAR.matcher(s);
		StringBuilder b = new StringBuilder();
		while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), m.group(0))));
		m.appendTail(b);
		return b.toString();
	}

	/** An argument list from a version file: plain strings, or {rules, value} objects. */
	static List<String> arguments(JsonArray list, Map<String, String> vars, Map<String, Boolean> features) {
		List<String> out = new ArrayList<>();
		if (list == null) return out;
		for (JsonElement e : list) {
			if (e.isJsonPrimitive()) {
				out.add(substitute(e.getAsString(), vars));
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			if (!GameFiles.allowed(o.has("rules") ? o.getAsJsonArray("rules") : null, features)) continue;
			JsonElement v = o.get("value");
			if (v.isJsonArray()) {
				for (JsonElement x : v.getAsJsonArray()) out.add(substitute(x.getAsString(), vars));
			} else {
				out.add(substitute(v.getAsString(), vars));
			}
		}
		return out;
	}

	private static JsonArray args(JsonObject version, String kind) {
		return version.has("arguments") && version.getAsJsonObject("arguments").has(kind) ? version.getAsJsonObject("arguments").getAsJsonArray(kind) : null;
	}

	/** The full command line. */
	public static List<String> command(GameFiles.Prepared p, Path gameDir, Path nativesDir, MicrosoftAuth.Session s, String clientId, Options o) {
		Map<String, String> vars = new HashMap<>();
		vars.put("natives_directory", nativesDir.toString());
		vars.put("launcher_name", "SkyPilot");
		vars.put("launcher_version", Net.versionName());
		vars.put("classpath", String.join(File.pathSeparator, p.classpath().stream().map(Path::toString).toList()));
		vars.put("classpath_separator", File.pathSeparator);
		vars.put("library_directory", p.minecraftDir().resolve("libraries").toString());
		vars.put("version_name", p.fabric().get("id").getAsString());
		vars.put("game_directory", gameDir.toString());
		vars.put("assets_root", p.minecraftDir().resolve("assets").toString());
		vars.put("assets_index_name", p.assetIndex());
		vars.put("auth_player_name", s.name());
		vars.put("auth_uuid", s.uuid());
		vars.put("auth_access_token", s.accessToken());
		vars.put("auth_xuid", s.xuid() == null ? "" : s.xuid());
		vars.put("clientid", clientId == null ? "" : clientId);
		vars.put("user_type", "msa");
		vars.put("version_type", "release");
		vars.put("resolution_width", Integer.toString(o.width()));
		vars.put("resolution_height", Integer.toString(o.height()));
		vars.put("quickPlayMultiplayer", HYPIXEL);
		Map<String, Boolean> features = new HashMap<>();
		features.put("has_custom_resolution", o.width() > 0 && o.height() > 0);
		features.put("is_quick_play_multiplayer", o.joinHypixel());

		List<String> cmd = new ArrayList<>();
		cmd.add(p.javaExe().toString());
		cmd.add("-Xmx" + o.memoryMb() + "M");
		cmd.add("-Xms" + Math.min(o.memoryMb(), 1024) + "M");
		String extra = JvmArgs.clean(o.extraJvmArgs() == null ? "" : o.extraJvmArgs());
		// each extra flag on its own: only the ones the settings page allows
		if (!extra.isBlank()) for (String a : extra.split(" ")) if (JvmArgs.problems(a).isEmpty()) cmd.add(a);
		cmd.addAll(arguments(args(p.vanilla(), "jvm"), vars, features));
		cmd.addAll(arguments(args(p.fabric(), "jvm"), vars, features));
		cmd.add(p.fabric().get("mainClass").getAsString());
		cmd.addAll(arguments(args(p.vanilla(), "game"), vars, features));
		cmd.addAll(arguments(args(p.fabric(), "game"), vars, features));
		return cmd;
	}

	/** The command for logs: the session token and the classpath are not printed. */
	public static String describe(List<String> cmd, MicrosoftAuth.Session s) {
		List<String> shown = new ArrayList<>();
		for (int i = 0; i < cmd.size(); i++) {
			String a = cmd.get(i);
			if (s != null && a.contains(s.accessToken())) a = "<hidden>";
			if (i > 0 && cmd.get(i - 1).equals("-cp")) a = "<" + a.split(File.pathSeparator).length + " jars>";
			shown.add(a);
		}
		return Diagnostics.anonymize(String.join(" ", shown));
	}

	/** Start the game; its console output goes to {@code logs/game-console.log} in the launcher folder. */
	public static Process start(List<String> cmd, Path gameDir, Path nativesDir, Path launcherDir) throws IOException {
		Files.createDirectories(gameDir);
		for (String sub : List.of("java", "jna", "lwjgl", "netty")) Files.createDirectories(nativesDir.resolve(sub));
		Path log = launcherDir.resolve("logs/game-console.log");
		Files.createDirectories(log.getParent());
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(gameDir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
		pb.environment().remove("_JAVA_OPTIONS");
		pb.environment().remove("JAVA_TOOL_OPTIONS");
		return pb.start();
	}
}
