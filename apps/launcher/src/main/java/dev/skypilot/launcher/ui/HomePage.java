package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Account;
import dev.skypilot.launcher.AccountStore;
import dev.skypilot.launcher.GameFiles;
import dev.skypilot.launcher.GameLauncher;
import dev.skypilot.launcher.GameWatcher;
import dev.skypilot.launcher.Importer;
import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.LauncherLog;
import dev.skypilot.launcher.LauncherProfiles;
import dev.skypilot.launcher.MicrosoftAuth;
import dev.skypilot.launcher.Net;
import dev.skypilot.launcher.OfficialLauncher;
import dev.skypilot.launcher.ServerList;
import dev.skypilot.launcher.Setup;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.Dto;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Home: the hero with the big Play button, live SkyBlock info and the installation status. */
final class HomePage {
	private final Ctx c;
	private final Widgets.PlayButton play;
	private final JLabel status = Widgets.pill("…", Color.WHITE);
	private final JProgressBar progress = new JProgressBar(0, 5);
	private final JLabel step = new JLabel(" ");
	private final JPanel checks = new JPanel();
	private final JPanel mayorBody = new JPanel();
	private final JPanel eventsBody = new JPanel();
	private final JPanel newsBody = new JPanel();
	private final JPanel statusBody = new JPanel();
	private Dto.ServerStatus serverStatus;
	private List<Setup.Check> lastChecks = List.of();
	private boolean installing;
	private GameWatcher.State game = GameWatcher.State.IDLE;
	private final JComponent root;

	HomePage(Ctx c) {
		this.c = c;
		play = new Widgets.PlayButton(c.t("PLAY", "プレイ"));
		play.setIcon(Icons.of("play", 22, Color.WHITE));
		play.addActionListener(e -> onPlay());
		root = build();
		refresh();
		loadLive();
		// game state and countdowns
		new Timer(3000, e -> watch()).start();
		// Hypixel status (the server caches it for 2 minutes)
		new Timer(120_000, e -> loadStatus()).start();
		new Timer(30_000, e -> {
			if (serverStatus != null) renderStatus(serverStatus);
		}).start();
	}

	JComponent view() {
		return root;
	}

	// ---------------------------------------------------------------- layout

	private JComponent build() {
		Ui.ScrollBody body = new Ui.ScrollBody(new GridBagLayout());
		body.setBorder(BorderFactory.createEmptyBorder(22, 28, 28, 28));
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = 0;
		g.gridy = 0;
		g.gridwidth = 3;
		g.weightx = 1;
		g.fill = GridBagConstraints.HORIZONTAL;
		g.insets = new Insets(0, 0, 18, 0);
		body.add(hero(), g);
		g.gridy = 1;
		body.add(statusCard(), g);
		g.gridy = 2;
		g.gridwidth = 1;
		g.weightx = 1;
		g.fill = GridBagConstraints.BOTH;
		g.insets = new Insets(0, 0, 18, 9);
		body.add(liveCard("crown", c.t("Mayor", "市長"), mayorBody), g);
		g.gridx = 1;
		g.insets = new Insets(0, 9, 18, 9);
		body.add(liveCard("calendar", c.t("Upcoming", "これから"), eventsBody), g);
		g.gridx = 2;
		g.insets = new Insets(0, 9, 18, 0);
		body.add(liveCard("news", c.t("SkyBlock news", "SkyBlockニュース"), newsBody), g);
		g.gridx = 0;
		g.gridy = 3;
		g.gridwidth = 2;
		g.insets = new Insets(0, 0, 0, 9);
		body.add(installCard(), g);
		g.gridx = 2;
		g.gridwidth = 1;
		g.insets = new Insets(0, 9, 0, 0);
		body.add(quickCard(), g);
		return Ui.scroll(body);
	}

	/** Gradient banner with a soft starfield and floating islands. */
	private JComponent hero() {
		JPanel hero = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = Widgets.smooth(g0);
				int w = getWidth(), h = getHeight();
				RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, h, 26, 26);
				g.setPaint(new GradientPaint(0, 0, Theme.HERO_A, w, h, Theme.HERO_B));
				g.fill(shape);
				g.setClip(shape);
				g.setPaint(new RadialGradientPaint(w * 0.82f, h * 0.15f, h * 1.1f, new float[] {0f, 1f}, new Color[] {new Color(255, 255, 255, 60), new Color(255, 255, 255, 0)}));
				g.fill(shape);
				java.util.Random r = new java.util.Random(7);
				for (int i = 0; i < 70; i++) {
					float x = r.nextFloat() * w, y = r.nextFloat() * h, s = 0.6f + r.nextFloat() * 1.6f;
					g.setColor(new Color(255, 255, 255, 40 + r.nextInt(120)));
					g.fill(new Ellipse2D.Float(x, y, s, s));
				}
				island(g, w * 0.70, h * 0.55, 1.0);
				island(g, w * 0.88, h * 0.32, 0.55);
				island(g, w * 0.60, h * 0.22, 0.35);
				g.dispose();
			}

			private void island(Graphics2D g, double cx, double cy, double s) {
				Path2D rock = new Path2D.Double();
				rock.moveTo(cx - 70 * s, cy);
				rock.lineTo(cx + 70 * s, cy);
				rock.lineTo(cx + 30 * s, cy + 46 * s);
				rock.lineTo(cx + 6 * s, cy + 70 * s);
				rock.lineTo(cx - 24 * s, cy + 40 * s);
				rock.closePath();
				g.setColor(new Color(20, 12, 40, 120));
				g.fill(rock);
				g.setColor(new Color(110, 220, 140, 150));
				g.fill(new RoundRectangle2D.Double(cx - 72 * s, cy - 9 * s, 144 * s, 12 * s, 10 * s, 10 * s));
				g.setColor(new Color(255, 255, 255, 30));
				g.setStroke(new BasicStroke(1f));
				g.draw(new RoundRectangle2D.Double(cx - 72 * s, cy - 9 * s, 144 * s, 12 * s, 10 * s, 10 * s));
			}
		};
		hero.setOpaque(false);
		hero.setBorder(BorderFactory.createEmptyBorder(30, 34, 28, 34));
		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		status.setAlignmentX(0);
		left.add(Ui.label("SKYPILOT  /  CLIENT", 12f, Font.BOLD, new Color(180, 240, 233)));
		left.add(Box.createVerticalStrut(10));
		left.add(status);
		left.add(Box.createVerticalStrut(14));
		JLabel t = Ui.label("Make the sky yours.", 38f, Font.BOLD, Color.WHITE);
		t.setAlignmentX(0);
		left.add(t);
		JLabel sub = Ui.label(c.t("Your Hypixel SkyBlock co-pilot  ·  Minecraft " + c.pins.minecraft() + "  ·  Fabric",
				"Hypixel SkyBlock の副操縦士  ·  Minecraft " + c.pins.minecraft() + "  ·  Fabric"), 14f, Font.PLAIN, new Color(255, 255, 255, 210));
		sub.setAlignmentX(0);
		left.add(sub);
		left.add(Box.createVerticalStrut(22));
		JPanel playRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		playRow.setOpaque(false);
		playRow.setAlignmentX(0);
		playRow.add(play);
		left.add(playRow);
		if (direct()) {
			left.add(Box.createVerticalStrut(10));
			javax.swing.JCheckBox hypixel = new javax.swing.JCheckBox(c.t("Join Hypixel right away", "起動したら Hypixel に接続"), c.settings.joinHypixel);
			hypixel.setOpaque(false);
			hypixel.setForeground(Color.WHITE);
			hypixel.setFont(Theme.font(Font.PLAIN, 12.5f));
			hypixel.setAlignmentX(0);
			hypixel.addActionListener(e -> {
				c.settings.joinHypixel = hypixel.isSelected();
				try {
					c.settings.save(c.launcherDir);
				} catch (Exception ex) {
					LauncherLog.error("settings not saved", ex);
				}
				updateHero();
			});
			left.add(hypixel);
		}
		left.add(Box.createVerticalStrut(10));
		progress.setVisible(false);
		progress.setAlignmentX(0);
		progress.setMaximumSize(new java.awt.Dimension(360, 6));
		progress.setPreferredSize(new java.awt.Dimension(360, 6));
		left.add(progress);
		step.setForeground(new Color(255, 255, 255, 220));
		step.setFont(Theme.font(Font.PLAIN, 12f));
		step.setAlignmentX(0);
		left.add(Box.createVerticalStrut(6));
		left.add(step);
		hero.add(left, BorderLayout.WEST);
		return hero;
	}

	private JComponent liveCard(String icon, String title, JPanel body) {
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.add(Ui.muted(c.t("Loading…", "読み込み中…")));
		Widgets.Card card = Widgets.card(icon, title, null, body);
		card.setPreferredSize(new java.awt.Dimension(240, 230));
		return card;
	}

	private JComponent statusCard() {
		statusBody.setOpaque(false);
		statusBody.setLayout(new BoxLayout(statusBody, BoxLayout.Y_AXIS));
		statusBody.add(Ui.muted(c.t("Loading…", "読み込み中…")));
		JButton open = Widgets.link(c.t("status.hypixel.net", "status.hypixel.net"), "external");
		open.addActionListener(e -> Ui.browse("https://status.hypixel.net"));
		return Widgets.card("server", c.t("Hypixel server status", "Hypixel サーバーの状態"), open, statusBody);
	}

	private JComponent installCard() {
		checks.setOpaque(false);
		checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
		JButton repair = Widgets.link(c.t("Repair", "修復"), "wrench");
		repair.addActionListener(e -> install(false));
		return Widgets.card("shield", c.t("Installation", "インストール状態"), repair, checks);
	}

	private JComponent quickCard() {
		// Uses the same configured web origin as the mod's API client.
		JPanel body = new JPanel(new GridLayout(0, 1, 0, 6));
		body.setOpaque(false);
		body.add(action("server", c.t("Add Hypixel to the server list", "サーバー一覧にHypixelを追加"), () -> c.async(() -> ServerList.addHypixel(c.settings.instance()), added -> c.ok(
				added ? c.t("Hypixel added", "Hypixelを追加しました") : c.t("Already in the list", "すでに一覧にあります"), "mc.hypixel.net"), e -> c.fail(c.t("Could not edit the server list", "サーバー一覧を編集できませんでした"), e))));
		body.add(action("import", c.t("Import settings from your main game", "通常のゲームから設定を取り込む"), () -> c.async(
				() -> Importer.run(c.settings.minecraft(), c.settings.instance(), true, true, true),
				r -> c.ok(c.t("Imported", "取り込みました"), c.t("Options " + (r.options() ? "✔" : "–") + ", servers +" + r.servers() + ", resource packs +" + r.packs(),
						"設定 " + (r.options() ? "✔" : "–") + "、サーバー +" + r.servers() + "、リソースパック +" + r.packs())),
				e -> c.fail(c.t("Import failed", "取り込みに失敗しました"), e))));
		body.add(action("bolt", c.t("FPS boost (performance switches)", "FPSブースト（軽量化スイッチ）"), this::fpsBoost));
		body.add(action("globe", c.t("Open SkyPilot Web", "SkyPilot Web を開く"), c::openWeb));
		body.add(action("globe", c.t("Equipment, pets & activity guides", "装備・ペット・攻略ガイド"), () -> c.openWeb("/guides")));
		body.add(action("globe", c.t("API setup walkthrough", "API 設定の図解ガイド"), () -> c.openWeb("/help/api")));
		body.add(action("folder", c.t("Open the game folder", "ゲームフォルダを開く"), () -> Ui.openFolder(c.settings.instance())));
		return Widgets.card("bolt", c.t("Quick actions", "クイック操作"), null, body);
	}

	private JComponent action(String icon, String text, Runnable run) {
		JButton b = Widgets.button(text, icon);
		b.setHorizontalAlignment(SwingConstants.LEFT);
		b.putClientProperty("JButton.buttonType", "borderless");
		b.addActionListener(e -> run.run());
		return b;
	}

	/** Turns on the mod's recommended performance switches in the game folder's config. */
	private void fpsBoost() {
		try {
			SkyPilotConfig cfg = Instance.config(c.settings.instance());
			int n = 0;
			for (Features.Feature f : List.of(Features.PERF_BACKGROUND_FPS, Features.PERF_MENU_FPS, Features.PERF_DAMAGE_SPLASH, Features.PERF_HIT_PARTICLES, Features.PERF_PARTICLE_CAP,
					Features.PERF_FAR_ENTITIES)) {
				if (!cfg.on(f)) {
					Features.set(cfg, f, true);
					n++;
				}
			}
			Instance.saveConfig(c.settings.instance(), cfg);
			c.toast("bolt", Theme.ACCENT, c.t("FPS boost on", "FPSブーストをオン"), c.t(n + " performance switches turned on. In game, /skypilot perf also lowers video settings.",
					"軽量化スイッチを " + n + " 個オンにしました。ゲーム内の /skypilot perf でビデオ設定も下げられます。"), c.t("Features", "機能"), () -> c.navigate.accept("features"));
		} catch (Exception e) {
			c.fail(c.t("Could not save", "保存できませんでした"), e);
		}
	}

	// ---------------------------------------------------------------- state

	void refresh() {
		c.async(() -> Setup.status(c.settings, c.pins, c.l, true), list -> {
			lastChecks = list;
			renderChecks(list);
			updateHero();
		}, e -> LauncherLog.error("status", e));
		lastChecks = Setup.status(c.settings, c.pins, c.l, false);
		renderChecks(lastChecks);
		updateHero();
	}

	private boolean ready() {
		return lastChecks.stream().filter(k -> !k.id().equals("server") && !k.id().equals("linked")).allMatch(Setup.Check::ok);
	}

	private boolean launcherFound() {
		return lastChecks.stream().anyMatch(k -> k.id().equals("launcher") && k.ok());
	}

	private void updateHero() {
		if (installing) return;
		if (game == GameWatcher.State.RUNNING) {
			setStatus(c.t("Game running", "ゲーム実行中"), Theme.OK);
			play.setText(c.t("RUNNING", "実行中"));
			play.sub = c.t("Have fun on SkyBlock!", "SkyBlockを楽しんで！");
			play.setEnabled(false);
		} else if (direct()) {
			AccountStore.Saved a = Account.saved(c.launcherDir);
			setStatus(c.t("Ready to play", "プレイ準備完了"), Theme.OK);
			play.setText(c.t("PLAY", "プレイ"));
			play.sub = (a == null ? "" : a.name() + "  ·  ") + (c.settings.joinHypixel ? c.t("joins Hypixel", "Hypixel に接続") : "Minecraft " + c.pins.minecraft());
			play.setEnabled(true);
		} else if (c.settings.needsSignIn(Account.signedIn(c.launcherDir))) {
			setStatus(c.t("Sign in to play", "サインインしてプレイ"), Theme.WARN);
			play.setText(c.t("SIGN IN", "サインイン"));
			play.sub = c.t("with your Microsoft account", "Microsoft アカウントで");
			play.setEnabled(true);
		} else if (!launcherFound()) {
			setStatus(c.t("Minecraft Launcher not found", "Minecraftランチャーが見つかりません"), Theme.WARN);
			play.setText(c.t("GET MINECRAFT", "Minecraftを入手"));
			play.sub = c.t("Install the official launcher, start it once", "公式ランチャーを入れて一度起動してください");
			play.setEnabled(true);
		} else if (ready()) {
			setStatus(c.t("Ready to play", "プレイ準備完了"), Theme.OK);
			play.setText(c.t("PLAY", "プレイ"));
			play.sub = "Minecraft " + c.pins.minecraft() + "  ·  Fabric " + c.pins.loader();
			play.setEnabled(true);
		} else {
			setStatus(c.t("Setup needed", "セットアップが必要"), Theme.WARN);
			play.setText(c.t("INSTALL", "インストール"));
			play.sub = c.t("Fabric, SkyPilot and Fabric API", "Fabric・SkyPilot・Fabric API");
			play.setEnabled(true);
		}
		play.repaint();
	}

	private void setStatus(String text, Color color) {
		JLabel fresh = Widgets.pill(text, color);
		status.setIcon(fresh.getIcon());
		status.setText(text);
	}

	private void renderChecks(List<Setup.Check> list) {
		checks.removeAll();
		for (Setup.Check k : list) {
			boolean optional = k.id().equals("server") || k.id().equals("linked");
			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			JLabel mark = new JLabel(Icons.of(k.ok() ? "check" : optional ? "info" : "x", 16, k.ok() ? Theme.OK : optional ? Theme.FAINT : Theme.BAD));
			JLabel label = new JLabel(k.label());
			label.setFont(Theme.font(Font.PLAIN, 13f));
			JLabel detail = Ui.muted(dev.skypilot.launcher.Diagnostics.anonymize(k.detail()));
			row.add(mark, BorderLayout.WEST);
			row.add(label, BorderLayout.CENTER);
			row.add(detail, BorderLayout.EAST);
			checks.add(row);
		}
		checks.revalidate();
		checks.repaint();
	}

	private void loadLive() {
		var api = c.api();
		if (api == null) {
			unavailable(mayorBody);
			unavailable(eventsBody);
			unavailable(newsBody);
			return;
		}
		api.skyblockNow().whenComplete((now, err) -> Ctx.later(() -> {
			if (err != null || now == null) unavailable(mayorBody);
			else renderMayor(now);
		}));
		api.calendar().whenComplete((cal, err) -> Ctx.later(() -> {
			if (err != null || cal == null) unavailable(eventsBody);
			else renderEvents(cal);
		}));
		api.news().whenComplete((news, err) -> Ctx.later(() -> {
			if (err != null || news == null) unavailable(newsBody);
			else renderNews(news);
		}));
		loadStatus();
	}

	/** For tests: shown instead of asking the server. */
	static Dto.ServerStatus previewStatus;

	private void loadStatus() {
		if (previewStatus != null) {
			showStatus(previewStatus);
			return;
		}
		var api = c.api();
		if (api == null) {
			unavailable(statusBody);
			return;
		}
		api.serverStatus().whenComplete((st, err) -> Ctx.later(() -> {
			if (st != null) {
				serverStatus = st;
				renderStatus(st);
			} else if (serverStatus == null) {
				unavailable(statusBody);
			}
		}));
	}

	private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm");

	private static String clock(Long ms) {
		return ms == null ? "?" : CLOCK.format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()));
	}

	private String serviceText(String status) {
		return switch (status == null ? "" : status) {
			case "operational" -> c.t("Online", "稼働中");
			case "degraded_performance" -> c.t("Slow", "遅延");
			case "partial_outage" -> c.t("Partial outage", "一部障害");
			case "major_outage" -> c.t("Outage", "障害");
			case "under_maintenance" -> c.t("Maintenance", "メンテナンス中");
			default -> c.t("Unknown", "不明");
		};
	}

	private static Color serviceColor(String status) {
		return switch (status == null ? "" : status) {
			case "operational" -> Theme.OK;
			case "degraded_performance", "under_maintenance" -> Theme.WARN;
			case "partial_outage", "major_outage" -> Theme.BAD;
			default -> Theme.FAINT;
		};
	}

	private String maintenanceText(Dto.Maintenance m, long now) {
		String span = clock(m.startsMs()) + " – " + clock(m.endsMs());
		if (dev.skypilot.mod.network.ServerStatusRules.running(m)) {
			return c.t("In progress", "実施中") + "  ·  " + span + (m.endsMs() != null && m.endsMs() > now ? c.t("  ·  ends in ", "  ·  終了まで ") + Ui.duration(m.endsMs() - now) : "");
		}
		return c.t("Scheduled", "予定") + "  ·  " + span + (m.startsMs() != null && m.startsMs() > now ? c.t("  ·  in ", "  ·  開始まで ") + Ui.duration(m.startsMs() - now) : "");
	}

	private void renderStatus(Dto.ServerStatus st) {
		statusBody.removeAll();
		long now = System.currentTimeMillis();
		if (!st.available()) {
			statusBody.add(Ui.muted(c.t("Hypixel's status page is not available right now.", "Hypixel のステータスページを今は取得できません。")));
		} else {
			JPanel services = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			services.setOpaque(false);
			services.setAlignmentX(0);
			for (Dto.ServiceStatus v : st.services() == null ? List.<Dto.ServiceStatus>of() : st.services()) {
				JLabel name = new JLabel(v.name() + "  ");
				name.setFont(Theme.font(Font.PLAIN, 13f));
				services.add(name);
				services.add(Widgets.badge(serviceText(v.status()), serviceColor(v.status())));
				services.add(Box.createHorizontalStrut(18));
			}
			statusBody.add(services);
			statusBody.add(Box.createVerticalStrut(8));
			List<Dto.Maintenance> ms = st.maintenances() == null ? List.of() : st.maintenances();
			List<Dto.Incident> incidents = st.incidents() == null ? List.of() : st.incidents();
			if (ms.isEmpty() && incidents.isEmpty()) {
				JLabel ok = Ui.muted(c.t("No maintenance announced", "予定されているメンテナンスはありません"));
				ok.setIcon(Icons.of("check", 14, Theme.OK));
				statusBody.add(ok);
			}
			for (Dto.Maintenance m : ms) {
				boolean running = dev.skypilot.mod.network.ServerStatusRules.running(m);
				statusBody.add(statusItem(running ? "x" : "calendar", running ? Theme.BAD : Theme.WARN, m.name(), maintenanceText(m, now), m.update(), m.link()));
			}
			for (Dto.Incident i : incidents) {
				statusBody.add(statusItem("info", Theme.BAD, i.name(), (i.startedMs() != null ? c.t("since ", "発生 ") + clock(i.startedMs()) : "")
						+ (i.services() == null || i.services().isEmpty() ? "" : "  ·  " + String.join(", ", i.services())), i.update(), i.link()));
			}
			JLabel src = Ui.muted(c.t("Source: status.hypixel.net  ·  checked ", "出典: status.hypixel.net  ·  確認 ") + Ui.duration(Math.max(0, now - st.checkedAtMs()))
					+ c.t(" ago", " 前"));
			src.setFont(Theme.font(Font.PLAIN, 11f));
			src.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			statusBody.add(src);
		}
		statusBody.revalidate();
		statusBody.repaint();
	}

	private JComponent statusItem(String icon, Color color, String title, String when, String update, String link) {
		JLabel item = new JLabel("<html><b>" + Ui.esc(title) + "</b>&nbsp;&nbsp;<span style='color:" + Theme.hex(color) + "'>" + Ui.esc(when) + "</span>"
				+ (update == null || update.isBlank() ? "" : "<br><span style='color:" + Theme.hex(Theme.MUTED) + "'>" + Ui.esc(shorten(update, 160)) + "</span>") + "</html>");
		item.setIcon(Icons.of(icon, 16, color));
		item.setIconTextGap(10);
		item.setFont(Theme.font(Font.PLAIN, 12.5f));
		item.setBorder(BorderFactory.createEmptyBorder(3, 0, 5, 0));
		item.setAlignmentX(0);
		if (link != null && link.startsWith("https://status.hypixel.net/")) {
			item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			item.setToolTipText(link);
			item.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					Ui.browse(link);
				}
			});
		}
		return item;
	}

	private void showStatus(Dto.ServerStatus st) {
		serverStatus = st;
		renderStatus(st);
	}

	private void unavailable(JPanel body) {
		body.removeAll();
		body.add(Ui.muted(c.t("Not available - is your SkyPilot server running?", "取得できません。SkyPilotサーバーは起動していますか？")));
		JButton s = Widgets.link(c.t("Server settings", "サーバー設定"), "settings");
		s.addActionListener(e -> c.navigate.accept("settings"));
		body.add(s);
		body.revalidate();
		body.repaint();
	}

	private void renderMayor(Dto.SkyblockNow now) {
		mayorBody.removeAll();
		if (now.mayor() == null) {
			mayorBody.add(Ui.muted(c.t("Not available from the API", "APIから取得できません")));
		} else {
			JLabel name = Ui.label(now.mayor().name(), 18f, Font.BOLD, Theme.TEXT);
			mayorBody.add(name);
			if (now.year() != null) mayorBody.add(Ui.muted(c.t("Year " + now.year(), now.year() + "年目")));
			mayorBody.add(Box.createVerticalStrut(8));
			int n = 0;
			Font f = Theme.font(Font.PLAIN, 12f);
			for (Dto.Perk p : now.mayor().perks()) {
				if (n++ >= 2) break;
				JLabel perk = new JLabel("<html><b>" + Ui.esc(Ui.displayable(Text.strip(p.name()), f)) + "</b><br><span style='color:" + Theme.hex(Theme.MUTED) + "'>"
						+ Ui.esc(shorten(Ui.displayable(Text.strip(p.description()), f), 64)) + "</span></html>");
				perk.setFont(f);
				perk.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
				mayorBody.add(perk);
			}
			if (now.mayor().perks().size() > 2) mayorBody.add(Ui.muted(c.t("+" + (now.mayor().perks().size() - 2) + " more perks", "ほか " + (now.mayor().perks().size() - 2) + " 個のパーク")));
			if (now.minister() != null) mayorBody.add(Ui.muted(c.t("Minister: ", "大臣: ") + now.minister().name()));
		}
		mayorBody.revalidate();
		mayorBody.repaint();
	}

	private void renderEvents(Dto.Calendar cal) {
		eventsBody.removeAll();
		long skew = cal.nowMs() - System.currentTimeMillis();
		long now = System.currentTimeMillis() + skew;
		List<Dto.CalendarEvent> list = cal.events().stream().filter(e -> e.endMs() > now).sorted(Comparator.comparingLong(Dto.CalendarEvent::startMs)).limit(6).toList();
		if (list.isEmpty()) eventsBody.add(Ui.muted(c.t("No events known", "予定はありません")));
		for (Dto.CalendarEvent e : list) {
			boolean active = e.startMs() <= now;
			JPanel row = new JPanel(new BorderLayout(8, 0));
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
			JLabel name = new JLabel(e.name());
			name.setFont(Theme.font(Font.PLAIN, 13f));
			row.add(name, BorderLayout.CENTER);
			JLabel when = active ? Widgets.badge(c.t("NOW", "開催中"), Theme.OK) : Ui.muted(c.t("in ", "あと ") + Ui.duration(e.startMs() - now));
			row.add(when, BorderLayout.EAST);
			eventsBody.add(row);
		}
		eventsBody.revalidate();
		eventsBody.repaint();
	}

	private void renderNews(Dto.News news) {
		newsBody.removeAll();
		if (news.items().isEmpty()) newsBody.add(Ui.muted(c.t("Not available from the API", "APIから取得できません")));
		for (Dto.NewsItem n : news.items().stream().limit(5).toList()) {
			JLabel item = new JLabel("<html><b>" + Ui.esc(n.title()) + "</b><br><span style='color:" + Theme.hex(Theme.MUTED) + "'>" + Ui.esc(n.date()) + "</span></html>");
			item.setFont(Theme.font(Font.PLAIN, 12.5f));
			item.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
			item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			item.setToolTipText(n.link());
			item.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (n.link().startsWith("https://hypixel.net/")) Ui.browse(n.link());
				}
			});
			newsBody.add(item);
		}
		newsBody.revalidate();
		newsBody.repaint();
	}

	private static String shorten(String s, int n) {
		return s.length() <= n ? s : s.substring(0, n - 1) + "…";
	}

	// ---------------------------------------------------------------- play

	/** Signed in with Microsoft and set to start the game from here. */
	private boolean direct() {
		return c.settings.direct(Account.signedIn(c.launcherDir));
	}

	private void onPlay() {
		if (direct()) {
			playDirect();
			return;
		}
		if (c.settings.needsSignIn(Account.signedIn(c.launcherDir))) {
			SignInDialog.open(c, root, c.rebuild);
			return;
		}
		if (!launcherFound()) {
			Ui.browse("https://www.minecraft.net/download");
			c.toast("info", Theme.ACCENT, c.t("Get the official launcher", "公式ランチャーを入手"), c.t("Install it, start it once and sign in, then come back here.",
					"インストールして一度起動・サインインしてから、ここに戻ってください。"));
			return;
		}
		if (!ready()) {
			install(true);
			return;
		}
		launch();
	}

	private void install(boolean thenPlay) {
		installing = true;
		play.setBusy(true);
		play.setText(c.t("INSTALLING", "インストール中"));
		play.sub = "";
		progress.setVisible(true);
		progress.setValue(0);
		setStatus(c.t("Installing…", "インストール中…"), Theme.ACCENT);
		final int[] n = {0};
		new javax.swing.SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() throws Exception {
				Setup.run(c.settings, c.pins, c.l, url -> Net.download(url, 64L << 20), this::publish);
				return null;
			}

			@Override
			protected void process(List<String> chunks) {
				for (String s : chunks) {
					step.setText(s);
					progress.setValue(Math.min(5, ++n[0]));
					LauncherLog.info(s);
				}
			}

			@Override
			protected void done() {
				installing = false;
				play.setBusy(false);
				progress.setVisible(false);
				try {
					get();
					step.setText(c.t("Everything is installed.", "すべてインストールしました。"));
					c.ok(c.t("Installed", "インストール完了"), c.t("SkyPilot is ready. Press PLAY.", "SkyPilotの準備ができました。プレイを押してください。"));
					refresh();
					if (thenPlay) launch();
				} catch (Exception e) {
					step.setText(" ");
					c.fail(c.t("Setup failed", "セットアップに失敗しました"), e);
					refresh();
				}
			}
		}.execute();
	}

	private void launch() {
		try {
			for (Path f : LauncherProfiles.files(c.settings.minecraft())) LauncherProfiles.touch(f);
		} catch (Exception e) {
			LauncherLog.error("could not mark the profile as last used", e);
		}
		String err = OfficialLauncher.start();
		if (err != null) {
			c.fail(c.t("Could not open the Minecraft Launcher", "Minecraftランチャーを開けませんでした"), new Exception(err));
			return;
		}
		c.playedAt = System.currentTimeMillis();
		LauncherLog.info("handed over to the official launcher");
		c.toast("play", Theme.ACCENT, c.t("Minecraft Launcher is opening", "Minecraftランチャーを開いています"),
				c.t("Choose \"" + LauncherProfiles.NAME + "\" next to the Play button (it is listed first) and press Play.",
						"プレイボタン横で「" + LauncherProfiles.NAME + "」（一番上に表示）を選び、プレイを押してください。"));
		if (c.settings.closeAfterPlay) {
			new Timer(1500, e -> System.exit(0)).start();
		}
	}

	/**
	 * Start the game from this launcher: install SkyPilot's files if needed, renew the session,
	 * check / download the game files, then run Minecraft with the player's own session.
	 */
	private void playDirect() {
		installing = true;
		play.setBusy(true);
		play.setText(c.t("STARTING", "起動中"));
		play.sub = "";
		progress.setVisible(true);
		progress.setIndeterminate(true);
		setStatus(c.t("Starting…", "起動しています…"), Theme.ACCENT);
		java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
		c.async(() -> {
			Setup.runDirect(c.settings, c.pins, c.l, url -> Net.download(url, 64L << 20), s -> Ctx.later(() -> step.setText(s)));
			Ctx.later(() -> step.setText(c.t("Signing in…", "サインイン中…")));
			MicrosoftAuth.Session session = Account.session(c.launcherDir, c.settings.msClientId, new MicrosoftAuth());
			GameFiles.Prepared prepared = GameFiles.prepare(c.settings.minecraft(), c.launcherDir.resolve("runtime"), c.pins, false, new GameFiles.Progress() {
				@Override
				public void stage(String en, String ja) {
					Ctx.later(() -> step.setText(c.t(en, ja)));
				}

				@Override
				public void count(long done, long total) {
					Ctx.later(() -> {
						progress.setIndeterminate(false);
						progress.setMaximum((int) Math.max(1, total));
						progress.setValue((int) done);
					});
				}
			}, stop::get);
			java.nio.file.Path natives = c.launcherDir.resolve("natives").resolve(c.pins.versionId());
			List<String> cmd = GameLauncher.command(prepared, c.settings.instance(), natives, session, c.settings.msClientId,
					new GameLauncher.Options(c.settings.memoryMb, c.settings.extraJvmArgs, c.settings.width, c.settings.height, c.settings.joinHypixel));
			LauncherLog.info("starting the game: " + GameLauncher.describe(cmd, session));
			return GameLauncher.start(cmd, c.settings.instance(), natives, c.launcherDir);
		}, process -> {
			finishDirect();
			c.playedAt = System.currentTimeMillis();
			step.setText(c.t("Minecraft is starting…", "Minecraft を起動しています…"));
			c.toast("play", Theme.ACCENT, c.t("Minecraft is starting", "Minecraft を起動しています"), c.settings.joinHypixel
					? c.t("It joins Hypixel (mc.hypixel.net) when the game has loaded.", "ゲームの読み込み後に Hypixel（mc.hypixel.net）に接続します。")
					: c.t("Have fun!", "楽しんでください！"));
			process.onExit().thenAccept(p -> Ctx.later(() -> {
				step.setText(" ");
				if (p.exitValue() != 0 && System.currentTimeMillis() - c.playedAt > 3_000) {
					c.toast("alert", Theme.BAD, c.t("The game stopped with an error", "ゲームがエラーで終了しました"), c.t("Exit code " + p.exitValue(), "終了コード " + p.exitValue()),
							c.t("Analyze", "分析"), () -> c.navigate.accept("logs"));
				}
				refresh();
			}));
			if (c.settings.closeAfterPlay) new Timer(1500, e -> System.exit(0)).start();
		}, e -> {
			finishDirect();
			step.setText(" ");
			if (e instanceof MicrosoftAuth.AuthException a) {
				c.toast("alert", Theme.BAD, c.t("Sign-in needed", "サインインが必要です"), a.text(c.l == dev.skypilot.mod.core.Lang.JA),
						c.t("Sign in", "サインイン"), () -> SignInDialog.open(c, root, c.rebuild));
			} else {
				c.fail(c.t("Could not start the game", "ゲームを起動できませんでした"), e);
			}
		});
	}

	private void finishDirect() {
		installing = false;
		play.setBusy(false);
		progress.setIndeterminate(false);
		progress.setVisible(false);
		refresh();
	}

	private void watch() {
		if (installing) return;
		GameWatcher.Status s = GameWatcher.check(c.settings.instance(), c.playedAt == 0 ? System.currentTimeMillis() - 60_000 : c.playedAt);
		GameWatcher.State before = game;
		game = s.state() == GameWatcher.State.CRASHED ? GameWatcher.State.IDLE : s.state();
		if (s.state() == GameWatcher.State.CRASHED && c.playedAt > 0) {
			c.playedAt = 0;
			c.toast("alert", Theme.BAD, c.t("The game crashed", "ゲームがクラッシュしました"), c.t("SkyPilot can explain what went wrong.", "原因の説明を表示できます。"),
					c.t("Analyze", "分析"), () -> c.navigate.accept("logs"));
		}
		if (before != game) updateHero();
	}
}
