package dev.skypilot.mod.core;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TerminalPlanTest {
    private static TerminalPlan.Cell cell(int slot,String color,int count){return new TerminalPlan.Cell(slot,color+"_stained_glass_pane",color,count,false);}
    @Test void numericOrderUsesVisibleRemainingCounts(){assertEquals(List.of(new TerminalPlan.Click(11,0,1),new TerminalPlan.Click(10,0,1)),TerminalPlan.solve("Click in order!",List.of(cell(10,"red",9),cell(11,"red",3),cell(12,"lime",1))));}
    @Test void sameColorFindsMinimumTotalIncludingRightClicks(){var result=TerminalPlan.solve("Change all to same color!",List.of(cell(10,"red",1),cell(11,"red",1),cell(12,"blue",1),cell(13,"yellow",1)));assertEquals(List.of(new TerminalPlan.Click(12,0,1),new TerminalPlan.Click(13,1,2)),result);}
    @Test void completedAndBorderItemsNeverSelected(){assertEquals(List.of(new TerminalPlan.Click(10,0,1)),TerminalPlan.solve("Correct all the panes!",List.of(cell(0,"red",1),cell(9,"red",1),cell(10,"red",1),new TerminalPlan.Cell(11,"red_stained_glass_pane","",1,true))));}
    @Test void unknownTitlesDoNotGuess(){assertFalse(TerminalPlan.recognizes("Some inventory"));assertTrue(TerminalPlan.solve("Unknown",List.of(cell(10,"red",1))).isEmpty());}
    @Test void bottomBorderAndUnexpectedLayoutIgnored(){assertTrue(TerminalPlan.solve("Correct all the panes!",List.of(cell(19,"red",1)),3).isEmpty());assertTrue(TerminalPlan.solve("Correct all the panes!",List.of(cell(10,"red",1)),7).isEmpty());}
    @Test void lettersAndColors(){assertEquals(1,TerminalPlan.solve("What starts with: 'A'?",List.of(new TerminalPlan.Cell(10,"apple","Apple",1,false),new TerminalPlan.Cell(11,"arrow","Arrow",1,true))).size());assertEquals(1,TerminalPlan.solve("Select all the SILVER items!",List.of(new TerminalPlan.Cell(10,"light_gray_wool","Light Gray Wool",1,false))).size());}
}
