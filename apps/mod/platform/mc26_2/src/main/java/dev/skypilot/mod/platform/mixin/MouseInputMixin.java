package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Afk;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFK guard: notes the time of mouse input. Observes only; the mouse is handled as usual. */
@Mixin(MouseHandler.class)
public abstract class MouseInputMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void skypilot$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		Afk.input();
	}

	@Inject(method = "onScroll", at = @At("HEAD"))
	private void skypilot$scroll(long window, double x, double y, CallbackInfo ci) {
		Afk.input();
	}

	@Inject(method = "onMove", at = @At("HEAD"))
	private void skypilot$move(long window, double x, double y, CallbackInfo ci) {
		Afk.input();
	}
}
