package dev.skypilot.mod.core;

import java.util.List;

/** Optional local rendering effects. No world/server settings are changed. */
public final class VisualSettings {
    public boolean zoom,killParticles,hitMarker,combo,healthBorder,crosshair,compass;
    public double zoomFactor=4;
    public boolean smoothZoom=true;
    public String shader="none",particleStyle="spark",crosshairStyle="dot";
    public int particleCount=32,crosshairColor=0x55D6D2;
    public void sanitize(){
        if(!Double.isFinite(zoomFactor))zoomFactor=4;zoomFactor=Math.max(2,Math.min(12,zoomFactor));
        particleCount=Math.max(8,Math.min(96,particleCount));crosshairColor&=0xFFFFFF;
        if(!List.of("none","cinematic","warm","cool","mono","sketch").contains(shader==null?"":shader))shader="none";
        if(!List.of("spark","heart","soul","flame").contains(particleStyle==null?"":particleStyle))particleStyle="spark";
        if(!List.of("dot","cross","ring").contains(crosshairStyle==null?"":crosshairStyle))crosshairStyle="dot";
    }
}
