package dev.skypilot.mod.core;

import java.util.List;

/** Optional local rendering effects. No world/server settings are changed. */
public final class VisualSettings {
    public boolean zoom,killParticles,hitMarker,combo,healthBorder,crosshair,compass,nightVision;
	public boolean landingDust,rainSplash,fireflies,caveDust,underwaterBubbles,sprintTrail;
	public int ambientDensity=2;
	public static final List<String> SHADERS=List.of("none","cinematic","warm","cool","mono","sketch","bloom","sharp","vibrant","sunset");
    public double zoomFactor=4;
    public boolean smoothZoom=true;
    public String shader="none",particleStyle="spark",crosshairStyle="dot";
    public int particleCount=32,crosshairColor=0x55D6D2;
    public void sanitize(){
        if(!Double.isFinite(zoomFactor))zoomFactor=4;zoomFactor=Math.max(2,Math.min(12,zoomFactor));
        particleCount=Math.max(8,Math.min(96,particleCount));crosshairColor&=0xFFFFFF;
		ambientDensity=Math.max(1,Math.min(4,ambientDensity));
        if(!SHADERS.contains(shader==null?"":shader))shader="none";
        if(!List.of("spark","heart","soul","flame").contains(particleStyle==null?"":particleStyle))particleStyle="spark";
        if(!List.of("dot","cross","ring").contains(crosshairStyle==null?"":crosshairStyle))crosshairStyle="dot";
    }
}
