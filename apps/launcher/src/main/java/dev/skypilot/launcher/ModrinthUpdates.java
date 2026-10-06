package dev.skypilot.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Check for updates": asks Modrinth (POST /v2/version_files/update) which newer version exists
 * for each mod file, by the file's SHA-1, for Fabric and this Minecraft version. Shows a link to
 * the version page only - nothing is downloaded. A 429 is not retried; answers are kept for an
 * hour.
 */
public final class ModrinthUpdates {
	static final String URL = "https://api.modrinth.com/v2/version_files/update";

	public record Update(String sha1, boolean upToDate, String versionNumber, String name, String url, String published) {
	}

	@FunctionalInterface
	public interface Poster {
		byte[] post(String url, String json) throws IOException;
	}

	private record Cached(long at, Map<String, Update> result) {
	}

	private static final Map<String, Cached> CACHE = new HashMap<>();
	private static final Gson GSON = new Gson();

	private ModrinthUpdates() {
	}

	/** File → update info (files Modrinth doesn't know are missing from the map). */
	public static Map<Path, Update> check(List<Path> files, String gameVersion, Poster post) throws IOException {
		Map<String, Path> byHash = new LinkedHashMap<>();
		for (Path f : files) byHash.put(Net.sha1(f), f);
		if (byHash.isEmpty()) return Map.of();
		String key = gameVersion + "|" + String.join(",", byHash.keySet().stream().sorted().toList());
		Cached c;
		synchronized (CACHE) {
			c = CACHE.get(key);
		}
		Map<String, Update> result;
		if (c != null && System.currentTimeMillis() - c.at() < 3_600_000) {
			result = c.result();
		} else {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("hashes", byHash.keySet());
			body.put("algorithm", "sha1");
			body.put("loaders", List.of("fabric"));
			body.put("game_versions", List.of(gameVersion));
			byte[] answer = post.post(URL, GSON.toJson(body));
			result = answer == null ? Map.of() : parse(new String(answer, StandardCharsets.UTF_8));
			synchronized (CACHE) {
				CACHE.put(key, new Cached(System.currentTimeMillis(), result));
			}
		}
		Map<Path, Update> out = new LinkedHashMap<>();
		for (Map.Entry<String, Path> e : byHash.entrySet()) {
			Update u = result.get(e.getKey());
			if (u != null) out.put(e.getValue(), u);
		}
		return out;
	}

	/** Modrinth's answer: { "<sha1>": { id, project_id, version_number, name, date_published, files: [{hashes: {sha1}}] } }. */
	static Map<String, Update> parse(String json) {
		Map<String, Update> out = new HashMap<>();
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject()) return out;
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			JsonObject v = e.getValue().getAsJsonObject();
			String project = str(v, "project_id");
			String id = str(v, "id");
			if (project.isEmpty() || id.isEmpty() || !project.matches("[A-Za-z0-9]+") || !id.matches("[A-Za-z0-9]+")) continue;
			String hash = e.getKey().toLowerCase(Locale.ROOT);
			boolean same = false;
			if (v.has("files") && v.get("files").isJsonArray()) {
				for (JsonElement f : v.getAsJsonArray("files")) {
					if (f.isJsonObject() && f.getAsJsonObject().has("hashes") && hash.equalsIgnoreCase(str(f.getAsJsonObject().getAsJsonObject("hashes"), "sha1"))) same = true;
				}
			}
			out.put(hash, new Update(hash, same, str(v, "version_number"), str(v, "name"), "https://modrinth.com/project/" + project + "/version/" + id, str(v, "date_published")));
		}
		return out;
	}

	private static String str(JsonObject o, String key) {
		return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}
}
