package dev.skypilot.mod.translation;

import dev.skypilot.mod.network.CircuitBreaker;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Asynchronous EN→JA chat translation.
 *
 * <ul>
 *   <li>Never blocks the caller: work is async, bounded ({@link #MAX_IN_FLIGHT}) and timed out.</li>
 *   <li>Dictionary phrases are answered locally; repeated lines come from an in-memory LRU cache.</li>
 *   <li>A circuit breaker stops requests while the translator is failing.</li>
 *   <li>Chat text is never written to disk or logs; the cache lives only in memory.</li>
 *   <li>On any failure the original line simply stays as it is.</li>
 * </ul>
 */
public final class TranslationService {
	/** Remote translation function: protected text + channel → translated text. */
	@FunctionalInterface
	public interface Backend {
		CompletableFuture<String> translate(String protectedText, Channel channel);
	}

	public static final int MAX_IN_FLIGHT = 4;
	public static final int CACHE_SIZE = 512;
	public static final long TIMEOUT_MS = 4_000;

	public record Stats(long requested, long translated, long cached, long dropped, long failed) {
	}

	private final Backend backend;
	private final Executor deliver;
	private final CircuitBreaker breaker;
	private final Semaphore slots = new Semaphore(MAX_IN_FLIGHT);
	private final Map<String, String> cache = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > CACHE_SIZE;
		}
	};
	private volatile Map<String, String> phrases = Map.of();
	private volatile Set<String> terms = Set.of();
	private volatile Protector protector = new Protector(List.of());
	private final AtomicLong requested = new AtomicLong();
	private final AtomicLong translated = new AtomicLong();
	private final AtomicLong cached = new AtomicLong();
	private final AtomicLong dropped = new AtomicLong();
	private final AtomicLong failed = new AtomicLong();

	/** {@code deliver} runs the result callback (the platform passes the game thread executor). */
	public TranslationService(Backend backend, Executor deliver, LongSupplier clock) {
		this.backend = backend;
		this.deliver = deliver;
		this.breaker = new CircuitBreaker(4, 60_000, clock);
	}

	/** Install the server dictionary: protected terms and exact phrase translations. */
	public void setDictionary(Collection<String> protectedTerms, Map<String, String> phraseTranslations) {
		Map<String, String> p = new HashMap<>();
		phraseTranslations.forEach((k, v) -> p.put(Protector.normalizePhrase(k), v));
		this.phrases = Map.copyOf(p);
		this.terms = Set.copyOf(protectedTerms);
		this.protector = new Protector(protectedTerms);
	}

	public CircuitBreaker.State circuit() {
		return breaker.state();
	}

	public Stats stats() {
		return new Stats(requested.get(), translated.get(), cached.get(), dropped.get(), failed.get());
	}

	/**
	 * Translate the body of a chat line. {@code onTranslated} receives the rebuilt line (prefix
	 * styles preserved) on the delivery executor; it is not called when nothing was translated.
	 *
	 * @param extraProtected additional terms to keep (e.g. names of players currently online)
	 */
	public void translateLine(StyledLine line, Collection<String> extraProtected, String marker, Consumer<StyledLine> onTranslated) {
		ChannelClassifier.Classified c = ChannelClassifier.classify(line.plain());
		StyledLine[] parts = line.splitAt(c.bodyStart());
		String body = parts[1].plain();
		if (!Protector.worthTranslating(body)) return;
		translateText(body, c.channel(), extraProtected).thenAccept(result -> {
			if (result != null) deliver.execute(() -> onTranslated.accept(StyledLine.translated(parts[0], parts[1], result, marker)));
		});
	}

	/** Translate plain text (also used by {@code /skypilot translate <text>}). Completes with null when not translated. */
	public CompletableFuture<String> translateText(String body, Channel channel, Collection<String> extraProtected) {
		requested.incrementAndGet();
		String key = Protector.normalizePhrase(body);
		String phrase = phrases.get(key);
		if (phrase != null) {
			translated.incrementAndGet();
			return CompletableFuture.completedFuture(phrase);
		}
		synchronized (cache) {
			String hit = cache.get(key);
			if (hit != null) {
				cached.incrementAndGet();
				return CompletableFuture.completedFuture(hit);
			}
		}
		if (!breaker.allow() || !slots.tryAcquire()) {
			// Translator down or chat busy: skip rather than build a backlog of stale lines.
			dropped.incrementAndGet();
			return CompletableFuture.completedFuture(null);
		}
		Protector.Protected prot = protectorFor(extraProtected).protect(body);
		CompletableFuture<String> remote;
		try {
			remote = backend.translate(prot.text(), channel);
		} catch (RuntimeException e) {
			remote = CompletableFuture.failedFuture(e);
		}
		return remote.orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).handle((t, err) -> {
			slots.release();
			if (err != null || t == null || t.isBlank()) {
				breaker.failure();
				failed.incrementAndGet();
				return null;
			}
			breaker.success();
			String restored = Protector.restore(t.strip(), prot.tokens());
			if (restored == null || restored.equals(body)) {
				failed.incrementAndGet();
				return null;
			}
			synchronized (cache) {
				cache.put(key, restored);
			}
			translated.incrementAndGet();
			return restored;
		});
	}

	private Protector protectorFor(Collection<String> extra) {
		if (extra.isEmpty()) return protector;
		Set<String> all = new HashSet<>(terms);
		all.addAll(extra);
		return new Protector(all);
	}
}
