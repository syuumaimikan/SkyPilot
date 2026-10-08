package dev.skypilot.mod.platform.mixin;
import dev.skypilot.mod.platform.UiTransforms;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
abstract class HudTransformMixin {
    @Inject(method="extractScoreboardSidebar",at=@At("HEAD")) private void scoreboard(GuiGraphicsExtractor g,DeltaTracker d,CallbackInfo ci){UiTransforms.begin(g,"scoreboard");}
    @Inject(method="extractOverlayMessage",at=@At("HEAD")) private void actionbar(GuiGraphicsExtractor g,DeltaTracker d,CallbackInfo ci){UiTransforms.begin(g,"actionbar");}
    @Inject(method="extractBossOverlay",at=@At("HEAD")) private void bossbar(GuiGraphicsExtractor g,DeltaTracker d,CallbackInfo ci){UiTransforms.begin(g,"bossbar");}
    @Inject(method="extractHotbarAndDecorations",at=@At("HEAD")) private void hotbar(GuiGraphicsExtractor g,DeltaTracker d,CallbackInfo ci){UiTransforms.begin(g,"hotbar");}
    @Inject(method="extractEffects",at=@At("HEAD")) private void effects(GuiGraphicsExtractor g,DeltaTracker d,CallbackInfo ci){UiTransforms.begin(g,"effects");}
    @Inject(method={"extractScoreboardSidebar","extractOverlayMessage","extractBossOverlay","extractHotbarAndDecorations","extractEffects"},at=@At("RETURN")) private void end(GuiGraphicsExtractor g,DeltaTracker d,CallbackInfo ci){UiTransforms.end(g);}
}
