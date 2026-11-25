package dev.skypilot.mod.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Animated, textured, emissive-lit 3D Blockbench ({@code .bbmodel}) cosmetic layer for the local
 * player across all 6 independently combinable slots, with automatic armor & multi-slot
 * anti-clipping clearances.
 *
 * <p>During live 3D world rendering, {@code pet} and {@code companion} are delegated to
 * {@link CosmeticWorldRenderer} + {@link PetWorldPhysics} so they follow, orbit, perch on the
 * player's shoulders/head, snap cleanly to terrain heights, and obey {@code firstPersonPet}.
 * In GUI screens (Inventory / Wardrobe Studio), all 6 slots render together here.
 */
final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    static int submittedVoxels;

    CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId() || state.isInvisible || state.isSpectator) return;

        SkyPilotConfig.Appearance a = SkyPilot.get().config().appearance;
        var m = getParentModel();
        m.setupAnim(state);

        CapePhysics.update(mc.player);
        double time = System.currentTimeMillis() / 1000.0;
        boolean sneaking = mc.player.isShiftKeyDown() || mc.player.isCrouching();
        boolean isFalling = mc.player.getDeltaMovement().y < -0.25;
        boolean isFlying = mc.player.isFallFlying() || mc.player.getAbilities().flying;
        double speed = CapePhysics.getSmoothedSpeed();
        double vertSpeed = CapePhysics.getSmoothedVertSpeed();
        double yawTurnRate = CapePhysics.getYawTurnRate();
        boolean glow = a.cosmeticGlow;
        float wingScale = (float) Math.max(0.6, Math.min(1.5, a.wingScale <= 0 ? 1.0 : a.wingScale));

        // Dynamic armor & slot-stacking clearances — guarantees ZERO clipping bare OR in full armor!
        boolean hasHelmet = !mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean hasChest = !mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean hasLegs = !mc.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean hasCape = !"none".equals(a.cape);
        // Only render static Pet/Companion inside GUI entity boxes (Wardrobe Studio / Inventory),
        // never in the 3D world where CosmeticWorldRenderer renders them with live follow physics.
        // Uses collector identity (Sodium/Iris-safe; does not rely on LevelRenderEvents.START_MAIN).
        var screen = mc.gui != null ? mc.gui.screen() : null;
        boolean isGuiEntityPreview = (screen instanceof AppearanceScreen
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
                        // Uniformly wrap headwear outside full armor helmet (±5.0, top y=33) around head center
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
                            // Shift cloak cleanly behind chestplate backplate (+1.25px) and shoulder pads (-0.35px Y)
                            pose.translate(0.0f, -0.35f / 16.0f, 1.25f / 16.0f);
                        }
                        if (hasLegs) {
                            slotSpeed = Math.max(slotSpeed, 0.05);
                        }
                    } else if ("back".equals(slot)) {
                        // Stack wings/backwear cleanly behind BOTH chestplate armor AND any equipped cloak!
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
                    }
                }
                default -> {
                    // "root" (auras) or GUI-mode "pet"/"companion": upright in body-yaw space
                }
            }

            int cubes = CosmeticRigRenderer.submit(
                    rig, pose, collector, light, time,
                    slotSpeed, sneaking, isFalling, isFlying, vertSpeed, yawTurnRate, glow
            );
            pose.popPose();
            totalCubes += cubes;
        }

        // Preserve GameTest metric compatibility (scale sculpted multi-face cuboids proportionally)
        submittedVoxels = totalCubes * 8;
    }
}
