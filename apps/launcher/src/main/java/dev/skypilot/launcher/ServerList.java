package dev.skypilot.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The game folder's multiplayer server list ({@code servers.dat}: a "servers" list of compounds
 * with "name" and "ip", as written by 26.2's {@code ServerData.write}). The launcher only adds
 * entries; it never connects anywhere and never removes or edits the player's servers.
 */
public final class ServerList {
	public static final String HYPIXEL_NAME = "Hypixel";
	public static final String HYPIXEL_IP = "mc.hypixel.net";

	public record Server(String name, String ip) {
	}

	private ServerList() {
	}

	static Path file(Path gameDir) {
		return gameDir.resolve("servers.dat");
	}

	public static List<Server> servers(Path gameDir) {
		List<Server> out = new ArrayList<>();
		try {
			for (Object o : list(read(gameDir)).items()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>) o;
				out.add(new Server(str(m, "name"), str(m, "ip")));
			}
		} catch (IOException e) {
			// no or unreadable list: empty
		}
		return out;
	}

	private static String str(Map<String, Object> m, String key) {
		Object t = m.get(key);
		return t instanceof Nbt.Tagged tagged && tagged.value() instanceof String s ? s : "";
	}

	private static Map<String, Object> read(Path gameDir) throws IOException {
		Path f = file(gameDir);
		if (!Files.isRegularFile(f)) return new LinkedHashMap<>();
		return Nbt.read(new ByteArrayInputStream(Files.readAllBytes(f)));
	}

	private static Nbt.ListTag list(Map<String, Object> root) {
		Object t = root.get("servers");
		if (t instanceof Nbt.Tagged tagged && tagged.value() instanceof Nbt.ListTag l && (l.type() == 10 || l.items().isEmpty())) return l;
		return new Nbt.ListTag((byte) 10, new ArrayList<>());
	}

	/** Add servers that are not in the list yet (same address, ignoring case); returns how many were added. */
	public static int add(Path gameDir, List<Server> servers) throws IOException {
		Map<String, Object> root = read(gameDir);
		Nbt.ListTag old = list(root);
		List<Object> items = new ArrayList<>(old.items());
		List<String> known = new ArrayList<>();
		for (Object o : items) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) o;
			known.add(str(m, "ip").toLowerCase(Locale.ROOT));
		}
		int added = 0;
		for (Server s : servers) {
			if (s.ip().isBlank() || known.contains(s.ip().toLowerCase(Locale.ROOT))) continue;
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("name", Nbt.string(s.name()));
			entry.put("ip", Nbt.string(s.ip()));
			items.add(entry);
			known.add(s.ip().toLowerCase(Locale.ROOT));
			added++;
		}
		if (added == 0) return 0;
		root.put("servers", new Nbt.Tagged((byte) 9, new Nbt.ListTag((byte) 10, items)));
		Files.createDirectories(gameDir);
		Path f = file(gameDir);
		if (Files.isRegularFile(f)) {
			Path backup = f.resolveSibling("servers.dat.skypilot-backup");
			if (!Files.exists(backup)) Files.copy(f, backup);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Nbt.write(root, out);
		Path tmp = f.resolveSibling("servers.dat.skypilot-tmp");
		Files.write(tmp, out.toByteArray());
		Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
		return added;
	}

	public static boolean addHypixel(Path gameDir) throws IOException {
		return add(gameDir, List.of(new Server(HYPIXEL_NAME, HYPIXEL_IP))) > 0;
	}

	public static boolean hasHypixel(Path gameDir) {
		return servers(gameDir).stream().anyMatch(s -> s.ip().toLowerCase(Locale.ROOT).replaceAll(":25565$", "").endsWith("hypixel.net"));
	}
}
