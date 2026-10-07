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
    public static final List<Item> ITEMS=List.of(
        new Item("aurora","cape","Aurora","オーロラ","Northern light, woven in pixels.","ピクセルで織り込んだオーロラ。",0x55D6D2),
        new Item("midnight","cape","Midnight","ミッドナイト","A constellation after dark.","夜空に浮かぶ星座。",0x8D9DFF),
        new Item("ember","cape","Ember","エンバー","A bright trail of falling embers.","降り注ぐ火の粉の軌跡。",0xF09055),
        new Item("prism","cape","Prism","プリズム","A spectrum of diagonal ribbons.","斜めに重なる光のリボン。",0xDB8AE8),
        new Item("tide","cape","Tide","タイド","Quiet waves on a deep blue sea.","深い青に重なる静かな波。",0x55B7F2),
        new Item("eclipse","cape","Eclipse","エクリプス","A golden eclipse on charcoal.","チャコールに輝く金色の日食。",0xEAC574),
        new Item("crown","hat","Sovereign","クラウン","A sculpted gold pixel crown.","金色の立体ピクセルクラウン。",0xF5CD75),
        new Item("halo","hat","Celestial","ヘイロー","A floating ring of warm light.","頭上に浮かぶ暖かな光の輪。",0xF5CD75),
        new Item("ears","hat","Cat ears","キャットイヤー","A pair of graphite ears.","グラファイト色の猫耳。",0xC799E6),
        new Item("horns","hat","Obsidian horns","ホーン","Two curved, obsidian horns.","黒曜石を思わせる二本の角。",0xB39CF5),
        new Item("wings","back","Astral wings","アストラルウィング","A feathered violet silhouette.","紫の羽が描くシルエット。",0xA38BFA),
        new Item("dragon","back","Dragon wings","ドラゴンウィング","Sculpted wings with a violet membrane.","紫の膜を持つ立体的な翼。",0xA38BFA),
        new Item("phoenix","back","Phoenix wings","フェニックスウィング","Layered feathers in ember and gold.","炎と金色が重なる羽。",0xF09055));
    private CosmeticCatalog() {}
    public static boolean valid(String slot,String id) { return "none".equals(id)||ITEMS.stream().anyMatch(i->i.slot.equals(slot)&&i.id.equals(id)); }
    public static void migrate(SkyPilotConfig.Appearance a) {
        if ("crown".equals(a.accessory)||"halo".equals(a.accessory)) {if("none".equals(a.hat))a.hat=a.accessory;}
        if ("wings".equals(a.accessory)&&"none".equals(a.back))a.back=a.accessory;
        a.accessory="none";
    }
}
