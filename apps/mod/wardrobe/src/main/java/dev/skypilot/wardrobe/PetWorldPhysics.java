package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Living world-space physics, warp/teleport recovery, 8 signature per-species visual attack
 * choreographies, AND full interactive bonding system (Patting, Treats, 3D Ball Fetch, Tricks,
 * Sit/Stay, Head/Shoulder Perch, Crouch-Greeting, Eye-Contact &amp; Persisted Affection Levels)
 * — 100% client-side visual/audio only, zero game server packets!
 */
final class PetWorldPhysics {
    enum AttackStyle {
        BREATH_FIREBALL,  // Dragons, Phoenix, Wyverns, Demons, Imp, Cerberus, Magma
        CLAW_POUNCE,      // Cats, Foxes, Wolves, Tigers, Lions, Shiba, Ferret, Fenrir
        PHANTOM_BLINK,    // Reaper, Ghost, Wisp, Shadow, Void, Ender, Bat, Raven, Cobra
        ARCANE_BARRAGE,   // Fairy, Bee, Butterfly, Angel, Seraph, Star, Owl, Kitsune, Unicorn
        ORBITAL_LASER,    // Cyber, Mecha, Drone, UFO, Robot, Neon
        HYDRO_SURGE,      // Axolotl, Whale, Shark, Dolphin, Penguin, Duck, Koi, Jelly, Frog
        METEOR_SLAM,      // Slime, Bunny, Corgi, Panda, Capybara, Chick, Lamb, Hamster, Bear, Alpaca
        IAI_DASH_SLASH    // Samurai, Knight, Sword, Blade, Golem, Valkyrie
    }

    enum Action {
        PAT, TREAT, FETCH, TRICK, TOGGLE_STAY, TOGGLE_PERCH
    }

    enum PetMode {
        FOLLOW_SIDE, ORBIT_PLAYFUL, PERCH_SHOULDER_L, PERCH_SHOULDER_R, PERCH_HEAD, CURIOUS_PEEK, ATTACK_TARGET,
        INTERACT_PAT, INTERACT_TREAT, INTERACT_FETCH, INTERACT_TRICK, STAY_WAIT, PERCH_COMMAND
    }

    enum CompMode {
        FOLLOW, WANDER, SIT_WATCH, ATTACK_TARGET,
        INTERACT_PAT, INTERACT_TREAT, INTERACT_FETCH, INTERACT_TRICK, STAY_WAIT, PERCH_SHOULDER
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
    private static int petAttackShots;
    private static boolean petStruck;
    private static int petAttackSide = 1;
    private static AttackStyle petAttackStyle = AttackStyle.CLAW_POUNCE;
    private static String petAttackCosmeticId = "";
    private static int petInteractTicks;
    private static double petStayX, petStayY, petStayZ;

    // ---- Local Ground Companion State ----
    private static boolean compInit;
    private static double cx, cy, cz, prevCx, prevCy, prevCz;
    private static double cvx, cvz;
    private static float cYaw, prevCYaw, cPitch, prevCPitch, cRoll, prevCRoll;
    private static double cHop, prevCHop;
    private static CompMode compMode = CompMode.FOLLOW;
    private static int compModeTicks;
    private static double wanderAngle = -0.85;
    private static double wanderRadius = 1.45;
    private static double compSpeed;
    private static int compAttackTargetId = -1;
    private static int compAttackAge;
    private static boolean compStruck;
    private static int compAttackSide = 1;
    private static AttackStyle compAttackStyle = AttackStyle.CLAW_POUNCE;
    private static String compAttackCosmeticId = "";
    private static int compInteractTicks;
    private static double compStayX, compStayZ;

    // ---- Interactive Play Objects (Treat & 3D Bouncing Fetch Ball) ----
    private static boolean stayCommanded;
    private static boolean perchCommanded;
    private static boolean treatActive;
    private static double treatX, treatY, treatZ;
    private static int treatTicks;

    private static boolean ballActive;
    private static int ballCarriedBy; // 0 = loose/bouncing, 1 = pet carrying, 2 = companion carrying
    private static double ballX, ballY, ballZ, prevBallX, prevBallY, prevBallZ;
    private static double ballVx, ballVy, ballVz;
    private static int ballTicks;

    // ---- Natural Reactive Behavior (Crouch Greet, Eye Contact, Bond Cooldown) ----
    private static boolean wasCrouching;
    private static long lastCrouchEdgeMs;
    private static long lastCrouchGreetMs;
    private static long lastBondAwardMs;
    private static long lastDirectClickMs;
    private static int gazeTicks;

    private PetWorldPhysics() {}

    static AttackStyle attackStyleFor(String cosmeticId) {
        if (cosmeticId == null) return AttackStyle.CLAW_POUNCE;
        String s = cosmeticId.toLowerCase(Locale.ROOT);
        if (s.contains("cyber") || s.contains("mech") || s.contains("drone") || s.contains("ufo")
                || s.contains("robot") || s.contains("neon") || s.contains("holo")) {
            return AttackStyle.ORBITAL_LASER;
        }
        if (s.contains("dragon") || s.contains("wyvern") || s.contains("phoenix") || s.contains("drake")
                || s.contains("demon") || s.contains("imp") || s.contains("cerberus") || s.contains("magma")
                || s.contains("blaze") || s.contains("inferno") || s.contains("flame") || s.contains("crimson")) {
            return AttackStyle.BREATH_FIREBALL;
        }
        if (s.contains("reaper") || s.contains("ghost") || s.contains("wisp") || s.contains("shadow")
                || s.contains("void") || s.contains("ender") || s.contains("phantom") || s.contains("bat")
                || s.contains("raven") || s.contains("cobra") || s.contains("chomp") || s.contains("skull")) {
            return AttackStyle.PHANTOM_BLINK;
        }
        if (s.contains("axolotl") || s.contains("whale") || s.contains("shark") || s.contains("dolphin")
                || s.contains("penguin") || s.contains("duck") || s.contains("koi") || s.contains("jelly")
                || s.contains("frog") || s.contains("otter") || s.contains("hydro") || s.contains("tide") || s.contains("ocean")) {
            return AttackStyle.HYDRO_SURGE;
        }
        if (s.contains("samurai") || s.contains("knight") || s.contains("sword") || s.contains("katana")
                || s.contains("blade") || s.contains("golem") || s.contains("valkyrie")) {
            return AttackStyle.IAI_DASH_SLASH;
        }
        if (s.contains("fairy") || s.contains("bee") || s.contains("butterfly") || s.contains("angel")
                || s.contains("seraph") || s.contains("star") || s.contains("owl") || s.contains("kitsune")
                || s.contains("unicorn") || s.contains("spirit") || s.contains("cosmic") || s.contains("astral")
                || s.contains("moon") || s.contains("sun") || s.contains("lantern")) {
            return AttackStyle.ARCANE_BARRAGE;
        }
        if (s.contains("slime") || s.contains("bunny") || s.contains("rabbit") || s.contains("corgi")
                || s.contains("panda") || s.contains("capybara") || s.contains("chick") || s.contains("lamb")
                || s.contains("sheep") || s.contains("hamster") || s.contains("bear") || s.contains("alpaca")
                || s.contains("mochi") || s.contains("strawberry") || s.contains("monkey") || s.contains("pig")) {
            return AttackStyle.METEOR_SLAM;
        }
        return AttackStyle.CLAW_POUNCE;
    }

    private static int[] attackColors(String cosmeticId, AttackStyle style) {
        var item = CosmeticCatalog.byId(cosmeticId);
        int base = item != null ? (item.color() & 0xFFFFFF) : 0x55D6D2;
        return switch (style) {
            case BREATH_FIREBALL -> new int[]{base == 0 ? 0xF97316 : base, 0xFEF08A};
            case ORBITAL_LASER -> new int[]{0x22D3EE, 0xF472B6};
            case HYDRO_SURGE -> new int[]{0x38BDF8, 0xBAE6FD};
            case PHANTOM_BLINK -> new int[]{0xA855F7, 0xF0ABFC};
            case ARCANE_BARRAGE -> new int[]{base, 0xFEF08A};
            case METEOR_SLAM -> new int[]{base, 0xFBCFE8};
            case IAI_DASH_SLASH -> new int[]{0xE0F2FE, base};
            default -> new int[]{base, 0xFFFFFF};
        };
    }

    private static void playSpeciesSound(ClientLevel level, double x, double y, double z, String cosmeticId, float vol, float pitchBase) {
        if (level == null) return;
        String s = cosmeticId == null ? "" : cosmeticId.toLowerCase(Locale.ROOT);
        SoundEvent sound;
        float pitch = pitchBase;
        if (s.contains("cat") || s.contains("kitty") || s.contains("mochi") || s.contains("calico") || s.contains("tiger") || s.contains("lion")) {
            sound = SoundEvents.CAT_PURREOW_BABY.value();
            pitch = pitchBase * 1.05f;
        } else if (s.contains("axolotl") || s.contains("whale") || s.contains("shark") || s.contains("dolphin") || s.contains("koi") || s.contains("otter")) {
            sound = SoundEvents.AXOLOTL_IDLE_AIR;
            pitch = pitchBase * 1.12f;
        } else if (s.contains("wolf") || s.contains("dog") || s.contains("shiba") || s.contains("corgi") || s.contains("fenrir") || s.contains("cerberus")) {
            sound = SoundEvents.WOLF_AMBIENT_BABY.value();
            pitch = pitchBase * 1.08f;
        } else if (s.contains("fox") || s.contains("kitsune") || s.contains("ferret")) {
            sound = SoundEvents.FOX_AMBIENT;
            pitch = pitchBase * 1.18f;
        } else if (s.contains("slime") || s.contains("jelly")) {
            sound = SoundEvents.SLIME_SQUISH_SMALL;
            pitch = pitchBase * 1.25f;
        } else if (s.contains("owl") || s.contains("bird") || s.contains("chick") || s.contains("duck") || s.contains("penguin") || s.contains("raven") || s.contains("phoenix")) {
            sound = SoundEvents.PARROT_AMBIENT;
            pitch = pitchBase * 1.15f;
        } else {
            sound = SoundEvents.ALLAY_AMBIENT_WITH_ITEM;
            pitch = pitchBase * 1.10f;
        }
        level.playLocalSound(x, y, z, sound, SoundSource.NEUTRAL, vol, pitch, false);
    }

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
        cRoll = prevCRoll = 0.0f;
        treatActive = false;
        ballActive = false;
        ballCarriedBy = 0;
        stayCommanded = false;
        perchCommanded = false;
        petInteractTicks = 0;
        compInteractTicks = 0;
        gazeTicks = 0;
        REMOTE_STATES.clear();
    }

    // ---------------------------------------------------------------- Public Pet Interaction API

    static boolean isStaying() {
        return stayCommanded;
    }

    static boolean isPerchCommanded() {
        return perchCommanded;
    }

    static String currentMoodLabel(boolean ja) {
        if (ballActive) return ja ? "🎾 ボールを追いかけ中！" : "🎾 Playing fetch!";
        if (treatActive) return ja ? "🍪 おやつに夢中！" : "🍪 Catching a treat!";
        if (petMode == PetMode.INTERACT_PAT || compMode == CompMode.INTERACT_PAT) return ja ? "♥ なでられてご機嫌！" : "♥ Loving the pats!";
        if (petMode == PetMode.INTERACT_TRICK || compMode == CompMode.INTERACT_TRICK) return ja ? "✨ とくぎ披露中！" : "✨ Performing a trick!";
        if (stayCommanded) return ja ? "🪑 おすわり待機中" : "🪑 Sitting & waiting";
        if (perchCommanded) return ja ? "🤗 頭・肩でくつろぎ中" : "🤗 Snuggling on perch";
        return ja ? "🐾 いっしょにお散歩中" : "🐾 Happily following you";
    }

    /** Ray-sphere hit test checking if player is looking directly at their floating pet or companion. */
    static String aimedPetSlot(LocalPlayer player, double maxDist) {
        if (player == null) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double bestT = maxDist + 1.0;
        String best = null;
        if (petInit) {
            double t = raySphereHit(eye, dir, new Vec3(px, py, pz), 0.62, maxDist);
            if (t >= 0 && t < bestT) { bestT = t; best = "pet"; }
        }
        if (compInit) {
            double t = raySphereHit(eye, dir, new Vec3(cx, cy + cHop + 0.42, cz), 0.68, maxDist);
            if (t >= 0 && t < bestT) { bestT = t; best = "companion"; }
        }
        return best;
    }

    private static double raySphereHit(Vec3 ro, Vec3 rd, Vec3 center, double radius, double maxDist) {
        Vec3 oc = center.subtract(ro);
        double proj = oc.dot(rd);
        if (proj < 0.0 || proj > maxDist) return -1.0;
        double distSq = oc.lengthSqr() - proj * proj;
        return distSq <= radius * radius ? proj : -1.0;
    }

    /** Called on empty-hand right-click: if aiming at your pet or companion, pets them immediately! */
    static boolean tryDirectPetClick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastDirectClickMs < 320L) return false;
        String slot = aimedPetSlot(player, 4.4);
        if (slot == null) return false;
        lastDirectClickMs = now;
        return performAction(Action.PAT, slot);
    }

    /**
     * Performs an interactive action with the player's equipped Pet and/or Companion.
     *
     * @param action           One of the 6 bonding actions
     * @param targetSlotFilter {@code "pet"}, {@code "companion"}, or {@code null} for both
     * @return true if at least one pet/companion responded
     */
    static boolean performAction(Action action, String targetSlotFilter) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (player == null || level == null || rt == null) return false;

        SkyPilotConfig.Appearance a = rt.appearance();
        boolean ja = rt.japanese();
        boolean usePet = !"none".equals(a.pet) && (targetSlotFilter == null || "pet".equals(targetSlotFilter));
        boolean useComp = !"none".equals(a.companion) && (targetSlotFilter == null || "companion".equals(targetSlotFilter));

        if (!usePet && !useComp) {
            mc.gui.hud.setOverlayMessage(Component.literal(
                    ja ? "ペットまたは相棒を装備してから触れ合えます (Kキーで装備)"
                            : "Equip a Pet or Companion first (press K)"), false);
            return false;
        }

        // Ensure initialized right near player if invoked right after equipping
        if (usePet && !petInit) {
            px = prevPx = player.getX() + 0.85; py = prevPy = player.getY() + 1.5; pz = prevPz = player.getZ();
            petInit = true;
        }
        if (useComp && !compInit) {
            cx = prevCx = player.getX() - 0.95; cz = prevCz = player.getZ();
            cy = prevCy = findGroundY(level, cx, player.getY(), cz);
            compInit = true;
        }

        String primaryId = usePet ? a.pet : a.companion;
        var primaryItem = CosmeticCatalog.byId(primaryId);
        String petLabel = primaryItem != null ? primaryItem.label(ja) : (ja ? "ペット" : "Pet");
        int baseColor = primaryItem != null ? (primaryItem.color() & 0xFFFFFF) : 0xF472B6;
        if (usePet && useComp && targetSlotFilter == null) {
            petLabel = ja ? "ペット＆相棒" : "Pet & Companion";
        }

        long now = System.currentTimeMillis();
        boolean canAwardBond = (now - lastBondAwardMs >= 1600L);

        switch (action) {
            case PAT -> {
                if (usePet) {
                    petMode = PetMode.INTERACT_PAT;
                    petInteractTicks = 36;
                    WardrobeVfxEngine.spawnPetHearts(new Vec3(px, py + 0.25, pz), 8, 0xF472B6, 0xFDE047);
                    playSpeciesSound(level, px, py, pz, a.pet, 0.72f, 1.08f);
                }
                if (useComp) {
                    compMode = CompMode.INTERACT_PAT;
                    compInteractTicks = 36;
                    cHop = Math.max(cHop, 0.34);
                    WardrobeVfxEngine.spawnPetHearts(new Vec3(cx, cy + 0.55, cz), 8, 0xF472B6, 0x67E8F9);
                    playSpeciesSound(level, cx, cy + 0.5, cz, a.companion, 0.72f, 1.05f);
                }
                level.playLocalSound(player.getX(), player.getY() + 1.2, player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.45f, 1.35f, false);
                int lv = awardBond(rt, a, usePet, useComp, canAwardBond ? 2 : 0);
                int pts = a.bondOf(primaryId);
                mc.gui.hud.setOverlayMessage(Component.literal(
                        ja ? "♥ " + petLabel + "をなでた！ (なつき度 Lv." + lv + " · ♥" + pts + ")"
                                : "♥ You patted " + petLabel + "! (Bond Lv." + lv + " · ♥" + pts + ")"), false);
            }
            case TREAT -> {
                stayCommanded = false;
                perchCommanded = false;
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                treatX = eye.x + look.x * 1.45;
                treatY = Mth.clamp(eye.y + look.y * 1.10 - 0.08, player.getY() + 0.65, player.getY() + 2.15);
                treatZ = eye.z + look.z * 1.45;
                treatActive = true;
                treatTicks = 44;
                if (usePet) { petMode = PetMode.INTERACT_TREAT; petInteractTicks = 46; }
                if (useComp) { compMode = CompMode.INTERACT_TREAT; compInteractTicks = 46; }
                WardrobeVfxEngine.spawnTreatVisual(new Vec3(treatX, treatY, treatZ), false);
                level.playLocalSound(treatX, treatY, treatZ, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6f, 1.4f, false);
                int lv = awardBond(rt, a, usePet, useComp, canAwardBond ? 4 : 0);
                mc.gui.hud.setOverlayMessage(Component.literal(
                        ja ? "🍪 " + petLabel + "におやつをあげた！ (なつき度 Lv." + lv + ")"
                                : "🍪 You offered a treat to " + petLabel + "! (Bond Lv." + lv + ")"), false);
            }
            case FETCH -> {
                stayCommanded = false;
                perchCommanded = false;
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle().normalize();
                ballX = prevBallX = eye.x + look.x * 0.7;
                ballY = prevBallY = eye.y - 0.15;
                ballZ = prevBallZ = eye.z + look.z * 0.7;
                ballVx = look.x * 0.56;
                ballVy = Math.max(0.22, look.y * 0.42 + 0.24);
                ballVz = look.z * 0.56;
                ballCarriedBy = 0;
                ballActive = true;
                ballTicks = 130;
                if (usePet) { petMode = PetMode.INTERACT_FETCH; petInteractTicks = 130; }
                if (useComp) { compMode = CompMode.INTERACT_FETCH; compInteractTicks = 130; }
                level.playLocalSound(player.getX(), player.getY() + 1.2, player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.55f, 1.3f, false);
                mc.gui.hud.setOverlayMessage(Component.literal(
                        ja ? "🎾 ボールを投げた！ " + petLabel + "が取りに行きます！"
                                : "🎾 Ball tossed! " + petLabel + " is racing to fetch it!"), false);
            }
            case TRICK -> {
                stayCommanded = false;
                perchCommanded = false;
                if (usePet) {
                    petMode = PetMode.INTERACT_TRICK;
                    petInteractTicks = 42;
                    WardrobeVfxEngine.spawnTrickShow(new Vec3(px, py, pz), baseColor, 0xFDE047);
                    playSpeciesSound(level, px, py, pz, a.pet, 0.75f, 1.22f);
                }
                if (useComp) {
                    compMode = CompMode.INTERACT_TRICK;
                    compInteractTicks = 42;
                    WardrobeVfxEngine.spawnTrickShow(new Vec3(cx, cy + 0.45, cz), baseColor, 0x67E8F9);
                    playSpeciesSound(level, cx, cy + 0.5, cz, a.companion, 0.75f, 1.18f);
                }
                level.playLocalSound(player.getX(), player.getY() + 1.2, player.getZ(),
                        SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.65f, 1.25f, false);
                int lv = awardBond(rt, a, usePet, useComp, canAwardBond ? 3 : 0);
                mc.gui.hud.setOverlayMessage(Component.literal(
                        ja ? "✨ " + petLabel + "がクルッととくぎを披露！ (なつき度 Lv." + lv + ")"
                                : "✨ " + petLabel + " performed a dazzling trick! (Bond Lv." + lv + ")"), false);
            }
            case TOGGLE_STAY -> {
                stayCommanded = !stayCommanded;
                if (stayCommanded) {
                    perchCommanded = false;
                    if (usePet) {
                        petStayX = px; petStayY = py; petStayZ = pz;
                        petMode = PetMode.STAY_WAIT;
                    }
                    if (useComp) {
                        compStayX = cx; compStayZ = cz;
                        compMode = CompMode.STAY_WAIT;
                    }
                    level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(),
                            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.5f, 0.85f, false);
                    mc.gui.hud.setOverlayMessage(Component.literal(
                            ja ? "🪑 " + petLabel + "はおすわりして待っています"
                                    : "🪑 " + petLabel + " is sitting and waiting"), false);
                } else {
                    if (usePet) { petMode = PetMode.FOLLOW_SIDE; petModeTicks = 70; }
                    if (useComp) { compMode = CompMode.FOLLOW; compModeTicks = 70; cHop = 0.28; }
                    playSpeciesSound(level, player.getX(), player.getY() + 1.2, player.getZ(), primaryId, 0.65f, 1.15f);
                    mc.gui.hud.setOverlayMessage(Component.literal(
                            ja ? "🐾 " + petLabel + "が元気に戻ってきました！"
                                    : "🐾 " + petLabel + " came running back!"), false);
                }
            }
            case TOGGLE_PERCH -> {
                perchCommanded = !perchCommanded;
                if (perchCommanded) {
                    stayCommanded = false;
                    if (usePet) petMode = PetMode.PERCH_COMMAND;
                    if (useComp) compMode = CompMode.PERCH_SHOULDER;
                    playSpeciesSound(level, player.getX(), player.getY() + 1.5, player.getZ(), primaryId, 0.7f, 1.18f);
                    WardrobeVfxEngine.spawnPetHearts(player.position().add(0, 1.8, 0), 6, 0xF472B6, 0x67E8F9);
                    mc.gui.hud.setOverlayMessage(Component.literal(
                            ja ? "🤗 " + petLabel + "があなたの頭・肩にちょこんと乗りました！"
                                    : "🤗 " + petLabel + " hopped happily onto your perch!"), false);
                } else {
                    if (usePet) { petMode = PetMode.FOLLOW_SIDE; petModeTicks = 70; }
                    if (useComp) { compMode = CompMode.FOLLOW; compModeTicks = 70; }
                    mc.gui.hud.setOverlayMessage(Component.literal(
                            ja ? "🐾 " + petLabel + "が軽やかに降りました"
                                    : "🐾 " + petLabel + " hopped back down"), false);
                }
            }
        }
        return true;
    }

    private static int awardBond(WardrobeRuntime rt, SkyPilotConfig.Appearance a, boolean usePet, boolean useComp, int delta) {
        if (delta > 0) {
            lastBondAwardMs = System.currentTimeMillis();
            if (usePet) a.addBond(a.pet, delta);
            if (useComp) a.addBond(a.companion, delta);
            rt.save();
        }
        String key = usePet ? a.pet : a.companion;
        return SkyPilotConfig.Appearance.bondLevel(a.bondOf(key));
    }

    // ---------------------------------------------------------------- Attack Hook

    static void onPlayerAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (player == null || mc.level == null || rt == null) return;
        if (!isValidAttackTarget(player, target)) return;

        SkyPilotConfig.Appearance a = rt.appearance();
        lastAttackTriggerTick = player.tickCount;

        if (!"none".equals(a.pet) && petInit && !stayCommanded) {
            petAttackTargetId = target.getId();
            petMode = PetMode.ATTACK_TARGET;
            petAttackAge = 0;
            petAttackShots = 0;
            petStruck = false;
            petAttackSide = -petAttackSide;
            petAttackCosmeticId = a.pet;
            petAttackStyle = attackStyleFor(a.pet);
            petModeTicks = 24;
        }
        if (!"none".equals(a.companion) && compInit && !stayCommanded) {
            compAttackTargetId = target.getId();
            compMode = CompMode.ATTACK_TARGET;
            compAttackAge = 0;
            compStruck = false;
            compAttackSide = -compAttackSide;
            compAttackCosmeticId = a.companion;
            compAttackStyle = attackStyleFor(a.companion);
            compModeTicks = 28;
        }
    }

    private static boolean isValidAttackTarget(LocalPlayer player, Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (le == player || !le.isAlive() || le.isRemoved()) return false;
        if (le instanceof ArmorStand) return false;
        if (le.isInvisibleTo(player)) return false;
        return le.distanceToSqr(player) <= 18.0 * 18.0;
    }

    // ---------------------------------------------------------------- Tick Loop

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
            treatActive = false;
            ballActive = false;
            ballCarriedBy = 0;
            stayCommanded = false;
            cHop = prevCHop = 0.0;
            cPitch = prevCPitch = 0.0f;
            cRoll = prevCRoll = 0.0f;
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

        // Natural Crouch-Greeting (double-tap Shift near pet/companion -> happy greeting hop & heart!)
        boolean crouchingNow = player.isCrouching();
        if (crouchingNow && !wasCrouching && (!"none".equals(a.pet) || !"none".equals(a.companion))) {
            long now = System.currentTimeMillis();
            if (now - lastCrouchEdgeMs < 650L && now - lastCrouchGreetMs > 2800L) {
                lastCrouchGreetMs = now;
                if (petInit && petMode != PetMode.ATTACK_TARGET) {
                    petMode = PetMode.INTERACT_PAT;
                    petInteractTicks = 24;
                    WardrobeVfxEngine.spawnPetHearts(new Vec3(px, py + 0.25, pz), 4, 0xF472B6, 0xFDE047);
                    playSpeciesSound(level, px, py, pz, a.pet, 0.55f, 1.22f);
                }
                if (compInit && compMode != CompMode.ATTACK_TARGET) {
                    compMode = CompMode.INTERACT_PAT;
                    compInteractTicks = 24;
                    cHop = 0.36;
                    WardrobeVfxEngine.spawnPetHearts(new Vec3(cx, cy + 0.55, cz), 4, 0xF472B6, 0x67E8F9);
                    playSpeciesSound(level, cx, cy + 0.5, cz, a.companion, 0.55f, 1.18f);
                }
            }
            lastCrouchEdgeMs = now;
        }
        wasCrouching = crouchingNow;

        // Track eye-contact curiosity when looking at pet/companion while standing still
        if (aimedPetSlot(player, 4.5) != null && Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z) < 0.05) {
            gazeTicks = Math.min(80, gazeTicks + 1);
        } else {
            gazeTicks = 0;
        }

        // Advance floating treat & bouncing fetch toy physics
        tickInteractiveObjects(mc, player, level, rt, a);

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
            tickCompanion(player, level, a, firstPerson);
        } else {
            compInit = false;
            compAttackTargetId = -1;
        }

        if (a.showOtherPlayers && a.otherPlayerPets) {
            tickRemotePlayers(player, level);
        } else if (!REMOTE_STATES.isEmpty()) {
            REMOTE_STATES.clear();
        }
    }

    private static void tickInteractiveObjects(Minecraft mc, LocalPlayer player, ClientLevel level, WardrobeRuntime rt, SkyPilotConfig.Appearance a) {
        if (treatActive) {
            treatTicks--;
            treatY += Math.sin(player.tickCount * 0.25) * 0.012;
            WardrobeVfxEngine.spawnTreatVisual(new Vec3(treatX, treatY, treatZ), false);
            boolean petAte = petInit && Math.sqrt(Math.pow(px - treatX, 2) + Math.pow(py - treatY, 2) + Math.pow(pz - treatZ, 2)) < 0.52;
            boolean compAte = compInit && Math.hypot(cx - treatX, cz - treatZ) < 0.68 && treatTicks < 34;
            if (petAte || compAte || treatTicks <= 0) {
                treatActive = false;
                WardrobeVfxEngine.spawnTreatVisual(new Vec3(treatX, treatY, treatZ), true);
                level.playLocalSound(treatX, treatY, treatZ, SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.78f, 1.15f, false);
                level.playLocalSound(treatX, treatY, treatZ, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.45f, false);
                if (petInit) { petMode = PetMode.INTERACT_PAT; petInteractTicks = 26; }
                if (compInit) { compMode = CompMode.INTERACT_PAT; compInteractTicks = 26; cHop = 0.42; }
            }
        }

        if (ballActive) {
            ballTicks--;
            prevBallX = ballX; prevBallY = ballY; prevBallZ = ballZ;
            if (ballCarriedBy == 0) {
                ballVy -= 0.045;
                ballX += ballVx;
                ballY += ballVy;
                ballZ += ballVz;
                double floorY = findGroundY(level, ballX, ballY, ballZ) + 0.16;
                if (ballY < floorY) {
                    ballY = floorY;
                    ballVy = Math.abs(ballVy) * 0.56;
                    if (ballVy < 0.06) ballVy = 0.0;
                    ballVx *= 0.74;
                    ballVz *= 0.74;
                } else {
                    ballVx *= 0.985;
                    ballVz *= 0.985;
                }
                // Check pickup by Pet or Companion
                if (ballTicks < 122) {
                    if (petInit && Math.sqrt(Math.pow(px - ballX, 2) + Math.pow(py - ballY, 2) + Math.pow(pz - ballZ, 2)) < 0.72) {
                        ballCarriedBy = 1;
                        playSpeciesSound(level, px, py, pz, a.pet, 0.72f, 1.25f);
                        WardrobeVfxEngine.spawnPetHearts(new Vec3(ballX, ballY + 0.2, ballZ), 5, 0x38BDF8, 0xFDE047);
                    } else if (compInit && Math.hypot(cx - ballX, cz - ballZ) < 0.85 && ballY <= cy + 1.25) {
                        ballCarriedBy = 2;
                        playSpeciesSound(level, cx, cy + 0.5, cz, a.companion, 0.72f, 1.20f);
                        WardrobeVfxEngine.spawnPetHearts(new Vec3(ballX, ballY + 0.2, ballZ), 5, 0x38BDF8, 0xFDE047);
                    }
                }
            } else if (ballCarriedBy == 1 && petInit) {
                ballX = px; ballY = py - 0.18; ballZ = pz;
            } else if (ballCarriedBy == 2 && compInit) {
                ballX = cx; ballY = cy + cHop + 0.42; ballZ = cz;
            }

            WardrobeVfxEngine.spawnToyBallFrame(new Vec3(ballX, ballY, ballZ), new Vec3(prevBallX, prevBallY, prevBallZ), 0x38BDF8, 0xF472B6);

            // Delivered back to player!
            boolean delivered = ballCarriedBy != 0 && Math.hypot(ballX - player.getX(), ballZ - player.getZ()) < 1.65;
            if (delivered || ballTicks <= 0) {
                ballActive = false;
                if (delivered) {
                    boolean ja = rt.japanese();
                    WardrobeVfxEngine.spawnTrickShow(new Vec3(ballX, ballY + 0.2, ballZ), 0x38BDF8, 0xF472B6);
                    level.playLocalSound(player.getX(), player.getY() + 1.2, player.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.7f, 1.35f, false);
                    int lv = awardBond(rt, a, !"none".equals(a.pet), !"none".equals(a.companion), 5);
                    mc.gui.hud.setOverlayMessage(Component.literal(
                            ja ? "🎾 ナイスキャッチ！ ボールを持ってきてくれた！ (+5♥ · Lv." + lv + ")"
                                    : "🎾 Good fetch! Brought the ball right back! (+5♥ · Lv." + lv + ")"), false);
                }
                ballCarriedBy = 0;
                if (petInit && petMode == PetMode.INTERACT_FETCH) { petMode = PetMode.INTERACT_PAT; petInteractTicks = 24; }
                if (compInit && compMode == CompMode.INTERACT_FETCH) { compMode = CompMode.INTERACT_PAT; compInteractTicks = 24; cHop = 0.42; }
            }
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
                    st.pYaw = st.prevPYaw = remote.yBodyRot; st.petInit = true;
                } else {
                    st.prevPx = st.px; st.prevPy = st.py; st.prevPz = st.pz; st.prevPYaw = st.pYaw;
                    double vx = (tx - st.px) * 0.22, vy = (ty - st.py) * 0.22, vz = (tz - st.pz) * 0.22;
                    st.px += vx; st.py += vy; st.pz += vz;
                    st.petSpeed = Math.hypot(vx, vz);
                    float targetYaw = st.petSpeed > 0.03 ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : remote.yBodyRot;
                    st.pYaw += Mth.wrapDegrees(targetYaw - st.pYaw) * 0.24f;
                }
            } else st.petInit = false;

            if (hasComp) {
                double tx = remote.getX() - leftX * 1.22 - fwdX * 0.62;
                double tz = remote.getZ() - leftZ * 1.22 - fwdZ * 0.62;
                double groundY = findGroundY(level, tx, remote.getY(), tz);
                if (!st.compInit || Math.hypot(st.cx - remote.getX(), st.cz - remote.getZ()) > 12.0 || Math.abs(st.cy - groundY) > 4.5) {
                    st.cx = st.prevCx = tx; st.cy = st.prevCy = groundY; st.cz = st.prevCz = tz;
                    st.cYaw = st.prevCYaw = remote.yBodyRot; st.compInit = true;
                } else {
                    st.prevCx = st.cx; st.prevCy = st.cy; st.prevCz = st.cz; st.prevCYaw = st.cYaw;
                    double vx = (tx - st.cx) * 0.24, vz = (tz - st.cz) * 0.24;
                    st.cx += vx; st.cz += vz;
                    st.cy += (groundY - st.cy) * 0.45;
                    st.compSpeed = Math.hypot(vx, vz);
                    float targetYaw = st.compSpeed > 0.025 ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : remote.yBodyRot;
                    st.cYaw += Mth.wrapDegrees(targetYaw - st.cYaw) * 0.24f;
                }
            } else st.compInit = false;
        }
        if (nowTick % 40 == 0) REMOTE_STATES.entrySet().removeIf(e -> nowTick - e.getValue().lastSeenTick > 60);
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

    // ---------------------------------------------------------------- Floating Pet AI & Interactive Modes

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

        boolean wideRange = petMode == PetMode.ATTACK_TARGET || petMode == PetMode.INTERACT_FETCH || petMode == PetMode.STAY_WAIT;
        if (!petInit || Math.hypot(px - player.getX(), pz - player.getZ()) > (wideRange ? 24.0 : 13.0)
                || Math.abs(py - (player.getY() + 1.5)) > (wideRange ? 12.0 : 5.5)) {
            px = prevPx = player.getX() + leftX * 0.95 - fwdX * 0.35;
            py = prevPy = player.getY() + 1.55;
            pz = prevPz = player.getZ() + leftZ * 0.95 - fwdZ * 0.35;
            pvx = pvy = pvz = 0;
            pYaw = prevPYaw = player.yBodyRot; pPitch = prevPPitch = 0; pRoll = prevPRoll = 0;
            petMode = perchCommanded ? PetMode.PERCH_COMMAND : PetMode.FOLLOW_SIDE;
            stayCommanded = false;
            petAttackTargetId = -1; petStruck = false; petModeTicks = 90; petInit = true;
            return;
        }

        prevPx = px; prevPy = py; prevPz = pz;
        prevPYaw = pYaw; prevPPitch = pPitch; prevPRoll = pRoll;

        if (petMode == PetMode.ATTACK_TARGET) {
            Entity target = petAttackTargetId >= 0 ? level.getEntity(petAttackTargetId) : null;
            petAttackAge++;
            if (target == null || !isValidAttackTarget(player, target) || petAttackAge > 22) {
                petMode = perchCommanded ? PetMode.PERCH_COMMAND : PetMode.FOLLOW_SIDE;
                petAttackTargetId = -1; petStruck = false; petModeTicks = 65;
            } else {
                tickPetSignatureAttack(player, target);
                return;
            }
        }

        // ---- Interactive Pet Modes (Pat, Treat, Fetch, Trick, Stay, Commanded Perch) ----
        double t = player.tickCount * 0.05;
        double crouchDrop = player.isCrouching() ? 0.25 : 0.0;
        double headTopY = player.getY() + 2.02 - crouchDrop + (hasHelmet ? 0.10 : 0.0) + (hasHat ? 0.18 : 0.0);

        if (petMode == PetMode.INTERACT_PAT) {
            petInteractTicks--;
            double tx = player.getX() + lookFwdX * 1.12 + lookLeftX * (0.28 * petSide);
            double tz = player.getZ() + lookFwdZ * 1.12 + lookLeftZ * (0.28 * petSide);
            double ty = player.getEyeY() - 0.14 + Math.abs(Math.sin(petInteractTicks * 0.45)) * 0.22;
            px += (tx - px) * 0.24; py += (ty - py) * 0.26; pz += (tz - pz) * 0.24;
            float facePlayer = (float) Math.toDegrees(Math.atan2(-(player.getX() - px), player.getZ() - pz));
            pYaw += Mth.wrapDegrees(facePlayer - pYaw) * 0.35f;
            pPitch = (float) Math.sin(petInteractTicks * 0.55) * 14.0f;
            pRoll = (float) Math.sin(petInteractTicks * 0.72) * 24.0f; // Adorable happy head-wiggle!
            petSpeed = 0.08;
            if (petInteractTicks % 10 == 0 && petInteractTicks > 4) {
                WardrobeVfxEngine.spawnPetHearts(new Vec3(px, py + 0.24, pz), 2, 0xF472B6, 0xFDE047);
            }
            if (petInteractTicks <= 0) {
                petMode = stayCommanded ? PetMode.STAY_WAIT : (perchCommanded ? PetMode.PERCH_COMMAND : PetMode.FOLLOW_SIDE);
                petModeTicks = 75;
            }
            return;
        }

        if (petMode == PetMode.INTERACT_TREAT) {
            if (!treatActive || --petInteractTicks <= 0) {
                petMode = PetMode.INTERACT_PAT; petInteractTicks = 20;
            } else {
                px += (treatX - px) * 0.32;
                py += (treatY - py) * 0.32;
                pz += (treatZ - pz) * 0.32;
                float toTreat = (float) Math.toDegrees(Math.atan2(-(treatX - px), treatZ - pz));
                pYaw += Mth.wrapDegrees(toTreat - pYaw) * 0.45f;
                pPitch = 16.0f;
                pRoll = (float) Math.sin(petInteractTicks * 0.8) * 18.0f;
                petSpeed = 0.30;
            }
            return;
        }

        if (petMode == PetMode.INTERACT_FETCH) {
            if (!ballActive || --petInteractTicks <= 0) {
                petMode = PetMode.FOLLOW_SIDE; petModeTicks = 65;
            } else {
                double tx = (ballCarriedBy == 1) ? (player.getX() + lookFwdX * 1.10) : ballX;
                double ty = (ballCarriedBy == 1) ? (player.getEyeY() - 0.12) : (ballY + 0.18);
                double tz = (ballCarriedBy == 1) ? (player.getZ() + lookFwdZ * 1.10) : ballZ;
                double dx = tx - px, dy = ty - py, dz = tz - pz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double spd = Math.min(0.62, Math.max(0.24, dist * 0.34));
                if (dist > 1e-4) {
                    px += (dx / dist) * spd; py += (dy / dist) * spd; pz += (dz / dist) * spd;
                }
                float aim = (float) Math.toDegrees(Math.atan2(-dx, dz));
                pYaw += Mth.wrapDegrees(aim - pYaw) * 0.45f;
                pPitch = 20.0f; pRoll = (float) Math.sin(player.tickCount * 0.5) * 20.0f;
                petSpeed = spd;
            }
            return;
        }

        if (petMode == PetMode.INTERACT_TRICK) {
            petInteractTicks--;
            double p = 1.0 - (petInteractTicks / 42.0);
            double ang = p * Math.PI * 2.0;
            double cx0 = player.getX() + lookFwdX * 1.45;
            double cz0 = player.getZ() + lookFwdZ * 1.45;
            double tx = cx0 + Math.cos(ang) * 0.55;
            double tz = cz0 + Math.sin(ang) * 0.55;
            double ty = player.getEyeY() - 0.05 + Math.sin(p * Math.PI) * 0.72;
            px += (tx - px) * 0.35; py += (ty - py) * 0.35; pz += (tz - pz) * 0.35;
            pYaw = (float) ((p * 720.0) % 360.0); // Joyful double-spin!
            pPitch = (float) (Math.sin(p * Math.PI * 2.0) * 38.0);
            pRoll = (float) (p * 360.0); // Full acrobatic barrel roll!
            petSpeed = 0.25;
            if (petInteractTicks % 7 == 0) {
                WardrobeVfxEngine.spawnPetHearts(new Vec3(px, py, pz), 2, 0xFDE047, 0xF472B6);
            }
            if (petInteractTicks <= 0) {
                pRoll = 0.0f; pPitch = 0.0f;
                petMode = stayCommanded ? PetMode.STAY_WAIT : (perchCommanded ? PetMode.PERCH_COMMAND : PetMode.FOLLOW_SIDE);
                petModeTicks = 75;
            }
            return;
        }

        if (petMode == PetMode.STAY_WAIT) {
            double ty = petStayY + Math.sin(t * 2.0) * 0.08;
            px += (petStayX - px) * 0.16; py += (ty - py) * 0.16; pz += (petStayZ - pz) * 0.16;
            float toPlayer = (float) Math.toDegrees(Math.atan2(-(player.getX() - px), player.getZ() - pz));
            pYaw += Mth.wrapDegrees(toPlayer - pYaw) * 0.18f;
            pPitch += (0.0f - pPitch) * 0.2f;
            pRoll += ((gazeTicks > 15 ? (float) Math.sin(t * 2.2) * 14.0f : 0.0f) - pRoll) * 0.2f;
            petSpeed = 0.0;
            if (player.tickCount % 36 == 0) {
                WardrobeVfxEngine.spawnSleepySparkle(new Vec3(px, py + 0.1, pz), 0x7DD3FC);
            }
            return;
        }

        if (petMode == PetMode.PERCH_COMMAND) {
            double tx, ty, tz;
            if (firstPerson) {
                // Snuggle warmly right beside the player's cheek in 1st-person without blocking center!
                tx = player.getX() + lookFwdX * 0.72 + lookLeftX * 0.62;
                tz = player.getZ() + lookFwdZ * 0.72 + lookLeftZ * 0.62;
                ty = player.getEyeY() - 0.14 + Math.sin(t * 2.4) * 0.04;
            } else {
                tx = player.getX();
                tz = player.getZ();
                ty = headTopY;
            }
            px += (tx - px) * 0.36; py += (ty - py) * 0.36; pz += (tz - pz) * 0.36;
            pYaw += Mth.wrapDegrees(player.yBodyRot - pYaw) * 0.30f;
            pPitch += (0.0f - pPitch) * 0.25f;
            pRoll += ((float) Math.sin(t * 1.8) * 6.0f - pRoll) * 0.2f;
            petSpeed = 0.0;
            if (player.tickCount % 55 == 0) {
                WardrobeVfxEngine.spawnPetHearts(new Vec3(px, py + 0.28, pz), 1, 0xF472B6, 0xFDE047);
            }
            return;
        }

        // ---- Autonomous Living Follow / Orbit / Peek / Random Perch ----
        if ((firstPerson || movingFast) && (petMode == PetMode.PERCH_HEAD || petMode == PetMode.PERCH_SHOULDER_L || petMode == PetMode.PERCH_SHOULDER_R)) {
            petMode = PetMode.FOLLOW_SIDE; petModeTicks = 75;
        }

        if (--petModeTicks <= 0) {
            var rng = ThreadLocalRandom.current();
            int roll = rng.nextInt(100);
            if (movingFast) {
                if (roll < 62) { petMode = PetMode.FOLLOW_SIDE; if (rng.nextInt(3) == 0) petSide = -petSide; petModeTicks = 70 + rng.nextInt(60); }
                else if (roll < 86) { petMode = PetMode.ORBIT_PLAYFUL; orbitDir = rng.nextBoolean() ? 1.0 : -1.0; petModeTicks = 75 + rng.nextInt(55); }
                else { petMode = PetMode.CURIOUS_PEEK; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 55 + rng.nextInt(35); }
            } else if (firstPerson) {
                if (roll < 44) { petMode = PetMode.FOLLOW_SIDE; petSide = rng.nextBoolean() ? 1 : -1; petModeTicks = 80 + rng.nextInt(60); }
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

        double tx, ty, tz;
        boolean perched = false;
        double stiffness = 0.12, damping = 0.68;
        double shoulderY = player.getY() + 1.56 - crouchDrop + (hasChest ? 0.09 : 0.0);

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
                ty = shoulderY; stiffness = 0.22; damping = 0.62;
                perched = Math.hypot(tx - px, tz - pz) < 0.20 && Math.abs(ty - py) < 0.20;
            }
            case PERCH_HEAD -> {
                tx = player.getX(); tz = player.getZ(); ty = headTopY; stiffness = 0.22; damping = 0.62;
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
        petSpeed = Math.hypot(pvx, pvz);

        boolean eyeContact = gazeTicks > 18 && "pet".equals(aimedPetSlot(player, 4.5));
        float targetYaw = (eyeContact || (petMode == PetMode.CURIOUS_PEEK && petSpeed < 0.08))
                ? (float) Math.toDegrees(Math.atan2(-(player.getX() - px), player.getZ() - pz))
                : (perched ? player.yBodyRot + (float) Math.sin(t * 1.15) * 26.0f
                : (petSpeed > 0.035 ? (float) Math.toDegrees(Math.atan2(-pvx, pvz)) : player.yBodyRot));
        float yawDiff = Mth.wrapDegrees(targetYaw - pYaw);
        pYaw += yawDiff * 0.22f;
        pPitch += ((perched ? 0.0f : (float) Mth.clamp(-pvy * 55.0 + petSpeed * 26.0, -24.0, 24.0)) - pPitch) * 0.2f;
        float desiredRoll = eyeContact ? 15.0f : (perched ? 0.0f : Mth.clamp(yawDiff * 0.75f, -26.0f, 26.0f));
        pRoll += (desiredRoll - pRoll) * 0.22f;
    }

    /** Executes one of 8 distinct signature pet attack choreographies! */
    private static void tickPetSignatureAttack(LocalPlayer player, Entity target) {
        Vec3 aim = new Vec3(target.getX(), target.getY() + Math.max(0.55, target.getBbHeight() * 0.60), target.getZ());
        Vec3 petPos = new Vec3(px, py, pz);
        Vec3 to = aim.subtract(petPos);
        double dist = to.length();
        float aimYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        int[] cols = attackColors(petAttackCosmeticId, petAttackStyle);

        switch (petAttackStyle) {
            case BREATH_FIREBALL, ORBITAL_LASER, HYDRO_SURGE -> {
                double hoverDist = petAttackStyle == AttackStyle.ORBITAL_LASER ? 2.8 : 2.3;
                double hoverY = aim.y + (petAttackStyle == AttackStyle.ORBITAL_LASER ? 1.45 : 0.85);
                double inv = dist > 1e-4 ? 1.0 / dist : 1.0;
                double tx = aim.x - to.x * inv * hoverDist + (-to.z * inv) * 0.7 * petAttackSide;
                double tz = aim.z - to.z * inv * hoverDist + (to.x * inv) * 0.7 * petAttackSide;
                pvx = (pvx + (tx - px) * 0.34) * 0.62;
                pvy = (pvy + (hoverY - py) * 0.34) * 0.62;
                pvz = (pvz + (tz - pz) * 0.34) * 0.62;
                px += pvx; py += pvy; pz += pvz;
                if (petAttackAge < 5) {
                    pPitch = -26.0f;
                    pRoll = petAttackSide * 18.0f;
                } else if (!petStruck) {
                    petStruck = true;
                    pPitch = 28.0f;
                    WardrobeVfxEngine.spawnPetSignatureVfx(petAttackStyle, new Vec3(px, py, pz), aim, aimYaw, cols[0], cols[1]);
                } else {
                    pPitch += (0.0f - pPitch) * 0.22f;
                    pRoll += (0.0f - pRoll) * 0.22f;
                    if (petAttackAge >= 14) finishPetAttack();
                }
            }
            case ARCANE_BARRAGE -> {
                double ang = petAttackAge * 0.48 * petAttackSide;
                double tx = aim.x + Math.cos(ang) * 2.1;
                double tz = aim.z + Math.sin(ang) * 2.1;
                double ty = aim.y + 0.95 + Math.sin(petAttackAge * 0.6) * 0.35;
                px += (tx - px) * 0.38; py += (ty - py) * 0.38; pz += (tz - pz) * 0.38;
                pRoll = petAttackSide * 34.0f;
                pPitch = 14.0f;
                if (petAttackShots < 3 && (petAttackAge == 3 || petAttackAge == 7 || petAttackAge == 11)) {
                    petAttackShots++;
                    WardrobeVfxEngine.spawnPetSignatureVfx(AttackStyle.ARCANE_BARRAGE, new Vec3(px, py, pz), aim, aimYaw, cols[0], cols[1]);
                }
                if (petAttackAge >= 16) finishPetAttack();
            }
            case PHANTOM_BLINK -> {
                if (petAttackAge == 3) {
                    Vec3 prev = new Vec3(px, py, pz);
                    double rad = Math.toRadians(target.getYRot());
                    double bx = aim.x + Math.sin(rad) * 1.15;
                    double bz = aim.z - Math.cos(rad) * 1.15;
                    px = prevPx = bx; py = prevPy = aim.y + 0.25; pz = prevPz = bz;
                    petStruck = true;
                    pRoll = petAttackSide * 50.0f;
                    WardrobeVfxEngine.spawnPetSignatureVfx(AttackStyle.PHANTOM_BLINK, prev, aim, aimYaw, cols[0], cols[1]);
                } else if (petAttackAge > 3 && petAttackAge < 11) {
                    px += (aim.x - px) * 0.12; pz += (aim.z - pz) * 0.12;
                    pRoll += (0.0f - pRoll) * 0.25f;
                } else if (petAttackAge >= 11) {
                    returnPetToPlayer(player);
                    if (petAttackAge >= 16) finishPetAttack();
                }
            }
            case METEOR_SLAM -> {
                if (!petStruck) {
                    double frac = Math.min(1.0, petAttackAge / 8.0);
                    double arcY = aim.y + 0.2 + Math.sin(frac * Math.PI) * 2.15;
                    px += (aim.x - px) * 0.35;
                    pz += (aim.z - pz) * 0.35;
                    py += (arcY - py) * 0.45;
                    pPitch = (float) (frac * 360.0);
                    if (petAttackAge >= 8) {
                        petStruck = true;
                        pvy = 0.35; pPitch = 0.0f;
                        WardrobeVfxEngine.spawnPetSignatureVfx(AttackStyle.METEOR_SLAM, new Vec3(px, py, pz), aim, aimYaw, cols[0], cols[1]);
                    }
                } else {
                    returnPetToPlayer(player);
                    if (petAttackAge >= 16) finishPetAttack();
                }
            }
            case IAI_DASH_SLASH -> {
                if (petAttackAge < 4) {
                    pPitch = -15.0f; pRoll = petAttackSide * 30.0f;
                } else if (!petStruck) {
                    petStruck = true;
                    Vec3 start = new Vec3(px, py, pz);
                    double inv = dist > 1e-4 ? 1.0 / dist : 1.0;
                    px = aim.x + to.x * inv * 1.4;
                    py = aim.y + 0.15;
                    pz = aim.z + to.z * inv * 1.4;
                    pRoll = -petAttackSide * 45.0f;
                    WardrobeVfxEngine.spawnPetSignatureVfx(AttackStyle.IAI_DASH_SLASH, start, aim, aimYaw, cols[0], cols[1]);
                } else {
                    returnPetToPlayer(player);
                    if (petAttackAge >= 14) finishPetAttack();
                }
            }
            default -> {
                double hitRadius = Math.max(0.45, target.getBbWidth() * 0.52 + 0.28);
                if (!petStruck) {
                    double inv = dist > 1e-4 ? 1.0 / dist : 0.0;
                    double tx = aim.x - to.x * inv * hitRadius;
                    double ty = aim.y - to.y * inv * (hitRadius * 0.35) + (petAttackAge < 3 ? 0.25 : 0.0);
                    double tz = aim.z - to.z * inv * hitRadius;
                    pvx = (pvx + (tx - px) * 0.50) * 0.58;
                    pvy = (pvy + (ty - py) * 0.50) * 0.58;
                    pvz = (pvz + (tz - pz) * 0.50) * 0.58;
                    px += pvx; py += pvy; pz += pvz;
                    double rem = aim.distanceTo(new Vec3(px, py, pz));
                    if (rem <= hitRadius + 0.45 || petAttackAge >= 7) {
                        petStruck = true;
                        pPitch = 36.0f; pRoll = petAttackSide * 42.0f;
                        WardrobeVfxEngine.spawnPetSignatureVfx(AttackStyle.CLAW_POUNCE, new Vec3(px, py, pz), aim, aimYaw, cols[0], cols[1]);
                    } else {
                        pPitch += (26.0f - pPitch) * 0.4f;
                        pRoll += (petAttackSide * 24.0f - pRoll) * 0.35f;
                    }
                } else {
                    returnPetToPlayer(player);
                    if (petAttackAge >= 15) finishPetAttack();
                }
            }
        }
        petSpeed = Math.max(0.32, Math.hypot(px - prevPx, pz - prevPz));
        pYaw += Mth.wrapDegrees(aimYaw - pYaw) * 0.55f;
    }

    private static void returnPetToPlayer(LocalPlayer player) {
        double bodyRad = Math.toRadians(player.yBodyRot);
        double leftX = Math.cos(bodyRad), leftZ = Math.sin(bodyRad);
        pvx = (pvx + (player.getX() + leftX * (0.95 * petAttackSide) - px) * 0.24) * 0.66;
        pvy = (pvy + (player.getY() + 1.60 - py) * 0.24) * 0.66;
        pvz = (pvz + (player.getZ() + leftZ * (0.95 * petAttackSide) - pz) * 0.24) * 0.66;
        px += pvx; py += pvy; pz += pvz;
        pPitch += (0.0f - pPitch) * 0.26f;
        pRoll += (0.0f - pRoll) * 0.26f;
    }

    private static void finishPetAttack() {
        petMode = perchCommanded ? PetMode.PERCH_COMMAND : PetMode.FOLLOW_SIDE;
        petAttackTargetId = -1;
        petStruck = false;
        petModeTicks = 60;
    }

    // ---------------------------------------------------------------- Ground Companion AI & Interactive Modes

    private static void tickCompanion(LocalPlayer player, ClientLevel level, SkyPilotConfig.Appearance a, boolean firstPerson) {
        double bodyRad = Math.toRadians(player.yBodyRot);
        double fwdX = -Math.sin(bodyRad), fwdZ = Math.cos(bodyRad);
        double rightX = -Math.cos(bodyRad), rightZ = -Math.sin(bodyRad);
        double lookRad = Math.toRadians(player.getYRot());
        double lookFwdX = -Math.sin(lookRad), lookFwdZ = Math.cos(lookRad);
        double playerHorizSpeed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);

        boolean wideRange = compMode == CompMode.ATTACK_TARGET || compMode == CompMode.INTERACT_FETCH || compMode == CompMode.STAY_WAIT;
        if (!compInit || Math.hypot(cx - player.getX(), cz - player.getZ()) > (wideRange ? 24.0 : 13.0)
                || Math.abs(cy - player.getY()) > (wideRange ? 9.0 : 4.5)) {
            cx = prevCx = player.getX() + rightX * 1.25 - fwdX * 0.4;
            cz = prevCz = player.getZ() + rightZ * 1.25 - fwdZ * 0.4;
            cy = prevCy = findGroundY(level, cx, player.getY(), cz);
            cvx = cvz = 0; cYaw = prevCYaw = player.yBodyRot; cPitch = prevCPitch = 0.0f; cRoll = prevCRoll = 0.0f; cHop = prevCHop = 0.0;
            compMode = perchCommanded ? CompMode.PERCH_SHOULDER : CompMode.FOLLOW;
            stayCommanded = false;
            compAttackTargetId = -1; compStruck = false; compModeTicks = 90; compInit = true;
            return;
        }

        prevCx = cx; prevCy = cy; prevCz = cz; prevCYaw = cYaw; prevCPitch = cPitch; prevCRoll = cRoll; prevCHop = cHop;

        if (compMode == CompMode.ATTACK_TARGET) {
            Entity target = compAttackTargetId >= 0 ? level.getEntity(compAttackTargetId) : null;
            compAttackAge++;
            if (target == null || !isValidAttackTarget(player, target) || compAttackAge > 24) {
                compMode = perchCommanded ? CompMode.PERCH_SHOULDER : CompMode.FOLLOW;
                compAttackTargetId = -1; compStruck = false; compModeTicks = 55;
            } else {
                tickCompanionSignatureAttack(player, level, target);
                return;
            }
        }

        // ---- Interactive Companion Modes ----
        if (compMode == CompMode.INTERACT_PAT) {
            compInteractTicks--;
            double tx = player.getX() + lookFwdX * 1.32 + rightX * 0.35;
            double tz = player.getZ() + lookFwdZ * 1.32 + rightZ * 0.35;
            cx += (tx - cx) * 0.22; cz += (tz - cz) * 0.22;
            snapCompanionY(findGroundY(level, cx, player.getY(), cz));
            cHop = Math.abs(Math.sin(compInteractTicks * 0.48)) * 0.34;
            float facePlayer = (float) Math.toDegrees(Math.atan2(-(player.getX() - cx), player.getZ() - cz));
            cYaw += Mth.wrapDegrees(facePlayer - cYaw) * 0.36f;
            cPitch = -14.0f; // Looking up happily at you!
            cRoll = (float) Math.sin(compInteractTicks * 0.72) * 20.0f;
            compSpeed = 0.08;
            if (compInteractTicks % 10 == 0 && compInteractTicks > 4) {
                WardrobeVfxEngine.spawnPetHearts(new Vec3(cx, cy + cHop + 0.55, cz), 2, 0xF472B6, 0x67E8F9);
            }
            if (compInteractTicks <= 0) {
                cRoll = 0.0f; cPitch = 0.0f;
                compMode = stayCommanded ? CompMode.STAY_WAIT : (perchCommanded ? CompMode.PERCH_SHOULDER : CompMode.FOLLOW);
                compModeTicks = 65;
            }
            return;
        }

        if (compMode == CompMode.INTERACT_TREAT) {
            if (!treatActive || --compInteractTicks <= 0) {
                compMode = CompMode.INTERACT_PAT; compInteractTicks = 20;
            } else {
                cx += (treatX - cx) * 0.26; cz += (treatZ - cz) * 0.26;
                snapCompanionY(findGroundY(level, cx, player.getY(), cz));
                cHop = Math.abs(Math.sin(compInteractTicks * 0.5)) * 0.48; // Hopping up for the treat!
                float aim = (float) Math.toDegrees(Math.atan2(-(treatX - cx), treatZ - cz));
                cYaw += Mth.wrapDegrees(aim - cYaw) * 0.42f;
                cPitch = -22.0f; cRoll = 0.0f; compSpeed = 0.26;
            }
            return;
        }

        if (compMode == CompMode.INTERACT_FETCH) {
            if (!ballActive || --compInteractTicks <= 0) {
                compMode = CompMode.FOLLOW; compModeTicks = 60;
            } else {
                double tx = (ballCarriedBy == 2) ? (player.getX() + lookFwdX * 1.15) : ballX;
                double tz = (ballCarriedBy == 2) ? (player.getZ() + lookFwdZ * 1.15) : ballZ;
                double dx = tx - cx, dz = tz - cz, dist = Math.hypot(dx, dz);
                double spd = Math.min(0.55, Math.max(0.24, dist * 0.35));
                if (dist > 1e-4) { cx += (dx / dist) * spd; cz += (dz / dist) * spd; }
                snapCompanionY(findGroundY(level, cx, player.getY(), cz));
                cHop = Math.abs(Math.sin(player.tickCount * 0.55)) * 0.24;
                float aim = (float) Math.toDegrees(Math.atan2(-dx, dz));
                cYaw += Mth.wrapDegrees(aim - cYaw) * 0.45f;
                cPitch = 10.0f; cRoll = 0.0f; compSpeed = spd;
            }
            return;
        }

        if (compMode == CompMode.INTERACT_TRICK) {
            compInteractTicks--;
            double p = 1.0 - (compInteractTicks / 42.0);
            cHop = Math.sin(p * Math.PI) * 1.15; // Joyful high acrobatic flip-leap!
            cYaw = (float) ((p * 720.0) % 360.0);
            cPitch = (float) (p * 360.0);
            cRoll = 0.0f;
            snapCompanionY(findGroundY(level, cx, player.getY(), cz));
            compSpeed = 0.22;
            if (compInteractTicks % 8 == 0) {
                WardrobeVfxEngine.spawnPetHearts(new Vec3(cx, cy + cHop + 0.5, cz), 2, 0x67E8F9, 0xF472B6);
            }
            if (compInteractTicks <= 0) {
                cPitch = 0.0f; cHop = 0.0;
                compMode = stayCommanded ? CompMode.STAY_WAIT : (perchCommanded ? CompMode.PERCH_SHOULDER : CompMode.FOLLOW);
                compModeTicks = 65;
            }
            return;
        }

        if (compMode == CompMode.STAY_WAIT) {
            cx += (compStayX - cx) * 0.20; cz += (compStayZ - cz) * 0.20;
            snapCompanionY(findGroundY(level, cx, cy, cz));
            cHop *= 0.5;
            float toPlayer = (float) Math.toDegrees(Math.atan2(-(player.getX() - cx), player.getZ() - cz));
            cYaw += Mth.wrapDegrees(toPlayer - cYaw) * 0.20f;
            cPitch += (-8.0f - cPitch) * 0.2f;
            cRoll += ((gazeTicks > 15 ? 14.0f : 0.0f) - cRoll) * 0.2f;
            compSpeed = 0.0;
            if (player.tickCount % 40 == 0) {
                WardrobeVfxEngine.spawnSleepySparkle(new Vec3(cx, cy + 0.45, cz), 0xA5F3FC);
            }
            return;
        }

        if (compMode == CompMode.PERCH_SHOULDER) {
            double crouchDrop = player.isCrouching() ? 0.25 : 0.0;
            boolean hasChest = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            double tx, ty, tz;
            if (firstPerson) {
                tx = player.getX() + lookFwdX * 0.78 - Math.cos(lookRad) * 0.68;
                tz = player.getZ() + lookFwdZ * 0.78 - Math.sin(lookRad) * 0.68;
                ty = player.getEyeY() - 0.36;
            } else {
                double sOff = 0.48 + (hasChest ? 0.07 : 0.0);
                tx = player.getX() + rightX * sOff - fwdX * 0.04;
                tz = player.getZ() + rightZ * sOff - fwdZ * 0.04;
                ty = player.getY() + 1.48 - crouchDrop + (hasChest ? 0.08 : 0.0);
            }
            cx += (tx - cx) * 0.38; cy += (ty - cy) * 0.38; cz += (tz - cz) * 0.38;
            cHop = 0.0;
            cYaw += Mth.wrapDegrees(player.yBodyRot - cYaw) * 0.32f;
            cPitch += (0.0f - cPitch) * 0.25f;
            cRoll += ((float) Math.sin(player.tickCount * 0.09) * 6.0f - cRoll) * 0.2f;
            compSpeed = 0.0;
            return;
        }

        // ---- Autonomous Companion Follow / Wander / Sit ----
        cHop *= 0.55; if (cHop < 0.01) cHop = 0.0;
        cPitch *= 0.65f;
        boolean eyeContact = gazeTicks > 18 && "companion".equals(aimedPetSlot(player, 4.5));
        cRoll += ((eyeContact ? -15.0f : 0.0f) - cRoll) * 0.22f;

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

        float targetYaw = (eyeContact || compMode == CompMode.SIT_WATCH)
                ? (float) Math.toDegrees(Math.atan2(-(player.getX() - cx), player.getZ() - cz))
                : (compSpeed > 0.025 ? (float) Math.toDegrees(Math.atan2(-cvx, cvz)) : player.yBodyRot);
        cYaw += Mth.wrapDegrees(targetYaw - cYaw) * 0.22f;
    }

    private static void tickCompanionSignatureAttack(LocalPlayer player, ClientLevel level, Entity target) {
        double dx = target.getX() - cx, dz = target.getZ() - cz, dist = Math.hypot(dx, dz);
        float aimYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        Vec3 hitPos = new Vec3(target.getX(), target.getY() + Math.min(0.95, target.getBbHeight() * 0.50), target.getZ());
        int[] cols = attackColors(compAttackCosmeticId, compAttackStyle);

        if (compAttackStyle == AttackStyle.BREATH_FIREBALL || compAttackStyle == AttackStyle.ORBITAL_LASER
                || compAttackStyle == AttackStyle.HYDRO_SURGE || compAttackStyle == AttackStyle.ARCANE_BARRAGE) {
            if (compAttackAge < 5) {
                cHop = Math.sin((compAttackAge / 5.0) * Math.PI) * 0.35;
                cPitch = -22.0f;
            } else if (!compStruck) {
                compStruck = true;
                cPitch = 20.0f;
                WardrobeVfxEngine.spawnPetSignatureVfx(compAttackStyle, new Vec3(cx, cy + 0.55, cz), hitPos, aimYaw, cols[0], cols[1]);
            } else {
                cHop = Math.max(0.0, cHop - 0.08);
                cPitch += (0.0f - cPitch) * 0.25f;
                if (compAttackAge >= 14) { compMode = CompMode.FOLLOW; compAttackTargetId = -1; compStruck = false; compModeTicks = 50; }
            }
            snapCompanionY(findGroundY(level, cx, player.getY(), cz));
            cYaw += Mth.wrapDegrees(aimYaw - cYaw) * 0.55f;
            return;
        }

        double reach = Math.max(0.65, target.getBbWidth() * 0.55 + 0.42);
        if (!compStruck) {
            double inv = dist > 1e-4 ? 1.0 / dist : 0.0;
            double speed = Math.min(0.78, Math.max(0.32, (dist - reach * 0.6) * 0.58));
            cvx += (dx * inv * speed - cvx) * 0.58;
            cvz += (dz * inv * speed - cvz) * 0.58;
            cx += cvx; cz += cvz; compSpeed = Math.max(0.28, Math.hypot(cvx, cvz));
            double hopHeight = compAttackStyle == AttackStyle.METEOR_SLAM ? 1.25 : 0.55;
            if (dist < reach + 1.8) {
                double frac = Mth.clamp(1.0 - (dist - reach) / 1.8, 0.0, 1.0);
                cHop = Math.sin(frac * Math.PI) * hopHeight;
                cPitch += (26.0f - cPitch) * 0.42f;
            } else { cHop *= 0.6; cPitch += (12.0f - cPitch) * 0.3f; }
            double newDist = Math.hypot(target.getX() - cx, target.getZ() - cz);
            if (newDist <= reach + 0.40 || (compAttackAge >= 10 && newDist <= reach + 1.35)) {
                compStruck = true;
                double rInv = newDist > 1e-4 ? 1.0 / newDist : 1.0;
                cvx = -(target.getX() - cx) * rInv * 0.30; cvz = -(target.getZ() - cz) * rInv * 0.30;
                cHop = 0.32; cPitch = 32.0f;
                WardrobeVfxEngine.spawnPetSignatureVfx(compAttackStyle, new Vec3(cx, cy + 0.5, cz), hitPos, aimYaw, cols[0], cols[1]);
            }
        } else {
            cvx *= 0.72; cvz *= 0.72; cx += cvx; cz += cvz;
            cHop = Math.max(0.0, cHop - 0.11); cPitch += (0.0f - cPitch) * 0.3f; compSpeed = Math.hypot(cvx, cvz);
            if (compAttackAge >= 18) { compMode = CompMode.FOLLOW; compAttackTargetId = -1; compStruck = false; compModeTicks = 50; }
        }
        snapCompanionY(findGroundY(level, cx, Mth.lerp(0.5, player.getY(), target.getY()), cz));
        cYaw += Mth.wrapDegrees(aimYaw - cYaw) * 0.52f;
    }

    private static void snapCompanionY(double groundY) {
        double dy = groundY - cy;
        if (Math.abs(dy) > 3.5) cy = groundY;
        else if (dy > 0.30) cy += Math.min(dy, 0.32);
        else if (dy < -0.05) cy = Math.max(groundY, cy - Math.max(0.22, -dy * 0.48));
        else cy += dy * 0.55;
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
        boolean perched = (petMode == PetMode.PERCH_HEAD || petMode == PetMode.PERCH_SHOULDER_L
                || petMode == PetMode.PERCH_SHOULDER_R || petMode == PetMode.PERCH_COMMAND) && petSpeed < 0.05;
        return new Snapshot(x, y, z, yaw, pitch, roll, petSpeed, perched);
    }

    static Snapshot companionSnapshot(float partialTick) {
        if (!compInit) return null;
        double x = Mth.lerp(partialTick, prevCx, cx), y = Mth.lerp(partialTick, prevCy, cy) + Mth.lerp(partialTick, prevCHop, cHop), z = Mth.lerp(partialTick, prevCz, cz);
        float yaw = prevCYaw + Mth.wrapDegrees(cYaw - prevCYaw) * partialTick;
        float pitch = Mth.lerp(partialTick, prevCPitch, cPitch);
        float roll = Mth.lerp(partialTick, prevCRoll, cRoll);
        boolean sitting = (compMode == CompMode.SIT_WATCH || compMode == CompMode.STAY_WAIT || compMode == CompMode.PERCH_SHOULDER) && compSpeed < 0.02;
        return new Snapshot(x, y, z, yaw, pitch, roll, compSpeed, sitting);
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
