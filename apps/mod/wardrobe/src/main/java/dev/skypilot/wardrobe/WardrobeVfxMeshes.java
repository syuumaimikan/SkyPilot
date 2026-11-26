package dev.skypilot.wardrobe;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.util.*;

/**
 * Commercial-grade reusable 3D VFX mesh primitives rendered natively via
 * {@code SubmitNodeCollector.submitModelPart} with hardware ARGB color tinting & fullbright emission.
 */
final class WardrobeVfxMeshes {
    enum Kind {
        QUAD_BILLBOARD,
        RING_FLAT,
        SLASH_ARC,
        PILLAR_BEAM,
        SHARD_3D,
        BOLT_ZIGZAG,
        RIBBON_SEG,
        ORB_CORE,
        DOME_WAVE,
        BEAM_SHAFT
    }

    private static final EnumSet<Direction> FACE_NS = EnumSet.of(Direction.NORTH, Direction.SOUTH);
    private static final EnumSet<Direction> FACE_UD = EnumSet.of(Direction.UP, Direction.DOWN);
    private static final EnumSet<Direction> FACE_EW = EnumSet.of(Direction.EAST, Direction.WEST);
    private static final EnumSet<Direction> FACE_ALL = EnumSet.allOf(Direction.class);

    private static final EnumMap<Kind, ModelPart> MESHES = new EnumMap<>(Kind.class);

    private WardrobeVfxMeshes() {}

    static ModelPart get(Kind kind) {
        return MESHES.computeIfAbsent(kind, WardrobeVfxMeshes::build);
    }

    private static ModelPart build(Kind kind) {
        return switch (kind) {
            case QUAD_BILLBOARD -> buildQuadBillboard();
            case RING_FLAT -> buildFlatRing();
            case SLASH_ARC -> buildCurvedSlashArc();
            case PILLAR_BEAM -> buildCrossedPillar();
            case SHARD_3D -> buildCrystalShard();
            case BOLT_ZIGZAG -> buildLightningBolt();
            case RIBBON_SEG -> buildRibbonSegment();
            case ORB_CORE -> buildOrbCore();
            case DOME_WAVE -> buildShockwaveDome();
            case BEAM_SHAFT -> buildBeamShaft();
        };
    }

    private static ModelPart buildQuadBillboard() {
        ModelPart.Cube c = mappedCube(-8.0f, -8.0f, -0.02f, 16.0f, 16.0f, 0.04f, 0.0f, 0.0f, 1.0f, 1.0f, FACE_NS);
        return new ModelPart(List.of(c), Map.of());
    }

    private static ModelPart buildFlatRing() {
        ModelPart.Cube c = mappedCube(-8.0f, -0.02f, -8.0f, 16.0f, 0.04f, 16.0f, 0.0f, 0.0f, 1.0f, 1.0f, FACE_UD);
        return new ModelPart(List.of(c), Map.of());
    }

    /** True 12-segment sculpted 175-degree anime katana crescent slash arc with smooth UV sweep! */
    private static ModelPart buildCurvedSlashArc() {
        Map<String, ModelPart> segs = new LinkedHashMap<>();
        int count = 12;
        float radius = 19.0f;
        for (int i = 0; i < count; i++) {
            float t0 = i / (float) count;
            float t1 = (i + 1) / (float) count;
            float tm = (t0 + t1) * 0.5f;
            float angDeg = -87.0f + tm * 174.0f;
            float angRad = (float) Math.toRadians(angDeg);
            float taper = (float) Math.pow(Math.sin(tm * Math.PI), 0.65);
            float width = 6.4f * Math.max(0.22f, taper);
            float segLen = 5.2f;

            ModelPart.Cube c = mappedCube(-segLen * 0.5f, -0.03f, -width * 0.5f, segLen, 0.06f, width, t0, 0.0f, t1, 1.0f, FACE_UD);
            ModelPart part = new ModelPart(List.of(c), Map.of());
            part.x = (float) (Math.sin(angRad) * radius);
            part.y = 0.0f;
            part.z = (float) (-Math.cos(angRad) * radius);
            part.yRot = -angRad;
            segs.put("s" + i, part);
        }
        return new ModelPart(List.of(), segs);
    }

    /** Triple-plane crossed vertical light-shaft pillar for volumetric 360-degree visibility. */
    private static ModelPart buildCrossedPillar() {
        Map<String, ModelPart> planes = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            ModelPart p = new ModelPart(List.of(mappedCube(-6.0f, -44.0f, -0.02f, 12.0f, 44.0f, 0.04f, 0, 0, 1, 1, FACE_NS)), Map.of());
            p.yRot = (float) (i * Math.PI / 3.0);
            planes.put("p" + i, p);
        }
        return new ModelPart(List.of(), planes);
    }

    /** Faceted 3D crystal prism shard. */
    private static ModelPart buildCrystalShard() {
        ModelPart.Cube core = unitCube(-2.0f, -5.6f, -2.0f, 4.0f, 11.2f, 4.0f);
        ModelPart.Cube tipU = unitCube(-1.1f, -8.2f, -1.1f, 2.2f, 2.8f, 2.2f);
        ModelPart.Cube tipD = unitCube(-1.1f, 5.4f, -1.1f, 2.2f, 2.8f, 2.2f);
        return new ModelPart(List.of(core, tipU, tipD), Map.of());
    }

    /** Crossed vertical glowing zigzag plasma lightning bolt planes. */
    private static ModelPart buildLightningBolt() {
        ModelPart p1 = new ModelPart(List.of(mappedCube(-7.5f, -34.0f, -0.02f, 15.0f, 34.0f, 0.04f, 0, 0, 1, 1, FACE_NS)), Map.of());
        ModelPart p2 = new ModelPart(List.of(mappedCube(-7.5f, -34.0f, -0.02f, 15.0f, 34.0f, 0.04f, 0, 0, 1, 1, FACE_NS)), Map.of());
        p2.yRot = (float) (Math.PI * 0.5);
        return new ModelPart(List.of(), Map.of("b1", p1, "b2", p2));
    }

    /** Crossed unit-length longitudinal ribbon segment (length 1.0 block = 16px along +Z) for seamless connected 3D trails! */
    private static ModelPart buildRibbonSegment() {
        ModelPart h = new ModelPart(List.of(mappedCube(-2.2f, -0.02f, -8.0f, 4.4f, 0.04f, 16.0f, 0, 0, 1, 1, FACE_UD)), Map.of());
        ModelPart v = new ModelPart(List.of(mappedCube(-0.02f, -2.2f, -8.0f, 0.04f, 4.4f, 16.0f, 0, 0, 1, 1, FACE_EW)), Map.of());
        return new ModelPart(List.of(), Map.of("h", h, "v", v));
    }

    private static ModelPart buildOrbCore() {
        ModelPart.Cube c = unitCube(-2.8f, -2.8f, -2.8f, 5.6f, 5.6f, 5.6f);
        return new ModelPart(List.of(c), Map.of());
    }

    /** Sculpted 3D volumetric shockwave dome (8 angled radial energy facets forming an expanding hemisphere). */
    private static ModelPart buildShockwaveDome() {
        Map<String, ModelPart> facets = new LinkedHashMap<>();
        int n = 8;
        float r = 7.4f;
        for (int i = 0; i < n; i++) {
            float ang = (float) (i * Math.PI * 2.0 / n);
            ModelPart f = new ModelPart(List.of(mappedCube(-4.2f, -6.5f, -0.02f, 8.4f, 6.5f, 0.04f, 0, 0, 1, 1, FACE_NS)), Map.of());
            f.x = (float) (Math.sin(ang) * r);
            f.y = 0.0f;
            f.z = (float) (Math.cos(ang) * r);
            f.yRot = ang;
            f.xRot = (float) Math.toRadians(-34.0);
            facets.put("d" + i, f);
        }
        return new ModelPart(List.of(), facets);
    }

    /** Focused 3D laser / dragon breath beam shaft (length 1.0 block = 16px along +Z, crossed planes). */
    private static ModelPart buildBeamShaft() {
        ModelPart h = new ModelPart(List.of(mappedCube(-3.2f, -0.02f, 0.0f, 6.4f, 0.04f, 16.0f, 0, 0, 1, 1, FACE_UD)), Map.of());
        ModelPart v = new ModelPart(List.of(mappedCube(-0.02f, -3.2f, 0.0f, 0.04f, 6.4f, 16.0f, 0, 0, 1, 1, FACE_EW)), Map.of());
        return new ModelPart(List.of(), Map.of("bh", h, "bv", v));
    }

    private static ModelPart.Cube unitCube(float ox, float oy, float oz, float sx, float sy, float sz) {
        return mappedCube(ox, oy, oz, sx, sy, sz, 0.0f, 0.0f, 1.0f, 1.0f, FACE_ALL);
    }

    private static ModelPart.Cube mappedCube(
            float ox, float oy, float oz, float sx, float sy, float sz,
            float u0, float v0, float u1, float v1,
            Set<Direction> faces
    ) {
        ModelPart.Cube cube = new ModelPart.Cube(
                0, 0, ox, oy, oz, sx, sy, sz,
                0.0f, 0.0f, 0.0f, false, 16.0f, 16.0f, faces
        );
        for (int i = 0; i < cube.polygons.length; i++) {
            ModelPart.Polygon poly = cube.polygons[i];
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
            cube.polygons[i] = new ModelPart.Polygon(verts, poly.normal());
        }
        return cube;
    }
}
