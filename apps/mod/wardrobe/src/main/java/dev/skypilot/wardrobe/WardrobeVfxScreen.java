package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeTools;
import dev.skypilot.mod.core.WardrobeVfxCatalog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commercial-grade Custom 3D VFX Studio screen for {@code skypilot_wardrobe}.
 * <ul>
 *   <li>Full-color 256×256 HDR studio thumbnails ({@code textures/vfx_preview/<id>.png}) on every card</li>
 *   <li>Real-time 60 FPS multi-layer event-aware animated VFX stage (Attack slash, Kill pillar &amp; circles,
 *       Move ribbon wave, Jump shockwave, Dash portal beam, Projectile comet, Ambient orbit)</li>
 *   <li>Instant hover inspection + 1-click live 3D in-world preview burst</li>
 * </ul>
 */
final class WardrobeVfxScreen extends Screen {
    private static final Identifier TEX_GLOW = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/glow_soft.png");
    private static final Identifier TEX_SLASH = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/slash_arc.png");
    private static final Identifier TEX_SLASH_CORE = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/slash_core.png");
    private static final Identifier TEX_PILLAR = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/pillar_beam.png");
    private static final Identifier TEX_RIBBON = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/ribbon_trail.png");
    private static final Identifier TEX_SIGIL = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/magic_sigil_outer.png");
    private static final Identifier TEX_VORTEX = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/vortex_spiral.png");
    private static final Identifier TEX_BEAM = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/pet_beam.png");
    private static final Identifier TEX_SPARK = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/star_spark.png");
    private static final Identifier TEX_SHOCK = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/shockwave_ring.png");

    private final Screen parent;
    private String eventSlot = "attack";
    private String query = "";
    private String statusMsg = "";
    private boolean searching;
    private int page;
    private long flashUntilMs;
    private int flashColor = 0x55D6D2;
    private EditBox search;

    private int left, top, gridWidth, sideX, sideWidth, cardW, cardH, rows, capacity;
    private int stageY, stageH, summaryTop;
    private List<WardrobeVfxCatalog.VfxStyle> visible = List.of();

    WardrobeVfxScreen(Screen parent) {
        super(Component.literal("Custom 3D VFX Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        SkyPilotConfig.VfxSettings vfx = rt.appearance().vfx;
        if (vfx == null) {
            vfx = new SkyPilotConfig.VfxSettings();
            rt.appearance().vfx = vfx;
        }
        final SkyPilotConfig.VfxSettings cfg = vfx;

        left = 12;
        top = 82;
        sideWidth = Math.max(162, Math.min(220, width / 3));
        sideX = width - sideWidth - 12;
        gridWidth = sideX - left - 12;
        cardW = (gridWidth - 8) / 3;
        rows = Math.max(1, Math.min(3, (height - top - 56) / 56));
        capacity = rows * 3;
        cardH = Math.max(52, Math.min(86, (height - top - 62 - (rows - 1) * 5) / rows));
        stageY = 32;
        stageH = Math.max(88, height - 170);

        // Search row
        int clearW = 16;
        int offW = Math.min(68, Math.max(48, gridWidth / 5));
        int searchW = Math.max(70, gridWidth - clearW - offW - 6);
        search = new EditBox(font, left, 32, searchW, 16, Component.literal(l.t("Search VFX", "エフェクト検索")));
        search.setMaxLength(64);
        search.setHint(Component.literal(l.t("Search VFX (sakura, thunder, heart, custom…)", "エフェクト検索 (例: 桜 / 雷 / ハート / 自作…)")));
        search.setValue(query);
        search.setResponder(val -> {
            query = val;
            searching = true;
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(search);

        var clearBtn = WardrobeUi.button("×", b -> {
            query = "";
            searching = false;
            page = 0;
            rebuildWidgets();
        }).bounds(left + searchW + 2, 32, clearW, 16).tooltip(l.t("Clear search", "検索をクリア")).build();
        clearBtn.active = !query.isEmpty();
        addRenderableWidget(clearBtn);

        String currentId = WardrobeVfxCatalog.equippedId(cfg, eventSlot);
        var slotOffBtn = WardrobeUi.button(l.t("Slot OFF", "この枠OFF"), b -> {
            WardrobeVfxCatalog.equip(cfg, eventSlot, "none");
            rt.save();
            statusMsg = WardrobeVfxCatalog.eventLabel(eventSlot, ja) + ": OFF";
            rebuildWidgets();
        }).bounds(left + searchW + clearW + 4, 32, offW, 16).tooltip(l.t("Disable VFX for this trigger", "選択中のイベント枠をOFFにする")).build();
        slotOffBtn.active = !"none".equals(currentId);
        addRenderableWidget(slotOffBtn);

        // 7 Event Slot Tabs
        int tabCount = WardrobeVfxCatalog.EVENT_SLOTS.size();
        int tabW = Math.max(24, (gridWidth - (tabCount - 1) * 2) / tabCount);
        for (int i = 0; i < tabCount; i++) {
            String slot = WardrobeVfxCatalog.EVENT_SLOTS.get(i);
            boolean activeTab = slot.equals(eventSlot);
            boolean equipped = !"none".equals(WardrobeVfxCatalog.equippedId(cfg, slot));
            String prefix = equipped ? "●" : "";
            String lbl = prefix + WardrobeVfxCatalog.eventLabel(slot, ja);
            addRenderableWidget(WardrobeUi.button(lbl, b -> {
                eventSlot = slot;
                page = 0;
                rebuildWidgets();
            }).bounds(left + i * (tabW + 2), 52, tabW, 15)
                    .style(activeTab ? WardrobeUi.Style.ACTIVE_TAB : (equipped ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT))
                    .tooltip(WardrobeVfxCatalog.eventDesc(slot, ja)).build());
        }

        visible = WardrobeVfxCatalog.search(query);
        page = Math.min(page, Math.max(0, (visible.size() - 1) / capacity));

        // Style cards
        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            WardrobeVfxCatalog.VfxStyle style = visible.get(page * capacity + n);
            int x = left + (n % 3) * (cardW + 4);
            int y = top + (n / 3) * (cardH + 5);
            boolean selected = style.id().equals(currentId);

            addRenderableWidget(WardrobeUi.button((selected ? "✓ " : "") + style.label(ja), b -> {
                toggleStyle(cfg, style, rt, ja);
            }).bounds(x, y + cardH - 15, cardW - 18, 15)
                    .style(selected ? WardrobeUi.Style.EQUIPPED : WardrobeUi.Style.DEFAULT)
                    .accent(style.primaryColor())
                    .tooltip(style.detail(ja)).build());

            addRenderableWidget(WardrobeUi.button("▶", b -> {
                WardrobeVfxEngine.triggerPreview(eventSlot, style.id());
                flash(style.primaryColor());
                statusMsg = "▶ " + style.label(ja) + " (" + WardrobeVfxCatalog.eventLabel(eventSlot, ja) + ")";
            }).bounds(x + cardW - 17, y + cardH - 15, 17, 15).tooltip(l.t("Play live 3D VFX preview", "ワールドに3Dエフェクトをテスト再生")).build());
        }

        // Bottom Left: Pagination + Custom PNG VFX folder tools
        int footer = height - 26;
        var prev = WardrobeUi.button("‹", b -> { page--; rebuildWidgets(); }).bounds(left, footer, 22, 18).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        var next = WardrobeUi.button("›", b -> { page++; rebuildWidgets(); }).bounds(left + 26, footer, 22, 18).build();
        next.active = (page + 1) * capacity < visible.size();
        addRenderableWidget(next);

        int customX = left + 100;
        int avail = Math.max(96, gridWidth - 100);
        int cbW = Math.min(72, (avail - 6) / 3);
        addRenderableWidget(WardrobeUi.button(l.t("VFX Dir", "VFXフォルダ"), b -> {
            try {
                var dir = CustomVfxLoader.ensureDir(rt.configDir());
                Util.getPlatform().openPath(dir);
                statusMsg = l.t("Opened config/skypilot/vfx/", "自作VFXフォルダを開きました");
            } catch (Exception e) { statusMsg = e.getMessage(); }
        }).bounds(customX, footer, cbW, 18).tooltip(l.t("Drop custom PNG sprites into config/skypilot/vfx/", "自作PNG画像を config/skypilot/vfx/ に入れて読み込み")).build());

        addRenderableWidget(WardrobeUi.button(l.t("Reload", "再読込"), b -> {
            var loaded = CustomVfxLoader.reload(rt.configDir());
            statusMsg = l.t("Loaded " + loaded.size() + " custom VFX PNGs", "自作VFX " + loaded.size() + " 件読込");
            rebuildWidgets();
        }).bounds(customX + cbW + 3, footer, cbW, 18).build());

        addRenderableWidget(WardrobeUi.button(l.t("Samples", "雛形出力"), b -> {
            try {
                var dir = CustomVfxLoader.exportSampleTemplates(rt.configDir());
                Util.getPlatform().openPath(dir);
                CustomVfxLoader.reload(rt.configDir());
                statusMsg = l.t("Exported 5 sample 256×256 PNG templates!", "256×256 自作VFX用PNGテンプレート5種を出力");
                rebuildWidgets();
            } catch (Exception e) { statusMsg = e.getMessage(); }
        }).bounds(customX + (cbW + 3) * 2, footer, cbW, 18).tooltip(l.t("Export 5 glowing 256x256 PNG templates to customize", "カスタマイズ用の256x256発光PNG雛形5種を出力")).build());

        // Right Studio Controls
        int half = (sideWidth - 4) / 2;

        // Row 1: Play Test + Apply to All 7 Slots
        addRenderableWidget(WardrobeUi.button(l.t("▶ Preview", "▶ テスト再生"), b -> {
            String id = WardrobeVfxCatalog.equippedId(cfg, eventSlot);
            if ("none".equals(id) && !visible.isEmpty()) id = visible.get(0).id();
            WardrobeVfxEngine.triggerPreview(eventSlot, id);
            var st = WardrobeVfxCatalog.byId(id);
            flash(st != null ? st.primaryColor() : 0x55D6D2);
            statusMsg = "▶ " + (st != null ? st.label(ja) : id);
        }).bounds(sideX, height - 134, half, 16).tooltip(l.t("Trigger this VFX live around your character", "プレイヤーの周囲に3Dエフェクトをテスト再生")).build());

        addRenderableWidget(WardrobeUi.button(l.t("✦ Apply All", "✦ 全枠に適用"), b -> {
            String id = WardrobeVfxCatalog.equippedId(cfg, eventSlot);
            if ("none".equals(id) && !visible.isEmpty()) id = visible.get(0).id();
            WardrobeVfxCatalog.applyThemeToAll(cfg, id);
            rt.save();
            WardrobeVfxEngine.triggerPreview(eventSlot, id);
            var st = WardrobeVfxCatalog.byId(id);
            flash(st != null ? st.primaryColor() : 0x55D6D2);
            statusMsg = l.t("Applied to all 7 slots!", "7つの全イベント枠に一括適用しました！");
            rebuildWidgets();
        }).bounds(sideX + half + 4, height - 134, half, 16).tooltip(l.t("Apply this theme to all 7 event triggers", "このテーマを全7イベント枠に一括適用")).build());

        // Row 2: Scale - / Size % / +
        int scPct = (int) Math.round(cfg.scale * 100);
        addRenderableWidget(WardrobeUi.button("S-", b -> {
            cfg.scale = Math.max(0.5, Math.round((cfg.scale - 0.1) * 10.0) / 10.0);
            rt.save();
            rebuildWidgets();
        }).bounds(sideX, height - 114, 26, 15).build());
        addRenderableWidget(WardrobeUi.button(l.t("Size " + scPct + "%", "大きさ " + scPct + "%"), b -> {
            cfg.scale = 1.0;
            rt.save();
            rebuildWidgets();
        }).bounds(sideX + 28, height - 114, sideWidth - 56, 15).tooltip(l.t("Reset VFX scale to 100%", "大きさを100%にリセット")).build());
        addRenderableWidget(WardrobeUi.button("S+", b -> {
            cfg.scale = Math.min(2.0, Math.round((cfg.scale + 0.1) * 10.0) / 10.0);
            rt.save();
            rebuildWidgets();
        }).bounds(sideX + sideWidth - 26, height - 114, 26, 15).build());

        // Row 3: Intensity + Custom Color toggle + Palette swatch
        String intLbl = switch (cfg.intensity) {
            case 1 -> l.t("Density:Low", "量:控えめ");
            case 3 -> l.t("Density:Max", "量:派手");
            default -> l.t("Density:Med", "量:標準");
        };
        addRenderableWidget(WardrobeUi.button(intLbl, b -> {
            cfg.intensity = (cfg.intensity % 3) + 1;
            rt.save();
            rebuildWidgets();
        }).bounds(sideX, height - 95, half, 15).build());

        addRenderableWidget(WardrobeUi.button(cfg.customColorEnabled ? l.t("Tint:Custom", "色:カスタム") : l.t("Tint:Theme", "色:テーマ色"), b -> {
            cfg.customColorEnabled = !cfg.customColorEnabled;
            rt.save();
            rebuildWidgets();
        }).bounds(sideX + half + 4, height - 95, half - 22, 15).build());

        addRenderableWidget(WardrobeUi.button("◈", b -> {
            int idx = 0;
            while (idx < WardrobeTools.PALETTE.length && WardrobeTools.PALETTE[idx] != cfg.customColor) idx++;
            cfg.customColor = WardrobeTools.PALETTE[(idx + 1) % WardrobeTools.PALETTE.length];
            cfg.customColorEnabled = true;
            rt.save();
            flash(cfg.customColor);
            rebuildWidgets();
        }).bounds(sideX + sideWidth - 18, height - 95, 18, 15).tooltip(l.t("Cycle custom tint color", "カスタムカラーを切り替え")).build());

        // Row 4: 1st-Person VFX & Other Players' VFX
        addRenderableWidget(WardrobeUi.button(l.t("1stP:", "一人称:") + (cfg.firstPersonVfx ? "ON" : "OFF"), b -> {
            cfg.firstPersonVfx = !cfg.firstPersonVfx;
            rt.save();
            rebuildWidgets();
        }).bounds(sideX, height - 76, half, 15).tooltip(l.t("Show custom VFX in 1st-person view", "一人称視点でも自作エフェクトを表示")).build());

        addRenderableWidget(WardrobeUi.button(l.t("Others:", "他人VFX:") + (cfg.otherPlayerVfx ? "ON" : "OFF"), b -> {
            cfg.otherPlayerVfx = !cfg.otherPlayerVfx;
            rt.save();
            rebuildWidgets();
        }).bounds(sideX + half + 4, height - 76, half, 15).tooltip(l.t("Render synced VFX for nearby mod users", "他プレイヤーの自作エフェクトも表示")).build());

        // Row 5: Randomize 7 Slots | Reset All OFF | Done
        addRenderableWidget(WardrobeUi.button(l.t("Random", "ランダム"), b -> {
            var all = WardrobeVfxCatalog.all();
            if (!all.isEmpty()) {
                var rng = ThreadLocalRandom.current();
                for (String s : WardrobeVfxCatalog.EVENT_SLOTS) {
                    WardrobeVfxCatalog.equip(cfg, s, all.get(rng.nextInt(all.size())).id());
                }
                rt.save();
                WardrobeVfxEngine.triggerPreview(eventSlot, WardrobeVfxCatalog.equippedId(cfg, eventSlot));
                statusMsg = l.t("Randomized all 7 VFX slots!", "全7スロットにランダムVFXを適用しました！");
                rebuildWidgets();
            }
        }).bounds(sideX, height - 56, half, 16).build());

        addRenderableWidget(WardrobeUi.button(l.t("All OFF", "すべてOFF"), b -> {
            WardrobeVfxCatalog.applyThemeToAll(cfg, "none");
            rt.save();
            statusMsg = l.t("Disabled all VFX slots", "全エフェクトをOFFにしました");
            rebuildWidgets();
        }).bounds(sideX + half + 4, height - 56, half, 16).build());

        addRenderableWidget(WardrobeUi.button(l.t("Done", "完了"), b -> onClose())
                .bounds(sideX, footer, sideWidth, 18).build());

        if (searching) {
            setFocused(search);
            search.setCursorPosition(query.length());
        }
    }

    private void toggleStyle(SkyPilotConfig.VfxSettings cfg, WardrobeVfxCatalog.VfxStyle style, WardrobeRuntime rt, boolean ja) {
        String currentId = WardrobeVfxCatalog.equippedId(cfg, eventSlot);
        if (style.id().equals(currentId)) {
            WardrobeVfxCatalog.equip(cfg, eventSlot, "none");
            WardrobeUi.playEquip(false);
            statusMsg = WardrobeVfxCatalog.eventLabel(eventSlot, ja) + ": OFF";
        } else {
            WardrobeVfxCatalog.equip(cfg, eventSlot, style.id());
            WardrobeVfxEngine.triggerPreview(eventSlot, style.id());
            WardrobeUi.playEquip(true);
            flash(style.primaryColor());
            statusMsg = "✦ " + WardrobeVfxCatalog.eventLabel(eventSlot, ja) + " → " + style.label(ja);
        }
        rt.save();
        rebuildWidgets();
    }

    private void flash(int rgb) {
        this.flashColor = rgb & 0xFFFFFF;
        this.flashUntilMs = System.currentTimeMillis() + 600L;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        SkyPilotConfig.VfxSettings cfg = rt.appearance().vfx;
        if (cfg == null) cfg = new SkyPilotConfig.VfxSettings();
        double time = CosmeticAnimation.animationTimeSeconds();

        int activeCnt = WardrobeVfxCatalog.activeCount(cfg);
        g.text(font, l.t("✦ CUSTOM 3D VFX STUDIO", "✦ 自作3Dエフェクトスタジオ (HD VFX)"), left, 10, WardrobeUi.WHITE, false);
        String badge = l.t("Active: " + activeCnt + "/7 · Live 3D: " + WardrobeVfxEngine.activeEffectCount(),
                "設定済: " + activeCnt + "/7枠 · 描画中: " + WardrobeVfxEngine.activeEffectCount());
        g.text(font, badge, width - font.width(badge) - 14, 10, activeCnt > 0 ? WardrobeUi.CYAN : WardrobeUi.GRAY, false);

        String sub = statusMsg.isEmpty()
                ? l.t("256×256 HDR emissive sprites + 3D ribbons & domes · Drop custom PNGs into config/skypilot/vfx/",
                        "256×256高精細発光スプライト＋3Dリボン＆ドーム · config/skypilot/vfx/ に自作PNG追加可能")
                : statusMsg;
        g.text(font, font.plainSubstrByWidth(sub, width - 28), left, 21,
                statusMsg.isEmpty() ? WardrobeUi.GRAY : WardrobeUi.CYAN, false);

        // Event description banner
        g.fill(left, 69, left + gridWidth, 80, 0xC40D1528);
        String evText = "▸ " + WardrobeVfxCatalog.eventLabel(eventSlot, ja) + " — " + WardrobeVfxCatalog.eventDesc(eventSlot, ja);
        g.text(font, font.plainSubstrByWidth(evText, gridWidth - 8), left + 4, 71, WardrobeUi.CYAN, false);

        String eqId = WardrobeVfxCatalog.equippedId(cfg, eventSlot);
        WardrobeVfxCatalog.VfxStyle eqStyle = WardrobeVfxCatalog.byId(eqId);

        // Grid of Full-Color HDR VFX Style Cards (compute hover first so right stage can live-preview hovered card!)
        WardrobeVfxCatalog.VfxStyle hovered = null;
        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            WardrobeVfxCatalog.VfxStyle st = visible.get(page * capacity + n);
            int x = left + (n % 3) * (cardW + 4);
            int y = top + (n / 3) * (cardH + 5);
            boolean eq = st.id().equals(eqId);
            boolean isHover = mx >= x && mx < x + cardW && my >= y && my < y + cardH;
            if (isHover) hovered = st;

            int c1 = st.primaryColor() & 0xFFFFFF;
            int c2 = st.secondaryColor() & 0xFFFFFF;
            int rCol = st.rarityColor() & 0xFFFFFF;

            // Card drop shadow + dark HDR studio gradient
            g.fill(x + 2, y + 2, x + cardW + 2, y + cardH + 2, 0x7A000000);
            g.fillGradient(x, y, x + cardW, y + cardH - 15,
                    isHover ? 0xF5151E34 : 0xEE0B1020,
                    isHover ? 0xF50A0F1D : 0xEE060912);
            g.fill(x + 1, y + 1, x + cardW - 1, y + 3, 0xFF000000 | (eq ? 0x55D6D2 : c1));
            g.outline(x, y, cardW, cardH, eq ? WardrobeUi.CYAN : (isHover ? (0xFF000000 | c1) : WardrobeUi.PANEL_BORDER));

            // Full-color 256×256 HDR studio artwork thumbnail + gentle pulse on hover/equip
            drawCardThumbnail(g, st, x + 2, y + 4, x + cardW - 2, y + cardH - 16, time + n * 0.4, isHover || eq);

            // Subtle dual palette pills on bottom-left
            g.fill(x + 5, y + cardH - 22, x + 12, y + cardH - 18, 0xFF000000 | c1);
            g.fill(x + 13, y + cardH - 22, x + 20, y + cardH - 18, 0xFF000000 | c2);

            String rTag = st.custom() ? "CUSTOM" : st.rarity().toUpperCase(Locale.ROOT);
            g.text(font, rTag, x + 5, y + 5, 0xFF000000 | rCol, false);
            if (eq) {
                String eqLbl = ja ? "✓適用" : "✓ON";
                g.text(font, eqLbl, x + cardW - font.width(eqLbl) - 4, y + 5, WardrobeUi.CYAN, false);
            }
        }

        if (visible.isEmpty()) {
            g.text(font, l.t("No matching VFX themes", "一致するエフェクトがありません"), left, top + 14, WardrobeUi.GRAY, false);
        }

        // ---- Right Live 60 FPS Animated VFX Showcase Panel ----
        WardrobeUi.panel(g, sideX, stageY, sideWidth, stageH);
        WardrobeVfxCatalog.VfxStyle previewStyle = hovered != null ? hovered : eqStyle;
        boolean hoveringPreview = hovered != null && (eqStyle == null || !hovered.id().equals(eqStyle.id()));
        int accentRgb = cfg.customColorEnabled ? (cfg.customColor & 0xFFFFFF)
                : (previewStyle != null ? previewStyle.primaryColor() & 0xFFFFFF : 0x55D6D2);
        g.fill(sideX + 1, stageY + 1, sideX + sideWidth - 1, stageY + 3, 0xFF000000 | accentRgb);

        long now = System.currentTimeMillis();
        if (now < flashUntilMs) {
            int alpha = (int) (75 * ((flashUntilMs - now) / 600.0));
            g.fill(sideX + 1, stageY + 3, sideX + sideWidth - 1, stageY + stageH - 1, (alpha << 24) | flashColor);
        }

        String headerPrefix = hoveringPreview ? (ja ? "試覧: " : "Preview: ") : (WardrobeVfxCatalog.eventLabel(eventSlot, ja) + ": ");
        String headerName = previewStyle != null ? previewStyle.label(ja) : "OFF";
        g.text(font, font.plainSubstrByWidth(headerPrefix + headerName, sideWidth - 12),
                sideX + 6, stageY + 6, 0xFF000000 | accentRgb, false);

        int summaryRows = WardrobeVfxCatalog.EVENT_SLOTS.size();
        int rowH = stageH >= 176 ? 11 : 10;
        int summaryH = Math.min(stageH - 54, summaryRows * rowH + 6);
        int stageInnerX0 = sideX + 5;
        int stageInnerY0 = stageY + 17;
        int stageInnerX1 = sideX + sideWidth - 5;
        int stageInnerY1 = stageY + stageH - summaryH - 4;
        summaryTop = stageY + stageH - summaryH;

        if (stageInnerY1 - stageInnerY0 >= 32) {
            // Deep obsidian HDR preview viewport
            g.fillGradient(stageInnerX0, stageInnerY0, stageInnerX1, stageInnerY1, 0xFF070B18, 0xFF101930);
            g.outline(stageInnerX0, stageInnerY0, stageInnerX1 - stageInnerX0, stageInnerY1 - stageInnerY0, 0x663B4D6B);

            if (previewStyle != null) {
                g.enableScissor(stageInnerX0 + 1, stageInnerY0 + 1, stageInnerX1 - 1, stageInnerY1 - 1);
                drawLiveAnimatedStage(g, previewStyle, eventSlot,
                        cfg.customColorEnabled ? cfg.customColor : -1,
                        stageInnerX0 + 1, stageInnerY0 + 1, stageInnerX1 - 1, stageInnerY1 - 1, time);
                g.disableScissor();

                // Small event tag badge at bottom-right of live viewport
                String evTag = WardrobeVfxCatalog.eventLabel(eventSlot, ja);
                int tw = font.width(evTag);
                g.fill(stageInnerX1 - tw - 8, stageInnerY1 - 12, stageInnerX1 - 2, stageInnerY1 - 2, 0xAA060A14);
                g.text(font, evTag, stageInnerX1 - tw - 5, stageInnerY1 - 11, 0xFF000000 | accentRgb, false);
            } else {
                g.centeredText(font, l.t("Hover or click a VFX card", "左のカードにカーソル/クリックで表示"),
                        (stageInnerX0 + stageInnerX1) / 2, (stageInnerY0 + stageInnerY1) / 2 - 4, WardrobeUi.GRAY);
            }
        }

        // Clickable 7-slot summary table inside showcase panel
        g.fill(sideX + 4, summaryTop - 2, sideX + sideWidth - 4, summaryTop - 1, 0x553B4D6B);
        for (int i = 0; i < summaryRows; i++) {
            int ry = summaryTop + 2 + i * rowH;
            if (ry + 9 > stageY + stageH) break;
            String s = WardrobeVfxCatalog.EVENT_SLOTS.get(i);
            String sid = WardrobeVfxCatalog.equippedId(cfg, s);
            WardrobeVfxCatalog.VfxStyle st = WardrobeVfxCatalog.byId(sid);
            boolean cur = s.equals(eventSlot);
            boolean rowHover = mx >= sideX + 4 && mx <= sideX + sideWidth - 4 && my >= ry - 1 && my < ry + rowH - 1;
            if (cur) g.fill(sideX + 4, ry - 1, sideX + sideWidth - 4, ry + rowH - 1, 0x3855D6D2);
            else if (rowHover) g.fill(sideX + 4, ry - 1, sideX + sideWidth - 4, ry + rowH - 1, 0x22FFFFFF);
            String sl = WardrobeVfxCatalog.eventLabel(s, ja);
            String val = st != null ? st.label(ja) : "OFF";
            int vCol = st != null ? (0xFF000000 | (st.primaryColor() & 0xFFFFFF)) : WardrobeUi.DARK_GRAY;
            g.text(font, (cur ? "▸" : " ") + sl, sideX + 6, ry, cur ? WardrobeUi.WHITE : WardrobeUi.GRAY, false);
            String shortVal = font.plainSubstrByWidth(val, Math.max(24, sideWidth - font.width(sl) - 24));
            g.text(font, shortVal, sideX + sideWidth - font.width(shortVal) - 6, ry, vCol, false);
        }

        // Bottom inspector bar
        int inspY = height - 42;
        g.fill(left, inspY, left + gridWidth, inspY + 13, 0xC40B101D);
        g.outline(left, inspY, gridWidth, 13, 0x443B4D6B);
        if (hovered != null) {
            int col = 0xFF000000 | (hovered.primaryColor() & 0xFFFFFF);
            String prefix = hovered.label(ja) + " — ";
            g.text(font, prefix, left + 5, inspY + 3, col, false);
            int px = left + 5 + font.width(prefix);
            int rem = gridWidth - (px - left) - 5;
            if (rem > 24) {
                g.text(font, font.plainSubstrByWidth(hovered.detail(ja), rem), px, inspY + 3, WardrobeUi.GRAY, false);
            }
        } else {
            g.text(font, l.t("Hover any card for live 60fps preview · Click card to equip · Click ▶ for 3D world burst!",
                    "カードにカーソルで右にアニメーション表示 · クリックで適用 · ▶でワールド再生！"), left + 5, inspY + 3, WardrobeUi.GRAY, false);
        }

        g.text(font, (page + 1) + "/" + Math.max(1, (visible.size() + capacity - 1) / capacity), left + 52, height - 20, WardrobeUi.GRAY, false);
        super.extractRenderState(g, mx, my, delta);
    }

    /** Draws a full-color 256×256 HDR studio preview thumbnail on a style card. */
    private static void drawCardThumbnail(
            GuiGraphicsExtractor g,
            WardrobeVfxCatalog.VfxStyle st,
            int x0, int y0, int x1, int y1,
            double time,
            boolean emphasize
    ) {
        int w = x1 - x0;
        int h = y1 - y0;
        if (w < 12 || h < 12) return;
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;
        int baseSize = Math.max(20, Math.min(w - 4, h - 2));
        float pulse = emphasize ? (float) (1.0 + 0.055 * Math.sin(time * 4.2)) : 1.0f;
        int drawSz = Math.round(baseSize * pulse);

        int c1 = resolveColor(st, -1, time, false);
        // Soft ambient studio glow behind the thumbnail
        int glowSz = Math.round(drawSz * 1.28f);
        blitCentered(g, TEX_GLOW, cx, cy, glowSz, glowSz, argb(emphasize ? 135 : 80, c1));

        if (!st.custom()) {
            Identifier previewTex = Identifier.fromNamespaceAndPath("skypilot", "textures/vfx_preview/" + st.id() + ".png");
            int tint = "prism".equals(st.id()) ? argb(255, blendWhite(c1, 0.55f)) : 0xFFFFFFFF;
            blitCentered(g, previewTex, cx, cy, drawSz, drawSz, tint);
        } else {
            // Custom user PNG: render ring + user artwork full color
            Identifier ringTex = CustomVfxLoader.resolveTexture(st.ringTex());
            Identifier customTex = CustomVfxLoader.resolveTexture(st.primaryTex());
            blitCentered(g, ringTex, cx, cy, drawSz, drawSz, argb(165, st.secondaryColor()));
            int artSz = Math.round(drawSz * 0.72f);
            blitCentered(g, customTex, cx, cy, artSz, artSz, 0xFFFFFFFF);
        }
    }

    /** Renders a rich 60 FPS multi-layer event-specific animated preview inside the right showcase stage. */
    private static void drawLiveAnimatedStage(
            GuiGraphicsExtractor g,
            WardrobeVfxCatalog.VfxStyle st,
            String slot,
            int overrideColor,
            int x0, int y0, int x1, int y1,
            double time
    ) {
        int w = x1 - x0;
        int h = y1 - y0;
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;
        int unit = Math.max(26, Math.min(w, h));
        int c1 = resolveColor(st, overrideColor, time, false);
        int c2 = resolveColor(st, overrideColor, time, true);
        int cCore = blendWhite(c1, 0.72f);
        Identifier motif = CustomVfxLoader.resolveTexture(st.primaryTex());
        Identifier ring = CustomVfxLoader.resolveTexture(st.ringTex());
        int motifTint = st.custom() ? 0xFFFFFFFF : argb(245, blendWhite(c1, 0.25f));

        // Subtle atmospheric backdrop halo
        int haloSz = Math.round(unit * (0.95f + 0.08f * (float) Math.sin(time * 2.5)));
        blitCentered(g, TEX_GLOW, cx, cy, haloSz, haloSz, argb(105, c1));

        switch (slot) {
            case "attack" -> {
                double p = (time * 0.85) % 1.0;
                // Expanding impact shockwave
                int ringSz = Math.round(unit * (0.28f + 0.86f * (float) p));
                int ringA = Math.max(0, (int) (210 * (1.0 - p)));
                blitCentered(g, ring, cx, cy, ringSz, ringSz, argb(ringA, c2));

                // Sweeping dual-layer slash arc + white-hot core
                float sweepX = cx + (float) ((p - 0.35) * w * 0.36);
                float sweepY = cy - (float) ((p - 0.35) * h * 0.14);
                int slashW = Math.round(unit * (0.82f + 0.28f * (float) Math.sin(p * Math.PI)));
                int slashH = Math.round(slashW * 0.72f);
                int slashA = Math.max(0, (int) (255 * Math.sin(Math.min(1.0, p * 1.15) * Math.PI)));
                blitCentered(g, TEX_SLASH, Math.round(sweepX), Math.round(sweepY), slashW, slashH, argb(slashA, c1));
                blitCentered(g, TEX_SLASH_CORE, Math.round(sweepX), Math.round(sweepY),
                        Math.round(slashW * 0.92f), Math.round(slashH * 0.92f), argb(Math.min(255, slashA + 25), cCore));

                // Impact star flare + scattering motifs
                int sparkSz = Math.round(unit * (0.22f + 0.30f * (float) (1.0 - p)));
                blitCentered(g, TEX_SPARK, cx, cy, sparkSz, sparkSz, argb(ringA, 0xFFFFFF));
                for (int i = 0; i < 6; i++) {
                    double ang = i * (Math.PI * 2.0 / 6.0) + 0.35;
                    double rad = unit * (0.14 + 0.42 * p);
                    int mx = cx + (int) Math.round(Math.cos(ang) * rad);
                    int my = cy + (int) Math.round(Math.sin(ang) * rad * 0.75);
                    int mSz = Math.max(10, Math.round(unit * 0.22f * (1.0f - (float) p * 0.35f)));
                    blitCentered(g, motif, mx, my, mSz, mSz, argb(Math.max(0, (int) (240 * (1.0 - p * 0.85))), (i % 2 == 0) ? c1 : c2));
                }
            }
            case "kill" -> {
                double p = (time * 0.58) % 1.0;
                // Ground sigil & counter-pulsing vortex
                int sigilW = Math.round(unit * (0.55f + 0.58f * (float) p));
                int sigilH = Math.round(sigilW * 0.44f);
                int baseY = y1 - Math.max(14, h / 5);
                int fade = (int) (235 * Math.sin(Math.min(1.0, p) * Math.PI));
                blitCentered(g, TEX_SIGIL, cx, baseY, sigilW, sigilH, argb(fade, c1));
                blitCentered(g, TEX_VORTEX, cx, baseY, Math.round(sigilW * 0.72f), Math.round(sigilH * 0.72f), argb(fade, c2));

                // Celestial light pillar rising from ground
                int pilW = Math.round(unit * (0.46f - 0.14f * (float) p));
                int pilH = Math.round(h * (0.45f + 0.65f * (float) Math.sin(Math.min(1.0, p * 1.1) * Math.PI)));
                blitCentered(g, TEX_PILLAR, cx, baseY - pilH / 3, pilW, pilH, argb(Math.min(255, fade + 20), c1));
                blitCentered(g, TEX_PILLAR, cx, baseY - pilH / 3, Math.max(6, pilW / 2), pilH, argb(fade, cCore));

                // Rising fountain of themed motifs
                for (int i = 0; i < 8; i++) {
                    double fp = (p + i * 0.125) % 1.0;
                    double swirl = fp * 5.0 + i * 1.1;
                    int px = cx + (int) Math.round(Math.cos(swirl) * unit * (0.14 + 0.28 * fp));
                    int py = baseY - (int) Math.round(fp * (h * 0.78));
                    int mSz = Math.max(10, Math.round(unit * (0.24f - 0.08f * (float) fp)));
                    int a = (int) (245 * Math.sin(fp * Math.PI));
                    blitCentered(g, motif, px, py, mSz, mSz, st.custom() ? argb(a, 0xFFFFFF) : argb(a, (i % 2 == 0) ? c1 : c2));
                }
            }
            case "move" -> {
                // Flowing sinusoidal ribbon wave across the stage
                int segs = 12;
                for (int i = 0; i < segs; i++) {
                    double t = i / (double) (segs - 1);
                    int px = x0 + (int) Math.round(w * (0.08 + 0.84 * t));
                    int py = cy + (int) Math.round(Math.sin(t * 5.2 - time * 4.5) * h * 0.22);
                    int rSz = Math.round(unit * (0.24f + 0.12f * (float) Math.sin(t * Math.PI)));
                    int a = (int) (185 * Math.sin(t * Math.PI));
                    blitCentered(g, TEX_RIBBON, px, py, rSz, Math.max(8, rSz / 2), argb(a, (i % 2 == 0) ? c1 : c2));
                }
                // Drifting motifs along the trail
                for (int i = 0; i < 6; i++) {
                    double fp = ((time * 0.45) + i / 6.0) % 1.0;
                    int px = x0 + (int) Math.round(w * (0.08 + 0.84 * fp));
                    int py = cy + (int) Math.round(Math.sin(fp * 5.2 - time * 4.5) * h * 0.22
                            + Math.cos(i * 2.1 + time * 3.0) * 6.0);
                    int mSz = Math.max(11, Math.round(unit * 0.24f));
                    int a = (int) (250 * Math.sin(fp * Math.PI));
                    blitCentered(g, motif, px, py, mSz, mSz, st.custom() ? argb(a, 0xFFFFFF) : argb(a, (i % 2 == 0) ? c1 : c2));
                }
            }
            case "jump" -> {
                double p = (time * 0.78) % 1.0;
                int baseY = y1 - Math.max(16, h / 4);
                // Dual expanding concentric ground shockwaves
                for (int k = 0; k < 2; k++) {
                    double kp = (p + k * 0.35) % 1.0;
                    int rw = Math.round(unit * (0.25f + 0.92f * (float) kp));
                    int rh = Math.round(rw * 0.42f);
                    int a = (int) (235 * (1.0 - kp));
                    blitCentered(g, k == 0 ? TEX_SHOCK : ring, cx, baseY, rw, rh, argb(a, k == 0 ? c1 : c2));
                }
                // Upward burst of theme motifs
                for (int i = 0; i < 7; i++) {
                    double spread = (i - 3) / 3.0;
                    int px = cx + (int) Math.round(spread * unit * 0.44 * p);
                    double arc = 4.0 * p * (1.0 - p);
                    int py = baseY - (int) Math.round(arc * h * 0.58 + (i % 2) * 5);
                    int mSz = Math.max(10, Math.round(unit * 0.22f));
                    int a = (int) (245 * (1.0 - p * 0.7));
                    blitCentered(g, motif, px, py, mSz, mSz, st.custom() ? argb(a, 0xFFFFFF) : argb(a, (i % 2 == 0) ? c1 : c2));
                }
            }
            case "dash" -> {
                double p = (time * 0.92) % 1.0;
                int leftX = x0 + Math.max(18, w / 5);
                int rightX = x1 - Math.max(18, w / 5);
                int portalH = Math.round(unit * (0.58f + 0.16f * (float) Math.sin(time * 5.0)));
                int portalW = Math.round(portalH * 0.42f);
                // Twin warp portals
                blitCentered(g, ring, leftX, cy, portalW, portalH, argb(210, c1));
                blitCentered(g, ring, rightX, cy, portalW, portalH, argb(210, c2));
                // Blazing warp beam streak connecting portals
                int beamW = Math.max(20, rightX - leftX);
                int beamH = Math.round(unit * (0.28f + 0.14f * (float) Math.sin(p * Math.PI)));
                blitCentered(g, TEX_BEAM, cx, cy, beamW, beamH, argb(230, c1));
                blitCentered(g, TEX_BEAM, cx, cy, beamW, Math.max(6, beamH / 2), argb(245, cCore));
                // Zooming motifs along beam
                for (int i = 0; i < 5; i++) {
                    double fp = (p + i * 0.2) % 1.0;
                    int px = leftX + (int) Math.round((rightX - leftX) * fp);
                    int py = cy + (int) Math.round(Math.sin(fp * 9.0 + i) * 9.0);
                    int mSz = Math.max(10, Math.round(unit * 0.24f));
                    blitCentered(g, motif, px, py, mSz, mSz, st.custom() ? argb(245, 0xFFFFFF) : argb(245, (i % 2 == 0) ? c1 : c2));
                }
            }
            case "projectile" -> {
                double p = (time * 0.72) % 1.0;
                int headX = x0 + (int) Math.round(w * (0.14 + 0.74 * p));
                int headY = cy - (int) Math.round(Math.sin(p * Math.PI) * h * 0.18);
                // Comet wake ribbon segments trailing behind head
                for (int i = 1; i <= 8; i++) {
                    double tp = Math.max(0.0, p - i * 0.045);
                    int tx = x0 + (int) Math.round(w * (0.14 + 0.74 * tp));
                    int ty = cy - (int) Math.round(Math.sin(tp * Math.PI) * h * 0.18);
                    int segSz = Math.max(8, Math.round(unit * (0.34f - i * 0.03f)));
                    int a = Math.max(25, 225 - i * 24);
                    blitCentered(g, TEX_GLOW, tx, ty, segSz, segSz, argb(a, (i % 2 == 0) ? c1 : c2));
                    if (i % 2 == 0) {
                        int ms = Math.max(9, Math.round(unit * 0.19f));
                        blitCentered(g, motif, tx, ty + ((i % 4 == 0) ? -5 : 5), ms, ms,
                                st.custom() ? argb(a, 0xFFFFFF) : argb(a, c2));
                    }
                }
                // Bright comet core
                int headSz = Math.round(unit * 0.40f);
                blitCentered(g, TEX_GLOW, headX, headY, headSz, headSz, argb(235, c1));
                blitCentered(g, TEX_SPARK, headX, headY, Math.round(headSz * 0.85f), Math.round(headSz * 0.85f), argb(255, cCore));
                blitCentered(g, motif, headX, headY, Math.round(headSz * 0.62f), Math.round(headSz * 0.62f), motifTint);
            }
            default -> { // ambient
                // Breathing central magic circle + floating orbiting motifs
                int rSz = Math.round(unit * (0.76f + 0.08f * (float) Math.sin(time * 2.2)));
                blitCentered(g, ring, cx, cy, rSz, rSz, argb(180, c2));
                blitCentered(g, TEX_SIGIL, cx, cy, Math.round(rSz * 1.08f), Math.round(rSz * 1.08f), argb(120, c1));
                for (int i = 0; i < 6; i++) {
                    double ang = time * 1.15 + i * (Math.PI * 2.0 / 6.0);
                    int px = cx + (int) Math.round(Math.cos(ang) * unit * 0.36);
                    int py = cy + (int) Math.round(Math.sin(ang) * unit * 0.28 + Math.sin(time * 2.5 + i) * 3.0);
                    int mSz = Math.max(11, Math.round(unit * (0.24f + 0.04f * (float) Math.sin(time * 3.2 + i))));
                    blitCentered(g, motif, px, py, mSz, mSz, st.custom() ? 0xFFFFFFFF : argb(240, (i % 2 == 0) ? c1 : c2));
                }
                blitCentered(g, TEX_SPARK, cx, cy, Math.round(unit * 0.34f), Math.round(unit * 0.34f), argb(220, cCore));
            }
        }
    }

    private static void blitCentered(GuiGraphicsExtractor g, Identifier tex, int cx, int cy, int w, int h, int argbColor) {
        if (w <= 1 || h <= 1 || ((argbColor >>> 24) & 0xFF) <= 2) return;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - w / 2, cy - h / 2, 0f, 0f, w, h, 1, 1, 1, 1, argbColor);
    }

    private static int resolveColor(WardrobeVfxCatalog.VfxStyle st, int overrideColor, double time, boolean secondary) {
        if (overrideColor >= 0) return overrideColor & 0xFFFFFF;
        if ("prism".equals(st.id())) {
            float hue = (float) ((time * 0.24 + (secondary ? 0.33 : 0.0)) % 1.0);
            return Color.HSBtoRGB(hue, 0.78f, 1.0f) & 0xFFFFFF;
        }
        return (secondary ? st.secondaryColor() : st.primaryColor()) & 0xFFFFFF;
    }

    private static int blendWhite(int rgb, float amt) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int nr = Math.min(255, Math.round(r + (255 - r) * amt));
        int ng = Math.min(255, Math.round(g + (255 - g) * amt));
        int nb = Math.min(255, Math.round(b + (255 - b) * amt));
        return (nr << 16) | (ng << 8) | nb;
    }

    private static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x();
        double my = e.y();
        // Allow clicking directly on a card's HDR artwork area to equip & preview immediately
        var rt = WardrobeRuntime.get();
        SkyPilotConfig.VfxSettings cfg = rt.appearance().vfx;
        if (cfg != null) {
            for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
                int x = left + (n % 3) * (cardW + 4);
                int y = top + (n / 3) * (cardH + 5);
                if (mx >= x && mx < x + cardW && my >= y && my < y + cardH - 16) {
                    toggleStyle(cfg, visible.get(page * capacity + n), rt, rt.japanese());
                    return true;
                }
            }
            // Allow clicking any row in the right-hand 7-slot summary to jump straight to that event slot
            int rowH = stageH >= 176 ? 11 : 10;
            if (mx >= sideX + 4 && mx <= sideX + sideWidth - 4 && my >= summaryTop && my < stageY + stageH) {
                int idx = (int) ((my - summaryTop - 1) / rowH);
                if (idx >= 0 && idx < WardrobeVfxCatalog.EVENT_SLOTS.size()) {
                    eventSlot = WardrobeVfxCatalog.EVENT_SLOTS.get(idx);
                    page = 0;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
