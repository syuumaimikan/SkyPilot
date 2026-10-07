package dev.skypilot.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypilot.mod.core.SkinVoxels;
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
import net.minecraft.world.entity.player.PlayerModelType;
import java.util.*;

/** Actual pixel extrusion of the local player's outer skin, following Minecraft's limb poses. */
final class PixelSkinLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    static int submittedPixels;
    private int[] counts;
    private Identifier texture;private boolean slim;private ModelPart[] mesh;
    PixelSkinLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || state.id!=mc.player.getId() || state.isInvisible || state.isSpectator || !SkyPilot.get().config().appearance.skin3d)return;
        Identifier next=state.skin.body().texturePath();boolean nextSlim=state.skin.model()==PlayerModelType.SLIM;
        if(!next.equals(texture) || slim!=nextSlim || mesh==null){
            int[] pixels=SkinPixels.get(next);if(pixels==null)return;
            texture=next;slim=nextSlim;var voxels=SkinVoxels.build(pixels,slim);mesh=bake(voxels);
            counts=new int[6];for(var voxel:voxels)counts[voxel.part()]++;
        }
        PlayerModel m=getParentModel();m.setupAnim(state);ModelPart[] parts={m.head,m.body,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg};
        boolean[] shown={state.showHat,state.showJacket,state.showRightSleeve,state.showLeftSleeve,state.showRightPants,state.showLeftPants};
        submittedPixels=0;
        for(int i=0;i<6;i++)if(shown[i] && parts[i].visible){
            submittedPixels+=counts[i];pose.pushPose();parts[i].translateAndRotate(pose);
            collector.submitModelPart(mesh[i],pose,RenderTypes.entityCutout(texture),light,OverlayTexture.NO_OVERLAY,null);
            pose.popPose();
        }
    }
    private static ModelPart[] bake(List<SkinVoxels.Pixel> voxels){
        List<List<ModelPart.Cube>> cubes=new ArrayList<>();for(int i=0;i<6;i++)cubes.add(new ArrayList<>());
        for(var p:voxels){
            var cube=new ModelPart.Cube(0,0,p.x(),p.y(),p.z(),p.w(),p.h(),p.d(),0,0,0,false,64,64,EnumSet.allOf(Direction.class));
            // Every face samples the same opaque skin pixel, including the newly exposed sides.
            for(int i=0;i<cube.polygons.length;i++){
                var polygon=cube.polygons[i];var vertices=polygon.vertices().clone();
                for(int j=0;j<vertices.length;j++)vertices[j]=vertices[j].remap((p.u()+0.5f)/64,(p.v()+0.5f)/64);
                cube.polygons[i]=new ModelPart.Polygon(vertices,polygon.normal());
            }
            cubes.get(p.part()).add(cube);
        }
        ModelPart[] result=new ModelPart[6];for(int i=0;i<6;i++)result[i]=new ModelPart(cubes.get(i),Map.of());return result;
    }
}
