package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.ClientButton;
import dev.skypilot.mod.platform.SkyPilot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only rendering changes: drag, keyboard input, option updates and narration stay vanilla. */
@Mixin(AbstractSliderButton.class)
abstract class MenuSliderMixin {
    @Shadow protected double value;
    @Shadow protected abstract void handleCursor(GuiGraphicsExtractor g);
    @Inject(method="extractWidgetRenderState",at=@At("HEAD"),cancellable=true)
    private void skypilot$slider(GuiGraphicsExtractor g,int mx,int my,float delta,CallbackInfo ci){
        var sp=SkyPilot.get();if(sp==null||!sp.config().appearance.richMenus)return;
        var button=(AbstractWidget)(Object)this;ClientButton.paint(g,button);
        int x=button.getX()+5,y=button.getY()+button.getHeight()-4,w=Math.max(0,button.getWidth()-10);
        int position=(int)(Math.max(0,Math.min(1,value))*w);
        g.fill(x,y,x+w,y+1,0xFF354460);g.fill(x,y,x+position,y+1,button.active?0xFF55D6D2:0xFF546174);
        g.fill(x+position-1,y-1,x+position+2,y+2,button.active?0xFFA38BFA:0xFF546174);handleCursor(g);ci.cancel();
    }
}
