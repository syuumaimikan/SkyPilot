package dev.skypilot.mod.hud;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyBlockContext;
import dev.skypilot.mod.network.Dto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudTest {
	private static Dto.HudState state(long updatedAt) {
		return new Dto.HudState(true, "Steve", "Terminator", 3, 7, "Enderman Slayer 7", 0.42, 3_500_000.0, 7_000_000.0, "XP", "Buy Juju Shortbow", 120_000_000.0, 0.31,
				new Dto.HudContextBlock("SLAYER", "Slayer advisor", List.of(new Dto.HudLine("Best tier", "T4"), new Dto.HudLine("", "Bring a Juju"))), List.of("Do T4 Voidgloom"),
				updatedAt, false, null);
	}

	@Test
	void roadmapAndContextRows() {
		List<HudRow> rows = HudModel.build(new HudModel.Input(true, state(1_000), null, SkyBlockContext.NONE, null, true, true, Lang.EN, 2_000));
		List<String> text = rows.stream().map(HudRow::text).toList();
		assertTrue(text.contains("Goal: Terminator 31%"));
		assertTrue(text.contains("[3/7] Enderman Slayer 7 (3.50M/7.00M XP)"));
		assertTrue(text.contains("Next: Buy Juju Shortbow"));
		assertTrue(text.contains("Best tier: T4"));
		assertTrue(text.contains("Bring a Juju"));
		assertEquals(0.42, rows.stream().filter(r -> r.text().startsWith("[3/7]")).findFirst().orElseThrow().progress(), 1e-9);
	}

	@Test
	void notLinkedAndStaleStates() {
		assertTrue(HudModel.build(new HudModel.Input(false, null, null, SkyBlockContext.NONE, null, true, true, Lang.JA, 0)).stream().anyMatch(r -> r.text().contains("/skypilot link")));
		List<HudRow> stale = HudModel.build(new HudModel.Input(true, state(0), null, SkyBlockContext.NONE, null, false, false, Lang.EN, 3_600_000));
		assertTrue(stale.stream().anyMatch(r -> r.text().startsWith("STALE DATA")));
		assertFalse(stale.stream().anyMatch(r -> r.text().equals("Slayer advisor")), "context hidden when disabled");
	}

	@Test
	void layoutStaysOnScreen() {
		HudLayout.Box b = HudLayout.place(5000, 5000, 150, 80, 2.0, 640, 360);
		assertEquals(640 - 300, b.x());
		assertEquals(360 - 160, b.y());
		assertTrue(b.contains(b.x() + 1, b.y() + 1));
		assertEquals(0, HudLayout.place(-20, -20, 10, 10, 1, 100, 100).x());
		assertEquals(0x99101018, HudLayout.background(0.6));
		assertTrue(HudLayout.contentHeight(List.of(HudRow.text("a", 0), HudRow.bar("b", 0, 0.5)), 9) > 18);
	}
}
