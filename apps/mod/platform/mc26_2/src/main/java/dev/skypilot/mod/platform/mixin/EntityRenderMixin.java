package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Performance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Performance features: leaves far, damage-number, falling-block, firework and lightning entities
 * out of the frame. Drawing only - the entities stay in the world and react as usual.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void skypilot$hide(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (Performance.hide(entity, camX, camY, camZ)) cir.setReturnValue(false);
	}
}
