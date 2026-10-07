package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.WardrobeTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Named looks shared with the launcher. Saving never silently discards an existing look. */
final class OutfitScreen extends Screen {
    private final Screen parent;
    private String name="",notice="";
    OutfitScreen(Screen parent){super(Component.literal("Saved outfits"));this.parent=parent;}
    @Override protected void init(){var sp=SkyPilot.get();var l=sp.lang();int x=width/2-140;
        EditBox input=new EditBox(font,x,34,174,18,Component.literal(l.t("Outfit name","コーデ名")));input.setMaxLength(24);input.setValue(name);input.setHint(Component.literal(l.t("Name this outfit…","コーデに名前を…")));input.setResponder(value->name=value);addRenderableWidget(input);
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Save outfit","コーデ保存")),b->{try{WardrobeTools.remember(sp.config().appearance,name);sp.saveConfig();notice=l.t("Saved","保存しました");rebuildWidgets();}catch(IllegalArgumentException e){notice=l.t("Name: 1–24 characters. Maximum: 8 looks.","名前は1〜24文字・最大8件です。");}}).bounds(x+178,34,102,18).build());
        int row=0;for(var outfit:sp.config().appearance.outfits){int y=58+row++*17;addRenderableWidget(ClientButton.create(Component.literal(outfit.name),b->{WardrobeTools.apply(outfit,sp.config().appearance);sp.saveConfig();onClose();}).bounds(x,y,252,15).build());addRenderableWidget(ClientButton.create(Component.literal("×"),b->{sp.config().appearance.outfits.remove(outfit);sp.saveConfig();rebuildWidgets();}).bounds(x+256,y,24,15).build());}
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Back","戻る")),b->onClose()).bounds(x,height-26,280,18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){ClientStyle.backdrop(g,width,height);g.centeredText(font,SkyPilot.get().lang().t("SAVED OUTFITS / 8 SLOTS","保存コーデ / 8枠"),width/2,14,Ui.PURPLE);if(!notice.isEmpty())g.textWithWordWrap(font,Component.literal(notice),width/2-140,height-47,280,Ui.GRAY);super.extractRenderState(g,mx,my,delta);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
