package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.CustomCosmetics;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * 6-slot 3D Wardrobe browser & live animated fitting studio supporting all 168+ bundled
 * {@code .bbmodel} cosmetics plus user-imported custom Blockbench models.
 */
final class AppearanceScreen extends Screen {
    private final Screen parent;
    private String category = "all", query = "", statusMsg = "";
    private int page;
    private boolean searching;
    private float rotation = 155, zoom = 1;
    private EditBox search;
    private int left, top, collectionWidth, studioX, studioWidth, cardW, cardH, rows, capacity;
    private List<CosmeticCatalog.Item> visible = List.of();

    AppearanceScreen(Screen parent) {
        super(Component.literal("Wardrobe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var sp = SkyPilot.get();
        var l = sp.lang();
        boolean ja = l.t("en", "ja").equals("ja");
        left = 12;
        top = 74;
        studioWidth = Math.max(122, Math.min(196, width / 3));
        studioX = width - studioWidth - 12;
        collectionWidth = studioX - left - 12;
        cardW = (collectionWidth - 8) / 3;
        rows = Math.max(1, Math.min(3, (height - top - 42) / 52));
        capacity = rows * 3;
        cardH = Math.max(48, Math.min(76, (height - top - 48 - (rows - 1) * 4) / rows));

        search = new EditBox(font, left, 34, collectionWidth, 16, Component.literal(l.t("Search collection", "コレクション検索")));
        search.setMaxLength(60);
        search.setHint(Component.literal(l.t("Search 168+ 3D cosmetics…", "168種以上の3Dコスメを検索…")));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            searching = true;
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(search);

        // 8 category tabs: All, Cloaks, Wings, Head, Auras, Pets, Companions, Favorites
        String[] ids = {"all", "cape", "back", "hat", "aura", "pet", "companion", "favorites"};
        String[] en = {"All", "Cloak", "Wing", "Head", "Aura", "Pet", "Comp", "★"};
        String[] jaL = {"全て", "マント", "翼", "頭", "オーラ", "ペット", "相棒", "★"};
        int tabW = Math.max(20, (collectionWidth - (ids.length - 1) * 2) / ids.length);
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            String label = (category.equals(id) ? "•" : "") + (ja ? jaL[i] : en[i]);
            addRenderableWidget(ClientButton.create(Component.literal(label), b -> {
                category = id;
                page = 0;
                rebuildWidgets();
            }).bounds(left + i * (tabW + 2), 54, tabW, 15).build());
        }

        String q = query.toLowerCase(Locale.ROOT).strip();
        visible = CosmeticCatalog.all().stream()
                .filter(i -> ("all".equals(category)
                        || i.slot().equals(category)
                        || ("favorites".equals(category) && sp.config().appearance.favorites.contains(i.id())))
                        && (i.name() + " " + i.ja() + " " + i.slot() + " " + i.rarity()).toLowerCase(Locale.ROOT).contains(q))
                .toList();
        page = Math.min(page, Math.max(0, (visible.size() - 1) / capacity));

        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            var item = visible.get(page * capacity + n);
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 4);
            boolean selected = item.equipped(sp.config().appearance);
            var button = ClientButton.create(Component.literal((selected ? "✓ " : "") + item.label(ja)), b -> {
                var a = sp.config().appearance;
                if (item.equipped(a)) item.unequip(a);
                else item.equip(a);
                sp.saveConfig();
                rebuildWidgets();
            }).bounds(x, y + cardH - 15, cardW - 17, 15).tooltip(Tooltip.create(Component.literal(item.detail(ja)))).build();
            addRenderableWidget(button);
            addRenderableWidget(ClientButton.create(Component.literal(sp.config().appearance.favorites.contains(item.id()) ? "★" : "☆"), b -> {
                WardrobeTools.favorite(sp.config().appearance, item.id());
                sp.saveConfig();
                rebuildWidgets();
            }).bounds(x + cardW - 16, y + cardH - 15, 16, 15).build());
        }

        int footer = height - 26;
        var previous = ClientButton.create(Component.literal("‹"), b -> { page--; rebuildWidgets(); }).bounds(left, footer, 22, 18).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        var next = ClientButton.create(Component.literal("›"), b -> { page++; rebuildWidgets(); }).bounds(left + 26, footer, 22, 18).build();
        next.active = (page + 1) * capacity < visible.size();
        addRenderableWidget(next);

        // Custom .bbmodel controls in collection footer
        int customX = left + 106;
        int avail = Math.max(90, collectionWidth - 106);
        int cbW = Math.min(72, (avail - 6) / 3);
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Folder", "フォルダ")), b -> {
            try {
                var dir = CustomCosmetics.ensureDirectory(sp.configDir());
                Util.getPlatform().openPath(dir);
                statusMsg = l.t("Opened cosmetics folder", "フォルダを開きました");
            } catch (Exception e) {
                statusMsg = e.getMessage();
            }
        }).bounds(customX, footer, cbW, 18).tooltip(Tooltip.create(Component.literal(l.t("Open config/skypilot/cosmetics/ to drop .bbmodel or .png files", "自作 .bbmodel / .png を置くフォルダを開く")))).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Reload", "再読込")), b -> {
            var list = CustomCosmetics.reload(sp.configDir());
            CosmeticRigRenderer.clearCache();
            statusMsg = l.t("Loaded " + list.size() + " custom 3D models", "自作モデル " + list.size() + " 件を読込");
            rebuildWidgets();
        }).bounds(customX + cbW + 3, footer, cbW, 18).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Templates", "雛形出力")), b -> {
            try {
                var tpl = CustomCosmetics.exportTemplates(sp.configDir());
                Util.getPlatform().openPath(tpl);
                statusMsg = l.t("Exported 6 .bbmodel templates!", "6種 .bbmodel テンプレート出力済");
            } catch (Exception e) {
                statusMsg = e.getMessage();
            }
        }).bounds(customX + (cbW + 3) * 2, footer, cbW, 18).tooltip(Tooltip.create(Component.literal(l.t("Export starter .bbmodel files for Blockbench", "Blockbench用の自作テンプレートを書き出す")))).build());

        // Studio controls (right column)
        int half = (studioWidth - 4) / 2;
        int pct = (int) Math.round(sp.config().appearance.wingScale * 100);
        addRenderableWidget(ClientButton.create(Component.literal("W-"), b -> {
            sp.config().appearance.wingScale = Math.max(0.6, Math.round((sp.config().appearance.wingScale - 0.1) * 10.0) / 10.0);
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX, height - 110, 26, 15).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Wing " + pct + "%", "翼 " + pct + "%")), b -> {
            sp.config().appearance.wingScale = 1.0;
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX + 28, height - 110, studioWidth - 56, 15).build());
        addRenderableWidget(ClientButton.create(Component.literal("W+"), b -> {
            sp.config().appearance.wingScale = Math.min(1.5, Math.round((sp.config().appearance.wingScale + 0.1) * 10.0) / 10.0);
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX + studioWidth - 26, height - 110, 26, 15).build());

        addRenderableWidget(ClientButton.create(Component.literal(l.t("1st-Person Pet: ", "一人称ペット: ") + (sp.config().appearance.firstPersonPet ? "ON" : "OFF")), b -> {
            sp.config().appearance.firstPersonPet = !sp.config().appearance.firstPersonPet;
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX, height - 92, studioWidth, 15).tooltip(Tooltip.create(Component.literal(l.t(
                "Show your floating wardrobe pet & companion in first-person view",
                "一人称視点でもワードローブのペット・相棒を表示するか切り替え"
        )))).build());

        addRenderableWidget(ClientButton.create(Component.literal(l.t("Random", "ランダム")), b -> {
            WardrobeTools.randomize(sp.config().appearance, java.util.concurrent.ThreadLocalRandom.current());
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX, height - 74, half, 15).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Clear", "外す")), b -> {
            WardrobeTools.clear(sp.config().appearance);
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX + half + 4, height - 74, half, 15).build());

        int third = (studioWidth - 6) / 3;
        addRenderableWidget(ClientButton.create(Component.literal("3D:" + (sp.config().appearance.skin3d ? "ON" : "OFF")), b -> {
            sp.config().appearance.skin3d = !sp.config().appearance.skin3d;
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX, height - 56, third, 15).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Glow:", "発光:") + (sp.config().appearance.cosmeticGlow ? "ON" : "OFF")), b -> {
            sp.config().appearance.cosmeticGlow = !sp.config().appearance.cosmeticGlow;
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX + third + 3, height - 56, third, 15).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Dark:", "暗:") + (sp.config().appearance.richMenus ? "ON" : "OFF")), b -> {
            sp.config().appearance.richMenus = !sp.config().appearance.richMenus;
            sp.saveConfig();
            rebuildWidgets();
        }).bounds(studioX + (third + 3) * 2, height - 56, third, 15).build());

        addRenderableWidget(ClientButton.create(Component.literal(l.t("Outfits", "コーデ")), b -> minecraft.gui.setScreen(new OutfitScreen(this))).bounds(studioX, footer, half, 18).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Done", "完了")), b -> onClose()).bounds(studioX + half + 4, footer, half, 18).build());
        addRenderableWidget(ClientButton.create(Component.literal("◈"), b -> {
            var a = sp.config().appearance;
            int i = 0;
            while (i < WardrobeTools.PALETTE.length && WardrobeTools.PALETTE[i] != a.color) i++;
            a.color = WardrobeTools.PALETTE[(i + 1) % WardrobeTools.PALETTE.length];
            sp.saveConfig();
        }).bounds(studioX + studioWidth - 24, 38, 18, 14).tooltip(Tooltip.create(Component.literal(l.t("Change palette", "配色を切り替え")))).build());

        if (searching) {
            setFocused(search);
            search.setCursorPosition(query.length());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ClientStyle.backdrop(g, width, height);
        var sp = SkyPilot.get();
        var l = sp.lang();
        double time = System.currentTimeMillis() / 1000.0;

        g.text(font, l.t("YOUR 3D WARDROBE", "3D ワードローブ"), left, 11, Ui.WHITE, false);
        int totalCount = CosmeticCatalog.all().size();
        String sub = totalCount + " " + l.t("3D .bbmodel designs · 6 slots · free", "種の3Dモデル · 6スロット · 無料")
                + (statusMsg.isEmpty() ? "" : "  ·  " + statusMsg);
        g.text(font, sub, left, 22, Ui.GRAY, false);

        ClientStyle.panel(g, studioX, 34, studioWidth, height - 148);
        g.text(font, "STUDIO " + WardrobeTools.equippedCount(sp.config().appearance) + "/6", studioX + 8, 41, Ui.PURPLE, false);
        int y0 = 56, y1 = height - 132;
        if (y1 > y0) {
            float scale = Math.min(studioWidth * .36f, (y1 - y0) * .38f) * zoom;
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
                WardrobeModel.renderStudio(g, sp.config().appearance, scale, -8, rotation, studioX, y0, studioX + studioWidth, y1, time);
            }
        }
        g.centeredText(font, l.t("Drag to rotate", "ドラッグで回転"), studioX + studioWidth / 2, height - 125, Ui.GRAY);

        for (int n = 0; n < capacity && page * capacity + n < visible.size(); n++) {
            var item = visible.get(page * capacity + n);
            int x = left + n % 3 * (cardW + 4), y = top + n / 3 * (cardH + 4);
            ClientStyle.panel(g, x, y, cardW, cardH);
            var sample = new SkyPilotConfig.Appearance();
            sample.cosmeticGlow = sp.config().appearance.cosmeticGlow;
            item.equip(sample);
            float cardRot = switch (item.slot()) {
                case "hat", "pet", "companion" -> 22.0f;
                case "aura" -> 35.0f;
                default -> 155.0f;
            };
            float cardScale = switch (item.slot()) {
                case "pet", "companion", "aura" -> Math.min(cardW * .20f, (cardH - 16) * .33f);
                default -> Math.min(cardW * .24f, (cardH - 16) * .39f);
            };
            WardrobeModel.renderStudio(g, sample, cardScale, -8, cardRot, x, y, x + cardW, y + cardH - 16, time);
            boolean eq = item.equipped(sp.config().appearance);
            String badge = eq ? "✓" : (item.custom() ? "CUSTOM" : item.rarity().substring(0, 1).toUpperCase(Locale.ROOT));
            g.text(font, badge, x + 4, y + 4, eq ? 0xFF55D6D2 : (0xFF000000 | item.rarityColor()), false);
        }

        if (visible.isEmpty()) g.text(font, l.t("No matching designs", "一致するアイテムがありません"), left, top + 14, Ui.GRAY, false);
        g.text(font, (page + 1) + "/" + Math.max(1, (visible.size() + capacity - 1) / capacity), left + 52, height - 20, Ui.GRAY, false);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.x() >= studioX && e.y() > 50 && e.y() < height - 100) {
            rotation += (float) dx * 2;
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= studioX) {
            zoom = Math.max(.6f, Math.min(1.4f, zoom + (float) sy * .08f));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(!SkyPilot.get().config().appearance.richMenus && parent instanceof ClientHomeScreen
                ? new net.minecraft.client.gui.screens.TitleScreen() : parent);
    }
}
