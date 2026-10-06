package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.LauncherProfiles;
import dev.skypilot.launcher.Net;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;

/** What the launcher does and never does, versions, and third-party licences. */
final class AboutPage {
	private final Ctx c;
	private final JComponent root;

	AboutPage(Ctx c) {
		this.c = c;
		root = build();
	}

	JComponent view() {
		return root;
	}

	private JComponent build() {
		JPanel page = Ui.page();
		JPanel head = new JPanel(new BorderLayout(18, 0));
		head.setOpaque(false);
		Image icon = LauncherFrame.icon();
		if (icon != null) head.add(new JLabel(new ImageIcon(icon.getScaledInstance(72, 72, Image.SCALE_SMOOTH))), BorderLayout.WEST);
		JPanel t = new JPanel();
		t.setOpaque(false);
		t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
		t.add(Ui.label("SkyPilot Launcher", 26f, Font.BOLD, Theme.TEXT));
		t.add(Ui.muted(c.t("Version ", "バージョン ") + Net.versionName() + "  ·  SkyPilot " + c.pins.modVersion() + "  ·  Minecraft " + c.pins.minecraft() + "  ·  Fabric Loader "
				+ c.pins.loader() + "  ·  Fabric API " + c.pins.fabricApi()));
		t.add(Box.createVerticalStrut(6));
		t.add(Ui.wrap(c.t("Installs SkyPilot and starts Minecraft with it - after a Microsoft sign-in, or through the official Minecraft Launcher. Not affiliated with Mojang, Microsoft or Hypixel.",
				"SkyPilot を入れて Minecraft を起動します（Microsoft でサインイン、または公式 Minecraft ランチャー経由）。Mojang・Microsoft・Hypixel とは無関係です。"), Theme.MUTED, 13f));
		head.add(t, BorderLayout.CENTER);
		page.add(head, BorderLayout.NORTH);

		JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0));
		cards.setOpaque(false);
		cards.add(list("check", c.t("What it does", "行うこと"), Theme.OK,
				c.t("Installs Fabric Loader " + c.pins.loader() + " for Minecraft " + c.pins.minecraft() + " (the version file from meta.fabricmc.net, as Fabric's installer does).",
						"Minecraft " + c.pins.minecraft() + " 用の Fabric Loader " + c.pins.loader() + " を入れます（Fabric公式インストーラーと同じ meta.fabricmc.net のバージョンファイル）。"),
				c.t("Copies the SkyPilot mod bundled with it (SHA-256 checked) and Fabric API " + c.pins.fabricApi() + " from Modrinth (SHA-512 pinned).",
						"同梱の SkyPilot MOD（SHA-256確認）と、Modrinth の Fabric API " + c.pins.fabricApi() + "（SHA-512固定）を配置します。"),
				c.t("Adds the \"" + LauncherProfiles.NAME + "\" profile; your other profiles stay, and the file is backed up first.",
						"「" + LauncherProfiles.NAME + "」プロファイルを追加します。他のプロファイルはそのままで、事前にバックアップします。"),
				c.t("Signs in with Microsoft's device code page (your password is typed only there) and starts the game with Minecraft's own files and Java runtime from Mojang, each SHA-1 checked.",
						"Microsoft のデバイスコードのページでサインインし（パスワードはそのページにだけ入力）、Mojang の Minecraft 本体と Java ランタイム（各ファイル SHA-1 確認）でゲームを起動します。"),
				c.t("Shows public information only: SkyBlock mayor, events and news from your SkyPilot server, Modrinth version pages, your public Minecraft head.",
						"公開情報だけを表示します: SkyPilotサーバー経由の市長・イベント・ニュース、Modrinthのページ、公開されているMinecraftの頭の画像。")));
		cards.add(list("shield", c.t("What it never does", "行わないこと"), Theme.ACCENT,
				c.t("Ask for your password or a token, or send your sign-in anywhere: the renewable sign-in stays on this PC, encrypted for your Windows account; the game session lives in memory only.",
						"パスワードやトークンを尋ねたり、サインイン情報をどこかへ送信すること。更新用のサインイン情報はこのPCにだけ、Windowsアカウント用に暗号化して保存し、ゲームのセッションはメモリ上だけで扱います。"),
				c.t("Change the game or run unchecked downloads: game files only from Mojang / Fabric (SHA-1 from their metadata), mods only from meta.fabricmc.net and cdn.modrinth.com (pinned hashes), launcher updates only with SkyPilot's signature.",
						"ゲームの改変や、未確認のダウンロードの実行。ゲームファイルは Mojang / Fabric から（メタデータの SHA-1 で確認）、MODは meta.fabricmc.net と cdn.modrinth.com から（固定ハッシュ）、ランチャーの更新は SkyPilot の署名付きのみです。"),
				c.t("Download other mods for you - update checks only link to Modrinth.", "他のMODのダウンロード（更新確認はModrinthへのリンクのみ）。"),
				c.t("Delete your files - mods are switched off by renaming, settings are backed up before changes.", "ファイルの削除（MODは名前の変更でオフにし、設定は変更前にバックアップします）。")));
		JPanel body = new JPanel(new BorderLayout(0, 16));
		body.setOpaque(false);
		body.add(cards, BorderLayout.NORTH);
		body.add(Widgets.card("info", c.t("Third-party software", "サードパーティーソフトウェア"), null,
				Ui.wrap("FlatLaf (Apache License 2.0) · Gson (Apache License 2.0) · Java runtime: Eclipse Temurin (GPLv2 with Classpath Exception)\n"
						+ c.t("Game data shown in SkyPilot comes from the Hypixel API and the sources listed on the SkyPilot web app.",
								"SkyPilotに表示されるゲームデータは、Hypixel API と SkyPilot Web に記載の出典に基づきます。"), Theme.MUTED, 12.5f)), BorderLayout.CENTER);
		page.add(Ui.scroll(wrapBody(body)), BorderLayout.CENTER);
		return page;
	}

	private static JComponent wrapBody(JComponent body) {
		Ui.ScrollBody b = new Ui.ScrollBody(new BorderLayout());
		b.add(body, BorderLayout.NORTH);
		return b;
	}

	private JComponent list(String icon, String title, java.awt.Color color, String... items) {
		JPanel b = new JPanel();
		b.setOpaque(false);
		b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
		for (String s : items) {
			JPanel r = new JPanel(new BorderLayout(10, 0));
			r.setOpaque(false);
			r.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			r.add(new JLabel(Icons.of(icon.equals("shield") ? "x" : "check", 15, color)), BorderLayout.WEST);
			r.add(Ui.wrap(s, Theme.TEXT, 12.5f), BorderLayout.CENTER);
			r.setAlignmentX(0);
			b.add(r);
		}
		return Widgets.card(icon, title, null, b);
	}
}
