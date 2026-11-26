package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.GameWatcher;
import dev.skypilot.launcher.Instance;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.CustomCosmetics;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeConfigStore;
import dev.skypilot.mod.core.WardrobeSearch;
import dev.skypilot.mod.core.WardrobeTools;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Searchable 6-slot 3D {@code .bbmodel} collection browser, live animated fitting studio,
 * wing scale & glow controls, and custom Blockbench 3D model importer/template exporter.
 */
final class WardrobePage {
    private final Ctx c;
    private final JPanel root = Ui.page(), grid = new JPanel(new GridLayout(0, 3, 12, 12));
    private final JTextField search = new JTextField();
    private final JLabel notice = Ui.muted(""), count = Ui.muted(""), equipped = Ui.muted(""), scaleLabel = Ui.muted("");
    private final JCheckBox menus = new JCheckBox(), skin3d = new JCheckBox(), glow = new JCheckBox(), particles = new JCheckBox(), firstPersonPet = new JCheckBox();
    private final JSlider wingScale = new JSlider(60, 150, 100);
    private final JButton save;
    private final ArrayList<JButton> cards = new ArrayList<>();
    private SkyPilotConfig.Appearance draft = new SkyPilotConfig.Appearance();
    private final AvatarPreview preview = new AvatarPreview(() -> draft, true);
    private String filter = "all";
    private String rarityFilter = "all";
    private WardrobeSearch.SortOrder sortOrder = WardrobeSearch.SortOrder.DEFAULT;
    private int presetIdx;
    private boolean favoritesOnly;
    private boolean customOnly;
    private boolean updatingControls;
    private final JComboBox<String> outfits = new JComboBox<>();

    WardrobePage(Ctx c) {
        this.c = c;
        save = Widgets.button(c.t("Save style", "スタイルを保存"), "check");
        save.addActionListener(e -> save());
        root.add(Ui.header(
                c.t("Your 3D wardrobe", "3D ワードローブ"),
                c.t("300+ HD .bbmodel designs across 6 slots · standalone mod · multiplayer sync · always free.",
                        "300種以上の高精細3D(.bbmodel)・独立MOD・マルチ共有・位置/傾き調整対応。すべて無料。"),
                save), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(20, 0));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));

        JPanel collection = new JPanel(new BorderLayout(0, 12));
        collection.setOpaque(false);

        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        toolbar.setOpaque(false);
        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText", c.t("Search 300+ (e.g. sakura wing, cute, mythic…)", "300種+検索（例: 桜 翼 / うーぱー / かわいい…）"));
        search.getAccessibleContext().setAccessibleName(c.t("Search collection", "コレクション検索"));
        search.setPreferredSize(new Dimension(200, 36));
        searchRow.add(search, BorderLayout.CENTER);

        JPanel searchExtras = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchExtras.setOpaque(false);
        JButton clearSearch = new JButton("×");
        clearSearch.setToolTipText(c.t("Clear search", "検索をクリア"));
        clearSearch.addActionListener(e -> { search.setText(""); rarityFilter = "all"; sortOrder = WardrobeSearch.SortOrder.DEFAULT; rebuild(); });
        searchExtras.add(clearSearch);

        JButton rarityBtn = new JButton(WardrobeSearch.rarityLabel(rarityFilter, ja()));
        rarityBtn.setToolTipText(c.t("Filter by rarity", "レア度フィルタ"));
        rarityBtn.addActionListener(e -> {
            rarityFilter = WardrobeSearch.nextRarity(rarityFilter);
            rarityBtn.setText(WardrobeSearch.rarityLabel(rarityFilter, ja()));
            rebuild();
        });
        searchExtras.add(rarityBtn);

        JButton sortBtn = new JButton(sortOrder.label(ja()));
        sortBtn.setToolTipText(c.t("Change sort order", "並び替え順を変更"));
        sortBtn.addActionListener(e -> {
            sortOrder = sortOrder.next();
            sortBtn.setText(sortOrder.label(ja()));
            rebuild();
        });
        searchExtras.add(sortBtn);
        searchRow.add(searchExtras, BorderLayout.EAST);
        toolbar.add(searchRow, BorderLayout.NORTH);

        // 7 Category tabs: All + 6 cosmetic slots
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        tabs.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        String[] ids = {"all", "cape", "back", "hat", "aura", "pet", "companion"};
        String[] en = {"All", "Cloaks", "Wings", "Head", "Auras", "Pets", "Companions"};
        String[] jaL = {"すべて", "クローク", "ウィング", "ヘッド", "オーラ", "ペット", "コンパニオン"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            JToggleButton tab = new JToggleButton(c.t(en[i], jaL[i]));
            tab.setSelected(i == 0);
            group.add(tab);
            tabs.add(tab);
            tab.addActionListener(e -> { filter = id; rebuild(); });
        }
        toolbar.add(tabs, BorderLayout.CENTER);

        // Quick filters + Custom .bbmodel creation & import bar (compact so all controls fit cleanly on 1 row in EN & JA)
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        quick.setOpaque(false);
        JToggleButton favorites = new JToggleButton(c.t("★ Favs", "★ お気に入り"));
        favorites.setToolTipText(c.t("Show favorite cosmetics", "お気に入りのみ表示"));
        favorites.addActionListener(e -> { favoritesOnly = favorites.isSelected(); rebuild(); });
        quick.add(favorites);
        JToggleButton customTab = new JToggleButton(c.t("✦ Custom", "✦ 自作"));
        customTab.setToolTipText(c.t("Show user-imported custom 3D models", "ユーザーが読み込んだ自作3Dモデルのみ表示"));
        customTab.addActionListener(e -> { customOnly = customTab.isSelected(); rebuild(); });
        quick.add(customTab);
        JButton importBtn = Widgets.button(c.t("Import 3D", "モデル読込"), "folder");
        importBtn.setToolTipText(c.t("Import a Blockbench .bbmodel 3D file or custom PNG cloak", "Blockbenchの .bbmodel や自作PNGクロークを読み込み"));
        importBtn.addActionListener(e -> importCustomModel());
        quick.add(importBtn);
        JButton templatesBtn = Widgets.button(c.t("Templates", "雛形出力"), "image");
        templatesBtn.setToolTipText(c.t("Export 6 ready-to-edit .bbmodel starter templates for Blockbench", "Blockbenchですぐ編集できる6スロット雛形(.bbmodel)を書き出し"));
        templatesBtn.addActionListener(e -> exportStarterTemplates());
        quick.add(templatesBtn);
        JButton reloadBtn = Widgets.button(c.t("Reload", "再読込"), "refresh");
        reloadBtn.setToolTipText(c.t("Hot-reload custom .bbmodel files from cosmetics folder", "cosmeticsフォルダから自作.bbmodelを再読み込み"));
        reloadBtn.addActionListener(e -> reloadCustomModels());
        quick.add(reloadBtn);
        JButton random = Widgets.button(c.t("Random", "ランダム"), "refresh");
        random.setToolTipText(c.t("Equip a random 6-slot outfit", "6スロットのランダムコーデを試着"));
        random.addActionListener(e -> { WardrobeTools.randomize(draft, java.util.concurrent.ThreadLocalRandom.current()); changed(); });
        quick.add(random);

        JPanel below = new JPanel(new BorderLayout(0, 6));
        below.setOpaque(false);
        below.add(quick, BorderLayout.NORTH);
        below.add(count, BorderLayout.SOUTH);
        toolbar.add(below, BorderLayout.SOUTH);
        collection.add(toolbar, BorderLayout.NORTH);

        grid.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        collection.add(scroll, BorderLayout.CENTER);
        body.add(collection, BorderLayout.CENTER);

        // Right Studio Column
        JPanel studio = new JPanel(new BorderLayout(0, 10));
        studio.setOpaque(false);
        studio.setPreferredSize(new Dimension(330, 500));
        preview.setPreferredSize(new Dimension(330, 310));
        studio.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel angles = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        angles.setOpaque(false);
        JButton front = Widgets.button(c.t("Front", "正面"), "user");
        JButton back = Widgets.button(c.t("Back", "背面"), "user");
        JButton reset = Widgets.button(c.t("Clear outfit", "装備を外す"), "reset");
        JButton adjustBtn = Widgets.button(c.t("Adjust", "位置調整"), "settings");
        adjustBtn.setToolTipText(c.t("Adjust X/Y/Z position, scale & tilt per slot", "スロットごとの位置・大きさ・傾きを調整"));
        adjustBtn.addActionListener(e -> openTransformDialog());
        front.addActionListener(e -> preview.front());
        back.addActionListener(e -> preview.back());
        reset.addActionListener(e -> { WardrobeTools.clear(draft); changed(); });
        angles.add(front);
        angles.add(back);
        angles.add(adjustBtn);
        angles.add(reset);
        controls.add(angles);
        controls.add(Box.createVerticalStrut(6));

        // Scalable wing size slider
        JPanel scaleRow = new JPanel(new BorderLayout(8, 0));
        scaleRow.setOpaque(false);
        wingScale.setOpaque(false);
        wingScale.addChangeListener(e -> {
            if (updatingControls) return;
            draft.wingScale = wingScale.getValue() / 100.0;
            scaleLabel.setText(c.t("Wing scale: ", "翼サイズ: ") + wingScale.getValue() + "%");
            preview.repaint();
            notice.setText(c.t("Unsaved changes · save to apply", "未保存の変更 · 保存すると反映"));
        });
        scaleRow.add(scaleLabel, BorderLayout.WEST);
        scaleRow.add(wingScale, BorderLayout.CENTER);
        controls.add(scaleRow);
        controls.add(Box.createVerticalStrut(6));

        JPanel looks = new JPanel(new BorderLayout(6, 0));
        looks.setOpaque(false);
        outfits.setPreferredSize(new Dimension(140, 30));
        looks.add(outfits, BorderLayout.CENTER);
        JButton load = Widgets.button(c.t("Wear", "着る"), "check");
        load.addActionListener(e -> {
            String name = (String) outfits.getSelectedItem();
            draft.outfits.stream().filter(o -> o.name.equals(name)).findFirst().ifPresent(o -> {
                WardrobeTools.apply(o, draft);
                changed();
            });
        });
        JPanel outfitActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        outfitActions.setOpaque(false);
        outfitActions.add(load);
        JButton remove = new JButton("×");
        remove.setToolTipText(c.t("Remove selected saved outfit", "選択した保存コーデを削除"));
        remove.addActionListener(e -> {
            String name = (String) outfits.getSelectedItem();
            draft.outfits.removeIf(o -> o.name.equals(name));
            refreshOutfits();
            changed();
        });
        outfitActions.add(remove);
        looks.add(outfitActions, BorderLayout.EAST);
        controls.add(looks);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        actions.setOpaque(false);
        JButton remember = Widgets.button(c.t("Save outfit", "コーデ保存"), "check");
        remember.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(root, c.t("Outfit name (up to 24 characters)", "コーデ名（24文字まで）"));
            if (name != null) {
                try {
                    WardrobeTools.remember(draft, name);
                    refreshOutfits();
                    changed();
                } catch (IllegalArgumentException ex) {
                    c.fail(c.t("Invalid name", "名前を確認してください"), ex);
                }
            }
        });
        actions.add(remember);
        JButton color = Widgets.button(c.t("Palette", "配色"), "image");
        color.addActionListener(e -> {
            int i = 0;
            while (i < WardrobeTools.PALETTE.length && WardrobeTools.PALETTE[i] != draft.color) i++;
            draft.color = WardrobeTools.PALETTE[(i + 1) % WardrobeTools.PALETTE.length];
            changed();
        });
        actions.add(color);
        JButton themeBtn = Widgets.button(c.t("✦ Theme", "✦ テーマ"), "refresh");
        themeBtn.setToolTipText(c.t("Try 6 curated 6-slot theme outfits", "厳選6種のフルコーデテーマをワンクリック試着"));
        themeBtn.addActionListener(e -> {
            var preset = WardrobeTools.PRESETS.get(presetIdx % WardrobeTools.PRESETS.size());
            presetIdx = (presetIdx + 1) % WardrobeTools.PRESETS.size();
            preset.apply(draft);
            changed();
            notice.setText("✦ " + preset.label(ja()) + " · " + c.t("save to apply", "保存すると反映"));
        });
        actions.add(themeBtn);
        JButton export = Widgets.button(c.t("Save PNG", "画像保存"), "image");
        export.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("skypilot-outfit.png"));
            if (chooser.showSaveDialog(root) == JFileChooser.APPROVE_OPTION) {
                try {
                    var image = LauncherFrame.paint(preview, 640, 720);
                    javax.imageio.ImageIO.write(image, "png", chooser.getSelectedFile());
                    c.ok(c.t("Preview saved", "プレビューを保存しました"), chooser.getSelectedFile().getName());
                } catch (Exception ex) {
                    c.fail(c.t("Could not save", "保存できませんでした"), ex);
                }
            }
        });
        actions.add(export);
        controls.add(actions);
        controls.add(Box.createVerticalStrut(4));
        controls.add(equipped);
        controls.add(Box.createVerticalStrut(8));

        firstPersonPet.setText(c.t("Show pet in 1st-person", "一人称視点でペットを表示"));
        particles.setText(c.t("Ambient cosmetic particles", "コスメ演出パーティクル"));
        glow.setText(c.t("Emissive glow effects", "エミッシブ発光エフェクト"));
        skin3d.setText(c.t("Extruded 3D skin pixels", "立体的な3Dスキン"));
        menus.setText(c.t("Rich dark game menus", "リッチなダークメニュー"));
        for (JCheckBox b : java.util.List.of(firstPersonPet, particles, glow, skin3d, menus)) {
            b.setOpaque(false);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            controls.add(b);
            b.addActionListener(e -> {
                draft.firstPersonPet = firstPersonPet.isSelected();
                draft.cosmeticParticles = particles.isSelected();
                draft.cosmeticGlow = glow.isSelected();
                draft.skin3d = skin3d.isSelected();
                draft.richMenus = menus.isSelected();
                changed();
            });
        }
        JButton fineSettingsBtn = Widgets.button(c.t("1st-Person / Multiplayer Settings…", "一人称・マルチ共有詳細設定…"), "settings");
        fineSettingsBtn.addActionListener(e -> openFineSettingsDialog());
        controls.add(fineSettingsBtn);
        controls.add(Box.createVerticalStrut(6));
        controls.add(Ui.muted(c.t("Standalone Wardrobe mod · 300+ .bbmodel · multiplayer sync", "独立ワードローブMOD · 300種+ .bbmodel · マルチ共有対応")));
        controls.add(Box.createVerticalStrut(4));
        controls.add(notice);
        for (var component : controls.getComponents()) {
            if (component instanceof JComponent child) child.setAlignmentX(0);
        }
        studio.add(controls, BorderLayout.SOUTH);
        body.add(studio, BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { rebuild(); }
            public void removeUpdate(DocumentEvent e) { rebuild(); }
            public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        reload();
    }

    private boolean ja() { return c.t("en", "ja").equals("ja"); }

    private Path configDir() {
        return Instance.configDir(c.settings.instance());
    }

    private void importCustomModel() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(c.t("Select Blockbench .bbmodel, .json, or PNG cloak", "Blockbench .bbmodel / .json / PNGクロークを選択"));
        chooser.setFileFilter(new FileNameExtensionFilter("3D Models & Cloaks (*.bbmodel, *.json, *.png)", "bbmodel", "json", "png"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        File chosen = chooser.getSelectedFile();
        if (chosen == null) return;

        String[] slotIds = {"back", "cape", "hat", "aura", "pet", "companion"};
        String[] slotLabels = {
                c.t("Wings & Backwear (back)", "ウィング・背面 (back)"),
                c.t("Cloak (cape)", "クローク (cape)"),
                c.t("Headwear (hat)", "ヘッドウェア (hat)"),
                c.t("Aura (aura)", "オーラ (aura)"),
                c.t("Floating Pet (pet)", "浮遊ペット (pet)"),
                c.t("Ground Companion (companion)", "地上コンパニオン (companion)")
        };
        int defIdx = chosen.getName().toLowerCase(Locale.ROOT).endsWith(".png") ? 1 : 0;
        Object pick = JOptionPane.showInputDialog(
                root,
                c.t("Choose cosmetic slot for " + chosen.getName(), chosen.getName() + " の装備スロットを選択"),
                c.t("Import 3D Cosmetic", "自作3Dモデル読込"),
                JOptionPane.PLAIN_MESSAGE, null, slotLabels, slotLabels[defIdx]
        );
        if (pick == null) return;
        int idx = 0;
        for (int i = 0; i < slotLabels.length; i++) if (slotLabels[i].equals(pick)) idx = i;

        String stem = chosen.getName().replaceAll("\\.[^.]+$", "");
        String name = JOptionPane.showInputDialog(root, c.t("Display name", "表示名"), stem);
        if (name == null) return;

        try {
            var imported = CustomCosmetics.importModel(configDir(), chosen.toPath(), slotIds[idx], name);
            AvatarPreview.clearCaches();
            if (imported != null) {
                imported.equip(draft);
                changed();
                c.ok(c.t("3D Model Imported", "自作3Dモデルを読み込みました"), imported.label(ja()));
            }
        } catch (Exception ex) {
            c.fail(c.t("Import failed", "読み込みに失敗しました"), ex);
        }
    }

    private void exportStarterTemplates() {
        try {
            Path tpl = CustomCosmetics.exportTemplates(configDir());
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(tpl.toFile());
            }
            c.ok(c.t("Starter templates exported", "Blockbench用テンプレートを書き出しました"), tpl.toString());
        } catch (Exception ex) {
            c.fail(c.t("Export failed", "テンプレート書き出し失敗"), ex);
        }
    }

    private void reloadCustomModels() {
        var list = CustomCosmetics.reload(configDir());
        AvatarPreview.clearCaches();
        rebuild();
        preview.repaint();
        notice.setText(c.t("Reloaded " + list.size() + " custom models", "自作モデル " + list.size() + " 件を再読込"));
    }

    private void rebuild() {
        grid.removeAll();
        cards.clear();
        var filtered = WardrobeSearch.filterAndSort(
                CosmeticCatalog.all(), draft, filter, rarityFilter, favoritesOnly, customOnly, false, search.getText(), sortOrder);
        int found = 0;
        for (var item : filtered) {
            found++;

            JButton card = new JButton();
            card.setLayout(new BorderLayout(0, 4));
            card.setPreferredSize(new Dimension(150, 198));
            card.setBackground(Theme.SURFACE);
            Color borderColor = item.equipped(draft) ? Theme.ACCENT
                    : (item.custom() ? new Color(0x2DD4BF) : Theme.alpha(new Color(item.rarityColor()), 115));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor, item.equipped(draft) ? 2 : 1, true),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            card.setToolTipText(item.detail(ja()));
            card.getAccessibleContext().setAccessibleName(item.label(ja()));
            card.setName(item.id());

            var outfit = new SkyPilotConfig.Appearance();
            outfit.cosmeticGlow = draft.cosmeticGlow;
            item.equip(outfit);
            AvatarPreview mini = new AvatarPreview(() -> outfit, false, item.id(), item.slot());
            mini.setPreferredSize(new Dimension(108, 134));
            card.add(mini, BorderLayout.CENTER);

            JPanel labels = new JPanel(new BorderLayout(0, 4));
            labels.setOpaque(false);
            JLabel title = new JLabel(item.label(ja()));
            title.setForeground(Theme.TEXT);
            title.setFont(Theme.font(Font.BOLD, 12));
            JPanel nameRow = new JPanel(new BorderLayout(2, 0));
            nameRow.setOpaque(false);
            nameRow.add(title, BorderLayout.CENTER);

            JPanel rightIcons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            rightIcons.setOpaque(false);
            if (item.custom()) {
                JButton del = new JButton("×");
                del.setMargin(new Insets(0, 2, 0, 2));
                del.setPreferredSize(new Dimension(20, 20));
                del.setToolTipText(c.t("Delete custom model", "自作モデルを削除"));
                del.addActionListener(e -> {
                    try {
                        if (item.equipped(draft)) item.unequip(draft);
                        CustomCosmetics.deleteCustom(configDir(), item.id());
                        AvatarPreview.clearCaches();
                        changed();
                    } catch (Exception ex) {
                        c.fail(c.t("Delete failed", "削除できませんでした"), ex);
                    }
                });
                rightIcons.add(del);
            }
            JButton star = new JButton(draft.favorites.contains(item.id()) ? "★" : "☆");
            star.setMargin(new Insets(0, 2, 0, 2));
            star.setPreferredSize(new Dimension(22, 20));
            star.setToolTipText(c.t("Toggle favorite", "お気に入りを切り替え"));
            star.getAccessibleContext().setAccessibleName(c.t("Favorite ", "お気に入り: ") + item.label(ja()));
            star.addActionListener(e -> { WardrobeTools.favorite(draft, item.id()); changed(); });
            rightIcons.add(star);
            nameRow.add(rightIcons, BorderLayout.EAST);
            labels.add(nameRow, BorderLayout.NORTH);

            String tag = item.custom() ? c.t("CUSTOM", "自作")
                    : item.rarity().toUpperCase(Locale.ROOT);
            String slotTxt = CosmeticCatalog.slotLabel(item.slot(), ja());
            JLabel status = new JLabel(item.equipped(draft)
                    ? c.t("EQUIPPED  ✓", "装備中  ✓")
                    : tag + " · " + slotTxt + (item.scalable() ? " · ↕" : ""));
            status.setForeground(item.equipped(draft) ? Theme.ACCENT_2 : new Color(item.rarityColor()));
            status.setFont(Theme.font(Font.BOLD, 10));
            labels.add(status, BorderLayout.SOUTH);
            card.add(labels, BorderLayout.SOUTH);

            card.addActionListener(e -> {
                if (item.equipped(draft)) item.unequip(draft);
                else item.equip(draft);
                changed();
            });
            grid.add(card);
            cards.add(card);
        }

        int customCnt = CustomCosmetics.items().size();
        count.setText(c.t(
                found + " 3D designs" + (customCnt > 0 ? " (" + customCnt + " custom)" : "") + " · .bbmodel",
                found + " アイテム" + (customCnt > 0 ? "（自作 " + customCnt + " 件）" : "") + " · すべて無料 .bbmodel"));
        if (found == 0) {
            JLabel empty = Ui.muted(c.t("No matching designs. Try another search.", "一致するアイテムがありません。"));
            grid.add(empty);
        }
        grid.revalidate();
        grid.repaint();
    }

    private void refreshOutfits() {
        String selected = (String) outfits.getSelectedItem();
        outfits.removeAllItems();
        for (var o : draft.outfits) outfits.addItem(o.name);
        if (selected != null) outfits.setSelectedItem(selected);
    }

    private void syncControls() {
        updatingControls = true;
        try {
            firstPersonPet.setSelected(draft.firstPersonPet);
            particles.setSelected(draft.cosmeticParticles);
            glow.setSelected(draft.cosmeticGlow);
            skin3d.setSelected(draft.skin3d);
            menus.setSelected(draft.richMenus);
            int pct = (int) Math.round(Math.max(0.6, Math.min(1.5, draft.wingScale <= 0 ? 1.0 : draft.wingScale)) * 100);
            wingScale.setValue(pct);
            scaleLabel.setText(c.t("Wing scale: ", "翼サイズ: ") + pct + "%");
            equipped.setText(c.t("Outfit", "装備") + ": " + WardrobeTools.equippedCount(draft) + " / 6");
        } finally {
            updatingControls = false;
        }
    }

    private void changed() {
        syncControls();
        preview.repaint();
        notice.setText(c.t("Unsaved changes · save to apply", "未保存の変更 · 保存すると反映"));
        rebuild();
    }

    void reload() {
        CustomCosmetics.reload(configDir());
        AvatarPreview.clearCaches();
        draft = Instance.config(c.settings.instance()).appearance;
        refreshOutfits();
        syncControls();
        boolean running = GameWatcher.check(c.settings.instance(), 0).state() == GameWatcher.State.RUNNING;
        save.setEnabled(!running);
        notice.setText(c.t(
                running ? "Close the game before saving." : "Ready for your next launch.",
                running ? "保存前にゲームを終了してください。" : "次回起動から反映します。"));
        rebuild();
        preview.repaint();
    }

    private void openTransformDialog() {
        String[] slots = {"cape", "back", "hat", "aura", "pet", "companion"};
        String[] labels = new String[slots.length];
        for (int i = 0; i < slots.length; i++) labels[i] = CosmeticCatalog.slotLabel(slots[i], ja());
        JComboBox<String> slotBox = new JComboBox<>(labels);
        JSpinner ox = new JSpinner(new SpinnerNumberModel(0.0, -8.0, 8.0, 0.25));
        JSpinner oy = new JSpinner(new SpinnerNumberModel(0.0, -8.0, 8.0, 0.25));
        JSpinner oz = new JSpinner(new SpinnerNumberModel(0.0, -8.0, 8.0, 0.25));
        JSpinner sc = new JSpinner(new SpinnerNumberModel(1.0, 0.4, 2.0, 0.05));
        JSpinner rx = new JSpinner(new SpinnerNumberModel(0.0, -60.0, 60.0, 5.0));
        JSpinner ry = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 5.0));
        JSpinner rz = new JSpinner(new SpinnerNumberModel(0.0, -60.0, 60.0, 5.0));

        Runnable loadSlot = () -> {
            var tr = draft.transformOf(slots[slotBox.getSelectedIndex()]);
            ox.setValue(tr.offsetX); oy.setValue(tr.offsetY); oz.setValue(tr.offsetZ);
            sc.setValue(tr.scale); rx.setValue(tr.rotX); ry.setValue(tr.rotY); rz.setValue(tr.rotZ);
        };
        loadSlot.run();
        slotBox.addActionListener(e -> loadSlot.run());

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel(c.t("Slot", "スロット"))); form.add(slotBox);
        form.add(new JLabel(c.t("Pos X (px)", "位置 X (px)"))); form.add(ox);
        form.add(new JLabel(c.t("Pos Y (px)", "位置 Y (px)"))); form.add(oy);
        form.add(new JLabel(c.t("Pos Z (px)", "位置 Z (px)"))); form.add(oz);
        form.add(new JLabel(c.t("Scale", "サイズ倍率"))); form.add(sc);
        form.add(new JLabel(c.t("Tilt X (°)", "傾き X (°)"))); form.add(rx);
        form.add(new JLabel(c.t("Tilt Y (°)", "傾き Y (°)"))); form.add(ry);
        form.add(new JLabel(c.t("Tilt Z (°)", "傾き Z (°)"))); form.add(rz);

        int res = JOptionPane.showConfirmDialog(root, form,
                c.t("Adjust Cosmetic Position / Scale / Tilt", "ワードローブ位置・サイズ・傾き調整"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            var tr = draft.editTransform(slots[slotBox.getSelectedIndex()]);
            tr.offsetX = ((Number) ox.getValue()).doubleValue();
            tr.offsetY = ((Number) oy.getValue()).doubleValue();
            tr.offsetZ = ((Number) oz.getValue()).doubleValue();
            tr.scale = ((Number) sc.getValue()).doubleValue();
            tr.rotX = ((Number) rx.getValue()).doubleValue();
            tr.rotY = ((Number) ry.getValue()).doubleValue();
            tr.rotZ = ((Number) rz.getValue()).doubleValue();
            tr.sanitize();
            changed();
        }
    }

    private void openFineSettingsDialog() {
        JCheckBox fpAura = new JCheckBox(c.t("1st-person Aura", "一人称でオーラを表示"), draft.firstPersonAura);
        JCheckBox fpWings = new JCheckBox(c.t("1st-person Wings", "一人称で翼を表示"), draft.firstPersonWings);
        JCheckBox fpCape = new JCheckBox(c.t("1st-person Cloak", "一人称でマントを表示"), draft.firstPersonCape);
        JCheckBox fpHat = new JCheckBox(c.t("1st-person Headwear", "一人称で頭・帽子を表示"), draft.firstPersonHat);
        JCheckBox fpPet = new JCheckBox(c.t("1st-person Floating Pet", "一人称で浮遊ペットを表示"), draft.firstPersonPet);
        JCheckBox fpComp = new JCheckBox(c.t("1st-person Companion", "一人称で地上相棒を表示"), draft.firstPersonCompanion);
        JCheckBox mpShow = new JCheckBox(c.t("Show other players' cosmetics", "他プレイヤーのワードローブを表示"), draft.showOtherPlayers);
        JCheckBox mpShare = new JCheckBox(c.t("Share my wardrobe with others", "自分のワードローブを他プレイヤーへ共有"), draft.shareWithOthers);
        JCheckBox mpPets = new JCheckBox(c.t("Show other players' pets", "他プレイヤーのペット・相棒も表示"), draft.otherPlayerPets);
        JTextField urlField = new JTextField(draft.wardrobeSyncUrl == null ? "http://localhost:8080" : draft.wardrobeSyncUrl, 24);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JCheckBox b : java.util.List.of(fpAura, fpWings, fpCape, fpHat, fpPet, fpComp, mpShow, mpShare, mpPets)) {
            panel.add(b);
        }
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel(c.t("Multiplayer Sync Server URL:", "マルチ同期サーバーURL:")));
        panel.add(urlField);

        int res = JOptionPane.showConfirmDialog(root, panel,
                c.t("1st-Person & Multiplayer Wardrobe Settings", "一人称・マルチプレイ詳細設定"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            draft.firstPersonAura = fpAura.isSelected();
            draft.firstPersonWings = fpWings.isSelected();
            draft.firstPersonCape = fpCape.isSelected();
            draft.firstPersonHat = fpHat.isSelected();
            draft.firstPersonPet = fpPet.isSelected();
            draft.firstPersonCompanion = fpComp.isSelected();
            draft.showOtherPlayers = mpShow.isSelected();
            draft.shareWithOthers = mpShare.isSelected();
            draft.otherPlayerPets = mpPets.isSelected();
            draft.wardrobeSyncUrl = urlField.getText().strip();
            changed();
        }
    }

    private void save() {
        if (GameWatcher.check(c.settings.instance(), 0).state() == GameWatcher.State.RUNNING) {
            reload();
            return;
        }
        var cfg = Instance.config(c.settings.instance());
        cfg.appearance = draft;
        try {
            Instance.saveConfig(c.settings.instance(), cfg);
            new WardrobeConfigStore(configDir()).save(draft);
            notice.setText(c.t("Saved · ready for your next launch", "保存済み · 次回起動から反映"));
            c.ok(c.t("Style saved", "保存しました"), c.t("Ready for your next launch.", "次回起動から反映します。"));
        } catch (Exception e) {
            c.fail(c.t("Could not save", "保存できませんでした"), e);
        }
    }

    JComponent view() { return root; }
}
