package dev.skypilot.mod.platform.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.skypilot.mod.platform.Performance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance features: lower frame-rate limit in the background and in menus (on top of Minecraft's own limits). */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void skypilot$cap(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(Performance.fpsLimit(cir.getReturnValue()));
	}
}
