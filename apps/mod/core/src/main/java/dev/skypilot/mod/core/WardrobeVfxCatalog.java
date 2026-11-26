package dev.skypilot.mod.core;

import java.util.*;

/**
 * Catalog of custom 3D procedural VFX themes across 7 independently configurable event slots
 * (Attack, Kill, Move, Jump/Land, Dash/Warp, Projectile, Ambient), plus user-imported custom
 * PNG sprite effects from {@code config/skypilot/vfx/}.
 */
public final class WardrobeVfxCatalog {
    public static final List<String> EVENT_SLOTS = List.of(
            "attack", "kill", "move", "jump", "dash", "projectile", "ambient"
    );

    public record VfxStyle(
            String id,
            String name,
            String ja,
            String description,
            String descriptionJa,
            int primaryColor,
            int secondaryColor,
            String primaryTex,
            String ringTex,
            String rarity,
            boolean custom
    ) {
        public String label(boolean japanese) { return japanese ? ja : name; }
        public String detail(boolean japanese) { return japanese ? descriptionJa : description; }

        public int rarityColor() {
            if (custom) return 0x2DD4BF;
            return switch (rarity == null ? "epic" : rarity) {
                case "mythic" -> 0xF472B6;
                case "legendary" -> 0xFBBF24;
                case "epic" -> 0xA855F7;
                default -> 0x38BDF8;
            };
        }
    }

    public static final List<VfxStyle> BUILTIN = List.of(
            new VfxStyle("sakura", "Sakura Blossom", "桜吹雪",
                    "Curved cherry-pink crescent slashes, glowing magic rings & drifting 3D petals!",
                    "桜色の弧状斬撃・花紋リング・舞い散る立体花びらの和風エフェクト。",
                    0xFFB7D5, 0xFF5CA8, "sakura_petal", "magic_circle", "mythic", false),
            new VfxStyle("phoenix", "Crimson Phoenix", "紅蓮フェニックス",
                    "Blazing solar-flame arcs, fiery shockwaves & rising ember plumes!",
                    "紅蓮と黄金の炎弧・衝撃波・舞い昇る火の羽根エフェクト。",
                    0xF97316, 0xFEF08A, "flame_wisp", "shockwave_ring", "mythic", false),
            new VfxStyle("frost", "Glacier Crystal", "氷結クリスタル",
                    "Crystalline ice arcs, six-point snowflakes & shattering frost diamonds!",
                    "透き通る氷刃・雪結晶リング・砕け散る氷ダイヤエフェクト。",
                    0x7DD3FC, 0xE0F2FE, "snowflake", "magic_circle", "legendary", false),
            new VfxStyle("thunder", "Tempest Lightning", "迅雷ライトニング",
                    "Forked 3D lightning bolts, crackling discharge rings & electric sparks!",
                    "稲妻の3D電撃ボルトと蒼白の放電リングが炸裂する雷神エフェクト。",
                    0x38BDF8, 0xFEF08A, "lightning_bolt", "shockwave_ring", "legendary", false),
            new VfxStyle("void", "Abyssal Void", "深淵ボイドリフト",
                    "Dimensional violet rifts, spinning dark arcane sigils & crystal shards!",
                    "紫電の空間裂け目と回転する古代魔法陣が広がる深淵エフェクト。",
                    0xA855F7, 0xF0ABFC, "crystal_shard", "magic_circle", "mythic", false),
            new VfxStyle("starlight", "Astral Starlight", "星海アストラル",
                    "Radiant golden constellations, 4-point anime sparkles & zodiac magic rings!",
                    "黄金の星屑と4芒星スパークル・天球魔法陣が煌めく宇宙エフェクト。",
                    0xFDE047, 0x818CF8, "star_spark", "magic_circle", "mythic", false),
            new VfxStyle("kawaii_heart", "Kawaii Hearts", "ゆめかわハート",
                    "Glossy pastel candy hearts, dreamy pink-cyan rings & sparkling stars!",
                    "ぷっくり輝くパステルハートとゆめかわ光輪が弾けるキュートエフェクト。",
                    0xF472B6, 0x67E8F9, "heart_glow", "shockwave_ring", "legendary", false),
            new VfxStyle("prism", "Chromatic Prism", "極彩プリズム",
                    "Shifting full-spectrum rainbow slashes, faceted crystals & prism waves!",
                    "七色に移ろう虹の斬撃とプリズム結晶が輝くレインボーエフェクト。",
                    0xEC4899, 0x22D3EE, "crystal_shard", "shockwave_ring", "mythic", false),
            new VfxStyle("cyber", "Cyber Neon", "サイバーネオン",
                    "Holographic cyan-magenta hex grids, digital scan rings & laser slashes!",
                    "ネオン六角グリッドとホログラム走査波が閃くサイバーエフェクト。",
                    0x22D3EE, 0xEC4899, "cyber_hex", "cyber_hex", "legendary", false),
            new VfxStyle("jade_dragon", "Jade Dragon", "翡翠龍気",
                    "Emerald-gold dragon energy arcs, glowing spirit wisps & jade runes!",
                    "翡翠と黄金の龍気が渦巻く東洋ファンタジーエフェクト。",
                    0x10B981, 0xFDE047, "flame_wisp", "magic_circle", "legendary", false),
            new VfxStyle("seraph", "Seraph Feathers", "熾天使の羽根",
                    "Heavenly gold-white light shafts, divine halos & floating angel feathers!",
                    "神々しい光の柱と純白の羽根が舞い降りる熾天使エフェクト。",
                    0xFEF9C3, 0xFFFFFF, "feather", "magic_circle", "mythic", false),
            new VfxStyle("ocean", "Abyssal Tide", "蒼海ウェーブ",
                    "Aqua surging waves, glowing ocean bubbles & expanding water ripples!",
                    "透き通る蒼波と発光バブル・広がる水紋リングのアクアエフェクト。",
                    0x06B6D4, 0xA5F3FC, "bubble_orb", "shockwave_ring", "epic", false),
            new VfxStyle("blood_moon", "Crimson Moon", "紅月ヴァンパイア",
                    "Scarlet lunar crescent arcs, crimson shockwaves & glowing night moons!",
                    "真紅の三日月斬撃と紅月の光輪が夜を裂くダークエフェクト。",
                    0xEF4444, 0xFCA5A5, "crescent_moon", "magic_circle", "legendary", false),
            new VfxStyle("melody", "Symphony Notes", "奏でるメロディ",
                    "Glowing neon eighth-notes, rhythmic pulse rings & musical sparkles!",
                    "カラフルに輝く音符とビート波紋が弾けるミュージックエフェクト。",
                    0xEC4899, 0x38BDF8, "music_note", "shockwave_ring", "epic", false),
            new VfxStyle("butterfly", "Morpho Butterflies", "幻想モルフォ蝶",
                    "Iridescent cobalt-violet butterflies fluttering outward in shimmering trails!",
                    "瑠璃色の光蝶が羽ばたきながら軌跡を描く幻想エフェクト。",
                    0x3B82F6, 0xC084FC, "butterfly", "magic_circle", "legendary", false),
            new VfxStyle("solar", "Solar Sunburst", "日輪サンバースト",
                    "Blazing 12-ray sunburst flares, golden rings & radiant light beams!",
                    "12条の太陽フレアと黄金の日輪が炸裂するサンバーストエフェクト。",
                    0xF59E0B, 0xFEF08A, "sun_flare", "shockwave_ring", "legendary", false),
            new VfxStyle("reaper", "Shadow Reaper", "死神の漆黒鎌",
                    "Sweeping obsidian-indigo scythe arcs, crescent runes & spectral embers!",
                    "藍黒の巨大な鎌弧と亡霊の燐火が舞う死神エフェクト。",
                    0x6366F1, 0x1E1B4B, "crescent_moon", "shockwave_ring", "epic", false),
            new VfxStyle("paw", "Kawaii Pawprints", "もふもふ肉球",
                    "Adorable glowing pastel pawprints, soft heart pops & cheerful sparkles!",
                    "歩くたび・跳ねるたびに光る肉球と星が弾ける癒しエフェクト。",
                    0xFDA4AF, 0xFEF08A, "pawprint", "shockwave_ring", "epic", false)
    );

    private static final Map<String, VfxStyle> BUILTIN_BY_ID;
    private static volatile List<VfxStyle> customStyles = List.of();

    static {
        Map<String, VfxStyle> m = new LinkedHashMap<>();
        for (VfxStyle s : BUILTIN) m.put(s.id(), s);
        BUILTIN_BY_ID = Collections.unmodifiableMap(m);
    }

    private WardrobeVfxCatalog() {}

    public static void setCustomStyles(List<VfxStyle> custom) {
        customStyles = custom == null ? List.of() : List.copyOf(custom);
    }

    public static List<VfxStyle> customStyles() {
        return customStyles;
    }

    public static List<VfxStyle> all() {
        if (customStyles.isEmpty()) return BUILTIN;
        List<VfxStyle> out = new ArrayList<>(customStyles.size() + BUILTIN.size());
        out.addAll(customStyles);
        out.addAll(BUILTIN);
        return out;
    }

    public static VfxStyle byId(String id) {
        if (id == null || id.isEmpty() || "none".equals(id)) return null;
        VfxStyle b = BUILTIN_BY_ID.get(id);
        if (b != null) return b;
        for (VfxStyle c : customStyles) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }

    public static boolean validId(String id) {
        if (id == null || "none".equals(id)) return true;
        if (BUILTIN_BY_ID.containsKey(id)) return true;
        return id.startsWith("custom_vfx_") && id.matches("custom_vfx_[a-z0-9_]{1,48}");
    }

    public static String eventLabel(String eventSlot, boolean ja) {
        return switch (eventSlot == null ? "" : eventSlot) {
            case "attack" -> ja ? "攻撃" : "Attack";
            case "kill" -> ja ? "撃破" : "Kill";
            case "move" -> ja ? "移動" : "Move";
            case "jump" -> ja ? "ジャンプ" : "Jump/Land";
            case "dash" -> ja ? "ワープ/疾走" : "Dash/Warp";
            case "projectile" -> ja ? "飛翔体" : "Projectile";
            case "ambient" -> ja ? "常時" : "Ambient";
            default -> eventSlot;
        };
    }

    public static String eventDesc(String eventSlot, boolean ja) {
        return switch (eventSlot == null ? "" : eventSlot) {
            case "attack" -> ja ? "敵への攻撃時に3D斬撃アーク＆衝撃波を発生" : "3D crescent slash arc & hit burst when attacking";
            case "kill" -> ja ? "敵を倒した瞬間に光の柱＆魔法陣フィニッシャー" : "Celestial pillar & explosion burst on mob kill";
            case "move" -> ja ? "歩行・走行・飛行時に足元へ軌跡＆リボンを発生" : "Glowing ribbon trail & motes while moving";
            case "jump" -> ja ? "ジャンプ離陸・着地時に地面へ衝撃波リング" : "Ground shockwave ring on takeoff & landing";
            case "dash" -> ja ? "ダッシュ開始・テレポート/ワープ時に空間リング" : "Portal ring burst on sprint dash & teleport/warp";
            case "projectile" -> ja ? "矢・雪玉・エンダーパール等の飛翔体に光の軌跡" : "Comet wake trailing arrows, pearls & snowballs";
            case "ambient" -> ja ? "待機・移動中に周囲へ常時3Dエフェクトが漂う" : "Continuous 3D ambient motifs drifting around you";
            default -> "";
        };
    }

    public static String equippedId(SkyPilotConfig.VfxSettings vfx, String eventSlot) {
        if (vfx == null || eventSlot == null) return "none";
        return switch (eventSlot) {
            case "attack" -> vfx.attack;
            case "kill" -> vfx.kill;
            case "move" -> vfx.move;
            case "jump" -> vfx.jump;
            case "dash" -> vfx.dash;
            case "projectile" -> vfx.projectile;
            case "ambient" -> vfx.ambient;
            default -> "none";
        };
    }

    public static void equip(SkyPilotConfig.VfxSettings vfx, String eventSlot, String styleId) {
        if (vfx == null || eventSlot == null) return;
        String val = styleId == null ? "none" : styleId;
        switch (eventSlot) {
            case "attack" -> vfx.attack = val;
            case "kill" -> vfx.kill = val;
            case "move" -> vfx.move = val;
            case "jump" -> vfx.jump = val;
            case "dash" -> vfx.dash = val;
            case "projectile" -> vfx.projectile = val;
            case "ambient" -> vfx.ambient = val;
        }
    }

    public static void applyThemeToAll(SkyPilotConfig.VfxSettings vfx, String styleId) {
        if (vfx == null) return;
        String val = styleId == null ? "none" : styleId;
        for (String slot : EVENT_SLOTS) equip(vfx, slot, val);
    }

    public static int activeCount(SkyPilotConfig.VfxSettings vfx) {
        if (vfx == null) return 0;
        int n = 0;
        for (String slot : EVENT_SLOTS) {
            if (!"none".equals(equippedId(vfx, slot))) n++;
        }
        return n;
    }

    private static final Map<String, String> KANA_ALIASES = Map.ofEntries(
            Map.entry("sakura", "さくら サクラ はなびら 花びら 和風 春 ピンク"),
            Map.entry("phoenix", "ほのお 炎 ひ 火 ふぇにっくす 不死鳥 ぐれん"),
            Map.entry("frost", "こおり 氷 ゆき 雪 結晶 ふろすと 青 水色"),
            Map.entry("thunder", "かみなり 雷 いなずま 稲妻 でんげき 電撃 らいてぃんぐ 雷神"),
            Map.entry("void", "やみ 闇 ぼいど しんえん まほうじん 魔法陣 紫"),
            Map.entry("starlight", "ほし 星 ほしくず 星屑 うちゅう 宇宙 せいざ 星座 金"),
            Map.entry("kawaii_heart", "はーと ハート かわいい 可愛い ゆめかわ ピンク"),
            Map.entry("prism", "にじ 虹 れいんぼー レインボー なないろ 七色 ぷりずむ"),
            Map.entry("cyber", "さいばー ねおん ネオン でじたる デジタル 未来"),
            Map.entry("jade_dragon", "りゅう 龍 竜 どらごん ドラゴン ひすい 緑 翡翠"),
            Map.entry("seraph", "てんし 天使 はね 羽根 羽 ひかり 光 白 金"),
            Map.entry("ocean", "うみ 海 みず 水 あわ 泡 ばぶる バブル 波"),
            Map.entry("blood_moon", "つき 月 みかづき 三日月 あか 赤 紅 ばんぱいあ"),
            Map.entry("melody", "おんぷ 音符 おんがく 音楽 みゅーじっく めろでぃ"),
            Map.entry("butterfly", "ちょう 蝶 ちょうちょ ばたふらい もるふぉ 幻想"),
            Map.entry("solar", "たいよう 太陽 ひかり 光 にちりん 黄金 ふれあ"),
            Map.entry("reaper", "しにがみ 死神 かま 鎌 くろ 黒 漆黒 亡霊"),
            Map.entry("paw", "にくきゅう 肉球 ねこ 猫 いぬ 犬 もふもふ かわいい 可愛い 動物")
    );

    public static List<VfxStyle> search(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return all();
        String norm = WardrobeSearch.normalize(rawQuery).trim();
        String[] tokens = norm.split("[\\s,、・/]+");
        List<VfxStyle> out = new ArrayList<>();
        for (VfxStyle s : all()) {
            String alias = KANA_ALIASES.getOrDefault(s.id(), "");
            String idx = WardrobeSearch.normalize(s.id() + " " + s.name() + " " + s.ja() + " " + alias + " "
                    + s.description() + " " + s.descriptionJa() + " " + s.rarity() + (s.custom() ? " custom 自作" : ""));
            boolean ok = true;
            for (String tok : tokens) {
                if (!tok.isEmpty() && !idx.contains(tok)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(s);
        }
        return out;
    }
}
