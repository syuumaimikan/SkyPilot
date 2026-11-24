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
	private final JComboBox<String> launchMode = new JComboBox<>();
	private final JTextField clientId = new JTextField();
	private final JLabel clientHint = Ui.muted(" ");
	private final JLabel repairHint = Ui.muted(" ");
	private final Widgets.Toggle autoUpdate;
	private final Widgets.Toggle autostartServer;
	private final Widgets.Toggle autoUpdateMods;
	private final Widgets.Toggle skyblockerAuto;
	private final JTextField hypixelKeyField = new JTextField();
	private final JTextField deeplKeyField = new JTextField();
	private final JTextField anthropicKeyField = new JTextField();
	private final JTextField openaiKeyField = new JTextField();
	private final JLabel updateHint = Ui.muted(" ");
	private final long totalMb = SystemInfo.totalMemoryMb();

	SettingsPage(Ctx c) {
		this.c = c;
		closeAfter = new Widgets.Toggle(c.settings.closeAfterPlay);
		autoUpdate = new Widgets.Toggle(c.settings.autoUpdateCheck);
		autostartServer = new Widgets.Toggle(isAutoStartEnabled());
		autoUpdateMods = new Widgets.Toggle(c.settings.autoUpdateMods);
		skyblockerAuto = new Widgets.Toggle(Instance.skyblockerAutoUpdate(c.settings.instance()));
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
		page.add(Ui.header(c.t("Settings", "設定"), c.t("Memory, window size and Java arguments apply to the next start (and to the \"" + LauncherProfiles.NAME + "\" profile of the official launcher).",
				"メモリ・ウィンドウサイズ・Java引数は次回の起動から使われます（公式ランチャーの「" + LauncherProfiles.NAME + "」プロファイルにも反映）。"), save), BorderLayout.NORTH);

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

		body.add(section("user", c.t("Account and launch", "アカウントと起動"), accountForm()));

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

		JButton startServer = Widgets.button(c.t("Start local server", "サーバーを起動"), "play");
		JButton stopServer = Widgets.button(c.t("Stop", "停止"), "square");
		JLabel localServerHint = Ui.muted(c.t("Run SkyPilot natively on Windows without Docker.", "Docker Desktop不要でネイティブ起動できます。"));
		startServer.addActionListener(e -> {
			localServerHint.setText(c.t("Starting local server…", "サーバーを起動中…"));
			localServerHint.setForeground(Theme.MUTED);
			c.async(() -> {
				runServerScript("start");
				for (int i = 0; i < 15; i++) {
					Thread.sleep(500);
					if (Net.healthy(server.getText().strip())) return true;
				}
				return Net.healthy(server.getText().strip());
			}, ok -> {
				localServerHint.setText(ok ? c.t("Server is running.", "サーバーが起動しました。") : c.t("Server started, waiting for health check…", "サーバー起動処理を開始しました。"));
				localServerHint.setForeground(ok ? Theme.OK : Theme.WARN);
			}, ex -> {
				localServerHint.setText(c.t("Failed to start server: ", "サーバー起動失敗: ") + ex.getMessage());
				localServerHint.setForeground(Theme.BAD);
			});
		});
		stopServer.addActionListener(e -> {
			localServerHint.setText(c.t("Stopping server…", "サーバーを停止中…"));
			c.async(() -> {
				runServerScript("stop");
				Thread.sleep(800);
				return !Net.healthy(server.getText().strip());
			}, stopped -> {
				localServerHint.setText(stopped ? c.t("Server stopped.", "サーバーを停止しました。") : c.t("Stop signal sent.", "停止シグナルを送信しました。"));
				localServerHint.setForeground(Theme.MUTED);
			}, ex -> {
				localServerHint.setText(c.t("Error stopping server: ", "サーバー停止エラー: ") + ex.getMessage());
				localServerHint.setForeground(Theme.BAD);
			});
		});

		JPanel serverBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		serverBtns.setOpaque(false);
		serverBtns.add(startServer);
		serverBtns.add(stopServer);
		JPanel localServerPanel = new JPanel(new BorderLayout(0, 4));
		localServerPanel.setOpaque(false);
		localServerPanel.add(serverBtns, BorderLayout.CENTER);
		localServerPanel.add(localServerHint, BorderLayout.SOUTH);

		body.add(section("server", c.t("SkyPilot server", "SkyPilotサーバー"), form(
				row(c.t("Address", "アドレス"), srv),
				row(c.t("Local server (Docker-free)", "ローカルサーバー (Docker不要)"), localServerPanel),
				row(c.t("Start with Windows", "Windows起動時に自動起動"), autostartServer))));
		JButton checkUpdate = Widgets.button(c.t("Check now", "今すぐ確認"), "refresh");
		checkUpdate.addActionListener(e -> UpdateUi.checkNow(c, t -> updateHint.setText(t)));
		JPanel upd = new JPanel(new BorderLayout(8, 4));
		upd.setOpaque(false);
		upd.add(Ui.label(c.t("Version ", "バージョン ") + Net.versionName(), 13f, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
		upd.add(checkUpdate, BorderLayout.EAST);
		upd.add(updateHint, BorderLayout.SOUTH);
		updateHint.setText(c.t("Updates come from your SkyPilot server and are installed only when their signature checks out.",
				"更新はSkyPilotサーバーから取得し、署名を確認できたものだけをインストールします。"));
		body.add(section("download", c.t("Updates", "アップデート"), form(
				row(c.t("This launcher", "このランチャー"), upd),
				row(c.t("Check when the launcher starts", "起動時に確認する"), autoUpdate),
				row(c.t("Auto-update mods on launch", "ゲーム起動時にMODを自動更新"), autoUpdateMods),
				row(c.t("Auto-update the bundled Skyblocker", "内蔵Skyblockerを自動更新"), skyblockerAuto))));

		hypixelKeyField.putClientProperty("JTextField.placeholderText", c.t("Hypixel Persistent API Key (UUID)", "Hypixel 永続APIキー (UUID)"));
		JButton applyHypixel = Widgets.primary(c.t("Apply on Dashboard", "永続APIキー申請"), "external");
		applyHypixel.addActionListener(e -> {
			String desc = "SkyPilot is an open-source Fabric client mod and self-hosted server companion for Hypixel SkyBlock. It displays in-game information (market values, wardrobe previews, puzzle directions, and progression roadmaps) without automating player actions. Hypixel API policy compliance: strictly read-only, aggressive caching (120s+ for player profiles, 30s+ for bazaar/auctions), RateLimit-* headers honored with automatic exponential backoff.";
			try {
				java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(desc), null);
			} catch (Exception ignored) {}
			Ui.browse("https://developer.hypixel.net/dashboard");
			c.ok(c.t("Description copied", "申請用説明文をコピーしました"),
					c.t("Dashboard opened in browser. Create an application and paste the description, then enter your key here.",
							"ブラウザで申請ダッシュボードを開きました。説明文を貼り付けて作成し、発行されたキーをここに入力してください。"));
		});
		JPanel hypixelRow = new JPanel(new BorderLayout(8, 0));
		hypixelRow.setOpaque(false);
		hypixelRow.add(hypixelKeyField, BorderLayout.CENTER);
		hypixelRow.add(applyHypixel, BorderLayout.EAST);

		deeplKeyField.putClientProperty("JTextField.placeholderText", c.t("DeepL API Key (for chat translation)", "DeepL APIキー (チャット翻訳用)"));
		anthropicKeyField.putClientProperty("JTextField.placeholderText", c.t("Anthropic API Key (Claude)", "Anthropic APIキー (Claude)"));
		openaiKeyField.putClientProperty("JTextField.placeholderText", c.t("OpenAI API Key (ChatGPT / Custom)", "OpenAI APIキー (ChatGPT / 互換)"));

		body.add(section("key", c.t("External APIs", "外部API設定"), form(
				row(c.t("Hypixel API", "Hypixel API"), hypixelRow),
				row(c.t("DeepL API", "DeepL翻訳"), deeplKeyField),
				row(c.t("Anthropic API", "Anthropic Claude"), anthropicKeyField),
				row(c.t("OpenAI API", "OpenAI / 互換"), openaiKeyField))));
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

	private JComponent accountForm() {
		dev.skypilot.launcher.AccountStore.Saved a = dev.skypilot.launcher.Account.saved(c.launcherDir);
		JPanel acct = new JPanel(new BorderLayout(10, 0));
		acct.setOpaque(false);
		acct.add(Ui.label(a == null ? c.t("Not signed in", "サインインしていません") : a.name() + c.t(" (Microsoft account)", "（Microsoft アカウント）"), 13f, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
		JButton sign = a == null ? Widgets.primary(c.t("Sign in with Microsoft", "Microsoft でサインイン"), "user") : Widgets.button(c.t("Sign out", "サインアウト"), "x");
		sign.addActionListener(e -> {
			if (a == null) {
				SignInDialog.open(c, root, c.rebuild);
				return;
			}
			try {
				dev.skypilot.launcher.Account.signOut(c.launcherDir);
				c.ok(c.t("Signed out", "サインアウトしました"), null);
			} catch (Exception ex) {
				c.fail(c.t("Could not sign out", "サインアウトできませんでした"), ex);
			}
			c.rebuild.run();
		});
		JPanel signBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		signBox.setOpaque(false);
		signBox.add(sign);
		acct.add(signBox, BorderLayout.EAST);

		launchMode.addItem(c.t("This launcher (Microsoft sign-in)", "このランチャー（Microsoft サインイン）"));
		launchMode.addItem(c.t("Official Minecraft Launcher", "公式 Minecraft ランチャー"));
		launchMode.setSelectedIndex("official".equals(c.settings.launchMode) ? 1 : 0);

		clientId.setText(c.settings.msClientId);
		clientId.putClientProperty("JTextField.placeholderText", "00000000-0000-0000-0000-000000000000");
		clientHint.setText(c.t("Your Azure app's Application (client) ID, approved by Mojang for Minecraft. Without it PLAY uses the official launcher.",
				"Mojang に Minecraft 用として承認された Azure アプリのアプリケーション（クライアント）ID。未設定ならプレイは公式ランチャーを使います。"));
		JPanel idBox = new JPanel(new BorderLayout(8, 4));
		idBox.setOpaque(false);
		idBox.add(clientId, BorderLayout.CENTER);
		JButton help = Widgets.link(c.t("How to get one", "取得方法"), "external");
		help.addActionListener(e -> Ui.browse(SignInDialog.REGISTER_DOCS));
		idBox.add(help, BorderLayout.EAST);
		idBox.add(clientHint, BorderLayout.SOUTH);

		JButton repair = Widgets.button(c.t("Check and repair game files", "ゲームファイルを確認・修復"), "wrench");
		repair.addActionListener(e -> {
			repair.setEnabled(false);
			repairHint.setText(c.t("Checking every file (SHA-1)…", "すべてのファイルを確認中（SHA-1）…"));
			c.async(() -> dev.skypilot.launcher.GameFiles.prepare(c.settings.minecraft(), c.launcherDir.resolve("runtime"), c.pins, true, new dev.skypilot.launcher.GameFiles.Progress() {
				@Override
				public void stage(String en, String ja) {
					Ctx.later(() -> repairHint.setText(c.t(en, ja)));
				}

				@Override
				public void count(long done, long total) {
					Ctx.later(() -> repairHint.setText(done + " / " + total));
				}
			}, () -> false), p -> {
				repair.setEnabled(true);
				repairHint.setText(c.t("All game files are fine.", "ゲームファイルはすべて正常です。"));
			}, ex -> {
				repair.setEnabled(true);
				repairHint.setText(c.t("Repair failed: ", "修復に失敗しました: ") + ex.getMessage());
			});
		});
		JPanel repairBox = new JPanel(new BorderLayout(0, 4));
		repairBox.setOpaque(false);
		JPanel rb = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		rb.setOpaque(false);
		rb.add(repair);
		repairBox.add(rb, BorderLayout.CENTER);
		repairHint.setText(c.t("Minecraft " + c.pins.minecraft() + ", Fabric, assets and Mojang's Java runtime (downloaded from Mojang and Fabric, SHA-1 checked).",
				"Minecraft " + c.pins.minecraft() + "・Fabric・アセット・Mojang の Java ランタイム（Mojang と Fabric から取得、SHA-1 で確認）。"));
		repairBox.add(repairHint, BorderLayout.SOUTH);
		return form(
				row(c.t("Account", "アカウント"), acct),
				row(c.t("Start the game with", "ゲームの起動方法"), launchMode),
				row(c.t("Microsoft client ID", "Microsoft クライアントID"), idBox),
				row(c.t("Game files", "ゲームファイル"), repairBox));
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
		c.settings.autoUpdateCheck = autoUpdate.isOn();
		c.settings.autoUpdateMods = autoUpdateMods.isOn();
		saveExternalApis();
		String id = clientId.getText().strip();
		if (!id.isEmpty() && !dev.skypilot.launcher.MicrosoftAuth.validClientId(id)) {
			c.toast("alert", Theme.BAD, c.t("Not saved", "保存していません"), c.t("The client ID looks like 00000000-0000-0000-0000-000000000000.", "クライアントIDは 00000000-0000-0000-0000-000000000000 の形式です。"));
			return;
		}
		boolean accountChanged = !id.equals(c.settings.msClientId) || !c.settings.launchMode.equals(launchMode.getSelectedIndex() == 1 ? "official" : "direct");
		c.settings.msClientId = id;
		c.settings.launchMode = launchMode.getSelectedIndex() == 1 ? "official" : "direct";
		c.settings.memoryMb = memory.getValue() * 1024;
		c.settings.width = SIZES[size.getSelectedIndex()][0];
		c.settings.height = SIZES[size.getSelectedIndex()][1];
		c.settings.extraJvmArgs = JvmArgs.clean(args.getText());
		c.settings.playerName = name;
		c.settings.minecraftDir = mcDir.getText().strip().equals(Env.minecraftDir().toString()) ? null : mcDir.getText().strip();
		c.settings.instanceDir = instDir.getText().strip().equals(Env.defaultInstanceDir(c.settings.minecraft()).toString()) ? null : instDir.getText().strip();
		try {
			c.settings.save(c.launcherDir);
			if (autostartServer.isOn() != isAutoStartEnabled()) {
				setAutoStart(autostartServer.isOn());
			}
			String url = server.getText().strip();
			if (!url.equals(Instance.config(c.settings.instance()).apiBaseUrl)) Instance.setServer(c.settings.instance(), url);
			if (skyblockerAuto.isOn() != Instance.skyblockerAutoUpdate(c.settings.instance())) Instance.setSkyblockerAutoUpdate(c.settings.instance(), skyblockerAuto.isOn());
			int updated = 0;
			for (Path f : LauncherProfiles.files(c.settings.minecraft())) if (LauncherProfiles.updateOptions(f, c.settings.profileOptions())) updated++;
			c.ok(c.t("Settings saved", "設定を保存しました"), updated > 0 ? c.t("The SkyPilot profile was updated too.", "SkyPilotプロファイルにも反映しました。") : null);
		} catch (Exception e) {
			c.fail(c.t("Could not save", "保存できませんでした"), e);
			return;
		}
		if (!oldLang.equals(c.settings.language) || !oldTheme.equals(c.settings.theme) || accountChanged) c.rebuild.run();
	}

	private static boolean isAutoStartEnabled() {
		if (Env.os() != Env.Os.WINDOWS) return false;
		try {
			Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "SkyPilotServer").start();
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private static void setAutoStart(boolean enable) {
		if (Env.os() != Env.Os.WINDOWS) return;
		try {
			if (enable) {
				Path script = findServerScript();
				if (script != null) {
					String cmd = "powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File \"" + script.toAbsolutePath() + "\" start";
					new ProcessBuilder("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "SkyPilotServer", "/t", "REG_SZ", "/d", cmd, "/f").start().waitFor();
				}
			} else {
				new ProcessBuilder("reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "SkyPilotServer", "/f").start().waitFor();
			}
		} catch (Exception ignored) {}
	}

	private static Path findServerScript() {
		Path p = Path.of("scripts/server.ps1").toAbsolutePath();
		if (java.nio.file.Files.exists(p)) return p;
		Path cur = Path.of("").toAbsolutePath();
		for (int i = 0; i < 4; i++) {
			Path check = cur.resolve("scripts/server.ps1");
			if (java.nio.file.Files.exists(check)) return check;
			if (cur.getParent() != null) cur = cur.getParent();
		}
		return null;
	}

	private static void runServerScript(String action) throws Exception {
		Path script = findServerScript();
		if (script == null) {
			throw new IllegalStateException("scripts/server.ps1 not found");
		}
		ProcessBuilder pb = new ProcessBuilder(
				"powershell.exe",
				"-ExecutionPolicy", "Bypass",
				"-File", script.toAbsolutePath().toString(),
				action
		);
		pb.start().waitFor();
	}

	private void saveExternalApis() {
		String hypixel = hypixelKeyField.getText().strip();
		String deepl = deeplKeyField.getText().strip();
		String anthropic = anthropicKeyField.getText().strip();
		String openai = openaiKeyField.getText().strip();
		if (hypixel.isEmpty() && deepl.isEmpty() && anthropic.isEmpty() && openai.isEmpty()) return;

		java.util.Map<String, String> body = new java.util.HashMap<>();
		if (!hypixel.isEmpty()) body.put("hypixelKey", hypixel);
		if (!deepl.isEmpty()) body.put("deeplKey", deepl);
		if (!anthropic.isEmpty()) body.put("anthropicKey", anthropic);
		if (!openai.isEmpty()) body.put("openaiKey", openai);

		c.async(() -> {
			String url = server.getText().strip().replaceAll("/+$", "") + "/api/settings/apis";
			return Net.postInfo(url, new com.google.gson.Gson().toJson(body), 1 << 20);
		}, ok -> {
			if (ok != null) {
				c.ok(c.t("API settings saved", "外部API設定を保存しました"), c.t("API keys updated on SkyPilot server.", "SkyPilotサーバーのAPIキーを更新しました。"));
				hypixelKeyField.setText("");
				deeplKeyField.setText("");
				anthropicKeyField.setText("");
				openaiKeyField.setText("");
			}
		}, ex -> {
			// server might not be up; keys will be used next time
		});
	}
}
