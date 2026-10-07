package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.CosmeticMesh;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Lit 3D studio mannequin, available before joining a world; uses exactly the world cosmetic mesh. */
final class WardrobeModel {
    static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("skypilot","wardrobe_palette");
    private static final List<Integer> COLORS=new ArrayList<>(List.of(0x9CAABC,0x394961,0x455673,0x253247,0x172031,0x55D6D2));
    private static final Map<String,Model.Simple> CACHE=new LinkedHashMap<>();
    private static boolean ready;
    static Model.Simple get(SkyPilotConfig.Appearance a) {
        if(!ready) {
            for(var item:CosmeticCatalog.ITEMS){var sample=new SkyPilotConfig.Appearance();item.equip(sample);for(var v:CosmeticMesh.build(sample))if(!COLORS.contains(v.color()))COLORS.add(v.color());}
            uploadPalette();ready=true;
        }
        if(!COLORS.contains(a.color)){
            if(COLORS.size()>=256){COLORS.removeLast();CACHE.clear();}
            COLORS.add(a.color);uploadPalette();
        }
        String key=a.cape+":"+a.hat+":"+a.back+":"+a.color;
        if(CACHE.containsKey(key))return CACHE.get(key);
        List<ModelPart.Cube> cubes=new ArrayList<>();
        box(cubes,0,1.65,0,.49,.49,.49,0x9CAABC);box(cubes,0,1.08,0,.50,.65,.25,0x394961);
        box(cubes,-.39,1.08,0,.24,.65,.25,0x455673);box(cubes,.39,1.08,0,.24,.65,.25,0x455673);
        box(cubes,-.13,.43,0,.24,.65,.25,0x253247);box(cubes,.13,.43,0,.24,.65,.25,0x253247);
        box(cubes,-.13,.07,.04,.25,.13,.33,0x172031);box(cubes,.13,.07,.04,.25,.13,.33,0x172031);
        box(cubes,-.12,1.68,.249,.065,.045,.009,0x253247);box(cubes,.12,1.68,.249,.065,.045,.009,0x253247);
        box(cubes,0,1.27,.13,.10,.12,.015,0x55D6D2);
        for(var v:CosmeticMesh.build(a))box(cubes,v.x(),v.y(),v.z(),v.size(),v.size(),v.size(),v.color());
        Model.Simple model=new Model.Simple(new ModelPart(cubes,Map.of()),RenderTypes::entityCutout);
        if(CACHE.size()>=32)CACHE.remove(CACHE.keySet().iterator().next());CACHE.put(key,model);return model;
    }
    private static void uploadPalette(){
            NativeImage image=new NativeImage(16,16,false);
            for(int i=0;i<256;i++)image.setPixel(i%16,i/16,0xFF000000|(i<COLORS.size()?COLORS.get(i):0xFFFFFF));
            Minecraft.getInstance().getTextureManager().register(TEXTURE,new DynamicTexture(()->"SkyPilot wardrobe palette",image));
    }
    private static void box(List<ModelPart.Cube> cubes,double x,double y,double z,double w,double h,double d,int color) {
        var cube=new ModelPart.Cube(0,0,(float)((x-w/2)*16),(float)((1.5-y-h/2)*16),(float)((-z-d/2)*16),(float)(w*16),(float)(h*16),(float)(d*16),0,0,0,false,16,16,EnumSet.allOf(Direction.class));
        int index=COLORS.indexOf(color);if(index<0)index=0;
        for(int i=0;i<cube.polygons.length;i++){
            var p=cube.polygons[i];var vertices=p.vertices().clone();for(int j=0;j<vertices.length;j++)vertices[j]=vertices[j].remap((index%16+.5f)/16,(index/16+.5f)/16);cube.polygons[i]=new ModelPart.Polygon(vertices,p.normal());
        }
        cubes.add(cube);
    }
}
