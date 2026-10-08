package dev.skypilot.mod.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Explicit user shortcuts; defaults are unbound to avoid stealing existing controls. */
final class ClientShortcuts {
    private static final Map<KeyMapping,Supplier<Screen>> screens=new LinkedHashMap<>();
    private static KeyMapping copy,cycleShader,randomOutfit,pauseFocus;
    private static KeyMapping mediaToggle,mediaNext,mediaPrevious;
    private static KeyMapping tttCenter;
    private static KeyMapping nightVision;
    static void register(KeyMapping.Category category){
        add("wardrobe",()->new AppearanceScreen(null),category);
        add("desk",()->new DeskScreen(null),category);
        add("puzzle",()->new PuzzleScreen(null),category);
        add("visual",()->new VisualScreen(null),category);
        add("utility",()->new UtilityScreen(null),category);
        add("keys",()->new KeySettingsScreen(null),category);
        add("perf",()->new PerformanceScreen(null),category);
        cycleShader=action("cycle_shader",category);randomOutfit=action("random_outfit",category);pauseFocus=action("pause_focus",category);
        copy=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skypilot.copy_position",InputConstants.UNKNOWN.getValue(),category));
        mediaToggle=action("media_toggle",category);mediaNext=action("media_next",category);mediaPrevious=action("media_previous",category);
        tttCenter=action("ttt_center",category);
        nightVision=action("night_vision",category);
    }
    private static KeyMapping action(String name,KeyMapping.Category category){return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skypilot."+name,InputConstants.UNKNOWN.getValue(),category));}
    private static void add(String name,Supplier<Screen> screen,KeyMapping.Category category){screens.put(KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skypilot."+name,InputConstants.UNKNOWN.getValue(),category)),screen);}
    static void tick(Minecraft mc){
        while(mediaToggle.consumeClick())WindowsMedia.command("toggle");
        while(mediaNext.consumeClick())WindowsMedia.command("next");
        while(mediaPrevious.consumeClick())WindowsMedia.command("previous");
        while(tttCenter.consumeClick())PuzzleGuides.calibrateBoard();
        boolean available=mc.player!=null&&mc.gui.screen()==null;
        while(nightVision.consumeClick())if(available){var v=SkyPilot.get().config().visuals;v.nightVision=!v.nightVision;SkyPilot.get().saveConfig();notice(mc,SkyPilot.get().lang().t("Night vision: ","暗視: ")+(v.nightVision?"ON":"OFF"));}
        boolean opened=false;
        for(var entry:screens.entrySet())while(entry.getKey().consumeClick()){if(available&&!opened){mc.gui.setScreen(entry.getValue().get());opened=true;}}
        while(copy.consumeClick())if(available&&!opened)copyPosition(mc);
        while(cycleShader.consumeClick())if(available&&!opened){var shaders=dev.skypilot.mod.core.VisualSettings.SHADERS;var v=SkyPilot.get().config().visuals;v.shader=shaders.get((shaders.indexOf(v.shader)+1)%shaders.size());SkyPilot.get().saveConfig();notice(mc,SkyPilot.get().lang().t("Post effect: ","画面効果: ")+v.shader);}
        while(randomOutfit.consumeClick())if(available&&!opened){dev.skypilot.mod.core.WardrobeTools.randomize(SkyPilot.get().config().appearance,java.util.concurrent.ThreadLocalRandom.current());SkyPilot.get().saveConfig();notice(mc,SkyPilot.get().lang().t("Random outfit applied","ランダムコーデを適用"));}
        while(pauseFocus.consumeClick())if(available&&!opened){DeskRuntime.FOCUS.toggle(DeskRuntime.now());notice(mc,SkyPilot.get().lang().t("Focus: ","集中タイマー: ")+DeskRuntime.FOCUS.display(DeskRuntime.now()));}
    }
    private static void notice(Minecraft mc,String text){mc.gui.hud.setOverlayMessage(Component.literal(text),false);}
    static void copyPosition(Minecraft mc){if(mc.player==null)return;var pos=mc.player.blockPosition();String text=pos.getX()+" "+pos.getY()+" "+pos.getZ();mc.keyboardHandler.setClipboard(text);mc.gui.hud.setOverlayMessage(Component.literal(SkyPilot.get().lang().t("Coordinates copied: ","座標をコピー: ")+text),false);}
}
