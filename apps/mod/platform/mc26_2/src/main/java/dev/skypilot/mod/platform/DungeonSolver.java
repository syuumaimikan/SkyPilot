package dev.skypilot.mod.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.*;
import java.util.*;

/** Read-only adapter for the pinned, unmodified Skyblocker solver module. */
final class DungeonSolver {
    private static final String ROOT="de.hysky.skyblocker.";
    private static final Map<String,Field> FIELDS=new HashMap<>();
    private static boolean configured,wasEnabled,failed;
    private static long nextUpdate;
    private static String heading="",instruction="";
    private static List<Target> targets=List.of();
    private record Target(Vec3 pos,String text) {}
    static boolean available(){return FabricLoader.getInstance().isModLoaded("skyblocker");}
    private static Class<?> type(String name)throws ClassNotFoundException{return Class.forName(ROOT+name);}
    private static Object field(Object instance,Class<?> owner,String name)throws ReflectiveOperationException{
        String key=owner.getName()+":"+name;Field f=FIELDS.get(key);if(f==null){f=owner.getDeclaredField(name);f.setAccessible(true);FIELDS.put(key,f);}return f.get(instance);
    }
    private static Object read(Object object,String name)throws ReflectiveOperationException{return field(object,object.getClass(),name);}
    private static Object staticField(String name,String field)throws ReflectiveOperationException{return field(null,type(name),field);}
    static void tick(){
        if(!available()||failed)return;
        boolean enabled=SkyPilot.get().config().dungeonSolver;
        try{
            if(!configured||enabled!=wasEnabled){configure(enabled);configured=true;wasEnabled=enabled;}
            if(!enabled||!DungeonMap.inDungeon()){targets=List.of();heading="";return;}
            if(System.currentTimeMillis()<nextUpdate)return;nextUpdate=System.currentTimeMillis()+50;
            targets=List.of();heading="";instruction="";
            if(devices())return;
            Object room=type("skyblock.dungeon.secrets.DungeonManager").getMethod("getCurrentRoom").invoke(null);
            if(room==null||!(boolean)room.getClass().getMethod("isMatched").invoke(room))return;
            String name=(String)room.getClass().getMethod("getName").invoke(room);
            if(name==null)return;
            if(name.contains("water")){water(room);return;}
            String solver=null,box=null;
            if(name.contains("tic-tac-toe")||name.contains("tictactoe")){heading="三目並べ";solver="TicTacToe";box="nextBestMoveToMake";}
            else if(name.contains("weirdos")||name.contains("three-chests")){heading="Three Weirdos";solver="ThreeWeirdos";box="boundingBox";}
            else if(name.contains("boulder")||name.contains("boxes-room")){heading="Boulder";solver="boulder.Boulder";box="boundingBox";}
            else if(name.contains("blaze")){heading="Blaze";Object entity=staticField("skyblock.dungeon.puzzle.DungeonBlaze",name.contains("high")?"highestBlaze":"lowestBlaze");if(entity instanceof Entity e&&e.isAlive())targets=List.of(new Target(e.getBoundingBox().getCenter().add(0,-1,0),"次に弓で撃つ"));}
            else if(name.contains("trivia")||name.contains("quiz")){heading="Quiz";Object boxes=staticField("skyblock.dungeon.puzzle.Trivia","BOXES_TO_HIGHLIGHT");if(boxes instanceof List<?> list)targets=list.stream().filter(AABB.class::isInstance).map(b->new Target(((AABB)b).getCenter(),"正解のボタン")).toList();}
            else if(name.contains("ice-fill")||name.equals("ice-path"))heading="Ice Fill · 表示された経路を歩く";
            else if(name.contains("silverfish"))heading="Ice Path · 経路の方向へSilverfishを押す";
            else if(name.contains("teleport"))heading="Teleport Maze · 強調された候補へ進む";
            else if(name.contains("creeper"))heading="Creeper Beams · 同じ色の両端を弓で撃つ";
            if(solver!=null){Object b=staticField("skyblock.dungeon.puzzle."+solver,box);if(b instanceof AABB a)targets=List.of(new Target(a.getCenter(),"次に右クリック"));}
            instruction=targets.isEmpty()?"盤面/会話の更新待ち · 解法はワールド上に表示":"緑の対象を操作 · 更新を確認して次へ";
        }catch(ReflectiveOperationException|RuntimeException e){failed=true;targets=List.of();SkyPilot.LOG.warn("Dungeon solver adapter unavailable; native solver overlays remain available",e);}
    }
    static boolean verifyBackend() {
        try { configure(true);
            for(String[] pair:new String[][]{{"TicTacToe","nextBestMoveToMake"},{"ThreeWeirdos","boundingBox"},{"boulder.Boulder","boundingBox"},{"Trivia","BOXES_TO_HIGHLIGHT"},{"DungeonBlaze","highestBlaze"}})staticField("skyblock.dungeon.puzzle."+pair[0],pair[1]);
            Object water=staticField("skyblock.dungeon.puzzle.waterboard.WaterboardOneFlow","INSTANCE");
            for(String name:new String[]{"room","solution","waterStartMillis","currentTimeMillis"})read(water,name);
            for(String[] pair:new String[][]{{"SimonSays","SIMON_PATTERN"},{"SimonSays","CLICKED_BUTTONS"},{"LightsOn","LEVERS"},{"ArrowAlign","currentSolution"},{"TargetPractice","POSSIBLE_TARGETS"}})staticField("skyblock.dungeon.device."+pair[0],pair[1]);
            return !failed;
        }catch(ReflectiveOperationException e){SkyPilot.LOG.warn("Solver compatibility verification failed",e);return false;}
    }
    private static boolean devices()throws ReflectiveOperationException {
        Class<?> manager=type("skyblock.dungeon.secrets.DungeonManager");
        if(!(boolean)manager.getMethod("isInBoss").invoke(null))return false;
        Object boss=manager.getMethod("getBoss").invoke(null);
        if(!(boss instanceof Enum<?> e)||!e.name().equals("MAXOR"))return false;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return false;
        var guides=new ArrayList<Target>();
        if(mc.player.position().distanceToSqr(new Vec3(110,121,93))<225){
            heading="Simon Says";
            Object pattern=staticField("skyblock.dungeon.device.SimonSays","SIMON_PATTERN"),clicked=staticField("skyblock.dungeon.device.SimonSays","CLICKED_BUTTONS");
            if(pattern instanceof List<?> list && clicked instanceof Collection<?> used)for(Object p:list)if(p instanceof BlockPos pos){BlockPos button=pos.west();if(!used.contains(button)&&mc.level.getBlockState(button).is(net.minecraft.world.level.block.Blocks.STONE_BUTTON))guides.add(new Target(Vec3.atCenterOf(button),(guides.size()+1)+" · 右クリック"));}
            instruction=guides.isEmpty()?"点灯の順番を記憶中 / 開始ボタンを押す":"番号順に右クリック · ボタン出現後に操作";
        }else if(mc.player.position().distanceToSqr(new Vec3(60,135,142))<144){
            heading="Lights On";BlockPos[] levers=(BlockPos[])staticField("skyblock.dungeon.device.LightsOn","LEVERS");
            for(BlockPos pos:levers){var state=mc.level.getBlockState(pos);if(state.is(net.minecraft.world.level.block.Blocks.LEVER)&&state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)&&!state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))guides.add(new Target(Vec3.atCenterOf(pos),"右クリックして点灯"));}
            instruction="消灯中のレバーを順に右クリック";
        }else if(mc.player.position().distanceToSqr(new Vec3(-2,122,77))<64){
            heading="Arrow Align";Object raw=staticField("skyblock.dungeon.device.ArrowAlign","currentSolution");
            if(raw instanceof int[] solution)for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame&&frame.position().distanceToSqr(new Vec3(-2,122,77))<20){var p=frame.blockPosition();int index=(124-p.getY())*5+79-p.getZ();if(index<0||index>=solution.length||solution[index]<0)continue;int clicks=Math.floorMod(solution[index]-frame.getRotation(),8);if(clicks>0)guides.add(new Target(frame.position(),"右クリック × "+clicks));}
            instruction="矢印上の回数だけ右クリック · 更新後に再計算";
        }else if(mc.player.position().distanceToSqr(new Vec3(63,127,35))<100){
            heading="Target Practice";Object raw=staticField("skyblock.dungeon.device.TargetPractice","POSSIBLE_TARGETS");
            if(raw instanceof Set<?> positions)for(Object p:positions)if(p instanceof BlockPos pos&&mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK))guides.add(new Target(Vec3.atCenterOf(pos),"今、弓で撃つ"));
            instruction=guides.isEmpty()?"感圧板に立ち、緑の標的の出現を待つ":"緑の標的を撃つ · 変化したら次へ";
        }else return false;
        targets=List.copyOf(guides);return true;
    }
    private static void configure(boolean enabled)throws ReflectiveOperationException{
        Object config=type("config.SkyblockerConfigManager").getMethod("get").invoke(null),dungeons=read(config,"dungeons");
        String[][] groups={{"puzzleSolvers","solveTicTacToe","solveThreeWeirdos","creeperSolver","waterboardOneFlow","previewWaterPath","previewLeverEffects","blazeSolver","solveBoulder","solveIceFill","solveSilverfish","solveTrivia","solveTeleportMaze"},{"devices","solveSimonSays","solveLightsOn","solveArrowAlign","solveTargetPractice"},{"terminals","solveColor","solveSameColor","solveOrder","solveStartsWith"}};
        for(String[] group:groups){Object settings=read(dungeons,group[0]);for(int i=1;i<group.length;i++)settings.getClass().getField(group[i]).setBoolean(settings,enabled);}
        Object terminals=read(dungeons,"terminals");terminals.getClass().getField("blockIncorrectClicks").setBoolean(terminals,false);
        // Bundling a display helper must never opt the player into automatic party messages.
        for(String[] setting:new String[][]{{"mimicMessage","sendMimicMessage"},{"princeMessage","sendPrinceMessage"},{"batMessage","sendBatMessage"}}){Object c=read(dungeons,setting[0]);c.getClass().getField(setting[1]).setBoolean(c,false);}
    }
    private static void water(Object room)throws ReflectiveOperationException{
        heading="Water Board";Object instance=staticField("skyblock.dungeon.puzzle.waterboard.WaterboardOneFlow","INSTANCE");
        if(read(instance,"room")!=room)return;
        Object raw=read(instance,"solution");if(!(raw instanceof Map<?,?> solution)){instruction="盤面の認識/解法計算待ち";return;}
        long start=(long)read(instance,"waterStartMillis"),clock=(long)read(instance,"currentTimeMillis");
        record Step(Object lever,double seconds,int ordinal){}
        var steps=new ArrayList<Step>();
        for(var entry:solution.entrySet())if(entry.getValue() instanceof List<?> times)for(Object t:times)if(t instanceof Number n)steps.add(new Step(entry.getKey(),n.doubleValue(),((Enum<?>)entry.getKey()).ordinal()));
        steps.sort(Comparator.comparingDouble((Step s)->s.seconds()+(((Enum<?>)s.lever()).name().equals("WATER")?.001:0)).thenComparingInt(Step::ordinal));
        var guides=new ArrayList<Target>();int i=0;
        for(Step step:steps){BlockPos pos=(BlockPos)step.lever().getClass().getField("leverPos").get(step.lever());BlockPos actual=(BlockPos)room.getClass().getMethod("relativeToActual",BlockPos.class).invoke(room,pos);
            String name=((Enum<?>)step.lever()).name();double remaining=start==0?step.seconds():(start+Math.round(step.seconds()*1000)-clock)/1000.;
            String text=start==0&&step.seconds()>0?"水開始後 "+String.format(Locale.ROOT,"%.2f 秒",step.seconds()):remaining<=0?"今クリック":String.format(Locale.ROOT,"%.2f 秒後",remaining);
            if(i==0)instruction=name+" · "+text+"（サーバー進行基準）";
            guides.add(new Target(Vec3.atCenterOf(actual).add(0,(++i)*.12,0),i+" · "+name+" · "+text));
        }
        targets=List.copyOf(guides);if(steps.isEmpty())instruction="レバー操作済み · 水の到達待ち";
    }
    static void world(){var mc=Minecraft.getInstance();if(!SkyPilot.get().config().dungeonSolver||mc.player==null||mc.level==null||!DungeonMap.inDungeon())return;
        try(var ignored=mc.levelRenderer.collectPerFrameRenderThreadGizmos()){Vec3 previous=mc.player.getEyePosition().add(0,-.3,0);int i=0;for(Target target:targets){int color=i++==0?0xFF55FF99:0xAA55DDFF;Gizmos.line(previous,target.pos(),color,2f);Gizmos.cuboid(new AABB(target.pos().add(-.2,-.2,-.2),target.pos().add(.2,.2,.2)),GizmoStyle.stroke(color,2f));Gizmos.billboardText(target.text(),target.pos().add(0,.5,0),TextGizmo.Style.forColorAndCentered(color));previous=target.pos();}}
    }
    static void hud(GuiGraphicsExtractor g){if(!SkyPilot.get().config().dungeonSolver||!DungeonMap.inDungeon()||Minecraft.getInstance().gui.screen()!=null)return;
        String title=heading.isEmpty()?"Dungeon Solver":heading,detail=failed?"追加HUDは非対応 · Skyblockerのガイドを確認":instruction.isEmpty()?"部屋を認識中 · F7デバイスは対象上にガイド表示":instruction;
        var font=Minecraft.getInstance().font;var b=UiTransforms.base("solver",g.guiWidth(),g.guiHeight());UiTransforms.begin(g,"solver");g.fill(b.x(),b.y(),b.x()+b.width(),b.y()+32,0xCC10182B);g.text(font,"SkyPilot · "+title,b.x()+4,b.y()+4,0xFFAA99FF,false);g.text(font,font.plainSubstrByWidth(detail,b.width()-8),b.x()+4,b.y()+18,0xFFFFFF99,false);UiTransforms.end(g);
    }
}
