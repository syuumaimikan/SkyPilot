package dev.skypilot.launcher;

import dev.skypilot.mod.core.Lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explains a crash report, log or JVM error file in plain words. The patterns are the texts the
 * current tools print (checked 2026-10-06):
 * <ul>
 * <li>Minecraft 26.2 crash reports: "Description: …";</li>
 * <li>Fabric Loader 0.19.5 (Messages.properties): "Incompatible mods found!", "… requires … which
 * is missing!", "… but only the wrong version …", "… is incompatible with …", "A mod crashed on
 * startup!", and the "Install / Replace / Remove …" suggestions;</li>
 * <li>Fabric's Mixin 0.17.4: "Mixin [x] from phase [p] in config [c] FAILED during APPLY",
 * "Error encountered whilst initialising mixin config 'c' from mod 'm'", and injected handler
 * names "handler$xxx000$&lt;mod id&gt;$…";</li>
 * <li>HotSpot error files: "# Problematic frame: … [driver.dll+0x…]".</li>
 * </ul>
 */
public final class CrashAnalyzer {
	public enum Kind {
		CAUSE, MOD, FIX, INFO
	}

	public record Finding(Kind kind, String title, String detail) {
	}

	private static final Pattern DESCRIPTION = Pattern.compile("(?m)^Description: (.+)$");
	private static final Pattern RESOLUTION = Pattern.compile("(?m)^\\s*-\\s*(.+(?:which is missing!|but only the wrong version.+|is incompatible with .+|conflicts with .+|which can't be loaded due to other constraints!))\\s*$");
	private static final Pattern SOLUTION = Pattern.compile("(?m)^\\s*-\\s*((?:Install|Replace|Remove) .+)$");
	private static final Pattern MIXIN_FAILED = Pattern.compile("Mixin \\[([^\\]]+)\\] from phase \\[[^\\]]*\\] in config \\[([^\\]]+)\\] FAILED");
	private static final Pattern MIXIN_INIT = Pattern.compile("initialising mixin config '([^']+)' from mod '([^']+)'");
	private static final Pattern HANDLER = Pattern.compile("\\$[a-z]{3}\\d{3}\\$([a-z0-9_-]+)\\$");
	private static final Pattern FRAME = Pattern.compile("(?m)^\\s+at (?:[\\w.]+//)?([\\w.$]+)\\.[\\w$<>]+\\(");
	private static final Pattern CLASS_VERSION = Pattern.compile("class file version (\\d+)\\.\\d+\\), this version of the Java Runtime only recognizes class file versions up to (\\d+)");
	private static final Pattern NO_CLASS = Pattern.compile("(?:NoClassDefFoundError|ClassNotFoundException): ([\\w./$]+)");
	private static final Pattern NATIVE_FRAME = Pattern.compile("Problematic frame:\\s*\\n#\\s*C\\s+\\[([^\\]+]+)");
	private static final Set<String> GPU = Set.of("atio6axx.dll", "atioglxx.dll", "amdxc64.dll", "nvoglv64.dll", "nvoglv32.dll", "ig9icd64.dll", "ig75icd64.dll", "ig7icd64.dll",
			"igxelpicd64.dll", "ig11icd64.dll", "ig12icd64.dll", "opengl32.dll", "libnvidia-glcore.so", "radeonsi_dri.so", "iris_dri.so");

	private CrashAnalyzer() {
	}

	public static List<Finding> analyze(String text, List<ModScan.Meta> mods, int memoryMb, Lang l) {
		List<Finding> out = new ArrayList<>();
		if (text == null || text.isBlank()) return out;
		Matcher d = DESCRIPTION.matcher(text);
		if (d.find()) {
			String desc = d.group(1).strip();
			String exception = firstLineAfter(text, d.end());
			out.add(new Finding(Kind.INFO, l.t("Crash: ", "クラッシュ: ") + desc, exception));
		}
		if (text.contains("Incompatible mods found!")) {
			out.add(new Finding(Kind.CAUSE, l.t("Fabric found mods that don't fit together", "Fabricが合わないMODの組み合わせを見つけました"),
					l.t("The game stopped before starting. The lines below say what to change.", "ゲームは起動前に止まりました。下の行に変更点が書かれています。")));
		}
		Set<String> seen = new LinkedHashSet<>();
		Matcher r = RESOLUTION.matcher(text);
		while (r.find() && seen.size() < 12) {
			if (seen.add(r.group(1).strip())) out.add(new Finding(Kind.CAUSE, r.group(1).strip(), ""));
		}
		Matcher s = SOLUTION.matcher(text);
		Set<String> fixes = new LinkedHashSet<>();
		while (s.find() && fixes.size() < 8) fixes.add(s.group(1).strip());
		for (String f : fixes) out.add(new Finding(Kind.FIX, f, ""));
		if (text.contains("A mod crashed on startup!")) {
			out.add(new Finding(Kind.CAUSE, l.t("A mod crashed while the game was starting", "ゲームの起動中にMODがクラッシュしました"),
					l.t("See \"Suspected mods\" below; updating or switching that mod off usually helps.", "下の「疑わしいMOD」を更新するか無効にすると直ることが多いです。")));
		}
		if (text.contains("java.lang.OutOfMemoryError")) {
			out.add(new Finding(Kind.CAUSE, l.t("Minecraft ran out of memory", "Minecraftのメモリが足りなくなりました"),
					l.t("It has " + memoryMb / 1024 + " GB now. Give it more in Settings → Memory (4-6 GB is usual for SkyBlock with mods), or switch heavy mods off.",
							"現在 " + memoryMb / 1024 + " GB です。設定 → メモリで増やすか（MOD入りのSkyBlockなら4〜6GBが目安）、重いMODを無効にしてください。")));
		}
		Matcher cv = CLASS_VERSION.matcher(text);
		if (cv.find()) {
			int needs = Integer.parseInt(cv.group(1)) - 44;
			int has = Integer.parseInt(cv.group(2)) - 44;
			out.add(new Finding(Kind.CAUSE, l.t("A mod needs Java " + needs + ", the game runs on Java " + has, "Java " + needs + " が必要なMODがありますが、ゲームは Java " + has + " で動いています"),
					l.t("That mod is made for a newer Minecraft. Look for a version for Minecraft 26.2.", "そのMODは新しいMinecraft用です。Minecraft 26.2 用の版を探してください。")));
		}
		Matcher nc = NO_CLASS.matcher(text);
		if (nc.find()) {
			String cls = nc.group(1).replace('/', '.');
			out.add(new Finding(Kind.CAUSE, l.t("Missing code: ", "見つからないコード: ") + cls,
					l.t("A mod expects another mod (or another Minecraft version) that isn't there.", "あるMODが、入っていない別のMOD（または別のMinecraftバージョン）を前提にしています。")));
		}
		Matcher nf = NATIVE_FRAME.matcher(text);
		if (nf.find()) {
			String lib = nf.group(1).strip();
			if (GPU.contains(lib.toLowerCase(Locale.ROOT))) {
				out.add(new Finding(Kind.CAUSE, l.t("The graphics driver crashed (" + lib + ")", "グラフィックドライバーがクラッシュしました（" + lib + "）"),
						l.t("Update the graphics driver from AMD / NVIDIA / Intel's site. Shader or rendering mods can also trigger this.",
								"AMD / NVIDIA / Intel のサイトからグラフィックドライバーを更新してください。シェーダーや描画系MODが原因のこともあります。")));
			} else {
				out.add(new Finding(Kind.INFO, l.t("Crashed in native code: ", "ネイティブコードでクラッシュ: ") + lib, ""));
			}
		}
		if (text.contains("GLFW error 65542") || text.contains("does not appear to support OpenGL")) {
			out.add(new Finding(Kind.CAUSE, l.t("OpenGL is not available", "OpenGLが使えません"),
					l.t("Install or update the graphics driver; Minecraft can't draw without it.", "グラフィックドライバーをインストール・更新してください。")));
		}
		// Which mods are involved?
		Map<String, Integer> suspects = new LinkedHashMap<>();
		Matcher mf = MIXIN_FAILED.matcher(text);
		while (mf.find()) bump(suspects, modForMixin(mods, mf.group(2)), 5);
		Matcher mi = MIXIN_INIT.matcher(text);
		while (mi.find()) bump(suspects, name(mods, mi.group(2)), 5);
		Matcher h = HANDLER.matcher(text);
		while (h.find()) bump(suspects, name(mods, h.group(1)), 2);
		Matcher fr = FRAME.matcher(text);
		int frames = 0;
		while (fr.find() && frames++ < 400) bump(suspects, modForClass(mods, fr.group(1)), 1);
		suspects.remove(null);
		suspects.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
				.forEach(e -> out.add(new Finding(Kind.MOD, l.t("Suspected mod: ", "疑わしいMOD: ") + e.getKey(), l.t("mentioned " + e.getValue() + "×", e.getValue() + " 回登場"))));
		if (out.stream().noneMatch(f -> f.kind() == Kind.CAUSE || f.kind() == Kind.MOD)) {
			out.add(new Finding(Kind.INFO, l.t("No known cause found", "既知の原因は見つかりませんでした"),
					l.t("Try Mods → Troubleshoot (only SkyPilot + Fabric API), then switch mods back on a few at a time.",
							"MOD → トラブルシュート（SkyPilotとFabric APIだけにする）を試し、その後少しずつMODを戻してください。")));
		}
		return out;
	}

	private static String firstLineAfter(String text, int pos) {
		int i = pos;
		while (i < text.length() && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) i++;
		int end = text.indexOf('\n', i);
		String line = (end < 0 ? text.substring(i) : text.substring(i, end)).strip();
		return line.length() > 200 ? line.substring(0, 200) + "…" : line;
	}

	private static void bump(Map<String, Integer> m, String key, int by) {
		if (key != null) m.merge(key, by, Integer::sum);
	}

	private static String name(List<ModScan.Meta> mods, String id) {
		if (id == null || id.isBlank() || id.equals("minecraft") || id.equals("fabricloader") || id.equals("java")) return null;
		for (ModScan.Meta m : mods) if (m.id().equals(id) || m.provides().containsKey(id)) return m.name();
		return id;
	}

	private static String modForMixin(List<ModScan.Meta> mods, String config) {
		for (ModScan.Meta m : mods) if (m.mixinConfigs().contains(config)) return m.name();
		return config;
	}

	private static String modForClass(List<ModScan.Meta> mods, String cls) {
		if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("net.minecraft.") || cls.startsWith("com.mojang.")
				|| cls.startsWith("net.fabricmc.loader.") || cls.startsWith("org.spongepowered.")) {
			return null;
		}
		String best = null;
		int bestLen = 0;
		for (ModScan.Meta m : mods) {
			for (String p : m.packages()) {
				if ((cls.startsWith(p + ".") || cls.equals(p)) && p.length() > bestLen) {
					best = m.name();
					bestLen = p.length();
				}
			}
		}
		return best;
	}
}
