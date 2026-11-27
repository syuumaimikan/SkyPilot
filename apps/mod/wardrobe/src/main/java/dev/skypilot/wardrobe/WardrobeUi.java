package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Flagship glassmorphic UI kit for standalone {@code skypilot_wardrobe}:
 * smooth animated hover easing, semantic pill styles, radial ambient studio spotlights,
 * 4 selectable 3D studio environment backdrops, and crisp tactile sound cues.
 */
final class WardrobeUi {
    static final Identifier TEX_GLOW = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/glow_soft.png");
    static final Identifier TEX_RING = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/shockwave_ring.png");

    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFB0BCD0;
    static final int DARK_GRAY = 0xFF64748B;
    static final int PURPLE = 0xFFA38BFA;
    static final int CYAN = 0xFF55D6D2;
    static final int GREEN = 0xFF4ADE80;
    static final int PINK = 0xFFF472B6;
    static final int GOLD = 0xFFFBBF24;
    static final int RED = 0xFFFB7185;
    static final int PANEL_TOP = 0xEE121B2E;
    static final int PANEL_BOT = 0xF4090E1B;
    static final int PANEL_BORDER = 0xFF2D3C58;

    enum Style {
        DEFAULT,
        PRIMARY,
        ACTIVE_TAB,
        EQUIPPED,
        DANGER
    }

    private WardrobeUi() {}

    /** Rich obsidian-midnight glassmorphic full-screen studio backdrop with gently breathing ambient glow. */
    static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        double time = CosmeticAnimation.animationTimeSeconds();
        g.fillGradient(0, 0, width, height, 0xF2070B16, 0xF70D1426);

        // Soft animated radial ambient studio glows (top-left violet, bottom-right cyan)
        int glowA = 42 + (int) (10.0 * Math.sin(time * 0.9));
        int glowB = 38 + (int) (10.0 * Math.cos(time * 0.75));
        int rad = Math.max(180, Math.min(width, height) * 3 / 4);
        blitGlow(g, width / 5, height / 5, rad, rad, (glowA << 24) | 0x7C3AED);
        blitGlow(g, width * 4 / 5, height * 4 / 5, rad, rad, (glowB << 24) | 0x06B6D4);

        // Subtle twinkling starlight micro-dots
        for (int i = 0; i < 42; i++) {
            int x = Math.floorMod(i * 131 + 43, Math.max(1, width));
            int y = Math.floorMod(i * 79 + 19, Math.max(1, height));
            int alpha = 35 + (int) (55.0 * (0.5 + 0.5 * Math.sin(time * (1.2 + (i % 5) * 0.35) + i * 1.7)));
            g.fill(x, y, x + 1, y + 1, (alpha << 24) | ((i % 3 == 0) ? 0xA5F3FC : 0xDDD6FE));
        }

        // Sleek 2px prismatic top studio accent bar
        g.fill(0, 0, width / 2, 2, PURPLE);
        g.fill(width / 2, 0, width, 2, CYAN);
    }

    /** Frosted glassmorphic container panel with soft drop shadow &amp; top inner glass sheen. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x44000000);
        g.fillGradient(x, y, x + w, y + h, PANEL_TOP, PANEL_BOT);
        g.outline(x, y, w, h, PANEL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x24FFFFFF);
    }

    /** Draws one of 4 curated 3D Studio lighting environments + glowing circular pedestal floor! */
    static void studioBackdrop(
            GuiGraphicsExtractor g, int x, int y, int w, int h, int mode, int accentRgb, double time
    ) {
        int topCol, botCol, floorGlowRgb;
        switch (Math.floorMod(mode, 4)) {
            case 1 -> { // Neon Cyber
                topCol = 0xF2120826;
                botCol = 0xF507182B;
                floorGlowRgb = 0x22D3EE;
            }
            case 2 -> { // Golden Sunset
                topCol = 0xF2261328;
                botCol = 0xF53B1D1B;
                floorGlowRgb = 0xFBBF24;
            }
            case 3 -> { // Bright Daylight Studio
                topCol = 0xE834577A;
                botCol = 0xEC1E2E46;
                floorGlowRgb = 0xE0F2FE;
            }
            default -> { // Midnight Studio
                topCol = 0xF20A1022;
                botCol = 0xF6060A14;
                floorGlowRgb = accentRgb & 0xFFFFFF;
            }
        }
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, topCol, botCol);

        int cx = x + w / 2;
        int cy = y + h / 2;
        int spot = Math.min(w, h) + 24;
        int pulseA = 46 + (int) (14.0 * Math.sin(time * 1.8));
        blitGlow(g, cx, cy - h / 12, spot, spot, (pulseA << 24) | floorGlowRgb);

        // Subtle cyber perspective lines in Neon mode
        if (Math.floorMod(mode, 4) == 1) {
            int gridTop = y + h * 3 / 4;
            for (int gy = gridTop; gy < y + h - 4; gy += 9) {
                g.fill(x + 4, gy, x + w - 4, gy + 1, 0x2022D3EE);
            }
        }

        // Glowing circular showcase pedestal under the character's feet
        int floorY = y + h - 14;
        int pedW = Math.min(w - 20, 108);
        int pedH = Math.max(18, pedW / 4);
        blitGlow(g, cx, floorY, pedW + 20, pedH + 14, (78 << 24) | floorGlowRgb);
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_RING,
                cx - pedW / 2, floorY - pedH / 2, 0f, 0f, pedW, pedH, 1, 1, 1, 1,
                (145 << 24) | floorGlowRgb);
    }

    static String backdropName(int mode, boolean ja) {
        return switch (Math.floorMod(mode, 4)) {
            case 1 -> ja ? "照明:ネオン" : "Env:Neon";
            case 2 -> ja ? "照明:夕暮" : "Env:Sunset";
            case 3 -> ja ? "照明:昼空" : "Env:Day";
            default -> ja ? "照明:夜景" : "Env:Night";
        };
    }

    static void blitGlow(GuiGraphicsExtractor g, int cx, int cy, int w, int h, int argb) {
        if (w <= 2 || h <= 2 || ((argb >>> 24) & 0xFF) <= 2) return;
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_GLOW, cx - w / 2, cy - h / 2, 0f, 0f, w, h, 1, 1, 1, 1, argb);
    }

    /** Crisp subtle client-side sound when equipping/unequipping a cosmetic (zero packets!). */
    static void playEquip(boolean equipped) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        var p = mc.player;
        mc.level.playLocalSound(p.getX(), p.getY() + 1.2, p.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER,
                equipped ? 0.34f : 0.22f, equipped ? 1.38f : 0.88f, false);
    }

    /** Sparkly celebratory chime for applying presets, outfits, favorites, or Chroma! */
    static void playSparkle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        var p = mc.player;
        mc.level.playLocalSound(p.getX(), p.getY() + 1.2, p.getZ(),
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.35f, 1.48f, false);
    }

    static BtnBuilder button(Component label, Button.OnPress press) {
        return new BtnBuilder(label, press);
    }

    static BtnBuilder button(String label, Button.OnPress press) {
        return new BtnBuilder(Component.literal(label), press);
    }

    static final class StyledButton extends Button {
        private final Style style;
        private final int customAccent;
        private float hoverAnim;
        private long lastPaintNs = System.nanoTime();

        private StyledButton(int x, int y, int w, int h, Component label, OnPress press, Style style, int customAccent) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION);
            this.style = style == null ? Style.DEFAULT : style;
            this.customAccent = customAccent;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            long now = System.nanoTime();
            float dt = Math.min(0.08f, Math.max(0.001f, (now - lastPaintNs) * 1e-9f));
            lastPaintNs = now;
            boolean hovered = this.active && this.isHoveredOrFocused();
            float target = hovered ? 1.0f : 0.0f;
            hoverAnim += (target - hoverAnim) * Math.min(1.0f, dt * 15.0f);
            paint(g, this, style, customAccent, hoverAnim);
        }

        private static void paint(GuiGraphicsExtractor g, StyledButton b, Style style, int customAccent, float hover) {
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            int accent = customAccent >= 0 ? (customAccent & 0xFFFFFF) : switch (style) {
                case PRIMARY -> 0xA38BFA;
                case ACTIVE_TAB, EQUIPPED -> 0x55D6D2;
                case DANGER -> 0xFB7185;
                default -> 0xA38BFA;
            };

            int topBase, botBase, borderBase;
            if (!b.active) {
                topBase = 0xFF121824;
                botBase = 0xFF0D121C;
                borderBase = 0xFF212B3D;
            } else {
                topBase = switch (style) {
                    case PRIMARY -> lerpRgb(0x2B2452, 0x3D3274, hover);
                    case ACTIVE_TAB -> lerpRgb(0x163244, 0x1E455C, hover);
                    case EQUIPPED -> lerpRgb(0x13303E, 0x1B4354, hover);
                    case DANGER -> lerpRgb(0x301824, 0x4C2032, hover);
                    default -> lerpRgb(0x1B253A, 0x2A3554, hover);
                };
                botBase = switch (style) {
                    case PRIMARY -> lerpRgb(0x1C173A, 0x2B2356, hover);
                    case ACTIVE_TAB -> lerpRgb(0x102334, 0x163348, hover);
                    case EQUIPPED -> lerpRgb(0x0E222E, 0x143342, hover);
                    case DANGER -> lerpRgb(0x20101A, 0x341624, hover);
                    default -> lerpRgb(0x121A2B, 0x1E2842, hover);
                };
                int idleBorder = switch (style) {
                    case PRIMARY -> 0x6D5CC9;
                    case ACTIVE_TAB, EQUIPPED -> 0x3CB7B5;
                    case DANGER -> 0x7A3348;
                    default -> 0x31415E;
                };
                borderBase = 0xFF000000 | lerpRgb(idleBorder, accent, Math.min(1.0f, hover * 0.9f + (style == Style.ACTIVE_TAB ? 0.35f : 0.0f)));
                topBase |= 0xFF000000;
                botBase |= 0xFF000000;
            }

            // Rounded-corner pill silhouette (1px chamfered corners)
            g.fill(x + 1, y, x + w - 1, y + h, borderBase);
            g.fill(x, y + 1, x + w, y + h - 1, borderBase);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, topBase, botBase);

            // Crisp 1px top specular glass highlight
            if (b.active) {
                int sheenA = 24 + Math.round(hover * 34);
                g.fill(x + 2, y + 1, x + w - 2, y + 2, (sheenA << 24) | 0xFFFFFF);
            }

            // Smooth center-expanding animated bottom glow underline!
            float barFrac = style == Style.ACTIVE_TAB ? Math.max(0.65f, hover) : (style == Style.EQUIPPED ? Math.max(0.45f, hover) : hover);
            if (b.active && barFrac > 0.04f && w > 8) {
                int barW = Math.max(4, Math.round((w - 6) * barFrac));
                int bx = x + (w - barW) / 2;
                int a = Math.min(255, Math.round(barFrac * 240));
                g.fill(bx, y + h - 2, bx + barW, y + h - 1, (a << 24) | accent);
            }

            var font = Minecraft.getInstance().font;
            String lbl = font.plainSubstrByWidth(b.getMessage().getString(), Math.max(0, w - 6));
            int textCol = !b.active ? DARK_GRAY
                    : (style == Style.ACTIVE_TAB || style == Style.EQUIPPED ? 0xFFE6FFFE : WHITE);
            g.centeredText(font, Component.literal(lbl), x + w / 2, y + (h - 8) / 2, textCol);
        }

        private static int lerpRgb(int c0, int c1, float t) {
            float k = Math.max(0.0f, Math.min(1.0f, t));
            int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
            int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int r = Math.round(r0 + (r1 - r0) * k);
            int g = Math.round(g0 + (g1 - g0) * k);
            int b = Math.round(b0 + (b1 - b0) * k);
            return (r << 16) | (g << 8) | b;
        }
    }

    static final class BtnBuilder {
        private final Component label;
        private final Button.OnPress press;
        private int x, y, w = 100, h = 18;
        private Tooltip tooltip;
        private Style style = Style.DEFAULT;
        private int accent = -1;

        BtnBuilder(Component label, Button.OnPress press) {
            this.label = label;
            this.press = press;
        }

        BtnBuilder bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        BtnBuilder style(Style style) {
            this.style = style == null ? Style.DEFAULT : style;
            return this;
        }

        BtnBuilder accent(int rgb) {
            this.accent = rgb & 0xFFFFFF;
            return this;
        }

        BtnBuilder tooltip(String tip) {
            if (tip != null && !tip.isEmpty()) this.tooltip = Tooltip.create(Component.literal(tip));
            return this;
        }

        Button build() {
            var rt = WardrobeRuntime.get();
            boolean rich = rt == null || rt.appearance().richMenus;
            Button b = rich ? new StyledButton(x, y, w, h, label, press, style, accent)
                    : Button.builder(label, press).bounds(x, y, w, h).build();
            if (tooltip != null) b.setTooltip(tooltip);
            return b;
        }
    }
}
