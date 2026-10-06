package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skypilot.mod.core.Lang;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the switched-on mods against each other and against this profile (Minecraft 26.2,
 * Fabric Loader 0.19.5, Java 25) the way Fabric Loader resolves them: "depends" must be present in
 * a matching version, "breaks" must not be, "conflicts" and missing "recommends" are warnings.
 * Fabric itself provides the built-in mods "minecraft", "java" and "fabricloader" (checked in
 * Fabric Loader 0.19.5's sources).
 */
public final class ModCheck {
	public enum Level {
		ERROR, WARNING, INFO
	}

	public record Issue(Path file, String modName, Level level, String text) {
	}

	private ModCheck() {
	}

	/** Built-in mods of this profile: id → version. */
	public static Map<String, String> builtins(Pins pins, Path minecraftDir) {
		Map<String, String> b = new LinkedHashMap<>();
		b.put("minecraft", pins.minecraft());
		b.put("fabricloader", pins.loader());
		b.put("java", String.valueOf(javaVersion(minecraftDir, pins.minecraft())));
		return b;
	}

	/** The Java version the official launcher uses for this Minecraft version (from its version file; 25 for 26.x). */
	static int javaVersion(Path minecraftDir, String minecraft) {
		try {
			Path f = minecraftDir.resolve("versions").resolve(minecraft).resolve(minecraft + ".json");
			if (Files.isRegularFile(f)) {
				JsonObject json = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
				return json.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
			}
		} catch (Exception e) {
			// fall through
		}
		return 25;
	}

	/** Ids that Fabric resolves in ways the launcher cannot see (bundled inside Fabric Loader). */
	private static boolean unknownBuiltin(String id) {
		return id.equals("mixinextras");
	}

	public static List<Issue> check(List<ModScan.Meta> mods, Map<String, String> builtins, Lang l) {
		List<Issue> out = new ArrayList<>();
		Map<String, String> present = new HashMap<>(builtins);
		Map<String, List<ModScan.Meta>> byId = new LinkedHashMap<>();
		for (ModScan.Meta m : mods) {
			if (!m.enabled()) continue;
			if (!m.fabric()) {
				out.add(new Issue(m.file(), m.name(), Level.WARNING, l.t("Not a Fabric mod (maybe for Forge/NeoForge, or a library). Fabric ignores it.",
						"Fabric用のMODではありません（Forge/NeoForge用やライブラリの可能性）。Fabricは読み込みません。")));
				continue;
			}
			byId.computeIfAbsent(m.id(), k -> new ArrayList<>()).add(m);
			for (Map.Entry<String, String> p : m.provides().entrySet()) present.merge(p.getKey(), p.getValue(), (a, b) -> FabricVersion.parse(a).compareTo(FabricVersion.parse(b)) >= 0 ? a : b);
		}
		for (Map.Entry<String, List<ModScan.Meta>> e : byId.entrySet()) {
			if (e.getValue().size() < 2) continue;
			List<String> files = e.getValue().stream().map(m -> m.file().getFileName().toString()).toList();
			for (ModScan.Meta m : e.getValue()) {
				out.add(new Issue(m.file(), m.name(), Level.WARNING, l.t("Switched on twice (" + String.join(", ", files) + "). Fabric loads only the newest - switch the other off.",
						"2つ有効になっています（" + String.join("、", files) + "）。Fabricは新しい方だけを読み込むので、もう一方を無効にしてください。")));
			}
		}
		for (List<ModScan.Meta> same : byId.values()) {
			for (ModScan.Meta m : same) {
				if (m.environment().equals("server")) {
					out.add(new Issue(m.file(), m.name(), Level.WARNING, l.t("A server-only mod: it is not loaded in the game client.", "サーバー専用のMODです。ゲームクライアントでは読み込まれません。")));
					continue;
				}
				for (Map.Entry<String, List<String>> d : m.depends().entrySet()) {
					String id = d.getKey();
					if (unknownBuiltin(id)) continue;
					String have = present.get(id);
					String range = range(d.getValue(), l);
					if (have == null) {
						out.add(new Issue(m.file(), m.name(), Level.ERROR, l.t("Needs " + label(id) + " " + range + ", which is not installed.",
								label(id) + " " + range + " が必要ですが、入っていません。")));
					} else if (!FabricVersion.matchesAny(d.getValue(), have)) {
						out.add(new Issue(m.file(), m.name(), Level.ERROR, id.equals("minecraft")
								? l.t("Made for Minecraft " + range + "; this profile is Minecraft " + have + ".", "Minecraft " + range + " 用です。このプロファイルは Minecraft " + have + " です。")
								: l.t("Needs " + label(id) + " " + range + ", but " + have + " is installed.", label(id) + " " + range + " が必要ですが、入っているのは " + have + " です。")));
					}
				}
				for (Map.Entry<String, List<String>> d : m.breaks().entrySet()) {
					String have = present.get(d.getKey());
					if (have != null && FabricVersion.matchesAny(d.getValue(), have)) {
						out.add(new Issue(m.file(), m.name(), Level.ERROR, l.t("Does not work together with " + label(d.getKey()) + " " + have + ". Switch one of them off.",
								label(d.getKey()) + " " + have + " と同時には動きません。どちらかを無効にしてください。")));
					}
				}
				for (Map.Entry<String, List<String>> d : m.conflicts().entrySet()) {
					String have = present.get(d.getKey());
					if (have != null && FabricVersion.matchesAny(d.getValue(), have)) {
						out.add(new Issue(m.file(), m.name(), Level.WARNING, l.t("May cause problems with " + label(d.getKey()) + " " + have + ".",
								label(d.getKey()) + " " + have + " と一緒だと問題が起きる可能性があります。")));
					}
				}
				for (Map.Entry<String, List<String>> d : m.recommends().entrySet()) {
					if (!present.containsKey(d.getKey()) && !unknownBuiltin(d.getKey())) {
						out.add(new Issue(m.file(), m.name(), Level.INFO, l.t("Works best with " + label(d.getKey()) + ".", label(d.getKey()) + " と一緒に使うのがおすすめです。")));
					}
				}
			}
		}
		return out;
	}

	static String label(String id) {
		return switch (id) {
			case "minecraft" -> "Minecraft";
			case "fabricloader" -> "Fabric Loader";
			case "fabric-api", "fabric" -> "Fabric API";
			case "java" -> "Java";
			default -> id;
		};
	}

	static String range(List<String> ranges, Lang l) {
		if (ranges.isEmpty() || ranges.stream().allMatch(r -> r.isBlank() || r.equals("*"))) return l.t("(any version)", "（どの版でも）");
		return String.join(l.t(" or ", " または "), ranges);
	}

	/** Issues of one file. */
	public static List<Issue> of(List<Issue> all, Path file) {
		return all.stream().filter(i -> i.file().equals(file)).toList();
	}

	/** The worst level of one file, or null. */
	public static Level worst(List<Issue> all, Path file) {
		Level w = null;
		for (Issue i : all) if (i.file().equals(file) && (w == null || i.level().ordinal() < w.ordinal())) w = i.level();
		return w;
	}
}
