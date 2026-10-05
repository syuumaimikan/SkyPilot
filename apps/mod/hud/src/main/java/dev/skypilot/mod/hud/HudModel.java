package dev.skypilot.mod.hud;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyBlockContext;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.session.SessionTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns roadmap/context/session data into HUD rows (Roadmap HUD, Progress HUD, Context Advisor). */
public final class HudModel {
	public record Input(boolean linked, Dto.HudState state, String error, SkyBlockContext context, SessionTracker.Snapshot session, boolean showContext,
			boolean showSession, Lang lang, long nowMs) {
	}

	private HudModel() {
	}

	public static List<HudRow> build(Input in) {
		Lang l = in.lang();
		List<HudRow> rows = new ArrayList<>();
		rows.add(HudRow.text("SkyPilot", HudRow.ACCENT));
		if (!in.linked()) {
			rows.add(HudRow.text(l.t("Not linked - /skypilot link", "未連携 - /skypilot link"), HudRow.WARN));
		} else if (in.state() == null) {
			rows.add(HudRow.text(in.error() != null ? in.error() : l.t("Loading roadmap…", "ロードマップを読み込み中…"), in.error() != null ? HudRow.BAD : HudRow.MUTED));
		} else {
			roadmap(rows, in.state(), l);
			if (in.showContext() && in.state().context() != null) context(rows, in.state().context());
			if (in.error() != null) rows.add(HudRow.text(in.error(), HudRow.BAD));
			long age = (in.nowMs() - in.state().updatedAtMs()) / 1000;
			if (in.state().stale() || age > 600) rows.add(HudRow.text(l.t("STALE DATA - re-analyse on the web", "データが古い - Webで再分析してください"), HudRow.WARN));
		}
		if (in.showSession() && in.session() != null && in.session().activeMs() > 60_000) session(rows, in.session(), l);
		return rows;
	}

	private static void roadmap(List<HudRow> rows, Dto.HudState s, Lang l) {
		if (s.message() != null && !s.message().isEmpty()) rows.add(HudRow.text(s.message(), HudRow.MUTED));
		if (s.goalTitle() != null) {
			String overall = s.overallProgress() != null ? String.format(Locale.ROOT, " %.0f%%", s.overallProgress() * 100) : "";
			if (s.overallProgress() != null) {
				rows.add(HudRow.bar(l.t("Goal: ", "目標: ") + s.goalTitle() + overall, HudRow.WHITE, s.overallProgress()));
			} else {
				rows.add(HudRow.text(l.t("Goal: ", "目標: ") + s.goalTitle(), HudRow.WHITE));
			}
		}
		if (s.stepTitle() != null) {
			String idx = s.stepIndex() != null && s.stepCount() != null ? "[" + s.stepIndex() + "/" + s.stepCount() + "] " : "";
			String amount = s.stepCurrent() != null && s.stepTarget() != null
					? " (" + num(s.stepCurrent()) + "/" + num(s.stepTarget()) + (s.stepUnit() != null ? " " + s.stepUnit() : "") + ")"
					: "";
			if (s.stepProgress() != null) {
				rows.add(HudRow.bar(idx + s.stepTitle() + amount, HudRow.WHITE, s.stepProgress()));
			} else {
				rows.add(HudRow.text(idx + s.stepTitle() + amount, HudRow.WHITE));
			}
		}
		if (s.nextTitle() != null) rows.add(HudRow.text(l.t("Next: ", "次: ") + s.nextTitle(), HudRow.MUTED));
		if (s.estimatedCost() != null && s.estimatedCost() > 0) rows.add(HudRow.text(l.t("Cost: ", "費用: ") + Text.coins(s.estimatedCost()), HudRow.MUTED));
		if (s.actions() != null) {
			int n = 0;
			for (String a : s.actions()) {
				if (n++ >= 2) break;
				rows.add(HudRow.text("• " + a, HudRow.MUTED));
			}
		}
	}

	private static void context(List<HudRow> rows, Dto.HudContextBlock c) {
		rows.add(HudRow.text(c.title(), HudRow.ACCENT));
		if (c.lines() == null) return;
		for (Dto.HudLine line : c.lines().subList(0, Math.min(4, c.lines().size()))) {
			rows.add(HudRow.text(line.label().isEmpty() ? line.value() : line.label() + ": " + line.value(), HudRow.WHITE));
		}
	}

	private static void session(List<HudRow> rows, SessionTracker.Snapshot s, Lang l) {
		rows.add(HudRow.text(l.t("Session ", "セッション ") + Text.duration(s.activeMs()), HudRow.ACCENT));
		rows.add(HudRow.text(l.t("Purse: ", "財布: ") + (s.purseEarned() >= 0 ? "+" : "") + Text.coins(s.purseEarned()) + " (" + Text.coins(s.coinsPerHour()) + "/h)",
				s.purseEarned() >= 0 ? HudRow.GOOD : HudRow.BAD));
		s.xpPerHour().entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())).limit(2)
				.forEach(e -> rows.add(HudRow.text(e.getKey() + " XP: " + Text.coins(e.getValue()) + "/h", HudRow.WHITE)));
	}

	private static String num(double v) {
		if (Math.abs(v) >= 1000) return Text.coins(v);
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
	}
}
