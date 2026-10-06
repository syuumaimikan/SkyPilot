package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AFK guard rules: which chat lines matter while you are away, and the summary shown when you
 * are back. Notification only - nothing is pressed, moved or sent for you, and there is no
 * "anti-AFK" (that would be input automation).
 */
public final class AfkRules {
	public enum Event {
		PRIVATE_MESSAGE("private message", "個人メッセージ"),
		PARTY_INVITE("party invite", "パーティ招待"),
		MENTION("mention of your name", "名前の呼びかけ"),
		LIMBO("sent to Limbo / AFK notice", "Limbo送り・AFK通知"),
		RESTART("server restart warning", "サーバー再起動の予告"),
		DEATH("death", "死亡"),
		HEALTH("health dropped", "体力低下"),
		DISCONNECT("disconnected", "切断");

		public final String en;
		public final String ja;

		Event(String en, String ja) {
			this.en = en;
			this.ja = ja;
		}
	}

	private AfkRules() {
	}

	/** True once {@code idleMs} without keyboard or mouse input reaches {@code minutes}. */
	public static boolean isAfk(long idleMs, int minutes) {
		return minutes > 0 && idleMs >= minutes * 60_000L;
	}

	/**
	 * The event a chat line stands for, or null. {@code me} is your player name (may be null);
	 * your own messages ("me: …") never count as mentions.
	 */
	public static Event classify(String line, String me) {
		if (line == null) return null;
		String s = Text.strip(line).strip();
		if (s.isEmpty()) return null;
		if (s.startsWith("From ")) return Event.PRIVATE_MESSAGE;
		if (s.contains("has invited you to join their party")) return Event.PARTY_INVITE;
		if (s.equals("You were spawned in Limbo.") || s.startsWith("You are AFK.")) return Event.LIMBO;
		if (s.contains("This server will restart soon") || s.contains("Evacuating to Hub")) return Event.RESTART;
		if (s.startsWith("☠ You ") || s.equals("You died!")) return Event.DEATH;
		if (me != null && me.length() >= 3 && mentions(s, me)) return Event.MENTION;
		return null;
	}

	private static boolean mentions(String s, String me) {
		String lower = s.toLowerCase(Locale.ROOT);
		String name = me.toLowerCase(Locale.ROOT);
		if (lower.contains(name + ":")) return false;
		int i = lower.indexOf(name);
		while (i >= 0) {
			boolean startOk = i == 0 || !isNameChar(lower.charAt(i - 1));
			int end = i + name.length();
			boolean endOk = end >= lower.length() || !isNameChar(lower.charAt(end));
			if (startOk && endOk) return true;
			i = lower.indexOf(name, i + 1);
		}
		return false;
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/** True when health fell below a third of the maximum, or below half of what it was when you left. */
	public static boolean healthAlarm(float health, float max, float atStart) {
		return max > 0 && health > 0 && (health < max / 3f || health < atStart / 2f);
	}

	/** "While you were away (12m): 2× private message, 1× party invite", or null when nothing happened. */
	public static String summary(Map<Event, Integer> counts, long awayMs, Lang l) {
		List<String> parts = new ArrayList<>();
		for (Event e : Event.values()) {
			Integer n = counts.get(e);
			if (n != null && n > 0) parts.add(n + "× " + l.t(e.en, e.ja));
		}
		if (parts.isEmpty()) return null;
		return l.t("While you were away (", "離席中（") + Text.duration(awayMs) + l.t("): ", "）: ") + String.join(", ", parts);
	}
}
