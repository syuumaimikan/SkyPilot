package dev.skypilot.mod.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only values; no packets or movement changes. Time uses a monotonic session clock. */
final class UtilityHud {
    private static long joined=System.nanoTime();
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm:ss");
    static void join(){joined=System.nanoTime();}
    static List<String> rows(Minecraft mc){
        var rows=new ArrayList<String>();if(mc.player==null||mc.level==null)return rows;
        var u=SkyPilot.get().config().utility;var l=SkyPilot.get().lang();var p=mc.player;
        if(u.coordinates){var pos=p.blockPosition();rows.add("XYZ  "+pos.getX()+" / "+pos.getY()+" / "+pos.getZ());}
        if(u.fps)rows.add("FPS  "+mc.getFps());
        if(u.ping){var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(p.getUUID());rows.add(l.t("Ping  ","遅延  ")+(info==null?"—":Math.max(0,info.getLatency())+" ms"));}
        if(u.clock)rows.add(l.t("Clock  ","時計  ")+LocalTime.now().format(CLOCK));
        if(u.speed)rows.add(l.t("Speed  ","速度  ")+String.format(Locale.ROOT,"%.1f m/s",p.getDeltaMovement().horizontalDistance()*20));
        if(u.durability){var item=p.getMainHandItem();if(!item.isDamageableItem())item=p.getOffhandItem();rows.add(l.t("Durability  ","耐久値  ")+(item.isDamageableItem()?(item.getMaxDamage()-item.getDamageValue())+" / "+item.getMaxDamage():"—"));}
        if(u.slots){int free=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).isEmpty())free++;rows.add(l.t("Free slots  ","空き  ")+free+" / 36");}
        if(u.focus)rows.add(l.t("Focus  ","集中  ")+DeskRuntime.FOCUS.display(DeskRuntime.now()));
        if(u.session){long seconds=Math.max(0,(System.nanoTime()-joined)/1_000_000_000);rows.add(l.t("Session  ","プレイ  ")+String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));}
        if(u.worldTime){long minutes=Math.floorMod(mc.level.getDefaultClockTime()+6000,24000)*60/1000;rows.add(l.t("World  ","ゲーム内  ")+String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60));}
        return rows;
    }
    static void render(GuiGraphicsExtractor g,DeltaTracker delta){var mc=Minecraft.getInstance();if(mc.player==null||mc.gui.hud.isHidden()||mc.gui.screen()!=null)return;var rows=rows(mc);if(rows.isEmpty())return;var u=SkyPilot.get().config().utility;int width=rows.stream().mapToInt(mc.font::width).max().orElse(0),x=u.right?g.guiWidth()-width-10:10,y=24;if(u.background)g.fill(x-5,y-5,x+width+5,y+rows.size()*11+2,0xB510182B);for(String row:rows){g.text(mc.font,row,x,y,Ui.WHITE,true);y+=11;}}
}
