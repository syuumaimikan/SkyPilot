package dev.skypilot.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skypilot.mod.core.CosmeticRig;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the player's equipped Wardrobe Floating Pet and Ground Companion directly in 3D world space
 * via {@code LevelRenderEvents.COLLECT_SUBMITS}, decoupled from {@code PlayerRenderer} so they:
 * <ul>
 *   <li>Follow, orbit, perch on the player's shoulders/head, and trot across stairs/slabs naturally</li>
 *   <li>Remain visible in First-Person view when {@code appearance.firstPersonPet} is enabled (and
 *       cleanly hidden in First-Person when disabled)</li>
 * </ul>
 */
final class CosmeticWorldRenderer {
    private static net.minecraft.client.renderer.SubmitNodeCollector lastWorldCollector;

    private CosmeticWorldRenderer() {}

    static net.minecraft.client.renderer.SubmitNodeCollector worldCollector() {
        return lastWorldCollector;
    }

    static void render(LevelRenderContext ctx) {
        lastWorldCollector = ctx.submitNodeCollector();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isInvisible() || player.isSpectator()) return;

        SkyPilotConfig.Appearance a = SkyPilot.get().config().appearance;
        boolean hasPet = !"none".equals(a.pet);
        boolean hasComp = !"none".equals(a.companion);
        if (!hasPet && !hasComp) return;

        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        if (firstPerson && !a.firstPersonPet) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        PoseStack pose = ctx.poseStack();
        var collector = ctx.submitNodeCollector();
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
        double time = System.currentTimeMillis() / 1000.0;
        boolean glow = a.cosmeticGlow;

        if (hasPet) {
            var rig = CosmeticRigRenderer.get(a.pet);
            var snap = PetWorldPhysics.petSnapshot(partialTick);
            if (rig != null && snap != null) {
                double[] anchor = modelPivot(rig.model, 15.0, 25.5, 2.0);
                pose.pushPose();
                pose.translate(snap.x - cam.x, snap.y - cam.y, snap.z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - snap.yaw));
                pose.mulPose(Axis.XP.rotationDegrees(snap.pitch));
                pose.mulPose(Axis.ZP.rotationDegrees(snap.roll));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                // Cancel the static studio shoulder offset so the pet's center sits right at (snap.x, snap.y, snap.z)
                pose.translate((float) (-anchor[0] / 16.0), (float) (-(24.0 - anchor[1]) / 16.0), (float) (-anchor[2] / 16.0));
                CosmeticRigRenderer.submit(
                        rig, pose, collector, light, time,
                        snap.perched ? 0.0 : snap.speed, false, false, !snap.perched, 0.0, 0.0, glow
                );
                pose.popPose();
            }
        }

        if (hasComp) {
            var rig = CosmeticRigRenderer.get(a.companion);
            var snap = PetWorldPhysics.companionSnapshot(partialTick);
            if (rig != null && snap != null) {
                double[] anchor = modelPivot(rig.model, -16.5, 0.0, 2.5);
                pose.pushPose();
                pose.translate(snap.x - cam.x, snap.y - cam.y, snap.z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - snap.yaw));
                if (Math.abs(snap.pitch) > 0.1f) {
                    pose.mulPose(Axis.XP.rotationDegrees(snap.pitch));
                }
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                // Place BB y=0 (ModelPart y=24) EXACTLY on snap.y (terrain CollisionShape surface + pounce hop)
                pose.translate((float) (-anchor[0] / 16.0), -1.501f, (float) (-anchor[2] / 16.0));
                CosmeticRigRenderer.submit(
                        rig, pose, collector, light, time,
                        snap.speed, false, false, false, 0.0, 0.0, glow
                );
                pose.popPose();
            }
        }
    }

    /** Returns the primary root bone pivot in Blockbench pixels so both bundled & custom .bbmodel files center cleanly. */
    private static double[] modelPivot(CosmeticRig.Model m, double defX, double defY, double defZ) {
        if (m != null && m.bones != null && !m.bones.isEmpty()) {
            CosmeticRig.Bone root = m.bones.get(0);
            if (root.pivot != null && root.pivot.length >= 3) {
                return new double[]{root.pivot[0], root.pivot[1], root.pivot[2]};
            }
        }
        return new double[]{defX, defY, defZ};
    }
}

