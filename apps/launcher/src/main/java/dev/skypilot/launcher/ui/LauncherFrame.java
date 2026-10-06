package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Avatar;
import dev.skypilot.launcher.Env;
import dev.skypilot.launcher.LauncherLog;
import dev.skypilot.launcher.LauncherMain;
import dev.skypilot.launcher.LauncherSettings;
import dev.skypilot.launcher.Net;
import dev.skypilot.launcher.Pins;
import dev.skypilot.launcher.SingleInstance;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The launcher window: sidebar (pages, player card, versions) and the pages. Pages are built when
 * first shown and refreshed each time they are shown again.
 */
public final class LauncherFrame {
	private record PageDef(String id, String icon, String en, String ja, Function<Ctx, Object> make) {
	}

	private static final List<PageDef> PAGES = List.of(
			new PageDef("home", "home", "Home", "ホーム", HomePage::new),
			new PageDef("mods", "mods", "Mods", "MOD", ModsPage::new),
			new PageDef("features", "features", "Features", "機能", FeaturesPage::new),
			new PageDef("screenshots", "image", "Screenshots", "スクリーンショット", ScreenshotsPage::new),
			new PageDef("logs", "terminal", "Logs & crashes", "ログとクラッシュ", LogsPage::new),
			new PageDef("tools", "tools", "Tools", "ツール", ToolsPage::new),
			new PageDef("settings", "settings", "Settings", "設定", SettingsPage::new),
			new PageDef("about", "info", "About", "このランチャーについて", AboutPage::new));

	private final Ctx c;
	private final JPanel cards = new JPanel(new CardLayout());
	private final Map<String, Object> built = new LinkedHashMap<>();
	private final Map<String, Widgets.NavButton> nav = new LinkedHashMap<>();
	private final JPanel playerCard = new JPanel(new BorderLayout(10, 0));
	private String current;

	private LauncherFrame(Ctx c) {
		this.c = c;
	}

	public static void open(LauncherSettings s, Pins pins) {
		Path dir = Env.launcherDir();
		LauncherLog.init(dir);
		LauncherLog.info("launcher " + Net.versionName() + " starting");
		Theme.apply(!"light".equals(s.theme));
		if (!SingleInstance.acquire(dir)) {
			JOptionPane.showMessageDialog(null, LauncherMain.lang(s).t("SkyPilot Launcher is already open.", "SkyPilot Launcher はすでに開いています。"), "SkyPilot Launcher",
					JOptionPane.INFORMATION_MESSAGE);
			System.exit(0);
		}
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> LauncherLog.error("uncaught on " + t.getName(), e));
		SwingUtilities.invokeLater(() -> {
			JFrame f = new JFrame("SkyPilot Launcher");
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			f.setIconImages(icons());
			f.setMinimumSize(new Dimension(1040, 680));
			Runnable[] build = new Runnable[1];
			build[0] = () -> {
				Theme.apply(!"light".equals(s.theme));
				Ctx ctx = new Ctx(s, pins, LauncherMain.lang(s), dir);
				ctx.toasts = new Widgets.Toasts(f.getLayeredPane());
				ctx.rebuild = () -> SwingUtilities.invokeLater(build[0]);
				f.setContentPane(new LauncherFrame(ctx).content(s.lastPage));
				SwingUtilities.updateComponentTreeUI(f);
				f.revalidate();
				f.repaint();
			};
			build[0].run();
			if (s.windowW >= 1040 && s.windowH >= 680) {
				f.setBounds(s.windowX, s.windowY, s.windowW, s.windowH);
			} else {
				f.setSize(1240, 780);
				f.setLocationRelativeTo(null);
			}
			f.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					if ((f.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
						s.windowX = f.getX();
						s.windowY = f.getY();
						s.windowW = f.getWidth();
						s.windowH = f.getHeight();
					}
					try {
						s.save(dir);
					} catch (Exception ex) {
						LauncherLog.error("settings not saved", ex);
					}
				}
			});
			f.setVisible(true);
		});
	}

	/** The whole window content (also painted to images by the tests). */
	public static JComponent createContent(LauncherSettings s, Pins pins, String page) {
		return createContent(s, pins, page, Env.launcherDir());
	}

	public static JComponent createContent(LauncherSettings s, Pins pins, String page, Path launcherDir) {
		Theme.apply(!"light".equals(s.theme));
		Ctx ctx = new Ctx(s, pins, LauncherMain.lang(s), launcherDir);
		return new LauncherFrame(ctx).content(page);
	}

	static Image icon() {
		try (InputStream in = LauncherFrame.class.getResourceAsStream("/bundled/icon.png")) {
			return in == null ? null : ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	private static List<Image> icons() {
		List<Image> out = new ArrayList<>();
		Image base = icon();
		if (base != null) for (int sz : new int[] {16, 24, 32, 48, 64, 128}) out.add(base.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));
		return out;
	}

	private JComponent content(String page) {
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Theme.BG);
		root.add(sidebar(), BorderLayout.WEST);
		cards.setOpaque(true);
		cards.setBackground(Theme.BG);
		root.add(cards, BorderLayout.CENTER);
		c.navigate = this::show;
		show(PAGES.stream().anyMatch(p -> p.id().equals(page)) ? page : "home");
		return root;
	}

	private void show(String id) {
		PageDef def = PAGES.stream().filter(p -> p.id().equals(id)).findFirst().orElse(PAGES.get(0));
		Object page = built.get(def.id());
		if (page == null) {
			page = def.make().apply(c);
			built.put(def.id(), page);
			cards.add(view(page), def.id());
		} else if (!def.id().equals(current)) {
			refresh(page);
		}
		((CardLayout) cards.getLayout()).show(cards, def.id());
		current = def.id();
		c.settings.lastPage = def.id();
		Widgets.NavButton b = nav.get(def.id());
		if (b != null) b.setSelected(true);
	}

	private static JComponent view(Object page) {
		if (page instanceof HomePage p) return p.view();
		if (page instanceof ModsPage p) return p.view();
		if (page instanceof FeaturesPage p) return p.view();
		if (page instanceof ScreenshotsPage p) return p.view();
		if (page instanceof LogsPage p) return p.view();
		if (page instanceof ToolsPage p) return p.view();
		if (page instanceof SettingsPage p) return p.view();
		return ((AboutPage) page).view();
	}

	private static void refresh(Object page) {
		if (page instanceof HomePage p) p.refresh();
		else if (page instanceof ModsPage p) p.reload();
		else if (page instanceof FeaturesPage p) p.reload();
		else if (page instanceof ScreenshotsPage p) p.reload();
		else if (page instanceof LogsPage p) p.reload();
		else if (page instanceof ToolsPage p) p.reload();
	}

	private JComponent sidebar() {
		JPanel side = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0.create();
				g.setColor(Theme.SIDEBAR);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(Theme.BORDER);
				g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
				g.dispose();
			}
		};
		side.setPreferredSize(new Dimension(236, 10));
		side.setBorder(BorderFactory.createEmptyBorder(20, 14, 16, 14));
		JPanel top = new JPanel();
		top.setOpaque(false);
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		Image icon = icon();
		JLabel logo = new JLabel("SkyPilot", icon == null ? null : new ImageIcon(icon.getScaledInstance(34, 34, Image.SCALE_SMOOTH)), SwingConstants.LEFT);
		logo.setFont(Theme.font(Font.BOLD, 19f));
		logo.setForeground(Theme.TEXT);
		logo.setIconTextGap(11);
		logo.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
		logo.setAlignmentX(0);
		top.add(logo);
		JLabel sub = Ui.label(c.t("LAUNCHER", "ランチャー"), 10.5f, Font.BOLD, Theme.ACCENT);
		sub.setBorder(BorderFactory.createEmptyBorder(2, 51, 0, 0));
		sub.setAlignmentX(0);
		top.add(sub);
		top.add(Box.createVerticalStrut(24));
		ButtonGroup group = new ButtonGroup();
		for (PageDef p : PAGES) {
			Widgets.NavButton b = new Widgets.NavButton(c.t(p.en(), p.ja()), p.icon());
			b.addActionListener(e -> show(p.id()));
			group.add(b);
			nav.put(p.id(), b);
			top.add(b);
			top.add(Box.createVerticalStrut(3));
			if (p.id().equals("logs")) top.add(Box.createVerticalStrut(10));
		}
		side.add(top, BorderLayout.NORTH);
		JPanel bottom = new JPanel();
		bottom.setOpaque(false);
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
		playerCard.setOpaque(false);
		playerCard.setAlignmentX(0);
		renderPlayer(Avatar.cached(c.launcherDir, c.settings.playerName));
		bottom.add(playerCard);
		bottom.add(Box.createVerticalStrut(12));
		JLabel ver = Ui.label("Minecraft " + c.pins.minecraft() + "  ·  Fabric " + c.pins.loader(), 11f, Font.PLAIN, Theme.FAINT);
		ver.setAlignmentX(0);
		ver.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
		bottom.add(ver);
		side.add(bottom, BorderLayout.SOUTH);
		String name = c.settings.playerName;
		if (Avatar.validName(name) && !Avatar.fresh(c.launcherDir, name)) {
			c.async(() -> Avatar.fetch(c.launcherDir, name, url -> Net.getInfo(url, 1L << 20)), this::renderPlayer, e -> LauncherLog.error("head not loaded", e));
		}
		return side;
	}

	private void renderPlayer(BufferedImage head) {
		playerCard.removeAll();
		String name = c.settings.playerName;
		playerCard.add(Widgets.roundImage(head, 38, name.isEmpty() ? "S" : name, false), BorderLayout.WEST);
		JPanel t = new JPanel();
		t.setOpaque(false);
		t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
		t.add(Ui.label(name.isEmpty() ? c.t("Player", "プレイヤー") : name, 13.5f, Font.BOLD, Theme.TEXT));
		t.add(Ui.label(c.t("Signs in via the Minecraft Launcher", "サインインは公式ランチャーで"), 11f, Font.PLAIN, Theme.FAINT));
		playerCard.add(t, BorderLayout.CENTER);
		playerCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(14, 6, 0, 0)));
		playerCard.revalidate();
		playerCard.repaint();
	}

	// ---------------------------------------------------------------- tests

	/** For tests: paint a component without a window. */
	public static BufferedImage paint(JComponent c, int w, int h) {
		JLayeredPane layered = new JLayeredPane();
		layered.setSize(w, h);
		c.setBounds(0, 0, w, h);
		layered.add(c);
		// two full validations: wrapped text knows its height only after the first one
		for (int i = 0; i < 3; i++) {
			invalidateAll(c);
			layoutAll(c);
		}
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Theme.BG == null ? Color.BLACK : Theme.BG);
		g.fillRect(0, 0, w, h);
		c.paint(g);
		g.dispose();
		return img;
	}

	private static void layoutAll(Component c) {
		c.doLayout();
		if (c instanceof Container ct) for (Component ch : ct.getComponents()) layoutAll(ch);
	}

	private static void invalidateAll(Component c) {
		c.invalidate();
		if (c instanceof Container ct) for (Component ch : ct.getComponents()) invalidateAll(ch);
	}
}
