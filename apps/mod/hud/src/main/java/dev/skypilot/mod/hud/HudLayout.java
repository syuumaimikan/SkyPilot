package dev.skypilot.mod.hud;

/** Position/size maths for the HUD box, kept free of Minecraft types so it can be unit-tested. */
public final class HudLayout {
	public static final int PADDING = 4;
	public static final int LINE_GAP = 2;
	public static final int BAR_HEIGHT = 2;

	public record Box(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + width && py < y + height;
		}
	}

	private HudLayout() {
	}

	/** Unscaled content height for the given rows. */
	public static int contentHeight(java.util.List<HudRow> rows, int lineHeight) {
		int h = 0;
		for (HudRow r : rows) h += lineHeight + LINE_GAP + (r.progress() >= 0 ? BAR_HEIGHT + 1 : 0);
		return Math.max(0, h - LINE_GAP) + PADDING * 2;
	}

	/**
	 * Final on-screen box (GUI-scaled pixels) for content of {@code contentW x contentH} drawn at
	 * {@code scale}, keeping it fully inside the screen.
	 */
	public static Box place(int x, int y, int contentW, int contentH, double scale, int screenW, int screenH) {
		int w = (int) Math.ceil(contentW * scale);
		int h = (int) Math.ceil(contentH * scale);
		int cx = Math.max(0, Math.min(x, screenW - w));
		int cy = Math.max(0, Math.min(y, screenH - h));
		return new Box(cx, cy, w, h);
	}

	/** ARGB background colour for an opacity 0..1. */
	public static int background(double opacity) {
		int a = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
		return (a << 24) | 0x101018;
	}
}
