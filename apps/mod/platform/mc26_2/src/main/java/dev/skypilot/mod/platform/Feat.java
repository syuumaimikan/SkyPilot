package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** Feature switches and the ways features get the player's attention (title, sound, toast). */
final class Feat {
	private Feat() {
	}

	static boolean on(Features.Feature f) {
		SkyPilot sp = SkyPilot.get();
		return sp != null && sp.config().on(f);
	}

	/** Big title in the middle of the screen (client-side only, like a vanilla /title). */
	static void title(String title, int color, String subtitle) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.hud.setTimes(5, 40, 10);
		mc.gui.hud.setSubtitle(Component.literal(subtitle == null ? "" : subtitle).withColor(0xFFDDDDDD));
		mc.gui.hud.setTitle(Component.literal(title).withColor(color));
	}

	static void ping(float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, pitch));
	}

	static void toast(ItemStack icon, String title, int color, String line) {
		Minecraft.getInstance().gui.toastManager().addToast(new FlipToast(icon, title, color, line));
	}

	/** 26.2 item components are bound by a world connection, not by reaching the title screen. */
	static ItemStack icon(net.minecraft.world.item.Item item) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || mc.player == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
