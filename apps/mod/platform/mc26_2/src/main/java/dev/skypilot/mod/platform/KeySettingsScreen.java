package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.Arrays;
import java.util.List;

/** Rebinds the actual vanilla mappings, persisted in options.txt. Escape cancels capture. */
final class KeySettingsScreen extends Screen {
    private final Screen parent;
    private KeyMapping waiting;
    private int page;
    private static final int PER_PAGE=5;
    KeySettingsScreen(Screen parent){super(Component.literal("Key bindings"));this.parent=parent;}
    private String t(String en,String ja){return SkyPilot.get().lang().t(en,ja);}
    static List<KeyMapping> mappings(){return Arrays.stream(net.minecraft.client.Minecraft.getInstance().options.keyMappings).filter(k->k.getName().startsWith("key.skypilot.")).toList();}
    static List<KeyMapping> conflicts(KeyMapping key){return key.isUnbound()?List.of():Arrays.stream(net.minecraft.client.Minecraft.getInstance().options.keyMappings).filter(other->other!=key&&!other.isUnbound()&&other.same(key)).toList();}
    private void button(String label,int x,int y,int w,Runnable action){var button=ClientButton.create(Component.literal(label),b->action.run()).bounds(x,y,w,18).build();if(label.equals(t("Reset","復元"))||label.equals("×"))button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label.equals(t("Reset","復元"))?t("Restore default key","初期キーに戻す"):t("Remove binding","割り当てを解除"))));addRenderableWidget(button);}
    @Override protected void init(){
        var keys=mappings();int x=width/2-154,y=44;
        for(int i=page*PER_PAGE;i<Math.min(keys.size(),(page+1)*PER_PAGE);i++){
            var key=keys.get(i);int row=y+(i%PER_PAGE)*26;
            button(waiting==key?t("Press a key…","キーを押す…"):key.getTranslatedKeyMessage().getString(),x+156,row,70,()->{waiting=key;rebuildWidgets();});
            button(t("Reset","復元"),x+230,row,48,()->assign(key,key.getDefaultKey()));
            button("×",x+282,row,22,()->assign(key,InputConstants.UNKNOWN));
        }
        button("‹",x,height-48,32,()->{waiting=null;page=Math.max(0,page-1);rebuildWidgets();});
        button("›",x+36,height-48,32,()->{waiting=null;page=Math.min((keys.size()-1)/PER_PAGE,page+1);rebuildWidgets();});
        button(t("Minecraft controls…","Minecraftの操作設定…"),x+104,height-48,200,()->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options)));
        button(t("Reset this page","このページをリセット"),x,height-26,148,()->{for(int i=page*PER_PAGE;i<Math.min(keys.size(),(page+1)*PER_PAGE);i++)keys.get(i).setKey(keys.get(i).getDefaultKey());waiting=null;persist();});
        button(t("Back","戻る"),x+156,height-26,148,this::onClose);
    }
    private void persist(){KeyMapping.resetMapping();minecraft.options.save();rebuildWidgets();}
    private void assign(KeyMapping mapping,InputConstants.Key key){mapping.setDown(false);mapping.setKey(key);waiting=null;persist();}
    @Override public boolean keyPressed(KeyEvent event){if(waiting!=null){if(event.key()==256){waiting=null;rebuildWidgets();}else assign(waiting,InputConstants.getKey(event));return true;}return super.keyPressed(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(waiting!=null){assign(waiting,InputConstants.Type.MOUSE.getOrCreate(event.button()));return true;}return super.mouseClicked(event,doubleClick);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        ClientStyle.backdrop(g,width,height);g.centeredText(font,t("KEY BINDINGS","キー設定"),width/2,12,Ui.PURPLE);
        g.centeredText(font,t("Click a key · Esc cancels · × unbinds","キーをクリック · Escで取消 · ×で解除"),width/2,28,Ui.GRAY);
        var keys=mappings();int x=width/2-154;
        for(int i=page*PER_PAGE;i<Math.min(keys.size(),(page+1)*PER_PAGE);i++){
            var key=keys.get(i);int y=44+(i%PER_PAGE)*26;String name=Component.translatable(key.getName()).getString();g.text(font,font.plainSubstrByWidth(name,150),x,y+4,Ui.WHITE,false);
            var clashes=conflicts(key);if(mx>=x&&mx<x+304&&my>=y&&my<y+26){var tooltip=new java.util.ArrayList<Component>();tooltip.add(Component.literal(name));for(var clash:clashes)tooltip.add(Component.literal(t("Conflict: ","競合: ")+Component.translatable(clash.getName()).getString()));g.setComponentTooltipForNextFrame(font,tooltip,mx,my);}
            if(!clashes.isEmpty()){String hint=t("Conflict: ","競合: ")+Component.translatable(clashes.getFirst().getName()).getString()+(clashes.size()>1?" +"+(clashes.size()-1):"");g.text(font,font.plainSubstrByWidth(hint,304),x,y+19,0xFFE9AE70,false);}
        }
        g.text(font,(page+1)+" / "+((keys.size()+PER_PAGE-1)/PER_PAGE),x+76,height-43,Ui.GRAY,false);super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){waiting=null;minecraft.gui.setScreen(parent);}
}
