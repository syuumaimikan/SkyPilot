package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flagship 10-slot 3D Wardrobe Studio surpassing Lunar Client's Wardrobe:
 * <ul>
 *   <li>Live 3D Hover-to-Try-On directly on your Minecraft skin</li>
 *   <li>Smooth inertia turntable camera (drag X to spin, drag Y to tilt, scroll to zoom)</li>
 *   <li>4 selectable studio lighting backdrops &amp; 4 live motion simulators (Idle/Walk/Sprint/Fly)</li>
 *   <li>Interactive 10-slot equipped Loadout Dock (left-click jump to slot, right-click unequip)</li>
 *   <li>Full-card left-click equip, right-click ★ favorite, mouse-wheel page turning &amp; keyboard shortcuts</li>
 * </ul>
 */
final class AppearanceScreen extends Screen {
    private static final String[] DOCK_EN = {"Ck", "Wg", "Hd", "Fc", "Sh", "Ws", "Ft", "Au", "Pt", "Cp"};
    private static final String[] DOCK_JA = {"マ", "翼", "頭", "顔", "肩", "腰", "足", "オ", "ペ", "相"};

    private final Screen parent;
    private String category = "all", query = "", rarityFilter = "all", statusMsg = "";
    private WardrobeSearch.SortOrder sortOrder = WardrobeSearch.SortOrder.DEFAULT;
    private int page, presetIdx;
    private boolean searching, autoSpin;

    // Smoothly interpolated turntable studio camera
    private float rotation = 155f, targetRotation = 155f, rotVel = 0f;
    private float camPitch = -8f, targetPitch = -8f;
    private float zoom = 1.0f, targetZoom = 1.0f;
    private long lastFrameNs = System.nanoTime();
    private long equipFlashUntilMs;
    private int equipFlashColor = 0x55D6D2;

    private EditBox search;
    private int left, top, collectionWidth, studioX, studioWidth, cardW, cardH, rows, capacity;
    private int stageY, stageH, dockY;
    private List<CosmeticCatalog.Item> visible = List.of();

    AppearanceScreen(Screen parent) {
        super(Component.literal("SkyPilot Wardrobe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();

        left = 12;
        top = 86;
        studioWidth = Math.max(152, Math.min(220, width / 3));
        studioX = width - studioWidth - 12;
        collectionWidth = studioX - left - 12;
        cardW = (collectionWidth - 8) / 3;
        rows = Math.max(1, Math.min(3, (height - top - 54) / 54));
        capacity = rows * 3;
        cardH = Math.max(50, Math.min(82, (height - top - 60 - (rows - 1) * 5) / rows));
        stageY = 32;
        stageH = Math.max(66, height - 166);
        dockY = stageY + stageH - 15;

        // Smart Search Row: [Search Box] [×] [Rarity] [Sort]
        int rarityW = Math.min(58, Math.max(46, collectionWidth / 6));
        int sortW = Math.min(72, Math.max(52, collectionWidth / 5));
        int clearW = 16;
        int searchW = Math.max(70, collectionWidth - rarityW - sortW - clearW - 6);

        search = new EditBox(font, left, 32, searchW, 16, Component.literal(l.t("Search collection", "コレクション検索")));
        search.setMaxLength(80);
        search.setHint(Component.literal(l.t("Search 384 designs (e.g. kitsune, sakura, chroma…)", "384種検索 (例: 桜 翼 / 狐 / 眼鏡 / うーぱー…)")));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            searching = true;
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(search);

        int bx = left + searchW + 2;
        var clearBtn = WardrobeUi.button("×", b -> {
            query = "";
            rarityFilter = "all";
            sortOrder = WardrobeSearch.SortOrder.DEFAULT;
            page = 0;
            searching = false;
            rebuildWidgets();
        }).bounds(bx, 32, clearW, 16).style(WardrobeUi.Style.DANGER).tooltip(l.t("Clear search & filters", "検索・フィルタをクリア")).build();
        clearBtn.active = !query.isEmpty() || !"all".equals(rarityFilter) || sortOrder != WardrobeSearch.SortOrder.DEFAULT;
        addRenderableWidget(clearBtn);

        bx += clearW + 2;
        addRenderableWidget(WardrobeUi.button(WardrobeSearch.rarityLabel(rarityFilter, ja), b -> {
            rarityFilter = WardrobeSearch.nextRarity(rarityFilter);
            page = 0;
            rebuildWidgets();
        }).bounds(bx, 32, rarityW, 16)
                .style(!"all".equals(rarityFilter) ? WardrobeUi.Style.ACTIVE_TAB : WardrobeUi.Style.DEFAULT)
                .tooltip(l.t("Filter by rarity", "レアリティで絞り込み")).build());

        bx += rarityW + 2;
        addRenderableWidget(WardrobeUi.button(sortOrder.label(ja), b -> {
            sortOrder = sortOrder.next();
            page = 0;
            rebuildWidgets();
        }).bounds(bx, 32, sortW, 16)
                .style(sortOrder != WardrobeSearch.SortOrder.DEFAULT ? WardrobeUi.Style.ACTIVE_TAB : WardrobeUi.Style.DEFAULT)
                .tooltip(l.t("Change sort order", "並び替え順を変更")).build());

        // 12 Category Tabs arranged cleanly in 2 rows of 6
        String[][] rowIds = {
                {"all", "cape", "back", "hat", "face", "shoulder"},
                {"waist", "feet", "aura", "pet", "companion", "favorites"}
        };
        String[][] rowEn = {
                {"All", "Cloak", "Wings", "Head", "Face", "Shoulder"},
                {"Waist", "Feet", "Aura", "Pet", "Comp", "★ Fav"}
        };
        String[][] rowJa = {
                {"全て", "マント", "翼", "頭", "顔", "肩"},
                {"腰・尻尾", "足元", "オーラ", "ペット", "相棒", "★お気入"}
        };
        int tabW = Math.max(24, (collectionWidth - 5 * 2) / 6);
        for (int r = 0; r < 2; r++) {
            int ty = 51 + r * 16;
            for (int c = 0; c < 6; c++) {
                String id = rowIds[r][c];
                boolean active = category.equals(id);
                boolean eqSlot = CosmeticCatalog.SLOTS.contains(id) && !"none".equals(CosmeticCatalog.equippedId(a, id));
                String prefix = (eqSlot ? "●" : "");
                String label = prefix + (ja ? rowJa[r][c] : rowEn[r][c]);
                addRenderableWidget(WardrobeUi.button(label, b -> switchCategory(id))
                        .bounds(left + c * (tabW + 2), ty, tabW, 14)
                        .style(active ? WardrobeUi.Style.ACTIVE_TAB : (eqSlot ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT))
                        .build());
            }
        }

        visible = WardrobeSearch.filterAndSort(
                CosmeticCatalog.all(), a, category, rarityFilter, false, false, false, query, sortOrder);
        page = Math.min(page, Math.max(0, (visible.size() - 1) / capacity));

        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            var item = visible.get(page * capacity + n);
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 5);
            boolean selected = item.equipped(a);
            addRenderableWidget(WardrobeUi.button((selected ? "✓ " : "") + item.label(ja), b -> toggleItem(item, a, rt, ja))
                    .bounds(x, y + cardH - 15, cardW - 17, 15)
                    .style(selected ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT)
                    .accent(item.rarityColor())
                    .tooltip(item.detail(ja)).build());
            boolean fav = a.favorites.contains(item.id());
            addRenderableWidget(WardrobeUi.button(fav ? "★" : "☆", b -> {
                WardrobeTools.favorite(a, item.id());
                WardrobeUi.playSparkle();
                rt.save();
                rebuildWidgets();
            }).bounds(x + cardW - 16, y + cardH - 15, 16, 15)
                    .style(fav ? WardrobeUi.Style.PRIMARY : WardrobeUi.Style.DEFAULT)
                    .accent(0xFBBF24).build());
        }

        int footer = height - 26;
        var previous = WardrobeUi.button("‹", b -> { page--; rebuildWidgets(); }).bounds(left, footer, 22, 18).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        var next = WardrobeUi.button("›", b -> { page++; rebuildWidgets(); }).bounds(left + 26, footer, 22, 18).build();
        next.active = (page + 1) * capacity < visible.size();
        addRenderableWidget(next);

        int customX = left + 104;
        int avail = Math.max(90, collectionWidth - 104);
        int cbW = Math.min(72, (avail - 6) / 3);
        addRenderableWidget(WardrobeUi.button(l.t("Folder", "フォルダ"), b -> {
            try {
                var dir = CustomCosmetics.ensureDirectory(rt.configDir());
                Util.getPlatform().openPath(dir);
                statusMsg = l.t("Opened cosmetics folder", "フォルダを開きました");
            } catch (Exception e) { statusMsg = e.getMessage(); }
        }).bounds(customX, footer, cbW, 18).tooltip(l.t("Open custom .bbmodel/.png cosmetics folder", "自作 .bbmodel / .png フォルダを開く")).build());
        addRenderableWidget(WardrobeUi.button(l.t("Reload", "再読込"), b -> {
            var list = CustomCosmetics.reload(rt.configDir());
            CosmeticRigRenderer.clearCache();
            WardrobeUi.playSparkle();
            statusMsg = l.t("Loaded " + list.size() + " custom models", "自作モデル " + list.size() + " 件読込");
            rebuildWidgets();
        }).bounds(customX + cbW + 3, footer, cbW, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Templates", "雛形出力"), b -> {
            try {
                var tpl = CustomCosmetics.exportTemplates(rt.configDir());
                Util.getPlatform().openPath(tpl);
                statusMsg = l.t("Exported 10 .bbmodel templates!", "10種 .bbmodel テンプレート出力済");
            } catch (Exception e) { statusMsg = e.getMessage(); }
        }).bounds(customX + (cbW + 3) * 2, footer, cbW, 18).tooltip(l.t("Export 10-slot Blockbench starter templates", "10スロット対応Blockbenchテンプレート書き出し")).build());

        // Studio Top-Right Mini Controls (Environment Lighting + Motion Simulator + Accent)
        int topBtnY = stageY + 5;
        int envW = Math.min(54, Math.max(40, studioWidth / 3 - 8));
        addRenderableWidget(WardrobeUi.button(WardrobeUi.backdropName(a.studioBackdrop, ja), b -> {
            a.studioBackdrop = (a.studioBackdrop + 1) % 4;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + studioWidth - envW * 2 - 22, topBtnY, envW, 13)
                .tooltip(l.t("Switch studio lighting environment", "スタジオ照明・背景を切り替え")).build());

        String motionLbl = switch (CosmeticLayer.studioMotionMode) {
            case 1 -> l.t("Walk", "歩行");
            case 2 -> l.t("Sprint", "疾走");
            case 3 -> l.t("Glide", "飛行");
            default -> l.t("Idle", "待機");
        };
        addRenderableWidget(WardrobeUi.button(motionLbl, b -> {
            CosmeticLayer.studioMotionMode = (CosmeticLayer.studioMotionMode + 1) % 4;
            rebuildWidgets();
        }).bounds(studioX + studioWidth - envW - 20, topBtnY, envW - 4, 13)
                .style(CosmeticLayer.studioMotionMode > 0 ? WardrobeUi.Style.ACTIVE_TAB : WardrobeUi.Style.DEFAULT)
                .tooltip(l.t("Simulate walking/sprinting/flying physics live in Studio!", "歩行・疾走・飛行時のマントや翼の動きをプレビュー！")).build());

        addRenderableWidget(WardrobeUi.button("◈", b -> {
            int i = 0;
            while (i < WardrobeTools.PALETTE.length && WardrobeTools.PALETTE[i] != a.color) i++;
            a.color = WardrobeTools.PALETTE[(i + 1) % WardrobeTools.PALETTE.length];
            rt.save();
        }).bounds(studioX + studioWidth - 22, topBtnY, 17, 13).tooltip(l.t("Change studio accent color", "スタジオ配色を切り替え")).build());

        // Right Studio Action Rows
        int third = (studioWidth - 6) / 3;

        // Row 1: Camera angles
        addRenderableWidget(WardrobeUi.button(l.t("Front", "正面"), b -> { targetRotation = 18; targetPitch = -8; autoSpin = false; })
                .bounds(studioX, height - 128, third, 14).build());
        addRenderableWidget(WardrobeUi.button(l.t("Back", "背面"), b -> { targetRotation = 160; targetPitch = -8; autoSpin = false; })
                .bounds(studioX + third + 3, height - 128, third, 14).build());
        addRenderableWidget(WardrobeUi.button(autoSpin ? l.t("Spin:ON", "回転:ON") : l.t("↻ Spin", "↻ 回転"), b -> {
            autoSpin = !autoSpin;
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 128, third, 14)
                .style(autoSpin ? WardrobeUi.Style.ACTIVE_TAB : WardrobeUi.Style.DEFAULT).build());

        // Row 2: Fine-Transform & Chroma/Dye + Custom 3D VFX Studio + Settings
        String initialSlot = CosmeticCatalog.SLOTS.contains(category) ? category : "cape";
        int activeVfx = WardrobeVfxCatalog.activeCount(a.vfx);
        addRenderableWidget(WardrobeUi.button(l.t("✦ Dye/Pos", "✦ 位置・色"), b -> {
            CosmeticLayer.clearStudioPreviews();
            minecraft.gui.setScreen(new WardrobeAdjustScreen(this, initialSlot));
        }).bounds(studioX, height - 110, third, 15).style(WardrobeUi.Style.PRIMARY)
                .tooltip(l.t("Fine-tune X/Y/Z, scale, tilt, Custom Dye & Rainbow Chroma for all 10 slots!", "全10スロットの位置・サイズ・傾き・カスタム染色・レインボークロマ設定"))
                .build());
        addRenderableWidget(WardrobeUi.button(
                activeVfx > 0 ? l.t("✦ VFX(" + activeVfx + ")", "✦ VFX(" + activeVfx + ")") : l.t("✦ VFX", "✦ エフェクト"),
                b -> {
                    CosmeticLayer.clearStudioPreviews();
                    minecraft.gui.setScreen(new WardrobeVfxScreen(this));
                })
                .bounds(studioX + third + 3, height - 110, third, 15)
                .style(activeVfx > 0 ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.PRIMARY)
                .tooltip(l.t("Configure 256×256 HDR custom 3D Attack, Kill, Move, Jump, Warp, Projectile & Ambient VFX!",
                        "256×256高精細3Dエフェクト設定（攻撃・撃破・移動・ジャンプ・ワープ・飛翔体・常時）"))
                .build());
        addRenderableWidget(WardrobeUi.button(l.t("⚙ Settings", "⚙ 設定"), b -> {
            CosmeticLayer.clearStudioPreviews();
            minecraft.gui.setScreen(new WardrobeSettingsScreen(this));
        }).bounds(studioX + (third + 3) * 2, height - 110, third, 15)
                .tooltip(l.t("1st-person visibility for all 10 slots & standalone LAN sync", "全10スロット一人称設定とサーバー不要LAN共有設定"))
                .build());

        // Row 3: Theme Presets + Random + Clear
        addRenderableWidget(WardrobeUi.button(l.t("✦ Theme", "✦ テーマ"), b -> {
            var preset = WardrobeTools.PRESETS.get(presetIdx % WardrobeTools.PRESETS.size());
            presetIdx = (presetIdx + 1) % WardrobeTools.PRESETS.size();
            preset.apply(a);
            rt.save();
            WardrobeUi.playSparkle();
            flashStudio(preset.color());
            statusMsg = "✦ " + preset.label(ja);
            rebuildWidgets();
        }).bounds(studioX, height - 92, third, 15).tooltip(l.t("Cycle 10 curated 10-slot flagship theme outfits", "厳選10種の10スロットコーデをワンクリック試着")).build());
        addRenderableWidget(WardrobeUi.button(l.t("Random", "ランダム"), b -> {
            WardrobeTools.randomize(a, ThreadLocalRandom.current());
            rt.save();
            WardrobeUi.playSparkle();
            flashStudio(a.color);
            rebuildWidgets();
        }).bounds(studioX + third + 3, height - 92, third, 15).build());
        addRenderableWidget(WardrobeUi.button(l.t("Clear", "全解除"), b -> {
            WardrobeTools.clear(a);
            rt.save();
            WardrobeUi.playEquip(false);
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 92, third, 15).style(WardrobeUi.Style.DANGER).build());

        // Row 4: Wing quick scale
        int pct = (int) Math.round(a.wingScale * 100);
        addRenderableWidget(WardrobeUi.button("W-", b -> {
            a.wingScale = Math.max(0.6, Math.round((a.wingScale - 0.1) * 10.0) / 10.0);
            rt.save();
            rebuildWidgets();
        }).bounds(studioX, height - 74, 26, 15).build());
        addRenderableWidget(WardrobeUi.button(l.t("Wing " + pct + "%", "翼 " + pct + "%"), b -> {
            a.wingScale = 1.0;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + 28, height - 74, studioWidth - 56, 15).build());
        addRenderableWidget(WardrobeUi.button("W+", b -> {
            a.wingScale = Math.min(1.5, Math.round((a.wingScale + 0.1) * 10.0) / 10.0);
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + studioWidth - 26, height - 74, 26, 15).build());

        // Row 5: FX | Glow | 3D
        addRenderableWidget(WardrobeUi.button(l.t("FX:", "光粒:") + (a.cosmeticParticles ? "ON" : "OFF"), b -> {
            a.cosmeticParticles = !a.cosmeticParticles;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX, height - 56, third, 15).style(a.cosmeticParticles ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT).build());
        addRenderableWidget(WardrobeUi.button(l.t("Glow:", "発光:") + (a.cosmeticGlow ? "ON" : "OFF"), b -> {
            a.cosmeticGlow = !a.cosmeticGlow;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + third + 3, height - 56, third, 15).style(a.cosmeticGlow ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT).build());
        addRenderableWidget(WardrobeUi.button("3D:" + (a.skin3d ? "ON" : "OFF"), b -> {
            a.skin3d = !a.skin3d;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 56, third, 15).style(a.skin3d ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT).build());

        // Footer: Pet Play | Outfits | Done
        addRenderableWidget(WardrobeUi.button(l.t("♥ Play", "♥ 遊ぶ"), b -> {
            CosmeticLayer.clearStudioPreviews();
            minecraft.gui.setScreen(new PetInteractScreen(this));
        }).bounds(studioX, footer, third, 18).style(WardrobeUi.Style.PRIMARY).accent(0xF472B6)
                .tooltip(l.t("Pat, feed treats, play fetch & bond with your pets! (Shift+H)", "ペットをなでる・おやつ・ボール投げ・とくぎ (Shift+H / 素手右クリック)"))
                .build());
        addRenderableWidget(WardrobeUi.button(l.t("Outfits", "コーデ"), b -> {
            CosmeticLayer.clearStudioPreviews();
            minecraft.gui.setScreen(new OutfitScreen(this));
        }).bounds(studioX + third + 3, footer, third, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Done", "完了"), b -> onClose())
                .bounds(studioX + (third + 3) * 2, footer, third, 18).build());

        if (searching) {
            setFocused(search);
            search.setCursorPosition(query.length());
        }
    }

    private void switchCategory(String id) {
        category = id;
        page = 0;
        autoSpin = false;
        if ("cape".equals(id) || "back".equals(id) || "waist".equals(id)) {
            targetRotation = 158f; targetPitch = -8f;
        } else if ("hat".equals(id) || "face".equals(id) || "shoulder".equals(id)
                || "pet".equals(id) || "companion".equals(id)) {
            targetRotation = 22f; targetPitch = -8f;
        } else if ("feet".equals(id)) {
            targetRotation = 34f; targetPitch = -14f;
        }
        rebuildWidgets();
    }

    private void toggleItem(CosmeticCatalog.Item item, SkyPilotConfig.Appearance a, WardrobeRuntime rt, boolean ja) {
        boolean nowEquipped;
        if (item.equipped(a)) {
            item.unequip(a);
            nowEquipped = false;
            statusMsg = CosmeticCatalog.slotLabel(item.slot(), ja) + ": " + (ja ? "外しました" : "Unequipped");
        } else {
            item.equip(a);
            nowEquipped = true;
            flashStudio(item.rarityColor());
            statusMsg = "✦ " + item.label(ja);
        }
        WardrobeUi.playEquip(nowEquipped);
        rt.save();
        rebuildWidgets();
    }

    private void flashStudio(int rgb) {
        this.equipFlashColor = rgb & 0xFFFFFF;
        this.equipFlashUntilMs = System.currentTimeMillis() + 450L;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.08f, Math.max(0.001f, (nowNs - lastFrameNs) * 1e-9f));
        lastFrameNs = nowNs;

        // Silky smooth turntable interpolation + drag inertia
        if (autoSpin) {
            targetRotation = (targetRotation + dt * 44.0f) % 360.0f;
            rotation = targetRotation;
        } else {
            if (Math.abs(rotVel) > 0.2f) {
                targetRotation += rotVel * dt;
                rotVel *= (float) Math.pow(0.06, dt);
            } else {
                rotVel = 0f;
            }
            float diff = Mth.wrapDegrees(targetRotation - rotation);
            rotation += diff * Math.min(1.0f, dt * 11.0f);
        }
        camPitch += (targetPitch - camPitch) * Math.min(1.0f, dt * 11.0f);
        zoom += (targetZoom - zoom) * Math.min(1.0f, dt * 12.0f);

        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();
        double time = CosmeticAnimation.animationTimeSeconds();
        int accentRgb = a.color & 0xFFFFFF;

        g.text(font, l.t("✦ 3D WARDROBE STUDIO", "✦ 3D ワードローブスタジオ"), left, 9, WardrobeUi.WHITE, false);
        String syncBadge = RemoteWardrobeSync.statusSummary(ja);
        int syncCol = RemoteWardrobeSync.nearbySyncedCount() > 0 ? WardrobeUi.GREEN
                : (RemoteWardrobeSync.isConnected() ? WardrobeUi.CYAN : WardrobeUi.GRAY);
        g.text(font, syncBadge, width - font.width(syncBadge) - 14, 9, syncCol, false);

        int totalCount = CosmeticCatalog.all().size();
        String sub = visible.size() + " / " + totalCount + " "
                + l.t("3D .bbmodel · 10 slots · Chroma · Free", "種表示 · 10スロット · 染色/クロマ対応")
                + (statusMsg.isEmpty() ? "" : "  ·  " + statusMsg);
        g.text(font, font.plainSubstrByWidth(sub, width - 28), left, 20,
                statusMsg.isEmpty() ? WardrobeUi.GRAY : (0xFF000000 | accentRgb), false);

        // Compute hovered card first so 3D Studio renders Live Hover Try-On immediately!
        CosmeticCatalog.Item hovered = null;
        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 5);
            if (mx >= x && mx < x + cardW && my >= y && my < y + cardH) {
                hovered = visible.get(page * capacity + n);
                break;
            }
        }
        if (hovered != null && !hovered.equipped(a)) {
            CosmeticLayer.studioPreviewSlot = hovered.slot();
            CosmeticLayer.studioPreviewId = hovered.id();
        } else {
            CosmeticLayer.studioPreviewSlot = null;
            CosmeticLayer.studioPreviewId = null;
        }

        // ---- Right 3D Studio Showcase Stage ----
        WardrobeUi.panel(g, studioX, stageY, studioWidth, stageH);
        WardrobeUi.studioBackdrop(g, studioX, stageY, studioWidth, stageH - 16, a.studioBackdrop, accentRgb, time);
        g.fill(studioX + 1, stageY + 1, studioX + studioWidth - 1, stageY + 3, 0xFF000000 | accentRgb);

        long nowMs = System.currentTimeMillis();
        if (nowMs < equipFlashUntilMs) {
            int alpha = (int) (55 * ((equipFlashUntilMs - nowMs) / 450.0));
            g.fill(studioX + 1, stageY + 3, studioX + studioWidth - 1, dockY, (alpha << 24) | equipFlashColor);
        }

        g.text(font, "STUDIO " + WardrobeTools.equippedCount(a) + "/10", studioX + 7, stageY + 7, 0xFF000000 | accentRgb, false);

        // Live Hover Try-On badge right inside the studio stage!
        if (hovered != null && !hovered.equipped(a)) {
            String tryTxt = (ja ? "✦ 試着中: " : "✦ Try-On: ") + hovered.label(ja);
            String shortTry = font.plainSubstrByWidth(tryTxt, studioWidth - 14);
            int tw = font.width(shortTry) + 8;
            int tx = studioX + (studioWidth - tw) / 2;
            g.fill(tx, stageY + 20, tx + tw, stageY + 31, 0xCC0D1628);
            g.outline(tx, stageY + 20, tw, 11, 0xFF000000 | (hovered.rarityColor() & 0xFFFFFF));
            g.centeredText(font, shortTry, studioX + studioWidth / 2, stageY + 22, 0xFF000000 | (hovered.rarityColor() & 0xFFFFFF));
        }

        int y0 = stageY + 22, y1 = dockY - 2;
        if (y1 > y0) {
            float scale = Math.min(studioWidth * .36f, (y1 - y0) * .41f) * zoom;
            if (minecraft.player != null) {
                var state = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, delta);
                if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar) {
                    avatar.bodyRot = 180 + rotation;
                    avatar.yRot = 0;
                    avatar.xRot = 0;
                }
                var orientation = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(camPitch));
                g.entity(state, scale, new org.joml.Vector3f(0, state.boundingBoxHeight / 2, 0), orientation, new org.joml.Quaternionf(), studioX, y0, studioX + studioWidth, y1);
            } else {
                WardrobeModel.renderStudio(g, a, scale, camPitch, rotation, studioX, y0, studioX + studioWidth, y1, time);
            }
        }

        // Interactive 10-Slot Equipped Loadout Dock along the bottom of Studio!
        g.fill(studioX + 1, dockY - 1, studioX + studioWidth - 1, dockY, 0x55354460);
        int chipW = Math.max(12, (studioWidth - 6) / 10);
        int dockStartX = studioX + (studioWidth - chipW * 10) / 2;
        String hoveredDockSlot = null;
        for (int i = 0; i < 10; i++) {
            String sl = CosmeticCatalog.SLOTS.get(i);
            String eqId = CosmeticCatalog.equippedId(a, sl);
            boolean eq = !"none".equals(eqId);
            int cx = dockStartX + i * chipW;
            boolean chHover = mx >= cx && mx < cx + chipW && my >= dockY && my < dockY + 14;
            if (chHover) hoveredDockSlot = sl;

            var tr = a.transformOf(sl);
            int dotCol = !eq ? 0xFF283448
                    : (tr.chroma ? (0xFF000000 | CosmeticAnimation.chromaRgb(time, i * 0.1))
                    : (tr.tintEnabled ? (0xFF000000 | (tr.tintColor & 0xFFFFFF))
                    : (0xFF000000 | (CosmeticCatalog.byId(eqId) != null ? CosmeticCatalog.byId(eqId).rarityColor() : 0x55D6D2))));

            g.fill(cx + 1, dockY + 1, cx + chipW - 1, dockY + 13, chHover ? 0xEE263450 : (eq ? 0xCC132436 : 0xAA0B111E));
            if (eq) g.fill(cx + 2, dockY + 11, cx + chipW - 2, dockY + 13, dotCol);
            if (chHover) g.outline(cx + 1, dockY + 1, chipW - 2, 12, WardrobeUi.CYAN);
            String lbl = ja ? DOCK_JA[i] : DOCK_EN[i];
            g.centeredText(font, lbl, cx + chipW / 2, dockY + 3, eq ? WardrobeUi.WHITE : WardrobeUi.DARK_GRAY);
        }

        // ---- Collection Grid with Radial Rarity Glow & Hover Spin Boost ----
        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            var item = visible.get(page * capacity + n);
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 5);
            boolean eq = item.equipped(a);
            boolean isHover = item == hovered;

            int rRgb = item.rarityColor() & 0xFFFFFF;
            g.fill(x + 2, y + 3, x + cardW + 2, y + cardH + 3, 0x66000000);
            g.fillGradient(x, y, x + cardW, y + cardH - 15,
                    isHover ? 0xF21A2640 : (eq ? 0xEE112234 : 0xE80E1627),
                    isHover ? 0xF40E1628 : 0xEC080D18);

            // Soft radial rarity spotlight behind the 3D cosmetic!
            int glowA = isHover ? 95 : (eq ? 68 : 34);
            WardrobeUi.blitGlow(g, x + cardW / 2, y + (cardH - 15) / 2, cardW - 6, cardH - 10, (glowA << 24) | rRgb);

            g.fill(x + 1, y + 1, x + cardW - 1, y + 3, 0xFF000000 | (eq ? 0x55D6D2 : rRgb));
            g.outline(x, y, cardW, cardH, eq ? WardrobeUi.CYAN : (isHover ? (0xFF000000 | rRgb) : WardrobeUi.PANEL_BORDER));

            WardrobeModel.renderCard(g, item, a.cosmeticGlow, x + 2, y + 4, x + cardW - 2, y + cardH - 15, time, isHover);

            String rTag = rarityTag(item);
            g.text(font, rTag, x + 5, y + 5, 0xFF000000 | rRgb, false);
            if (eq) {
                String eqLbl = ja ? "✓装備" : "✓ON";
                g.text(font, eqLbl, x + cardW - font.width(eqLbl) - 4, y + 5, WardrobeUi.CYAN, false);
            }
        }

        if (visible.isEmpty()) {
            g.text(font, l.t("No matching designs — try clearing filters", "一致するアイテムがありません（×でフィルタ解除）"),
                    left, top + 14, WardrobeUi.GRAY, false);
        }

        // ---- Bottom Hover Inspector Bar ----
        int inspY = height - 42;
        g.fillGradient(left, inspY, left + collectionWidth, inspY + 13, 0xD00D1426, 0xD8090E1B);
        g.outline(left, inspY, collectionWidth, 13, 0x55354460);
        if (hoveredDockSlot != null) {
            String eqId = CosmeticCatalog.equippedId(a, hoveredDockSlot);
            var eqItem = CosmeticCatalog.byId(eqId);
            String txt = CosmeticCatalog.slotLabel(hoveredDockSlot, ja) + ": "
                    + (eqItem != null ? eqItem.label(ja) : (ja ? "未装備" : "Empty"))
                    + (ja ? "  (左クリックで表示 · 右クリックで外す)" : "  (L-Click: view · R-Click: unequip)");
            g.text(font, font.plainSubstrByWidth(txt, collectionWidth - 10), left + 5, inspY + 3, WardrobeUi.CYAN, false);
        } else if (hovered != null) {
            int rCol = 0xFF000000 | (hovered.rarityColor() & 0xFFFFFF);
            String prefix = "[" + rarityTag(hovered) + "] " + hovered.label(ja) + " — ";
            g.text(font, prefix, left + 5, inspY + 3, rCol, false);
            int px = left + 5 + font.width(prefix);
            int rem = collectionWidth - (px - left) - 5;
            if (rem > 24) {
                g.text(font, font.plainSubstrByWidth(hovered.detail(ja), rem), px, inspY + 3, WardrobeUi.GRAY, false);
            }
        } else {
            g.text(font, l.t("Hover card for Live 3D Try-On · Click card to equip · Right-Click ★ · Scroll grid to flip pages",
                    "カーソルでリアルタイム3D試着 · カードクリックで装備 · 右クリックで★ · ホイールでページ切替"), left + 5, inspY + 3, WardrobeUi.GRAY, false);
        }

        g.text(font, (page + 1) + "/" + Math.max(1, (visible.size() + capacity - 1) / capacity), left + 52, height - 20, WardrobeUi.GRAY, false);
        super.extractRenderState(g, mx, my, delta);
    }

    private static String rarityTag(CosmeticCatalog.Item item) {
        if (item.custom()) return "CUSTOM";
        return switch (item.rarity() == null ? "rare" : item.rarity()) {
            case "mythic" -> "MYTHIC";
            case "legendary" -> "LEGEND";
            case "epic" -> "EPIC";
            default -> "RARE";
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        int btn = e.button();
        var rt = WardrobeRuntime.get();
        var a = rt.appearance();

        // 1. Clickable 10-slot Loadout Dock (L-click: jump to slot, R-click: unequip slot!)
        if (my >= dockY && my < dockY + 14 && mx >= studioX && mx < studioX + studioWidth) {
            int chipW = Math.max(12, (studioWidth - 6) / 10);
            int dockStartX = studioX + (studioWidth - chipW * 10) / 2;
            int idx = (int) ((mx - dockStartX) / chipW);
            if (idx >= 0 && idx < 10) {
                String sl = CosmeticCatalog.SLOTS.get(idx);
                if (btn == 1) {
                    if (!"none".equals(CosmeticCatalog.equippedId(a, sl))) {
                        CosmeticCatalog.clearSlot(a, sl);
                        WardrobeUi.playEquip(false);
                        rt.save();
                        statusMsg = CosmeticCatalog.slotLabel(sl, rt.japanese()) + ": " + (rt.japanese() ? "解除" : "Cleared");
                        rebuildWidgets();
                    }
                } else {
                    switchCategory(sl);
                }
                return true;
            }
        }

        // 2. Full-card click (L-click: equip/unequip, R-click: toggle ★ favorite!)
        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 5);
            if (mx >= x && mx < x + cardW && my >= y && my < y + cardH - 16) {
                var item = visible.get(page * capacity + n);
                if (btn == 1) {
                    WardrobeTools.favorite(a, item.id());
                    WardrobeUi.playSparkle();
                    rt.save();
                    rebuildWidgets();
                } else if (btn == 0) {
                    toggleItem(item, a, rt, rt.japanese());
                }
                return true;
            }
        }

        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= studioX && e.y() > stageY + 16 && e.y() < dockY) {
            autoSpin = false;
            targetRotation += (float) dx * 2.1f;
            rotVel = (float) dx * 26.0f;
            targetPitch = Mth.clamp(targetPitch + (float) dy * 0.55f, -26.0f, 22.0f);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= studioX) {
            targetZoom = Mth.clamp(targetZoom + (float) sy * 0.10f, 0.62f, 1.55f);
            return true;
        }
        // Smooth mouse-wheel page turning anywhere over the cosmetic collection grid!
        if (x >= left && x < studioX && y >= top && y < height - 30) {
            int maxPage = Math.max(0, (visible.size() - 1) / capacity);
            if (sy < 0 && page < maxPage) { page++; rebuildWidgets(); return true; }
            if (sy > 0 && page > 0) { page--; rebuildWidgets(); return true; }
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (search == null || !search.isFocused()) {
            int maxPage = Math.max(0, (visible.size() - 1) / capacity);
            if (e.key() == GLFW.GLFW_KEY_RIGHT && page < maxPage) { page++; rebuildWidgets(); return true; }
            if (e.key() == GLFW.GLFW_KEY_LEFT && page > 0) { page--; rebuildWidgets(); return true; }
            if (e.key() == GLFW.GLFW_KEY_R) {
                targetRotation = 155f; targetPitch = -8f; targetZoom = 1.0f; autoSpin = false;
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public void removed() {
        CosmeticLayer.clearStudioPreviews();
        super.removed();
    }

    @Override
    public void onClose() {
        CosmeticLayer.clearStudioPreviews();
        minecraft.gui.setScreen(parent);
    }
}
