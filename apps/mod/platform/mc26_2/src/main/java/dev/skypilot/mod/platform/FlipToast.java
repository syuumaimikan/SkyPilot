package dev.skypilot.mod.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.world.item.ItemStack;

/** Corner notification for a flip alert: item icon, name and the profit line. */
final class FlipToast implements Toast {
	private static final long SHOW_MS = 6_000;
	private static final int WIDTH = 200;
	private final ItemStack icon;
	private final String title;
	private final int titleColor;
	private final String line;
	private Visibility visibility = Visibility.SHOW;

	FlipToast(ItemStack icon, String title, int titleColor, String line) {
		this.icon = icon;
		this.title = title;
		this.titleColor = titleColor;
		this.line = line;
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long visibleMs) {
		if (visibleMs >= SHOW_MS) visibility = Visibility.HIDE;
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleMs) {
		g.fill(0, 0, WIDTH, height(), Ui.PANEL);
		g.outline(0, 0, WIDTH, height(), Ui.PANEL_BORDER);
		g.item(icon, 7, 8);
		g.text(font, font.plainSubstrByWidth(title, WIDTH - 34), 28, 7, titleColor, false);
		g.text(font, font.plainSubstrByWidth(line, WIDTH - 34), 28, 18, Ui.GREEN, false);
	}
}
