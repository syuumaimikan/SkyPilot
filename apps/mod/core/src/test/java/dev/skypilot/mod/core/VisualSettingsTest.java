package dev.skypilot.mod.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VisualSettingsTest {
 @Test void corruptSettingsRecoverToBoundedEffects(){var cfg=new SkyPilotConfig();cfg.visuals=null;cfg.sanitize();var v=cfg.visuals;v.zoomFactor=Double.NaN;v.shader="../../other";v.particleStyle=null;v.crosshairStyle="unknown";v.particleCount=Integer.MAX_VALUE;v.crosshairColor=-1;cfg.sanitize();assertEquals(4,v.zoomFactor);assertEquals("none",v.shader);assertEquals("spark",v.particleStyle);assertEquals("dot",v.crosshairStyle);assertEquals(96,v.particleCount);assertEquals(0xFFFFFF,v.crosshairColor);v.zoomFactor=-1;v.particleCount=-1;cfg.sanitize();assertEquals(2,v.zoomFactor);assertEquals(8,v.particleCount);v.zoomFactor=100;cfg.sanitize();assertEquals(12,v.zoomFactor);}
 @Test void effectsAreOptIn(){var v=new VisualSettings();assertFalse(v.zoom||v.killParticles||v.hitMarker||v.combo||v.healthBorder||v.crosshair||v.compass);assertEquals("none",v.shader);}
}
