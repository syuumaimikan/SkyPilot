package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Performance;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance features: skips hidden particle kinds and particles over the per-tick limit (null = not created, as vanilla does for unknown kinds). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void skypilot$filter(ParticleOptions options, double x, double y, double z, double xs, double ys, double zs, CallbackInfoReturnable<Particle> cir) {
		if (!Performance.allowParticle(options)) cir.setReturnValue(null);
	}
}
