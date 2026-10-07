package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.GameWatcher;
import dev.skypilot.launcher.Instance;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.WardrobeTools;
import dev.skypilot.mod.core.SkyPilotConfig;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Locale;

/** Searchable, keyboard-accessible collection and a live, independently layered fitting studio. */
final class WardrobePage {
    private final Ctx c;
    private final JPanel root=Ui.page(),grid=new JPanel(new GridLayout(0,3,12,12));
    private final JTextField search=new JTextField();
    private final JLabel notice=Ui.muted(""),count=Ui.muted(""),equipped=Ui.muted("");
    private final JCheckBox menus=new JCheckBox(),skin3d=new JCheckBox();
    private final JButton save;
    private final ArrayList<JButton> cards=new ArrayList<>();
    private SkyPilotConfig.Appearance draft=new SkyPilotConfig.Appearance();
    private final AvatarPreview preview=new AvatarPreview(()->draft,true);
    private String filter="all";
    private boolean favoritesOnly;
    private final JComboBox<String> outfits=new JComboBox<>();
    WardrobePage(Ctx c) {
        this.c=c;
        save=Widgets.button(c.t("Save style","スタイルを保存"),"check");save.addActionListener(e->save());
        root.add(Ui.header(c.t("Your wardrobe","ワードローブ"),c.t("Original designs. Always free. Make them yours.","オリジナルコレクション。すべて無料。自分らしい組み合わせを。"),save),BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(20,0));body.setOpaque(false);body.setBorder(BorderFactory.createEmptyBorder(24,0,0,0));
        JPanel collection=new JPanel(new BorderLayout(0,14));collection.setOpaque(false);
        JPanel toolbar=new JPanel(new BorderLayout(12,12));toolbar.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText",c.t("Search the collection…","コレクションを検索…"));search.getAccessibleContext().setAccessibleName(c.t("Search collection","コレクション検索"));search.setPreferredSize(new Dimension(200,38));toolbar.add(search,BorderLayout.NORTH);
        JPanel tabs=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));tabs.setOpaque(false);ButtonGroup group=new ButtonGroup();
        String[] ids={"all","cape","hat","back"},en={"All","Capes","Headwear","Wings"},ja={"すべて","マント","ヘッド","ウィング"};
        for(int i=0;i<ids.length;i++){final String id=ids[i];JToggleButton tab=new JToggleButton(c.t(en[i],ja[i]));tab.setSelected(i==0);group.add(tab);tabs.add(tab);tab.addActionListener(e->{filter=id;rebuild();});}
        toolbar.add(tabs,BorderLayout.CENTER);
        JPanel quick=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));quick.setOpaque(false);
        JToggleButton favorites=new JToggleButton(c.t("★ Favorites","★ お気に入り"));favorites.addActionListener(e->{favoritesOnly=favorites.isSelected();rebuild();});quick.add(favorites);
        JButton random=Widgets.button(c.t("Surprise me","ランダムコーデ"),"refresh");random.addActionListener(e->{WardrobeTools.randomize(draft,java.util.concurrent.ThreadLocalRandom.current());changed();});quick.add(random);
        JButton revert=Widgets.button(c.t("Revert changes","保存済みに戻す"),"reset");revert.addActionListener(e->reload());quick.add(revert);
        JPanel below=new JPanel(new BorderLayout(0,8));below.setOpaque(false);below.add(quick,BorderLayout.NORTH);below.add(count,BorderLayout.SOUTH);toolbar.add(below,BorderLayout.SOUTH);collection.add(toolbar,BorderLayout.NORTH);
        grid.setOpaque(false);JPanel holder=new JPanel(new BorderLayout());holder.setOpaque(false);holder.add(grid,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(holder);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setUnitIncrement(24);collection.add(scroll,BorderLayout.CENTER);body.add(collection,BorderLayout.CENTER);
        JPanel studio=new JPanel(new BorderLayout(0,12));studio.setOpaque(false);studio.setPreferredSize(new Dimension(320,450));
        preview.setPreferredSize(new Dimension(320,330));studio.add(preview,BorderLayout.CENTER);
        JPanel controls=new JPanel();controls.setOpaque(false);controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));
        JPanel angles=new JPanel(new FlowLayout(FlowLayout.CENTER,8,0));angles.setOpaque(false);
        JButton front=Widgets.button(c.t("Front","正面"),"user"),back=Widgets.button(c.t("Back","背面"),"user"),reset=Widgets.button(c.t("Clear outfit","装備を外す"),"reset");
        front.addActionListener(e->preview.front());back.addActionListener(e->preview.back());reset.addActionListener(e->{draft.cape="none";draft.hat="none";draft.back="none";draft.accessory="none";changed();});angles.add(front);angles.add(back);angles.add(reset);controls.add(angles);controls.add(Box.createVerticalStrut(8));
        JPanel looks=new JPanel(new BorderLayout(6,0));looks.setOpaque(false);outfits.setPreferredSize(new Dimension(140,30));looks.add(outfits,BorderLayout.CENTER);
        JButton load=Widgets.button(c.t("Wear","着る"),"check");load.addActionListener(e->{String name=(String)outfits.getSelectedItem();draft.outfits.stream().filter(o->o.name.equals(name)).findFirst().ifPresent(o->{WardrobeTools.apply(o,draft);changed();});});JPanel outfitActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,0));outfitActions.setOpaque(false);outfitActions.add(load);JButton remove=new JButton("×");remove.setToolTipText(c.t("Remove selected saved outfit","選択した保存コーデを削除"));remove.addActionListener(e->{String name=(String)outfits.getSelectedItem();draft.outfits.removeIf(o->o.name.equals(name));refreshOutfits();changed();});outfitActions.add(remove);looks.add(outfitActions,BorderLayout.EAST);controls.add(looks);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,4,4));actions.setOpaque(false);
        JButton remember=Widgets.button(c.t("Save outfit","コーデ保存"),"check");remember.addActionListener(e->{String name=JOptionPane.showInputDialog(root,c.t("Outfit name (up to 24 characters)","コーデ名（24文字まで）"));if(name!=null)try{WardrobeTools.remember(draft,name);refreshOutfits();changed();}catch(IllegalArgumentException ex){c.fail(c.t("Invalid name","名前を確認してください"),ex);}});actions.add(remember);
        JButton color=Widgets.button(c.t("Palette","配色"),"image");color.addActionListener(e->{int i=0;while(i<WardrobeTools.PALETTE.length&&WardrobeTools.PALETTE[i]!=draft.color)i++;draft.color=WardrobeTools.PALETTE[(i+1)%WardrobeTools.PALETTE.length];changed();});actions.add(color);
        JButton export=Widgets.button(c.t("Save preview","プレビュー保存"),"image");export.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("skypilot-outfit.png"));if(chooser.showSaveDialog(root)==JFileChooser.APPROVE_OPTION)try{var image=LauncherFrame.paint(preview,640,720);javax.imageio.ImageIO.write(image,"png",chooser.getSelectedFile());c.ok(c.t("Preview saved","プレビューを保存しました"),chooser.getSelectedFile().getName());}catch(Exception ex){c.fail(c.t("Could not save","保存できませんでした"),ex);}});controls.add(actions);export.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));controls.add(export);controls.add(Box.createVerticalStrut(8));controls.add(equipped);controls.add(Box.createVerticalStrut(12));
        skin3d.setText(c.t("Extruded 3D skin pixels","立体的な3Dスキン"));menus.setText(c.t("Rich dark game menus","リッチなダークメニュー"));
        for(JCheckBox b:java.util.List.of(skin3d,menus)){b.setOpaque(false);b.setAlignmentX(Component.LEFT_ALIGNMENT);controls.add(b);b.addActionListener(e->{draft.skin3d=skin3d.isSelected();draft.richMenus=menus.isSelected();changed();});}
        controls.add(Box.createVerticalStrut(12));controls.add(Ui.muted(c.t("Studio mannequin · local only","プレビュー用モデル · 自分の画面のみ")));controls.add(Box.createVerticalStrut(6));controls.add(notice);for(var component:controls.getComponents())if(component instanceof JComponent child)child.setAlignmentX(0);studio.add(controls,BorderLayout.SOUTH);body.add(studio,BorderLayout.EAST);root.add(body,BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){rebuild();}public void removeUpdate(DocumentEvent e){rebuild();}public void changedUpdate(DocumentEvent e){rebuild();}});
        reload();
    }
    private boolean ja(){return c.t("en","ja").equals("ja");}
    private void rebuild(){
        grid.removeAll();cards.clear();String query=search.getText().strip().toLowerCase(Locale.ROOT);int found=0;
        for(var item:CosmeticCatalog.ITEMS){
            if(favoritesOnly&&!draft.favorites.contains(item.id()))continue;
            if(!"all".equals(filter)&&!item.slot().equals(filter))continue;
            if(!(item.name()+" "+item.ja()+" "+item.slot()).toLowerCase(Locale.ROOT).contains(query))continue;
            found++;JButton card=new JButton();card.setLayout(new BorderLayout(0,4));card.setPreferredSize(new Dimension(145,190));card.setBackground(Theme.SURFACE);card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(item.equipped(draft)?Theme.ACCENT:Theme.BORDER,1,true),BorderFactory.createEmptyBorder(10,10,10,10)));
            card.setToolTipText(item.detail(ja()));card.getAccessibleContext().setAccessibleName(item.label(ja()));card.setName(item.id());
            var outfit=new SkyPilotConfig.Appearance();item.equip(outfit);AvatarPreview mini=new AvatarPreview(()->outfit,false);mini.setPreferredSize(new Dimension(100,135));card.add(mini,BorderLayout.CENTER);
            JPanel labels=new JPanel(new BorderLayout(0,5));labels.setOpaque(false);JLabel title=new JLabel(item.label(ja()));title.setForeground(Theme.TEXT);title.setFont(Theme.font(Font.BOLD,13));JPanel nameRow=new JPanel(new BorderLayout(2,0));nameRow.setOpaque(false);nameRow.add(title,BorderLayout.CENTER);
            JButton star=new JButton(draft.favorites.contains(item.id())?"★":"☆");star.setMargin(new Insets(0,2,0,2));star.setPreferredSize(new Dimension(24,22));star.setToolTipText(c.t("Toggle favorite","お気に入りを切り替え"));star.getAccessibleContext().setAccessibleName(c.t("Favorite ","お気に入り: ")+item.label(ja()));star.addActionListener(e->{WardrobeTools.favorite(draft,item.id());changed();});nameRow.add(star,BorderLayout.EAST);labels.add(nameRow,BorderLayout.NORTH);
            JLabel status=new JLabel(item.equipped(draft)?c.t("EQUIPPED  ✓","装備中  ✓"):c.t("FREE / "+item.slot().toUpperCase(Locale.ROOT),"無料 / "+switch(item.slot()){case "cape"->"マント";case "hat"->"ヘッド";default->"ウィング";}));status.setForeground(item.equipped(draft)?Theme.ACCENT_2:Theme.MUTED);status.setFont(Theme.font(Font.PLAIN,10));labels.add(status,BorderLayout.SOUTH);card.add(labels,BorderLayout.SOUTH);
            card.addActionListener(e->{if(item.equipped(draft)){switch(item.slot()){case "cape"->draft.cape="none";case "hat"->draft.hat="none";default->draft.back="none";}}else item.equip(draft);changed();});grid.add(card);cards.add(card);
        }
        count.setText(c.t(found+" designs · all included",found+" アイテム · すべて同梱"));if(found==0){JLabel empty=Ui.muted(c.t("No matching designs. Try another search.","一致するアイテムがありません。"));grid.add(empty);}grid.revalidate();grid.repaint();
    }
    private void refreshOutfits(){String selected=(String)outfits.getSelectedItem();outfits.removeAllItems();for(var o:draft.outfits)outfits.addItem(o.name);if(selected!=null)outfits.setSelectedItem(selected);}
    private void changed(){skin3d.setSelected(draft.skin3d);menus.setSelected(draft.richMenus);preview.repaint();equipped.setText(c.t("Outfit","装備")+": "+java.util.stream.Stream.of(draft.cape,draft.hat,draft.back).filter(v->!"none".equals(v)).count()+" / 3");notice.setText(c.t("Unsaved changes · save to apply","未保存の変更 · 保存すると反映"));rebuild();}
    void reload(){draft=Instance.config(c.settings.instance()).appearance;refreshOutfits();skin3d.setSelected(draft.skin3d);menus.setSelected(draft.richMenus);boolean running=GameWatcher.check(c.settings.instance(),0).state()==GameWatcher.State.RUNNING;save.setEnabled(!running);notice.setText(c.t(running?"Close the game before saving.":"Ready for your next launch.",running?"保存前にゲームを終了してください。":"次回起動から反映します。"));equipped.setText(c.t("Outfit","装備")+": "+java.util.stream.Stream.of(draft.cape,draft.hat,draft.back).filter(v->!"none".equals(v)).count()+" / 3");rebuild();preview.repaint();}
    private void save(){
        if(GameWatcher.check(c.settings.instance(),0).state()==GameWatcher.State.RUNNING){reload();return;}
        var cfg=Instance.config(c.settings.instance());cfg.appearance=draft;
        try{Instance.saveConfig(c.settings.instance(),cfg);notice.setText(c.t("Saved · ready for your next launch","保存済み · 次回起動から反映"));c.ok(c.t("Style saved","保存しました"),c.t("Ready for your next launch.","次回起動から反映します。"));}catch(Exception e){c.fail(c.t("Could not save","保存できませんでした"),e);}
    }
    JComponent view(){return root;}
}
