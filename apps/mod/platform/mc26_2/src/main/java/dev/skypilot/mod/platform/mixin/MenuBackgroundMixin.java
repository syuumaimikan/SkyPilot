package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.ClientStyle;
import dev.skypilot.mod.platform.SkyPilot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Consistent styling for the original world, server and options flows; containers retain world visibility. */
@Mixin(Screen.class)
abstract class MenuBackgroundMixin {
    @Inject(method="extractBackground",at=@At("HEAD"),cancellable=true)
    private void skypilot$background(GuiGraphicsExtractor g,int x,int y,float delta,CallbackInfo ci) {
        var sp=SkyPilot.get();Screen screen=(Screen)(Object)this;
        if(sp==null||!sp.config().appearance.richMenus||screen instanceof AbstractContainerScreen<?>)return;
        ClientStyle.backdrop(g,screen.width,screen.height);ci.cancel();
    }
}
