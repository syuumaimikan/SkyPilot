package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.VisualEffects;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes the rendered lightmap only, preserving vanilla darkness effects. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class NightVisionMixin {
    @Inject(method="extract",at=@At("RETURN"))
    private void skypilot$nightVision(LightmapRenderState state,float partialTick,CallbackInfo ci){
        if(VisualEffects.nightVision()){
            state.needsUpdate=true;
            state.nightVisionEffectIntensity=1;
            state.nightVisionColor=LightmapRenderStateExtractor.WHITE;
        }
    }
}
