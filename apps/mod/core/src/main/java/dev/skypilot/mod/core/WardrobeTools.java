package dev.skypilot.mod.core;

import java.util.*;
import java.util.random.RandomGenerator;

/** Local outfit operations shared by the launcher and game. */
public final class WardrobeTools {
    public static final int[] PALETTE={0xA38BFA,0x55D6D2,0xF09055,0xEAC574,0xDB8AE8,0x8D9DFF};
    public static final class Outfit {
        public String name="",cape="none",hat="none",back="none";
        public int color=0xA38BFA;
        public boolean skin3d;
    }
    private WardrobeTools() {}
    public static Outfit capture(String name,SkyPilotConfig.Appearance a){var o=new Outfit();o.name=name.strip();o.cape=a.cape;o.hat=a.hat;o.back=a.back;o.color=a.color;o.skin3d=a.skin3d;return o;}
    public static void apply(Outfit o,SkyPilotConfig.Appearance a){a.cape=o.cape;a.hat=o.hat;a.back=o.back;a.color=o.color;a.skin3d=o.skin3d;a.accessory="none";}
    public static void randomize(SkyPilotConfig.Appearance a,RandomGenerator random){
        for(String slot:List.of("cape","hat","back")){var pool=CosmeticCatalog.ITEMS.stream().filter(i->i.slot().equals(slot)).toList();pool.get(random.nextInt(pool.size())).equip(a);}
        a.color=PALETTE[random.nextInt(PALETTE.length)];a.accessory="none";
    }
    public static void favorite(SkyPilotConfig.Appearance a,String id){if(!a.favorites.remove(id)&&CosmeticCatalog.ITEMS.stream().anyMatch(i->i.id().equals(id)))a.favorites.add(id);}
    public static void remember(SkyPilotConfig.Appearance a,String name){
        String label=name==null?"":name.strip();if(label.isEmpty()||label.length()>24)throw new IllegalArgumentException("Use a name of 1–24 characters.");
        a.outfits.removeIf(o->o.name.equals(label));if(a.outfits.size()>=8)throw new IllegalArgumentException("Eight outfits maximum. Remove one before saving another.");a.outfits.add(capture(label,a));
    }
    public static void sanitize(SkyPilotConfig.Appearance a){
        if(a.favorites==null)a.favorites=new ArrayList<>();a.favorites=new ArrayList<>(a.favorites.stream().filter(Objects::nonNull).filter(id->CosmeticCatalog.ITEMS.stream().anyMatch(i->i.id().equals(id))).distinct().limit(13).toList());
        if(a.outfits==null)a.outfits=new ArrayList<>();
        Map<String,Outfit> unique=new LinkedHashMap<>();
        for(var o:a.outfits){
            if(o==null||o.name==null||o.name.isBlank())continue;
            o.name=o.name.strip().substring(0,Math.min(24,o.name.strip().length()));
            if(!CosmeticCatalog.valid("cape",o.cape))o.cape="none";
            if(!CosmeticCatalog.valid("hat",o.hat))o.hat="none";
            if(!CosmeticCatalog.valid("back",o.back))o.back="none";
            o.color&=0xFFFFFF;
            if(unique.containsKey(o.name)||unique.size()<8)unique.put(o.name,o);
        }
        a.outfits=new ArrayList<>(unique.values());
    }
}
