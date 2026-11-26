package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightweight, strictly client-side ambient cosmetic particle trails for local player (obeying
 * granular 1st-person settings) and synced nearby players. Zero packets, zero gameplay impact.
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
        int interval = 3;
        if (tickCounter % interval != 0) return;

        var rng = ThreadLocalRandom.current();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (a.cosmeticParticles && !p.isInvisible() && !p.isSpectator()) {
            spawnForPlayer(mc, p, a, true, firstPerson, interval, rng);
        }

        if (a.showOtherPlayers && a.otherPlayerParticles && (tickCounter % (interval * 3) == 0)) {
            int rendered = 0;
            for (AbstractClientPlayer remote : mc.level.players()) {
                if (remote == null || remote == p || remote.isInvisible() || remote.isSpectator()) continue;
                if (remote.distanceToSqr(p) > 48.0 * 48.0) continue;
                SkyPilotConfig.Appearance ra = RemoteWardrobeSync.getRemote(remote.getUUID());
                if (ra == null || !ra.cosmeticParticles) continue;
                spawnForPlayer(mc, remote, ra, false, false, interval * 3, rng);
                if (++rendered >= 12) break;
            }
        }
    }

    private static void spawnForPlayer(
            Minecraft mc,
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

        // 1. Wings / Backwear & Cloak wake particles
        String backOrCape = !"none".equals(a.back) ? a.back : a.cape;
        boolean allowBack = !firstPerson || a.firstPersonWings || a.firstPersonCape || speed > 0.06;
        if (!"none".equals(backOrCape) && allowBack) {
            boolean moving = speed > 0.07 || !p.onGround();
            if (moving || (tickCounter % (interval * 3) == 0)) {
                ParticleOptions pt = particleFor(backOrCape, rng);
                if (pt != null) {
                    double side = (rng.nextDouble() - 0.5) * 1.25;
                    double distBack = firstPerson ? 0.85 : 0.48;
                    double px = p.getX() + backX * distBack + rightX * side;
                    double py = p.getY() + 0.75 + rng.nextDouble() * 0.95;
                    double pz = p.getZ() + backZ * distBack + rightZ * side;
                    mc.level.addParticle(pt, px, py, pz,
                            (rng.nextDouble() - 0.5) * 0.015,
                            pt == ParticleTypes.CHERRY_LEAVES || pt == ParticleTypes.SNOWFLAKE ? -0.01 : 0.015,
                            (rng.nextDouble() - 0.5) * 0.015);
                }
            }
        }

        // 2. Aura ambient shimmering particles (obeys firstPersonAura in 1st-person!)
        if (!"none".equals(a.aura) && (!firstPerson || a.firstPersonAura) && (tickCounter % (interval * 2) == 0)) {
            ParticleOptions pt = particleFor(a.aura, rng);
            if (pt != null) {
                double ang = (tickCounter * 0.25) + rng.nextDouble() * Math.PI * 2.0;
                double r = 0.84 + rng.nextDouble() * 0.22;
                double px = p.getX() + Math.cos(ang) * r;
                double py = p.getY() + 0.25 + rng.nextDouble() * 1.45;
                double pz = p.getZ() + Math.sin(ang) * r;
                mc.level.addParticle(pt, px, py, pz,
                        -Math.sin(ang) * 0.018, 0.014, Math.cos(ang) * 0.018);
            }
        }

        // 3. Cute Pet & Companion ambient motes (obey separate firstPersonPet & firstPersonCompanion!)
        if (tickCounter % (interval * 4) == 0) {
            if (!"none".equals(a.pet) && (!firstPerson || a.firstPersonPet)) {
                var snap = isLocal ? PetWorldPhysics.petSnapshot(1.0f) : PetWorldPhysics.remotePetSnapshot(p.getUUID(), 1.0f);
                if (snap != null) {
                    ParticleOptions pt = petParticleFor(a.pet, rng);
                    if (pt != null) {
                        mc.level.addParticle(pt,
                                snap.x + (rng.nextDouble() - 0.5) * 0.35,
                                snap.y + 0.15 + rng.nextDouble() * 0.25,
                                snap.z + (rng.nextDouble() - 0.5) * 0.35,
                                0.0, 0.018, 0.0);
                    }
                }
            }
            if (!"none".equals(a.companion) && (!firstPerson || a.firstPersonCompanion) && rng.nextInt(2) == 0) {
                var snap = isLocal ? PetWorldPhysics.companionSnapshot(1.0f) : PetWorldPhysics.remoteCompanionSnapshot(p.getUUID(), 1.0f);
                if (snap != null) {
                    ParticleOptions pt = petParticleFor(a.companion, rng);
                    if (pt != null) {
                        mc.level.addParticle(pt,
                                snap.x + (rng.nextDouble() - 0.5) * 0.45,
                                snap.y + 0.35 + rng.nextDouble() * 0.35,
                                snap.z + (rng.nextDouble() - 0.5) * 0.45,
                                0.0, 0.016, 0.0);
                    }
                }
            }
        }
    }

    private static ParticleOptions particleFor(String id, ThreadLocalRandom rng) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("sakura") || s.contains("blossom") || s.contains("fuji") || s.contains("torii")) {
            return ParticleTypes.CHERRY_LEAVES;
        }
        if (s.contains("strawberry") || s.contains("ribbon") || s.contains("heart") || s.contains("candy") || s.contains("macaron")) {
            return rng.nextInt(3) == 0 ? ParticleTypes.HEART : ParticleTypes.CHERRY_LEAVES;
        }
        if (s.contains("phoenix") || s.contains("inferno") || s.contains("blaze")
                || s.contains("ember") || s.contains("demon") || s.contains("magma") || s.contains("solar") || s.contains("lantern")) {
            return rng.nextInt(3) == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
        }
        if (s.contains("frost") || s.contains("glacier") || s.contains("snow") || s.contains("fenrir")) {
            return ParticleTypes.SNOWFLAKE;
        }
        if (s.contains("tide") || s.contains("hydro") || s.contains("bubble") || s.contains("ocean") || s.contains("koi") || s.contains("jelly")) {
            return rng.nextInt(2) == 0 ? ParticleTypes.SPLASH : ParticleTypes.GLOW;
        }
        if (s.contains("void") || s.contains("shadow") || s.contains("abyss") || s.contains("wisp") || s.contains("reaper") || s.contains("raven")) {
            return rng.nextInt(2) == 0 ? ParticleTypes.PORTAL : ParticleTypes.WITCH;
        }
        if (s.contains("cyber") || s.contains("emerald") || s.contains("thunder") || s.contains("neon") || s.contains("volt") || s.contains("lightning")) {
            return ParticleTypes.ELECTRIC_SPARK;
        }
        return rng.nextInt(2) == 0 ? ParticleTypes.END_ROD : ParticleTypes.GLOW;
    }

    private static ParticleOptions petParticleFor(String id, ThreadLocalRandom rng) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("axolotl") || s.contains("bunny") || s.contains("cat") || s.contains("kitten")
                || s.contains("shiba") || s.contains("corgi") || s.contains("samoyed") || s.contains("pom")
                || s.contains("red_panda") || s.contains("lamb") || s.contains("sheep") || s.contains("chick")
                || s.contains("duck") || s.contains("penguin") || s.contains("alpaca") || s.contains("fawn")
                || s.contains("fennec") || s.contains("strawberry") || s.contains("teacup") || s.contains("capybara")
                || s.contains("retriever") || s.contains("terrier") || s.contains("collie")) {
            return rng.nextInt(3) == 0 ? ParticleTypes.HEART : ParticleTypes.END_ROD;
        }
        if (s.contains("frost") || s.contains("ice") || s.contains("polar") || s.contains("arctic") || s.contains("fenrir") || s.contains("husky")) {
            return ParticleTypes.SNOWFLAKE;
        }
        if (s.contains("magma") || s.contains("imp") || s.contains("cerberus") || s.contains("crimson") || s.contains("phoenix") || s.contains("sun")) {
            return ParticleTypes.SMALL_FLAME;
        }
        if (s.contains("shadow") || s.contains("void") || s.contains("ender") || s.contains("reaper") || s.contains("cobra") || s.contains("cosmic")) {
            return ParticleTypes.WITCH;
        }
        if (s.contains("cyber") || s.contains("ufo") || s.contains("mech") || s.contains("drone")) {
            return ParticleTypes.ELECTRIC_SPARK;
        }
        return ParticleTypes.GLOW;
    }
}

