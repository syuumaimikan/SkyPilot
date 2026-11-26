package dev.skypilot.wardrobe;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticRig;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.*;

/**
 * Compiles, caches, animates, and renders Blockbench ({@code .bbmodel}) {@link CosmeticRig.Model}
 * hierarchies in Minecraft 26.2 — with overflow-safe animated texture strip playback, per-slot
 * user transforms (offset/scale/tilt), translucent materials, and fullbright emissive glow masks.
 */
final class CosmeticRigRenderer {
    static final int FULL_BRIGHT = 0xF000F0;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("skypilot", "textures/cosmetics/white.png");
    private static final Map<String, CompiledRig> CACHE = new LinkedHashMap<>();
    private static final Set<Identifier> REGISTERED_DYNAMIC = new HashSet<>();

    record BoundBone(
            CosmeticRig.Bone def,
            ModelPart part,
            float baseX, float baseY, float baseZ,
            float baseRx, float baseRy, float baseRz
    ) {}

    record FrameTree(ModelPart root, List<BoundBone> bones, int cubeCount) {}

    static final class CompiledRig {
        final CosmeticRig.Model model;
        final Identifier baseTexture;
        final Identifier emissiveTexture;
        final FrameTree[] baseFrames;
        final FrameTree[] emissiveCubeFrames;
        final ModelPart[] guiTransformWraps;
        final ModelPart[] guiEmissiveTransformWraps;
        final Model.Simple[] guiBaseModels;
        final Model.Simple[] guiGlowMaskModels;
        final Model.Simple[] guiEmissiveCubeModels;
        final Model.Simple[] guiCenteredBaseModels;
        final Model.Simple[] guiCenteredGlowMaskModels;
        final Model.Simple[] guiCenteredEmissiveCubeModels;

        CompiledRig(
                CosmeticRig.Model model,
                Identifier baseTexture,
                Identifier emissiveTexture,
                FrameTree[] baseFrames,
                FrameTree[] emissiveCubeFrames
        ) {
            this.model = model;
            this.baseTexture = baseTexture;
            this.emissiveTexture = emissiveTexture;
            this.baseFrames = baseFrames;
            this.emissiveCubeFrames = emissiveCubeFrames;
            this.guiTransformWraps = new ModelPart[baseFrames.length];
            this.guiEmissiveTransformWraps = emissiveCubeFrames != null ? new ModelPart[emissiveCubeFrames.length] : null;
            this.guiBaseModels = new Model.Simple[baseFrames.length];
            this.guiGlowMaskModels = emissiveTexture != null ? new Model.Simple[baseFrames.length] : null;
            this.guiEmissiveCubeModels = emissiveCubeFrames != null ? new Model.Simple[emissiveCubeFrames.length] : null;

            boolean centerSolo = "pet".equals(model.slot) || "companion".equals(model.slot);
            this.guiCenteredBaseModels = centerSolo ? new Model.Simple[baseFrames.length] : null;
            this.guiCenteredGlowMaskModels = (centerSolo && emissiveTexture != null) ? new Model.Simple[baseFrames.length] : null;
            this.guiCenteredEmissiveCubeModels = (centerSolo && emissiveCubeFrames != null) ? new Model.Simple[emissiveCubeFrames.length] : null;

            double ax = "pet".equals(model.slot) ? 15.0 : -16.5;
            double ay = "pet".equals(model.slot) ? 25.5 : 5.5;
            double az = "pet".equals(model.slot) ? 2.0 : 2.5;
            if (centerSolo && !model.bones.isEmpty() && model.bones.get(0).pivot != null && model.bones.get(0).pivot.length >= 3) {
                ax = model.bones.get(0).pivot[0];
                if ("pet".equals(model.slot)) ay = model.bones.get(0).pivot[1];
                az = model.bones.get(0).pivot[2];
            }

            for (int i = 0; i < baseFrames.length; i++) {
                ModelPart wrap = new ModelPart(List.of(), Map.of("inner", baseFrames[i].root));
                this.guiTransformWraps[i] = wrap;
                this.guiBaseModels[i] = new Model.Simple(
                        wrap,
                        model.translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout
                );
                if (this.guiGlowMaskModels != null) {
                    this.guiGlowMaskModels[i] = new Model.Simple(wrap, RenderTypes::entityTranslucentEmissive);
                }
                if (this.guiEmissiveCubeModels != null) {
                    ModelPart emWrap = new ModelPart(List.of(), Map.of("inner", emissiveCubeFrames[i].root));
                    this.guiEmissiveTransformWraps[i] = emWrap;
                    this.guiEmissiveCubeModels[i] = new Model.Simple(emWrap, RenderTypes::entityTranslucentEmissive);
                }
                if (centerSolo) {
                    ModelPart wrappedBase = wrapCentered(baseFrames[i].root, ax, ay, az);
                    this.guiCenteredBaseModels[i] = new Model.Simple(
                            wrappedBase,
                            model.translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout
                    );
                    if (this.guiCenteredGlowMaskModels != null) {
                        this.guiCenteredGlowMaskModels[i] = new Model.Simple(wrappedBase, RenderTypes::entityTranslucentEmissive);
                    }
                    if (this.guiCenteredEmissiveCubeModels != null) {
                        ModelPart wrappedEm = wrapCentered(emissiveCubeFrames[i].root, ax, ay, az);
                        this.guiCenteredEmissiveCubeModels[i] = new Model.Simple(wrappedEm, RenderTypes::entityTranslucentEmissive);
                    }
                }
            }
        }

        private static ModelPart wrapCentered(ModelPart inner, double ax, double ay, double az) {
            ModelPart wrap = new ModelPart(List.of(), Map.of("c", inner));
            wrap.x = (float) -ax;
            wrap.y = (float) (ay - 12.0);
            wrap.z = (float) -az;
            return wrap;
        }

        int frameIndex(double time) {
            // Overflow-safe 64-bit frame index (fixes cloaks freezing on frame 1 at 2026 epoch timestamps!)
            return CosmeticAnimation.frameIndex(time, model.animFps, baseFrames.length);
        }
    }

    private CosmeticRigRenderer() {}

    static void clearCache() {
        CACHE.clear();
        var mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            for (Identifier id : REGISTERED_DYNAMIC) {
                try { mc.getTextureManager().release(id); } catch (Exception ignored) {}
            }
        }
        REGISTERED_DYNAMIC.clear();
    }

    static CompiledRig get(String id) {
        if (id == null || id.isEmpty() || "none".equals(id)) return null;
        CompiledRig cached = CACHE.get(id);
        if (cached != null) return cached;
        CosmeticRig.Model model = CosmeticRig.load(id);
        if (model == null || model.bones.isEmpty()) return null;
        CompiledRig compiled = compile(model);
        if (CACHE.size() >= 360) {
            String first = CACHE.keySet().iterator().next();
            CACHE.remove(first);
        }
        CACHE.put(id, compiled);
        return compiled;
    }

    /**
     * Applies per-slot user offset (Blockbench px), uniform scale, and pitch/yaw/roll tilt
     * cleanly around the slot's natural visual pivot in MC entity PoseStack coordinates (+Y down, +Z back).
     */
    static void applySlotTransform(PoseStack pose, String slot, SkyPilotConfig.SlotTransform tr) {
        if (tr == null || tr.isIdentity()) return;
        float[] pivot = slotPivotModelPart(slot);
        float px = pivot[0] / 16.0f, py = pivot[1] / 16.0f, pz = pivot[2] / 16.0f;
        pose.translate((float) (tr.offsetX / 16.0), (float) (-tr.offsetY / 16.0), (float) (tr.offsetZ / 16.0));
        boolean hasRot = Math.abs(tr.rotX) > 0.01 || Math.abs(tr.rotY) > 0.01 || Math.abs(tr.rotZ) > 0.01;
        boolean hasScale = Math.abs(tr.scale - 1.0) > 0.005;
        if (hasRot || hasScale) {
            pose.translate(px, py, pz);
            if (Math.abs(tr.rotY) > 0.01) pose.mulPose(Axis.YP.rotationDegrees((float) tr.rotY));
            if (Math.abs(tr.rotX) > 0.01) pose.mulPose(Axis.XP.rotationDegrees((float) -tr.rotX));
            if (Math.abs(tr.rotZ) > 0.01) pose.mulPose(Axis.ZP.rotationDegrees((float) -tr.rotZ));
            if (hasScale) {
                float sc = (float) tr.scale;
                pose.scale(sc, sc, sc);
            }
            pose.translate(-px, -py, -pz);
        }
    }

    private static float[] slotPivotModelPart(String slot) {
        return switch (slot == null ? "" : slot) {
            case "hat" -> new float[]{0.0f, -4.0f, 0.0f};
            case "cape", "back" -> new float[]{0.0f, 3.0f, 3.5f};
            case "aura" -> new float[]{0.0f, 8.0f, 0.0f};
            case "pet" -> new float[]{15.0f, -1.5f, 2.0f};
            case "companion" -> new float[]{-16.5f, 20.0f, 2.5f};
            default -> new float[]{0.0f, 0.0f, 0.0f};
        };
    }

    /** Submits an animated 3D cosmetic rig into Minecraft's live entity/world renderer. */
    static int submit(
            CompiledRig rig,
            PoseStack pose,
            SubmitNodeCollector collector,
            int light,
            double time,
            double speed,
            boolean sneaking,
            boolean falling,
            boolean flying,
            double vertSpeed,
            double yawTurnRate,
            boolean glowEnabled
    ) {
        if (rig == null) return 0;
        int fi = rig.frameIndex(time);
        FrameTree baseTree = rig.baseFrames[fi];
        applyPose(rig.model, baseTree, time, speed, sneaking, falling, flying, vertSpeed, yawTurnRate);

        RenderType baseType = rig.model.translucent
                ? RenderTypes.entityTranslucent(rig.baseTexture)
                : RenderTypes.entityCutout(rig.baseTexture);
        int baseLight = (rig.model.emissive && glowEnabled && rig.emissiveTexture == null && rig.emissiveCubeFrames == null)
                ? FULL_BRIGHT : light;
        collector.submitModelPart(baseTree.root, pose, baseType, baseLight, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);

        if (glowEnabled) {
            if (rig.emissiveTexture != null) {
                collector.submitModelPart(
                        baseTree.root, pose,
                        RenderTypes.entityTranslucentEmissive(rig.emissiveTexture),
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null
                );
            } else if (rig.emissiveCubeFrames != null) {
                FrameTree emTree = rig.emissiveCubeFrames[fi];
                applyPose(rig.model, emTree, time, speed, sneaking, falling, flying, vertSpeed, yawTurnRate);
                collector.submitModelPart(
                        emTree.root, pose,
                        RenderTypes.entityTranslucentEmissive(rig.baseTexture),
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null
                );
            }
        }
        return baseTree.cubeCount;
    }

    /** Renders a cosmetic rig into GUI with optional vertical offset, centered solo card framing, and per-slot transform. */
    static void renderGui(
            CompiledRig rig,
            GuiGraphicsExtractor g,
            float scale,
            float rotX,
            float rotY,
            float yOffset,
            int x0, int y0, int x1, int y1,
            double time,
            boolean glowEnabled,
            boolean centeredSolo,
            SkyPilotConfig.SlotTransform tr
    ) {
        if (rig == null) return;
        int fi = rig.frameIndex(time);
        FrameTree baseTree = rig.baseFrames[fi];
        applyPose(rig.model, baseTree, time, 0.04, false, false, false, 0.0, 0.0);

        boolean useCenter = centeredSolo && rig.guiCenteredBaseModels != null;
        if (!useCenter) {
            applyWrapTransform(rig.guiTransformWraps[fi], tr);
        }

        Model.Simple baseM = useCenter ? rig.guiCenteredBaseModels[fi] : rig.guiBaseModels[fi];
        g.skin(baseM, rig.baseTexture, scale, rotX, rotY, yOffset, x0, y0, x1, y1);
        if (glowEnabled) {
            Model.Simple[] glowArr = useCenter ? rig.guiCenteredGlowMaskModels : rig.guiGlowMaskModels;
            Model.Simple[] emArr = useCenter ? rig.guiCenteredEmissiveCubeModels : rig.guiEmissiveCubeModels;
            if (glowArr != null && rig.emissiveTexture != null) {
                g.skin(glowArr[fi], rig.emissiveTexture, scale, rotX, rotY, yOffset, x0, y0, x1, y1);
            } else if (emArr != null && rig.emissiveCubeFrames != null) {
                FrameTree emTree = rig.emissiveCubeFrames[fi];
                applyPose(rig.model, emTree, time, 0.04, false, false, false, 0.0, 0.0);
                if (!useCenter && rig.guiEmissiveTransformWraps != null) {
                    applyWrapTransform(rig.guiEmissiveTransformWraps[fi], tr);
                }
                g.skin(emArr[fi], rig.baseTexture, scale, rotX, rotY, yOffset, x0, y0, x1, y1);
            }
        }
    }

    private static void applyWrapTransform(ModelPart wrap, SkyPilotConfig.SlotTransform tr) {
        if (wrap == null) return;
        if (tr == null || tr.isIdentity()) {
            wrap.x = wrap.y = wrap.z = 0.0f;
            wrap.xRot = wrap.yRot = wrap.zRot = 0.0f;
            wrap.xScale = wrap.yScale = wrap.zScale = 1.0f;
        } else {
            wrap.x = (float) tr.offsetX;
            wrap.y = (float) -tr.offsetY;
            wrap.z = (float) tr.offsetZ;
            wrap.xRot = (float) -Math.toRadians(tr.rotX);
            wrap.yRot = (float) Math.toRadians(tr.rotY);
            wrap.zRot = (float) -Math.toRadians(tr.rotZ);
            float sc = (float) tr.scale;
            wrap.xScale = wrap.yScale = wrap.zScale = sc;
        }
    }

    private static void applyPose(
            CosmeticRig.Model model,
            FrameTree tree,
            double time, double speed,
            boolean sneaking, boolean falling, boolean flying,
            double vertSpeed, double yawTurnRate
    ) {
        for (BoundBone bb : tree.bones) {
            CosmeticAnimation.BoneTransform tr = model.evaluateBone(
                    bb.def, time, speed, sneaking, falling, flying, vertSpeed, yawTurnRate
            );
            bb.part.visible = tr.visible();
            bb.part.x = bb.baseX + (float) tr.dx();
            bb.part.y = bb.baseY - (float) tr.dy();
            bb.part.z = bb.baseZ + (float) tr.dz();
            bb.part.xRot = bb.baseRx - (float) Math.toRadians(tr.rxDeg());
            bb.part.yRot = bb.baseRy + (float) Math.toRadians(tr.ryDeg());
            bb.part.zRot = bb.baseRz - (float) Math.toRadians(tr.rzDeg());
            bb.part.xScale = (float) tr.sx();
            bb.part.yScale = (float) tr.sy();
            bb.part.zScale = (float) tr.sz();
        }
    }

    private static CompiledRig compile(CosmeticRig.Model model) {
        Identifier baseTex = resolveTexture(model, false);
        Identifier emTex = resolveTexture(model, true);

        int frames = Math.max(1, model.animFrames);
        boolean hasCubeEmissive = emTex == null && hasAnyEmissiveCube(model.bones);
        FrameTree[] baseTrees = new FrameTree[frames];
        FrameTree[] emTrees = hasCubeEmissive ? new FrameTree[frames] : null;

        for (int f = 0; f < frames; f++) {
            baseTrees[f] = buildFrameTree(model, f, false);
            if (emTrees != null) emTrees[f] = buildFrameTree(model, f, true);
        }
        return new CompiledRig(model, baseTex, emTex, baseTrees, emTrees);
    }

    private static Identifier resolveTexture(CosmeticRig.Model model, boolean emissive) {
        if (model.custom) {
            byte[] bytes = emissive ? model.embeddedEmissiveBytes : model.embeddedTextureBytes;
            if (bytes == null || bytes.length == 0) {
                return emissive ? null : WHITE;
            }
            String safe = model.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            Identifier dynId = Identifier.fromNamespaceAndPath("skypilot", "custom_cosmetic/" + safe + (emissive ? "_glow" : ""));
            try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
                NativeImage img = NativeImage.read(in);
                Minecraft.getInstance().getTextureManager().register(dynId, new DynamicTexture(() -> "SkyPilot custom " + model.id, img));
                REGISTERED_DYNAMIC.add(dynId);
                return dynId;
            } catch (Exception e) {
                WardrobeRuntime.LOG.warn("Failed to upload custom texture for {}: {}", model.id, e.getMessage());
                return emissive ? null : WHITE;
            }
        }
        String path = emissive ? model.emissiveTexture : model.texture;
        if (path == null || path.isEmpty()) return emissive ? null : WHITE;
        return Identifier.fromNamespaceAndPath("skypilot", path);
    }

    private static boolean hasAnyEmissiveCube(List<CosmeticRig.Bone> bones) {
        for (CosmeticRig.Bone b : bones) {
            if (b.emissive) return true;
            for (CosmeticRig.Cube c : b.cubes) if (c.emissive) return true;
            if (hasAnyEmissiveCube(b.children)) return true;
        }
        return false;
    }

    private static FrameTree buildFrameTree(CosmeticRig.Model model, int frameIdx, boolean emissiveOnly) {
        List<BoundBone> bound = new ArrayList<>();
        int[] counter = {0};
        Map<String, ModelPart> topChildren = new LinkedHashMap<>();
        int idx = 0;
        for (CosmeticRig.Bone b : model.bones) {
            ModelPart child = buildBonePart(model, b, 0.0, 24.0, 0.0, frameIdx, emissiveOnly, bound, counter);
            topChildren.put(b.name + "_" + (idx++), child);
        }
        ModelPart root = new ModelPart(List.of(), topChildren);
        return new FrameTree(root, List.copyOf(bound), counter[0]);
    }

    private static ModelPart buildBonePart(
            CosmeticRig.Model model,
            CosmeticRig.Bone bone,
            double parentPx, double parentPy, double parentPz,
            int frameIdx,
            boolean emissiveOnly,
            List<BoundBone> bound,
            int[] counter
    ) {
        double px = bone.pivot[0], py = bone.pivot[1], pz = bone.pivot[2];
        List<ModelPart.Cube> alignedCubes = new ArrayList<>();
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int ci = 0;

        for (CosmeticRig.Cube c : bone.cubes) {
            if (emissiveOnly && !c.emissive && !bone.emissive) continue;
            boolean rotated = Math.abs(c.rot[0]) > 0.01 || Math.abs(c.rot[1]) > 0.01 || Math.abs(c.rot[2]) > 0.01;
            if (!rotated) {
                alignedCubes.add(buildCube(model, c, px, py, pz, frameIdx));
                counter[0]++;
            } else {
                double cpx = c.pivot[0], cpy = c.pivot[1], cpz = c.pivot[2];
                ModelPart.Cube mcCube = buildCube(model, c, cpx, cpy, cpz, frameIdx);
                ModelPart sub = new ModelPart(List.of(mcCube), Map.of());
                sub.x = (float) (cpx - px);
                sub.y = (float) -(cpy - py);
                sub.z = (float) (cpz - pz);
                sub.xRot = (float) -Math.toRadians(c.rot[0]);
                sub.yRot = (float) Math.toRadians(c.rot[1]);
                sub.zRot = (float) -Math.toRadians(c.rot[2]);
                children.put("rc_" + (ci++), sub);
                counter[0]++;
            }
        }

        int bi = 0;
        for (CosmeticRig.Bone child : bone.children) {
            children.put(child.name + "_" + (bi++), buildBonePart(model, child, px, py, pz, frameIdx, emissiveOnly, bound, counter));
        }

        ModelPart part = new ModelPart(alignedCubes, children);
        float bx = (float) (px - parentPx);
        float by = (float) -(py - parentPy);
        float bz = (float) (pz - parentPz);
        float brx = (float) -Math.toRadians(bone.rot[0]);
        float bry = (float) Math.toRadians(bone.rot[1]);
        float brz = (float) -Math.toRadians(bone.rot[2]);
        part.x = bx;
        part.y = by;
        part.z = bz;
        part.xRot = brx;
        part.yRot = bry;
        part.zRot = brz;
        bound.add(new BoundBone(bone, part, bx, by, bz, brx, bry, brz));
        return part;
    }

    private static ModelPart.Cube buildCube(
            CosmeticRig.Model model,
            CosmeticRig.Cube c,
            double pivX, double pivY, double pivZ,
            int frameIdx
    ) {
        float ox = (float) (c.from[0] - pivX);
        float oy = (float) -(c.to[1] - pivY);
        float oz = (float) (c.from[2] - pivZ);
        float sx = (float) Math.max(0.05, c.sizeX());
        float sy = (float) Math.max(0.05, c.sizeY());
        float sz = (float) Math.max(0.05, c.sizeZ());
        float inf = (float) c.inflate;
        float texW = model.texWidth;
        float fullTexH = model.texHeight * (float) Math.max(1, model.animFrames);

        int boxU = Math.round(c.uv[0]);
        int boxV = Math.round(c.uv[1] + frameIdx * model.texHeight);
        ModelPart.Cube cube = new ModelPart.Cube(
                boxU, boxV, ox, oy, oz, sx, sy, sz,
                inf, inf, inf, c.mirror, texW, fullTexH, EnumSet.allOf(Direction.class)
        );
        if (c.faces != null) {
            remapFaces(cube, c.faces, texW, model.texHeight, fullTexH, frameIdx);
        }
        return cube;
    }

    private static void remapFaces(
            ModelPart.Cube cube,
            CosmeticRig.FaceMap faces,
            float texW,
            float frameH,
            float fullTexH,
            int frameIdx
    ) {
        float vOffset = frameIdx * frameH;
        for (int i = 0; i < cube.polygons.length; i++) {
            ModelPart.Polygon poly = cube.polygons[i];
            var n = poly.normal();
            String faceName;
            if (n.x() > 0.5f) faceName = "east";
            else if (n.x() < -0.5f) faceName = "west";
            else if (n.y() < -0.5f) faceName = "up";
            else if (n.y() > 0.5f) faceName = "down";
            else if (n.z() < -0.5f) faceName = "north";
            else faceName = "south";

            CosmeticRig.FaceUv fuv = faces.get(faceName);
            if (fuv == null || fuv.uv == null || fuv.uv.length < 4) continue;

            float u0 = fuv.uv[0] / texW;
            float v0 = (fuv.uv[1] + vOffset) / fullTexH;
            float u1 = fuv.uv[2] / texW;
            float v1 = (fuv.uv[3] + vOffset) / fullTexH;

            ModelPart.Vertex[] verts = poly.vertices().clone();
            float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
            for (var v : verts) {
                if (v.u() < minU) minU = v.u();
                if (v.u() > maxU) maxU = v.u();
                if (v.v() < minV) minV = v.v();
                if (v.v() > maxV) maxV = v.v();
            }
            float midU = (minU + maxU) * 0.5f;
            float midV = (minV + maxV) * 0.5f;
            for (int j = 0; j < verts.length; j++) {
                float nu = verts[j].u() <= midU ? u0 : u1;
                float nv = verts[j].v() <= midV ? v0 : v1;
                verts[j] = verts[j].remap(nu, nv);
            }
            cube.polygons[i] = new ModelPart.Polygon(verts, n);
        }
    }
}

