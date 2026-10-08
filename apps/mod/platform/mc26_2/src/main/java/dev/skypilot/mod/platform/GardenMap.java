package dev.skypilot.mod.platform;
import dev.skypilot.mod.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
final class GardenMap {
    static void world(){var mc=Minecraft.getInstance();if(!Feat.on(Features.GARDEN_MAP)||mc.player==null||!SkyPilot.get().context().islandId().equals("GARDEN"))return;try(var ignored=mc.levelRenderer.collectPerFrameRenderThreadGizmos()){for(int plot:GardenPlots.pests(Activity.tab)){int cell=GardenPlots.cell(plot);var target=new net.minecraft.world.phys.Vec3((cell%5-2)*96,mc.player.getY()+2,(cell/5-2)*96);net.minecraft.gizmos.Gizmos.billboardText("Pests · Plot "+plot,target,net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(0xFFFF7777)).setAlwaysOnTop();net.minecraft.gizmos.Gizmos.line(mc.player.getEyePosition().add(0,-.6,0),target,0xAAFF7777,2f);}}}
    static void render(GuiGraphicsExtractor g,boolean preview){
        var mc=Minecraft.getInstance();if(mc.player==null||!preview&&(!Feat.on(Features.GARDEN_MAP)||mc.gui.screen()!=null))return;
        var plots=GardenPlots.pests(Activity.tab);
        if(!preview&&!SkyPilot.get().context().islandId().equals("GARDEN")&&!SkyPilot.get().context().area().contains("Garden"))return;
        int x=8,y=170;UiTransforms.begin(g,"garden",x,y);g.fill(x,y,x+105,y+105,0xD010182B);g.text(mc.font,"Pests · N ↑",x+3,y+3,Ui.PURPLE,true);
        for(int i=0;i<25;i++){int px=x+3+i%5*20,py=y+16+i/5*17;int plot=GardenPlots.GRID[i];g.fill(px,py,px+18,py+15,plots.contains(plot)?0xCCB43C4C:0xCC27354B);g.text(mc.font,plot==0?"B":""+plot,px+3,py+3,plots.contains(plot)?0xFFFFFF88:Ui.WHITE,true);}
        int currentX=(int)Math.floor((mc.player.getX()+240)/96),currentZ=(int)Math.floor((mc.player.getZ()+240)/96);
        if(currentX>=0&&currentX<5&&currentZ>=0&&currentZ<5)g.outline(x+3+currentX*20,y+16+currentZ*17,18,15,0xFF55EEFF);
        UiTransforms.end(g);
    }
}
