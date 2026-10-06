package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.LauncherLog;
import dev.skypilot.launcher.Net;
import dev.skypilot.launcher.Updater;

import java.nio.file.Path;

/** Launcher update check / download / install, with toasts. Installing always waits for the player's click. */
final class UpdateUi {
	private static boolean checkedOnStart;

	private UpdateUi() {
	}

	private static String server(Ctx c) {
		return Instance.config(c.settings.instance()).apiBaseUrl;
	}

	/** Once per launcher start, when "check for updates" is on. */
	static void checkOnStart(Ctx c) {
		if (checkedOnStart || !c.settings.autoUpdateCheck) return;
		checkedOnStart = true;
		c.async(() -> Updater.check(server(c)), r -> {
			if (r != null) offer(c, r);
		}, e -> LauncherLog.info("update check skipped: " + e.getMessage()));
	}

	/** "Check now": also says when there is nothing new. */
	static void checkNow(Ctx c, java.util.function.Consumer<String> status) {
		status.accept(c.t("Checking…", "確認中…"));
		c.async(() -> Updater.check(server(c)), r -> {
			if (r == null) {
				status.accept(c.t("You have the newest version (" + Net.versionName() + ").", "最新版です（" + Net.versionName() + "）。"));
			} else {
				status.accept(c.t("Version " + r.version() + " is available.", "バージョン " + r.version() + " があります。"));
				offer(c, r);
			}
		}, e -> status.accept(c.t("Could not check: ", "確認できませんでした: ") + e.getMessage()));
	}

	static void offer(Ctx c, Updater.Release r) {
		c.toast("download", Theme.ACCENT, c.t("Launcher update " + r.version(), "ランチャーの更新 " + r.version()),
				c.t("Signed by SkyPilot and checked before it is installed.", "SkyPilot の署名付き。インストール前に検証します。"), c.t("Install", "インストール"), () -> install(c, r));
	}

	static void install(Ctx c, Updater.Release r) {
		c.toast("download", Theme.ACCENT, c.t("Downloading the update…", "更新をダウンロード中…"), r.file());
		Path dir = c.launcherDir.resolve("updates");
		c.async(() -> Updater.download(server(c), r, dir, n -> {
		}), installer -> {
			try {
				LauncherLog.info("installing launcher update " + r.version());
				Updater.install(installer);
				c.ok(c.t("Installing " + r.version(), r.version() + " をインストール中"), c.t("The launcher closes now and opens again when the installer is done.",
						"ランチャーを閉じます。インストールが終わると再び開きます。"));
				new javax.swing.Timer(1200, e -> System.exit(0)).start();
			} catch (Exception e) {
				c.fail(c.t("Could not start the installer", "インストーラーを開始できませんでした"), e);
			}
		}, e -> c.fail(c.t("Update not installed", "更新をインストールしませんでした"), e));
	}
}
