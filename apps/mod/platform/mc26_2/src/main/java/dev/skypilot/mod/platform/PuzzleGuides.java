package dev.skypilot.mod.platform;
import dev.skypilot.mod.core.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
final class PuzzleGuides {
    private record Blaze(Vec3 pos,double health){}
    private static List<Blaze> blazes=List.of();private static long next;
    private static List<Vec3> route=List.of();private static Object routeWorld;
    private static Vec3 ticMove;private static List<Vec3[]> beams=List.of();private static long nextWorld;
    private static Vec3 boardCenter,boardRight;private static Object boardWorld;
    static void calibrateBoard(){var mc=Minecraft.getInstance();if(mc.player==null||!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)||hit.getDirection().getAxis().isVertical())return;boardCenter=Vec3.atCenterOf(hit.getBlockPos());var normal=hit.getDirection();boardRight=new Vec3(normal.getStepZ(),0,-normal.getStepX());boardWorld=mc.level;mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("三目並べ: 盤面中央を記録しました"),false);}
    static void mazeRoute(String solution){var mc=Minecraft.getInstance();if(mc.player==null||solution.startsWith("No"))return;var points=new ArrayList<Vec3>();Vec3 pos=mc.player.position().add(0,.2,0);points.add(pos);for(String s:solution.split("\\s+")){pos=switch(s){case "↑"->pos.add(0,0,-1);case "→"->pos.add(1,0,0);case "↓"->pos.add(0,0,1);case "←"->pos.add(-1,0,0);default->pos;};if(!points.getLast().equals(pos))points.add(pos);}route=List.copyOf(points);routeWorld=mc.level;}
    static void render(){
        if(DungeonSolver.available() && DungeonMap.inDungeon())return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(routeWorld!=mc.level)route=List.of();
        if(boardWorld!=mc.level){boardCenter=null;ticMove=null;}
        try(var ignored=mc.levelRenderer.collectPerFrameRenderThreadGizmos()){
            for(int i=1;i<route.size();i++)Gizmos.line(route.get(i-1),route.get(i),0xFF55DDFF,2f);
            if(DungeonMap.inDungeon()){
                if(System.currentTimeMillis()>nextWorld){nextWorld=System.currentTimeMillis()+700;updateWorld(mc);}
                if(Feat.on(Features.TTT_GUIDE)&&ticMove!=null){Gizmos.cuboid(new net.minecraft.world.phys.AABB(ticMove.add(-.45,-.45,-.45),ticMove.add(.45,.45,.45)),GizmoStyle.stroke(0xFF55FF88,3f));Gizmos.line(mc.player.getEyePosition().add(0,-.4,0),ticMove,0xFF55FF88,2f);}
                if(Feat.on(Features.CREEPER_GUIDE))for(int i=0;i<beams.size();i++){var beam=beams.get(i);int color=new int[]{0xFF66DDFF,0xFF99FF55,0xFFFFFF55,0xFFDD88FF,0xFFFF99BB}[i%5];Gizmos.line(beam[0],beam[1],color,3f);Gizmos.billboardText(""+(i+1),beam[0],TextGizmo.Style.forColorAndCentered(color));Gizmos.billboardText(""+(i+1),beam[1],TextGizmo.Style.forColorAndCentered(color));}
            }
            if(!Feat.on(Features.BLAZE_GUIDE)||!DungeonMap.inDungeon())return;
            if(System.currentTimeMillis()>next){next=System.currentTimeMillis()+300;var found=new ArrayList<Blaze>();for(var entity:mc.level.entitiesForRendering()){if(!entity.isAlive()||entity.distanceToSqr(mc.player)>1600||!mc.player.hasLineOfSight(entity))continue;String name=entity.getName().getString().replaceAll("§.","");var match=java.util.regex.Pattern.compile(".*Blaze.*?([\\d,]+)/([\\d,]+).*",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(name);if(match.matches())try{found.add(new Blaze(entity.position(),Double.parseDouble(match.group(2).replace(",",""))));}catch(NumberFormatException ignoredNumber){}}found.sort(Comparator.comparingDouble(Blaze::health));if(SkyPilot.get().config().blazeDescending)Collections.reverse(found);blazes=List.copyOf(found);}
            if(blazes.size()<2||blazes.size()>10)return;
            for(int i=0;i<blazes.size();i++){var b=blazes.get(i);Gizmos.billboardText(""+(i+1),b.pos().add(0,.4,0),TextGizmo.Style.forColorAndCentered(i==0?0xFF55FF88:0xFFFFFF55));if(i>0)Gizmos.line(blazes.get(i-1).pos(),b.pos(),0xAA55DDFF,2f);}
        }
    }
    private static void updateWorld(Minecraft mc){
        ticMove=null;beams=List.of();
        if(Feat.on(Features.TTT_GUIDE)){
            var frames=new ArrayList<net.minecraft.world.entity.decoration.ItemFrame>();
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame f&&f.distanceToSqr(mc.player)<225&&f.getItem().has(net.minecraft.core.component.DataComponents.MAP_ID))frames.add(f);
            if(boardCenter!=null&&mc.player.position().distanceToSqr(boardCenter)<400){
                char[] board=".........".toCharArray();boolean valid=true;int found=0;
                for(var frame:frames){Vec3 relative=frame.position().subtract(boardCenter);int col=(int)Math.round(relative.dot(boardRight))+1,row=1-(int)Math.round(relative.y);if(col<0||col>2||row<0||row>2||Math.abs(relative.x*boardRight.z-relative.z*boardRight.x)>1.2)continue;var map=net.minecraft.world.item.MapItem.getSavedData(frame.getItem(),mc.level);if(map==null){valid=false;break;}int color=Byte.toUnsignedInt(map.colors[8256]);if(color!=114&&color!=33)continue;board[row*3+col]=color==114?'X':'O';found++;}
                if(valid&&found>0)try{int move=dev.skypilot.mod.core.PuzzleSolver.ticTacToe(new String(board),'O');if(move>=0)ticMove=boardCenter.add(boardRight.scale(move%3-1)).add(0,1-move/3,0);}catch(IllegalArgumentException ignored){}
            }
        }
        if(Feat.on(Features.CREEPER_GUIDE))for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof net.minecraft.world.entity.monster.Creeper)||entity.distanceToSqr(mc.player)>400||!mc.player.hasLineOfSight(entity))continue;
            boolean puzzleBase=false;for(int below=1;below<=4;below++)if(mc.level.getBlockState(entity.blockPosition().below(below)).is(net.minecraft.world.level.block.Blocks.SEA_LANTERN))puzzleBase=true;if(!puzzleBase)continue;
            var center=entity.getBoundingBox().getCenter();var blocks=new ArrayList<Vec3>();var base=entity.blockPosition();
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-7;y<=8;y++){var pos=base.offset(x,y,z);if(!mc.level.hasChunkAt(pos))continue;var state=mc.level.getBlockState(pos);if(state.is(net.minecraft.world.level.block.Blocks.SEA_LANTERN)||state.is(net.minecraft.world.level.block.Blocks.PRISMARINE)){var point=Vec3.atCenterOf(pos);if(point.distanceToSqr(center)>4)blocks.add(point);}}
            if(blocks.size()>24||blocks.size()<4)continue;var candidates=new ArrayList<Vec3[]>();var used=new HashSet<Integer>();
            for(int i=0;i<blocks.size();i++)for(int j=i+1;j<blocks.size();j++){if(used.contains(i)||used.contains(j))continue;var a=blocks.get(i);var b=blocks.get(j);var ab=b.subtract(a);double t=center.subtract(a).dot(ab)/ab.lengthSqr();if(t<=0||t>=1||a.add(ab.scale(t)).distanceToSqr(center)>.5)continue;candidates.add(new Vec3[]{a,b});used.add(i);used.add(j);}
            if(candidates.size()>=2&&candidates.size()<=5){beams=List.copyOf(candidates);break;}
        }
    }
}
