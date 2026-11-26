package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Living world-space physics, warp/teleport recovery, visual co-op attack choreography for the
 * local player's equipped Floating Pet &amp; Ground Companion, PLUS live remote-player Pet &amp;
 * Companion tracking so other players with the Wardrobe mod also have animated pets!
 */
final class PetWorldPhysics {
    enum PetMode {
        FOLLOW_SIDE, ORBIT_PLAYFUL, PERCH_SHOULDER_L, PERCH_SHOULDER_R, PERCH_HEAD, CURIOUS_PEEK, ATTACK_TARGET
    }

    enum CompMode {
        FOLLOW, WANDER, SIT_WATCH, ATTACK_TARGET
    }

    static final class Snapshot {
        final double x, y, z;
        final float yaw, pitch, roll;
        final double speed;
        final boolean perched;

        Snapshot(double x, double y, double z, float yaw, float pitch, float roll, double speed, boolean perched) {
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch; this.roll = roll;
            this.speed = speed; this.perched = perched;
        }
    }

    private static final class RemoteState {
        boolean petInit, compInit;
        double px, py, pz, prevPx, prevPy, prevPz;
        float pYaw, prevPYaw;
        double petSpeed;
        double cx, cy, cz, prevCx, prevCy, prevCz;
        float cYaw, prevCYaw;
        double compSpeed;
        long lastSeenTick;
    }

    private static final Map<UUID, RemoteState> REMOTE_STATES = new ConcurrentHashMap<>();

    // ---- Local Player Warp / Teleport Detection State ----
    private static ClientLevel lastLevel;
    private static int lastPlayerId = -1;
    private static boolean hasPlayerPos;
    private static double lastPlayerX, lastPlayerY, lastPlayerZ;
    private static int lastAttackTriggerTick = -100;

    // ---- Local Floating Pet State ----
    private static boolean petInit;
    private static double px, py, pz, prevPx, prevPy, prevPz;
    private static double pvx, pvy, pvz;
    private static float pYaw, prevPYaw, pPitch, prevPPitch, pRoll, prevPRoll;
    private static PetMode petMode = PetMode.FOLLOW_SIDE;
    private static int petModeTicks;
    private static int petSide = 1;
    private static double orbitAngle;
    private static double orbitDir = 1.0;
    private static double petSpeed;
    private static int petAttackTargetId = -1;
    private static int petAttackAge;
    private static boolean petStruck;
    private static int petAttackSide = 1;

    // ---- Local Ground Companion State ----
    private static boolean compInit;
    private static double cx, cy, cz, prevCx, prevCy, prevCz;
    private static double cvx, cvz;
    private static float cYaw, prevCYaw, cPitch, prevCPitch;
    private static double cHop, prevCHop;
    private static CompMode compMode = CompMode.FOLLOW;
    private static int compModeTicks;
    private static double wanderAngle = -0.85;
    private static double wanderRadius = 1.45;
    private static double compSpeed;
    private static int compAttackTargetId = -1;
    private static int compAttackAge;
    private static boolean compStruck;

    private PetWorldPhysics() {}

    static void reset() {
        petInit = false;
        compInit = false;
        hasPlayerPos = false;
        lastLevel = null;
        lastPlayerId = -1;
        petAttackTargetId = -1;
        compAttackTargetId = -1;
        petStruck = false;
        compStruck = false;
        cHop = prevCHop = 0.0;
        cPitch = prevCPitch = 0.0f;
        REMOTE_STATES.clear();
    }

    static void onPlayerAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (player == null || mc.level == null || rt == null) return;
        if (!isValidAttackTarget(player, target)) return;

        SkyPilotConfig.Appearance a = rt.appearance();
        lastAttackTriggerTick = player.tickCount;

        if (!"none".equals(a.pet) && petInit) {
            petAttackTargetId = target.getId();
            petMode = PetMode.ATTACK_TARGET;
            petAttackAge = 0;
            petStruck = false;
            petAttackSide = -petAttackSide;
            petModeTicks = 22;
        }
        if (!"none".equals(a.companion) && compInit) {
            compAttackTargetId = target.getId();
            compMode = CompMode.ATTACK_TARGET;
            compAttackAge = 0;
            compStruck = false;
            compModeTicks = 26;
        }
    }

    private static boolean isValidAttackTarget(LocalPlayer player, Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (le == player || !le.isAlive() || le.isRemoved()) return false;
        if (le instanceof ArmorStand) return false;
        if (le.isInvisibleTo(player)) return false;
        return le.distanceToSqr(player) <= 16.0 * 16.0;
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (player == null || level == null || rt == null || player.isSpectator()) {
            reset();
            return;
        }

        boolean levelChanged = (level != lastLevel) || (player.getId() != lastPlayerId);
        boolean suddenTeleport = hasPlayerPos && (
                Math.hypot(player.getX() - lastPlayerX, player.getZ() - lastPlayerZ) > 6.5
                        || Math.abs(player.getY() - lastPlayerY) > 5.0
        );
        if (levelChanged || suddenTeleport) {
            petInit = false;
            compInit = false;
            petAttackTargetId = -1;
            compAttackTargetId = -1;
            petStruck = false;
            compStruck = false;
            cHop = prevCHop = 0.0;
            cPitch = prevCPitch = 0.0f;
            if (levelChanged) REMOTE_STATES.clear();
        }
        lastLevel = level;
        lastPlayerId = player.getId();
        lastPlayerX = player.getX();
        lastPlayerY = player.getY();
        lastPlayerZ = player.getZ();
        hasPlayerPos = true;

        SkyPilotConfig.Appearance a = rt.appearance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (player.swinging && player.swingTime == 1 && (player.tickCount - lastAttackTriggerTick) >= 2
                && (!"none".equals(a.pet) || !"none".equals(a.companion))) {
            Entity aimed = findAimedTarget(mc, player, level);
            if (aimed != null) onPlayerAttack(aimed);
        }

        if (!"none".equals(a.pet)) {
            tickPet(player, level, a, firstPerson);
        } else {
            petInit = false;
            petAttackTargetId = -1;
        }
        if (!"none".equals(a.companion)) {
            tickCompanion(player, level);
        } else {
            compInit = false;
            compAttackTargetId = -1;
        }

        // Tick synced remote players' pets & companions cleanly!
        if (a.showOtherPlayers && a.otherPlayerPets) {
            tickRemotePlayers(player, level);
        } else if (!REMOTE_STATES.isEmpty()) {
            REMOTE_STATES.clear();
        }
    }

    private static void tickRemotePlayers(LocalPlayer local, ClientLevel level) {
        long nowTick = local.tickCount;
        for (AbstractClientPlayer remote : level.players()) {
            if (remote == null || remote == local || remote.isInvisible() || remote.isSpectator()) continue;
            if (remote.distanceToSqr(local) > 72.0 * 72.0) continue;
            SkyPilotConfig.Appearance ra = RemoteWardrobeSync.getRemote(remote.getUUID());
            if (ra == null) continue;
            boolean hasPet = !"none".equals(ra.pet);
            boolean hasComp = !"none".equals(ra.companion);
            if (!hasPet && !hasComp) continue;

            RemoteState st = REMOTE_STATES.computeIfAbsent(remote.getUUID(), k -> new RemoteState());
            st.lastSeenTick = nowTick;

            double bodyRad = Math.toRadians(remote.yBodyRot);
            double fwdX = -Math.sin(bodyRad), fwdZ = Math.cos(bodyRad);
            double leftX = Math.cos(bodyRad), leftZ = Math.sin(bodyRad);
            double t = (nowTick + (remote.getId() & 31) * 7) * 0.05;

            if (hasPet) {
                double tx = remote.getX() + leftX * 0.96 - fwdX * 0.32 + Math.cos(t * 1.4) * 0.14;
                double ty = remote.getY() + 1.56 - (remote.isCrouching() ? 0.25 : 0.0) + Math.sin(t * 2.5) * 0.14;
                double tz = remote.getZ() + leftZ * 0.96 - fwdZ * 0.32 + Math.sin(t * 1.4) * 0.14;
                if (!st.petInit || Math.hypot(st.px - remote.getX(), st.pz - remote.getZ()) > 12.0 || Math.abs(st.py - ty) > 5.0) {
                    st.px = st.prevPx = tx; st.py = st.prevPy = ty; st.pz = st.prevPz = tz;
                    st.pYaw = st.prevPYaw = remote.yBodyRot;
                    st.petInit = true;
                } else {
                    st.prevPx = st.px; st.prevPy = st.py; st.prevPz = st.pz; st.prevPYaw = st.pYaw;
                    double vx = (tx - st.px) * 0.22, vy = (ty - st.py) * 0.22, vz = (tz - st.pz) * 0.22;
                    st.px += vx; st.py += vy; st.pz += vz;
                    st.petSpeed = Math.hypot(vx, vz);
                    float targetYaw = st.petSpeed > 0.03 ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : remote.yBodyRot;
                    st.pYaw += Mth.wrapDegrees(targetYaw - st.pYaw) * 0.24f;
                }
            } else {
                st.petInit = false;
            }

            if (hasComp) {
                double tx = remote.getX() - leftX * 1.22 - fwdX * 0.62;
                double tz = remote.getZ() - leftZ * 1.22 - fwdZ * 0.62;
                double groundY = findGroundY(level, tx, remote.getY(), tz);
                if (!st.compInit || Math.hypot(st.cx - remote.getX(), st.cz - remote.getZ()) > 12.0 || Math.abs(st.cy - groundY) > 4.5) {
                    st.cx = st.prevCx = tx; st.cy = st.prevCy = groundY; st.cz = st.prevCz = tz;
                    st.cYaw = st.prevCYaw = remote.yBodyRot;
                    st.compInit = true;
                } else {
                    st.prevCx = st.cx; st.prevCy = st.cy; st.prevCz = st.cz; st.prevCYaw = st.cYaw;
                    double vx = (tx - st.cx) * 0.24, vz = (tz - st.cz) * 0.24;
                    st.cx += vx; st.cz += vz;
                    st.cy += (groundY - st.cy) * 0.45;
                    st.compSpeed = Math.hypot(vx, vz);
                    float targetYaw = st.compSpeed > 0.025 ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : remote.yBodyRot;
                    st.cYaw += Mth.wrapDegrees(targetYaw - st.cYaw) * 0.24f;
                }
            } else {
                st.compInit = false;
            }
        }
        if (nowTick % 40 == 0) {
            REMOTE_STATES.entrySet().removeIf(e -> nowTick - e.getValue().lastSeenTick > 60);
        }
    }

    private static Entity findAimedTarget(Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (isValidAttackTarget(player, mc.crosshairPickEntity)) return mc.crosshairPickEntity;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(9.5, 4.5, 9.5);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, e -> isValidAttackTarget(player, e));
        Entity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (LivingEntity e : nearby) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 0.1 || dist > 9.5) continue;
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot < 0.88) continue;
            double score = dist * (1.2 - dot);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    private static void tickPet(LocalPlayer player, ClientLevel level, SkyPilotConfig.Appearance a, boolean firstPerson) {
        double bodyRad = Math.toRadians(player.yBodyRot);
        double fwdX = -Math.sin(bodyRad), fwdZ = Math.cos(bodyRad);
        double leftX = Math.cos(bodyRad), leftZ = Math.sin(bodyRad);

        double lookRad = Math.toRadians(player.getYRot());
        double lookFwdX = -Math.sin(lookRad), lookFwdZ = Math.cos(lookRad);
        double lookLeftX = Math.cos(lookRad), lookLeftZ = Math.sin(lookRad);

        double playerHorizSpeed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        boolean movingFast = playerHorizSpeed > 0.14 || player.isSprinting() || player.isFallFlying();
        boolean hasHelmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean hasChest = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean hasHat = !"none".equals(a.hat);

        boolean attacking = petMode == PetMode.ATTACK_TARGET;
        double maxHoriz = attacking ? 20.0 : 13.0;
        double maxVert = attacking ? 10.0 : 5.5;

        if (!petInit || Math.hypot(px - player.getX(), pz - player.getZ()) > maxHoriz
                || Math.abs(py - (player.getY() + 1.5)) > maxVert) {
            px = prevPx = player.getX() + leftX * 0.95 - fwdX * 0.35;
            py = prevPy = player.getY() + 1.55;
            pz = prevPz = player.getZ() + leftZ * 0.95 - fwdZ * 0.35;
            pvx = pvy = pvz = 0;
            pYaw = prevPYaw = player.yBodyRot;
            pPitch = prevPPitch = 0;
            pRoll = prevPRoll = 0;
            petMode = PetMode.FOLLOW_SIDE;
            petAttackTargetId = -1;
            petStruck = false;
            petModeTicks = 90;
            petInit = true;
            return;
        }

        prevPx = px; prevPy = py; prevPz = pz;
        prevPYaw = pYaw; prevPPitch = pPitch; prevPRoll = pRoll;

        if (petMode == PetMode.ATTACK_TARGET) {
            Entity target = petAttackTargetId >= 0 ? level.getEntity(petAttackTargetId) : null;
            petAttackAge++;
            if (target == null || !isValidAttackTarget(player, target) || petAttackAge > 18) {
                petMode = PetMode.FOLLOW_SIDE;
                petAttackTargetId = -1;
                petStruck = false;
                petModeTicks = 65;
            } else {
                tickPetAttack(player, level, target);
                return;
            }
        }

        if (firstPerson && (petMode == PetMode.PERCH_HEAD || petMode == PetMode.PERCH_SHOULDER_L || petMode == PetMode.PERCH_SHOULDER_R)) {
            petMode = PetMode.FOLLOW_SIDE; petModeTicks = 80;
        }
        if (movingFast && (petMode == PetMode.PERCH_HEAD || petMode == PetMode.PERCH_SHOULDER_L || petMode == PetMode.PERCH_SHOULDER_R)) {
            petMode = PetMode.FOLLOW_SIDE; petModeTicks = 70;
        }

        if (--petModeTicks <= 0) {
            var rng = ThreadLocalRandom.current();
            int roll = rng.nextInt(100);
            if (movingFast) {
                if (roll < 62) { petMode = PetMode.FOLLOW_SIDE; if (rng.nextInt(3) == 0) petSide = -petSide; petModeTicks = 70 + rng.nextInt(60); }
                else if (roll < 86) { petMode = PetMode.ORBIT_PLAYFUL; orbitDir = rng.nextBoolean() ? 1.0 : -1.0; petModeTicks = 75 + rng.nextInt(55); }
                else { petMode = PetMode.CURIOUS_PEEK; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 55 + rng.nextInt(35); }
            } else if (firstPerson) {
                if (roll < 42) { petMode = PetMode.FOLLOW_SIDE; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 80 + rng.nextInt(60); }
                else if (roll < 74) { petMode = PetMode.CURIOUS_PEEK; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 75 + rng.nextInt(50); }
                else { petMode = PetMode.ORBIT_PLAYFUL; orbitDir = rng.nextBoolean() ? 1.0 : -1.0; petModeTicks = 90 + rng.nextInt(60); }
            } else {
                if (roll < 26) { petMode = PetMode.FOLLOW_SIDE; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 80 + rng.nextInt(60); }
                else if (roll < 50) { petMode = PetMode.ORBIT_PLAYFUL; orbitDir = rng.nextBoolean() ? 1.0 : -1.0; petModeTicks = 85 + rng.nextInt(55); }
                else if (roll < 68) { petMode = PetMode.PERCH_SHOULDER_L; petModeTicks = 95 + rng.nextInt(75); }
                else if (roll < 84) { petMode = PetMode.PERCH_SHOULDER_R; petModeTicks = 95 + rng.nextInt(75); }
                else if (roll < 93) { petMode = PetMode.PERCH_HEAD; petModeTicks = 85 + rng.nextInt(65); }
                else { petMode = PetMode.CURIOUS_PEEK; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 65 + rng.nextInt(40); }
            }
        }

        double t = player.tickCount * 0.05;
        double tx, ty, tz;
        boolean perched = false;
        double stiffness = 0.12, damping = 0.68;
        double crouchDrop = player.isCrouching() ? 0.25 : 0.0;
        double shoulderY = player.getY() + 1.56 - crouchDrop + (hasChest ? 0.09 : 0.0);
        double headTopY = player.getY() + 2.02 - crouchDrop + (hasHelmet ? 0.10 : 0.0) + (hasHat ? 0.18 : 0.0);

        switch (petMode) {
            case ORBIT_PLAYFUL -> {
                orbitAngle += 0.085 * orbitDir;
                double radius = firstPerson ? 1.38 : 1.18;
                tx = player.getX() + Math.cos(orbitAngle) * radius;
                tz = player.getZ() + Math.sin(orbitAngle) * radius;
                ty = player.getY() + 1.48 - crouchDrop + Math.sin(orbitAngle * 2.0) * 0.24;
                stiffness = 0.14; damping = 0.72;
            }
            case PERCH_SHOULDER_L, PERCH_SHOULDER_R -> {
                double sgn = petMode == PetMode.PERCH_SHOULDER_L ? 1.0 : -1.0;
                double sOff = 0.44 + (hasChest ? 0.07 : 0.0);
                tx = player.getX() + leftX * (sOff * sgn) - fwdX * 0.04;
                tz = player.getZ() + leftZ * (sOff * sgn) - fwdZ * 0.04;
                ty = shoulderY;
                stiffness = 0.22; damping = 0.62;
                perched = Math.hypot(tx - px, tz - pz) < 0.20 && Math.abs(ty - py) < 0.20;
            }
            case PERCH_HEAD -> {
                tx = player.getX(); tz = player.getZ(); ty = headTopY;
                stiffness = 0.22; damping = 0.62;
                perched = Math.hypot(tx - px, tz - pz) < 0.20 && Math.abs(ty - py) < 0.20;
            }
            case CURIOUS_PEEK -> {
                double fwd = firstPerson ? 1.28 : 1.15;
                double lat = (firstPerson ? 0.82 : 0.72) * petSide;
                tx = player.getX() + lookFwdX * fwd + lookLeftX * lat;
                tz = player.getZ() + lookFwdZ * fwd + lookLeftZ * lat;
                ty = player.getEyeY() - 0.16 + Math.sin(t * 3.4) * 0.10;
            }
            default -> {
                if (firstPerson) {
                    tx = player.getX() + lookFwdX * 0.85 + lookLeftX * (0.86 * petSide);
                    tz = player.getZ() + lookFwdZ * 0.85 + lookLeftZ * (0.86 * petSide);
                    ty = player.getEyeY() - 0.18 + Math.sin(t * 2.8) * 0.11;
                } else {
                    double lat = 0.96 * petSide;
                    double back = 0.32 + Math.min(0.45, playerHorizSpeed * 1.4);
                    tx = player.getX() + leftX * lat - fwdX * back;
                    tz = player.getZ() + leftZ * lat - fwdZ * back;
                    ty = player.getY() + 1.56 - crouchDrop + Math.sin(t * 2.6) * 0.14;
                }
            }
        }

        if (firstPerson) {
            Vec3 eye = player.getEyePosition();
            double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.80 && dist > 1e-4) {
                tx = eye.x + dx / dist * 0.80; ty = eye.y + dy / dist * 0.80; tz = eye.z + dz / dist * 0.80;
            }
            double dot = (tx - eye.x) * lookFwdX + (tz - eye.z) * lookFwdZ;
            double cross = (tx - eye.x) * lookLeftX + (tz - eye.z) * lookLeftZ;
            if (dot > 0.20 && Math.abs(cross) < 0.55) {
                double push = (cross >= 0 ? 1 : -1) * (0.58 - Math.abs(cross));
                tx += lookLeftX * push; tz += lookLeftZ * push;
            }
        }

        if (!perched) {
            BlockPos bp = BlockPos.containing(tx, ty, tz);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                tx = Mth.lerp(0.6, tx, player.getX());
                tz = Mth.lerp(0.6, tz, player.getZ());
                ty = Mth.lerp(0.4, ty, player.getY() + 1.55);
            }
        }
        ty = Mth.clamp(ty, player.getY() + 0.45, player.getY() + 2.85);

        pvx = (pvx + (tx - px) * stiffness) * damping;
        pvy = (pvy + (ty - py) * stiffness) * damping;
        pvz = (pvz + (tz - pz) * stiffness) * damping;
        double spd = Math.sqrt(pvx * pvx + pvy * pvy + pvz * pvz);
        if (spd > 0.58) { double sc = 0.58 / spd; pvx *= sc; pvy *= sc; pvz *= sc; }
        px += pvx; py += pvy; pz += pvz;

        if (firstPerson) {
            Vec3 eye = player.getEyePosition();
            double rx = px - eye.x, ry = py - eye.y, rz = pz - eye.z;
            double fwdDot = rx * lookFwdX + rz * lookFwdZ;
            double latDot = rx * lookLeftX + rz * lookLeftZ;
            if (fwdDot > 0.15 && Math.abs(latDot) < 0.62 && Math.abs(ry) < 0.55) {
                double dir = latDot >= 0 ? 1.0 : -1.0;
                double nudge = (0.65 - Math.abs(latDot)) * 0.45;
                px += lookLeftX * dir * nudge; pz += lookLeftZ * dir * nudge;
            }
        }
        petSpeed = Math.hypot(pvx, pvz);

        float targetYaw = (petMode == PetMode.CURIOUS_PEEK && petSpeed < 0.08)
                ? (float) Math.toDegrees(Math.atan2(-(player.getX() - px), player.getZ() - pz))
                : (perched ? player.yBodyRot + (float) Math.sin(t * 1.15) * 26.0f
                : (petSpeed > 0.035 ? (float) Math.toDegrees(Math.atan2(-pvx, pvz)) : player.yBodyRot));
        float yawDiff = Mth.wrapDegrees(targetYaw - pYaw);
        pYaw += yawDiff * 0.22f;
        pPitch += ((perched ? 0.0f : (float) Mth.clamp(-pvy * 55.0 + petSpeed * 26.0, -24.0, 24.0)) - pPitch) * 0.2f;
        pRoll += ((perched ? 0.0f : Mth.clamp(yawDiff * 0.75f, -26.0f, 26.0f)) - pRoll) * 0.22f;
    }

    private static void tickPetAttack(LocalPlayer player, ClientLevel level, Entity target) {
        double aimX = target.getX();
        double aimY = target.getY() + Math.max(0.5, target.getBbHeight() * 0.64);
        double aimZ = target.getZ();
        double toX = aimX - px, toY = aimY - py, toZ = aimZ - pz;
        double dist = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        double hitRadius = Math.max(0.45, target.getBbWidth() * 0.52 + 0.28);

        if (!petStruck) {
            double inv = dist > 1e-4 ? 1.0 / dist : 0.0;
            double tx = aimX - toX * inv * hitRadius;
            double ty = aimY - toY * inv * (hitRadius * 0.4) + (petAttackAge < 3 ? 0.28 : 0.0);
            double tz = aimZ - toZ * inv * hitRadius;
            pvx = (pvx + (tx - px) * 0.48) * 0.58;
            pvy = (pvy + (ty - py) * 0.48) * 0.58;
            pvz = (pvz + (tz - pz) * 0.48) * 0.58;
            double spd = Math.sqrt(pvx * pvx + pvy * pvy + pvz * pvz);
            if (spd > 1.25) { double sc = 1.25 / spd; pvx *= sc; pvy *= sc; pvz *= sc; }
            px += pvx; py += pvy; pz += pvz;
            double rem = Math.sqrt((aimX - px) * (aimX - px) + (aimY - py) * (aimY - py) + (aimZ - pz) * (aimZ - pz));
            if (rem <= hitRadius + 0.42 || petAttackAge >= 8) {
                petStruck = true;
                double rInv = rem > 1e-4 ? 1.0 / rem : 1.0;
                double sideX = -(aimZ - pz) * rInv * petAttackSide;
                double sideZ = (aimX - px) * rInv * petAttackSide;
                pvx = -(aimX - px) * rInv * 0.34 + sideX * 0.24;
                pvy = 0.22;
                pvz = -(aimZ - pz) * rInv * 0.34 + sideZ * 0.24;
                pPitch = 40.0f; pRoll = petAttackSide * 38.0f;
                spawnAttackSparks(level, aimX - (aimX - px) * rInv * (hitRadius * 0.5), aimY, aimZ - (aimZ - pz) * rInv * (hitRadius * 0.5), false);
            } else {
                pPitch += (28.0f - pPitch) * 0.4f;
                pRoll += (petAttackSide * 22.0f - pRoll) * 0.35f;
            }
        } else {
            double bodyRad = Math.toRadians(player.yBodyRot);
            double leftX = Math.cos(bodyRad), leftZ = Math.sin(bodyRad);
            pvx = (pvx + (player.getX() + leftX * (0.95 * petAttackSide) - px) * 0.22) * 0.68;
            pvy = (pvy + (player.getY() + 1.62 - py) * 0.22) * 0.68;
            pvz = (pvz + (player.getZ() + leftZ * (0.95 * petAttackSide) - pz) * 0.22) * 0.68;
            px += pvx; py += pvy; pz += pvz;
            pPitch += (0.0f - pPitch) * 0.25f;
            pRoll += (0.0f - pRoll) * 0.25f;
            if (petAttackAge >= 15) {
                petMode = PetMode.FOLLOW_SIDE; petAttackTargetId = -1; petStruck = false; petModeTicks = 60;
            }
        }
        petSpeed = Math.max(0.28, Math.hypot(pvx, pvz));
        float aimYaw = (float) Math.toDegrees(Math.atan2(-(aimX - px), aimZ - pz));
        pYaw += Mth.wrapDegrees(aimYaw - pYaw) * 0.55f;
    }

    private static void tickCompanion(LocalPlayer player, ClientLevel level) {
        double bodyRad = Math.toRadians(player.yBodyRot);
        double fwdX = -Math.sin(bodyRad), fwdZ = Math.cos(bodyRad);
        double rightX = -Math.cos(bodyRad), rightZ = -Math.sin(bodyRad);
        double playerHorizSpeed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);

        boolean attacking = compMode == CompMode.ATTACK_TARGET;
        if (!compInit || Math.hypot(cx - player.getX(), cz - player.getZ()) > (attacking ? 20.0 : 13.0)
                || Math.abs(cy - player.getY()) > (attacking ? 8.0 : 4.5)) {
            cx = prevCx = player.getX() + rightX * 1.25 - fwdX * 0.4;
            cz = prevCz = player.getZ() + rightZ * 1.25 - fwdZ * 0.4;
            cy = prevCy = findGroundY(level, cx, player.getY(), cz);
            cvx = cvz = 0; cYaw = prevCYaw = player.yBodyRot; cPitch = prevCPitch = 0.0f; cHop = prevCHop = 0.0;
            compMode = CompMode.FOLLOW; compAttackTargetId = -1; compStruck = false; compModeTicks = 90; compInit = true;
            return;
        }

        prevCx = cx; prevCy = cy; prevCz = cz; prevCYaw = cYaw; prevCPitch = cPitch; prevCHop = cHop;

        if (compMode == CompMode.ATTACK_TARGET) {
            Entity target = compAttackTargetId >= 0 ? level.getEntity(compAttackTargetId) : null;
            compAttackAge++;
            if (target == null || !isValidAttackTarget(player, target) || compAttackAge > 22) {
                compMode = CompMode.FOLLOW; compAttackTargetId = -1; compStruck = false; compModeTicks = 55;
            } else {
                tickCompanionAttack(player, level, target);
                return;
            }
        }

        cHop *= 0.55; if (cHop < 0.01) cHop = 0.0;
        cPitch *= 0.65f;

        if (playerHorizSpeed > 0.08) {
            compMode = CompMode.FOLLOW; compModeTicks = 55;
        } else if (--compModeTicks <= 0) {
            var rng = ThreadLocalRandom.current();
            int r = rng.nextInt(100);
            if (r < 48) { compMode = CompMode.WANDER; wanderAngle = rng.nextDouble() * Math.PI * 2.0; wanderRadius = 1.25 + rng.nextDouble() * 0.85; compModeTicks = 75 + rng.nextInt(55); }
            else if (r < 80) { compMode = CompMode.SIT_WATCH; compModeTicks = 80 + rng.nextInt(70); }
            else { compMode = CompMode.FOLLOW; compModeTicks = 60 + rng.nextInt(40); }
        }

        double tx, tz;
        switch (compMode) {
            case WANDER -> { tx = player.getX() + Math.cos(wanderAngle) * wanderRadius; tz = player.getZ() + Math.sin(wanderAngle) * wanderRadius; }
            case SIT_WATCH -> { tx = cx; tz = cz; }
            default -> { tx = player.getX() + rightX * 1.22 - fwdX * 0.65; tz = player.getZ() + rightZ * 1.22 - fwdZ * 0.65; }
        }

        double relX = tx - player.getX(), relZ = tz - player.getZ(), relDist = Math.hypot(relX, relZ);
        if (relDist < 1.05) {
            if (relDist < 1e-4) { tx = player.getX() + rightX * 1.15; tz = player.getZ() + rightZ * 1.15; }
            else { tx = player.getX() + relX / relDist * 1.05; tz = player.getZ() + relZ / relDist * 1.05; }
        }

        double dx = tx - cx, dz = tz - cz, dist = Math.hypot(dx, dz);
        double desiredSpeed = dist < 0.14 ? 0.0 : Math.min(0.38, dist * 0.19);
        cvx += ((dist > 1e-4 ? (dx / dist) * desiredSpeed : 0.0) - cvx) * 0.28;
        cvz += ((dist > 1e-4 ? (dz / dist) * desiredSpeed : 0.0) - cvz) * 0.28;
        double nextX = cx + cvx, nextZ = cz + cvz;
        double pSepX = nextX - player.getX(), pSepZ = nextZ - player.getZ(), pSep = Math.hypot(pSepX, pSepZ);
        if (pSep < 0.90 && pSep > 1e-4) { nextX = player.getX() + pSepX / pSep * 0.90; nextZ = player.getZ() + pSepZ / pSep * 0.90; }
        cx = nextX; cz = nextZ; compSpeed = Math.hypot(cvx, cvz);

        snapCompanionY(findGroundY(level, cx, player.getY(), cz));

        float targetYaw = compSpeed > 0.025 ? (float) Math.toDegrees(Math.atan2(-cvx, cvz))
                : (compMode == CompMode.SIT_WATCH ? (float) Math.toDegrees(Math.atan2(-(player.getX() - cx), player.getZ() - cz)) : player.yBodyRot);
        cYaw += Mth.wrapDegrees(targetYaw - cYaw) * 0.22f;
    }

    private static void tickCompanionAttack(LocalPlayer player, ClientLevel level, Entity target) {
        double dx = target.getX() - cx, dz = target.getZ() - cz, dist = Math.hypot(dx, dz);
        double reach = Math.max(0.65, target.getBbWidth() * 0.55 + 0.42);
        if (!compStruck) {
            double inv = dist > 1e-4 ? 1.0 / dist : 0.0;
            double speed = Math.min(0.72, Math.max(0.28, (dist - reach * 0.6) * 0.55));
            cvx += (dx * inv * speed - cvx) * 0.55; cvz += (dz * inv * speed - cvz) * 0.55;
            cx += cvx; cz += cvz; compSpeed = Math.max(0.24, Math.hypot(cvx, cvz));
            if (dist < reach + 1.4) {
                double frac = Mth.clamp(1.0 - (dist - reach) / 1.4, 0.0, 1.0);
                cHop = Math.sin(frac * Math.PI) * 0.48; cPitch += (24.0f - cPitch) * 0.4f;
            } else { cHop *= 0.6; cPitch += (10.0f - cPitch) * 0.3f; }
            double newDist = Math.hypot(target.getX() - cx, target.getZ() - cz);
            if (newDist <= reach + 0.35 || (compAttackAge >= 11 && newDist <= reach + 1.25)) {
                compStruck = true;
                double rInv = newDist > 1e-4 ? 1.0 / newDist : 1.0;
                cvx = -(target.getX() - cx) * rInv * 0.28; cvz = -(target.getZ() - cz) * rInv * 0.28;
                cHop = 0.28; cPitch = 34.0f;
                spawnAttackSparks(level,
                        target.getX() - (target.getX() - cx) * rInv * (target.getBbWidth() * 0.4),
                        target.getY() + Math.min(0.95, target.getBbHeight() * 0.45),
                        target.getZ() - (target.getZ() - cz) * rInv * (target.getBbWidth() * 0.4), true);
            }
        } else {
            cvx *= 0.72; cvz *= 0.72; cx += cvx; cz += cvz;
            cHop = Math.max(0.0, cHop - 0.11); cPitch += (0.0f - cPitch) * 0.3f; compSpeed = Math.hypot(cvx, cvz);
            if (compAttackAge >= 18) { compMode = CompMode.FOLLOW; compAttackTargetId = -1; compStruck = false; compModeTicks = 50; }
        }
        snapCompanionY(findGroundY(level, cx, Mth.lerp(0.5, player.getY(), target.getY()), cz));
        cYaw += Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-(target.getX() - cx), target.getZ() - cz)) - cYaw) * 0.50f;
    }

    private static void snapCompanionY(double groundY) {
        double dy = groundY - cy;
        if (Math.abs(dy) > 3.5) cy = groundY;
        else if (dy > 0.30) cy += Math.min(dy, 0.32);
        else if (dy < -0.05) cy = Math.max(groundY, cy - Math.max(0.22, -dy * 0.48));
        else cy += dy * 0.55;
    }

    private static void spawnAttackSparks(ClientLevel level, double x, double y, double z, boolean groundStrike) {
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            level.addParticle(i % 2 == 0 ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT, x, y, z,
                    (rng.nextDouble() - 0.5) * 0.22, 0.06 + rng.nextDouble() * 0.14, (rng.nextDouble() - 0.5) * 0.22);
        }
        if (groundStrike) level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0.0, 0.0, 0.0);
    }

    private static double findGroundY(ClientLevel level, double x, double anchorY, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int startY = Mth.floor(anchorY + 1.25);
        int minY = Math.max(level.getMinY(), Mth.floor(anchorY - 3.0));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = startY; y >= minY; y--) {
            pos.set(bx, y, bz);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                if (top <= anchorY + 1.55) return top;
            }
        }
        return anchorY;
    }

    static Snapshot petSnapshot(float partialTick) {
        if (!petInit) return null;
        double x = Mth.lerp(partialTick, prevPx, px), y = Mth.lerp(partialTick, prevPy, py), z = Mth.lerp(partialTick, prevPz, pz);
        float yaw = prevPYaw + Mth.wrapDegrees(pYaw - prevPYaw) * partialTick;
        float pitch = Mth.lerp(partialTick, prevPPitch, pPitch), roll = Mth.lerp(partialTick, prevPRoll, pRoll);
        boolean perched = (petMode == PetMode.PERCH_HEAD || petMode == PetMode.PERCH_SHOULDER_L || petMode == PetMode.PERCH_SHOULDER_R) && petSpeed < 0.05;
        return new Snapshot(x, y, z, yaw, pitch, roll, petSpeed, perched);
    }

    static Snapshot companionSnapshot(float partialTick) {
        if (!compInit) return null;
        double x = Mth.lerp(partialTick, prevCx, cx), y = Mth.lerp(partialTick, prevCy, cy) + Mth.lerp(partialTick, prevCHop, cHop), z = Mth.lerp(partialTick, prevCz, cz);
        float yaw = prevCYaw + Mth.wrapDegrees(cYaw - prevCYaw) * partialTick;
        return new Snapshot(x, y, z, yaw, Mth.lerp(partialTick, prevCPitch, cPitch), 0.0f, compSpeed, compMode == CompMode.SIT_WATCH && compSpeed < 0.02);
    }

    static Snapshot remotePetSnapshot(UUID uuid, float partialTick) {
        RemoteState st = REMOTE_STATES.get(uuid);
        if (st == null || !st.petInit) return null;
        double x = Mth.lerp(partialTick, st.prevPx, st.px), y = Mth.lerp(partialTick, st.prevPy, st.py), z = Mth.lerp(partialTick, st.prevPz, st.pz);
        float yaw = st.prevPYaw + Mth.wrapDegrees(st.pYaw - st.prevPYaw) * partialTick;
        return new Snapshot(x, y, z, yaw, 0.0f, 0.0f, st.petSpeed, false);
    }

    static Snapshot remoteCompanionSnapshot(UUID uuid, float partialTick) {
        RemoteState st = REMOTE_STATES.get(uuid);
        if (st == null || !st.compInit) return null;
        double x = Mth.lerp(partialTick, st.prevCx, st.cx), y = Mth.lerp(partialTick, st.prevCy, st.cy), z = Mth.lerp(partialTick, st.prevCz, st.cz);
        float yaw = st.prevCYaw + Mth.wrapDegrees(st.cYaw - st.prevCYaw) * partialTick;
        return new Snapshot(x, y, z, yaw, 0.0f, 0.0f, st.compSpeed, false);
    }
}

