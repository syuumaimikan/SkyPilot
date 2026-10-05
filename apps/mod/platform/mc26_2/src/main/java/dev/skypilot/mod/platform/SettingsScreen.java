package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** In-game settings. Every option is display-related or opt-in data sharing. */
final class SettingsScreen extends Screen {
	private static final int W = 300;
	private final Screen parent;
	private boolean showDisclosure;

	SettingsScreen(Screen parent) {
		super(Component.literal("SkyPilot"));
		this.parent = parent;
	}

	private static Component onOff(Lang l, String label, boolean on) {
		return Component.literal(label + ": " + (on ? l.t("ON", "オン") : l.t("OFF", "オフ")));
	}

	@Override
	protected void init() {
		SkyPilot sp = SkyPilot.get();
		SkyPilotConfig c = sp.config();
		Lang l = sp.lang();
		int x = width / 2 - W / 2;
		int y = 38;
		if (showDisclosure) {
			addRenderableWidget(Button.builder(Component.literal(l.t("I agree", "同意する")), b -> {
				c.translation.disclosureAccepted = true;
				c.translation.mode = SkyPilotConfig.TranslationModeSetting.PARTY;
				showDisclosure = false;
				sp.saveConfig();
				rebuildWidgets();
			}).bounds(x, height - 60, W / 2 - 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal(l.t("Cancel", "キャンセル")), b -> {
				showDisclosure = false;
				rebuildWidgets();
			}).bounds(x + W / 2 + 2, height - 60, W / 2 - 2, 20).build());
			return;
		}
		// Two columns so everything fits even at the smallest GUI size.
		int colW = W / 2 - 2;
		int left = x;
		int right = x + colW + 4;
		int row = y;
		toggle(left, row, colW, onOff(l, l.t("HUD", "HUD"), c.hud.enabled), b -> c.hud.enabled = !c.hud.enabled);
		toggle(right, row, colW, onOff(l, l.t("Context", "状況表示"), c.hud.showContext), b -> c.hud.showContext = !c.hud.showContext);
		row += 24;
		toggle(left, row, colW, onOff(l, l.t("Session on HUD", "セッション表示"), c.hud.showSession), b -> c.hud.showSession = !c.hud.showSession);
		toggle(right, row, colW, onOff(l, l.t("Price tooltips", "価格表示"), c.priceTooltip), b -> c.priceTooltip = !c.priceTooltip);
		row += 24;
		toggle(left, row, colW, onOff(l, l.t("Session tracking", "セッション記録"), c.session.enabled), b -> c.session.enabled = !c.session.enabled);
		toggle(right, row, colW, onOff(l, l.t("Upload sessions", "セッション送信"), c.session.upload), b -> c.session.upload = !c.session.upload);
		row += 24;
		toggle(left, row, W, Component.literal(l.t("Chat translation: ", "チャット翻訳: ") + c.translation.mode), b -> cycleTranslation(c));
		row += 24;
		addRenderableWidget(Button.builder(Component.literal(l.t("Move / resize HUD…", "HUDの位置・サイズ…")), b -> minecraft.gui.setScreen(new HudEditorScreen(this)))
				.bounds(left, row, colW, 20).build());
		if (sp.linked()) {
			addRenderableWidget(Button.builder(Component.literal(l.t("Unlink", "連携を解除")), b -> {
				sp.unlink();
				rebuildWidgets();
			}).bounds(right, row, colW, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal(l.t("Link with SkyPilot…", "SkyPilotと連携…")), b -> {
				onClose();
				minecraft.gui.setScreen(null);
				Commands.startLink();
			}).bounds(right, row, colW, 20).build());
		}
		row += 24;
		addRenderableWidget(Button.builder(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(x, Math.min(row + 6, height - 24), W, 20).build());
	}

	private void toggle(int x, int y, int w, Component label, Consumer<Button> action) {
		addRenderableWidget(Button.builder(label, b -> {
			action.accept(b);
			SkyPilot.get().saveConfig();
			rebuildWidgets();
		}).bounds(x, y, w, 20).build());
	}

	private void cycleTranslation(SkyPilotConfig c) {
		SkyPilotConfig.TranslationModeSetting[] all = SkyPilotConfig.TranslationModeSetting.values();
		SkyPilotConfig.TranslationModeSetting next = all[(c.translation.mode.ordinal() + 1) % all.length];
		boolean automatic = next != SkyPilotConfig.TranslationModeSetting.OFF && next != SkyPilotConfig.TranslationModeSetting.MANUAL;
		if (automatic && !c.translation.disclosureAccepted) {
			showDisclosure = true;
			return;
		}
		c.translation.mode = next;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		g.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
		if (showDisclosure) {
			Component text = Component.literal(l.t(
					"Chat translation sends the text of the lines it translates to your SkyPilot server (" + sp.config().apiBaseUrl
							+ "), which may forward it to its configured translation provider. Translations are not stored. The original message always stays visible.",
					"チャット翻訳では、翻訳する行のテキストがSkyPilotサーバー（" + sp.config().apiBaseUrl
							+ "）へ送信され、サーバー設定の翻訳プロバイダへ転送される場合があります。翻訳内容は保存されません。元のメッセージは常に表示されたままです。"));
			g.textWithWordWrap(font, text, width / 2 - 150, 50, 300, 0xFFDDDDDD);
			return;
		}
		String status = (sp.linked() ? l.t("Linked", "連携済み") : l.t("Not linked", "未連携")) + " · " + sp.config().apiBaseUrl;
		g.centeredText(font, status, width / 2, 28, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		SkyPilot.get().saveConfig();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
