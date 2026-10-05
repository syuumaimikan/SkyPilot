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
				{"/skypilot goal | next | roadmap", l.t("your goal, next actions, roadmap", "目標・次の行動・ロードマップ")},
				{"/skypilot market | mining | slayer | farming", l.t("advisor panels", "アドバイザー")},
				{"/skypilot price", l.t("price of the held item", "手に持っているアイテムの価格")},
				{"/skypilot translate <text>", l.t("translate text to Japanese", "テキストを日本語に翻訳")},
				{"/skypilot translate mode <off|manual|npc_only|party|guild|auto>", l.t("chat translation mode", "チャット翻訳モード")},
				{"/skypilot translate click on | off", l.t("[→JA] button on chat lines: click to translate", "チャットに[訳]ボタンを表示（クリックで翻訳）")},
				{"/skypilot hud edit | on | off", l.t("move / toggle the HUD", "HUDの移動・表示切替")},
				{"/skypilot session [reset | upload on|off]", l.t("session statistics", "セッション統計")},
				{"/skypilot settings", l.t("settings screen", "設定画面")},
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
		String old = sp.config().apiBaseUrl;
		sp.config().apiBaseUrl = url;
		sp.config().sanitize();
		sp.rebuildApi();
		if (sp.api() == null) {
			String err = sp.apiError();
			sp.config().apiBaseUrl = old;
			sp.rebuildApi();
			McText.error(err);
			return 0;
		}
		if (!old.equals(sp.config().apiBaseUrl)) sp.unlink();
		sp.saveConfig();
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
