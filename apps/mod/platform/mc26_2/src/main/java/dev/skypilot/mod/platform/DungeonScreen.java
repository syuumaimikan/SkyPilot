package dev.skypilot.mod.platform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class DungeonScreen extends Screen {
    private final Screen parent;
    DungeonScreen(Screen parent){super(Component.literal("Dungeon Solver"));this.parent=parent;}
    @Override protected void init(){int x=width/2-145,y=Math.max(110,height-104);
        addRenderableWidget(ClientButton.create(Component.literal("ダンジョンガイド: "+(SkyPilot.get().config().dungeonSolver?"オン":"オフ")),b->{SkyPilot.get().config().dungeonSolver=!SkyPilot.get().config().dungeonSolver;SkyPilot.get().saveConfig();rebuildWidgets();}).bounds(x,y,290,20).build());
        addRenderableWidget(ClientButton.create(Component.literal("ソルバーの詳細設定"),b->{try{minecraft.gui.setScreen((Screen)Class.forName("de.hysky.skyblocker.config.SkyblockerConfigManager").getMethod("createGUI",Screen.class).invoke(null,this));}catch(ReflectiveOperationException e){SkyPilot.LOG.warn("Cannot open solver settings",e);}}).bounds(x,y+24,142,20).build());
        addRenderableWidget(ClientButton.create(Component.literal("HUDの位置を調整"),b->minecraft.gui.setScreen(new HudEditorScreen(this))).bounds(x+148,y+24,142,20).build());
        addRenderableWidget(ClientButton.create(Component.literal("戻る"),b->onClose()).bounds(x,y+52,290,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){ClientStyle.backdrop(g,width,height);g.centeredText(font,"DUNGEON SOLVER",width/2,14,Ui.PURPLE);
        String[] lines={"部屋: Blaze / Boulder / Creeper / Ice Fill / Ice Path", "三目並べ / Three Weirdos / Quiz / Teleport Maze / Water Board", "F7: Simon Says / Lights On / Arrow Align / Target Practice", "ターミナル: 数字順 / 色 / 頭文字 / 色統一 / 全点灯 / Melody", "緑 = 次の操作 · ガイド線 = 操作順 · L/R = 左/右クリック", "Water Board: サーバー進行の残り秒 · Melody: 観測に基づく推定", "未観測の盤面/通信待ちは解法・時刻が未確定です"};
        int y=34;for(String line:lines){g.centeredText(font,line,width/2,y,Ui.GRAY);y+=12;}
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
