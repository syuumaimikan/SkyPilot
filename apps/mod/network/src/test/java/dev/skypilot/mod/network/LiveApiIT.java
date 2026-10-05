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
		} finally {
			sched.shutdownNow();
			transport.close();
		}
	}
}
