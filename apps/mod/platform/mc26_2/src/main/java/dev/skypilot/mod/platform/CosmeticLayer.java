package dev.skypilot.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypilot.mod.core.CosmeticMesh;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Local-only cosmetics rendered as normal, lit entity geometry, also visible in the fitting studio. */
final class CosmeticLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private static final Identifier WHITE=Identifier.fromNamespaceAndPath("skypilot","textures/cosmetics/white.png");
    private record Batch(ModelPart mesh,int color,boolean head,int count) {}
    private String key="";
    private List<Batch> batches=List.of();
    static int submittedVoxels;
    CosmeticLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch) {
        var mc=Minecraft.getInstance();if(mc.player==null||state.id!=mc.player.getId()||state.isInvisible||state.isSpectator)return;
        var a=SkyPilot.get().config().appearance;String next=a.cape+":"+a.hat+":"+a.back+":"+a.accessory+":"+a.color;
        if(!next.equals(key)){key=next;batches=bake(a);}
        submittedVoxels=0;var m=getParentModel();m.setupAnim(state);
        for(var batch:batches){
            pose.pushPose();(batch.head?m.head:m.body).translateAndRotate(pose);
            collector.submitModelPart(batch.mesh,pose,RenderTypes.entityCutout(WHITE),light,OverlayTexture.NO_OVERLAY,null,0xFF000000|batch.color,null);
            submittedVoxels+=batch.count;pose.popPose();
        }
    }
    private static List<Batch> bake(SkyPilotConfig.Appearance a) {
        record Key(int color,boolean head) {}
        Map<Key,List<ModelPart.Cube>> groups=new LinkedHashMap<>();
        for(var v:CosmeticMesh.build(a)){
            boolean head=v.y()>1.85;float size=(float)(v.size()*16);
            // Head pivot is at y=0, body pivot at y=0 in the player model. World head base is 24px.
            float y=(float)(24-v.y()*16)+(head?0:0);
            var cube=new ModelPart.Cube(0,0,(float)(v.x()*16)-size/2,y-size/2,(float)(-v.z()*16)-size/2,size,size,size,0,0,0,false,16,16,EnumSet.allOf(Direction.class));
            groups.computeIfAbsent(new Key(v.color(),head),k->new ArrayList<>()).add(cube);
        }
        return groups.entrySet().stream().map(e->new Batch(new ModelPart(e.getValue(),Map.of()),e.getKey().color,e.getKey().head,e.getValue().size())).toList();
    }
}
