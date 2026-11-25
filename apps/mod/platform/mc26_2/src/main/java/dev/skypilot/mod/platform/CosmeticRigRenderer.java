package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticRig;
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
 * hierarchies in Minecraft 26.2 — supporting both bundled resource textures and user-imported
 * custom {@code .bbmodel} / {@code .png} dynamic textures, multi-frame animated texture strips,
 * translucent materials, and fullbright emissive glow overlays.
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
        final Identifier emissiveTexture; // non-null if separate _glow.png mask exists
        final FrameTree[] baseFrames;
        final FrameTree[] emissiveCubeFrames; // non-null if any cube has emissive=true
        final Model.Simple[] guiBaseModels;
        final Model.Simple[] guiGlowMaskModels;
        final Model.Simple[] guiEmissiveCubeModels;

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
            this.guiBaseModels = new Model.Simple[baseFrames.length];
            this.guiGlowMaskModels = emissiveTexture != null ? new Model.Simple[baseFrames.length] : null;
            this.guiEmissiveCubeModels = emissiveCubeFrames != null ? new Model.Simple[emissiveCubeFrames.length] : null;
            for (int i = 0; i < baseFrames.length; i++) {
                this.guiBaseModels[i] = new Model.Simple(
                        baseFrames[i].root,
                        model.translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout
                );
                if (this.guiGlowMaskModels != null) {
                    this.guiGlowMaskModels[i] = new Model.Simple(baseFrames[i].root, RenderTypes::entityTranslucentEmissive);
                }
                if (this.guiEmissiveCubeModels != null) {
                    this.guiEmissiveCubeModels[i] = new Model.Simple(emissiveCubeFrames[i].root, RenderTypes::entityTranslucentEmissive);
                }
            }
        }

        int frameIndex(double time) {
            if (baseFrames.length <= 1) return 0;
            return Math.floorMod((int) Math.floor(time * model.animFps), baseFrames.length);
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
        if (CACHE.size() >= 256) {
            String first = CACHE.keySet().iterator().next();
            CACHE.remove(first);
        }
        CACHE.put(id, compiled);
        return compiled;
    }

    /** Submits an animated 3D cosmetic rig into Minecraft's live entity renderer. */
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

    /** Renders a cosmetic rig into the 3D GUI Studio / Wardrobe item card. */
    static void renderGui(
            CompiledRig rig,
            GuiGraphicsExtractor g,
            float scale,
            float rotX,
            float rotY,
            int x0, int y0, int x1, int y1,
            double time,
            boolean glowEnabled
    ) {
        if (rig == null) return;
        int fi = rig.frameIndex(time);
        FrameTree baseTree = rig.baseFrames[fi];
        applyPose(rig.model, baseTree, time, 0.04, false, false, false, 0.0, 0.0);
        g.skin(rig.guiBaseModels[fi], rig.baseTexture, scale, rotX, rotY, 0.0f, x0, y0, x1, y1);
        if (glowEnabled) {
            if (rig.guiGlowMaskModels != null && rig.emissiveTexture != null) {
                g.skin(rig.guiGlowMaskModels[fi], rig.emissiveTexture, scale, rotX, rotY, 0.0f, x0, y0, x1, y1);
            } else if (rig.guiEmissiveCubeModels != null && rig.emissiveCubeFrames != null) {
                FrameTree emTree = rig.emissiveCubeFrames[fi];
                applyPose(rig.model, emTree, time, 0.04, false, false, false, 0.0, 0.0);
                g.skin(rig.guiEmissiveCubeModels[fi], rig.baseTexture, scale, rotX, rotY, 0.0f, x0, y0, x1, y1);
            }
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
                SkyPilot.LOG.warn("Failed to upload custom texture for {}: {}", model.id, e.getMessage());
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
        // Origin of player body/head ModelPart is at Blockbench (0, 24, 0)
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
