package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.PerfRules;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Performance ("軽量化"): the performance switches with their numbers, and Minecraft's own video
 * settings lowered with one button (and put back with another). Opens with /skypilot perf or
 * Settings → General → Performance….
 */
final class PerformanceScreen extends Screen {
	private static final int ROW = 22;
	private static boolean videoTab;
	private static int pageIndex;

	private final Screen parent;
	private String message;
	private int px;
	private int pw;
	private int listY;

	PerformanceScreen(Screen parent) {
		super(Component.literal("SkyPilot"));
		this.parent = parent;
	}

	/** For the client game test. */
	static void showVideo(boolean video) {
		videoTab = video;
		pageIndex = 0;
	}

	/** The number a feature has, or null. */
	private static String number(Features.Feature f, SkyPilotConfig c, Lang l) {
		if (f == Features.PERF_BACKGROUND_FPS) return c.perf.backgroundFps + " FPS";
		if (f == Features.PERF_MENU_FPS) return c.perf.menuFps + " FPS";
		if (f == Features.PERF_FAR_ENTITIES) return c.perf.entityDistance + l.t(" blocks", " ブロック");
		if (f == Features.PERF_FAR_PLAYERS) return c.perf.playerDistance + l.t(" blocks", " ブロック");
		if (f == Features.PERF_PARTICLE_CAP) return c.perf.particlesPerTick + l.t("/tick", "/tick");
		return null;
	}

	private static void step(Features.Feature f, SkyPilotConfig c, int dir) {
		if (f == Features.PERF_BACKGROUND_FPS) c.perf.backgroundFps += 5 * dir;
		if (f == Features.PERF_MENU_FPS) c.perf.menuFps += (c.perf.menuFps + (dir > 0 ? 0 : -1) >= 60 ? 30 : 15) * dir;
		if (f == Features.PERF_FAR_ENTITIES) c.perf.entityDistance += 8 * dir;
		if (f == Features.PERF_FAR_PLAYERS) c.perf.playerDistance += 4 * dir;
		if (f == Features.PERF_PARTICLE_CAP) c.perf.particlesPerTick += (c.perf.particlesPerTick + (dir > 0 ? 0 : -1) >= 200 ? 100 : 25) * dir;
		c.sanitize();
	}

	private int perPage() {
		return Math.max(3, (height - listY - 56) / ROW);
	}

	@Override
	protected void init() {
		SkyPilot sp = SkyPilot.get();
		SkyPilotConfig c = sp.config();
		Lang l = sp.lang();
		pw = Math.max(200, Math.min(380, width - 8));
		px = width / 2 - pw / 2;
		Button sw = Button.builder(Component.literal(l.t("Switches", "スイッチ")), b -> {
			videoTab = false;
			rebuildWidgets();
		}).bounds(px, 34, pw / 2 - 2, 20).build();
		sw.active = videoTab;
		addRenderableWidget(sw);
		Button vid = Button.builder(Component.literal(l.t("Video settings", "ビデオ設定")), b -> {
			videoTab = true;
			rebuildWidgets();
		}).bounds(px + pw / 2 + 2, 34, pw / 2 - 2, 20).build();
		vid.active = !videoTab;
		addRenderableWidget(vid);
		listY = 60;
		if (videoTab) video(sp, c, l);
		else switches(sp, c, l);
		addRenderableWidget(Button.builder(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(px, height - 26, pw, 20).build());
	}

	private void switches(SkyPilot sp, SkyPilotConfig c, Lang l) {
		List<Features.Feature> list = Features.in(Features.Category.PERFORMANCE);
		int per = perPage();
		int pages = (list.size() + per - 1) / per;
		pageIndex = Math.max(0, Math.min(pageIndex, pages - 1));
		int y = listY;
		for (int i = pageIndex * per; i < Math.min(list.size(), (pageIndex + 1) * per); i++) {
			Features.Feature f = list.get(i);
			String num = number(f, c, l);
			int bw = num == null ? pw : pw - 44;
			boolean on = c.on(f);
			Button b = Button.builder(Component.literal(f.name(l) + (num == null ? "" : " (" + num + ")") + ": " + (on ? l.t("ON", "オン") : l.t("OFF", "オフ"))), btn -> {
				Features.set(c, f, !c.on(f));
				sp.saveConfig();
				rebuildWidgets();
			}).bounds(px, y, bw, 20).build();
			b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(f.description(l))));
			addRenderableWidget(b);
			if (num != null) {
				addRenderableWidget(Button.builder(Component.literal("−"), btn -> {
					step(f, c, -1);
					sp.saveConfig();
					rebuildWidgets();
				}).bounds(px + pw - 42, y, 20, 20).build());
				addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
					step(f, c, 1);
					sp.saveConfig();
					rebuildWidgets();
				}).bounds(px + pw - 20, y, 20, 20).build());
			}
			y += ROW;
		}
		int by = height - 50;
		Button prev = Button.builder(Component.literal("‹"), b -> {
			pageIndex--;
			rebuildWidgets();
		}).bounds(px, by, 20, 20).build();
		prev.active = pageIndex > 0;
		addRenderableWidget(prev);
		Button next = Button.builder(Component.literal("›"), b -> {
			pageIndex++;
			rebuildWidgets();
		}).bounds(px + 22, by, 20, 20).build();
		next.active = pageIndex < pages - 1;
		addRenderableWidget(next);
		addRenderableWidget(Button.builder(Component.literal(l.t("Recommended on", "おすすめをオン")), b -> {
			int n = 0;
			for (Features.Feature f : list) {
				if (f.recommended() && !c.on(f)) {
					Features.set(c, f, true);
					n++;
				}
			}
			sp.saveConfig();
			message = l.t(n + " switched on.", n + " 個をオンにしました。");
			rebuildWidgets();
		}).bounds(px + 46, by, (pw - 46) / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal(l.t("All off", "すべてオフ")), b -> {
			Features.disableAll(c, Features.Category.PERFORMANCE);
			sp.saveConfig();
			message = l.t("Every performance switch is off.", "軽量化のスイッチをすべてオフにしました。");
			rebuildWidgets();
		}).bounds(px + 46 + (pw - 46) / 2, by, (pw - 46) / 2, 20).build());
	}

	private void video(SkyPilot sp, SkyPilotConfig c, Lang l) {
		int by = height - 50;
		Button apply = Button.builder(Component.literal(l.t("Apply lightweight settings", "軽量設定を適用")), b -> {
			int n = VideoPreset.apply(minecraft, c);
			sp.saveConfig();
			message = n == 0 ? l.t("Already as light as the preset.", "すでに軽量設定と同じかそれ以下です。")
					: l.t(n + " option(s) lowered. \"Restore\" puts your values back.", n + " 項目を下げました。「元に戻す」で戻せます。");
			rebuildWidgets();
		}).bounds(px, by, pw / 2 - 2, 20).build();
		apply.active = VideoPreset.pending(minecraft.options) > 0;
		addRenderableWidget(apply);
		Button restore = Button.builder(Component.literal(l.t("Restore my settings", "元に戻す")), b -> {
			int n = VideoPreset.restore(minecraft, c);
			sp.saveConfig();
			message = l.t(n + " option(s) restored.", n + " 項目を元に戻しました。");
			rebuildWidgets();
		}).bounds(px + pw / 2 + 2, by, pw / 2 - 2, 20).build();
		restore.active = c.perf.videoBackup != null;
		addRenderableWidget(restore);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// own backdrop, so the page stays readable over the HUD whatever the menu blur setting is
		g.fill(px - 6, 2, px + pw + 6, height - 2, 0xD0101018);
		super.extractRenderState(g, mouseX, mouseY, delta);
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		g.centeredText(font, Component.literal(l.t("SkyPilot · Performance", "SkyPilot · 軽量化")), width / 2, 8, 0xFFFFFFFF);
		Runtime rt = Runtime.getRuntime();
		String stats = minecraft.getFps() + " FPS  ·  " + PerfRules.memory(rt.totalMemory() - rt.freeMemory(), rt.maxMemory())
				+ (minecraft.level == null ? "" : "  ·  " + l.t("Entities ", "エンティティ ") + minecraft.level.getEntityCount() + "  ·  " + l.t("Particles ", "パーティクル ")
						+ PerfRules.particleTotal(minecraft.particleEngine.countParticles()));
		g.centeredText(font, Component.literal(stats), width / 2, 20, 0xFFAAAAAA);
		if (videoTab) {
			SkyPilotConfig c = sp.config();
			int y = listY;
			int col2 = px + pw * 52 / 100;
			int col3 = px + pw * 76 / 100;
			g.text(font, l.t("Option", "項目"), px, y, 0xFF888888, false);
			g.text(font, l.t("Now", "現在"), col2, y, 0xFF888888, false);
			g.text(font, c.perf.videoBackup == null ? l.t("Lightweight", "軽量設定") : l.t("Yours (saved)", "元の値"), col3, y, 0xFF888888, false);
			y += 14;
			for (String[] r : VideoPreset.rows(minecraft.options, c, l)) {
				g.text(font, font.plainSubstrByWidth(r[0], col2 - px - 4), px, y, 0xFFFFFFFF, false);
				g.text(font, r[1], col2, y, r[2].isEmpty() ? 0xFF55DD77 : 0xFFFFAA55, false);
				String right = c.perf.videoBackup == null ? (r[2].isEmpty() ? "✔" : "→ " + r[2]) : r[3];
				g.text(font, right, col3, y, c.perf.videoBackup == null && r[2].isEmpty() ? 0xFF55DD77 : 0xFFCCCCCC, false);
				y += 12;
			}
			Component note = Component.literal(l.t("Only lowers heavier options, never raises any. Your own values are kept and \"Restore\" puts them back.",
					"重い項目だけを下げ、上げることはありません。元の値は保存され、「元に戻す」で戻せます。"));
			if (y + 6 + font.wordWrapHeight(note, pw) < height - 64) g.textWithWordWrap(font, note, px, y + 6, pw, 0xFF888888);
		} else {
			List<Features.Feature> list = Features.in(Features.Category.PERFORMANCE);
			int per = perPage();
			int pages = (list.size() + per - 1) / per;
			g.text(font, (pageIndex + 1) + "/" + pages, px + 2, height - 62, 0xFF888888, false);
			if (message == null) {
				g.text(font, font.plainSubstrByWidth(l.t("Drawing only: hidden things still exist and react as usual.", "描画だけを省きます。隠したものも存在し、普段どおり反応します。"), pw - 30),
						px + 30, height - 62, 0xFF888888, false);
			}
		}
		if (message != null) g.text(font, font.plainSubstrByWidth(message, pw - 30), px + (videoTab ? 0 : 30), height - 62, 0xFF55FF55, false);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
