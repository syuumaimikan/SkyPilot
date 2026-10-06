package dev.skypilot.mod.network;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end check against a running SkyPilot server (opt-in):
 * {@code SKYPILOT_E2E_URL=http://localhost:3000 gradlew :network:test}.
 * Links a device exactly like the mod does, approving it through a web session.
 */
@EnabledIfEnvironmentVariable(named = "SKYPILOT_E2E_URL", matches = ".+")
class LiveApiIT {
	@Test
	void deviceLinkAndModEndpoints(@TempDir Path dir) throws Exception {
		String base = System.getenv("SKYPILOT_E2E_URL");
		HttpTransport transport = new HttpTransport("SkyPilot-Mod-IT");
		TokenStore tokens = new TokenStore(dir);
		ApiClient api = new ApiClient(transport, base, tokens::get, System::currentTimeMillis);
		ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();

		// A browser session that will approve the code.
		// (The JDK CookieManager does not keep host-only cookies for "localhost", so the session
		// cookie is carried explicitly, like a browser would.)
		HttpClient web = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
		HttpResponse<String> meResp = web.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString());
		String cookie = meResp.headers().allValues("set-cookie").stream().map(c -> c.split(";", 2)[0]).filter(c -> c.startsWith("sp_session=")).findFirst().orElseThrow();
		String csrf = JsonParser.parseString(meResp.body()).getAsJsonObject().get("csrfToken").getAsString();

		try {
			DeviceFlow flow = new DeviceFlow(api, tokens, sched, System::currentTimeMillis);
			CountDownLatch linked = new CountDownLatch(1);
			AtomicReference<String> failure = new AtomicReference<>();
			flow.start("SkyPilot integration test", new DeviceFlow.Listener() {
				@Override
				public void onCode(String userCode, String uri, String uriComplete) {
					try {
						assertTrue(uriComplete.endsWith("/link?code=" + userCode));
						HttpResponse<String> r = web.send(HttpRequest.newBuilder(URI.create(base + "/api/device/approve"))
								.header("Content-Type", "application/json").header("x-csrf-token", csrf).header("Cookie", cookie)
								.POST(HttpRequest.BodyPublishers.ofString("{\"userCode\":\"" + userCode + "\",\"approve\":true}")).build(), HttpResponse.BodyHandlers.ofString());
						if (r.statusCode() != 200) throw new IllegalStateException("approve failed: " + r.statusCode() + " " + r.body());
					} catch (Exception e) {
						failure.set(e.toString());
						linked.countDown();
					}
				}

				@Override
				public void onLinked() {
					linked.countDown();
				}

				@Override
				public void onFailed(String reason) {
					failure.set(reason);
					linked.countDown();
				}
			});
			assertTrue(linked.await(30, TimeUnit.SECONDS), "linked within one poll interval");
			assertNull(failure.get(), failure.get());
			assertTrue(tokens.get().startsWith("spm_"));

			Dto.HudState hud = api.hud("Dwarven Mines", "en").join();
			assertTrue(hud.linked());
			assertNotNull(hud.message(), "without an analysis the HUD explains what to do");

			Dto.TextResponse market = api.text("market", "ja").join();
			assertFalse(market.lines().isEmpty());

			Dto.PriceResponse price = api.price("ENCHANTED_DIAMOND", null).join();
			assertEquals("ENCHANTED_DIAMOND", price.skyblockId());
			assertNotNull(price.bazaarBuy(), "bazaar item has an instant-buy price");

			Dto.TranslateResponse tr = api.translate("good luck", "en", "ja", "PARTY").join();
			assertNotNull(tr.translated());

			Dto.TranslationDictionary dict = api.dictionary().join();
			assertFalse(dict.protectedTerms().isEmpty());

			// In-game GUI endpoints, parsed from the real server's JSON.
			Dto.MarketPanel ah = api.market("ah", 10, "ja").join();
			assertEquals("AH", ah.kind());
			for (Dto.FlipRow r : ah.rows()) {
				assertNotNull(r.icon().item());
				assertTrue(r.command().startsWith("/viewauction "));
			}
			Dto.MarketPanel bz = api.market("bazaar", 10, "ja").join();
			assertEquals("BAZAAR", bz.kind());
			bz.rows().forEach(r -> assertTrue(r.icon().item().startsWith("minecraft:")));
			java.util.Map<String, Dto.ItemIcon> icons = api.icons(java.util.List.of("HYPERION", "LAPIS_ARMOR_LEGGINGS", "PET:ENDER_DRAGON:LEGENDARY")).join();
			assertEquals("minecraft:iron_sword", icons.get("HYPERION").item());
			assertEquals(255, icons.get("LAPIS_ARMOR_LEGGINGS").color());
			assertNotNull(icons.get("PET:ENDER_DRAGON:LEGENDARY").texture());
			Dto.Plan plan = api.plan("ja").join();
			assertNotNull(plan.message(), "a fresh account has no analysis yet");
			Dto.Alerts alerts = api.alerts(System.currentTimeMillis() - 60_000, "ja").join();
			assertFalse(alerts.rules().isEmpty(), "default rule when the player has none");

			// Item browser, chest value, guides and gear.
			Dto.ItemPage found = api.items("hyperion", 0, 5).join();
			assertTrue(found.items().stream().anyMatch(i -> i.id().equals("HYPERION")));
			Dto.ItemDetail hyperion = api.item("HYPERION").join();
			assertFalse(hyperion.recipes().isEmpty(), "Hyperion has a crafting recipe");
			com.google.gson.JsonArray stacks = com.google.gson.JsonParser.parseString("[{\"customData\":{\"id\":\"ENCHANTED_DIAMOND\"},\"count\":64}]").getAsJsonArray();
			Dto.Values values = api.values(stacks).join();
			assertTrue(values.total() > 0);
			Dto.Guides guides = api.guides("ja").join();
			assertTrue(guides.guides().size() >= 3);
			assertNotNull(api.gear("ja").join().sections());
			Dto.Calendar cal = api.calendar().join();
			assertEquals(1_200_000L, cal.dayMs());
			assertTrue(cal.events().stream().anyMatch(e -> "jacobs_contest".equals(e.id()) && Integer.valueOf(3).equals(e.everyDays())));
			assertNotNull(api.price("ENCHANTED_DIAMOND", null).join().npcSell(), "NPC sell price from the item list");
			assertEquals(0.0125, api.price("ENCHANTED_DIAMOND", null).join().bazaarTaxRate());
			assertTrue(api.seaCreatures().join().creatures().size() >= 80);
			assertTrue(api.puzzles("ja").join().length >= 10);
			assertNotNull(api.fit("ja").join().message(), "a fresh account has no analysis yet");
			assertNotNull(api.builds("ja").join().message(), "builds need an analysis");
			assertNotNull(api.money("ja").join().message(), "money methods need an analysis");

			// Per-user translation provider: the key goes in, only "…last4" comes back.
			Dto.UserSettings before = api.userSettings().join();
			assertEquals("server", before.translation().provider());
			Dto.UserSettings saved = api.saveTranslationSettings("deepl", "liveapiit-0000-1111-2222:fx", null).join();
			assertEquals("deepl", saved.translation().provider());
			assertTrue(saved.translation().deepl().configured());
			assertEquals("…2:fx", saved.translation().deepl().hint());
			Dto.UserSettings reset = api.saveTranslationSettings("server", "", null).join();
			assertFalse(reset.translation().deepl().configured());
		} finally {
			sched.shutdownNow();
			transport.close();
		}
	}
}
