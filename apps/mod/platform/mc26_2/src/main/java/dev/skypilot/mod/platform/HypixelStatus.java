package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.hud.HudRow;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.network.ServerStatusRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hypixel's status page (via the SkyPilot server): SkyBlock maintenance and outages for the info
 * HUD, the menu's status tab and one-time notices. Information only.
 */
final class HypixelStatus {
	static Dto.ServerStatus status;
	static String error;
	private static long at;
	private static long triedAt;
	private static String previousSkyblock;
	private static final Set<String> SEEN = new HashSet<>();
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("M/d HH:mm");

	private HypixelStatus() {
	}

	/** Once per second: refresh every 2 minutes while a status feature is on. */
	static void tick(Minecraft mc, long now) {
		if (!Feat.on(Features.HUD_SERVER_STATUS) && !Feat.on(Features.STATUS_ALERTS)) return;
		if (now - at > 120_000 && now - triedAt > 60_000) refresh(mc, false, null);
	}

	/** Loads the status; {@code done} runs on the game thread afterwards. */
	static void refresh(Minecraft mc, boolean force, Runnable done) {
		ApiClient api = SkyPilot.get().api();
		long now = System.currentTimeMillis();
		if (api == null) {
			error = SkyPilot.get().apiError();
			if (done != null) done.run();
			return;
		}
		if (!force && status != null && now - at < 120_000) {
			if (done != null) done.run();
			return;
		}
		triedAt = now;
		api.serverStatus().whenComplete((s, err) -> mc.execute(() -> {
			if (s != null) set(s);
			else error = SkyPilot.shortError(err);
			if (done != null) done.run();
		}));
	}

	static void set(Dto.ServerStatus s) {
		long now = System.currentTimeMillis();
		status = s;
		error = null;
		at = now;
		if (Feat.on(Features.STATUS_ALERTS)) {
			for (ServerStatusRules.Alert a : ServerStatusRules.alerts(s, previousSkyblock, now, SEEN)) notice(a, now);
		} else {
			// mark what is known so turning alerts on later does not replay old notices
			ServerStatusRules.alerts(s, previousSkyblock, now, SEEN);
		}
		if (SEEN.size() > 200) SEEN.clear();
		String sb = ServerStatusRules.skyblockStatus(s);
		if (sb != null) previousSkyblock = sb;
	}

	/** Test hook: forget notices given and the last SkyBlock state. */
	static void resetForTest() {
		SEEN.clear();
		previousSkyblock = null;
		status = null;
		at = 0;
		triedAt = 0;
	}

	private static void notice(ServerStatusRules.Alert a, long now) {
		Lang l = SkyPilot.get().lang();
		String title;
		String line;
		int color;
		ItemStack icon;
		switch (a.kind()) {
			case SCHEDULED -> {
				title = l.t("SkyBlock maintenance scheduled", "SkyBlockメンテナンス予定");
				line = when(a.maintenance(), now, l);
				color = HudRow.WARN;
				icon = Feat.icon(Items.CLOCK);
			}
			case SOON -> {
				title = l.t("SkyBlock maintenance soon", "まもなくSkyBlockメンテナンス");
				line = l.t("starts in ", "開始まで ") + InfoHud.eta(a.maintenance().startsMs() - now) + " (" + clock(a.maintenance().startsMs(), now) + ")";
				color = HudRow.WARN;
				icon = Feat.icon(Items.CLOCK);
			}
			case STARTED -> {
				title = l.t("SkyBlock maintenance started", "SkyBlockメンテナンス開始");
				line = a.maintenance().endsMs() != null ? l.t("planned end ", "終了予定 ") + clock(a.maintenance().endsMs(), now) : "";
				color = HudRow.BAD;
				icon = Feat.icon(Items.BARRIER);
			}
			case BACK -> {
				title = l.t("SkyBlock is back", "SkyBlockが再開しました");
				line = l.t("status.hypixel.net: operational", "status.hypixel.net: 稼働中");
				color = HudRow.GOOD;
				icon = Feat.icon(Items.EMERALD);
			}
			default -> {
				title = l.t("Hypixel: ", "Hypixel: ") + a.incident().name();
				line = impact(a.incident().impact(), l) + " · " + incidentStatus(a.incident().status(), l);
				color = HudRow.BAD;
				icon = Feat.icon(Items.REDSTONE_TORCH);
			}
		}
		Feat.toast(icon, title, color, line);
		McText.chat(McText.prefix().append(Component.literal(title).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(line.isEmpty() ? "" : " - " + line).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(l.t("  /skypilot hypixel", "  /skypilot hypixel")).withStyle(ChatFormatting.DARK_AQUA)));
		Feat.ping(a.kind() == ServerStatusRules.Kind.BACK ? 1.6f : 0.9f);
	}

	// ---------------------------------------------------------------- text

	/** "21:00" today, "10/8 21:00" on other days, in the player's time zone. */
	static String clock(Long ms, long now) {
		if (ms == null) return "?";
		ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
		boolean today = t.toLocalDate().equals(LocalDate.now(ZoneId.systemDefault()));
		return (today ? TIME : DAY_TIME).format(t);
	}

	/** "10/6 21:00 - 10/7 03:00 (in 3h 10m)" */
	static String when(Dto.Maintenance m, long now) {
		return when(m, now, SkyPilot.get().lang());
	}

	static String when(Dto.Maintenance m, long now, Lang l) {
		String span = clock(m.startsMs(), now) + " - " + clock(m.endsMs(), now);
		if (ServerStatusRules.running(m)) return span + (m.endsMs() != null && m.endsMs() > now ? l.t(" (ends in ", "（終了まで ") + InfoHud.eta(m.endsMs() - now) + l.t(")", "）") : "");
		if (m.startsMs() != null && m.startsMs() > now) return span + l.t(" (in ", "（開始まで ") + InfoHud.eta(m.startsMs() - now) + l.t(")", "）");
		return span;
	}

	static String service(String status, Lang l) {
		if (status == null) return l.t("Unknown", "不明");
		return switch (status) {
			case "operational" -> l.t("Online", "稼働中");
			case "degraded_performance" -> l.t("Slow", "遅延");
			case "partial_outage" -> l.t("Partial outage", "一部障害");
			case "major_outage" -> l.t("Outage", "障害");
			case "under_maintenance" -> l.t("Maintenance", "メンテナンス中");
			default -> status;
		};
	}

	static int serviceColor(String status) {
		if (status == null) return HudRow.MUTED;
		return switch (status) {
			case "operational" -> HudRow.GOOD;
			case "degraded_performance", "under_maintenance" -> HudRow.WARN;
			default -> HudRow.BAD;
		};
	}

	static String maintenanceStatus(String status, Lang l) {
		return switch (status == null ? "" : status) {
			case "scheduled" -> l.t("Scheduled", "予定");
			case "in_progress" -> l.t("In progress", "実施中");
			case "verifying" -> l.t("Checking", "確認中");
			case "completed" -> l.t("Finished", "終了");
			default -> status == null ? "" : status;
		};
	}

	static String incidentStatus(String status, Lang l) {
		return switch (status == null ? "" : status) {
			case "investigating" -> l.t("investigating", "調査中");
			case "identified" -> l.t("cause found", "原因特定");
			case "monitoring" -> l.t("monitoring", "経過観察");
			default -> status == null ? "" : status;
		};
	}

	static String impact(String impact, Lang l) {
		return switch (impact == null ? "" : impact) {
			case "critical" -> l.t("critical", "重大");
			case "major" -> l.t("major", "大");
			case "minor" -> l.t("minor", "小");
			default -> l.t("notice", "お知らせ");
		};
	}

	/** Info HUD lines: SkyBlock's state, the relevant maintenance and incidents that matter. */
	static void hudRows(List<HudRow> out, Lang l, long now) {
		Dto.ServerStatus s = status;
		if (s == null) {
			if (error != null) out.add(HudRow.text(l.t("Hypixel status: not available", "Hypixel状態: 取得できません"), HudRow.MUTED));
			return;
		}
		if (!s.available()) {
			out.add(HudRow.text(l.t("Hypixel status: not available", "Hypixel状態: 取得できません"), HudRow.MUTED));
			return;
		}
		Dto.Maintenance m = ServerStatusRules.relevant(s, now);
		String sb = ServerStatusRules.skyblockStatus(s);
		if (m != null && ServerStatusRules.running(m)) {
			out.add(HudRow.text(l.t("SkyBlock: maintenance", "SkyBlock: メンテナンス中")
					+ (m.endsMs() != null && m.endsMs() > now ? l.t(" · ends in ", " · 終了まで ") + InfoHud.eta(m.endsMs() - now) : ""), HudRow.BAD));
		} else {
			out.add(HudRow.text("SkyBlock: " + service(sb, l), serviceColor(sb)));
			if (m != null) {
				out.add(HudRow.text("  " + l.t("Maintenance in ", "メンテまで ") + InfoHud.eta(m.startsMs() - now) + " (" + clock(m.startsMs(), now) + ")",
						m.startsMs() - now <= ServerStatusRules.SOON_MS ? HudRow.BAD : HudRow.WARN));
			}
		}
		for (Dto.Incident i : s.incidents() == null ? List.<Dto.Incident>of() : s.incidents()) {
			if (ServerStatusRules.affectsSkyblock(i)) out.add(HudRow.text("  ! " + i.name(), HudRow.BAD));
		}
	}
}
