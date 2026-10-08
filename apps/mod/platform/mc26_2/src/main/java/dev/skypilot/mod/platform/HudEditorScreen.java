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

/** Drag either HUD box (SkyPilot HUD, info HUD) to move it, scroll over it to resize; buttons adjust opacity. */
final class HudEditorScreen extends Screen {
	private final Screen parent;
	private HudLayout.Box box = new HudLayout.Box(0, 0, 0, 0);
	private HudLayout.Box infoBox = new HudLayout.Box(0, 0, 0, 0);
	private boolean dragging;
	private boolean draggingInfo;
	private String draggingElement;
	private String selectedElement="scoreboard";
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
		addRenderableWidget(Button.builder(Component.literal("選択: "+UiTransforms.name(selectedElement)),b->{int n=java.util.Arrays.asList(UiTransforms.ELEMENTS).indexOf(selectedElement);selectedElement=UiTransforms.ELEMENTS[(n+1)%UiTransforms.ELEMENTS.length];rebuildWidgets();}).bounds(x,height-126,320,18).build());
		addRenderableWidget(Button.builder(Component.literal("情報HUD: コンパクト/全表示"),b->{SkyPilot.get().config().compactInfo=!SkyPilot.get().config().compactInfo;}).bounds(x,height-82,200,18).build());
		addRenderableWidget(Button.builder(Component.literal("Blaze: 昇順/降順"),b->{SkyPilot.get().config().blazeDescending=!SkyPilot.get().config().blazeDescending;}).bounds(x+204,height-82,116,18).build());
		addRenderableWidget(Button.builder(Component.literal("インベントリ中のHUD: 表示/非表示"),b->{SkyPilot.get().config().hudInContainers=!SkyPilot.get().config().hudInContainers;}).bounds(x,height-104,320,18).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Opacity -", "不透明度 -")), b -> h.opacity = Math.max(0, h.opacity - 0.1)).bounds(x, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Opacity +", "不透明度 +")), b -> h.opacity = Math.min(1, h.opacity + 0.1)).bounds(x + 80, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Reset", "リセット")), b -> {
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
		}).bounds(x + 160, y, 76, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(x + 240, y, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if(minecraft.player!=null){var preview=(dev.skypilot.mod.platform.mixin.HudPreviewAccessor)minecraft.gui.hud;var d=net.minecraft.client.DeltaTracker.ZERO;preview.skypilot$scoreboard(g,d);preview.skypilot$actionbar(g,d);preview.skypilot$bossbar(g,d);preview.skypilot$hotbar(g,d);preview.skypilot$effects(g,d);}
		SkyPilot sp = SkyPilot.get();
		box = HudRenderer.draw(g, font, HudRenderer.rows(sp), sp.config().hud, width, height, true);
		java.util.List<dev.skypilot.mod.hud.HudRow> info = minecraft.player == null ? java.util.List.of() : InfoHud.rows(sp, minecraft, System.currentTimeMillis());
		if (info.isEmpty()) {
			info = java.util.List.of(dev.skypilot.mod.hud.HudRow.text(sp.lang().t("Info HUD (turn on features: coordinates, timers…)", "情報HUD（座標・タイマーなどの機能をオン）"),
					dev.skypilot.mod.hud.HudRow.MUTED));
		}
		infoBox = InfoHud.draw(g, info, sp.config().infoHud, box, width, height, true);
		GardenMap.render(g,true);DungeonMap.render(g,true);WindowsMedia.render(g);
		var selected=UiTransforms.box(selectedElement,width,height);g.outline(selected.x(),selected.y(),selected.width(),selected.height(),0xFFAA88FF);g.text(font,UiTransforms.name(selectedElement),selected.x(),Math.max(0,selected.y()-10),0xFFFFFF88,true);
		Lang l = sp.lang();
		SkyPilotConfig.Hud h = sp.config().hud;
		g.centeredText(font, l.t("Drag to move · scroll to resize", "ドラッグで移動 · スクロールでサイズ変更"), width / 2, height - 44, 0xFFFFFFFF);
		g.centeredText(font, String.format(Locale.ROOT, "x %d  y %d  ×%.1f  %.0f%%", h.x, h.y, h.scale, h.opacity * 100), width / 2, height - 56, 0xFFAAAAAA);
		super.extractRenderState(g,mouseX,mouseY,delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if(event.y()>=height-126&&super.mouseClicked(event,doubleClick))return true;
		{String id=selectedElement;var b=UiTransforms.box(id,width,height);if(event.button()==0&&b.contains(event.x(),event.y())){draggingElement=id;offX=event.x()-b.x();offY=event.y()-b.y();return true;}}
		if (event.button() == 0 && box.contains(event.x(), event.y())) {
			dragging = true;
			offX = event.x() - box.x();
			offY = event.y() - box.y();
			return true;
		}
		if (event.button() == 0 && infoBox.contains(event.x(), event.y())) {
			draggingInfo = true;
			offX = event.x() - infoBox.x();
			offY = event.y() - infoBox.y();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if(draggingElement!=null){var p=UiTransforms.position(draggingElement);var b=UiTransforms.base(draggingElement,width,height);var size=UiTransforms.box(draggingElement,width,height);p.dx=(int)Math.clamp(event.x()-offX,0,Math.max(0,width-size.width()))-b.x();p.dy=(int)Math.clamp(event.y()-offY,0,Math.max(0,height-size.height()))-b.y();return true;}
		if (dragging) {
			SkyPilotConfig.Hud h = SkyPilot.get().config().hud;
			h.x = (int) Math.max(0, Math.round(event.x() - offX));
			h.y = (int) Math.max(0, Math.round(event.y() - offY));
			return true;
		}
		if (draggingInfo) {
			SkyPilotConfig.InfoHud i = SkyPilot.get().config().infoHud;
			int nx = (int) Math.max(0, Math.round(event.x() - offX));
			// keep it anchored to the right edge when dropped in the right half
			i.x = nx + infoBox.width() / 2 > width / 2 ? -Math.max(1, width - nx - infoBox.width()) : nx;
			i.y = (int) Math.max(0, Math.round(event.y() - offY));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		draggingInfo = false;
		draggingElement=null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if(UiTransforms.box(selectedElement,width,height).contains(x,y)){var p=UiTransforms.position(selectedElement);p.scale=Math.clamp(p.scale+scrollY*.1,.5,2);return true;}
		if (infoBox.contains(x, y)) {
			SkyPilotConfig.InfoHud i = SkyPilot.get().config().infoHud;
			i.scale = Math.max(0.5, Math.min(2.5, Math.round((i.scale + scrollY * 0.1) * 10) / 10.0));
			return true;
		}
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
