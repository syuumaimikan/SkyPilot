package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.hud.HudLayout;
import dev.skypilot.mod.hud.HudModel;
import dev.skypilot.mod.hud.HudRow;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Draws the SkyPilot HUD box (Roadmap / Progress / Context / Session). Display only. */
final class HudRenderer {
	private HudRenderer() {
	}

	/** Where the SkyPilot HUD was drawn this frame (null when hidden); the info HUD goes below it. */
	static HudLayout.Box lastBox;

	static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		lastBox = null;
		SkyPilot sp = SkyPilot.get();
		Minecraft mc = Minecraft.getInstance();
		if (sp == null || !sp.config().hud.enabled || mc.player == null) return;
		if (mc.gui.screen() instanceof HudEditorScreen) return; // the editor draws its own preview
		if(!sp.config().hudInContainers && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)return;
		if (!sp.context().inSkyBlock()) return;
		lastBox = draw(g, mc.font, rows(sp), sp.config().hud, g.guiWidth(), g.guiHeight(), false);
	}

	static List<HudRow> rows(SkyPilot sp) {
		SkyPilotConfig.Hud h = sp.config().hud;
		return HudModel.build(new HudModel.Input(sp.linked(), sp.hudState(), sp.apiError() != null ? sp.apiError() : sp.hudError(), sp.context(),
				sp.config().session.enabled ? sp.session().snapshot() : null, h.showContext, h.showSession, sp.lang(), System.currentTimeMillis()));
	}

	/** Returns the on-screen box so the HUD editor can hit-test it. */
	static HudLayout.Box draw(GuiGraphicsExtractor g, Font font, List<HudRow> rows, SkyPilotConfig.Hud cfg, int screenW, int screenH, boolean outline) {
		return draw(g, font, rows, cfg.x, cfg.y, cfg.scale, cfg.opacity, 320, screenW, screenH, outline);
	}

	static HudLayout.Box draw(GuiGraphicsExtractor g, Font font, List<HudRow> rows, int x, int y0, double scale, double opacity, int maxTextW, int screenW, int screenH,
			boolean outline) {
		int width = 0;
		for (HudRow r : rows) width = Math.max(width, font.width(r.text()));
		width = Math.min(width, maxTextW) + HudLayout.PADDING * 2;
		int height = HudLayout.contentHeight(rows, font.lineHeight);
		HudLayout.Box box = HudLayout.place(x, y0, width, height, scale, screenW, screenH);
		float s = (float) scale;
		g.pose().pushMatrix();
		g.pose().translate(box.x(), box.y());
		g.pose().scale(s, s);
		g.fill(0, 0, width, height, HudLayout.background(opacity));
		if (outline) g.outline(0, 0, width, height, 0xFF9D8CFF);
		int y = HudLayout.PADDING;
		for (HudRow r : rows) {
			String text = font.width(r.text()) > maxTextW ? font.plainSubstrByWidth(r.text(), maxTextW - 6) + "…" : r.text();
			g.text(font, text, HudLayout.PADDING, y, r.color(), false);
			y += font.lineHeight;
			if (r.progress() >= 0) {
				int barW = width - HudLayout.PADDING * 2;
				g.fill(HudLayout.PADDING, y, HudLayout.PADDING + barW, y + HudLayout.BAR_HEIGHT, 0x40FFFFFF);
				g.fill(HudLayout.PADDING, y, HudLayout.PADDING + (int) Math.round(barW * r.progress()), y + HudLayout.BAR_HEIGHT, 0xFF9D8CFF);
				y += HudLayout.BAR_HEIGHT + 1;
			}
			y += HudLayout.LINE_GAP;
		}
		g.pose().popMatrix();
		return box;
	}
}
