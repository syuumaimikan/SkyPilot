package dev.skypilot.launcher.ui;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** The game folder's screenshots as a gallery, with a viewer (arrows, copy, open). */
final class ScreenshotsPage {
	private static final int MAX = 120;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final Ctx c;
	private final JPanel grid = new JPanel(new GridLayout(0, 4, 14, 14));
	private final JLabel count = Ui.muted("");
	private List<Path> files = List.of();
	private final JComponent root;

	ScreenshotsPage(Ctx c) {
		this.c = c;
		root = build();
		reload();
	}

	JComponent view() {
		return root;
	}

	private JComponent build() {
		JPanel page = Ui.page();
		JButton refresh = Widgets.button(c.t("Refresh", "再読み込み"), "refresh");
		refresh.addActionListener(e -> reload());
		JButton folder = Widgets.button(c.t("Folder", "フォルダ"), "folder");
		folder.addActionListener(e -> Ui.openFolder(dir()));
		page.add(Ui.header(c.t("Screenshots", "スクリーンショット"), c.t("Pictures you take in game with F2.", "ゲーム内でF2を押して撮った画像です。"), count, refresh, folder), BorderLayout.NORTH);
		grid.setOpaque(false);
		Ui.ScrollBody body = new Ui.ScrollBody(new BorderLayout());
		body.add(grid, BorderLayout.NORTH);
		page.add(Ui.scroll(body), BorderLayout.CENTER);
		return page;
	}

	private Path dir() {
		return c.settings.instance().resolve("screenshots");
	}

	void reload() {
		List<Path> list = new ArrayList<>();
		if (Files.isDirectory(dir())) {
			try (Stream<Path> s = Files.list(dir())) {
				s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")).sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
						.limit(MAX).forEach(list::add);
			} catch (Exception e) {
				c.fail(c.t("Could not read screenshots", "スクリーンショットを読めませんでした"), e);
			}
		}
		files = list;
		count.setText(c.t(list.size() + " pictures", list.size() + " 枚"));
		grid.removeAll();
		if (list.isEmpty()) {
			grid.setLayout(new BorderLayout());
			grid.add(Ui.muted(c.t("No screenshots yet. Press F2 in game.", "まだありません。ゲーム内でF2を押すと撮影できます。")), BorderLayout.NORTH);
		} else {
			grid.setLayout(new GridLayout(0, 4, 14, 14));
			for (int i = 0; i < list.size(); i++) grid.add(tile(i));
		}
		grid.revalidate();
		grid.repaint();
	}

	private JComponent tile(int index) {
		Path f = files.get(index);
		JPanel tile = new JPanel(new BorderLayout()) {
			Image thumb;
			boolean over;

			{
				setOpaque(false);
				setPreferredSize(new Dimension(220, 150));
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				c.async(() -> thumbnail(f, 440), img -> {
					thumb = img;
					repaint();
				}, e -> {
				});
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

					@Override
					public void mouseClicked(MouseEvent e) {
						viewer(index);
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = Widgets.smooth(g0);
				int w = getWidth(), h = getHeight();
				RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, h, 16, 16);
				g.setColor(Theme.SURFACE_2);
				g.fill(shape);
				if (thumb != null) {
					g.setClip(shape);
					double s = Math.max((double) w / thumb.getWidth(null), (double) h / thumb.getHeight(null));
					int iw = (int) (thumb.getWidth(null) * s), ih = (int) (thumb.getHeight(null) * s);
					g.drawImage(thumb, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
					g.setClip(null);
				}
				if (over) {
					g.setColor(new Color(0, 0, 0, 120));
					g.fill(new RoundRectangle2D.Double(0, h - 30, w, 30, 0, 0));
					g.setClip(shape);
					g.fill(new java.awt.Rectangle(0, h - 30, w, 30));
					g.setClip(null);
					g.setColor(Color.WHITE);
					g.setFont(Theme.font(Font.PLAIN, 11.5f));
					g.drawString(DATE.format(Instant.ofEpochMilli(f.toFile().lastModified())), 10, h - 11);
				}
				g.setColor(over ? Theme.ACCENT : Theme.BORDER);
				g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 16, 16));
				g.dispose();
			}
		};
		return tile;
	}

	/** Subsampled decode, so a 4K screenshot doesn't need 30 MB for a thumbnail. */
	static BufferedImage thumbnail(Path f, int width) throws Exception {
		try (ImageInputStream in = ImageIO.createImageInputStream(f.toFile())) {
			var readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) return null;
			ImageReader r = readers.next();
			try {
				r.setInput(in);
				int w = r.getWidth(0);
				var p = r.getDefaultReadParam();
				int step = Math.max(1, w / width);
				p.setSourceSubsampling(step, step, 0, 0);
				return r.read(0, p);
			} finally {
				r.dispose();
			}
		}
	}

	private void viewer(int start) {
		JDialog d = new JDialog(SwingUtilities.getWindowAncestor(root), c.t("Screenshot", "スクリーンショット"), java.awt.Dialog.ModalityType.MODELESS);
		int[] i = {start};
		BufferedImage[] img = {null};
		JLabel name = Ui.label("", 13f, Font.BOLD, Color.WHITE);
		JPanel canvas = new JPanel() {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = Widgets.smooth(g0);
				g.setColor(new Color(8, 8, 12));
				g.fillRect(0, 0, getWidth(), getHeight());
				if (img[0] != null) {
					double s = Math.min((double) getWidth() / img[0].getWidth(), (double) getHeight() / img[0].getHeight());
					int w = (int) (img[0].getWidth() * s), h = (int) (img[0].getHeight() * s);
					g.drawImage(img[0], (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
				}
				g.dispose();
			}
		};
		Runnable load = () -> {
			Path f = files.get(i[0]);
			name.setText(f.getFileName() + "   ·   " + (i[0] + 1) + " / " + files.size());
			c.async(() -> ImageIO.read(f.toFile()), im -> {
				img[0] = im;
				canvas.repaint();
			}, e -> c.fail(c.t("Could not open", "開けませんでした"), e));
		};
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(new Color(16, 16, 22));
		bar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
		bar.add(name, BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);
		JButton prev = Widgets.button("", "chevron");
		prev.setIcon(new javax.swing.Icon() {
			final javax.swing.Icon base = Icons.of("chevron", 16);

			@Override
			public void paintIcon(java.awt.Component comp, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.rotate(Math.PI, x + 8, y + 8);
				base.paintIcon(comp, g2, x, y);
				g2.dispose();
			}

			@Override
			public int getIconWidth() {
				return 16;
			}

			@Override
			public int getIconHeight() {
				return 16;
			}
		});
		JButton next = Widgets.button("", "chevron");
		JButton copy = Widgets.button(c.t("Copy", "コピー"), "copy");
		JButton open = Widgets.button(c.t("Open", "開く"), "external");
		JButton show = Widgets.button(c.t("Folder", "フォルダ"), "folder");
		prev.addActionListener(e -> {
			i[0] = (i[0] - 1 + files.size()) % files.size();
			load.run();
		});
		next.addActionListener(e -> {
			i[0] = (i[0] + 1) % files.size();
			load.run();
		});
		copy.addActionListener(e -> {
			if (img[0] == null) return;
			BufferedImage im = img[0];
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[] {DataFlavor.imageFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor fl) {
					return DataFlavor.imageFlavor.equals(fl);
				}

				@Override
				public Object getTransferData(DataFlavor fl) {
					return im;
				}
			}, null);
			c.ok(c.t("Copied", "コピーしました"), files.get(i[0]).getFileName().toString());
		});
		open.addActionListener(e -> Ui.open(files.get(i[0])));
		show.addActionListener(e -> Ui.openFolder(dir()));
		for (JButton b : new JButton[] {prev, next, copy, open, show}) buttons.add(b);
		bar.add(buttons, BorderLayout.EAST);
		d.getContentPane().setLayout(new BorderLayout());
		d.getContentPane().add(canvas, BorderLayout.CENTER);
		d.getContentPane().add(bar, BorderLayout.SOUTH);
		var keys = d.getRootPane();
		keys.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "prev");
		keys.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "next");
		keys.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
		keys.getActionMap().put("prev", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				prev.doClick();
			}
		});
		keys.getActionMap().put("next", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				next.doClick();
			}
		});
		keys.getActionMap().put("close", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				d.dispose();
			}
		});
		d.setSize(1100, 700);
		d.setLocationRelativeTo(root);
		load.run();
		d.setVisible(true);
	}
}
