package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkRulesTest {
	@Test
	void becomesAfkAfterTheSetMinutes() {
		assertFalse(AfkRules.isAfk(179_999, 3));
		assertTrue(AfkRules.isAfk(180_000, 3));
		assertFalse(AfkRules.isAfk(10_000_000, 0));
	}

	@Test
	void recognisesTheLinesThatMatterWhileAway() {
		assertEquals(AfkRules.Event.PRIVATE_MESSAGE, AfkRules.classify("§dFrom §b[MVP§c+§b] Friend§7: hi", "Steve"));
		assertEquals(AfkRules.Event.PARTY_INVITE, AfkRules.classify("§b[MVP§c+§b] Friend §ehas invited you to join their party!", "Steve"));
		assertEquals(AfkRules.Event.LIMBO, AfkRules.classify("You were spawned in Limbo.", "Steve"));
		assertEquals(AfkRules.Event.LIMBO, AfkRules.classify("§cYou are AFK. Move around to return from AFK.", "Steve"));
		assertEquals(AfkRules.Event.RESTART, AfkRules.classify("§c[Important] §eThis server will restart soon: §bScheduled Reboot", "Steve"));
		assertEquals(AfkRules.Event.DEATH, AfkRules.classify("§c ☠ §7You were killed by Zombie§7§7.", "Steve"));
		assertEquals(AfkRules.Event.MENTION, AfkRules.classify("§9Party §8> §b[MVP§c+§b] Friend§f: steve come back", "Steve"));
		// your own lines, longer names and ordinary chat are not alerts
		assertNull(AfkRules.classify("§9Party §8> §a[VIP] Steve§f: brb", "Steve"));
		assertNull(AfkRules.classify("Friend: Steven is here", "Steve"));
		assertNull(AfkRules.classify("[NPC] Banker: Hello!", "Steve"));
		assertNull(AfkRules.classify("", "Steve"));
		assertNull(AfkRules.classify(null, "Steve"));
	}

	@Test
	void healthAlarmAndSummary() {
		assertTrue(AfkRules.healthAlarm(10, 100, 100));
		assertTrue(AfkRules.healthAlarm(40, 100, 100));
		assertFalse(AfkRules.healthAlarm(60, 100, 100));
		assertFalse(AfkRules.healthAlarm(0, 100, 100));
		Map<AfkRules.Event, Integer> counts = new EnumMap<>(AfkRules.Event.class);
		assertNull(AfkRules.summary(counts, 60_000, Lang.EN));
		counts.put(AfkRules.Event.PARTY_INVITE, 1);
		counts.put(AfkRules.Event.PRIVATE_MESSAGE, 2);
		assertEquals("While you were away (12m 0s): 2× private message, 1× party invite", AfkRules.summary(counts, 720_000, Lang.EN));
		assertEquals("離席中（12m 0s）: 2× 個人メッセージ, 1× パーティ招待", AfkRules.summary(counts, 720_000, Lang.JA));
	}
}
