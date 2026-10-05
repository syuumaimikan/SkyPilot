package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.platform.mixin.ChatComponentAccessor;
import dev.skypilot.mod.translation.ChannelClassifier;
import dev.skypilot.mod.translation.ClickToTranslate;
import dev.skypilot.mod.translation.RecentEntries;
import dev.skypilot.mod.translation.StyledLine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * Click-to-translate: eligible chat lines get a small "[訳]" button; clicking it replaces that
 * line with its translation, and "[原文]" switches back.
 *
 * <ul>
 *   <li>The server's component is kept as it is (hover / click events such as "click to open
 *   the Auction House" keep working); the button is a separate sibling after it.</li>
 *   <li>The button runs a client-side command ({@code /skypilot tr <id>}), which Fabric handles
 *   locally - nothing is sent to the game server.</li>
 *   <li>Only the clicked line is sent for translation, after the one-time disclosure. Lines are
 *   kept in memory only (bounded, cleared on disconnect) so they can be toggled.</li>
 * </ul>
 */
final class ClickTranslate {
	static final String COMMAND = "/skypilot tr ";
	/** About as many lines as the chat history keeps. */
	private static final int CAPACITY = 300;
	private static final RecentEntries<Entry> ENTRIES = new RecentEntries<>(CAPACITY);

	/** Last decorated message → the server's original, for the auto translator that runs right after. */
	private static Component lastDecorated;
	private static Component lastOriginal;

	private static final class Entry {
		final McText.Flattened flat;
		/** The server's message plus the "[訳]" button, as added to the chat. */
		Component withButton;
		Component translated;
		/** The component currently in the chat history for this line. */
		Component shown;
		boolean pending;

		Entry(McText.Flattened flat) {
			this.flat = flat;
		}
	}

	private ClickTranslate() {
	}

	/** {@code ClientReceiveMessageEvents.MODIFY_GAME}: append the button to eligible lines. */
	static Component decorate(Component message, boolean overlay) {
		SkyPilot sp = SkyPilot.get();
		if (overlay || sp == null || !sp.config().translation.clickToTranslate) return message;
		McText.Flattened flat = McText.flatten(message);
		if (!ClickToTranslate.eligible(flat.line().plain())) return message;
		// the button carries the id, so register first and build the component after
		Entry entry = new Entry(flat);
		int id = ENTRIES.add(entry);
		Component decorated = Component.empty().append(message).append(button(id, false, flat.line().plain(), sp.lang()));
		entry.withButton = decorated;
		entry.shown = decorated;
		lastDecorated = decorated;
		lastOriginal = message;
		return decorated;
	}

	/** The server's message without our button (so other features never see "[訳]"). */
	static Component undecorated(Component message) {
		return message == lastDecorated ? lastOriginal : message;
	}

	static void clear() {
		ENTRIES.clear();
		lastDecorated = null;
		lastOriginal = null;
	}

	static MutableComponent button(int id, boolean translated, String originalPlain, Lang l) {
		Component hover = translated
				? Component.literal(l.t("Click to show the original:\n", "クリックで原文に戻す:\n")).append(Component.literal(originalPlain).withStyle(ChatFormatting.GRAY))
				: Component.literal(l.t("Click to translate into Japanese (SkyPilot)", "クリックで日本語に翻訳（SkyPilot）"));
		Style style = Style.EMPTY.withColor(translated ? ChatFormatting.GREEN : ChatFormatting.DARK_AQUA)
				.withClickEvent(new ClickEvent.RunCommand(COMMAND + id))
				.withHoverEvent(new HoverEvent.ShowText(hover));
		return Component.literal(" ").append(Component.literal(translated ? l.t("[Original]", "[原文]") : l.t("[→JA]", "[訳]")).setStyle(style));
	}

	/** {@code /skypilot tr <id> [ok]}: translate the line, or switch between translation and original. */
	static int click(int id, boolean consent) {
		SkyPilot sp = SkyPilot.get();
		Lang l = sp.lang();
		Entry e = ENTRIES.get(id);
		if (e == null) {
			McText.info(l.t("This message is too old to translate.", "古いメッセージのため翻訳できません。"));
			return 0;
		}
		if (consent && !sp.config().translation.disclosureAccepted) {
			sp.config().translation.disclosureAccepted = true;
			sp.saveConfig();
		}
		if (e.translated != null) {
			show(e, e.shown == e.translated ? e.withButton : e.translated);
			return 1;
		}
		if (!sp.config().translation.disclosureAccepted) {
			McText.chat(McText.prefix()
					.append(Component.literal(l.t(
							"Translating sends the text of the clicked line to your SkyPilot server (" + sp.config().apiBaseUrl + "), which may forward it to its translation provider. Translations are not stored. ",
							"翻訳すると、クリックした行のテキストがSkyPilotサーバー（" + sp.config().apiBaseUrl + "）へ送信され、サーバー設定の翻訳プロバイダへ転送される場合があります。翻訳内容は保存されません。"))
							.withStyle(ChatFormatting.GRAY))
					.append(Component.literal(l.t("[Agree and translate]", "[同意して翻訳]")).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
							.withClickEvent(new ClickEvent.RunCommand(COMMAND + id + " ok")))));
			return 0;
		}
		if (e.pending) return 1;
		e.pending = true;
		String plain = e.flat.line().plain();
		ChannelClassifier.Classified c = ChannelClassifier.classify(plain);
		StyledLine[] parts = e.flat.line().splitAt(c.bodyStart());
		Minecraft mc = Minecraft.getInstance();
		sp.translator().translateText(parts[1].plain(), c.channel(), Sidebar.playerNames(mc)).whenComplete((result, err) -> mc.execute(() -> {
			e.pending = false;
			if (result == null) {
				McText.error(sp.linked()
						? l.t("Could not translate this line right now (the original is unchanged).", "この行は現在翻訳できません（原文はそのままです）。")
						: l.t("Lines outside the built-in dictionary need a linked SkyPilot server - /skypilot link", "辞書にない文の翻訳にはSkyPilotとの連携が必要です - /skypilot link"));
				return;
			}
			StyledLine line = StyledLine.translated(parts[0], parts[1], result, "");
			e.translated = Component.empty().append(McText.rebuild(line, e.flat.styles())).append(button(id, true, plain, l));
			show(e, e.translated);
		}));
		return 1;
	}

	/** Put {@code next} where the line is shown now; if it left the chat history, add it at the bottom. */
	private static void show(Entry e, Component next) {
		ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
		if (!replace(chat, e.shown, next)) chat.addClientSystemMessage(next);
		e.shown = next;
	}

	/** Swap a message in the chat history in place (same time stamp, signature and tag). */
	static boolean replace(ChatComponent chat, Component current, Component next) {
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		List<GuiMessage> all = access.skypilot$allMessages();
		for (int i = 0; i < all.size(); i++) {
			GuiMessage m = all.get(i);
			if (m.content() == current || m.content().equals(current)) {
				all.set(i, new GuiMessage(m.addedTime(), next, m.signature(), m.source(), m.tag()));
				access.skypilot$refreshTrimmedMessages();
				return true;
			}
		}
		return false;
	}
}
