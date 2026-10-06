package dev.skypilot.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The game folder's {@code options.txt} ("key:value" lines). The launcher changes only the
 * language line ("lang", the key 26.2's {@code Options} uses), and only when asked.
 */
public final class GameOptions {
	private GameOptions() {
	}

	static Path file(Path gameDir) {
		return gameDir.resolve("options.txt");
	}

	public static String get(Path gameDir, String key) {
		try {
			Path f = file(gameDir);
			if (!Files.isRegularFile(f)) return null;
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				if (line.startsWith(key + ":")) return line.substring(key.length() + 1);
			}
		} catch (IOException e) {
			// unreadable: unknown
		}
		return null;
	}

	/** Replace (or add) one "key:value" line, keeping every other line. */
	public static void set(Path gameDir, String key, String value) throws IOException {
		if (!key.matches("[A-Za-z0-9_.]{1,64}") || value.contains("\n") || value.contains("\r")) throw new IOException("invalid option");
		Path f = file(gameDir);
		List<String> lines = Files.isRegularFile(f) ? new ArrayList<>(Files.readAllLines(f, StandardCharsets.UTF_8)) : new ArrayList<>();
		boolean found = false;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(key + ":")) {
				lines.set(i, key + ":" + value);
				found = true;
			}
		}
		if (!found) lines.add(key + ":" + value);
		Files.createDirectories(gameDir);
		Path tmp = f.resolveSibling("options.txt.skypilot-tmp");
		Files.write(tmp, lines, StandardCharsets.UTF_8);
		Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** "ja_jp" or "en_us". */
	public static void setLanguage(Path gameDir, String code) throws IOException {
		if (!code.equals("ja_jp") && !code.equals("en_us")) throw new IOException("unsupported language " + code);
		set(gameDir, "lang", code);
	}
}
