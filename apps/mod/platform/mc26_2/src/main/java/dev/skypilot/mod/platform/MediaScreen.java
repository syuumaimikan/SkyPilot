package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit system media controls; no key binding needs to be configured. */
final class MediaScreen extends Screen {
    private final Screen parent;
    private final Map<String, Button> controls = new LinkedHashMap<>();
    private int x,w;
    MediaScreen(Screen parent){super(Component.literal("Media controls"));this.parent=parent;}
    private String t(String en,String ja){return SkyPilot.get().lang().t(en,ja);}
    @Override protected void init(){
        controls.clear();w=Math.min(340,width-24);x=(width-w)/2;
        addRenderableWidget(ClientButton.create(Component.literal(t("Enable media controls","メディア操作を有効化")),b->{var cfg=SkyPilot.get().config();Features.set(cfg,Features.MEDIA,true);SkyPilot.get().saveConfig();WindowsMedia.command("poll");}).bounds(x,44,w,20).build());
        String[][] buttons={{"previous","Previous","前の曲"},{"play","Play","再生"},{"pause","Pause","一時停止"},{"toggle","Play / pause","再生・停止切替"},{"next","Next","次の曲"}};
        for(int i=0;i<buttons.length;i++){var def=buttons[i];var b=ClientButton.create(Component.literal(t(def[1],def[2])),v->WindowsMedia.command(def[0])).bounds(x+(i%2)*((w+4)/2),112+(i/2)*24,(w-4)/2,20).build();controls.put(def[0],b);addRenderableWidget(b);}
        addRenderableWidget(ClientButton.create(Component.literal(t("Back","戻る")),b->onClose()).bounds(x,height-26,w,20).build());
        WindowsMedia.command("poll");
    }
    @Override public void tick(){controls.forEach((action,b)->b.active=Feat.on(Features.MEDIA)&&WindowsMedia.supports(action));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,t("Media controls","メディア操作"),width/2,22,Ui.WHITE);
        g.text(font,font.plainSubstrByWidth(WindowsMedia.title(),w),x,74,Ui.WHITE,false);
        g.text(font,font.plainSubstrByWidth(WindowsMedia.artist()+" · "+WindowsMedia.status(),w),x,90,Ui.GRAY,false);
        if(height>235){g.text(font,font.plainSubstrByWidth(WindowsMedia.controlMessage(),w),x,193,Ui.PURPLE,false);g.text(font,t("Windows media apps; unsupported actions are disabled.","Windows の再生アプリ用。非対応の操作は無効です。"),x,210,Ui.GRAY,false);}
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
