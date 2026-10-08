package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.AfkRules;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/**
 * AFK guard: notices when you stop touching the keyboard and mouse, tells you (sound, toast,
 * flashing taskbar button) about the things that need you while you are away, sums them up when
 * you are back and lowers the frame rate meanwhile. It only watches - no input is ever made for
 * you, so it never keeps you from being marked AFK by the server.
 */
public final class Afk {
	private static volatile long lastInput = System.currentTimeMillis();
	private static boolean afk;
	private static long since;
	private static float healthAtStart;
	private static boolean healthAlerted;
	private static boolean disconnectAlerted;
	private static long lastAlert;
	private static final Map<AfkRules.Event, Integer> COUNTS = new EnumMap<>(AfkRules.Event.class);

	private Afk() {
	}

	/** Keyboard or mouse input (from the input mixins; any thread-safe write is enough). */
	public static void input() {
		lastInput = System.currentTimeMillis();
	}

	static boolean afk() {
		return afk;
	}

	/** How long you have been away, or 0. */
	static long awayMs() {
		return afk ? System.currentTimeMillis() - since : 0;
	}

	/** Frame cap while AFK (afk.low_fps), or {@code vanilla}. */
	public static int fpsLimit(int vanilla) {
		SkyPilot sp = SkyPilot.get();
		if (!afk || sp == null || !sp.config().on(Features.AFK_FPS)) return vanilla;
		return Math.min(vanilla, sp.config().afk.fps);
	}

	static void tick(Minecraft mc) {
		SkyPilot sp = SkyPilot.get();
		if (sp == null) return;
		SkyPilotConfig c = sp.config();
		long now = System.currentTimeMillis();
		if (!c.on(Features.AFK_DETECT)) {
			if (afk) back(sp, now, false);
			return;
		}
		if (afk && lastInput > since) {
			back(sp, now, true);
		} else if (!afk && AfkRules.isAfk(now - lastInput, c.afk.minutes)) {
			afk = true;
			since = lastInput;
			COUNTS.clear();
			healthAtStart = mc.player == null ? 0 : mc.player.getHealth();
			healthAlerted = false;
			disconnectAlerted = false;
		}
		if (!afk) return;
		if (mc.player != null && !healthAlerted && AfkRules.healthAlarm(mc.player.getHealth(), mc.player.getMaxHealth(), healthAtStart)) {
			healthAlerted = true;
			event(AfkRules.Event.HEALTH);
		}
		if (mc.gui.screen() instanceof DisconnectedScreen && !disconnectAlerted) {
			disconnectAlerted = true;
			event(AfkRules.Event.DISCONNECT);
		}
	}

	/** Every chat line (not the action bar). */
	static void chat(String line) {
		if (!afk) return;
		Minecraft mc = Minecraft.getInstance();
		AfkRules.Event e = AfkRules.classify(line, mc.player == null ? null : mc.player.getGameProfile().name());
		if (e != null) event(e);
	}

	private static void event(AfkRules.Event e) {
		COUNTS.merge(e, 1, Integer::sum);
		SkyPilot sp = SkyPilot.get();
		if (sp == null || !sp.config().on(Features.AFK_ALERTS)) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		Lang l = sp.lang();
		Feat.toast(Feat.icon(Items.BELL), l.t("While you are away", "離席中の通知"), 0xFFFFAA00, l.t(e.en, e.ja));
		if (now - lastAlert > 4_000) {
			lastAlert = now;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f));
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.5f));
		}
		if (!mc.isWindowActive()) GLFW.glfwRequestWindowAttention(mc.getWindow().handle());
	}

	private static void back(SkyPilot sp, long now, boolean greet) {
		afk = false;
		if (greet && sp.config().on(Features.AFK_SUMMARY)) {
			String s = AfkRules.summary(COUNTS, now - since, sp.lang());
			if (s != null) McText.info(s);
		}
		COUNTS.clear();
	}

	/** For tests: pretend the last input was {@code msAgo} ago. */
	static void idleFor(long msAgo) {
		lastInput = System.currentTimeMillis() - msAgo;
	}
}
