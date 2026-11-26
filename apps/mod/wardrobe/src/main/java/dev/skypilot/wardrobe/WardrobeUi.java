package dev.skypilot.wardrobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Self-contained dark sleek UI primitives for the standalone Wardrobe mod. */
final class WardrobeUi {
    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFAAAAAA;
    static final int DARK_GRAY = 0xFF666666;
    static final int PURPLE = 0xFFA38BFA;
    static final int CYAN = 0xFF55D6D2;
    static final int GREEN = 0xFF55FF55;
    static final int RED = 0xFFFF5555;
    static final int PANEL = 0xF0111827;
    static final int PANEL_BORDER = 0xFF354460;

    private WardrobeUi() {}

    static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fill(0, 0, width, height, 0xFF090D16);
        for (int i = 0; i < 24; i++) {
            int y = i * height / 24;
            int shade = 12 + i / 2;
            g.fill(0, y, width, (i + 1) * height / 24, 0xFF000000 | (shade << 16) | ((shade + 4) << 8) | (shade + 14));
        }
        for (int i = 0; i < 48; i++) {
            int x = Math.floorMod(i * 127 + 43, Math.max(1, width));
            int y = Math.floorMod(i * 73 + 19, Math.max(1, height));
            g.fill(x, y, x + 1, y + 1, 0xFF3C5065);
        }
        g.fill(0, 0, width, 2, PURPLE);
        g.fill(width / 2, 0, width, 2, CYAN);
    }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, PANEL);
        g.outline(x, y, w, h, PANEL_BORDER);
    }

    static BtnBuilder button(Component label, Button.OnPress press) {
        return new BtnBuilder(label, press);
    }

    static BtnBuilder button(String label, Button.OnPress press) {
        return new BtnBuilder(Component.literal(label), press);
    }

    static final class StyledButton extends Button {
        private StyledButton(int x, int y, int w, int h, Component label, OnPress press) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            paint(g, this);
        }

        static void paint(GuiGraphicsExtractor g, AbstractWidget b) {
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            boolean hi = b.active && b.isHoveredOrFocused();
            int border = hi ? PURPLE : PANEL_BORDER;
            int fill = !b.active ? 0xFF111722 : (hi ? 0xFF302D50 : 0xFF1B2538);
            g.fill(x + 2, y, x + w - 2, y + h, border);
            g.fill(x, y + 2, x + w, y + h - 2, border);
            g.fill(x + 2, y + 1, x + w - 2, y + h - 1, fill);
            g.fill(x + 1, y + 2, x + w - 1, y + h - 2, fill);
            if (hi) g.fill(x + 4, y + h - 2, x + w - 4, y + h - 1, CYAN);
            var font = Minecraft.getInstance().font;
            String lbl = font.plainSubstrByWidth(b.getMessage().getString(), Math.max(0, w - 8));
            g.centeredText(font, Component.literal(lbl), x + w / 2, y + (h - 8) / 2, b.active ? WHITE : DARK_GRAY);
        }
    }

    static final class BtnBuilder {
        private final Component label;
        private final Button.OnPress press;
        private int x, y, w = 100, h = 18;
        private Tooltip tooltip;

        BtnBuilder(Component label, Button.OnPress press) {
            this.label = label;
            this.press = press;
        }

        BtnBuilder bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        BtnBuilder tooltip(String tip) {
            if (tip != null && !tip.isEmpty()) this.tooltip = Tooltip.create(Component.literal(tip));
            return this;
        }

        Button build() {
            var rt = WardrobeRuntime.get();
            boolean rich = rt == null || rt.appearance().richMenus;
            Button b = rich ? new StyledButton(x, y, w, h, label, press)
                    : Button.builder(label, press).bounds(x, y, w, h).build();
            if (tooltip != null) b.setTooltip(tooltip);
            return b;
        }
    }
}

