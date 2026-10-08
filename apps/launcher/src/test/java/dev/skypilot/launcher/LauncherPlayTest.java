package dev.skypilot.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Microsoft sign-in, the saved account, game files, the start command and signed updates (no network). */
public class LauncherPlayTest {
	static final String CLIENT = "11111111-2222-3333-4444-555555555555";

	@Test
	void approvedAppIsUsedForNewAndExistingEmptySettings(@TempDir Path dir) throws Exception {
		assertEquals(LauncherSettings.DEFAULT_MS_CLIENT_ID, LauncherSettings.load(dir).msClientId);
		Files.writeString(dir.resolve("launcher.json"), "{\"msClientId\":\"\",\"memoryMb\":6144}");
		LauncherSettings migrated = LauncherSettings.load(dir);
		assertEquals(LauncherSettings.DEFAULT_MS_CLIENT_ID, migrated.msClientId);
		assertEquals(6144, migrated.memoryMb);
		migrated.msClientId = CLIENT;
		migrated.save(dir);
		assertEquals(CLIENT, LauncherSettings.load(dir).msClientId);
	}

	@Test
	void directModeRequiresSignInBeforePlay() {
		LauncherSettings settings = new LauncherSettings();
		settings.msClientId = CLIENT;
		assertTrue(settings.needsSignIn(false));
		assertFalse(settings.direct(false));
		assertFalse(settings.needsSignIn(true));
		assertTrue(settings.direct(true));
		settings.launchMode = "official";
		assertFalse(settings.needsSignIn(false));
		assertFalse(settings.direct(true));
		settings.launchMode = "direct";
		settings.msClientId = "";
		assertFalse(settings.needsSignIn(false));
	}

	/** Scripted answers by URL, in order; records what was sent. */
	static final class FakeHttp implements MicrosoftAuth.Transport {
		final List<String> sent = new ArrayList<>();
		final Map<String, List<Net.Reply>> answers;

		FakeHttp(Map<String, List<Net.Reply>> answers) {
			this.answers = answers;
		}

		@Override
		public Net.Reply send(String url, String contentType, String body, String bearer) throws IOException {
			sent.add(url + " " + (body == null ? "" : body) + (bearer != null ? " bearer" : ""));
			List<Net.Reply> list = answers.get(url);
			if (list == null || list.isEmpty()) throw new IOException("unexpected " + url);
			return list.size() == 1 ? list.get(0) : list.remove(0);
		}
	}

	static Net.Reply reply(int status, String json) {
		return new Net.Reply(status, json.getBytes(StandardCharsets.UTF_8));
	}

	static Map<String, List<Net.Reply>> happyXbox() {
		Map<String, List<Net.Reply>> m = new java.util.HashMap<>();
		m.put(MicrosoftAuth.XBL_URL, new ArrayList<>(List.of(reply(200, "{\"Token\":\"xbl-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"4242\"}]}}"))));
		m.put(MicrosoftAuth.XSTS_URL, new ArrayList<>(List.of(reply(200, "{\"Token\":\"xsts-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"4242\"}]}}"))));
		m.put(MicrosoftAuth.LOGIN_URL, new ArrayList<>(List.of(reply(200, "{\"access_token\":\"mc-token\",\"expires_in\":86400}"))));
		m.put(MicrosoftAuth.PROFILE_URL, new ArrayList<>(List.of(reply(200, "{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"Steve\"}"))));
		return m;
	}

	@Test
	void deviceCodeSignInEndsInAGameSession() throws Exception {
		Map<String, List<Net.Reply>> m = happyXbox();
		m.put(MicrosoftAuth.DEVICE_URL, new ArrayList<>(List.of(reply(200,
				"{\"user_code\":\"ABCD-EFGH\",\"device_code\":\"dc\",\"verification_uri\":\"https://www.microsoft.com/link\",\"expires_in\":900,\"interval\":1}"))));
		m.put(MicrosoftAuth.TOKEN_URL, new ArrayList<>(List.of(reply(400, "{\"error\":\"authorization_pending\"}"), reply(200, "{\"access_token\":\"ms-token\",\"refresh_token\":\"ms-refresh\"}"))));
		FakeHttp http = new FakeHttp(m);
		MicrosoftAuth auth = new MicrosoftAuth(http);
		MicrosoftAuth.DeviceCode code = auth.start(CLIENT);
		assertEquals("ABCD-EFGH", code.userCode());
		assertEquals("https://www.microsoft.com/link", code.verificationUri());
		MicrosoftAuth.MsTokens tokens = auth.poll(CLIENT, code, () -> false);
		assertEquals("ms-refresh", tokens.refreshToken());
		MicrosoftAuth.Session s = auth.minecraft(tokens.accessToken());
		assertEquals("Steve", s.name());
		assertEquals("4242", s.xuid());
		assertFalse(s.toString().contains("mc-token"), "the token is never in toString");
		assertFalse(tokens.toString().contains("ms-refresh"));
		assertTrue(http.sent.get(0).contains("scope=XboxLive.signin+offline_access"));
		assertTrue(http.sent.stream().anyMatch(x -> x.startsWith(MicrosoftAuth.XBL_URL) && x.contains("\"RpsTicket\":\"d=ms-token\"")));
		assertTrue(http.sent.stream().anyMatch(x -> x.startsWith(MicrosoftAuth.LOGIN_URL) && x.contains("XBL3.0 x=4242;xsts-token")));
		assertTrue(http.sent.stream().anyMatch(x -> x.startsWith(MicrosoftAuth.PROFILE_URL) && x.endsWith("bearer")));
		// no client ID: nothing is sent
		assertThrows(MicrosoftAuth.AuthException.class, () -> new MicrosoftAuth(new FakeHttp(Map.of())).start(""));
	}

	@Test
	void signInProblemsAreExplained() {
		Map<String, List<Net.Reply>> notApproved = happyXbox();
		notApproved.put(MicrosoftAuth.LOGIN_URL, List.of(reply(403, "{}")));
		MicrosoftAuth.AuthException e = assertThrows(MicrosoftAuth.AuthException.class, () -> new MicrosoftAuth(new FakeHttp(notApproved)).minecraft("t"));
		assertTrue(e.en.contains("aka.ms/mce-reviewappid") && e.ja.contains("Mojang"));
		Map<String, List<Net.Reply>> child = happyXbox();
		child.put(MicrosoftAuth.XSTS_URL, List.of(reply(401, "{\"XErr\":2148916238}")));
		assertTrue(assertThrows(MicrosoftAuth.AuthException.class, () -> new MicrosoftAuth(new FakeHttp(child)).minecraft("t")).en.contains("child account"));
		Map<String, List<Net.Reply>> noGame = happyXbox();
		noGame.put(MicrosoftAuth.PROFILE_URL, List.of(reply(404, "{}")));
		assertTrue(assertThrows(MicrosoftAuth.AuthException.class, () -> new MicrosoftAuth(new FakeHttp(noGame)).minecraft("t")).en.contains("own the game"));
		Map<String, List<Net.Reply>> expired = new java.util.HashMap<>();
		expired.put(MicrosoftAuth.TOKEN_URL, List.of(reply(400, "{\"error\":\"invalid_grant\"}")));
		assertTrue(assertThrows(MicrosoftAuth.AuthException.class, () -> new MicrosoftAuth(new FakeHttp(expired)).refresh(CLIENT, "old")).en.contains("Sign in again"));
		Map<String, List<Net.Reply>> publicFlows = new java.util.HashMap<>();
		publicFlows.put(MicrosoftAuth.DEVICE_URL, List.of(reply(400, "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000218: The request body must contain client_assertion\"}")));
		assertTrue(assertThrows(MicrosoftAuth.AuthException.class, () -> new MicrosoftAuth(new FakeHttp(publicFlows)).start(CLIENT)).en.contains("Allow public client flows"));
		assertTrue(MicrosoftAuth.validClientId(CLIENT));
		assertFalse(MicrosoftAuth.validClientId("not-an-id"));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void savedSignInIsEncryptedForThisWindowsUser(@TempDir Path dir) throws Exception {
		byte[] secret = "refresh-token-123".getBytes(StandardCharsets.UTF_8);
		byte[] sealed = Dpapi.protect(secret);
		assertFalse(new String(sealed, StandardCharsets.ISO_8859_1).contains("refresh-token-123"));
		assertArrayEquals(secret, Dpapi.unprotect(sealed));
		assertTrue(AccountStore.save(dir, "Steve", "0123456789abcdef0123456789abcdef", "refresh-token-123"));
		String file = Files.readString(dir.resolve(AccountStore.FILE));
		assertFalse(file.contains("refresh-token-123"), "the refresh token is not stored in clear text");
		assertEquals("Steve", AccountStore.load(dir).name());
		assertEquals("refresh-token-123", AccountStore.refreshToken(dir));
		assertTrue(Account.signedIn(dir));
		// a damaged / foreign file is just "signed out"
		Files.writeString(dir.resolve(AccountStore.FILE), file.replaceAll("\"refresh\":\"[^\"]+\"", "\"refresh\":\"AAAA\""));
		assertNull(AccountStore.refreshToken(dir));
		Account.signOut(dir);
		assertNull(AccountStore.load(dir));
		assertFalse(Account.signedIn(dir));
	}

	@Test
	void versionRulesAndLibraries() {
		String os = GameFiles.osName();
		JsonObject other = JsonParser.parseString("{\"action\":\"allow\",\"os\":{\"name\":\"" + (os.equals("windows") ? "osx" : "windows") + "\"}}").getAsJsonObject();
		JsonObject mine = JsonParser.parseString("{\"action\":\"allow\",\"os\":{\"name\":\"" + os + "\"}}").getAsJsonObject();
		com.google.gson.JsonArray onlyOther = new com.google.gson.JsonArray();
		onlyOther.add(other);
		com.google.gson.JsonArray onlyMine = new com.google.gson.JsonArray();
		onlyMine.add(mine);
		assertFalse(GameFiles.allowed(onlyOther, Map.of()));
		assertTrue(GameFiles.allowed(onlyMine, Map.of()));
		assertTrue(GameFiles.allowed(null, Map.of()));
		com.google.gson.JsonArray demo = JsonParser.parseString("[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}]").getAsJsonArray();
		assertFalse(GameFiles.allowed(demo, Map.of()));
		assertTrue(GameFiles.allowed(demo, Map.of("is_demo_user", true)));
		assertEquals("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar", GameFiles.mavenPath("org.ow2.asm:asm:9.10.1"));
		assertEquals("org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1-natives-windows.jar", GameFiles.mavenPath("org.lwjgl:lwjgl:3.4.1:natives-windows"));
		assertEquals("org.lwjgl:lwjgl:natives-windows", GameFiles.libraryKey("org.lwjgl:lwjgl:3.4.1:natives-windows"));
		// Fabric's ASM wins over Minecraft's; other libraries stay
		JsonObject vanilla = JsonParser.parseString("{\"libraries\":["
				+ "{\"name\":\"org.ow2.asm:asm:9.6\",\"downloads\":{\"artifact\":{\"path\":\"org/ow2/asm/asm/9.6/asm-9.6.jar\",\"url\":\"https://libraries.minecraft.net/org/ow2/asm/asm/9.6/asm-9.6.jar\",\"sha1\":\"aa\",\"size\":1}}},"
				+ "{\"name\":\"com.mojang:brigadier:1.3.10\",\"downloads\":{\"artifact\":{\"path\":\"com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar\",\"url\":\"https://libraries.minecraft.net/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar\",\"sha1\":\"bb\",\"size\":2}}},"
				+ "{\"name\":\"x:mac-only:1\",\"rules\":[" + other + "],\"downloads\":{\"artifact\":{\"path\":\"x/mac-only/1/mac-only-1.jar\",\"url\":\"https://libraries.minecraft.net/x\",\"sha1\":\"cc\",\"size\":3}}}"
				+ "]}").getAsJsonObject();
		JsonObject fabric = JsonParser.parseString("{\"libraries\":[{\"name\":\"org.ow2.asm:asm:9.10.1\",\"url\":\"https://maven.fabricmc.net/\",\"sha1\":\"dd\",\"size\":4}]}").getAsJsonObject();
		List<GameFiles.Download> libs = GameFiles.libraries(Path.of("libs"), vanilla, fabric);
		assertEquals(2, libs.size());
		assertEquals("https://maven.fabricmc.net/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar", libs.get(0).url());
		assertEquals("dd", libs.get(0).sha1());
		assertTrue(libs.get(1).url().contains("brigadier"));
	}

	@Test
	void startCommandFollowsTheVersionFilesAndHidesTheToken() {
		JsonObject vanilla = JsonParser.parseString("{\"arguments\":{"
				+ "\"game\":[\"--username\",\"${auth_player_name}\",\"--accessToken\",\"${auth_access_token}\",\"--gameDir\",\"${game_directory}\","
				+ "{\"rules\":[{\"action\":\"allow\",\"features\":{\"has_custom_resolution\":true}}],\"value\":[\"--width\",\"${resolution_width}\",\"--height\",\"${resolution_height}\"]},"
				+ "{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_quick_play_multiplayer\":true}}],\"value\":[\"--quickPlayMultiplayer\",\"${quickPlayMultiplayer}\"]},"
				+ "{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"--demo\"}],"
				+ "\"jvm\":[\"-Djava.library.path=${natives_directory}/java\",\"-Dminecraft.launcher.brand=${launcher_name}\",\"-cp\",\"${classpath}\"]},"
				+ "\"mainClass\":\"net.minecraft.client.main.Main\"}").getAsJsonObject();
		JsonObject fabric = JsonParser.parseString("{\"id\":\"fabric-loader-0.19.5-26.2\",\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\","
				+ "\"arguments\":{\"game\":[],\"jvm\":[\"-DFabricMcEmu= net.minecraft.client.main.Main \"]}}").getAsJsonObject();
		GameFiles.Prepared p = new GameFiles.Prepared(Path.of("mc"), Path.of("java"), vanilla, fabric, List.of(Path.of("a.jar"), Path.of("b.jar")), "32");
		MicrosoftAuth.Session s = new MicrosoftAuth.Session("Steve", "0123456789abcdef0123456789abcdef", "secret-mc-token", "4242", Long.MAX_VALUE);
		List<String> cmd = GameLauncher.command(p, Path.of("game"), Path.of("natives"), s, CLIENT, new GameLauncher.Options(6144, "-XX:+UseZGC -XX:OnError=calc", 1920, 1080, true));
		assertEquals("java", cmd.get(0));
		assertTrue(cmd.contains("-Xmx6144M") && cmd.contains("-XX:+UseZGC"));
		assertFalse(cmd.stream().anyMatch(a -> a.contains("OnError")), "blocked JVM flags are dropped");
		assertTrue(cmd.contains("net.fabricmc.loader.impl.launch.knot.KnotClient"));
		assertTrue(cmd.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient") < cmd.indexOf("--username"), "main class before the game arguments");
		assertEquals("Steve", cmd.get(cmd.indexOf("--username") + 1));
		assertEquals("secret-mc-token", cmd.get(cmd.indexOf("--accessToken") + 1));
		assertEquals(GameLauncher.HYPIXEL, cmd.get(cmd.indexOf("--quickPlayMultiplayer") + 1));
		assertEquals("1920", cmd.get(cmd.indexOf("--width") + 1));
		assertFalse(cmd.contains("--demo"));
		assertTrue(cmd.contains("-Djava.library.path=" + Path.of("natives") + "/java"));
		assertTrue(cmd.contains("a.jar" + java.io.File.pathSeparator + "b.jar"));
		String shown = GameLauncher.describe(cmd, s);
		assertFalse(shown.contains("secret-mc-token"), "the token is never logged");
		assertTrue(shown.contains("<hidden>") && shown.contains("<2 jars>"));
		List<String> noHypixel = GameLauncher.command(p, Path.of("game"), Path.of("natives"), s, CLIENT, new GameLauncher.Options(4096, "", 0, 0, false));
		assertFalse(noHypixel.contains("--quickPlayMultiplayer") || noHypixel.contains("--width"));
	}

	@Test
	void updatesNeedAValidSignature() throws Exception {
		KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		String manifest = "{\"version\":\"0.2.0\",\"file\":\"SkyPilot-Launcher-0.2.0.exe\",\"sha256\":\"" + "ab".repeat(32) + "\",\"size\":1234,\"publishedAt\":\"2026-10-06T00:00:00Z\"}";
		Signature sig = Signature.getInstance("Ed25519");
		sig.initSign(kp.getPrivate());
		sig.update(manifest.getBytes(StandardCharsets.UTF_8));
		String signature = Base64.getEncoder().encodeToString(sig.sign());
		Updater.Release r = Updater.verify(manifest, signature, kp.getPublic());
		assertEquals("0.2.0", r.version());
		assertEquals(1234, r.size());
		// changed manifest, other key, bad file name: refused
		assertThrows(IOException.class, () -> Updater.verify(manifest.replace("1234", "999999"), signature, kp.getPublic()));
		KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		assertThrows(IOException.class, () -> Updater.verify(manifest, signature, other.getPublic()));
		String evil = manifest.replace("SkyPilot-Launcher-0.2.0.exe", "../evil.bat");
		sig.initSign(kp.getPrivate());
		sig.update(evil.getBytes(StandardCharsets.UTF_8));
		String evilSig = Base64.getEncoder().encodeToString(sig.sign());
		assertThrows(IOException.class, () -> Updater.verify(evil, evilSig, kp.getPublic()));
		assertThrows(IOException.class, () -> Updater.verify(manifest, signature, null));
		assertTrue(Updater.newer("0.10.0", "0.9.2"));
		assertTrue(Updater.newer("0.2", "0.1.9"));
		assertFalse(Updater.newer("0.1.0", "0.1.0"));
		assertFalse(Updater.newer("1.0.0", "dev"), "development builds are never offered updates");
		assertNotEquals(null, Updater.publicKey(), "the launcher ships the update public key");
	}

	@Test
	void signInSettingsAreChecked() {
		LauncherSettings s = new LauncherSettings();
		s.msClientId = "  " + CLIENT + " ";
		s.launchMode = "weird";
		s.sanitize();
		assertEquals(CLIENT, s.msClientId);
		assertEquals("direct", s.launchMode);
		assertTrue(s.direct(true));
		assertFalse(s.direct(false));
		s.launchMode = "official";
		assertFalse(s.direct(true));
		s.msClientId = "x";
		s.sanitize();
		assertEquals(LauncherSettings.DEFAULT_MS_CLIENT_ID, s.msClientId);
	}
}
