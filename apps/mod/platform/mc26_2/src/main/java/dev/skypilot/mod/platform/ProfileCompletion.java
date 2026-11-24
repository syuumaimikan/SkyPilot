package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.hud.HudRow;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Profile completion in % from the SkyPilot server (computed from the last analysis). Display only. */
final class ProfileCompletion {
	static Dto.Completion data;
	static String error;
	private static long at;
	private static long triedAt;

	private static final Map<String, String[]> NAMES = Map.of(
			"skills", new String[] {"Skills", "スキル"},
			"catacombs", new String[] {"Catacombs", "カタコンベ"},
			"classes", new String[] {"Dungeon classes", "ダンジョンクラス"},
			"slayers", new String[] {"Slayers", "スレイヤー"},
			"hotm", new String[] {"HOTM", "HOTM"},
			"garden", new String[] {"Garden", "ガーデン"},
			"collections", new String[] {"Collections", "コレクション"},
			"minions", new String[] {"Minion tiers", "ミニオン"},
			"museum", new String[] {"Museum", "博物館"},
			"fairy_souls", new String[] {"Fairy souls", "フェアリーソウル"});

	private ProfileCompletion() {
	}

	static String name(String id, String fallback, Lang l) {
		String[] n = NAMES.get(id);
		return n == null ? fallback : l.t(n[0], n[1]);
	}

	static String pct(double v) {
		return String.format(Locale.ROOT, "%.1f%%", v);
	}

	/** Once per second: refresh every 5 minutes while the HUD line is on. */
	static void tick(Minecraft mc, long now) {
		if (!Feat.on(Features.HUD_COMPLETION)) return;
		if (now - at > 300_000 && now - triedAt > 60_000) refresh(mc, false, null);
	}

	static void refresh(Minecraft mc, boolean force, Runnable done) {
		ApiClient api = SkyPilot.get().api();
		long now = System.currentTimeMillis();
		if (api == null) {
			error = SkyPilot.get().apiError();
			if (done != null) done.run();
			return;
		}
		if (!force && data != null && now - at < 120_000) {
			if (done != null) done.run();
			return;
		}
		triedAt = now;
		api.completion().whenComplete((c, err) -> mc.execute(() -> {
			if (c != null) {
				data = c;
				error = null;
				at = System.currentTimeMillis();
			} else {
				error = SkyPilot.shortError(err);
			}
			if (done != null) done.run();
		}));
	}

	/** The part with the lowest % that is not complete yet (null when all are 100%). */
	static Dto.CompletionPart lowest(Dto.Completion c) {
		if (c == null || c.parts() == null) return null;
		return c.parts().stream().filter(p -> p.percent() < 100).min(Comparator.comparingDouble(Dto.CompletionPart::percent)).orElse(null);
	}

	static void hudRows(List<HudRow> out, Lang l) {
		Dto.Completion c = data;
		if (c == null || c.overall() == null) {
			if (c != null || error != null) out.add(HudRow.text(l.t("Completion: analyse your profile first", "達成率: 先にプロフィールを分析"), HudRow.MUTED));
			return;
		}
		out.add(HudRow.bar(l.t("Completion ", "達成率 ") + pct(c.overall()), 0xFF9D8CFF, c.overall() / 100.0));
		Dto.CompletionPart low = lowest(c);
		if (low != null) out.add(HudRow.text("  " + l.t("lowest: ", "最低: ") + name(low.id(), low.name(), l) + " " + pct(low.percent()), HudRow.MUTED));
	}
}
