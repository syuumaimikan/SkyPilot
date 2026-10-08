package dev.skypilot.mod.platform;
import dev.skypilot.mod.core.Features;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.MapItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.Arrays;
final class DungeonMap {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("skypilot","dungeon_map");
    private static byte[] previous;private static MapItemSavedData current;private static long next;
    static boolean inDungeon(){var sp=SkyPilot.get();return sp!=null&&sp.context().inSkyBlock()&&(sp.context().islandId().equals("DUNGEON")||sp.context().area().contains("Catacombs")||sp.context().area().contains("Dungeon")||Activity.dungeon!=null);}
    static void render(GuiGraphicsExtractor g,boolean preview){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!preview&&(!Feat.on(Features.DUNGEON_MAP)||!inDungeon()||mc.gui.screen()!=null))return;
        if(System.currentTimeMillis()>next){next=System.currentTimeMillis()+250;current=null;for(int i=0;i<mc.player.getInventory().getContainerSize();i++){var stack=mc.player.getInventory().getItem(i);if(stack.has(DataComponents.MAP_ID)){current=MapItem.getSavedData(stack,mc.level);if(current!=null)break;}}
            if(current!=null&&!Arrays.equals(previous,current.colors)){previous=current.colors.clone();var image=new NativeImage(128,128,false);for(int i=0;i<previous.length;i++)image.setPixel(i%128,i/128,MapColor.getColorFromPackedId(Byte.toUnsignedInt(previous[i])));mc.getTextureManager().register(TEXTURE,new DynamicTexture(()->"SkyPilot dungeon map",image));}
        }
        int x=g.guiWidth()-112,y=64;UiTransforms.begin(g,"dungeon",x,y);g.fill(x,y,x+104,y+116,0xD010182B);g.text(mc.font,"Dungeon map",x+3,y+3,Ui.PURPLE,true);
        if(current!=null&&previous!=null){g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,x+4,y+16,0f,0f,96,96,128,128,128,128);for(var marker:current.getDecorations()){int mx=x+4+(int)((marker.x()/2.0+64)*.75),my=y+16+(int)((marker.y()/2.0+64)*.75);g.fill(mx-1,my-1,mx+2,my+2,0xFFFFFF55);}}
        else g.text(mc.font,"Map: waiting…",x+4,y+25,Ui.GRAY,true);UiTransforms.end(g);
    }
}
