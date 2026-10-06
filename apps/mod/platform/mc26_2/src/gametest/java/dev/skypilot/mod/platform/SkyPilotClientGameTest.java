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
	private static ItemStack order(net.minecraft.world.item.Item item, String name, String... lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		List<Component> lines = new ArrayList<>();
		for (String l : lore) lines.add(Component.literal(l));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
		return s;
	}

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
		// No SkyPilot server during the test (as on CI): a server running on this PC would replace
		// the synthetic data the screens are checked with.
		ctx.runOnClient(mc -> {
			SkyPilot.get().config().apiBaseUrl = "http://127.0.0.1:9";
			SkyPilot.get().rebuildApi();
		});

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
								List.of(new Dto.HudLine("MP", "181 → 184")), 0.95, null, 0.05, List.of(), null),
								new Dto.PlanAction("Voidgloom Seraph用の武器を購入", "Slayer", "武器ティアが周回できるTierを制限しています。", List.of(),
										List.of(), 0.6, 1.4e6, 0.1, List.of(new Dto.ItemRef("VOIDEDGE_KATANA", "Voidedge Katana", 1.4e6, book)),
										new Dto.Place(new Dto.Waypoint("HUB", "Auction House", -25, 73, -13), "/hub", "オークションハウスかバザーで購入します。"))),
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

			// ---------------------------------------------------------------- second feature batch
			ctx.runOnClient(mc -> {
				var c = SkyPilot.get().config();
				for (var f : List.of(dev.skypilot.mod.core.Features.CHAT_HIDE_SACKS, dev.skypilot.mod.core.Features.CHAT_HIDE_SEPARATORS,
						dev.skypilot.mod.core.Features.CHAT_KEYWORDS, dev.skypilot.mod.core.Features.CHAT_SEARCH, dev.skypilot.mod.core.Features.MINING_HIDE_CHESTS,
						dev.skypilot.mod.core.Features.MINING_CHEST_TRACKER, dev.skypilot.mod.core.Features.DUNGEONS_FILTER, dev.skypilot.mod.core.Features.DUNGEONS_TIMER,
						dev.skypilot.mod.core.Features.DUNGEONS_BOSS, dev.skypilot.mod.core.Features.FISHING_SEA_CREATURE, dev.skypilot.mod.core.Features.FISHING_TRACKER,
						dev.skypilot.mod.core.Features.FISHING_TROPHY_ALERT, dev.skypilot.mod.core.Features.HUD_PET, dev.skypilot.mod.core.Features.MINING_FORGES,
						dev.skypilot.mod.core.Features.FARMING_GARDEN, dev.skypilot.mod.core.Features.HUD_PLAYTIME, dev.skypilot.mod.core.Features.HUD_PURSE,
						dev.skypilot.mod.core.Features.HUD_COMPASS, dev.skypilot.mod.core.Features.TOOLS_TODO, dev.skypilot.mod.core.Features.TOOLS_STOPWATCH,
						dev.skypilot.mod.core.Features.INV_AH_HIGHLIGHT, dev.skypilot.mod.core.Features.TOOLTIP_BAZAAR_MARGIN)) {
					dev.skypilot.mod.core.Features.set(c, f, true);
				}
				c.keywords.add("carry");
				Activity.setSeaCreatures(List.of(new Dto.SeaCreature("Sea Emperor", "WATER", "The Sea Emperor arises from the depths.", "LEGENDARY", 2000.0)));
				Activity.tab = List.of("Pet:", " [Lvl 100] Ender Dragon", " 2,345,678/25,353,230 XP", "", "Forges:", " 1) Refined Diamond: 3h 20m", " 2) Ready!", "",
						"Garden Level: XV", "Copper: 1,234");
			});
			// a dungeon sidebar: floor, time elapsed, cleared %
			world.getServer().runOnServer(server -> {
				ServerScoreboard sb = server.getScoreboard();
				Objective o = sb.getObjective("sp_test");
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("⏣ The Catacombs (F7)"), o).set(6);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Time Elapsed: 05m 12s"), o).set(5);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Cleared: 45% (123)"), o).set(4);
				sb.resetSinglePlayerScore(ScoreHolder.forNameOnly("Purse: 1,234,567"), o);
				sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Purse: 1,300,000"), o).set(2);
			});
			ctx.waitFor(mc -> Activity.dungeon != null && "F7".equals(Activity.dungeon.floor()), 100);
			world.getServer().runOnServer(server -> {
				var pl = server.getPlayerList();
				for (String line : List.of("[Sacks] +123 items. (Last 30s.)", "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", "You uncovered a treasure chest!",
						"You have successfully picked the lock on this chest!", "    Gemstone Powder x537", "The Lost Adventurer hit you for 1,200 damage!",
						"GOOD CATCH! You found a Fish Bait.", " TROPHY FISH! You caught a Lavahorse GOLD!", "Party > Alex: need a carry for f7",
						"[BOSS] Maxor: WELL WELL WELL", "The Sea Emperor arises from the depths.")) {
					pl.broadcastSystemMessage(Component.literal(line), false);
				}
			});
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> {
				check(shown(mc, "[Sacks]") == null, "sack summary hidden");
				check(shown(mc, "▬▬▬▬") == null, "separator hidden");
				check(shown(mc, "uncovered a treasure chest") == null && shown(mc, "picked the lock") == null, "treasure chest spam hidden");
				check(shown(mc, "hit you for 1,200 damage") == null, "dungeon damage line hidden");
				check(shown(mc, "need a carry").getString().contains("★ carry"), "keyword marked");
				check(Activity.chests == 1 && Activity.CHEST_POWDER.get("Gemstone") == 537, "hidden chest line still counted");
				check(Activity.seaCreatureTotal == 1 && Activity.SEA_CREATURES.get("Sea Emperor") == 1, "sea creature recognised");
				check(Activity.treasureCatches == 1 && Activity.TROPHIES.get("GOLD") == 1, "treasure and trophy counted");
				check("Maxor".equals(Activity.bossName) && Activity.bossElapsed == 312, "boss room split at 05m 12s");
				check(Activity.purseDelta == 1_300_000 - 1_234_567, "purse change: " + Activity.purseDelta);
				check(ChatFeatures.LOG.stream().anyMatch(e -> e.hidden() && e.text().startsWith("[Sacks]")), "chat search keeps hidden lines");
				mc.player.connection.sendCommand("skypilot todo add Buy a Hyperion");
				mc.player.connection.sendCommand("skypilot timer start");
				mc.player.connection.sendCommand("skypilot find carry");
				// the test world has no Hypixel tab list; the per-second tick reads the real one, so set it just before
				Activity.tab = List.of("Pet:", " [Lvl 100] Ender Dragon", " 2,345,678/25,353,230 XP", "", "Forges:", " 1) Refined Diamond: 3h 20m", " 2) Ready!", "",
						"Garden Level: XV", "Copper: 1,234");
				List<dev.skypilot.mod.hud.HudRow> rows = InfoHud.rows(SkyPilot.get(), mc, System.currentTimeMillis());
				check(rows.stream().anyMatch(r -> r.text().contains("[Lvl 100] Ender Dragon")), "pet from the tab list");
				check(rows.stream().anyMatch(r -> r.text().contains("Refined Diamond")), "forges from the tab list");
				check(rows.stream().anyMatch(r -> r.text().startsWith("F7") && r.text().contains("45%")), "dungeon timer line");
				check(rows.stream().anyMatch(r -> r.text().startsWith("Sea creatures 1")), "fishing tracker line");
				check(rows.stream().anyMatch(r -> r.text().startsWith("Chests 1")), "chest tracker line");
				check(PriceCheck.bazaarMargin(100, 120, 0.0125, dev.skypilot.mod.core.Lang.EN).getString().contains("+18.5"), "bazaar margin after tax");
			});
			ctx.waitTicks(25);
			ctx.runOnClient(mc -> check(shown(mc, "Buy a Hyperion") != null && SkyPilot.get().config().todos.size() == 1, "todo added"));
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-features-2"));
			// auction browser: a listing far below its value gets a green outline
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.sixRows(5, mc.player.getInventory());
				ItemStack cheap = skyblockItem(Items.IRON_SWORD, "HYPERION", 1);
				cheap.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Seller: Alex"), Component.literal("Buy it now: 600,000,000 coins"))));
				ItemStack dear = skyblockItem(Items.DIAMOND, "ENCHANTED_DIAMOND", 1);
				dear.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Buy it now: 9,000 coins"))));
				menu.getSlot(11).set(cheap);
				menu.getSlot(12).set(dear);
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Auctions Browser"));
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				AuctionHighlight a = ScreenOverlays.auctionOf(mc.gui.screen());
				check(a != null, "auction highlight attached");
				a.setValues(java.util.Map.of(11, 800_000_000.0, 12, 5_000.0));
				check(AuctionHighlight.verdict(600e6, 800e6) == -1 && AuctionHighlight.verdict(9_000, 5_000) == 1 && AuctionHighlight.verdict(100, 100) == 0, "deal verdicts");
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-auction-deals"));
			ctx.setScreen(() -> null);

			// Item browser: the grid fills the whole panel, hover shows the item card, categories,
			// and the left edge pushes the list into the screen edge.
			ctx.runOnClient(mc -> {
				SkyPilot.get().config().language = "ja";
				SkyPilot.get().config().inventory.itemBrowser = true; // switched off by the features section above
				SkyPilot.get().config().inventory.itemBrowserCategory = "weapons";
				List<Dto.ItemEntry> many = new ArrayList<>();
				Dto.ItemIcon diamondSword = new Dto.ItemIcon("minecraft:diamond_sword", null, null, false);
				Dto.ItemIcon bow = new Dto.ItemIcon("minecraft:bow", null, null, true);
				many.add(new Dto.ItemEntry("HYPERION", "Hyperion", "LEGENDARY", diamondSword, 4.7e8));
				for (int i = 1; i < 600; i++) many.add(new Dto.ItemEntry("W" + i, "Weapon " + i, i % 3 == 0 ? "EPIC" : "RARE", i % 2 == 0 ? bow : diamondSword, 1000.0 * i));
				ItemBrowser.setData(new Dto.ItemPage(1200, 0, many), "");
				ItemBrowser.setCard("HYPERION", new Dto.ItemDetail(many.get(0), "SWORD", List.of("§7Damage: §c+260", "§7Strength: §c+150", "", "§6Ability: Wither Impact §e§lRIGHT CLICK",
						"§7Teleports §a10 blocks §7ahead of you."), "BIN", 4.65e8, 3.2, null, null, 100_000.0, 4.4e8, List.of(), List.of(), List.of(), null));
			});
			ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(3);
			int[] cell = new int[2];
			double[] scale = new double[1];
			ctx.runOnClient(mc -> {
				ItemBrowser b = ScreenOverlays.browserOf(mc.gui.screen());
				check(b != null, "item browser attached");
				check(b.fillsPanel(), "item grid fills the panel height");
				check(b.cells() >= 30, "cells on one page: " + b.cells());
				int[] c = b.cellCenter(0);
				cell[0] = c[0];
				cell[1] = c[1];
				scale[0] = mc.getWindow().getGuiScale();
				Dto.ItemDetail card = new Dto.ItemDetail(new Dto.ItemEntry("HYPERION", "Hyperion", "LEGENDARY", null, 4.7e8), "SWORD", List.of("§7Damage: §c+260"),
						"BIN", 4.65e8, 3.2, null, null, 100_000.0, null, List.of(), List.of(), List.of(), null);
				String tip = dev.skypilot.mod.core.Text.strip(ItemBrowser.tooltip(card.item(), card, dev.skypilot.mod.core.Lang.JA).getString());
				check(tip.contains("Damage: +260") && tip.contains("最安BIN") && tip.contains("NPC売却") && !tip.contains("クラフト費用"), "hover card: " + tip);
			});
			ctx.getInput().setCursorPos(cell[0] * scale[0], cell[1] * scale[0]);
			ctx.waitTicks(10);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-item-browser-full"));
			ctx.runOnClient(mc -> SkyPilot.get().config().inventory.itemBrowserCollapsed = true);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> check(ScreenOverlays.browserOf(mc.gui.screen()).collapsedNow(), "item browser collapsed to a tab"));
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-item-browser-collapsed"));
			ctx.runOnClient(mc -> {
				SkyPilot.get().config().inventory.itemBrowserCollapsed = false;
				SkyPilot.get().config().inventory.itemBrowserWidth = 120;
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-item-browser-narrow"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				SkyPilot.get().config().inventory.itemBrowserWidth = 0;
				SkyPilot.get().config().inventory.itemBrowserCategory = "all";
				SkyPilot.get().config().inventory.itemBrowser = false;
				ItemBrowser.setData(null, "");
			});

			// Bazaar order helper: colours your own order items, reading only.
			ctx.runOnClient(mc -> SkyPilot.get().config().features.put("market.bazaar_order_helper", true));
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.fourRows(7, mc.player.getInventory());
				menu.getSlot(10).set(order(Items.DIAMOND, "BUY Enchanted Diamond", "Filled: 10/640 (1.6%)", "Price per unit: 1,234.5 coins"));
				menu.getSlot(11).set(order(Items.GOLD_INGOT, "SELL Enchanted Gold", "Filled: 0/64 (0%)", "Price per unit: 980 coins"));
				menu.getSlot(12).set(order(Items.SUGAR_CANE, "BUY Enchanted Sugar Cane", "Filled: 64/64 100%!", "Price per unit: 1,500 coins"));
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Your Bazaar Orders"));
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				BazaarOrders o = ScreenOverlays.ordersOf(mc.gui.screen());
				check(o != null, "order helper attached");
				o.setQuotes(java.util.Map.of(
						"Enchanted Diamond", new Dto.BazaarQuote("Enchanted Diamond", "ENCHANTED_DIAMOND", 1300.0, 1234.5),
						"Enchanted Gold", new Dto.BazaarQuote("Enchanted Gold", "ENCHANTED_GOLD", 950.0, 900.0)));
				dev.skypilot.mod.core.BazaarOrderRules.Order gold = BazaarOrders.order(((ContainerScreen) mc.gui.screen()).getMenu().getSlot(11).getItem());
				check(gold != null && !gold.buy() && gold.price() == 980, "order read from the item: " + gold);
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-bazaar-orders"));
			ctx.setScreen(() -> null);

			// AFK guard: away after the set minutes, alerts counted, low FPS, summary when back.
			ctx.runOnClient(mc -> {
				var f = SkyPilot.get().config().features;
				f.put("afk.detect", true);
				f.put("afk.alerts", true);
				f.put("afk.summary", true);
				f.put("afk.low_fps", true);
				Afk.idleFor(10 * 60_000L);
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				check(Afk.afk(), "marked AFK after 10 minutes without input");
				check(Afk.fpsLimit(260) == 10, "10 FPS while AFK");
				Afk.chat("From [VIP] Friend: are you there?");
				Afk.chat("[MVP+] Friend has invited you to join their party!");
				Afk.chat("Hello everyone");
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-afk-alert"));
			ctx.runOnClient(mc -> Afk.input());
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				check(!Afk.afk(), "back after input");
				check(Afk.fpsLimit(260) == 260, "frame rate back");
				Component back = shown(mc, "離席中");
				check(back != null && back.getString().contains("1× 個人メッセージ") && back.getString().contains("1× パーティ招待"),
						"welcome-back summary: " + (back == null ? null : back.getString()));
				var f = SkyPilot.get().config().features;
				f.remove("afk.detect");
				f.remove("afk.alerts");
				f.remove("afk.summary");
				f.remove("afk.low_fps");
				f.remove("market.bazaar_order_helper");
				SkyPilot.get().config().language = "auto";
			});
			ctx.runOnClient(mc -> {
				dev.skypilot.mod.core.Features.disableAll(SkyPilot.get().config(), null);
				SkyPilot.get().config().priceTooltip = true;
				SkyPilot.get().config().hud.enabled = true;
				SkyPilot.get().config().keywords.clear();
				SkyPilot.get().config().todos.clear();
				Activity.reset();
				Activity.tab = List.of();
			});

			// ---------------------------------------------------------------- fit, builds, money, rift, dungeon helpers
			ctx.runOnClient(mc -> {
				Dto.Place maddox = new Dto.Place(new Dto.Waypoint("HUB", "Maddox the Slayer", -83, 69, -131), "/warp crypt", "Maddox the Slayerでスレイヤークエストを開始します。");
				Dto.PlanAction slay = new Dto.PlanAction("Voidgloom Seraph T2を周回", "Slayer", "目標に最も近づきます。", List.of(), List.of(), 0.7, 2.0e5, 1.0, List.of(), maddox);
				MenuScreen.setFit(new Dto.Fit(
						List.of(new Dto.ClassFit("Mage", 34, 0.4, 0.71, List.of("Mage レベル 34", "Hyperion を所持"), true),
								new Dto.ClassFit("Archer", 21, 0.1, 0.25, List.of("Archer レベル 21"), false),
								new Dto.ClassFit("Berserk", 18, 0.6, 0.22, List.of("Berserk レベル 18"), false)),
						"Mage",
						List.of(new Dto.FloorFit("F6", 0.92, true, false, 40, List.of()), new Dto.FloorFit("F7", 0.74, true, true, 3, List.of("Catacombs: 28 / 30", "Magical Power: 410 / 550"))),
						List.of(new Dto.SlayerFit("enderman", "Voidgloom Seraph", 2, 2, 0.64, "REQUIRED")),
						List.of(new Dto.GoalPath("Enderman Slayer 7", 0.12, 1.6e7, 15.0,
								List.of(new Dto.PlanStep("Magical Power 300", "Voidgloom Seraph T2の推奨値", "IN_PROGRESS", 0.6, 6.0e6, 0.6)), slay)),
						List.of("クラス適性はSkyPilotの推定です。"), null));
				Dto.ItemIcon katanaIcon = new Dto.ItemIcon("minecraft:diamond_sword", null, null, false);
				MenuScreen.setBuilds(new Dto.Builds(List.of(new Dto.Build("enderman", "Voidgloom Seraph の装備構成", List.of(
						new Dto.BuildTier("REQUIRED", List.of(new Dto.BuildSlot("weapon", List.of(new Dto.GearItem("VOIDEDGE_KATANA", "Voidedge Katana", katanaIcon, "weapon", "REQUIRED", true, 1.4e6, null)), true, null)),
								true, 0.0),
						new Dto.BuildTier("RECOMMENDED", List.of(new Dto.BuildSlot("weapon", List.of(new Dto.GearItem("VORPAL_KATANA", "Vorpal Katana", katanaIcon, "weapon", "RECOMMENDED", false, 9.5e6, null)), false, 9.5e6)),
								false, 9.5e6)), true, "data/registry/slayers.toml")), null));
				MenuScreen.setMoney(new Dto.Money(List.of(new Dto.MoneyMethod("slayer_best", "Slayer bosses (expected value)", "Slayer", 1.2e6, 2.0e6, 0.0, "MEDIUM", true, List.of(),
						"T3 Revenant Horror の期待値", 0.6, maddox)), null));
				// a "Guide" sends the line to Maddox and, off the island, offers the warp in the chat box
				Waypoints.setIslandOverride("DUNGEON_HUB");
				Guides.go(maddox, false);
				check(Waypoints.target() != null && Waypoints.target().name().equals("Maddox the Slayer"), "guide line to the action's place");
				check(!Waypoints.onTargetIsland(), "different island");
			});
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen, "warp offered in the chat box (not sent)");
				mc.gui.setScreen(null);
				Waypoints.clear();
				Waypoints.setIslandOverride(null);
			});
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.FIT));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-fit"));
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.BUILD));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-builds"));
			ctx.setScreen(() -> new MenuScreen(null, MenuScreen.Tab.MONEY));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-menu-money"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				var c = SkyPilot.get().config();
				for (var f : List.of(dev.skypilot.mod.core.Features.RIFT_TIMER, dev.skypilot.mod.core.Features.RIFT_ALERT, dev.skypilot.mod.core.Features.DUNGEONS_PUZZLES,
						dev.skypilot.mod.core.Features.DUNGEONS_SPLITS, dev.skypilot.mod.core.Features.HUD_STATS)) {
					dev.skypilot.mod.core.Features.set(c, f, true);
				}
				// the current action bar uses private-use icons
				Trackers.actionBar("§c1,200/1,500     §a300§a     §b400/620     §a0m 45s Left");
				check(Trackers.stats != null && Trackers.stats.health() == 1200, "health from the U+E010 icon");
				check(Activity.riftSeconds == 45, "Rift time from the U+E020 icon");
				Guides.setPuzzles(new Dto.Puzzle[] {new Dto.Puzzle("Three Weirdos", "all", "本当のことを言っている人の宝箱を開けます。", true, "wiki")});
				Activity.tab = List.of("Puzzles: (2)", " Three Weirdos: [✦]", " Water Board: [✔]");
				Activity.BOSS_SPLITS.put("Maxor", 130);
				Activity.BOSS_SPLITS.put("Storm", 185);
				List<dev.skypilot.mod.hud.HudRow> rows = InfoHud.rows(SkyPilot.get(), mc, System.currentTimeMillis());
				check(rows.stream().anyMatch(r -> r.text().contains("本当のことを言っている人")), "puzzle rule shown");
				check(rows.stream().anyMatch(r -> r.text().startsWith("Maxor 2m 10s → Storm")), "boss phase splits");
				check(rows.stream().anyMatch(r -> r.text().startsWith("Rift") && r.text().contains("45s")), "Rift time line");
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-rift-dungeon-hud"));
			ctx.runOnClient(mc -> {
				dev.skypilot.mod.core.Features.disableAll(SkyPilot.get().config(), null);
				SkyPilot.get().config().priceTooltip = true;
				SkyPilot.get().config().hud.enabled = true;
				Activity.BOSS_SPLITS.clear();
				Activity.tab = List.of();
			});

			// ---------------------------------------------------------------- third batch: filters, garden, mayor, chests, sacks
			ctx.runOnClient(mc -> {
				var c = SkyPilot.get().config();
				for (var f : List.of(dev.skypilot.mod.core.Features.CHAT_HIDE_WELCOME, dev.skypilot.mod.core.Features.CHAT_HIDE_USELESS_DROPS,
						dev.skypilot.mod.core.Features.CHAT_HIDE_WARNINGS, dev.skypilot.mod.core.Features.CHAT_HIDE_SKYMALL, dev.skypilot.mod.core.Features.FARMING_PEST_ALERT,
						dev.skypilot.mod.core.Features.FARMING_TRAPS, dev.skypilot.mod.core.Features.FARMING_VISITOR_ALERT, dev.skypilot.mod.core.Features.HUD_MAYOR,
						dev.skypilot.mod.core.Features.HUD_TIMERS, dev.skypilot.mod.core.Features.DUNGEONS_CHEST_PROFIT, dev.skypilot.mod.core.Features.INV_SACK_VALUE,
						dev.skypilot.mod.core.Features.COMBAT_RARE_DROP, dev.skypilot.mod.core.Features.INV_CHEST_VALUE)) {
					dev.skypilot.mod.core.Features.set(c, f, true);
				}
				Timers.skyblock = new Dto.SkyblockNow(new Dto.Mayor("Diana", List.of(new Dto.Perk("Mythological Ritual", ""), new Dto.Perk("Pet XP Buff", ""))),
						new Dto.Mayor("Paul", List.of(new Dto.Perk("EZPZ", ""))), 518, List.of());
			});
			world.getServer().runOnServer(server -> {
				var pl = server.getPlayerList();
				for (String line : List.of("Welcome to Hypixel SkyBlock!", "RARE DROP! Enchanted Ender Pearl (+120% ✯ Magic Find)", "Whoa! Slow down there!",
						"New day! Your Sky Mall buff changed!", "GROSS! A  Pest has appeared in Plot - S 4!")) {
					pl.broadcastSystemMessage(Component.literal(line), false);
				}
			});
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> {
				check(shown(mc, "Welcome to Hypixel SkyBlock") == null && shown(mc, "Enchanted Ender Pearl") == null && shown(mc, "Whoa! Slow down") == null
						&& shown(mc, "Sky Mall buff changed") == null, "minor lines hidden");
				check(shown(mc, "Pest has appeared") != null, "the pest line stays (only the title is added)");
				// garden tab widgets: a visitor arrives, a trap fills up
				Activity.tab = List.of("Visitors: (1)", " Jacob", "", "Pest Traps: 2/3", "Full Traps: None", "No Bait: #3", "", "Timers:", " Cookie Buff: 3d 4h");
				Activity.garden(SkyPilot.get().lang());
				Activity.tab = List.of("Visitors: (2)", " Jacob", " Emissary Carlton", "", "Pest Traps: 2/3", "Full Traps: #1", "No Bait: #3", "", "Timers:", " Cookie Buff: 3d 4h");
				Activity.garden(SkyPilot.get().lang());
				List<dev.skypilot.mod.hud.HudRow> rows = InfoHud.rows(SkyPilot.get(), mc, System.currentTimeMillis());
				check(rows.stream().anyMatch(r -> r.text().startsWith("Mayor Diana")), "mayor line");
				check(rows.stream().anyMatch(r -> r.text().equals("Full Traps: #1")), "trap lines");
				check(rows.stream().anyMatch(r -> r.text().contains("Cookie Buff: 3d 4h")), "timers widget");
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-features-3"));
			// a Bedrock reward chest: contents value, cost and profit
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.sixRows(6, mc.player.getInventory());
				menu.getSlot(10).set(skyblockItem(Items.IRON_SWORD, "HYPERION", 1));
				ItemStack open = new ItemStack(Items.CHEST);
				open.set(DataComponents.CUSTOM_NAME, Component.literal("Open Reward Chest"));
				open.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Cost"), Component.literal("4,000,000 Coins"))));
				menu.getSlot(31).set(open);
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Bedrock Chest"));
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				var screen = (ContainerScreen) mc.gui.screen();
				ChestValue cv = ScreenOverlays.chestOf(screen);
				List<net.minecraft.world.inventory.Slot> slots = ChestValue.slotsOf(screen).stream().filter(sl -> PriceCheck.item(sl.getItem()) != null).toList();
				cv.apply(slots, new Dto.Values(List.of(new Dto.Value("HYPERION", "Hyperion", 4.7e8, 0.8)), 4.7e8));
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-reward-chest"));
			// a sack: stored amounts × prices
			ctx.setScreen(() -> {
				Minecraft mc = Minecraft.getInstance();
				ChestMenu menu = ChestMenu.sixRows(7, mc.player.getInventory());
				ItemStack wheat = new ItemStack(Items.HAY_BLOCK);
				wheat.set(DataComponents.CUSTOM_NAME, Component.literal("Enchanted Hay Bale"));
				wheat.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Stored: 1,234/60.5k"))));
				ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS);
				seeds.set(DataComponents.CUSTOM_NAME, Component.literal("Seeds"));
				seeds.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Stored: 52,100/60.5k"))));
				menu.getSlot(10).set(wheat);
				menu.getSlot(11).set(seeds);
				NamePrices.set("Enchanted Hay Bale", 150_000.0);
				NamePrices.set("Seeds", 3.0);
				return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Enchanted Agronomy Sack"));
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-sack-value"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				dev.skypilot.mod.core.Features.disableAll(SkyPilot.get().config(), null);
				SkyPilot.get().config().priceTooltip = true;
				SkyPilot.get().config().hud.enabled = true;
				SkyPilot.get().config().inventory.chestValue = true;
				Activity.tab = List.of();
				Timers.skyblock = null;
			});

			// ---------------------------------------------------------------- mod manager
			ctx.runOnClient(mc -> {
				try {
					java.nio.file.Path dir = ModManager.modsDir();
					java.nio.file.Files.createDirectories(dir);
					java.nio.file.Path jar = dir.resolve("skypilot-test-mod.jar.disabled");
					try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
						zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
						zip.write("{\"schemaVersion\":1,\"id\":\"sp_test_mod\",\"name\":\"SkyPilot Test Mod\",\"version\":\"1.0.0\",\"description\":\"A switched-off mod for the test.\"}"
								.getBytes(java.nio.charset.StandardCharsets.UTF_8));
						zip.closeEntry();
					}
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
				List<ModManager.Entry> mods = ModManager.scan();
				check(mods.stream().anyMatch(e -> e.id().equals("skypilot")), "SkyPilot itself is listed");
				check(mods.stream().anyMatch(e -> e.id().equals("minecraft") && e.kind() == ModManager.Kind.BUILTIN), "Minecraft is built in");
				ModManager.Entry off = mods.stream().filter(e -> e.id().equals("sp_test_mod")).findFirst().orElseThrow();
				check(off.kind() == ModManager.Kind.DISABLED && off.toggleable(), "the .jar.disabled file is listed as disabled");
				check(mods.stream().anyMatch(e -> !e.dependents().isEmpty()), "dependents are known (e.g. what needs fabric-api)");
				// switch it on: renamed to .jar, listed as not loaded until a restart
				check(ModManager.toggle(off) == null, "switched on");
				check(java.nio.file.Files.exists(ModManager.modsDir().resolve("skypilot-test-mod.jar")), "file renamed to .jar");
				ModManager.Entry on = ModManager.scan().stream().filter(e -> e.id().equals("sp_test_mod")).findFirst().orElseThrow();
				check(on.kind() == ModManager.Kind.NOT_LOADED && ModManager.PENDING.contains(on.file()), "pending until restart");
				check(ModManager.toggle(on) == null && java.nio.file.Files.exists(ModManager.modsDir().resolve("skypilot-test-mod.jar.disabled")), "switched back off");
				check(ModManager.PENDING.isEmpty(), "back to the state the game started with");
				check(ModManager.sha1(ModManager.modsDir().resolve("skypilot-test-mod.jar.disabled")).length() == 40, "file hash for the update check");
				ModManagerScreen.setFilter(ModManagerScreen.Filter.ALL);
			});
			ctx.setScreen(() -> {
				ModManagerScreen s = new ModManagerScreen(null);
				return s;
			});
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> ((ModManagerScreen) mc.gui.screen()).select("skypilot"));
			ctx.waitTicks(2);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-mod-manager"));
			ctx.runOnClient(mc -> ((ModManagerScreen) mc.gui.screen()).select("sp_test_mod"));
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				var enable = mc.gui.screen().children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b
						&& (b.getMessage().getString().equals("Enable") || b.getMessage().getString().equals("有効にする"))).map(w -> (net.minecraft.client.gui.components.Button) w)
						.findFirst().orElse(null);
				check(enable != null && enable.active, "Enable button is usable for a disabled mod file: " + (enable == null ? "missing" : "inactive"));
			});
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-mod-manager-disabled"));
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				try {
					java.nio.file.Files.deleteIfExists(ModManager.modsDir().resolve("skypilot-test-mod.jar.disabled"));
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
				ModManagerScreen.setFilter(ModManagerScreen.Filter.MODS);
			});

			// Performance features: through the real render, particle and frame-rate hooks.
			ctx.runOnClient(mc -> {
				var cfg = SkyPilot.get().config();
				var p = mc.player;
				var splash = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, p.getX() + 2, p.getY(), p.getZ());
				splash.setCustomName(Component.literal("§f✧§e1,234§f✧"));
				var label = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, p.getX() + 2, p.getY(), p.getZ() + 1);
				label.setCustomName(Component.literal("§c[Lv10] Zombie §a100/100❤"));
				var far = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, p.getX() + 100, p.getY(), p.getZ());
				// entities in a level always have an id (hashCode uses it)
				splash.setId(-9001);
				label.setId(-9002);
				far.setId(-9003);
				var frustum = new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(), new org.joml.Matrix4f());
				var dispatcher = mc.getEntityRenderDispatcher();
				dev.skypilot.mod.core.Features.disableAll(cfg, dev.skypilot.mod.core.Features.Category.PERFORMANCE);
				Performance.tick(mc);
				check(!Performance.hide(splash, p.getX(), p.getEyeY(), p.getZ()) && !Performance.hide(far, p.getX(), p.getEyeY(), p.getZ()), "nothing is hidden while the switches are off");
				check(mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0) != null, "particles are created while off");
				for (var f : List.of(dev.skypilot.mod.core.Features.PERF_DAMAGE_SPLASH, dev.skypilot.mod.core.Features.PERF_FAR_ENTITIES, dev.skypilot.mod.core.Features.PERF_HIT_PARTICLES,
						dev.skypilot.mod.core.Features.PERF_MENU_FPS, dev.skypilot.mod.core.Features.PERF_HUD_MEMORY, dev.skypilot.mod.core.Features.PERF_HUD_COUNTS)) {
					dev.skypilot.mod.core.Features.set(cfg, f, true);
				}
				cfg.perf.menuFps = 20;
				Performance.tick(mc);
				check(!dispatcher.shouldRender(splash, frustum, p.getX(), p.getEyeY(), p.getZ()), "damage number left out by the render hook");
				check(!Performance.hide(label, p.getX(), p.getEyeY(), p.getZ()), "mob name labels stay");
				check(Performance.hide(far, p.getX(), p.getEyeY(), p.getZ()), "far entity left out");
				check(!Performance.hide(p, p.getX(), p.getEyeY(), p.getZ()), "the player is never hidden");
				check(mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0) == null, "crit particles skipped by the particle hook");
				check(mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0) != null, "other particles still created");
			});
			// Lightweight video settings: lowered on request, the player's own values come back.
			ctx.runOnClient(mc -> {
				var cfg = SkyPilot.get().config();
				mc.options.renderDistance().set(12);
				mc.options.entityShadows().set(true);
				mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
				int lighter = VideoPreset.pending(mc.options);
				check(lighter >= 3, "preset has something to lower (" + lighter + ")");
				VideoPreset.apply(mc, cfg);
				check(mc.options.renderDistance().get() == 8 && !mc.options.entityShadows().get() && mc.options.cloudStatus().get() == net.minecraft.client.CloudStatus.OFF,
						"video options lowered");
				check(cfg.perf.videoBackup != null && "12".equals(cfg.perf.videoBackup.get("renderDistance")), "own values kept");
				VideoPreset.apply(mc, cfg);
				check("12".equals(cfg.perf.videoBackup.get("renderDistance")), "applying twice keeps the first values");
				VideoPreset.restore(mc, cfg);
				check(mc.options.renderDistance().get() == 12 && mc.options.entityShadows().get() && mc.options.cloudStatus().get() == net.minecraft.client.CloudStatus.FANCY,
						"own values restored");
				check(cfg.perf.videoBackup == null, "backup cleared after restore");
				VideoPreset.apply(mc, cfg);
			});
			PerformanceScreen.showVideo(false);
			ctx.setScreen(() -> new PerformanceScreen(null));
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				int limit = mc.getFramerateLimitTracker().getFramerateLimit();
				check(limit <= 20, "menu FPS limit applied through the frame-rate hook (" + limit + ")");
			});
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-performance"));
			PerformanceScreen.showVideo(true);
			ctx.setScreen(() -> new PerformanceScreen(null));
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-performance-video"));
			ctx.setScreen(() -> null);
			ctx.waitTicks(3);
			ctx.takeScreenshot(TestScreenshotOptions.of("skypilot-performance-hud"));
			ctx.runOnClient(mc -> {
				var cfg = SkyPilot.get().config();
				VideoPreset.restore(mc, cfg);
				cfg.perf.menuFps = 60;
				dev.skypilot.mod.core.Features.disableAll(cfg, dev.skypilot.mod.core.Features.Category.PERFORMANCE);
				Performance.tick(mc);
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
