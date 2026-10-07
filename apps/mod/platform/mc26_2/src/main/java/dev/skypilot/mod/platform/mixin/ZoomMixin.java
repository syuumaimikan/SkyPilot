package dev.skypilot.mod.platform.mixin;
import dev.skypilot.mod.platform.VisualEffects;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Camera.class)
abstract class ZoomMixin {@Inject(method="calculateFov",at=@At("RETURN"),cancellable=true) private void skypilot$zoom(float partial,CallbackInfoReturnable<Float> cir){cir.setReturnValue(VisualEffects.fov(cir.getReturnValue()));}}
