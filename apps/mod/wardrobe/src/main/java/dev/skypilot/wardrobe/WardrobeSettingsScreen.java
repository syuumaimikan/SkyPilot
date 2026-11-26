package dev.skypilot.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Granular Wardrobe Settings screen:
 * <ul>
 *   <li>Per-slot First-Person visibility (Aura, Wings, Cloak, Headwear, Pet, Companion)</li>
 *   <li>Multiplayer visibility &amp; Hypixel-safe HTTP sync toggles + Sync Server URL &amp; test</li>
 *   <li>Visual FX toggles (Glow, Particles, 3D Skin)</li>
 * </ul>
 */
final class WardrobeSettingsScreen extends Screen {
    private final Screen parent;
    private String urlDraft;
    private String testNotice = "";

    WardrobeSettingsScreen(Screen parent) {
        super(Component.literal("Wardrobe Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();
        if (urlDraft == null) urlDraft = a.wardrobeSyncUrl;

        int colW = Math.min(210, (width - 40) / 2);
        int leftX = width / 2 - colW - 8;
        int rightX = width / 2 + 8;

        // ---- Left Column: Granular 1st-Person Visibility per Slot ----
        int ly = 48;
        int step = 21;
        addToggle(leftX, ly, colW, l.t("1st-Person Aura: ", "一人称 オーラ表示: "), a.firstPersonAura,
                () -> { a.firstPersonAura = !a.firstPersonAura; rt.save(); rebuildWidgets(); });
        ly += step;
        addToggle(leftX, ly, colW, l.t("1st-Person Wings: ", "一人称 翼・背面表示: "), a.firstPersonWings,
                () -> { a.firstPersonWings = !a.firstPersonWings; rt.save(); rebuildWidgets(); });
        ly += step;
        addToggle(leftX, ly, colW, l.t("1st-Person Cloak: ", "一人称 マント表示: "), a.firstPersonCape,
                () -> { a.firstPersonCape = !a.firstPersonCape; rt.save(); rebuildWidgets(); });
        ly += step;
        addToggle(leftX, ly, colW, l.t("1st-Person Headwear: ", "一人称 頭・帽子表示: "), a.firstPersonHat,
                () -> { a.firstPersonHat = !a.firstPersonHat; rt.save(); rebuildWidgets(); });
        ly += step;
        addToggle(leftX, ly, colW, l.t("1st-Person Floating Pet: ", "一人称 浮遊ペット表示: "), a.firstPersonPet,
                () -> { a.firstPersonPet = !a.firstPersonPet; rt.save(); rebuildWidgets(); });
        ly += step;
        addToggle(leftX, ly, colW, l.t("1st-Person Companion: ", "一人称 地上相棒表示: "), a.firstPersonCompanion,
                () -> { a.firstPersonCompanion = !a.firstPersonCompanion; rt.save(); rebuildWidgets(); });
        ly += step + 4;
        addToggle(leftX, ly, colW, l.t("Emissive Glow: ", "エミッシブ発光: "), a.cosmeticGlow,
                () -> { a.cosmeticGlow = !a.cosmeticGlow; rt.save(); rebuildWidgets(); });
        ly += step;
        addToggle(leftX, ly, colW, l.t("Ambient Particles: ", "演出パーティクル: "), a.cosmeticParticles,
                () -> { a.cosmeticParticles = !a.cosmeticParticles; rt.save(); rebuildWidgets(); });

        // ---- Right Column: Multiplayer Cosmetic Visibility & Sync ----
        int ry = 48;
        addToggle(rightX, ry, colW, l.t("Show Other Players: ", "他プレイヤーのコスメ表示: "), a.showOtherPlayers,
                () -> { a.showOtherPlayers = !a.showOtherPlayers; rt.save(); rebuildWidgets(); });
        ry += step;
        addToggle(rightX, ry, colW, l.t("Share My Wardrobe: ", "自分のコスメを共有: "), a.shareWithOthers,
                () -> { a.shareWithOthers = !a.shareWithOthers; rt.save(); rebuildWidgets(); });
        ry += step;
        addToggle(rightX, ry, colW, l.t("Other Players' Pets: ", "他プレイヤーのペット表示: "), a.otherPlayerPets,
                () -> { a.otherPlayerPets = !a.otherPlayerPets; rt.save(); rebuildWidgets(); });
        ry += step;
        addToggle(rightX, ry, colW, l.t("Other Players' FX: ", "他プレイヤーの粒子表示: "), a.otherPlayerParticles,
                () -> { a.otherPlayerParticles = !a.otherPlayerParticles; rt.save(); rebuildWidgets(); });
        ry += step + 18;

        EditBox urlBox = new EditBox(font, rightX, ry, colW, 18, Component.literal("Sync Server URL"));
        urlBox.setMaxLength(160);
        urlBox.setValue(urlDraft);
        urlBox.setResponder(v -> urlDraft = v);
        addRenderableWidget(urlBox);
        ry += 22;

        int half = (colW - 4) / 2;
        addRenderableWidget(WardrobeUi.button(l.t("Save URL", "URL保存"), b -> {
            String valid = RemoteWardrobeSync.validatedBaseUrl(urlDraft);
            if (valid == null) {
                testNotice = l.t("Invalid URL (use https:// or LAN/localhost http://)", "無効なURL（https:// またはローカル http://）");
            } else {
                a.wardrobeSyncUrl = valid;
                urlDraft = valid;
                rt.save();
                testNotice = l.t("Saved sync server URL!", "同期サーバーURLを保存しました！");
            }
        }).bounds(rightX, ry, half, 18).build());

        addRenderableWidget(WardrobeUi.button(l.t("Test Sync", "接続テスト"), b -> {
            testNotice = l.t("Testing…", "接続確認中…");
            RemoteWardrobeSync.testConnectionAsync(urlDraft, ja).thenAccept(res ->
                    minecraft.execute(() -> testNotice = res));
        }).bounds(rightX + half + 4, ry, half, 18).build());

        addRenderableWidget(WardrobeUi.button(l.t("Back to Wardrobe", "ワードローブへ戻る"), b -> onClose())
                .bounds(width / 2 - 110, height - 28, 220, 18).build());
    }

    private void addToggle(int x, int y, int w, String prefix, boolean on, Runnable toggle) {
        addRenderableWidget(WardrobeUi.button(prefix + (on ? "ON" : "OFF"), b -> toggle.run())
                .bounds(x, y, w, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();

        int colW = Math.min(210, (width - 40) / 2);
        int leftX = width / 2 - colW - 8;
        int rightX = width / 2 + 8;

        g.centeredText(font, l.t("✦ WARDROBE FIRST-PERSON & MULTIPLAYER SETTINGS", "✦ ワードローブ 一人称・マルチプレイ詳細設定"),
                width / 2, 12, WardrobeUi.WHITE);
        g.text(font, l.t("FIRST-PERSON VISIBILITY & FX", "一人称視点の表示 ＆ エフェクト"), leftX, 34, WardrobeUi.CYAN, false);
        g.text(font, l.t("MULTIPLAYER VISIBILITY (HYPIXEL-SAFE)", "マルチプレイ共有 (Hypixel安全・外部HTTP同期)"), rightX, 34, WardrobeUi.PURPLE, false);

        g.text(font, l.t("Sync Server URL (SkyPilot API)", "同期サーバーURL (SkyPilot API)"), rightX, 138, WardrobeUi.GRAY, false);
        String st = RemoteWardrobeSync.statusSummary(ja);
        g.text(font, st, rightX, 198, RemoteWardrobeSync.isConnected() ? WardrobeUi.GREEN : WardrobeUi.GRAY, false);
        if (!testNotice.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(testNotice, colW), rightX, 212, WardrobeUi.CYAN, false);
        }
        g.text(font, font.plainSubstrByWidth(
                l.t("No game packets sent — safe on Hypixel & all servers.", "ゲームサーバーへ独自パケットは一切送信しません。"),
                colW), rightX, 228, WardrobeUi.DARK_GRAY, false);

        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

