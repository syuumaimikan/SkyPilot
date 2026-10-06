package dev.skypilot.launcher.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.skypilot.launcher.Env;
import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.LauncherMain;
import dev.skypilot.launcher.LauncherProfiles;
import dev.skypilot.launcher.LauncherSettings;
import dev.skypilot.launcher.Mods;
import dev.skypilot.launcher.Net;
import dev.skypilot.launcher.OfficialLauncher;
import dev.skypilot.launcher.Pins;
import dev.skypilot.launcher.Setup;
import dev.skypilot.mod.core.Lang;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The launcher window. Pages: Home (status + Set up / Repair + open the official launcher), Mods,
 * Settings, Logs, About. Long work runs off the UI thread.
 */
public final class LauncherFrame {
	static final Color ACCENT = new Color(0x9D8CFF);
	static final Color OK = new Color(0x55DD77);
	static final Color BAD = new Color(0xFF7070);
	static final Color MUTED = new Color(0x9A9AB0);

	private final LauncherSettings settings;
	private final Pins pins;
	private Lang l;
	private final JPanel cards = new JPanel(new CardLayout());
	private Runnable refreshHome = () -> {
	};

	private LauncherFrame(LauncherSettings settings, Pins pins) {
		this.settings = settings;
		this.pins = pins;
		this.l = LauncherMain.lang(settings);
	}

	public static void open(LauncherSettings s, Pins pins) {
		FlatDarkLaf.setup();
		SwingUtilities.invokeLater(() -> {
			JFrame f = new JFrame("SkyPilot Launcher");
			f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			Image icon = icon();
			if (icon != null) f.setIconImage(icon);
			f.setContentPane(new LauncherFrame(s, pins).content("home"));
			f.setSize(980, 640);
			f.setMinimumSize(new Dimension(820, 540));
			f.setLocationRelativeTo(null);
			f.setVisible(true);
		});
	}

	/** The whole window content; {@code page} is shown first (also used to render screenshots in tests). */
	public static JComponent createContent(LauncherSettings s, Pins pins, String page) {
		FlatDarkLaf.setup();
		return new LauncherFrame(s, pins).content(page);
	}

	static Image icon() {
		try (InputStream in = LauncherFrame.class.getResourceAsStream("/bundled/icon.png")) {
			return in == null ? null : ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	private JComponent content(String page) {
		JPanel root = new JPanel(new BorderLayout());
		root.add(sidebar(page), BorderLayout.WEST);
		cards.add(home(), "home");
		cards.add(mods(), "mods");
		cards.add(settingsPage(), "settings");
		cards.add(logs(), "logs");
		cards.add(about(), "about");
		((CardLayout) cards.getLayout()).show(cards, page);
		root.add(cards, BorderLayout.CENTER);
		return root;
	}

	private JComponent sidebar(String page) {
		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBorder(BorderFactory.createEmptyBorder(18, 14, 14, 14));
		side.setBackground(new Color(0x1B1B26));
		side.setPreferredSize(new Dimension(190, 10));
		Image icon = icon();
		JLabel logo = new JLabel("SkyPilot", icon == null ? null : new ImageIcon(icon.getScaledInstance(32, 32, Image.SCALE_SMOOTH)), SwingConstants.LEFT);
		logo.setFont(logo.getFont().deriveFont(Font.BOLD, 18f));
		logo.setForeground(ACCENT);
		logo.setIconTextGap(10);
		side.add(logo);
		JLabel sub = new JLabel(l.t("Launcher", "ランチャー"));
		sub.setForeground(MUTED);
		side.add(sub);
		side.add(Box.createVerticalStrut(22));
		ButtonGroup group = new ButtonGroup();
		String[][] items = {{"home", l.t("Home", "ホーム")}, {"mods", l.t("Mods", "MOD")}, {"settings", l.t("Settings", "設定")}, {"logs", l.t("Logs", "ログ")},
				{"about", l.t("About", "このランチャーについて")}};
		for (String[] it : items) {
			JToggleButton b = new JToggleButton(it[1]);
			b.setAlignmentX(Component.LEFT_ALIGNMENT);
			b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
			b.setHorizontalAlignment(SwingConstants.LEFT);
			b.putClientProperty("JButton.buttonType", "borderless");
			b.setSelected(it[0].equals(page));
			b.addActionListener(e -> {
				((CardLayout) cards.getLayout()).show(cards, it[0]);
				if (it[0].equals("home")) refreshHome.run();
			});
			group.add(b);
			side.add(b);
			side.add(Box.createVerticalStrut(4));
		}
		side.add(Box.createVerticalGlue());
		JLabel ver = new JLabel("<html>SkyPilot " + pins.modVersion() + "<br>Minecraft " + pins.minecraft() + "<br>Fabric " + pins.loader() + "</html>");
		ver.setForeground(MUTED);
		ver.setFont(ver.getFont().deriveFont(11f));
		side.add(ver);
		return side;
	}

	private static JPanel page(String title, String subtitle) {
		JPanel p = new JPanel(new BorderLayout(0, 12));
		p.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
		JPanel head = new JPanel(new BorderLayout(0, 4));
		JLabel t = new JLabel(title);
		t.setFont(t.getFont().deriveFont(Font.BOLD, 22f));
		head.add(t, BorderLayout.NORTH);
		if (subtitle != null) head.add(wrapped(subtitle, MUTED), BorderLayout.CENTER);
		p.add(head, BorderLayout.NORTH);
		return p;
	}

	/** Plain text that wraps to the available width. */
	static JTextArea wrapped(String text, Color color) {
		JTextArea a = new JTextArea(text);
		a.setEditable(false);
		a.setFocusable(false);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setOpaque(false);
		a.setBorder(null);
		a.setForeground(color);
		a.setFont(new JLabel().getFont());
		return a;
	}

	// ---------------------------------------------------------------- home

	private JComponent home() {
		JPanel p = page(l.t("Play SkyBlock with SkyPilot", "SkyPilotでSkyBlockを遊ぶ"),
				l.t("Sets up Fabric and the SkyPilot mod as a profile in the official Minecraft Launcher. You sign in and press Play there - SkyPilot never asks for your Microsoft account.",
						"公式Minecraftランチャーに、FabricとSkyPilot MODを入れたプロファイルを用意します。サインインとプレイは公式ランチャーで行います。SkyPilotがMicrosoftアカウントを尋ねることはありません。"));
		JPanel checks = new JPanel();
		checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
		JTextArea log = new JTextArea(5, 40);
		log.setEditable(false);
		log.setLineWrap(true);
		log.setWrapStyleWord(true);
		JButton setup = new JButton(l.t("Set up / Repair", "セットアップ／修復"));
		setup.putClientProperty("JButton.buttonType", "default");
		setup.setFont(setup.getFont().deriveFont(Font.BOLD, 14f));
		JButton openLauncher = new JButton(l.t("Open Minecraft Launcher", "Minecraftランチャーを開く"));
		JButton web = new JButton(l.t("Open SkyPilot web", "SkyPilot Webを開く"));
		JButton refresh = new JButton(l.t("Refresh", "再確認"));
		Runnable reload = () -> new SwingWorker<List<Setup.Check>, Void>() {
			@Override
			protected List<Setup.Check> doInBackground() {
				return Setup.status(settings, pins, l, true);
			}

			@Override
			protected void done() {
				try {
					render(checks, get());
				} catch (Exception e) {
					log.append(e.getMessage() + "\n");
				}
			}
		}.execute();
		refreshHome = reload;
		render(checks, Setup.status(settings, pins, l, false));
		reload.run();
		setup.addActionListener(e -> {
			setup.setEnabled(false);
			log.setText("");
			new SwingWorker<Void, String>() {
				@Override
				protected Void doInBackground() throws Exception {
					Setup.run(settings, pins, l, url -> Net.download(url, 64L << 20), this::publish);
					return null;
				}

				@Override
				protected void process(List<String> chunks) {
					for (String s : chunks) log.append(s + "\n");
				}

				@Override
				protected void done() {
					try {
						get();
					} catch (Exception ex) {
						Throwable c = ex.getCause() != null ? ex.getCause() : ex;
						log.append(l.t("Failed: ", "失敗: ") + c.getMessage() + "\n");
					}
					setup.setEnabled(true);
					reload.run();
				}
			}.execute();
		});
		openLauncher.addActionListener(e -> {
			String err = OfficialLauncher.start();
			log.append(err == null ? l.t("Opening the official launcher - choose \"" + LauncherProfiles.NAME + "\" and press Play.\n",
					"公式ランチャーを開きます。「" + LauncherProfiles.NAME + "」を選んでプレイを押してください。\n") : l.t("Could not open it: ", "開けませんでした: ") + err + "\n");
		});
		web.addActionListener(e -> browse(Instance.config(settings.instance()).apiBaseUrl, log));
		refresh.addActionListener(e -> reload.run());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		buttons.add(setup);
		buttons.add(openLauncher);
		buttons.add(web);
		buttons.add(refresh);
		JPanel top = new JPanel(new BorderLayout(0, 14));
		top.add(checks, BorderLayout.NORTH);
		top.add(buttons, BorderLayout.CENTER);
		top.add(steps(), BorderLayout.SOUTH);
		JPanel center = new JPanel(new BorderLayout(0, 14));
		center.add(top, BorderLayout.NORTH);
		JScrollPane logScroll = new JScrollPane(log);
		center.add(logScroll, BorderLayout.CENTER);
		p.add(center, BorderLayout.CENTER);
		return p;
	}

	/** How to get from here to playing. */
	private JComponent steps() {
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		JLabel head = new JLabel(l.t("How to play", "遊び方"));
		head.setFont(head.getFont().deriveFont(Font.BOLD));
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(head);
		box.add(Box.createVerticalStrut(4));
		String[] steps = {
				l.t("1. Press \"Set up / Repair\" (once, and again after updating this launcher).", "1. 「セットアップ／修復」を押す（初回と、このランチャーを更新したとき）。"),
				l.t("2. Open the Minecraft Launcher, sign in there, choose \"" + LauncherProfiles.NAME + "\" next to Play, and press Play.",
						"2. Minecraftランチャーを開いてサインインし、プレイボタン横で「" + LauncherProfiles.NAME + "」を選んでプレイを押す。"),
				l.t("3. Join Hypixel. Optional: /skypilot link connects the mod to your SkyPilot web account (one-time code, no password).",
						"3. Hypixelに参加。任意: /skypilot link でMODとSkyPilot Webアカウントを連携（ワンタイムコード、パスワード不要）。"),
				l.t("In game: J opens the SkyPilot menu, /skypilot settings the settings, and /skypilot mods manages your other mods.",
						"ゲーム内: J でSkyPilotメニュー、/skypilot settings で設定、/skypilot mods で他のMODを管理できます。")};
		for (String st : steps) {
			JTextArea a = wrapped(st, Character.isDigit(st.charAt(0)) ? new JLabel().getForeground() : MUTED);
			a.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.add(a);
			box.add(Box.createVerticalStrut(3));
		}
		return box;
	}

	private void render(JPanel checks, List<Setup.Check> list) {
		checks.removeAll();
		checks.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x34344A)), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		for (Setup.Check c : list) {
			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setOpaque(false);
			// The server and the account link are optional: shown, not counted as problems.
			boolean optional = c.id().equals("server") || c.id().equals("linked");
			JLabel mark = new JLabel(c.ok() ? "✔" : optional ? "•" : "✖");
			mark.setForeground(c.ok() ? OK : optional ? MUTED : BAD);
			mark.setFont(mark.getFont().deriveFont(Font.BOLD, 14f));
			mark.setPreferredSize(new Dimension(18, 22));
			JLabel label = new JLabel(c.label());
			JLabel detail = new JLabel(c.detail());
			detail.setForeground(MUTED);
			row.add(mark, BorderLayout.WEST);
			row.add(label, BorderLayout.CENTER);
			row.add(detail, BorderLayout.EAST);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			checks.add(row);
		}
		checks.revalidate();
		checks.repaint();
	}

	private void browse(String url, JTextArea log) {
		try {
			if (!Instance.validServerUrl(url)) throw new IllegalArgumentException("invalid URL");
			Desktop.getDesktop().browse(URI.create(url));
		} catch (Exception e) {
			log.append(l.t("Could not open ", "開けませんでした: ") + url + " (" + e.getMessage() + ")\n");
		}
	}

	// ---------------------------------------------------------------- mods

	private JComponent mods() {
		JPanel p = page(l.t("Mods", "MOD"),
				l.t("Mods in the SkyPilot game folder. Switching off renames the file to .jar.disabled; nothing is deleted. Changes apply the next time you play.",
						"SkyPilot用ゲームフォルダのMODです。無効にするとファイル名が .jar.disabled になり、削除はしません。次にプレイしたときに反映されます。"));
		DefaultListModel<Mods.ModFile> model = new DefaultListModel<>();
		JList<Mods.ModFile> list = new JList<>(model);
		list.setCellRenderer((lst, m, i, sel, focus) -> {
			JLabel lab = new JLabel("<html><b>" + esc(m.name()) + "</b> &nbsp;<span style='color:#9a9ab0'>" + esc(m.version()) + " · " + esc(m.file().getFileName().toString()) + "</span>"
					+ (m.enabled() ? "" : " &nbsp;<span style='color:#ff7070'>" + l.t("off", "無効") + "</span>") + "</html>");
			lab.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
			lab.setOpaque(true);
			lab.setBackground(sel ? new Color(0x3A3360) : lst.getBackground());
			return lab;
		});
		Runnable reload = () -> {
			model.clear();
			for (Mods.ModFile m : Mods.list(settings.mods())) model.addElement(m);
		};
		reload.run();
		JButton toggle = new JButton(l.t("Switch on / off", "有効／無効を切り替え"));
		toggle.addActionListener(e -> {
			Mods.ModFile m = list.getSelectedValue();
			if (m == null) return;
			try {
				Mods.toggle(m);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(p, ex.getMessage());
			}
			reload.run();
		});
		JButton add = new JButton(l.t("Add a mod file…", "MODファイルを追加…"));
		add.addActionListener(e -> {
			JFileChooser ch = new JFileChooser();
			ch.setFileFilter(new FileNameExtensionFilter("Fabric mod (.jar)", "jar"));
			if (ch.showOpenDialog(p) != JFileChooser.APPROVE_OPTION) return;
			try {
				Mods.add(settings.mods(), ch.getSelectedFile().toPath());
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(p, ex.getMessage());
			}
			reload.run();
		});
		JButton folder = new JButton(l.t("Open mods folder", "modsフォルダを開く"));
		folder.addActionListener(e -> openFolder(settings.mods()));
		JButton refresh = new JButton(l.t("Refresh", "再読み込み"));
		refresh.addActionListener(e -> reload.run());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		buttons.add(toggle);
		buttons.add(add);
		buttons.add(folder);
		buttons.add(refresh);
		JPanel center = new JPanel(new BorderLayout(0, 10));
		center.add(new JScrollPane(list), BorderLayout.CENTER);
		center.add(buttons, BorderLayout.SOUTH);
		p.add(center, BorderLayout.CENTER);
		return p;
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			Desktop.getDesktop().open(dir.toFile());
		} catch (Exception e) {
			// no desktop integration: nothing to open
		}
	}

	// ---------------------------------------------------------------- settings

	private JComponent settingsPage() {
		JPanel p = page(l.t("Settings", "設定"), l.t("Memory and folders apply to the profile at the next \"Set up / Repair\".", "メモリとフォルダは次の「セットアップ／修復」でプロファイルに反映されます。"));
		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(6, 0, 6, 12);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		JComboBox<String> lang = new JComboBox<>(new String[] {l.t("Auto", "自動"), "English", "日本語"});
		lang.setSelectedIndex(switch (settings.language) {
			case "en" -> 1;
			case "ja" -> 2;
			default -> 0;
		});
		JSpinner memory = new JSpinner(new SpinnerNumberModel(settings.memoryMb / 1024, 2, 32, 1));
		JTextField mc = new JTextField(settings.minecraft().toString(), 40);
		JTextField inst = new JTextField(settings.instance().toString(), 40);
		JTextField server = new JTextField(Instance.config(settings.instance()).apiBaseUrl, 40);
		JLabel status = new JLabel(" ");
		row(form, c, 0, l.t("Language", "言語"), lang);
		row(form, c, 1, l.t("Memory for Minecraft (GB)", "Minecraftのメモリ（GB）"), memory);
		row(form, c, 2, l.t("Minecraft folder", "Minecraftフォルダ"), mc);
		row(form, c, 3, l.t("SkyPilot game folder", "SkyPilot用ゲームフォルダ"), inst);
		row(form, c, 4, l.t("SkyPilot server", "SkyPilotサーバー"), server);
		JButton save = new JButton(l.t("Save", "保存"));
		save.putClientProperty("JButton.buttonType", "default");
		save.addActionListener(e -> {
			settings.language = switch (lang.getSelectedIndex()) {
				case 1 -> "en";
				case 2 -> "ja";
				default -> "auto";
			};
			settings.memoryMb = ((Number) memory.getValue()).intValue() * 1024;
			settings.minecraftDir = mc.getText().strip().equals(Env.minecraftDir().toString()) ? null : mc.getText().strip();
			settings.instanceDir = inst.getText().strip().equals(Env.defaultInstanceDir(settings.minecraft()).toString()) ? null : inst.getText().strip();
			try {
				settings.save(Env.launcherDir());
				String url = server.getText().strip();
				if (!url.equals(Instance.config(settings.instance()).apiBaseUrl)) Instance.setServer(settings.instance(), url);
				status.setForeground(OK);
				status.setText(l.t("Saved. Restart the launcher to change the language.", "保存しました。言語の変更はランチャーの再起動後に反映されます。"));
			} catch (Exception ex) {
				status.setForeground(BAD);
				status.setText(ex.getMessage());
			}
		});
		c.gridx = 1;
		c.gridy = 5;
		c.fill = GridBagConstraints.NONE;
		form.add(save, c);
		c.gridy = 6;
		form.add(status, c);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(form, BorderLayout.NORTH);
		p.add(wrap, BorderLayout.CENTER);
		return p;
	}

	private static void row(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
		c.gridx = 0;
		c.gridy = y;
		c.weightx = 0;
		form.add(new JLabel(label), c);
		c.gridx = 1;
		c.weightx = 1;
		form.add(field, c);
	}

	// ---------------------------------------------------------------- logs

	private JComponent logs() {
		JPanel p = page(l.t("Logs", "ログ"), l.t("The last game log and crash reports of the SkyPilot game folder.", "SkyPilot用ゲームフォルダの最新ログとクラッシュレポートです。"));
		JTextArea text = new JTextArea();
		text.setEditable(false);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		DefaultListModel<Path> crashes = new DefaultListModel<>();
		JList<Path> crashList = new JList<>(crashes);
		crashList.setCellRenderer((lst, f, i, sel, focus) -> {
			JLabel lab = new JLabel(f.getFileName().toString());
			lab.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
			lab.setOpaque(true);
			lab.setBackground(sel ? new Color(0x3A3360) : lst.getBackground());
			return lab;
		});
		Runnable reload = () -> {
			List<String> lines = Instance.latestLog(settings.instance(), 400);
			text.setText(lines.isEmpty() ? l.t("No log yet - play once with the SkyPilot profile.", "まだログがありません。SkyPilotプロファイルで一度プレイしてください。") : String.join("\n", lines));
			text.setCaretPosition(text.getDocument().getLength());
			crashes.clear();
			for (Path f : Instance.crashReports(settings.instance())) crashes.addElement(f);
		};
		reload.run();
		crashList.addListSelectionListener(e -> {
			Path f = crashList.getSelectedValue();
			if (f == null) return;
			try {
				text.setText(Files.readString(f));
				text.setCaretPosition(0);
			} catch (Exception ex) {
				text.setText(ex.getMessage());
			}
		});
		JButton refresh = new JButton(l.t("Show latest log", "最新ログを表示"));
		refresh.addActionListener(e -> {
			crashList.clearSelection();
			reload.run();
		});
		JButton folder = new JButton(l.t("Open folder", "フォルダを開く"));
		folder.addActionListener(e -> openFolder(settings.instance().resolve("logs")));
		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.add(new JLabel(l.t("Crash reports", "クラッシュレポート")), BorderLayout.NORTH);
		JScrollPane cs = new JScrollPane(crashList);
		cs.setPreferredSize(new Dimension(230, 10));
		right.add(cs, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		buttons.add(refresh);
		buttons.add(folder);
		JPanel center = new JPanel(new BorderLayout(10, 10));
		center.add(new JScrollPane(text), BorderLayout.CENTER);
		center.add(right, BorderLayout.EAST);
		center.add(buttons, BorderLayout.SOUTH);
		p.add(center, BorderLayout.CENTER);
		return p;
	}

	// ---------------------------------------------------------------- about

	private JComponent about() {
		JPanel p = page(l.t("About this launcher", "このランチャーについて"), null);
		String html = "<html><body style='width:560px'>"
				+ l.t("<b>What it does</b><br>• Installs Fabric Loader " + pins.loader() + " for Minecraft " + pins.minecraft() + " (version file from meta.fabricmc.net, as Fabric's installer does).<br>"
						+ "• Puts the SkyPilot mod " + pins.modVersion() + " (bundled with this launcher, SHA-256 checked) and Fabric API " + pins.fabricApi()
						+ " (from Modrinth, SHA-512 pinned) into its own game folder.<br>"
						+ "• Adds the profile \"" + LauncherProfiles.NAME + "\" to the official launcher (your other profiles are kept; the file is backed up first).<br><br>"
						+ "<b>What it never does</b><br>• Ask for, read or store your Microsoft / Minecraft account, password or tokens - you sign in and press Play in the official launcher.<br>"
						+ "• Start Minecraft itself, change the game, or download anything from other places (only meta.fabricmc.net and cdn.modrinth.com).<br>"
						+ "• Delete your files: replaced mods are switched off (.jar.disabled).",
						"<b>行うこと</b><br>• Minecraft " + pins.minecraft() + " 用の Fabric Loader " + pins.loader() + " をインストール（Fabric公式インストーラーと同じく meta.fabricmc.net のバージョンファイル）。<br>"
								+ "• 専用のゲームフォルダに SkyPilot MOD " + pins.modVersion() + "（ランチャーに同梱・SHA-256確認）と Fabric API " + pins.fabricApi()
								+ "（Modrinthから・SHA-512固定）を配置。<br>"
								+ "• 公式ランチャーに「" + LauncherProfiles.NAME + "」プロファイルを追加（他のプロファイルはそのまま、事前にバックアップ）。<br><br>"
								+ "<b>行わないこと</b><br>• Microsoft / Minecraft のアカウント・パスワード・トークンを尋ねる・読む・保存すること。サインインとプレイは公式ランチャーで行います。<br>"
								+ "• Minecraft を自分で起動すること、ゲームの改変、指定以外の場所からのダウンロード（meta.fabricmc.net と cdn.modrinth.com のみ）。<br>"
								+ "• ファイルの削除。置き換えたMODは無効化（.jar.disabled）するだけです。")
				+ "<br><br><span style='color:#9a9ab0'>Fabric API SHA-512: " + pins.fabricApiSha512().substring(0, 32) + "…<br>"
				+ l.t("Minecraft folder: ", "Minecraftフォルダ: ") + settings.minecraft() + "<br>" + l.t("SkyPilot game folder: ", "SkyPilot用ゲームフォルダ: ") + settings.instance() + "</span></body></html>";
		JLabel text = new JLabel(html);
		text.setVerticalAlignment(SwingConstants.TOP);
		p.add(new JScrollPane(text), BorderLayout.CENTER);
		return p;
	}

	/** For tests: paint a component without a window. */
	public static BufferedImage paint(JComponent c, int w, int h) {
		c.setSize(w, h);
		layoutAll(c);
		layoutAll(c); // wrapped text knows its height after the first pass
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		var g = img.createGraphics();
		c.paint(g);
		g.dispose();
		return img;
	}

	private static void layoutAll(Component c) {
		c.doLayout();
		if (c instanceof java.awt.Container ct) for (Component ch : ct.getComponents()) layoutAll(ch);
	}
}
