package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Upgraded 6-slot 3D Wardrobe Studio supporting 300+ bundled {@code .bbmodel} designs, smart
 * multi-keyword &amp; Hiragana/Katakana search, rarity filtering, 6 sort modes, per-slot fine
 * transform tuning, granular 1st-person &amp; multiplayer settings, and custom Blockbench import.
 */
final class AppearanceScreen extends Screen {
    private final Screen parent;
    private String category = "all", query = "", rarityFilter = "all", statusMsg = "";
    private WardrobeSearch.SortOrder sortOrder = WardrobeSearch.SortOrder.DEFAULT;
    private int page, presetIdx;
    private boolean searching, autoSpin;
    private float rotation = 155, zoom = 1;
    private long lastFrameMs = System.currentTimeMillis();
    private EditBox search;
    private int left, top, collectionWidth, studioX, studioWidth, cardW, cardH, rows, capacity;
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
        top = 74;
        studioWidth = Math.max(142, Math.min(210, width / 3));
        studioX = width - studioWidth - 12;
        collectionWidth = studioX - left - 12;
        cardW = (collectionWidth - 8) / 3;
        rows = Math.max(1, Math.min(3, (height - top - 56) / 56));
        capacity = rows * 3;
        cardH = Math.max(52, Math.min(82, (height - top - 62 - (rows - 1) * 5) / rows));

        // Smart Search Row: [Search Box] [×] [Rarity] [Sort]
        int rarityW = Math.min(58, Math.max(46, collectionWidth / 6));
        int sortW = Math.min(72, Math.max(52, collectionWidth / 5));
        int clearW = 16;
        int searchW = Math.max(70, collectionWidth - rarityW - sortW - clearW - 6);

        search = new EditBox(font, left, 34, searchW, 16, Component.literal(l.t("Search collection", "コレクション検索")));
        search.setMaxLength(80);
        search.setHint(Component.literal(l.t("Search 300+ (e.g. sakura wing, cute, mythic…)", "300種+検索 (例: 桜 翼 / うーぱー / かわいい…)")));
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
        }).bounds(bx, 34, clearW, 16).tooltip(l.t("Clear search & filters", "検索・フィルタをクリア")).build();
        clearBtn.active = !query.isEmpty() || !"all".equals(rarityFilter) || sortOrder != WardrobeSearch.SortOrder.DEFAULT;
        addRenderableWidget(clearBtn);

        bx += clearW + 2;
        addRenderableWidget(WardrobeUi.button(WardrobeSearch.rarityLabel(rarityFilter, ja), b -> {
            rarityFilter = WardrobeSearch.nextRarity(rarityFilter);
            page = 0;
            rebuildWidgets();
        }).bounds(bx, 34, rarityW, 16).tooltip(l.t("Filter by rarity", "レアリティで絞り込み")).build());

        bx += rarityW + 2;
        addRenderableWidget(WardrobeUi.button(sortOrder.label(ja), b -> {
            sortOrder = sortOrder.next();
            page = 0;
            rebuildWidgets();
        }).bounds(bx, 34, sortW, 16).tooltip(l.t("Change sort order", "並び替え順を変更")).build());

        // 8 Category tabs
        String[] ids = {"all", "cape", "back", "hat", "aura", "pet", "companion", "favorites"};
        String[] en = {"All", "Cloak", "Wing", "Head", "Aura", "Pet", "Comp", "★"};
        String[] jaL = {"全て", "マント", "翼", "頭", "オーラ", "ペット", "相棒", "★"};
        int tabW = Math.max(20, (collectionWidth - (ids.length - 1) * 2) / ids.length);
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            String label = (category.equals(id) ? "▸" : "") + (ja ? jaL[i] : en[i]);
            addRenderableWidget(WardrobeUi.button(label, b -> {
                category = id;
                page = 0;
                if ("cape".equals(id) || "back".equals(id)) { rotation = 160; autoSpin = false; }
                else if ("hat".equals(id) || "pet".equals(id) || "companion".equals(id)) { rotation = 20; autoSpin = false; }
                rebuildWidgets();
            }).bounds(left + i * (tabW + 2), 54, tabW, 15).build());
        }

        visible = WardrobeSearch.filterAndSort(
                CosmeticCatalog.all(), a, category, rarityFilter, false, false, false, query, sortOrder);
        page = Math.min(page, Math.max(0, (visible.size() - 1) / capacity));

        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            var item = visible.get(page * capacity + n);
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 5);
            boolean selected = item.equipped(a);
            addRenderableWidget(WardrobeUi.button((selected ? "✓ " : "") + item.label(ja), b -> {
                if (item.equipped(a)) item.unequip(a);
                else item.equip(a);
                rt.save();
                rebuildWidgets();
            }).bounds(x, y + cardH - 15, cardW - 17, 15).tooltip(item.detail(ja)).build());
            addRenderableWidget(WardrobeUi.button(a.favorites.contains(item.id()) ? "★" : "☆", b -> {
                WardrobeTools.favorite(a, item.id());
                rt.save();
                rebuildWidgets();
            }).bounds(x + cardW - 16, y + cardH - 15, 16, 15).build());
        }

        int footer = height - 26;
        var previous = WardrobeUi.button("‹", b -> { page--; rebuildWidgets(); }).bounds(left, footer, 22, 18).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        var next = WardrobeUi.button("›", b -> { page++; rebuildWidgets(); }).bounds(left + 26, footer, 22, 18).build();
        next.active = (page + 1) * capacity < visible.size();
        addRenderableWidget(next);

        int customX = left + 108;
        int avail = Math.max(90, collectionWidth - 108);
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
            statusMsg = l.t("Loaded " + list.size() + " custom models", "自作モデル " + list.size() + " 件読込");
            rebuildWidgets();
        }).bounds(customX + cbW + 3, footer, cbW, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Templates", "雛形出力"), b -> {
            try {
                var tpl = CustomCosmetics.exportTemplates(rt.configDir());
                Util.getPlatform().openPath(tpl);
                statusMsg = l.t("Exported 6 .bbmodel templates!", "6種 .bbmodel テンプレート出力済");
            } catch (Exception e) { statusMsg = e.getMessage(); }
        }).bounds(customX + (cbW + 3) * 2, footer, cbW, 18).tooltip(l.t("Export Blockbench starter templates", "Blockbench用テンプレート書き出し")).build());

        // Right Studio Action Rows
        int half = (studioWidth - 4) / 2;
        int third = (studioWidth - 6) / 3;

        // Row 1: Camera angles
        addRenderableWidget(WardrobeUi.button(l.t("Front", "正面"), b -> { rotation = 18; autoSpin = false; })
                .bounds(studioX, height - 128, third, 14).build());
        addRenderableWidget(WardrobeUi.button(l.t("Back", "背面"), b -> { rotation = 160; autoSpin = false; })
                .bounds(studioX + third + 3, height - 128, third, 14).build());
        addRenderableWidget(WardrobeUi.button(autoSpin ? l.t("Spin:ON", "回転:ON") : l.t("↻ Spin", "↻ 回転"), b -> {
            autoSpin = !autoSpin;
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 128, third, 14).build());

        // Row 2: Dedicated Fine-Transform Adjust + Granular 1st-Person/Multiplayer Settings!
        String initialSlot = CosmeticCatalog.SLOTS.contains(category) ? category : "cape";
        addRenderableWidget(WardrobeUi.button(l.t("✦ Adjust", "✦ 位置・傾き"), b ->
                minecraft.gui.setScreen(new WardrobeAdjustScreen(this, initialSlot)))
                .bounds(studioX, height - 110, half, 15)
                .tooltip(l.t("Fine-tune X/Y/Z position, scale & tilt for every cosmetic slot", "スロットごとに位置(XYZ)・大きさ・傾きを細かく調整"))
                .build());
        addRenderableWidget(WardrobeUi.button(l.t("⚙ Settings", "⚙ 詳細設定"), b ->
                minecraft.gui.setScreen(new WardrobeSettingsScreen(this)))
                .bounds(studioX + half + 4, height - 110, half, 15)
                .tooltip(l.t("1st-person visibility (Aura/Wings/Cape/Hat/Pet) & Multiplayer sync", "一人称視点(オーラ/翼/マント/頭/ペット)とマルチプレイ共有設定"))
                .build());

        // Row 3: Theme Presets + Random + Clear
        addRenderableWidget(WardrobeUi.button(l.t("✦ Theme", "✦ テーマ"), b -> {
            var preset = WardrobeTools.PRESETS.get(presetIdx % WardrobeTools.PRESETS.size());
            presetIdx = (presetIdx + 1) % WardrobeTools.PRESETS.size();
            preset.apply(a);
            rt.save();
            statusMsg = "✦ " + preset.label(ja);
            rebuildWidgets();
        }).bounds(studioX, height - 92, third, 15).tooltip(l.t("Cycle curated 6-slot theme outfits", "厳選6スロットコーデをワンクリック試着")).build());
        addRenderableWidget(WardrobeUi.button(l.t("Random", "ランダム"), b -> {
            WardrobeTools.randomize(a, ThreadLocalRandom.current());
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + third + 3, height - 92, third, 15).build());
        addRenderableWidget(WardrobeUi.button(l.t("Clear", "外す"), b -> {
            WardrobeTools.clear(a);
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 92, third, 15).build());

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
        addRenderableWidget(WardrobeUi.button(l.t("FX:", "粒子:") + (a.cosmeticParticles ? "ON" : "OFF"), b -> {
            a.cosmeticParticles = !a.cosmeticParticles;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX, height - 56, third, 15).build());
        addRenderableWidget(WardrobeUi.button(l.t("Glow:", "発光:") + (a.cosmeticGlow ? "ON" : "OFF"), b -> {
            a.cosmeticGlow = !a.cosmeticGlow;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + third + 3, height - 56, third, 15).build());
        addRenderableWidget(WardrobeUi.button("3D:" + (a.skin3d ? "ON" : "OFF"), b -> {
            a.skin3d = !a.skin3d;
            rt.save();
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 56, third, 15).build());

        // Footer: Outfits | Done
        addRenderableWidget(WardrobeUi.button(l.t("Outfits", "コーデ"), b -> minecraft.gui.setScreen(new OutfitScreen(this)))
                .bounds(studioX, footer, half, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Done", "完了"), b -> onClose())
                .bounds(studioX + half + 4, footer, half, 18).build());
        addRenderableWidget(WardrobeUi.button("◈", b -> {
            int i = 0;
            while (i < WardrobeTools.PALETTE.length && WardrobeTools.PALETTE[i] != a.color) i++;
            a.color = WardrobeTools.PALETTE[(i + 1) % WardrobeTools.PALETTE.length];
            rt.save();
        }).bounds(studioX + studioWidth - 24, 38, 18, 14).tooltip(l.t("Change studio accent color", "スタジオ配色を切り替え")).build());

        if (searching) {
            setFocused(search);
            search.setCursorPosition(query.length());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long nowMs = System.currentTimeMillis();
        float dt = Math.min(0.1f, Math.max(0.0f, (nowMs - lastFrameMs) / 1000.0f));
        lastFrameMs = nowMs;
        if (autoSpin) rotation = (rotation + dt * 42.0f) % 360.0f;

        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();
        double time = CosmeticAnimation.animationTimeSeconds();
        int accentRgb = a.color & 0xFFFFFF;

        g.text(font, l.t("✦ SKYPILOT 3D WARDROBE", "✦ 3D ワードローブ (独立MOD)"), left, 10, WardrobeUi.WHITE, false);
        String syncBadge = RemoteWardrobeSync.statusSummary(ja);
        int syncCol = RemoteWardrobeSync.isConnected() ? WardrobeUi.CYAN : WardrobeUi.GRAY;
        g.text(font, syncBadge, width - font.width(syncBadge) - 14, 10, syncCol, false);

        int totalCount = CosmeticCatalog.all().size();
        String sub = visible.size() + " / " + totalCount + " "
                + l.t("HD 3D .bbmodel designs · 6 slots · free", "種表示 · 6スロット · 無料")
                + (statusMsg.isEmpty() ? "" : "  ·  " + statusMsg);
        g.text(font, sub, left, 22, statusMsg.isEmpty() ? WardrobeUi.GRAY : (0xFF000000 | accentRgb), false);

        // Right 3D Studio Stage
        int stageH = Math.max(60, height - 166);
        WardrobeUi.panel(g, studioX, 34, studioWidth, stageH);
        g.fill(studioX + 1, 35, studioX + studioWidth - 1, 37, 0xFF000000 | accentRgb);
        g.fill(studioX + 1, 37, studioX + studioWidth - 1, 37 + Math.min(34, stageH / 3), 0x1E000000 | accentRgb);
        g.text(font, "STUDIO " + WardrobeTools.equippedCount(a) + "/6", studioX + 8, 41, 0xFF000000 | accentRgb, false);

        int y0 = 54, y1 = height - 134;
        if (y1 > y0) {
            int midX = studioX + studioWidth / 2;
            int floorY = y1 - 12;
            int ringW = Math.min(studioWidth - 28, 92);
            g.fill(midX - ringW / 2, floorY, midX + ringW / 2, floorY + 2, 0x44000000 | accentRgb);
            g.fill(midX - ringW / 3, floorY + 2, midX + ringW / 3, floorY + 4, 0x26000000 | accentRgb);

            float scale = Math.min(studioWidth * .36f, (y1 - y0) * .39f) * zoom;
            if (minecraft.player != null) {
                var state = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, delta);
                if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar) {
                    avatar.bodyRot = 180 + rotation;
                    avatar.yRot = 0;
                    avatar.xRot = 0;
                }
                var orientation = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-8));
                g.entity(state, scale, new org.joml.Vector3f(0, state.boundingBoxHeight / 2, 0), orientation, new org.joml.Quaternionf(), studioX, y0, studioX + studioWidth, y1);
            } else {
                WardrobeModel.renderStudio(g, a, scale, -8, rotation, studioX, y0, studioX + studioWidth, y1, time);
            }
        }

        // Collection Grid
        CosmeticCatalog.Item hovered = null;
        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            var item = visible.get(page * capacity + n);
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 5);
            boolean eq = item.equipped(a);
            boolean isHover = mx >= x && mx < x + cardW && my >= y && my < y + cardH;
            if (isHover) hovered = item;

            int rRgb = item.rarityColor() & 0xFFFFFF;
            g.fill(x + 2, y + 2, x + cardW + 2, y + cardH + 2, 0x70000000);
            g.fill(x, y, x + cardW, y + cardH, isHover ? 0xEE182135 : 0xE6101726);
            int sheenH = Math.min(26, (cardH - 16) / 2);
            g.fill(x + 1, y + 2, x + cardW - 1, y + 2 + sheenH, (eq ? 0x30000000 : 0x1D000000) | rRgb);
            g.fill(x + 1, y + 1, x + cardW - 1, y + 3, 0xFF000000 | (eq ? 0x55D6D2 : rRgb));
            g.outline(x, y, cardW, cardH, eq ? WardrobeUi.CYAN : (isHover ? (0xFF000000 | rRgb) : WardrobeUi.PANEL_BORDER));

            WardrobeModel.renderCard(g, item, a.cosmeticGlow, x + 2, y + 4, x + cardW - 2, y + cardH - 15, time);

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

        // Hover Inspector Bar
        int inspY = height - 42;
        g.fill(left, inspY, left + collectionWidth, inspY + 13, 0xB80B101D);
        g.outline(left, inspY, collectionWidth, 13, 0x443B4D6B);
        if (hovered != null) {
            int rCol = 0xFF000000 | (hovered.rarityColor() & 0xFFFFFF);
            String prefix = "[" + rarityTag(hovered) + "] " + hovered.label(ja) + " — ";
            g.text(font, prefix, left + 5, inspY + 3, rCol, false);
            int px = left + 5 + font.width(prefix);
            int rem = collectionWidth - (px - left) - 5;
            if (rem > 24) {
                g.text(font, font.plainSubstrByWidth(hovered.detail(ja), rem), px, inspY + 3, WardrobeUi.GRAY, false);
            }
        } else {
            g.text(font, l.t("Multi-word search (sakura wing) · Drag studio to spin · Scroll to zoom",
                    "複数語/ひらがな検索対応 (例: うーぱー / 桜 翼) · ドラッグで回転 · スクロールで拡大"), left + 5, inspY + 3, WardrobeUi.GRAY, false);
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
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= studioX && e.y() > 40 && e.y() < height - 128) {
            autoSpin = false;
            rotation += (float) dx * 2;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= studioX) {
            zoom = Math.max(.6f, Math.min(1.45f, zoom + (float) sy * .08f));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

