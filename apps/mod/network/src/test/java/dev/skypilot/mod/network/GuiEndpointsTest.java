package dev.skypilot.mod.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Parsing of the in-game GUI endpoints (shapes as produced by packages/protocol mod_api). */
class GuiEndpointsTest {
	private static final String ICON = "{\"item\":\"minecraft:player_head\",\"texture\":\"eyJ0ZXh0dXJlcyI6e319\",\"color\":null,\"glint\":false}";

	@Test
	void marketPanelIsPublicAndParses() {
		String row = "{\"id\":\"0f0a\",\"kind\":\"AH\",\"itemId\":\"HYPERION\",\"name\":\"Hyperion\",\"rarity\":\"LEGENDARY\",\"modifiers\":[\"5✪\"],\"buy\":1.0E9,"
				+ "\"sell\":1.1E9,\"profit\":6.0E7,\"roi\":0.06,\"profitPerHour\":null,\"amount\":null,\"confidence\":0.8,\"confidenceLabel\":\"HIGH\",\"risk\":\"MEDIUM\","
				+ "\"sellTime\":\"30m–2h\",\"doNotFlip\":false,\"notes\":[],\"command\":\"/viewauction 0f0a\",\"icon\":" + ICON + "}";
		NetworkTest.FakeTransport t = new NetworkTest.FakeTransport(c -> new Transport.Response(200,
				"{\"kind\":\"AH\",\"title\":\"AH\",\"rows\":[" + row + "],\"ageSeconds\":12,\"stale\":false,\"note\":\"info only\"}"));
		ApiClient api = new ApiClient(t, "http://localhost:3000", () -> null, System::currentTimeMillis);
		Dto.MarketPanel p = api.market("ah", 30, "ja").join();
		assertEquals(1, p.rows().size());
		Dto.FlipRow r = p.rows().get(0);
		assertEquals("Hyperion", r.name());
		assertEquals("/viewauction 0f0a", r.command());
		assertEquals("minecraft:player_head", r.icon().item());
		assertNull(r.icon().color());
		assertNull(t.calls.get(0).headers().get("Authorization"), "market data needs no account");
		assertEquals("kind=ah&limit=30&lang=ja&sort=score&flipper=0", t.calls.get(0).uri().getRawQuery());
		api.market("bazaar", 20, "en", "hour", 12_500_000, 2).join();
		assertEquals("kind=bazaar&limit=20&lang=en&sort=hour&budget=12500000&flipper=2", t.calls.get(1).uri().getRawQuery());
	}

	@Test
	void planReportAndIconsParse() {
		NetworkTest.FakeTransport t = new NetworkTest.FakeTransport(c -> switch (c.uri().getPath()) {
			case "/api/mod/plan" -> new Transport.Response(200, "{\"profile\":\"x · Coconut\",\"analyzedAtMs\":1,\"goals\":[{\"title\":\"Terminator\",\"progress\":0.12,"
					+ "\"remainingCost\":4.2E8,\"remainingHours\":null,\"steps\":[{\"title\":\"Enderman Slayer 7\",\"detail\":null,\"status\":\"NOT_STARTED\",\"progress\":0,\"cost\":1.0E7,\"hours\":14.0}]}],"
					+ "\"actions\":[{\"title\":\"Farm\",\"category\":\"Farming\",\"why\":\"best\",\"steps\":[\"a\"],\"metrics\":[{\"label\":\"Coins/h\",\"value\":\"2M\"}],"
					+ "\"confidence\":0.65,\"cost\":null,\"hours\":1.0,\"items\":[{\"itemId\":\"HYPERION\",\"name\":\"Hyperion\",\"price\":1.0E9,\"icon\":" + ICON + "}]}],"
					+ "\"warnings\":[],\"message\":null}");
			case "/api/advisor/report/mining" -> new Transport.Response(200, "{\"domain\":\"mining\",\"title\":\"Mining\",\"summary\":[{\"label\":\"HOTM\",\"before\":null,"
					+ "\"after\":null,\"value\":null,\"unit\":null,\"text\":\"Locked\"}],\"sections\":[{\"id\":\"money\",\"title\":\"Money\",\"description\":null,\"metrics\":[],"
					+ "\"table\":{\"columns\":[\"Target\",\"Coins/h\"],\"rows\":[[{\"kind\":\"item\",\"itemId\":\"TITANIUM_ORE\",\"name\":\"Titanium\",\"rarity\":\"RARE\"},"
					+ "{\"kind\":\"coins\",\"value\":61000}]]},\"bullets\":[\"b\"],\"notes\":[],\"sources\":[]}],\"actions\":[],\"unknowns\":[],\"freshness\":[]}");
			case "/api/mod/icons" -> new Transport.Response(200, "{\"HYPERION\":{\"item\":\"minecraft:iron_sword\",\"texture\":null,\"color\":null,\"glint\":false},"
					+ "\"LAPIS_ARMOR_LEGGINGS\":{\"item\":\"minecraft:leather_leggings\",\"texture\":null,\"color\":255,\"glint\":false}}");
			default -> new Transport.Response(404, "{\"code\":\"NOT_FOUND\",\"message\":\"no\"}");
		});
		ApiClient api = new ApiClient(t, "http://localhost:3000", () -> "spm_abc", System::currentTimeMillis);
		Dto.Plan plan = api.plan("ja").join();
		assertEquals("Terminator", plan.goals().get(0).title());
		assertEquals("NOT_STARTED", plan.goals().get(0).steps().get(0).status());
		assertEquals("2M", plan.actions().get(0).metrics().get(0).value());
		assertEquals("Hyperion", plan.actions().get(0).items().get(0).name());

		Dto.Report rep = api.report("mining", "ja").join();
		assertEquals("Locked", rep.summary().get(0).text());
		List<com.google.gson.JsonObject> cells = rep.sections().get(0).table().rows().get(0);
		assertEquals("item", cells.get(0).get("kind").getAsString());
		assertEquals(61000, cells.get(1).get("value").getAsDouble());

		Map<String, Dto.ItemIcon> icons = api.icons(List.of("HYPERION", "LAPIS_ARMOR_LEGGINGS")).join();
		assertEquals("minecraft:iron_sword", icons.get("HYPERION").item());
		assertEquals(255, icons.get("LAPIS_ARMOR_LEGGINGS").color());
		assertTrue(t.calls.stream().anyMatch(c -> c.uri().getPath().equals("/api/mod/icons") && c.uri().getRawQuery().equals("ids=HYPERION%2CLAPIS_ARMOR_LEGGINGS")));
	}
}
