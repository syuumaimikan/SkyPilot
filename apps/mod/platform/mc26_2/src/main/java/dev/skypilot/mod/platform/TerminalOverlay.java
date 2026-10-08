package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.TerminalPlan;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** Server-confirmed terminal guide; a separate instance for each opened container. */
final class TerminalOverlay {
    private final AbstractContainerScreen<?> screen;
    private int previous=-1,direction,samples; private long lastChange; private double period;
    TerminalOverlay(AbstractContainerScreen<?> screen){this.screen=screen;}
    static boolean active(AbstractContainerScreen<?> screen){return SkyPilot.get().config().dungeonSolver&&DungeonMap.inDungeon()&&TerminalPlan.recognizes(ExperimentOverlay.clean(screen.getTitle().getString()));}
    void render(GuiGraphicsExtractor g,int mouseX,int mouseY){
        if(!active(screen))return;
        var box=(ContainerScreenAccessor)screen;var font=Minecraft.getInstance().font;
        var cells=new ArrayList<TerminalPlan.Cell>();
        for(var s:screen.getMenu().slots){if(s.container instanceof Inventory)continue;var item=s.getItem();cells.add(new TerminalPlan.Cell(s.index,BuiltInRegistries.ITEM.getKey(item.getItem()).getPath(),ExperimentOverlay.clean(item.getHoverName().getString()),item.getCount(),item.hasFoil()));}
        String title=ExperimentOverlay.clean(screen.getTitle().getString());
        if(cells.size()%9!=0||cells.size()<27||cells.size()>54)return;
        var plan=TerminalPlan.solve(title,cells,cells.size()/9);String timing="次をクリック / サーバーの更新ごとに再計算";
        if(title.equals("Click the button on time!")) {
            // Locate the moving marker and the target column from visible panes, not a timer started locally.
            var markers=cells.stream().filter(c->c.item().equals("lime_stained_glass_pane")&&c.slot()%9>=1&&c.slot()%9<=5&&c.slot()>=9).toList();
            var targets=cells.stream().filter(c->c.item().equals("magenta_stained_glass_pane")&&c.slot()<9).toList();
            plan=List.of();timing="盤面の更新待ち";
            if(markers.size()==1&&targets.size()==1){int pos=markers.getFirst().slot(),col=pos%9,target=targets.getFirst().slot()%9;long now=System.currentTimeMillis();
                if(pos!=previous){if(previous>=0&&pos/9==previous/9&&Math.abs(col-previous%9)==1){double step=now-lastChange;if(step>=30&&step<=1500){period=period==0?step:period*.7+step*.3;samples++;direction=Integer.signum(col-previous%9);}else{samples=0;period=0;}}else{samples=0;period=0;direction=0;}previous=pos;lastChange=now;}
                int button=pos/9*9+7;boolean visibleButton=cells.stream().anyMatch(c->c.slot()==button&&(c.item().endsWith("_terracotta")||c.item().equals("red_stained_glass_pane")));
                if(visibleButton){plan=List.of(new TerminalPlan.Click(button,0,1));
                    if(now-lastChange>1500)timing="通信更新待ち — タイミング不明";
                    else if(col==target)timing="今クリック！ / 表示中の列が一致";
                    else if(samples>=3&&period>0&&now-lastChange<period*3){int steps=direction>0?(target>col?target-col:5-col+5-target):(target<col?col-target:col-1+target-1);double wait=Math.max(0,(steps*period-(now-lastChange))/1000);timing=String.format(Locale.ROOT,"約 %.2f 秒後 / 通信遅延で変動",wait);}
                    else timing="列が一致したらクリック / 周期を測定中";
                }
            }
        }
        int labelY=Math.max(4,box.skypilot$topPos()-30);
        g.fill(box.skypilot$leftPos(),labelY,Math.min(g.guiWidth(),box.skypilot$leftPos()+310),labelY+26,0xE610182B);
        g.text(font,"SkyPilot · "+(plan.isEmpty()?"更新待ち / 完了時は閉じてください":"残り "+plan.stream().mapToInt(TerminalPlan.Click::count).sum()+" クリック"),box.skypilot$leftPos()+4,labelY+3,0xFFAA99FF,false);
        g.text(font,timing,box.skypilot$leftPos()+4,labelY+15,0xFFFFFF99,false);
        int order=0,lastX=mouseX,lastY=mouseY;
        for(var click:plan){if(click.slot()<0||click.slot()>=screen.getMenu().slots.size())continue;var slot=screen.getMenu().slots.get(click.slot());int x=box.skypilot$leftPos()+slot.x,y=box.skypilot$topPos()+slot.y;
            int color=order==0?0xFF55FF99:0xAA55DDFF;g.outline(x-1,y-1,18,18,color);
            if(order<3)line(g,lastX,lastY,x+8,y+8,color);
            g.text(font,""+(++order),x,y,0xFFFFFFFF,true);
            if(click.count()>1||click.button()==1)g.text(font,(click.button()==1?"R":"L")+click.count(),x,y+9,0xFFFFFF55,true);
            lastX=x+8;lastY=y+8;
        }
    }
    private static void line(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int color){int steps=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));for(int i=0;i<=steps;i+=2){double t=steps==0?0:(double)i/steps;int x=(int)Math.round(x0+(x1-x0)*t),y=(int)Math.round(y0+(y1-y0)*t);g.fill(x,y,x+2,y+2,color);}}
}
