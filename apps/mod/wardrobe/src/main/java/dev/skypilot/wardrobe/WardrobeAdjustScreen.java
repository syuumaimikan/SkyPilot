package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Interactive per-slot fine Transform, Custom 24-Color Dye, and Live 60 FPS Rainbow Chroma
 * editor across all 10 Wardrobe slots — surpasses Lunar Client's paid Chroma cosmetics!
 */
final class WardrobeAdjustScreen extends Screen {
    private final Screen parent;
    private String activeSlot;
    private float rotation = 155f, targetRotation = 155f, zoom = 1.0f, targetZoom = 1.0f;
    private int dyePage;
    private long lastFrameNs = System.nanoTime();
    private int leftX, panelW, studioX, studioW, dyeBarY, dyeSwatchW;

    WardrobeAdjustScreen(Screen parent, String initialSlot) {
        super(Component.literal("Cosmetic Transform & Chroma Studio"));
        this.parent = parent;
        this.activeSlot = CosmeticCatalog.SLOTS.contains(initialSlot) ? initialSlot : "cape";
        angleForSlot(this.activeSlot);
        this.rotation = this.targetRotation;
    }

    private void angleForSlot(String slot) {
        if ("cape".equals(slot) || "back".equals(slot) || "waist".equals(slot)) targetRotation = 156f;
        else if ("hat".equals(slot) || "face".equals(slot) || "shoulder".equals(slot)
                || "pet".equals(slot) || "companion".equals(slot)) targetRotation = 22f;
        else targetRotation = 34f;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();

        leftX = 14;
        studioW = Math.max(152, Math.min(232, width * 2 / 5));
        studioX = width - studioW - 14;
        panelW = studioX - leftX - 14;

        // 10 Slot Tabs in 2 tidy rows of 5
        String[][] slotRows = {
                {"cape", "back", "hat", "face", "shoulder"},
                {"waist", "feet", "aura", "pet", "companion"}
        };
        String[][] enRows = {
                {"Cloak", "Wings", "Head", "Face", "Shoulder"},
                {"Waist", "Feet", "Aura", "Pet", "Comp"}
        };
        String[][] jaRows = {
                {"マント", "翼", "頭", "顔", "肩"},
                {"腰・尻尾", "足元", "オーラ", "ペット", "相棒"}
        };
        int tabW = Math.max(26, (panelW - 4 * 3) / 5);
        for (int r = 0; r < 2; r++) {
            int ty = 30 + r * 16;
            for (int c = 0; c < 5; c++) {
                String sl = slotRows[r][c];
                var tr = a.transformOf(sl);
                boolean modified = !tr.isIdentity();
                String mark = tr.chroma ? "🌈" : (modified ? "*" : "");
                String lbl = (ja ? jaRows[r][c] : enRows[r][c]) + mark;
                addRenderableWidget(WardrobeUi.button(lbl, b -> {
                    activeSlot = sl;
                    angleForSlot(sl);
                    rebuildWidgets();
                }).bounds(leftX + c * (tabW + 3), ty, tabW, 14)
                        .style(activeSlot.equals(sl) ? WardrobeUi.Style.ACTIVE_TAB : (modified ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT))
                        .build());
            }
        }

        // 7 Fine-Adjustment Parameter Rows (Position X/Y/Z, Scale, Tilt X/Y/Z)
        SkyPilotConfig.SlotTransform tr = a.transformOf(activeSlot);
        int y = 76;
        int rowStep = Math.max(17, Math.min(21, (height - 154) / 7));

        addControlRow(y, String.format(Locale.ROOT, "%+.2f px", tr.offsetX), 0.25, 1.0,
                d -> { var t = a.editTransform(activeSlot); t.offsetX = round2(clamp(t.offsetX + d, -8, 8)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).offsetX = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, String.format(Locale.ROOT, "%+.2f px", tr.offsetY), 0.25, 1.0,
                d -> { var t = a.editTransform(activeSlot); t.offsetY = round2(clamp(t.offsetY + d, -8, 8)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).offsetY = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, String.format(Locale.ROOT, "%+.2f px", tr.offsetZ), 0.25, 1.0,
                d -> { var t = a.editTransform(activeSlot); t.offsetZ = round2(clamp(t.offsetZ + d, -8, 8)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).offsetZ = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, String.format(Locale.ROOT, "%d%%", Math.round(tr.scale * 100)), 0.05, 0.20,
                d -> { var t = a.editTransform(activeSlot); t.scale = round2(clamp(t.scale + d, 0.4, 2.0)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).scale = 1.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, String.format(Locale.ROOT, "%+.0f°", tr.rotX), 5.0, 15.0,
                d -> { var t = a.editTransform(activeSlot); t.rotX = Math.round(clamp(t.rotX + d, -60, 60)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).rotX = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, String.format(Locale.ROOT, "%+.0f°", tr.rotY), 5.0, 30.0,
                d -> { var t = a.editTransform(activeSlot); t.rotY = Math.round(clamp(t.rotY + d, -180, 180)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).rotY = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, String.format(Locale.ROOT, "%+.0f°", tr.rotZ), 5.0, 15.0,
                d -> { var t = a.editTransform(activeSlot); t.rotZ = Math.round(clamp(t.rotZ + d, -60, 60)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).rotZ = 0.0; saveAndRefresh(); });
        y += rowStep + 4;

        // ---- Custom Dye & Live Rainbow Chroma Strip ----
        int third = (panelW - 6) / 3;
        addRenderableWidget(WardrobeUi.button(
                l.t("🌈 Chroma:", "🌈 クロマ:") + (tr.chroma ? "ON" : "OFF"), b -> {
                    var t = a.editTransform(activeSlot);
                    t.chroma = !t.chroma;
                    if (t.chroma) WardrobeUi.playSparkle();
                    saveAndRefresh();
                }).bounds(leftX, y, third, 16)
                .style(tr.chroma ? WardrobeUi.Style.ACTIVE_TAB : WardrobeUi.Style.PRIMARY)
                .tooltip(l.t("Enable smooth 60fps Rainbow Chroma tint on this slot!", "このスロットを七色に輝くレインボークロマ発光にする！")).build());

        addRenderableWidget(WardrobeUi.button(
                l.t("🎨 Dye:", "🎨 染色:") + (tr.tintEnabled ? "ON" : "OFF"), b -> {
                    var t = a.editTransform(activeSlot);
                    t.tintEnabled = !t.tintEnabled;
                    if (t.tintEnabled) t.chroma = false;
                    saveAndRefresh();
                }).bounds(leftX + third + 3, y, third, 16)
                .style(tr.tintEnabled && !tr.chroma ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT)
                .tooltip(l.t("Tint this cosmetic with any of 24 custom dye colors", "24色のカスタムカラーで自由に染色")).build());

        addRenderableWidget(WardrobeUi.button(
                l.t("Palette " + (dyePage + 1) + "/2", "パレット " + (dyePage + 1) + "/2"), b -> {
                    dyePage = (dyePage + 1) % 2;
                    rebuildWidgets();
                }).bounds(leftX + (third + 3) * 2, y, third, 16).build());

        dyeBarY = y + 20;
        dyeSwatchW = Math.max(12, (panelW - 11 * 2) / 12);

        // Footer buttons
        int footer = height - 26;
        int b3 = (panelW - 8) / 3;
        addRenderableWidget(WardrobeUi.button(l.t("Reset Slot", "スロット初期化"), b -> {
            a.resetTransform(activeSlot);
            WardrobeUi.playEquip(false);
            saveAndRefresh();
        }).bounds(leftX, footer, b3, 18).style(WardrobeUi.Style.DANGER).build());
        addRenderableWidget(WardrobeUi.button(l.t("Reset All", "すべて初期化"), b -> {
            a.resetAllTransforms();
            WardrobeUi.playEquip(false);
            saveAndRefresh();
        }).bounds(leftX + b3 + 4, footer, b3, 18).style(WardrobeUi.Style.DANGER).build());
        addRenderableWidget(WardrobeUi.button(l.t("Back", "戻る"), b -> onClose())
                .bounds(leftX + (b3 + 4) * 2, footer, b3, 18).build());

        // Studio quick buttons: Front | Back | Motion Simulator
        int s3 = (studioW - 6) / 3;
        addRenderableWidget(WardrobeUi.button(l.t("Front", "正面"), b -> targetRotation = 18f)
                .bounds(studioX, footer, s3, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Back", "背面"), b -> targetRotation = 160f)
                .bounds(studioX + s3 + 3, footer, s3, 18).build());
        String motionLbl = switch (CosmeticLayer.studioMotionMode) {
            case 1 -> l.t("Walk", "歩行");
            case 2 -> l.t("Sprint", "疾走");
            case 3 -> l.t("Glide", "飛行");
            default -> l.t("Idle", "待機");
        };
        addRenderableWidget(WardrobeUi.button(motionLbl, b -> {
            CosmeticLayer.studioMotionMode = (CosmeticLayer.studioMotionMode + 1) % 4;
            rebuildWidgets();
        }).bounds(studioX + (s3 + 3) * 2, footer, s3, 18)
                .style(CosmeticLayer.studioMotionMode > 0 ? WardrobeUi.Style.ACTIVE_TAB : WardrobeUi.Style.DEFAULT).build());
    }

    private void addControlRow(
            int y, String valText, double fineStep, double coarseStep,
            java.util.function.DoubleConsumer adjust, Runnable reset
    ) {
        int btnW = 20, coarseW = 22, resetW = 16, valW = 52;
        int rightEdge = leftX + panelW;
        int xReset = rightEdge - resetW;
        int xPlusCoarse = xReset - coarseW - 2;
        int xPlusFine = xPlusCoarse - btnW - 2;
        int xVal = xPlusFine - valW - 2;
        int xMinusFine = xVal - btnW - 2;
        int xMinusCoarse = xMinusFine - coarseW - 2;

        addRenderableWidget(WardrobeUi.button("«", b -> adjust.accept(-coarseStep)).bounds(xMinusCoarse, y, coarseW, 15).build());
        addRenderableWidget(WardrobeUi.button("-", b -> adjust.accept(-fineStep)).bounds(xMinusFine, y, btnW, 15).build());
        addRenderableWidget(WardrobeUi.button(valText, b -> reset.run()).bounds(xVal, y, valW, 15).build());
        addRenderableWidget(WardrobeUi.button("+", b -> adjust.accept(fineStep)).bounds(xPlusFine, y, btnW, 15).build());
        addRenderableWidget(WardrobeUi.button("»", b -> adjust.accept(coarseStep)).bounds(xPlusCoarse, y, coarseW, 15).build());
        addRenderableWidget(WardrobeUi.button("↺", b -> reset.run()).bounds(xReset, y, resetW, 15).build());
    }

    private void saveAndRefresh() {
        WardrobeRuntime.get().save();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.08f, Math.max(0.001f, (nowNs - lastFrameNs) * 1e-9f));
        lastFrameNs = nowNs;
        rotation += Mth.wrapDegrees(targetRotation - rotation) * Math.min(1.0f, dt * 11.0f);
        zoom += (targetZoom - zoom) * Math.min(1.0f, dt * 12.0f);

        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();
        double time = CosmeticAnimation.animationTimeSeconds();

        g.text(font, l.t("✦ TRANSFORM, CUSTOM DYE & CHROMA STUDIO", "✦ 位置・サイズ・傾き ＆ カスタム染色・クロマ設定"), leftX, 10, WardrobeUi.WHITE, false);
        String eqId = CosmeticCatalog.equippedId(a, activeSlot);
        var eqItem = CosmeticCatalog.byId(eqId);
        String eqLabel = eqItem != null ? eqItem.label(ja) : l.t("(Not equipped)", "（未装備）");
        var activeTr = a.transformOf(activeSlot);
        int headerCol = activeTr.chroma ? (0xFF000000 | CosmeticAnimation.chromaRgb(time, 0))
                : (activeTr.tintEnabled ? (0xFF000000 | (activeTr.tintColor & 0xFFFFFF)) : WardrobeUi.CYAN);
        g.text(font, CosmeticCatalog.slotLabel(activeSlot, ja) + ": " + eqLabel, leftX, 63, headerCol, false);

        // Parameter Row Labels
        String[] labels = {
                l.t("Pos X (Left/Right)", "位置 X (左右)"),
                l.t("Pos Y (Up/Down)", "位置 Y (上下)"),
                l.t("Pos Z (Front/Back)", "位置 Z (前後)"),
                l.t("Scale (Size)", "サイズ (大きさ)"),
                l.t("Tilt X (Pitch)", "傾き X (前後)"),
                l.t("Tilt Y (Yaw)", "傾き Y (左右)"),
                l.t("Tilt Z (Roll)", "傾き Z (横傾き)"),
        };
        int y = 76;
        int rowStep = Math.max(17, Math.min(21, (height - 154) / 7));
        for (String lbl : labels) {
            g.text(font, lbl, leftX, y + 3, WardrobeUi.WHITE, false);
            y += rowStep;
        }

        // 12 Clickable Glowing Dye Swatches (Page 1 or Page 2 of 24 designer colors!)
        int baseIdx = dyePage * 12;
        for (int i = 0; i < 12 && baseIdx + i < WardrobeTools.DYE_PALETTE.length; i++) {
            int col = WardrobeTools.DYE_PALETTE[baseIdx + i] & 0xFFFFFF;
            int sx = leftX + i * (dyeSwatchW + 2);
            boolean sel = activeTr.tintEnabled && !activeTr.chroma && activeTr.tintColor == col;
            boolean hov = mx >= sx && mx < sx + dyeSwatchW && my >= dyeBarY && my < dyeBarY + 14;
            g.fill(sx, dyeBarY, sx + dyeSwatchW, dyeBarY + 14, 0xFF000000 | col);
            g.fill(sx + 1, dyeBarY + 1, sx + dyeSwatchW - 1, dyeBarY + 3, 0x44FFFFFF);
            g.outline(sx, dyeBarY, dyeSwatchW, 14, sel ? WardrobeUi.WHITE : (hov ? WardrobeUi.CYAN : 0xAA0B101D));
        }

        // Right Live 3D Studio Preview with curated Studio Backdrop
        int stageY = 30, stageH = height - 64;
        WardrobeUi.panel(g, studioX, stageY, studioW, stageH);
        WardrobeUi.studioBackdrop(g, studioX, stageY, studioW, stageH, a.studioBackdrop, a.color, time);
        g.text(font, l.t("LIVE 3D PREVIEW (Drag/Scroll)", "ライブ3Dプレビュー (ドラッグ回転)"), studioX + 8, stageY + 7, WardrobeUi.CYAN, false);

        int py0 = stageY + 20, py1 = stageY + stageH - 8;
        if (py1 > py0) {
            float scale = Math.min(studioW * .36f, (py1 - py0) * .40f) * zoom;
            if (minecraft.player != null) {
                var state = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, delta);
                if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar) {
                    avatar.bodyRot = 180 + rotation;
                    avatar.yRot = 0;
                    avatar.xRot = 0;
                }
                var orientation = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-8));
                g.entity(state, scale, new org.joml.Vector3f(0, state.boundingBoxHeight / 2, 0), orientation, new org.joml.Quaternionf(), studioX, py0, studioX + studioW, py1);
            } else {
                WardrobeModel.renderStudio(g, a, scale, -8, rotation, studioX, py0, studioX + studioW, py1, time);
            }
        }

        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        if (my >= dyeBarY && my < dyeBarY + 14 && mx >= leftX && mx < leftX + panelW) {
            int idx = (int) ((mx - leftX) / (dyeSwatchW + 2));
            int palIdx = dyePage * 12 + idx;
            if (idx >= 0 && idx < 12 && palIdx < WardrobeTools.DYE_PALETTE.length) {
                var t = WardrobeRuntime.get().appearance().editTransform(activeSlot);
                t.tintColor = WardrobeTools.DYE_PALETTE[palIdx];
                t.tintEnabled = (t.tintColor != 0xFFFFFF);
                t.chroma = false;
                WardrobeUi.playSparkle();
                saveAndRefresh();
                return true;
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= studioX) {
            targetRotation += (float) dx * 2.1f;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= studioX) {
            targetZoom = Mth.clamp(targetZoom + (float) sy * 0.09f, 0.6f, 1.55f);
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
