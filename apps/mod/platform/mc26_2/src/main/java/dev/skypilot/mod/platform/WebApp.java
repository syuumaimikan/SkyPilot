package dev.skypilot.mod.platform;

import dev.skypilot.mod.network.ApiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/** Opens the configured SkyPilot web app through Minecraft's link confirmation screen. */
final class WebApp {
	private WebApp() {}

	static void open(Screen parent) {
		open(parent, "");
	}

	static void open(Screen parent, String path) {
		Minecraft mc = Minecraft.getInstance();
		try {
			if (!java.util.Set.of("", "/guides", "/help/api", "/money", "/networth").contains(path)) throw new IllegalArgumentException("Unknown web page");
			String url = ApiClient.validateBaseUrl(SkyPilot.get().config().apiBaseUrl).toString() + path;
			mc.execute(() -> mc.gui.setScreen(new ConfirmLinkScreen(yes -> {
				if (yes) Util.getPlatform().openUri(url);
				mc.gui.setScreen(parent);
			}, url, false)));
		} catch (IllegalArgumentException e) {
			McText.error(SkyPilot.get().lang().t("Check your SkyPilot server URL in Settings.", "設定の SkyPilot サーバー URL を確認してください。"));
		}
	}
}
