package dev.skypilot.launcher;

import dev.skypilot.mod.core.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end, against the real Mojang / Fabric / Modrinth servers (about 700 MB the first time):
 * installs SkyPilot, downloads and checks the game files and Java runtime, starts Minecraft
 * with an offline session (no account) and waits until the title screen has loaded.
 * Only with {@code -Dskypilot.liveLaunch=<empty folder>}; never on CI.
 */
class LiveLaunchTest {
	@Test
	@EnabledIfSystemProperty(named = "skypilot.liveLaunch", matches = ".+")
	void downloadsAndStartsTheGame() throws Exception {
		Path root = Path.of(System.getProperty("skypilot.liveLaunch"));
		Path mc = root.resolve("minecraft");
		Path launcherDir = root.resolve("launcher");
		Files.createDirectories(mc);
		LauncherSettings s = new LauncherSettings();
		s.minecraftDir = mc.toString();
		Pins pins = Pins.load();
		Setup.runDirect(s, pins, Lang.EN, url -> Net.download(url, 64L << 20), System.out::println);
		long t0 = System.currentTimeMillis();
		GameFiles.Prepared p = GameFiles.prepare(mc, launcherDir.resolve("runtime"), pins, false, new GameFiles.Progress() {
			@Override
			public void stage(String en, String ja) {
				System.out.println(en);
			}

			@Override
			public void count(long done, long total) {
				if (done % 500 == 0 || done == total) System.out.println("  " + done + " / " + total);
			}
		}, () -> false);
		System.out.println("files ready in " + (System.currentTimeMillis() - t0) / 1000 + " s");
		// offline session: the title screen loads; servers would refuse it
		MicrosoftAuth.Session offline = new MicrosoftAuth.Session("SkyPilotTest", UUID.randomUUID().toString().replace("-", ""), "0", "0", Long.MAX_VALUE);
		Path natives = launcherDir.resolve("natives").resolve(pins.versionId());
		List<String> cmd = GameLauncher.command(p, s.instance(), natives, offline, "", new GameLauncher.Options(3072, "", 854, 480, false));
		System.out.println(GameLauncher.describe(cmd, offline));
		Process game = GameLauncher.start(cmd, s.instance(), natives, launcherDir);
		Path log = s.instance().resolve("logs/latest.log");
		boolean loaded = false;
		try {
			for (int i = 0; i < 240 && game.isAlive() && !loaded; i++) {
				Thread.sleep(1000);
				if (Files.isRegularFile(log)) {
					String text = Files.readString(log, StandardCharsets.UTF_8);
					loaded = text.contains("Sound engine started") || text.contains("Created: 1024x");
					if (loaded) System.out.println("title screen after " + i + " s; mods: "
							+ text.lines().filter(l -> l.contains("Loading") && l.contains("mods")).findFirst().orElse("?"));
				}
			}
		} finally {
			game.descendants().forEach(ProcessHandle::destroy);
			game.destroy();
		}
		if (!loaded) System.out.println(Files.exists(log) ? Files.readString(log) : Files.readString(launcherDir.resolve("logs/game-console.log")));
		assertTrue(loaded, "the game reached the title screen");
	}
}
