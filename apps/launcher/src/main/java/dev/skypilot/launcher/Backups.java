package dev.skypilot.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backups of the SkyPilot game folder's settings: every mod's config, options.txt and the server
 * list, as a zip in the launcher's folder. Restoring first backs up the current state, and only
 * writes inside the game folder.
 */
public final class Backups {
	private static final String[] FILES = {"options.txt", "servers.dat"};
	private static final long MAX_ENTRY = 64L << 20;

	private Backups() {
	}

	public static Path dir(Path launcherDir) {
		return launcherDir.resolve("backups");
	}

	public static Path create(Path launcherDir, Path gameDir, String label) throws IOException {
		Files.createDirectories(dir(launcherDir));
		String name = "skypilot-" + Importer.stamp() + (label == null ? "" : "-" + label) + ".zip";
		Path zipFile = dir(launcherDir).resolve(name);
		Path tmp = zipFile.resolveSibling(name + ".part");
		try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (String f : FILES) {
				Path p = gameDir.resolve(f);
				if (Files.isRegularFile(p)) add(zip, p, f);
			}
			Path config = gameDir.resolve("config");
			if (Files.isDirectory(config)) {
				try (Stream<Path> s = Files.walk(config)) {
					for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
						add(zip, p, "config/" + config.relativize(p).toString().replace('\\', '/'));
					}
				}
			}
		}
		Files.move(tmp, zipFile, StandardCopyOption.REPLACE_EXISTING);
		return zipFile;
	}

	private static void add(ZipOutputStream zip, Path file, String name) throws IOException {
		if (Files.size(file) > MAX_ENTRY) return;
		zip.putNextEntry(new ZipEntry(name));
		Files.copy(file, zip);
		zip.closeEntry();
	}

	/** Newest first. */
	public static List<Path> list(Path launcherDir) {
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(dir(launcherDir))) return out;
		try (Stream<Path> s = Files.list(dir(launcherDir))) {
			s.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).forEach(out::add);
		} catch (IOException e) {
			return out;
		}
		return out;
	}

	/** Put a backup back (the current settings are backed up first); returns the number of files written. */
	public static int restore(Path launcherDir, Path gameDir, Path backup) throws IOException {
		create(launcherDir, gameDir, "before-restore");
		Path root = gameDir.toAbsolutePath().normalize();
		int n = 0;
		try (InputStream in = Files.newInputStream(backup); ZipInputStream zip = new ZipInputStream(in)) {
			ZipEntry e;
			while ((e = zip.getNextEntry()) != null) {
				if (e.isDirectory()) continue;
				String name = e.getName();
				boolean allowed = name.equals("options.txt") || name.equals("servers.dat") || name.startsWith("config/");
				Path target = root.resolve(name).normalize();
				if (!allowed || !target.startsWith(root) || name.contains("..")) throw new IOException("unexpected entry in backup: " + name);
				Files.createDirectories(target.getParent());
				Path tmp = target.resolveSibling(target.getFileName() + ".skypilot-tmp");
				try (OutputStream out = Files.newOutputStream(tmp)) {
					byte[] buf = new byte[65536];
					long total = 0;
					int r;
					while ((r = zip.read(buf)) > 0) {
						total += r;
						if (total > MAX_ENTRY) throw new IOException("entry too large: " + name);
						out.write(buf, 0, r);
					}
				}
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				n++;
			}
		}
		return n;
	}
}
