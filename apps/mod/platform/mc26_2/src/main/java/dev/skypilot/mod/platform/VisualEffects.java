package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.VisualSettings;
import dev.skypilot.mod.platform.mixin.PostEffectAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import java.util.*;

/** Optional local visuals, using received damage events; no combat or movement automation. */
public final class VisualEffects {
    private record Hit(LivingEntity entity,long at) {}
    private static final Map<Integer,Hit> hits=new LinkedHashMap<>();
    private static long hitAt,lastKill;
    private static int streak;
    private static double zoom=1;
    private static KeyMapping zoomKey;
    private static Identifier applied;
    static int bursts;
    static void key(KeyMapping key){zoomKey=key;}
    private static long now(){return System.nanoTime()/1_000_000;}
    public static void hurt(LivingEntity target,DamageSource damage){
        var mc=Minecraft.getInstance();var sp=SkyPilot.get();if(sp==null||mc.player==null||mc.level==null||target.level()!=mc.level||target==mc.player)return;
        if(damage.getEntity()!=mc.player){hits.remove(target.getId());return;}
        var a=sp.config().visuals;if(!a.killParticles&&!a.hitMarker&&!a.combo)return;
        if(target.isInvisibleTo(mc.player)||target.distanceToSqr(mc.player)>4096)return;
        if(hits.size()>=128)hits.remove(hits.keySet().iterator().next());hits.put(target.getId(),new Hit(target,now()));hitAt=now();
    }
    static void tick(Minecraft mc){
        var a=SkyPilot.get().config().visuals;
        boolean held=a.zoom&&zoomKey!=null&&zoomKey.isDown()&&mc.player!=null&&mc.gui.screen()==null;
        double target=held?1/a.zoomFactor:1;
        zoom=mc.gui.screen()!=null||mc.player==null?1:a.smoothZoom?zoom+(target-zoom)*.35:target;
        long now=now();if(mc.level==null||mc.player==null){hits.clear();streak=0;hitAt=0;}
        else for(var iterator=hits.entrySet().iterator();iterator.hasNext();){var hit=iterator.next().getValue();if(hit.entity.level()!=mc.level||now-hit.at>8000){iterator.remove();continue;}if(hit.entity.isDeadOrDying()){if(!hit.entity.isInvisibleTo(mc.player)){streak=now-lastKill<=8000?streak+1:1;lastKill=now;if(a.killParticles)burst(mc,hit.entity,a);}iterator.remove();}else if(hit.entity.isRemoved())iterator.remove();}
        shader(mc,a.shader);
    }
    private static void burst(Minecraft mc,LivingEntity entity,VisualSettings a){
        var type=switch(a.particleStyle){case "heart"->ParticleTypes.HEART;case "soul"->ParticleTypes.SOUL_FIRE_FLAME;case "flame"->ParticleTypes.FLAME;default->ParticleTypes.END_ROD;};
        for(int i=0;i<a.particleCount;i++){double angle=i*Math.PI*2/a.particleCount;mc.level.addParticle(type,entity.getX(),entity.getY()+entity.getBbHeight()*.55,entity.getZ(),Math.cos(angle)*.12,.05+(i%5)*.025,Math.sin(angle)*.12);}bursts++;
    }
    public static float fov(float original){return (float)(original*zoom);}
    public static double mouseScale(){return zoom;}
    static void shader(Minecraft mc,String name){
        Identifier desired="none".equals(name)||mc.level==null||mc.getCameraEntity()!=mc.player?null:Identifier.fromNamespaceAndPath("skypilot",name);
        Identifier current=mc.gameRenderer.currentPostEffect();
        if(applied!=null&&!applied.equals(current))applied=null;
        if(desired==null){if(applied!=null&&applied.equals(current))mc.gameRenderer.clearPostEffect();applied=null;return;}
        if(current!=null&&(applied==null||!current.equals(applied)))return;
        if(!desired.equals(current)){((PostEffectAccessor)mc.gameRenderer).skypilot$setPostEffect(desired);applied=desired;}
    }
    static void hud(GuiGraphicsExtractor g,net.minecraft.client.DeltaTracker delta){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.gui.hud.isHidden()||mc.gui.screen()!=null)return;var a=SkyPilot.get().config().visuals;int w=g.guiWidth(),h=g.guiHeight(),cx=w/2,cy=h/2;long now=now();
        if(a.healthBorder&&mc.player.getHealth()<mc.player.getMaxHealth()*.3){int color=0x70E34D67;g.fill(0,0,w,3,color);g.fill(0,h-3,w,h,color);g.fill(0,0,3,h,color);g.fill(w-3,0,w,h,color);}
        if(a.hitMarker&&now-hitAt<250){for(int i=4;i<8;i++){g.fill(cx-i,cy-i,cx-i+1,cy-i+1,0xFFDDEAFF);g.fill(cx+i,cy-i,cx+i+1,cy-i+1,0xFFDDEAFF);g.fill(cx-i,cy+i,cx-i+1,cy+i+1,0xFFDDEAFF);g.fill(cx+i,cy+i,cx+i+1,cy+i+1,0xFFDDEAFF);}}
        if(customCrosshair()&&mc.options.getCameraType().isFirstPerson()){int color=0xFF000000|a.crosshairColor;if("dot".equals(a.crosshairStyle))g.fill(cx-1,cy-1,cx+2,cy+2,color);else if("cross".equals(a.crosshairStyle)){g.fill(cx-6,cy,cx-2,cy+1,color);g.fill(cx+3,cy,cx+7,cy+1,color);g.fill(cx,cy-6,cx+1,cy-2,color);g.fill(cx,cy+3,cx+1,cy+7,color);}else for(int i=0;i<24;i++){int x=cx+(int)Math.round(Math.cos(i*Math.PI/12)*5),y=cy+(int)Math.round(Math.sin(i*Math.PI/12)*5);g.fill(x,y,x+1,y+1,color);}}
        if(a.combo&&streak>0&&now-lastKill<8000)g.centeredText(mc.font,"COMBO ×"+streak,cx,cy+20,0xFF55D6D2);
        if(a.compass){String[] directions={"S","SW","W","NW","N","NE","E","SE"};int index=Math.floorMod(Math.round(mc.player.getYRot()/45),8);g.centeredText(mc.font,directions[index]+" · "+Math.floorMod(Math.round(mc.player.getYRot()),360)+"°",cx,6,0xFFDDEAFF);}
    }
    public static boolean customCrosshair(){var sp=SkyPilot.get();var mc=Minecraft.getInstance();return sp!=null&&sp.config().visuals.crosshair&&mc.player!=null&&!mc.player.isSpectator()&&!mc.debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);}
    static void reset(){hits.clear();hitAt=0;streak=0;zoom=1;}
}
