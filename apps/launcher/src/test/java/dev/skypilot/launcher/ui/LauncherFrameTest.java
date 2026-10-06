package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.LauncherFeaturesTest;
import dev.skypilot.launcher.LauncherSettings;
import dev.skypilot.launcher.Pins;
import dev.skypilot.launcher.ServerList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paints every page without a window (headless), in English, Japanese and the light theme, and
 * saves them to build/launcher-screens for a visual check. Temporary folders only.
 */
class LauncherFrameTest {
	@Test
	void paintsEveryPage(@TempDir Path dir) throws Exception {
		System.setProperty("java.awt.headless", "true");
		Path mc = dir.resolve("mc");
		Path game = mc.resolve("skypilot");
		Files.createDirectories(game);
		Files.writeString(mc.resolve("launcher_profiles.json"), "{\"profiles\":{}}", StandardCharsets.UTF_8);
		Path mods = LauncherFeaturesTest.modsFolder(game);
		assertTrue(Files.isDirectory(mods));
		Files.createDirectories(game.resolve("logs"));
		Files.writeString(game.resolve("logs/latest.log"), String.join("\n", List.of(
				"[12:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.5",
				"[12:00:01] [main/INFO]: SkyPilot ready",
				"[12:00:02] [Render thread/WARN]: Missing sound for event: minecraft:item.goat_horn.play",
				"[12:00:03] [Render thread/ERROR]: Couldn't load texture",
				"[12:00:04] [main/INFO]: [SkyPilot] linked")));
		Files.createDirectories(game.resolve("crash-reports"));
		Files.writeString(game.resolve("crash-reports/crash-2026-10-06_12.00.00-client.txt"), String.join("\n", List.of(
				"---- Minecraft Crash Report ----", "// Don't panic!", "", "Time: 2026-10-06 12:00:00", "Description: Rendering entity in world", "",
				"java.lang.NullPointerException: Cannot invoke \"Object.toString()\" because \"x\" is null",
				"\tat knot//com.example.needy.render.Thing.draw(Thing.java:10)",
				"\tat knot//net.minecraft.client.renderer.LevelRenderer.render(LevelRenderer.java:99)")));
		Files.createDirectories(game.resolve("screenshots"));
		for (int i = 0; i < 6; i++) {
			BufferedImage shot = new BufferedImage(480, 270, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = shot.createGraphics();
			g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(i / 6f, 0.6f, 0.8f), 480, 270, Color.getHSBColor(i / 6f + 0.3f, 0.7f, 0.3f)));
			g.fillRect(0, 0, 480, 270);
			g.dispose();
			ImageIO.write(shot, "png", game.resolve("screenshots/2026-10-0" + (i + 1) + "_12.00.00.png").toFile());
		}
		ServerList.addHypixel(game);
		Path launcherDir = dir.resolve("launcher");
		Path out = Path.of("build", "launcher-screens");
		Files.createDirectories(out);
		String[] pages = {"home", "mods", "features", "screenshots", "logs", "tools", "settings", "about"};
		String[][] runs = {{"en", "dark"}, {"ja", "dark"}, {"ja", "light"}};
		for (String[] run : runs) {
			LauncherSettings s = new LauncherSettings();
			s.minecraftDir = mc.toString();
			s.language = run[0];
			s.theme = run[1];
			s.playerName = "";
			for (String page : run[1].equals("light") ? new String[] {"home", "mods", "settings"} : pages) {
				JComponent[] content = new JComponent[1];
				SwingUtilities.invokeAndWait(() -> content[0] = LauncherFrame.createContent(s, Pins.load(), page, launcherDir));
				Thread.sleep(page.equals("mods") || page.equals("screenshots") || page.equals("tools") ? 1500 : 300);
				BufferedImage[] img = new BufferedImage[1];
				SwingUtilities.invokeAndWait(() -> img[0] = LauncherFrame.paint(content[0], 1240, 780));
				Path f = out.resolve(run[0] + "-" + run[1] + "-" + page + ".png");
				ImageIO.write(img[0], "png", f.toFile());
				assertTrue(Files.size(f) > 8_000, f + " looks empty");
			}
		}
	}
}
