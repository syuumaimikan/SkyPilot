package dev.skypilot.mod.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.*;

/** Local TTF only. Vanilla glyph references preserve Japanese and game symbols. */
public final class FontPack {
    public static final String FILE = "skypilot-local-font.zip";
    private FontPack() {}
    public static Path create(Path directory, Path font, int major, int minor, double size) throws IOException {
        if (!font.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf")
                || !Files.isRegularFile(font) || Files.size(font)>16*1024*1024 || Files.size(font)<12)
            throw new IOException("Choose a local .ttf file smaller than 16 MB / 16MB 未満の TTF を選択してください");
        if (!Double.isFinite(size) || size<6 || size>12) throw new IOException("Font size must be 6–12");
        byte[] bytes=Files.readAllBytes(font);
        if (!((bytes[0]==0 && bytes[1]==1 && bytes[2]==0 && bytes[3]==0)
                || new String(bytes,0,4,StandardCharsets.US_ASCII).equals("true")))
            throw new IOException("TrueType outlines required; TTC/OTF are unsupported / TTC・OTF は未対応です");
        try { java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,new java.io.ByteArrayInputStream(bytes)); }
        catch(java.awt.FontFormatException invalid){throw new IOException("Invalid TTF / TTF の形式が不正です",invalid);}
        Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,"skypilot-font-",".tmp");
        try {
            try(var zip=new ZipOutputStream(Files.newOutputStream(temp))) {
                entry(zip,"pack.mcmeta",("{\"pack\":{\"description\":\"SkyPilot local font\",\"min_format\":["+major+","+minor+"],\"max_format\":["+major+","+minor+"]}}").getBytes(StandardCharsets.UTF_8));
                entry(zip,"assets/skypilot/font/local.ttf",bytes);
                String definition=String.format(Locale.ROOT,"{\"providers\":[{\"type\":\"reference\",\"id\":\"minecraft:include/space\"},{\"type\":\"ttf\",\"file\":\"skypilot:local.ttf\",\"shift\":[0,0],\"size\":%.1f,\"oversample\":2,\"skip\":\"\\uE000-\\uF8FF\"},{\"type\":\"reference\",\"id\":\"minecraft:include/default\"},{\"type\":\"reference\",\"id\":\"minecraft:include/unifont\"}]}",size);
                String symbols=new String(java.util.stream.IntStream.rangeClosed(0xE000,0xF8FF).toArray(),0,0x1900);
                definition=definition.replace("\\uE000-\\uF8FF",symbols);
                entry(zip,"assets/minecraft/font/default.json",definition.getBytes(StandardCharsets.UTF_8));
            }
            Path target=directory.resolve(FILE);
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally { Files.deleteIfExists(temp); }
    }
    private static void entry(ZipOutputStream zip,String name,byte[] value)throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(value);zip.closeEntry();
    }
}
