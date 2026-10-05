package dev.skypilot.mod.network;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Minimal async HTTP abstraction (real implementation: {@link HttpTransport}; fakes in tests). */
public interface Transport {
	record Response(int status, String body) {
	}

	CompletableFuture<Response> send(String method, URI uri, Map<String, String> headers, String body, Duration timeout);
}
