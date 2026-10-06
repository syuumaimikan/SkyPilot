package dev.skypilot.launcher.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** The launcher's own components: cards, the Play button, switches, sidebar items, chips, badges, toasts. */
final class Widgets {
	private Widgets() {
	}

	static Graphics2D smooth(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		return g;
	}

	// ---------------------------------------------------------------- card

	/** A rounded panel with an optional header (title, icon, right-hand component). */
	static class Card extends JPanel {
		private final int radius;
		Color fill = null;
		Color stroke = null;

		Card(LayoutManager layout) {
			this(layout, 16);
		}

		Card(LayoutManager layout, int radius) {
			super(layout);
			this.radius = radius;
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = smooth(g0);
			g.setColor(fill != null ? fill : Theme.SURFACE);
			g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));
			g.setColor(stroke != null ? stroke : Theme.BORDER);
			g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, radius, radius));
			g.dispose();
		}
	}

	/** A card with a small title row and a body. */
	static Card card(String icon, String title, JComponent right, JComponent body) {
		Card c = new Card(new BorderLayout(0, 12));
		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setOpaque(false);
		JLabel t = new JLabel(title.toUpperCase(java.util.Locale.ROOT), icon == null ? null : Icons.of(icon, 16, Theme.ACCENT), SwingConstants.LEFT);
		t.setIconTextGap(8);
		t.setFont(Theme.font(Font.BOLD, 11.5f));
		t.setForeground(Theme.MUTED);
		head.add(t, BorderLayout.WEST);
		if (right != null) head.add(right, BorderLayout.EAST);
		c.add(head, BorderLayout.NORTH);
		if (body != null) c.add(body, BorderLayout.CENTER);
		return c;
	}

	// ---------------------------------------------------------------- big Play button

	static class PlayButton extends JButton {
		private float hover;
		private final Timer anim;
		private boolean over;
		String sub = "";
		boolean busy;
		private float phase;

		PlayButton(String text) {
			super(text);
			setFont(Theme.font(Font.BOLD, 20f));
			setForeground(Color.WHITE);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setPreferredSize(new Dimension(260, 64));
			anim = new Timer(16, e -> {
				float target = over && isEnabled() ? 1f : 0f;
				hover += (target - hover) * 0.25f;
				if (busy) phase = (phase + 2f) % 40f;
				if (Math.abs(target - hover) < 0.01f && !busy) ((Timer) e.getSource()).stop();
				repaint();
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					over = true;
					anim.start();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					over = false;
					anim.start();
				}
			});
		}

		void setBusy(boolean b) {
			busy = b;
			setEnabled(!b);
			if (b) anim.start();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = smooth(g0);
			int w = getWidth(), h = getHeight();
			Color a = isEnabled() || busy ? Theme.ACCENT : Theme.mix(Theme.ACCENT, Theme.SURFACE_2, 0.6);
			Color b = isEnabled() || busy ? Theme.ACCENT_2 : Theme.mix(Theme.ACCENT_2, Theme.SURFACE_2, 0.6);
			if (hover > 0) {
				g.setColor(Theme.alpha(Theme.ACCENT, (int) (70 * hover)));
				for (int i = 1; i <= 3; i++) g.fill(new RoundRectangle2D.Double(-i * 2 + 6, -i * 2 + 6, w + i * 4 - 12, h + i * 4 - 12, 20 + i * 2, 20 + i * 2));
			}
			RoundRectangle2D shape = new RoundRectangle2D.Double(4, 4, w - 8, h - 8, 18, 18);
			g.setPaint(new GradientPaint(0, 0, Theme.mix(a, Color.WHITE, hover * 0.12), w, h, Theme.mix(b, Color.WHITE, hover * 0.12)));
			g.fill(shape);
			if (busy) {
				g.setClip(shape);
				g.setColor(new Color(255, 255, 255, 38));
				g.setStroke(new BasicStroke(10f));
				for (float x = -h + phase - 40; x < w + h; x += 40) g.drawLine((int) x, h, (int) (x + h), 0);
				g.setClip(null);
			}
			g.setColor(new Color(255, 255, 255, 50));
			g.draw(new RoundRectangle2D.Double(4.5, 4.5, w - 9, h - 9, 18, 18));
			Icon icon = busy ? null : getIcon();
			var fm = g.getFontMetrics(getFont());
			String text = getText();
			int tw = fm.stringWidth(text) + (icon != null ? icon.getIconWidth() + 10 : 0);
			int tx = (w - tw) / 2;
			int ty = sub.isEmpty() ? (h + fm.getAscent() - fm.getDescent()) / 2 : h / 2 - 1;
			if (icon != null) {
				icon.paintIcon(this, g, tx, (sub.isEmpty() ? h / 2 : h / 2 - 7) - icon.getIconHeight() / 2);
				tx += icon.getIconWidth() + 10;
			}
			g.setColor(Color.WHITE);
			g.setFont(getFont());
			g.drawString(text, tx, ty);
			if (!sub.isEmpty()) {
				g.setFont(Theme.font(Font.PLAIN, 11.5f));
				var sm = g.getFontMetrics();
				g.setColor(new Color(255, 255, 255, 200));
				g.drawString(sub, (w - sm.stringWidth(sub)) / 2, h / 2 + 15);
			}
			g.dispose();
		}
	}

	// ---------------------------------------------------------------- switch

	static class Toggle extends JComponent {
		private boolean on;
		private float pos;
		private final Timer anim;
		private final List<ActionListener> listeners = new ArrayList<>();

		Toggle(boolean on) {
			this.on = on;
			this.pos = on ? 1 : 0;
			setPreferredSize(new Dimension(40, 22));
			setMaximumSize(new Dimension(40, 22));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			anim = new Timer(15, e -> {
				float t = this.on ? 1 : 0;
				pos += (t - pos) * 0.35f;
				if (Math.abs(t - pos) < 0.02f) {
					pos = t;
					((Timer) e.getSource()).stop();
				}
				repaint();
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (!isEnabled()) return;
					setOn(!Toggle.this.on);
					for (ActionListener l : listeners) l.actionPerformed(new java.awt.event.ActionEvent(Toggle.this, 0, "toggle"));
				}
			});
		}

		boolean isOn() {
			return on;
		}

		void setOn(boolean v) {
			on = v;
			anim.start();
		}

		void onChange(ActionListener l) {
			listeners.add(l);
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = smooth(g0);
			int w = 40, h = 22;
			int y = (getHeight() - h) / 2;
			Color track = Theme.mix(Theme.mix(Theme.SURFACE_2, Theme.BORDER, 0.5), Theme.ACCENT, pos);
			if (!isEnabled()) track = Theme.mix(track, Theme.SURFACE, 0.5);
			g.setColor(track);
			g.fill(new RoundRectangle2D.Double(0, y, w, h, h, h));
			double kx = 3 + pos * (w - h);
			g.setColor(isEnabled() ? Color.WHITE : Theme.FAINT);
			g.fill(new Ellipse2D.Double(kx, y + 3, h - 6, h - 6));
			g.dispose();
		}
	}

	// ---------------------------------------------------------------- sidebar item

	static class NavButton extends JToggleButton {
		private boolean over;

		NavButton(String text, String icon) {
			super(text, Icons.of(icon, 19));
			setHorizontalAlignment(SwingConstants.LEFT);
			setIconTextGap(12);
			setFont(Theme.font(Font.PLAIN, 13.5f));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 12));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setAlignmentX(LEFT_ALIGNMENT);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					over = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					over = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = smooth(g0);
			if (isSelected() || over) {
				g.setColor(isSelected() ? Theme.alpha(Theme.ACCENT, Theme.dark ? 40 : 34) : Theme.alpha(Theme.TEXT, 14));
				g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
			}
			if (isSelected()) {
				g.setColor(Theme.ACCENT);
				g.fill(new RoundRectangle2D.Double(0, 9, 3.5, getHeight() - 18, 3, 3));
			}
			setForeground(isSelected() ? Theme.TEXT : Theme.MUTED);
			g.dispose();
			super.paintComponent(g0);
		}
	}

	// ---------------------------------------------------------------- chips and badges

	static JToggleButton chip(String text) {
		JToggleButton b = new JToggleButton(text);
		b.putClientProperty("JButton.buttonType", "roundRect");
		b.setFont(Theme.font(Font.PLAIN, 12f));
		b.setFocusPainted(false);
		b.setMargin(new Insets(3, 12, 3, 12));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	static JLabel badge(String text, Color color) {
		JLabel l = new JLabel(text) {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = smooth(g0);
				g.setColor(Theme.alpha(color, Theme.dark ? 46 : 36));
				g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
				g.dispose();
				super.paintComponent(g0);
			}
		};
		l.setForeground(color);
		l.setFont(Theme.font(Font.BOLD, 10.5f));
		l.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		return l;
	}

	/** A coloured dot (status pill). */
	static JLabel pill(String text, Color color) {
		JLabel l = new JLabel(text, new Icon() {
			@Override
			public void paintIcon(java.awt.Component c, Graphics g0, int x, int y) {
				Graphics2D g = smooth(g0);
				g.setColor(Theme.alpha(color, 90));
				g.fill(new Ellipse2D.Double(x, y, 10, 10));
				g.setColor(color);
				g.fill(new Ellipse2D.Double(x + 2, y + 2, 6, 6));
				g.dispose();
			}

			@Override
			public int getIconWidth() {
				return 10;
			}

			@Override
			public int getIconHeight() {
				return 10;
			}
		}, SwingConstants.LEFT) {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = smooth(g0);
				g.setColor(new Color(0, 0, 0, Theme.dark ? 90 : 40));
				g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
				g.dispose();
				super.paintComponent(g0);
			}
		};
		l.setIconTextGap(8);
		l.setForeground(Color.WHITE);
		l.setFont(Theme.font(Font.BOLD, 12f));
		l.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 12));
		return l;
	}

	/** A flat button with an icon. */
	static JButton button(String text, String icon) {
		JButton b = new JButton(text, icon == null ? null : Icons.of(icon, 16));
		b.setIconTextGap(8);
		b.setFocusPainted(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setMargin(new Insets(6, 12, 6, 14));
		return b;
	}

	static JButton primary(String text, String icon) {
		JButton b = button(text, icon);
		b.putClientProperty("JButton.buttonType", "default");
		b.setForeground(Color.WHITE);
		b.setBackground(Theme.ACCENT);
		return b;
	}

	static JButton link(String text, String icon) {
		JButton b = button(text, icon);
		b.putClientProperty("JButton.buttonType", "borderless");
		b.setForeground(Theme.ACCENT);
		return b;
	}

	/** Round image (player head, mod icon) with a fallback letter. */
	static JComponent roundImage(Image img, int size, String fallback, boolean circle) {
		return new JComponent() {
			{
				setPreferredSize(new Dimension(size, size));
				setMinimumSize(new Dimension(size, size));
				setMaximumSize(new Dimension(size, size));
			}

			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = smooth(g0);
				java.awt.Shape clip = circle ? new Ellipse2D.Double(0, 0, size, size) : new RoundRectangle2D.Double(0, 0, size, size, size / 3.5, size / 3.5);
				g.setColor(Theme.SURFACE_2);
				g.fill(clip);
				if (img != null) {
					g.setClip(clip);
					boolean pixel = img.getWidth(null) <= 32;
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, pixel ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					g.drawImage(img, 0, 0, size, size, null);
				} else {
					g.setPaint(new GradientPaint(0, 0, Theme.ACCENT, size, size, Theme.ACCENT_2));
					g.fill(clip);
					g.setColor(Color.WHITE);
					g.setFont(Theme.font(Font.BOLD, size * 0.45f));
					var fm = g.getFontMetrics();
					String t = fallback == null || fallback.isEmpty() ? "?" : fallback.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
					g.drawString(t, (size - fm.stringWidth(t)) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
				}
				g.dispose();
			}
		};
	}

	static BufferedImage read(byte[] png) {
		if (png == null) return null;
		try {
			return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
		} catch (Exception e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- toasts

	/** A card that fades in and out. */
	static final class Toast extends Card {
		float alpha;

		Toast() {
			super(new BorderLayout(12, 0), 14);
		}

		@Override
		public void paint(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
			super.paint(g);
			g.dispose();
		}
	}

	/** Small messages in the bottom-right corner that fade out by themselves. */
	static final class Toasts {
		private final JLayeredPane layer;
		private final List<JComponent> shown = new ArrayList<>();

		Toasts(JLayeredPane layer) {
			this.layer = layer;
		}

		void show(String icon, Color color, String title, String text, String actionText, Runnable action) {
			if (layer == null) return;
			Toast t = new Toast();
			t.fill = Theme.SURFACE_2;
			t.stroke = Theme.alpha(color, 160);
			t.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
			t.add(new JLabel(Icons.of(icon, 22, color)), BorderLayout.WEST);
			JPanel body = new JPanel();
			body.setOpaque(false);
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			JLabel tl = new JLabel(title);
			tl.setFont(Theme.font(Font.BOLD, 13f));
			body.add(tl);
			if (text != null && !text.isEmpty()) {
				JLabel tx = new JLabel("<html><div style='width:250px'>" + Ui.esc(text) + "</div></html>");
				tx.setForeground(Theme.MUTED);
				body.add(Box.createVerticalStrut(2));
				body.add(tx);
			}
			t.add(body, BorderLayout.CENTER);
			if (action != null) {
				JButton a = link(actionText, null);
				a.addActionListener(e -> {
					action.run();
					remove(t);
				});
				JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
				r.setOpaque(false);
				r.add(a);
				t.add(r, BorderLayout.EAST);
			}
			t.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					remove(t);
				}
			});
			layer.add(t, JLayeredPane.POPUP_LAYER);
			shown.add(0, t);
			relayout();
			long start = System.currentTimeMillis();
			long life = action != null ? 9000 : 5000;
			new Timer(16, e -> {
				long age = System.currentTimeMillis() - start;
				t.alpha = age < 200 ? age / 200f : age > life ? 1f - (age - life) / 300f : 1f;
				t.repaint();
				if (age > life + 300 || !shown.contains(t)) {
					((Timer) e.getSource()).stop();
					remove(t);
				}
			}).start();
		}

		private void remove(JComponent t) {
			if (shown.remove(t)) {
				layer.remove(t);
				relayout();
				layer.repaint();
			}
		}

		private void relayout() {
			int y = layer.getHeight() - 20;
			for (JComponent t : shown) {
				Dimension d = t.getPreferredSize();
				int w = Math.max(320, Math.min(420, d.width));
				int h = t.getPreferredSize().height;
				y -= h;
				t.setBounds(layer.getWidth() - w - 20, y, w, h);
				y -= 10;
			}
			layer.revalidate();
			layer.repaint();
		}
	}

	static AbstractButton plain(AbstractButton b) {
		b.setContentAreaFilled(false);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		return b;
	}
}
