package dev.skypilot.mod.translation;

import java.util.ArrayList;
import java.util.List;

/**
 * A chat line as styled text segments. {@code styleId} is an opaque handle the platform layer
 * maps back to the original Minecraft {@code Style} (colour, hover/click events...), so this
 * module can rebuild a line without touching Minecraft classes.
 */
public record StyledLine(List<Segment> segments) {
	/** Style id for the "[JA]" marker the platform renders in a muted colour. */
	public static final int MARKER_STYLE = -1;

	public record Segment(String text, int styleId) {
	}

	public String plain() {
		StringBuilder sb = new StringBuilder();
		for (Segment s : segments) sb.append(s.text());
		return sb.toString();
	}

	/** Split at a character offset of {@link #plain()}: [0, index) and [index, end). */
	public StyledLine[] splitAt(int index) {
		List<Segment> head = new ArrayList<>();
		List<Segment> tail = new ArrayList<>();
		int pos = 0;
		for (Segment s : segments) {
			int end = pos + s.text().length();
			if (end <= index) {
				head.add(s);
			} else if (pos >= index) {
				tail.add(s);
			} else {
				head.add(new Segment(s.text().substring(0, index - pos), s.styleId()));
				tail.add(new Segment(s.text().substring(index - pos), s.styleId()));
			}
			pos = end;
		}
		return new StyledLine[] {new StyledLine(head), new StyledLine(tail)};
	}

	/** Style of the first non-blank segment (used for the translated body). */
	public int firstStyle(int fallback) {
		for (Segment s : segments) {
			if (!s.text().isBlank()) return s.styleId();
		}
		return fallback;
	}

	/**
	 * Translated copy: the prefix (channel tag, ranks, name - with their hover/click styles) is kept
	 * as-is, followed by a marker and the translated body in the body's original style.
	 */
	public static StyledLine translated(StyledLine prefix, StyledLine body, String translatedBody, String marker) {
		List<Segment> out = new ArrayList<>(prefix.segments());
		out.add(new Segment(marker, MARKER_STYLE));
		out.add(new Segment(translatedBody, body.firstStyle(prefix.firstStyle(MARKER_STYLE))));
		return new StyledLine(out);
	}
}
