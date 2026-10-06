package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Backups;
import dev.skypilot.launcher.Diagnostics;
import dev.skypilot.launcher.GameOptions;
import dev.skypilot.launcher.Importer;
import dev.skypilot.launcher.LauncherLog;
import dev.skypilot.launcher.ModCheck;
import dev.skypilot.launcher.ModScan;
import dev.skypilot.launcher.ServerList;
import dev.skypilot.launcher.Setup;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Tools: import from the main game, server list, game language, backups, diagnostics, disk use. */
final class ToolsPage {
	private final Ctx c;
	private final JPanel backups = new JPanel();
	private final JLabel servers = Ui.muted("");
	private final JPanel disk = new JPanel(new GridLayout(0, 2, 10, 4));
	private final JComponent root;

	ToolsPage(Ctx c) {
		this.c = c;
		root = build();
		reload();
	}

	JComponent view() {
		return root;
	}

	private JComponent build() {
		JPanel page = Ui.page();
		page.add(Ui.header(c.t("Tools", "ツール"), c.t("Bring your settings over, keep backups, and collect what support needs.", "設定の取り込み、バックアップ、サポートに必要な情報の収集ができます。")), BorderLayout.NORTH);
		JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
		grid.setOpaque(false);
		grid.add(importCard());
		grid.add(serverCard());
		grid.add(backupCard());
		grid.add(diagnosticsCard());
		grid.add(languageCard());
		grid.add(diskCard());
		Ui.ScrollBody body = new Ui.ScrollBody(new BorderLayout());
		body.add(grid, BorderLayout.NORTH);
		page.add(Ui.scroll(body), BorderLayout.CENTER);
		return page;
	}

	void reload() {
		int n = ServerList.servers(c.settings.instance()).size();
		servers.setText(c.t(n + " servers in the SkyPilot game's list" + (ServerList.hasHypixel(c.settings.instance()) ? ", Hypixel included." : "."),
				"SkyPilot用ゲームのサーバー一覧: " + n + " 件" + (ServerList.hasHypixel(c.settings.instance()) ? "（Hypixelあり）" : "")));
		renderBackups();
		c.async(this::diskUse, rows -> {
			disk.removeAll();
			for (String[] r : rows) {
				disk.add(Ui.label(r[0], 12.5f, Font.PLAIN, Theme.TEXT));
				JLabel v = Ui.muted(r[1]);
				v.setHorizontalAlignment(JLabel.RIGHT);
				disk.add(v);
			}
			disk.revalidate();
			disk.repaint();
		}, e -> LauncherLog.error("disk use", e));
	}

	private JComponent body(JComponent... parts) {
		JPanel b = new JPanel();
		b.setOpaque(false);
		b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
		for (JComponent p : parts) {
			p.setAlignmentX(0);
			b.add(p);
			b.add(Box.createVerticalStrut(8));
		}
		return b;
	}

	private JComponent importCard() {
		JCheckBox options = new JCheckBox(c.t("Options (key bindings, video, sound)", "オプション（キー設定・ビデオ・音量）"), true);
		JCheckBox srv = new JCheckBox(c.t("Server list", "サーバー一覧"), true);
		JCheckBox packs = new JCheckBox(c.t("Resource packs", "リソースパック"), true);
		JButton run = Widgets.primary(c.t("Import", "取り込む"), "import");
		run.addActionListener(e -> c.async(() -> Importer.run(c.settings.minecraft(), c.settings.instance(), options.isSelected(), srv.isSelected(), packs.isSelected()), r -> {
			c.ok(c.t("Imported", "取り込みました"), c.t("Options " + (r.options() ? "✔" : "–") + ", servers +" + r.servers() + ", resource packs +" + r.packs() + (r.notes().isEmpty() ? "" : " (not found: " + String.join(", ", r.notes()) + ")"),
					"オプション " + (r.options() ? "✔" : "–") + "、サーバー +" + r.servers() + "、リソースパック +" + r.packs() + (r.notes().isEmpty() ? "" : "（見つからず: " + String.join("、", r.notes()) + "）")));
			reload();
		}, ex -> c.fail(c.t("Import failed", "取り込みに失敗しました"), ex)));
		return Widgets.card("import", c.t("Import from your main game", "通常のゲームから取り込む"), null,
				body(Ui.wrap(c.t("Copies from " + Ui.shortPath(c.settings.minecraft()) + ". Files already in the SkyPilot folder are backed up first; your main game is not changed.",
						Ui.shortPath(c.settings.minecraft()) + " からコピーします。SkyPilot側にある同名ファイルは先にバックアップし、通常のゲームは変更しません。"), Theme.MUTED, 12.5f), options, srv, packs, run));
	}

	private JComponent serverCard() {
		JButton add = Widgets.primary(c.t("Add Hypixel (mc.hypixel.net)", "Hypixel（mc.hypixel.net）を追加"), "plus");
		add.addActionListener(e -> c.async(() -> ServerList.addHypixel(c.settings.instance()), added -> {
			c.ok(added ? c.t("Hypixel added", "Hypixelを追加しました") : c.t("Already in the list", "すでに一覧にあります"), "mc.hypixel.net");
			reload();
		}, ex -> c.fail(c.t("Could not edit the server list", "サーバー一覧を編集できませんでした"), ex)));
		return Widgets.card("server", c.t("Server list", "サーバー一覧"), null, body(servers, Ui.wrap(c.t("Adds an entry to Multiplayer. You still connect yourself; nothing is joined automatically.",
				"マルチプレイの一覧に追加するだけです。接続はご自身で行います。"), Theme.MUTED, 12.5f), add));
	}

	private JComponent languageCard() {
		JComboBox<String> lang = new JComboBox<>(new String[] {"日本語 (ja_jp)", "English (en_us)"});
		String cur = GameOptions.get(c.settings.instance(), "lang");
		lang.setSelectedIndex("en_us".equals(cur) ? 1 : 0);
		JButton apply = Widgets.button(c.t("Apply", "適用"), "globe");
		apply.addActionListener(e -> {
			try {
				GameOptions.setLanguage(c.settings.instance(), lang.getSelectedIndex() == 0 ? "ja_jp" : "en_us");
				c.ok(c.t("Game language set", "ゲームの言語を設定しました"), c.t("Applies the next time the game starts.", "次回のゲーム起動から反映されます。"));
			} catch (Exception ex) {
				c.fail(c.t("Could not set the language", "言語を設定できませんでした"), ex);
			}
		});
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.add(lang);
		row.add(Box.createHorizontalStrut(8));
		row.add(apply);
		return Widgets.card("globe", c.t("Game language", "ゲームの言語"), null, body(Ui.wrap(c.t("Minecraft's language in the SkyPilot game folder (current: " + (cur == null ? "default" : cur) + ").",
				"SkyPilot用ゲームフォルダでのMinecraftの言語です（現在: " + (cur == null ? "既定" : cur) + "）。"), Theme.MUTED, 12.5f), row));
	}

	private JComponent backupCard() {
		backups.setOpaque(false);
		backups.setLayout(new BoxLayout(backups, BoxLayout.Y_AXIS));
		JButton now = Widgets.primary(c.t("Back up now", "今すぐバックアップ"), "archive");
		now.addActionListener(e -> c.async(() -> Backups.create(c.launcherDir, c.settings.instance(), null), p -> {
			c.ok(c.t("Backup saved", "バックアップしました"), p.getFileName().toString());
			reload();
		}, ex -> c.fail(c.t("Backup failed", "バックアップに失敗しました"), ex)));
		JButton folder = Widgets.link(c.t("Open folder", "フォルダを開く"), "folder");
		folder.addActionListener(e -> Ui.openFolder(Backups.dir(c.launcherDir)));
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.add(now);
		row.add(Box.createHorizontalStrut(6));
		row.add(folder);
		return Widgets.card("archive", c.t("Backups", "バックアップ"), null, body(Ui.wrap(c.t("Mod settings, options and the server list of the SkyPilot game folder.",
				"SkyPilot用ゲームフォルダのMOD設定・オプション・サーバー一覧を保存します。"), Theme.MUTED, 12.5f), row, backups));
	}

	private void renderBackups() {
		backups.removeAll();
		List<Path> list = Backups.list(c.launcherDir);
		if (list.isEmpty()) backups.add(Ui.muted(c.t("No backups yet.", "まだバックアップはありません。")));
		for (Path p : list.stream().limit(4).toList()) {
			JPanel r = new JPanel(new BorderLayout(8, 0));
			r.setOpaque(false);
			r.setAlignmentX(0);
			r.add(Ui.label(p.getFileName().toString().replace("skypilot-", "").replace(".zip", ""), 12.5f, Font.PLAIN, Theme.TEXT), BorderLayout.CENTER);
			JButton restore = Widgets.link(c.t("Restore", "復元"), null);
			restore.addActionListener(e -> {
				int ok = JOptionPane.showConfirmDialog(root, c.t("Restore this backup? The current settings are backed up first.", "このバックアップを復元しますか？ 今の設定は先にバックアップされます。"),
						c.t("Restore", "復元"), JOptionPane.OK_CANCEL_OPTION);
				if (ok != JOptionPane.OK_OPTION) return;
				c.async(() -> Backups.restore(c.launcherDir, c.settings.instance(), p), n -> {
					c.ok(c.t("Restored", "復元しました"), c.t(n + " file(s).", n + " 個のファイル"));
					reload();
				}, ex -> c.fail(c.t("Restore failed", "復元に失敗しました"), ex));
			});
			r.add(restore, BorderLayout.EAST);
			backups.add(r);
		}
		backups.revalidate();
		backups.repaint();
	}

	private JComponent diagnosticsCard() {
		JButton copy = Widgets.primary(c.t("Copy diagnostics", "診断情報をコピー"), "copy");
		copy.addActionListener(e -> c.async(() -> {
			List<ModScan.Meta> mods = ModScan.scan(c.settings.mods());
			return Diagnostics.build(c.settings, c.pins, c.l, Setup.status(c.settings, c.pins, c.l, true), mods, ModCheck.check(mods, ModCheck.builtins(c.pins, c.settings.minecraft()), c.l));
		}, text -> {
			Ui.copy(text);
			c.ok(c.t("Copied", "コピーしました"), c.t("System, versions, checks and mods - your user folder is hidden.", "システム・バージョン・チェック・MOD一覧（ユーザーフォルダ名は伏せています）。"));
		}, ex -> c.fail(c.t("Could not collect", "収集できませんでした"), ex)));
		JButton log = Widgets.link(c.t("Launcher log", "ランチャーのログ"), "terminal");
		log.addActionListener(e -> {
			if (LauncherLog.file() != null) Ui.openFolder(LauncherLog.file().getParent());
		});
		JButton repair = Widgets.link(c.t("Repair installation", "インストールを修復"), "wrench");
		repair.addActionListener(e -> c.navigate.accept("home"));
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.add(copy);
		row.add(Box.createHorizontalStrut(6));
		row.add(log);
		return Widgets.card("activity", c.t("Diagnostics", "診断"), null, body(Ui.wrap(c.t("Everything support needs in one paste. No account data exists to include - the launcher never has any.",
				"サポートに必要な情報をまとめてコピーします。ランチャーはアカウント情報を一切持たないため、含まれることはありません。"), Theme.MUTED, 12.5f), row, repair));
	}

	private JComponent diskCard() {
		disk.setOpaque(false);
		return Widgets.card("memory", c.t("Disk use", "ディスク使用量"), null, body(disk));
	}

	private List<String[]> diskUse() {
		Path g = c.settings.instance();
		String[][] parts = {{"mods", c.t("Mods", "MOD")}, {"screenshots", c.t("Screenshots", "スクリーンショット")}, {"resourcepacks", c.t("Resource packs", "リソースパック")},
				{"logs", c.t("Logs", "ログ")}, {"crash-reports", c.t("Crash reports", "クラッシュレポート")}, {"config", c.t("Mod settings", "MOD設定")}};
		List<String[]> out = new java.util.ArrayList<>();
		for (String[] p : parts) out.add(new String[] {p[1], Ui.size(size(g.resolve(p[0])))});
		return out;
	}

	private static long size(Path p) {
		if (!Files.exists(p)) return 0;
		try (Stream<Path> s = Files.walk(p)) {
			return s.filter(Files::isRegularFile).mapToLong(f -> f.toFile().length()).sum();
		} catch (Exception e) {
			return 0;
		}
	}

	static JComponent pad(JComponent c) {
		c.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		return c;
	}
}
