package dev.skypilot.mod.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon run state from the sidebar: the floor ("⏣ The Catacombs (F7)"), "Time Elapsed: 01m 17s"
 * and "Cleared: 45% (123)" (line formats as in SkyHanni's DungeonApi / ScoreboardPattern).
 */
public record DungeonInfo(String floor, Integer elapsedSeconds, Integer clearedPercent) {
	private static final Pattern FLOOR = Pattern.compile("The Catacombs \\((?<floor>[EFM]\\d?)\\)");
	private static final Pattern ELAPSED = Pattern.compile("^Time Elapsed: ((?:\\d+[hms] ?)+)$");
	private static final Pattern PART = Pattern.compile("(\\d+)([hms])");
	private static final Pattern CLEARED = Pattern.compile("^Cleared: (\\d+)%");

	/** Null when the sidebar shows no dungeon. */
	public static DungeonInfo parse(List<String> sidebar) {
		String floor = null;
		Integer elapsed = null;
		Integer cleared = null;
		for (String raw : sidebar) {
			String s = Text.strip(raw).strip();
			Matcher f = FLOOR.matcher(s);
			if (floor == null && f.find()) floor = f.group("floor");
			Matcher e = ELAPSED.matcher(s);
			if (e.matches()) {
				int secs = 0;
				Matcher p = PART.matcher(e.group(1));
				while (p.find()) {
					int n = Integer.parseInt(p.group(1));
					secs += switch (p.group(2)) {
						case "h" -> n * 3600;
						case "m" -> n * 60;
						default -> n;
					};
				}
				elapsed = secs;
			}
			Matcher c = CLEARED.matcher(s);
			if (c.find()) cleared = Integer.parseInt(c.group(1));
		}
		return floor == null && elapsed == null ? null : new DungeonInfo(floor, elapsed, cleared);
	}
}
