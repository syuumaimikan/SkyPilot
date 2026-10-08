package dev.skypilot.mod.core;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ExperimentMemoryTest {
    @Test void remembersOnlyObservedMatchingCards(){var m=new ExperimentMemory();assertNull(m.card(10));assertEquals(-1,m.pair(10));m.reveal(10,"XP 1000");m.reveal(12,"XP 2000");assertEquals(-1,m.pair(10));m.reveal(20,"XP 1000");assertEquals(20,m.pair(10));assertEquals(10,m.pair(20));m.reveal(20,"XP 3000");assertEquals(-1,m.pair(10));}
    @Test void repeatedFlashesRequireAnOffTransition(){var m=new ExperimentMemory();m.flash(10);m.flash(10);m.flash(-1);m.flash(10);m.flash(12);assertEquals(List.of(10,10,12),m.sequence());m.number(10,3);m.startRound();assertTrue(m.sequence().isEmpty());assertNull(m.number(10));m.flash(10);assertEquals(List.of(10),m.sequence());}
    @Test void separateWindowsAndInvalidNumbers(){var a=new ExperimentMemory();a.reveal(10,"Book");var b=new ExperimentMemory();assertNull(b.card(10));b.number(10,0);b.number(12,50);assertNull(b.number(10));assertNull(b.number(12));b.number(12,49);assertEquals(49,b.number(12));}
}
