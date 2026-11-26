package dev.skypilot.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Granular Wardrobe Settings screen:
 * <ul>
 *   <li>Per-slot First-Person visibility for all 10 cosmetic slots + 1st-Person VFX, Glow &amp; Glitter</li>
 *   <li>100% standalone multiplayer sync mode selector: Zero-Server LAN Auto-Sync (default),
 *       Local-Only (Offline), or Optional Independent Relay URL</li>
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
        if (urlDraft == null) urlDraft = a.wardrobeSyncUrl == null ? "" : a.wardrobeSyncUrl;

        int colW = Math.min(218, (width - 36) / 2);
        int leftX = width / 2 - colW - 8;
        int rightX = width / 2 + 8;
        int halfL = (colW - 4) / 2;

        // ---- Left Column: Compact 2x7 Grid of Granular 1st-Person Visibility (All 10 Slots + FX) ----
        int ly = 42;
        int step = 18;
        addToggle(leftX, ly, halfL, l.t("Aura:", "オーラ:"), a.firstPersonAura,
                () -> { a.firstPersonAura = !a.firstPersonAura; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("Wings:", "翼・背:"), a.firstPersonWings,
                () -> { a.firstPersonWings = !a.firstPersonWings; rt.save(); rebuildWidgets(); });
        ly += step;

        addToggle(leftX, ly, halfL, l.t("Cloak:", "マント:"), a.firstPersonCape,
                () -> { a.firstPersonCape = !a.firstPersonCape; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("Head:", "頭・帽:"), a.firstPersonHat,
                () -> { a.firstPersonHat = !a.firstPersonHat; rt.save(); rebuildWidgets(); });
        ly += step;

        addToggle(leftX, ly, halfL, l.t("Face:", "顔・眼:"), a.firstPersonFace,
                () -> { a.firstPersonFace = !a.firstPersonFace; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("Should:", "肩装飾:"), a.firstPersonShoulder,
                () -> { a.firstPersonShoulder = !a.firstPersonShoulder; rt.save(); rebuildWidgets(); });
        ly += step;

        addToggle(leftX, ly, halfL, l.t("Waist:", "腰・尾:"), a.firstPersonWaist,
                () -> { a.firstPersonWaist = !a.firstPersonWaist; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("Feet:", "足元:"), a.firstPersonFeet,
                () -> { a.firstPersonFeet = !a.firstPersonFeet; rt.save(); rebuildWidgets(); });
        ly += step;

        addToggle(leftX, ly, halfL, l.t("Pet:", "ペット:"), a.firstPersonPet,
                () -> { a.firstPersonPet = !a.firstPersonPet; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("Comp:", "相棒:"), a.firstPersonCompanion,
                () -> { a.firstPersonCompanion = !a.firstPersonCompanion; rt.save(); rebuildWidgets(); });
        ly += step;

        addToggle(leftX, ly, halfL, l.t("1stVFX:", "1人VFX:"), a.vfx.firstPersonVfx,
                () -> { a.vfx.firstPersonVfx = !a.vfx.firstPersonVfx; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("3D Skin:", "3D肌:"), a.skin3d,
                () -> { a.skin3d = !a.skin3d; rt.save(); rebuildWidgets(); });
        ly += step;

        addToggle(leftX, ly, halfL, l.t("Glow:", "発光:"), a.cosmeticGlow,
                () -> { a.cosmeticGlow = !a.cosmeticGlow; rt.save(); rebuildWidgets(); });
        addToggle(leftX + halfL + 4, ly, halfL, l.t("Glitter:", "光粒:"), a.cosmeticParticles,
                () -> { a.cosmeticParticles = !a.cosmeticParticles; rt.save(); rebuildWidgets(); });

        // ---- Right Column: 100% Standalone Multiplayer Sync ----
        int ry = 42;
        String mode = RemoteWardrobeSync.syncMode(a);
        String modeLabel = switch (mode) {
            case "offline" -> l.t("Mode: Local Only (Offline)", "同期方式: ローカル専用 (完全オフライン)");
            case "relay" -> l.t("Mode: Custom Relay Server", "同期方式: 外部リレーサーバー (任意)");
            default -> l.t("Mode: LAN Auto-Sync (No Server)", "同期方式: LAN自動共有 (サーバー不要)");
        };
        addRenderableWidget(WardrobeUi.button(modeLabel, b -> {
            a.wardrobeSyncMode = switch (RemoteWardrobeSync.syncMode(a)) {
                case "lan" -> "offline";
                case "offline" -> "relay";
                default -> "lan";
            };
            testNotice = "";
            rt.save();
            rebuildWidgets();
        }).bounds(rightX, ry, colW, 17).tooltip(l.t(
                "Standalone sync: LAN works automatically with zero server; Relay is optional for internet lobbies",
                "独立MOD標準のLAN自動共有はサーバー不要。外部リレーはHypixel等で遠隔の友達と共有したい場合のみ設定。"
        )).build());
        ry += step;

        addToggle(rightX, ry, colW, l.t("Show Other Players: ", "他プレイヤーのコスメ表示: "), a.showOtherPlayers,
                () -> { a.showOtherPlayers = !a.showOtherPlayers; rt.save(); rebuildWidgets(); });
        ry += step;
        addToggle(rightX, ry, colW, l.t("Share My Wardrobe: ", "自分のコスメを共有: "), a.shareWithOthers,
                () -> { a.shareWithOthers = !a.shareWithOthers; rt.save(); rebuildWidgets(); });
        ry += step;
        addToggle(rightX, ry, colW, l.t("Other Players' Pets: ", "他プレイヤーのペット表示: "), a.otherPlayerPets,
                () -> { a.otherPlayerPets = !a.otherPlayerPets; rt.save(); rebuildWidgets(); });
        ry += step;
        addToggle(rightX, ry, halfL, l.t("OtherVFX:", "他人VFX:"), a.vfx.otherPlayerVfx,
                () -> { a.vfx.otherPlayerVfx = !a.vfx.otherPlayerVfx; rt.save(); rebuildWidgets(); });
        addToggle(rightX + halfL + 4, ry, halfL, l.t("OtherFX:", "他人光粒:"), a.otherPlayerParticles,
                () -> { a.otherPlayerParticles = !a.otherPlayerParticles; rt.save(); rebuildWidgets(); });
        ry += step + 14;

        // Only show external Relay URL editor when explicitly in "relay" mode
        if ("relay".equals(mode)) {
            EditBox urlBox = new EditBox(font, rightX, ry, colW, 16, Component.literal("Optional Relay URL"));
            urlBox.setMaxLength(160);
            urlBox.setHint(Component.literal("https://your-relay.example.com"));
            urlBox.setValue(urlDraft);
            urlBox.setResponder(v -> urlDraft = v);
            addRenderableWidget(urlBox);
            ry += 19;

            addRenderableWidget(WardrobeUi.button(l.t("Save Relay", "リレー保存"), b -> {
                if (urlDraft == null || urlDraft.isBlank()) {
                    a.wardrobeSyncUrl = "";
                    rt.save();
                    testNotice = l.t("Cleared relay URL", "リレーURLをクリアしました");
                    return;
                }
                String valid = RemoteWardrobeSync.validatedBaseUrl(urlDraft);
                if (valid == null) {
                    testNotice = l.t("Invalid URL (use https:// or local http://)", "無効なURL（https:// またはローカル http://）");
                } else {
                    a.wardrobeSyncUrl = valid;
                    urlDraft = valid;
                    rt.save();
                    testNotice = l.t("Saved relay URL!", "リレーURLを保存しました！");
                }
            }).bounds(rightX, ry, halfL, 16).build());

            addRenderableWidget(WardrobeUi.button(l.t("Test Relay", "接続テスト"), b -> {
                testNotice = l.t("Testing…", "接続確認中…");
                RemoteWardrobeSync.testConnectionAsync(urlDraft, ja).thenAccept(res ->
                        minecraft.execute(() -> testNotice = res));
            }).bounds(rightX + halfL + 4, ry, halfL, 16).build());
        }

        addRenderableWidget(WardrobeUi.button(l.t("Back to Wardrobe", "ワードローブへ戻る"), b -> onClose())
                .bounds(width / 2 - 110, height - 26, 220, 18).build());
    }

    private void addToggle(int x, int y, int w, String prefix, boolean on, Runnable toggle) {
        addRenderableWidget(WardrobeUi.button(prefix + (on ? "ON" : "OFF"), b -> toggle.run())
                .bounds(x, y, w, 16).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        String mode = RemoteWardrobeSync.syncMode(rt.appearance());

        int colW = Math.min(218, (width - 36) / 2);
        int leftX = width / 2 - colW - 8;
        int rightX = width / 2 + 8;

        g.centeredText(font, l.t("✦ WARDROBE FIRST-PERSON & STANDALONE SYNC SETTINGS", "✦ ワードローブ 一人称・マルチプレイ詳細設定"),
                width / 2, 11, WardrobeUi.WHITE);
        g.text(font, l.t("FIRST-PERSON VISIBILITY (10 SLOTS) & FX", "一人称視点の表示 (全10スロット) ＆ 演出"), leftX, 30, WardrobeUi.CYAN, false);
        g.text(font, l.t("MULTIPLAYER SYNC (100% STANDALONE)", "マルチプレイ共有 (完全独立・サーバー不要)"), rightX, 30, WardrobeUi.PURPLE, false);

        if ("relay".equals(mode)) {
            g.text(font, l.t("Optional External Relay URL (for public servers)", "任意: 外部リレーサーバーURL (公開サーバー用)"), rightX, 134, WardrobeUi.GRAY, false);
            String st = RemoteWardrobeSync.statusSummary(ja);
            g.text(font, st, rightX, 185, RemoteWardrobeSync.isConnected() ? WardrobeUi.GREEN : WardrobeUi.GRAY, false);
            if (!testNotice.isEmpty()) {
                g.text(font, font.plainSubstrByWidth(testNotice, colW), rightX, 197, WardrobeUi.CYAN, false);
            }
        } else {
            int infoY = 136;
            WardrobeUi.panel(g, rightX, infoY, colW, 52);
            String st = RemoteWardrobeSync.statusSummary(ja);
            g.text(font, st, rightX + 7, infoY + 7,
                    "lan".equals(mode) ? WardrobeUi.CYAN : WardrobeUi.GRAY, false);
            String note1 = "lan".equals(mode)
                    ? l.t("Zero-server UDP LAN sync active automatically.", "内蔵UDP通信で同一LAN内の導入者と自動共有。")
                    : l.t("All networking disabled — strictly local visuals.", "ネットワーク通信を一切行わない完全ローカル動作。");
            String note2 = l.t("100% standalone — no SkyPilot server needed.", "独立MODのためSkyPilotサーバー接続は不要です。");
            g.text(font, font.plainSubstrByWidth(note1, colW - 14), rightX + 7, infoY + 21, WardrobeUi.GRAY, false);
            g.text(font, font.plainSubstrByWidth(note2, colW - 14), rightX + 7, infoY + 34, WardrobeUi.DARK_GRAY, false);
        }

        g.centeredText(font, l.t("Zero custom Minecraft packets sent — 100% safe on Hypixel & all servers.",
                        "ゲームサーバーへ独自パケットは一切送信しません（Hypixel等の全サーバーで安全）"),
                width / 2, height - 39, WardrobeUi.DARK_GRAY);

        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
