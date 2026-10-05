package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.ApiClient;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Flip alerts (opt-in): every 30 s asks the SkyPilot server for new AH flips that match the
 * player's alert rules and shows a toast plus a chat line. The chat line can open the menu or
 * put {@code /viewauction} into the chat box - SkyPilot never sends it or buys anything.
 */
final class FlipAlerts {
	private static final long POLL_MS = 30_000;
	private static final int MAX_PER_POLL = 3;
	private static final Set<String> SHOWN = new LinkedHashSet<>();
	private static final Deque<Dto.FlipRow> RECENT = new ArrayDeque<>();
	private static long since;
	private static long nextPoll;
	private static String lastError;

	private FlipAlerts() {
	}

	static List<Dto.FlipRow> recent() {
		return new ArrayList<>(RECENT);
	}

	static String lastError() {
		return lastError;
	}

	/** Called once per second (game thread). */
	static void tickSecond(Minecraft mc) {
		SkyPilot sp = SkyPilot.get();
		ApiClient api = sp.api();
		long now = System.currentTimeMillis();
		if (!sp.config().market.alerts || api == null || !sp.linked() || mc.player == null || now < nextPoll) return;
		nextPoll = now + POLL_MS;
		long from = since == 0 ? now - 60_000 : since;
		api.alerts(from, sp.lang().code()).whenComplete((a, err) -> mc.execute(() -> {
			if (a == null) {
				lastError = SkyPilot.shortError(err);
				return;
			}
			lastError = null;
			since = a.nowMs();
			int shown = 0;
			for (Dto.FlipRow r : a.rows()) {
				if (shown >= MAX_PER_POLL || !SHOWN.add(r.id())) continue;
				show(r);
				shown++;
			}
			while (SHOWN.size() > 500) SHOWN.remove(SHOWN.iterator().next());
		}));
	}

	static void show(Dto.FlipRow r) {
		Minecraft mc = Minecraft.getInstance();
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		RECENT.addFirst(r);
		while (RECENT.size() > 20) RECENT.removeLast();
		mc.gui.toastManager().addToast(new FlipToast(ItemIcons.of(r.icon()), r.name(), Ui.rarityColor(r.rarity()), Ui.flipLine(r)));
		var line = McText.prefix()
				.append(Component.literal(l.t("Flip: ", "フリップ: ")).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(r.name()).withStyle(Style.EMPTY.withColor(Ui.rarityColor(r.rarity()) & 0xFFFFFF)))
				.append(Component.literal(" " + Ui.flipLine(r) + " ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(l.t("[Details]", "[詳細]")).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.RunCommand("/skypilot menu ah"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(l.t("Open the SkyPilot menu", "SkyPilotメニューを開く"))))));
		if (r.command() != null) {
			line.append(Component.literal(" "))
					.append(Component.literal(l.t("[To chat box]", "[入力欄へ]")).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
							.withClickEvent(new ClickEvent.SuggestCommand(r.command()))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(r.command() + "\n" + l.t("Only fills the chat box - you decide whether to send it.", "チャット欄に入力するだけです。送信するかはご自身で判断してください。"))))));
		}
		mc.gui.hud.getChat().addClientSystemMessage(line);
		if (sp.config().market.alertSound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
	}
}
