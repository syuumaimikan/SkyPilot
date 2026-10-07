package dev.skypilot.mod.core;

import java.util.*;

/** Deterministic helpers for values entered by the player. Never reads the world. */
public final class PuzzleSolver {
    private PuzzleSolver() {}

    /** Row-major 3x3 board: X, O and . (empty). Returns the best legal cell, or -1. */
    public static int ticTacToe(String input, char player) {
        String board = input.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        if (board.length() != 9 || !board.matches("[XO.]{9}") || (player != 'X' && player != 'O'))
            throw new IllegalArgumentException("Use exactly 9 cells: X, O or .");
        char[] cells = board.toCharArray();
        long x = board.chars().filter(c -> c == 'X').count(), o = board.chars().filter(c -> c == 'O').count();
        if (o > x || x > o + 1 || (player == 'X' ? x != o : x != o + 1))
            throw new IllegalArgumentException("The board does not match this player's turn.");
        if (winner(cells) != '.') return -1;
        int best = -1, score = -100;
        for (int cell : new int[]{4, 0, 2, 6, 8, 1, 3, 5, 7}) {
            if (cells[cell] != '.') continue;
            cells[cell] = player;
            int value = minimax(cells, other(player), player, 0);
            cells[cell] = '.';
            if (value > score) { score = value; best = cell; }
        }
        return best;
    }

    private static char other(char p) { return p == 'X' ? 'O' : 'X'; }
    private static int minimax(char[] b, char turn, char self, int depth) {
        char w = winner(b);
        if (w != '.') return w == self ? 10 - depth : depth - 10;
        int best = turn == self ? -100 : 100;
        boolean empty = false;
        for (int i = 0; i < 9; i++) if (b[i] == '.') {
            empty = true; b[i] = turn;
            int value = minimax(b, other(turn), self, depth + 1); b[i] = '.';
            best = turn == self ? Math.max(best, value) : Math.min(best, value);
        }
        return empty ? best : 0;
    }
    private static char winner(char[] b) {
        for (int[] line : new int[][]{{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}})
            if (b[line[0]] != '.' && b[line[0]] == b[line[1]] && b[line[1]] == b[line[2]]) return b[line[0]];
        return '.';
    }

    /** Shortest four-direction route. # wall, . floor, S start, G goal. */
    public static String maze(String input) {
        String[] rows = input.strip().replace("/", "\n").split("\\R");
        int h = rows.length, w = rows[0].length();
        if (h > 32 || w > 32 || w == 0) throw new IllegalArgumentException("Maximum maze size: 32 x 32.");
        int start = -1, goal = -1;
        for (int y = 0; y < h; y++) {
            if (rows[y].length() != w || !rows[y].matches("[#.SG]+")) throw new IllegalArgumentException("Use equal rows of # . S G, separated by /.");
            for (int x = 0; x < w; x++) {
                if (rows[y].charAt(x) == 'S') { if (start != -1) throw new IllegalArgumentException("Use one start."); start = y*w+x; }
                if (rows[y].charAt(x) == 'G') { if (goal != -1) throw new IllegalArgumentException("Use one goal."); goal = y*w+x; }
            }
        }
        if (start == -1 || goal == -1) throw new IllegalArgumentException("Add one S and one G.");
        int[] previous = new int[w*h], direction = new int[w*h]; Arrays.fill(previous, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(start); previous[start] = start;
        int[] dx = {0,1,0,-1}, dy = {-1,0,1,0}; String[] names = {"↑","→","↓","←"};
        while (!queue.isEmpty()) {
            int p = queue.remove(); if (p == goal) break;
            for (int d=0;d<4;d++) {
                int x=p%w+dx[d], y=p/w+dy[d];
                if (x<0 || x>=w || y<0 || y>=h || rows[y].charAt(x)=='#') continue;
                int next=y*w+x; if(previous[next]!=-1) continue;
                previous[next]=p; direction[next]=d; queue.add(next);
            }
        }
        if (previous[goal] == -1) return "No route";
        List<String> steps=new ArrayList<>();
        for(int p=goal;p!=start;p=previous[p]) steps.add(names[direction[p]]);
        Collections.reverse(steps); return String.join(" ",steps);
    }
    /** One valid solution for a manually entered Sudoku; search is bounded to protect the UI thread. */
    public static String sudoku(String input){
        String value=input.replaceAll("\\s", "").replace('.', '0');
        if(!value.matches("[0-9]{81}"))throw new IllegalArgumentException("Use 81 digits; 0 or . means empty.");
        int[] board=new int[81],rows=new int[9],cols=new int[9],boxes=new int[9];
        for(int i=0;i<81;i++){int n=value.charAt(i)-'0';board[i]=n;if(n==0)continue;int r=i/9,c=i%9,b=r/3*3+c/3,bit=1<<n;if(((rows[r]|cols[c]|boxes[b])&bit)!=0)throw new IllegalArgumentException("Duplicate number in a row, column or box.");rows[r]|=bit;cols[c]|=bit;boxes[b]|=bit;}
        if(!solveSudoku(board,rows,cols,boxes,new int[]{0}))throw new IllegalArgumentException("This Sudoku has no solution.");
        StringBuilder out=new StringBuilder();for(int i=0;i<81;i++){if(i>0&&i%9==0)out.append('\n');out.append(board[i]);}return out.toString();
    }
    private static boolean solveSudoku(int[] board,int[] rows,int[] cols,int[] boxes,int[] nodes){
        if(++nodes[0]>250000)throw new IllegalArgumentException("Search limit reached; enter a few more clues.");
        int cell=-1,mask=0,count=10;
        for(int i=0;i<81;i++)if(board[i]==0){int r=i/9,c=i%9,b=r/3*3+c/3,m=0x3FE&~(rows[r]|cols[c]|boxes[b]),n=Integer.bitCount(m);if(n==0)return false;if(n<count){cell=i;mask=m;count=n;if(n==1)break;}}
        if(cell<0)return true;int r=cell/9,c=cell%9,b=r/3*3+c/3;
        for(int n=1;n<=9;n++)if((mask&(1<<n))!=0){int bit=1<<n;board[cell]=n;rows[r]|=bit;cols[c]|=bit;boxes[b]|=bit;if(solveSudoku(board,rows,cols,boxes,nodes))return true;board[cell]=0;rows[r]&=~bit;cols[c]&=~bit;boxes[b]&=~bit;}
        return false;
    }

}
