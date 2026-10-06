package dev.skypilot.mod.network;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStatusRulesTest {
	static final long H = 3_600_000L;
	static final long NOW = 1_000L * H;

	static Dto.Maintenance m(String id, String status, long startIn, boolean skyblock) {
		return new Dto.Maintenance(id, "SkyBlock Maintenance", status, NOW + startIn, NOW + startIn + 6 * H, List.of(skyblock ? "SkyBlock" : "Forums"), skyblock,
				"SkyBlock will be in maintenance to roll v0.27.2", "https://status.hypixel.net/incidents/" + id);
	}

	static Dto.ServerStatus status(String skyblock, List<Dto.Maintenance> ms, List<Dto.Incident> inc) {
		return new Dto.ServerStatus(true, "none", "", List.of(new Dto.ServiceStatus("SkyBlock", skyblock)), ms, inc, List.of(), NOW, "https://status.hypixel.net",
				"status.hypixel.net");
	}

	static List<ServerStatusRules.Kind> kinds(List<ServerStatusRules.Alert> a) {
		return a.stream().map(ServerStatusRules.Alert::kind).toList();
	}

	@Test
	void picksTheMaintenanceToShow() {
		assertNull(ServerStatusRules.relevant(status("operational", List.of(m("far", "scheduled", 72 * H, true)), List.of()), NOW), "more than 48 h away");
		assertNull(ServerStatusRules.relevant(status("operational", List.of(m("forum", "scheduled", H, false)), List.of()), NOW), "not SkyBlock");
		var soon = m("a", "scheduled", 5 * H, true);
		var sooner = m("b", "scheduled", 2 * H, true);
		assertEquals("b", ServerStatusRules.relevant(status("operational", List.of(soon, sooner), List.of()), NOW).id());
		var running = m("c", "in_progress", -H, true);
		assertEquals("c", ServerStatusRules.relevant(status("under_maintenance", List.of(sooner, running), List.of()), NOW).id(), "running wins");
		assertNull(ServerStatusRules.relevant(null, NOW));
	}

	@Test
	void eachNoticeOnce() {
		Set<String> seen = new HashSet<>();
		var m = m("a", "scheduled", 5 * H, true);
		assertEquals(List.of(ServerStatusRules.Kind.SCHEDULED), kinds(ServerStatusRules.alerts(status("operational", List.of(m), List.of()), null, NOW, seen)));
		assertTrue(ServerStatusRules.alerts(status("operational", List.of(m), List.of()), "operational", NOW, seen).isEmpty(), "not twice");
		// 20 minutes before: "soon"
		var near = m("a", "scheduled", 20 * 60_000L, true);
		assertEquals(List.of(ServerStatusRules.Kind.SOON), kinds(ServerStatusRules.alerts(status("operational", List.of(near), List.of()), "operational", NOW, seen)));
		// started
		var run = m("a", "in_progress", -60_000L, true);
		assertEquals(List.of(ServerStatusRules.Kind.STARTED), kinds(ServerStatusRules.alerts(status("under_maintenance", List.of(run), List.of()), "operational", NOW, seen)));
		// back
		assertEquals(List.of(ServerStatusRules.Kind.BACK), kinds(ServerStatusRules.alerts(status("operational", List.of(), List.of()), "under_maintenance", NOW, seen)));
		assertTrue(ServerStatusRules.alerts(status("operational", List.of(), List.of()), "operational", NOW, seen).isEmpty());
	}

	@Test
	void joiningDuringMaintenanceGivesOneNotice() {
		Set<String> seen = new HashSet<>();
		var run = m("x", "in_progress", -H, true);
		assertEquals(List.of(ServerStatusRules.Kind.STARTED), kinds(ServerStatusRules.alerts(status("under_maintenance", List.of(run), List.of()), null, NOW, seen)));
		// no "back" without an earlier status
		assertTrue(ServerStatusRules.alerts(status("operational", List.of(), List.of()), null, NOW, new HashSet<>()).isEmpty());
	}

	@Test
	void incidentsThatMatter() {
		var forums = new Dto.Incident("f", "Forums slow", "investigating", "minor", NOW, List.of("Forums"), false, null, "");
		var login = new Dto.Incident("l", "Login issues", "identified", "major", NOW, List.of("Minecraft Server Authentication"), false, null, "");
		Set<String> seen = new HashSet<>();
		var alerts = ServerStatusRules.alerts(status("operational", List.of(), List.of(forums, login)), "operational", NOW, seen);
		assertEquals(1, alerts.size());
		assertEquals("l", alerts.get(0).incident().id());
		assertEquals("operational", ServerStatusRules.skyblockStatus(status("operational", null, null)));
		assertTrue(ServerStatusRules.alerts(status("operational", null, null), null, NOW, seen).isEmpty(), "null lists are fine");
	}
}
