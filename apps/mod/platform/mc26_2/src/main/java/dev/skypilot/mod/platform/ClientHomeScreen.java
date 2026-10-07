package dev.skypilot.mod.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** Client home. All game navigation uses Minecraft's original screens. */
final class ClientHomeScreen extends Screen {
    ClientHomeScreen() { super(Component.literal("SkyPilot")); }
    @Override protected void init() {
        var l = SkyPilot.get().lang();
        int x = width / 2 - 100, y = Math.max(72, height / 2 - 35);
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Singleplayer", "シングルプレイ")), b -> minecraft.gui.setScreen(new SelectWorldScreen(this))).bounds(x,y,200,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Multiplayer", "マルチプレイ")), b -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this))).bounds(x,y+24,200,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Wardrobe", "ワードローブ")), b -> minecraft.gui.setScreen(new AppearanceScreen(this))).bounds(x,y+48,98,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Tool desk", "ツールデスク")), b -> minecraft.gui.setScreen(new DeskScreen(this))).bounds(x+102,y+48,98,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Visual studio", "演出スタジオ")), b -> minecraft.gui.setScreen(new VisualScreen(this))).bounds(x,y+72,98,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Options", "設定")), b -> minecraft.gui.setScreen(new OptionsScreen(this,minecraft.options,false))).bounds(x+102,y+72,98,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Quit", "終了")), b -> minecraft.stop()).bounds(x,y+96,200,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        ClientStyle.backdrop(g,width,height);
        int y=Math.max(72,height/2-35);
        ClientStyle.panel(g,width/2-112,y-10,224,140);
        super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,Component.literal("S K Y P I L O T"),width/2,y-42,Ui.WHITE);
        g.centeredText(font,Component.literal("YOUR SKY. YOUR STYLE."),width/2,y-27,0xFF55D6D2);
        g.text(font,"Minecraft 26.2  /  Fabric",12,height-16,Ui.GRAY,false);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
