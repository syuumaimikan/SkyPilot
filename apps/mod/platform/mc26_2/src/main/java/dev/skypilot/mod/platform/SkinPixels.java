package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Small CPU cache. Reuses textures Minecraft has already loaded; never downloads anything. */
public final class SkinPixels {
    private static final Map<Identifier,int[]> CACHE=new LinkedHashMap<>();
    private SkinPixels() {}
    public static synchronized void capture(ClientAsset.Texture texture,NativeImage image) {
        if(image.getWidth()!=64 || image.getHeight()!=64)return;
        capture(texture.texturePath(), image);
    }
    private static synchronized void capture(Identifier texture, NativeImage image) {
        CACHE.put(texture,image.getPixels());
        while(CACHE.size()>8)CACHE.remove(CACHE.keySet().iterator().next());
    }
    static synchronized int[] get(Identifier texture) {
        int[] cached=CACHE.get(texture);if(cached!=null)return cached;
        var mc=Minecraft.getInstance();
        var loaded=mc.getTextureManager().getTexture(texture);
        if(loaded instanceof net.minecraft.client.renderer.texture.DynamicTexture dynamic) {
            var image=dynamic.getPixels();
            if(image!=null && !image.isClosed() && image.getWidth()==64 && image.getHeight()==64){capture(texture,image);return CACHE.get(texture);}
        }
        var resource=mc.getResourceManager().getResource(texture);
        if(resource.isPresent())try(var in=resource.get().open();var image=NativeImage.read(in)) {
            if(image.getWidth()==64 && image.getHeight()==64){capture(texture,image);return CACHE.get(texture);}
        }catch(java.io.IOException ignored){}
        return null;
    }
}
