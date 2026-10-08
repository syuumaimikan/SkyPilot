package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PestAndFontTest {
    @TempDir Path temp;
    @Test void pestHealthLabelsDoNotMatchTrapsOrChat(){
        assertEquals("Beetle",PestNames.identify("§c[Lv1] Beetle §a5,000/5,000❤").orElseThrow());
        assertEquals("Lunar Moth",PestNames.identify("Lunar Moth 20k❤").orElseThrow());
        assertEquals("Field Mouse",PestNames.identify("Field Mouse").orElseThrow());
        for(String other:new String[]{"PEST TRAP #3","You killed a Rat!","Rat Pet","Rat 0❤ extra","Garden Visitor", "Pest"})assertTrue(PestNames.identify(other).isEmpty(),other);
    }
    @Test void invalidFontsDoNotOverwriteExistingPack() throws Exception {
        var target=temp.resolve(FontPack.FILE);Files.writeString(target,"existing");
        var invalid=temp.resolve("broken.ttf");Files.write(invalid,new byte[20]);
        assertThrows(java.io.IOException.class,()->FontPack.create(temp,invalid,86,0,9));
        assertEquals("existing",Files.readString(target));
        assertThrows(java.io.IOException.class,()->FontPack.create(temp,temp.resolve("missing.ttf"),86,0,9));
    }
    @Test void generatedPackKeepsFallbacksAndPrivateUseSymbols() throws Exception {
        var ttf=Path.of("C:/Windows/Fonts/segoeui.ttf");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(ttf));
        var generated=FontPack.create(temp,ttf,86,0,9);
        try(var zip=new ZipFile(generated.toFile())){
            String json=new String(zip.getInputStream(zip.getEntry("assets/minecraft/font/default.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var parsed=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            var providers=parsed.getAsJsonArray("providers");
            assertEquals(4,providers.size());
            assertEquals(0x1900,providers.get(1).getAsJsonObject().get("skip").getAsString().length());
            assertTrue(json.contains("minecraft:include/unifont"));assertTrue(json.contains("minecraft:include/default"));
            assertNotNull(zip.getEntry("assets/skypilot/font/local.ttf"));
        }
    }
}
