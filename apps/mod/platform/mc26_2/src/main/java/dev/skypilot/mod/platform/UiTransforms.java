package dev.skypilot.mod.platform;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.hud.HudLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiTransforms {
    static final String[] ELEMENTS={"scoreboard","actionbar","bossbar","hotbar","effects","media","garden","dungeon","solver"};
    static SkyPilotConfig.UiPosition position(String id){return SkyPilot.get().config().uiPositions.computeIfAbsent(id,k->new SkyPilotConfig.UiPosition());}
    static HudLayout.Box base(String id,int w,int h){return switch(id){
        case "scoreboard"->new HudLayout.Box(w-160,h/2-65,155,130);
        case "actionbar"->new HudLayout.Box(w/2-130,h-72,260,12);
        case "bossbar"->new HudLayout.Box(w/2-92,12,184,32);
        case "hotbar"->new HudLayout.Box(w/2-92,h-48,184,48);
        case "effects"->new HudLayout.Box(w-100,1,98,50);
        case "media"->new HudLayout.Box(8,h-88,200,42);
        case "garden"->new HudLayout.Box(8,170,105,105);
        case "solver"->new HudLayout.Box(w/2-150,48,300,32);
        default->new HudLayout.Box(w-112,64,104,116);
    };}
    static HudLayout.Box box(String id,int w,int h){var b=base(id,w,h);var p=position(id);return new HudLayout.Box(b.x()+p.dx,b.y()+p.dy,(int)(b.width()*p.scale),(int)(b.height()*p.scale));}
    public static void begin(GuiGraphicsExtractor g,String id){var b=base(id,g.guiWidth(),g.guiHeight());begin(g,id,b.x(),b.y());}
    static void begin(GuiGraphicsExtractor g,String id,int x,int y){g.pose().pushMatrix();var sp=SkyPilot.get();if(sp==null)return;var p=position(id);g.pose().translate(x+p.dx,y+p.dy);g.pose().scale((float)p.scale,(float)p.scale);g.pose().translate(-x,-y);}
    public static void end(GuiGraphicsExtractor g){g.pose().popMatrix();}
    static String name(String id){return switch(id){case "scoreboard"->"スコアボード";case "actionbar"->"アクションバー";case "bossbar"->"ボスバー";case "hotbar"->"ホットバー/体力";case "effects"->"ステータス効果";case "media"->"メディア";case "garden"->"ペスト地図";case "solver"->"ダンジョン操作ガイド";default->"ダンジョンマップ";};}
}
