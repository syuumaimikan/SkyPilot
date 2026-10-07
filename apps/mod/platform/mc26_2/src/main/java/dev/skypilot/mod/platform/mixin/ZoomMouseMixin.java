package dev.skypilot.mod.platform.mixin;
import dev.skypilot.mod.platform.VisualEffects;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(MouseHandler.class)
abstract class ZoomMouseMixin {@ModifyArgs(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")) private void skypilot$precision(Args args){double scale=VisualEffects.mouseScale();args.set(0,(Double)args.get(0)*scale);args.set(1,(Double)args.get(1)*scale);}}
