package dev.skypilot.mod.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** All optional effects can be disabled and reset without modifying vanilla video options. */
final class VisualScreen extends Screen {
    private final Screen parent;
    VisualScreen(Screen parent){super(Component.literal("Visual studio"));this.parent=parent;}
    private String t(String en,String ja){return SkyPilot.get().lang().t(en,ja);}
    private String state(boolean value){return value?t("On","オン"):t("Off","オフ");}
    private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(ClientButton.create(Component.literal(label),b->{action.run();SkyPilot.get().saveConfig();rebuildWidgets();}).bounds(x,y,w,18).build());}
    private static String next(String value,List<String> options){return options.get((options.indexOf(value)+1)%options.size());}
    @Override protected void init(){var a=SkyPilot.get().config().visuals;int x=width/2-154,y=48,w=152;
        button(t("Zoom: ","ズーム: ")+state(a.zoom),x,y,w,()->a.zoom=!a.zoom);
        button(t("Magnification: ","倍率: ")+a.zoomFactor+"×",x+156,y,w,()->a.zoomFactor=a.zoomFactor>=12?2:a.zoomFactor==2?4:a.zoomFactor==4?8:12);
        button(t("Smooth zoom: ","滑らかズーム: ")+state(a.smoothZoom),x,y+22,w,()->a.smoothZoom=!a.smoothZoom);
        button(t("Shader: ","シェーダー: ")+a.shader,x+156,y+22,w,()->a.shader=next(a.shader,List.of("none","cinematic","warm","cool","mono","sketch")));
        button(t("Kill particles: ","キル演出: ")+state(a.killParticles),x,y+44,w,()->a.killParticles=!a.killParticles);
        button(t("Particle style: ","演出種類: ")+a.particleStyle,x+156,y+44,w,()->a.particleStyle=next(a.particleStyle,List.of("spark","heart","soul","flame")));
        button(t("Particle count: ","粒子数: ")+a.particleCount,x,y+66,w,()->a.particleCount=a.particleCount>=96?8:a.particleCount*2);
        button(t("Hit marker: ","ヒットマーカー: ")+state(a.hitMarker),x+156,y+66,w,()->a.hitMarker=!a.hitMarker);
        button(t("Combo: ","連続キル表示: ")+state(a.combo),x,y+88,w,()->a.combo=!a.combo);
        button(t("Low health border: ","低体力の枠: ")+state(a.healthBorder),x+156,y+88,w,()->a.healthBorder=!a.healthBorder);
        button(t("Custom crosshair: ","照準カスタム: ")+state(a.crosshair),x,y+110,w,()->a.crosshair=!a.crosshair);
        button(t("Crosshair: ","照準種類: ")+a.crosshairStyle,x+156,y+110,w,()->a.crosshairStyle=next(a.crosshairStyle,List.of("dot","cross","ring")));
        button(t("Compass: ","方角表示: ")+state(a.compass),x,y+132,w,()->a.compass=!a.compass);
        button(t("Color: ","色: ")+String.format("#%06X",a.crosshairColor),x+156,y+132,w,()->{var p=dev.skypilot.mod.core.WardrobeTools.PALETTE;int i=0;while(i<p.length&&p[i]!=a.crosshairColor)i++;a.crosshairColor=p[(i+1)%p.length];});
        button(t("Reset all effects","演出をリセット"),x,height-26,w,()->SkyPilot.get().config().visuals=new dev.skypilot.mod.core.VisualSettings());
        addRenderableWidget(ClientButton.create(Component.literal(t("Back","戻る")),b->onClose()).bounds(x+156,height-26,w,18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){ClientStyle.backdrop(g,width,height);g.centeredText(font,"VISUAL STUDIO",width/2,12,Ui.PURPLE);g.centeredText(font,t("Hold C to zoom · change binding in Controls","C長押しでズーム · キーは操作設定で変更可"),width/2,28,Ui.GRAY);super.extractRenderState(g,mx,my,delta);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
