package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
	@Test
	void parsesSkyBlockSidebar() {
		SkyBlockContext c = ContextParser.parse("§e§lSKYBLOCK CO-OP", List.of("§710/05/26 §8m12A", " ", "Autumn 3rd", "§79:40pm ☽", "§7⏣ §bDwarven Mines", " ", "Purse: §61,234,567", "Bits: §b2,400", "§ewww.hypixel.net"), List.of());
		assertTrue(c.inSkyBlock());
		assertEquals("Dwarven Mines", c.area());
		assertEquals(1_234_567L, c.purse().getAsLong());
		assertEquals(2_400L, c.bits().getAsLong());
		assertEquals("CO-OP", c.profileType());
	}

	@Test
	void riftAreaAndTabFallback() {
		assertEquals("Wizard Tower", ContextParser.parse("SKYBLOCK", List.of("ф Wizard Tower"), List.of()).area());
		assertEquals("Catacombs", ContextParser.parse("SKYBLOCK", List.of("Purse: 5"), List.of("Dungeon: Catacombs")).area());
		assertFalse(ContextParser.parse("BED WARS", List.of("⏣ Lobby"), List.of()).inSkyBlock());
	}

	@Test
	void islandFromTabList() {
		// sidebar shows the sub-area, the tab list the island
		SkyBlockContext c = ContextParser.parse("SKYBLOCK", List.of("⏣ Royal Mines"), List.of(" §b§lArea: §7Dwarven Mines"));
		assertEquals("Royal Mines", c.area());
		assertEquals("Dwarven Mines", c.island());
		assertEquals("DWARVEN_MINES", c.islandId());
		assertEquals("THE_RIFT", Islands.idOf("The Rift"));
		assertEquals("SPIDER_DEN", Islands.idOf("Spider's Den"));
		assertEquals("WINTER", Islands.idOf("Jerry's Workshop"));
		assertNull(Islands.idOf("Somewhere New"));
		assertEquals("", ContextParser.parse("SKYBLOCK", List.of("⏣ Village"), List.of()).islandId());
		assertEquals("ドワーフ鉱山", Islands.name("DWARVEN_MINES", Lang.JA));
	}

	@Test
	void textHelpers() {
		assertEquals("Hello", Text.strip("§a§lHello​"));
		assertEquals(1_200L, Text.parseLong("1.2k").getAsLong());
		assertEquals(3_000_000L, Text.parseLong("3m").getAsLong());
		assertTrue(Text.parseLong("abc").isEmpty());
		assertEquals("1.50M", Text.coins(1_500_000));
		assertEquals("-2.0k", Text.coins(-2000));
		assertEquals("?", Text.coins(Double.NaN));
		assertEquals("1h 1m", Text.duration(3_660_000));
	}

	@Test
	void configRoundTripAndSanitize(@TempDir Path dir) throws Exception {
		ConfigStore store = new ConfigStore(dir);
		SkyPilotConfig c = store.load();
		assertEquals(SkyPilotConfig.TranslationModeSetting.OFF, c.translation.mode, "translation is OFF by default");
		assertFalse(c.session.upload, "session upload is opt-in");
		c.hud.x = 50;
		c.hud.scale = 99;
		c.translation.mode = SkyPilotConfig.TranslationModeSetting.AUTO; // not accepted without the disclosure
		store.save(c);
		SkyPilotConfig back = store.load();
		assertEquals(50, back.hud.x);
		assertEquals(2.5, back.hud.scale);
		assertEquals(SkyPilotConfig.TranslationModeSetting.OFF, back.translation.mode);
		Files.writeString(store.file(), "{ not json");
		assertEquals(4, store.load().hud.x, "broken file falls back to defaults");
	}

	@Test
	void languageResolution() {
		assertEquals(Lang.JA, Lang.resolve("auto", "ja_jp"));
		assertEquals(Lang.EN, Lang.resolve("auto", "en_us"));
		assertEquals(Lang.JA, Lang.resolve("ja", "en_us"));
		assertEquals("翻訳", Lang.JA.t("Translate", "翻訳"));
	}
}
