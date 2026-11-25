package dev.skypilot.launcher.ui;

import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.CosmeticRig;
import dev.skypilot.mod.core.SkyPilotConfig;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Animated, textured, depth-sorted 3D Blockbench ({@code .bbmodel}) cosmetic studio and cached
 * card thumbnail renderer shared with the in-game rigs and PNG textures.
 */
final class AvatarPreview extends JPanel {
    private static final Map<String, BufferedImage> TEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> THUMB_CACHE = new ConcurrentHashMap<>();

    private final Supplier<SkyPilotConfig.Appearance> appearance;
    private final boolean studio;
    private final String cardItemId;
    private final String cardSlot;
    private double yaw, zoom = 1.0;
    private int dragX;
    private javax.swing.Timer animTimer;

    AvatarPreview(Supplier<SkyPilotConfig.Appearance> appearance, boolean studio) {
        this(appearance, studio, null, null);
    }

    AvatarPreview(Supplier<SkyPilotConfig.Appearance> appearance, boolean studio, String cardItemId, String cardSlot) {
        this.appearance = appearance;
        this.studio = studio;
        this.cardItemId = cardItemId;
        this.cardSlot = cardSlot;
        this.yaw = defaultYaw(studio, cardSlot);
        setOpaque(false);
        setFocusable(studio);
        getAccessibleContext().setAccessibleName("3D cosmetic preview");
        if (studio) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragX = e.getX();
                    requestFocusInWindow();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    yaw += (e.getX() - dragX) * .013;
                    dragX = e.getX();
                    repaint();
                }
            });
            addMouseWheelListener(e -> {
                zoom = Math.max(.55, Math.min(1.55, zoom - e.getPreciseWheelRotation() * .07));
                repaint();
            });
            bind("LEFT", -.15);
            bind("RIGHT", .15);
            if (!GraphicsEnvironment.isHeadless()) {
                animTimer = new javax.swing.Timer(45, e -> {
                    if (isShowing()) repaint();
                });
                animTimer.start();
            }
        }
    }

    static void clearCaches() {
        TEX_CACHE.clear();
        THUMB_CACHE.clear();
    }

    private static double defaultYaw(boolean studio, String slot) {
        if (studio) return Math.PI * 0.85;
        if (slot == null) return Math.PI * 0.85;
        return switch (slot) {
            case "hat" -> 0.35;
            case "pet" -> -0.35;
            case "companion" -> 0.42;
            case "aura" -> 0.55;
            default -> Math.PI * 0.88;
        };
    }

    private void bind(String key, double step) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                yaw += step;
                repaint();
            }
        });
    }

    void front() { yaw = .25; repaint(); }
    void back() { yaw = Math.PI * .92; repaint(); }

    private record Face(double[] x, double[] y, double depth, int argb, boolean emissive) {}

    @Override
    protected void paintComponent(Graphics graphics) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Fast cached thumbnail path for collection grid cards (168+ cards scroll at 60 FPS!)
        if (!studio && cardItemId != null) {
            String key = cardItemId + ":" + w + "x" + h;
            BufferedImage cached = THUMB_CACHE.get(key);
            if (cached == null) {
                cached = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D tg = Widgets.smooth(cached.createGraphics());
                renderScene(tg, w, h, appearance.get(), 1.25);
                tg.dispose();
                if (THUMB_CACHE.size() > 400) THUMB_CACHE.clear();
                THUMB_CACHE.put(key, cached);
            }
            graphics.drawImage(cached, 0, 0, null);
            return;
        }

        Graphics2D g = Widgets.smooth(graphics);
        double time = GraphicsEnvironment.isHeadless() ? 1.25 : (System.currentTimeMillis() / 1000.0);
        renderScene(g, w, h, appearance.get(), time);
        g.dispose();
    }

    private void renderScene(Graphics2D g, int w, int h, SkyPilotConfig.Appearance a, double time) {
        if (studio) {
            g.setPaint(new GradientPaint(0, 0, Theme.SURFACE, w, h, Theme.HERO_B));
            g.fillRoundRect(0, 0, w, h, 24, 24);
            g.setColor(Theme.alpha(Theme.ACCENT, 18));
            g.fillOval(w / 2 - w / 3, h / 2 - w / 3, w * 2 / 3, w * 2 / 3);
            g.setColor(Theme.alpha(Theme.ACCENT_2, 38));
            g.drawOval(w / 2 - w / 3, h - 56, w * 2 / 3, 24);
            g.setFont(Theme.font(Font.BOLD, 10));
            g.setColor(Theme.MUTED);
            g.drawString("STUDIO / 3D .BBMODEL PREVIEW", 18, 24);
        }

        double slotZoom = 1.0;
        double shiftX = 0.0, shiftY = 0.0;
        if (!studio && cardSlot != null) {
            switch (cardSlot) {
                case "hat" -> { slotZoom = 1.32; shiftY = -0.35; }
                case "pet" -> { slotZoom = 0.84; shiftX = -0.25; shiftY = -0.15; }
                case "companion" -> { slotZoom = 0.80; shiftX = 0.30; shiftY = 0.20; }
                case "aura" -> { slotZoom = 0.78; shiftY = 0.05; }
                case "back" -> { slotZoom = 0.78; shiftY = 0.02; }
                default -> { slotZoom = 1.0; }
            }
        } else if (studio) {
            boolean wide = !"none".equals(a.back) || !"none".equals(a.pet) || !"none".equals(a.companion) || !"none".equals(a.aura);
            slotZoom = wide ? 0.76 : 0.96;
        }

        double scale = (studio ? Math.min(w * .36, Math.max(20, (h - 102) / 2.25)) : Math.min(w * .40, h * .38)) * zoom * slotZoom;
        double cx = w / 2.0 + shiftX * scale;
        double base = (studio ? h - 62 : h - 10) - shiftY * scale;

        g.setColor(new Color(0, 0, 0, 48));
        g.fillOval((int) (cx - scale * .65), (int) (base - 5), (int) (scale * 1.3), 12);

        ArrayList<Face> faces = new ArrayList<>(512);
        // Original neutral mannequin
        Mat4 id = Mat4.identity();
        addMannequinBox(faces, 0, 1.65, 0, .49, .49, .49, 0x9CAABC);
        addMannequinBox(faces, 0, 1.08, 0, .50, .65, .25, 0x394961);
        addMannequinBox(faces, -.39, 1.08, 0, .24, .65, .25, 0x455673);
        addMannequinBox(faces, .39, 1.08, 0, .24, .65, .25, 0x455673);
        addMannequinBox(faces, -.13, .43, 0, .24, .65, .25, 0x253247);
        addMannequinBox(faces, .13, .43, 0, .24, .65, .25, 0x253247);
        addMannequinBox(faces, -.13, .07, .04, .25, .13, .33, 0x172031);
        addMannequinBox(faces, .13, .07, .04, .25, .13, .33, 0x172031);
        addMannequinBox(faces, -.12, 1.68, .249, .065, .045, .009, 0x253247);
        addMannequinBox(faces, .12, 1.68, .249, .065, .045, .009, 0x253247);
        addMannequinBox(faces, 0, 1.27, .13, .10, .12, .015, 0x55D6D2);

        double wingScale = Math.max(0.6, Math.min(1.5, a.wingScale <= 0 ? 1.0 : a.wingScale));
        boolean hasCape = !"none".equals(a.cape);
        for (String slot : CosmeticCatalog.SLOTS) {
            String itemId = CosmeticCatalog.equippedId(a, slot);
            if ("none".equals(itemId)) continue;
            CosmeticRig.Model model = CosmeticRig.load(itemId);
            if (model == null) continue;
            BufferedImage tex = loadTexture(model, false);
            BufferedImage glowTex = a.cosmeticGlow ? loadTexture(model, true) : null;
            int frameIdx = model.animFrames > 1 ? Math.floorMod((int) Math.floor(time * model.animFps), model.animFrames) : 0;

            Mat4 rootMat = id;
            if ("back".equals(slot) && hasCape) {
                // Stack wings/backwear cleanly behind equipped cloak (matches live Minecraft CosmeticLayer!)
                rootMat = Mat4.translate(0, 0, 1.65);
            }
            if (model.scalable && Math.abs(wingScale - 1.0) > 0.005) {
                rootMat = rootMat.mul(Mat4.translate(0, 21.0, 3.5))
                        .mul(Mat4.scale(wingScale, wingScale, wingScale))
                        .mul(Mat4.translate(0, -21.0, -3.5));
            }
            for (CosmeticRig.Bone bone : model.bones) {
                emitBone(faces, model, bone, rootMat, tex, glowTex, frameIdx, time, a.cosmeticGlow);
            }
        }

        faces.sort(Comparator.comparingDouble(Face::depth));
        for (Face f : faces) {
            Path2D p = new Path2D.Double();
            p.moveTo(cx + f.x[0] * scale, base - f.y[0] * scale);
            for (int i = 1; i < 4; i++) p.lineTo(cx + f.x[i] * scale, base - f.y[i] * scale);
            p.closePath();
            int alpha = (f.argb >>> 24) & 255;
            if (alpha < 10) continue;
            g.setColor(new Color((f.argb >> 16) & 255, (f.argb >> 8) & 255, f.argb & 255, alpha));
            g.fill(p);
        }

        if (studio) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 11));
            String label = "DRAG TO ROTATE  ·  SCROLL TO ZOOM";
            g.drawString(label, (w - g.getFontMetrics().stringWidth(label)) / 2, h - 18);
        }
    }

    private void emitBone(
            ArrayList<Face> faces,
            CosmeticRig.Model model,
            CosmeticRig.Bone bone,
            Mat4 parentMat,
            BufferedImage tex,
            BufferedImage glowTex,
            int frameIdx,
            double time,
            boolean glowEnabled
    ) {
        CosmeticAnimation.BoneTransform tr = model.evaluateBone(bone, time, 0.04, false, false, false, 0.0, 0.0);
        if (!tr.visible()) return;

        double px = bone.pivot[0], py = bone.pivot[1], pz = bone.pivot[2];
        double rx = bone.rot[0] + tr.rxDeg();
        double ry = bone.rot[1] + tr.ryDeg();
        double rz = bone.rot[2] + tr.rzDeg();

        Mat4 m = parentMat
                .mul(Mat4.translate(px + tr.dx(), py + tr.dy(), pz + tr.dz()))
                .mul(Mat4.rotZ(rz))
                .mul(Mat4.rotY(ry))
                .mul(Mat4.rotX(rx))
                .mul(Mat4.scale(tr.sx(), tr.sy(), tr.sz()))
                .mul(Mat4.translate(-px, -py, -pz));

        for (CosmeticRig.Cube c : bone.cubes) {
            Mat4 cm = m;
            if (Math.abs(c.rot[0]) > 0.01 || Math.abs(c.rot[1]) > 0.01 || Math.abs(c.rot[2]) > 0.01) {
                cm = m.mul(Mat4.translate(c.pivot[0], c.pivot[1], c.pivot[2]))
                        .mul(Mat4.rotZ(c.rot[2]))
                        .mul(Mat4.rotY(c.rot[1]))
                        .mul(Mat4.rotX(c.rot[0]))
                        .mul(Mat4.translate(-c.pivot[0], -c.pivot[1], -c.pivot[2]));
            }
            emitBbCube(faces, model, c, cm, tex, glowTex, frameIdx, glowEnabled);
        }
        for (CosmeticRig.Bone child : bone.children) {
            emitBone(faces, model, child, m, tex, glowTex, frameIdx, time, glowEnabled);
        }
    }

    private void emitBbCube(
            ArrayList<Face> faces,
            CosmeticRig.Model model,
            CosmeticRig.Cube c,
            Mat4 m,
            BufferedImage tex,
            BufferedImage glowTex,
            int frameIdx,
            boolean glowEnabled
    ) {
        // High-res illustrated cloak panels subdivide their outer back (+Z / south) face so 40x64 artwork is razor sharp!
        int fullH = model.texHeight * Math.max(1, model.animFrames);
        if ("cape".equals(model.slot) && c.name.startsWith("cape_panel_") && tex != null && c.faces != null && c.faces.south != null) {
            int cols = studio ? 20 : 12;
            int rows = studio ? 4 : 3;
            float[] uv = c.faces.south.uv;
            double x0 = c.from[0], x1 = c.to[0], y0 = c.from[1], y1 = c.to[1], z1 = c.to[2];
            double eps = 0.04;
            for (int r = 0; r < rows; r++) {
                double ty0 = y1 - (r + 1.0) / rows * (y1 - y0) - eps;
                double ty1 = y1 - (r + 0.0) / rows * (y1 - y0) + eps;
                double v = uv[1] + (r + 0.5) / rows * (uv[3] - uv[1]) + frameIdx * model.texHeight;
                for (int col = 0; col < cols; col++) {
                    double tx0 = x0 + (col + 0.0) / cols * (x1 - x0) - eps;
                    double tx1 = x0 + (col + 1.0) / cols * (x1 - x0) + eps;
                    // Viewed from behind (+Z looking to -Z), +X is screen-left, so u runs from uv[2] at x0 to uv[0] at x1
                    double u = uv[2] - (col + 0.5) / cols * (uv[2] - uv[0]);
                    int argb = sampleTex(tex, u, v, model.texWidth, fullH, 0xFF000000 | c.color);
                    int gcol = glowTex != null ? sampleTex(glowTex, u, v, model.texWidth, fullH, 0) : 0;
                    boolean em = glowEnabled && ((gcol >>> 24) > 80);
                    if (em) argb = blendGlow(argb, gcol);
                    // Winding {4, 6, 7, 5} for south (+Z in BB -> -pz BACK in preview space)
                    emitQuadBb(faces, m,
                            new double[]{tx0, ty0, z1}, new double[]{tx0, ty1, z1},
                            new double[]{tx1, ty1, z1}, new double[]{tx1, ty0, z1},
                            argb, em ? 1.08 : 0.98, em);
                }
            }
            // Inner silk lining face (north, -Z in BB -> +pz FRONT) with winding {0, 1, 3, 2}
            double uMid = c.faces.north != null ? (c.faces.north.uv[0] + c.faces.north.uv[2]) * 0.5 : 50.0;
            double vMid = (c.faces.north != null ? (c.faces.north.uv[1] + c.faces.north.uv[3]) * 0.5 : 8.0) + frameIdx * model.texHeight;
            int innerArgb = sampleTex(tex, uMid, vMid, model.texWidth, fullH, 0xFF1A1E2E);
            emitQuadBb(faces, m,
                    new double[]{x0, y0, c.from[2]}, new double[]{x1, y0, c.from[2]},
                    new double[]{x1, y1, c.from[2]}, new double[]{x0, y1, c.from[2]},
                    innerArgb, 0.80, false);
            return;
        }

        double x0 = c.from[0], y0 = c.from[1], z0 = c.from[2];
        double x1 = c.to[0], y1 = c.to[1], z1 = c.to[2];
        double[][] pts = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x1, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1},
        };
        // Outward CCW windings after pz = -bz/16 reflection (matching addMannequinBox)
        String[] fNames = {"north", "south", "west", "east", "up", "down"};
        int[][] idx = {
                {0, 1, 3, 2}, // north (-Z in BB -> +pz FRONT)
                {4, 6, 7, 5}, // south (+Z in BB -> -pz BACK)
                {4, 0, 2, 6}, // west (-X)
                {5, 7, 3, 1}, // east (+X)
                {6, 2, 3, 7}, // up (+Y)
                {4, 5, 1, 0}, // down (-Y)
        };
        double[] shade = {1.00, 0.95, 0.84, 0.90, 1.10, 0.64};

        for (int f = 0; f < 6; f++) {
            int fallbackArgb = (c.translucent || model.translucent ? 0xC8000000 : 0xFF000000) | (c.color & 0xFFFFFF);
            boolean baseEm = glowEnabled && (c.emissive || model.emissive);
            CosmeticRig.FaceUv fuv = c.faces != null ? c.faces.get(fNames[f]) : null;
            int[] q = idx[f];

            if (studio && tex != null && fuv != null && fuv.uv != null && fuv.uv.length >= 4
                    && Math.abs(fuv.uv[2] - fuv.uv[0]) >= 14 && Math.abs(fuv.uv[3] - fuv.uv[1]) >= 14) {
                // 2x2 textured subdivision in studio so wing feathers, tiger stripes & runes show crisply
                double[] p0 = pts[q[0]], p1 = pts[q[1]], p2 = pts[q[2]], p3 = pts[q[3]];
                for (int sr = 0; sr < 2; sr++) {
                    double t0 = sr * 0.5, t1 = (sr + 1) * 0.5;
                    for (int sc = 0; sc < 2; sc++) {
                        double s0 = sc * 0.5, s1 = (sc + 1) * 0.5;
                        double u = fuv.uv[0] + (sc + 0.5) * 0.5 * (fuv.uv[2] - fuv.uv[0]);
                        double v = fuv.uv[1] + (1.5 - sr) * 0.5 * (fuv.uv[3] - fuv.uv[1]) + frameIdx * model.texHeight;
                        int argb = sampleTex(tex, u, v, model.texWidth, fullH, fallbackArgb);
                        boolean em = baseEm;
                        if (glowTex != null) {
                            int gc = sampleTex(glowTex, u, v, model.texWidth, fullH, 0);
                            if ((gc >>> 24) > 80) {
                                argb = blendGlow(argb, gc);
                                em = true;
                            }
                        }
                        emitQuadBb(faces, m,
                                bilerp(p0, p1, p2, p3, s0, t0),
                                bilerp(p0, p1, p2, p3, s1, t0),
                                bilerp(p0, p1, p2, p3, s1, t1),
                                bilerp(p0, p1, p2, p3, s0, t1),
                                argb, em ? 1.12 : shade[f], em);
                    }
                }
                continue;
            }

            int argb = fallbackArgb;
            boolean em = baseEm;
            if (tex != null) {
                double u = c.uv[0] + 2, v = c.uv[1] + 2 + frameIdx * model.texHeight;
                if (fuv != null && fuv.uv != null && fuv.uv.length >= 4) {
                    u = (fuv.uv[0] + fuv.uv[2]) * 0.5;
                    v = (fuv.uv[1] + fuv.uv[3]) * 0.5 + frameIdx * model.texHeight;
                }
                argb = sampleTex(tex, u, v, model.texWidth, fullH, argb);
                if (glowTex != null) {
                    int gc = sampleTex(glowTex, u, v, model.texWidth, fullH, 0);
                    if ((gc >>> 24) > 80) {
                        argb = blendGlow(argb, gc);
                        em = true;
                    }
                }
            }
            emitQuadBb(faces, m, pts[q[0]], pts[q[1]], pts[q[2]], pts[q[3]], argb, em ? 1.12 : shade[f], em);
        }
    }

    private static double[] bilerp(double[] p0, double[] p1, double[] p2, double[] p3, double s, double t) {
        double a = (1 - s) * (1 - t), b = s * (1 - t), c = s * t, d = (1 - s) * t;
        return new double[]{
                p0[0] * a + p1[0] * b + p2[0] * c + p3[0] * d,
                p0[1] * a + p1[1] * b + p2[1] * c + p3[1] * d,
                p0[2] * a + p1[2] * b + p2[2] * c + p3[2] * d
        };
    }

    private void emitQuadBb(
            ArrayList<Face> faces, Mat4 m,
            double[] p0, double[] p1, double[] p2, double[] p3,
            int argb, double sh, boolean emissive
    ) {
        double[][] raw = {p0, p1, p2, p3};
        double[] ax = new double[4], ay = new double[4];
        double depth = 0.0;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        for (int i = 0; i < 4; i++) {
            double[] w = m.apply(raw[i][0], raw[i][1], raw[i][2]);
            // BB pixel -> preview meters (x = bx/16, y = by/16, z = -bz/16)
            double px = w[0] / 16.0, py = w[1] / 16.0, pz = -w[2] / 16.0;
            double rx = px * cosY + pz * sinY;
            double rz = -px * sinY + pz * cosY;
            ax[i] = rx;
            ay[i] = py * .98 - rz * .20;
            depth += (rz * .98 + py * .20) * 0.25;
        }
        double cross = (ax[1] - ax[0]) * (ay[2] - ay[0]) - (ay[1] - ay[0]) * (ax[2] - ax[0]);
        if (cross <= 0) return;

        int a = (argb >>> 24) & 255;
        int r = Math.min(255, (int) (((argb >> 16) & 255) * sh));
        int gr = Math.min(255, (int) (((argb >> 8) & 255) * sh));
        int b = Math.min(255, (int) ((argb & 255) * sh));
        faces.add(new Face(ax, ay, depth, (a << 24) | (r << 16) | (gr << 8) | b, emissive));
    }

    private void addMannequinBox(ArrayList<Face> faces, double x, double y, double z, double w, double h, double d, int color) {
        double[][] points = new double[8][3];
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        for (int i = 0; i < 8; i++) {
            double px = x + ((i & 1) == 0 ? -w : w) / 2;
            double py = y + ((i & 2) == 0 ? -h : h) / 2;
            double pz = z + ((i & 4) == 0 ? -d : d) / 2;
            double rx = px * cosY + pz * sinY;
            double rz = -px * sinY + pz * cosY;
            points[i] = new double[]{rx, py * .98 - rz * .20, rz * .98 + py * .20};
        }
        int[][] indices = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}, {2, 6, 7, 3}, {0, 1, 5, 4}};
        double[] shade = {.63, 1, .74, .87, 1.15, .52};
        for (int f = 0; f < 6; f++) {
            var ids = indices[f];
            double[] ax = new double[4], ay = new double[4];
            double depth = 0;
            for (int j = 0; j < 4; j++) {
                ax[j] = points[ids[j]][0];
                ay[j] = points[ids[j]][1];
                depth += points[ids[j]][2] / 4;
            }
            double cross = (ax[1] - ax[0]) * (ay[2] - ay[0]) - (ay[1] - ay[0]) * (ax[2] - ax[0]);
            if (cross <= 0) continue;
            int r = Math.min(255, (int) (((color >> 16) & 255) * shade[f]));
            int gr = Math.min(255, (int) (((color >> 8) & 255) * shade[f]));
            int b = Math.min(255, (int) ((color & 255) * shade[f]));
            faces.add(new Face(ax, ay, depth, 0xFF000000 | (r << 16) | (gr << 8) | b, false));
        }
    }

    private static BufferedImage loadTexture(CosmeticRig.Model model, boolean emissive) {
        String key = model.id + (emissive ? ":glow" : ":base");
        if (TEX_CACHE.containsKey(key)) return TEX_CACHE.get(key);
        try (InputStream in = emissive ? model.openEmissiveStream() : model.openTextureStream()) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            if (img != null) TEX_CACHE.put(key, img);
            return img;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int sampleTex(BufferedImage img, double u, double v, int texW, int texH, int fallback) {
        if (img == null || texW <= 0 || texH <= 0) return fallback;
        int ix = Math.max(0, Math.min(img.getWidth() - 1, (int) Math.floor(u * img.getWidth() / texW)));
        int iy = Math.max(0, Math.min(img.getHeight() - 1, (int) Math.floor(v * img.getHeight() / texH)));
        int px = img.getRGB(ix, iy);
        return ((px >>> 24) < 16) ? fallback : px;
    }

    private static int blendGlow(int base, int glow) {
        int ga = (glow >>> 24) & 255;
        double t = Math.min(1.0, ga / 255.0 * 0.75);
        int r = Math.min(255, (int) (((base >> 16) & 255) * (1 - t) + ((glow >> 16) & 255) * t + 18 * t));
        int g = Math.min(255, (int) (((base >> 8) & 255) * (1 - t) + ((glow >> 8) & 255) * t + 18 * t));
        int b = Math.min(255, (int) ((base & 255) * (1 - t) + (glow & 255) * t + 18 * t));
        return (Math.max((base >>> 24) & 255, ga) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Lightweight 4x4 affine transform for hierarchical Blockbench bones. */
    private static final class Mat4 {
        final double[] m;
        Mat4(double[] m) { this.m = m; }

        static Mat4 identity() {
            return new Mat4(new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        }

        static Mat4 translate(double x, double y, double z) {
            return new Mat4(new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1});
        }

        static Mat4 scale(double x, double y, double z) {
            return new Mat4(new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1});
        }

        static Mat4 rotX(double deg) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            return new Mat4(new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1});
        }

        static Mat4 rotY(double deg) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            return new Mat4(new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1});
        }

        static Mat4 rotZ(double deg) {
            double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
            return new Mat4(new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        }

        Mat4 mul(Mat4 o) {
            double[] r = new double[16];
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 4; col++) {
                    r[row * 4 + col] = m[row * 4] * o.m[col]
                            + m[row * 4 + 1] * o.m[4 + col]
                            + m[row * 4 + 2] * o.m[8 + col]
                            + m[row * 4 + 3] * o.m[12 + col];
                }
            }
            return new Mat4(r);
        }

        double[] apply(double x, double y, double z) {
            return new double[]{
                    m[0] * x + m[1] * y + m[2] * z + m[3],
                    m[4] * x + m[5] * y + m[6] * z + m[7],
                    m[8] * x + m[9] * y + m[10] * z + m[11]
            };
        }
    }
}
