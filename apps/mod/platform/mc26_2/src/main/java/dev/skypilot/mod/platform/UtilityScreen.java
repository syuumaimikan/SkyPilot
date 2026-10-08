package dev.skypilot.mod.platform;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

final class UtilityScreen extends Screen {
    private final Screen parent;
	private boolean extras;
	void showExtras(){extras=true;}
    UtilityScreen(Screen parent){super(Component.literal("Utility HUD"));this.parent=parent;}
    private String t(String en,String ja){return SkyPilot.get().lang().t(en,ja);}
    private void toggle(String en,String ja,int index,BooleanSupplier value,Runnable action){int x=width/2-154+(index%2)*156,y=48+(index/2)*22;addRenderableWidget(ClientButton.create(Component.literal(t(en,ja)+": "+(value.getAsBoolean()?t("On","オン"):t("Off","オフ"))),b->{action.run();SkyPilot.get().saveConfig();rebuildWidgets();}).bounds(x,y,152,18).build());}
    private void button(String en,String ja,int x,int y,int w,Runnable action){addRenderableWidget(ClientButton.create(Component.literal(t(en,ja)),b->action.run()).bounds(x,y,w,18).build());}
    @Override protected void init(){var u=SkyPilot.get().config().utility;
        toggle("Coordinates","座標",0,()->u.coordinates,()->u.coordinates=!u.coordinates);
        toggle("FPS","FPS",1,()->u.fps,()->u.fps=!u.fps);
        toggle("Latency","通信遅延",2,()->u.ping,()->u.ping=!u.ping);
        toggle("Local clock","時計",3,()->u.clock,()->u.clock=!u.clock);
        toggle("Speed","移動速度",4,()->u.speed,()->u.speed=!u.speed);
        toggle("Tool durability","道具の耐久値",5,()->u.durability,()->u.durability=!u.durability);
        toggle("Free slots","空きスロット",6,()->u.slots,()->u.slots=!u.slots);
        toggle("Focus timer","集中タイマー",7,()->u.focus,()->u.focus=!u.focus);
        toggle("Session time","プレイ時間",8,()->u.session,()->u.session=!u.session);
        toggle("World clock","ゲーム内時刻",9,()->u.worldTime,()->u.worldTime=!u.worldTime);
        toggle("Align right","右側に表示",10,()->u.right,()->u.right=!u.right);
        toggle("Panel background","背景",11,()->u.background,()->u.background=!u.background);
		if(extras){clearWidgets();
			toggle("Armor durability","防具の耐久値",0,()->u.armor,()->u.armor=!u.armor);
			toggle("Active effects","効果と残り時間",1,()->u.effects,()->u.effects=!u.effects);
			toggle("Heap memory","メモリ使用量",2,()->u.memory,()->u.memory=!u.memory);
			toggle("GC counters","GC回数・時間",3,()->u.gc,()->u.gc=!u.gc);
			toggle("Inventory warning","持ち物の空き警告",4,()->u.inventoryWarning,()->u.inventoryWarning=!u.inventoryWarning);
			toggle("Durability warning","耐久値の警告",5,()->u.durabilityWarning,()->u.durabilityWarning=!u.durabilityWarning);
			toggle("Compare item lore","装備の数値比較",6,()->u.itemComparison,()->u.itemComparison=!u.itemComparison);
		}
        int x=width/2-154;
		button(extras?"Basic →":"More →",extras?"基本 →":"便利 →",x+208,height-48,96,()->{extras=!extras;rebuildWidgets();});
        button("Copy XYZ","座標コピー",x,height-48,100,()->ClientShortcuts.copyPosition(minecraft));
        button("Keys…","キー設定…",x+104,height-48,100,()->minecraft.gui.setScreen(new KeySettingsScreen(this)));
        button("Disable widgets","HUDをすべてオフ",x,height-26,148,()->{u.clear();SkyPilot.get().saveConfig();rebuildWidgets();});
        button("Back","戻る",x+156,height-26,148,this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){ClientStyle.backdrop(g,width,height);g.centeredText(font,"UTILITY HUD",width/2,12,Ui.PURPLE);g.centeredText(font,t("Small details. A better everyday experience.","いつものプレイに、小さな便利を。"),width/2,28,Ui.GRAY);super.extractRenderState(g,mx,my,delta);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
