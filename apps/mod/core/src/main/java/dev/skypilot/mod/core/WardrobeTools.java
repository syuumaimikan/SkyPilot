package dev.skypilot.mod.core;

import java.util.*;
import java.util.random.RandomGenerator;

/** Local outfit operations shared by the launcher and game across all 6 cosmetic slots. */
public final class WardrobeTools {
    public static final int[] PALETTE = {
        0xA38BFA, 0x55D6D2, 0xF09055, 0xEAC574, 0xDB8AE8, 0x8D9DFF,
        0xF43F5E, 0x10B981, 0x38BDF8, 0xFDE047, 0xEC4899, 0xFFFFFF
    };

    public static final int MAX_OUTFITS = 8;

    public static final class Outfit {
        public String name = "", cape = "none", hat = "none", back = "none", aura = "none", pet = "none", companion = "none";
        public double wingScale = 1.0;
        public boolean cosmeticGlow = true;
        public int color = 0xA38BFA;
        public boolean skin3d;
    }

    private WardrobeTools() {}

    public static Outfit capture(String name, SkyPilotConfig.Appearance a) {
        var o = new Outfit();
        o.name = name.strip();
        o.cape = a.cape;
        o.hat = a.hat;
        o.back = a.back;
        o.aura = a.aura;
        o.pet = a.pet;
        o.companion = a.companion;
        o.wingScale = a.wingScale;
        o.cosmeticGlow = a.cosmeticGlow;
        o.color = a.color;
        o.skin3d = a.skin3d;
        return o;
    }

    public static void apply(Outfit o, SkyPilotConfig.Appearance a) {
        a.cape = o.cape == null ? "none" : o.cape;
        a.hat = o.hat == null ? "none" : o.hat;
        a.back = o.back == null ? "none" : o.back;
        a.aura = o.aura == null ? "none" : o.aura;
        a.pet = o.pet == null ? "none" : o.pet;
        a.companion = o.companion == null ? "none" : o.companion;
        a.wingScale = o.wingScale <= 0 ? 1.0 : Math.max(0.6, Math.min(1.5, o.wingScale));
        a.cosmeticGlow = o.cosmeticGlow;
        a.color = o.color;
        a.skin3d = o.skin3d;
        a.accessory = "none";
    }

    public static void clear(SkyPilotConfig.Appearance a) {
        a.cape = "none";
        a.hat = "none";
        a.back = "none";
        a.aura = "none";
        a.pet = "none";
        a.companion = "none";
        a.accessory = "none";
    }

    public static int equippedCount(SkyPilotConfig.Appearance a) {
        int n = 0;
        for (String slot : CosmeticCatalog.SLOTS) {
            if (!"none".equals(CosmeticCatalog.equippedId(a, slot))) n++;
        }
        return n;
    }

    public static void randomize(SkyPilotConfig.Appearance a, RandomGenerator random) {
        for (String slot : CosmeticCatalog.SLOTS) {
            var pool = CosmeticCatalog.bySlot(slot);
            if (!pool.isEmpty()) pool.get(random.nextInt(pool.size())).equip(a);
        }
        a.color = PALETTE[random.nextInt(PALETTE.length)];
        a.accessory = "none";
    }

    public static void favorite(SkyPilotConfig.Appearance a, String id) {
        if (!a.favorites.remove(id) && CosmeticCatalog.byId(id) != null) a.favorites.add(id);
    }

    public static void remember(SkyPilotConfig.Appearance a, String name) {
        String label = name == null ? "" : name.strip();
        if (label.isEmpty() || label.length() > 24) throw new IllegalArgumentException("Use a name of 1–24 characters.");
        a.outfits.removeIf(o -> o.name.equals(label));
        if (a.outfits.size() >= MAX_OUTFITS) throw new IllegalArgumentException("Eight outfits maximum. Remove one before saving another.");
        a.outfits.add(capture(label, a));
    }

    public static void sanitize(SkyPilotConfig.Appearance a) {
        if (a.favorites == null) a.favorites = new ArrayList<>();
        a.favorites = new ArrayList<>(a.favorites.stream()
                .filter(Objects::nonNull)
                .filter(id -> CosmeticCatalog.byId(id) != null)
                .distinct()
                .limit(Math.max(256, CosmeticCatalog.ITEMS.size()))
                .toList());
        if (a.outfits == null) a.outfits = new ArrayList<>();
        Map<String, Outfit> unique = new LinkedHashMap<>();
        for (var o : a.outfits) {
            if (o == null || o.name == null || o.name.isBlank()) continue;
            o.name = o.name.strip().substring(0, Math.min(24, o.name.strip().length()));
            if (!CosmeticCatalog.valid("cape", o.cape)) o.cape = "none";
            if (!CosmeticCatalog.valid("hat", o.hat)) o.hat = "none";
            if (!CosmeticCatalog.valid("back", o.back)) o.back = "none";
            if (!CosmeticCatalog.valid("aura", o.aura)) o.aura = "none";
            if (!CosmeticCatalog.valid("pet", o.pet)) o.pet = "none";
            if (!CosmeticCatalog.valid("companion", o.companion)) o.companion = "none";
            if (!Double.isFinite(o.wingScale) || o.wingScale <= 0) o.wingScale = 1.0;
            o.wingScale = Math.max(0.6, Math.min(1.5, o.wingScale));
            o.color &= 0xFFFFFF;
            if (unique.containsKey(o.name) || unique.size() < MAX_OUTFITS) unique.put(o.name, o);
        }
        a.outfits = new ArrayList<>(unique.values());
    }
}
