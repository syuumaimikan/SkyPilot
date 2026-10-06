package dev.skypilot.mod.platform;

import dev.skypilot.mod.translation.ChannelClassifier;
import dev.skypilot.mod.translation.TranslationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Receives server chat / action-bar messages. The original message is never delayed or hidden:
 * the action bar feeds the session tracker, and (only when the player enabled a translation
 * mode) a translated copy is added below the original line once it is ready. The only change
 * to a server line is the optional click-to-translate button ({@link ClickTranslate}).
 */
final class ChatHook {
	private static final String MARKER = "[JA] ";

	private ChatHook() {
	}

	static void onGameMessage(Component message, boolean overlay) {
		SkyPilot sp = SkyPilot.get();
		if (sp == null) return;
		if (overlay) {
			String text = message.getString();
			if (sp.config().session.enabled) sp.session().onActionBar(text);
			Trackers.actionBar(text);
			return;
		}
		message = ChatFeatures.original(message);
		Afk.chat(message.getString());
		TranslationMode mode = sp.translationMode();
		if (!mode.sendsAutomatically() || !sp.config().translation.disclosureAccepted || !sp.context().inSkyBlock()) return;
		McText.Flattened flat = McText.flatten(message);
		String plain = flat.line().plain();
		// Our own translated lines carry the marker: never translate them again.
		if (plain.length() > 600 || plain.contains(MARKER) || !mode.automatic(ChannelClassifier.classify(plain).channel())) return;
		sp.translator().translateLine(flat.line(), Sidebar.playerNames(Minecraft.getInstance()), MARKER,
				translated -> Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(McText.rebuild(translated, flat.styles())));
	}
}
