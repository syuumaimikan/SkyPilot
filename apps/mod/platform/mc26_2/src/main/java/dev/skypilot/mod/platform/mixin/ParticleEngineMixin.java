package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Performance;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Performance features: skips hidden particle kinds and particles over the per-tick limit.
 *
 * <p>Never returns {@code null} for {@link ParticleTypes#FIREWORK} from {@code createParticle},
 * because vanilla {@link FireworkParticles.Starter} dereferences the returned {@code SparkParticle}
 * without a null check (handled safely in {@link FireworkStarterMixin} and {@link #skypilot$filterAdd}).
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void skypilot$filter(ParticleOptions options, double x, double y, double z, double xs, double ys, double zs, CallbackInfoReturnable<Particle> cir) {
		if (options != null && options.getType() == ParticleTypes.FIREWORK) return;
		if (!Performance.allowParticle(options, x, y, z)) cir.setReturnValue(null);
	}

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void skypilot$filterAdd(Particle particle, CallbackInfo ci) {
		if (particle != null && particle.getClass().getEnclosingClass() == FireworkParticles.class) {
			if (Performance.hideFireworkStarter()) ci.cancel();
		}
	}
}
