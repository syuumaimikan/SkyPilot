package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Env;
import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.LauncherLog;
import dev.skypilot.launcher.LauncherSettings;
import dev.skypilot.launcher.Pins;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.HttpTransport;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** What every page shares: settings, pins, language, the window's toasts and navigation. */
final class Ctx {
	final LauncherSettings settings;
	final Pins pins;
	final Lang l;
	final Path launcherDir;
	Widgets.Toasts toasts;
	Consumer<String> navigate = p -> {
	};
	Runnable rebuild = () -> {
	};
	/** When the player last pressed Play (crashes after this are reported). */
	long playedAt;
	private ApiClient api;
	private String apiUrl;

	Ctx(LauncherSettings settings, Pins pins, Lang l, Path launcherDir) {
		this.settings = settings;
		this.pins = pins;
		this.l = l;
		this.launcherDir = launcherDir;
	}

	static Ctx forTests(LauncherSettings s, Pins pins, Lang l) {
		return new Ctx(s, pins, l, Env.launcherDir());
	}

	String t(String en, String ja) {
		return l.t(en, ja);
	}

	/** SkyPilot server client for public data (no token: the launcher never reads the mod's link token). */
	synchronized ApiClient api() {
		String url = Instance.config(settings.instance()).apiBaseUrl;
		if (api == null || !url.equals(apiUrl)) {
			try {
				api = new ApiClient(new HttpTransport("SkyPilot-Launcher"), url, () -> null, System::currentTimeMillis);
				apiUrl = url;
			} catch (IllegalArgumentException e) {
				api = null;
			}
		}
		return api;
	}

	void toast(String icon, Color color, String title, String text) {
		if (toasts != null) toasts.show(icon, color, title, text, null, null);
	}

	void toast(String icon, Color color, String title, String text, String action, Runnable run) {
		if (toasts != null) toasts.show(icon, color, title, text, action, run);
	}

	void ok(String title, String text) {
		toast("check", Theme.OK, title, text);
	}

	void fail(String title, Throwable e) {
		LauncherLog.error(title, e);
		Throwable c = e.getCause() != null && e.getCause() != e ? e.getCause() : e;
		toast("alert", Theme.BAD, title, c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage());
	}

	/** Run off the UI thread; the result handlers run on it. */
	<T> void async(Callable<T> work, Consumer<T> ok, Consumer<Throwable> fail) {
		new SwingWorker<T, Void>() {
			@Override
			protected T doInBackground() throws Exception {
				return work.call();
			}

			@Override
			protected void done() {
				try {
					ok.accept(get());
				} catch (Exception e) {
					Throwable c = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
					if (fail != null) fail.accept(c);
					else LauncherLog.error("background task failed", c);
				}
			}
		}.execute();
	}

	static void later(Runnable r) {
		SwingUtilities.invokeLater(r);
	}
}
