package dev.skypilot.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModDeletionTest {
    @TempDir Path temp;
    @Test void disablingPreservesBothExistingCopies() throws Exception {
        Path active=Files.writeString(temp.resolve("old.jar"),"active");
        Path disabled=Files.writeString(temp.resolve("old.jar.disabled"),"prior disabled");
        Files.writeString(temp.resolve("old.backup-1.jar.disabled"),"prior backup");
        Path moved=Mods.toggle(new Mods.ModFile(active,"skypilot","SkyPilot","old",true));
        assertEquals("old.backup-2.jar.disabled",moved.getFileName().toString());
        assertEquals("active",Files.readString(moved));assertEquals("prior disabled",Files.readString(disabled));assertFalse(Files.exists(active));
    }
    @Test void deletesOnlySelectedEnabledOrDisabledFile() throws Exception {
        Path mods=Files.createDirectory(temp.resolve("mods"));
        Path old=Files.writeString(mods.resolve("old.jar.disabled"),"old");
        Path current=Files.writeString(mods.resolve("current.jar"),"current");
        Mods.delete(mods,old);assertFalse(Files.exists(old));assertEquals("current",Files.readString(current));
        Mods.delete(mods,current);assertFalse(Files.exists(current));assertTrue(Files.isDirectory(mods));
    }
    @Test void rejectsOtherFoldersAndNonModFiles() throws Exception {
        Path mods=Files.createDirectory(temp.resolve("mods"));
        Path outside=Files.writeString(temp.resolve("outside.jar"),"keep");
        Path notes=Files.writeString(mods.resolve("notes.txt"),"keep");
        Path directory=Files.createDirectory(mods.resolve("directory.jar"));
        assertThrows(IOException.class,()->Mods.delete(mods,outside));
        assertThrows(IOException.class,()->Mods.delete(mods,notes));
        assertThrows(IOException.class,()->Mods.delete(mods,directory));
        assertTrue(Files.exists(outside));assertTrue(Files.exists(notes));assertTrue(Files.isDirectory(directory));
    }
    @Test void rejectsMissingFiles() throws Exception {
        Path mods=Files.createDirectory(temp.resolve("mods"));
        assertThrows(IOException.class,()->Mods.delete(mods,mods.resolve("missing.jar")));
    }
}
