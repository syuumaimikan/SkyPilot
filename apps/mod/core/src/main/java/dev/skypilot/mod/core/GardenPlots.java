package dev.skypilot.mod.core;
import java.util.*;
public final class GardenPlots {
    public static final int[] GRID={21,13,9,14,22,15,5,1,6,16,10,2,0,3,11,17,7,4,8,18,23,19,12,20,24};
    public static Set<Integer> pests(List<String> tab){var out=new LinkedHashSet<Integer>();boolean section=false;for(String raw:tab){String s=Text.strip(raw).strip();if(s.startsWith("Pests:")){section=true;continue;}if(section&&s.startsWith("Plots:")){var matcher=java.util.regex.Pattern.compile("\\d+").matcher(s.substring(6));while(matcher.find()){int n=Integer.parseInt(matcher.group());if(n>=1&&n<=24)out.add(n);}break;}if(section&&(s.isBlank()||s.endsWith(":")))section=false;}return out;}
    public static int cell(int plot){for(int i=0;i<GRID.length;i++)if(GRID[i]==plot)return i;return -1;}
}
