package dev.skypilot.mod.core;

import java.util.*;
import java.util.random.RandomGenerator;

/** A local 2048 game. A tile merges at most once per move; unsuccessful moves never spawn tiles. */
public final class TileGame {
    private int[] cells=new int[16],undo;
    private int score,undoScore;
    private final RandomGenerator random;
    public TileGame(RandomGenerator random){this.random=random;restart();}
    TileGame(RandomGenerator random,int[] initial){if(initial.length!=16)throw new IllegalArgumentException("16 cells");for(int v:initial)if(v<0||(v!=0&&(v<2||(v&(v-1))!=0)))throw new IllegalArgumentException("power of two");this.random=random;cells=initial.clone();}
    public int[] cells(){return cells.clone();}public int score(){return score;}public boolean canUndo(){return undo!=null;}
    public void restart(){cells=new int[16];score=0;undo=null;spawn();spawn();}
    public boolean move(int direction){
        if(direction<0||direction>3)throw new IllegalArgumentException("direction");
        int[] before=cells.clone();int beforeScore=score;
        for(int row=0;row<4;row++){
            var packed=new ArrayList<Integer>();for(int col=0;col<4;col++){int v=cells[index(direction,row,col)];if(v!=0)packed.add(v);}
            var merged=new ArrayList<Integer>();for(int i=0;i<packed.size();i++){int v=packed.get(i);if(i+1<packed.size()&&v==packed.get(i+1)){v*=2;score+=v;i++;}merged.add(v);}
            for(int col=0;col<4;col++)cells[index(direction,row,col)]=col<merged.size()?merged.get(col):0;
        }
        if(Arrays.equals(before,cells))return false;undo=before;undoScore=beforeScore;spawn();return true;
    }
    private static int index(int d,int row,int col){return switch(d){case 0->row*4+col;case 1->row*4+3-col;case 2->col*4+row;default->(3-col)*4+row;};}
    public void undo(){if(undo!=null){cells=undo;score=undoScore;undo=null;}}
    public boolean over(){for(int i=0;i<16;i++){if(cells[i]==0)return false;if(i%4<3&&cells[i]==cells[i+1]||i<12&&cells[i]==cells[i+4])return false;}return true;}
    private void spawn(){var empty=new ArrayList<Integer>();for(int i=0;i<16;i++)if(cells[i]==0)empty.add(i);if(!empty.isEmpty())cells[empty.get(random.nextInt(empty.size()))]=random.nextInt(10)==0?4:2;}
}
