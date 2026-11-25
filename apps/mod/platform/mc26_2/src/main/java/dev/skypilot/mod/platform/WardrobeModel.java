package dev.skypilot.mod.platform;

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
 * Lit 3D studio mannequin & live textured {@code .bbmodel} preview renderer, available both on
 * the title screen (before joining a world) and inside the in-game Wardrobe cards.
 */
final class WardrobeModel {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skypilot", "wardrobe_palette");
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
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "SkyPilot mannequin palette", image));
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

    /** Renders the neutral mannequin plus every equipped {@code .bbmodel} cosmetic with live textures and animation. */
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
            CosmeticRigRenderer.renderGui(rig, g, s, rotX, rotY, x0, y0, x1, y1, time, a.cosmeticGlow);
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
