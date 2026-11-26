package dev.skypilot.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticRig;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Renders world-space 3D cosmetics via {@code LevelRenderEvents.COLLECT_SUBMITS}:
 * <ol>
 *   <li>Local player's living Floating Pet &amp; Ground Companion (obeying separate
 *       {@code firstPersonPet} and {@code firstPersonCompanion} toggles + per-slot transforms)</li>
 *   <li>Local player's First-Person Aura, Wings, Cloak, and Headwear whenever their granular
 *       first-person toggles ({@code firstPersonAura}, {@code firstPersonWings},
 *       {@code firstPersonCape}, {@code firstPersonHat}) are enabled</li>
 *   <li>Synced remote players' living Floating Pets &amp; Ground Companions when
 *       {@code showOtherPlayers && otherPlayerPets} is enabled</li>
 * </ol>
 */
final class CosmeticWorldRenderer {
    private static SubmitNodeCollector lastWorldCollector;

    private CosmeticWorldRenderer() {}

    static SubmitNodeCollector worldCollector() {
        return lastWorldCollector;
    }

    static void render(LevelRenderContext ctx) {
        lastWorldCollector = ctx.submitNodeCollector();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (player == null || mc.level == null || rt == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector collector = ctx.submitNodeCollector();
        double time = CosmeticAnimation.animationTimeSeconds();
        SkyPilotConfig.Appearance localApp = rt.appearance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        // 1. Local player world-space Pets, Companions, and optional First-Person Aura/Wings/Cape/Hat
        if (!player.isInvisible() && !player.isSpectator()) {
            int light = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
            boolean glow = localApp.cosmeticGlow;

            if (!"none".equals(localApp.pet) && (!firstPerson || localApp.firstPersonPet)) {
                var snap = PetWorldPhysics.petSnapshot(partialTick);
                renderWorldPet(localApp.pet, localApp.transformOf("pet"), snap, cam, pose, collector, light, time, glow);
            }
            if (!"none".equals(localApp.companion) && (!firstPerson || localApp.firstPersonCompanion)) {
                var snap = PetWorldPhysics.companionSnapshot(partialTick);
                renderWorldCompanion(localApp.companion, localApp.transformOf("companion"), snap, cam, pose, collector, light, time, glow);
            }

            // Granular 1st-person rendering for Aura, Wings, Cape, and Hat (AvatarRenderer is skipped in 1st-person!)
            if (firstPerson) {
                renderFirstPersonBodyCosmetics(player, localApp, partialTick, cam, pose, collector, light, time);
            }
        }

        // 2. Synced Remote Players' living world-space Pets & Companions
        if (localApp.showOtherPlayers && localApp.otherPlayerPets) {
            for (AbstractClientPlayer remote : mc.level.players()) {
                if (remote == null || remote == player || remote.isInvisible() || remote.isSpectator()) continue;
                if (remote.distanceToSqr(player) > 72.0 * 72.0) continue;
                SkyPilotConfig.Appearance ra = RemoteWardrobeSync.getRemote(remote.getUUID());
                if (ra == null) continue;
                boolean hasPet = !"none".equals(ra.pet);
                boolean hasComp = !"none".equals(ra.companion);
                if (!hasPet && !hasComp) continue;
                int rLight = mc.getEntityRenderDispatcher().getPackedLightCoords(remote, partialTick);
                if (hasPet) {
                    var snap = PetWorldPhysics.remotePetSnapshot(remote.getUUID(), partialTick);
                    renderWorldPet(ra.pet, ra.transformOf("pet"), snap, cam, pose, collector, rLight, time, ra.cosmeticGlow);
                }
                if (hasComp) {
                    var snap = PetWorldPhysics.remoteCompanionSnapshot(remote.getUUID(), partialTick);
                    renderWorldCompanion(ra.companion, ra.transformOf("companion"), snap, cam, pose, collector, rLight, time, ra.cosmeticGlow);
                }
            }
        }

        // 3. Custom 3D Procedural Emissive VFX (Slashes, Magic Circles, Light Pillars, Ribbons, etc.)
        WardrobeVfxEngine.render(ctx);
    }

    private static void renderFirstPersonBodyCosmetics(
            LocalPlayer player,
            SkyPilotConfig.Appearance a,
            float partialTick,
            Vec3 cam,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            double time
    ) {
        boolean showAura = a.firstPersonAura && !"none".equals(a.aura);
        boolean showWings = a.firstPersonWings && !"none".equals(a.back);
        // Never display the flat inside lining of the cloak when looking steeply downward in 1st-person!
        boolean showCape = a.firstPersonCape && !"none".equals(a.cape) && player.getXRot() < 34.0f;
        boolean showHat = a.firstPersonHat && !"none".equals(a.hat);
        boolean showFeet = a.firstPersonFeet && !"none".equals(a.feet);
        boolean showWaist = a.firstPersonWaist && !"none".equals(a.waist);
        boolean showShoulder = a.firstPersonShoulder && !"none".equals(a.shoulder);
        if (!showAura && !showWings && !showCape && !showHat && !showFeet && !showWaist && !showShoulder) return;

        CapePhysics.State phys = CapePhysics.update(player);
        double px = Mth.lerp(partialTick, player.xOld, player.getX());
        double py = Mth.lerp(partialTick, player.yOld, player.getY());
        double pz = Mth.lerp(partialTick, player.zOld, player.getZ());
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean isFalling = player.getDeltaMovement().y < -0.25;
        boolean isFlying = player.isFallFlying() || player.getAbilities().flying;
        float crouchDrop = sneaking ? 0.22f : 0.0f;
        boolean hasChest = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean hasCape = !"none".equals(a.cape);
        float wingScale = (float) Math.max(0.6, Math.min(1.5, a.wingScale <= 0 ? 1.0 : a.wingScale));

        if (showAura) {
            var rig = CosmeticRigRenderer.get(a.aura);
            if (rig != null) {
                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y + 1.501 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                // Push slightly outward in 1st-person so orbiting ornaments frame peripheral vision without blocking eyes!
                pose.scale(-1.08f, -0.9375f, 1.08f);
                CosmeticRigRenderer.applySlotTransform(pose, "aura", a.transformOf("aura"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }

        if (showFeet) {
            var rig = CosmeticRigRenderer.get(a.feet);
            if (rig != null) {
                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y + 1.501 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                CosmeticRigRenderer.applySlotTransform(pose, "feet", a.transformOf("feet"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }

        if (showWaist) {
            var rig = CosmeticRigRenderer.get(a.waist);
            if (rig != null) {
                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y + 1.501 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                pose.translate(0.0f, 0.0f, 1.4f / 16.0f);
                CosmeticRigRenderer.applySlotTransform(pose, "waist", a.transformOf("waist"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }

        if (showShoulder) {
            var rig = CosmeticRigRenderer.get(a.shoulder);
            if (rig != null) {
                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y + 1.44 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                CosmeticRigRenderer.applySlotTransform(pose, "shoulder", a.transformOf("shoulder"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }

        if (showWings) {
            var rig = CosmeticRigRenderer.get(a.back);
            if (rig != null) {
                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y + 1.501 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                float zShift = (hasChest ? 1.25f : 0.0f) + (hasCape ? 1.65f : 0.0f) + 2.0f;
                pose.translate(0.0f, 0.0f, zShift / 16.0f);
                if (rig.model.scalable && Math.abs(wingScale - 1.0f) > 0.005f) {
                    float wpy = 3.0f / 16.0f, wpz = 3.5f / 16.0f;
                    pose.translate(0.0f, wpy, wpz);
                    pose.scale(wingScale, wingScale, wingScale);
                    pose.translate(0.0f, -wpy, -wpz);
                }
                CosmeticRigRenderer.applySlotTransform(pose, "back", a.transformOf("back"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }

        if (showCape) {
            var rig = CosmeticRigRenderer.get(a.cape);
            if (rig != null) {
                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y + 1.46 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                // Offset cleanly behind shoulders and trail outward so the inside lining never cuts into 1st-person view!
                pose.translate(0.0f, 0.8f / 16.0f, ((hasChest ? 1.25f : 0.0f) + 3.4f) / 16.0f);
                pose.mulPose(Axis.XP.rotationDegrees(-14.0f));
                CosmeticRigRenderer.applySlotTransform(pose, "cape", a.transformOf("cape"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }

        if (showHat) {
            var rig = CosmeticRigRenderer.get(a.hat);
            if (rig != null) {
                pose.pushPose();
                // Slight upward lift in 1st-person so visors/brims never block crosshair while halos/hats frame the top cleanly!
                pose.translate(px - cam.x, py - cam.y + 1.54 - crouchDrop, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - headYaw));
                pose.scale(-0.9375f, -0.9375f, 0.9375f);
                CosmeticRigRenderer.applySlotTransform(pose, "hat", a.transformOf("hat"));
                CosmeticRigRenderer.submit(rig, pose, collector, light, time,
                        phys.smoothedSpeed, sneaking, isFalling, isFlying, phys.smoothedVertSpeed, phys.yawTurnRate, a.cosmeticGlow);
                pose.popPose();
            }
        }
    }

    private static void renderWorldPet(
            String id,
            SkyPilotConfig.SlotTransform tr,
            PetWorldPhysics.Snapshot snap,
            Vec3 cam,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            double time,
            boolean glow
    ) {
        if (snap == null) return;
        var rig = CosmeticRigRenderer.get(id);
        if (rig == null) return;
        double[] anchor = modelPivot(rig.model, 15.0, 25.5, 2.0);
        pose.pushPose();
        pose.translate(snap.x - cam.x, snap.y - cam.y, snap.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - snap.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(snap.pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(snap.roll));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        if (tr != null && !tr.isIdentity()) {
            pose.translate((float) (tr.offsetX / 16.0), (float) (-tr.offsetY / 16.0), (float) (tr.offsetZ / 16.0));
            if (Math.abs(tr.rotY) > 0.01) pose.mulPose(Axis.YP.rotationDegrees((float) tr.rotY));
            if (Math.abs(tr.rotX) > 0.01) pose.mulPose(Axis.XP.rotationDegrees((float) -tr.rotX));
            if (Math.abs(tr.rotZ) > 0.01) pose.mulPose(Axis.ZP.rotationDegrees((float) -tr.rotZ));
            float sc = (float) tr.scale;
            if (Math.abs(sc - 1.0f) > 0.005f) pose.scale(sc, sc, sc);
        }
        pose.translate((float) (-anchor[0] / 16.0), (float) (-(24.0 - anchor[1]) / 16.0), (float) (-anchor[2] / 16.0));
        CosmeticRigRenderer.submit(
                rig, pose, collector, light, time,
                snap.perched ? 0.0 : snap.speed, false, false, !snap.perched, 0.0, 0.0, glow
        );
        pose.popPose();
    }

    private static void renderWorldCompanion(
            String id,
            SkyPilotConfig.SlotTransform tr,
            PetWorldPhysics.Snapshot snap,
            Vec3 cam,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            double time,
            boolean glow
    ) {
        if (snap == null) return;
        var rig = CosmeticRigRenderer.get(id);
        if (rig == null) return;
        double[] anchor = modelPivot(rig.model, -16.5, 0.0, 2.5);
        pose.pushPose();
        pose.translate(snap.x - cam.x, snap.y - cam.y, snap.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - snap.yaw));
        if (Math.abs(snap.pitch) > 0.1f) {
            pose.mulPose(Axis.XP.rotationDegrees(snap.pitch));
        }
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        if (tr != null && !tr.isIdentity()) {
            pose.translate((float) (tr.offsetX / 16.0), (float) (-tr.offsetY / 16.0), (float) (tr.offsetZ / 16.0));
            if (Math.abs(tr.rotY) > 0.01) pose.mulPose(Axis.YP.rotationDegrees((float) tr.rotY));
            if (Math.abs(tr.rotX) > 0.01) pose.mulPose(Axis.XP.rotationDegrees((float) -tr.rotX));
            if (Math.abs(tr.rotZ) > 0.01) pose.mulPose(Axis.ZP.rotationDegrees((float) -tr.rotZ));
            float sc = (float) tr.scale;
            if (Math.abs(sc - 1.0f) > 0.005f) pose.scale(sc, sc, sc);
        }
        pose.translate((float) (-anchor[0] / 16.0), -1.501f, (float) (-anchor[2] / 16.0));
        CosmeticRigRenderer.submit(
                rig, pose, collector, light, time,
                snap.speed, false, false, false, 0.0, 0.0, glow
        );
        pose.popPose();
    }

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

