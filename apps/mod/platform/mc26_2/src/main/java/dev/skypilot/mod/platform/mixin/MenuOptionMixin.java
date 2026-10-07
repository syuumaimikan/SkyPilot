package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.ClientButton;
import dev.skypilot.mod.platform.SkyPilot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CycleButton.class)
abstract class MenuOptionMixin {
    @Inject(method="extractContents",at=@At("HEAD"),cancellable=true)
    private void skypilot$option(GuiGraphicsExtractor g,int x,int y,float delta,CallbackInfo ci){
        var sp=SkyPilot.get();if(sp==null||!sp.config().appearance.richMenus)return;
        ClientButton.paint(g,(AbstractWidget)(Object)this);ci.cancel();
    }
}
