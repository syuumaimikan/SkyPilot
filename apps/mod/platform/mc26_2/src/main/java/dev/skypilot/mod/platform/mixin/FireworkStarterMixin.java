package dev.skypilot.mod.platform.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skypilot.mod.platform.Performance;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Safely filters firework sparks under SkyPilot's performance caps AND guards vanilla's
 * {@code FireworkParticles.Starter.createParticle} against {@code NullPointerException}
 * ({@code SparkParticle.setTrail}) whenever {@code ParticleEngine.createParticle} returns
 * {@code null} (e.g. during server resource-pack reloads or when any mod suppresses particles).
 */
@Mixin(FireworkParticles.Starter.class)
public abstract class FireworkStarterMixin {
	@Unique
	private boolean skypilot$nullSpark;

	@Inject(
			method = "createParticle(DDDDDDLit/unimi/dsi/fastutil/ints/IntList;Lit/unimi/dsi/fastutil/ints/IntList;ZZ)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void skypilot$filterFireworkSpark(
			double x, double y, double z, double xd, double yd, double zd,
			IntList colors, IntList fadeColors, boolean trail, boolean twinkle,
			CallbackInfo ci
	) {
		this.skypilot$nullSpark = false;
		if (!Performance.allowParticle(ParticleTypes.FIREWORK, x, y, z)) {
			ci.cancel();
		}
	}

	@WrapOperation(
			method = "createParticle(DDDDDDLit/unimi/dsi/fastutil/ints/IntList;Lit/unimi/dsi/fastutil/ints/IntList;ZZ)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/particle/ParticleEngine;createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"
			)
	)
	private Particle skypilot$trackNullSpark(
			ParticleEngine engine, ParticleOptions options,
			double x, double y, double z, double xd, double yd, double zd,
			Operation<Particle> original
	) {
		Particle p = original.call(engine, options, x, y, z, xd, yd, zd);
		this.skypilot$nullSpark = (p == null);
		return p;
	}

	@Inject(
			method = "createParticle(DDDDDDLit/unimi/dsi/fastutil/ints/IntList;Lit/unimi/dsi/fastutil/ints/IntList;ZZ)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/particle/FireworkParticles$SparkParticle;setTrail(Z)V"
			),
			cancellable = true
	)
	private void skypilot$cancelIfNullSpark(CallbackInfo ci) {
		if (this.skypilot$nullSpark) {
			this.skypilot$nullSpark = false;
			ci.cancel();
		}
	}
}

