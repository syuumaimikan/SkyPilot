package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.ClientButton;
import dev.skypilot.mod.platform.SkyPilot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restyle plain vanilla buttons without changing input, focus, tooltips or narration. */
@Mixin(targets="net.minecraft.client.gui.components.Button$Plain")
abstract class MenuButtonMixin {
    @Inject(method="extractContents",at=@At("HEAD"),cancellable=true)
    private void skypilot$button(GuiGraphicsExtractor g,int x,int y,float delta,CallbackInfo ci){
        var sp=SkyPilot.get();if(sp==null||!sp.config().appearance.richMenus)return;
        ClientButton.paint(g,(AbstractWidget)(Object)this);ci.cancel();
    }
}
