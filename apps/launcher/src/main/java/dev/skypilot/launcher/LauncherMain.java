package dev.skypilot.launcher;

import dev.skypilot.launcher.ui.LauncherFrame;
import dev.skypilot.mod.core.Lang;

import java.util.Arrays;
import java.util.Locale;

/**
 * SkyPilot Launcher. With no arguments it opens the window; {@code --status} prints the checks
 * and {@code --setup} runs the setup without a window (both honour {@code --minecraft-dir <dir>}
 * and {@code --instance-dir <dir>}).
 */
public final class LauncherMain {
	private LauncherMain() {
	}

	public static Lang lang(LauncherSettings s) {
		return Lang.resolve(s.language, Locale.getDefault().getLanguage().equals("ja") ? "ja_jp" : "en_us");
	}

	public static void main(String[] args) throws Exception {
		LauncherSettings s = LauncherSettings.load(Env.launcherDir());
		for (int i = 0; i + 1 < args.length; i++) {
			if (args[i].equals("--minecraft-dir")) s.minecraftDir = args[i + 1];
			if (args[i].equals("--instance-dir")) s.instanceDir = args[i + 1];
		}
		Pins pins = Pins.load();
		Lang l = lang(s);
		if (Arrays.asList(args).contains("--status")) {
			for (Setup.Check c : Setup.status(s, pins, l, true)) System.out.println((c.ok() ? "[ok] " : "[--] ") + c.label() + " - " + c.detail());
			return;
		}
		if (Arrays.asList(args).contains("--setup")) {
			Setup.run(s, pins, l, url -> Net.download(url, 64L << 20), System.out::println);
			return;
		}
		LauncherFrame.open(s, pins);
	}
}
