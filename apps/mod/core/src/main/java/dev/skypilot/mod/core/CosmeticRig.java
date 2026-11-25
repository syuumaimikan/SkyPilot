package dev.skypilot.mod.core;

import com.google.gson.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Blockbench ({@code .bbmodel}) 3D model rig loader and animator.
 *
 * <p>Loads both bundled 3D {@code .bbmodel} files ({@code /assets/skypilot/cosmetics/models/<id>.bbmodel})
 * and user-created {@code .bbmodel} / {@code .json} / {@code .png} files from
 * {@code config/skypilot/cosmetics/}. Supports hierarchical bones, per-cube pivots and Euler
 * rotations, per-face or box UV coordinates, embedded base64 PNG textures, emissive glow masks,
 * multi-frame animated textures, procedural SkyPilot bone tags, AND native Blockbench keyframe animations.
 *
 * <p>Coordinate system: Blockbench / Bedrock entity pixel space (16 px = 1 block):
 * <ul>
 *   <li>Origin (0, 0, 0) = player feet center on the ground</li>
 *   <li>+Y = up (waist = 12, shoulders/neck = 24, head top = 32)</li>
 *   <li>-Z = player front (face), +Z = player back (cape/wings)</li>
 *   <li>+X = player left</li>
 * </ul>
 */
public final class CosmeticRig {
    private static final Map<String, Model> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Model> CUSTOM_MODELS = new ConcurrentHashMap<>();

    public static final class FaceUv {
        public float[] uv; // [u1, v1, u2, v2] in texture pixels
        public int rotation; // 0, 90, 180, 270
    }

    public static final class FaceMap {
        public FaceUv north;
        public FaceUv south;
        public FaceUv east;
        public FaceUv west;
        public FaceUv up;
        public FaceUv down;

        public FaceUv get(String face) {
            return switch (face) {
                case "north" -> north;
                case "south" -> south;
                case "east" -> east;
                case "west" -> west;
                case "up" -> up;
                case "down" -> down;
                default -> null;
            };
        }
    }

    public static final class Keyframe {
        public double time;
        public double x, y, z;

        public Keyframe(double time, double x, double y, double z) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class BoneTrack {
        public List<Keyframe> position = new ArrayList<>();
        public List<Keyframe> rotation = new ArrayList<>();
        public List<Keyframe> scale = new ArrayList<>();
    }

    public static final class Clip {
        public String name = "";
        public double length = 1.0;
        public boolean loop = true;
        public Map<String, BoneTrack> bones = new LinkedHashMap<>();

        public CosmeticAnimation.BoneTransform sample(String boneName, double time) {
            BoneTrack track = bones.get(boneName);
            if (track == null) return null;
            double len = length > 0.001 ? length : 1.0;
            double t = loop ? (time % len + len) % len : Math.min(len, Math.max(0.0, time));
            double[] pos = sampleChannel(track.position, t, 0.0);
            double[] rot = sampleChannel(track.rotation, t, 0.0);
            double[] scl = sampleChannel(track.scale, t, 1.0);
            return new CosmeticAnimation.BoneTransform(
                    pos[0], pos[1], pos[2],
                    rot[0], rot[1], rot[2],
                    scl[0], scl[1], scl[2],
                    true
            );
        }

        private static double[] sampleChannel(List<Keyframe> kfs, double t, double def) {
            if (kfs == null || kfs.isEmpty()) return new double[]{def, def, def};
            if (kfs.size() == 1 || t <= kfs.get(0).time) {
                Keyframe k = kfs.get(0);
                return new double[]{k.x, k.y, k.z};
            }
            Keyframe last = kfs.get(kfs.size() - 1);
            if (t >= last.time) return new double[]{last.x, last.y, last.z};
            for (int i = 0; i < kfs.size() - 1; i++) {
                Keyframe a = kfs.get(i), b = kfs.get(i + 1);
                if (t >= a.time && t <= b.time) {
                    double span = b.time - a.time;
                    double f = span > 1e-6 ? (t - a.time) / span : 0.0;
                    return new double[]{
                            a.x + (b.x - a.x) * f,
                            a.y + (b.y - a.y) * f,
                            a.z + (b.z - a.z) * f
                    };
                }
            }
            return new double[]{last.x, last.y, last.z};
        }
    }

    public static final class Cube {
        public String name = "";
        public double[] from = {0, 0, 0};
        public double[] to = {1, 1, 1};
        public double[] pivot = {0, 0, 0};
        public double[] rot = {0, 0, 0}; // degrees [rx, ry, rz]
        public float[] uv = {0, 0};
        public FaceMap faces;
        public double inflate = 0.0;
        public boolean mirror = false;
        public boolean emissive = false;
        public boolean translucent = false;
        public int color = 0xFFFFFF;

        public double sizeX() { return Math.max(0.0, to[0] - from[0]); }
        public double sizeY() { return Math.max(0.0, to[1] - from[1]); }
        public double sizeZ() { return Math.max(0.0, to[2] - from[2]); }
    }

    public static final class Bone {
        public String name = "";
        public double[] pivot = {0, 0, 0};
        public double[] rot = {0, 0, 0}; // base Euler degrees [rx, ry, rz]
        public String anim = "";
        public double animAmp = 1.0;
        public double animPhase = 0.0;
        public boolean emissive = false;
        public boolean translucent = false;
        public List<Cube> cubes = new ArrayList<>();
        public List<Bone> children = new ArrayList<>();
    }

    public static final class Model {
        public String id = "";
        public String slot = "";
        public String anchor = "body"; // body | head | root | pet | companion
        public String texture = ""; // e.g. "textures/cosmetics/cape/aurora.png"
        public String emissiveTexture = "";
        public byte[] embeddedTextureBytes; // decoded PNG bytes from .bbmodel textures[] or custom file
        public byte[] embeddedEmissiveBytes;
        public int texWidth = 64;
        public int texHeight = 64;
        public boolean translucent = false;
        public boolean emissive = false;
        public boolean scalable = false;
        public boolean custom = false;
        public int animFrames = 1;
        public int animFps = 8;
        public List<Bone> bones = new ArrayList<>();
        public List<Clip> clips = new ArrayList<>();

        public String textureClasspath() {
            return texture == null || texture.isEmpty() ? null : "/assets/skypilot/" + texture;
        }

        public String emissiveClasspath() {
            return emissiveTexture == null || emissiveTexture.isEmpty() ? null : "/assets/skypilot/" + emissiveTexture;
        }

        public InputStream openTextureStream() {
            if (embeddedTextureBytes != null && embeddedTextureBytes.length > 0) {
                return new java.io.ByteArrayInputStream(embeddedTextureBytes);
            }
            String cp = textureClasspath();
            return cp == null ? null : CosmeticRig.class.getResourceAsStream(cp);
        }

        public InputStream openEmissiveStream() {
            if (embeddedEmissiveBytes != null && embeddedEmissiveBytes.length > 0) {
                return new java.io.ByteArrayInputStream(embeddedEmissiveBytes);
            }
            String cp = emissiveClasspath();
            return cp == null ? null : CosmeticRig.class.getResourceAsStream(cp);
        }

        /** Evaluates either a Blockbench keyframe clip (if present for this bone) or procedural animation tag. */
        public CosmeticAnimation.BoneTransform evaluateBone(
                Bone bone, double time, double speed,
                boolean sneaking, boolean falling, boolean flying,
                double vertSpeed, double yawTurnRate
        ) {
            if (clips != null && !clips.isEmpty()) {
                CosmeticAnimation.BoneTransform kf = clips.get(0).sample(bone.name, time);
                if (kf != null) return kf;
            }
            return CosmeticAnimation.evaluateBone(
                    bone.anim, bone.animAmp, bone.animPhase,
                    time, speed, sneaking, falling, flying, vertSpeed, yawTurnRate
            );
        }

        public int totalCubes() {
            int n = 0;
            for (Bone b : bones) n += countCubes(b);
            return n;
        }

        private static int countCubes(Bone b) {
            int n = b.cubes == null ? 0 : b.cubes.size();
            if (b.children != null) {
                for (Bone c : b.children) n += countCubes(c);
            }
            return n;
        }
    }

    private CosmeticRig() {}

    public static Model load(String id) {
        if (id == null || id.isEmpty() || "none".equals(id)) return null;
        Model custom = CUSTOM_MODELS.get(id);
        if (custom != null) return custom;
        return CACHE.computeIfAbsent(id, CosmeticRig::readBundled);
    }

    public static void registerCustom(String id, Model model) {
        if (id != null && model != null) {
            model.custom = true;
            CUSTOM_MODELS.put(id, model);
        }
    }

    public static void clearCustomModels() {
        CUSTOM_MODELS.clear();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Model readBundled(String id) {
        String bbPath = "/assets/skypilot/cosmetics/models/" + id + ".bbmodel";
        try (InputStream in = CosmeticRig.class.getResourceAsStream(bbPath)) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                return parseBbModel(root, id, "", null);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load bundled 3D model " + bbPath, e);
        }
        String jsonPath = "/assets/skypilot/cosmetics/models/" + id + ".json";
        try (InputStream in = CosmeticRig.class.getResourceAsStream(jsonPath)) {
            if (in == null) return null;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return parseBbModel(root, id, "", null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load bundled cosmetic model " + jsonPath, e);
        }
    }

    /**
     * Parses a Blockbench {@code .bbmodel} JSON object (or legacy SkyPilot rig JSON) into a runtime {@link Model}.
     */
    public static Model parseBbModel(JsonObject root, String fallbackId, String fallbackSlot, Path sourceDir) {
        Model m = new Model();
        JsonObject sp = root.has("skypilot") && root.get("skypilot").isJsonObject() ? root.getAsJsonObject("skypilot") : null;

        m.id = optStr(sp, "id", optStr(root, "id", fallbackId));
        m.slot = optStr(sp, "slot", optStr(root, "slot", fallbackSlot));
        if (m.slot.isEmpty()) m.slot = "hat";
        m.anchor = optStr(sp, "anchor", optStr(root, "anchor", defaultAnchorForSlot(m.slot)));
        m.texture = optStr(sp, "texture", optStr(root, "texture", ""));
        m.emissiveTexture = optStr(sp, "emissiveTexture", optStr(root, "emissiveTexture", ""));
        m.translucent = optBool(sp, "translucent", optBool(root, "translucent", false));
        m.emissive = optBool(sp, "emissive", optBool(root, "emissive", false));
        m.scalable = optBool(sp, "scalable", optBool(root, "scalable", "back".equals(m.slot)));
        m.animFrames = Math.max(1, optInt(sp, "animFrames", optInt(root, "animFrames", 1)));
        m.animFps = Math.max(1, optInt(sp, "animFps", optInt(root, "animFps", 8)));

        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject res = root.getAsJsonObject("resolution");
            m.texWidth = Math.max(1, optInt(res, "width", 64));
            m.texHeight = Math.max(1, optInt(res, "height", 64));
        } else {
            m.texWidth = Math.max(1, optInt(root, "texWidth", 64));
            m.texHeight = Math.max(1, optInt(root, "texHeight", 64));
        }

        // Parse embedded textures from Blockbench textures[] (data:image/png;base64,...)
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            for (JsonElement te : root.getAsJsonArray("textures")) {
                if (!te.isJsonObject()) continue;
                JsonObject tobj = te.getAsJsonObject();
                String tname = optStr(tobj, "name", "").toLowerCase(Locale.ROOT);
                String src = optStr(tobj, "source", "");
                byte[] decoded = decodeDataUriPng(src);
                if (decoded == null && sourceDir != null) {
                    String rel = optStr(tobj, "relative_path", optStr(tobj, "path", ""));
                    if (!rel.isEmpty()) {
                        Path p = sourceDir.resolve(Path.of(rel).getFileName().toString());
                        if (Files.isRegularFile(p)) {
                            try { decoded = Files.readAllBytes(p); } catch (Exception ignored) {}
                        }
                    }
                }
                if (decoded != null) {
                    boolean isGlow = tname.contains("glow") || tname.contains("emissive");
                    if (isGlow && m.embeddedEmissiveBytes == null) {
                        m.embeddedEmissiveBytes = decoded;
                        m.emissive = true;
                    } else if (!isGlow && m.embeddedTextureBytes == null) {
                        m.embeddedTextureBytes = decoded;
                    }
                }
            }
        }

        // Native Blockbench elements[] + outliner[] format
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            Map<String, Cube> byUuid = new LinkedHashMap<>();
            List<Cube> allCubes = new ArrayList<>();
            int defaultColor = optInt(sp, "color", 0xA38BFA);
            for (JsonElement el : root.getAsJsonArray("elements")) {
                if (!el.isJsonObject()) continue;
                JsonObject eo = el.getAsJsonObject();
                if (!optBool(eo, "visibility", true)) continue;
                Cube c = parseBbCube(eo, defaultColor);
                String uuid = optStr(eo, "uuid", "");
                if (!uuid.isEmpty()) byUuid.put(uuid, c);
                allCubes.add(c);
            }

            Set<String> placed = new HashSet<>();
            List<Bone> bones = new ArrayList<>();
            List<Cube> looseRootCubes = new ArrayList<>();
            if (root.has("outliner") && root.get("outliner").isJsonArray()) {
                for (JsonElement oe : root.getAsJsonArray("outliner")) {
                    if (oe.isJsonPrimitive() && oe.getAsJsonPrimitive().isString()) {
                        String u = oe.getAsString();
                        Cube c = byUuid.get(u);
                        if (c != null) {
                            looseRootCubes.add(c);
                            placed.add(u);
                        }
                    } else if (oe.isJsonObject()) {
                        bones.add(parseBbOutlinerBone(oe.getAsJsonObject(), byUuid, placed, m.slot));
                    }
                }
            }
            // Any element not referenced in outliner goes to an implicit root bone
            for (var entry : byUuid.entrySet()) {
                if (!placed.contains(entry.getKey())) looseRootCubes.add(entry.getValue());
            }
            if (byUuid.isEmpty() && !allCubes.isEmpty()) looseRootCubes.addAll(allCubes);
            if (!looseRootCubes.isEmpty()) {
                Bone rootBone = new Bone();
                rootBone.name = "root";
                rootBone.pivot = defaultPivotForSlot(m.slot);
                rootBone.anim = inferBoneAnim(rootBone.name, m.slot);
                rootBone.cubes = looseRootCubes;
                bones.add(0, rootBone);
            }
            m.bones = bones;
        } else if (root.has("bones") && root.get("bones").isJsonArray()) {
            // Direct bones[] format fallback
            List<Bone> bones = new ArrayList<>();
            for (JsonElement be : root.getAsJsonArray("bones")) {
                if (be.isJsonObject()) bones.add(parseLegacyBone(be.getAsJsonObject(), m.slot));
            }
            m.bones = bones;
        }

        // Native Blockbench animations[] keyframe parser
        if (root.has("animations") && root.get("animations").isJsonArray()) {
            for (JsonElement ae : root.getAsJsonArray("animations")) {
                if (ae.isJsonObject()) {
                    Clip clip = parseBbAnimation(ae.getAsJsonObject());
                    if (!clip.bones.isEmpty()) m.clips.add(clip);
                }
            }
        }

        normalize(m);
        return m;
    }

    private static Cube parseBbCube(JsonObject o, int defaultColor) {
        Cube c = new Cube();
        c.name = optStr(o, "name", "");
        c.from = optVec3(o, "from", new double[]{0, 0, 0});
        c.to = optVec3(o, "to", new double[]{1, 1, 1});
        // Ensure from <= to
        for (int i = 0; i < 3; i++) {
            if (c.from[i] > c.to[i]) {
                double tmp = c.from[i];
                c.from[i] = c.to[i];
                c.to[i] = tmp;
            }
        }
        double[] mid = {(c.from[0] + c.to[0]) * 0.5, (c.from[1] + c.to[1]) * 0.5, (c.from[2] + c.to[2]) * 0.5};
        c.pivot = o.has("origin") ? optVec3(o, "origin", mid) : optVec3(o, "pivot", mid);
        c.rot = o.has("rotation") ? optVec3(o, "rotation", new double[]{0, 0, 0}) : optVec3(o, "rot", new double[]{0, 0, 0});
        c.inflate = optDouble(o, "inflate", 0.0);
        c.mirror = optBool(o, "mirror_uv", optBool(o, "mirror", false));
        c.emissive = optBool(o, "skypilot_emissive", optBool(o, "emissive", false));
        c.translucent = optBool(o, "skypilot_translucent", optBool(o, "translucent", false));
        c.color = optInt(o, "skypilot_color", defaultColor) & 0xFFFFFF;
        if (o.has("uv_offset") && o.get("uv_offset").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("uv_offset");
            if (arr.size() >= 2) c.uv = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat()};
        }
        if (o.has("faces") && o.get("faces").isJsonObject()) {
            JsonObject fo = o.getAsJsonObject("faces");
            FaceMap fm = new FaceMap();
            fm.north = parseFace(fo, "north");
            fm.south = parseFace(fo, "south");
            fm.east = parseFace(fo, "east");
            fm.west = parseFace(fo, "west");
            fm.up = parseFace(fo, "up");
            fm.down = parseFace(fo, "down");
            c.faces = fm;
        }
        return c;
    }

    private static FaceUv parseFace(JsonObject fo, String key) {
        if (!fo.has(key) || !fo.get(key).isJsonObject()) return null;
        JsonObject f = fo.getAsJsonObject(key);
        if (!f.has("uv") || !f.get("uv").isJsonArray()) return null;
        JsonArray arr = f.getAsJsonArray("uv");
        if (arr.size() < 4) return null;
        FaceUv uv = new FaceUv();
        uv.uv = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat(), arr.get(3).getAsFloat()};
        uv.rotation = optInt(f, "rotation", 0);
        return uv;
    }

    private static Bone parseBbOutlinerBone(JsonObject o, Map<String, Cube> byUuid, Set<String> placed, String slot) {
        Bone b = new Bone();
        b.name = optStr(o, "name", "bone");
        b.pivot = o.has("origin") ? optVec3(o, "origin", new double[]{0, 0, 0}) : optVec3(o, "pivot", new double[]{0, 0, 0});
        b.rot = o.has("rotation") ? optVec3(o, "rotation", new double[]{0, 0, 0}) : optVec3(o, "rot", new double[]{0, 0, 0});
        b.anim = optStr(o, "skypilot_anim", optStr(o, "anim", ""));
        if (b.anim.isEmpty()) b.anim = inferBoneAnim(b.name, slot);
        b.animAmp = optDouble(o, "skypilot_anim_amp", optDouble(o, "animAmp", 1.0));
        b.animPhase = optDouble(o, "skypilot_anim_phase", optDouble(o, "animPhase", 0.0));
        b.emissive = optBool(o, "skypilot_emissive", optBool(o, "emissive", false));
        b.translucent = optBool(o, "skypilot_translucent", optBool(o, "translucent", false));
        if (o.has("children") && o.get("children").isJsonArray()) {
            for (JsonElement ch : o.getAsJsonArray("children")) {
                if (ch.isJsonPrimitive() && ch.getAsJsonPrimitive().isString()) {
                    String u = ch.getAsString();
                    Cube c = byUuid.get(u);
                    if (c != null) {
                        b.cubes.add(c);
                        placed.add(u);
                    }
                } else if (ch.isJsonObject()) {
                    b.children.add(parseBbOutlinerBone(ch.getAsJsonObject(), byUuid, placed, slot));
                }
            }
        }
        return b;
    }

    private static Bone parseLegacyBone(JsonObject o, String slot) {
        Bone b = new Bone();
        b.name = optStr(o, "name", "bone");
        b.pivot = optVec3(o, "pivot", new double[]{0, 0, 0});
        b.rot = optVec3(o, "rot", new double[]{0, 0, 0});
        b.anim = optStr(o, "anim", "");
        if (b.anim.isEmpty()) b.anim = inferBoneAnim(b.name, slot);
        b.animAmp = optDouble(o, "animAmp", 1.0);
        b.animPhase = optDouble(o, "animPhase", 0.0);
        b.emissive = optBool(o, "emissive", false);
        b.translucent = optBool(o, "translucent", false);
        if (o.has("cubes") && o.get("cubes").isJsonArray()) {
            for (JsonElement ce : o.getAsJsonArray("cubes")) {
                if (ce.isJsonObject()) b.cubes.add(parseBbCube(ce.getAsJsonObject(), 0xFFFFFF));
            }
        }
        if (o.has("children") && o.get("children").isJsonArray()) {
            for (JsonElement ce : o.getAsJsonArray("children")) {
                if (ce.isJsonObject()) b.children.add(parseLegacyBone(ce.getAsJsonObject(), slot));
            }
        }
        return b;
    }

    private static Clip parseBbAnimation(JsonObject o) {
        Clip clip = new Clip();
        clip.name = optStr(o, "name", "idle");
        clip.length = optDouble(o, "length", 2.0);
        String loopStr = optStr(o, "loop", "loop");
        clip.loop = !"once".equalsIgnoreCase(loopStr) && !"hold".equalsIgnoreCase(loopStr);
        if (o.has("animators") && o.get("animators").isJsonObject()) {
            for (var entry : o.getAsJsonObject("animators").entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject ao = entry.getValue().getAsJsonObject();
                String boneName = optStr(ao, "name", entry.getKey());
                if (!ao.has("keyframes") || !ao.get("keyframes").isJsonArray()) continue;
                BoneTrack track = new BoneTrack();
                for (JsonElement ke : ao.getAsJsonArray("keyframes")) {
                    if (!ke.isJsonObject()) continue;
                    JsonObject ko = ke.getAsJsonObject();
                    String ch = optStr(ko, "channel", "");
                    double time = optDouble(ko, "time", 0.0);
                    double[] xyz = parseKeyframePoint(ko);
                    if (xyz == null) continue;
                    Keyframe kf = new Keyframe(time, xyz[0], xyz[1], xyz[2]);
                    switch (ch) {
                        case "position" -> track.position.add(kf);
                        case "rotation" -> track.rotation.add(kf);
                        case "scale" -> track.scale.add(kf);
                    }
                }
                track.position.sort(Comparator.comparingDouble(k -> k.time));
                track.rotation.sort(Comparator.comparingDouble(k -> k.time));
                track.scale.sort(Comparator.comparingDouble(k -> k.time));
                if (!track.position.isEmpty() || !track.rotation.isEmpty() || !track.scale.isEmpty()) {
                    clip.bones.put(boneName, track);
                }
            }
        }
        return clip;
    }

    private static double[] parseKeyframePoint(JsonObject ko) {
        if (!ko.has("data_points") || !ko.get("data_points").isJsonArray()) return null;
        JsonArray pts = ko.getAsJsonArray("data_points");
        if (pts.isEmpty() || !pts.get(0).isJsonObject()) return null;
        JsonObject dp = pts.get(0).getAsJsonObject();
        return new double[]{parseNum(dp.get("x")), parseNum(dp.get("y")), parseNum(dp.get("z"))};
    }

    private static double parseNum(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0.0;
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
            return Double.parseDouble(e.getAsString().trim());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    /** Smart convention mapping so user-created Blockbench models animate out of the box just by naming bones! */
    static String inferBoneAnim(String boneName, String slot) {
        String n = boneName == null ? "" : boneName.toLowerCase(Locale.ROOT);
        if (n.startsWith("cape_seg_")) return n;
        if (n.contains("wing_l_tip") || n.contains("left_wing_tip")) return "wing_l_tip";
        if (n.contains("wing_r_tip") || n.contains("right_wing_tip")) return "wing_r_tip";
        if (n.contains("wing_l_mid") || n.contains("left_wing_mid")) return "wing_l_mid";
        if (n.contains("wing_r_mid") || n.contains("right_wing_mid")) return "wing_r_mid";
        if (n.contains("wing_l") || n.contains("left_wing")) return "wing_l_root";
        if (n.contains("wing_r") || n.contains("right_wing")) return "wing_r_root";
        if (n.contains("halo")) return "halo_float";
        if (n.equals("ear_l") || n.equals("left_ear")) return "ear_l";
        if (n.equals("ear_r") || n.equals("right_ear")) return "ear_r";
        if (n.contains("gear")) return "gear_cw";
        if (n.contains("orbit")) return "aura_orbit_cw";
        if (n.equals("pet_root") || ("pet".equals(slot) && n.equals("root"))) return "pet_hover";
        if (n.equals("comp_root") || ("companion".equals(slot) && n.equals("root"))) return "comp_root";
        if ("aura".equals(slot) && n.equals("root")) return "aura_orbit_cw";
        return "";
    }

    public static String defaultAnchorForSlot(String slot) {
        return switch (slot == null ? "" : slot) {
            case "hat" -> "head";
            case "aura" -> "root";
            case "pet" -> "pet";
            case "companion" -> "companion";
            default -> "body";
        };
    }

    private static double[] defaultPivotForSlot(String slot) {
        return switch (slot == null ? "" : slot) {
            case "hat" -> new double[]{0, 32, 0};
            case "aura" -> new double[]{0, 16, 0};
            case "pet" -> new double[]{13.5, 24.5, 1.5};
            case "companion" -> new double[]{-15.5, 0, 2.0};
            default -> new double[]{0, 20, 2.8};
        };
    }

    private static byte[] decodeDataUriPng(String src) {
        if (src == null || !src.startsWith("data:image/")) return null;
        int comma = src.indexOf(',');
        if (comma < 0) return null;
        try {
            return Base64.getDecoder().decode(src.substring(comma + 1).replaceAll("\\s+", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void normalize(Model m) {
        if (m.bones == null) m.bones = List.of();
        if (m.clips == null) m.clips = List.of();
        if (m.texWidth <= 0) m.texWidth = 64;
        if (m.texHeight <= 0) m.texHeight = 64;
        if (m.animFrames < 1) m.animFrames = 1;
        if (m.animFps < 1) m.animFps = 8;
        for (Bone b : m.bones) normalizeBone(b);
    }

    private static void normalizeBone(Bone b) {
        if (b.pivot == null || b.pivot.length < 3) b.pivot = new double[]{0, 0, 0};
        if (b.rot == null || b.rot.length < 3) b.rot = new double[]{0, 0, 0};
        if (b.anim == null) b.anim = "";
        if (b.cubes == null) b.cubes = List.of();
        if (b.children == null) b.children = List.of();
        for (Cube c : b.cubes) {
            if (c.from == null || c.from.length < 3) c.from = new double[]{0, 0, 0};
            if (c.to == null || c.to.length < 3) c.to = new double[]{1, 1, 1};
            if (c.pivot == null || c.pivot.length < 3) c.pivot = b.pivot.clone();
            if (c.rot == null || c.rot.length < 3) c.rot = new double[]{0, 0, 0};
            if (c.uv == null || c.uv.length < 2) c.uv = new float[]{0, 0};
            if (b.emissive) c.emissive = true;
            if (b.translucent) c.translucent = true;
        }
        for (Bone child : b.children) normalizeBone(child);
    }

    private static String optStr(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try { return o.get(key).getAsString(); } catch (Exception e) { return def; }
    }

    private static boolean optBool(JsonObject o, String key, boolean def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try { return o.get(key).getAsBoolean(); } catch (Exception e) { return def; }
    }

    private static int optInt(JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try { return o.get(key).getAsInt(); } catch (Exception e) { return def; }
    }

    private static double optDouble(JsonObject o, String key, double def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try { return o.get(key).getAsDouble(); } catch (Exception e) { return def; }
    }

    private static double[] optVec3(JsonObject o, String key, double[] def) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) return def.clone();
        JsonArray a = o.getAsJsonArray(key);
        if (a.size() < 3) return def.clone();
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    public static float[] bbToModelPart(double bx, double by, double bz) {
        return new float[]{(float) bx, (float) (24.0 - by), (float) bz};
    }

    public static double[] bbToPreviewMeters(double bx, double by, double bz) {
        return new double[]{bx / 16.0, by / 16.0, -bz / 16.0};
    }
}
