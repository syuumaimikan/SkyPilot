package dev.skypilot.mod.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Original, bundled 3D {@code .bbmodel} cosmetic designs + user-imported custom 3D models across
 * 6 independently combinable slots: Cloaks (cape), Wings & Backwear (back), Headwear (hat),
 * Auras (aura), Floating Pets (pet), and Ground Companions (companion).
 */
public final class CosmeticCatalog {
    public static final List<String> SLOTS = List.of(
            "cape", "back", "hat", "face", "shoulder", "waist", "feet", "aura", "pet", "companion"
    );

    public record Item(
            String id,
            String slot,
            String name,
            String ja,
            String description,
            String descriptionJa,
            int color,
            String rarity,
            boolean animated,
            boolean scalable,
            boolean emissive,
            boolean custom
    ) {
        public String label(boolean japanese) { return japanese ? ja : name; }
        public String detail(boolean japanese) { return japanese ? descriptionJa : description; }

        public void equip(SkyPilotConfig.Appearance a) {
            CosmeticCatalog.equip(a, slot, id);
        }

        public void unequip(SkyPilotConfig.Appearance a) {
            CosmeticCatalog.clearSlot(a, slot);
        }

        public boolean equipped(SkyPilotConfig.Appearance a) {
            return id.equals(CosmeticCatalog.equippedId(a, slot));
        }

        public int rarityColor() {
            return switch (rarity == null ? "rare" : rarity) {
                case "custom" -> 0x2DD4BF;
                case "mythic" -> 0xF472B6;
                case "legendary" -> 0xFBBF24;
                case "epic" -> 0xA855F7;
                case "rare" -> 0x38BDF8;
                default -> 0x94A3B8;
            };
        }
    }

    private static final class RawItem {
        String id, slot, name, ja, description, descriptionJa, rarity;
        int color;
        boolean animated, scalable, emissive;
    }

    /** Bundled original designs loaded from {@code /assets/skypilot/cosmetics/catalog.json}. */
    public static final List<Item> ITEMS;
    private static final Map<String, Item> BY_ID;
    private static final Map<String, List<Item>> BY_SLOT;

    static {
        List<Item> loaded = loadCatalog();
        ITEMS = List.copyOf(loaded);
        Map<String, Item> idMap = new LinkedHashMap<>();
        Map<String, List<Item>> slotMap = new LinkedHashMap<>();
        for (String s : SLOTS) slotMap.put(s, new ArrayList<>());
        for (Item item : ITEMS) {
            idMap.put(item.id(), item);
            slotMap.computeIfAbsent(item.slot(), k -> new ArrayList<>()).add(item);
        }
        BY_ID = Collections.unmodifiableMap(idMap);
        Map<String, List<Item>> frozen = new LinkedHashMap<>();
        for (var e : slotMap.entrySet()) frozen.put(e.getKey(), List.copyOf(e.getValue()));
        BY_SLOT = Collections.unmodifiableMap(frozen);
    }

    private static List<Item> loadCatalog() {
        String path = "/assets/skypilot/cosmetics/catalog.json";
        try (InputStream in = CosmeticCatalog.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing cosmetic catalog resource: " + path);
            }
            Type listType = new TypeToken<List<RawItem>>() {}.getType();
            List<RawItem> raw = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), listType);
            List<Item> result = new ArrayList<>(raw.size());
            for (RawItem r : raw) {
                result.add(new Item(
                        r.id, r.slot, r.name, r.ja,
                        r.description, r.descriptionJa,
                        r.color & 0xFFFFFF,
                        r.rarity == null ? "epic" : r.rarity,
                        r.animated, r.scalable, r.emissive, false
                ));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + path, e);
        }
    }

    private CosmeticCatalog() {}

    /** All bundled items plus any user-loaded custom 3D cosmetics. */
    public static List<Item> all() {
        List<Item> custom = CustomCosmetics.items();
        if (custom.isEmpty()) return ITEMS;
        List<Item> combined = new ArrayList<>(ITEMS.size() + custom.size());
        combined.addAll(custom); // Show user's custom creations right at the top!
        combined.addAll(ITEMS);
        return combined;
    }

    public static Item byId(String id) {
        if (id == null) return null;
        Item builtin = BY_ID.get(id);
        if (builtin != null) return builtin;
        for (Item c : CustomCosmetics.items()) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }

    public static List<Item> bySlot(String slot) {
        List<Item> builtin = BY_SLOT.getOrDefault(slot, List.of());
        List<Item> custom = CustomCosmetics.items();
        if (custom.isEmpty()) return builtin;
        List<Item> out = new ArrayList<>();
        for (Item c : custom) {
            if (c.slot().equals(slot)) out.add(c);
        }
        out.addAll(builtin);
        return out;
    }

    public static String equippedId(SkyPilotConfig.Appearance a, String slot) {
        if (a == null || slot == null) return "none";
        return switch (slot) {
            case "cape" -> a.cape == null ? "none" : a.cape;
            case "hat" -> a.hat == null ? "none" : a.hat;
            case "back" -> a.back == null ? "none" : a.back;
            case "face" -> a.face == null ? "none" : a.face;
            case "shoulder" -> a.shoulder == null ? "none" : a.shoulder;
            case "waist" -> a.waist == null ? "none" : a.waist;
            case "feet" -> a.feet == null ? "none" : a.feet;
            case "aura" -> a.aura == null ? "none" : a.aura;
            case "pet" -> a.pet == null ? "none" : a.pet;
            case "companion" -> a.companion == null ? "none" : a.companion;
            default -> "none";
        };
    }

    public static void equip(SkyPilotConfig.Appearance a, String slot, String id) {
        if (a == null || slot == null) return;
        String val = (id == null || id.isBlank()) ? "none" : id;
        switch (slot) {
            case "cape" -> a.cape = val;
            case "hat" -> a.hat = val;
            case "back" -> a.back = val;
            case "face" -> a.face = val;
            case "shoulder" -> a.shoulder = val;
            case "waist" -> a.waist = val;
            case "feet" -> a.feet = val;
            case "aura" -> a.aura = val;
            case "pet" -> a.pet = val;
            case "companion" -> a.companion = val;
        }
    }

    public static void clearSlot(SkyPilotConfig.Appearance a, String slot) {
        if (a == null || slot == null) return;
        switch (slot) {
            case "cape" -> a.cape = "none";
            case "hat" -> a.hat = "none";
            case "back" -> a.back = "none";
            case "face" -> a.face = "none";
            case "shoulder" -> a.shoulder = "none";
            case "waist" -> a.waist = "none";
            case "feet" -> a.feet = "none";
            case "aura" -> a.aura = "none";
            case "pet" -> a.pet = "none";
            case "companion" -> a.companion = "none";
        }
    }

    public static String slotLabel(String slot, boolean japanese) {
        return switch (slot) {
            case "cape" -> japanese ? "マント" : "Cloak";
            case "back" -> japanese ? "翼・背面" : "Wings";
            case "hat" -> japanese ? "頭・帽子" : "Head";
            case "face" -> japanese ? "顔・アクセ" : "Face";
            case "shoulder" -> japanese ? "肩・首飾り" : "Shoulder";
            case "waist" -> japanese ? "腰・尻尾" : "Waist";
            case "feet" -> japanese ? "足元・足羽根" : "Feet";
            case "aura" -> japanese ? "オーラ" : "Aura";
            case "pet" -> japanese ? "ペット" : "Pet";
            case "companion" -> japanese ? "相棒" : "Companion";
            default -> japanese ? "すべて" : "All";
        };
    }

    public static boolean valid(String slot, String id) {
        if ("none".equals(id)) return true;
        Item item = byId(id);
        if (item != null) return item.slot().equals(slot);
        // Preserve custom_* IDs in saved config even if config.sanitize() runs before CustomCosmetics.reload()
        return id != null && id.startsWith("custom_") && id.matches("custom_[a-z0-9_]{1,64}");
    }

    public static void migrate(SkyPilotConfig.Appearance a) {
        if ("crown".equals(a.accessory) || "halo".equals(a.accessory)) {
            if ("none".equals(a.hat)) a.hat = a.accessory;
        }
        if ("wings".equals(a.accessory) && "none".equals(a.back)) a.back = a.accessory;
        a.accessory = "none";
    }
}
