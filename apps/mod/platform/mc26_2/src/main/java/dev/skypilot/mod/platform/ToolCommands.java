package dev.skypilot.mod.platform;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.skypilot.mod.core.Calc;
import dev.skypilot.mod.core.Durations;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.Text;
import dev.skypilot.mod.network.Dto;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Commands of the optional tools: features, calc, remind, watch, wp, en, drops. Client-side
 * only; nothing is sent to the game server ("en" only fills the chat box).
 */
final class ToolCommands {
	private ToolCommands() {
	}

	static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(literal("features").executes(c -> open())
				.then(literal("recommended").executes(c -> recommended())));
		root.then(literal("feature").then(argument("id", StringArgumentType.word())
				.suggests((c, b) -> {
					for (Features.Feature f : Features.all()) b.suggest(f.id());
					return b.buildFuture();
				})
				.then(literal("on").executes(c -> set(StringArgumentType.getString(c, "id"), true)))
				.then(literal("off").executes(c -> set(StringArgumentType.getString(c, "id"), false)))));
		root.then(literal("calc").then(argument("expression", StringArgumentType.greedyString()).executes(c -> calc(StringArgumentType.getString(c, "expression")))));
		root.then(literal("remind")
				.then(literal("list").executes(c -> remindList()))
				.then(literal("clear").executes(c -> {
					Timers.REMINDERS.clear();
					info("Reminders cleared.", "リマインダーを消去しました。");
					return 1;
				}))
				.then(argument("in", StringArgumentType.word()).then(argument("text", StringArgumentType.greedyString())
						.executes(c -> remind(StringArgumentType.getString(c, "in"), StringArgumentType.getString(c, "text"))))));
		root.then(literal("watch")
				.executes(c -> watchList())
				.then(literal("list").executes(c -> watchList()))
				.then(literal("remove").then(argument("item", StringArgumentType.word()).executes(c -> watchRemove(StringArgumentType.getString(c, "item")))))
				.then(argument("item", StringArgumentType.word())
						.then(literal("below").then(argument("price", StringArgumentType.word()).executes(c -> watchAdd(StringArgumentType.getString(c, "item"), true, StringArgumentType.getString(c, "price")))))
						.then(literal("above").then(argument("price", StringArgumentType.word()).executes(c -> watchAdd(StringArgumentType.getString(c, "item"), false, StringArgumentType.getString(c, "price")))))));
		root.then(literal("wp")
				.executes(c -> wpList())
				.then(literal("list").executes(c -> wpList()))
				.then(literal("add").then(argument("name", StringArgumentType.greedyString()).executes(c -> wpAdd(StringArgumentType.getString(c, "name")))))
				.then(literal("show").then(argument("name", StringArgumentType.greedyString()).executes(c -> wpShow(StringArgumentType.getString(c, "name")))))
				.then(literal("remove").then(argument("name", StringArgumentType.greedyString()).executes(c -> wpRemove(StringArgumentType.getString(c, "name")))))
				.then(literal("hide").executes(c -> {
					Waypoints.clear();
					return 1;
				}))
				// target of the [Waypoint] button on chat coordinates
				.then(literal("at").then(argument("x", DoubleArgumentType.doubleArg()).then(argument("y", DoubleArgumentType.doubleArg()).then(argument("z", DoubleArgumentType.doubleArg())
						.executes(c -> wpAt(DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z"))))))));
		root.then(literal("en").then(argument("text", StringArgumentType.greedyString()).executes(c -> toEnglish(StringArgumentType.getString(c, "text")))));
		root.then(literal("drops").executes(c -> drops()).then(literal("reset").executes(c -> {
			Trackers.reset();
			info("Trackers reset.", "トラッカーをリセットしました。");
			return 1;
		})));
	}

	private static Lang lang() {
		return SkyPilot.get().lang();
	}

	private static void info(String en, String ja) {
		McText.info(lang().t(en, ja));
	}

	private static boolean require(Features.Feature f) {
		if (Feat.on(f)) return true;
		McText.chat(McText.prefix().append(Component.literal(lang().t("\"" + f.en() + "\" is off. ", "「" + f.ja() + "」はオフです。")).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(lang().t("[Turn on]", "[オンにする]")).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/skypilot feature " + f.id() + " on")))));
		return false;
	}

	private static int open() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new SettingsScreen(null, SettingsScreen.Page.FEATURES)));
		return 1;
	}

	private static int recommended() {
		SkyPilot sp = SkyPilot.get();
		int n = Features.applyRecommended(sp.config());
		sp.saveConfig();
		info(n + " recommended features turned on. /skypilot features to review them.", "おすすめ機能を " + n + " 個オンにしました。/skypilot features で確認できます。");
		return 1;
	}

	static int set(String id, boolean on) {
		SkyPilot sp = SkyPilot.get();
		Features.Feature f = Features.byId(id);
		if (f == null) {
			McText.error(lang().t("Unknown feature: ", "不明な機能: ") + id);
			return 0;
		}
		if (on && f.consent() && !sp.config().translation.disclosureAccepted) {
			Commands.showDisclosure();
			return 0;
		}
		Features.set(sp.config(), f, on);
		sp.saveConfig();
		McText.info(f.name(lang()) + ": " + (on ? lang().t("ON", "オン") : lang().t("OFF", "オフ")));
		return 1;
	}

	private static int calc(String expression) {
		if (!require(Features.TOOLS_CALC)) return 0;
		try {
			double v = Calc.eval(expression);
			String plain = Calc.plain(v);
			McText.chat(McText.prefix().append(Component.literal(expression.strip() + " = ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(plain).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent.CopyToClipboard(plain.replace(",", "")))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(lang().t("Click to copy", "クリックでコピー"))))))
					.append(Component.literal("  (" + Text.coins(v) + ")").withStyle(ChatFormatting.DARK_GRAY)));
			return 1;
		} catch (IllegalArgumentException e) {
			McText.error(lang().t("Cannot calculate: ", "計算できません: ") + e.getMessage());
			return 0;
		}
	}

	private static int remind(String in, String text) {
		if (!require(Features.TOOLS_REMINDERS)) return 0;
		try {
			long ms = Durations.parse(in);
			Timers.remind(ms, text.length() > 80 ? text.substring(0, 80) : text);
			info("Reminder in " + InfoHud.eta(ms) + ": " + text, InfoHud.eta(ms) + " 後に通知します: " + text);
			return 1;
		} catch (IllegalArgumentException e) {
			McText.error(lang().t("Duration like 30m, 1h30m or 90s (at most 7 days).", "時間は 30m・1h30m・90s のように指定してください（最大7日）。"));
			return 0;
		}
	}

	private static int remindList() {
		if (Timers.REMINDERS.isEmpty()) {
			info("No reminders.", "リマインダーはありません。");
			return 1;
		}
		long now = System.currentTimeMillis();
		for (Timers.Reminder r : Timers.REMINDERS) McText.info("⏰ " + InfoHud.eta(r.dueAt() - now) + "  " + r.text());
		return 1;
	}

	private static int watchAdd(String rawId, boolean below, String rawPrice) {
		if (!require(Features.MARKET_WATCHLIST)) return 0;
		SkyPilot sp = SkyPilot.get();
		String id = rawId.strip().toUpperCase(Locale.ROOT);
		if (!id.matches("[A-Z0-9_:;]{1,64}")) {
			McText.error(lang().t("Not a valid item id: ", "アイテムIDが正しくありません: ") + id);
			return 0;
		}
		double price;
		try {
			price = Calc.eval(rawPrice);
		} catch (IllegalArgumentException e) {
			price = -1;
		}
		if (!(price > 0)) {
			McText.error(lang().t("Price like 1.5m or 250k.", "価格は 1.5m や 250k のように指定してください。"));
			return 0;
		}
		SkyPilotConfig c = sp.config();
		c.watchlist.removeIf(w -> w.itemId.equals(id) && w.below == below);
		if (c.watchlist.size() >= SkyPilotConfig.MAX_WATCHES) {
			McText.error(lang().t("At most " + SkyPilotConfig.MAX_WATCHES + " watches.", "ウォッチは最大 " + SkyPilotConfig.MAX_WATCHES + " 件です。"));
			return 0;
		}
		c.watchlist.add(new SkyPilotConfig.Watch(id, below, price));
		sp.saveConfig();
		info("Watching " + id + (below ? " below " : " above ") + Text.coins(price) + ".", id + " が " + Text.coins(price) + (below ? " 以下" : " 以上") + "になったら通知します。");
		return 1;
	}

	private static int watchRemove(String rawId) {
		SkyPilot sp = SkyPilot.get();
		String id = rawId.strip().toUpperCase(Locale.ROOT);
		boolean removed = sp.config().watchlist.removeIf(w -> w.itemId.equals(id));
		sp.saveConfig();
		info(removed ? "Removed " + id + "." : id + " was not watched.", removed ? id + " を削除しました。" : id + " はウォッチしていません。");
		return 1;
	}

	private static int watchList() {
		var list = SkyPilot.get().config().watchlist;
		if (list.isEmpty()) {
			info("No watches. /skypilot watch <ITEM_ID> below|above <price>", "ウォッチはありません。/skypilot watch <アイテムID> below|above <価格>");
			return 1;
		}
		for (SkyPilotConfig.Watch w : list) McText.info(w.itemId + (w.below ? " ≤ " : " ≥ ") + Text.coins(w.price));
		return 1;
	}

	private static int wpAdd(String name) {
		if (!require(Features.TOOLS_WAYPOINTS)) return 0;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return 0;
		SkyPilot sp = SkyPilot.get();
		String n = name.strip();
		if (n.isEmpty() || n.length() > 32) {
			McText.error(lang().t("Name: 1 to 32 characters.", "名前は1〜32文字です。"));
			return 0;
		}
		String island = Waypoints.currentIsland();
		if (island == null || island.isEmpty()) island = "*";
		String isl = island;
		sp.config().waypoints.removeIf(w -> w.name.equalsIgnoreCase(n) && w.island.equals(isl));
		if (sp.config().waypoints.size() >= SkyPilotConfig.MAX_WAYPOINTS) {
			McText.error(lang().t("At most " + SkyPilotConfig.MAX_WAYPOINTS + " waypoints.", "ウェイポイントは最大 " + SkyPilotConfig.MAX_WAYPOINTS + " 件です。"));
			return 0;
		}
		sp.config().waypoints.add(new SkyPilotConfig.SavedWaypoint(n, island, Math.floor(mc.player.getX()) + 0.5, Math.floor(mc.player.getY()), Math.floor(mc.player.getZ()) + 0.5));
		sp.saveConfig();
		info("Saved waypoint \"" + n + "\".", "ウェイポイント「" + n + "」を保存しました。");
		return 1;
	}

	private static SkyPilotConfig.SavedWaypoint findWp(String name) {
		String island = Waypoints.currentIsland();
		SkyPilotConfig.SavedWaypoint any = null;
		for (SkyPilotConfig.SavedWaypoint w : SkyPilot.get().config().waypoints) {
			if (!w.name.equalsIgnoreCase(name.strip())) continue;
			if (w.island.equals(island)) return w;
			any = w;
		}
		return any;
	}

	private static int wpShow(String name) {
		if (!require(Features.TOOLS_WAYPOINTS)) return 0;
		SkyPilotConfig.SavedWaypoint w = findWp(name);
		if (w == null) {
			McText.error(lang().t("No waypoint named ", "ウェイポイントがありません: ") + name);
			return 0;
		}
		Waypoints.set(new Dto.Waypoint(w.island, w.name, w.x, w.y, w.z));
		return 1;
	}

	private static int wpRemove(String name) {
		SkyPilot sp = SkyPilot.get();
		SkyPilotConfig.SavedWaypoint w = findWp(name);
		if (w == null) {
			McText.error(lang().t("No waypoint named ", "ウェイポイントがありません: ") + name);
			return 0;
		}
		sp.config().waypoints.remove(w);
		sp.saveConfig();
		info("Removed \"" + w.name + "\".", "「" + w.name + "」を削除しました。");
		return 1;
	}

	private static int wpList() {
		var list = SkyPilot.get().config().waypoints;
		if (list.isEmpty()) {
			info("No waypoints. /skypilot wp add <name> saves where you stand.", "ウェイポイントはありません。/skypilot wp add <名前> で現在地を保存します。");
			return 1;
		}
		for (SkyPilotConfig.SavedWaypoint w : list) {
			McText.chat(Component.literal("  " + w.name).setStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withClickEvent(new ClickEvent.RunCommand("/skypilot wp show " + w.name)))
					.append(Component.literal(String.format(Locale.ROOT, "  %s  %.0f %.0f %.0f", w.island.equals("*") ? "" : w.island, w.x, w.y, w.z)).withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private static int wpAt(double x, double y, double z) {
		String island = Waypoints.currentIsland();
		Waypoints.set(new Dto.Waypoint(island == null || island.isEmpty() ? "*" : island, String.format(Locale.ROOT, "%.0f %.0f %.0f", x, y, z), x, y, z));
		info("Marked " + String.format(Locale.ROOT, "%.0f %.0f %.0f", x, y, z) + ". /skypilot wp hide removes it.",
				String.format(Locale.ROOT, "%.0f %.0f %.0f", x, y, z) + " に目印を表示しました。/skypilot wp hide で消せます。");
		return 1;
	}

	/** Japanese → English into the chat box; the player reads it and sends it themselves. */
	private static int toEnglish(String text) {
		if (!require(Features.CHAT_REPLY_TRANSLATE)) return 0;
		SkyPilot sp = SkyPilot.get();
		if (!sp.config().translation.disclosureAccepted) {
			Commands.showDisclosure();
			return 0;
		}
		if (sp.api() == null || !sp.linked()) {
			McText.error(lang().t("Link with SkyPilot first: /skypilot link", "先にSkyPilotと連携してください: /skypilot link"));
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		sp.api().translate(text, "ja", "en", null).whenComplete((r, err) -> mc.execute(() -> {
			if (r == null || r.unchanged()) {
				McText.error(lang().t("Could not translate (is a translation provider set? Settings → Translate).", "翻訳できませんでした（翻訳プロバイダの設定: 設定 → 翻訳）。"));
				return;
			}
			mc.gui.setScreen(new ChatScreen(r.translated(), false));
		}));
		return 1;
	}

	private static int drops() {
		if (Trackers.dropTotal == 0 && Trackers.slayerKills == 0) {
			info("No drops tracked yet (turn on Drop tracker / Slayer tracker).", "まだ記録がありません（ドロップ/スレイヤートラッカーをオン）。");
			return 1;
		}
		Lang l = lang();
		McText.info(l.t("Rare drops this session: ", "このセッションのレアドロップ: ") + Trackers.dropTotal);
		Trackers.DROPS.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.forEach(e -> McText.chat(Component.literal("  " + e.getKey() + " ×" + e.getValue()).withStyle(ChatFormatting.GRAY)));
		if (Trackers.slayerKills > 0) McText.info(l.t("Slayer bosses: ", "スレイヤーボス: ") + Trackers.slayerKills + l.t(", failed ", "、失敗 ") + Trackers.slayerFails);
		return 1;
	}
}
