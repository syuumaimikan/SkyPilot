package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeskToolsTest {
    @Test void pausedCountdownDoesNotElapseAndCompletionIsAnnouncedOnce(){var timer=new FocusTimer();timer.start(1,100);timer.toggle(10100);assertEquals(50000,timer.remaining(300000));timer.toggle(300000);assertEquals("00:01",timer.display(349100));assertTrue(timer.finished(350000));assertFalse(timer.finished(400000));assertEquals(1,timer.completed());assertThrows(IllegalArgumentException.class,()->timer.start(0,0));}
    @Test void eachTileMergesOnceAndUndoRestoresTheBoard(){int[] cells=new int[16];Arrays.fill(cells,0,4,2);var game=new TileGame(new Random(2),cells);assertTrue(game.move(0));assertEquals(4,game.cells()[0]);assertEquals(4,game.cells()[1]);assertEquals(8,game.score());game.undo();assertArrayEquals(cells,game.cells());assertEquals(0,game.score());assertFalse(game.canUndo());}
    @Test void invalidMoveDoesNotSpawnAndVerticalMovesWork(){int[] cells=new int[16];cells[0]=2;var game=new TileGame(new Random(1),cells);assertFalse(game.move(0));assertArrayEquals(cells,game.cells());assertTrue(game.move(3));assertEquals(2,game.cells()[12]);assertFalse(game.over());assertThrows(IllegalArgumentException.class,()->game.move(4));}
    @Test void presetsFavoritesAndRandomOutfitsAreSafeAndPersistent(){var cfg=new SkyPilotConfig();var a=cfg.appearance;WardrobeTools.randomize(a,new Random(1));assertTrue(CosmeticCatalog.valid("cape",a.cape));assertTrue(CosmeticCatalog.valid("hat",a.hat));assertTrue(CosmeticCatalog.valid("back",a.back));WardrobeTools.remember(a,"First");String cape=a.cape;a.cape="none";WardrobeTools.apply(a.outfits.getFirst(),a);assertEquals(cape,a.cape);WardrobeTools.favorite(a,"aurora");assertEquals(List.of("aurora"),a.favorites);WardrobeTools.favorite(a,"aurora");assertTrue(a.favorites.isEmpty());a.favorites.addAll(Arrays.asList(null,"bad","aurora","aurora"));a.outfits.add(null);cfg.sanitize();assertEquals(List.of("aurora"),a.favorites);assertEquals(1,a.outfits.size());assertThrows(IllegalArgumentException.class,()->WardrobeTools.remember(a," "));}
    @Test void fullOutfitCollectionNeverDiscardsAnExistingLook(){var a=new SkyPilotConfig.Appearance();for(int i=0;i<8;i++)WardrobeTools.remember(a,"Look "+i);assertThrows(IllegalArgumentException.class,()->WardrobeTools.remember(a,"Ninth"));assertEquals("Look 0",a.outfits.getFirst().name);a.cape="tide";WardrobeTools.remember(a,"Look 0");assertEquals(8,a.outfits.size());assertEquals("tide",a.outfits.getLast().cape);}

    @Test void malformedOutfitNamesAreNormalizedBeforeDeduplication(){var a=new SkyPilotConfig.Appearance();for(int i=0;i<10;i++){var o=new WardrobeTools.Outfit();o.name=i<3?" Same ":"Look "+i;a.outfits.add(o);}WardrobeTools.sanitize(a);assertEquals(8,a.outfits.size());assertEquals(1,a.outfits.stream().filter(o->o.name.equals("Same")).count());assertThrows(IllegalArgumentException.class,()->WardrobeTools.remember(a,null));}

}
