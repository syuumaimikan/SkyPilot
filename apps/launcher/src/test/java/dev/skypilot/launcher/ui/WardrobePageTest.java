package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.LauncherSettings;
import dev.skypilot.launcher.Pins;
import dev.skypilot.mod.core.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WardrobePageTest {
    @Test void savesAppearanceWithoutReplacingOtherSettings(@TempDir Path dir) throws Exception {
        LauncherSettings settings=new LauncherSettings();settings.minecraftDir=dir.resolve("mc").toString();
        var pins=new Pins("26.2","0.19.5","0.1.0","unused.jar","","0.161.0+26.2","https://example.com/test","",0);
        SwingUtilities.invokeAndWait(()->{
            Theme.apply(true);
            WardrobePage page=new WardrobePage(new Ctx(settings,pins,Lang.JA,dir));
            var before=Instance.config(settings.instance());before.perf.menuFps=120;
            try {Instance.saveConfig(settings.instance(),before);}catch(Exception e){throw new RuntimeException(e);}
            equip(page.view(),"aurora");equip(page.view(),"wings");equip(page.view(),"horns");click(page.view(),"スタイルを保存");
            var after=Instance.config(settings.instance());
            assertEquals("aurora",after.appearance.cape);assertEquals("wings",after.appearance.back);assertEquals("horns",after.appearance.hat);assertEquals(120,after.perf.menuFps);

        });
    }
    @Test void searchFiltersWithoutLosingUnsavedOutfit(@TempDir Path dir) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            Theme.apply(true);var settings=new LauncherSettings();settings.minecraftDir=dir.resolve("mc").toString();
            var pins=new Pins("26.2","0.19.5","0.1.0","unused.jar","","0.161.0+26.2","https://example.com/test","",0);
            var page=new WardrobePage(new Ctx(settings,pins,Lang.JA,dir));equip(page.view(),"aurora");
            var search=findSearch(page.view());assertNotNull(search);search.setText("EcLiPsE");
            assertEquals(java.util.List.of("eclipse"),itemIds(page.view()));search.setText("no matching design");assertTrue(itemIds(page.view()).isEmpty());
            search.setText("");assertEquals(13,itemIds(page.view()).size());click(page.view(),"スタイルを保存");assertEquals("aurora",Instance.config(settings.instance()).appearance.cape);
            click(page.view(),"装備を外す");click(page.view(),"スタイルを保存");assertEquals("none",Instance.config(settings.instance()).appearance.cape);
            try {
                var image=LauncherFrame.paint(page.view(),760,620);var out=dir.resolve("wardrobe-compact.png");javax.imageio.ImageIO.write(image,"png",out.toFile());assertTrue(Files.size(out)>8000);
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static JTextField findSearch(java.awt.Container root){
        for(var child:root.getComponents()) {if(child instanceof JTextField text)return text;if(child instanceof java.awt.Container c){var found=findSearch(c);if(found!=null)return found;}}return null;
    }
    private static java.util.List<String> itemIds(java.awt.Container root){
        var ids=new java.util.ArrayList<String>();for(var child:root.getComponents()){if(child instanceof JButton b&&b.getName()!=null)ids.add(b.getName());if(child instanceof java.awt.Container c)ids.addAll(itemIds(c));}return ids;
    }
    private static void equip(java.awt.Container root,String id){
        for(var child:root.getComponents()){
            if(child instanceof JButton b && id.equals(b.getName())){b.doClick();return;}
            if(child instanceof java.awt.Container c)equip(c,id);
        }
    }
    private static void click(java.awt.Container root,String text){
        for(var child:root.getComponents()){
            if(child instanceof JButton b && text.equals(b.getText()))b.doClick();
            if(child instanceof java.awt.Container c)click(c,text);
        }
    }
}
