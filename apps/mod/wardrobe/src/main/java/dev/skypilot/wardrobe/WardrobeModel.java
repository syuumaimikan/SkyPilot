package dev.skypilot.wardrobe;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Lit 3D studio mannequin & live textured {@code .bbmodel} preview renderer, supporting per-slot
 * transforms (offset, scale, tilt), centered solo Pet/Companion card views, and title-screen preview.
 */
final class WardrobeModel {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skypilot_wardrobe", "wardrobe_palette");
    private static final int[] MANNEQUIN_COLORS = {0x9CAABC, 0x394961, 0x455673, 0x253247, 0x172031, 0x55D6D2};
    private static Model.Simple mannequinModel;
    private static boolean ready;

    private WardrobeModel() {}

    static Model.Simple mannequin() {
        if (!ready) {
            NativeImage image = new NativeImage(16, 16, false);
            for (int i = 0; i < 256; i++) {
                int col = i < MANNEQUIN_COLORS.length ? MANNEQUIN_COLORS[i] : 0xFFFFFF;
                image.setPixel(i % 16, i / 16, 0xFF000000 | col);
            }
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "SkyPilot Wardrobe mannequin", image));
            List<ModelPart.Cube> cubes = new ArrayList<>();
            box(cubes, 0, 1.65, 0, .49, .49, .49, 0);
            box(cubes, 0, 1.08, 0, .50, .65, .25, 1);
            box(cubes, -.39, 1.08, 0, .24, .65, .25, 2);
            box(cubes, .39, 1.08, 0, .24, .65, .25, 2);
            box(cubes, -.13, .43, 0, .24, .65, .25, 3);
            box(cubes, .13, .43, 0, .24, .65, .25, 3);
            box(cubes, -.13, .07, .04, .25, .13, .33, 4);
            box(cubes, .13, .07, .04, .25, .13, .33, 4);
            box(cubes, -.12, 1.68, .249, .065, .045, .009, 3);
            box(cubes, .12, 1.68, .249, .065, .045, .009, 3);
            box(cubes, 0, 1.27, .13, .10, .12, .015, 5);
            mannequinModel = new Model.Simple(new ModelPart(cubes, Map.of()), RenderTypes::entityCutout);
            ready = true;
        }
        return mannequinModel;
    }

    static void renderCard(
            GuiGraphicsExtractor g,
            CosmeticCatalog.Item item,
            boolean glowEnabled,
            int x0, int y0, int x1, int y1,
            double time
    ) {
        var rig = CosmeticRigRenderer.get(item.id());
        if (rig == null) return;
        int w = x1 - x0, h = y1 - y0;
        String slot = item.slot();

        if ("pet".equals(slot) || "companion".equals(slot)) {
            float rotY = 28.0f + (float) (Math.sin(time * 0.9) * 14.0);
            float scale = ("pet".equals(slot) ? 0.56f : 0.44f) * Math.min(w, h);
            CosmeticRigRenderer.renderGui(rig, g, scale, -14.0f, rotY, 0.0f, x0, y0, x1, y1, time, glowEnabled, true, null);
            return;
        }

        if ("hat".equals(slot)) {
            float scale = Math.min(w * 0.34f, h * 0.52f);
            float rotY = 24.0f + (float) (Math.sin(time * 0.8) * 10.0);
            g.skin(mannequin(), TEXTURE, scale, -8.0f, rotY, 0.42f, x0, y0, x1, y1);
            CosmeticRigRenderer.renderGui(rig, g, scale, -8.0f, rotY, 0.42f, x0, y0, x1, y1, time, glowEnabled, false, null);
            return;
        }

        if ("aura".equals(slot)) {
            float scale = Math.min(w * 0.22f, h * 0.36f);
            float rotY = 32.0f + (float) (Math.sin(time * 0.6) * 12.0);
            g.skin(mannequin(), TEXTURE, scale, -10.0f, rotY, 0.0f, x0, y0, x1, y1);
            CosmeticRigRenderer.renderGui(rig, g, scale, -10.0f, rotY, 0.0f, x0, y0, x1, y1, time, glowEnabled, false, null);
            return;
        }

        float scale = "back".equals(slot) ? Math.min(w * 0.23f, h * 0.38f) : Math.min(w * 0.28f, h * 0.44f);
        float rotY = 158.0f + (float) (Math.sin(time * 0.7) * 8.0);
        g.skin(mannequin(), TEXTURE, scale, -8.0f, rotY, 0.0f, x0, y0, x1, y1);
        CosmeticRigRenderer.renderGui(rig, g, scale, -8.0f, rotY, 0.0f, x0, y0, x1, y1, time, glowEnabled, false, null);
    }

    static void renderStudio(
            GuiGraphicsExtractor g,
            SkyPilotConfig.Appearance a,
            float scale,
            float rotX,
            float rotY,
            int x0, int y0, int x1, int y1,
            double time
    ) {
        g.skin(mannequin(), TEXTURE, scale, rotX, rotY, 0.0f, x0, y0, x1, y1);
        for (String slot : CosmeticCatalog.SLOTS) {
            String id = CosmeticCatalog.equippedId(a, slot);
            if ("none".equals(id)) continue;
            var rig = CosmeticRigRenderer.get(id);
            if (rig == null) continue;
            float s = rig.model.scalable
                    ? scale * (float) Math.max(0.6, Math.min(1.5, a.wingScale <= 0 ? 1.0 : a.wingScale))
                    : scale;
            CosmeticRigRenderer.renderGui(rig, g, s, rotX, rotY, 0.0f, x0, y0, x1, y1, time, a.cosmeticGlow, false, a.transformOf(slot));
        }
    }

    private static void box(List<ModelPart.Cube> cubes, double x, double y, double z, double w, double h, double d, int index) {
        var cube = new ModelPart.Cube(0, 0, (float) ((x - w / 2) * 16), (float) ((1.5 - y - h / 2) * 16), (float) ((-z - d / 2) * 16),
                (float) (w * 16), (float) (h * 16), (float) (d * 16), 0, 0, 0, false, 16, 16, EnumSet.allOf(Direction.class));
        for (int i = 0; i < cube.polygons.length; i++) {
            var p = cube.polygons[i];
            var vertices = p.vertices().clone();
            for (int j = 0; j < vertices.length; j++) {
                vertices[j] = vertices[j].remap((index % 16 + .5f) / 16f, (index / 16 + .5f) / 16f);
            }
            cube.polygons[i] = new ModelPart.Polygon(vertices, p.normal());
        }
        cubes.add(cube);
    }
}

