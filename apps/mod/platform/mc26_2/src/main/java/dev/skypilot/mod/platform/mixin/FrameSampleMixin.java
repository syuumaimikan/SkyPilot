package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Performance;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FrameSampleMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void skypilot$frame(boolean renderLevel, CallbackInfo ci) { Performance.frame(); }
}
