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

    public record Preset(
            String id, String name, String ja, int color,
            String cape, String back, String hat, String face, String shoulder, String waist, String feet,
            String aura, String pet, String companion,
            double wingScale
    ) {
        public String label(boolean japanese) { return japanese ? ja : name; }

        public void apply(SkyPilotConfig.Appearance a) {
            a.cape = cape;
            a.back = back;
            a.hat = hat;
            a.face = face;
            a.shoulder = shoulder;
            a.waist = waist;
            a.feet = feet;
            a.aura = aura;
            a.pet = pet;
            a.companion = companion;
            a.wingScale = wingScale;
            a.color = color;
            a.cosmeticGlow = true;
            a.cosmeticParticles = true;
            a.accessory = "none";
        }
    }

    /** Curated 1-click 10-slot theme outfits (cool + kawaii showcases). */
    public static final List<Preset> PRESETS = List.of(
            new Preset("kawaii_pastel", "Kawaii Pastel", "ゆめかわパステル", 0xF472B6,
                    "strawberry_ribbon", "angel_ribbon_wings", "bunny_beret",
                    "face_rose_glasses", "sh_kawaii_capelet", "waist_pastel_bow", "feet_kawaii_bows",
                    "bubble_prism_aura", "pet_axolotl", "comp_red_panda", 1.05),
            new Preset("sakura_seraph", "Sakura Seraph", "桜の熾天使", 0xFBCFE8,
                    "sakura", "sakura_seraph_wings", "sakura_blossom",
                    "face_kitsune_half", "sh_sakura_muffler", "waist_nine_tails", "feet_sakura_anklets",
                    "sakura_bloom_aura", "pet_celestial_kitsune", "comp_shiba_inu", 1.15),
            new Preset("frost_sovereign", "Frost Sovereign", "蒼炎の覇王", 0x38BDF8,
                    "cyber_chains", "frost_dragon_wings", "empress_diadem",
                    "face_frost_veil", "sh_frost_epaulettes", "waist_dragon_tail", "feet_frost_rings",
                    "starfall_blades_aura", "pet_frost_dragon", "comp_fenrir_wolf", 1.2),
            new Preset("twilight_seraph", "Twilight Seraph", "黎明の熾天使", 0xA855F7,
                    "eclipse", "twilight_seraph_wings", "angel_halo",
                    "face_angel_cuffs", "sh_seraph_pauldrons", "waist_seraph_sash", "feet_hermes_wings",
                    "arcane_circle_aura", "pet_ivory_wyvern", "comp_void_tiger", 1.2),
            new Preset("astral_blade", "Astral Blade", "星界の剣聖", 0x818CF8,
                    "starlight", "celestial_katana_halo", "astral_halo_crown",
                    "face_star_monocle", "sh_starlight_stole", "waist_dual_katanas", "feet_starlight_orbit",
                    "galaxy_orbit_aura", "pet_star_slime", "comp_rainbow_unicorn", 1.1),
            new Preset("crimson_abyss", "Crimson Abyss", "紅蓮の冥帝", 0xF97316,
                    "blaze_spirit", "phoenix", "samurai_kabuto",
                    "face_oni_fang", "sh_dragon_guards", "waist_samurai_swords", "feet_phoenix_wings",
                    "inferno_flame_aura", "pet_shadow_wyvern", "comp_cerberus", 1.25)
    );

    public static final class Outfit {
        public String name = "", cape = "none", hat = "none", back = "none",
                face = "none", shoulder = "none", waist = "none", feet = "none",
                aura = "none", pet = "none", companion = "none";
        public double wingScale = 1.0;
        public boolean cosmeticGlow = true;
        public boolean cosmeticParticles = true;
        public int color = 0xA38BFA;
        public boolean skin3d;
        public Map<String, SkyPilotConfig.SlotTransform> slotTransforms = new LinkedHashMap<>();
        public SkyPilotConfig.VfxSettings vfx = new SkyPilotConfig.VfxSettings();
    }

    private WardrobeTools() {}

    public static Outfit capture(String name, SkyPilotConfig.Appearance a) {
        var o = new Outfit();
        o.name = name.strip();
        o.cape = a.cape;
        o.hat = a.hat;
        o.back = a.back;
        o.face = a.face;
        o.shoulder = a.shoulder;
        o.waist = a.waist;
        o.feet = a.feet;
        o.aura = a.aura;
        o.pet = a.pet;
        o.companion = a.companion;
        o.wingScale = a.wingScale;
        o.cosmeticGlow = a.cosmeticGlow;
        o.cosmeticParticles = a.cosmeticParticles;
        o.color = a.color;
        o.skin3d = a.skin3d;
        if (a.slotTransforms != null) {
            for (var e : a.slotTransforms.entrySet()) {
                if (e.getValue() != null && !e.getValue().isIdentity()) {
                    o.slotTransforms.put(e.getKey(), e.getValue().copy());
                }
            }
        }
        o.vfx = a.vfx == null ? new SkyPilotConfig.VfxSettings() : a.vfx.copy();
        return o;
    }

    public static void apply(Outfit o, SkyPilotConfig.Appearance a) {
        a.cape = o.cape == null ? "none" : o.cape;
        a.hat = o.hat == null ? "none" : o.hat;
        a.back = o.back == null ? "none" : o.back;
        a.face = o.face == null ? "none" : o.face;
        a.shoulder = o.shoulder == null ? "none" : o.shoulder;
        a.waist = o.waist == null ? "none" : o.waist;
        a.feet = o.feet == null ? "none" : o.feet;
        a.aura = o.aura == null ? "none" : o.aura;
        a.pet = o.pet == null ? "none" : o.pet;
        a.companion = o.companion == null ? "none" : o.companion;
        a.wingScale = o.wingScale <= 0 ? 1.0 : Math.max(0.6, Math.min(1.5, o.wingScale));
        a.cosmeticGlow = o.cosmeticGlow;
        a.cosmeticParticles = o.cosmeticParticles;
        a.color = o.color;
        a.skin3d = o.skin3d;
        a.accessory = "none";
        if (a.slotTransforms == null) a.slotTransforms = new LinkedHashMap<>();
        a.slotTransforms.clear();
        if (o.slotTransforms != null) {
            for (var e : o.slotTransforms.entrySet()) {
                if (e.getKey() != null && CosmeticCatalog.SLOTS.contains(e.getKey()) && e.getValue() != null) {
                    var copy = e.getValue().copy();
                    copy.sanitize();
                    if (!copy.isIdentity()) a.slotTransforms.put(e.getKey(), copy);
                }
            }
        }
        if (o.vfx != null) {
            a.vfx = o.vfx.copy();
            a.vfx.sanitize();
        }
    }

    public static void clear(SkyPilotConfig.Appearance a) {
        a.cape = "none";
        a.hat = "none";
        a.back = "none";
        a.face = "none";
        a.shoulder = "none";
        a.waist = "none";
        a.feet = "none";
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
            if (!CosmeticCatalog.valid("face", o.face)) o.face = "none";
            if (!CosmeticCatalog.valid("shoulder", o.shoulder)) o.shoulder = "none";
            if (!CosmeticCatalog.valid("waist", o.waist)) o.waist = "none";
            if (!CosmeticCatalog.valid("feet", o.feet)) o.feet = "none";
            if (!CosmeticCatalog.valid("aura", o.aura)) o.aura = "none";
            if (!CosmeticCatalog.valid("pet", o.pet)) o.pet = "none";
            if (!CosmeticCatalog.valid("companion", o.companion)) o.companion = "none";
            if (!Double.isFinite(o.wingScale) || o.wingScale <= 0) o.wingScale = 1.0;
            o.wingScale = Math.max(0.6, Math.min(1.5, o.wingScale));
            o.color &= 0xFFFFFF;
            if (o.slotTransforms == null) o.slotTransforms = new LinkedHashMap<>();
            o.slotTransforms.entrySet().removeIf(e -> e.getKey() == null || !CosmeticCatalog.SLOTS.contains(e.getKey()) || e.getValue() == null);
            for (var tr : o.slotTransforms.values()) tr.sanitize();
            o.slotTransforms.entrySet().removeIf(e -> e.getValue().isIdentity());
            if (o.vfx == null) o.vfx = new SkyPilotConfig.VfxSettings();
            o.vfx.sanitize();
            if (unique.containsKey(o.name) || unique.size() < MAX_OUTFITS) unique.put(o.name, o);
        }
        a.outfits = new ArrayList<>(unique.values());
    }
}
