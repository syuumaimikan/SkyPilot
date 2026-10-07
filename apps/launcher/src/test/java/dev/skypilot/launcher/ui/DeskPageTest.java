package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.*;
import dev.skypilot.mod.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DeskPageTest {
    @Test void notebookSavePreservesUnrelatedSettingsAndAllToolsRender(@TempDir Path dir) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            Theme.apply(true);var settings=new LauncherSettings();settings.minecraftDir=dir.resolve("mc").toString();
            var cfg=Instance.config(settings.instance());cfg.perf.menuFps=120;
            try{Instance.saveConfig(settings.instance(),cfg);}catch(Exception e){throw new RuntimeException(e);}
            var pins=new Pins("26.2","0.19.5","0.1.0","unused.jar","","0.161.0+26.2","https://example.com/test","",0);
            var page=new DeskPage(new Ctx(settings,pins,Lang.JA,dir));var tabs=find(page.view(),JTabbedPane.class);assertNotNull(tabs);assertEquals(6,tabs.getTabCount());
            tabs.setSelectedIndex(1);var text=find((java.awt.Container)tabs.getSelectedComponent(),JTextArea.class);text.setText("次の目標\n自分のメモ");click((java.awt.Container)tabs.getSelectedComponent(),"メモ保存");assertEquals(text.getText(),Instance.config(settings.instance()).deskNotes);assertEquals(120,Instance.config(settings.instance()).perf.menuFps);
            tabs.setSelectedIndex(2);click((java.awt.Container)tabs.getSelectedComponent(),"計算");assertTrue(hasText((java.awt.Container)tabs.getSelectedComponent(),"4,700,000.0000"));
            var full=Instance.config(settings.instance());for(int i=0;i<30;i++)full.todos.add(new SkyPilotConfig.Todo("Task "+i));
            try{Instance.saveConfig(settings.instance(),full);}catch(Exception e){throw new RuntimeException(e);}
            tabs.setSelectedIndex(4);var task=find((java.awt.Container)tabs.getSelectedComponent(),JTextField.class);task.setText("Keep this unsaved task");click((java.awt.Container)tabs.getSelectedComponent(),"追加");assertEquals("Keep this unsaved task",task.getText());assertEquals(30,Instance.config(settings.instance()).todos.size());
            try{Path out=Path.of("build/launcher-screens");Files.createDirectories(out);for(int i=0;i<tabs.getTabCount();i++){tabs.setSelectedIndex(i);var image=LauncherFrame.paint(page.view(),980,680);Path file=out.resolve("desk-ja-"+i+".png");javax.imageio.ImageIO.write(image,"png",file.toFile());assertTrue(Files.size(file)>8000);}}catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static boolean hasText(java.awt.Container root,String text){for(var child:root.getComponents()){if(child instanceof JLabel label&&text.equals(label.getText()))return true;if(child instanceof java.awt.Container c&&hasText(c,text))return true;}return false;}
    private static <T> T find(java.awt.Container root,Class<T> type){for(var child:root.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof java.awt.Container c){T found=find(c,type);if(found!=null)return found;}}return null;}
    private static void click(java.awt.Container root,String text){for(var child:root.getComponents()){if(child instanceof JButton b&&text.equals(b.getText()))b.doClick();if(child instanceof java.awt.Container c)click(c,text);}}
}
