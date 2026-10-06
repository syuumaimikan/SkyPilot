package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.Performance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performance feature: no block break / mining crumbs. Only the particles are skipped; breaking, sounds and drops are untouched. */
@Mixin(ClientLevel.class)
public abstract class BlockBreakParticlesMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void skypilot$destroy(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (Performance.hideBreakParticles()) ci.cancel();
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	private void skypilot$breaking(BlockPos pos, Direction side, CallbackInfo ci) {
		if (Performance.hideBreakParticles()) ci.cancel();
	}
}
