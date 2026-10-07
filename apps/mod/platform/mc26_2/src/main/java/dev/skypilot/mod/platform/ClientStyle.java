package dev.skypilot.mod.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Static, inexpensive dark backdrop shared by client screens. */
public final class ClientStyle {
    private ClientStyle() {}
    public static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fill(0, 0, width, height, 0xFF090D16);
        for (int i = 0; i < 24; i++) {
            int y = i * height / 24;
            int shade = 12 + i / 2;
            g.fill(0, y, width, (i + 1) * height / 24, 0xFF000000 | shade << 16 | (shade + 4) << 8 | (shade + 14));
        }
        for (int i = 0; i < 48; i++) {
            int x = Math.floorMod(i * 127 + 43, Math.max(1, width));
            int y = Math.floorMod(i * 73 + 19, Math.max(1, height));
            g.fill(x, y, x + 1, y + 1, 0xFF3C5065);
        }
        g.fill(0, 0, width, 2, 0xFFA38BFA);
        g.fill(width / 2, 0, width, 2, 0xFF55D6D2);
    }
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, Ui.PANEL);
        g.outline(x, y, w, h, Ui.PANEL_BORDER);
    }
}
