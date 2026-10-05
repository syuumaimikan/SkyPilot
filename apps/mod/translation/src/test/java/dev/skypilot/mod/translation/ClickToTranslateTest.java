package dev.skypilot.mod.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickToTranslateTest {
	@Test
	void eligibleLines() {
		assertTrue(ClickToTranslate.eligible("Party > [MVP+] Steve: anyone want to do f7?"));
		assertTrue(ClickToTranslate.eligible("[312] ♲ [MVP+] Steve: anyone selling a hype"));
		assertTrue(ClickToTranslate.eligible("[NPC] Rhys: Welcome to the Forge!"));
		assertTrue(ClickToTranslate.eligible("You don't have enough coins to buy this!"));
		// nothing to translate
		assertFalse(ClickToTranslate.eligible("Party > [MVP+] Steve: gg"));
		assertFalse(ClickToTranslate.eligible("Party > [MVP+] Steve: よろしくお願いします"));
		assertFalse(ClickToTranslate.eligible("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
		assertFalse(ClickToTranslate.eligible(""));
		assertFalse(ClickToTranslate.eligible(null));
		// multi-line boxes and oversized lines are left alone
		assertFalse(ClickToTranslate.eligible("SLAYER QUEST COMPLETE!\nYou earned some XP"));
		assertFalse(ClickToTranslate.eligible("Steve: " + "word ".repeat(200)));
	}

	@Test
	void recentEntriesExpireOldestFirst() {
		RecentEntries<String> r = new RecentEntries<>(3);
		int a = r.add("a");
		int b = r.add("b");
		int c = r.add("c");
		int d = r.add("d");
		assertEquals(3, r.size());
		assertNull(r.get(a), "oldest expired");
		assertEquals("b", r.get(b));
		assertEquals("d", r.get(d));
		assertTrue(d > c && c > b && b > a, "ids increase");
		assertNull(r.get(12345));
		r.clear();
		assertEquals(0, r.size());
		assertNull(r.get(d));
	}
}
