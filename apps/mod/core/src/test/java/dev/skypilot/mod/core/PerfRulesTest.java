package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfRulesTest {
	@Test
	void recognisesDamageNumbers() {
		assertTrue(PerfRules.isDamageSplash("§f✧§e1§c,§6234§a,§b567§d✧", 3));
		assertTrue(PerfRules.isDamageSplash("§71,234", 10));
		assertTrue(PerfRules.isDamageSplash("12,345❤", 0));
		assertTrue(PerfRules.isDamageSplash("✯98765✯", 299));
		// Old stands, names and labels are never treated as damage numbers.
		assertFalse(PerfRules.isDamageSplash("1234", 301));
		assertFalse(PerfRules.isDamageSplash("§c[Lv10] Zombie §a100/100❤", 2));
		assertFalse(PerfRules.isDamageSplash("Revenant Horror 5:00", 2));
		assertFalse(PerfRules.isDamageSplash("", 2));
		assertFalse(PerfRules.isDamageSplash(null, 2));
	}

	@Test
	void capsTheFrameRateOnlyWhenAsked() {
		assertEquals(260, PerfRules.fpsLimit(260, true, false, 15, 60));
		assertEquals(15, PerfRules.fpsLimit(260, false, false, 15, 60));
		assertEquals(60, PerfRules.fpsLimit(260, true, true, 15, 60));
		assertEquals(15, PerfRules.fpsLimit(260, false, true, 15, 60));
		// Minecraft's own lower limit (minimised: 10) always wins.
		assertEquals(10, PerfRules.fpsLimit(10, false, true, 15, 60));
		// A switched-off cap is 0.
		assertEquals(260, PerfRules.fpsLimit(260, false, true, 0, 0));
	}

	@Test
	void particleBudgetResetsEveryTick() {
		PerfRules.Budget b = new PerfRules.Budget();
		int ok = 0;
		for (int i = 0; i < 500; i++) if (b.take(150)) ok++;
		assertEquals(150, ok);
		b.reset();
		assertTrue(b.take(150));
		assertEquals(1, b.used());
	}

	@Test
	void countsParticlesFromTheDebugText() {
		assertEquals(57, PerfRules.particleTotal("SQ 50 TL 7 T 3"));
		assertEquals(1, PerfRules.particleTotal("SQ 1 T 1"));
		assertEquals(0, PerfRules.particleTotal("T 0"));
		assertEquals(0, PerfRules.particleTotal(""));
	}

	@Test
	void formatsMemory() {
		long gb = 1024L * 1024 * 1024;
		assertEquals("RAM 1.0 / 4.0 GB (25%)", PerfRules.memory(gb, 4 * gb));
	}

	@Test
	void performanceSettingsAreKeptInRange() {
		SkyPilotConfig c = new SkyPilotConfig();
		c.perf.backgroundFps = 0;
		c.perf.menuFps = 9999;
		c.perf.entityDistance = -5;
		c.perf.particlesPerTick = 1;
		c.perf.videoBackup = new java.util.HashMap<>();
		c.sanitize();
		assertEquals(5, c.perf.backgroundFps);
		assertEquals(240, c.perf.menuFps);
		assertEquals(8, c.perf.entityDistance);
		assertEquals(20, c.perf.particlesPerTick);
		assertEquals(null, c.perf.videoBackup);
		// Every performance feature starts switched off.
		for (Features.Feature f : Features.in(Features.Category.PERFORMANCE)) assertFalse(c.on(f), f.id());
	}
}
