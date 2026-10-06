package dev.skypilot.mod.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.DeviceFlow;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.session.SessionTracker;
import dev.skypilot.mod.translation.Channel;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /skypilot ...} client commands. They only display information or change SkyPilot
 * settings - no command sends anything to the game server.
 */
final class Commands {
	private static final String[] PANELS = {"goal", "next", "roadmap", "market", "mining", "slayer", "farming"};

	private Commands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> d) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = literal("skypilot").executes(c -> help());
		root.then(literal("help").executes(c -> help()));
		root.then(literal("status").executes(c -> status()));
		root.then(literal("link").executes(c -> startLink()));
		root.then(literal("unlink").executes(c -> {
			SkyPilot.get().unlink();
			MccLang.info("Unlinked. Revoke the device on the website too if you no longer use it.", "連携を解除しました。不要ならWebの設定でもデバイスを解除してください。");
			return 1;
		}));
		for (String panel : PANELS) root.then(literal(panel).executes(c -> panel(panel)));
		root.then(literal("price").executes(c -> priceHeld()));
		root.then(literal("settings").executes(c -> openLater(() -> new SettingsScreen(null))));
		root.then(literal("menu").executes(c -> openLater(() -> new MenuScreen(null, null)))
				.then(argument("tab", StringArgumentType.word())
						.suggests((c, b) -> {
							for (MenuScreen.Tab t : MenuScreen.Tab.values()) b.suggest(t.name().toLowerCase(Locale.ROOT));
							return b.buildFuture();
						})
						.executes(c -> openLater(() -> new MenuScreen(null, MenuScreen.Tab.parse(StringArgumentType.getString(c, "tab")))))));
		root.then(literal("flips").executes(c -> openLater(() -> new MenuScreen(null, MenuScreen.Tab.AH))));
		root.then(literal("guide").executes(c -> openLater(() -> new MenuScreen(null, MenuScreen.Tab.PROGRESS)))
				.then(literal("next").executes(c -> {
					Waypoints.next();
					return 1;
				}))
				.then(literal("prev").executes(c -> {
					Waypoints.prev();
					return 1;
				}))
				.then(literal("stop").executes(c -> {
					Waypoints.clear();
					MccLang.info("Guide stopped.", "ガイドを終了しました。");
					return 1;
				})));
		root.then(literal("advice").executes(c -> openLater(() -> new MenuScreen(null, MenuScreen.Tab.GUIDE))));
		root.then(literal("gear").executes(c -> openLater(() -> new MenuScreen(null, MenuScreen.Tab.GEAR))));
		root.then(literal("item").then(argument("id", StringArgumentType.greedyString())
				.executes(c -> openLater(() -> new ItemDetailScreen(null, StringArgumentType.getString(c, "id").strip().toUpperCase(Locale.ROOT).replace(' ', '_'))))));
		root.then(literal("farm")
				.then(literal("next").executes(c -> {
					FarmBuilder.next();
					return 1;
				}))
				.then(literal("prev").executes(c -> {
					FarmBuilder.prev();
					return 1;
				}))
				.then(literal("stop").executes(c -> {
					FarmBuilder.stop();
					MccLang.info("Farm guide stopped.", "ファーム建築ガイドを終了しました。");
					return 1;
				}))
				.then(literal("start").then(argument("design", StringArgumentType.word()).executes(c -> startFarm(StringArgumentType.getString(c, "design"))))));
		root.then(literal("alerts").then(literal("on").executes(c -> setAlerts(true))).then(literal("off").executes(c -> setAlerts(false))));
		root.then(literal("panel").then(literal("on").executes(c -> setPanel(true))).then(literal("off").executes(c -> setPanel(false))));
		root.then(literal("hud")
				.then(literal("edit").executes(c -> openLater(() -> new HudEditorScreen(null))))
				.then(literal("on").executes(c -> setHud(true)))
				.then(literal("off").executes(c -> setHud(false))));
		root.then(literal("session")
				.executes(c -> sessionStats())
				.then(literal("reset").executes(c -> {
					SkyPilot.get().session().reset();
					MccLang.info("Session statistics reset.", "セッション統計をリセットしました。");
					return 1;
				}))
				.then(literal("upload").then(literal("on").executes(c -> setUpload(true))).then(literal("off").executes(c -> setUpload(false)))));
		root.then(literal("translate")
				.then(literal("consent").executes(c -> consent()))
				.then(literal("click").then(literal("on").executes(c -> setClick(true))).then(literal("off").executes(c -> setClick(false))))
				.then(literal("mode").then(argument("mode", StringArgumentType.word())
						.suggests((c, b) -> {
							for (SkyPilotConfig.TranslationModeSetting m : SkyPilotConfig.TranslationModeSetting.values()) b.suggest(m.name().toLowerCase(Locale.ROOT));
							return b.buildFuture();
						})
						.executes(c -> setMode(StringArgumentType.getString(c, "mode")))))
				.then(argument("text", StringArgumentType.greedyString()).executes(c -> translate(StringArgumentType.getString(c, "text")))));
		// Target of the click-to-translate buttons in chat ("ok" = accept the disclosure first).
		root.then(literal("tr").then(argument("id", IntegerArgumentType.integer(1))
				.executes(c -> ClickTranslate.click(IntegerArgumentType.getInteger(c, "id"), false))
				.then(literal("ok").executes(c -> ClickTranslate.click(IntegerArgumentType.getInteger(c, "id"), true)))));
		root.then(literal("server").then(argument("url", StringArgumentType.greedyString()).executes(c -> setServer(StringArgumentType.getString(c, "url")))));
		ToolCommands.register(root);
		d.register(root);
	}

	private static Lang lang() {
		return SkyPilot.get().lang();
	}

	/** Small helper so messages are always given in both languages. */
	static final class MccLang {
		static void info(String en, String ja) {
			McText.info(lang().t(en, ja));
		}

		static void error(String en, String ja) {
			McText.error(lang().t(en, ja));
		}
	}

	private static int help() {
		Lang l = lang();
		MccLang.info("SkyPilot - information only; it never plays, moves, clicks, buys or sells for you.", "SkyPilot は情報表示専用です。プレイ・移動・クリック・売買を代行することはありません。");
		String[][] rows = {
				{"/skypilot link", l.t("link this client to your SkyPilot workspace", "このクライアントをSkyPilotと連携")},
				{"/skypilot menu [now|roadmap|guide|ah|bazaar|alerts]", l.t("SkyPilot menu (default key: J)", "SkyPilotメニュー（初期キー: J）")},
				{"/skypilot flips | advice", l.t("menu: AH flips / advice for this area", "メニュー: AHフリップ / 現在地のアドバイス")},
				{"/skypilot alerts on | off", l.t("flip alerts (toast + chat)", "フリップ通知（トースト+チャット）")},
				{"/skypilot guide [next | prev | stop]", l.t("progression guides with waypoints", "進め方ガイド（ウェイポイント）")},
				{"/skypilot farm start <design> | next | prev | stop", l.t("farm build guide in the world", "ファーム建築ガイド（ワールド上に表示）")},
				{"/skypilot gear", l.t("recommended gear and how to make it", "推奨装備と作り方")},
				{"/skypilot panel on | off", l.t("flip panel beside the AH / Bazaar windows", "AH・バザー画面横のフリップパネル")},
				{"/skypilot goal | next | roadmap", l.t("your goal, next actions, roadmap", "目標・次の行動・ロードマップ")},
				{"/skypilot market | mining | slayer | farming", l.t("advisor panels", "アドバイザー")},
				{"/skypilot price", l.t("price of the held item", "手に持っているアイテムの価格")},
				{"/skypilot translate <text>", l.t("translate text to Japanese", "テキストを日本語に翻訳")},
				{"/skypilot translate mode <off|manual|npc_only|party|guild|auto>", l.t("chat translation mode", "チャット翻訳モード")},
				{"/skypilot translate click on | off", l.t("[→JA] button on chat lines: click to translate", "チャットに[訳]ボタンを表示（クリックで翻訳）")},
				{"/skypilot hud edit | on | off", l.t("move / toggle the HUD", "HUDの移動・表示切替")},
				{"/skypilot session [reset | upload on|off]", l.t("session statistics", "セッション統計")},
				{"/skypilot settings", l.t("settings screen", "設定画面")},
				{"/skypilot mods", l.t("manage your mods: details, settings, switch on/off, updates", "MOD管理: 詳細・設定・有効/無効・更新確認")},
				{"/skypilot perf", l.t("performance: background/menu FPS limits, hide far entities and particles, lightweight video settings",
						"軽量化: 非アクティブ時・メニュー中のFPS制限、遠くのエンティティやパーティクルの非表示、軽量ビデオ設定")},
				{"/skypilot features [recommended]", l.t("turn features on / off (or the recommended set)", "機能のオン/オフ（おすすめを一括オン）")},
				{"/skypilot feature <id> on | off", l.t("switch one feature", "機能を1つ切り替え")},
				{"/skypilot calc <expression>", l.t("calculator with k / m / b", "電卓（k / m / b 対応）")},
				{"/skypilot remind <30m> <text> | list | clear", l.t("reminders", "リマインダー")},
				{"/skypilot watch <ITEM> below|above <price> | list | remove <ITEM>", l.t("price watchlist", "価格ウォッチ")},
				{"/skypilot wp add|show|remove <name> | list | hide", l.t("your own waypoints", "自分のウェイポイント")},
				{"/skypilot en <日本語>", l.t("Japanese → English into the chat box", "日本語→英語にしてチャット欄へ")},
				{"/skypilot drops [reset]", l.t("rare drops / slayer this session", "このセッションのレアドロップ・スレイヤー")},
				{"/skypilot keyword add|remove <word> | list", l.t("chat keyword alerts", "チャットのキーワード通知")},
				{"/skypilot find <text>", l.t("search the last 500 chat lines", "直近500行のチャットを検索")},
				{"/skypilot todo add <text> | done <n> | list | clear", l.t("to-do list", "やることリスト")},
				{"/skypilot timer start|lap|stop|reset", l.t("stopwatch", "ストップウォッチ")},
				{"/skypilot lookup <name>", l.t("open an item's page", "アイテムのページを開く")},
				{"/skypilot highlight <price>", l.t("outline items worth this much", "この価格以上を縁取り")},
				{"/skypilot server <url>", l.t("SkyPilot server address", "SkyPilotサーバーのURL")},
		};
		for (String[] r : rows) {
			McText.chat(Component.literal("  " + r[0]).withStyle(ChatFormatting.AQUA).append(Component.literal(" - " + r[1]).withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private static int status() {
		SkyPilot sp = SkyPilot.get();
		Lang l = lang();
		ApiClient api = sp.api();
		McText.info(l.t("Server: ", "サーバー: ") + (api != null ? api.base() : sp.apiError()) + (api != null ? " (" + api.circuit() + ")" : ""));
		McText.info(l.t("Linked: ", "連携: ") + (sp.linked() ? l.t("yes", "はい") : l.t("no - /skypilot link", "いいえ - /skypilot link")));
		McText.info(l.t("Area: ", "エリア: ") + (sp.context().hasArea() ? sp.context().area() : "-") + " · " + l.t("Translation: ", "翻訳: ") + sp.translationMode()
				+ " · " + l.t("click to translate: ", "クリック翻訳: ") + (sp.config().translation.clickToTranslate ? l.t("on", "オン") : l.t("off", "オフ")));
		return 1;
	}

	/** Starts the device-authorization flow and prints the code in chat. */
	static int startLink() {
		SkyPilot sp = SkyPilot.get();
		DeviceFlow flow = sp.deviceFlow();
		if (flow == null) {
			MccLang.error("Invalid server URL: " + sp.apiError(), "サーバーURLが不正です: " + sp.apiError());
			return 0;
		}
		Lang l = lang();
		MccLang.info("Requesting a link code… (no Minecraft or Microsoft login is involved)", "連携コードを取得中…（Minecraft / Microsoft のログイン情報は使いません）");
		flow.start("Minecraft 26.2 (" + Minecraft.getInstance().getUser().getName() + ")", new DeviceFlow.Listener() {
			@Override
			public void onCode(String userCode, String uri, String uriComplete) {
				MutableComponent link = Component.literal(uri).setStyle(clickable(uriComplete, l.t("Open in browser", "ブラウザで開く")));
				McText.chat(McText.prefix().append(Component.literal(l.t("Open ", "")).withStyle(ChatFormatting.GRAY)).append(link)
						.append(Component.literal(l.t(" and enter code ", " を開いてコード ")).withStyle(ChatFormatting.GRAY))
						.append(Component.literal(userCode).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
						.append(Component.literal(l.t(" (valid 10 minutes).", " を入力してください（10分間有効）。")).withStyle(ChatFormatting.GRAY)));
			}

			@Override
			public void onLinked() {
				MccLang.info("Linked! Your roadmap HUD will appear in SkyBlock.", "連携しました！SkyBlock内でロードマップHUDが表示されます。");
				Minecraft.getInstance().execute(() -> sp.refreshHud(System.currentTimeMillis(), true));
			}

			@Override
			public void onFailed(String reason) {
				MccLang.error("Linking failed: " + reason, "連携に失敗しました: " + reason);
			}
		});
		return 1;
	}

	private static Style clickable(String url, String hover) {
		try {
			return Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(new URI(url)))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
		} catch (Exception e) {
			return Style.EMPTY.withColor(ChatFormatting.AQUA);
		}
	}

	private static int panel(String kind) {
		SkyPilot sp = SkyPilot.get();
		ApiClient api = sp.api();
		if (api == null) {
			McText.error(sp.apiError());
			return 0;
		}
		api.text(kind, lang().code()).whenComplete((r, err) -> {
			if (err != null) {
				McText.error(SkyPilot.shortError(err));
				return;
			}
			McText.chat(McText.prefix().append(Component.literal(r.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
			for (Dto.HudLine line : r.lines()) {
				MutableComponent c = Component.literal("  ");
				if (!line.label().isEmpty()) c.append(Component.literal(line.label() + ": ").withStyle(ChatFormatting.GRAY));
				McText.chat(c.append(Component.literal(line.value()).withStyle(ChatFormatting.WHITE)));
			}
		});
		return 1;
	}

	private static int priceHeld() {
		Minecraft mc = Minecraft.getInstance();
		SkyPilot sp = SkyPilot.get();
		PriceCheck.Item item = mc.player == null ? null : PriceCheck.item(mc.player.getMainHandItem());
		if (item == null) {
			MccLang.error("Hold a SkyBlock item first.", "SkyBlockのアイテムを手に持ってください。");
			return 0;
		}
		if (sp.api() == null || !sp.linked()) {
			MccLang.error("Not linked - /skypilot link", "未連携です - /skypilot link");
			return 0;
		}
		PriceCheck.fetch(sp.api(), item).whenComplete((r, err) -> {
			if (err != null) {
				McText.error(SkyPilot.shortError(err));
				return;
			}
			McText.chat(McText.prefix().append(Component.literal(r.name()).withStyle(ChatFormatting.WHITE)));
			for (Component c : PriceCheck.describe(r, lang())) McText.chat(Component.literal("  ").append(c));
			if (r.ageSeconds() != null) McText.info(lang().t("Updated " + r.ageSeconds() + "s ago", r.ageSeconds() + "秒前に更新"));
		});
		return 1;
	}

	private static int setHud(boolean on) {
		SkyPilot sp = SkyPilot.get();
		sp.config().hud.enabled = on;
		sp.saveConfig();
		MccLang.info(on ? "HUD enabled." : "HUD hidden.", on ? "HUDを表示します。" : "HUDを非表示にしました。");
		return 1;
	}

	private static int setUpload(boolean on) {
		SkyPilot sp = SkyPilot.get();
		sp.config().session.upload = on;
		sp.saveConfig();
		MccLang.info(on ? "Session statistics will be uploaded to your SkyPilot account when a session ends." : "Session statistics stay on this computer.",
				on ? "セッション終了時に統計をSkyPilotアカウントへ送信します。" : "セッション統計はこのPC内だけに保持します。");
		return 1;
	}

	private static int sessionStats() {
		SessionTracker.Snapshot s = SkyPilot.get().session().snapshot();
		Lang l = lang();
		McText.info(l.t("Session: ", "セッション: ") + Text.duration(s.activeMs()) + " · " + l.t("purse ", "財布 ") + Text.coins(s.purseEarned()) + " (" + Text.coins(s.coinsPerHour()) + "/h)");
		for (Map.Entry<String, Double> e : s.xpPerHour().entrySet()) {
			McText.info("  " + e.getKey() + ": " + Text.coins(s.xp().get(e.getKey())) + " XP (" + Text.coins(e.getValue()) + "/h)");
		}
		return 1;
	}

	static void showDisclosure() {
		MccLang.info("Chat translation sends the text of translated lines to your SkyPilot server, which may forward it to its translation provider. Translations are not stored. Run /skypilot translate consent to agree.",
				"チャット翻訳では、翻訳する行のテキストがSkyPilotサーバーへ送信され、サーバー設定の翻訳プロバイダに転送される場合があります。翻訳内容は保存されません。同意する場合は /skypilot translate consent を実行してください。");
	}

	private static int consent() {
		SkyPilot sp = SkyPilot.get();
		sp.config().translation.disclosureAccepted = true;
		sp.saveConfig();
		MccLang.info("Thanks. Choose a mode with /skypilot translate mode <npc_only|party|guild|auto>.", "同意しました。/skypilot translate mode <npc_only|party|guild|auto> でモードを選んでください。");
		return 1;
	}

	private static int startFarm(String id) {
		SkyPilot sp = SkyPilot.get();
		if (sp.api() == null) {
			McText.error(sp.apiError());
			return 0;
		}
		sp.api().farm(id, lang().code()).whenComplete((d, err) -> Minecraft.getInstance().execute(() -> {
			if (d == null) {
				MccLang.error("Unknown farm design: " + id, "不明なファーム設計: " + id);
			} else {
				FarmBuilder.start(d);
			}
		}));
		return 1;
	}

	private static int setAlerts(boolean on) {
		SkyPilot sp = SkyPilot.get();
		sp.config().market.alerts = on;
		sp.saveConfig();
		if (on && !sp.linked()) {
			MccLang.info("Flip alerts: on (they start once this client is linked - /skypilot link).", "フリップ通知: オン（連携後に開始します - /skypilot link）。");
		} else {
			MccLang.info(on ? "Flip alerts: on." : "Flip alerts: off.", on ? "フリップ通知: オン。" : "フリップ通知: オフ。");
		}
		return 1;
	}

	private static int setPanel(boolean on) {
		SkyPilot sp = SkyPilot.get();
		sp.config().market.containerPanel = on;
		sp.saveConfig();
		MccLang.info(on ? "AH / Bazaar panel: on." : "AH / Bazaar panel: off.", on ? "AH・バザー横パネル: オン。" : "AH・バザー横パネル: オフ。");
		return 1;
	}

	private static int setClick(boolean on) {
		SkyPilot sp = SkyPilot.get();
		sp.config().translation.clickToTranslate = on;
		sp.saveConfig();
		MccLang.info(on ? "Click to translate: on. New chat lines get a [→JA] button." : "Click to translate: off.",
				on ? "クリック翻訳: オン。新しいチャット行に[訳]ボタンが付きます。" : "クリック翻訳: オフ。");
		return 1;
	}

	private static int setMode(String raw) {
		SkyPilot sp = SkyPilot.get();
		SkyPilotConfig.TranslationModeSetting mode;
		try {
			mode = SkyPilotConfig.TranslationModeSetting.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			MccLang.error("Unknown mode: " + raw, "不明なモード: " + raw);
			return 0;
		}
		boolean automatic = mode != SkyPilotConfig.TranslationModeSetting.OFF && mode != SkyPilotConfig.TranslationModeSetting.MANUAL;
		if (automatic && !sp.config().translation.disclosureAccepted) {
			showDisclosure();
			return 0;
		}
		sp.config().translation.mode = mode;
		sp.saveConfig();
		MccLang.info("Translation mode: " + mode, "翻訳モード: " + mode);
		return 1;
	}

	private static int translate(String text) {
		SkyPilot sp = SkyPilot.get();
		if (!sp.config().translation.disclosureAccepted) {
			showDisclosure();
			return 0;
		}
		if (!sp.linked()) {
			MccLang.error("Not linked - /skypilot link", "未連携です - /skypilot link");
			return 0;
		}
		sp.translator().translateText(text, Channel.PLAYER, Sidebar.playerNames(Minecraft.getInstance())).whenComplete((r, err) -> {
			if (r == null) {
				MccLang.error("Could not translate right now (the original text is unchanged).", "現在翻訳できません（原文はそのままです）。");
			} else {
				McText.chat(McText.prefix().append(Component.literal(r).withStyle(ChatFormatting.WHITE)));
			}
		});
		return 1;
	}

	private static int setServer(String url) {
		SkyPilot sp = SkyPilot.get();
		String err = sp.setServer(url);
		if (err != null) {
			McText.error(err);
			return 0;
		}
		MccLang.info("Server set to " + sp.api().base() + ". Run /skypilot link to link this client.", "サーバーを " + sp.api().base() + " に設定しました。/skypilot link で連携してください。");
		return 1;
	}

	/** Commands run while the chat screen is closing; open our screen on the next tick. */
	private static int openLater(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(screen.get()));
		return 1;
	}
}
