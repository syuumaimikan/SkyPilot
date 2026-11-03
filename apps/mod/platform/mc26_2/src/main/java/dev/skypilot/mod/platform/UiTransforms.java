package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.hud.HudLayout;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;

public final class UiTransforms {
    public record ElementInfo(String id, String nameJa, String nameEn, String mod, int defaultX, int defaultY, int defaultW, int defaultH) {}

    private static final List<ElementInfo> REGISTRY = new ArrayList<>();
    private static final Map<String, ElementInfo> BY_ID = new LinkedHashMap<>();

    static {
        // Vanilla elements
        reg("scoreboard", "スコアボード", "Scoreboard", "Vanilla", -160, 0, 155, 130);
        reg("actionbar", "アクションバー", "Action Bar", "Vanilla", 0, -72, 260, 12);
        reg("bossbar", "ボスバー", "Boss Bar", "Vanilla", 0, 12, 184, 32);
        reg("hotbar", "ホットバー/体力", "Hotbar & Health", "Vanilla", 0, -48, 184, 48);
        reg("effects", "ステータス効果", "Status Effects", "Vanilla", -100, 1, 98, 50);

        // SkyPilot elements
        reg("skypilot_hud", "SkyPilot メインHUD", "SkyPilot Main HUD", "SkyPilot", 4, 4, 200, 80);
        reg("info_hud", "SkyPilot 情報HUD", "SkyPilot Info HUD", "SkyPilot", 4, 90, 180, 60);
        reg("utility_hud", "SkyPilot ユーティリティHUD", "SkyPilot Utility HUD", "SkyPilot", -130, 4, 120, 50);
        reg("media", "メディアプレイヤー", "Media Player", "SkyPilot", 8, -88, 200, 42);
        reg("garden", "ペスト地図", "Garden Pest Map", "SkyPilot", 8, 170, 105, 105);
        reg("dungeon", "ダンジョンマップ", "Dungeon Map", "SkyPilot", -112, 64, 104, 116);
        reg("solver", "ダンジョン操作ガイド", "Dungeon Solver Guide", "SkyPilot", 0, 48, 300, 32);

        // Skyblocker elements
        reg("skyblocker_solver", "Skyblocker ソルバー案内", "Skyblocker Solver", "Skyblocker", 0, 84, 280, 36);
        reg("skyblocker_dungeon", "Skyblocker ダンジョン案内", "Skyblocker Dungeon", "Skyblocker", 10, 40, 160, 70);
        reg("skyblocker_puzzle", "Skyblocker パズル", "Skyblocker Puzzle", "Skyblocker", -150, 185, 140, 60);
        reg("skyblocker_hud", "Skyblocker ステータスHUD", "Skyblocker Status HUD", "Skyblocker", 0, -90, 200, 16);
        reg("skyblocker_party", "Skyblocker パーティHUD", "Skyblocker Party HUD", "Skyblocker", 8, 280, 120, 64);
    }

    private static void reg(String id, String ja, String en, String mod, int dx, int dy, int w, int h) {
        ElementInfo info = new ElementInfo(id, ja, en, mod, dx, dy, w, h);
        REGISTRY.add(info);
        BY_ID.put(id, info);
    }

    public static List<String> allElements() {
        initOtherMods();
        return new ArrayList<>(BY_ID.keySet());
    }

    private static boolean otherModsScanned = false;
    private static void initOtherMods() {
        if (otherModsScanned) return;
        otherModsScanned = true;
        try {
            var loader = FabricLoader.getInstance();
            if (loader != null) {
                for (var mod : loader.getAllMods()) {
                    String modId = mod.getMetadata().getId();
                    if (modId.equals("minecraft") || modId.equals("fabricloader") || modId.equals("java")
                            || modId.startsWith("fabric") || modId.equals("skypilot") || modId.equals("skyblocker")) continue;
                    String modName = mod.getMetadata().getName();
                    String elementId = "mod_" + modId;
                    if (!BY_ID.containsKey(elementId)) {
                        reg(elementId, modName + " GUI要素", modName + " GUI Overlay", modName, 10, 100 + (REGISTRY.size() * 18) % 160, 140, 40);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isKnown(String id) {
        return BY_ID.containsKey(id) || id.startsWith("mod_") || java.util.Set.of("scoreboard","actionbar","bossbar","hotbar","effects","media","garden","dungeon","solver").contains(id);
    }

    public static SkyPilotConfig.UiPosition position(String id) {
        return SkyPilot.get().config().uiPositions.computeIfAbsent(id, k -> new SkyPilotConfig.UiPosition());
    }

    public static HudLayout.Box base(String id, int w, int h) {
        ElementInfo info = BY_ID.get(id);
        if (info == null) {
            initOtherMods();
            info = BY_ID.get(id);
        }
        if (info != null) {
            int x = info.defaultX() < 0 ? w + info.defaultX() : info.defaultX() == 0 ? (w - info.defaultW()) / 2 : info.defaultX();
            int y = info.defaultY() < 0 ? h + info.defaultY() : info.defaultY() == 0 ? (h - info.defaultH()) / 2 : info.defaultY();
            return new HudLayout.Box(Math.max(0, x), Math.max(0, y), info.defaultW(), info.defaultH());
        }
        return switch (id) {
            case "scoreboard" -> new HudLayout.Box(w - 160, h / 2 - 65, 155, 130);
            case "actionbar" -> new HudLayout.Box(w / 2 - 130, h - 72, 260, 12);
            case "bossbar" -> new HudLayout.Box(w / 2 - 92, 12, 184, 32);
            case "hotbar" -> new HudLayout.Box(w / 2 - 92, h - 48, 184, 48);
            case "effects" -> new HudLayout.Box(w - 100, 1, 98, 50);
            case "media" -> new HudLayout.Box(8, h - 88, 200, 42);
            case "garden" -> new HudLayout.Box(8, 170, 105, 105);
            case "solver" -> new HudLayout.Box(w / 2 - 150, 48, 300, 32);
            default -> new HudLayout.Box(w - 112, 64, 104, 116);
        };
    }

    public static HudLayout.Box box(String id, int w, int h) {
        var b = base(id, w, h);
        var p = position(id);
        return new HudLayout.Box(b.x() + p.dx, b.y() + p.dy, (int) Math.max(20, b.width() * p.scale), (int) Math.max(10, b.height() * p.scale));
    }

    public static void begin(GuiGraphicsExtractor g, String id) {
        var b = base(id, g.guiWidth(), g.guiHeight());
        begin(g, id, b.x(), b.y());
    }

    public static void begin(GuiGraphicsExtractor g, String id, int x, int y) {
        g.pose().pushMatrix();
        var sp = SkyPilot.get();
        if (sp == null) return;
        var p = position(id);
        if (p.hidden) {
            g.pose().scale(0.0001f, 0.0001f);
            g.pose().translate(-100000.0f, -100000.0f);
            return;
        }
        g.pose().translate(x + p.dx, y + p.dy);
        g.pose().scale((float) p.scale, (float) p.scale);
        g.pose().translate(-x, -y);
    }

    public static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    public static String name(String id) {
        ElementInfo info = BY_ID.get(id);
        if (info != null) {
            Lang l = SkyPilot.get() != null ? SkyPilot.get().lang() : Lang.JA;
            return l == Lang.EN ? info.nameEn() : info.nameJa();
        }
        return switch (id) {
            case "scoreboard" -> "スコアボード";
            case "actionbar" -> "アクションバー";
            case "bossbar" -> "ボスバー";
            case "hotbar" -> "ホットバー/体力";
            case "effects" -> "ステータス効果";
            case "media" -> "メディア";
            case "garden" -> "ペスト地図";
            case "solver" -> "ダンジョン操作ガイド";
            default -> "ダンジョンマップ";
        };
    }

    public static String modName(String id) {
        ElementInfo info = BY_ID.get(id);
        return info != null ? info.mod() : "Vanilla";
    }

    public static int color(String id) {
        ElementInfo info = BY_ID.get(id);
        if (info == null) return 0xFF999999;
        return switch (info.mod()) {
            case "SkyPilot" -> 0xFFA38BFA;
            case "Vanilla" -> 0xFFFFB347;
            case "Skyblocker" -> 0xFF38BDF8;
            default -> 0xFF34D399;
        };
    }
}
