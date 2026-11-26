package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.WardrobeTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Curated 1-click theme outfits + up to 8 personal saved 6-slot outfits (including slot transforms). */
final class OutfitScreen extends Screen {
    private final Screen parent;
    private String name = "", notice = "";

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

        int colW = Math.min(185, (width - 36) / 2);
        int leftX = width / 2 - colW - 8;
        int rightX = width / 2 + 8;

        int py = 54;
        for (var preset : WardrobeTools.PRESETS) {
            addRenderableWidget(WardrobeUi.button("✦ " + preset.label(ja), b -> {
                preset.apply(a);
                rt.save();
                onClose();
            }).bounds(leftX, py, colW, 18).build());
            py += 21;
        }

        int inpW = Math.max(80, colW - 68);
        EditBox input = new EditBox(font, rightX, 54, inpW, 18, Component.literal(l.t("Outfit name", "コーデ名")));
        input.setMaxLength(24);
        input.setValue(name);
        input.setHint(Component.literal(l.t("Name this outfit…", "コーデ名を入力…")));
        input.setResponder(value -> name = value);
        addRenderableWidget(input);
        addRenderableWidget(WardrobeUi.button(l.t("Save", "保存"), b -> {
            try {
                WardrobeTools.remember(a, name);
                rt.save();
                notice = l.t("Saved outfit (with position/scale)!", "コーデと調整値を保存しました！");
                rebuildWidgets();
            } catch (IllegalArgumentException e) {
                notice = l.t("Name: 1–24 chars (max 8 saved).", "名前は1〜24文字・最大8件までです。");
            }
        }).bounds(rightX + inpW + 4, 54, 64, 18).build());

        int row = 0;
        int maxRows = Math.max(4, (height - 118) / 18);
        for (var outfit : a.outfits) {
            if (row >= maxRows) break;
            int y = 78 + row++ * 18;
            addRenderableWidget(WardrobeUi.button(outfit.name, b -> {
                WardrobeTools.apply(outfit, a);
                rt.save();
                onClose();
            }).bounds(rightX, y, colW - 24, 16).build());
            addRenderableWidget(WardrobeUi.button("×", b -> {
                a.outfits.remove(outfit);
                rt.save();
                rebuildWidgets();
            }).bounds(rightX + colW - 20, y, 20, 16).build());
        }

        addRenderableWidget(WardrobeUi.button(l.t("Back to Wardrobe", "ワードローブへ戻る"), b -> onClose())
                .bounds(width / 2 - 110, height - 28, 220, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WardrobeUi.backdrop(g, width, height);
        var l = WardrobeRuntime.get().lang();
        int colW = Math.min(185, (width - 36) / 2);
        int leftX = width / 2 - colW - 8;
        int rightX = width / 2 + 8;

        g.centeredText(font, l.t("✦ THEMES & SAVED OUTFITS", "✦ おすすめテーマ ＆ 保存コーデ"), width / 2, 14, WardrobeUi.WHITE);
        g.text(font, l.t("CURATED 10-SLOT THEMES", "厳選10スロットテーマ"), leftX, 40, WardrobeUi.PURPLE, false);
        g.text(font, l.t("MY SAVED OUTFITS (8)", "マイ保存コーデ (最大8件)"), rightX, 40, WardrobeUi.CYAN, false);
        if (!notice.isEmpty()) g.centeredText(font, notice, width / 2, height - 44, WardrobeUi.CYAN);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

