package dev.skypilot.mod.platform.mixin;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface PostEffectAccessor {@Invoker("setPostEffect") void skypilot$setPostEffect(Identifier id);}
