package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.PuzzleSolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Manual-input puzzle helpers with independent inputs, readable long results and clipboard export. */
final class PuzzleScreen extends Screen {
    private final Screen parent;
    private EditBox input;
    private int mode,scroll;
    private final String[] saved={".........","S../##./..G","530070000600195000098000060800060003400803001700020006060000280000419005000080079"};
    private String result="";
    private char player='X';
    PuzzleScreen(Screen parent){super(Component.literal("Puzzle lab"));this.parent=parent;}
    @Override protected void init(){var l=SkyPilot.get().lang();int x=width/2-140,y=Math.max(48,height/2-56);
        String[] names={l.t("Tic-tac-toe","三目並べ"),l.t("Maze","迷路"),l.t("Sudoku","数独")};
        for(int i=0;i<3;i++){int m=i;addRenderableWidget(ClientButton.create(Component.literal((mode==i?"• ":"")+names[i]),b->{mode=m;result="";scroll=0;rebuildWidgets();}).bounds(x+i*94,y,92,20).build());}
        input=new EditBox(font,x,y+26,280,20,Component.literal(l.t("Puzzle input","パズル入力")));input.setMaxLength(1100);input.setValue(saved[mode]);input.setResponder(value->saved[mode]=value);addRenderableWidget(input);
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Solve","計算")),b->{try{scroll=0;if(mode==0){int cell=PuzzleSolver.ticTacToe(input.getValue(),player);result=cell<0?l.t("Game finished","終了済み"):l.t("Row ","行 ")+(cell/3+1)+l.t(" · Column "," · 列 ")+(cell%3+1);}else if(mode==1)result=PuzzleSolver.maze(input.getValue());else result=PuzzleSolver.sudoku(input.getValue());}catch(IllegalArgumentException e){result=l.t("Check input: ","入力を確認: ")+e.getMessage();}}).bounds(x,y+52,132,20).build());
        var who=ClientButton.create(Component.literal("X / O: "+player),b->{player=player=='X'?'O':'X';result="";rebuildWidgets();}).bounds(x+136,y+52,70,20).build();who.active=mode==0;addRenderableWidget(who);
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Copy","コピー")),b->minecraft.keyboardHandler.setClipboard(result)).bounds(x+210,y+52,70,20).build());
        addRenderableWidget(ClientButton.create(Component.literal(l.t("Back","戻る")),b->onClose()).bounds(x,height-26,280,18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){ClientStyle.backdrop(g,width,height);int y=Math.max(48,height/2-56),x=width/2-140;var l=SkyPilot.get().lang();g.centeredText(font,"PUZZLE LAB",width/2,12,Ui.PURPLE);String help=switch(mode){case 0->l.t("9 cells: X O . / left to right","9マスを左上から: X O .");case 1->l.t("# wall · . floor · S start · G goal · / new row","# 壁 · . 床 · S 始点 · G 終点 · / 改行");default->l.t("81 digits · 0 or . empty · one valid solution","81マス · 0 または . は空欄 · 解をひとつ表示");};g.centeredText(font,help,width/2,28,Ui.GRAY);
        var lines=font.split(Component.literal(result),280);int top=y+82,bottom=height-34;scroll=Math.min(scroll,Math.max(0,lines.size()*10-(bottom-top)));g.enableScissor(x,top,x+280,bottom);int py=top-scroll;for(var line:lines){g.text(font,line,x,py,Ui.WHITE);py+=10;}g.disableScissor();super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){scroll=Math.max(0,scroll-(int)(sy*20));return true;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
