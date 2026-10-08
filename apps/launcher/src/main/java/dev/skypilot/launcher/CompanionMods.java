package dev.skypilot.launcher;

import com.google.gson.Gson;
import dev.skypilot.mod.core.Lang;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in, pinned Fabric companions. Never replaces or enables a user's existing mod. */
public final class CompanionMods {
    public record Entry(String id, String name, String version, String file, String url,
                        String sha512, long size, String license, String homepage, List<String> requires) {}
    public record Catalog(String minecraft, List<Entry> mods) {}
    @FunctionalInterface public interface Download { byte[] get(String url, long max) throws IOException; }
    private CompanionMods() {}

    public static Catalog catalog() {
        try (var in = CompanionMods.class.getResourceAsStream("/companion-mods.json")) {
            if (in == null) throw new IOException("companion-mods.json missing");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Catalog.class);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static List<Entry> pack(boolean shaders) {
        return catalog().mods().stream().filter(e -> List.of("sodium", "ferritecore", "lithium").contains(e.id()) || shaders && e.id().equals("iris")).toList();
    }

    public static List<Entry> skyblockPack() {
        return catalog().mods().stream().filter(e -> List.of("skyblocker", "fabric-language-kotlin").contains(e.id())).toList();
    }

    /** Downloads and validates the complete set before adding any jars. Rolls back on failure. */
    public static synchronized int install(Path modsDir, List<Entry> entries, Map<String, String> builtins,
                                          Download download) throws IOException {
        return install(catalog(), modsDir, entries, builtins, download);
    }

    static int install(Catalog catalog, Path modsDir, List<Entry> entries, Map<String, String> builtins,
                       Download download) throws IOException {
        if (!catalog.minecraft().equals(builtins.get("minecraft"))) throw new IOException("Pack requires Minecraft " + catalog.minecraft());
        Files.createDirectories(modsDir);
        List<ModScan.Meta> existing = ModScan.scan(modsDir);
        List<Entry> missing = new ArrayList<>();
        for (Entry e : entries) {
            if (!catalog.mods().contains(e)) throw new IOException("Unknown companion");
            List<ModScan.Meta> same = existing.stream().filter(m -> m.provides().containsKey(e.id())).toList();
            if (!same.isEmpty()) {
                if (same.size() != 1 || !same.getFirst().enabled() || Files.size(same.getFirst().file()) != e.size()
                        || !Net.sha512(Files.readAllBytes(same.getFirst().file())).equals(e.sha512()))
                    throw new IOException(e.name() + ": another version or a disabled copy exists; manage it in Mods first");
            } else {
                if (Files.exists(modsDir.resolve(e.file())) || Files.exists(modsDir.resolve(e.file() + ".disabled")))
                    throw new IOException("File already exists: " + e.file());
                missing.add(e);
            }
            for (String dependency : e.requires())
                if (entries.stream().noneMatch(other -> other.id().equals(dependency))) throw new IOException("Pack needs " + dependency);
        }
        Path stage = Files.createTempDirectory(modsDir, ".skypilot-pack-");
        List<Path> added = new ArrayList<>();
        try {
            List<ModScan.Meta> combined = new ArrayList<>(existing);
            for (Entry e : missing) {
                byte[] bytes = download.get(e.url(), e.size());
                if (bytes.length != e.size() || !Net.sha512(bytes).equals(e.sha512())) throw new IOException(e.name() + " failed SHA-512 verification");
                Path file = stage.resolve(e.file());
                Files.write(file, bytes);
                var meta = ModScan.read(file, true);
                if (!meta.fabric() || !meta.id().equals(e.id())) throw new IOException(e.name() + " is not the expected Fabric mod");
                combined.add(meta);
            }
            var errors = ModCheck.check(combined, builtins, Lang.EN).stream().filter(i -> i.level() == ModCheck.Level.ERROR).toList();
            if (!errors.isEmpty()) throw new IOException(errors.getFirst().text());
            for (Entry e : missing) {
                Path target = modsDir.resolve(e.file());
                Files.move(stage.resolve(e.file()), target);
                added.add(target);
            }
            return added.size();
        } catch (IOException | RuntimeException e) {
            for (Path file : added) Files.deleteIfExists(file);
            throw e;
        } finally {
            try (var files = Files.list(stage)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
            Files.deleteIfExists(stage);
        }
    }
}
