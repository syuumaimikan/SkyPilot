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

	/** A stack with a SkyBlock id in custom_data, like Hypixel sends. */
	private static ItemStack skyblockItem(net.minecraft.world.item.Item item, String id, int count) {
		ItemStack s = new ItemStack(item, count);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		CustomData.set(DataComponents.CUSTOM_DATA, s, tag);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(id.equals("HYPERION") ? "Hyperion" : id.equals("ENCHANTED_DIAMOND") ? "Enchanted Diamond" : "Lapis Armor Leggings"));
		return s;
	}

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

			ctx.setScreen(() -> new SettingsScreen(null, SettingsScreen.Page.GENERAL));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-settings"));
			// Translation page with server settings (a DeepL key saved); a typed key is drawn masked.
			ctx.setScreen(() -> {
				SettingsScreen s = new SettingsScreen(null, SettingsScreen.Page.TRANSLATION);
				s.showRemote(new Dto.TranslationSettings("deepl", new Dto.KeyInfo(true, "…f3a9"), new Dto.KeyInfo(false, null), "claude",
						List.of("server", "deepl", "claude", "none")));
				return s;
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				var box = mc.gui.screen().children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox).map(w -> (net.minecraft.client.gui.components.EditBox) w)
						.findFirst().orElse(null);
				check(box != null, "key field on the translation page");
				box.setValue("abcdef0123456789-test:fx");
				check(SettingsScreen.plausibleKey(box.getValue()) && !SettingsScreen.plausibleKey("short") && !SettingsScreen.plausibleKey("has spaces in it 12345"),
						"key shape check");
			});
			ctx.waitTicks(1);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-settings-translation"));
			ctx.setScreen(() -> new SettingsScreen(null, SettingsScreen.Page.MARKET));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-settings-market"));
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

			// Item browser (right of the inventory), item page with a crafting grid, and chest value.
			Dto.ItemIcon sword = new Dto.ItemIcon("minecraft:iron_sword", null, null, false);
			Dto.ItemIcon eye = new Dto.ItemIcon("minecraft:player_head", HEAD_TEXTURE, null, false);
			ctx.runOnClient(mc -> {
				SkyPilot.get().config().language = "ja";
				List<Dto.ItemEntry> entries = new ArrayList<>();
				entries.add(new Dto.ItemEntry("HYPERION", "Hyperion", "LEGENDARY", sword, 4.7e8));
				entries.add(new Dto.ItemEntry("PET:ENDER_DRAGON:LEGENDARY", "Ender Dragon", "LEGENDARY", head, 5.2e8));
				entries.add(new Dto.ItemEntry("LAPIS_ARMOR_LEGGINGS", "Lapis Armor Leggings", "UNCOMMON", leggings, 1.0e5));
				entries.add(new Dto.ItemEntry("ENCHANTMENT_SHARPNESS_6", "Sharpness VI", "COMMON", book, 3.1e6));
				for (int i = 0; i < 40; i++) entries.add(new Dto.ItemEntry("X" + i, "Item " + i, "RARE", i % 2 == 0 ? book : sword, 1000.0 * i));
				ItemBrowser.setData(new Dto.ItemPage(entries.size(), 0, entries), "");
			});
			ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-item-browser"));
			Dto.ItemStackInfo laser = new Dto.ItemStackInfo("GIANT_FRAGMENT_LASER", "L.A.S.R.'s Eye", 8, eye, 5.6e7);
			Dto.ItemStackInfo blade = new Dto.ItemStackInfo("NECRON_BLADE", "Necron's Blade (Unrefined)", 1, sword, 6.4e7);
			Dto.ItemStackInfo one = new Dto.ItemStackInfo("GIANT_FRAGMENT_LASER", "L.A.S.R.'s Eye", 1, eye, 5.6e7);
			Dto.ItemDetail hyperion = new Dto.ItemDetail(new Dto.ItemEntry("HYPERION", "Hyperion", "LEGENDARY", sword, 4.7e8), "SWORD",
					List.of("§7Gear Score: §d615", "§7Damage: §c+260", "§6Ability: Wither Impact §e§lRIGHT CLICK"), "Sales", 4.85e8, 6.5, null, null, 1.0e5, 5.1e8,
					List.of(new Dto.Recipe("CRAFTING", new Dto.ItemStackInfo("HYPERION", "Hyperion", 1, sword, 4.85e8), List.of(laser, blade),
							java.util.Arrays.asList(one, one, one, one, blade, one, one, one, one), null, null, null, 5.12e8)),
					List.of(), List.of(), "https://hypixelskyblock.minecraft.wiki/w/Hyperion");
			ctx.setScreen(() -> {
				ItemDetailScreen s = new ItemDetailScreen(null, "HYPERION");
				s.setDetail(hyperion);
				return s;
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-item-recipe"));

			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.sixRows(2, mc.player.getInventory());
				menu.getSlot(0).set(skyblockItem(Items.IRON_SWORD, "HYPERION", 1));
				menu.getSlot(1).set(skyblockItem(Items.DIAMOND, "ENCHANTED_DIAMOND", 64));
				menu.getSlot(9).set(skyblockItem(Items.DIAMOND, "ENCHANTED_DIAMOND", 32));
				menu.getSlot(20).set(skyblockItem(Items.LEATHER_LEGGINGS, "LAPIS_ARMOR_LEGGINGS", 1));
				menu.getSlot(30).set(new ItemStack(Items.GLASS_PANE));
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Large Chest"));
			});
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				check(ChestValue.isStorage("Large Chest") && ChestValue.isStorage("Jumbo Backpack (Slot #3)") && ChestValue.isStorage("Ender Chest (1/9)")
						&& !ChestValue.isStorage("Auction House") && !ChestValue.isStorage("SkyBlock Menu"), "storage windows are recognised");
				var screen = (ContainerScreen) mc.gui.screen();
				ChestValue cv = ScreenOverlays.chestOf(screen);
				check(cv != null, "chest value panel attached");
				List<net.minecraft.world.inventory.Slot> slots = ChestValue.slotsOf(screen).stream().filter(s -> PriceCheck.item(s.getItem()) != null).toList();
				check(slots.size() == 4, "4 SkyBlock items in the chest (the glass pane has no SkyBlock id)");
				cv.apply(slots, new Dto.Values(List.of(new Dto.Value("HYPERION", "Hyperion", 4.7e8, 0.8), new Dto.Value("ENCHANTED_DIAMOND", "Enchanted Diamond", 7.9e4, 0.95),
						new Dto.Value("ENCHANTED_DIAMOND", "Enchanted Diamond", 3.9e4, 0.95), new Dto.Value("LAPIS_ARMOR_LEGGINGS", "Lapis Armor Leggings", 1.0e5, 0.3)), 4.70218e8));
				List<ChestValue.Group> groups = cv.groups();
				check(groups.size() == 3 && groups.get(0).name.contains("Hyperion"), "grouped and sorted by value");
				ChestValue.Group diamonds = groups.stream().filter(gr -> gr.name.contains("Diamond")).findFirst().orElseThrow();
				check(diamonds.count == 96 && diamonds.slots.size() == 2 && Math.abs(diamonds.value - 1.18e5) < 1, "same item in two slots is one row");
				cv.highlight(diamonds.key);
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-chest-value"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				ItemBrowser.setData(null, "");
				SkyPilot.get().config().language = "auto";
			});

			// Guides: menu tabs, a waypoint with guide line in the world, and the farm build guide.
			Dto.Guide[] guideRef = new Dto.Guide[1];
			ctx.runOnClient(mc -> {
				SkyPilot.get().config().language = "ja";
				net.minecraft.core.BlockPos p = mc.player.blockPosition();
				Dto.Waypoint rhys = new Dto.Waypoint("HUB", "Rhys", p.getX() + 6.5, p.getY(), p.getZ() + 14.5);
				Dto.Guide g = new Dto.Guide("dwarven_mines", "HUB", "ドワーフ鉱山とHeart of the Mountain", "https://hypixelskyblock.minecraft.wiki/w/Dwarven_Mines", List.of(
						new Dto.GuideStep("採掘レベル12にする", "ドワーフ鉱山には採掘レベル12が必要です。", true, null),
						new Dto.GuideStep("Rhysのクエスト「There are Dwarves?」", "Rhysにエンチャント鉱石を15個渡します。", false, rhys),
						new Dto.GuideStep("リフトで降りる", "Lift Operatorに話しかけます。", null, new Dto.Waypoint("DEEP_CAVERNS", "Lift Operator", 45, 67, 15))));
				guideRef[0] = g;
				MenuScreen.setGuides(new Dto.Guides(List.of(g), "Waypoints: SkyHanni-REPO (MIT). Facts: Hypixel SkyBlock Wiki."),
						new Dto.FarmSummary[]{new Dto.FarmSummary("cane_rows", "Water-channel rows (sugar cane)", List.of("SUGAR_CANE"), "Sand rows on both sides of each water channel.", List.of(96, 3, 96))});
				MenuScreen.setGear(new Dto.Gear(List.of(new Dto.GearSection("Voidgloom Seraph用の装備", List.of(
						new Dto.GearItem("VOIDEDGE_KATANA", "Voidedge Katana", sword, "weapon", "REQUIRED", false, 1.4e6, null),
						new Dto.GearItem("ATOMSPLIT_KATANA", "Atomsplit Katana", sword, "weapon", "RECOMMENDED", false, 2.3e7, null),
						new Dto.GearItem("FINAL_DESTINATION_CHESTPLATE", "Final Destination Chestplate", leggings, "armor", "LUXURY", true, 4.1e7, null)))), null));
				Waypoints.setIslandOverride("HUB");
			});
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.PROGRESS));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-progress"));
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.GEAR));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-gear"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				Waypoints.follow(guideRef[0], 1);
				check(Waypoints.target() != null && Waypoints.target().name().equals("Rhys"), "following the Rhys step");
				check(Waypoints.onTargetIsland(), "waypoint is on the current island");
				check("↑".equals(Waypoints.arrow(0f, 0, 10)) && "→".equals(Waypoints.arrow(0f, -10, 0)), "direction arrows (yaw 0 = south, west is to the right)");
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-waypoint"));
			ctx.runOnClient(mc -> {
				Waypoints.clear();
				Waypoints.setIslandOverride(null);
				net.minecraft.core.BlockPos p = mc.player.blockPosition().offset(2, -1, 3);
				List<Dto.FarmBlock> blocks = new ArrayList<>();
				for (int x = 0; x < 6; x++) {
					for (int z = 0; z < 6; z++) blocks.add(new Dto.FarmBlock(x, 0, z, x == 2 ? "water" : "sand"));
				}
				for (int x = 0; x < 6; x++) {
					for (int z = 0; z < 6; z++) if (x != 2) blocks.add(new Dto.FarmBlock(x, 1, z, "sugar_cane"));
				}
				Dto.FarmDesign d = new Dto.FarmDesign(new Dto.FarmSummary("test", "テスト用サトウキビ", List.of("SUGAR_CANE"), "", List.of(6, 2, 6)), blocks, java.util.Map.of(), List.of(0, 1), List.of(), "",
						List.of(), List.of(), List.of(), 1, null);
				FarmBuilder.start(d, p, net.minecraft.core.Direction.SOUTH);
			});
			ctx.waitTicks(10);
			int farmBefore = ctx.computeOnClient(mc -> FarmBuilder.remaining());
			check(farmBefore == 36, "whole first layer is missing at first: " + farmBefore);
			// place two sand blocks of the layer on the server: the guide notices
			world.getServer().runOnServer(server -> {
				var level = server.overworld();
				var pl = server.getPlayerList().getPlayers().get(0);
				net.minecraft.core.BlockPos o = pl.blockPosition().offset(2, -1, 3);
				level.setBlockAndUpdate(o, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
				level.setBlockAndUpdate(o.offset(0, 0, 1), net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
			});
			ctx.waitTicks(15);
			int farmAfter = ctx.computeOnClient(mc -> FarmBuilder.remaining());
			check(farmAfter == 34, "placed blocks are no longer outlined: " + farmAfter);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-farm-guide"));
			ctx.runOnClient(mc -> {
				FarmBuilder.stop();
				SkyPilot.get().config().language = "auto";
			});

			// ---------------------------------------------------------------- feature switches
			ctx.setScreen(() -> new SettingsScreen(null, SettingsScreen.Page.FEATURES));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-settings-features"));
			// clicking a row in the list switches that feature
			ctx.runOnClient(mc -> {
				SettingsScreen s = (SettingsScreen) mc.gui.screen();
				s.filterFeatures("timestamps", null);
				check(!SkyPilot.get().config().on(dev.skypilot.mod.core.Features.CHAT_TIMESTAMPS), "timestamps start off");
				s.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(s.width / 2.0, 74 + 13 + 10, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
				check(SkyPilot.get().config().on(dev.skypilot.mod.core.Features.CHAT_TIMESTAMPS), "clicking the row turned timestamps on");
				s.filterFeatures("", null);
			});
			ctx.setScreen(() -> null);

			// turn on a set of features and feed them what Hypixel would show
			ctx.runOnClient(mc -> {
				var c = SkyPilot.get().config();
				for (dev.skypilot.mod.core.Features.Feature f : List.of(dev.skypilot.mod.core.Features.HUD_COORDS, dev.skypilot.mod.core.Features.HUD_FPS,
						dev.skypilot.mod.core.Features.HUD_CLOCK, dev.skypilot.mod.core.Features.HUD_EVENTS, dev.skypilot.mod.core.Features.HUD_STATS,
						dev.skypilot.mod.core.Features.HUD_SKILL, dev.skypilot.mod.core.Features.FARMING_BPS, dev.skypilot.mod.core.Features.COMBAT_DROP_TRACKER,
						dev.skypilot.mod.core.Features.COMBAT_SLAYER_TRACKER, dev.skypilot.mod.core.Features.COMBAT_RARE_DROP, dev.skypilot.mod.core.Features.CHAT_COMPACT,
						dev.skypilot.mod.core.Features.CHAT_HIDE_LOBBY, dev.skypilot.mod.core.Features.CHAT_COORDS, dev.skypilot.mod.core.Features.CHAT_COPY,
						dev.skypilot.mod.core.Features.TOOLS_CALC, dev.skypilot.mod.core.Features.TOOLS_REMINDERS, dev.skypilot.mod.core.Features.TOOLS_WAYPOINTS,
						dev.skypilot.mod.core.Features.INV_RARITY, dev.skypilot.mod.core.Features.INV_NUMBERS, dev.skypilot.mod.core.Features.INV_SEARCH_HIGHLIGHT,
						dev.skypilot.mod.core.Features.TOOLTIP_ID, dev.skypilot.mod.core.Features.TOOLTIP_HIDE_FILLER, dev.skypilot.mod.core.Features.FARMING_VISITOR)) {
					dev.skypilot.mod.core.Features.set(c, f, true);
				}
				long now = System.currentTimeMillis();
				Timers.setCalendar(new Dto.Calendar(1560275700000L, 1200000L, 31,
						List.of("Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter",
								"Late Winter"),
						List.of(new Dto.CalendarEvent("dark_auction", "Dark Auction", 0, 0, false, 3, 0, 0),
								new Dto.CalendarEvent("jacobs_contest", "Jacob's Farming Contest", 0, 0, false, 3, 1, 1)),
						now));
				check(Timers.upcoming(now).size() == 2, "recurring events computed locally");
				Trackers.actionBar("§c1,200/1,500❤     §a300§a❈ Defense     §b400/620✎ Mana");
				Trackers.actionBar("§3+12.5 Farming (12,345/50,000)");
				Trackers.simulateBreaks(30);
				Timers.remind(600_000, "Check the forge");
				List<dev.skypilot.mod.hud.HudRow> rows = InfoHud.rows(SkyPilot.get(), mc, now);
				check(rows.size() >= 8, "info HUD lines: " + rows.size());
				check(rows.stream().anyMatch(r -> r.text().startsWith("XYZ")), "coordinates line");
				check(rows.stream().anyMatch(r -> r.text().contains("Dark Auction")), "event timer line");
				check(rows.stream().anyMatch(r -> r.text().startsWith("BPS")), "BPS line");
				check(rows.stream().anyMatch(r -> r.text().startsWith("Farming") && r.progress() > 0.24 && r.progress() < 0.25), "skill progress bar");
			});
			world.getServer().runOnServer(server -> {
				var pl = server.getPlayerList();
				pl.broadcastSystemMessage(Component.literal("[MVP+] Steve joined the lobby!"), false);
				pl.broadcastSystemMessage(Component.literal("Your Bazaar order was filled!"), false);
				pl.broadcastSystemMessage(Component.literal("Your Bazaar order was filled!"), false);
				pl.broadcastSystemMessage(Component.literal("Alex: meet me at x: 12, y: 70, z: -40"), false);
				pl.broadcastSystemMessage(Component.literal("  SLAYER QUEST STARTED!"), false);
				pl.broadcastSystemMessage(Component.literal("  SLAYER QUEST COMPLETE!"), false);
				pl.broadcastSystemMessage(Component.literal("RARE DROP! Enchanted Book (+208% ✯ Magic Find)"), false);
			});
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> {
				check(shown(mc, "joined the lobby") == null, "lobby join line hidden");
				long filled = history(mc).stream().filter(m -> m.content().getString().contains("Your Bazaar order was filled!")).count();
				check(filled == 1 && shown(mc, "Your Bazaar order was filled!").getString().contains("(x2)"), "repeated line stacked: " + filled);
				check(shown(mc, "Your Bazaar order was filled!").getString().startsWith("["), "timestamp in front");
				Component coords = shown(mc, "meet me at");
				String[] cmd = new String[1];
				boolean[] copy = new boolean[1];
				coords.visit((style, text) -> {
					if (style.getClickEvent() instanceof ClickEvent.RunCommand(String command) && command.startsWith("/skypilot wp at")) cmd[0] = command;
					if (style.getClickEvent() instanceof ClickEvent.CopyToClipboard) copy[0] = true;
					return java.util.Optional.empty();
				}, Style.EMPTY);
				check("/skypilot wp at 12 70 -40".equals(cmd[0]), "waypoint button: " + cmd[0]);
				check(copy[0], "click-to-copy on the text");
				check(Trackers.dropTotal == 1 && Trackers.DROPS.get("Enchanted Book") == 1, "rare drop counted");
				check(Trackers.slayerKills == 1, "slayer quest counted");
				mc.player.connection.sendCommand("skypilot calc 1.5m*3+200k");
				mc.player.connection.sendCommand("skypilot wp at 12 70 -40");
			});
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				check(shown(mc, "4,700,000") != null, "calculator answer in chat");
				check(Waypoints.target() != null && Waypoints.target().x() == 12, "chat coordinates became a waypoint");
			});
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-info-hud"));
			ctx.runOnClient(mc -> Waypoints.clear());

			// slot decorations: rarity colour, numbers, search dimming; filler panes have no tooltip
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.threeRows(3, mc.player.getInventory());
				ItemStack swordStack = skyblockItem(Items.IRON_SWORD, "HYPERION", 1);
				swordStack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Gear Score: 900"), Component.literal("LEGENDARY SWORD"))));
				CompoundTag stars = swordStack.get(DataComponents.CUSTOM_DATA).copyTag();
				stars.putInt("upgrade_level", 5);
				CustomData.set(DataComponents.CUSTOM_DATA, swordStack, stars);
				menu.getSlot(0).set(swordStack);
				ItemStack bookStack = new ItemStack(Items.ENCHANTED_BOOK);
				CompoundTag bt = new CompoundTag();
				bt.putString("id", "ENCHANTED_BOOK");
				CompoundTag ench = new CompoundTag();
				ench.putInt("sharpness", 7);
				bt.put("enchantments", ench);
				CustomData.set(DataComponents.CUSTOM_DATA, bookStack, bt);
				bookStack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Sharpness VII"), Component.literal("MYTHIC"))));
				menu.getSlot(1).set(bookStack);
				ItemStack pet = new ItemStack(Items.PLAYER_HEAD);
				CompoundTag pt = new CompoundTag();
				pt.putString("id", "PET");
				CustomData.set(DataComponents.CUSTOM_DATA, pet, pt);
				pet.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl 100] Ender Dragon"));
				pet.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("LEGENDARY"))));
				menu.getSlot(2).set(pet);
				ItemStack minion = new ItemStack(Items.COBBLESTONE);
				CompoundTag mt = new CompoundTag();
				mt.putString("id", "COBBLESTONE_GENERATOR_11");
				CustomData.set(DataComponents.CUSTOM_DATA, minion, mt);
				minion.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("RARE"))));
				menu.getSlot(3).set(minion);
				for (int i = 9; i < 18; i++) {
					ItemStack pane = new ItemStack(Items.GLASS_PANE);
					pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
					menu.getSlot(i).set(pane);
				}
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Accessory Bag"));
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-slot-decor"));
			ctx.runOnClient(mc -> {
				ItemStack pane = new ItemStack(Items.GLASS_PANE);
				pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
				List<Component> lines = new ArrayList<>(List.of(Component.literal(" ")));
				PriceCheck.tooltip(pane, lines);
				check(lines.isEmpty(), "filler pane tooltip hidden");
				List<Component> hyp = new ArrayList<>();
				PriceCheck.tooltip(skyblockItem(Items.IRON_SWORD, "HYPERION", 1), hyp);
				check(hyp.stream().anyMatch(c -> c.getString().equals("ID: HYPERION")), "item id line");
				check(PriceCheck.binPrice(List.of(Component.literal("Buy it now: 750,000,000 coins"))) == 750_000_000d, "BIN from auction lore");
				ItemBrowser.setData(new Dto.ItemPage(0, 0, List.of()), "hyperion");
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-search-highlight"));
			ctx.runOnClient(mc -> ItemBrowser.setData(new Dto.ItemPage(0, 0, List.of()), ""));

			// Garden visitor: what the requested items cost
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.sixRows(4, mc.player.getInventory());
				ItemStack offer = new ItemStack(Items.EMERALD);
				offer.set(DataComponents.CUSTOM_NAME, Component.literal("Accept Offer"));
				offer.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Items Required:"),
						Component.literal(" Enchanted Hay Bale x2"), Component.literal(" Wheat x64"), Component.literal(""), Component.literal("Rewards:"),
						Component.literal(" +4,000 Farming XP"), Component.literal(" +12 Copper"))));
				menu.getSlot(29).set(offer);
				VisitorPanel.setPrice("Enchanted Hay Bale", 150_000.0);
				VisitorPanel.setPrice("Wheat", 6.0);
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Jacob"));
			});
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> check(VisitorPanel.find((ContainerScreen) mc.gui.screen()) != null, "visitor offer recognised"));
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-visitor"));
			ctx.setScreen(() -> new HudEditorScreen(null));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-hud-editor-info"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				dev.skypilot.mod.core.Features.disableAll(SkyPilot.get().config(), null);
				SkyPilot.get().config().priceTooltip = true;
				SkyPilot.get().config().hud.enabled = true;
				Trackers.reset();
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
