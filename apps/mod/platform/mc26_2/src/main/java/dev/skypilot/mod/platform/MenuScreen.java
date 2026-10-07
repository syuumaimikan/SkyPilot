package dev.skypilot.mod.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.ApiException;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.network.ServerStatusRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The SkyPilot menu: what to do now, roadmap, guides for the current area, AH and Bazaar
 * flips, and alert / display settings. Everything shown comes from the player's SkyPilot
 * server; the screen only displays it (commands are put into the chat box, never sent).
 */
final class MenuScreen extends Screen {
	enum Tab {
		NOW("Now", "今やること"), STATUS("Status & events", "状態・予定"), ROADMAP("Roadmap", "ロードマップ"), FIT("Fit", "適性"), PROGRESS("Guides", "進め方"), GUIDE("Advice", "アドバイス"),
		BUILD("Builds", "ビルド"), GEAR("Gear", "装備"), MONEY("Money", "金策"), AH("AH flips", "AHフリップ"), BAZAAR("Bazaar", "バザー"), ALERTS("Alerts", "通知・表示");

		final String en;
		final String ja;

		Tab(String en, String ja) {
			this.en = en;
			this.ja = ja;
		}

		static Tab parse(String s) {
			for (Tab t : values()) {
				if (t.name().equalsIgnoreCase(s)) return t;
			}
			return null;
		}
	}

	/** Advisor domains offered in the guide tab (ids of the server's report providers). */
	static final String[][] DOMAINS = {
			{"mining", "Mining", "採掘"}, {"slayer", "Slayer", "スレイヤー"}, {"dungeons", "Dungeons", "ダンジョン"}, {"farming", "Farming", "農業"},
			{"fishing", "Fishing", "釣り"}, {"foraging", "Foraging", "伐採"}, {"kuudra", "Kuudra", "クードラ"}, {"rift", "Rift", "リフト"},
			{"accessories", "Accessories", "アクセサリー"}, {"pets", "Pets", "ペット"}, {"minions", "Minions", "ミニオン"}, {"money", "Money", "金策"},
			{"daily", "Daily", "デイリー"}, {"events", "Events", "イベント"}, {"gear", "Gear", "装備"}, {"combat", "Combat", "戦闘"},
			{"collections", "Collections", "コレクション"}, {"museum", "Museum", "博物館"}, {"liquidation", "Selling", "売却"},
	};

	private static final int ROW = 10;
	private static Tab lastTab = Tab.NOW;
	private static Dto.Plan plan;
	private static String planError;
	private static final Map<String, Dto.Report> REPORTS = new HashMap<>();
	private static final Map<String, String> REPORT_ERRORS = new HashMap<>();
	private static String guideDomain;
	private static int dataVersion;
	private static Dto.Guides guides;
	private static String guidesError;
	private static Dto.FarmSummary[] farms;
	private static Dto.Gear gear;
	private static String gearError;
	private static Dto.Fit fit;
	private static String fitError;
	private static Dto.Builds builds;
	private static String buildsError;
	private static String buildId;
	private static Dto.Money money;
	private static String moneyError;
	private static Dto.News news;

	private record Line(int height, Drawer drawer, IntConsumer onClick, Supplier<Component> tooltip) {
	}

	@FunctionalInterface
	private interface Drawer {
		void draw(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, boolean hovered);
	}

	private final Screen parent;
	private Tab tab;
	private final List<Line> lines = new ArrayList<>();
	private final Set<String> expanded = new HashSet<>();
	private int panelX;
	private int panelW;
	private int contentY;
	private int contentH;
	private double scroll;
	private int docHeight;
	private int builtVersion = -1;

	MenuScreen(Screen parent, Tab tab) {
		super(Component.literal("SkyPilot"));
		this.parent = parent;
		this.tab = tab == null ? lastTab : tab;
	}

	static void open(Tab tab) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new MenuScreen(null, tab)));
	}

	// ---------------------------------------------------------------- layout & data

	@Override
	protected void init() {
		Lang l = SkyPilot.get().lang();
		panelW = Math.min(width - 16, 460);
		panelX = (width - panelW) / 2;
		contentY = 70;
		contentH = Math.max(40, height - contentY - 30);
		Tab[] tabs = Tab.values();
		int perRow = (tabs.length + 1) / 2;
		int tabW = (panelW - (perRow - 1) * 2) / perRow;
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			int col = i % perRow;
			int row = i / perRow;
			Button b = ClientButton.create(Component.literal(l.t(t.en, t.ja)), btn -> switchTo(t)).bounds(panelX + col * (tabW + 2), 22 + row * 22, tabW, 20).build();
			b.active = t != tab;
			addRenderableWidget(b);
		}
		int bw = (panelW - 4) / 3;
		int by = height - 24;
		addRenderableWidget(ClientButton.create(Component.literal(l.t("Refresh", "更新")), b -> load(true)).bounds(panelX, by, bw, 20).build());
		addRenderableWidget(ClientButton.create(Component.literal(l.t("Settings", "設定")), b -> minecraft.gui.setScreen(new SettingsScreen(this))).bounds(panelX + bw + 2, by, bw, 20).build());
		addRenderableWidget(ClientButton.create(Component.literal(l.t("Close", "閉じる")), b -> onClose()).bounds(panelX + 2 * (bw + 2), by, bw, 20).build());
		load(false);
		builtVersion = -1;
	}

	private void switchTo(Tab t) {
		tab = t;
		lastTab = t;
		scroll = 0;
		expanded.clear();
		rebuildWidgets();
	}

	private void load(boolean force) {
		SkyPilot sp = SkyPilot.get();
		ApiClient api = sp.api();
		String lang = sp.lang().code();
		switch (tab) {
			case NOW, ROADMAP -> {
				if (api == null) {
					planError = sp.apiError();
				} else if (force || plan == null) {
					if (force) api.clearCache();
					api.plan(lang).whenComplete((p, err) -> minecraft.execute(() -> {
						if (p != null) {
							plan = p;
							planError = null;
						} else {
							planError = errorText(err);
						}
						dataVersion++;
					}));
				}
			}
			case GUIDE -> loadReport(domain(), force);
			case PROGRESS -> {
				if (api != null && (force || guides == null)) {
					api.guides(lang).whenComplete((g, err) -> minecraft.execute(() -> {
						if (g != null) {
							guides = g;
							guidesError = null;
						} else {
							guidesError = errorText(err);
						}
						dataVersion++;
					}));
					api.farms().whenComplete((f, err) -> minecraft.execute(() -> {
						if (f != null) farms = f;
						dataVersion++;
					}));
				}
			}
			case GEAR -> {
				if (api != null && (force || gear == null)) {
					api.gear(lang).whenComplete((g, err) -> minecraft.execute(() -> {
						if (g != null) {
							gear = g;
							gearError = null;
						} else {
							gearError = errorText(err);
						}
						dataVersion++;
					}));
				}
			}
			case FIT -> {
				if (api != null && (force || fit == null)) {
					api.fit(lang).whenComplete((f, err) -> minecraft.execute(() -> {
						if (f != null) {
							fit = f;
							fitError = null;
						} else {
							fitError = errorText(err);
						}
						dataVersion++;
					}));
				}
			}
			case BUILD -> {
				if (api != null && (force || builds == null)) {
					api.builds(lang).whenComplete((b, err) -> minecraft.execute(() -> {
						if (b != null) {
							builds = b;
							buildsError = null;
						} else {
							buildsError = errorText(err);
						}
						dataVersion++;
					}));
				}
			}
			case MONEY -> {
				if (api != null && (force || money == null)) {
					api.money(lang).whenComplete((m, err) -> minecraft.execute(() -> {
						if (m != null) {
							money = m;
							moneyError = null;
						} else {
							moneyError = errorText(err);
						}
						dataVersion++;
					}));
				}
			}
			case STATUS -> {
				HypixelStatus.refresh(minecraft, force, () -> dataVersion++);
				if (api != null) {
					if (force || Timers.time() == null) api.calendar().whenComplete((c, err) -> minecraft.execute(() -> {
						if (c != null) Timers.setCalendar(c);
						dataVersion++;
					}));
					if (force || Timers.skyblock == null) api.skyblockNow().whenComplete((sb, err) -> minecraft.execute(() -> {
						if (sb != null) Timers.skyblock = sb;
						dataVersion++;
					}));
					if (force || news == null) api.news().whenComplete((n, err) -> minecraft.execute(() -> {
						if (n != null) news = n;
						dataVersion++;
					}));
				}
			}
			case AH -> MarketData.refresh(MarketData.AH, force);
			case BAZAAR -> MarketData.refresh(MarketData.BAZAAR, force);
			case ALERTS -> {
			}
		}
		dataVersion++;
	}

	private void loadReport(String domain, boolean force) {
		ApiClient api = SkyPilot.get().api();
		if (api == null || (!force && REPORTS.containsKey(domain))) return;
		if (force) api.clearCache();
		api.report(domain, SkyPilot.get().lang().code()).whenComplete((r, err) -> minecraft.execute(() -> {
			if (r != null) {
				REPORTS.put(domain, r);
				REPORT_ERRORS.remove(domain);
			} else {
				REPORT_ERRORS.put(domain, errorText(err));
			}
			dataVersion++;
		}));
	}

	private static String errorText(Throwable err) {
		Lang l = SkyPilot.get().lang();
		if (err != null && err.getCause() instanceof ApiException e && e.unauthorized()) return l.t("Not linked - click here or run /skypilot link", "未連携です - ここをクリックするか /skypilot link を実行");
		return SkyPilot.shortError(err);
	}

	/** Guide domain: chosen by the player, else the one for the current area, else money. */
	static String domain() {
		if (guideDomain != null) return guideDomain;
		Dto.HudState h = SkyPilot.get().hudState();
		String ctx = h != null && h.context() != null ? h.context().context() : null;
		String byArea = ctx == null ? null : switch (ctx) {
			case "MINING" -> "mining";
			case "GARDEN" -> "farming";
			case "DUNGEON" -> "dungeons";
			case "KUUDRA" -> "kuudra";
			case "RIFT" -> "rift";
			case "SLAYER" -> "slayer";
			case "FORAGING" -> "foraging";
			case "FISHING" -> "fishing";
			default -> null;
		};
		return byArea != null ? byArea : "money";
	}

	// ---------------------------------------------------------------- document

	private int textWidth() {
		return panelW - 18;
	}

	private void rebuild() {
		lines.clear();
		Lang l = SkyPilot.get().lang();
		switch (tab) {
			case NOW -> buildNow(l);
			case STATUS -> buildStatus(l);
			case ROADMAP -> buildRoadmap(l);
			case GUIDE -> buildGuide(l);
			case PROGRESS -> buildProgress(l);
			case GEAR -> buildGear(l);
			case FIT -> buildFit(l);
			case BUILD -> buildBuilds(l);
			case MONEY -> buildMoney(l);
			case AH -> buildMarket(l, MarketData.AH);
			case BAZAAR -> buildMarket(l, MarketData.BAZAAR);
			case ALERTS -> buildAlerts(l);
		}
		docHeight = lines.stream().mapToInt(Line::height).sum();
		scroll = Math.max(0, Math.min(scroll, docHeight - contentH));
	}

	private void add(int height, Drawer d) {
		lines.add(new Line(height, d, null, null));
	}

	private void heading(String text, int color) {
		gap(3);
		add(12, (g, x, y, w, mx, hov) -> g.text(font, Component.literal(text).withStyle(ChatFormatting.BOLD), x, y + 1, color, false));
	}

	private void para(String text, int color, int indent) {
		if (text == null || text.isBlank()) return;
		for (FormattedCharSequence seq : font.split(Component.literal(text), textWidth() - indent)) {
			add(ROW, (g, x, y, w, mx, hov) -> g.text(font, seq, x + indent, y, color, false));
		}
	}

	private void link(String text, int color, Runnable action, String tooltip) {
		for (FormattedCharSequence seq : font.split(Component.literal(text), textWidth())) {
			lines.add(new Line(ROW + 1, (g, x, y, w, mx, hov) -> {
				g.text(font, seq, x, y, hov ? Ui.WHITE : color, false);
				if (hov) g.fill(x, y + 9, x + Math.min(w, font.width(seq)), y + 10, color);
			}, rel -> action.run(), tooltip == null ? null : () -> Component.literal(tooltip)));
		}
	}

	private void bar(double progress, int color, String label) {
		double p = Math.max(0, Math.min(1, progress));
		add(12, (g, x, y, w, mx, hov) -> {
			int bw = Math.min(w - 60, 220);
			g.fill(x, y + 3, x + bw, y + 8, 0x40FFFFFF);
			g.fill(x, y + 3, x + (int) Math.round(bw * p), y + 8, color);
			g.text(font, label, x + bw + 6, y + 1, Ui.GRAY, false);
		});
	}

	private void gap(int h) {
		add(h, (g, x, y, w, mx, hov) -> {
		});
	}

	/** An item row: icon + title + up to two sub lines. */
	private void itemRow(Supplier<ItemStack> icon, String title, int titleColor, String sub, int subColor, String sub2, int sub2Color, Runnable onClick,
			Supplier<Component> tooltip) {
		int h = sub2 == null ? 21 : 30;
		lines.add(new Line(h, (g, x, y, w, mx, hov) -> {
			if (hov && onClick != null) g.fill(x - 2, y, x + w, y + h - 1, 0x22FFFFFF);
			g.item(icon.get(), x, y + 2);
			g.text(font, font.plainSubstrByWidth(title, w - 22), x + 20, y + 1, titleColor, false);
			if (sub != null) g.text(font, font.plainSubstrByWidth(sub, w - 22), x + 20, y + 11, subColor, false);
			if (sub2 != null) g.text(font, font.plainSubstrByWidth(sub2, w - 22), x + 20, y + 20, sub2Color, false);
		}, onClick == null ? null : rel -> onClick.run(), tooltip));
	}

	private void notLinkedOrError(Lang l, String error) {
		if (error == null) {
			para(l.t("Loading…", "読み込み中…"), Ui.GRAY, 0);
			return;
		}
		if (!SkyPilot.get().linked()) {
			link(error, Ui.YELLOW, () -> {
				onClose();
				Commands.startLink();
			}, null);
		} else {
			para(error, Ui.RED, 0);
		}
	}

	private void buildNow(Lang l) {
		if (plan == null) {
			notLinkedOrError(l, planError);
			return;
		}
		if (plan.message() != null) {
			para(plan.message(), Ui.YELLOW, 0);
			return;
		}
		if (plan.profile() != null) para(plan.profile() + " · " + l.t("analysed ", "分析 ") + Text.duration(Math.max(0, System.currentTimeMillis() - plan.analyzedAtMs())) + l.t(" ago", "前"), Ui.GRAY, 0);
		for (Dto.PlanGoal goal : plan.goals()) {
			heading(goal.title(), Ui.GOLD);
			bar(goal.progress(), 0xFF9D8CFF, Ui.pct(goal.progress()) + goalRest(goal, l));
		}
		heading(l.t("What should I do now?", "今やるべきこと"), Ui.PURPLE);
		int i = 1;
		for (Dto.PlanAction a : plan.actions()) {
			gap(2);
			para(i++ + ". " + a.title(), Ui.WHITE, 0);
			para(a.why(), Ui.GRAY, 10);
			List<String> m = new ArrayList<>();
			for (Dto.HudLine x : a.metrics()) m.add(x.label() + ": " + x.value());
			if (!m.isEmpty()) para(String.join(" · ", m), Ui.AQUA, 10);
			for (String s : a.steps()) para("• " + s, Ui.GRAY, 10);
			guideLink(a.place(), l);
			for (Dto.ItemRef ref : a.items()) {
				itemRow(() -> ItemIcons.of(ref.icon()), ref.name(), Ui.WHITE, ref.price() != null ? Text.coins(ref.price()) : l.t("price unknown", "価格不明"), Ui.GOLD, null, 0,
						null, null);
			}
		}
		for (String w : plan.warnings()) para("! " + w, Ui.YELLOW, 0);
	}

	/** "▶ Guide: Maddox the Slayer (Hub) · /warp crypt" - draws the guide line there. */
	private void guideLink(Dto.Place p, Lang l) {
		if (p == null || p.waypoint() == null) return;
		String text = "▶ " + l.t("Guide: ", "ガイド: ") + p.waypoint().name() + " (" + Guides.island(p.waypoint().island()) + ")" + (p.warp() != null ? " · " + p.warp() : "");
		link(text, Ui.AQUA, () -> Guides.go(p, true), l.t("Draw a guide line to this place (the warp is only put into your chat box)", "この場所へのガイド線を表示（ワープはチャット欄に入れるだけです）")
				+ (p.note() != null ? "\n" + p.note() : ""));
	}

	// ---------------------------------------------------------------- status & events

	private void buildStatus(Lang l) {
		long now = System.currentTimeMillis();
		Dto.ServerStatus s = HypixelStatus.status;
		heading(l.t("Hypixel server status", "Hypixelサーバーの状態"), Ui.PURPLE);
		if (s == null) {
			if (HypixelStatus.error != null) para(HypixelStatus.error, Ui.RED, 0);
			else para(l.t("Loading…", "読み込み中…"), Ui.GRAY, 0);
		} else if (!s.available()) {
			para(l.t("Hypixel's status page is not available right now.", "Hypixelのステータスページを今は取得できません。"), Ui.YELLOW, 0);
		} else {
			for (Dto.ServiceStatus v : orEmpty(s.services())) statusRow(v.name(), HypixelStatus.service(v.status(), l), HypixelStatus.serviceColor(v.status()));
			para(l.t("Checked ", "確認: ") + Text.duration(Math.max(0, now - s.checkedAtMs())) + l.t(" ago · source: status.hypixel.net", "前 · 出典: status.hypixel.net"), Ui.DARK_GRAY, 0);
			heading(l.t("Maintenance", "メンテナンス"), Ui.GOLD);
			if (orEmpty(s.maintenances()).isEmpty()) para(l.t("No maintenance announced.", "予定されているメンテナンスはありません。"), Ui.GREEN, 0);
			for (Dto.Maintenance m : orEmpty(s.maintenances())) {
				gap(2);
				boolean running = ServerStatusRules.running(m);
				para(m.name() + " · " + HypixelStatus.maintenanceStatus(m.status(), l), running ? Ui.RED : Ui.YELLOW, 0);
				para(HypixelStatus.when(m, now, l), Ui.WHITE, 10);
				if (!m.skyblock() && !orEmpty(m.services()).isEmpty()) para(String.join(", ", m.services()), Ui.GRAY, 10);
				para(m.update(), Ui.GRAY, 10);
				webLink(m.link(), l);
			}
			if (!orEmpty(s.incidents()).isEmpty()) {
				heading(l.t("Incidents", "障害"), Ui.RED);
				for (Dto.Incident i : s.incidents()) {
					gap(2);
					para(i.name(), Ui.RED, 0);
					para(HypixelStatus.impact(i.impact(), l) + " · " + HypixelStatus.incidentStatus(i.status(), l)
							+ (i.startedMs() != null ? " · " + l.t("since ", "発生 ") + HypixelStatus.clock(i.startedMs(), now) : "")
							+ (orEmpty(i.services()).isEmpty() ? "" : " · " + String.join(", ", i.services())), Ui.GRAY, 10);
					para(i.update(), Ui.GRAY, 10);
					webLink(i.link(), l);
				}
			}
			if (!orEmpty(s.recentMaintenances()).isEmpty()) {
				heading(l.t("Recent SkyBlock maintenance", "最近のSkyBlockメンテナンス"), Ui.GOLD);
				for (Dto.Maintenance m : s.recentMaintenances()) {
					para(HypixelStatus.clock(m.startsMs(), now) + " - " + HypixelStatus.clock(m.endsMs(), now), Ui.WHITE, 0);
					para(m.update(), Ui.GRAY, 10);
				}
			}
		}
		gap(4);
		toggle(l.t("Status line on the info HUD", "情報HUDに状態を表示"), Feat.on(Features.HUD_SERVER_STATUS), () -> flipFeature(Features.HUD_SERVER_STATUS));
		toggle(l.t("Maintenance alerts", "メンテナンス通知"), Feat.on(Features.STATUS_ALERTS), () -> flipFeature(Features.STATUS_ALERTS));

		heading(l.t("SkyBlock events", "SkyBlockのイベント予定"), Ui.PURPLE);
		List<Timers.Upcoming> events = Timers.upcoming(now);
		if (events.isEmpty()) para(SkyPilot.get().api() == null ? SkyPilot.get().apiError() : l.t("Loading…", "読み込み中…"), Ui.GRAY, 0);
		var sbTime = Timers.time();
		if (sbTime != null) para(l.t("Now: ", "現在: ") + sbTime.format(now), Ui.GRAY, 0);
		int n = 0;
		for (Timers.Upcoming u : events) {
			if (n++ >= 20) break;
			boolean active = u.active(now);
			String right = active ? l.t("now · ", "開催中 · 残り ") + InfoHud.eta(u.endMs() - now)
					: l.t("in ", "あと ") + InfoHud.eta(u.startMs() - now) + " · " + HypixelStatus.clock(u.startMs(), now);
			statusRow(u.name(), right, active ? Ui.GREEN : Ui.GRAY);
		}

		var sb = Timers.skyblock;
		if (sb != null && sb.mayor() != null) {
			heading(l.t("Mayor", "市長"), Ui.GOLD);
			para(sb.mayor().name() + (sb.year() != null ? l.t(" · Year ", " · ") + sb.year() + l.t("", "年") : ""), Ui.WHITE, 0);
			for (var p : orEmpty(sb.mayor().perks())) para("• " + Text.strip(p.name()), Ui.GRAY, 10);
			if (sb.minister() != null) {
				para(l.t("Minister: ", "大臣: ") + sb.minister().name(), Ui.WHITE, 0);
				for (var p : orEmpty(sb.minister().perks())) para("• " + Text.strip(p.name()), Ui.GRAY, 10);
			}
		}
		if (sb != null && !orEmpty(sb.fireSales()).isEmpty()) {
			heading(l.t("Fire sales", "ファイアセール"), Ui.RED);
			for (Dto.FireSale f : sb.fireSales()) {
				boolean on = f.startMs() <= now && now < f.endMs();
				statusRow(f.name(), on ? l.t("now · ends in ", "開催中 · 残り ") + InfoHud.eta(f.endMs() - now) : l.t("in ", "あと ") + InfoHud.eta(f.startMs() - now),
						on ? Ui.GREEN : Ui.GRAY);
			}
		}
		if (news != null && !orEmpty(news.items()).isEmpty()) {
			heading(l.t("SkyBlock news", "SkyBlockニュース"), Ui.PURPLE);
			int k = 0;
			for (Dto.NewsItem it : news.items()) {
				if (k++ >= 6) break;
				String text = it.title() + (it.date() == null || it.date().isBlank() ? "" : "  (" + it.date() + ")");
				if (it.link() != null && it.link().startsWith("https://hypixel.net/")) {
					link(text, Ui.AQUA, () -> openLink(it.link()), it.link());
				} else {
					para(text, Ui.GRAY, 0);
				}
			}
		}
	}

	private static <T> List<T> orEmpty(List<T> l) {
		return l == null ? List.of() : l;
	}

	/** Name on the left, value on the right. */
	private void statusRow(String name, String value, int color) {
		add(ROW + 1, (g, x, y, w, mx, hov) -> {
			int vw = font.width(value);
			g.fill(x, y + 3, x + 3, y + 6, color);
			g.text(font, font.plainSubstrByWidth(name, Math.max(20, w - vw - 16)), x + 7, y, Ui.WHITE, false);
			g.text(font, value, x + w - vw, y, color, false);
		});
	}

	private void webLink(String url, Lang l) {
		if (url == null || !url.startsWith("https://status.hypixel.net/")) return;
		link("▶ " + l.t("Open on status.hypixel.net", "status.hypixel.net で開く"), Ui.AQUA, () -> openLink(url), url);
	}

	private void openLink(String url) {
		minecraft.gui.setScreen(new ConfirmLinkScreen(yes -> {
			if (yes) Util.getPlatform().openUri(url);
			minecraft.gui.setScreen(this);
		}, url, false));
	}

	private void flipFeature(Features.Feature f) {
		var cfg = SkyPilot.get().config();
		cfg.features.put(f.id(), !cfg.on(f));
		if (cfg.on(f)) HypixelStatus.refresh(minecraft, false, () -> dataVersion++);
	}

	/** For tests: show this status without a server. */
	static void setStatus(Dto.ServerStatus s, Dto.News n) {
		HypixelStatus.set(s);
		news = n;
		dataVersion++;
	}

	// ---------------------------------------------------------------- fit (適性)

	private void buildFit(Lang l) {
		if (fit == null) {
			notLinkedOrError(l, fitError);
			return;
		}
		if (fit.message() != null) {
			para(fit.message(), Ui.YELLOW, 0);
			return;
		}
		heading(l.t("Dungeon class fit", "ダンジョンのクラス適性"), Ui.PURPLE);
		if (fit.recommendedClass() != null) para(l.t("Best fit now: ", "今の適性: ") + fit.recommendedClass(), Ui.GOLD, 0);
		for (Dto.ClassFit c : fit.classes()) {
			bar(c.score(), c.className().equals(fit.recommendedClass()) ? 0xFFFFAA00 : 0xFF9D8CFF,
					c.className() + " " + l.t("Lv ", "Lv ") + c.level() + (c.selected() ? l.t(" (selected)", "（選択中）") : ""));
			para(String.join(" · ", c.reasons()), Ui.GRAY, 10);
		}
		heading(l.t("Floors", "フロア"), Ui.PURPLE);
		for (Dto.FloorFit f : fit.floors()) {
			if (!f.unlocked() && f.readiness() < 0.3) continue;
			bar(f.readiness(), f.recommended() ? Ui.GREEN : 0xFF9D8CFF, f.floor() + " " + Ui.pct(f.readiness()) + (f.recommended() ? l.t(" · recommended", " · おすすめ") : "")
					+ (f.completions() > 0 ? " · " + f.completions() + l.t(" runs", "回") : ""));
			if (f.recommended() || !f.missing().isEmpty() && f.readiness() >= 0.5) {
				for (String m : f.missing()) para("• " + m, Ui.GRAY, 10);
			}
		}
		heading(l.t("Slayers", "スレイヤー"), Ui.PURPLE);
		for (Dto.SlayerFit s : fit.slayers()) {
			bar(s.readiness(), 0xFFFF5555, s.name() + " T" + s.tier() + " " + Ui.pct(s.readiness()) + (s.gearTier() != null ? " · " + s.gearTier() : l.t(" · no gear tier", " · 装備未達")));
		}
		heading(l.t("Path to your goals", "目標までの道のり"), Ui.PURPLE);
		if (fit.goals().isEmpty()) para(l.t("Set a goal on the SkyPilot web to see the path to it.", "SkyPilotのWebで目標を設定すると道のりを表示します。"), Ui.GRAY, 0);
		for (Dto.GoalPath g : fit.goals()) {
			gap(2);
			para(g.title(), Ui.GOLD, 0);
			StringBuilder rest = new StringBuilder(Ui.pct(g.progress()));
			if (g.remainingCost() != null) rest.append(" · ").append(l.t("left ", "残り ")).append(Text.coins(g.remainingCost()));
			if (g.remainingHours() != null) rest.append(" · ").append(Text.duration((long) (g.remainingHours() * 3_600_000)));
			bar(g.progress(), 0xFF9D8CFF, rest.toString());
			int n = 1;
			for (Dto.PlanStep s : g.nextSteps()) {
				para(n++ + ". " + s.title() + (s.cost() != null ? " · " + Text.coins(s.cost()) : ""), Ui.WHITE, 6);
				if (s.detail() != null) para(s.detail(), Ui.GRAY, 14);
			}
			if (g.nextAction() != null) {
				para(l.t("Do now: ", "今やること: ") + g.nextAction().title(), Ui.AQUA, 6);
				guideLink(g.nextAction().place(), l);
			}
		}
		gap(4);
		for (String note : fit.notes()) para(note, Ui.DARK_GRAY, 0);
	}

	// ---------------------------------------------------------------- builds

	private void buildBuilds(Lang l) {
		if (builds == null) {
			notLinkedOrError(l, buildsError);
			return;
		}
		if (builds.message() != null) {
			para(builds.message(), Ui.YELLOW, 0);
			return;
		}
		if (builds.builds().isEmpty()) return;
		Dto.Build current = builds.builds().get(0);
		for (Dto.Build b : builds.builds()) if (b.id().equals(buildId)) current = b;
		para(l.t("Pick a build:", "ビルドを選択:"), Ui.GRAY, 0);
		for (Dto.Build b : builds.builds()) {
			boolean on = b == current;
			link((on ? "● " : "○ ") + b.title() + (b.goal() ? l.t(" ★ goal", " ★ 目標") : ""), on ? Ui.GOLD : Ui.AQUA, () -> {
				buildId = b.id();
				dataVersion++;
			}, null);
		}
		para(l.t("Click an item: how to make it and its price. ✔ = you own it.", "アイテムをクリックで作り方と価格。✔ = 所持。"), Ui.DARK_GRAY, 0);
		for (Dto.BuildTier t : current.tiers()) {
			String tierName = switch (t.tier()) {
				case "REQUIRED" -> l.t("Required", "必須");
				case "RECOMMENDED" -> l.t("Recommended", "推奨");
				default -> l.t("Luxury", "最上位");
			};
			String state = t.complete() ? l.t("complete ✔", "達成 ✔")
					: t.costToComplete() != null ? l.t("to complete: ", "完成まで: ") + Text.coins(t.costToComplete()) : l.t("some parts have no price", "一部価格不明");
			heading(tierName + " · " + state, t.complete() ? Ui.GREEN : Ui.GOLD);
			for (Dto.BuildSlot s : t.slots()) {
				para(s.slot() + (s.done() ? " ✔" : s.cost() != null ? " · " + Text.coins(s.cost()) : ""), s.done() ? Ui.GREEN : Ui.WHITE, 0);
				for (Dto.GearItem it : s.items()) {
					itemRow(() -> ItemIcons.of(it.icon()), it.name() + (it.owned() ? " ✔" : ""), it.owned() ? Ui.GREEN : Ui.WHITE,
							it.price() != null ? Text.coins(it.price()) : l.t("price unknown", "価格不明"), Ui.GOLD, null, 0,
							it.id().startsWith("PET:") ? null : () -> minecraft.gui.setScreen(new ItemDetailScreen(this, it.id())), null);
				}
			}
		}
		para(current.source(), Ui.DARK_GRAY, 0);
	}

	// ---------------------------------------------------------------- money

	private void buildMoney(Lang l) {
		if (money == null) {
			notLinkedOrError(l, moneyError);
			return;
		}
		if (money.message() != null) {
			para(money.message(), Ui.YELLOW, 0);
			return;
		}
		para(l.t("Coins per hour are ranges computed from your profile and live prices - not guarantees.", "時給はプロフィールと最新価格から計算した幅のある推定で、保証ではありません。"), Ui.DARK_GRAY, 0);
		int n = 1;
		for (Dto.MoneyMethod m : money.methods()) {
			gap(2);
			String range = m.lowPerHour() != null && m.highPerHour() != null ? Text.coins(m.lowPerHour()) + "–" + Text.coins(m.highPerHour()) + l.t("/h", "/時") : l.t("rate unknown", "時給不明");
			para(n++ + ". " + m.name(), m.requirementsMet() ? Ui.WHITE : Ui.GRAY, 0);
			para(range + " · " + m.category() + " · " + l.t("risk ", "リスク ") + m.risk() + (m.setupCost() != null && m.setupCost() > 0 ? " · " + l.t("setup ", "初期費用 ") + Text.coins(m.setupCost()) : ""),
					m.requirementsMet() ? Ui.GOLD : Ui.DARK_GRAY, 10);
			if (!m.requirementsMet() && !m.missing().isEmpty()) para(l.t("Needs: ", "必要: ") + String.join(", ", m.missing()), Ui.RED, 10);
			para(m.why(), Ui.GRAY, 10);
			guideLink(m.place(), l);
		}
	}

	private static String goalRest(Dto.PlanGoal goal, Lang l) {
		StringBuilder b = new StringBuilder();
		if (goal.remainingCost() != null) b.append(" · ").append(l.t("left ", "残り ")).append(Text.coins(goal.remainingCost()));
		if (goal.remainingHours() != null) b.append(" · ").append(Text.duration((long) (goal.remainingHours() * 3_600_000)));
		return b.toString();
	}

	private void buildRoadmap(Lang l) {
		if (plan == null || plan.message() != null) {
			buildNow(l);
			return;
		}
		for (Dto.PlanGoal goal : plan.goals()) {
			heading(goal.title(), Ui.GOLD);
			bar(goal.progress(), 0xFF9D8CFF, Ui.pct(goal.progress()) + goalRest(goal, l));
			for (Dto.PlanStep s : goal.steps()) {
				String mark;
				int color;
				switch (s.status()) {
					case "SATISFIED" -> {
						mark = "✔ ";
						color = Ui.GREEN;
					}
					case "IN_PROGRESS" -> {
						mark = "▶ ";
						color = Ui.YELLOW;
					}
					case "BLOCKED" -> {
						mark = "✖ ";
						color = Ui.RED;
					}
					default -> {
						mark = "○ ";
						color = Ui.WHITE;
					}
				}
				gap(2);
				para(mark + s.title() + (s.progress() > 0 && s.progress() < 1 ? " (" + Ui.pct(s.progress()) + ")" : ""), color, 0);
				para(s.detail(), Ui.GRAY, 12);
				List<String> extra = new ArrayList<>();
				if (s.cost() != null) extra.add(l.t("cost ", "費用 ") + Text.coins(s.cost()));
				if (s.hours() != null) extra.add(l.t("time ", "時間 ") + Text.duration((long) (s.hours() * 3_600_000)));
				if (!extra.isEmpty()) para(String.join(" · ", extra), Ui.DARK_GRAY, 12);
			}
		}
	}

	private void buildGuide(Lang l) {
		String current = domain();
		// domain chips, wrapped into rows
		List<String[]> row = new ArrayList<>();
		int used = 0;
		for (String[] d : DOMAINS) {
			int cw = font.width(l.t(d[1], d[2])) + 10;
			if (used + cw > textWidth() && !row.isEmpty()) {
				chipRow(new ArrayList<>(row), current, l);
				row.clear();
				used = 0;
			}
			row.add(d);
			used += cw + 3;
		}
		if (!row.isEmpty()) chipRow(row, current, l);
		if (guideDomain == null) para(l.t("Chosen from your current area. Click a topic to switch.", "現在地に合わせて選んでいます。トピックをクリックで切替。"), Ui.DARK_GRAY, 0);
		Dto.Report r = REPORTS.get(current);
		if (r == null) {
			notLinkedOrError(l, REPORT_ERRORS.get(current));
			return;
		}
		heading(r.title(), Ui.GOLD);
		if (r.summary() != null && !r.summary().isEmpty()) {
			List<String> m = new ArrayList<>();
			for (Dto.Metric x : r.summary()) m.add(x.label() + ": " + metricValue(x));
			para(String.join(" · ", m), Ui.AQUA, 0);
		}
		for (String u : r.unknowns() == null ? List.<String>of() : r.unknowns()) para(l.t("Unknown: ", "不明: ") + u, Ui.YELLOW, 0);
		for (Dto.ReportSection s : r.sections()) {
			heading(s.title(), Ui.PURPLE);
			para(s.description(), Ui.GRAY, 0);
			if (s.metrics() != null && !s.metrics().isEmpty()) {
				List<String> m = new ArrayList<>();
				for (Dto.Metric x : s.metrics()) m.add(x.label() + ": " + metricValue(x));
				para(String.join(" · ", m), Ui.AQUA, 0);
			}
			for (String b : s.bullets() == null ? List.<String>of() : s.bullets()) para("• " + b, Ui.WHITE, 0);
			if (s.table() != null) table(s.table(), l);
			for (String n : s.notes() == null ? List.<String>of() : s.notes()) para(n, Ui.DARK_GRAY, 0);
		}
	}

	private void chipRow(List<String[]> chips, String current, Lang l) {
		lines.add(new Line(15, (g, x, y, w, mx, hov) -> {
			int cx = x;
			for (String[] d : chips) {
				String label = l.t(d[1], d[2]);
				int cw = font.width(label) + 10;
				boolean on = d[0].equals(current);
				boolean over = hov && mx >= cx && mx < cx + cw;
				g.fill(cx, y + 1, cx + cw, y + 13, on ? 0xFF4A3B8C : over ? 0x40FFFFFF : 0x20FFFFFF);
				g.text(font, label, cx + 5, y + 3, on ? Ui.WHITE : Ui.GRAY, false);
				cx += cw + 3;
			}
		}, rel -> {
			int cx = 0;
			for (String[] d : chips) {
				int cw = font.width(l.t(d[1], d[2])) + 10;
				if (rel >= cx && rel < cx + cw) {
					guideDomain = d[0];
					scroll = 0;
					loadReport(d[0], false);
					dataVersion++;
					return;
				}
				cx += cw + 3;
			}
		}, null));
	}

	private void table(Dto.ReportTable t, Lang l) {
		List<String> cols = t.columns() == null ? List.of() : t.columns();
		int shown = 0;
		for (List<JsonObject> row : t.rows()) {
			if (shown++ >= 40) {
				para(l.t("… more on the web", "…続きはWebで"), Ui.DARK_GRAY, 0);
				break;
			}
			JsonObject first = row.isEmpty() ? null : row.get(0);
			StringBuilder rest = new StringBuilder();
			for (int i = 1; i < row.size(); i++) {
				String v = cellText(row.get(i), l);
				if (v.isEmpty()) continue;
				if (rest.length() > 0) rest.append(" · ");
				if (i < cols.size()) rest.append(cols.get(i)).append(": ");
				rest.append(v);
			}
			String head = first == null ? "" : cellText(first, l);
			if (first != null && "item".equals(str(first, "kind"))) {
				String id = str(first, "itemId");
				itemRow(() -> ItemIcons.forId(id), head, Ui.rarityColor(str(first, "rarity")), rest.toString(), Ui.GRAY, null, 0, null, null);
			} else {
				gap(1);
				para(head, Ui.WHITE, 0);
				para(rest.toString(), Ui.GRAY, 10);
			}
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
	}

	/** Table cell (same kinds as the web: text, number, coins, percent, duration, progress, badge, item, unknown). */
	static String cellText(JsonObject c, Lang l) {
		String kind = str(c, "kind");
		if (kind == null) return "";
		return switch (kind) {
			case "text", "badge" -> str(c, "text") == null ? "" : str(c, "text");
			case "number" -> {
				double v = num(c, "value");
				String unit = str(c, "unit");
				yield (v == Math.rint(v) ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "%,.1f", v)) + (unit == null ? "" : " " + unit);
			}
			case "coins" -> Text.coins(num(c, "value"));
			case "percent" -> Ui.pct(num(c, "value"));
			case "duration" -> Text.duration((long) (num(c, "hours") * 3_600_000));
			case "progress" -> str(c, "label") != null ? str(c, "label") : Ui.pct(num(c, "value"));
			case "item" -> str(c, "name") == null ? "" : str(c, "name");
			case "unknown" -> l.t("Unknown", "不明");
			default -> "";
		};
	}

	private static String metricValue(Dto.Metric m) {
		boolean coins = "coins".equals(m.unit());
		java.util.function.DoubleFunction<String> f = v -> coins ? Text.coins(v) : v == Math.rint(v) ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "%,.1f", v);
		String unit = m.unit() == null || coins ? "" : " " + m.unit();
		if (m.text() != null) return m.text();
		if (m.before() != null && m.after() != null) return f.apply(m.before()) + " → " + f.apply(m.after()) + unit;
		if (m.value() != null) return f.apply(m.value()) + unit;
		return "?";
	}

	private void buildMarket(Lang l, String kind) {
		Dto.MarketPanel panel = MarketData.get(kind);
		if (panel == null) {
			String err = MarketData.error(kind);
			para(err != null ? err : l.t("Loading…", "読み込み中…"), err != null ? Ui.RED : Ui.GRAY, 0);
			return;
		}
		para(panel.title() + " · " + (panel.stale() ? l.t("STALE DATA", "データが古い") : l.t("data ", "データ ") + panel.ageSeconds() + l.t("s old", "秒前")), panel.stale() ? Ui.RED : Ui.GRAY, 0);
		para(panel.note(), Ui.DARK_GRAY, 0);
		if (panel.rows().isEmpty()) para(l.t("No opportunities right now.", "現在、条件に合う機会はありません。"), Ui.GRAY, 0);
		for (Dto.FlipRow r : panel.rows()) {
			boolean open = expanded.contains(r.id());
			String title = (r.doNotFlip() ? "⚠ DO NOT FLIP · " : "") + r.name() + (r.modifiers().isEmpty() ? "" : " [" + String.join(", ", r.modifiers()) + "]");
			itemRow(() -> ItemIcons.of(r.icon()), title, r.doNotFlip() ? Ui.RED : Ui.rarityColor(r.rarity()), Ui.flipLine(r), r.doNotFlip() ? Ui.GRAY : Ui.GREEN,
					Ui.flipDetail(r, l), Ui.confidenceColor(r.confidenceLabel()), () -> {
						if (!expanded.remove(r.id())) expanded.add(r.id());
						dataVersion++;
					}, () -> Component.literal(l.t("Click for details", "クリックで詳細")));
			if (!open) continue;
			for (String n : r.notes()) para("• " + n, r.doNotFlip() ? Ui.RED : Ui.GRAY, 20);
			if (r.doNotFlip()) para(l.t("Suspected manipulation - do not flip this.", "相場操作の疑いがあります。フリップしないでください。"), Ui.RED, 20);
			if (r.command() != null) {
				lines.add(new Line(ROW + 2, (g, x, y, w, mx, hov) -> {
					String t = l.t("[Put into chat box] ", "[チャット欄に入力] ") + r.command();
					g.text(font, font.plainSubstrByWidth(t, w - 20), x + 20, y + 1, hov ? Ui.WHITE : Ui.YELLOW, false);
				}, rel -> Ui.suggest(r.command()), () -> Component.literal(l.t("Only fills the chat box - you decide whether to send it.", "チャット欄に入力するだけです。送信するかはご自身で判断してください。"))));
			}
		}
	}

	private void buildProgress(Lang l) {
		Dto.Waypoint active = Waypoints.target();
		if (active != null) {
			para(l.t("Following: ", "案内中: ") + active.name() + " (" + dev.skypilot.mod.core.Islands.name(active.island(), l) + ")", Ui.GREEN, 0);
			link(l.t("[Stop guiding]", "[案内を終了]"), Ui.YELLOW, () -> {
				Waypoints.clear();
				dataVersion++;
			}, null);
		}
		if (guides == null) {
			notLinkedOrError(l, guidesError);
		} else {
			String here = Waypoints.currentIsland();
			for (Dto.Guide g : guides.guides()) {
				heading(g.title(), Ui.GOLD);
				for (int i = 0; i < g.steps().size(); i++) {
					Dto.GuideStep s = g.steps().get(i);
					boolean done = Boolean.TRUE.equals(s.done());
					gap(2);
					para((done ? "✔ " : (i + 1) + ". ") + s.title(), done ? Ui.GREEN : Ui.WHITE, 0);
					para(s.text(), Ui.GRAY, 12);
					if (s.waypoint() != null) {
						int index = i;
						boolean there = s.waypoint().island().equals(here);
						String where = dev.skypilot.mod.core.Islands.name(s.waypoint().island(), l);
						link("   ➜ " + l.t("Show the way to ", "ここへ案内: ") + s.waypoint().name() + (there ? "" : " (" + where + ")"), there ? Ui.AQUA : Ui.PURPLE, () -> {
							Waypoints.follow(g, index);
							onClose();
						}, l.t("A marker, its distance and a straight line appear in the world. You walk there yourself.", "目印・距離・直線のガイド線がワールドに表示されます。移動はご自身で行います。"));
					}
				}
				para(l.t("Source: ", "出典: ") + g.source(), Ui.DARK_GRAY, 0);
			}
			para(guides.attribution(), Ui.DARK_GRAY, 0);
		}
		heading(l.t("Farm build guide (Garden)", "ファーム建築ガイド（ガーデン）"), Ui.PURPLE);
		para(l.t("Stand on the corner block where the farm should start, face along the farm, then pick a design. Missing blocks of each layer are outlined in the world.",
				"ファームを始める角のブロックに立ち、ファームを伸ばす方向を向いてから設計を選びます。各層の未設置ブロックがワールド上に枠で表示されます。"), Ui.GRAY, 0);
		if (FarmBuilder.active()) {
			para(l.t("Building: ", "建築中: ") + FarmBuilder.design().summary().name() + " · /skypilot farm next | prev | stop", Ui.GREEN, 0);
		}
		if (farms == null) {
			para(l.t("Loading designs…", "設計を読み込み中…"), Ui.GRAY, 0);
		} else {
			for (Dto.FarmSummary f : farms) {
				link("➜ " + f.name() + " (" + f.size().get(0) + "×" + f.size().get(2) + ")", Ui.AQUA, () -> {
					var api = SkyPilot.get().api();
					if (api == null) return;
					api.farm(f.id(), SkyPilot.get().lang().code()).whenComplete((d, err) -> minecraft.execute(() -> {
						if (d != null) FarmBuilder.start(d);
						else McText.error(SkyPilot.shortError(err));
					}));
					onClose();
				}, f.description());
			}
		}
	}

	private void buildGear(Lang l) {
		if (gear == null) {
			notLinkedOrError(l, gearError);
			return;
		}
		if (gear.message() != null) {
			para(gear.message(), Ui.YELLOW, 0);
			return;
		}
		para(l.t("Click an item to see how to make it (recipes, Forge, drops) and its price.", "アイテムをクリックすると作り方（レシピ・フォージ・ドロップ）と価格を表示します。"), Ui.GRAY, 0);
		for (Dto.GearSection sec : gear.sections()) {
			heading(sec.title(), Ui.GOLD);
			for (Dto.GearItem it : sec.items()) {
				String sub = it.slot() + " · " + it.tier() + (it.price() != null ? " · " + Text.coins(it.price()) : "") + (it.owned() ? l.t(" · owned ✔", " · 所持 ✔") : "");
				itemRow(() -> ItemIcons.of(it.icon()), it.name(), it.owned() ? Ui.GREEN : Ui.WHITE, sub, Ui.GRAY, it.note(), Ui.DARK_GRAY,
						() -> minecraft.gui.setScreen(new ItemDetailScreen(this, it.id())), () -> Component.literal(l.t("Click: how to make it", "クリック: 作り方")));
			}
		}
	}

	private void buildAlerts(Lang l) {
		SkyPilot sp = SkyPilot.get();
		var m = sp.config().market;
		heading(l.t("Flip alerts", "フリップ通知"), Ui.PURPLE);
		para(l.t("Every 30 s your SkyPilot server is asked for new AH flips matching your alert rules (set them on the web; without rules: profit ≥ 1M, ROI ≥ 10%, confidence ≥ 70%). New ones appear as a toast and a chat line. Manipulation-flagged flips never alert.",
				"30秒ごとにSkyPilotサーバーへ、通知ルールに合う新しいAHフリップを問い合わせます（ルールはWebで設定。未設定時は利益100万以上・ROI 10%以上・信頼度70%以上）。見つかるとトースト通知とチャットに表示します。相場操作の疑いがあるものは通知しません。"),
				Ui.GRAY, 0);
		toggle(l.t("Flip alerts", "フリップ通知"), m.alerts, () -> m.alerts = !m.alerts);
		toggle(l.t("Alert sound", "通知音"), m.alertSound, () -> m.alertSound = !m.alertSound);
		heading(l.t("In Hypixel's windows", "Hypixelの画面内"), Ui.PURPLE);
		para(l.t("Shows SkyPilot's current flips beside the Auction House / Bazaar windows (needs some free space to the left of the window).",
				"オークション・バザーの画面の左側に、SkyPilotの現在のフリップを表示します（画面左に余白が必要です）。"), Ui.GRAY, 0);
		toggle(l.t("AH / Bazaar panel", "AH・バザー横パネル"), m.containerPanel, () -> m.containerPanel = !m.containerPanel);
		toggle(l.t("Price lines in item tooltips", "アイテム説明の価格表示"), sp.config().priceTooltip, () -> sp.config().priceTooltip = !sp.config().priceTooltip);
		if (!sp.linked()) {
			gap(4);
			link(l.t("Alerts need a linked SkyPilot account - click to link", "通知にはSkyPilotとの連携が必要です - クリックで連携"), Ui.YELLOW, () -> {
				onClose();
				Commands.startLink();
			}, null);
		}
		if (FlipAlerts.lastError() != null) para(FlipAlerts.lastError(), Ui.RED, 0);
		heading(l.t("Recent alerts", "最近の通知"), Ui.PURPLE);
		List<Dto.FlipRow> recent = FlipAlerts.recent();
		if (recent.isEmpty()) para(l.t("None yet.", "まだありません。"), Ui.GRAY, 0);
		for (Dto.FlipRow r : recent) {
			itemRow(() -> ItemIcons.of(r.icon()), r.name(), Ui.rarityColor(r.rarity()), Ui.flipLine(r), Ui.GREEN, r.command(), Ui.YELLOW,
					r.command() == null ? null : () -> Ui.suggest(r.command()), () -> Component.literal(l.t("Click to put the command into the chat box", "クリックでコマンドをチャット欄に入力")));
		}
	}

	private void toggle(String label, boolean on, Runnable flip) {
		Lang l = SkyPilot.get().lang();
		lines.add(new Line(16, (g, x, y, w, mx, hov) -> {
			g.fill(x, y + 2, x + 26, y + 13, on ? 0xFF3C8C4A : 0xFF5A5A5A);
			g.fill(on ? x + 15 : x + 1, y + 3, on ? x + 25 : x + 11, y + 12, 0xFFEEEEEE);
			g.text(font, label + ": " + (on ? l.t("ON", "オン") : l.t("OFF", "オフ")), x + 32, y + 4, hov ? Ui.WHITE : Ui.GRAY, false);
		}, rel -> {
			flip.run();
			SkyPilot.get().saveConfig();
			dataVersion++;
		}, null));
	}

	// ---------------------------------------------------------------- rendering & input

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (SkyPilot.get().config().appearance.richMenus) ClientStyle.backdrop(g, width, height);
		super.extractRenderState(g, mouseX, mouseY, delta);
		int version = dataVersion * 31 + versionOf(MarketData.get(MarketData.AH)) * 7 + versionOf(MarketData.get(MarketData.BAZAAR))
				// countdowns on the status tab
				+ (tab == Tab.STATUS ? (int) (System.currentTimeMillis() / 1000) * 13 : 0);
		if (version != builtVersion) {
			builtVersion = version;
			rebuild();
		}
		ItemIcons.flush();
		Font f = font;
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		g.centeredText(f, Component.literal("SkyPilot").withStyle(ChatFormatting.BOLD), width / 2, 6, Ui.PURPLE);
		g.text(f, f.plainSubstrByWidth((sp.linked() ? l.t("Linked", "連携済み") : l.t("Not linked", "未連携")) + " · " + l.t("information only", "情報表示のみ"), panelW / 2 - 40),
				panelX, 8, Ui.DARK_GRAY, false);
		int top = contentY - 2;
		g.fill(panelX, top, panelX + panelW, contentY + contentH + 2, Ui.PANEL);
		g.outline(panelX, top, panelW, contentH + 4, Ui.PANEL_BORDER);
		g.enableScissor(panelX + 1, contentY, panelX + panelW - 1, contentY + contentH);
		int x = panelX + 6;
		int y = contentY + 2 - (int) scroll;
		int w = panelW - 18;
		Supplier<Component> tip = null;
		for (Line line : lines) {
			if (y + line.height() >= contentY && y <= contentY + contentH) {
				boolean hov = mouseX >= panelX && mouseX < panelX + panelW && mouseY >= Math.max(y, contentY) && mouseY < Math.min(y + line.height(), contentY + contentH);
				line.drawer().draw(g, x, y, w, mouseX - x, hov);
				if (hov && line.tooltip() != null) tip = line.tooltip();
			}
			y += line.height();
		}
		g.disableScissor();
		if (docHeight > contentH) {
			int trackX = panelX + panelW - 5;
			int thumbH = Math.max(12, contentH * contentH / docHeight);
			int thumbY = contentY + (int) ((contentH - thumbH) * (scroll / Math.max(1, docHeight - contentH)));
			g.fill(trackX, contentY, trackX + 3, contentY + contentH, 0x30FFFFFF);
			g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF9D8CFF);
		}
		if (tip != null) Ui.tooltip(g, tip.get(), mouseX, mouseY);
	}

	private static int versionOf(Dto.MarketPanel p) {
		return p == null ? 0 : System.identityHashCode(p);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= contentY && mouseY < contentY + contentH) {
			scroll = Math.max(0, Math.min(docHeight - contentH, scroll - scrollY * 24));
			if (docHeight <= contentH) scroll = 0;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x();
		double my = event.y();
		if (mx < panelX || mx >= panelX + panelW || my < contentY || my >= contentY + contentH) return false;
		int y = contentY + 2 - (int) scroll;
		for (Line line : lines) {
			if (my >= y && my < y + line.height()) {
				if (line.onClick() == null) return false;
				line.onClick().accept((int) mx - (panelX + 6));
				return true;
			}
			y += line.height();
		}
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** For tests: show synthetic data without a server. */
	static void setPlan(Dto.Plan p) {
		plan = p;
		planError = null;
		dataVersion++;
	}

	static void setGuides(Dto.Guides g, Dto.FarmSummary[] f) {
		guides = g;
		farms = f;
		dataVersion++;
	}

	static void setGear(Dto.Gear g) {
		gear = g;
		dataVersion++;
	}

	static void setFit(Dto.Fit f) {
		fit = f;
		dataVersion++;
	}

	static void setBuilds(Dto.Builds b) {
		builds = b;
		dataVersion++;
	}

	static void setMoney(Dto.Money m) {
		money = m;
		dataVersion++;
	}

	static void setReport(String domain, Dto.Report r) {
		REPORTS.put(domain, r);
		dataVersion++;
	}
}
