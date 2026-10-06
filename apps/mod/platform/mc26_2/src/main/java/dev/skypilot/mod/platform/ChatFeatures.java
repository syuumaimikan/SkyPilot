package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ChatRules;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.platform.mixin.ChatComponentAccessor;
import dev.skypilot.mod.translation.StyledLine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chat features. Everything happens to the client's own copy of a line: hiding chosen spam
 * groups, stacking repeats, timestamps, click-to-copy, a mention marker and a waypoint button.
 * Nothing is sent to the server; a hidden line is simply not added to this client's chat.
 *
 * <p>Order for a server line: hide? → stack repeats, copy, mention, waypoint button → the
 * click-to-translate button → timestamp in front.
 */
final class ChatFeatures {
	private static final long COMPACT_WINDOW_MS = 60_000;
	private static String lastKey;
	private static long lastAt;
	private static int repeat;
	/** The component last added to the chat for a server line, and the server's original. */
	private static Component lastShown;
	private static Component lastOriginal;

	private ChatFeatures() {
	}

	/** Recent server lines for /skypilot find (memory only, cleared on disconnect). */
	record Logged(long at, String text, boolean hidden) {
	}

	static final java.util.Deque<Logged> LOG = new java.util.ArrayDeque<>();
	private static final int LOG_SIZE = 500;

	/**
	 * {@code ClientReceiveMessageEvents.ALLOW_GAME}: every server line passes here first, so the
	 * trackers also see lines a filter hides; false hides the line from this client's chat.
	 */
	static boolean allow(Component message, boolean overlay) {
		if (overlay || SkyPilot.get() == null) return true;
		String plain = message.getString();
		onMessage(plain);
		boolean show = shown(plain);
		if (Feat.on(Features.CHAT_SEARCH)) {
			LOG.addLast(new Logged(System.currentTimeMillis(), plain, !show));
			while (LOG.size() > LOG_SIZE) LOG.pollFirst();
		}
		return show;
	}

	private static boolean shown(String plain) {
		Optional<ChatRules.Hide> group = ChatRules.hideGroup(plain);
		if (group.isEmpty()) return true;
		Features.Feature f = switch (group.get()) {
			case LOBBY -> Features.CHAT_HIDE_LOBBY;
			case WARPING -> Features.CHAT_HIDE_WARPING;
			case PROFILE -> Features.CHAT_HIDE_PROFILE;
			case COMBO -> Features.CHAT_HIDE_COMBO;
			case ABILITY -> Features.CHAT_HIDE_ABILITY;
			case GEXP -> Features.CHAT_HIDE_GEXP;
			case SACKS -> Features.CHAT_HIDE_SACKS;
			case SEPARATOR -> Features.CHAT_HIDE_SEPARATORS;
			case MINING_CHEST -> Features.MINING_HIDE_CHESTS;
			case DUNGEON -> Features.DUNGEONS_FILTER;
		};
		return !Feat.on(f);
	}

	/** {@code ClientReceiveMessageEvents.MODIFY_GAME}: the whole chain for one server line. */
	static Component modify(Component message, boolean overlay) {
		SkyPilot sp = SkyPilot.get();
		if (overlay || sp == null) return message;
		Component original = message;
		String plain = message.getString();
		Lang l = sp.lang();
		long now = System.currentTimeMillis();

		// stack repeats: remove the previous copy and count
		String key = ChatRules.compactKey(plain);
		boolean stackable = Feat.on(Features.CHAT_COMPACT) && key.length() >= 3 && !key.chars().allMatch(c -> c == '-' || c == '▬' || c == '=' || c == ' ');
		if (stackable && key.equals(lastKey) && now - lastAt < COMPACT_WINDOW_MS && lastShown != null) {
			repeat++;
			remove(lastShown);
		} else {
			repeat = 1;
		}
		lastKey = key;
		lastAt = now;

		Component out = message;
		if (Feat.on(Features.CHAT_COPY)) out = withCopy(out, plain, l);
		MutableComponent suffix = Component.empty();
		if (repeat > 1) suffix.append(Component.literal(" (x" + repeat + ")").withStyle(ChatFormatting.GRAY));
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player == null ? null : mc.player.getGameProfile().name();
		if (Feat.on(Features.CHAT_MENTION) && ChatRules.mentions(plain, me)) {
			suffix.append(Component.literal(" ◀").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			Feat.ping(2.0f);
		}
		if (Feat.on(Features.CHAT_KEYWORDS) && !sp.config().keywords.isEmpty()) {
			ChatRules.keyword(plain, sp.config().keywords).ifPresent(k -> {
				suffix.append(Component.literal(" ★ " + k).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
				Feat.ping(1.8f);
			});
		}
		if (Feat.on(Features.CHAT_COORDS)) {
			ChatRules.coords(plain).ifPresent(xyz -> {
				String where = "x " + xyz[0] + " y " + xyz[1] + " z " + xyz[2];
				Style style = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE)
						.withClickEvent(new ClickEvent.RunCommand("/skypilot wp at " + xyz[0] + " " + xyz[1] + " " + xyz[2]))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(l.t("Mark " + where + " (SkyPilot, client only)", where + " に目印を表示（SkyPilot・クライアント内のみ）"))));
				suffix.append(Component.literal(" ")).append(Component.literal(l.t("[Waypoint]", "[ウェイポイント]")).setStyle(style));
			});
		}
		if (!suffix.getSiblings().isEmpty()) out = Component.empty().append(out).append(suffix);

		Component decorated = ClickTranslate.decorate(out, false);
		Component shown = decorated;
		if (Feat.on(Features.CHAT_TIMESTAMPS)) {
			LocalTime t = LocalTime.now();
			Component prefix = Component.literal(String.format("[%02d:%02d] ", t.getHour(), t.getMinute())).withStyle(ChatFormatting.DARK_GRAY);
			shown = Component.empty().append(prefix).append(decorated);
			ClickTranslate.retarget(decorated, shown, prefix);
		}
		lastShown = shown;
		lastOriginal = original;
		return shown;
	}

	/** The server's line before SkyPilot's changes (for the auto translator that runs right after). */
	static Component original(Component shown) {
		return shown == lastShown ? lastOriginal : ClickTranslate.undecorated(shown);
	}

	/** Parts without a click event of their own copy the line when clicked. */
	static Component withCopy(Component message, String plain, Lang l) {
		McText.Flattened flat = McText.flatten(message);
		ClickEvent copy = new ClickEvent.CopyToClipboard(plain);
		HoverEvent hint = new HoverEvent.ShowText(Component.literal(l.t("Click to copy (SkyPilot)", "クリックでコピー（SkyPilot）")).withStyle(ChatFormatting.GRAY));
		List<Style> styles = new ArrayList<>();
		for (Style s : flat.styles()) {
			Style next = s.getClickEvent() == null ? s.withClickEvent(copy) : s;
			if (s.getClickEvent() == null && s.getHoverEvent() == null) next = next.withHoverEvent(hint);
			styles.add(next);
		}
		StyledLine line = flat.line();
		return McText.rebuild(line, styles);
	}

	private static void remove(Component shown) {
		var chat = Minecraft.getInstance().gui.hud.getChat();
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		List<GuiMessage> all = access.skypilot$allMessages();
		for (int i = 0; i < Math.min(all.size(), 5); i++) {
			if (all.get(i).content() == shown) {
				all.remove(i);
				access.skypilot$refreshTrimmedMessages();
				return;
			}
		}
	}

	/** Server lines (original text) for alerts and trackers; called from {@link #allow}. */
	static void onMessage(String plain) {
		Activity.chat(plain);
		Lang l = SkyPilot.get().lang();
		ChatRules.drop(plain).ifPresent(d -> {
			Trackers.drop(d.item());
			if (Feat.on(Features.COMBAT_RARE_DROP)) {
				Feat.title(d.kind(), 0xFFFFAA00, d.item());
				Feat.ping(1.2f);
			}
		});
		ChatRules.slayer(plain).ifPresent(Trackers::slayer);
		ChatRules.abilityReady(plain).ifPresent(name -> {
			if (Feat.on(Features.MINING_ABILITY)) {
				Feat.title(name, 0xFF55FF55, l.t("is ready", "が使えます"));
				Feat.ping(1.6f);
			}
		});
	}

	static void clear() {
		LOG.clear();
		lastKey = null;
		lastShown = null;
		lastOriginal = null;
		repeat = 0;
	}
}
