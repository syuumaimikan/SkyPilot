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
 * Animated, textured, emissive-lit, Chroma/Dye-tinted 3D Blockbench ({@code .bbmodel}) cosmetic
 * layer for BOTH local and synced remote players, plus live Studio Hover Try-On, full Outfit
 * Preview, and interactive Studio Motion Simulation (Idle / Walk / Sprint / Glide).
 */
final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    static int submittedVoxels;

    /** Transient live Studio hover try-on override (never mutates saved config!). */
    static volatile String studioPreviewSlot;
    static volatile String studioPreviewId;
    /** Transient full 10-slot Outfit/Preset live hover preview for OutfitScreen. */
    static volatile SkyPilotConfig.Appearance studioOutfitPreview;
    /** Studio pose/motion preview: 0=Idle, 1=Walk, 2=Sprint, 3=Glide/Fly. */
    static volatile int studioMotionMode;

    static void clearStudioPreviews() {
        studioPreviewSlot = null;
        studioPreviewId = null;
        studioOutfitPreview = null;
    }

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

        var screen = mc.gui != null ? mc.gui.screen() : null;
        boolean isGuiEntityPreview = isLocal && (screen instanceof AppearanceScreen
                || screen instanceof WardrobeAdjustScreen
                || screen instanceof OutfitScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
                && collector != CosmeticWorldRenderer.worldCollector();

        if (isLocal) {
            targetPlayer = mc.player;
            SkyPilotConfig.Appearance outfitPrev = (isGuiEntityPreview && screen instanceof OutfitScreen) ? studioOutfitPreview : null;
            a = outfitPrev != null ? outfitPrev : rt.appearance();
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

        // Live Studio Motion Simulator so players can inspect flowing capes, flapping wings & wagging tails!
        if (isGuiEntityPreview && (screen instanceof AppearanceScreen || screen instanceof WardrobeAdjustScreen || screen instanceof OutfitScreen)) {
            switch (studioMotionMode) {
                case 1 -> { speed = 0.16; yawTurnRate = Math.sin(time * 2.2) * 0.45; }
                case 2 -> { speed = 0.34; yawTurnRate = Math.sin(time * 3.1) * 0.85; }
                case 3 -> { speed = 0.38; isFlying = true; vertSpeed = -0.18; yawTurnRate = Math.sin(time * 1.8) * 0.65; }
                default -> { speed = Math.max(speed, 0.04); }
            }
        }

        boolean glow = a.cosmeticGlow;
        float wingScale = (float) Math.max(0.6, Math.min(1.5, a.wingScale <= 0 ? 1.0 : a.wingScale));

        boolean hasHelmet = !targetPlayer.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean hasChest = !targetPlayer.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean hasLegs = !targetPlayer.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        String hoverSlot = (isGuiEntityPreview && screen instanceof AppearanceScreen) ? studioPreviewSlot : null;
        String hoverId = (isGuiEntityPreview && screen instanceof AppearanceScreen) ? studioPreviewId : null;
        String effectiveCape = "cape".equals(hoverSlot) && hoverId != null ? hoverId : a.cape;
        boolean hasCape = !"none".equals(effectiveCape);

        int totalCubes = 0;

        for (int slotIdx = 0; slotIdx < CosmeticCatalog.SLOTS.size(); slotIdx++) {
            String slot = CosmeticCatalog.SLOTS.get(slotIdx);
            if (!isGuiEntityPreview && ("pet".equals(slot) || "companion".equals(slot))) continue;

            String id = (slot.equals(hoverSlot) && hoverId != null)
                    ? hoverId
                    : CosmeticCatalog.equippedId(a, slot);
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

            SkyPilotConfig.SlotTransform tr = a.transformOf(slot);
            CosmeticRigRenderer.applySlotTransform(pose, slot, tr);
            int tintArgb = CosmeticRigRenderer.resolveTintArgb(tr, time, slotIdx);

            int cubes = CosmeticRigRenderer.submit(
                    rig, pose, collector, light, time,
                    slotSpeed, sneaking, isFalling, isFlying, vertSpeed, yawTurnRate, glow, tintArgb
            );
            pose.popPose();
            totalCubes += cubes;
        }

        if (isLocal) {
            submittedVoxels = totalCubes * 8;
        }
    }
}
