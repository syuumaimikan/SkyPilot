package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.hud.HudLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Drag the HUD to move it, scroll to resize; buttons adjust opacity. */
final class HudEditorScreen extends Screen {
	private final Screen parent;
	private HudLayout.Box box = new HudLayout.Box(0, 0, 0, 0);
	private boolean dragging;
	private double offX;
	private double offY;

	HudEditorScreen(Screen parent) {
		super(Component.literal("SkyPilot HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Lang l = SkyPilot.get().lang();
		SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
		int y = height - 28;
		int x = width / 2 - 160;
		addRenderableWidget(Button.builder(Component.literal(l.t("Opacity -", "不透明度 -")), b -> h.opacity = Math.max(0, h.opacity - 0.1)).bounds(x, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Opacity +", "不透明度 +")), b -> h.opacity = Math.min(1, h.opacity + 0.1)).bounds(x + 80, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Reset", "リセット")), b -> {
			h.x = 4;
			h.y = 4;
			h.scale = 1.0;
			h.opacity = 0.6;
		}).bounds(x + 160, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(x + 240, y, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		SkyPilot sp = SkyPilot.get();
		box = HudRenderer.draw(g, font, HudRenderer.rows(sp), sp.config().hud, width, height, true);
		Lang l = sp.lang();
		SkyPilotConfig.Hud h = sp.config().hud;
		g.centeredText(font, l.t("Drag to move · scroll to resize", "ドラッグで移動 · スクロールでサイズ変更"), width / 2, height - 44, 0xFFFFFFFF);
		g.centeredText(font, String.format(Locale.ROOT, "x %d  y %d  ×%.1f  %.0f%%", h.x, h.y, h.scale, h.opacity * 100), width / 2, height - 56, 0xFFAAAAAA);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && box.contains(event.x(), event.y())) {
			dragging = true;
			offX = event.x() - box.x();
			offY = event.y() - box.y();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
			h.x = (int) Math.max(0, Math.round(event.x() - offX));
			h.y = (int) Math.max(0, Math.round(event.y() - offY));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
		h.scale = Math.max(0.5, Math.min(2.5, Math.round((h.scale + scrollY * 0.1) * 10) / 10.0));
		return true;
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
