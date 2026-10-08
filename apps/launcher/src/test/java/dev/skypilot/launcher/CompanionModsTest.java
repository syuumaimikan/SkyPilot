package dev.skypilot.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class CompanionModsTest {
    @TempDir Path temp;
	@Test void downloadedPinnedCompanionsInstallTogether() throws Exception {
		String directory=System.getenv("SKYPILOT_COMPANION_TEST_DIR");
		org.junit.jupiter.api.Assumptions.assumeTrue(directory!=null);
		var files=Path.of(directory);var catalog=CompanionMods.catalog();
		var pins=Pins.load();
		Map<String,String> builtins=Map.of("minecraft",pins.minecraft(),"fabricloader",pins.loader(),"java","25","fabric-api",pins.fabricApi());
		assertEquals(6,CompanionMods.install(temp,catalog.mods(),builtins,(url,max)->{
			var entry=catalog.mods().stream().filter(e->e.url().equals(url)).findFirst().orElseThrow();
			return Files.readAllBytes(files.resolve(entry.file()));
		}));
		assertEquals(6,ModScan.scan(temp).size());
	}
    private static byte[] jar(String id, String minecraft) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write(("{\"schemaVersion\":1,\"id\":\""+id+"\",\"version\":\"1.0.0\",\"depends\":{\"minecraft\":\""+minecraft+"\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
        return bytes.toByteArray();
    }
    private static CompanionMods.Entry entry(String id,byte[] bytes) {
        return new CompanionMods.Entry(id,id,"1.0.0",id+".jar","https://cdn.modrinth.com/"+id,Net.sha512(bytes),bytes.length,"MIT","https://modrinth.com",List.of());
    }
    @Test void shippedCatalogIsPinnedAndPacksContainTheirDependencies() {
        var catalog=CompanionMods.catalog();assertEquals("26.2",catalog.minecraft());assertEquals(6,catalog.mods().size());
        assertEquals(3,CompanionMods.pack(false).size());assertEquals(4,CompanionMods.pack(true).size());assertEquals(2,CompanionMods.skyblockPack().size());
        for(var e:catalog.mods()){assertTrue(e.size()>0);assertTrue(e.sha512().matches("[a-f0-9]{128}"));assertTrue(e.file().endsWith(".jar"));assertFalse(e.file().contains("/"));assertFalse(e.file().contains("\\"));assertNotNull(e.version());assertNotNull(e.license());assertTrue(e.url().startsWith("https://cdn.modrinth.com/"));}
        var iris=catalog.mods().stream().filter(e->e.id().equals("iris")).findFirst().orElseThrow();assertEquals(List.of("sodium"),iris.requires());
    }
    @Test void installsOnceAndDoesNotOverwriteDisabledOrDifferentCopies() throws Exception {
        byte[] bytes=jar("testmod","26.2");var e=entry("testmod",bytes);var cat=new CompanionMods.Catalog("26.2",List.of(e));
        assertEquals(1,CompanionMods.install(cat,temp,List.of(e),Map.of("minecraft","26.2"),(u,m)->bytes));
        assertEquals(0,CompanionMods.install(cat,temp,List.of(e),Map.of("minecraft","26.2"),(u,m)->{throw new AssertionError("No download on repeat");}));
        Files.move(temp.resolve(e.file()),temp.resolve(e.file()+".disabled"));
        assertThrows(IOException.class,()->CompanionMods.install(cat,temp,List.of(e),Map.of("minecraft","26.2"),(u,m)->bytes));
        assertTrue(Files.exists(temp.resolve(e.file()+".disabled")));
    }
    @Test void failedSecondDownloadCommitsNothing() throws Exception {
        byte[] a=jar("first","26.2"),b=jar("second","26.2");var one=entry("first",a);var two=entry("second",b);var entries=List.of(one,two);var cat=new CompanionMods.Catalog("26.2",entries);
        assertThrows(IOException.class,()->CompanionMods.install(cat,temp,entries,Map.of("minecraft","26.2"),(u,m)->u.endsWith("first")?a:new byte[]{0}));
        try(var files=Files.list(temp)){assertEquals(0,files.count());}
    }
    @Test void refusesWrongGameAndMetadataEvenWhenHashMatches() throws Exception {
        byte[] bytes=jar("wrongid","26.2");var e=entry("expected",bytes);var cat=new CompanionMods.Catalog("26.2",List.of(e));
        assertThrows(IOException.class,()->CompanionMods.install(cat,temp,List.of(e),Map.of("minecraft","26.1"),(u,m)->bytes));
        assertThrows(IOException.class,()->CompanionMods.install(cat,temp,List.of(e),Map.of("minecraft","26.2"),(u,m)->bytes));
        byte[] old=jar("expected","1.8.9");var incompatible=entry("expected",old);var oldCat=new CompanionMods.Catalog("26.2",List.of(incompatible));
        assertThrows(IOException.class,()->CompanionMods.install(oldCat,temp,List.of(incompatible),Map.of("minecraft","26.2"),(u,m)->old));
    }
}
