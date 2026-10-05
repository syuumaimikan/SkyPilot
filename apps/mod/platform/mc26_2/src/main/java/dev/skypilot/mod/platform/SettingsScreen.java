package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * In-game settings, one page per area. Every option is display-related or opt-in data sharing.
 *
 * <p>The translation page also picks the provider the player's SkyPilot server uses for their
 * translations. An own DeepL / Anthropic key is sent only to that server, which stores it
 * encrypted and never returns it (only "saved …last 4 characters"); this screen never writes it
 * to the config file or the log.
 */
final class SettingsScreen extends Screen {
	enum Page {
		GENERAL("General", "一般"), HUD("HUD", "HUD"), TRANSLATION("Translate", "翻訳"), MARKET("Market", "マーケット"), INVENTORY("Inventory", "インベントリ"),
		SESSION("Session", "記録・ガイド");

		final String en;
		final String ja;

		Page(String en, String ja) {
			this.en = en;
			this.ja = ja;
		}
	}

	private static final int MAX_W = 320;
	private static final int ROW = 24;
	private static final int CONTENT_Y = 50;
	private static final List<String> DEFAULT_PROVIDERS = List.of("server", "deepl", "claude", "none");
	private static Page lastPage = Page.GENERAL;

	private record Label(String text, int x, int y, int color, boolean center, int wrap) {
	}

	private final Screen parent;
	/** Panel width: 320, narrower on small GUI sizes (a widget off screen would break scissoring). */
	private int pw = MAX_W;
	private final List<Label> labels = new ArrayList<>();
	private Page page;
	private boolean showDisclosure;
	/** What "I agree" turns on (a translation mode or click to translate). */
	private Runnable afterAgree = () -> {
	};

	// General page
	private String urlDraft;
	private String urlMessage;
	private boolean urlError;

	// Translation page: the server-side settings, loaded when the page opens.
	private Dto.TranslationSettings remote;
	private boolean loading;
	/** Loaded once per visit; "Retry" or a server change asks again. */
	private boolean loadTried;
	private boolean busy;
	/** Chosen here but needs a key before it can be saved. */
	private String pendingProvider;
	/** The key being typed; kept only in memory until it is sent, then cleared. */
	private String keyDraft = "";
	private String remoteMessage;
	private boolean remoteError;
	private Dto.TranslationTest test;

	SettingsScreen(Screen parent) {
		this(parent, lastPage);
	}

	SettingsScreen(Screen parent, Page page) {
		super(Component.literal("SkyPilot"));
		this.parent = parent;
		this.page = page;
	}

	/** For the client game test: show the translation page with these server settings. */
	void showRemote(Dto.TranslationSettings settings) {
		remote = settings;
		page = Page.TRANSLATION;
	}

	private static Component onOff(Lang l, String label, boolean on) {
		return Component.literal(label + ": " + (on ? l.t("ON", "オン") : l.t("OFF", "オフ")));
	}

	@Override
	protected void init() {
		SkyPilot sp = SkyPilot.get();
		SkyPilotConfig c = sp.config();
		Lang l = sp.lang();
		labels.clear();
		pw = Math.max(160, Math.min(MAX_W, width - 8));
		int x = width / 2 - pw / 2;
		if (showDisclosure) {
			addRenderableWidget(Button.builder(Component.literal(l.t("I agree", "同意する")), b -> {
				c.translation.disclosureAccepted = true;
				afterAgree.run();
				showDisclosure = false;
				sp.saveConfig();
				rebuildWidgets();
			}).bounds(x, height - 60, pw / 2 - 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal(l.t("Cancel", "キャンセル")), b -> {
				showDisclosure = false;
				rebuildWidgets();
			}).bounds(x + pw / 2 + 2, height - 60, pw / 2 - 2, 20).build());
			return;
		}
		Page[] pages = Page.values();
		int tabW = pw / pages.length;
		for (int i = 0; i < pages.length; i++) {
			Page p = pages[i];
			Button tab = Button.builder(Component.literal(l.t(p.en, p.ja)), b -> {
				page = p;
				lastPage = p;
				rebuildWidgets();
			}).bounds(x + i * tabW, 22, tabW - 2, 20).build();
			tab.active = p != page;
			addRenderableWidget(tab);
		}
		switch (page) {
			case GENERAL -> general(sp, c, l, x);
			case HUD -> hud(c, l, x);
			case TRANSLATION -> translation(sp, c, l, x);
			case MARKET -> market(c, l, x);
			case INVENTORY -> inventory(c, l, x);
			case SESSION -> session(c, l, x);
		}
		addRenderableWidget(Button.builder(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(x, height - 26, pw, 20).build());
	}

	// ---------------------------------------------------------------- pages

	private void general(SkyPilot sp, SkyPilotConfig c, Lang l, int x) {
		int colW = pw / 2 - 2;
		int right = x + colW + 4;
		int y = CONTENT_Y;
		String langName = switch (c.language) {
			case "en" -> "English";
			case "ja" -> "日本語";
			default -> l.t("Auto (game)", "自動（ゲーム設定）");
		};
		toggle(x, y, colW, Component.literal(l.t("Language: ", "言語: ") + langName), b -> c.language = switch (c.language) {
			case "auto" -> "en";
			case "en" -> "ja";
			default -> "auto";
		});
		addRenderableWidget(Button.builder(Component.literal(l.t("SkyPilot menu (J)…", "SkyPilotメニュー（J）…")), b -> minecraft.gui.setScreen(new MenuScreen(this, null)))
				.bounds(right, y, colW, 20).build());
		y += ROW + 10;
		labels.add(new Label(l.t("SkyPilot server", "SkyPilotサーバー"), x, y - 10, 0xFFAAAAAA, false, 0));
		if (urlDraft == null) urlDraft = c.apiBaseUrl;
		EditBox url = new EditBox(font, x, y, pw - 64, 20, Component.literal(l.t("Server URL", "サーバーURL")));
		url.setMaxLength(200);
		url.setValue(urlDraft);
		url.setResponder(v -> urlDraft = v);
		addRenderableWidget(url);
		addRenderableWidget(Button.builder(Component.literal(l.t("Apply", "適用")), b -> {
			String err = sp.setServer(urlDraft);
			urlError = err != null;
			urlMessage = err != null ? err
					: l.t("Server set to " + sp.api().base() + ". Link again to use your account.", "サーバーを " + sp.api().base() + " に設定しました。アカウントを使うには再度連携してください。");
			urlDraft = c.apiBaseUrl;
			remote = null;
			loadTried = false;
			rebuildWidgets();
		}).bounds(x + pw - 60, y, 60, 20).build());
		y += ROW;
		if (sp.linked()) {
			addRenderableWidget(Button.builder(Component.literal(l.t("Unlink", "連携を解除")), b -> {
				sp.unlink();
				remote = null;
				loadTried = false;
				rebuildWidgets();
			}).bounds(x, y, colW, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal(l.t("Link with SkyPilot…", "SkyPilotと連携…")), b -> {
				onClose();
				minecraft.gui.setScreen(null);
				Commands.startLink();
			}).bounds(x, y, colW, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal(l.t("Refresh data", "データを再取得")), b -> {
			if (sp.api() != null) sp.api().clearCache();
			sp.translator().clearCache();
			urlError = false;
			urlMessage = l.t("Cached data cleared; it is fetched again when needed.", "キャッシュを消去しました。必要に応じて再取得します。");
			rebuildWidgets();
		}).bounds(right, y, colW, 20).build());
		y += ROW + 4;
		String status = (sp.linked() ? l.t("Linked", "連携済み") : l.t("Not linked", "未連携")) + " · " + c.apiBaseUrl;
		labels.add(new Label(status, x, y, sp.linked() ? 0xFF55FF55 : 0xFFAAAAAA, false, 0));
		y += 12;
		if (urlMessage != null) {
			labels.add(new Label(urlMessage, x, y, urlError ? 0xFFFF5555 : 0xFF55FF55, false, pw));
			y += 22;
		}
		labels.add(new Label(l.t("https only (http is allowed for localhost). Changing the server deletes this client's link so the token is never sent elsewhere.",
				"https のみ（localhost は http 可）。サーバーを変更すると、トークンが他のサーバーへ送られないよう連携が解除されます。"), x, y, 0xFF888888, false, pw));
	}

	private void hud(SkyPilotConfig c, Lang l, int x) {
		int colW = pw / 2 - 2;
		int right = x + colW + 4;
		int y = CONTENT_Y;
		toggle(x, y, colW, onOff(l, l.t("HUD", "HUD"), c.hud.enabled), b -> c.hud.enabled = !c.hud.enabled);
		toggle(right, y, colW, onOff(l, l.t("Context", "状況表示"), c.hud.showContext), b -> c.hud.showContext = !c.hud.showContext);
		y += ROW;
		toggle(x, y, colW, onOff(l, l.t("Session on HUD", "セッション表示"), c.hud.showSession), b -> c.hud.showSession = !c.hud.showSession);
		addRenderableWidget(Button.builder(Component.literal(l.t("Move / resize HUD…", "HUDの位置・サイズ…")), b -> minecraft.gui.setScreen(new HudEditorScreen(this)))
				.bounds(right, y, colW, 20).build());
		y += ROW;
		stepper(x, y, colW, l.t("Scale ", "サイズ ") + String.format(Locale.ROOT, "%.1f×", c.hud.scale), d -> c.hud.scale = Math.round((c.hud.scale + d * 0.1) * 10) / 10.0);
		stepper(right, y, colW, l.t("Background ", "背景 ") + Math.round(c.hud.opacity * 100) + "%", d -> c.hud.opacity = Math.round((c.hud.opacity + d * 0.1) * 10) / 10.0);
		y += ROW + 4;
		labels.add(new Label(l.t("The HUD shows your current goal step, the next action and (on SkyBlock) where you are. Drag it in the editor to move it.",
				"HUDには現在の目標ステップ、次にやること、（SkyBlock内では）現在地の情報が表示されます。位置はエディタでドラッグして変更できます。"), x, y, 0xFF888888, false, pw));
	}

	private void translation(SkyPilot sp, SkyPilotConfig c, Lang l, int x) {
		int colW = pw / 2 - 2;
		int right = x + colW + 4;
		int y = CONTENT_Y;
		toggle(x, y, colW, Component.literal(l.t("Translate: ", "翻訳: ") + modeName(l, c.translation.mode)), b -> cycleTranslation(c));
		toggle(right, y, colW, onOff(l, l.t("Click to translate", "クリック翻訳"), c.translation.clickToTranslate), b -> {
			if (!c.translation.clickToTranslate && !c.translation.disclosureAccepted) {
				afterAgree = () -> c.translation.clickToTranslate = true;
				showDisclosure = true;
				return;
			}
			c.translation.clickToTranslate = !c.translation.clickToTranslate;
		});
		y += ROW;
		if (remote == null) {
			String msg = loading || (!loadTried && sp.linked()) ? l.t("Loading your translation settings…", "翻訳設定を読み込み中…")
					: !sp.linked() ? l.t("Link this client (General page) to choose your translation provider or use your own API key. Until then the server's provider is used.",
							"翻訳プロバイダの選択や自分のAPIキーの利用には、連携が必要です（「一般」ページ）。連携するまではサーバーのプロバイダが使われます。")
					: remoteMessage != null ? remoteMessage : l.t("Translation settings are not available.", "翻訳設定を取得できません。");
			labels.add(new Label(msg, x, y + 4, remoteError ? 0xFFFF5555 : 0xFFAAAAAA, false, pw));
			if (!loading && sp.linked()) {
				addRenderableWidget(Button.builder(Component.literal(l.t("Retry", "再試行")), b -> {
					load(sp);
					rebuildWidgets();
				}).bounds(x, y + 30, colW, 20).build());
			}
			return;
		}
		String provider = shownProvider();
		Button cycle = Button.builder(Component.literal(l.t("Provider: ", "プロバイダ: ") + providerName(l, provider) + (pendingProvider != null ? l.t(" (needs a key)", "（キー未設定）") : "")),
				b -> {
					cycleProvider(sp, l);
					rebuildWidgets();
				}).bounds(x, y, colW, 20).build();
		cycle.active = !busy;
		addRenderableWidget(cycle);
		Button testBtn = Button.builder(Component.literal(busy ? l.t("Working…", "処理中…") : l.t("Test translation", "接続テスト")), b -> {
			test = null;
			remoteMessage = null;
			call(sp.api() == null ? null : sp.api().testTranslation(), t -> test = t);
			rebuildWidgets();
		}).bounds(right, y, colW, 20).build();
		testBtn.active = !busy && pendingProvider == null;
		addRenderableWidget(testBtn);
		y += ROW;
		if (needsKey(provider)) {
			Dto.KeyInfo info = keyInfo(provider);
			EditBox key = new EditBox(font, x, y, pw - 128, 20, Component.literal(l.t("API key", "APIキー")));
			key.setMaxLength(200);
			key.setValue(keyDraft);
			key.setResponder(v -> keyDraft = v);
			// Masked: the key is never drawn on screen (screenshots, streams).
			key.addFormatter((s, start) -> FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY));
			key.setHint(Component.literal(info.configured()
					? l.t("saved " + info.hint() + " — paste to replace", "保存済み " + info.hint() + " ・貼り付けで置換")
					: l.t("paste your " + providerName(l, provider) + " API key (Ctrl+V)", providerName(l, provider) + " のAPIキーを貼り付け（Ctrl+V）")));
			addRenderableWidget(key);
			if (pendingProvider != null) setInitialFocus(key);
			Button save = Button.builder(Component.literal(l.t("Save", "保存")), b -> {
				saveKey(sp, l, provider);
				rebuildWidgets();
			}).bounds(x + pw - 124, y, 60, 20).build();
			save.active = !busy;
			addRenderableWidget(save);
			Button remove = Button.builder(Component.literal(l.t("Remove", "削除")), b -> {
				// Removing the key of the provider in use falls back to the server's provider.
				String next = provider.equals(remote.provider()) ? "server" : remote.provider();
				put(sp, l, next, provider.equals("deepl") ? "" : null, provider.equals("claude") ? "" : null, l.t("Key removed.", "キーを削除しました。"));
				rebuildWidgets();
			}).bounds(x + pw - 60, y, 60, 20).build();
			remove.active = !busy && info.configured();
			addRenderableWidget(remove);
			y += ROW;
		}
		y += 2;
		String server = remote.serverProvider() == null ? l.t("none (dictionary only)", "なし（辞書のみ）") : remote.serverProvider();
		labels.add(new Label(l.t("Server's provider: ", "サーバーのプロバイダ: ") + server + "   DeepL: " + keyLabel(l, remote.deepl()) + "   Claude: " + keyLabel(l, remote.anthropic()),
				x, y, 0xFFAAAAAA, false, 0));
		y += 12;
		String notice = switch (provider) {
			case "deepl", "claude" -> l.t("Lines you translate go from your SkyPilot server to " + providerName(l, provider)
					+ " with your own key (your account and bill). The key is stored encrypted on the server and never shown again. Translations are not stored.",
					"翻訳する行は、SkyPilotサーバーからあなたのキーで " + providerName(l, provider) + " へ送られます（あなたのアカウント・料金）。キーはサーバーに暗号化して保存され、再表示されません。翻訳内容は保存されません。");
			case "none" -> l.t("Only the SkyPilot dictionary is used; nothing is sent to an external translator. Sentences outside the dictionary stay in English.",
					"SkyPilot辞書のみを使い、外部の翻訳サービスには送信しません。辞書にない文は英語のままです。");
			default -> l.t("Lines you translate are sent to your SkyPilot server, which uses its own provider (" + server + "). Translations are not stored.",
					"翻訳する行はSkyPilotサーバーへ送られ、サーバーのプロバイダ（" + server + "）で翻訳されます。翻訳内容は保存されません。");
		};
		labels.add(new Label(notice, x, y, 0xFF888888, false, pw));
		y += 32;
		if (test != null) {
			String t = test.ok() ? l.t("Test OK (", "テスト成功（") + test.provider() + "): " + test.translated()
					: l.t("Test failed: ", "テスト失敗: ") + test.message();
			labels.add(new Label(t, x, y, test.ok() ? 0xFF55FF55 : 0xFFFF5555, false, pw));
		} else if (remoteMessage != null) {
			labels.add(new Label(remoteMessage, x, y, remoteError ? 0xFFFF5555 : 0xFF55FF55, false, pw));
		}
	}

	private void market(SkyPilotConfig c, Lang l, int x) {
		int colW = pw / 2 - 2;
		int right = x + colW + 4;
		int y = CONTENT_Y;
		toggle(x, y, colW, onOff(l, l.t("Price tooltips", "価格表示"), c.priceTooltip), b -> c.priceTooltip = !c.priceTooltip);
		toggle(right, y, colW, onOff(l, l.t("Flip alerts", "フリップ通知"), c.market.alerts), b -> c.market.alerts = !c.market.alerts);
		y += ROW;
		toggle(x, y, colW, onOff(l, l.t("Alert sound", "通知音"), c.market.alertSound), b -> c.market.alertSound = !c.market.alertSound);
		toggle(right, y, colW, onOff(l, l.t("AH/BZ panel", "AH・バザー横パネル"), c.market.containerPanel), b -> c.market.containerPanel = !c.market.containerPanel);
		y += ROW;
		addRenderableWidget(Button.builder(Component.literal(l.t("AH flips…", "AHフリップ…")), b -> minecraft.gui.setScreen(new MenuScreen(this, MenuScreen.Tab.AH)))
				.bounds(x, y, colW, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Bazaar flips…", "バザーフリップ…")), b -> minecraft.gui.setScreen(new MenuScreen(this, MenuScreen.Tab.BAZAAR)))
				.bounds(right, y, colW, 20).build());
		y += ROW;
		addRenderableWidget(Button.builder(Component.literal(l.t("Recent alerts…", "最近の通知…")), b -> minecraft.gui.setScreen(new MenuScreen(this, MenuScreen.Tab.ALERTS)))
				.bounds(x, y, colW, 20).build());
		y += ROW + 4;
		labels.add(new Label(l.t("Display only: SkyPilot never buys, sells or clicks for you. Alert rules are edited on the web; commands are only put into the chat box.",
				"表示のみ: SkyPilotが代わりに売買やクリックをすることはありません。通知ルールはWebで編集します。コマンドはチャット欄に入力されるだけです。"), x, y, 0xFF888888, false, pw));
	}

	private void inventory(SkyPilotConfig c, Lang l, int x) {
		int colW = pw / 2 - 2;
		int right = x + colW + 4;
		int y = CONTENT_Y;
		toggle(x, y, colW, onOff(l, l.t("Item browser", "アイテム一覧"), c.inventory.itemBrowser), b -> c.inventory.itemBrowser = !c.inventory.itemBrowser);
		toggle(right, y, colW, onOff(l, l.t("Chest value", "チェストの価値"), c.inventory.chestValue), b -> c.inventory.chestValue = !c.inventory.chestValue);
		y += ROW + 4;
		labels.add(new Label(l.t("Item browser: every SkyBlock item to the right of inventory windows. Search, then click an item for its value, recipes, drop sources and uses.",
				"アイテム一覧: インベントリ画面の右側に全SkyBlockアイテムを表示。検索して、クリックすると価値・レシピ・ドロップ元・用途を表示します。"), x, y, 0xFF888888, false, pw));
		y += 32;
		labels.add(new Label(l.t("Chest value: total value of chests, backpacks and the ender chest (left side), highest first. Click a name to outline its slot.",
				"チェストの価値: チェスト・バックパック・エンダーチェストの合計価値を左側に高い順で表示。名前をクリックするとそのスロットが縁取られます。"), x, y, 0xFF888888, false, pw));
	}

	private void session(SkyPilotConfig c, Lang l, int x) {
		int colW = pw / 2 - 2;
		int right = x + colW + 4;
		int y = CONTENT_Y;
		toggle(x, y, colW, onOff(l, l.t("Session tracking", "セッション記録"), c.session.enabled), b -> c.session.enabled = !c.session.enabled);
		toggle(right, y, colW, onOff(l, l.t("Upload sessions", "セッション送信"), c.session.upload), b -> c.session.upload = !c.session.upload);
		y += ROW;
		Button stopGuide = Button.builder(Component.literal(l.t("Stop guide", "ガイドを終了")), b -> {
			Waypoints.clear();
			rebuildWidgets();
		}).bounds(x, y, colW, 20).build();
		stopGuide.active = Waypoints.guide() != null || Waypoints.target() != null;
		addRenderableWidget(stopGuide);
		Button stopFarm = Button.builder(Component.literal(l.t("Stop farm guide", "農場ガイドを終了")), b -> {
			FarmBuilder.stop();
			rebuildWidgets();
		}).bounds(right, y, colW, 20).build();
		stopFarm.active = FarmBuilder.active();
		addRenderableWidget(stopFarm);
		y += ROW;
		addRenderableWidget(Button.builder(Component.literal(l.t("Guides…", "進め方ガイド…")), b -> minecraft.gui.setScreen(new MenuScreen(this, MenuScreen.Tab.PROGRESS)))
				.bounds(x, y, colW, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Recommended gear…", "推奨装備…")), b -> minecraft.gui.setScreen(new MenuScreen(this, MenuScreen.Tab.GEAR)))
				.bounds(right, y, colW, 20).build());
		y += ROW + 4;
		Dto.Guide g = Waypoints.guide();
		String guide = g != null ? l.t("Following: ", "表示中のガイド: ") + g.title() + " (" + (Waypoints.stepIndex() + 1) + "/" + g.steps().size() + ")"
				: Waypoints.target() != null ? l.t("Waypoint: ", "ウェイポイント: ") + Waypoints.target().name() : l.t("No guide running.", "実行中のガイドはありません。");
		labels.add(new Label(guide, x, y, 0xFFAAAAAA, false, 0));
		y += 12;
		if (FarmBuilder.active()) {
			labels.add(new Label(l.t("Farm guide: ", "農場ガイド: ") + FarmBuilder.design().summary().name() + l.t(" — blocks left on this layer: ", " ・この層の残りブロック: ")
					+ FarmBuilder.remaining(), x, y, 0xFFAAAAAA, false, 0));
			y += 12;
		}
		labels.add(new Label(l.t("Session statistics stay on this computer unless uploading is on. Guides only point at public places; you walk there yourself.",
				"セッション統計は送信をオンにしない限りこのPCにだけ保存されます。ガイドは公開された場所を指すだけで、移動は自分で行います。"), x, y + 4, 0xFF888888, false, pw));
	}

	// ---------------------------------------------------------------- widgets

	private void toggle(int x, int y, int w, Component label, Consumer<Button> action) {
		addRenderableWidget(Button.builder(label, b -> {
			action.accept(b);
			SkyPilot.get().saveConfig();
			rebuildWidgets();
		}).bounds(x, y, w, 20).build());
	}

	/** "−  label  +": the label is drawn between the two buttons. */
	private void stepper(int x, int y, int w, String label, java.util.function.IntConsumer change) {
		toggle(x, y, 20, Component.literal("−"), b -> change.accept(-1));
		toggle(x + w - 20, y, 20, Component.literal("+"), b -> change.accept(1));
		labels.add(new Label(label, x + w / 2, y + 6, 0xFFFFFFFF, true, 0));
	}

	private void cycleTranslation(SkyPilotConfig c) {
		SkyPilotConfig.TranslationModeSetting[] all = SkyPilotConfig.TranslationModeSetting.values();
		SkyPilotConfig.TranslationModeSetting next = all[(c.translation.mode.ordinal() + 1) % all.length];
		boolean automatic = next != SkyPilotConfig.TranslationModeSetting.OFF && next != SkyPilotConfig.TranslationModeSetting.MANUAL;
		if (automatic && !c.translation.disclosureAccepted) {
			afterAgree = () -> c.translation.mode = next;
			showDisclosure = true;
			return;
		}
		c.translation.mode = next;
	}

	private static String modeName(Lang l, SkyPilotConfig.TranslationModeSetting m) {
		return switch (m) {
			case OFF -> l.t("Off", "オフ");
			case MANUAL -> l.t("Manual (/sp tr)", "手動（/sp tr）");
			case NPC_ONLY -> l.t("NPCs only", "NPCのみ");
			case PARTY -> l.t("Party", "パーティー");
			case GUILD -> l.t("Guild", "ギルド");
			case AUTO -> l.t("All chat", "すべて");
		};
	}

	// ---------------------------------------------------------------- translation provider (server side)

	static String providerName(Lang l, String provider) {
		return switch (provider) {
			case "deepl" -> "DeepL";
			case "claude" -> "Claude (Anthropic)";
			case "none" -> l.t("Dictionary only", "辞書のみ");
			default -> l.t("SkyPilot server", "SkyPilotサーバー");
		};
	}

	private static boolean needsKey(String provider) {
		return provider.equals("deepl") || provider.equals("claude");
	}

	/** Same shape the server accepts; checked here only to answer typos immediately. */
	static boolean plausibleKey(String key) {
		if (key.length() < 16 || key.length() > 200) return false;
		for (char ch : key.toCharArray()) {
			boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == ':' || ch == '.';
			if (!ok) return false;
		}
		return true;
	}

	private String shownProvider() {
		return pendingProvider != null ? pendingProvider : remote.provider();
	}

	private Dto.KeyInfo keyInfo(String provider) {
		Dto.KeyInfo k = provider.equals("deepl") ? remote.deepl() : remote.anthropic();
		return k == null ? new Dto.KeyInfo(false, null) : k;
	}

	private static String keyLabel(Lang l, Dto.KeyInfo k) {
		return k != null && k.configured() ? l.t("saved ", "保存済み ") + k.hint() : l.t("no key", "未設定");
	}

	private void load(SkyPilot sp) {
		loadTried = true;
		loading = true;
		remoteMessage = null;
		remoteError = false;
		call(sp.api() == null ? null : sp.api().userSettings(), s -> remote = s.translation());
	}

	@Override
	public void tick() {
		super.tick();
		// Loaded from the tick, not init(): a result that arrives at once rebuilds the page safely.
		SkyPilot sp = SkyPilot.get();
		if (page == Page.TRANSLATION && !showDisclosure && remote == null && !loadTried && sp.linked()) {
			load(sp);
			rebuildWidgets();
		}
	}

	/** Providers that need a key wait for one; the others (or a provider whose key is saved) apply at once. */
	private void cycleProvider(SkyPilot sp, Lang l) {
		List<String> all = remote.providers() == null || remote.providers().isEmpty() ? DEFAULT_PROVIDERS : remote.providers();
		String next = all.get((all.indexOf(shownProvider()) + 1) % all.size());
		test = null;
		remoteMessage = null;
		keyDraft = "";
		if (needsKey(next) && !keyInfo(next).configured()) {
			pendingProvider = next.equals(remote.provider()) ? null : next;
			if (pendingProvider == null) put(sp, l, next, null, null, l.t("Saved.", "保存しました。"));
			return;
		}
		pendingProvider = null;
		put(sp, l, next, null, null, l.t("Saved: ", "保存しました: ") + providerName(l, next));
	}

	private void saveKey(SkyPilot sp, Lang l, String provider) {
		String key = keyDraft.strip();
		keyDraft = "";
		test = null;
		if (!plausibleKey(key)) {
			remoteError = true;
			remoteMessage = key.isEmpty() ? l.t("Paste a key first.", "先にキーを貼り付けてください。")
					: l.t("That does not look like an API key (16–200 letters, digits, - _ : .).", "APIキーの形式ではありません（16〜200文字の英数字と - _ : .）。");
			return;
		}
		put(sp, l, provider, provider.equals("deepl") ? key : null, provider.equals("claude") ? key : null,
				l.t("Key saved. Try “Test translation”.", "キーを保存しました。「接続テスト」で確認できます。"));
	}

	private void put(SkyPilot sp, Lang l, String provider, String deeplKey, String anthropicKey, String done) {
		ApiClient api = sp.api();
		call(api == null ? null : api.saveTranslationSettings(provider, deeplKey, anthropicKey), s -> {
			remote = s.translation();
			pendingProvider = null;
			remoteMessage = done;
			// Lines translated by the previous provider should not be reused.
			sp.translator().clearCache();
		});
	}

	/**
	 * Runs a server call; the result is applied on the game thread and the page is rebuilt then.
	 * Callers rebuild right away themselves (to show "Working…"); this never runs inside init().
	 */
	private <T> void call(CompletableFuture<T> future, Consumer<T> ok) {
		Lang l = SkyPilot.get().lang();
		if (future == null) {
			remoteError = true;
			remoteMessage = l.t("No valid SkyPilot server is set.", "有効なSkyPilotサーバーが設定されていません。");
			loading = false;
			return;
		}
		busy = true;
		remoteError = false;
		future.whenComplete((r, err) -> minecraft.execute(() -> {
			busy = false;
			loading = false;
			if (err != null) {
				remoteError = true;
				remoteMessage = l.t("Server error: ", "サーバーエラー: ") + SkyPilot.shortError(err);
			} else {
				remoteError = false;
				ok.accept(r);
			}
			if (minecraft.gui.screen() == this) rebuildWidgets();
		}));
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		g.centeredText(font, Component.literal(l.t("SkyPilot settings", "SkyPilot 設定")), width / 2, 8, 0xFFFFFFFF);
		if (showDisclosure) {
			Component text = Component.literal(l.t(
					"Chat translation sends the text of the lines it translates to your SkyPilot server (" + sp.config().apiBaseUrl
							+ "), which may forward it to its configured translation provider (or the one you chose on the Translation page). Translations are not stored. The original message always stays visible.",
					"チャット翻訳では、翻訳する行のテキストがSkyPilotサーバー（" + sp.config().apiBaseUrl
							+ "）へ送信され、サーバー設定の翻訳プロバイダ（または「翻訳」ページで選んだプロバイダ）へ転送される場合があります。翻訳内容は保存されません。元のメッセージは常に表示されたままです。"));
			g.textWithWordWrap(font, text, width / 2 - pw / 2, 50, pw, 0xFFDDDDDD);
			return;
		}
		for (Label lb : labels) {
			if (lb.center()) g.centeredText(font, lb.text(), lb.x(), lb.y(), lb.color());
			else if (lb.wrap() > 0) g.textWithWordWrap(font, Component.literal(lb.text()), lb.x(), lb.y(), lb.wrap(), lb.color());
			else g.text(font, lb.text(), lb.x(), lb.y(), lb.color());
		}
	}

	@Override
	public void onClose() {
		keyDraft = "";
		SkyPilot.get().saveConfig();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
