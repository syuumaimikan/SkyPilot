package dev.skypilot.mod.translation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtectorTest {
	@Test
	void protectsTermsNumbersAndCommands() {
		Protector p = new Protector(List.of("Hyperion", "Dwarven Mines", "Hype"));
		Protector.Protected r = p.protect("selling hyperion for 1.2b in Dwarven Mines, /p join Steve");
		assertEquals("selling ⟦1⟧ for ⟦3⟧ in ⟦0⟧, ⟦2⟧", r.text());
		assertEquals(List.of("Dwarven Mines", "hyperion", "/p join Steve", "1.2b"), r.tokens());
		assertEquals("selling hyperion for 1.2b in Dwarven Mines, /p join Steve", Protector.restore(r.text(), r.tokens()));
	}

	@Test
	void longestTermWinsAndWordBoundariesHold() {
		Protector p = new Protector(List.of("Hype", "Hyperion"));
		assertEquals("⟦0⟧ hyped", p.protect("Hyperion hyped").text());
	}

	@Test
	void restoreRejectsMissingOrInventedPlaceholders() {
		assertNull(Protector.restore("no tokens here", List.of("x")));
		assertNull(Protector.restore("⟦0⟧ ⟦5⟧", List.of("x")));
		assertEquals("a b", Protector.restore("⟦0⟧ ⟦1⟧", List.of("a", "b")));
	}

	@Test
	void detectsJapaneseAndTinyText() {
		assertTrue(Protector.isJapanese("こんにちは"));
		assertTrue(Protector.isJapanese("F7行く"));
		assertFalse(Protector.isJapanese("hello"));
		assertFalse(Protector.worthTranslating("gg"));
		assertFalse(Protector.worthTranslating("123 456"));
		assertTrue(Protector.worthTranslating("need a healer"));
	}
}
