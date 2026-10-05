package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.SkyBlockContext;
import dev.skypilot.mod.network.Dto;
import dev.skypilot.mod.platform.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import dev.skypilot.mod.translation.StyledLine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs inside a real Minecraft 26.2 client: verifies the mod initialises, reads a SkyBlock-style
 * sidebar, renders its HUD/screens, keeps Text Component styles intact and parses item data.
 */
public final class SkyPilotClientGameTest implements FabricClientGameTest {
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	/** Minimal skin texture (only the texture URL of the Ender Dragon pet head). */
	private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWVjM2ZmNTYzMjkwYjEzZmYzYmNjMzY4OThhZjdlYWE5ODhiNmNjMThkYzI1NDE0N2Y1ODM3NGFmZTliMjFiOSJ9fX0=";

	private static Dto.FlipRow flip(String id, String name, String rarity, Dto.ItemIcon icon, double buy, double sell, double profit, boolean doNotFlip) {
		return new Dto.FlipRow(id, "AH", name.toUpperCase().replace(' ', '_'), name, rarity, List.of(), buy, sell, profit, profit / buy, null, null, doNotFlip ? 0.3 : 0.82,
				doNotFlip ? "LOW" : "HIGH", "MEDIUM", "30m–2h", doNotFlip, doNotFlip ? List.of("Lowest BIN far below recent sales") : List.of(), "/viewauction " + id, icon);
	}

	private static List<GuiMessage> history(Minecraft mc) {
		return ((ChatComponentAccessor) mc.gui.hud.getChat()).skypilot$allMessages();
	}

	/** The chat line (as currently shown) whose text contains {@code needle}, or null. */
	private static Component shown(Minecraft mc, String needle) {
		for (GuiMessage m : history(mc)) {
			if (m.content().getString().contains(needle)) return m.content();
		}
		return null;
	}

	/** The click-to-translate command of that line's button, or null. */
	private static String buttonCommand(Minecraft mc, String needle) {
		Component c = shown(mc, needle);
		if (c == null) return null;
		String[] cmd = new String[1];
		c.visit((style, text) -> {
			if (style.getClickEvent() instanceof ClickEvent.RunCommand(String command) && command.startsWith(ClickTranslate.COMMAND)) cmd[0] = command;
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return cmd[0];
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		check(ctx.computeOnClient(mc -> SkyPilot.get() != null), "SkyPilot initialised");
		check(ctx.computeOnClient(mc -> !SkyPilot.get().config().translation.mode.name().equals("AUTO")), "translation is not on by default");

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender(true, 1200);

			// A sidebar like Hypixel SkyBlock's (title, "⏣ <area>", "Purse: n").
			world.getServer().runOnServer(server -> {
				ServerScoreboard sb = server.getScoreboard();
				Objective o = sb.addObjective("sp_test", ObjectiveCriteria.DUMMY, Component.literal("SKYBLOCK CO-OP").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
						ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE);
				sb.setDisplayObjective(DisplaySlot.SIDEBAR, o);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("⏣ Dwarven Mines"), o).set(3);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Purse: 1,234,567"), o).set(2);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Bits: 500"), o).set(1);
			});
			ctx.waitFor(mc -> SkyPilot.get().context().inSkyBlock(), 200);
			SkyBlockContext seen = ctx.computeOnClient(mc -> SkyPilot.get().context());
			check("Dwarven Mines".equals(seen.area()), "area from sidebar, got: " + seen.area());
			check(seen.purse().isPresent() && seen.purse().getAsLong() == 1_234_567L, "purse from sidebar");

			// HUD is drawn in "SkyBlock" (not linked → shows the link hint).
			ctx.waitTicks(10);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-hud"));

			// Commands are client-side and informational.
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("skypilot status"));
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("skypilot help"));
			ctx.waitTicks(5);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-chat"));

			ctx.setScreen(() -> new SettingsScreen(null));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-settings"));
			ctx.setScreen(() -> new HudEditorScreen(null));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-hud-editor"));
			ctx.setScreen(() -> null);

			// Text Components survive flatten → rebuild (click events and colours included).
			ctx.runOnClient(mc -> {
				Style name = Style.EMPTY.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.SuggestCommand("/msg Steve "));
				Component msg = Component.literal("Party > ").withStyle(ChatFormatting.BLUE).append(Component.literal("Steve").setStyle(name))
						.append(Component.literal(": anyone want to do f7?").withStyle(ChatFormatting.WHITE));
				McText.Flattened flat = McText.flatten(msg);
				check(flat.line().plain().equals(msg.getString()), "flatten keeps text");
				Component rebuilt = McText.rebuild(StyledLine.translated(flat.line().splitAt(15)[0], flat.line().splitAt(15)[1], "f7行く人いる？", "[JA] "), flat.styles());
				check(rebuilt.getString().equals("Party > Steve: [JA] f7行く人いる？"), "rebuilt text: " + rebuilt.getString());
				List<Style> styles = new ArrayList<>();
				rebuilt.visit((style, text) -> {
					if (text.equals("Steve")) styles.add(style);
					return java.util.Optional.empty();
				}, Style.EMPTY);
				check(styles.size() == 1 && styles.get(0).getClickEvent() instanceof ClickEvent.SuggestCommand, "player name keeps its click event");
			});

			// Chat translation end to end: a server system message in Hypixel's party format goes
			// through ChatHook → TranslationService and a translated line is added below it.
			ctx.runOnClient(mc -> {
				SkyPilot sp = SkyPilot.get();
				sp.config().translation.disclosureAccepted = true;
				sp.config().translation.mode = dev.skypilot.mod.core.SkyPilotConfig.TranslationModeSetting.PARTY;
				sp.translator().setDictionary(List.of("Steve"), java.util.Map.of("good luck have fun", "よろしくお願いします"));
			});
			long before = ctx.computeOnClient(mc -> SkyPilot.get().translator().stats().translated());
			world.getServer().runOnServer(server -> {
				Component party = Component.literal("Party > ").withStyle(ChatFormatting.BLUE)
						.append(Component.literal("[MVP+] Steve").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.SuggestCommand("/msg Steve "))))
						.append(Component.literal(": good luck have fun!").withStyle(ChatFormatting.WHITE));
				server.getPlayerList().broadcastSystemMessage(party, false);
			});
			ctx.waitFor(mc -> SkyPilot.get().translator().stats().translated() > before, 100);
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-translation"));
			long requested = ctx.computeOnClient(mc -> SkyPilot.get().translator().stats().requested());
			// A public (non-party) line is not translated in PARTY mode.
			world.getServer().runOnServer(server -> server.getPlayerList().broadcastSystemMessage(Component.literal("Alex: anyone selling a hyperion"), false));
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> SkyPilot.get().translator().stats().requested()) == requested, "PARTY mode ignores public chat");
			ctx.runOnClient(mc -> SkyPilot.get().config().translation.mode = dev.skypilot.mod.core.SkyPilotConfig.TranslationModeSetting.OFF);

			// Click to translate: the line gets a "[訳]" button. Using it the way a chat click does
			// (sendUnattendedCommand → Fabric client command, nothing reaches the server) replaces
			// the line with its translation in place, keeps the server's click events, and
			// "[原文]" switches back.
			ctx.runOnClient(mc -> {
				SkyPilot sp = SkyPilot.get();
				sp.config().translation.clickToTranslate = true;
				sp.config().language = "ja";
				sp.translator().setDictionary(List.of("Steve"), java.util.Map.of("where do i buy a terminator", "ターミネーターはどこで買えますか"));
			});
			world.getServer().runOnServer(server -> {
				Component party = Component.literal("Party > ").withStyle(ChatFormatting.BLUE)
						.append(Component.literal("[MVP+] Steve").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.SuggestCommand("/msg Steve "))))
						.append(Component.literal(": where do I buy a terminator?").withStyle(ChatFormatting.WHITE));
				server.getPlayerList().broadcastSystemMessage(party, false);
			});
			ctx.waitFor(mc -> buttonCommand(mc, "Steve: where do I buy a terminator?") != null, 100);
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-click-button"));
			int historySize = ctx.computeOnClient(mc -> history(mc).size());
			String translateCmd = ctx.computeOnClient(mc -> buttonCommand(mc, "Steve: where do I buy a terminator?"));
			check(translateCmd.startsWith(ClickTranslate.COMMAND), "button runs the client command: " + translateCmd);
			ctx.runOnClient(mc -> mc.player.connection.sendUnattendedCommand(translateCmd.substring(1), null));
			ctx.waitFor(mc -> shown(mc, "Steve: ターミネーターはどこで買えますか") != null, 100);
			ctx.runOnClient(mc -> {
				check(history(mc).size() == historySize, "translation replaces the line instead of adding one");
				check(shown(mc, "Steve: where do I buy a terminator?") == null, "original text is swapped out");
				List<Style> nameStyles = new ArrayList<>();
				shown(mc, "Steve: ターミネーターはどこで買えますか").visit((style, text) -> {
					if (text.contains("Steve")) nameStyles.add(style);
					return java.util.Optional.empty();
				}, Style.EMPTY);
				check(nameStyles.size() == 1 && nameStyles.get(0).getClickEvent() instanceof ClickEvent.SuggestCommand, "server click event kept on the translated line");
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-click-translate"));
			String backCmd = ctx.computeOnClient(mc -> buttonCommand(mc, "Steve: ターミネーターはどこで買えますか"));
			check(backCmd != null && backCmd.equals(translateCmd), "[原文] toggles the same line");
			ctx.runOnClient(mc -> mc.player.connection.sendUnattendedCommand(backCmd.substring(1), null));
			ctx.waitFor(mc -> shown(mc, "Steve: where do I buy a terminator?") != null && shown(mc, "Steve: ターミネーターはどこで買えますか") == null, 100);
			// Lines with nothing to translate get no button.
			world.getServer().runOnServer(server -> server.getPlayerList().broadcastSystemMessage(Component.literal("Party > Alex: gg"), false));
			ctx.waitFor(mc -> shown(mc, "Alex: gg") != null, 100);
			check(ctx.computeOnClient(mc -> buttonCommand(mc, "Alex: gg")) == null, "no button on lines without text to translate");
			ctx.runOnClient(mc -> {
				SkyPilot sp = SkyPilot.get();
				sp.config().translation.clickToTranslate = false;
				sp.config().translation.disclosureAccepted = false;
				sp.config().language = "auto";
			});

			// In-game GUI: item icons, the SkyPilot menu, the AH panel and a flip alert, fed with
			// synthetic data (the screens only display what the SkyPilot server sends).
			Dto.ItemIcon head = new Dto.ItemIcon("minecraft:player_head", HEAD_TEXTURE, null, false);
			Dto.ItemIcon leggings = new Dto.ItemIcon("minecraft:leather_leggings", null, 0x0000FF, false);
			Dto.ItemIcon book = new Dto.ItemIcon("minecraft:enchanted_book", null, null, true);
			Dto.ItemIcon unknown = new Dto.ItemIcon("minecraft:not_a_real_item", null, null, true);
			ctx.runOnClient(mc -> {
				SkyPilot.get().config().language = "ja";
				ItemStack h = ItemIcons.build(head);
				check(h.is(Items.PLAYER_HEAD) && h.get(DataComponents.PROFILE) != null, "head icon carries its texture");
				check(ItemIcons.build(leggings).get(DataComponents.DYED_COLOR).rgb() == 0x0000FF, "leather icon is dyed");
				check(Boolean.TRUE.equals(ItemIcons.build(book).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "glint icon");
				check(ItemIcons.build(unknown).is(Items.PAPER), "unknown item ids fall back to paper");
				check(MarketData.AH.equals(ContainerPanel.kindOf("Auctions Browser")) && MarketData.BAZAAR.equals(ContainerPanel.kindOf("Bazaar ➜ Farming"))
						&& ContainerPanel.kindOf("Large Chest") == null, "window titles map to flip lists");

				List<Dto.FlipRow> rows = List.of(
						flip("a1", "Ender Dragon", "LEGENDARY", head, 5.2e8, 5.9e8, 4.1e7, false),
						flip("a2", "Lapis Armor Leggings", "RARE", leggings, 2.5e6, 7.9e6, 4.6e6, false),
						flip("a3", "Enchanted Book", "UNCOMMON", book, 1.2e6, 1.6e6, 3.2e5, false),
						flip("a4", "Suspicious Item", "EPIC", unknown, 1.0e6, 9.0e6, 7.5e6, true));
				MarketData.set(MarketData.AH, new Dto.MarketPanel("AH", "オークションのフリップ", rows, 14, false, "情報表示のみです。"));
				MenuScreen.setPlan(new Dto.Plan("Steve · Coconut", System.currentTimeMillis() - 600_000,
						List.of(new Dto.PlanGoal("Enderman Slayer 7", 0.12, 1.6e7, 15.0, List.of(
								new Dto.PlanStep("Magical Power 300", "Voidgloom Seraph T2の推奨値", "IN_PROGRESS", 0.6, 6.0e6, 0.6),
								new Dto.PlanStep("Enderman Slayer 7", "T1×1 → T2×1 → T3×1 → T4×200", "NOT_STARTED", 0, 1.0e7, 14.0)))),
						List.of(new Dto.PlanAction("所持中のアクセサリー1個をバッグに入れる", "Accessories", "無料でMPが上がります。", List.of("Book of Progression (Ender Chest) +3 MP"),
								List.of(new Dto.HudLine("MP", "181 → 184")), 0.95, null, 0.05, List.of()),
								new Dto.PlanAction("Voidgloom Seraph用の武器を購入", "Slayer", "武器ティアが周回できるTierを制限しています。", List.of(),
										List.of(), 0.6, 1.4e6, 0.1, List.of(new Dto.ItemRef("VOIDEDGE_KATANA", "Voidedge Katana", 1.4e6, book)))),
						List.of(), null));
				MenuScreen.setReport("money", new Dto.Report("money", "金策", List.of(new Dto.Metric("流動資金", null, null, 2162.0, "coins", null)),
						List.of(new Dto.ReportSection("best", "あなたに最適な金策", null, List.of(), null, List.of("Garden farming 1.6M〜2.1M/時"), List.of())), List.of()));
			});
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.NOW));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-now"));
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.AH));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-ah"));
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.GUIDE));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-guide"));
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.ALERTS));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-alerts"));

			// Panel beside a window titled like Hypixel's Auction House (only drawn, never clicks).
			ctx.runOnClient(mc -> SkyPilot.get().config().market.containerPanel = true);
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				return new ContainerScreen(ChestMenu.sixRows(1, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Auction House"));
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-ah-panel"));
			ctx.setScreen(() -> null);

			// Flip alert: toast + chat line whose buttons open the menu or only fill the chat box.
			ctx.runOnClient(mc -> FlipAlerts.show(MarketData.get(MarketData.AH).rows().get(0)));
			ctx.waitTicks(20);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-flip-alert"));
			ctx.runOnClient(mc -> {
				Component line = shown(mc, "Ender Dragon");
				check(line != null, "alert line in chat");
				check(line.getString().contains("Ender Dragon"), "alert line in chat: " + line.getString());
				List<ClickEvent> events = new ArrayList<>();
				line.visit((style, text) -> {
					if (style.getClickEvent() != null) events.add(style.getClickEvent());
					return java.util.Optional.empty();
				}, Style.EMPTY);
				check(events.stream().anyMatch(e -> e instanceof ClickEvent.RunCommand(String c) && c.equals("/skypilot menu ah")), "[詳細] opens the menu");
				check(events.stream().anyMatch(e -> e instanceof ClickEvent.SuggestCommand(String c) && c.equals("/viewauction a1")), "[入力欄へ] only suggests the command");
				check(events.stream().noneMatch(e -> e instanceof ClickEvent.RunCommand(String c) && c.startsWith("/viewauction")), "never runs /viewauction");
				SkyPilot.get().config().market.containerPanel = false;
				SkyPilot.get().config().language = "auto";
			});

			// SkyBlock item id from the 26.2 custom_data component.
			ctx.runOnClient(mc -> {
				ItemStack stack = new ItemStack(Items.GOLDEN_SWORD);
				CompoundTag tag = new CompoundTag();
				tag.putString("id", "HYPERION");
				tag.putString("uuid", "0f0a7e5e-0000-4000-8000-000000000000");
				CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
				PriceCheck.Item item = PriceCheck.item(stack);
				check(item != null && item.skyblockId().equals("HYPERION"), "skyblock id from custom_data");
				check(PriceCheck.json(item.tag()).getAsJsonObject().get("id").getAsString().equals("HYPERION"), "custom_data converts to JSON");
				check(PriceCheck.item(new ItemStack(Items.DIRT)) == null, "vanilla items are ignored");
			});
		}
	}
}
