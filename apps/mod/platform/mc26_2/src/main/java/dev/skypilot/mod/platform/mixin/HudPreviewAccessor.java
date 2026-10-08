package dev.skypilot.mod.platform.mixin;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Hud.class)
public interface HudPreviewAccessor {
 @Invoker("extractScoreboardSidebar") void skypilot$scoreboard(GuiGraphicsExtractor g,DeltaTracker d);
 @Invoker("extractOverlayMessage") void skypilot$actionbar(GuiGraphicsExtractor g,DeltaTracker d);
 @Invoker("extractBossOverlay") void skypilot$bossbar(GuiGraphicsExtractor g,DeltaTracker d);
 @Invoker("extractHotbarAndDecorations") void skypilot$hotbar(GuiGraphicsExtractor g,DeltaTracker d);
 @Invoker("extractEffects") void skypilot$effects(GuiGraphicsExtractor g,DeltaTracker d);
}
