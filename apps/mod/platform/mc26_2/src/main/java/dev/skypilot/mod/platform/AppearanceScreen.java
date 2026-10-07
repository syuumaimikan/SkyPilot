package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.WardrobeTools;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;

/** Collection browser, independent outfit slots and a freely rotatable, lit 3D fitting studio. */
final class AppearanceScreen extends Screen {
    private final Screen parent;
    private String category="all",query="";
    private int page;
    private boolean searching;
    private float rotation=155,zoom=1;
    private EditBox search;
    private int left,top,collectionWidth,studioX,studioWidth,cardW,cardH,rows,capacity;
    private List<CosmeticCatalog.Item> visible=List.of();
    AppearanceScreen(Screen parent){super(Component.literal("Wardrobe"));this.parent=parent;}
    @Override protected void init(){
        var sp=SkyPilot.get();var l=sp.lang();
        left=12;top=72;studioWidth=Math.max(108,Math.min(180,width/3));studioX=width-studioWidth-12;collectionWidth=studioX-left-12;
        cardW=(collectionWidth-8)/3;rows=Math.max(1,Math.min(3,(height-top-42)/52));capacity=rows*3;cardH=Math.max(48,Math.min(74,(height-top-48-(rows-1)*4)/rows));
        search=new EditBox(font,left,34,collectionWidth,18,Component.literal(l.t("Search collection","コレクション検索")));search.setMaxLength(60);search.setHint(Component.literal(l.t("Search the collection…","コレクションを検索…")));search.setValue(query);search.setResponder(value->{query=value;searching=true;page=0;rebuildWidgets();});addRenderableWidget(search);
        String[] ids={"all","cape","hat","back","favorites"},en={"All","Capes","Head","Wings","★"},ja={"全て","マント","ヘッド","翼","★"};int tabW=(collectionWidth-12)/5;
        for(int i=0;i<5;i++){String id=ids[i];addRenderableWidget(ClientButton.create(Component.literal((category.equals(id)?"• ":"")+l.t(en[i],ja[i])),b->{category=id;page=0;rebuildWidgets();}).bounds(left+i*(tabW+3),55,tabW,14).build());}
        String q=query.toLowerCase(Locale.ROOT).strip();visible=CosmeticCatalog.ITEMS.stream().filter(i->("all".equals(category)||i.slot().equals(category)||"favorites".equals(category)&&sp.config().appearance.favorites.contains(i.id()))&&(i.name()+" "+i.ja()).toLowerCase(Locale.ROOT).contains(q)).toList();
        page=Math.min(page,Math.max(0,(visible.size()-1)/capacity));
        for(int n=0;n<capacity && page*capacity+n<visible.size();n++){
            var item=visible.get(page*capacity+n);int x=left+n%3*(cardW+4),y=top+n/3*(cardH+4);boolean selected=item.equipped(sp.config().appearance);
            var button=ClientButton.create(Component.literal((selected?"✓ ":"")+item.label(l.t("en","ja").equals("ja"))),b->{var a=sp.config().appearance;if(item.equipped(a)){switch(item.slot()){case "cape"->a.cape="none";case "hat"->a.hat="none";default->a.back="none";}}else item.equip(a);sp.saveConfig();rebuildWidgets();}).bounds(x,y+cardH-15,cardW-17,15).tooltip(Tooltip.create(Component.literal(item.detail(l.t("en","ja").equals("ja"))))).build();addRenderableWidget(button);
            addRenderableWidget(ClientButton.create(Component.literal(sp.config().appearance.favorites.contains(item.id())?"★":"☆"),b->{WardrobeTools.favorite(sp.config().appearance,item.id());sp.saveConfig();rebuildWidgets();}).bounds(x+cardW-16,y+cardH-15,16,15).build());
        }
        int footer=height-28;
        var previous=ClientButton.create(Component.literal("‹"),b->{page--;rebuildWidgets();}).bounds(left,footer,22,18).build();previous.active=page>0;addRenderableWidget(previous);
        var next=ClientButton.create(Component.literal("›"),b->{page++;rebuildWidgets();}).bounds(left+26,footer,22,18).build();next.active=(page+1)*capacity<visible.size();addRenderableWidget(next);
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Random","ランダム")),b->{WardrobeTools.randomize(sp.config().appearance,java.util.concurrent.ThreadLocalRandom.current());sp.saveConfig();rebuildWidgets();}).bounds(studioX,height-74,(studioWidth-4)/2,16).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Clear","外す")),b->{var a=sp.config().appearance;a.cape="none";a.hat="none";a.back="none";a.accessory="none";sp.saveConfig();rebuildWidgets();}).bounds(studioX+(studioWidth+4)/2,height-74,(studioWidth-4)/2,16).build());
        int half=(studioWidth-4)/2;
        addRenderableWidget(ClientButton.create(Component.literal("3D: "+(sp.config().appearance.skin3d?"ON":"OFF")),b->{sp.config().appearance.skin3d=!sp.config().appearance.skin3d;sp.saveConfig();rebuildWidgets();}).bounds(studioX,height-54,half,16).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Dark: ","暗色: ")+(sp.config().appearance.richMenus?"ON":"OFF")),b->{sp.config().appearance.richMenus=!sp.config().appearance.richMenus;sp.saveConfig();rebuildWidgets();}).bounds(studioX+half+4,height-54,half,16).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Outfits","コーデ")),b->minecraft.gui.setScreen(new OutfitScreen(this))).bounds(studioX,footer,half,18).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Done","完了")),b->onClose()).bounds(studioX+half+4,footer,half,18).build());
        addRenderableWidget(ClientButton.create(Component.literal("◈"),b->{var a=sp.config().appearance;int i=0;while(i<WardrobeTools.PALETTE.length&&WardrobeTools.PALETTE[i]!=a.color)i++;a.color=WardrobeTools.PALETTE[(i+1)%WardrobeTools.PALETTE.length];sp.saveConfig();}).bounds(studioX+studioWidth-26,40,18,14).tooltip(Tooltip.create(Component.literal(l.t("Change palette","配色を切り替え")))).build());
        if(searching){setFocused(search);search.setCursorPosition(query.length());}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        ClientStyle.backdrop(g,width,height);var sp=SkyPilot.get();var l=sp.lang();
        g.text(font,l.t("YOUR WARDROBE","ワードローブ"),left,13,Ui.WHITE,false);g.text(font,l.t("13 original designs · always free","13 種類のオリジナル · すべて無料"),left,24,Ui.GRAY,false);
        ClientStyle.panel(g,studioX,34,studioWidth,height-114);
        g.text(font,"STUDIO / 3D",studioX+8,42,Ui.PURPLE,false);
        int y0=58,y1=height-104;
        if(y1>y0) {
            float scale=Math.min(studioWidth*.42f,(y1-y0)*.43f)*zoom;
            if(minecraft.player!=null){
                var state=minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player,delta);
                if(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar){avatar.bodyRot=180+rotation;avatar.yRot=0;avatar.xRot=0;}
                var orientation=new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateX((float)Math.toRadians(-8));
                g.entity(state,scale,new org.joml.Vector3f(0,state.boundingBoxHeight/2,0),orientation,new org.joml.Quaternionf(),studioX,y0,studioX+studioWidth,y1);
            } else g.skin(WardrobeModel.get(sp.config().appearance),WardrobeModel.TEXTURE,scale,-8,rotation,0,studioX,y0,studioX+studioWidth,y1);
        }
        g.centeredText(font,l.t("Drag to rotate","ドラッグで回転"),studioX+studioWidth/2,height-90,Ui.GRAY);
        for(int n=0;n<capacity && page*capacity+n<visible.size();n++) {
            var item=visible.get(page*capacity+n);int x=left+n%3*(cardW+4),y=top+n/3*(cardH+4);ClientStyle.panel(g,x,y,cardW,cardH);
            var a=new SkyPilotConfig.Appearance();item.equip(a);g.skin(WardrobeModel.get(a),WardrobeModel.TEXTURE,Math.min(cardW*.26f,(cardH-16)*.42f),-8,155,0,x,y,x+cardW,y+cardH-16);
            g.text(font,item.equipped(sp.config().appearance)?"✓":"FREE",x+4,y+4,item.equipped(sp.config().appearance)?0xFF55D6D2:Ui.GRAY,false);
        }
        if(visible.isEmpty())g.text(font,l.t("No matching designs","一致するアイテムがありません"),left,top+14,Ui.GRAY,false);
        g.text(font,(page+1)+" / "+Math.max(1,(visible.size()+capacity-1)/capacity),left+56,height-22,Ui.GRAY,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(e.x()>=studioX&&e.y()>50&&e.y()<height-90){rotation+=(float)dx*2;return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(x>=studioX){zoom=Math.max(.7f,Math.min(1.25f,zoom+(float)sy*.08f));return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public void onClose(){minecraft.gui.setScreen(!SkyPilot.get().config().appearance.richMenus && parent instanceof ClientHomeScreen ? new net.minecraft.client.gui.screens.TitleScreen() : parent);}
}
