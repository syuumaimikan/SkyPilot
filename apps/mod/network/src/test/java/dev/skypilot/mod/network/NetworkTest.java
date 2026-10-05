package dev.skypilot.mod.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class NetworkTest {
	record Call(String method, URI uri, Map<String, String> headers, String body) {
	}

	static final class FakeTransport implements Transport {
		final List<Call> calls = new ArrayList<>();
		Function<Call, Response> handler;

		FakeTransport(Function<Call, Response> handler) {
			this.handler = handler;
		}

		@Override
		public synchronized CompletableFuture<Response> send(String method, URI uri, Map<String, String> headers, String body, Duration timeout) {
			Call c = new Call(method, uri, headers, body);
			calls.add(c);
			try {
				return CompletableFuture.completedFuture(handler.apply(c));
			} catch (RuntimeException e) {
				return CompletableFuture.failedFuture(e);
			}
		}
	}

	@Test
	void baseUrlMustBeHttpsUnlessLocal() {
		assertDoesNotThrow(() -> ApiClient.validateBaseUrl("https://skypilot.example.com"));
		assertDoesNotThrow(() -> ApiClient.validateBaseUrl("http://localhost:3000/"));
		assertDoesNotThrow(() -> ApiClient.validateBaseUrl("http://127.0.0.1:8080"));
		assertThrows(IllegalArgumentException.class, () -> ApiClient.validateBaseUrl("http://example.com"));
		assertThrows(IllegalArgumentException.class, () -> ApiClient.validateBaseUrl("https://user:pw@example.com"));
		assertThrows(IllegalArgumentException.class, () -> ApiClient.validateBaseUrl("file:///etc/passwd"));
	}

	@Test
	void sendsBearerTokenParsesAndCaches() {
		FakeTransport t = new FakeTransport(c -> new Transport.Response(200, "{\"linked\":true,\"goalTitle\":\"Terminator\",\"stepIndex\":2,\"stepCount\":7,\"actions\":[],\"updatedAtMs\":1,\"stale\":false}"));
		ApiClient api = new ApiClient(t, "http://localhost:3000", () -> "spm_abc", System::currentTimeMillis);
		Dto.HudState s = api.hud("Dwarven Mines", "ja").join();
		assertEquals("Terminator", s.goalTitle());
		assertEquals(2, s.stepIndex());
		assertEquals("Bearer spm_abc", t.calls.get(0).headers().get("Authorization"));
		assertEquals("/api/mod/hud", t.calls.get(0).uri().getPath());
		assertTrue(t.calls.get(0).uri().getRawQuery().contains("area=Dwarven+Mines"));
		api.hud("Dwarven Mines", "ja").join();
		assertEquals(1, t.calls.size(), "second read is served from cache");
	}

	@Test
	void notLinkedFailsWithoutNetwork() {
		FakeTransport t = new FakeTransport(c -> new Transport.Response(200, "{}"));
		ApiClient api = new ApiClient(t, "http://localhost:3000", () -> null, System::currentTimeMillis);
		CompletionException e = assertThrows(CompletionException.class, () -> api.text("next", "en").join());
		assertEquals("NOT_LINKED", ((ApiException) e.getCause()).code());
		assertTrue(t.calls.isEmpty());
	}

	@Test
	void apiErrorsAndCircuitBreaker() {
		AtomicLong now = new AtomicLong(1_000);
		FakeTransport t = new FakeTransport(c -> new Transport.Response(503, "{\"code\":\"HYPIXEL_UNAVAILABLE\",\"message\":\"down\"}"));
		ApiClient api = new ApiClient(t, "https://sp.example.com", () -> "spm_x", now::get);
		for (int i = 0; i < 4; i++) {
			CompletionException e = assertThrows(CompletionException.class, () -> api.text("goal" + now.incrementAndGet(), "en").join());
			assertEquals("HYPIXEL_UNAVAILABLE", ((ApiException) e.getCause()).code());
		}
		CompletionException open = assertThrows(CompletionException.class, () -> api.text("goal", "en").join());
		assertEquals("CIRCUIT_OPEN", ((ApiException) open.getCause()).code());
		assertEquals(4, t.calls.size(), "no request while the circuit is open");
		now.addAndGet(31_000);
		t.handler = c -> new Transport.Response(200, "{\"title\":\"ok\",\"lines\":[]}");
		assertEquals("ok", api.text("goal", "en").join().title(), "half-open probe succeeds and closes the circuit");
		assertEquals(CircuitBreaker.State.CLOSED, api.circuit());
	}

	@Test
	void deviceFlowLinksAfterApproval(@TempDir Path dir) throws Exception {
		AtomicLong polls = new AtomicLong();
		FakeTransport t = new FakeTransport(c -> {
			if (c.uri().getPath().equals("/api/device/code")) {
				return new Transport.Response(200, "{\"deviceCode\":\"dc\",\"userCode\":\"WDJB-MJHT\",\"verificationUri\":\"http://localhost:3000/link\",\"verificationUriComplete\":\"http://localhost:3000/link?code=WDJB-MJHT\",\"expiresIn\":600,\"interval\":5}");
			}
			long n = polls.incrementAndGet();
			String status = n == 1 ? "authorization_pending" : n == 2 ? "slow_down" : "ok";
			return new Transport.Response(200, "{\"status\":\"" + status + "\",\"accessToken\":" + (status.equals("ok") ? "\"spm_token123\"" : "null") + "}");
		});
		TokenStore tokens = new TokenStore(dir);
		ApiClient api = new ApiClient(t, "http://localhost:3000", tokens::get, System::currentTimeMillis);
		ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
		try {
			DeviceFlow flow = new DeviceFlow(api, tokens, sched, System::currentTimeMillis, TimeUnit.MILLISECONDS);
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<String> code = new AtomicReference<>();
			flow.start("Minecraft 26.2", new DeviceFlow.Listener() {
				public void onCode(String userCode, String uri, String uriComplete) {
					code.set(userCode);
				}

				public void onLinked() {
					done.countDown();
				}

				public void onFailed(String reason) {
					fail(reason);
				}
			});
			assertTrue(done.await(5, TimeUnit.SECONDS));
			assertEquals("WDJB-MJHT", code.get());
			assertEquals("spm_token123", tokens.get());
			assertEquals("spm_token123", new TokenStore(dir).get(), "token persisted");
			assertEquals(3, polls.get());
			tokens.clear();
			assertFalse(new TokenStore(dir).linked());
		} finally {
			sched.shutdownNow();
		}
	}
}
