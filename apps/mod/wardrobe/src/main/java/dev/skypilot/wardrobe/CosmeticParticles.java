package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commercial-grade custom 3D emissive micro-glitter &amp; motif wake for equipped cosmetics
 * (replaces all coarse vanilla smoke/potion particles with crisp 256×256 HDR glow sprites!).
 */
final class CosmeticParticles {
    private static int tickCounter;

    private CosmeticParticles() {}

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (p == null || mc.level == null || rt == null) return;
        if (!mc.isWindowActive() || mc.gui.screen() != null) return;

        SkyPilotConfig.Appearance a = rt.appearance();
        tickCounter++;
        int interval = 4;
        if (tickCounter % interval != 0) return;

        var rng = ThreadLocalRandom.current();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (a.cosmeticParticles && !p.isInvisible() && !p.isSpectator()) {
            spawnForPlayer(p, a, true, firstPerson, interval, rng);
        }

        if (a.showOtherPlayers && a.otherPlayerParticles && (tickCounter % (interval * 3) == 0)) {
            int rendered = 0;
            for (AbstractClientPlayer remote : mc.level.players()) {
                if (remote == null || remote == p || remote.isInvisible() || remote.isSpectator()) continue;
                if (remote.distanceToSqr(p) > 48.0 * 48.0) continue;
                SkyPilotConfig.Appearance ra = RemoteWardrobeSync.getRemote(remote.getUUID());
                if (ra == null || !ra.cosmeticParticles) continue;
                spawnForPlayer(remote, ra, false, false, interval * 3, rng);
                if (++rendered >= 10) break;
            }
        }
    }

    private static void spawnForPlayer(
            AbstractClientPlayer p,
            SkyPilotConfig.Appearance a,
            boolean isLocal,
            boolean firstPerson,
            int interval,
            ThreadLocalRandom rng
    ) {
        double speed = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z);
        double bodyYawRad = Math.toRadians(p.yBodyRot);
        double backX = Math.sin(bodyYawRad);
        double backZ = -Math.cos(bodyYawRad);
        double rightX = Math.cos(bodyYawRad);
        double rightZ = Math.sin(bodyYawRad);

        // 1. Delicate wake glitter behind Wings / Cloak / Waist tail / Feet
        String hero = !"none".equals(a.back) ? a.back
                : (!"none".equals(a.cape) ? a.cape
                : (!"none".equals(a.waist) ? a.waist : a.feet));
        boolean allowBack = !firstPerson || a.firstPersonWings || speed > 0.08;
        if (!"none".equals(hero) && allowBack) {
            boolean moving = speed > 0.07 || !p.onGround();
            if (moving || (tickCounter % (interval * 3) == 0)) {
                var item = CosmeticCatalog.byId(hero);
                int c1 = item != null ? item.color() : 0xA38BFA;
                int c2 = 0xFFFFFF;
                String tex = textureFor(hero, rng);
                double side = (rng.nextDouble() - 0.5) * 1.15;
                double distBack = firstPerson ? 0.95 : 0.52;
                double px = p.getX() + backX * distBack + rightX * side;
                double py = p.getY() + 0.75 + rng.nextDouble() * 0.90;
                double pz = p.getZ() + backZ * distBack + rightZ * side;
                boolean falling = "sakura_petal".equals(tex) || "snowflake".equals(tex) || "feather".equals(tex);
                WardrobeVfxEngine.spawnCosmeticGlitter(
                        px, py, pz,
                        (rng.nextDouble() - 0.5) * 0.015,
                        falling ? -0.014 : 0.016,
                        (rng.nextDouble() - 0.5) * 0.015,
                        tex, c1, c2, 0.14f + rng.nextFloat() * 0.07f, 18, true
                );
            }
        }

        // 2. Aura delicate orbital sparkle dust (never bulky or blocking!)
        if (!"none".equals(a.aura) && (!firstPerson || a.firstPersonAura) && (tickCounter % (interval * 2) == 0)) {
            var item = CosmeticCatalog.byId(a.aura);
            int c1 = item != null ? item.color() : 0x55D6D2;
            String tex = textureFor(a.aura, rng);
            double ang = (tickCounter * 0.25) + rng.nextDouble() * Math.PI * 2.0;
            double r = (firstPerson ? 1.18 : 0.88) + rng.nextDouble() * 0.24;
            WardrobeVfxEngine.spawnCosmeticGlitter(
                    p.getX() + Math.cos(ang) * r,
                    p.getY() + 0.30 + rng.nextDouble() * 1.35,
                    p.getZ() + Math.sin(ang) * r,
                    -Math.sin(ang) * 0.018, 0.014, Math.cos(ang) * 0.018,
                    tex, c1, 0xFFFFFF, 0.12f + rng.nextFloat() * 0.06f, 20, true
            );
        }

        // 3. Cute subtle Pet & Companion shimmer motes
        if (tickCounter % (interval * 4) == 0) {
            if (!"none".equals(a.pet) && (!firstPerson || a.firstPersonPet)) {
                var snap = isLocal ? PetWorldPhysics.petSnapshot(1.0f) : PetWorldPhysics.remotePetSnapshot(p.getUUID(), 1.0f);
                if (snap != null) {
                    var item = CosmeticCatalog.byId(a.pet);
                    int c1 = item != null ? item.color() : 0xF472B6;
                    WardrobeVfxEngine.spawnCosmeticGlitter(
                            snap.x + (rng.nextDouble() - 0.5) * 0.32,
                            snap.y + 0.16 + rng.nextDouble() * 0.20,
                            snap.z + (rng.nextDouble() - 0.5) * 0.32,
                            0.0, 0.016, 0.0,
                            textureFor(a.pet, rng), c1, 0xFFFFFF, 0.11f, 16, true
                    );
                }
            }
            if (!"none".equals(a.companion) && (!firstPerson || a.firstPersonCompanion) && rng.nextInt(2) == 0) {
                var snap = isLocal ? PetWorldPhysics.companionSnapshot(1.0f) : PetWorldPhysics.remoteCompanionSnapshot(p.getUUID(), 1.0f);
                if (snap != null) {
                    var item = CosmeticCatalog.byId(a.companion);
                    int c1 = item != null ? item.color() : 0x38BDF8;
                    WardrobeVfxEngine.spawnCosmeticGlitter(
                            snap.x + (rng.nextDouble() - 0.5) * 0.38,
                            snap.y + 0.30 + rng.nextDouble() * 0.25,
                            snap.z + (rng.nextDouble() - 0.5) * 0.38,
                            0.0, 0.015, 0.0,
                            textureFor(a.companion, rng), c1, 0xFFFFFF, 0.11f, 16, true
                    );
                }
            }
        }
    }

    private static String textureFor(String id, ThreadLocalRandom rng) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("sakura") || s.contains("blossom") || s.contains("fuji") || s.contains("torii")) return "sakura_petal";
        if (s.contains("heart") || s.contains("strawberry") || s.contains("ribbon") || s.contains("kawaii") || s.contains("axolotl") || s.contains("bunny")) {
            return rng.nextInt(2) == 0 ? "heart_glow" : "star_spark";
        }
        if (s.contains("frost") || s.contains("glacier") || s.contains("snow") || s.contains("ice")) return "snowflake";
        if (s.contains("phoenix") || s.contains("flame") || s.contains("inferno") || s.contains("blaze") || s.contains("magma") || s.contains("lantern")) {
            return rng.nextInt(2) == 0 ? "flame_wisp" : "glow_soft";
        }
        if (s.contains("butterfly") || s.contains("morpho") || s.contains("fairy")) return "butterfly";
        if (s.contains("seraph") || s.contains("angel") || s.contains("hermes")) return "feather";
        if (s.contains("ocean") || s.contains("tide") || s.contains("bubble") || s.contains("hydro") || s.contains("koi")) return "bubble_orb";
        if (s.contains("cyber") || s.contains("neon") || s.contains("mech") || s.contains("drone")) return "cyber_hex";
        if (s.contains("paw") || s.contains("cat") || s.contains("shiba") || s.contains("corgi") || s.contains("fox") || s.contains("wolf")) {
            return rng.nextInt(3) == 0 ? "pawprint" : "star_spark";
        }
        return rng.nextInt(2) == 0 ? "star_spark" : "glow_soft";
    }
}
