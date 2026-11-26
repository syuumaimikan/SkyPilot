package dev.skypilot.mod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Loads, imports, deletes, and scaffolds user-created 3D cosmetics stored under
 * {@code <configDir>/cosmetics/}.
 *
 * <p>Users can drop standard Blockbench {@code .bbmodel} 3D model files (with embedded PNG textures
 * and optional keyframe animations), SkyPilot {@code .json} rig files, or custom {@code .png} cloak
 * textures into {@code config/skypilot/cosmetics/<slot>/} (where {@code <slot>} is one of
 * {@code cape}, {@code back}, {@code hat}, {@code aura}, {@code pet}, {@code companion}).
 */
public final class CustomCosmetics {
    private static final List<String> TEMPLATE_SOURCES = List.of(
            "aurora", "wings", "crown", "face_kitsune_half", "sh_sakura_muffler",
            "waist_nine_tails", "feet_hermes_wings", "gale_wind_aura", "pet_bumblebee", "comp_shiba_inu"
    );

    private static volatile List<CosmeticCatalog.Item> customItems = List.of();
    private static volatile Map<String, Path> customFiles = Map.of();
    private static volatile Path lastRoot;

    private CustomCosmetics() {}

    /** Resolves {@code <configDir>/cosmetics}. */
    public static Path directory(Path configDir) {
        return configDir.resolve("cosmetics");
    }

    /** Ensures the custom cosmetics folder tree exists and returns it. */
    public static Path ensureDirectory(Path configDir) throws IOException {
        Path root = directory(configDir);
        Files.createDirectories(root);
        for (String slot : CosmeticCatalog.SLOTS) {
            Files.createDirectories(root.resolve(slot));
        }
        return root;
    }

    /** Reloads all user-created 3D models from {@code <configDir>/cosmetics/}. */
    public static synchronized List<CosmeticCatalog.Item> reload(Path configDir) {
        lastRoot = configDir;
        CosmeticRig.clearCustomModels();
        if (configDir == null) {
            customItems = List.of();
            customFiles = Map.of();
            return customItems;
        }
        Path root = directory(configDir);
        if (!Files.isDirectory(root)) {
            customItems = List.of();
            customFiles = Map.of();
            return customItems;
        }

        List<CosmeticCatalog.Item> loaded = new ArrayList<>();
        Map<String, Path> fileMap = new LinkedHashMap<>();
        Set<String> usedIds = new HashSet<>();

        try (var stream = Files.walk(root, 3)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(p -> !isInTemplates(root, p))
                    .sorted()
                    .toList();

            for (Path file : files) {
                String fname = file.getFileName().toString();
                String lower = fname.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".bbmodel") && !lower.endsWith(".json") && !lower.endsWith(".png")) continue;
                // Skip sibling texture PNGs that belong to a .bbmodel/.json in the same directory
                if (lower.endsWith(".png") && hasMatchingModel(file)) continue;

                String inferredSlot = inferSlotFromPath(root, file);
                if (lower.endsWith(".png")) {
                    // Only load standalone PNGs as custom cloaks (either in cape/ or root)
                    if (!"cape".equals(inferredSlot)) continue;
                    CosmeticCatalog.Item item = loadPngCloak(file, usedIds);
                    if (item != null) {
                        loaded.add(item);
                        fileMap.put(item.id(), file);
                    }
                } else {
                    CosmeticCatalog.Item item = loadBbModelFile(file, inferredSlot, usedIds);
                    if (item != null) {
                        loaded.add(item);
                        fileMap.put(item.id(), file);
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep whatever models parsed cleanly
        }

        customItems = List.copyOf(loaded);
        customFiles = Collections.unmodifiableMap(fileMap);
        return customItems;
    }

    public static List<CosmeticCatalog.Item> items() {
        return customItems;
    }

    public static Path fileFor(String id) {
        return id == null ? null : customFiles.get(id);
    }

    public static Path lastConfigDir() {
        return lastRoot;
    }

    /**
     * Imports a user's {@code .bbmodel}, {@code .json}, or {@code .png} file into
     * {@code <configDir>/cosmetics/<slot>/}, reloads the registry, and returns the imported item.
     */
    public static CosmeticCatalog.Item importModel(Path configDir, Path sourceFile, String slot, String displayName) throws IOException {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new IOException("Model file not found: " + sourceFile);
        }
        String cleanSlot = CosmeticCatalog.SLOTS.contains(slot) ? slot : "hat";
        String origName = sourceFile.getFileName().toString();
        String lower = origName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".bbmodel") && !lower.endsWith(".json") && !lower.endsWith(".png")) {
            throw new IllegalArgumentException("Unsupported format. Use .bbmodel, .json, or .png (cloak).");
        }
        if (lower.endsWith(".png")) cleanSlot = "cape";

        // Validate before copying
        if (lower.endsWith(".bbmodel") || lower.endsWith(".json")) {
            String raw = Files.readString(sourceFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            CosmeticRig.Model parsed = CosmeticRig.parseBbModel(obj, "validate", cleanSlot, sourceFile.getParent());
            if (parsed.totalCubes() <= 0) {
                throw new IllegalArgumentException("3D model contains no cubes/elements.");
            }
        }

        Path slotDir = ensureDirectory(configDir).resolve(cleanSlot);
        String ext = lower.endsWith(".bbmodel") ? ".bbmodel" : (lower.endsWith(".json") ? ".json" : ".png");
        String baseSlug = slugify(displayName != null && !displayName.isBlank() ? displayName : stripExt(origName));
        if (baseSlug.isEmpty()) baseSlug = "custom_model";
        Path target = slotDir.resolve(baseSlug + ext);
        int suffix = 2;
        while (Files.exists(target)) {
            target = slotDir.resolve(baseSlug + "_" + (suffix++) + ext);
        }
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);

        // If a sibling PNG texture exists next to the imported model, copy it too
        if (!ext.equals(".png") && sourceFile.getParent() != null) {
            String srcStem = stripExt(origName);
            for (String texSuffix : List.of(".png", "_glow.png")) {
                Path sib = sourceFile.getParent().resolve(srcStem + texSuffix);
                if (Files.isRegularFile(sib)) {
                    String dstStem = stripExt(target.getFileName().toString());
                    Files.copy(sib, slotDir.resolve(dstStem + texSuffix), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        List<CosmeticCatalog.Item> after = reload(configDir);
        for (var entry : customFiles.entrySet()) {
            if (entry.getValue().toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                String foundId = entry.getKey();
                return after.stream().filter(i -> i.id().equals(foundId)).findFirst().orElse(null);
            }
        }
        return after.isEmpty() ? null : after.get(after.size() - 1);
    }

    /** Deletes a user-imported custom cosmetic file and reloads. */
    public static boolean deleteCustom(Path configDir, String id) throws IOException {
        Path p = customFiles.get(id);
        if (p == null) return false;
        Files.deleteIfExists(p);
        reload(configDir);
        return true;
    }

    /**
     * Exports ready-to-edit Blockbench {@code .bbmodel} starter templates for all 6 slots into
     * {@code <configDir>/cosmetics/templates/} so users can open them directly in Blockbench.
     */
    public static Path exportTemplates(Path configDir) throws IOException {
        Path root = ensureDirectory(configDir);
        Path tplDir = root.resolve("templates");
        Files.createDirectories(tplDir);
        for (String id : TEMPLATE_SOURCES) {
            CosmeticCatalog.Item item = CosmeticCatalog.byId(id);
            if (item == null) continue;
            String res = "/assets/skypilot/cosmetics/models/" + id + ".bbmodel";
            try (InputStream in = CustomCosmetics.class.getResourceAsStream(res)) {
                if (in != null) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    // Embed the PNG texture into the template so opening it in Blockbench immediately shows textures!
                    CosmeticRig.Model rig = CosmeticRig.load(id);
                    if (rig != null && rig.textureClasspath() != null) {
                        try (InputStream tin = CustomCosmetics.class.getResourceAsStream(rig.textureClasspath())) {
                            if (tin != null) {
                                String b64 = Base64.getEncoder().encodeToString(tin.readAllBytes());
                                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                                JsonObject tex = new JsonObject();
                                tex.addProperty("name", id + ".png");
                                tex.addProperty("id", "0");
                                tex.addProperty("source", "data:image/png;base64," + b64);
                                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                                arr.add(tex);
                                obj.add("textures", arr);
                                content = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj);
                            }
                        }
                    }
                    Files.writeString(tplDir.resolve("template_" + item.slot() + ".bbmodel"), content, StandardCharsets.UTF_8);
                }
            }
        }
        String guide = """
                SkyPilot Custom 3D Wardrobe Guide / 自作3Dコスメティック作成ガイド
                ================================================================

                [JA]
                1. templates/ 内の .bbmodel テンプレートを Blockbench で開いて編集（または Bedrock Entity 形式で新規作成）します。
                2. テクスチャは Blockbench 内でペイントしてそのまま .bbmodel に保存すると自動埋め込みされます。
                   （別ファイルにする場合は同じフォルダに <モデル名>.png / <モデル名>_glow.png を置けます）
                3. 完成した .bbmodel ファイルを以下の対応フォルダへ保存（またはランチャーの「3Dモデル読込」ボタンで選択）します:
                   - cape/       クローク（PNG画像単体の投入もOK！）
                   - back/       ウィング・背面装備
                   - hat/        ヘッドウェア・帽子・ヘイロー
                   - aura/       オーラ（周囲の3Dエフェクト）
                   - pet/        浮遊ペット（肩の横）
                   - companion/  コンパニオン（足元で歩行）
                4. ワードローブ画面で「再読込 (Reload)」を押すと即座に試着・装備できます！
                ※ ボーン名に wing_l / wing_r / halo / ear_l / ear_r / gear / orbit を含めるか、Blockbench の Animate タブでキーフレームを作成すると自動アニメーションします。

                [EN]
                1. Open any .bbmodel file in templates/ with Blockbench (or create a Bedrock Entity model).
                2. Save your .bbmodel with its embedded texture into cosmetics/<slot>/ (cape, back, hat, aura, pet, companion),
                   or click "Import 3D Model" in SkyPilot Launcher's Wardrobe page.
                3. Click "Reload" in the Wardrobe to preview and equip your creation immediately!
                """;
        Files.writeString(tplDir.resolve("README.txt"), guide, StandardCharsets.UTF_8);
        return tplDir;
    }

    private static CosmeticCatalog.Item loadBbModelFile(Path file, String inferredSlot, Set<String> usedIds) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject sp = obj.has("skypilot") && obj.get("skypilot").isJsonObject() ? obj.getAsJsonObject("skypilot") : null;
            String stem = stripExt(file.getFileName().toString());
            String slot = (sp != null && sp.has("slot")) ? sp.get("slot").getAsString() : inferredSlot;
            if (!CosmeticCatalog.SLOTS.contains(slot)) slot = "hat";

            String baseId = "custom_" + slugify(stem);
            if (baseId.equals("custom_")) baseId = "custom_model";
            String id = baseId;
            int n = 2;
            while (!usedIds.add(id)) id = baseId + "_" + (n++);

            CosmeticRig.Model model = CosmeticRig.parseBbModel(obj, id, slot, file.getParent());
            model.id = id;
            model.slot = slot;
            model.custom = true;
            if (model.totalCubes() <= 0) return null;

            // Load sibling PNG textures if not embedded inside .bbmodel
            if (model.embeddedTextureBytes == null && file.getParent() != null) {
                Path sib = file.getParent().resolve(stem + ".png");
                if (Files.isRegularFile(sib)) model.embeddedTextureBytes = Files.readAllBytes(sib);
            }
            if (model.embeddedEmissiveBytes == null && file.getParent() != null) {
                Path sibGlow = file.getParent().resolve(stem + "_glow.png");
                if (Files.isRegularFile(sibGlow)) {
                    model.embeddedEmissiveBytes = Files.readAllBytes(sibGlow);
                    model.emissive = true;
                }
            }

            CosmeticRig.registerCustom(id, model);
            String title = (sp != null && sp.has("name")) ? sp.get("name").getAsString()
                    : (obj.has("name") && !obj.get("name").getAsString().isBlank() ? obj.get("name").getAsString() : prettifyName(stem));
            String ja = (sp != null && sp.has("ja")) ? sp.get("ja").getAsString() : title;
            int color = (sp != null && sp.has("color")) ? (sp.get("color").getAsInt() & 0xFFFFFF) : 0x55D6D2;
            boolean animated = !model.clips.isEmpty() || model.animFrames > 1;
            return new CosmeticCatalog.Item(
                    id, slot, title, ja,
                    "Custom 3D model (" + file.getFileName() + ")",
                    "自作3Dモデル (" + file.getFileName() + ")",
                    color, "custom", animated, model.scalable, model.emissive, true
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CosmeticCatalog.Item loadPngCloak(Path file, Set<String> usedIds) {
        try {
            byte[] png = Files.readAllBytes(file);
            if (png.length < 32) return null;
            String stem = stripExt(file.getFileName().toString());
            String baseId = "custom_cape_" + slugify(stem);
            String id = baseId;
            int n = 2;
            while (!usedIds.add(id)) id = baseId + "_" + (n++);

            // Clone our 8-segment silky cloth cape .bbmodel rig and bind the user's PNG texture!
            String tpl = "/assets/skypilot/cosmetics/models/royal_mantle.bbmodel";
            try (InputStream in = CustomCosmetics.class.getResourceAsStream(tpl)) {
                if (in == null) return null;
                JsonObject obj = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                CosmeticRig.Model model = CosmeticRig.parseBbModel(obj, id, "cape", file.getParent());
                model.id = id;
                model.slot = "cape";
                model.texture = "";
                model.emissiveTexture = "";
                model.animFrames = 1;
                model.embeddedTextureBytes = png;
                model.custom = true;
                CosmeticRig.registerCustom(id, model);
            }
            String title = prettifyName(stem);
            return new CosmeticCatalog.Item(
                    id, "cape", title, title,
                    "Custom PNG cloak (" + file.getFileName() + ")",
                    "自作PNGクローク (" + file.getFileName() + ")",
                    0x55D6D2, "custom", false, false, false, true
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isInTemplates(Path root, Path file) {
        Path rel = root.relativize(file);
        return rel.getNameCount() > 0 && rel.getName(0).toString().equalsIgnoreCase("templates");
    }

    private static boolean hasMatchingModel(Path pngFile) {
        Path parent = pngFile.getParent();
        if (parent == null) return false;
        String stem = stripExt(pngFile.getFileName().toString());
        if (stem.endsWith("_glow")) stem = stem.substring(0, stem.length() - 5);
        return Files.isRegularFile(parent.resolve(stem + ".bbmodel")) || Files.isRegularFile(parent.resolve(stem + ".json"));
    }

    private static String inferSlotFromPath(Path root, Path file) {
        Path rel = root.relativize(file);
        if (rel.getNameCount() >= 2) {
            String dir = rel.getName(0).toString().toLowerCase(Locale.ROOT);
            if (CosmeticCatalog.SLOTS.contains(dir)) return dir;
            if (dir.equals("cloaks") || dir.equals("capes")) return "cape";
            if (dir.equals("wings")) return "back";
            if (dir.equals("hats") || dir.equals("headwear")) return "hat";
            if (dir.equals("auras")) return "aura";
            if (dir.equals("pets")) return "pet";
            if (dir.equals("companions")) return "companion";
        }
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.contains("cape") || lower.contains("cloak")) return "cape";
        if (lower.contains("wing") || lower.contains("back") || lower.contains("jetpack")) return "back";
        if (lower.contains("aura") || lower.contains("orbit")) return "aura";
        if (lower.contains("pet")) return "pet";
        if (lower.contains("comp") || lower.contains("companion")) return "companion";
        return "hat";
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String slugify(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String prettifyName(String stem) {
        String clean = stem.replace('_', ' ').replace('-', ' ').trim();
        if (clean.isEmpty()) return "Custom Model";
        StringBuilder sb = new StringBuilder();
        for (String w : clean.split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}

