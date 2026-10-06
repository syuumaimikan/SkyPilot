package dev.skypilot.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies settings from the player's main game (.minecraft) into the SkyPilot game folder, so
 * key bindings, video settings, servers and resource packs carry over. Existing files in the
 * SkyPilot folder are backed up first; nothing in .minecraft is changed.
 */
public final class Importer {
	public record Result(boolean options, int servers, int packs, List<String> notes) {
	}

	private static final long MAX_PACKS = 2L << 30;

	private Importer() {
	}

	public static Result run(Path mainDir, Path gameDir, boolean options, boolean servers, boolean packs) throws IOException {
		if (mainDir.toAbsolutePath().normalize().equals(gameDir.toAbsolutePath().normalize())) throw new IOException("the SkyPilot game folder is the main game folder");
		List<String> notes = new ArrayList<>();
		boolean opt = false;
		if (options) {
			Path src = mainDir.resolve("options.txt");
			if (Files.isRegularFile(src)) {
				Files.createDirectories(gameDir);
				Path dst = gameDir.resolve("options.txt");
				if (Files.exists(dst)) Files.copy(dst, gameDir.resolve("options.txt.skypilot-backup-" + stamp()), StandardCopyOption.REPLACE_EXISTING);
				Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
				opt = true;
			} else {
				notes.add("options.txt");
			}
		}
		int srv = 0;
		if (servers) {
			List<ServerList.Server> list = ServerList.servers(mainDir);
			if (list.isEmpty()) notes.add("servers.dat");
			else srv = ServerList.add(gameDir, list);
		}
		int pk = 0;
		if (packs) {
			Path src = mainDir.resolve("resourcepacks");
			Path dst = gameDir.resolve("resourcepacks");
			if (Files.isDirectory(src)) {
				Files.createDirectories(dst);
				long total = 0;
				try (Stream<Path> s = Files.list(src)) {
					for (Path p : s.sorted().toList()) {
						Path target = dst.resolve(p.getFileName().toString());
						if (Files.exists(target)) continue;
						total += size(p);
						if (total > MAX_PACKS) {
							notes.add("resourcepacks (over 2 GB)");
							break;
						}
						copyTree(p, target);
						pk++;
					}
				}
			} else {
				notes.add("resourcepacks");
			}
		}
		return new Result(opt, srv, pk, notes);
	}

	static String stamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
	}

	static long size(Path p) throws IOException {
		if (Files.isRegularFile(p)) return Files.size(p);
		try (Stream<Path> s = Files.walk(p)) {
			return s.filter(Files::isRegularFile).mapToLong(f -> f.toFile().length()).sum();
		}
	}

	static void copyTree(Path src, Path dst) throws IOException {
		if (Files.isRegularFile(src)) {
			Files.copy(src, dst);
			return;
		}
		try (Stream<Path> s = Files.walk(src)) {
			for (Path p : s.toList()) {
				Path t = dst.resolve(src.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(t);
				else if (!Files.isSymbolicLink(p)) Files.copy(p, t);
			}
		}
	}
}
