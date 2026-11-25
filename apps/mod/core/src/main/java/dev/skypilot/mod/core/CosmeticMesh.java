package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy voxel projection derived from {@link CosmeticRig} {@code .bbmodel} models in player-local
 * meter coordinates. Primary renderers ({@code CosmeticLayer}, {@code WardrobeModel},
 * {@code AvatarPreview}) render the full hierarchical textured Blockbench rigs directly.
 */
public final class CosmeticMesh {
    public record Voxel(double x, double y, double z, double size, int color) {}

    private CosmeticMesh() {}

    public static List<Voxel> build(SkyPilotConfig.Appearance a) {
        if (a == null) return List.of();
        List<Voxel> out = new ArrayList<>();
        String hatId = "none".equals(a.hat) ? a.accessory : a.hat;
        String backId = "none".equals(a.back) ? a.accessory : a.back;
        collect(out, a.cape, a.color);
        collect(out, hatId, a.color);
        collect(out, backId, a.color);
        collect(out, a.aura, a.color);
        collect(out, a.pet, a.color);
        collect(out, a.companion, a.color);
        return List.copyOf(out);
    }

    private static void collect(List<Voxel> out, String id, int accent) {
        CosmeticRig.Model model = CosmeticRig.load(id);
        if (model == null) return;
        CosmeticCatalog.Item meta = CosmeticCatalog.byId(id);
        int fallback = meta != null ? meta.color() : accent;
        for (CosmeticRig.Bone bone : model.bones) {
            collectBone(out, bone, fallback);
        }
    }

    private static void collectBone(List<Voxel> out, CosmeticRig.Bone bone, int fallback) {
        if (bone.cubes != null) {
            for (CosmeticRig.Cube c : bone.cubes) {
                int col = (c.color != 0 && c.color != 0xFFFFFF) ? (c.color & 0xFFFFFF) : (fallback & 0xFFFFFF);
                double sx = c.sizeX(), sy = c.sizeY(), sz = c.sizeZ();
                int nx = Math.max(1, Math.min(10, (int) Math.round(sx / 1.1)));
                int ny = Math.max(1, Math.min(4, (int) Math.round(sy / 1.1)));
                double stepX = sx / nx, stepY = sy / ny;
                double vSize = Math.max(0.025, Math.min(0.14, Math.max(stepX, Math.max(stepY, Math.min(sz, 1.8))) / 16.0));
                double cz = -(c.from[2] + c.to[2]) * 0.5 / 16.0;
                for (int ix = 0; ix < nx; ix++) {
                    double cx = (c.from[0] + (ix + 0.5) * stepX) / 16.0;
                    for (int iy = 0; iy < ny; iy++) {
                        double cy = (c.from[1] + (iy + 0.5) * stepY) / 16.0;
                        out.add(new Voxel(cx, cy, cz, vSize, col));
                    }
                }
            }
        }
        if (bone.children != null) {
            for (CosmeticRig.Bone child : bone.children) {
                collectBone(out, child, fallback);
            }
        }
    }
}
