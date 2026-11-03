package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.hud.HudLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Universal HUD & GUI editor: allows moving any element by drag and drop,
 * scrolling to resize, toggling visibility, and managing GUI elements from
 * Vanilla, SkyPilot, Skyblocker, and other installed mods.
 */
final class HudEditorScreen extends Screen {
	private final Screen parent;
	private HudLayout.Box box = new HudLayout.Box(0, 0, 0, 0);
	private HudLayout.Box infoBox = new HudLayout.Box(0, 0, 0, 0);
	private String draggingElement;
	private String selectedElement = "scoreboard";
	private double offX;
	private double offY;

	HudEditorScreen(Screen parent) {
		super(Component.literal("SkyPilot HUD & GUI Editor"));
		this.parent = parent;
	}

	private HudLayout.Box elementBox(String id) {
		if ("skypilot_hud".equals(id)) return box;
		if ("info_hud".equals(id)) return infoBox;
		return UiTransforms.box(id, width, height);
	}

	@Override
	protected void init() {
		Lang l = SkyPilot.get().lang();
		SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
		int y = height - 26;
		int x = width / 2 - 190;
		int bw = 380;

		List<String> all = UiTransforms.allElements();
		boolean isHidden = UiTransforms.position(selectedElement).hidden;

		// Row 1: Element selection, visibility toggle, scale buttons
		addRenderableWidget(Button.builder(Component.literal("選択: " + UiTransforms.name(selectedElement) + " [" + UiTransforms.modName(selectedElement) + "]"), b -> {
			int n = all.indexOf(selectedElement);
			selectedElement = all.get((n + 1) % all.size());
			rebuildWidgets();
		}).bounds(x, height - 122, 230, 18).build());

		addRenderableWidget(Button.builder(Component.literal(isHidden ? "表示: オフ" : "表示: オン"), b -> {
			var p = UiTransforms.position(selectedElement);
			p.hidden = !p.hidden;
			rebuildWidgets();
		}).bounds(x + 234, height - 122, 70, 18).build());

		addRenderableWidget(Button.builder(Component.literal("拡大+"), b -> {
			var p = UiTransforms.position(selectedElement);
			p.scale = Math.clamp(Math.round((p.scale + 0.1) * 10) / 10.0, 0.4, 2.5);
			rebuildWidgets();
		}).bounds(x + 308, height - 122, 34, 18).build());

		addRenderableWidget(Button.builder(Component.literal("縮小-"), b -> {
			var p = UiTransforms.position(selectedElement);
			p.scale = Math.clamp(Math.round((p.scale - 0.1) * 10) / 10.0, 0.4, 2.5);
			rebuildWidgets();
		}).bounds(x + 346, height - 122, 34, 18).build());

		// Row 2: SkyPilot specific toggles
		addRenderableWidget(Button.builder(Component.literal("情報HUD: " + (SkyPilot.get().config().compactInfo ? "コンパクト" : "全表示")), b -> {
			SkyPilot.get().config().compactInfo = !SkyPilot.get().config().compactInfo;
			rebuildWidgets();
		}).bounds(x, height - 100, 150, 18).build());

		addRenderableWidget(Button.builder(Component.literal("インベントリ中: " + (SkyPilot.get().config().hudInContainers ? "表示" : "非表示")), b -> {
			SkyPilot.get().config().hudInContainers = !SkyPilot.get().config().hudInContainers;
			rebuildWidgets();
		}).bounds(x + 154, height - 100, 130, 18).build());

		addRenderableWidget(Button.builder(Component.literal("Blaze: " + (SkyPilot.get().config().blazeDescending ? "降順" : "昇順")), b -> {
			SkyPilot.get().config().blazeDescending = !SkyPilot.get().config().blazeDescending;
			rebuildWidgets();
		}).bounds(x + 288, height - 100, 92, 18).build());

		// Row 3: Opacity and Reset
		addRenderableWidget(Button.builder(Component.literal(l.t("Opacity -", "不透明度 -")), b -> h.opacity = Math.max(0, h.opacity - 0.1)).bounds(x, height - 76, 92, 18).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Opacity +", "不透明度 +")), b -> h.opacity = Math.min(1, h.opacity + 0.1)).bounds(x + 96, height - 76, 92, 18).build());
		addRenderableWidget(Button.builder(Component.literal("位置リセット"), b -> {
			var p = UiTransforms.position(selectedElement);
			p.dx = 0;
			p.dy = 0;
			p.scale = 1.0;
			p.hidden = false;
			rebuildWidgets();
		}).bounds(x + 192, height - 76, 92, 18).build());
		addRenderableWidget(Button.builder(Component.literal("全リセット"), b -> {
			h.x = 4;
			h.y = 4;
			h.scale = 1.0;
			h.opacity = 0.6;
			SkyPilotConfig.InfoHud i = SkyPilot.get().config().infoHud;
			SkyPilot.get().config().uiPositions.clear();
			i.x = 4;
			i.y = -1;
			i.scale = 1.0;
			i.opacity = 0.5;
			rebuildWidgets();
		}).bounds(x + 288, height - 76, 92, 18).build());

		// Row 4: Done
		addRenderableWidget(Button.builder(Component.literal(l.t("Done", "完了 (保存)")), b -> onClose()).bounds(x, y, bw, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (minecraft.player != null) {
			var preview = (dev.skypilot.mod.platform.mixin.HudPreviewAccessor) minecraft.gui.hud;
			var d = net.minecraft.client.DeltaTracker.ZERO;
			preview.skypilot$scoreboard(g, d);
			preview.skypilot$actionbar(g, d);
			preview.skypilot$bossbar(g, d);
			preview.skypilot$hotbar(g, d);
			preview.skypilot$effects(g, d);
		}
		SkyPilot sp = SkyPilot.get();
		box = HudRenderer.draw(g, font, HudRenderer.rows(sp), sp.config().hud, width, height, true);
		List<dev.skypilot.mod.hud.HudRow> info = minecraft.player == null ? List.of() : InfoHud.rows(sp, minecraft, System.currentTimeMillis());
		if (info.isEmpty()) {
			info = List.of(dev.skypilot.mod.hud.HudRow.text(sp.lang().t("Info HUD (turn on features: coordinates, timers…)", "情報HUD（座標・タイマーなどの機能をオン）"),
					dev.skypilot.mod.hud.HudRow.MUTED));
		}
		infoBox = InfoHud.draw(g, info, sp.config().infoHud, box, width, height, true);
		GardenMap.render(g, true);
		DungeonMap.render(g, true);
		WindowsMedia.render(g);

		// Render outlines and labels for all elements
		List<String> all = UiTransforms.allElements();
		for (String id : all) {
			HudLayout.Box b = elementBox(id);
			boolean isSel = id.equals(selectedElement);
			boolean isHov = b.contains(mouseX, mouseY);
			boolean isHid = UiTransforms.position(id).hidden;
			int col = UiTransforms.color(id);

			if (isSel) {
				g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0x33A38BFA);
				g.outline(b.x(), b.y(), b.width(), b.height(), 0xFFFFFFFF);
				String tag = UiTransforms.name(id) + " [" + UiTransforms.modName(id) + "]" + (isHid ? " (非表示)" : "")
						+ String.format(Locale.ROOT, " · (x:%d, y:%d, ×%.1f)", b.x(), b.y(), UiTransforms.position(id).scale);
				g.text(font, tag, b.x(), Math.max(0, b.y() - 11), 0xFFFFFFFF, true);
			} else if (isHov) {
				g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0x22FFFFFF);
				g.outline(b.x(), b.y(), b.width(), b.height(), col | 0xFF000000);
				String tag = UiTransforms.name(id) + " [" + UiTransforms.modName(id) + "]";
				g.text(font, tag, b.x(), Math.max(0, b.y() - 10), col | 0xFF000000, true);
			} else if (isHid) {
				g.outline(b.x(), b.y(), b.width(), b.height(), 0x44FF4444);
			} else {
				g.outline(b.x(), b.y(), b.width(), b.height(), (col & 0x00FFFFFF) | 0x88000000);
			}
		}

		Lang l = sp.lang();
		g.centeredText(font, l.t("Click any element to drag & drop · Scroll to resize", "要素をクリックしてドラッグ＆ドロップ移動 · スクロールでサイズ変更"), width / 2, height - 140, 0xFFFFFFFF);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.y() >= height - 128 && super.mouseClicked(event, doubleClick)) return true;

		// Hit-test: first check the currently selected element
		if (event.button() == 0) {
			HudLayout.Box selBox = elementBox(selectedElement);
			if (selBox.contains(event.x(), event.y())) {
				draggingElement = selectedElement;
				offX = event.x() - selBox.x();
				offY = event.y() - selBox.y();
				return true;
			}

			// Then hit-test all other elements
			for (String id : UiTransforms.allElements()) {
				HudLayout.Box b = elementBox(id);
				if (b.contains(event.x(), event.y())) {
					selectedElement = id;
					draggingElement = id;
					offX = event.x() - b.x();
					offY = event.y() - b.y();
					rebuildWidgets();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingElement != null) {
			if ("skypilot_hud".equals(draggingElement)) {
				SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
				h.x = (int) Math.max(0, Math.round(event.x() - offX));
				h.y = (int) Math.max(0, Math.round(event.y() - offY));
				return true;
			}
			if ("info_hud".equals(draggingElement)) {
				SkyPilotConfig.InfoHud i = SkyPilot.get().config().infoHud;
				int nx = (int) Math.max(0, Math.round(event.x() - offX));
				i.x = nx + infoBox.width() / 2 > width / 2 ? -Math.max(1, width - nx - infoBox.width()) : nx;
				i.y = (int) Math.max(0, Math.round(event.y() - offY));
				return true;
			}
			var p = UiTransforms.position(draggingElement);
			var b = UiTransforms.base(draggingElement, width, height);
			var size = UiTransforms.box(draggingElement, width, height);
			p.dx = (int) Math.clamp(event.x() - offX, 0, Math.max(0, width - size.width())) - b.x();
			p.dy = (int) Math.clamp(event.y() - offY, 0, Math.max(0, height - size.height())) - b.y();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingElement = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		// Find hovered element to scale
		for (String id : UiTransforms.allElements()) {
			HudLayout.Box b = elementBox(id);
			if (b.contains(x, y)) {
				if ("skypilot_hud".equals(id)) {
					SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
					h.scale = Math.max(0.5, Math.min(2.5, Math.round((h.scale + scrollY * 0.1) * 10) / 10.0));
					return true;
				}
				if ("info_hud".equals(id)) {
					SkyPilotConfig.InfoHud i = SkyPilot.get().config().infoHud;
					i.scale = Math.max(0.5, Math.min(2.5, Math.round((i.scale + scrollY * 0.1) * 10) / 10.0));
					return true;
				}
				var p = UiTransforms.position(id);
				p.scale = Math.clamp(Math.round((p.scale + scrollY * 0.1) * 10) / 10.0, 0.4, 2.5);
				rebuildWidgets();
				return true;
			}
		}
		var p = UiTransforms.position(selectedElement);
		p.scale = Math.clamp(Math.round((p.scale + scrollY * 0.1) * 10) / 10.0, 0.4, 2.5);
		rebuildWidgets();
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
