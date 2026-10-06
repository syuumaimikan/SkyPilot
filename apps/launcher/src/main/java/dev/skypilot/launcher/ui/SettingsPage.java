package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Avatar;
import dev.skypilot.launcher.Env;
import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.JvmArgs;
import dev.skypilot.launcher.LauncherProfiles;
import dev.skypilot.launcher.Net;
import dev.skypilot.launcher.SystemInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.List;

/** Launcher and game settings. "Save" writes the launcher settings and updates the SkyPilot profile right away. */
final class SettingsPage {
	private static final int[][] SIZES = {{0, 0}, {1280, 720}, {1600, 900}, {1920, 1080}, {2560, 1440}};
	private final Ctx c;
	private final JComponent root;
	private final JComboBox<String> lang = new JComboBox<>();
	private final JComboBox<String> theme = new JComboBox<>();
	private final Widgets.Toggle closeAfter;
	private final JSlider memory;
	private final JLabel memoryLabel = Ui.label("", 15f, Font.BOLD, Theme.TEXT);
	private final JLabel memoryHint = Ui.muted("");
	private final JComboBox<String> size = new JComboBox<>();
	private final JTextField args = new JTextField();
	private final JLabel argsHint = Ui.muted(" ");
	private final JTextField player = new JTextField();
	private final JTextField mcDir = new JTextField();
	private final JTextField instDir = new JTextField();
	private final JTextField server = new JTextField();
	private final JLabel serverHint = Ui.muted(" ");
	private final long totalMb = SystemInfo.totalMemoryMb();

	SettingsPage(Ctx c) {
		this.c = c;
		closeAfter = new Widgets.Toggle(c.settings.closeAfterPlay);
		memory = new JSlider(2, 16, Math.max(2, Math.min(16, c.settings.memoryMb / 1024)));
		root = build();
	}

	JComponent view() {
		return root;
	}

	private JComponent build() {
		JPanel page = Ui.page();
		JButton save = Widgets.primary(c.t("Save", "保存"), "check");
		save.addActionListener(e -> save());
		page.add(Ui.header(c.t("Settings", "設定"), c.t("Memory, window size and Java arguments go into the \"" + LauncherProfiles.NAME + "\" profile when you save.",
				"メモリ・ウィンドウサイズ・Java引数は、保存すると「" + LauncherProfiles.NAME + "」プロファイルに反映されます。"), save), BorderLayout.NORTH);

		Ui.ScrollBody body = new Ui.ScrollBody(null);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		lang.addItem(c.t("Automatic", "自動"));
		lang.addItem("English");
		lang.addItem("日本語");
		lang.setSelectedIndex(switch (c.settings.language) {
			case "en" -> 1;
			case "ja" -> 2;
			default -> 0;
		});
		theme.addItem(c.t("Dark", "ダーク"));
		theme.addItem(c.t("Light", "ライト"));
		theme.setSelectedIndex(c.settings.theme.equals("light") ? 1 : 0);
		body.add(section("palette", c.t("Appearance", "表示"), form(
				row(c.t("Language", "言語"), lang),
				row(c.t("Theme", "テーマ"), theme),
				row(c.t("Close the launcher after Play", "プレイ後にランチャーを閉じる"), closeAfter))));

		memory.setMajorTickSpacing(2);
		memory.setMinorTickSpacing(1);
		memory.setSnapToTicks(true);
		memory.setPaintTicks(true);
		memory.setOpaque(false);
		memory.addChangeListener(e -> updateMemory());
		updateMemory();
		JPanel mem = new JPanel(new BorderLayout(12, 4));
		mem.setOpaque(false);
		mem.add(memoryLabel, BorderLayout.WEST);
		mem.add(memory, BorderLayout.CENTER);
		mem.add(memoryHint, BorderLayout.SOUTH);
		size.addItem(c.t("Launcher default", "ランチャーの既定"));
		for (int i = 1; i < SIZES.length; i++) size.addItem(SIZES[i][0] + " × " + SIZES[i][1]);
		int sel = 0;
		for (int i = 1; i < SIZES.length; i++) if (SIZES[i][0] == c.settings.width && SIZES[i][1] == c.settings.height) sel = i;
		size.setSelectedIndex(sel);
		args.setText(c.settings.extraJvmArgs);
		args.putClientProperty("JTextField.placeholderText", "-XX:+UseZGC");
		args.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				checkArgs();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				checkArgs();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				checkArgs();
			}
		});
		checkArgs();
		JPanel argsBox = new JPanel(new BorderLayout(0, 4));
		argsBox.setOpaque(false);
		argsBox.add(args, BorderLayout.CENTER);
		argsBox.add(argsHint, BorderLayout.SOUTH);
		body.add(section("memory", c.t("Game", "ゲーム"), form(
				row(c.t("Memory", "メモリ"), mem),
				row(c.t("Window size", "ウィンドウサイズ"), size),
				row(c.t("Extra Java arguments", "追加のJava引数"), argsBox))));

		player.setText(c.settings.playerName);
		player.putClientProperty("JTextField.placeholderText", c.t("Minecraft name (optional)", "Minecraftの名前（任意）"));
		body.add(section("user", c.t("Profile picture", "プロフィール画像"), form(row(c.t("Minecraft name", "Minecraftの名前"), withHint(player,
				c.t("Only used to show your head in the sidebar (public Mojang profile). No sign-in, no password.", "サイドバーに頭の画像を表示するためだけに使います（Mojangの公開プロフィール）。サインインやパスワードは不要です。"))))));

		mcDir.setText(c.settings.minecraft().toString());
		instDir.setText(c.settings.instance().toString());
		body.add(section("folder", c.t("Folders", "フォルダ"), form(
				row(c.t("Minecraft folder", "Minecraftフォルダ"), browse(mcDir)),
				row(c.t("SkyPilot game folder", "SkyPilot用ゲームフォルダ"), browse(instDir)),
				row(c.t("Launcher data", "ランチャーのデータ"), openButton(c.launcherDir)))));

		server.setText(Instance.config(c.settings.instance()).apiBaseUrl);
		JButton test = Widgets.button(c.t("Test", "接続テスト"), "activity");
		test.addActionListener(e -> {
			String url = server.getText().strip();
			if (!Instance.validServerUrl(url)) {
				serverHint.setText(c.t("Use https (http only for localhost).", "https を使ってください（http は localhost のみ）。"));
				serverHint.setForeground(Theme.BAD);
				return;
			}
			serverHint.setText(c.t("Testing…", "テスト中…"));
			serverHint.setForeground(Theme.MUTED);
			c.async(() -> Net.healthy(url), ok -> {
				serverHint.setText(ok ? c.t("Connected.", "接続できました。") : c.t("No answer from " + url, url + " から応答がありません"));
				serverHint.setForeground(ok ? Theme.OK : Theme.BAD);
			}, ex -> serverHint.setText(ex.getMessage()));
		});
		JPanel srv = new JPanel(new BorderLayout(8, 4));
		srv.setOpaque(false);
		srv.add(server, BorderLayout.CENTER);
		srv.add(test, BorderLayout.EAST);
		srv.add(serverHint, BorderLayout.SOUTH);
		body.add(section("server", c.t("SkyPilot server", "SkyPilotサーバー"), form(row(c.t("Address", "アドレス"), srv))));
		body.add(Box.createVerticalStrut(10));
		page.add(Ui.scroll(body), BorderLayout.CENTER);
		return page;
	}

	private void updateMemory() {
		int gb = memory.getValue();
		memoryLabel.setText(gb + " GB");
		int rec = SystemInfo.recommendedMb(totalMb);
		String base = totalMb > 0 ? c.t("This PC has " + Math.round(totalMb / 1024.0) + " GB. Recommended: " + rec / 1024 + " GB.", "このPCのメモリは " + Math.round(totalMb / 1024.0) + " GB。おすすめ: " + rec / 1024 + " GB。")
				: c.t("Recommended: " + rec / 1024 + " GB.", "おすすめ: " + rec / 1024 + " GB。");
		if (SystemInfo.tooMuch(gb * 1024, totalMb)) {
			memoryHint.setText(base + c.t(" This is more than 3/4 of your memory - Windows may slow down.", " メモリの3/4を超えています。Windowsが遅くなる可能性があります。"));
			memoryHint.setForeground(Theme.WARN);
		} else {
			memoryHint.setText(base);
			memoryHint.setForeground(Theme.MUTED);
		}
	}

	private void checkArgs() {
		List<String> p = JvmArgs.problems(args.getText());
		argsHint.setText(p.isEmpty() ? c.t("Optional tuning flags (e.g. garbage collector). Memory is set above.", "任意の調整用フラグ（GCの種類など）。メモリは上で設定します。") : String.join("; ", p));
		argsHint.setForeground(p.isEmpty() ? Theme.MUTED : Theme.BAD);
	}

	private JComponent section(String icon, String title, JComponent body) {
		Widgets.Card card = Widgets.card(icon, title, null, body);
		card.setAlignmentX(0);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		wrap.add(card);
		wrap.setAlignmentX(0);
		return wrap;
	}

	private JComponent[] row(String label, JComponent field) {
		JLabel l = new JLabel(label);
		l.setFont(Theme.font(Font.PLAIN, 13f));
		return new JComponent[] {l, field};
	}

	private JComponent form(JComponent[]... rows) {
		JPanel f = new JPanel(new GridBagLayout());
		f.setOpaque(false);
		GridBagConstraints g = new GridBagConstraints();
		g.insets = new Insets(6, 0, 6, 16);
		g.anchor = GridBagConstraints.WEST;
		for (int i = 0; i < rows.length; i++) {
			g.gridy = i;
			g.gridx = 0;
			g.weightx = 0;
			g.fill = GridBagConstraints.NONE;
			JComponent label = rows[i][0];
			label.setPreferredSize(new Dimension(210, label.getPreferredSize().height));
			f.add(label, g);
			g.gridx = 1;
			g.weightx = 1;
			g.fill = rows[i][1] instanceof Widgets.Toggle ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
			f.add(rows[i][1], g);
		}
		return f;
	}

	private JComponent withHint(JComponent field, String hint) {
		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setOpaque(false);
		p.add(field, BorderLayout.CENTER);
		p.add(Ui.muted(hint), BorderLayout.SOUTH);
		return p;
	}

	private JComponent browse(JTextField field) {
		JPanel p = new JPanel(new BorderLayout(8, 0));
		p.setOpaque(false);
		p.add(field, BorderLayout.CENTER);
		JButton b = Widgets.button(c.t("Browse…", "参照…"), "folder");
		b.addActionListener(e -> {
			JFileChooser ch = new JFileChooser(field.getText());
			ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (ch.showOpenDialog(root) == JFileChooser.APPROVE_OPTION) field.setText(ch.getSelectedFile().getAbsolutePath());
		});
		p.add(b, BorderLayout.EAST);
		return p;
	}

	private JComponent openButton(Path dir) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		p.setOpaque(false);
		p.add(Ui.muted(Ui.shortPath(dir) + "   "));
		JButton b = Widgets.link(c.t("Open", "開く"), "folder");
		b.addActionListener(e -> Ui.openFolder(dir));
		p.add(b);
		return p;
	}

	private void save() {
		List<String> argProblems = JvmArgs.problems(args.getText());
		if (!argProblems.isEmpty()) {
			c.toast("alert", Theme.BAD, c.t("Not saved", "保存していません"), String.join("; ", argProblems));
			return;
		}
		String name = player.getText().strip();
		if (!name.isEmpty() && !Avatar.validName(name)) {
			c.toast("alert", Theme.BAD, c.t("Not saved", "保存していません"), c.t("Minecraft names are 3-16 letters, digits or _.", "Minecraftの名前は3〜16文字の英数字と _ です。"));
			return;
		}
		String oldLang = c.settings.language;
		String oldTheme = c.settings.theme;
		c.settings.language = switch (lang.getSelectedIndex()) {
			case 1 -> "en";
			case 2 -> "ja";
			default -> "auto";
		};
		c.settings.theme = theme.getSelectedIndex() == 1 ? "light" : "dark";
		c.settings.closeAfterPlay = closeAfter.isOn();
		c.settings.memoryMb = memory.getValue() * 1024;
		c.settings.width = SIZES[size.getSelectedIndex()][0];
		c.settings.height = SIZES[size.getSelectedIndex()][1];
		c.settings.extraJvmArgs = JvmArgs.clean(args.getText());
		c.settings.playerName = name;
		c.settings.minecraftDir = mcDir.getText().strip().equals(Env.minecraftDir().toString()) ? null : mcDir.getText().strip();
		c.settings.instanceDir = instDir.getText().strip().equals(Env.defaultInstanceDir(c.settings.minecraft()).toString()) ? null : instDir.getText().strip();
		try {
			c.settings.save(c.launcherDir);
			String url = server.getText().strip();
			if (!url.equals(Instance.config(c.settings.instance()).apiBaseUrl)) Instance.setServer(c.settings.instance(), url);
			int updated = 0;
			for (Path f : LauncherProfiles.files(c.settings.minecraft())) if (LauncherProfiles.updateOptions(f, c.settings.profileOptions())) updated++;
			c.ok(c.t("Settings saved", "設定を保存しました"), updated > 0 ? c.t("The SkyPilot profile was updated too.", "SkyPilotプロファイルにも反映しました。") : null);
		} catch (Exception e) {
			c.fail(c.t("Could not save", "保存できませんでした"), e);
			return;
		}
		if (!oldLang.equals(c.settings.language) || !oldTheme.equals(c.settings.theme)) c.rebuild.run();
	}
}
