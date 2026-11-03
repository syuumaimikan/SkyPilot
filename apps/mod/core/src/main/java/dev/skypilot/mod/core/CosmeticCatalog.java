package dev.skypilot.mod.core;

import java.util.List;

/** Original, bundled designs. IDs are shared by both wardrobe interfaces and the renderer. */
public final class CosmeticCatalog {
    public record Item(String id, String slot, String name, String ja, String description, String descriptionJa, int color) {
        public String label(boolean japanese) { return japanese ? ja : name; }
        public String detail(boolean japanese) { return japanese ? descriptionJa : description; }
        public void equip(SkyPilotConfig.Appearance a) {
            switch(slot) { case "cape" -> a.cape=id; case "hat" -> a.hat=id; case "back" -> a.back=id; }
        }
        public boolean equipped(SkyPilotConfig.Appearance a) {
            return id.equals(switch(slot) {case "cape" -> a.cape;case "hat" -> a.hat;default -> a.back;});
        }
    }

    public static final List<Item> ITEMS = List.of(
        // Capes (16)
        new Item("aurora", "cape", "Aurora", "オーロラ", "Northern light, woven in pixels with shimmering waves.", "波打つピクセルで織り込んだオーロラ。", 0x55D6D2),
        new Item("midnight", "cape", "Midnight", "ミッドナイト", "A night sky constellation with twinkling stars.", "瞬く星屑が夜空に浮かぶ星座。", 0x8D9DFF),
        new Item("ember", "cape", "Ember", "エンバー", "A bright trail of rising incandescent embers.", "舞い上がる火の粉の軌跡。", 0xF09055),
        new Item("prism", "cape", "Prism", "プリズム", "A dynamic spectrum of flowing rainbow ribbons.", "流れる虹色の光のリボン。", 0xDB8AE8),
        new Item("tide", "cape", "Tide", "タイド", "Surging foamy waves on a deep blue ocean.", "深い青に重なる波頭のうねり。", 0x55B7F2),
        new Item("eclipse", "cape", "Eclipse", "エクリプス", "A breathing golden corona on charcoal cloth.", "黒布に輝く金色の日食フレア。", 0xEAC574),
        new Item("phantom", "cape", "Phantom", "ファントム", "Spectral silk with an eerie cyan glow.", "幽玄な青緑の光を帯びた幽霊の絹。", 0x3CE5D0),
        new Item("void_walker", "cape", "Void Walker", "ボイドウォーカー", "Shifting purple runes of the End dimension.", "ジ・エンドの深淵を漂う紫のルーン。", 0x9333EA),
        new Item("sakura", "cape", "Sakura Breeze", "桜吹雪", "Cherry blossom petals floating on a gentle wind.", "春風に乗って舞い散る桜の花びら。", 0xFFB7C5),
        new Item("hyperion", "cape", "Hyperion Crest", "ハイペリオン", "Radiant golden shockwaves of an ancient blade.", "古の銘刃が放つ黄金の衝撃波。", 0xF59E0B),
        new Item("cyberpunk", "cape", "Cyber Grid", "サイバーグリッド", "High-tech neon scanlines cascading in code.", "コードの滝が流れるネオンの電子格子。", 0x06B6D4),
        new Item("frostbite", "cape", "Frostbite", "フロストバイト", "Glacial diamond crystals that glint in the cold.", "極寒の氷河で煌めくダイヤモンドの結晶。", 0x93C5FD),
        new Item("dragon_scale", "cape", "Dragon Scale", "ドラゴンスケール", "Iridescent violet scales of an elder drake.", "古龍の背を覆う虹色に輝く紫の鱗。", 0x8B5CF6),
        new Item("inferno", "cape", "Inferno", "インフェルノ", "Molten magma rivers pulsing with volcanic fury.", "火山の怒りを宿した灼熱の溶岩流。", 0xEF4444),
        new Item("starlight", "cape", "Starlight", "スターライト", "Cosmic nebulae woven with gleaming stardust.", "星屑の瞬きを織り交ぜた宇宙の星雲。", 0xFDE047),
        new Item("royal_mantle", "cape", "Royal Mantle", "ロイヤルマント", "Regal crimson velvet bordered by shimmering gold.", "輝く金糸で縁取られた高貴な深紅のベルベット。", 0xDC2626),

        // Head / Hats / Halos (16)
        new Item("crown", "hat", "Sovereign Crown", "クラウン", "A sculpted gold pixel crown with pulsing gems.", "宝玉が煌めく金色の立体ピクセルクラウン。", 0xF5CD75),
        new Item("halo", "hat", "Celestial Halo", "ヘイロー", "A floating ring of warm light that bobs and spins.", "頭上で浮遊して回転する暖かな光の輪。", 0xF5CD75),
        new Item("dark_halo", "hat", "Abyssal Halo", "アビサルヘイロー", "A counter-revolving ring of dark void flames.", "反転回転する漆黒の虚無の輪。", 0x8B5CF6),
        new Item("angel_halo", "hat", "Seraph Halo", "セラフヘイロー", "Concentric golden halos with a floating central star.", "中央の星が浮遊する二重の光輪。", 0xFDE047),
        new Item("ears", "hat", "Cat Ears", "キャットイヤー", "A pair of graphite ears with lifelike twitches.", "グラファイト色の猫耳。時折ピクピク動く。", 0xC799E6),
        new Item("horns", "hat", "Obsidian Horns", "黒曜石の角", "Two curved horns with pulsing runic tips.", "ルーンの光が脈動する黒曜石の二本角。", 0xB39CF5),
        new Item("dragon_horns", "hat", "Dragon Horns", "ドラゴンホーン", "Majestic spiraled horns of an ancient drake.", "古龍の威厳を宿した螺旋の大角。", 0x7C3AED),
        new Item("valkyrie", "hat", "Valkyrie Helm", "ヴァルキリー", "A winged golden circlet blessed by the heavens.", "天上の加護を受けた有翼の黄金兜。", 0xEAB308),
        new Item("wizard", "hat", "Mystic Hat", "ウィザードハット", "A pointed hat embroidered with shining constellations.", "星座の刺繍が光る星詠みのとんがり帽子。", 0x6366F1),
        new Item("kitsune", "hat", "Kitsune Mask", "狐面", "A traditional fox mask tilted with spirit flames.", "青白い狐火をまとった斜め掛けの狐面。", 0xEF4444),
        new Item("cyber_visor", "hat", "Cyber Visor", "サイバーバイザー", "A holographic HUD displaying sweeping scanlines.", "走査線が流れるサイバーホログラムバイザー。", 0x06B6D4),
        new Item("tophat", "hat", "Steampunk Top Hat", "トップハット", "A brass-trimmed hat fitted with ticking clockwork gears.", "真鍮の歯車が静かに回転するシルクハット。", 0xB45309),
        new Item("sakura_blossom", "hat", "Sakura Blossom", "桜の髪飾り", "Delicate pink petals that gently orbit your head.", "頭の周りを優しく舞う満開の桜の花びら。", 0xF472B6),
        new Item("wither_gaze", "hat", "Wither Gaze", "ウィザーゲイズ", "Three ethereal mini skulls hovering in orbit.", "周囲を旋回する三つの浮遊ウィザースカル。", 0x475569),
        new Item("flower_crown", "hat", "Flower Crown", "フラワークラウン", "A vibrant wreath of living forest blossoms.", "森の息吹を感じさせるみずみずしい花の冠。", 0x10B981),
        new Item("demon_horns", "hat", "Fiery Horns", "炎熱の角", "Curved demonic horns radiating intense heat.", "熱波を放つ燃え盛る悪魔の角。", 0xF97316),

        // Wings & Backwear (16)
        new Item("wings", "back", "Astral Wings", "アストラルウィング", "A feathered violet silhouette that beats in flight.", "羽ばたく紫の羽が描くシルエット。", 0xA38BFA),
        new Item("dragon", "back", "Dragon Wings", "ドラゴンウィング", "Sculpted wings with a flexible violet membrane.", "紫の膜が伸縮する立体的な大翼。", 0xA38BFA),
        new Item("phoenix", "back", "Phoenix Wings", "フェニックスウィング", "Blazing feathers that burn with incandescent fury.", "燃え盛る炎と金色が重なる不死鳥の翼。", 0xF09055),
        new Item("fairy", "back", "Fairy Wings", "フェアリーウィング", "Translucent four-part wings that flutter rapidly.", "高速で羽ばたく透明な妖精の四翅。", 0x38BDF8),
        new Item("cyber_wings", "back", "Cyber Thrusters", "サイバーウィング", "Articulated mechanical blades with ion plasma exhaust.", "プラズマジェットを噴射する機械仕掛けの推進翼。", 0x06B6D4),
        new Item("angel_wings", "back", "Seraph Wings", "熾天使の翼", "Six layered wings of pure divine luminescence.", "純白の光輝を放つ六枚の神聖な大翼。", 0xFEF08A),
        new Item("demon_wings", "back", "Demon Wings", "悪魔の翼", "Jagged bat-like wings coursing with volcanic magma.", "溶岩の脈動が走る禍々しい蝙蝠の羽。", 0xDC2626),
        new Item("crystal_wings", "back", "Crystal Shards", "クリスタルウィング", "Floating prismatic shards hovering in wing formation.", "翼の陣形を保ちながら浮遊する水晶の破片。", 0xC084FC),
        new Item("steampunk", "back", "Clockwork Glider", "クロックワークウィング", "Brass articulated struts driven by spinning gears.", "歯車とピストンで連動する真鍮の滑空翼。", 0xD97706),
        new Item("void_tendrils", "back", "Void Tendrils", "ボイドテンドリル", "Six undulating shadow tentacles slithering behind.", "背後でうねりながら蠢く六本の影の触手。", 0x7E22CE),
        new Item("katana_scabbard", "back", "Dual Katanas", "双剣の鞘", "Crossed enchanted blades radiating elemental energy.", "背中に交差した魔力を帯びる二振りの名刀。", 0x3B82F6),
        new Item("quiver", "back", "Mystic Quiver", "神秘の矢筒", "An infinite archer's quiver with glowing arrows.", "淡い光を放つ無限の魔導矢が収まった矢筒。", 0x14B8A6),
        new Item("jetpack", "back", "Plasma Jetpack", "ジェットパック", "A dual-thruster rocket pack with live flame cones.", "噴射炎が揺らめくツインロケットパック。", 0xEA580C),
        new Item("backpack", "back", "Explorer's Pack", "冒険者のリュック", "A rugged leather pack with a swinging lit lantern.", "夜道を照らすカンテラが揺れる冒険者の革鞄。", 0x854D0E),
        new Item("shield_crest", "back", "Aegis Crest", "イージスの盾", "A paladin shield surrounded by revolving light wards.", "回転する守護陣をまとった聖騎士の大盾。", 0x60A5FA),
        new Item("solar_cloak", "back", "Solar Cloak", "ソーラーマント", "Radiating sun crest radiating warm solar flares.", "太陽のフレアを背負う神々しい光輪の外套。", 0xFBBF24)
    );

    private CosmeticCatalog() {}

    public static boolean valid(String slot, String id) {
        return "none".equals(id) || ITEMS.stream().anyMatch(i -> i.slot.equals(slot) && i.id.equals(id));
    }

    public static void migrate(SkyPilotConfig.Appearance a) {
        if ("crown".equals(a.accessory) || "halo".equals(a.accessory)) {
            if ("none".equals(a.hat)) a.hat = a.accessory;
        }
        if ("wings".equals(a.accessory) && "none".equals(a.back)) a.back = a.accessory;
        a.accessory = "none";
    }
}
