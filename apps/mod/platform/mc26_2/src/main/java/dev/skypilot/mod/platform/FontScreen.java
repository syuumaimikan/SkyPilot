package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.FontPack;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.ArrayList;

/** Generates and selects a local resource pack using Minecraft's native font renderer. */
final class FontScreen extends Screen {
    private final Screen parent;
    private String path="",message="";
    private double size=9;
    private boolean busy;
    FontScreen(Screen parent){super(Component.literal("Font / フォント"));this.parent=parent;}
    private String t(String en,String ja){return SkyPilot.get().lang().t(en,ja);}
    @Override protected void init(){
        int x=width/2-150;
        var input=new EditBox(font,x,60,300,20,Component.literal(t("Local TTF path","ローカル TTF のパス")));
        input.setMaxLength(1024);input.setValue(path);input.setHint(Component.literal("C:\\Windows\\Fonts\\segoeui.ttf"));input.setResponder(v->path=v);addRenderableWidget(input);
        addRenderableWidget(ClientButton.create(Component.literal("Segoe UI"),b->{path="C:\\Windows\\Fonts\\segoeui.ttf";rebuildWidgets();}).bounds(x,88,148,20).build());
        addRenderableWidget(ClientButton.create(Component.literal("Arial"),b->{path="C:\\Windows\\Fonts\\arial.ttf";rebuildWidgets();}).bounds(x+152,88,148,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(t("Size: ","サイズ: ")+size),b->{size=size>=12?6:size+1;rebuildWidgets();}).bounds(x,116,148,20).build());
        var apply=ClientButton.create(Component.literal(t("Apply & reload","適用して再読み込み")),b->apply(false)).bounds(x+152,116,148,20).build();apply.active=!busy;addRenderableWidget(apply);
        var reset=ClientButton.create(Component.literal(t("Restore previous fonts","以前のフォントに戻す")),b->apply(true)).bounds(x,144,300,20).build();reset.active=!busy;addRenderableWidget(reset);
        addRenderableWidget(ClientButton.create(Component.literal(t("Back","戻る")),b->onClose()).bounds(x,height-26,300,20).build());
    }
    private void apply(boolean reset){
        try {
            var format=SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
            if(!reset)FontPack.create(minecraft.getResourcePackDirectory(),Path.of(path.strip()),format.major(),format.minor(),size);
            var repo=minecraft.getResourcePackRepository();repo.reload();
            var selected=new ArrayList<>(repo.getSelectedIds());selected.remove("file/"+FontPack.FILE);
            if(!reset){if(repo.getPack("file/"+FontPack.FILE)==null)throw new IllegalStateException("Font pack not discovered");selected.add("file/"+FontPack.FILE);}
            repo.setSelected(selected);minecraft.options.updateResourcePacks(repo);minecraft.options.save();
            busy=true;message=t("Reloading…","再読み込み中…");rebuildWidgets();
            minecraft.reloadResourcePacks().whenComplete((v,error)->minecraft.execute(()->{busy=false;message=error==null?t("Applied. 日本語・symbols keep vanilla fallback.","適用しました。日本語・記号は元の字体にフォールバックします。"):t("Reload failed; restore fonts or inspect log.","読込失敗。元に戻すかログを確認してください。");if(minecraft.gui.screen()==this)rebuildWidgets();}));
        }catch(Exception error){message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();rebuildWidgets();}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        ClientStyle.backdrop(g,width,height);int x=width/2-150;
        g.centeredText(font,t("GAME FONT","ゲーム全体のフォント"),width/2,15,Ui.PURPLE);
        g.text(font,t("Local .ttf path (6–12 px)","手持ちの .ttf のパス（6〜12 px）"),x,44,Ui.GRAY);
        g.textWithWordWrap(font,Component.literal(t("Menus, chat & HUD. Local TTF only; TTC/OTF unsupported. Missing glyphs and other packs are preserved.","メニュー・チャット・HUDに適用。TTFのみ対応。不足文字と他のパックは維持します。")),x,167,300,Ui.GRAY);
        var status=font.split(Component.literal(message),300);
        for(int i=0;i<Math.min(2,status.size());i++)g.text(font,status.get(i),x,height-48+i*10,Ui.WHITE,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
