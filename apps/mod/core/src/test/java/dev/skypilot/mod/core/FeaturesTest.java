package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeaturesTest {
	@Test
	void everyFeatureHasAUniqueIdAndBothLanguages() {
		Set<String> ids = new HashSet<>();
		for (Features.Feature f : Features.all()) {
			assertTrue(ids.add(f.id()), "duplicate " + f.id());
			assertTrue(f.id().matches("[a-z]+\\.[a-z_]+"), f.id());
			assertFalse(f.en().isBlank() || f.ja().isBlank() || f.descEn().isBlank() || f.descJa().isBlank(), f.id());
			assertSame(f, Features.byId(f.id()));
		}
		assertTrue(Features.all().size() >= 50, "lots of features: " + Features.all().size());
		for (Features.Category c : Features.Category.values()) assertFalse(Features.in(c).isEmpty(), c.name());
	}

	@Test
	void newFeaturesAreOffByDefaultAndExistingOnesKeepTheirFields() {
		SkyPilotConfig c = new SkyPilotConfig();
		Set<String> onByDefault = Set.of("tooltip.price", "inventory.item_browser", "inventory.chest_value", "hud.main", "hud.context", "hud.session", "market.alert_sound",
				"tools.session_tracking");
		for (Features.Feature f : Features.all()) assertEquals(onByDefault.contains(f.id()), c.on(f), f.id());
		Features.set(c, Features.CHAT_TIMESTAMPS, true);
		assertTrue(c.on(Features.CHAT_TIMESTAMPS));
		assertEquals(Map.of("chat.timestamps", true), c.features);
		Features.set(c, Features.CHAT_TIMESTAMPS, false);
		assertTrue(c.features.isEmpty());
		Features.set(c, Features.TOOLTIP_PRICE, false);
		assertFalse(c.priceTooltip, "existing switches stay in their own config field");
	}

	@Test
	void recommendedPresetNeverTurnsOnConsentFeatures() {
		SkyPilotConfig c = new SkyPilotConfig();
		int n = Features.applyRecommended(c);
		assertTrue(n >= 20, "preset turns on " + n);
		assertFalse(c.on(Features.CHAT_REPLY_TRANSLATE));
		assertFalse(c.on(Features.CHAT_CLICK_TRANSLATE));
		assertTrue(c.on(Features.CHAT_COMPACT));
		Features.disableAll(c, Features.Category.CHAT);
		assertFalse(c.on(Features.CHAT_COMPACT));
		assertTrue(c.on(Features.INV_RARITY));
		assertTrue(Features.matches(Features.INV_RARITY, "レアリティ") && Features.matches(Features.INV_RARITY, "rarity") && !Features.matches(Features.INV_RARITY, "zzz"));
	}

	@Test
	void sanitizeDropsBadWatchesAndWaypoints() {
		SkyPilotConfig c = new SkyPilotConfig();
		c.features.put("chat.timestamps", false);
		c.watchlist.add(new SkyPilotConfig.Watch("HYPERION", true, 800e6));
		c.watchlist.add(new SkyPilotConfig.Watch("bad id", true, 1));
		c.watchlist.add(new SkyPilotConfig.Watch("WHEAT", false, -5));
		c.waypoints.add(new SkyPilotConfig.SavedWaypoint("forge", "DWARVEN_MINES", 1, 2, 3));
		c.waypoints.add(new SkyPilotConfig.SavedWaypoint("", "HUB", 1, 2, 3));
		c.waypoints.add(new SkyPilotConfig.SavedWaypoint("nan", "HUB", Double.NaN, 2, 3));
		c.sanitize();
		assertTrue(c.features.isEmpty());
		assertEquals(1, c.watchlist.size());
		assertEquals(1, c.waypoints.size());
	}

	@Test
	void chatRulesMatchHypixelLines() {
		assertEquals(ChatRules.Hide.LOBBY, ChatRules.hideGroup(">>> [MVP++] Steve joined the lobby! <<<").orElseThrow());
		assertEquals(ChatRules.Hide.LOBBY, ChatRules.hideGroup("[MVP+] Steve slid into the lobby!").orElseThrow());
		assertEquals(ChatRules.Hide.WARPING, ChatRules.hideGroup("Sending to server mini12AB...").orElseThrow());
		assertEquals(ChatRules.Hide.WARPING, ChatRules.hideGroup("Warping...").orElseThrow());
		assertEquals(ChatRules.Hide.PROFILE, ChatRules.hideGroup("Profile ID: 691d6a3b-23ea-4541-80b5-771facc73b16").orElseThrow());
		assertEquals(ChatRules.Hide.PROFILE, ChatRules.hideGroup("You are playing on profile: Mango").orElseThrow());
		assertEquals(ChatRules.Hide.COMBO, ChatRules.hideGroup("+5 Kill Combo +3% ✯ Magic Find").orElseThrow());
		assertEquals(ChatRules.Hide.ABILITY, ChatRules.hideGroup("Your Implosion hit 3 enemies for 1,234,567.8 damage.").orElseThrow());
		assertEquals(ChatRules.Hide.ABILITY, ChatRules.hideGroup("There are blocks in the way!").orElseThrow());
		assertEquals(ChatRules.Hide.GEXP, ChatRules.hideGroup("You earned 22 GEXP + 210 Event EXP from playing SkyBlock!").orElseThrow());
		assertTrue(ChatRules.hideGroup("Party > [MVP+] Steve: Warping...").isEmpty(), "player chat is never hidden by these rules");

		assertEquals(new ChatRules.Drop("RARE DROP", "Enchanted Book"), ChatRules.drop("RARE DROP! Enchanted Book (+208% ✯ Magic Find)").orElseThrow());
		assertEquals(new ChatRules.Drop("PET DROP", "Baby Yeti"), ChatRules.drop("PET DROP! Baby Yeti (+168 ✯ Magic Find)").orElseThrow());
		assertEquals("Overflux Capacitor", ChatRules.drop("CRAZY RARE DROP! (Overflux Capacitor)").orElseThrow().item());
		assertTrue(ChatRules.drop("Steve: RARE DROP! fake").isEmpty());

		assertEquals(ChatRules.SlayerEvent.COMPLETE, ChatRules.slayer("  SLAYER QUEST COMPLETE!").orElseThrow());
		assertEquals("Mining Speed Boost", ChatRules.abilityReady("Mining Speed Boost is now available!").orElseThrow());
		assertArrayEquals(new int[] {12, 70, -40}, ChatRules.coords("Party > Steve: x: 12, y: 70, z: -40 come here").orElseThrow());
		assertTrue(ChatRules.coords("x: 12, y: 9999, z: 1").isEmpty());

		assertTrue(ChatRules.mentions("Guild > [VIP] Alex: hey steve want to dungeon?", "Steve"));
		assertFalse(ChatRules.mentions("Guild > [VIP] Steve: hi Steve", "Steve"), "own message");
		assertFalse(ChatRules.mentions("Guild > Alex: steven is here", "Steve"), "whole word only");
		assertFalse(ChatRules.mentions("Steve joined the guild", "Steve"), "system line");
	}

	@Test
	void skyblockTimeFromServerConstants() {
		SkyBlockTime t = new SkyBlockTime(1560275700000L, 1200000L, 31,
				List.of("Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"));
		assertEquals(new SkyBlockTime.Date(1, 1, 1, 0, 0), t.at(1560275700000L));
		// 32 days later: Spring 2nd of year 1; half a day later is noon
		assertEquals(new SkyBlockTime.Date(1, 2, 2, 12, 0), t.at(1560275700000L + 32 * 1200000L + 600000L));
		assertEquals(new SkyBlockTime.Date(2, 1, 1, 0, 0), t.at(1560275700000L + 372L * 1200000L));
		assertTrue(t.format(1560275700000L).startsWith("Early Spring 1st, Year 1"));
		// Dark Auction: every 3 days at midnight (xx:55 real time); Jacob's contest: offset 1 day, lasts a day (xx:15-xx:35)
		long now = 1560275700000L + 10 * 60_000L; // 18:05
		assertEquals(1560275700000L + 3 * 1200000L, t.nextRecurring(now, 3, 0, 0));
		assertEquals(1560275700000L + 1200000L, t.nextRecurring(now, 3, 1, 1200000L));
		assertEquals(1560275700000L + 1200000L, t.nextRecurring(1560275700000L + 1200000L + 60_000L, 3, 1, 1200000L), "still running");
	}

	@Test
	void tabSectionsAndActionBar() {
		List<String> tab = List.of("Area: Dwarven Mines", "Commissions:", " Mithril Miner: 45%", " Goblin Slayer: DONE", "", "Powders:", " Mithril: 1,234,567", " Gemstone: 12.5k");
		assertEquals(List.of("Mithril Miner: 45%", "Goblin Slayer: DONE"), TabSections.section(tab, "Commissions:"));
		List<String> powders = TabSections.section(tab, "Powders:");
		assertEquals(1_234_567, TabSections.valueAfterColon(powders.get(0)).getAsDouble());
		assertEquals(12_500, TabSections.valueAfterColon(powders.get(1)).getAsDouble());
		assertEquals("Gemstone", TabSections.label(powders.get(1)));

		ActionBarStats.Stats s = ActionBarStats.stats("§c1,500/1,500❤     §a300§a❈ Defense     §b500/620✎ Mana");
		assertEquals(1500, s.health());
		assertEquals(300, s.defense());
		assertEquals(620, s.maxMana());
		ActionBarStats.SkillGain g = ActionBarStats.skill("§3+12.5 Farming (12,345/50,000)").orElseThrow();
		assertEquals("Farming", g.skill());
		assertEquals(24.69, g.percent(), 0.01);
		assertEquals(40.0, ActionBarStats.skill("+3 Mining (40%)").orElseThrow().percent());
		assertEquals(1_200_000, ActionBarStats.skill("+3 Mining (100,000/1.2M)").orElseThrow().needed());
	}

	@Test
	void itemInfoRarityAndSlotNumbers() {
		assertEquals(ItemInfo.Rarity.LEGENDARY, ItemInfo.rarity(List.of("Gear Score: 500", "", "LEGENDARY SWORD")));
		assertEquals(ItemInfo.Rarity.MYTHIC, ItemInfo.rarity(List.of("a MYTHIC DUNGEON SWORD a")));
		assertEquals(ItemInfo.Rarity.VERY_SPECIAL, ItemInfo.rarity(List.of("VERY SPECIAL")));
		assertNull(ItemInfo.rarity(List.of("A rare drop from zombies")));
		assertEquals("7", ItemInfo.slotNumber(new ItemInfo.Facts("Enchanted Book", "ENCHANTED_BOOK", Map.of("sharpness", 7), null, "")));
		assertEquals("100", ItemInfo.slotNumber(new ItemInfo.Facts("[Lvl 100] Ender Dragon", "PET", null, null, "")));
		assertEquals("11", ItemInfo.slotNumber(new ItemInfo.Facts("Cobblestone Minion XI", "COBBLESTONE_GENERATOR_11", null, null, "")));
		assertEquals("5✪", ItemInfo.slotNumber(new ItemInfo.Facts("Hyperion", "HYPERION", null, 5, "")));
		assertEquals("25", ItemInfo.slotNumber(new ItemInfo.Facts("Farming XXV", null, null, null, "Your Skills")));
		assertNull(ItemInfo.slotNumber(new ItemInfo.Facts("Farming XXV", null, null, null, "Chest")));
		assertEquals(-1, ItemInfo.roman("MCM"), "M/D are not used in SkyBlock menus");
		assertEquals(49, ItemInfo.roman("XLIX"));
	}

	@Test
	void calculatorDurationsAndVisitors() {
		assertEquals(4_700_000, Calc.eval("1.5m*3+200k"), 1e-6);
		assertEquals(-2, Calc.eval("-(1+1)"), 1e-9);
		assertEquals(1024, Calc.eval("2^10"), 1e-9);
		assertEquals(64 * 1_234, Calc.eval("64 x 1,234"), 1e-9);
		assertEquals(2e9, Calc.eval("(1b+1b)"), 1e-3);
		assertThrows(IllegalArgumentException.class, () -> Calc.eval("1/0"));
		assertThrows(IllegalArgumentException.class, () -> Calc.eval("2+"));
		assertThrows(IllegalArgumentException.class, () -> Calc.eval("abc"));
		assertEquals("4,700,000", Calc.plain(4_700_000));
		assertEquals("1.5", Calc.plain(1.5));

		assertEquals(90 * 60_000L, Durations.parse("1h30m"));
		assertEquals(45 * 60_000L, Durations.parse("45"));
		assertEquals(90_000L, Durations.parse("90s"));
		assertThrows(IllegalArgumentException.class, () -> Durations.parse("8d"));
		assertThrows(IllegalArgumentException.class, () -> Durations.parse("soon"));

		VisitorOffer o = VisitorOffer.parse(List.of("Items Required:", " Enchanted Hay Bale x2", " Wheat x64", "", "Rewards:", " +4,000 Farming XP", " +12 Copper", "", "Click to give!"));
		assertEquals(List.of(new VisitorOffer.Need("Enchanted Hay Bale", 2), new VisitorOffer.Need("Wheat", 64)), o.needs());
		assertEquals(12, o.copper().getAsDouble());
		assertNull(VisitorOffer.parse(List.of("Just lore")));
	}
}
