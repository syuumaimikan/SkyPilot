package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** A small offline desk: focus timer, notes, calculator, random picker, tasks and 2048. */
final class DeskScreen extends Screen {
    private final Screen parent;
    private int tab,taskPage;
    private String result="";
    private final Map<Integer,String> values=new HashMap<>();
    private EditBox input;
    private int x,w;
    DeskScreen(Screen parent){super(Component.literal("Tool desk"));this.parent=parent;}
    private String t(String en,String ja){return SkyPilot.get().lang().t(en,ja);}
    @Override protected void init(){
        var sp=SkyPilot.get();x=Math.max(12,width/2-160);w=Math.min(320,width-24);
        String[] en={"Focus","Notes","Calc","Random","Tasks","2048"},ja={"集中","メモ","電卓","抽選","ToDo","2048"};int tw=(w-15)/6;
        for(int i=0;i<6;i++){int index=i;button((tab==i?"• ":"")+t(en[i],ja[i]),x+i*(tw+3),34,tw,18,()->{tab=index;result="";rebuildWidgets();});}
        if(tab<5){String initial=switch(tab){case 0->Integer.toString(sp.config().focusMinutes);case 1->sp.config().deskNotes.replace("\n","\\n");case 2->"1.5m * 3 + 200k";case 3->t("Mining, Fishing, Farming","採掘, 釣り, 農業");default->"";};
            input=new EditBox(font,x,62,w,20,Component.literal(t("Tool input","ツールの入力")));input.setMaxLength(tab==1?2800:tab==4?80:200);input.setValue(values.getOrDefault(tab,initial));input.setHint(Component.literal(tab==4?t("Add a task…","やることを追加…"):""));input.setResponder(value->values.put(tab,value));addRenderableWidget(input);
        }
        switch(tab){
            case 0->{button(t("Start","開始"),x,88,100,20,()->{try{int minutes=Integer.parseInt(input.getValue().strip());DeskRuntime.FOCUS.start(minutes,DeskRuntime.now());sp.config().focusMinutes=minutes;sp.saveConfig();result=t("Timer started","タイマー開始");}catch(IllegalArgumentException e){result=t("Enter 1–180 minutes","1〜180分を入力してください");}});button(t("Pause / resume","停止／再開"),x+104,88,108,20,()->DeskRuntime.FOCUS.toggle(DeskRuntime.now()));button(t("Reset","リセット"),x+216,88,104,20,()->DeskRuntime.FOCUS.reset());}
            case 1->{button(t("Save note","メモ保存"),x,88,156,20,()->{String text=input.getValue().replace("\\n","\n");if(text.length()>1400){result=t("1400 characters maximum","1400文字までです");return;}sp.config().deskNotes=text;sp.saveConfig();result=t("Saved locally","端末に保存しました");});button(t("Copy note","コピー"),x+160,88,160,20,()->minecraft.keyboardHandler.setClipboard(input.getValue().replace("\\n","\n")));}
            case 2->{button(t("Calculate","計算"),x,88,156,20,()->{try{result=String.format(Locale.ROOT,"%,.4f",Calc.eval(input.getValue()));}catch(IllegalArgumentException e){result=t("Check your expression (+ - * / ^, k/m/b/t)","数式を確認してください（+ - * / ^、k/m/b/t）");}});button(t("Copy result","結果コピー"),x+160,88,160,20,()->minecraft.keyboardHandler.setClipboard(result));}
            case 3->{button(t("Pick one","ひとつ選ぶ"),x,88,100,20,()->{var options=Arrays.stream(input.getValue().split("[,、]",-1)).map(String::strip).filter(s->!s.isEmpty()).toList();result=options.isEmpty()?t("Add some choices","候補を入力してください"):options.get(ThreadLocalRandom.current().nextInt(options.size()));});button(t("Coin","コイン"),x+104,88,68,20,()->result=ThreadLocalRandom.current().nextBoolean()?t("Heads","表"):t("Tails","裏"));button("D6",x+176,88,68,20,()->result=Integer.toString(ThreadLocalRandom.current().nextInt(1,7)));button("D20",x+248,88,72,20,()->result=Integer.toString(ThreadLocalRandom.current().nextInt(1,21)));}
            case 4->{button(t("Add task","追加"),x,86,100,18,()->{String text=input.getValue().strip();if(text.isEmpty()){result=t("Enter a task","ToDoを入力してください");return;}if(sp.config().todos.size()>=30){result=t("Maximum 30 tasks","ToDoは30件までです");return;}result="";if(!text.isEmpty()){sp.config().todos.add(new SkyPilotConfig.Todo(text));sp.saveConfig();values.put(4,"");rebuildWidgets();}});button(t("Clear completed","完了分を削除"),x+104,86,216,18,()->{sp.config().todos.removeIf(task->task.done);sp.saveConfig();rebuildWidgets();});int max=Math.max(1,(height-164)/20);taskPage=Math.min(taskPage,Math.max(0,(sp.config().todos.size()-1)/max));for(int i=0;i<Math.min(max,sp.config().todos.size()-taskPage*max);i++){var task=sp.config().todos.get(taskPage*max+i);button((task.done?"✓ ":"□ ")+task.text,x,110+i*20,w,18,()->{task.done=!task.done;sp.saveConfig();rebuildWidgets();});}
                button("‹",x,height-48,24,18,()->{taskPage=Math.max(0,taskPage-1);rebuildWidgets();});button("›",x+28,height-48,24,18,()->{if((taskPage+1)*max<sp.config().todos.size())taskPage++;rebuildWidgets();});}
            case 5->{String[] arrows={"←","→","↑","↓"};for(int i=0;i<4;i++){int direction=i;button(arrows[i],x+i*34,Math.min(height-50,192),30,18,()->move(direction));}button(t("Undo","一手戻す"),x+144,Math.min(height-50,192),80,18,()->{DeskRuntime.TILES.undo();});button(t("New game","新しく遊ぶ"),x+228,Math.min(height-50,192),92,18,()->DeskRuntime.TILES.restart());}
        }
        button(t("Back","戻る"),x,height-26,(w-4)/2,18,this::onClose);button(t("Puzzle lab","パズルラボ"),x+(w+4)/2,height-26,(w-4)/2,18,()->minecraft.gui.setScreen(new PuzzleScreen(this)));
    }
    private void move(int direction){DeskRuntime.TILES.move(direction);var sp=SkyPilot.get();if(DeskRuntime.TILES.score()>sp.config().tileBest){sp.config().tileBest=DeskRuntime.TILES.score();sp.saveConfig();}}
    private void button(String label,int px,int py,int width,int height,Runnable action){addRenderableWidget(ClientButton.create(Component.literal(label),b->action.run()).bounds(px,py,width,height).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        ClientStyle.backdrop(g,width,height);g.text(font,t("YOUR TOOL DESK","ツールデスク"),x,14,Ui.WHITE,false);
        if(tab==0){g.centeredText(font,DeskRuntime.FOCUS.display(DeskRuntime.now())+" · "+DeskRuntime.FOCUS.completed()+t(" sessions"," セッション"),width/2,130,0xFF55D6D2);g.centeredText(font,t("Minutes · countdown continues after closing","分数を入力 · 画面を閉じても継続"),width/2,153,Ui.GRAY);}
        if(tab==1)g.text(font,t("Use \\n for a new line · shared with launcher","改行は \\n · ランチャーとメモを共有"),x,116,Ui.GRAY,false);
        if(tab==5){int size=Math.min(27,(height-116)/4),bx=width/2-size*2,by=65;var cells=DeskRuntime.TILES.cells();for(int i=0;i<16;i++){int px=bx+i%4*size,py=by+i/4*size,v=cells[i];g.fill(px+1,py+1,px+size-1,py+size-1,v==0?0xFF1B2538:0xFF000000|tileColor(v));if(v>0)g.centeredText(font,Integer.toString(v),px+size/2,py+(size-8)/2,Ui.WHITE);}g.centeredText(font,t("Score ","スコア ")+DeskRuntime.TILES.score()+t(" · Best "," · ベスト ")+SkyPilot.get().config().tileBest,width/2,56,Ui.GRAY);if(DeskRuntime.TILES.over())g.centeredText(font,t("Game over · undo or start again","終了 · 一手戻すか新しく遊べます"),width/2,by+size*4+3,Ui.PURPLE);}
        if(!result.isEmpty())g.textWithWordWrap(font,Component.literal(result),tab==4?x+60:x,tab==4?height-48:tab==0?171:132,tab==4?w-60:w,Ui.WHITE);
        super.extractRenderState(g,mx,my,delta);
    }
    private static int tileColor(int v){return switch(v){case 2->0x3F476F;case 4->0x55558A;case 8->0x69629F;case 16->0x8471B8;case 32->0x977ACB;case 64->0xAA86DC;default->0x3F8C97;};}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e){if(tab==5){int direction=switch(e.key()){case 263,65->0;case 262,68->1;case 265,87->2;case 264,83->3;default->-1;};if(direction>=0){move(direction);return true;}}return super.keyPressed(e);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
