package dev.skypilot.mod.core;

import java.util.*;

/** Opaque pixels of the 64x64 outer skin layer, extruded in each limb's local coordinates. */
public final class SkinVoxels {
    public record Pixel(int part,int u,int v,float x,float y,float z,float w,float h,float d) {}
    private SkinVoxels() {}
    public static List<Pixel> build(int[] argb,boolean slim) {
        if(argb.length!=4096)throw new IllegalArgumentException("A 64x64 skin is required.");
        List<Pixel> out=new ArrayList<>();
        box(out,argb,0,32,0,-4,-8,-4,8,8,8);
        box(out,argb,1,16,32,-4,0,-2,8,12,4);
        box(out,argb,2,40,32,slim?-2:-3,-2,-2,slim?3:4,12,4);
        box(out,argb,3,48,48,-1,-2,-2,slim?3:4,12,4);
        box(out,argb,4,0,32,-2,0,-2,4,12,4);
        box(out,argb,5,0,48,-2,0,-2,4,12,4);
        return List.copyOf(out);
    }
    private static void box(List<Pixel> out,int[] a,int p,int u,int v,int x,int y,int z,int w,int h,int d) {
        float t=0.75f;
        for(int row=0;row<h;row++) {
            for(int col=0;col<w;col++) {
                pixel(out,a,new Pixel(p,u+d+col,v+d+row,x+col,y+row,z-t,1,1,t));
                pixel(out,a,new Pixel(p,u+2*d+w+col,v+d+row,x+w-1-col,y+row,z+d,1,1,t));
            }
            for(int col=0;col<d;col++) {
                pixel(out,a,new Pixel(p,u+col,v+d+row,x-t,y+row,z+d-1-col,t,1,1));
                pixel(out,a,new Pixel(p,u+d+w+col,v+d+row,x+w,y+row,z+col,t,1,1));
            }
        }
        for(int row=0;row<d;row++)for(int col=0;col<w;col++) {
            pixel(out,a,new Pixel(p,u+d+col,v+row,x+col,y-t,z+row,1,t,1));
            pixel(out,a,new Pixel(p,u+d+w+col,v+row,x+col,y+h,z+d-1-row,1,t,1));
        }
    }
    private static void pixel(List<Pixel> out,int[] a,Pixel p){if((a[p.v*64+p.u]>>>24)>=128)out.add(p);}
}
