package dev.skypilot.mod.hud;

/** One HUD line. {@code progress} (0..1) draws a bar under the text when non-negative. */
public record HudRow(String text, int color, double progress) {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int MUTED = 0xFFAAAAAA;
	public static final int ACCENT = 0xFF9D8CFF;
	public static final int GOOD = 0xFF55FF55;
	public static final int WARN = 0xFFFFAA00;
	public static final int BAD = 0xFFFF5555;

	public static HudRow text(String text, int color) {
		return new HudRow(text, color, -1);
	}

	public static HudRow bar(String text, int color, double progress) {
		return new HudRow(text, color, Math.max(0, Math.min(1, progress)));
	}
}
