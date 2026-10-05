package dev.skypilot.mod.translation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * translation_component_test: chat lines are translated without breaking their Text Components
 * (prefix styles with hover/click events survive), protected tokens survive, and the pipeline
 * fails open without blocking.
 */
class TranslationComponentTest {
	private static StyledLine line(Object... parts) {
		List<StyledLine.Segment> segs = new ArrayList<>();
		for (int i = 0; i < parts.length; i += 2) segs.add(new StyledLine.Segment((String) parts[i], (Integer) parts[i + 1]));
		return new StyledLine(segs);
	}

	@Test
	void prefixStylesArePreservedAndBodyIsReplaced() {
		// "Party > " (style 1) "[MVP+] " (style 2, rank hover) "Steve" (style 3, click to /msg) ": anyone want to do f7?" (style 4)
		StyledLine original = line("Party > ", 1, "[MVP+] ", 2, "Steve", 3, ": ", 4, "anyone want to do f7?", 4);
		AtomicReference<StyledLine> out = new AtomicReference<>();
		TranslationService svc = new TranslationService((text, ch) -> CompletableFuture.completedFuture("⟦0⟧に行きたい人いる？"), Runnable::run, System::currentTimeMillis);
		svc.setDictionary(List.of("F7"), Map.of());
		svc.translateLine(original, List.of(), " [JA] ", out::set);
		StyledLine t = out.get();
		assertNotNull(t);
		assertEquals("Party > [MVP+] Steve: ", t.splitAt(22)[0].plain());
		// Prefix segments keep their exact style ids (hover/click events live in those styles).
		assertEquals(new StyledLine.Segment("Party > ", 1), t.segments().get(0));
		assertEquals(new StyledLine.Segment("[MVP+] ", 2), t.segments().get(1));
		assertEquals(new StyledLine.Segment("Steve", 3), t.segments().get(2));
		assertEquals(new StyledLine.Segment(": ", 4), t.segments().get(3));
		assertEquals(StyledLine.MARKER_STYLE, t.segments().get(4).styleId());
		// Body: translated, protected "f7" restored, rendered in the body's original style.
		StyledLine.Segment body = t.segments().get(5);
		assertEquals("f7に行きたい人いる？", body.text());
		assertEquals(4, body.styleId());
	}

	@Test
	void segmentSplitInsideAStyleKeepsBothHalvesStyled() {
		StyledLine l = line("[NPC] Rhys: Hello adventurer", 7);
		StyledLine[] parts = l.splitAt(ChannelClassifier.classify(l.plain()).bodyStart());
		assertEquals(List.of(new StyledLine.Segment("[NPC] Rhys: ", 7)), parts[0].segments());
		assertEquals(List.of(new StyledLine.Segment("Hello adventurer", 7)), parts[1].segments());
	}

	@Test
	void dictionaryPhrasesNeedNoNetwork() {
		AtomicInteger calls = new AtomicInteger();
		TranslationService svc = new TranslationService((text, ch) -> {
			calls.incrementAndGet();
			return CompletableFuture.completedFuture("x");
		}, Runnable::run, System::currentTimeMillis);
		svc.setDictionary(List.of(), Map.of("good luck have fun", "よろしくお願いします"));
		AtomicReference<StyledLine> out = new AtomicReference<>();
		svc.translateLine(line("Guild > Alex [Officer]: ", 1, "Good luck have fun!", 2), List.of(), " [JA] ", out::set);
		assertEquals("よろしくお願いします", out.get().segments().get(out.get().segments().size() - 1).text());
		assertEquals(0, calls.get());
	}

	@Test
	void cachedTranslationsAreReused() {
		AtomicInteger calls = new AtomicInteger();
		TranslationService svc = new TranslationService((text, ch) -> {
			calls.incrementAndGet();
			return CompletableFuture.completedFuture("こんにちは みんな");
		}, Runnable::run, System::currentTimeMillis);
		svc.translateText("hello everyone", Channel.PLAYER, List.of()).join();
		svc.translateText("Hello everyone!", Channel.PLAYER, List.of()).join();
		assertEquals(1, calls.get());
		assertEquals(1, svc.stats().cached());
	}

	@Test
	void japaneseAndTinyLinesAreSkipped() {
		AtomicInteger calls = new AtomicInteger();
		TranslationService svc = new TranslationService((text, ch) -> {
			calls.incrementAndGet();
			return CompletableFuture.completedFuture("x");
		}, Runnable::run, System::currentTimeMillis);
		svc.translateLine(line("Party > Steve: ", 1, "こんにちは", 2), List.of(), "", l -> fail("must not translate Japanese"));
		svc.translateLine(line("Party > Steve: ", 1, "gg", 2), List.of(), "", l -> fail("must not translate 'gg'"));
		assertEquals(0, calls.get());
	}

	@Test
	void lostPlaceholderMeansNoTranslation() {
		TranslationService svc = new TranslationService((text, ch) -> CompletableFuture.completedFuture("ハイペリオンを売ります"), Runnable::run, System::currentTimeMillis);
		svc.setDictionary(List.of("Hyperion"), Map.of());
		assertNull(svc.translateText("selling Hyperion cheap", Channel.PLAYER, List.of()).join(), "translator dropped ⟦0⟧, keep the original");
	}

	@Test
	void playerNamesAreProtected() {
		AtomicReference<String> sent = new AtomicReference<>();
		TranslationService svc = new TranslationService((text, ch) -> {
			sent.set(text);
			return CompletableFuture.completedFuture("⟦0⟧ を待って");
		}, Runnable::run, System::currentTimeMillis);
		String r = svc.translateText("wait for Notch", Channel.PARTY, Set.of("Notch")).join();
		assertEquals("wait for ⟦0⟧", sent.get());
		assertEquals("Notch を待って", r);
	}

	@Test
	void failuresOpenTheCircuitAndNeverThrow() {
		AtomicInteger calls = new AtomicInteger();
		TranslationService svc = new TranslationService((text, ch) -> {
			calls.incrementAndGet();
			return CompletableFuture.failedFuture(new RuntimeException("down"));
		}, Runnable::run, System::currentTimeMillis);
		for (int i = 0; i < 10; i++) {
			assertNull(svc.translateText("message number " + "abcdefghij".charAt(i), Channel.PLAYER, List.of()).join());
		}
		assertEquals(4, calls.get(), "breaker opens after 4 failures");
		assertEquals(6, svc.stats().dropped());
	}

	@Test
	void slowTranslatorIsBoundedAndDoesNotBlock() {
		List<CompletableFuture<String>> pending = new ArrayList<>();
		TranslationService svc = new TranslationService((text, ch) -> {
			CompletableFuture<String> f = new CompletableFuture<>();
			pending.add(f);
			return f;
		}, Runnable::run, System::currentTimeMillis);
		long start = System.nanoTime();
		for (int i = 0; i < 20; i++) svc.translateText("busy chat line " + (char) ('a' + i), Channel.PLAYER, List.of());
		assertTrue(System.nanoTime() - start < 200_000_000L, "submitting must return immediately");
		assertEquals(TranslationService.MAX_IN_FLIGHT, pending.size());
		assertEquals(20 - TranslationService.MAX_IN_FLIGHT, svc.stats().dropped());
	}

	@Test
	void modesSelectChannels() {
		assertFalse(TranslationMode.OFF.automatic(Channel.PARTY));
		assertFalse(TranslationMode.MANUAL.automatic(Channel.PARTY));
		assertTrue(TranslationMode.PARTY.automatic(Channel.PARTY));
		assertFalse(TranslationMode.PARTY.automatic(Channel.GUILD));
		assertTrue(TranslationMode.NPC_ONLY.automatic(Channel.NPC));
		assertTrue(TranslationMode.AUTO.automatic(Channel.PRIVATE));
		assertFalse(TranslationMode.AUTO.automatic(Channel.SYSTEM));
		assertFalse(TranslationMode.MANUAL.sendsAutomatically());
	}
}
