package dev.skypilot.mod.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Dark, keyboard-accessible buttons. Vanilla input and narration behavior is retained. */
public final class ClientButton extends Button {
    private ClientButton(int x,int y,int w,int h,Component label,OnPress press,CreateNarration narration) {
        super(x,y,w,h,label,press,narration);
    }
    static Builder create(Component label,OnPress press){return new Builder(label,press);}
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        paint(g,this);
    }
    public static void paint(GuiGraphicsExtractor g,net.minecraft.client.gui.components.AbstractWidget button){
        int x=button.getX(),y=button.getY(),w=button.getWidth(),h=button.getHeight();boolean highlight=button.active && button.isHoveredOrFocused();
        int border=highlight?0xFFA38BFA:0xFF354460;
        int fill=!button.active?0xFF111722:highlight?0xFF302D50:0xFF1B2538;
        g.fill(x+2,y,x+w-2,y+h,border);g.fill(x,y+2,x+w,y+h-2,border);
        g.fill(x+2,y+1,x+w-2,y+h-1,fill);g.fill(x+1,y+2,x+w-1,y+h-2,fill);
        if(highlight)g.fill(x+4,y+h-2,x+w-4,y+h-1,0xFF55D6D2);
        var font=Minecraft.getInstance().font;
        String label=font.plainSubstrByWidth(button.getMessage().getString(),Math.max(0,w-10));
        g.centeredText(font,Component.literal(label),x+w/2,y+(h-8)/2,button.active?Ui.WHITE:Ui.DARK_GRAY);
    }
    static final class Builder {
        private final Component label;private final OnPress press;
        private int x,y,w=200,h=20;private Tooltip tooltip;private CreateNarration narration=DEFAULT_NARRATION;
        Builder(Component label,OnPress press){this.label=label;this.press=press;}
        Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        Builder tooltip(Tooltip tooltip){this.tooltip=tooltip;return this;}
        Button build(){
            Button b=SkyPilot.get().config().appearance.richMenus?new ClientButton(x,y,w,h,label,press,narration):Button.builder(label,press).bounds(x,y,w,h).build();
            if(tooltip!=null)b.setTooltip(tooltip);return b;
        }
    }
}
