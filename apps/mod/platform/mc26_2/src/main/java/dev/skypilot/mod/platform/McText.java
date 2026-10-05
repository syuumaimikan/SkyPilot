package dev.skypilot.mod.platform;

import dev.skypilot.mod.translation.StyledLine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Converts Minecraft text components to {@link StyledLine} and back without losing styles. */
final class McText {
	/** A flattened component plus the styles its segments refer to. */
	record Flattened(StyledLine line, List<Style> styles) {
	}

	private McText() {
	}

	static Flattened flatten(Component component) {
		List<StyledLine.Segment> segments = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		component.visit((style, text) -> {
			if (!text.isEmpty()) {
				int id = styles.indexOf(style);
				if (id < 0) {
					styles.add(style);
					id = styles.size() - 1;
				}
				segments.add(new StyledLine.Segment(text, id));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new Flattened(new StyledLine(segments), styles);
	}

	/** Rebuild a component: original styles (incl. hover/click events) are re-applied by id. */
	static Component rebuild(StyledLine line, List<Style> styles) {
		MutableComponent out = Component.empty();
		for (StyledLine.Segment s : line.segments()) {
			Style style = s.styleId() == StyledLine.MARKER_STYLE ? Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true)
					: s.styleId() >= 0 && s.styleId() < styles.size() ? styles.get(s.styleId()) : Style.EMPTY;
			out.append(Component.literal(s.text()).setStyle(style));
		}
		return out;
	}

	static MutableComponent prefix() {
		return Component.literal("[SkyPilot] ").withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** Show a client-side chat line (never sent to the server). */
	static void chat(Component c) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(c));
	}

	static void info(String text) {
		chat(prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
	}

	static void error(String text) {
		chat(prefix().append(Component.literal(text).withStyle(ChatFormatting.RED)));
	}
}
