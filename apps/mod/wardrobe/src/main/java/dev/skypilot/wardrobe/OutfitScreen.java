package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Boutique Outfits &amp; Themes Studio featuring 10 flagship curated 10-slot themes, up to 16
 * personal saved outfits (with Chroma/Dye/Transforms/VFX), Live 3D Hover Try-On on your actual
 * skin, and 1-click Clipboard Outfit Share Code Export/Import ({@code SPW1:...}).
 */
final class OutfitScreen extends Screen {
    private final Screen parent;
    private String name = "", notice = "";
    private float rotation = 155f;
    private long lastFrameNs = System.nanoTime();
    private int leftX, midX, rightX, colW, studioW, listTop, rowStep;

    OutfitScreen(Screen parent) {
        super(Component.literal("Outfits & Themes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();

        studioW = Math.max(132, Math.min(196, width / 3 - 10));
        colW = Math.max(106, (width - studioW - 44) / 2);
        leftX = 12;
        midX = leftX + colW + 10;
        rightX = midX + colW + 10;
        listTop = 44;
        rowStep = Math.max(15, Math.min(18, (height - 104) / Math.max(10, WardrobeTools.PRESETS.size())));

        // Left Column: 10 Curated 10-Slot Flagship Themes
        int py = listTop;
        for (var preset : WardrobeTools.PRESETS) {
            addRenderableWidget(WardrobeUi.button("✦ " + preset.label(ja), b -> {
                preset.apply(a);
                rt.save();
                WardrobeUi.playSparkle();
                CosmeticLayer.studioOutfitPreview = null;
                onClose();
            }).bounds(leftX, py, colW, rowStep - 2).accent(preset.color()).build());
            py += rowStep;
        }

        // Middle Column: Save Input + Share Code Export/Import + Up to 16 Saved Outfits
        int inpW = Math.max(54, colW - 48);
        EditBox input = new EditBox(font, midX, listTop, inpW, 16, Component.literal(l.t("Outfit name", "コーデ名")));
        input.setMaxLength(24);
        input.setValue(name);
        input.setHint(Component.literal(l.t("Name…", "コーデ名…")));
        input.setResponder(value -> name = value);
        addRenderableWidget(input);

        addRenderableWidget(WardrobeUi.button(l.t("Save", "保存"), b -> {
            try {
                WardrobeTools.remember(a, name);
                rt.save();
                WardrobeUi.playSparkle();
                notice = l.t("Saved outfit (10 slots + Chroma + VFX)!", "コーデ・染色・調整値を保存しました！");
                rebuildWidgets();
            } catch (IllegalArgumentException e) {
                notice = l.t("Name: 1–24 chars (max 16 saved).", "名前は1〜24文字・最大16件までです。");
            }
        }).bounds(midX + inpW + 3, listTop, 45, 16).style(WardrobeUi.Style.PRIMARY).build());

        // Clipboard Share Code row (Copy / Import)
        int shareY = listTop + 20;
        int halfM = (colW - 3) / 2;
        addRenderableWidget(WardrobeUi.button(l.t("📋 Copy Code", "📋 コード共有"), b -> {
            String code = WardrobeTools.exportShareCode(a);
            minecraft.keyboardHandler.setClipboard(code);
            WardrobeUi.playSparkle();
            notice = l.t("Copied outfit share code to clipboard!", "コーデ共有コードをクリップボードにコピーしました！");
        }).bounds(midX, shareY, halfM, 15).tooltip(l.t("Copy shareable SPW1:... code for friends", "友達に送れるコーデ共有コードをコピー")).build());

        addRenderableWidget(WardrobeUi.button(l.t("📥 Import", "📥 貼付読込"), b -> {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (WardrobeTools.importShareCode(clip, a)) {
                rt.save();
                WardrobeUi.playSparkle();
                notice = l.t("Imported outfit from clipboard!", "クリップボードからコーデを読み込みました！");
                rebuildWidgets();
            } else {
                notice = l.t("Clipboard has no valid SPW1:... outfit code.", "有効なコーデ共有コード(SPW1:...)が見つかりません。");
            }
        }).bounds(midX + halfM + 3, shareY, halfM, 15).tooltip(l.t("Paste an SPW1:... code from clipboard", "クリップボードのコーデ共有コードを読み込む")).build());

        // Saved Outfits list (2 columns if > 8 or compact rows up to 16)
        int savedStartY = shareY + 20;
        int savedStep = Math.max(15, Math.min(17, (height - savedStartY - 38) / Math.max(6, Math.min(8, a.outfits.size()))));
        int maxVisible = Math.min(a.outfits.size(), Math.max(4, (height - savedStartY - 36) / savedStep));
        for (int i = 0; i < maxVisible; i++) {
            var outfit = a.outfits.get(i);
            int y = savedStartY + i * savedStep;
            addRenderableWidget(WardrobeUi.button(outfit.name, b -> {
                WardrobeTools.apply(outfit, a);
                rt.save();
                WardrobeUi.playSparkle();
                CosmeticLayer.studioOutfitPreview = null;
                onClose();
            }).bounds(midX, y, colW - 20, savedStep - 2).accent(outfit.color).build());
            addRenderableWidget(WardrobeUi.button("×", b -> {
                a.outfits.remove(outfit);
                rt.save();
                WardrobeUi.playEquip(false);
                rebuildWidgets();
            }).bounds(midX + colW - 18, y, 18, savedStep - 2).style(WardrobeUi.Style.DANGER).build());
        }

        addRenderableWidget(WardrobeUi.button(l.t("Back to Wardrobe", "ワードローブへ戻る"), b -> onClose())
                .bounds(width / 2 - 110, height - 26, 220, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.08f, Math.max(0.001f, (nowNs - lastFrameNs) * 1e-9f));
        lastFrameNs = nowNs;
        rotation = (rotation + dt * 34.0f) % 360.0f;

        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();
        double time = CosmeticAnimation.animationTimeSeconds();

        // Detect hovered Preset or Saved Outfit -> Live 3D Full-Outfit Preview!
        SkyPilotConfig.Appearance previewApp = null;
        String previewTitle = l.t("Current Outfit", "現在のコーデ");
        if (mx >= leftX && mx < leftX + colW && my >= listTop) {
            int idx = (my - listTop) / rowStep;
            if (idx >= 0 && idx < WardrobeTools.PRESETS.size()) {
                var p = WardrobeTools.PRESETS.get(idx);
                previewApp = p.previewAppearance();
                previewTitle = "✦ " + p.label(ja);
            }
        }
        int savedStartY = listTop + 40;
        int savedStep = Math.max(15, Math.min(17, (height - savedStartY - 38) / Math.max(6, Math.min(8, a.outfits.size()))));
        if (previewApp == null && mx >= midX && mx < midX + colW - 20 && my >= savedStartY) {
            int idx = (my - savedStartY) / savedStep;
            if (idx >= 0 && idx < a.outfits.size()) {
                var o = a.outfits.get(idx);
                previewApp = o.previewAppearance();
                previewTitle = o.name;
            }
        }
        CosmeticLayer.studioOutfitPreview = previewApp;

        g.centeredText(font, l.t("✦ THEMES, SAVED OUTFITS & SHARE CODES", "✦ 厳選テーマ・保存コーデ ＆ 共有コード"), width / 2, 11, WardrobeUi.WHITE);
        g.text(font, l.t("10 FLAGSHIP THEMES", "厳選10スロットテーマ (10種)"), leftX, 31, WardrobeUi.PURPLE, false);
        g.text(font, l.t("SAVED OUTFITS (" + a.outfits.size() + "/16)", "マイ保存コーデ (" + a.outfits.size() + "/16)"), midX, 31, WardrobeUi.CYAN, false);

        // Right Column: Live 3D Outfit Boutique Stage
        int stageH = Math.max(80, height - 76);
        WardrobeUi.panel(g, rightX, listTop, studioW, stageH);
        int accent = previewApp != null ? previewApp.color : a.color;
        WardrobeUi.studioBackdrop(g, rightX, listTop, studioW, stageH, a.studioBackdrop, accent, time);
        g.fill(rightX + 1, listTop + 1, rightX + studioW - 1, listTop + 3, 0xFF000000 | (accent & 0xFFFFFF));
        g.centeredText(font, font.plainSubstrByWidth(previewTitle, studioW - 10), rightX + studioW / 2, listTop + 7, 0xFF000000 | (accent & 0xFFFFFF));

        int py0 = listTop + 20, py1 = listTop + stageH - 10;
        if (py1 > py0) {
            float scale = Math.min(studioW * .38f, (py1 - py0) * .40f);
            if (minecraft.player != null) {
                var state = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, delta);
                if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar) {
                    avatar.bodyRot = 180 + rotation;
                    avatar.yRot = 0;
                    avatar.xRot = 0;
                }
                var orientation = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-8));
                g.entity(state, scale, new org.joml.Vector3f(0, state.boundingBoxHeight / 2, 0), orientation, new org.joml.Quaternionf(), rightX, py0, rightX + studioW, py1);
            } else {
                WardrobeModel.renderStudio(g, previewApp != null ? previewApp : a, scale, -8, rotation, rightX, py0, rightX + studioW, py1, time);
            }
        }

        if (!notice.isEmpty()) {
            g.centeredText(font, notice, width / 2, height - 38, WardrobeUi.CYAN);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= rightX) {
            rotation = (rotation + (float) dx * 2.0f) % 360.0f;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public void removed() {
        CosmeticLayer.studioOutfitPreview = null;
        super.removed();
    }

    @Override
    public void onClose() {
        CosmeticLayer.studioOutfitPreview = null;
        minecraft.gui.setScreen(parent);
    }
}
