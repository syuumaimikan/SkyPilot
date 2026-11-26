package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Interactive per-slot fine transform editor allowing precise adjustment of Position (X/Y/Z),
 * Scale (40%–200%), and Tilt/Rotation (Pitch/Yaw/Roll) for each of the 6 Wardrobe slots with
 * live rotatable 3D studio preview.
 */
final class WardrobeAdjustScreen extends Screen {
    private final Screen parent;
    private String activeSlot;
    private float rotation = 155, zoom = 1.0f;
    private int leftX, panelW, studioX, studioW;

    WardrobeAdjustScreen(Screen parent, String initialSlot) {
        super(Component.literal("Cosmetic Transform Studio"));
        this.parent = parent;
        this.activeSlot = CosmeticCatalog.SLOTS.contains(initialSlot) ? initialSlot : "cape";
        angleForSlot(this.activeSlot);
    }

    private void angleForSlot(String slot) {
        if ("cape".equals(slot) || "back".equals(slot)) rotation = 160;
        else if ("hat".equals(slot) || "pet".equals(slot) || "companion".equals(slot)) rotation = 22;
        else rotation = 35;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();

        leftX = 14;
        studioW = Math.max(150, Math.min(230, width * 2 / 5));
        studioX = width - studioW - 14;
        panelW = studioX - leftX - 14;

        // 6 Slot Tabs
        String[] slots = {"cape", "back", "hat", "aura", "pet", "companion"};
        String[] en = {"Cloak", "Wings", "Head", "Aura", "Pet", "Comp"};
        String[] jaL = {"マント", "翼", "頭", "オーラ", "ペット", "相棒"};
        int tabW = Math.max(26, (panelW - (slots.length - 1) * 3) / slots.length);
        for (int i = 0; i < slots.length; i++) {
            String sl = slots[i];
            boolean modified = !a.transformOf(sl).isIdentity();
            String lbl = (activeSlot.equals(sl) ? "▸" : "") + (ja ? jaL[i] : en[i]) + (modified ? "*" : "");
            addRenderableWidget(WardrobeUi.button(lbl, b -> {
                activeSlot = sl;
                angleForSlot(sl);
                rebuildWidgets();
            }).bounds(leftX + i * (tabW + 3), 36, tabW, 18).build());
        }

        // 7 Fine-Adjustment Parameter Rows (Position X/Y/Z, Scale, Tilt X/Y/Z)
        SkyPilotConfig.SlotTransform tr = a.transformOf(activeSlot);
        int y = 78;
        int rowStep = Math.max(21, Math.min(25, (height - 138) / 7));

        addControlRow(y, l.t("Pos X (Left/Right)", "位置 X (左右)"),
                String.format(Locale.ROOT, "%+.2f px", tr.offsetX), -8.0, 8.0, 0.25, 1.0,
                d -> { var t = a.editTransform(activeSlot); t.offsetX = round2(clamp(t.offsetX + d, -8, 8)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).offsetX = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, l.t("Pos Y (Up/Down)", "位置 Y (上下)"),
                String.format(Locale.ROOT, "%+.2f px", tr.offsetY), -8.0, 8.0, 0.25, 1.0,
                d -> { var t = a.editTransform(activeSlot); t.offsetY = round2(clamp(t.offsetY + d, -8, 8)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).offsetY = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, l.t("Pos Z (Front/Back)", "位置 Z (前後)"),
                String.format(Locale.ROOT, "%+.2f px", tr.offsetZ), -8.0, 8.0, 0.25, 1.0,
                d -> { var t = a.editTransform(activeSlot); t.offsetZ = round2(clamp(t.offsetZ + d, -8, 8)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).offsetZ = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, l.t("Scale (Size)", "サイズ (大きさ)"),
                String.format(Locale.ROOT, "%d%%", Math.round(tr.scale * 100)), 0.4, 2.0, 0.05, 0.20,
                d -> { var t = a.editTransform(activeSlot); t.scale = round2(clamp(t.scale + d, 0.4, 2.0)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).scale = 1.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, l.t("Tilt X (Pitch)", "傾き X (前後)"),
                String.format(Locale.ROOT, "%+.0f°", tr.rotX), -60.0, 60.0, 5.0, 15.0,
                d -> { var t = a.editTransform(activeSlot); t.rotX = Math.round(clamp(t.rotX + d, -60, 60)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).rotX = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, l.t("Tilt Y (Yaw)", "傾き Y (左右回転)"),
                String.format(Locale.ROOT, "%+.0f°", tr.rotY), -180.0, 180.0, 5.0, 30.0,
                d -> { var t = a.editTransform(activeSlot); t.rotY = Math.round(clamp(t.rotY + d, -180, 180)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).rotY = 0.0; saveAndRefresh(); });
        y += rowStep;

        addControlRow(y, l.t("Tilt Z (Roll)", "傾き Z (横傾き)"),
                String.format(Locale.ROOT, "%+.0f°", tr.rotZ), -60.0, 60.0, 5.0, 15.0,
                d -> { var t = a.editTransform(activeSlot); t.rotZ = Math.round(clamp(t.rotZ + d, -60, 60)); saveAndRefresh(); },
                () -> { a.editTransform(activeSlot).rotZ = 0.0; saveAndRefresh(); });

        // Footer buttons
        int footer = height - 28;
        int b3 = (panelW - 8) / 3;
        addRenderableWidget(WardrobeUi.button(l.t("Reset Slot", "スロット初期化"), b -> {
            a.resetTransform(activeSlot);
            saveAndRefresh();
        }).bounds(leftX, footer, b3, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Reset All", "すべて初期化"), b -> {
            a.resetAllTransforms();
            saveAndRefresh();
        }).bounds(leftX + b3 + 4, footer, b3, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Back", "戻る"), b -> onClose())
                .bounds(leftX + (b3 + 4) * 2, footer, b3, 18).build());

        // Studio angle quick buttons
        int shalf = (studioW - 4) / 2;
        addRenderableWidget(WardrobeUi.button(l.t("Front", "正面"), b -> rotation = 18)
                .bounds(studioX, footer, shalf, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Back", "背面"), b -> rotation = 160)
                .bounds(studioX + shalf + 4, footer, shalf, 18).build());
    }

    private void addControlRow(
            int y, String label, String valText, double lo, double hi, double fineStep, double coarseStep,
            java.util.function.DoubleConsumer adjust, Runnable reset
    ) {
        int btnW = 22, coarseW = 24, resetW = 18, valW = 54;
        int rightEdge = leftX + panelW;
        int xReset = rightEdge - resetW;
        int xPlusCoarse = xReset - coarseW - 2;
        int xPlusFine = xPlusCoarse - btnW - 2;
        int xVal = xPlusFine - valW - 2;
        int xMinusFine = xVal - btnW - 2;
        int xMinusCoarse = xMinusFine - coarseW - 2;

        addRenderableWidget(WardrobeUi.button("«", b -> adjust.accept(-coarseStep)).bounds(xMinusCoarse, y, coarseW, 18).build());
        addRenderableWidget(WardrobeUi.button("-", b -> adjust.accept(-fineStep)).bounds(xMinusFine, y, btnW, 18).build());
        var valBtn = WardrobeUi.button(valText, b -> reset.run()).bounds(xVal, y, valW, 18).build();
        addRenderableWidget(valBtn);
        addRenderableWidget(WardrobeUi.button("+", b -> adjust.accept(fineStep)).bounds(xPlusFine, y, btnW, 18).build());
        addRenderableWidget(WardrobeUi.button("»", b -> adjust.accept(coarseStep)).bounds(xPlusCoarse, y, coarseW, 18).build());
        addRenderableWidget(WardrobeUi.button("↺", b -> reset.run()).bounds(xReset, y, resetW, 18).build());
    }

    private void saveAndRefresh() {
        WardrobeRuntime.get().save();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();

        g.text(font, l.t("✦ COSMETIC POSITION, SCALE & TILT", "✦ ワードローブ位置・サイズ・傾き調整"), leftX, 12, WardrobeUi.WHITE, false);
        String eqId = CosmeticCatalog.equippedId(a, activeSlot);
        var eqItem = CosmeticCatalog.byId(eqId);
        String eqLabel = eqItem != null ? eqItem.label(ja) : l.t("(Not equipped)", "（未装備）");
        g.text(font, CosmeticCatalog.slotLabel(activeSlot, ja) + ": " + eqLabel, leftX, 60, WardrobeUi.CYAN, false);

        // Parameter Row Labels
        String[] labels = {
                l.t("Pos X (Left/Right)", "位置 X (左右)"),
                l.t("Pos Y (Up/Down)", "位置 Y (上下)"),
                l.t("Pos Z (Front/Back)", "位置 Z (前後)"),
                l.t("Scale (Size)", "サイズ (大きさ)"),
                l.t("Tilt X (Pitch)", "傾き X (前後)"),
                l.t("Tilt Y (Yaw)", "傾き Y (左右回転)"),
                l.t("Tilt Z (Roll)", "傾き Z (横傾き)"),
        };
        int y = 78;
        int rowStep = Math.max(21, Math.min(25, (height - 138) / 7));
        for (String lbl : labels) {
            g.text(font, lbl, leftX, y + 5, WardrobeUi.WHITE, false);
            y += rowStep;
        }

        // Right Live 3D Studio Preview
        int stageY = 36, stageH = height - 72;
        WardrobeUi.panel(g, studioX, stageY, studioW, stageH);
        g.text(font, l.t("LIVE 3D PREVIEW (Drag to rotate)", "ライブ3Dプレビュー (ドラッグ回転)"), studioX + 8, stageY + 7, WardrobeUi.CYAN, false);

        int py0 = stageY + 22, py1 = stageY + stageH - 10;
        if (py1 > py0) {
            float scale = Math.min(studioW * .36f, (py1 - py0) * .39f) * zoom;
            double time = CosmeticAnimation.animationTimeSeconds();
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
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= studioX) {
            rotation += (float) dx * 2;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= studioX) {
            zoom = Math.max(.6f, Math.min(1.5f, zoom + (float) sy * .08f));
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

