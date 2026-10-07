package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.FocusTimer;
import dev.skypilot.mod.core.TileGame;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Session state remains available when a tool screen is closed. */
final class DeskRuntime {
    static final FocusTimer FOCUS=new FocusTimer();
    static final TileGame TILES=new TileGame(ThreadLocalRandom.current());
    static long now(){return System.nanoTime()/1_000_000;}
    static void tick(){if(FOCUS.finished(now())){var l=SkyPilot.get().lang();Feat.toast(new ItemStack(Items.CLOCK),l.t("Focus complete","集中タイマー終了"),0xFF55D6D2,l.t("Time for a short break.","短い休憩を取りましょう。"));}}
}
