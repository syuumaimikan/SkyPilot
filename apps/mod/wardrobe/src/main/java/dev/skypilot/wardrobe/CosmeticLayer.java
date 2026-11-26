package dev.skypilot.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Animated, textured, emissive-lit 3D Blockbench ({@code .bbmodel}) cosmetic layer for BOTH
 * the local player and synced remote players running the Wardrobe mod, with per-slot fine
 * transform adjustments (offset, scale, tilt) and dynamic armor & multi-slot clearances.
 */
final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    static int submittedVoxels;

    CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var mc = Minecraft.getInstance();
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (mc.player == null || rt == null || state.isInvisible || state.isSpectator) return;

        boolean isLocal = (state.id == mc.player.getId());
        AbstractClientPlayer targetPlayer;
        SkyPilotConfig.Appearance a;

        if (isLocal) {
            targetPlayer = mc.player;
            a = rt.appearance();
        } else {
            if (!rt.appearance().showOtherPlayers || mc.level == null) return;
            Entity ent = mc.level.getEntity(state.id);
            if (!(ent instanceof AbstractClientPlayer remote)) return;
            a = RemoteWardrobeSync.getRemote(remote.getUUID());
            if (a == null) return;
            targetPlayer = remote;
        }

        var m = getParentModel();
        m.setupAnim(state);

        CapePhysics.State phys = CapePhysics.update(targetPlayer);
        double time = CosmeticAnimation.animationTimeSeconds();
        boolean sneaking = targetPlayer.isShiftKeyDown() || targetPlayer.isCrouching();
        boolean isFalling = targetPlayer.getDeltaMovement().y < -0.25;
        boolean isFlying = targetPlayer.isFallFlying() || targetPlayer.getAbilities().flying;
        double speed = phys.smoothedSpeed;
        double vertSpeed = phys.smoothedVertSpeed;
        double yawTurnRate = phys.yawTurnRate;
        boolean glow = a.cosmeticGlow;
        float wingScale = (float) Math.max(0.6, Math.min(1.5, a.wingScale <= 0 ? 1.0 : a.wingScale));

        boolean hasHelmet = !targetPlayer.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean hasChest = !targetPlayer.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean hasLegs = !targetPlayer.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean hasCape = !"none".equals(a.cape);

        // Render static Pet/Companion only inside GUI entity previews (Wardrobe Studio / Adjust / Inventory);
        // in the live 3D world, CosmeticWorldRenderer renders them with smooth world-space follow physics!
        var screen = mc.gui != null ? mc.gui.screen() : null;
        boolean isGuiEntityPreview = isLocal && (screen instanceof AppearanceScreen
                || screen instanceof WardrobeAdjustScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
                && collector != CosmeticWorldRenderer.worldCollector();

        int totalCubes = 0;

        for (String slot : CosmeticCatalog.SLOTS) {
            if (!isGuiEntityPreview && ("pet".equals(slot) || "companion".equals(slot))) continue;

            String id = CosmeticCatalog.equippedId(a, slot);
            if ("none".equals(id)) continue;
            var rig = CosmeticRigRenderer.get(id);
            if (rig == null) continue;

            pose.pushPose();
            double slotSpeed = speed;
            switch (rig.model.anchor) {
                case "head" -> {
                    m.head.translateAndRotate(pose);
                    if (hasHelmet) {
                        float cy = -4.0f / 16.0f;
                        pose.translate(0.0f, cy, 0.0f);
                        pose.scale(1.14f, 1.14f, 1.14f);
                        pose.translate(0.0f, -cy, 0.0f);
                    }
                }
                case "body" -> {
                    m.body.translateAndRotate(pose);
                    if ("cape".equals(slot)) {
                        if (hasChest) {
                            pose.translate(0.0f, -0.35f / 16.0f, 1.25f / 16.0f);
                        }
                        if (hasLegs) {
                            slotSpeed = Math.max(slotSpeed, 0.05);
                        }
                    } else if ("back".equals(slot)) {
                        float zShift = (hasChest ? 1.25f : 0.0f) + (hasCape ? 1.65f : 0.0f);
                        if (zShift > 0.0f) {
                            pose.translate(0.0f, 0.0f, zShift / 16.0f);
                        }
                        if (rig.model.scalable && Math.abs(wingScale - 1.0f) > 0.005f) {
                            float py = 3.0f / 16.0f;
                            float pz = 3.5f / 16.0f;
                            pose.translate(0.0f, py, pz);
                            pose.scale(wingScale, wingScale, wingScale);
                            pose.translate(0.0f, -py, -pz);
                        }
                    } else if ("shoulder".equals(slot)) {
                        if (hasChest) {
                            pose.translate(0.0f, -0.75f / 16.0f, 0.0f);
                            pose.scale(1.07f, 1.04f, 1.08f);
                        }
                    } else if ("waist".equals(slot)) {
                        if (hasChest || hasLegs) {
                            pose.translate(0.0f, 0.0f, 0.75f / 16.0f);
                            pose.scale(1.06f, 1.0f, 1.06f);
                        }
                    }
                }
                default -> {
                    // "root" (auras, feet) or GUI-mode "pet"/"companion": upright in body-yaw space
                }
            }

            // Apply fine user-adjusted per-slot transform (Position X/Y/Z, Scale, Tilt X/Y/Z)
            CosmeticRigRenderer.applySlotTransform(pose, slot, a.transformOf(slot));

            int cubes = CosmeticRigRenderer.submit(
                    rig, pose, collector, light, time,
                    slotSpeed, sneaking, isFalling, isFlying, vertSpeed, yawTurnRate, glow
            );
            pose.popPose();
            totalCubes += cubes;
        }

        if (isLocal) {
            submittedVoxels = totalCubes * 8;
        }
    }
}

