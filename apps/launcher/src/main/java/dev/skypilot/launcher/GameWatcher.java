package dev.skypilot.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Whether the game is running in the SkyPilot folder and whether it crashed, without touching
 * the game process: Minecraft keeps writing logs/latest.log while it runs, and writes a crash
 * report (or a JVM error file) when it crashes.
 */
public final class GameWatcher {
	public enum State {
		IDLE, RUNNING, CRASHED
	}

	public record Status(State state, Path crash) {
	}

	/** The log is written at least every few seconds while the game is open (render/chat/tick messages vary; 90 s is generous). */
	static final long RUNNING_WINDOW_MS = 90_000;

	private GameWatcher() {
	}

	public static Status check(Path gameDir, long since) {
		long now = System.currentTimeMillis();
		Path newest = newestCrash(gameDir);
		if (newest != null && newest.toFile().lastModified() > since) return new Status(State.CRASHED, newest);
		Path log = gameDir.resolve("logs").resolve("latest.log");
		if (Files.isRegularFile(log) && now - log.toFile().lastModified() < RUNNING_WINDOW_MS && log.toFile().lastModified() > since) {
			return new Status(State.RUNNING, null);
		}
		return new Status(State.IDLE, null);
	}

	/** Newest crash report or JVM error file (hs_err_pid*.log in the game folder). */
	public static Path newestCrash(Path gameDir) {
		Path best = null;
		List<Path> all = Instance.crashReports(gameDir);
		if (!all.isEmpty()) best = all.get(0);
		try (var s = Files.list(gameDir)) {
			for (Path p : s.filter(p -> p.getFileName().toString().matches("hs_err_pid\\d+\\.log")).toList()) {
				if (best == null || p.toFile().lastModified() > best.toFile().lastModified()) best = p;
			}
		} catch (Exception e) {
			// no folder yet
		}
		return best;
	}
}
