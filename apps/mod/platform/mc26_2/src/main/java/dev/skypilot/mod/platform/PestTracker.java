package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.PestNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;

/** Tracks current client entities, drops despawned targets, and draws no hidden entities. */
final class PestTracker {
    record Pest(Entity entity, String name) {}
    private static List<Pest> pests = List.of();
    private static long nextScan;
    private static boolean available(Minecraft mc) {
        return Feat.on(Features.PEST_TRACKER) && mc.player != null && mc.level != null
            && "GARDEN".equals(SkyPilot.get().context().islandId());
    }
    static void tick() {
        var mc = Minecraft.getInstance();
        if (!available(mc)) { pests = List.of(); nextScan=0; return; }
        long now=System.nanoTime();if(now<nextScan)return;nextScan=now+100_000_000L;
        var entities = new ArrayList<Entity>();
        for (var e : mc.level.entitiesForRendering()) entities.add(e);
        pests=detect(mc,entities);
    }
    static List<Pest> detect(Minecraft mc,List<Entity> entities) {
        var found = new LinkedHashMap<Integer,Pest>();
        for (var label : entities) {
            if (!label.hasCustomName() || label.distanceToSqr(mc.player) > 96*96) continue;
            var name = PestNames.identify(label.getCustomName().getString());
            if (name.isEmpty()) continue;
            Entity body = label;
            // Hypixel's health nametag can be an invisible stand above a visible body.
            if (label instanceof ArmorStand && label.isInvisible()) {
                body = entities.stream().filter(e -> e instanceof LivingEntity && !(e instanceof ArmorStand)
                    && e != mc.player && !e.isInvisible() && e.isAlive()
                    && e.distanceToSqr(label) < 9 && mc.player.hasLineOfSight(e))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(label))).orElse(null);
            }
            if (body != null && body.isAlive() && !body.isInvisible() && mc.player.hasLineOfSight(body))
                found.put(body.getId(), new Pest(body,name.get()));
        }
        return found.values().stream().sorted(Comparator.comparingDouble(p -> p.entity.distanceToSqr(mc.player))).limit(24).toList();
    }
    private static boolean visible(Minecraft mc,Pest p) {
        return !p.entity.isRemoved() && p.entity.isAlive() && !p.entity.isInvisible() && mc.player.hasLineOfSight(p.entity);
    }
    static void world() {
        var mc = Minecraft.getInstance(); if (!available(mc)) return;
        try (var ignored=mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (var p : pests) if (visible(mc,p)) {
                var position=p.entity.getBoundingBox().getCenter();
                Gizmos.cuboid(p.entity.getBoundingBox().inflate(.15),GizmoStyle.stroke(0xFF55EE88,2f));
                Gizmos.billboardText(p.name+String.format(Locale.ROOT," %.1fm",mc.player.distanceTo(p.entity)),position.add(0,1,0),TextGizmo.Style.forColorAndCentered(0xFF55EE88));
                Gizmos.line(mc.player.getEyePosition().add(0,-.5,0),position,0xAA55EE88,2f);
            }
        }
    }
    static void hud(GuiGraphicsExtractor g) {
        var mc=Minecraft.getInstance(); if (!available(mc) || mc.gui.screen()!=null) return;
        var nearest=pests.stream().filter(p -> visible(mc,p)).findFirst();
        String text=nearest.map(p -> p.name+String.format(Locale.ROOT," %.1fm ",mc.player.distanceTo(p.entity))
            +Waypoints.arrow(mc.player.getYRot(),p.entity.getX()-mc.player.getX(),p.entity.getZ()-mc.player.getZ())
            +String.format(Locale.ROOT," · %.0f %.0f %.0f",p.entity.getX(),p.entity.getY(),p.entity.getZ()))
            .orElse(SkyPilot.get().lang().t("No visible pest · approach the marked plot","見えるペストなし · 対象プロットに近づいてください"));
        int x=(g.guiWidth()-mc.font.width(text))/2;
        g.text(mc.font,text,x,32,0xFF55EE88,true);
    }
}
