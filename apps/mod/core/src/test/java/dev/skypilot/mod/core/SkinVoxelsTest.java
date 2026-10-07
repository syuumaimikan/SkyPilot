package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class SkinVoxelsTest {
    @Test void ignoresTransparentAndBaseLayerPixels() {
        int[] pixels=new int[4096];pixels[8*64+8]=0xFFFFFFFF;
        assertTrue(SkinVoxels.build(pixels,false).isEmpty());
        pixels[8*64+40]=0xFFA38BFA;
        var voxels=SkinVoxels.build(pixels,false);assertEquals(1,voxels.size());
        var pixel=voxels.getFirst();assertEquals(0,pixel.part());assertEquals(-4,pixel.x());assertEquals(-8,pixel.y());assertTrue(pixel.z() < -4);
    }
    @Test void respectsSlimArmsAndBoundsTheMesh() {
        int[] pixels=new int[4096];Arrays.fill(pixels,0xFFFFFFFF);
        assertEquals(1632,SkinVoxels.build(pixels,false).size());
        assertEquals(1568,SkinVoxels.build(pixels,true).size());
        assertThrows(IllegalArgumentException.class,()->SkinVoxels.build(new int[2048],false));
    }
}
