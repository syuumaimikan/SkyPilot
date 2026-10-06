package dev.skypilot.launcher;

import dev.skypilot.mod.core.Lang;

import java.util.List;

/**
 * A support summary to paste into a bug report: system, versions, checks and mods. The home
 * folder (which contains the Windows user name) is replaced with "~"; no account data exists to
 * include in the first place.
 */
public final class Diagnostics {
	private Diagnostics() {
	}

	public static String anonymize(String text) {
		if (text == null) return "";
		String home = System.getProperty("user.home", "");
		String out = text;
		if (home.length() > 3) {
			out = out.replace(home, "~").replace(home.replace('\\', '/'), "~");
		}
		// any other Windows profile path
		return out.replaceAll("(?i)([A-Z]:\\\\Users\\\\)[^\\\\\\s]+", "$1<user>");
	}

	public static String build(LauncherSettings s, Pins pins, Lang l, List<Setup.Check> checks, List<ModScan.Meta> mods, List<ModCheck.Issue> issues) {
		StringBuilder b = new StringBuilder();
		b.append("SkyPilot Launcher ").append(Net.version()).append('\n');
		b.append("OS: ").append(SystemInfo.os()).append('\n');
		b.append("Launcher Java: ").append(SystemInfo.java()).append('\n');
		long ram = SystemInfo.totalMemoryMb();
		b.append("RAM: ").append(ram > 0 ? ram / 1024 + " GB" : "?").append(", game memory ").append(s.memoryMb).append(" MB");
		if (!s.extraJvmArgs.isBlank()) b.append(", extra args: ").append(s.extraJvmArgs);
		b.append('\n');
		b.append("Profile: Minecraft ").append(pins.minecraft()).append(", Fabric Loader ").append(pins.loader()).append(", SkyPilot ").append(pins.modVersion()).append(", Fabric API ")
				.append(pins.fabricApi()).append('\n');
		b.append("Minecraft folder: ").append(s.minecraft()).append('\n');
		b.append("Game folder: ").append(s.instance()).append('\n');
		b.append("\nChecks:\n");
		for (Setup.Check c : checks) b.append(c.ok() ? "  [ok] " : "  [--] ").append(c.label()).append(" - ").append(c.detail()).append('\n');
		b.append("\nMods (").append(mods.size()).append("):\n");
		for (ModScan.Meta m : mods) {
			b.append(m.enabled() ? "  [on]  " : "  [off] ").append(m.name()).append(' ').append(m.version()).append(" (").append(m.id().isEmpty() ? "?" : m.id()).append(", ")
					.append(m.file().getFileName()).append(")\n");
		}
		if (!issues.isEmpty()) {
			b.append("\nProblems:\n");
			for (ModCheck.Issue i : issues) b.append("  ").append(i.level()).append(' ').append(i.modName()).append(": ").append(i.text()).append('\n');
		}
		return anonymize(b.toString());
	}
}
