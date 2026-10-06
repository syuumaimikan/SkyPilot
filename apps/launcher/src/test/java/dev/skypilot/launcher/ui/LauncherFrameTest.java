package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.LauncherSettings;
import dev.skypilot.launcher.Pins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paints every page without a window (headless) and saves it to build/launcher-screens for a
 * visual check. Uses temporary folders, never the real .minecraft.
 */
class LauncherFrameTest {
	@Test
	void paintsEveryPage(@TempDir Path dir) throws Exception {
		System.setProperty("java.awt.headless", "true");
		Path mc = dir.resolve("mc");
		Files.createDirectories(mc.resolve("skypilot/mods"));
		Files.writeString(mc.resolve("launcher_profiles.json"), "{\"profiles\":{}}", StandardCharsets.UTF_8);
		Files.write(mc.resolve("skypilot/mods/sodium-fabric-0.8.0+mc26.2.jar"), dev.skypilot.launcher.LauncherTest.jar("sodium", "0.8.0+mc26.2"));
		Files.write(mc.resolve("skypilot/mods/old-mod.jar.disabled"), dev.skypilot.launcher.LauncherTest.jar("old_mod", "1.0"));
		Files.createDirectories(mc.resolve("skypilot/logs"));
		Files.writeString(mc.resolve("skypilot/logs/latest.log"), "[12:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.5\n[12:00:01] [main/INFO]: SkyPilot ready\n");
		Path out = Path.of("build", "launcher-screens");
		Files.createDirectories(out);
		for (String lang : new String[] {"en", "ja"}) {
			LauncherSettings s = new LauncherSettings();
			s.minecraftDir = mc.toString();
			s.language = lang;
			for (String page : new String[] {"home", "mods", "settings", "logs", "about"}) {
				BufferedImage[] img = new BufferedImage[1];
				SwingUtilities.invokeAndWait(() -> {
					JComponent c = LauncherFrame.createContent(s, Pins.load(), page);
					img[0] = LauncherFrame.paint(c, 980, 640);
				});
				Path f = out.resolve(lang + "-" + page + ".png");
				ImageIO.write(img[0], "png", f.toFile());
				assertTrue(Files.size(f) > 5_000, f + " looks empty");
			}
		}
	}
}
