package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.Dto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.Locale;

/** Colours and short texts shared by SkyPilot's screens, toasts and panels. */
final class Ui {
	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFAAAAAA;
	static final int DARK_GRAY = 0xFF666666;
	static final int GOLD = 0xFFFFAA00;
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int AQUA = 0xFF55FFFF;
	static final int YELLOW = 0xFFFFFF55;
	static final int PURPLE = 0xFFB07CFF;
	static final int PANEL = 0xE0101018;
	static final int PANEL_BORDER = 0xFF4A3B8C;

	private Ui() {
	}

	static int rarityColor(String rarity) {
		if (rarity == null) return WHITE;
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			default -> WHITE;
		};
	}

	static int confidenceColor(String label) {
		if (label == null) return GRAY;
		return switch (label) {
			case "HIGH" -> GREEN;
			case "MEDIUM" -> YELLOW;
			default -> RED;
		};
	}

	static String pct(double v) {
		return String.format(Locale.ROOT, "%.0f%%", v * 100);
	}

	/** "1.2M → 1.5M · +250k (21%)" */
	static String flipLine(Dto.FlipRow r) {
		return Text.coins(r.buy()) + " → " + Text.coins(r.sell()) + " · +" + Text.coins(r.profit()) + " (" + pct(r.roi()) + ")";
	}

	/** Detail line: confidence, risk, time to sell / amount. */
	static String flipDetail(Dto.FlipRow r, Lang l) {
		StringBuilder b = new StringBuilder();
		b.append(l.t("confidence ", "信頼度 ")).append(r.confidenceLabel());
		if (r.risk() != null && !r.risk().isEmpty()) b.append(" · ").append(l.t("risk ", "リスク ")).append(r.risk());
		if (r.amount() != null) b.append(" · ×").append(Text.coins(r.amount()));
		if (r.profitPerHour() != null) b.append(" · ").append(Text.coins(r.profitPerHour())).append(l.t("/h", "/時"));
		if (r.sellTime() != null) b.append(" · ").append(l.t("sells in ", "売却まで ")).append(r.sellTime());
		return b.toString();
	}

	/** Put a server command into the chat box. The player decides whether to send it. */
	static void suggest(String command) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ChatScreen(command, false)));
	}
}
