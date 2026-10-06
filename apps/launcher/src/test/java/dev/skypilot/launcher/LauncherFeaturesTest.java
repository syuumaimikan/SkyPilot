package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skypilot.mod.core.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The launcher's mod, profile, file and crash tools - temporary folders only. */
public class LauncherFeaturesTest {
	/** A jar with the given entries. */
	public static byte[] zip(Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(out)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				z.putNextEntry(new ZipEntry(e.getKey()));
				z.write(e.getValue());
				z.closeEntry();
			}
		}
		return out.toByteArray();
	}

	public static byte[] mod(String fabricModJson, Map<String, byte[]> extra) throws IOException {
		Map<String, byte[]> m = new LinkedHashMap<>();
		m.put("fabric.mod.json", fabricModJson.getBytes(StandardCharsets.UTF_8));
		m.putAll(extra);
		return zip(m);
	}

	static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	static final Map<String, String> BUILTINS = Map.of("minecraft", "26.2", "fabricloader", "0.19.5", "java", "25");

	// ---------------------------------------------------------------- Fabric version ranges

	@Test
	void fabricVersionRangesFollowFabricLoader() {
		assertTrue(FabricVersion.matches(">=0.19", "0.19.5"));
		assertTrue(FabricVersion.matches("~26.2", "26.2"));
		assertTrue(FabricVersion.matches("~26.2", "26.2.1"));
		assertFalse(FabricVersion.matches("~26.2", "26.3"));
		assertTrue(FabricVersion.matches("26.2.x", "26.2.1"));
		assertFalse(FabricVersion.matches("26.2.x", "26.3"));
		assertTrue(FabricVersion.matches("26.x", "26.5"));
		assertFalse(FabricVersion.matches("1.21.x", "26.2"));
		assertTrue(FabricVersion.matches("*", "anything"));
		assertTrue(FabricVersion.matches("", "1.0"));
		assertTrue(FabricVersion.matches(">=26.2 <26.3", "26.2.1"));
		assertFalse(FabricVersion.matches(">=26.2 <26.3", "26.3"));
		assertTrue(FabricVersion.matches("^1.2.0", "1.9"));
		assertFalse(FabricVersion.matches("^1.2.0", "2.0"));
		assertTrue(FabricVersion.matches("=1.0", "1.0.0"));
		assertTrue(FabricVersion.matches("1.0.0", "1.0.0+build.7"));
		// a pre-release is below its release; the ".x" form includes pre-releases
		assertFalse(FabricVersion.matches(">=1.0.0", "1.0.0-beta.1"));
		assertTrue(FabricVersion.matches(">=1.0.0-beta", "1.0.0"));
		assertTrue(FabricVersion.matches("1.0.x", "1.0.0-rc.1"));
		assertTrue(FabricVersion.parse("1.0.0-alpha.2").compareTo(FabricVersion.parse("1.0.0-alpha.10")) < 0);
		assertTrue(FabricVersion.parse("1.0.0-alpha").compareTo(FabricVersion.parse("1.0.0-alpha.1")) < 0);
		// text versions only compare equal
		assertTrue(FabricVersion.matches("abc", "abc"));
		assertFalse(FabricVersion.matches(">=abc", "abd"));
		assertTrue(FabricVersion.matches(">abc", "abd"), "a range Fabric cannot read is left to Fabric");
		assertTrue(FabricVersion.matchesAny(List.of("1.21.x", "26.2.x"), "26.2"));
		assertFalse(FabricVersion.matchesAny(List.of("1.21.x", "1.20.x"), "26.2"));
	}

	// ---------------------------------------------------------------- mod scan and compatibility

	private static Path write(Path p, byte[] data) throws IOException {
		Files.createDirectories(p.getParent());
		return Files.write(p, data);
	}

	private static byte[] png() throws IOException {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		javax.imageio.ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	public static Path modsFolder(Path dir) throws IOException {
		Path mods = dir.resolve("mods");
		byte[] base = mod("{\"schemaVersion\":1,\"id\":\"fabric-api-base\",\"version\":\"1.0.5\"}", Map.of());
		write(mods.resolve("fabric-api.jar"), mod("{\"schemaVersion\":1,\"id\":\"fabric-api\",\"version\":\"0.161.0+26.2\",\"name\":\"Fabric API\",\"jars\":[{\"file\":\"META-INF/jars/base.jar\"}]}",
				Map.of("META-INF/jars/base.jar", base)));
		write(mods.resolve("skypilot.jar"), mod("{\"schemaVersion\":1,\"id\":\"skypilot\",\"version\":\"0.1.0\",\"name\":\"SkyPilot\",\"icon\":\"assets/skypilot/icon.png\","
				+ "\"depends\":{\"minecraft\":\"~26.2\",\"fabricloader\":\">=0.19.5\",\"fabric-api-base\":\"*\",\"java\":\">=25\"},\"contact\":{\"homepage\":\"https://example.com\",\"sources\":\"javascript:alert(1)\"},"
				+ "\"entrypoints\":{\"client\":[\"dev.example.sky.Client\"]},\"mixins\":[\"skypilot.mixins.json\"]}",
				Map.of("assets/skypilot/icon.png", png(), "skypilot.mixins.json", utf8("{\"package\":\"dev.example.sky.mixin\"}"))));
		write(mods.resolve("old.jar"), mod("{\"schemaVersion\":1,\"id\":\"oldmod\",\"version\":\"2.0\",\"name\":\"Old Mod\",\"depends\":{\"minecraft\":\"1.21.x\"}}", Map.of()));
		write(mods.resolve("needy.jar"), mod("{\"schemaVersion\":1,\"id\":\"needy\",\"version\":\"1.0\",\"name\":\"Needy\",\"depends\":{\"cloth-config\":\">=15\"},\"recommends\":{\"modmenu\":\"*\"},"
				+ "\"entrypoints\":{\"main\":[{\"value\":\"com.example.needy.Main\"}]},\"mixins\":[{\"config\":\"needy.mixins.json\",\"environment\":\"client\"}]}",
				Map.of("needy.mixins.json", utf8("{\"package\":\"com.example.needy.mixin\"}"))));
		write(mods.resolve("breaker.jar"), mod("{\"schemaVersion\":1,\"id\":\"breaker\",\"version\":\"1.0\",\"breaks\":{\"skypilot\":\"<1.0\"}}", Map.of()));
		write(mods.resolve("dup-a.jar"), mod("{\"schemaVersion\":1,\"id\":\"dup\",\"version\":\"1.0\"}", Map.of()));
		write(mods.resolve("dup-b.jar"), mod("{\"schemaVersion\":1,\"id\":\"dup\",\"version\":\"1.1\"}", Map.of()));
		write(mods.resolve("server.jar"), mod("{\"schemaVersion\":1,\"id\":\"serveronly\",\"version\":\"1.0\",\"environment\":\"server\"}", Map.of()));
		write(mods.resolve("forge.jar"), zip(Map.of("META-INF/mods.toml", utf8("modLoader=\"javafml\""))));
		write(mods.resolve("ignored.jar.disabled"), mod("{\"schemaVersion\":1,\"id\":\"ignored\",\"version\":\"1.0\",\"depends\":{\"nothing\":\"*\"}}", Map.of()));
		return mods;
	}

	@Test
	void scansModMetadataIconsAndNestedMods(@TempDir Path dir) throws IOException {
		List<ModScan.Meta> mods = ModScan.scan(modsFolder(dir));
		assertEquals(10, mods.size());
		ModScan.Meta api = mods.stream().filter(m -> m.id().equals("fabric-api")).findFirst().orElseThrow();
		assertEquals("1.0.5", api.provides().get("fabric-api-base"));
		ModScan.Meta sky = mods.stream().filter(m -> m.id().equals("skypilot")).findFirst().orElseThrow();
		assertNotNull(sky.icon());
		assertEquals("https://example.com", sky.links().get("homepage"));
		assertFalse(sky.links().containsKey("sources"), "only http(s) links are kept");
		assertTrue(sky.packages().contains("dev.example.sky"));
		assertTrue(sky.packages().contains("dev.example.sky.mixin"));
		ModScan.Meta needy = mods.stream().filter(m -> m.id().equals("needy")).findFirst().orElseThrow();
		assertEquals(List.of("needy.mixins.json"), needy.mixinConfigs());
		ModScan.Meta forge = mods.stream().filter(m -> m.file().getFileName().toString().equals("forge.jar")).findFirst().orElseThrow();
		assertFalse(forge.fabric());
		assertFalse(mods.stream().filter(m -> m.id().equals("ignored")).findFirst().orElseThrow().enabled());
	}

	@Test
	void findsTheProblemsFabricWouldStopAt(@TempDir Path dir) throws IOException {
		List<ModScan.Meta> mods = ModScan.scan(modsFolder(dir));
		List<ModCheck.Issue> issues = ModCheck.check(mods, BUILTINS, Lang.EN);
		java.util.function.Function<String, List<ModCheck.Issue>> of = file -> issues.stream().filter(i -> i.file().getFileName().toString().equals(file)).toList();
		assertTrue(of.apply("skypilot.jar").stream().noneMatch(i -> i.level() == ModCheck.Level.ERROR), "skypilot's needs are all met (incl. nested fabric-api-base): " + of.apply("skypilot.jar"));
		assertTrue(of.apply("old.jar").stream().anyMatch(i -> i.level() == ModCheck.Level.ERROR && i.text().contains("Minecraft 1.21.x")));
		assertTrue(of.apply("needy.jar").stream().anyMatch(i -> i.level() == ModCheck.Level.ERROR && i.text().contains("cloth-config")));
		assertTrue(of.apply("needy.jar").stream().anyMatch(i -> i.level() == ModCheck.Level.INFO && i.text().contains("modmenu")));
		assertTrue(of.apply("breaker.jar").stream().anyMatch(i -> i.level() == ModCheck.Level.ERROR));
		assertEquals(1, of.apply("dup-a.jar").size());
		assertEquals(ModCheck.Level.WARNING, of.apply("dup-b.jar").get(0).level());
		assertEquals(ModCheck.Level.WARNING, of.apply("server.jar").get(0).level());
		assertEquals(ModCheck.Level.WARNING, of.apply("forge.jar").get(0).level());
		assertTrue(of.apply("ignored.jar.disabled").isEmpty(), "switched-off mods are not checked");
		assertEquals(ModCheck.Level.ERROR, ModCheck.worst(issues, dir.resolve("mods/old.jar")));
		// Java 25 is what the official launcher runs 26.x with; read from the version file when present
		assertEquals(25, ModCheck.javaVersion(dir, "26.2"));
		write(dir.resolve("versions/26.2/26.2.json"), utf8("{\"javaVersion\":{\"component\":\"java-runtime-epsilon\",\"majorVersion\":26}}"));
		assertEquals(26, ModCheck.javaVersion(dir, "26.2"));
	}

	@Test
	void presetsSwitchFilesWithoutDeletingThem(@TempDir Path dir) throws IOException {
		Path mods = modsFolder(dir);
		Path launcher = dir.resolve("launcher");
		Path inst = dir;
		Presets.save(launcher, inst, "all", Presets.capture(Mods.list(mods)));
		int off = Presets.safeMode(launcher, inst, mods);
		assertEquals(7, off);
		List<Mods.ModFile> after = Mods.list(mods);
		assertEquals(List.of("fabric-api", "skypilot"), after.stream().filter(Mods.ModFile::enabled).map(Mods.ModFile::id).sorted().toList());
		assertEquals(10, after.size(), "nothing deleted");
		Map<String, Presets.Preset> saved = Presets.load(launcher, inst);
		assertTrue(saved.containsKey(Presets.BEFORE_SAFE_MODE));
		assertEquals(7, Presets.apply(mods, saved.get(Presets.BEFORE_SAFE_MODE)));
		assertEquals(Presets.capture(Mods.list(mods)), saved.get("all"));
		Presets.delete(launcher, inst, "all");
		assertFalse(Presets.load(launcher, inst).containsKey("all"));
		assertTrue(Presets.load(launcher, dir.resolve("other")).isEmpty(), "presets belong to one game folder");
		assertThrows(IOException.class, () -> Presets.save(launcher, inst, " ", new Presets.Preset(List.of(), List.of())));
	}

	// ---------------------------------------------------------------- Modrinth update check

	@Test
	void modrinthUpdateCheckSendsHashesAndReadsLinks(@TempDir Path dir) throws IOException {
		Path a = write(dir.resolve("a.jar"), utf8("aaa"));
		Path b = write(dir.resolve("b.jar"), utf8("bbb"));
		String ha = Net.sha1(a);
		String hb = Net.sha1(b);
		List<String> bodies = new ArrayList<>();
		String answer = "{\"" + ha + "\":{\"id\":\"v2\",\"project_id\":\"P1\",\"version_number\":\"1.2.0\",\"name\":\"Mod 1.2.0\",\"files\":[{\"hashes\":{\"sha1\":\"" + "0".repeat(40) + "\"}}]},"
				+ "\"" + hb + "\":{\"id\":\"v9\",\"project_id\":\"P2\",\"version_number\":\"3.0\",\"files\":[{\"hashes\":{\"sha1\":\"" + hb.toUpperCase() + "\"}}]}}";
		Map<Path, ModrinthUpdates.Update> r = ModrinthUpdates.check(List.of(a, b), "26.2", (url, json) -> {
			bodies.add(url + " " + json);
			return utf8(answer);
		});
		assertEquals(1, bodies.size());
		assertTrue(bodies.get(0).startsWith("https://api.modrinth.com/v2/version_files/update "));
		JsonObject body = JsonParser.parseString(bodies.get(0).substring(bodies.get(0).indexOf(' ') + 1)).getAsJsonObject();
		assertEquals("sha1", body.get("algorithm").getAsString());
		assertEquals("fabric", body.getAsJsonArray("loaders").get(0).getAsString());
		assertEquals("26.2", body.getAsJsonArray("game_versions").get(0).getAsString());
		assertFalse(r.get(a).upToDate());
		assertEquals("https://modrinth.com/project/P1/version/v2", r.get(a).url());
		assertTrue(r.get(b).upToDate());
		// cached: no second request within the hour
		ModrinthUpdates.check(List.of(a, b), "26.2", (url, json) -> {
			throw new AssertionError("asked again");
		});
		assertTrue(ModrinthUpdates.parse("{\"x\":{\"id\":\"../evil\",\"project_id\":\"P\"}}").isEmpty(), "odd ids are not turned into links");
	}

	@Test
	void informationRequestsGoOnlyToKnownHosts() {
		assertThrows(IOException.class, () -> Net.getInfo("https://evil.example/x", 100));
		assertThrows(IOException.class, () -> Net.getInfo("http://api.modrinth.com/v2/x", 100));
		assertThrows(IOException.class, () -> Net.postInfo("https://cdn.modrinth.com/x", "{}", 100));
		assertThrows(IOException.class, () -> Net.download("https://api.modrinth.com/v2/x", 100), "installs never come from the API host");
	}

	// ---------------------------------------------------------------- servers.dat

	@Test
	void writesServersDatInMinecraftsNbtFormat() throws IOException {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("name", Nbt.string("A"));
		entry.put("ip", Nbt.string("b"));
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("servers", new Nbt.Tagged((byte) 9, new Nbt.ListTag((byte) 10, List.of(entry))));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Nbt.write(root, out);
		byte[] expected = {10, 0, 0, 9, 0, 7, 's', 'e', 'r', 'v', 'e', 'r', 's', 10, 0, 0, 0, 1, 8, 0, 4, 'n', 'a', 'm', 'e', 0, 1, 'A', 8, 0, 2, 'i', 'p', 0, 1, 'b', 0, 0};
		assertArrayEquals(expected, out.toByteArray());
		assertEquals(root.keySet(), Nbt.read(new ByteArrayInputStream(expected)).keySet());
		assertThrows(IOException.class, () -> Nbt.read(new ByteArrayInputStream(new byte[] {8, 0, 0})));
	}

	@Test
	void addsHypixelAndKeepsEveryOtherServer(@TempDir Path game) throws IOException {
		Map<String, Object> mine = new LinkedHashMap<>();
		mine.put("name", Nbt.string("My server"));
		mine.put("ip", Nbt.string("play.example.net"));
		mine.put("icon", Nbt.string("iVBORw0KGgo="));
		mine.put("acceptTextures", new Nbt.Tagged((byte) 1, (byte) 1));
		mine.put("acceptedCodeOfConduct", new Nbt.Tagged((byte) 3, 7));
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("servers", new Nbt.Tagged((byte) 9, new Nbt.ListTag((byte) 10, new ArrayList<>(List.of(mine)))));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Nbt.write(root, out);
		Files.write(game.resolve("servers.dat"), out.toByteArray());

		assertFalse(ServerList.hasHypixel(game));
		assertTrue(ServerList.addHypixel(game));
		assertFalse(ServerList.addHypixel(game), "not added twice");
		assertTrue(ServerList.hasHypixel(game));
		List<ServerList.Server> list = ServerList.servers(game);
		assertEquals(List.of(new ServerList.Server("My server", "play.example.net"), new ServerList.Server("Hypixel", "mc.hypixel.net")), list);
		Map<String, Object> back = Nbt.read(new ByteArrayInputStream(Files.readAllBytes(game.resolve("servers.dat"))));
		@SuppressWarnings("unchecked")
		Map<String, Object> first = (Map<String, Object>) ((Nbt.ListTag) ((Nbt.Tagged) back.get("servers")).value()).items().get(0);
		assertEquals((byte) 1, ((Nbt.Tagged) first.get("acceptTextures")).value());
		assertEquals(7, ((Nbt.Tagged) first.get("acceptedCodeOfConduct")).value());
		assertEquals("iVBORw0KGgo=", ((Nbt.Tagged) first.get("icon")).value());
		assertTrue(Files.exists(game.resolve("servers.dat.skypilot-backup")));
		// a fresh folder gets a new list
		Path empty = game.resolve("fresh");
		assertTrue(ServerList.addHypixel(empty));
		assertEquals(1, ServerList.servers(empty).size());
	}

	// ---------------------------------------------------------------- options, import, backups

	@Test
	void setsTheGameLanguageOnly(@TempDir Path game) throws IOException {
		Files.writeString(game.resolve("options.txt"), "version:4671\nlang:en_us\nfov:0.5\n");
		GameOptions.setLanguage(game, "ja_jp");
		assertEquals(List.of("version:4671", "lang:ja_jp", "fov:0.5"), Files.readAllLines(game.resolve("options.txt")));
		assertEquals("ja_jp", GameOptions.get(game, "lang"));
		assertThrows(IOException.class, () -> GameOptions.setLanguage(game, "xx\nfov:9"));
		Path fresh = game.resolve("new");
		GameOptions.setLanguage(fresh, "en_us");
		assertEquals("en_us", GameOptions.get(fresh, "lang"));
	}

	@Test
	void importsFromTheMainGameWithoutTouchingIt(@TempDir Path dir) throws IOException {
		Path main = dir.resolve("main");
		Path game = dir.resolve("main/skypilot");
		write(main.resolve("options.txt"), utf8("lang:ja_jp\nkey_key.jump:key.keyboard.space\n"));
		ServerList.add(main, List.of(new ServerList.Server("Friends", "friends.example.net")));
		write(main.resolve("resourcepacks/Pack A.zip"), utf8("zip"));
		write(main.resolve("resourcepacks/Folder Pack/pack.mcmeta"), utf8("{}"));
		write(game.resolve("options.txt"), utf8("lang:en_us\n"));
		byte[] mainOptions = Files.readAllBytes(main.resolve("options.txt"));

		Importer.Result r = Importer.run(main, game, true, true, true);
		assertTrue(r.options());
		assertEquals(1, r.servers());
		assertEquals(2, r.packs());
		assertArrayEquals(mainOptions, Files.readAllBytes(game.resolve("options.txt")));
		assertTrue(Files.exists(game.resolve("resourcepacks/Folder Pack/pack.mcmeta")));
		try (var s = Files.list(game)) {
			assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("options.txt.skypilot-backup-")), "the old options were kept");
		}
		assertArrayEquals(mainOptions, Files.readAllBytes(main.resolve("options.txt")), "main game unchanged");
		// importing again adds nothing new
		Importer.Result again = Importer.run(main, game, false, true, true);
		assertEquals(0, again.servers());
		assertEquals(0, again.packs());
		assertThrows(IOException.class, () -> Importer.run(main, main, true, false, false));
	}

	@Test
	void backupsRestoreOnlyInsideTheGameFolder(@TempDir Path dir) throws IOException {
		Path launcher = dir.resolve("launcher");
		Path game = dir.resolve("game");
		write(game.resolve("options.txt"), utf8("lang:ja_jp\n"));
		write(game.resolve("config/skypilot/config.json"), utf8("{\"a\":1}"));
		write(game.resolve("config/other/x.toml"), utf8("x=1"));
		Path backup = Backups.create(launcher, game, null);
		Files.writeString(game.resolve("options.txt"), "lang:en_us\n");
		Files.delete(game.resolve("config/other/x.toml"));
		assertEquals(3, Backups.restore(launcher, game, backup));
		assertEquals("lang:ja_jp\n", Files.readString(game.resolve("options.txt")));
		assertEquals("x=1", Files.readString(game.resolve("config/other/x.toml")));
		assertEquals(2, Backups.list(launcher).size(), "the state before restoring was backed up too");

		Path evil = launcher.resolve("backups/evil.zip");
		Files.write(evil, zip(Map.of("../outside.txt", utf8("no"))));
		assertThrows(IOException.class, () -> Backups.restore(launcher, game, evil));
		assertFalse(Files.exists(dir.resolve("outside.txt")));
		Path odd = launcher.resolve("backups/odd.zip");
		Files.write(odd, zip(Map.of("mods/evil.jar", utf8("no"))));
		assertThrows(IOException.class, () -> Backups.restore(launcher, game, odd), "only settings are restored, never mods");
		assertFalse(Files.exists(game.resolve("mods/evil.jar")));
	}

	// ---------------------------------------------------------------- crash explanations

	@Test
	void explainsFabricResolutionErrors(@TempDir Path dir) throws IOException {
		List<ModScan.Meta> mods = ModScan.scan(modsFolder(dir));
		String log = """
				[12:00:00] [main/ERROR]: Incompatible mods found!
				net.fabricmc.loader.impl.FormattedException: Incompatible mods found!
				A potential solution has been determined, this may resolve your problem:
					 - Install cloth-config, version 15.0.0 or later.
				More details:
					 - Mod 'Needy' (needy) 1.0 requires version 15.0.0 or later of mod 'cloth-config', which is missing!
				""";
		List<CrashAnalyzer.Finding> f = CrashAnalyzer.analyze(log, mods, 4096, Lang.EN);
		assertTrue(f.stream().anyMatch(x -> x.kind() == CrashAnalyzer.Kind.CAUSE && x.title().contains("don't fit")));
		assertTrue(f.stream().anyMatch(x -> x.kind() == CrashAnalyzer.Kind.CAUSE && x.title().contains("which is missing!")));
		assertTrue(f.stream().anyMatch(x -> x.kind() == CrashAnalyzer.Kind.FIX && x.title().equals("Install cloth-config, version 15.0.0 or later.")));
	}

	@Test
	void namesTheModBehindACrash(@TempDir Path dir) throws IOException {
		List<ModScan.Meta> mods = ModScan.scan(modsFolder(dir));
		String report = """
				---- Minecraft Crash Report ----
				// Don't panic!

				Time: 2026-10-06 12:00:00
				Description: Rendering entity in world

				java.lang.NullPointerException: Cannot invoke "Object.toString()" because "x" is null
					at knot//com.example.needy.render.Thing.draw(Thing.java:10)
					at knot//net.minecraft.client.renderer.entity.EntityRenderDispatcher.handler$zjn000$needy$onRender(EntityRenderDispatcher.java:1020)
					at knot//net.minecraft.client.renderer.LevelRenderer.render(LevelRenderer.java:99)
				Caused by: org.spongepowered.asm.mixin.throwables.MixinApplyError: Mixin [needy.mixins.json:FooMixin] from phase [DEFAULT] in config [needy.mixins.json] FAILED during APPLY
				""";
		List<CrashAnalyzer.Finding> f = CrashAnalyzer.analyze(report, mods, 4096, Lang.EN);
		assertEquals("Crash: Rendering entity in world", f.get(0).title());
		assertTrue(f.get(0).detail().startsWith("java.lang.NullPointerException"));
		assertTrue(f.stream().anyMatch(x -> x.kind() == CrashAnalyzer.Kind.MOD && x.title().equals("Suspected mod: Needy")), f.toString());
		assertTrue(f.stream().noneMatch(x -> x.title().contains("Minecraft")), "game classes are never blamed");
	}

	@Test
	void explainsMemoryJavaAndDriverCrashes() {
		List<ModScan.Meta> none = List.of();
		assertTrue(CrashAnalyzer.analyze("java.lang.OutOfMemoryError: Java heap space", none, 2048, Lang.EN).stream()
				.anyMatch(x -> x.kind() == CrashAnalyzer.Kind.CAUSE && x.detail().contains("2 GB")));
		assertTrue(CrashAnalyzer.analyze("java.lang.UnsupportedClassVersionError: x/Y has been compiled by a more recent version of the Java Runtime (class file version 70.0), "
				+ "this version of the Java Runtime only recognizes class file versions up to 69.0", none, 4096, Lang.EN).stream().anyMatch(x -> x.title().contains("Java 26")));
		String hsErr = "#\n# A fatal error has been detected by the Java Runtime Environment:\n#\n# Problematic frame:\n# C  [atio6axx.dll+0x1234]\n#\n";
		assertTrue(CrashAnalyzer.analyze(hsErr, none, 4096, Lang.JA).stream().anyMatch(x -> x.kind() == CrashAnalyzer.Kind.CAUSE && x.title().contains("グラフィックドライバー")));
		List<CrashAnalyzer.Finding> unknown = CrashAnalyzer.analyze("something odd happened", none, 4096, Lang.EN);
		assertEquals(CrashAnalyzer.Kind.INFO, unknown.get(0).kind());
		assertTrue(CrashAnalyzer.analyze("", none, 4096, Lang.EN).isEmpty());
	}

	// ---------------------------------------------------------------- profile options and settings

	@Test
	void profileGetsMemoryArgumentsWindowSizeAndLastUsed(@TempDir Path mc) throws IOException {
		Path file = write(mc.resolve("launcher_profiles.json"), utf8(LauncherTest.PROFILES));
		LauncherProfiles.upsert(file, "fabric-loader-0.19.5-26.2", mc.resolve("sp"), new LauncherProfiles.Options(6144, "-XX:+UseZGC", 1920, 1080), null);
		JsonObject p = LauncherProfiles.read(file);
		assertEquals("-Xmx6144M -Xms1024M -XX:+UseZGC", p.get("javaArgs").getAsString());
		assertEquals(1920, p.getAsJsonObject("resolution").get("width").getAsInt());
		assertEquals(1080, p.getAsJsonObject("resolution").get("height").getAsInt());
		String lastUsed = p.get("lastUsed").getAsString();
		assertTrue(LauncherProfiles.updateOptions(file, LauncherProfiles.Options.memory(4096)));
		p = LauncherProfiles.read(file);
		assertEquals("-Xmx4096M -Xms1024M", p.get("javaArgs").getAsString());
		assertFalse(p.has("resolution"));
		assertTrue(LauncherProfiles.touch(file));
		assertFalse(lastUsed.equals(LauncherProfiles.read(file).get("lastUsed").getAsString()));
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals("My vanilla", root.getAsJsonObject("profiles").getAsJsonObject("abc123").get("name").getAsString());
		Path other = write(mc.resolve("other.json"), utf8("{\"profiles\":{}}"));
		assertFalse(LauncherProfiles.touch(other));
		assertFalse(LauncherProfiles.updateOptions(other, LauncherProfiles.Options.memory(4096)));
	}

	@Test
	void onlyHarmlessJavaArgumentsAreAccepted() {
		assertTrue(JvmArgs.problems("-XX:+UseZGC -XX:+ZGenerational -Xss2M -Dfile.encoding=UTF-8 -XX:MaxGCPauseMillis=50").isEmpty());
		assertTrue(JvmArgs.problems("").isEmpty());
		for (String bad : List.of("-javaagent:x.jar", "-agentlib:jdwp=transport=dt_socket", "-XX:OnOutOfMemoryError=calc", "-XX:OnError=cmd", "-Dfabric.addMods=C:/x.jar",
				"-Djava.system.class.loader=x", "-Xmx8G", "-cp x.jar", "-XX:Flags=x", "-XX:+UseZGC;rm")) {
			assertFalse(JvmArgs.problems(bad).isEmpty(), bad);
		}
		LauncherSettings s = new LauncherSettings();
		s.extraJvmArgs = "-javaagent:evil.jar";
		assertEquals("", s.profileOptions().extraArgs(), "rejected arguments never reach the profile");
		s.extraJvmArgs = "  -XX:+UseZGC   -Xss2M ";
		assertEquals("-XX:+UseZGC -Xss2M", s.profileOptions().extraArgs());
	}

	@Test
	void settingsKeepTheirLimits(@TempDir Path dir) throws IOException {
		LauncherSettings s = new LauncherSettings();
		s.width = 100;
		s.height = 50;
		s.theme = "neon";
		s.playerName = "not a name!";
		s.lastPage = "../x";
		s.save(dir);
		LauncherSettings back = LauncherSettings.load(dir);
		assertEquals(0, back.width);
		assertEquals("dark", back.theme);
		assertEquals("", back.playerName);
		assertEquals("home", back.lastPage);
		assertEquals(2048, SystemInfo.recommendedMb(6 * 1024));
		assertEquals(4096, SystemInfo.recommendedMb(12 * 1024));
		assertEquals(6144, SystemInfo.recommendedMb(16 * 1024));
		assertEquals(8192, SystemInfo.recommendedMb(64 * 1024));
		assertTrue(SystemInfo.tooMuch(14 * 1024, 16 * 1024));
		assertFalse(SystemInfo.tooMuch(8 * 1024, 16 * 1024));
	}

	// ---------------------------------------------------------------- avatar, watcher, diagnostics

	@Test
	void cutsThePlayerHeadOutOfTheSkin() throws IOException {
		String textures = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc123\"}}}";
		String session = "{\"id\":\"0\",\"name\":\"x\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + Base64.getEncoder().encodeToString(utf8(textures)) + "\"}]}";
		assertEquals("https://textures.minecraft.net/texture/abc123", Avatar.skinUrl(session));
		String other = "{\"properties\":[{\"name\":\"textures\",\"value\":\"" + Base64.getEncoder().encodeToString(utf8("{\"textures\":{\"SKIN\":{\"url\":\"https://evil.example/x.png\"}}}")) + "\"}]}";
		assertNull(Avatar.skinUrl(other));
		BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		skin.setRGB(8, 8, 0xFFFF0000);
		skin.setRGB(9, 8, 0xFF00FF00);
		skin.setRGB(41, 8, 0xFF0000FF);
		BufferedImage head = Avatar.head(skin);
		assertEquals(0xFFFF0000, head.getRGB(0, 0));
		assertEquals(0xFF0000FF, head.getRGB(1, 0), "the hat layer covers the face");
		assertTrue(Avatar.validName("Notch_1"));
		assertFalse(Avatar.validName("a"));
		assertFalse(Avatar.validName("../x"));
	}

	@Test
	void watchesTheGameThroughItsFiles(@TempDir Path game) throws IOException {
		long since = System.currentTimeMillis() - 5_000;
		assertEquals(GameWatcher.State.IDLE, GameWatcher.check(game, since).state());
		write(game.resolve("logs/latest.log"), utf8("[12:00] started"));
		assertEquals(GameWatcher.State.RUNNING, GameWatcher.check(game, since).state());
		Path old = write(game.resolve("crash-reports/crash-old.txt"), utf8("x"));
		old.toFile().setLastModified(since - 60_000);
		assertEquals(GameWatcher.State.RUNNING, GameWatcher.check(game, since).state(), "old crashes don't count");
		write(game.resolve("hs_err_pid123.log"), utf8("# Problematic frame:"));
		GameWatcher.Status s = GameWatcher.check(game, since);
		assertEquals(GameWatcher.State.CRASHED, s.state());
		assertEquals("hs_err_pid123.log", s.crash().getFileName().toString());
	}

	@Test
	void diagnosticsHideTheUserFolder() {
		String home = System.getProperty("user.home");
		assertEquals("~" + java.io.File.separator + "x", Diagnostics.anonymize(home + java.io.File.separator + "x"));
		assertEquals("C:\\Users\\<user>\\AppData", Diagnostics.anonymize("C:\\Users\\someone\\AppData"));
	}
}
