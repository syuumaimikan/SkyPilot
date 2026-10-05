package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.SkyBlockContext;
import dev.skypilot.mod.platform.mixin.ChatComponentAccessor;
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
