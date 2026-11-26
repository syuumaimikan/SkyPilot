package dev.skypilot.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeVfxCatalog;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commercial AAA 3D procedural emissive VFX engine for standalone {@code skypilot_wardrobe}.
 * Renders multi-layered HDR-core slashes, counter-rotating filigree magic circles, 3D shockwave
 * domes, continuous connected energy ribbons, pet signature attack beams/projectiles, and delicate
 * cosmetic micro-glitter using 256×256 custom textures &amp; sculpted 3D meshes (zero vanilla smoke!).
 */
final class WardrobeVfxEngine {
    private static final int MAX_LIVE = 760;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final class EffectInstance {
        WardrobeVfxMeshes.Kind kind;
        Identifier texture;
        boolean billboard;
        boolean rainbow;
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        double gravity;
        double drag = 0.94;
        float yaw, pitch, roll;
        float vYaw, vPitch, vRoll;
        float scaleStart, scaleEnd;
        float scaleXMult = 1.0f, scaleYMult = 1.0f, scaleZMult = 1.0f;
        int colorStart, colorEnd;
        float baseAlpha = 0.94f;
        int age, maxAge;
    }

    private static final class PlayerMotion {
        boolean init;
        double x, y, z;
        double lastRibbonX, lastRibbonY, lastRibbonZ;
        boolean hasRibbon;
        boolean wasOnGround = true;
        boolean wasSprinting;
        double distAccum;
        int ambientCooldown;
        int lastTick;
    }

    private static final class ProjectileTrail {
        double x, y, z;
        int lastTick;
    }

    private static final class TrackedVictim {
        final LivingEntity entity;
        double x, y, z;
        float height;
        long atMs;

        TrackedVictim(LivingEntity e, long nowMs) {
            this.entity = e;
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
            this.height = e.getBbHeight();
            this.atMs = nowMs;
        }
    }

    private static final ArrayList<EffectInstance> LIVE = new ArrayList<>(384);
    private static final Map<UUID, PlayerMotion> MOTION = new HashMap<>();
    private static final Map<Integer, ProjectileTrail> PROJ_TRAILS = new HashMap<>();
    private static final Map<Integer, TrackedVictim> VICTIMS = new LinkedHashMap<>();
    private static int slashToggle = 1;
    private static int lastAttackTick = -100;
    private static int tickCount;

    private WardrobeVfxEngine() {}

    static void reset() {
        LIVE.clear();
        MOTION.clear();
        PROJ_TRAILS.clear();
        VICTIMS.clear();
    }

    static int activeEffectCount() {
        return LIVE.size();
    }

    // ---------------------------------------------------------------- public triggers & pet/cosmetic VFX hooks

    static void onPlayerAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (p == null || mc.level == null || rt == null) return;
        if (!(target instanceof LivingEntity le) || le == p || le instanceof ArmorStand || !le.isAlive()) return;
        if (le.isInvisibleTo(p) || le.distanceToSqr(p) > 20.0 * 20.0) return;

        lastAttackTick = p.tickCount;
        SkyPilotConfig.VfxSettings vfx = rt.appearance().vfx;
        if (vfx == null) return;

        if (!"none".equals(vfx.kill)) {
            if (VICTIMS.size() >= 64) VICTIMS.remove(VICTIMS.keySet().iterator().next());
            VICTIMS.put(le.getId(), new TrackedVictim(le, System.currentTimeMillis()));
        }

        if ("none".equals(vfx.attack)) return;
        if (mc.options.getCameraType().isFirstPerson() && !vfx.firstPersonVfx) return;

        WardrobeVfxCatalog.VfxStyle style = WardrobeVfxCatalog.byId(vfx.attack);
        if (style == null) return;

        Vec3 hitPos = new Vec3(le.getX(), le.getY() + Math.max(0.55, le.getBbHeight() * 0.56), le.getZ());
        spawnAttackBurst(hitPos, p.getYRot(), p.getXRot(), style, vfx);
    }

    /** Plays a live demonstration of any event slot & style directly in front of the player! */
    static void triggerPreview(String eventSlot, String styleId) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (p == null || rt == null || styleId == null || "none".equals(styleId)) return;
        WardrobeVfxCatalog.VfxStyle style = WardrobeVfxCatalog.byId(styleId);
        if (style == null) return;
        SkyPilotConfig.VfxSettings vfx = rt.appearance().vfx;
        double rad = Math.toRadians(p.getYRot());
        Vec3 ahead = p.position().add(-Math.sin(rad) * 2.4, 1.15, Math.cos(rad) * 2.4);
        switch (eventSlot == null ? "attack" : eventSlot) {
            case "attack" -> spawnAttackBurst(ahead, p.getYRot(), 0.0f, style, vfx);
            case "kill" -> spawnKillFinisher(ahead.add(0, -1.0, 0), 1.8f, style, vfx);
            case "move" -> {
                Vec3 from = p.position().add(Math.sin(rad) * 0.8, 0.3, -Math.cos(rad) * 0.8);
                spawnConnectedRibbon(from, ahead.add(0, -0.5, 0), style, vfx, 0.65f, 14);
                spawnMoveStep(ahead.add(0, -1.0, 0), new Vec3(-Math.sin(rad) * 0.3, 0, Math.cos(rad) * 0.3), style, vfx, true);
            }
            case "jump" -> spawnJumpLandRing(p.position(), true, style, vfx);
            case "dash" -> spawnDashWarpBurst(p.position(), ahead.add(0, -0.9, 0), p.getYRot(), style, vfx);
            case "projectile" -> {
                Vec3 to = ahead.add(-Math.sin(rad) * 2.5, 0.25, Math.cos(rad) * 2.5);
                spawnConnectedRibbon(ahead, to, style, vfx, 0.55f, 14);
                spawnProjectileSpark(to, style, vfx);
            }
            case "ambient" -> spawnAmbientMotes(p.position(), style, vfx, false, 2);
        }
    }

    /** Delicate custom 3D cosmetic glitter replacing cheap vanilla smoke particles! */
    static void spawnCosmeticGlitter(double x, double y, double z, double vx, double vy, double vz,
                                     String texName, int color1, int color2, float size, int maxAge, boolean spin) {
        EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, tex(texName), true, false);
        if (e == null) return;
        e.x = e.prevX = x;
        e.y = e.prevY = y;
        e.z = e.prevZ = z;
        e.vx = vx; e.vy = vy; e.vz = vz;
        e.drag = 0.96;
        e.roll = ThreadLocalRandom.current().nextFloat() * 360.0f;
        e.vRoll = spin ? (ThreadLocalRandom.current().nextFloat() - 0.5f) * 14.0f : 0.0f;
        e.scaleStart = size;
        e.scaleEnd = size * 0.12f;
        e.colorStart = color1;
        e.colorEnd = color2;
        e.baseAlpha = 0.82f;
        e.maxAge = maxAge;
    }

    /** Connects two 3D world points with a razor-bright laser / dragon-breath energy beam! */
    static void spawnEnergyBeam(Vec3 from, Vec3 to, int coreColor, int outerColor, float width, int maxAge) {
        Vec3 diff = to.subtract(from);
        double len = diff.length();
        if (len < 0.08 || len > 32.0) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float horiz = (float) Math.hypot(diff.x, diff.z);
        float pitch = (float) Math.toDegrees(Math.atan2(-diff.y, Math.max(1e-4, horiz)));

        // Outer glowing plasma sheath
        EffectInstance outer = spawn(WardrobeVfxMeshes.Kind.BEAM_SHAFT, tex("pet_beam"), false, false);
        if (outer != null) {
            outer.x = outer.prevX = from.x; outer.y = outer.prevY = from.y; outer.z = outer.prevZ = from.z;
            outer.yaw = yaw; outer.pitch = pitch;
            outer.scaleStart = width; outer.scaleEnd = width * 0.18f;
            outer.scaleZMult = (float) (len / Math.max(0.05, width));
            outer.colorStart = outerColor; outer.colorEnd = coreColor;
            outer.baseAlpha = 0.85f;
            outer.maxAge = maxAge;
        }
        // White-hot incandescent inner beam core
        EffectInstance inner = spawn(WardrobeVfxMeshes.Kind.BEAM_SHAFT, tex("pet_beam"), false, false);
        if (inner != null) {
            inner.x = inner.prevX = from.x; inner.y = inner.prevY = from.y; inner.z = inner.prevZ = from.z;
            inner.yaw = yaw; inner.pitch = pitch;
            inner.scaleStart = width * 0.45f; inner.scaleEnd = width * 0.08f;
            inner.scaleZMult = (float) (len / Math.max(0.05, width * 0.45));
            inner.colorStart = 0xFFFFFF; inner.colorEnd = coreColor;
            inner.baseAlpha = 0.98f;
            inner.maxAge = Math.max(3, maxAge - 1);
        }
    }

    /** Spawns signature 3D VFX for each of the 8 unique Pet & Companion attack styles! */
    static void spawnPetSignatureVfx(PetWorldPhysics.AttackStyle style, Vec3 origin, Vec3 hitPos, float yaw, int c1, int c2) {
        var rng = ThreadLocalRandom.current();
        switch (style) {
            case BREATH_FIREBALL -> {
                spawnEnergyBeam(origin, hitPos, 0xFFF7C2, c1, 0.34f, 8);
                spawnFlashStar(hitPos, 0xFFF7C2, c1, 0.95f, 9);
                spawnRing(hitPos, 0.25f, 1.45f, 0, yaw, 75, c1, c2, tex("shockwave_ring"), 10, 0.85f);
                spawnMoteBurst(hitPos, tex("flame_wisp"), c1, c2, 10, 0.14, 0.26f, 13);
            }
            case CLAW_POUNCE -> {
                // Twin X-shaped curved claw slash arcs!
                for (int s : new int[]{-1, 1}) {
                    spawnSlashPair(hitPos, yaw + s * 18f, 0f, s * 36f, s * -12f, 0.45f, 1.05f, c1, c2, false, 8);
                }
                spawnFlashStar(hitPos, 0xFFFFFF, c1, 0.8f, 7);
                spawnMoteBurst(hitPos, tex("star_spark"), c1, c2, 7, 0.12, 0.18f, 11);
            }
            case PHANTOM_BLINK -> {
                // Void arrival portal ring + dark crescent scythe cut!
                spawnRing(origin, 0.25f, 1.35f, 14f, yaw, 90f, c1, c2, tex("magic_circle"), 12, 0.85f);
                spawnSlashPair(hitPos, yaw, 0f, -22f, 15f, 0.55f, 1.25f, c1, c2, false, 9);
                spawnMoteBurst(hitPos, tex("crescent_moon"), c1, c2, 7, 0.11, 0.22f, 13);
            }
            case ARCANE_BARRAGE -> {
                // Starlight energy bolt streak + multi-sparkle detonation!
                spawnEnergyBeam(origin, hitPos, 0xFFFFFF, c1, 0.18f, 6);
                spawnFlashStar(hitPos, 0xFFFFFF, c2, 0.9f, 10);
                spawnRing(hitPos, 0.2f, 1.25f, 12f, yaw, 0f, c1, c2, tex("magic_circle"), 12, 0.78f);
                spawnMoteBurst(hitPos, tex("star_spark"), c1, c2, 9, 0.12, 0.22f, 14);
            }
            case ORBITAL_LASER -> {
                // Rotating cyan holo reticle + crisp twin laser strike!
                spawnRing(hitPos, 0.95f, 0.32f, 18f, yaw, 90f, c1, c2, tex("cyber_hex"), 11, 0.9f);
                spawnEnergyBeam(origin, hitPos, 0xE0FFFF, c1, 0.24f, 8);
                spawnFlashStar(hitPos, 0xFFFFFF, c1, 0.85f, 7);
                spawnMoteBurst(hitPos, tex("cyber_hex"), c1, c2, 6, 0.11, 0.18f, 10);
            }
            case HYDRO_SURGE -> {
                // Swirling aqua vortex spiral + pressurized bubble splash!
                spawnEnergyBeam(origin, hitPos, 0xE0FFFF, c1, 0.28f, 7);
                spawnRing(hitPos, 0.3f, 1.65f, 14f, yaw, 0f, c1, c2, tex("vortex_spiral"), 13, 0.85f);
                spawnMoteBurst(hitPos, tex("bubble_orb"), c1, c2, 10, 0.13, 0.22f, 15);
            }
            case METEOR_SLAM -> {
                // Bouncy ground shockwave dome + radial hearts/stars!
                Vec3 ground = new Vec3(hitPos.x, hitPos.y - 0.35, hitPos.z);
                spawnDomeWave(ground, 0.35f, 1.55f, c1, c2, 11);
                spawnRing(ground.add(0, 0.06, 0), 0.3f, 1.85f, 8f, 0f, 0f, c1, c2, tex("shockwave_ring"), 13, 0.85f);
                spawnMoteBurst(hitPos, rng.nextBoolean() ? tex("heart_glow") : tex("star_spark"), c1, c2, 9, 0.14, 0.24f, 14);
            }
            case IAI_DASH_SLASH -> {
                // Blinding horizontal iaijutsu flash line across the target!
                spawnEnergyBeam(origin, hitPos, 0xFFFFFF, c1, 0.20f, 7);
                spawnSlashPair(hitPos, yaw, 0f, 4f, -18f, 0.65f, 1.45f, 0xFFFFFF, c1, false, 8);
                spawnFlashStar(hitPos, 0xFFFFFF, c2, 1.15f, 8);
                spawnMoteBurst(hitPos, tex("star_spark"), 0xFFFFFF, c1, 8, 0.15, 0.20f, 11);
            }
        }
    }

    // ---------------------------------------------------------------- tick loop

    static void tick(Minecraft mc) {
        LocalPlayer local = mc.player;
        ClientLevel level = mc.level;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (local == null || level == null || rt == null) {
            reset();
            return;
        }
        tickCount++;
        advanceInstances();

        SkyPilotConfig.Appearance localApp = rt.appearance();
        SkyPilotConfig.VfxSettings vfx = localApp.vfx;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (vfx != null && (!"none".equals(vfx.attack) || !"none".equals(vfx.kill))
                && local.swinging && local.swingTime == 1 && (local.tickCount - lastAttackTick) >= 2) {
            Entity aimed = findAimedVictim(mc, local, level);
            if (aimed != null) onPlayerAttack(aimed);
        }

        if (vfx != null && !"none".equals(vfx.kill) && !VICTIMS.isEmpty()) {
            long now = System.currentTimeMillis();
            WardrobeVfxCatalog.VfxStyle killStyle = WardrobeVfxCatalog.byId(vfx.kill);
            var it = VICTIMS.entrySet().iterator();
            while (it.hasNext()) {
                TrackedVictim tv = it.next().getValue();
                if (now - tv.atMs > 8000 || tv.entity.level() != level) {
                    it.remove();
                    continue;
                }
                if (tv.entity.isAlive() && !tv.entity.isRemoved()) {
                    tv.x = tv.entity.getX(); tv.y = tv.entity.getY(); tv.z = tv.entity.getZ();
                }
                if (tv.entity.isDeadOrDying() || (tv.entity.isRemoved() && tv.entity.getHealth() <= 0.5f)) {
                    if (killStyle != null && (!firstPerson || vfx.firstPersonVfx)) {
                        spawnKillFinisher(new Vec3(tv.x, tv.y, tv.z), tv.height, killStyle, vfx);
                    }
                    it.remove();
                }
            }
        }

        if (vfx != null && !local.isInvisible() && !local.isSpectator()) {
            boolean allowLocal = !firstPerson || vfx.firstPersonVfx;
            tickPlayerMotionVfx(local, vfx, allowLocal, firstPerson);
            if (allowLocal && !"none".equals(vfx.projectile)) {
                tickProjectileVfx(local, level, vfx);
            }
        }

        if (localApp.showOtherPlayers && vfx != null && vfx.otherPlayerVfx) {
            int remoteCount = 0;
            for (AbstractClientPlayer remote : level.players()) {
                if (remote == null || remote == local || remote.isInvisible() || remote.isSpectator()) continue;
                if (remote.distanceToSqr(local) > 56.0 * 56.0) continue;
                SkyPilotConfig.Appearance ra = RemoteWardrobeSync.getRemote(remote.getUUID());
                if (ra == null || ra.vfx == null) continue;
                tickPlayerMotionVfx(remote, ra.vfx, true, false);
                if (++remoteCount >= 10) break;
            }
        }

        if (tickCount % 60 == 0) {
            MOTION.entrySet().removeIf(e -> tickCount - e.getValue().lastTick > 100);
            PROJ_TRAILS.entrySet().removeIf(e -> tickCount - e.getValue().lastTick > 40);
        }
    }

    private static void tickPlayerMotionVfx(
            AbstractClientPlayer p, SkyPilotConfig.VfxSettings vfx, boolean allowRender, boolean firstPerson
    ) {
        PlayerMotion m = MOTION.computeIfAbsent(p.getUUID(), k -> new PlayerMotion());
        m.lastTick = tickCount;
        double px = p.getX(), py = p.getY(), pz = p.getZ();
        if (!m.init) {
            m.x = px; m.y = py; m.z = pz;
            m.lastRibbonX = px; m.lastRibbonY = py + 0.35; m.lastRibbonZ = pz;
            m.hasRibbon = true;
            m.wasOnGround = p.onGround();
            m.wasSprinting = p.isSprinting();
            m.init = true;
            return;
        }

        double dx = px - m.x, dy = py - m.y, dz = pz - m.z;
        double horiz = Math.hypot(dx, dz);
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (allowRender) {
            // 1. Warp / Teleport OR Sprint Dash Gate
            if (!"none".equals(vfx.dash)) {
                WardrobeVfxCatalog.VfxStyle dashStyle = WardrobeVfxCatalog.byId(vfx.dash);
                if (dashStyle != null) {
                    if (totalDist > 3.6 && totalDist < 72.0) {
                        spawnDashWarpBurst(new Vec3(m.x, m.y, m.z), new Vec3(px, py, pz), p.getYRot(), dashStyle, vfx);
                        m.hasRibbon = false;
                    } else if (p.isSprinting() && !m.wasSprinting && horiz > 0.08) {
                        spawnSprintStartGate(new Vec3(px, py + 0.95, pz), p.getYRot(), dashStyle, vfx);
                    }
                }
            }

            // 2. Jump Takeoff & Landing Volumetric Shockwave
            if (!"none".equals(vfx.jump) && totalDist < 3.6) {
                WardrobeVfxCatalog.VfxStyle jumpStyle = WardrobeVfxCatalog.byId(vfx.jump);
                if (jumpStyle != null) {
                    if (m.wasOnGround && !p.onGround() && (dy > 0.12 || p.getDeltaMovement().y > 0.15)) {
                        spawnJumpLandRing(new Vec3(px, py + 0.05, pz), false, jumpStyle, vfx);
                    } else if (!m.wasOnGround && p.onGround()) {
                        spawnJumpLandRing(new Vec3(px, py + 0.05, pz), true, jumpStyle, vfx);
                    }
                }
            }

            // 3. Seamless Connected 3D Energy Ribbon + Delicate Theme Motes while Moving!
            if (!"none".equals(vfx.move) && horiz > 0.05 && totalDist < 3.6) {
                WardrobeVfxCatalog.VfxStyle moveStyle = WardrobeVfxCatalog.byId(vfx.move);
                if (moveStyle != null) {
                    double curRY = py + (p.isSprinting() || !p.onGround() ? 0.36 : 0.16);
                    if (m.hasRibbon) {
                        double rDist = Math.sqrt(Math.pow(px - m.lastRibbonX, 2) + Math.pow(curRY - m.lastRibbonY, 2) + Math.pow(pz - m.lastRibbonZ, 2));
                        if (rDist >= 0.24 && rDist < 3.0) {
                            // Never obstruct 1st-person center — keep ribbon low at feet level!
                            double yAdj = firstPerson ? py + 0.12 : curRY;
                            spawnConnectedRibbon(new Vec3(m.lastRibbonX, firstPerson ? m.y + 0.12 : m.lastRibbonY, m.lastRibbonZ),
                                    new Vec3(px, yAdj, pz), moveStyle, vfx,
                                    (p.isSprinting() ? 0.42f : 0.28f) * (float) vfx.scale, p.isSprinting() ? 12 : 9);
                            m.lastRibbonX = px; m.lastRibbonY = curRY; m.lastRibbonZ = pz;
                        }
                    } else {
                        m.lastRibbonX = px; m.lastRibbonY = curRY; m.lastRibbonZ = pz; m.hasRibbon = true;
                    }

                    m.distAccum += horiz;
                    double stepThresh = vfx.intensity >= 3 ? 0.42 : (vfx.intensity == 1 ? 0.82 : 0.58);
                    if (m.distAccum >= stepThresh) {
                        m.distAccum = 0.0;
                        spawnMoveStep(new Vec3(px, py, pz), new Vec3(dx, dy, dz), moveStyle, vfx, p.isSprinting() || !p.onGround());
                    }
                }
            } else {
                m.lastRibbonX = px; m.lastRibbonY = py + 0.25; m.lastRibbonZ = pz; m.hasRibbon = true;
            }

            // 4. Delicate Ambient Shimmering Motes
            if (!"none".equals(vfx.ambient)) {
                if (--m.ambientCooldown <= 0) {
                    m.ambientCooldown = vfx.intensity >= 3 ? 5 : (vfx.intensity == 1 ? 14 : 9);
                    WardrobeVfxCatalog.VfxStyle ambStyle = WardrobeVfxCatalog.byId(vfx.ambient);
                    if (ambStyle != null) {
                        spawnAmbientMotes(new Vec3(px, py, pz), ambStyle, vfx, firstPerson, 1);
                    }
                }
            }
        }

        m.x = px; m.y = py; m.z = pz;
        m.wasOnGround = p.onGround();
        m.wasSprinting = p.isSprinting();
    }

    private static void tickProjectileVfx(LocalPlayer local, ClientLevel level, SkyPilotConfig.VfxSettings vfx) {
        WardrobeVfxCatalog.VfxStyle style = WardrobeVfxCatalog.byId(vfx.projectile);
        if (style == null) return;
        AABB box = local.getBoundingBox().inflate(54.0);
        List<Projectile> projs = level.getEntitiesOfClass(Projectile.class, box,
                pr -> pr.isAlive() && !pr.onGround() && pr.getDeltaMovement().lengthSqr() > 0.03);
        int count = 0;
        for (Projectile pr : projs) {
            Entity owner = pr.getOwner();
            if (owner != null && owner != local) continue;
            Vec3 cur = pr.position();
            ProjectileTrail tr = PROJ_TRAILS.computeIfAbsent(pr.getId(), k -> {
                ProjectileTrail t = new ProjectileTrail();
                t.x = cur.x; t.y = cur.y; t.z = cur.z;
                return t;
            });
            tr.lastTick = tickCount;
            Vec3 prev = new Vec3(tr.x, tr.y, tr.z);
            if (cur.distanceToSqr(prev) > 0.06 && cur.distanceToSqr(prev) < 36.0) {
                spawnConnectedRibbon(prev, cur, style, vfx, 0.34f * (float) vfx.scale, 11);
                if (tickCount % 2 == 0) spawnProjectileSpark(cur, style, vfx);
            }
            tr.x = cur.x; tr.y = cur.y; tr.z = cur.z;
            if (++count >= 6) break;
        }
    }

    // ---------------------------------------------------------------- AAA multi-layer choreographies

    private static void spawnAttackBurst(Vec3 pos, float yaw, float pitch, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());
        slashToggle = -slashToggle;

        // 1. Layered Anime Katana Crescent Slash (outer colored plasma + incandescent white razor core!)
        float roll = slashToggle * (22.0f + rng.nextFloat() * 16.0f);
        spawnSlashPair(pos, yaw + slashToggle * 30f, Mth.clamp(pitch * 0.35f, -22f, 22f),
                roll, -slashToggle * 15f, 0.58f * sc, 1.38f * sc, c1, c2, rb, 8);

        if (vfx.intensity >= 2) {
            spawnSlashPair(pos.add(0, 0.06, 0), yaw - slashToggle * 22f, 0f,
                    -roll * 0.72f, slashToggle * 12f, 0.44f * sc, 1.08f * sc, c2, c1, rb, 9);
        }

        // 2. Razor-crisp Impact Star-Cross Flash + Soft HDR Bloom Core
        spawnFlashStar(pos, 0xFFFFFF, c1, 0.95f * sc, 7);

        // 3. Tilted Delicate Impact Filigree / Shockwave Ring
        spawnRing(pos, 0.22f * sc, 1.55f * sc, 11f * slashToggle, yaw, 78f, c2, c1,
                CustomVfxLoader.resolveTexture(style.ringTex()), 10, 0.82f);

        // 4. Lightning Strike Bolt for Thunder
        if ("thunder".equals(style.id())) {
            EffectInstance bolt = spawn(WardrobeVfxMeshes.Kind.BOLT_ZIGZAG, tex("lightning_bolt"), false, false);
            if (bolt != null) {
                bolt.x = bolt.prevX = pos.x; bolt.y = bolt.prevY = pos.y - 0.5; bolt.z = bolt.prevZ = pos.z;
                bolt.yaw = rng.nextFloat() * 360.0f;
                bolt.scaleStart = 0.78f * sc; bolt.scaleEnd = 1.05f * sc;
                bolt.colorStart = 0xFFFFFF; bolt.colorEnd = c1;
                bolt.maxAge = 7;
            }
        }

        // 5. Delicate Directional Micro-Sparks + Hero Theme Motifs
        int n = 4 + vfx.intensity * 3;
        Identifier moteTex = CustomVfxLoader.resolveTexture(style.primaryTex());
        boolean crystal = "frost".equals(style.id()) || "void".equals(style.id()) || "prism".equals(style.id());
        for (int i = 0; i < n; i++) {
            boolean isGlitter = (i % 3 == 2);
            boolean isShard = !isGlitter && crystal && i % 2 == 0;
            EffectInstance e = spawn(isShard ? WardrobeVfxMeshes.Kind.SHARD_3D : WardrobeVfxMeshes.Kind.QUAD_BILLBOARD,
                    isGlitter ? tex("star_spark") : moteTex, !isShard, rb);
            if (e == null) break;
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double spd = 0.09 + rng.nextDouble() * 0.16;
            e.x = e.prevX = pos.x + (rng.nextDouble() - 0.5) * 0.22;
            e.y = e.prevY = pos.y + (rng.nextDouble() - 0.5) * 0.22;
            e.z = e.prevZ = pos.z + (rng.nextDouble() - 0.5) * 0.22;
            e.vx = Math.cos(ang) * spd;
            e.vy = 0.04 + rng.nextDouble() * 0.12;
            e.vz = Math.sin(ang) * spd;
            e.gravity = "sakura".equals(style.id()) || "seraph".equals(style.id()) ? -0.003 : -0.007;
            e.drag = 0.89;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 22.0f;
            e.scaleStart = (isGlitter ? 0.16f : 0.22f + rng.nextFloat() * 0.14f) * sc;
            e.scaleEnd = 0.03f * sc;
            e.colorStart = isGlitter ? 0xFFFFFF : (i % 2 == 0 ? c1 : c2);
            e.colorEnd = i % 2 == 0 ? c2 : c1;
            e.maxAge = 11 + rng.nextInt(9);
        }
    }

    private static void spawnKillFinisher(Vec3 groundPos, float targetHeight, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());

        // 1. Dual Counter-Rotating Filigree Ground Magic Circles!
        spawnRing(groundPos.add(0, 0.06, 0), 0.35f * sc, 2.8f * sc, 8.0f, 0f, 0f, c1, c2,
                CustomVfxLoader.resolveTexture(style.ringTex()), 24, 0.90f);
        spawnRing(groundPos.add(0, 0.09, 0), 0.55f * sc, 3.3f * sc, -6.5f, 0f, 0f, c2, c1,
                tex("magic_sigil_outer"), 26, 0.72f);

        // 2. Volumetric 3D Expanding Shockwave Dome + Swirling Spiral Vortex!
        spawnDomeWave(groundPos.add(0, 0.08, 0), 0.4f * sc, 2.2f * sc, c1, c2, 14);
        spawnRing(groundPos.add(0, 0.25, 0), 0.35f * sc, 2.3f * sc, 16f, 0f, 0f, c2, c1,
                tex("vortex_spiral"), 18, 0.75f);

        // 3. Sky-Piercing Layered Light Pillar (colored outer beam + white-hot inner core) OR Thunder Strike
        boolean boltKill = "thunder".equals(style.id());
        EffectInstance pillar = spawn(
                boltKill ? WardrobeVfxMeshes.Kind.BOLT_ZIGZAG : WardrobeVfxMeshes.Kind.PILLAR_BEAM,
                tex(boltKill ? "lightning_bolt" : "pillar_beam"), false, rb);
        if (pillar != null) {
            pillar.x = pillar.prevX = groundPos.x; pillar.y = pillar.prevY = groundPos.y + 0.05; pillar.z = pillar.prevZ = groundPos.z;
            pillar.vYaw = boltKill ? 16.0f : 6.0f;
            pillar.scaleStart = 1.05f * sc; pillar.scaleEnd = 0.16f * sc;
            pillar.scaleYMult = 1.85f;
            pillar.colorStart = c1; pillar.colorEnd = c2;
            pillar.maxAge = boltKill ? 13 : 20;
        }
        if (!boltKill) {
            EffectInstance core = spawn(WardrobeVfxMeshes.Kind.PILLAR_BEAM, tex("pillar_beam"), false, false);
            if (core != null) {
                core.x = core.prevX = groundPos.x; core.y = core.prevY = groundPos.y + 0.05; core.z = core.prevZ = groundPos.z;
                core.vYaw = -8.0f;
                core.scaleStart = 0.42f * sc; core.scaleEnd = 0.06f * sc;
                core.scaleYMult = 2.05f;
                core.colorStart = 0xFFFFFF; core.colorEnd = c2;
                core.baseAlpha = 0.98f;
                core.maxAge = 16;
            }
        }

        // 4. Fountain Explosion of Delicate Theme Motifs + Starfield Glitter
        int n = 10 + vfx.intensity * 5;
        Identifier moteTex = CustomVfxLoader.resolveTexture(style.primaryTex());
        for (int i = 0; i < n; i++) {
            boolean glitter = (i % 3 == 0);
            EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, glitter ? tex("star_spark") : moteTex, true, rb);
            if (e == null) break;
            double ang = (i * Math.PI * 2.0 / n) + rng.nextDouble() * 0.35;
            double spd = 0.10 + rng.nextDouble() * 0.16;
            e.x = e.prevX = groundPos.x;
            e.y = e.prevY = groundPos.y + targetHeight * 0.45;
            e.z = e.prevZ = groundPos.z;
            e.vx = Math.cos(ang) * spd;
            e.vy = 0.13 + rng.nextDouble() * 0.20;
            e.vz = Math.sin(ang) * spd;
            e.gravity = -0.009;
            e.drag = 0.92;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 24.0f;
            e.scaleStart = (glitter ? 0.18f : 0.28f + rng.nextFloat() * 0.16f) * sc;
            e.scaleEnd = 0.04f * sc;
            e.colorStart = glitter ? 0xFFFFFF : (i % 2 == 0 ? c1 : c2);
            e.colorEnd = i % 2 == 0 ? c2 : c1;
            e.maxAge = 18 + rng.nextInt(12);
        }
    }

    private static void spawnConnectedRibbon(Vec3 from, Vec3 to, WardrobeVfxCatalog.VfxStyle style,
                                             SkyPilotConfig.VfxSettings vfx, float width, int maxAge) {
        Vec3 diff = to.subtract(from);
        double len = diff.length();
        if (len < 0.04 || len > 8.0) return;
        Vec3 mid = from.add(to).scale(0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float horiz = (float) Math.hypot(diff.x, diff.z);
        float pitch = (float) Math.toDegrees(Math.atan2(-diff.y, Math.max(1e-4, horiz)));
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());

        EffectInstance rib = spawn(WardrobeVfxMeshes.Kind.RIBBON_SEG, tex("ribbon_trail"), false, rb);
        if (rib != null) {
            rib.x = rib.prevX = mid.x; rib.y = rib.prevY = mid.y; rib.z = rib.prevZ = mid.z;
            rib.yaw = yaw; rib.pitch = pitch;
            rib.scaleStart = width; rib.scaleEnd = width * 0.08f;
            rib.scaleZMult = (float) ((len * 1.18) / Math.max(0.05, width));
            rib.colorStart = c1; rib.colorEnd = c2;
            rib.baseAlpha = 0.72f;
            rib.maxAge = maxAge;
        }
        // Subtle hot core thread inside the ribbon!
        EffectInstance core = spawn(WardrobeVfxMeshes.Kind.RIBBON_SEG, tex("ribbon_trail"), false, false);
        if (core != null) {
            core.x = core.prevX = mid.x; core.y = core.prevY = mid.y; core.z = core.prevZ = mid.z;
            core.yaw = yaw; core.pitch = pitch;
            core.scaleStart = width * 0.36f; core.scaleEnd = width * 0.04f;
            core.scaleZMult = (float) ((len * 1.18) / Math.max(0.05, width * 0.36));
            core.colorStart = 0xFFFFFF; core.colorEnd = c2;
            core.baseAlpha = 0.85f;
            core.maxAge = Math.max(4, maxAge - 3);
        }
    }

    private static void spawnMoveStep(Vec3 pos, Vec3 delta, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx, boolean fast) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());
        float travelYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));

        // Ground pawprints / delicate ripples for Paw, Ocean, Cyber, Frost!
        if ("paw".equals(style.id()) || "ocean".equals(style.id()) || "cyber".equals(style.id()) || "frost".equals(style.id())) {
            boolean isPaw = "paw".equals(style.id());
            double side = (tickCount % 2 == 0 ? 1.0 : -1.0) * 0.22;
            double rad = Math.toRadians(travelYaw);
            Vec3 fp = new Vec3(pos.x + Math.cos(rad) * side, pos.y + 0.04, pos.z + Math.sin(rad) * side);
            spawnRing(fp, (isPaw ? 0.30f : 0.20f) * sc, (isPaw ? 0.34f : 0.72f) * sc,
                    0f, travelYaw, 0f, c1, c2, isPaw ? tex("pawprint") : CustomVfxLoader.resolveTexture(style.ringTex()), 16, 0.72f);
        }

        // Delicate drifting theme motifs + tiny shimmering star glitter (small & tasteful, never bulky!)
        int count = Math.max(1, vfx.intensity);
        Identifier moteTex = CustomVfxLoader.resolveTexture(style.primaryTex());
        for (int i = 0; i < count; i++) {
            boolean star = (i == count - 1 && count >= 2);
            EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, star ? tex("star_spark") : moteTex, true, rb);
            if (e == null) break;
            e.x = e.prevX = pos.x + (rng.nextDouble() - 0.5) * 0.55;
            e.y = e.prevY = pos.y + 0.14 + rng.nextDouble() * 0.45;
            e.z = e.prevZ = pos.z + (rng.nextDouble() - 0.5) * 0.55;
            e.vx = -delta.x * 0.14 + (rng.nextDouble() - 0.5) * 0.035;
            e.vy = 0.018 + rng.nextDouble() * 0.038;
            e.vz = -delta.z * 0.14 + (rng.nextDouble() - 0.5) * 0.035;
            e.gravity = -0.0012;
            e.drag = 0.94;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 14.0f;
            e.scaleStart = (star ? 0.14f : 0.18f + rng.nextFloat() * 0.11f) * sc;
            e.scaleEnd = 0.03f * sc;
            e.baseAlpha = 0.85f;
            e.colorStart = star ? 0xFFFFFF : (i % 2 == 0 ? c1 : c2);
            e.colorEnd = c2;
            e.maxAge = 14 + rng.nextInt(10);
        }
    }

    private static void spawnJumpLandRing(Vec3 pos, boolean landing, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());

        // 1. Crisp expanding ground filigree ring + volumetric 3D landing dome!
        spawnRing(pos.add(0, 0.05, 0), 0.28f * sc, (landing ? 2.15f : 1.65f) * sc,
                landing ? -8f : 10f, 0f, 0f, c1, c2, CustomVfxLoader.resolveTexture(style.ringTex()), landing ? 16 : 13, 0.82f);
        if (landing) {
            spawnDomeWave(pos.add(0, 0.06, 0), 0.35f * sc, 1.85f * sc, c2, c1, 12);
        }

        // 2. Crisp rising starlight + motif ring burst
        int n = (landing ? 6 : 4) + vfx.intensity * 2;
        Identifier moteTex = CustomVfxLoader.resolveTexture(style.primaryTex());
        for (int i = 0; i < n; i++) {
            boolean spark = (i % 2 == 1);
            EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, spark ? tex("star_spark") : moteTex, true, rb);
            if (e == null) break;
            double ang = i * (Math.PI * 2.0 / n) + rng.nextDouble() * 0.25;
            double spd = (landing ? 0.10 : 0.06) + rng.nextDouble() * 0.05;
            e.x = e.prevX = pos.x + Math.cos(ang) * 0.24;
            e.y = e.prevY = pos.y + 0.12;
            e.z = e.prevZ = pos.z + Math.sin(ang) * 0.24;
            e.vx = Math.cos(ang) * spd;
            e.vy = landing ? (0.04 + rng.nextDouble() * 0.06) : (0.08 + rng.nextDouble() * 0.07);
            e.vz = Math.sin(ang) * spd;
            e.gravity = -0.0035;
            e.drag = 0.91;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 16.0f;
            e.scaleStart = (spark ? 0.15f : 0.22f) * sc;
            e.scaleEnd = 0.03f * sc;
            e.colorStart = spark ? 0xFFFFFF : c1;
            e.colorEnd = c2;
            e.maxAge = 13 + rng.nextInt(8);
        }
    }

    private static void spawnSprintStartGate(Vec3 pos, float yaw, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        spawnRing(pos, 0.45f * sc, 1.95f * sc, 14f, yaw, 90f, c1, c2, CustomVfxLoader.resolveTexture(style.ringTex()), 12, 0.78f);
        spawnRing(pos, 0.30f * sc, 1.45f * sc, -18f, yaw, 90f, 0xFFFFFF, c1, tex("shockwave_ring"), 9, 0.65f);
    }

    private static void spawnDashWarpBurst(Vec3 from, Vec3 to, float yaw, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());

        // Connecting energy warp beam between origin and destination!
        if (from.distanceTo(to) < 28.0) {
            spawnEnergyBeam(from.add(0, 0.95, 0), to.add(0, 0.95, 0), 0xFFFFFF, c1, 0.26f * sc, 9);
        }
        for (Vec3 p : new Vec3[]{from, to}) {
            spawnRing(p.add(0, 0.08, 0), 0.35f * sc, 2.35f * sc, 14f, 0f, 0f, c1, c2, CustomVfxLoader.resolveTexture(style.ringTex()), 16, 0.85f);
            spawnRing(p.add(0, 1.0, 0), 0.42f * sc, 2.05f * sc, -16f, yaw, 90f, c2, c1, tex("vortex_spiral"), 13, 0.80f);
        }
        spawnFlashStar(to.add(0, 1.0, 0), 0xFFFFFF, c1, 1.1f * sc, 9);

        int n = 6 + vfx.intensity * 3;
        Identifier moteTex = CustomVfxLoader.resolveTexture(style.primaryTex());
        for (int i = 0; i < n; i++) {
            EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, i % 3 == 0 ? tex("star_spark") : moteTex, true, rb);
            if (e == null) break;
            double ang = rng.nextDouble() * Math.PI * 2.0;
            e.x = e.prevX = to.x; e.y = e.prevY = to.y + 0.8 + rng.nextDouble() * 0.6; e.z = e.prevZ = to.z;
            e.vx = Math.cos(ang) * (0.08 + rng.nextDouble() * 0.11);
            e.vy = (rng.nextDouble() - 0.3) * 0.10;
            e.vz = Math.sin(ang) * (0.08 + rng.nextDouble() * 0.11);
            e.drag = 0.90;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 20.0f;
            e.scaleStart = 0.24f * sc; e.scaleEnd = 0.04f * sc;
            e.colorStart = i % 3 == 0 ? 0xFFFFFF : c1; e.colorEnd = c2;
            e.maxAge = 14 + rng.nextInt(8);
        }
    }

    private static void spawnProjectileSpark(Vec3 pos, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());
        EffectInstance mote = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, CustomVfxLoader.resolveTexture(style.primaryTex()), true, rb);
        if (mote != null) {
            mote.x = mote.prevX = pos.x + (rng.nextDouble() - 0.5) * 0.16;
            mote.y = mote.prevY = pos.y + (rng.nextDouble() - 0.5) * 0.16;
            mote.z = mote.prevZ = pos.z + (rng.nextDouble() - 0.5) * 0.16;
            mote.vx = (rng.nextDouble() - 0.5) * 0.025; mote.vy = 0.014; mote.vz = (rng.nextDouble() - 0.5) * 0.025;
            mote.drag = 0.92;
            mote.roll = rng.nextFloat() * 360.0f; mote.vRoll = (rng.nextFloat() - 0.5f) * 18.0f;
            mote.scaleStart = 0.22f * sc; mote.scaleEnd = 0.03f * sc;
            mote.colorStart = c1; mote.colorEnd = c2;
            mote.maxAge = 11;
        }
    }

    private static void spawnAmbientMotes(Vec3 pos, WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx, boolean firstPerson, int mult) {
        var rng = ThreadLocalRandom.current();
        float sc = (float) vfx.scale;
        int c1 = primaryColor(style, vfx);
        int c2 = secondaryColor(style, vfx);
        boolean rb = "prism".equals(style.id());

        int count = Math.max(1, vfx.intensity) * mult;
        Identifier moteTex = CustomVfxLoader.resolveTexture(style.primaryTex());
        for (int i = 0; i < count; i++) {
            boolean sparkle = (i % 2 == 1);
            EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, sparkle ? tex("star_spark") : moteTex, true, rb);
            if (e == null) break;
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double r = (firstPerson ? 1.25 : 0.78) + rng.nextDouble() * 0.65;
            e.x = e.prevX = pos.x + Math.cos(ang) * r;
            e.y = e.prevY = pos.y + 0.28 + rng.nextDouble() * 1.55;
            e.z = e.prevZ = pos.z + Math.sin(ang) * r;
            e.vx = -Math.sin(ang) * 0.022;
            e.vy = "sakura".equals(style.id()) || "frost".equals(style.id()) ? -0.011 : 0.020;
            e.vz = Math.cos(ang) * 0.022;
            e.drag = 0.97;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 9.0f;
            e.scaleStart = (sparkle ? 0.13f : 0.17f + rng.nextFloat() * 0.10f) * sc;
            e.scaleEnd = 0.03f * sc;
            e.baseAlpha = 0.78f;
            e.colorStart = sparkle ? 0xFFFFFF : (i % 2 == 0 ? c1 : c2);
            e.colorEnd = c2;
            e.maxAge = 24 + rng.nextInt(14);
        }

        if (!firstPerson && tickCount % 32 == 0) {
            spawnRing(pos.add(0, 0.05, 0), 0.95f * sc, 1.55f * sc, 4.0f, 0f, 0f, c1, c2,
                    CustomVfxLoader.resolveTexture(style.ringTex()), 28, 0.44f);
        }
    }

    // ---------------------------------------------------------------- layered primitive spawners

    private static void spawnSlashPair(Vec3 pos, float yaw, float pitch, float roll, float vYaw,
                                       float s0, float s1, int c1, int c2, boolean rb, int maxAge) {
        EffectInstance outer = spawn(WardrobeVfxMeshes.Kind.SLASH_ARC, tex("slash_arc"), false, rb);
        if (outer != null) {
            outer.x = outer.prevX = pos.x; outer.y = outer.prevY = pos.y; outer.z = outer.prevZ = pos.z;
            outer.yaw = yaw; outer.vYaw = vYaw; outer.pitch = pitch; outer.roll = roll;
            outer.scaleStart = s0; outer.scaleEnd = s1;
            outer.colorStart = c1; outer.colorEnd = c2;
            outer.baseAlpha = 0.92f;
            outer.maxAge = maxAge;
        }
        EffectInstance core = spawn(WardrobeVfxMeshes.Kind.SLASH_ARC, tex("slash_core"), false, false);
        if (core != null) {
            core.x = core.prevX = pos.x; core.y = core.prevY = pos.y + 0.01; core.z = core.prevZ = pos.z;
            core.yaw = yaw; core.vYaw = vYaw; core.pitch = pitch; core.roll = roll;
            core.scaleStart = s0 * 0.98f; core.scaleEnd = s1 * 1.02f;
            core.colorStart = 0xFFFFFF; core.colorEnd = c2;
            core.baseAlpha = 0.98f;
            core.maxAge = Math.max(4, maxAge - 2);
        }
    }

    private static void spawnFlashStar(Vec3 pos, int coreColor, int haloColor, float size, int maxAge) {
        EffectInstance bloom = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, tex("glow_soft"), true, false);
        if (bloom != null) {
            bloom.x = bloom.prevX = pos.x; bloom.y = bloom.prevY = pos.y; bloom.z = bloom.prevZ = pos.z;
            bloom.scaleStart = size * 1.15f; bloom.scaleEnd = size * 0.15f;
            bloom.colorStart = haloColor; bloom.colorEnd = haloColor;
            bloom.baseAlpha = 0.58f;
            bloom.maxAge = maxAge;
        }
        EffectInstance star = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, tex("star_spark"), true, false);
        if (star != null) {
            star.x = star.prevX = pos.x; star.y = star.prevY = pos.y; star.z = star.prevZ = pos.z;
            star.roll = ThreadLocalRandom.current().nextFloat() * 45.0f;
            star.vRoll = 12.0f;
            star.scaleStart = size; star.scaleEnd = size * 0.10f;
            star.colorStart = coreColor; star.colorEnd = haloColor;
            star.baseAlpha = 0.98f;
            star.maxAge = Math.max(4, maxAge - 1);
        }
    }

    private static void spawnRing(Vec3 pos, float s0, float s1, float spin, float yaw, float pitch,
                                  int c1, int c2, Identifier texId, int maxAge, float alpha) {
        EffectInstance ring = spawn(WardrobeVfxMeshes.Kind.RING_FLAT, texId, false, false);
        if (ring != null) {
            ring.x = ring.prevX = pos.x; ring.y = ring.prevY = pos.y; ring.z = ring.prevZ = pos.z;
            ring.yaw = yaw; ring.pitch = pitch;
            if (Math.abs(pitch) > 45f) ring.vRoll = spin; else ring.vYaw = spin;
            ring.scaleStart = s0; ring.scaleEnd = s1;
            ring.colorStart = c1; ring.colorEnd = c2;
            ring.baseAlpha = alpha;
            ring.maxAge = maxAge;
        }
    }

    private static void spawnDomeWave(Vec3 pos, float s0, float s1, int c1, int c2, int maxAge) {
        EffectInstance dome = spawn(WardrobeVfxMeshes.Kind.DOME_WAVE, tex("ribbon_trail"), false, false);
        if (dome != null) {
            dome.x = dome.prevX = pos.x; dome.y = dome.prevY = pos.y; dome.z = dome.prevZ = pos.z;
            dome.vYaw = 9.0f;
            dome.scaleStart = s0; dome.scaleEnd = s1;
            dome.scaleYMult = 0.72f;
            dome.colorStart = c1; dome.colorEnd = c2;
            dome.baseAlpha = 0.56f;
            dome.maxAge = maxAge;
        }
    }

    private static void spawnMoteBurst(Vec3 pos, Identifier moteTex, int c1, int c2, int count, double speed, float size, int maxAge) {
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            EffectInstance e = spawn(WardrobeVfxMeshes.Kind.QUAD_BILLBOARD, moteTex, true, false);
            if (e == null) break;
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double sp = speed * (0.55 + rng.nextDouble() * 0.75);
            e.x = e.prevX = pos.x; e.y = e.prevY = pos.y; e.z = e.prevZ = pos.z;
            e.vx = Math.cos(ang) * sp;
            e.vy = (rng.nextDouble() - 0.2) * sp;
            e.vz = Math.sin(ang) * sp;
            e.drag = 0.90;
            e.roll = rng.nextFloat() * 360.0f;
            e.vRoll = (rng.nextFloat() - 0.5f) * 20.0f;
            e.scaleStart = size * (0.75f + rng.nextFloat() * 0.5f);
            e.scaleEnd = size * 0.12f;
            e.colorStart = i % 2 == 0 ? c1 : c2;
            e.colorEnd = c2;
            e.maxAge = maxAge + rng.nextInt(5);
        }
    }

    // ---------------------------------------------------------------- simulation & 3D rendering

    private static void advanceInstances() {
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            EffectInstance e = LIVE.get(i);
            e.age++;
            if (e.age >= e.maxAge) {
                int last = LIVE.size() - 1;
                if (i != last) LIVE.set(i, LIVE.get(last));
                LIVE.remove(last);
                continue;
            }
            e.prevX = e.x; e.prevY = e.y; e.prevZ = e.z;
            e.vy += e.gravity;
            e.vx *= e.drag; e.vy *= e.drag; e.vz *= e.drag;
            e.x += e.vx; e.y += e.vy; e.z += e.vz;
            e.yaw += e.vYaw; e.pitch += e.vPitch; e.roll += e.vRoll;
        }
    }

    static void render(LevelRenderContext ctx) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        var camRot = ctx.levelState().cameraRenderState.orientation;
        PoseStack pose = ctx.poseStack();
        SubmitNodeCollector collector = ctx.submitNodeCollector();
        double time = CosmeticAnimation.animationTimeSeconds();

        for (int i = 0; i < LIVE.size(); i++) {
            EffectInstance e = LIVE.get(i);
            float t = Math.min(1.0f, (e.age + partial) / (float) Math.max(1, e.maxAge));
            float fadeIn = Math.min(1.0f, t / 0.12f);
            float fadeOut = 1.0f - (t * t);
            int alpha = Math.max(0, Math.min(255, Math.round(fadeIn * fadeOut * e.baseAlpha * 255.0f)));
            if (alpha < 5) continue;

            float easeOut = 1.0f - (1.0f - t) * (1.0f - t);
            float sc = Mth.lerp(easeOut, e.scaleStart, e.scaleEnd);
            if (sc <= 0.005f) continue;

            int rgb = e.rainbow ? rainbowRgb((time * 0.75 + i * 0.09) % 1.0) : lerpRgb(e.colorStart, e.colorEnd, t);
            int argb = (alpha << 24) | (rgb & 0xFFFFFF);

            double rx = Mth.lerp(partial, e.prevX, e.x) - cam.x;
            double ry = Mth.lerp(partial, e.prevY, e.y) - cam.y;
            double rz = Mth.lerp(partial, e.prevZ, e.z) - cam.z;

            pose.pushPose();
            pose.translate(rx, ry, rz);
            if (e.billboard) {
                pose.mulPose(camRot);
                if (Math.abs(e.roll) > 0.1f || Math.abs(e.vRoll) > 0.1f) {
                    pose.mulPose(Axis.ZP.rotationDegrees(e.roll + e.vRoll * partial));
                }
            } else {
                float yRot = e.yaw + e.vYaw * partial;
                float xRot = e.pitch + e.vPitch * partial;
                float zRot = e.roll + e.vRoll * partial;
                if (Math.abs(yRot) > 0.1f) pose.mulPose(Axis.YP.rotationDegrees(180.0f - yRot));
                if (Math.abs(xRot) > 0.1f) pose.mulPose(Axis.XP.rotationDegrees(xRot));
                if (Math.abs(zRot) > 0.1f) pose.mulPose(Axis.ZP.rotationDegrees(zRot));
            }
            // Positive determinant (+1): preserves front-face winding order across Iris/Sodium!
            pose.scale(sc * e.scaleXMult, -sc * e.scaleYMult, -sc * e.scaleZMult);

            collector.submitModelPart(
                    WardrobeVfxMeshes.get(e.kind),
                    pose,
                    RenderTypes.entityTranslucentEmissive(e.texture),
                    FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    null,
                    argb,
                    null
            );
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static EffectInstance spawn(WardrobeVfxMeshes.Kind kind, Identifier texture, boolean billboard, boolean rainbow) {
        if (LIVE.size() >= MAX_LIVE) return null;
        EffectInstance e = new EffectInstance();
        e.kind = kind;
        e.texture = texture;
        e.billboard = billboard;
        e.rainbow = rainbow;
        LIVE.add(e);
        return e;
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("skypilot", "textures/vfx/" + name + ".png");
    }

    private static int primaryColor(WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        if (vfx != null && vfx.customColorEnabled) return vfx.customColor & 0xFFFFFF;
        return style.primaryColor() & 0xFFFFFF;
    }

    private static int secondaryColor(WardrobeVfxCatalog.VfxStyle style, SkyPilotConfig.VfxSettings vfx) {
        if (vfx != null && vfx.customColorEnabled) return lighten(vfx.customColor & 0xFFFFFF, 0.42f);
        return style.secondaryColor() & 0xFFFFFF;
    }

    private static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (Math.min(255, (int) (r + (255 - r) * f)) << 16)
                | (Math.min(255, (int) (g + (255 - g) * f)) << 8)
                | Math.min(255, (int) (b + (255 - b) * f));
    }

    private static int lerpRgb(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
        int r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int rainbowRgb(double h) {
        double s = (h % 1.0) * 6.0;
        int i = (int) s % 6;
        double f = s - Math.floor(s);
        int q = (int) (255 * (1.0 - f)), v = (int) (255 * f);
        return switch (i) {
            case 0 -> (255 << 16) | (v << 8) | 80;
            case 1 -> (q << 16) | (255 << 8) | 80;
            case 2 -> (80 << 16) | (255 << 8) | v;
            case 3 -> (80 << 16) | (q << 8) | 255;
            case 4 -> (v << 16) | (90 << 8) | 255;
            default -> (255 << 16) | (80 << 8) | q;
        };
    }

    private static Entity findAimedVictim(Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (mc.crosshairPickEntity instanceof LivingEntity le && le != player && !(le instanceof ArmorStand) && le.isAlive()) {
            return le;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(7.5, 3.5, 7.5);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && !(e instanceof ArmorStand) && e.isAlive() && !e.isInvisibleTo(player));
        Entity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (LivingEntity e : nearby) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 0.1 || dist > 7.5) continue;
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot < 0.88) continue;
            double score = dist * (1.2 - dot);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }
}
