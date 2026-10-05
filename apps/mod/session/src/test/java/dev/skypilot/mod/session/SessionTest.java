package dev.skypilot.mod.session;

import dev.skypilot.mod.core.SkyBlockContext;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
	private static SkyBlockContext ctx(String area, long purse) {
		return new SkyBlockContext(true, area, OptionalLong.of(purse), OptionalLong.empty(), "");
	}

	@Test
	void parsesActionBarXp() {
		assertEquals(new ActionBarParser.XpGain("Mining", 12.5), ActionBarParser.parse("§c1500/1500❤   §3+12.5 Mining (1,234/5,000)   §b800/800✎").orElseThrow());
		assertEquals(1234.0, ActionBarParser.parse("+1,234 Farming (45.2%)").orElseThrow().amount());
		assertTrue(ActionBarParser.parse("§c1500/1500❤").isEmpty());
	}

	@Test
	void tracksPurseXpAndAreaTime() {
		SessionTracker t = new SessionTracker();
		long now = 0;
		t.observe(ctx("Dwarven Mines", 1_000_000), now);
		for (int i = 0; i < 3600; i++) {
			now += 1000;
			t.observe(ctx("Dwarven Mines", 1_000_000 + (i + 1) * 1000L), now);
		}
		t.onActionBar("+100 Mining (1/2)");
		t.onActionBar("+50 Mining (1/2)");
		SessionTracker.Snapshot s = t.snapshot();
		assertEquals(3_600_000, s.activeMs());
		assertEquals(3_600_000, s.purseEarned());
		assertEquals(3_600_000, s.coinsPerHour(), 1);
		assertEquals(150.0, s.xp().get("Mining"));
		assertEquals(150.0, s.xpPerHour().get("Mining"), 0.01);
		assertEquals(3_600_000L, s.areaMs().get("Dwarven Mines"));
		assertEquals(3_600_000L, SessionTracker.toJson(s, now).get("purseEarned").getAsLong());
	}

	@Test
	void ignoresAfkGapsAndBankTransfers() {
		SessionTracker t = new SessionTracker();
		t.observe(ctx("Hub", 100), 0);
		t.observe(ctx("Hub", 200), 1000);
		t.observe(ctx("Hub", 90_000_000), 2000); // bank withdrawal
		t.observe(ctx("Hub", 90_000_100), 10 * 60_000); // came back after AFK
		SessionTracker.Snapshot s = t.snapshot();
		assertEquals(2000, s.activeMs());
		assertEquals(200, s.purseEarned());
		t.observe(SkyBlockContext.NONE, 10 * 60_000 + 1000);
		t.reset();
		assertFalse(t.started());
	}
}
