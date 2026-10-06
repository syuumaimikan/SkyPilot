package dev.skypilot.mod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What the Hypixel status page means for a SkyBlock player: the maintenance worth showing, and
 * which notices to give once (scheduled, starting soon, started, SkyBlock back, incident).
 * Information only.
 */
public final class ServerStatusRules {
	public static final String SKYBLOCK = "SkyBlock";
	/** Notice before a scheduled SkyBlock maintenance starts. */
	public static final long SOON_MS = 30 * 60_000L;
	/** A scheduled maintenance is shown on the HUD from this long before it starts. */
	public static final long HUD_AHEAD_MS = 48 * 3_600_000L;
	/** Services whose incidents matter even when SkyBlock is not named. */
	private static final Set<String> CORE = Set.of(SKYBLOCK, "Hypixel Minecraft Server", "Minecraft Server Authentication");

	public enum Kind {
		SCHEDULED, SOON, STARTED, BACK, INCIDENT
	}

	public record Alert(String key, Kind kind, Dto.Maintenance maintenance, Dto.Incident incident) {
	}

	private ServerStatusRules() {
	}

	private static <T> List<T> list(List<T> l) {
		return l == null ? List.of() : l;
	}

	/** SkyBlock's own status (operational, under_maintenance, …), or null when not known. */
	public static String skyblockStatus(Dto.ServerStatus s) {
		if (s == null || !s.available()) return null;
		for (Dto.ServiceStatus v : list(s.services())) if (SKYBLOCK.equals(v.name())) return v.status();
		return null;
	}

	public static boolean running(Dto.Maintenance m) {
		return "in_progress".equals(m.status()) || "verifying".equals(m.status());
	}

	/** The SkyBlock maintenance to show: one in progress, else the next scheduled one within 48 h. */
	public static Dto.Maintenance relevant(Dto.ServerStatus s, long now) {
		if (s == null) return null;
		Dto.Maintenance next = null;
		for (Dto.Maintenance m : list(s.maintenances())) {
			if (!m.skyblock() || "completed".equals(m.status())) continue;
			if (running(m)) return m;
			if (m.startsMs() != null && m.startsMs() - now <= HUD_AHEAD_MS && (m.endsMs() == null || m.endsMs() > now)
					&& (next == null || m.startsMs() < next.startsMs())) {
				next = m;
			}
		}
		return next;
	}

	public static boolean affectsSkyblock(Dto.Incident i) {
		if (i.skyblock()) return true;
		for (String n : list(i.services())) if (CORE.contains(n)) return true;
		return false;
	}

	/**
	 * Notices for the new status, each at most once ({@code seen} keeps their keys).
	 *
	 * @param previousSkyblock SkyBlock's status from the last check (null on the first)
	 */
	public static List<Alert> alerts(Dto.ServerStatus s, String previousSkyblock, long now, Set<String> seen) {
		List<Alert> out = new ArrayList<>();
		if (s == null || !s.available()) return out;
		for (Dto.Maintenance m : list(s.maintenances())) {
			if (!m.skyblock() || "completed".equals(m.status())) continue;
			if (running(m)) {
				// starting also covers the earlier notices
				if (seen.add("start:" + m.id())) out.add(new Alert("start:" + m.id(), Kind.STARTED, m, null));
				seen.add("sched:" + m.id());
				seen.add("soon:" + m.id());
			} else if (m.startsMs() != null && m.startsMs() > now) {
				if (m.startsMs() - now <= SOON_MS) {
					if (seen.add("soon:" + m.id())) out.add(new Alert("soon:" + m.id(), Kind.SOON, m, null));
					seen.add("sched:" + m.id());
				} else if (seen.add("sched:" + m.id())) {
					out.add(new Alert("sched:" + m.id(), Kind.SCHEDULED, m, null));
				}
			}
		}
		String sb = skyblockStatus(s);
		if (previousSkyblock != null && !"operational".equals(previousSkyblock) && "operational".equals(sb)) {
			out.add(new Alert("back:" + now, Kind.BACK, null, null));
		}
		for (Dto.Incident i : list(s.incidents())) {
			if (affectsSkyblock(i) && seen.add("inc:" + i.id())) out.add(new Alert("inc:" + i.id(), Kind.INCIDENT, null, i));
		}
		return out;
	}
}
