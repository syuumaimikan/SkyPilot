package dev.skypilot.mod.core;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class GardenHelpersTest {
 @Test void invalidSavedPositionsCannotBreakRendering(){var c=new SkyPilotConfig();var p=new SkyPilotConfig.UiPosition();p.scale=Double.NaN;p.dx=Integer.MAX_VALUE;c.uiPositions.put("media",p);c.uiPositions.put("unknown",new SkyPilotConfig.UiPosition());c.sanitize();assertEquals(1,p.scale);assertEquals(10000,p.dx);assertFalse(c.uiPositions.containsKey("unknown"));c.uiPositions=null;c.sanitize();assertNotNull(c.uiPositions);}
 @Test void rewardsExcludeUntradeableXpAndCopper(){var o=VisitorOffer.parse(List.of("Items Required:","Wheat x64","","Rewards:","+4,000 Farming XP","+12 Copper","+1 Overgrown Grass","+100 Coins"));assertEquals(2,o.rewards().size());assertEquals(new VisitorOffer.Need("Overgrown Grass",1),o.rewards().getFirst());assertEquals(12,o.copper().getAsDouble());}
 @Test void pestPlotsAreScopedAndValidated(){assertEquals(Set.of(7,13,19),GardenPlots.pests(List.of("Visitors:","Plots: 8","","Pests:","Alive: 3","Plots: 7, 13, 19, 99","Spray: None")));assertTrue(GardenPlots.pests(List.of("Pests:","","Plots: 8")).isEmpty());assertEquals(12,GardenPlots.cell(0));assertEquals(16,GardenPlots.cell(7));assertEquals(-1,GardenPlots.cell(25));}
}
