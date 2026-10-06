package dev.skypilot.launcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The launcher's own log ({@code <launcher dir>/logs/launcher.log}, rotated at 1 MB) for support.
 * Paths are written with the home folder shortened to "~"; nothing secret is ever logged.
 */
public final class LauncherLog {
	private static Path file;

	private LauncherLog() {
	}

	public static synchronized void init(Path launcherDir) {
		try {
			Path dir = launcherDir.resolve("logs");
			Files.createDirectories(dir);
			file = dir.resolve("launcher.log");
			if (Files.isRegularFile(file) && Files.size(file) > 1_000_000) Files.move(file, dir.resolve("launcher.old.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			file = null;
		}
	}

	public static Path file() {
		return file;
	}

	public static synchronized void info(String message) {
		write("INFO", message);
	}

	public static synchronized void error(String message, Throwable t) {
		StringWriter sw = new StringWriter();
		if (t != null) t.printStackTrace(new PrintWriter(sw));
		write("ERROR", message + (t == null ? "" : "\n" + sw));
	}

	private static void write(String level, String message) {
		if (file == null) return;
		String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " [" + level + "] " + Diagnostics.anonymize(message) + "\n";
		try {
			Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			// logging must never break the launcher
		}
	}
}
