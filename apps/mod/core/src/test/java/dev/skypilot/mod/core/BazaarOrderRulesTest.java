package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarOrderRulesTest {
	private static final List<String> LORE = List.of("§8Worth 1.2M coins", "", "§7Order amount: §a640§7x", "§7Filled: §61§7/640 §8(0.2%)", "",
			"§7Price per unit: §61,234.5 coins", "", "§eClick to view options!");

	@Test
	void readsOrderItems() {
		assertTrue(BazaarOrderRules.isOrdersWindow("Your Bazaar Orders"));
		assertTrue(BazaarOrderRules.isOrdersWindow("Co-op Bazaar Orders"));
		assertFalse(BazaarOrderRules.isOrdersWindow("Bazaar ➜ Mining"));
		BazaarOrderRules.Order buy = BazaarOrderRules.parse("§a§lBUY §fEnchanted Diamond", LORE);
		assertEquals(new BazaarOrderRules.Order(true, "Enchanted Diamond", 1234.5, false), buy);
		BazaarOrderRules.Order sell = BazaarOrderRules.parse("§6§lSELL §aEnchanted Gold", List.of("§7Filled: §a64§7/64 §a§l100%!", "§7Price per unit: §6980 coins"));
		assertEquals(new BazaarOrderRules.Order(false, "Enchanted Gold", 980, true), sell);
		// not orders: other items, or no readable price
		assertNull(BazaarOrderRules.parse("§aGo Back", List.of("§7To Bazaar")));
		assertNull(BazaarOrderRules.parse("§a§lBUY §fEnchanted Diamond", List.of("§7Price per unit: §6? coins")));
		assertNull(BazaarOrderRules.parse(null, LORE));
	}

	@Test
	void judgesAgainstTheTopOfTheBook() {
		BazaarOrderRules.Order buy = new BazaarOrderRules.Order(true, "X", 100.0, false);
		// buy orders compete with the highest buy order (= instant sell price)
		assertEquals(BazaarOrderRules.Status.BEST, BazaarOrderRules.judge(buy, 120.0, 100.0).status());
		BazaarOrderRules.Verdict out = BazaarOrderRules.judge(buy, 120.0, 100.4);
		assertEquals(BazaarOrderRules.Status.OUTBID, out.status());
		assertEquals(0.4, out.gap(), 1e-9);
		// sell offers compete with the lowest sell offer (= instant buy price)
		BazaarOrderRules.Order sell = new BazaarOrderRules.Order(false, "X", 120.0, false);
		assertEquals(BazaarOrderRules.Status.BEST, BazaarOrderRules.judge(sell, 120.0, 100.0).status());
		assertEquals(BazaarOrderRules.Status.OUTBID, BazaarOrderRules.judge(sell, 119.9, 100.0).status());
		assertEquals(BazaarOrderRules.Status.UNKNOWN, BazaarOrderRules.judge(sell, null, 100.0).status());
		assertEquals(BazaarOrderRules.Status.FILLED, BazaarOrderRules.judge(new BazaarOrderRules.Order(true, "X", 1, true), null, null).status());
		assertEquals("outbid by 10 (10.0%)", BazaarOrderRules.describe(new BazaarOrderRules.Verdict(BazaarOrderRules.Status.OUTBID, 10), buy, Lang.EN));
	}
}
