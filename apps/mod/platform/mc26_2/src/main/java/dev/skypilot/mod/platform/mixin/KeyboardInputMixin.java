package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Afk;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFK guard: notes the time of keyboard input. Observes only; the key is handled as usual. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void skypilot$key(long window, int action, KeyEvent event, CallbackInfo ci) {
		Afk.input();
	}

	@Inject(method = "charTyped", at = @At("HEAD"))
	private void skypilot$char(long window, CharacterEvent event, CallbackInfo ci) {
		Afk.input();
	}
}
