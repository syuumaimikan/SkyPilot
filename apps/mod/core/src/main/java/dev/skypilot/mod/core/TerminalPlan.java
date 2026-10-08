package dev.skypilot.mod.core;

import java.util.*;
import java.util.regex.Pattern;

/** A plan from the currently visible terminal stacks. Never advances on predicted clicks. */
public final class TerminalPlan {
    public record Cell(int slot, String item, String name, int count, boolean completed) {}
    public record Click(int slot, int button, int count) {}
    private static final List<String> CYCLE=List.of("red","orange","yellow","green","blue");
    private TerminalPlan() {}
    public static boolean recognizes(String title) {
        return title.equals("Click in order!") || title.equals("Correct all the panes!")
            || title.equals("Change all to same color!") || title.equals("Click the button on time!")
            || title.matches("What starts with: ['\"].+['\"]\\?") || title.matches("Select all the .+ items!");
    }
    public static List<Click> solve(String title,List<Cell> cells) {
        return solve(title,cells,6);
    }
    public static List<Click> solve(String title,List<Cell> cells,int rows) {
        if (!recognizes(title)) return List.of();
        if(rows<3||rows>6)return List.of();
        List<Cell> board=cells.stream().filter(c->c.slot()>=9 && c.slot()<(rows-1)*9 && c.slot()%9>0 && c.slot()%9<8 && !c.completed()).toList();
        if(title.equals("Click in order!")) return board.stream().filter(c->color(c).equals("red") && c.count()>0 && c.count()<=64)
            .sorted(Comparator.comparingInt(Cell::count)).map(c->new Click(c.slot(),0,1)).toList();
        if(title.equals("Correct all the panes!")) return board.stream().filter(c->color(c).equals("red")).map(c->new Click(c.slot(),0,1)).toList();
        if(title.equals("Change all to same color!")) {
            List<Cell> panes=board.stream().filter(c->CYCLE.contains(color(c))).toList();
            int best=0,cost=Integer.MAX_VALUE;
            for(int target=0;target<5;target++) { int sum=0;for(Cell c:panes){int d=Math.floorMod(target-CYCLE.indexOf(color(c)),5);sum+=Math.min(d,5-d);}if(sum<cost){cost=sum;best=target;} }
            List<Click> result=new ArrayList<>();
            for(Cell c:panes){int d=Math.floorMod(best-CYCLE.indexOf(color(c)),5);if(d>0)result.add(new Click(c.slot(),d<=2?0:1,d<=2?d:5-d));}
            return List.copyOf(result);
        }
        var letter=Pattern.compile("What starts with: ['\"](.+)['\"]\\?").matcher(title);
        if(letter.matches()) {String prefix=letter.group(1).toLowerCase(Locale.ROOT);return board.stream().filter(c->!c.item().endsWith("glass_pane")&&c.name().toLowerCase(Locale.ROOT).startsWith(prefix)).map(c->new Click(c.slot(),0,1)).toList();}
        var selected=Pattern.compile("Select all the (.+) items!").matcher(title);
        if(selected.matches()) {String wanted=selected.group(1).toLowerCase(Locale.ROOT).replace(' ','_');if(wanted.equals("silver"))wanted="light_gray";String match=wanted;return board.stream().filter(c->itemColor(c.item()).equals(match)).map(c->new Click(c.slot(),0,1)).toList();}
        return List.of();
    }
    private static String itemColor(String item){
        String special=switch(item){case "bone_meal"->"white";case "lapis_lazuli"->"blue";case "cocoa_beans"->"brown";case "ink_sac"->"black";default->"";};
        if(!special.isEmpty())return special;
        for(String suffix:List.of("_wool","_dye","_stained_glass","_terracotta"))if(item.endsWith(suffix))return item.substring(0,item.length()-suffix.length());
        return "";
    }
    public static String color(Cell cell){return cell.item().replace("_stained_glass_pane","");}
}
