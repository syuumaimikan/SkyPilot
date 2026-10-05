package dev.skypilot.mod.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** {@link Transport} on the JDK HTTP client, running on its own small daemon thread pool. */
public final class HttpTransport implements Transport, AutoCloseable {
	private static final int MAX_BODY_CHARS = 2 * 1024 * 1024;
	private final ExecutorService executor;
	private final HttpClient client;
	private final String userAgent;

	public HttpTransport(String userAgent) {
		this.userAgent = userAgent;
		this.executor = Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "SkyPilot-HTTP");
			t.setDaemon(true);
			return t;
		});
		this.client = HttpClient.newBuilder()
				.executor(executor)
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Override
	public CompletableFuture<Response> send(String method, URI uri, Map<String, String> headers, String body, Duration timeout) {
		HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", userAgent).header("Accept", "application/json");
		// Plain http (localhost only): no "Upgrade: h2c" probe - some servers drop such requests.
		if ("http".equalsIgnoreCase(uri.getScheme())) b.version(HttpClient.Version.HTTP_1_1);
		headers.forEach(b::header);
		if (body != null) {
			b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		} else {
			b.method(method, HttpRequest.BodyPublishers.noBody());
		}
		return client.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			String text = r.body() == null ? "" : r.body();
			if (text.length() > MAX_BODY_CHARS) text = text.substring(0, MAX_BODY_CHARS);
			return new Response(r.statusCode(), text);
		});
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}
}
