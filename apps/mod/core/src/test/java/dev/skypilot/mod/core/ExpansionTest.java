package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExpansionTest {
    @Test void adaptiveBudgetHasDeadBandAndRecoversWithinLimits() {
        assertEquals(132,PerfRules.adaptiveBudget(150,150,30,60));
        assertEquals(132,PerfRules.adaptiveBudget(132,150,60,60));
        assertEquals(139,PerfRules.adaptiveBudget(132,150,90,60));
        assertEquals(20,PerfRules.adaptiveBudget(20,150,1,60));
        assertEquals(150,PerfRules.adaptiveBudget(149,150,90,60));
        assertEquals(40,PerfRules.adaptiveBudget(150,40,0,60));
    }
    @Test void frameSamplesKeepStallsAndEvictOldFrames() {
        var frames=new FrameSamples(100);
        for(int i=0;i<98;i++)frames.add(10);
        frames.add(50);frames.add(100);
        assertEquals(10,frames.percentile(.95));assertEquals(50,frames.percentile(.99));assertEquals(20,frames.slowFrameFps());
        for(int i=0;i<100;i++)frames.add(5);
        assertEquals(5,frames.percentile(.99));
        frames.add(Double.NaN);frames.add(-2);assertEquals(100,frames.count());
        frames.clear();assertEquals(0,frames.slowFrameFps());
    }
    @Test void comparesOnlyKnownMatchingStatUnits() {
        var deltas=ItemStatComparison.compare(List.of("§cDamage: +1,250 (+30)","❁ Strength: +210", "Crit Chance: +12%", "Defense: +45", "Health: +100%"),
                List.of("Damage: +1,000", "Strength: +150", "Crit Chance: +15%", "Health: +100"));
        assertEquals(3,deltas.size());assertEquals(250,deltas.get(0).amount());assertEquals(60,deltas.get(1).amount());
        assertEquals(-3,deltas.get(2).amount());assertTrue(deltas.get(2).percent());
        assertTrue(ItemStatComparison.compare(List.of("Strength: 12"),List.of()).isEmpty());
        assertTrue(ItemStatComparison.parse(List.of("Damage: NaN","Damage: Infinity","Damage: 20 coins","Bonus: +200")).keySet().stream().noneMatch(n->n.equals("Bonus")));
    }
    @Test void newSettingsAreBoundedAndOptIn() {
        var c=new SkyPilotConfig();c.perf.targetFps=-1;c.perf.particleDistance=999;c.visuals.ambientDensity=999;c.sanitize();
        assertEquals(30,c.perf.targetFps);assertEquals(128,c.perf.particleDistance);assertEquals(4,c.visuals.ambientDensity);
        assertFalse(c.visuals.landingDust||c.visuals.rainSplash||c.visuals.fireflies||c.visuals.caveDust||c.visuals.underwaterBubbles||c.visuals.sprintTrail);
        c.utility.armor=c.utility.effects=c.utility.memory=c.utility.gc=c.utility.inventoryWarning=c.utility.durabilityWarning=c.utility.itemComparison=true;
        c.utility.clear();assertFalse(c.utility.armor||c.utility.effects||c.utility.memory||c.utility.gc||c.utility.inventoryWarning||c.utility.durabilityWarning||c.utility.itemComparison);
    }
}
