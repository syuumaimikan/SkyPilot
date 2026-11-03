package dev.skypilot.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypilot.mod.core.CosmeticAnimation;
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
import org.joml.Quaternionf;

import java.util.*;

/**
 * Animated, lit entity geometry for local-player cosmetics.
 * Features realistic multi-segment cloth cape physics with ambient wind billowing,
 * dynamic wing flapping with aerodynamic response, hovering celestial halos,
 * lifelike ear twitches, and procedurally animated color shaders.
 */
final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("skypilot", "textures/cosmetics/white.png");

    enum ComponentType {
        CAPE_0, CAPE_1, CAPE_2, CAPE_3,
        WING_LEFT, WING_RIGHT,
        HEAD_FLOATING, HEAD_EAR_LEFT, HEAD_EAR_RIGHT, HEAD_FIXED,
        BACK_FIXED
    }

    private record Batch(ModelPart mesh, int baseColor, ComponentType type, String cosmeticId, int count) {}

    private String key = "";
    private List<Batch> batches = List.of();
    static int submittedVoxels;

    CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId() || state.isInvisible || state.isSpectator) return;

        var a = SkyPilot.get().config().appearance;
        String next = a.cape + ":" + a.hat + ":" + a.back + ":" + a.accessory + ":" + a.color;
        if (!next.equals(key)) {
            key = next;
            batches = bake(a);
        }

        submittedVoxels = 0;
        var m = getParentModel();
        m.setupAnim(state);

        CapePhysics.update(mc.player);
        double time = System.currentTimeMillis() / 1000.0;
        boolean sneaking = mc.player.isShiftKeyDown() || mc.player.isCrouching();
        boolean isFalling = mc.player.getDeltaMovement().y < -0.25;
        boolean isFlying = mc.player.isFallFlying() || mc.player.getAbilities().flying;
        double speed = CapePhysics.getSmoothedSpeed();

        // 1. Render Capes (Chained multi-segment cloth wave simulation)
        renderCape(pose, collector, light, m, a.cape, time, sneaking);

        // 2. Render Wings (Aerodynamic flapping physics)
        renderWings(pose, collector, light, m, a.back, time, speed, isFalling, isFlying);

        // 3. Render Headwear (Halos with bobbing/rotation, twitching ears, fixed hats)
        renderHeadwear(pose, collector, light, m, a.hat, time);

        // 4. Render Fixed Backwear (Quivers, katanas, jetpacks, backpacks, shields)
        renderFixedBackwear(pose, collector, light, m, a.back, time);
    }

    private void renderCape(PoseStack pose, SubmitNodeCollector collector, int light, PlayerModel m, String capeId, double time, boolean sneaking) {
        if ("none".equals(capeId)) return;

        var seg0 = findBatches(ComponentType.CAPE_0);
        var seg1 = findBatches(ComponentType.CAPE_1);
        var seg2 = findBatches(ComponentType.CAPE_2);
        var seg3 = findBatches(ComponentType.CAPE_3);
        if (seg0.isEmpty() && seg1.isEmpty() && seg2.isEmpty() && seg3.isEmpty()) return;

        pose.pushPose();
        m.body.translateAndRotate(pose);

        // Shoulder anchor point:
        pose.translate(0, 0.64f / 16.0f, 3.84f / 16.0f);

        // Root segment 0:
        float pitch0 = CapePhysics.getSegmentPitch(time, 0, sneaking);
        float roll0 = CapePhysics.getSegmentRoll(time, 0);
        pose.mulPose(new Quaternionf().rotationX(pitch0).rotateZ(roll0));
        submitBatchList(seg0, pose, collector, light, capeId, 0, time);

        // Segment 1 (chained rotation):
        float segHeight = 3.65f / 16.0f;
        pose.translate(0, segHeight, 0);
        float dp1 = CapePhysics.getDeltaPitch(time, 1, sneaking);
        pose.mulPose(new Quaternionf().rotationX(dp1));
        submitBatchList(seg1, pose, collector, light, capeId, 4, time);

        // Segment 2 (chained rotation):
        pose.translate(0, segHeight, 0);
        float dp2 = CapePhysics.getDeltaPitch(time, 2, sneaking);
        pose.mulPose(new Quaternionf().rotationX(dp2));
        submitBatchList(seg2, pose, collector, light, capeId, 8, time);

        // Segment 3 (chained rotation):
        pose.translate(0, segHeight, 0);
        float dp3 = CapePhysics.getDeltaPitch(time, 3, sneaking);
        pose.mulPose(new Quaternionf().rotationX(dp3));
        submitBatchList(seg3, pose, collector, light, capeId, 12, time);

        pose.popPose();
    }

    private void renderWings(PoseStack pose, SubmitNodeCollector collector, int light, PlayerModel m, String backId, double time, double speed, boolean falling, boolean flying) {
        var leftWings = findBatches(ComponentType.WING_LEFT);
        var rightWings = findBatches(ComponentType.WING_RIGHT);
        if (leftWings.isEmpty() && rightWings.isEmpty()) return;

        float flap = (float) CosmeticAnimation.wingFlap(time, speed, falling, flying);
        float tipWave = (float) (Math.sin(time * 2.8) * 0.04);

        // Left Wing:
        if (!leftWings.isEmpty()) {
            pose.pushPose();
            m.body.translateAndRotate(pose);
            pose.translate(1.92f / 16.0f, 2.0f / 16.0f, 4.64f / 16.0f);
            pose.mulPose(new Quaternionf().rotationY(flap).rotateX(tipWave));
            submitBatchList(leftWings, pose, collector, light, backId, 0, time);
            pose.popPose();
        }

        // Right Wing (mirrored):
        if (!rightWings.isEmpty()) {
            pose.pushPose();
            m.body.translateAndRotate(pose);
            pose.translate(-1.92f / 16.0f, 2.0f / 16.0f, 4.64f / 16.0f);
            pose.mulPose(new Quaternionf().rotationY(-flap).rotateX(tipWave));
            submitBatchList(rightWings, pose, collector, light, backId, 0, time);
            pose.popPose();
        }
    }

    private void renderHeadwear(PoseStack pose, SubmitNodeCollector collector, int light, PlayerModel m, String hatId, double time) {
        // Floating headwear (Halos, orbiting blossoms, skulls):
        var floating = findBatches(ComponentType.HEAD_FLOATING);
        if (!floating.isEmpty()) {
            pose.pushPose();
            m.head.translateAndRotate(pose);
            float bob = (float) CosmeticAnimation.haloBob(time);
            float spin = (float) CosmeticAnimation.haloSpin(time, "dark_halo".equals(hatId));
            pose.translate(0, bob, 0);
            pose.mulPose(new Quaternionf().rotationY(spin));
            submitBatchList(floating, pose, collector, light, hatId, 0, time);
            pose.popPose();
        }

        // Twitching cat ears:
        var leftEar = findBatches(ComponentType.HEAD_EAR_LEFT);
        if (!leftEar.isEmpty()) {
            pose.pushPose();
            m.head.translateAndRotate(pose);
            pose.translate(0.18f, -0.45f, 0);
            pose.mulPose(new Quaternionf().rotationZ((float) CosmeticAnimation.earTwitch(time, 1)));
            submitBatchList(leftEar, pose, collector, light, hatId, 0, time);
            pose.popPose();
        }
        var rightEar = findBatches(ComponentType.HEAD_EAR_RIGHT);
        if (!rightEar.isEmpty()) {
            pose.pushPose();
            m.head.translateAndRotate(pose);
            pose.translate(-0.18f, -0.45f, 0);
            pose.mulPose(new Quaternionf().rotationZ((float) CosmeticAnimation.earTwitch(time, -1)));
            submitBatchList(rightEar, pose, collector, light, hatId, 0, time);
            pose.popPose();
        }

        // Fixed headwear (Crowns, Horns, Visors, Hats):
        var fixedHead = findBatches(ComponentType.HEAD_FIXED);
        if (!fixedHead.isEmpty()) {
            pose.pushPose();
            m.head.translateAndRotate(pose);
            submitBatchList(fixedHead, pose, collector, light, hatId, 0, time);
            pose.popPose();
        }
    }

    private void renderFixedBackwear(PoseStack pose, SubmitNodeCollector collector, int light, PlayerModel m, String backId, double time) {
        var backFixed = findBatches(ComponentType.BACK_FIXED);
        if (!backFixed.isEmpty()) {
            pose.pushPose();
            m.body.translateAndRotate(pose);
            submitBatchList(backFixed, pose, collector, light, backId, 0, time);
            pose.popPose();
        }
    }

    private List<Batch> findBatches(ComponentType type) {
        List<Batch> result = new ArrayList<>();
        for (var b : batches) {
            if (b.type == type) result.add(b);
        }
        return result;
    }

    private void submitBatchList(List<Batch> list, PoseStack pose, SubmitNodeCollector collector, int light, String cosmeticId, int rowHint, double time) {
        for (var batch : list) {
            int dynamicColor = CosmeticAnimation.animatedColor(cosmeticId, batch.baseColor, rowHint, 5, time);
            collector.submitModelPart(batch.mesh, pose, RenderTypes.entityCutout(WHITE), light, OverlayTexture.NO_OVERLAY, null, 0xFF000000 | dynamicColor, null);
            submittedVoxels += batch.count;
        }
    }

    private static List<Batch> bake(SkyPilotConfig.Appearance a) {
        record GroupKey(ComponentType type, int color, String id) {}
        Map<GroupKey, List<ModelPart.Cube>> groups = new LinkedHashMap<>();

        // Generate full voxels from CosmeticMesh
        for (var v : CosmeticMesh.build(a)) {
            ComponentType type = classify(v, a);
            float[] local = localCoords(v, type);

            float size = (float) (v.size() * 16.0);
            float originX = local[0] - size / 2.0f;
            float originY = local[1] - size / 2.0f;
            float originZ = local[2] - size / 2.0f;

            var cube = new ModelPart.Cube(0, 0, originX, originY, originZ, size, size, size, 0, 0, 0, false, 16, 16, EnumSet.allOf(Direction.class));
            String cosmeticId = findSourceId(type, a);
            groups.computeIfAbsent(new GroupKey(type, v.color(), cosmeticId), k -> new ArrayList<>()).add(cube);
        }

        List<Batch> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            var mesh = new ModelPart(entry.getValue(), Map.of());
            result.add(new Batch(mesh, entry.getKey().color, entry.getKey().type, entry.getKey().id, entry.getValue().size()));
        }
        return result;
    }

    private static ComponentType classify(CosmeticMesh.Voxel v, SkyPilotConfig.Appearance a) {
        // 1. Cape voxels (identified by Z near -0.24 and Y between 0.6 and 1.5)
        if (!"none".equals(a.cape) && v.z() <= -0.23 && v.y() <= 1.48 && v.y() >= 0.55 && Math.abs(v.x()) <= 0.25) {
            int row = (int) Math.round((1.46 - v.y()) / 0.057);
            if (row < 4) return ComponentType.CAPE_0;
            if (row < 8) return ComponentType.CAPE_1;
            if (row < 12) return ComponentType.CAPE_2;
            return ComponentType.CAPE_3;
        }

        // 2. Headwear voxels (Y >= 1.70)
        if (v.y() >= 1.70) {
            String hat = "none".equals(a.hat) ? a.accessory : a.hat;
            if ("halo".equals(hat) || "dark_halo".equals(hat) || "angel_halo".equals(hat) || "wither_gaze".equals(hat) || "sakura_blossom".equals(hat)) {
                return ComponentType.HEAD_FLOATING;
            }
            if ("ears".equals(hat)) {
                return v.x() > 0 ? ComponentType.HEAD_EAR_LEFT : ComponentType.HEAD_EAR_RIGHT;
            }
            return ComponentType.HEAD_FIXED;
        }

        // 3. Backwear voxels
        String back = "none".equals(a.back) ? a.accessory : a.back;
        if (List.of("wings", "dragon", "phoenix", "fairy", "cyber_wings", "angel_wings", "demon_wings", "crystal_wings", "steampunk", "void_tendrils").contains(back)) {
            if (v.x() > 0.05) return ComponentType.WING_LEFT;
            if (v.x() < -0.05) return ComponentType.WING_RIGHT;
        }
        return ComponentType.BACK_FIXED;
    }

    private static float[] localCoords(CosmeticMesh.Voxel v, ComponentType type) {
        float worldX = (float) (v.x() * 16.0);
        float worldY = (float) (24.0 - v.y() * 16.0);
        float worldZ = (float) (-v.z() * 16.0);

        return switch (type) {
            case CAPE_0 -> new float[]{worldX, worldY - 0.64f, worldZ - 3.84f};
            case CAPE_1 -> new float[]{worldX, worldY - (0.64f + 3.65f), worldZ - 3.84f};
            case CAPE_2 -> new float[]{worldX, worldY - (0.64f + 7.30f), worldZ - 3.84f};
            case CAPE_3 -> new float[]{worldX, worldY - (0.64f + 10.95f), worldZ - 3.84f};
            case WING_LEFT -> new float[]{worldX - 1.92f, worldY - 2.0f, worldZ - 4.64f};
            case WING_RIGHT -> new float[]{worldX + 1.92f, worldY - 2.0f, worldZ - 4.64f};
            case HEAD_EAR_LEFT -> new float[]{worldX - 2.88f, worldY + 7.2f, worldZ};
            case HEAD_EAR_RIGHT -> new float[]{worldX + 2.88f, worldY + 7.2f, worldZ};
            case HEAD_FLOATING, HEAD_FIXED, BACK_FIXED -> new float[]{worldX, worldY, worldZ};
        };
    }

    private static String findSourceId(ComponentType type, SkyPilotConfig.Appearance a) {
        return switch (type) {
            case CAPE_0, CAPE_1, CAPE_2, CAPE_3 -> a.cape;
            case WING_LEFT, WING_RIGHT, BACK_FIXED -> "none".equals(a.back) ? a.accessory : a.back;
            case HEAD_FLOATING, HEAD_EAR_LEFT, HEAD_EAR_RIGHT, HEAD_FIXED -> "none".equals(a.hat) ? a.accessory : a.hat;
        };
    }
}
