package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skypilot.mod.core.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Everything runs in temporary folders - never in the real .minecraft. */
public class LauncherTest {
	static final String META = "{\"id\":\"fabric-loader-0.19.5-26.2\",\"inheritsFrom\":\"26.2\",\"type\":\"release\",\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\","
			+ "\"arguments\":{\"game\":[],\"jvm\":[]},\"libraries\":[{\"name\":\"net.fabricmc:fabric-loader:0.19.5\",\"url\":\"https://maven.fabricmc.net/\"}]}";
	static final String PROFILES = "{\"profiles\":{\"abc123\":{\"name\":\"My vanilla\",\"type\":\"custom\",\"lastVersionId\":\"26.2\"},"
			+ "\"latest\":{\"type\":\"latest-release\",\"lastVersionId\":\"latest-release\"}},\"settings\":{\"crashAssistance\":true},\"version\":3}";

	public static byte[] jar(String id, String version) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			if (id != null) {
				zip.putNextEntry(new ZipEntry("fabric.mod.json"));
				zip.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"name\":\"" + id + " mod\"}").getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
			zip.putNextEntry(new ZipEntry("x/Placeholder.class"));
			zip.write(new byte[] {1, 2, 3});
			zip.closeEntry();
		}
		return out.toByteArray();
	}

	static Pins pins(byte[] mod, byte[] api) {
		return new Pins("26.2", "0.19.5", "0.1.0", "skypilot-0.1.0.jar", Net.sha256(mod), "0.161.0+26.2",
				"https://cdn.modrinth.com/data/P7dR8mSH/versions/ewUK83HI/fabric-api-0.161.0%2B26.2.jar", Net.sha512(api), api.length);
	}

	static Path write(Path p, byte[] data) throws IOException {
		Files.createDirectories(p.getParent());
		return Files.write(p, data);
	}

	static List<String> names(Path dir) throws IOException {
		try (var s = Files.list(dir)) {
			return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
		}
	}

	// ---------------------------------------------------------------- pins (generated at build time)

	@Test
	void bundledPinsMatchTheBuild() throws IOException {
		Pins p = Pins.load();
		assertEquals("fabric-loader-0.19.5-26.2", p.versionId());
		assertEquals("https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.5/profile/json", p.fabricMetaUrl());
		assertTrue(Net.HOSTS.contains(java.net.URI.create(p.fabricApiUrl()).getHost()));
		assertEquals(128, p.fabricApiSha512().length());
		assertTrue(p.fabricApiSize() > 1_000_000);
		byte[] mod = Pins.bundledMod();
		assertEquals(p.modSha256(), Net.sha256(mod));
		assertEquals("skypilot", Mods.modJson(write(Files.createTempFile("bundled", ".jar"), mod)).get("id").getAsString());
	}

	// ---------------------------------------------------------------- Fabric version file

	@Test
	void installsTheFabricVersionFile(@TempDir Path mc) throws IOException {
		Pins p = pins(new byte[1], new byte[1]);
		write(mc.resolve("versions/fabric-loader-0.19.5-26.2/fabric-loader-0.19.5-26.2.jar"), new byte[0]);
		List<String> asked = new ArrayList<>();
		FabricInstaller.install(mc, p, url -> {
			asked.add(url);
			return META.getBytes(StandardCharsets.UTF_8);
		});
		assertEquals(List.of(p.fabricMetaUrl()), asked);
		assertTrue(FabricInstaller.installed(mc, p));
		assertEquals(List.of("fabric-loader-0.19.5-26.2.json"), names(mc.resolve("versions/fabric-loader-0.19.5-26.2")));
		JsonObject json = JsonParser.parseString(Files.readString(FabricInstaller.versionFile(mc, p))).getAsJsonObject();
		assertEquals("26.2", json.get("inheritsFrom").getAsString());
	}

	@Test
	void rejectsUnexpectedFabricMetadata(@TempDir Path mc) {
		Pins p = pins(new byte[1], new byte[1]);
		assertThrows(IOException.class, () -> FabricInstaller.install(mc, p, url -> META.replace("0.19.5-26.2", "0.18.0-26.1").getBytes(StandardCharsets.UTF_8)));
		assertThrows(IOException.class, () -> FabricInstaller.install(mc, p, url -> META.replace("\"inheritsFrom\":\"26.2\"", "\"inheritsFrom\":\"1.8.9\"").getBytes(StandardCharsets.UTF_8)));
		assertThrows(IOException.class, () -> FabricInstaller.install(mc, p, url -> "<html>not json".getBytes(StandardCharsets.UTF_8)));
		assertFalse(FabricInstaller.installed(mc, p));
		assertFalse(Files.exists(mc.resolve("versions")));
	}

	// ---------------------------------------------------------------- launcher_profiles.json

	@Test
	void addsTheProfileAndKeepsEverythingElse(@TempDir Path mc) throws IOException {
		Path file = write(mc.resolve("launcher_profiles.json"), PROFILES.getBytes(StandardCharsets.UTF_8));
		Path game = mc.resolve("skypilot");
		assertEquals(List.of(file), LauncherProfiles.files(mc));
		assertFalse(LauncherProfiles.upToDate(file, "fabric-loader-0.19.5-26.2", game));

		LauncherProfiles.upsert(file, "fabric-loader-0.19.5-26.2", game, 6144, "data:image/png;base64,AAAA");
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals("My vanilla", root.getAsJsonObject("profiles").getAsJsonObject("abc123").get("name").getAsString());
		assertTrue(root.getAsJsonObject("profiles").has("latest"));
		assertTrue(root.getAsJsonObject("settings").get("crashAssistance").getAsBoolean());
		assertEquals(3, root.get("version").getAsInt());
		JsonObject sp = LauncherProfiles.read(file);
		assertEquals(LauncherProfiles.NAME, sp.get("name").getAsString());
		assertEquals("custom", sp.get("type").getAsString());
		assertEquals("fabric-loader-0.19.5-26.2", sp.get("lastVersionId").getAsString());
		assertEquals("-Xmx6144M -Xms1024M", sp.get("javaArgs").getAsString());
		assertTrue(LauncherProfiles.upToDate(file, "fabric-loader-0.19.5-26.2", game));
		assertFalse(LauncherProfiles.upToDate(file, "fabric-loader-0.19.5-26.2", mc.resolve("other")));
		assertEquals(PROFILES, Files.readString(mc.resolve("launcher_profiles.json.skypilot-backup")));

		// A second run updates the same entry, keeps "created" and never overwrites the backup.
		String created = sp.get("created").getAsString();
		LauncherProfiles.upsert(file, "fabric-loader-0.19.5-26.2", game, 4096, null);
		JsonObject again = LauncherProfiles.read(file);
		assertEquals(created, again.get("created").getAsString());
		assertEquals("-Xmx4096M -Xms1024M", again.get("javaArgs").getAsString());
		assertEquals("data:image/png;base64,AAAA", again.get("icon").getAsString());
		assertEquals(PROFILES, Files.readString(mc.resolve("launcher_profiles.json.skypilot-backup")));
		assertEquals(3, JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("profiles").size());
	}

	@Test
	void findsTheStoreLauncherProfileFile(@TempDir Path mc) throws IOException {
		assertTrue(LauncherProfiles.files(mc).isEmpty());
		write(mc.resolve("launcher_profiles_microsoft_store.json"), "{}".getBytes(StandardCharsets.UTF_8));
		assertEquals(1, LauncherProfiles.files(mc).size());
		LauncherProfiles.upsert(LauncherProfiles.files(mc).get(0), "fabric-loader-0.19.5-26.2", mc.resolve("sp"), 4096, null);
		assertTrue(LauncherProfiles.upToDate(mc.resolve("launcher_profiles_microsoft_store.json"), "fabric-loader-0.19.5-26.2", mc.resolve("sp")));
	}

	// ---------------------------------------------------------------- mods

	@Test
	void installsTheBundledModOnlyWhenItsHashMatches(@TempDir Path dir) throws IOException {
		byte[] mod = jar("skypilot", "0.1.0");
		Pins p = pins(mod, new byte[1]);
		Path mods = dir.resolve("mods");
		write(mods.resolve("skypilot-0.0.9.jar"), jar("skypilot", "0.0.9"));
		write(mods.resolve("other.jar"), jar("other", "1.0"));

		assertThrows(IOException.class, () -> Mods.installSkyPilot(mods, p, jar("skypilot", "evil")));
		assertFalse(Mods.skyPilotInstalled(mods, p));

		Mods.installSkyPilot(mods, p, mod);
		assertTrue(Mods.skyPilotInstalled(mods, p));
		// The older copy is switched off, not deleted; other mods are untouched.
		assertEquals(List.of("other.jar", "skypilot-0.0.9.jar.disabled", "skypilot-0.1.0.jar"), names(mods));
		assertFalse(Mods.skyPilotInstalled(dir.resolve("nowhere"), p));
	}

	@Test
	void installsFabricApiOnlyWithThePinnedHash(@TempDir Path dir) throws IOException {
		byte[] api = jar("fabric-api", "0.161.0+26.2");
		Pins p = pins(new byte[1], api);
		Path mods = dir.resolve("mods");
		write(mods.resolve("fabric-api-0.150.0.jar"), jar("fabric-api", "0.150.0"));

		List<String> asked = new ArrayList<>();
		assertThrows(IOException.class, () -> Mods.installFabricApi(mods, p, url -> jar("fabric-api", "tampered")));
		assertEquals(List.of("fabric-api-0.150.0.jar"), names(mods));
		assertFalse(Mods.fabricApiInstalled(mods, p));

		Mods.installFabricApi(mods, p, url -> {
			asked.add(url);
			return api;
		});
		assertEquals(List.of(p.fabricApiUrl()), asked);
		assertTrue(Mods.fabricApiInstalled(mods, p));
		assertEquals(List.of("fabric-api-0.150.0.jar.disabled", "fabric-api-0.161.0+26.2.jar"), names(mods));

		// Already there: nothing is downloaded again.
		Mods.installFabricApi(mods, p, url -> {
			throw new AssertionError("downloaded again");
		});
	}

	@Test
	void listsTogglesAndAddsMods(@TempDir Path dir) throws IOException {
		Path mods = dir.resolve("mods");
		Path a = write(mods.resolve("a.jar"), jar("alpha", "1.2"));
		write(mods.resolve("notes.txt"), new byte[1]);
		List<Mods.ModFile> list = Mods.list(mods);
		assertEquals(1, list.size());
		assertEquals("alpha", list.get(0).id());
		assertEquals("1.2", list.get(0).version());

		Path off = Mods.toggle(list.get(0));
		assertEquals("a.jar.disabled", off.getFileName().toString());
		assertFalse(Mods.list(mods).get(0).enabled());
		Path on = Mods.toggle(Mods.list(mods).get(0));
		assertEquals(a, on);

		Path picked = write(dir.resolve("picked/beta.jar"), jar("beta", "2.0"));
		Mods.add(mods, picked);
		assertEquals(List.of("a.jar", "beta.jar", "notes.txt"), names(mods));
		assertThrows(IOException.class, () -> Mods.add(mods, picked));
		Path notMod = write(dir.resolve("picked/plain.jar"), jar(null, null));
		assertThrows(IOException.class, () -> Mods.add(mods, notMod));
		Path notJar = write(dir.resolve("picked/x.zip"), jar("x", "1"));
		assertThrows(IOException.class, () -> Mods.add(mods, notJar));
		assertTrue(Mods.list(dir.resolve("missing")).isEmpty());
	}

	// ---------------------------------------------------------------- network rules

	@Test
	void downloadsOnlyOverHttpsFromTheTwoHosts() {
		assertThrows(IOException.class, () -> Net.download("http://meta.fabricmc.net/v2/versions", 1000));
		assertThrows(IOException.class, () -> Net.download("https://example.com/fabric-api.jar", 1000));
		assertThrows(IOException.class, () -> Net.download("https://cdn.modrinth.com.evil.test/x.jar", 1000));
		assertThrows(IOException.class, () -> Net.download("file:///C:/Windows/win.ini", 1000));
		assertEquals(Map.of(), Map.of()); // no request was made for any of these
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Net.sha256("abc".getBytes(StandardCharsets.UTF_8)));
	}

	// ---------------------------------------------------------------- SkyPilot game folder

	@Test
	void serverUrlRulesAndLinkState(@TempDir Path inst) throws IOException {
		assertTrue(Instance.validServerUrl("https://skypilot.example"));
		assertTrue(Instance.validServerUrl("http://localhost:3000"));
		assertTrue(Instance.validServerUrl("http://127.0.0.1:3000"));
		assertFalse(Instance.validServerUrl("http://skypilot.example"));
		assertFalse(Instance.validServerUrl("https://user:pw@skypilot.example"));
		assertFalse(Instance.validServerUrl("https://skypilot.example/?token=x"));
		assertFalse(Instance.validServerUrl("javascript:alert(1)"));
		assertFalse(Instance.validServerUrl("not a url"));

		Instance.setServer(inst, "https://skypilot.example/");
		assertEquals("https://skypilot.example", Instance.config(inst).apiBaseUrl);
		assertThrows(IOException.class, () -> Instance.setServer(inst, "http://skypilot.example"));
		assertEquals("https://skypilot.example", Instance.config(inst).apiBaseUrl);

		assertFalse(Instance.linked(inst));
		write(Instance.configDir(inst).resolve("token.json"), "{\"accessToken\":\"x\"}".getBytes(StandardCharsets.UTF_8));
		assertTrue(Instance.linked(inst));
	}

	@Test
	void readsLogsAndCrashReports(@TempDir Path inst) throws IOException {
		assertTrue(Instance.latestLog(inst, 10).isEmpty());
		StringBuilder log = new StringBuilder();
		for (int i = 1; i <= 50; i++) log.append("line ").append(i).append('\n');
		write(inst.resolve("logs/latest.log"), log.toString().getBytes(StandardCharsets.UTF_8));
		assertEquals(List.of("line 49", "line 50"), Instance.latestLog(inst, 2));
		write(inst.resolve("crash-reports/crash-1.txt"), new byte[1]).toFile().setLastModified(1_000_000L);
		write(inst.resolve("crash-reports/crash-2.txt"), new byte[1]).toFile().setLastModified(2_000_000L);
		write(inst.resolve("crash-reports/readme.md"), new byte[1]);
		assertEquals(List.of("crash-2.txt", "crash-1.txt"), Instance.crashReports(inst).stream().map(p -> p.getFileName().toString()).toList());
	}

	@Test
	void settingsRoundTripAndLimits(@TempDir Path dir) throws IOException {
		LauncherSettings s = new LauncherSettings();
		s.minecraftDir = dir.resolve("mc").toString();
		s.memoryMb = 999_999;
		s.language = "fr";
		s.save(dir);
		LauncherSettings back = LauncherSettings.load(dir);
		assertEquals(32768, back.memoryMb);
		assertEquals("auto", back.language);
		assertEquals(dir.resolve("mc"), back.minecraft());
		assertEquals(dir.resolve("mc/skypilot"), back.instance());
		assertEquals(dir.resolve("mc/skypilot/mods"), back.mods());
		Files.writeString(dir.resolve("launcher.json"), "{broken");
		assertNull(LauncherSettings.load(dir).minecraftDir);
	}

	// ---------------------------------------------------------------- the whole setup

	@Test
	void setupNeedsTheOfficialLauncherFirst(@TempDir Path dir) {
		LauncherSettings s = new LauncherSettings();
		s.minecraftDir = dir.resolve("mc").toString();
		Pins p = Pins.load();
		IOException e = assertThrows(IOException.class, () -> Setup.run(s, p, Lang.EN, url -> {
			throw new AssertionError("nothing may be downloaded before the launcher check");
		}, msg -> {
		}));
		assertTrue(e.getMessage().contains("official Minecraft Launcher"));
		assertFalse(Files.exists(dir.resolve("mc/versions")));
	}

	@Test
	void setupPreparesEverything(@TempDir Path dir) throws IOException {
		byte[] api = jar("fabric-api", "0.161.0+26.2");
		Pins real = Pins.load();
		Pins p = new Pins(real.minecraft(), real.loader(), real.modVersion(), real.modFile(), real.modSha256(), real.fabricApi(), real.fabricApiUrl(), Net.sha512(api), api.length);
		LauncherSettings s = new LauncherSettings();
		s.minecraftDir = dir.resolve("mc").toString();
		write(dir.resolve("mc/launcher_profiles.json"), PROFILES.getBytes(StandardCharsets.UTF_8));

		List<String> before = Setup.status(s, p, Lang.EN, false).stream().filter(Setup.Check::ok).map(Setup.Check::id).toList();
		assertEquals(List.of("launcher"), before);

		List<String> progress = new ArrayList<>();
		List<String> asked = new ArrayList<>();
		Setup.run(s, p, Lang.EN, url -> {
			asked.add(url);
			return url.equals(p.fabricMetaUrl()) ? META.replace("0.19.5", real.loader()).replace("26.2", real.minecraft()).getBytes(StandardCharsets.UTF_8) : api;
		}, progress::add);
		assertEquals(List.of(p.fabricMetaUrl(), p.fabricApiUrl()), asked);
		assertTrue(progress.get(progress.size() - 1).startsWith("Done."));

		List<String> ok = Setup.status(s, p, Lang.EN, false).stream().filter(Setup.Check::ok).map(Setup.Check::id).toList();
		assertEquals(List.of("launcher", "fabric", "profile", "mod", "fabric-api"), ok);
		assertArrayEquals(Pins.bundledMod(), Files.readAllBytes(s.mods().resolve(p.modFile())));
		JsonObject profile = LauncherProfiles.read(dir.resolve("mc/launcher_profiles.json"));
		assertTrue(profile.get("icon").getAsString().startsWith("data:image/png;base64,"));
		assertEquals(dir.resolve("mc/skypilot").toAbsolutePath().toString(), profile.get("gameDir").getAsString());
		// No account data anywhere in what the launcher wrote.
		String all = Files.readString(dir.resolve("mc/launcher_profiles.json")).toLowerCase(java.util.Locale.ROOT);
		assertFalse(all.contains("accesstoken") || all.contains("refresh") || all.contains("password"));
	}
}
