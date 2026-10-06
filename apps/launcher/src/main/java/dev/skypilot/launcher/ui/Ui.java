package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Diagnostics;
import dev.skypilot.launcher.LauncherLog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Small helpers shared by the pages. */
final class Ui {
	private Ui() {
	}

	static String esc(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
	}

	static JLabel label(String text, float size, int style, Color color) {
		JLabel l = new JLabel(text);
		l.setFont(Theme.font(style, size));
		l.setForeground(color);
		return l;
	}

	static JLabel title(String text) {
		return label(text, 24f, Font.BOLD, Theme.TEXT);
	}

	static JLabel muted(String text) {
		return label(text, 12.5f, Font.PLAIN, Theme.MUTED);
	}

	/** Text that wraps to the available width. */
	static JTextArea wrap(String text, Color color, float size) {
		JTextArea a = new JTextArea(text);
		a.setEditable(false);
		a.setFocusable(false);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setOpaque(false);
		a.setBorder(null);
		a.setForeground(color);
		a.setFont(Theme.font(Font.PLAIN, size));
		return a;
	}

	/** Page header: title, subtitle and actions on the right. */
	static JComponent header(String title, String subtitle, JComponent... actions) {
		JPanel h = new JPanel(new BorderLayout(16, 0));
		h.setOpaque(false);
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		JLabel t = title(title);
		t.setAlignmentX(0);
		left.add(t);
		if (subtitle != null) {
			left.add(Box.createVerticalStrut(4));
			JTextArea s = wrap(subtitle, Theme.MUTED, 13f);
			s.setAlignmentX(0);
			left.add(s);
		}
		h.add(left, BorderLayout.CENTER);
		if (actions.length > 0) {
			JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
			right.setOpaque(false);
			for (JComponent a : actions) right.add(a);
			JPanel wrapRight = new JPanel(new BorderLayout());
			wrapRight.setOpaque(false);
			wrapRight.add(right, BorderLayout.NORTH);
			h.add(wrapRight, BorderLayout.EAST);
		}
		return h;
	}

	/** A panel that fills the scroll pane's width (so wrapped text wraps) and scrolls vertically. */
	static final class ScrollBody extends JPanel implements Scrollable {
		ScrollBody(java.awt.LayoutManager layout) {
			super(layout);
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
			return 24;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
			return r.height - 40;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	static JScrollPane scroll(JComponent body) {
		JScrollPane sp = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.setOpaque(false);
		sp.getViewport().setOpaque(false);
		sp.getVerticalScrollBar().setUnitIncrement(24);
		return sp;
	}

	/** Page padding. */
	static JPanel page() {
		JPanel p = new JPanel(new BorderLayout(0, 18));
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(26, 32, 26, 32));
		return p;
	}

	/** Opens only http(s) links. */
	static boolean browse(String url) {
		try {
			String u = url == null ? "" : url.strip();
			if (!u.toLowerCase(Locale.ROOT).startsWith("https://") && !u.toLowerCase(Locale.ROOT).startsWith("http://")) return false;
			Desktop.getDesktop().browse(URI.create(u));
			return true;
		} catch (Exception e) {
			LauncherLog.error("cannot open " + url, e);
			return false;
		}
	}

	static void openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			Desktop.getDesktop().open(dir.toFile());
		} catch (Exception e) {
			LauncherLog.error("cannot open folder " + dir, e);
		}
	}

	static void open(Path file) {
		try {
			Desktop.getDesktop().open(file.toFile());
		} catch (Exception e) {
			LauncherLog.error("cannot open " + file, e);
		}
	}

	static void copy(String text) {
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		} catch (Exception e) {
			LauncherLog.error("clipboard unavailable", e);
		}
	}

	/** Drops characters the font has no glyph for (Hypixel's private symbols would show as boxes). */
	static String displayable(String text, Font font) {
		if (text == null) return "";
		StringBuilder b = new StringBuilder(text.length());
		text.codePoints().forEach(cp -> {
			if (Character.isWhitespace(cp) || font.canDisplay(cp)) b.appendCodePoint(cp);
		});
		return b.toString().replaceAll(" {2,}", " ").strip();
	}

	static String size(long bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
		if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024);
		return String.format(Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024 / 1024);
	}

	static String shortPath(Path p) {
		return Diagnostics.anonymize(p.toString());
	}

	/** "12m", "3h 05m", "2d 4h". */
	static String duration(long ms) {
		long m = Math.max(0, ms) / 60_000;
		if (m < 60) return m + "m";
		long h = m / 60;
		if (h < 24) return String.format(Locale.ROOT, "%dh %02dm", h, m % 60);
		return (h / 24) + "d " + (h % 24) + "h";
	}
}
