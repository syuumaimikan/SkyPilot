package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PuzzleSolverTest {
    @Test void winsAndBlocks() {
        assertEquals(2,PuzzleSolver.ticTacToe("XX.OO....",'X'));
        assertEquals(2,PuzzleSolver.ticTacToe("OO.X...X.",'X'));
        assertEquals(4,PuzzleSolver.ticTacToe(".........",'X'));
        assertEquals(-1,PuzzleSolver.ticTacToe("XOXOXOOXX",'O'));
    }
    @Test void rejectsIllegalTurnsAndSymbols() {
        assertThrows(IllegalArgumentException.class,()->PuzzleSolver.ticTacToe("OO.......",'X'));
        assertThrows(IllegalArgumentException.class,()->PuzzleSolver.ticTacToe("X........",'X'));
        assertThrows(IllegalArgumentException.class,()->PuzzleSolver.ticTacToe("abcdefghi",'X'));
    }
    @Test void findsShortestMazeRouteAndDetectsImpossibleInput() {
        assertEquals("→ → ↓ ↓",PuzzleSolver.maze("S../##./..G"));
        assertEquals("No route",PuzzleSolver.maze("S#G"));
        assertThrows(IllegalArgumentException.class,()->PuzzleSolver.maze("SSG"));
        assertThrows(IllegalArgumentException.class,()->PuzzleSolver.maze("S../.G"));
    }
    @Test void sudokuPreservesCluesAndRejectsConflicts(){String puzzle="530070000600195000098000060800060003400803001700020006060000280000419005000080079";String solution=PuzzleSolver.sudoku(puzzle).replace("\n","");assertEquals(81,solution.length());for(int i=0;i<81;i++)if(puzzle.charAt(i)!='0')assertEquals(puzzle.charAt(i),solution.charAt(i));for(int r=0;r<9;r++){assertEquals(9,solution.substring(r*9,r*9+9).chars().distinct().count());}assertThrows(IllegalArgumentException.class,()->PuzzleSolver.sudoku("11"+"0".repeat(79)));assertThrows(IllegalArgumentException.class,()->PuzzleSolver.sudoku("0".repeat(80)));}
    @Test void cosmeticsAreBoundedAndOptIn() {
        var config=new SkyPilotConfig().sanitize();
        assertTrue(CosmeticMesh.build(config.appearance).isEmpty());
        config.appearance.cape="aurora";config.appearance.accessory="wings";
        assertTrue(CosmeticMesh.build(config.appearance).size()>160);
        config.sanitize();assertEquals("wings",config.appearance.back);
        config.appearance.cape="bad";config.appearance.accessory=null;
        config.sanitize();assertEquals("none",config.appearance.cape);assertEquals("none",config.appearance.accessory);
    }
}
