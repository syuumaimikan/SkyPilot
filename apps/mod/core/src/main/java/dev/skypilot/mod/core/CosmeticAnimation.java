package dev.skypilot.mod.core;

/**
 * Procedural animation and physics equations for cosmetics across all 6 slots
 * (Cloaks, Wings/Backwear, Headwear, Auras, Floating Pets, Ground Companions).
 * Pure mathematical functions decoupled from Minecraft rendering classes, enabling
 * shared simulation across in-game world rendering, in-game 3D studio, and Launcher preview.
 */
public final class CosmeticAnimation {
    private static final long START_NANOS = System.nanoTime();

    private CosmeticAnimation() {}

    /**
     * High-precision relative animation clock in seconds (starts at 0.0 on JVM start).
     * Avoids both 32-bit int overflow and IEEE-754 trig precision loss from raw epoch timestamps.
     */
    public static double animationTimeSeconds() {
        return (System.nanoTime() - START_NANOS) * 1e-9;
    }

    /**
     * Overflow-safe animated texture strip frame index for any timestamp (including epoch seconds).
     */
    public static int frameIndex(double time, int animFps, int totalFrames) {
        if (totalFrames <= 1) return 0;
        int fps = Math.max(1, animFps);
        long frame = (long) Math.floor(time * (double) fps);
        return (int) Math.floorMod(frame, (long) totalFrames);
    }

    /**
     * Incremental bone transform in Blockbench pixel & degree space.
     * Added to the bone's base pivot/rotation; scale multiplies base scale.
     */
    public record BoneTransform(
            double dx, double dy, double dz,
            double rxDeg, double ryDeg, double rzDeg,
            double sx, double sy, double sz,
            boolean visible
    ) {
        public static final BoneTransform IDENTITY = new BoneTransform(0, 0, 0, 0, 0, 0, 1, 1, 1, true);
    }

    /**
     * Evaluates a named animation tag for a CosmeticRig bone.
     */
    public static BoneTransform evaluateBone(
            String anim, double amp, double phase, double time,
            double speed, boolean sneaking, boolean falling, boolean flying,
            double vertSpeed, double yawTurnRate
    ) {
        if (anim == null || anim.isEmpty()) return BoneTransform.IDENTITY;
        double a = amp == 0.0 ? 1.0 : amp;
        double t = time;

        // ---- 8-segment ultra-smooth cloth cape drape (cape_seg_0 .. cape_seg_7) ----
        if (anim.startsWith("cape_seg_")) {
            int seg = parseIndex(anim, "cape_seg_");
            int row = seg * 2 + 1;
            double pitchRad = capePitch(t, row, 5, speed, sneaking, vertSpeed);
            double prevRad = seg == 0 ? 0.0 : capePitch(t, (seg - 1) * 2 + 1, 5, speed, sneaking, vertSpeed);
            double deltaDeg = Math.toDegrees(pitchRad - prevRad) * a;
            double rollDeg = seg == 0 ? Math.toDegrees(capeRoll(t, row, 5, yawTurnRate)) * a
                    : Math.toDegrees(capeRoll(t, row, 5, yawTurnRate) - capeRoll(t, (seg - 1) * 2 + 1, 5, yawTurnRate)) * a;
            // Positive pitch swings cape backward (+Z), which is negative rx in Blockbench (+Y up, +Z back)
            return new BoneTransform(0, 0, 0, -deltaDeg, 0, rollDeg, 1, 1, 1, true);
        }

        // ---- Articulated multi-joint wings ----
        switch (anim) {
            case "wing_l_root", "wing_r_root" -> {
                double side = anim.equals("wing_l_root") ? 1.0 : -1.0;
                double flapDeg = Math.toDegrees(wingFlap(t + phase, speed, falling, flying)) * a;
                double breathe = Math.sin(t * 2.1 + phase) * 3.5 * a;
                return new BoneTransform(0, 0, 0, breathe, -side * flapDeg, side * (breathe * 0.6), 1, 1, 1, true);
            }
            case "wing_l_mid", "wing_r_mid" -> {
                double side = anim.equals("wing_l_mid") ? 1.0 : -1.0;
                double lagDeg = Math.toDegrees(wingTipLag(t + phase, speed)) * 1.15 * a;
                return new BoneTransform(0, 0, 0, 0, -side * lagDeg, side * lagDeg * 0.35, 1, 1, 1, true);
            }
            case "wing_l_tip", "wing_r_tip" -> {
                double side = anim.equals("wing_l_tip") ? 1.0 : -1.0;
                double lagDeg = Math.toDegrees(wingTipLag(t - 0.16 + phase, speed)) * 1.45 * a;
                return new BoneTransform(0, 0, 0, 0, -side * lagDeg, side * lagDeg * 0.45, 1, 1, 1, true);
            }
            case "wing_l_lower", "wing_r_lower" -> {
                double side = anim.equals("wing_l_lower") ? 1.0 : -1.0;
                double freq = speed > 0.08 ? 9.5 : 4.2;
                double flap = (12.0 + Math.sin(t * freq - 0.6 + phase) * 20.0) * a;
                return new BoneTransform(0, 0, 0, 0, -side * flap, side * Math.sin(t * freq + phase) * 5.0, 1, 1, 1, true);
            }
            case "wing_float" -> {
                double dy = Math.sin(t * 2.6 + phase) * 1.1 * a;
                double dz = Math.cos(t * 1.9 + phase) * 0.55 * a;
                double rz = Math.sin(t * 2.1 + phase) * 6.0 * a;
                return new BoneTransform(0, dy, dz, 0, 0, rz, 1, 1, 1, true);
            }
            case "gear_cw", "gear_ccw" -> {
                double dir = anim.equals("gear_cw") ? 1.0 : -1.0;
                double deg = Math.toDegrees(gearAngle(t, a)) * dir;
                return new BoneTransform(0, 0, 0, 0, 0, deg, 1, 1, 1, true);
            }
            case "flame_pulse" -> {
                double pulse = 0.86 + 0.22 * Math.sin(t * 14.0 + phase) + 0.08 * Math.cos(t * 23.0);
                double sy = Math.max(0.5, pulse * a);
                return new BoneTransform(0, 0, 0, 0, 0, 0, 0.92 + 0.12 * Math.sin(t * 11.0 + phase), sy, 0.92 + 0.12 * Math.cos(t * 11.0 + phase), true);
            }
            case "chain_swing" -> {
                double rx = Math.sin(t * 2.4 + phase) * (8.0 + speed * 25.0) * a;
                double rz = Math.cos(t * 1.9 + phase) * 6.0 * a;
                return new BoneTransform(0, 0, 0, rx, 0, rz, 1, 1, 1, true);
            }
            // ---- Headwear ----
            case "halo_float" -> {
                double dy = haloBob(t + phase) * 16.0 * a;
                double ry = Math.toDegrees(haloSpin(t, false)) * a;
                return new BoneTransform(0, dy, 0, 0, ry, 0, 1, 1, 1, true);
            }
            case "halo_spin_cw" -> {
                double dy = haloBob(t + phase) * 14.0;
                double ry = ((t * 72.0 * a) % 360.0);
                return new BoneTransform(0, dy, 0, 0, ry, 0, 1, 1, 1, true);
            }
            case "halo_spin_ccw" -> {
                double dy = haloBob(t + phase + 1.0) * 14.0;
                double ry = -((t * 64.0 * a) % 360.0);
                return new BoneTransform(0, dy, 0, 0, ry, 0, 1, 1, 1, true);
            }
            case "halo_tilt" -> {
                double dy = Math.sin(t * 2.6 + phase) * 0.7;
                double ry = ((t * 80.0 * a) % 360.0);
                return new BoneTransform(0, dy, 0, 12.0 * Math.sin(t * 1.4), ry, 10.0 * Math.cos(t * 1.4), 1, 1, 1, true);
            }
            case "ear_l", "ear_r" -> {
                int side = anim.equals("ear_l") ? 1 : -1;
                double rz = Math.toDegrees(earTwitch(t, side)) * a;
                return new BoneTransform(0, 0, 0, 0, 0, rz, 1, 1, 1, true);
            }
            case "visor_pulse" -> {
                double s = 1.0 + 0.025 * Math.sin(t * 5.0 + phase);
                return new BoneTransform(0, 0, 0, 0, 0, 0, s, 1.0, 1.0, true);
            }
            // ---- Auras ----
            case "aura_orbit_cw" -> {
                double ry = (t * 68.0 * a + Math.toDegrees(phase)) % 360.0;
                double dy = Math.sin(t * 2.2 + phase) * 1.2;
                return new BoneTransform(0, dy, 0, 0, ry, 0, 1, 1, 1, true);
            }
            case "aura_orbit_ccw" -> {
                double ry = -((t * 58.0 * a + Math.toDegrees(phase)) % 360.0);
                double dy = Math.cos(t * 2.0 + phase) * 1.2;
                return new BoneTransform(0, dy, 0, 0, ry, 0, 1, 1, 1, true);
            }
            case "aura_orbit_fast" -> {
                double ry = (t * 135.0 * a + Math.toDegrees(phase)) % 360.0;
                double dy = Math.sin(t * 3.8 + phase) * 1.6;
                return new BoneTransform(0, dy, 0, Math.sin(t * 2.1 + phase) * 8.0, ry, Math.cos(t * 1.7 + phase) * 8.0, 1, 1, 1, true);
            }
            case "aura_spiral_up" -> {
                double cycle = (t * 0.42 * a + phase) % 1.0;
                double dy = (cycle - 0.5) * 24.0;
                double ry = (cycle * 360.0 * 1.5) % 360.0;
                double fade = Math.sin(cycle * Math.PI);
                double sc = Math.max(0.25, 0.35 + 0.75 * fade);
                return new BoneTransform(0, dy, 0, 0, ry, 0, sc, sc, sc, true);
            }
            case "aura_ring_ground" -> {
                double ry = (t * 95.0 * a + Math.toDegrees(phase)) % 360.0;
                double pulse = 1.0 + 0.05 * Math.sin(t * 4.0 + phase);
                return new BoneTransform(0, Math.sin(t * 3.0) * 0.35, 0, 0, ry, 0, pulse, 1.0, pulse, true);
            }
            case "aura_cloud" -> {
                double dx = Math.sin(t * 1.1 + phase) * 1.2;
                double dy = Math.sin(t * 2.3 + phase) * 0.8;
                double dz = Math.cos(t * 0.9 + phase) * 0.9;
                return new BoneTransform(dx, dy, dz, 0, Math.sin(t * 0.7) * 6.0, 0, 1, 1, 1, true);
            }
            case "aura_bolt" -> {
                double cyc = (t * 1.4 + phase) % 3.2;
                boolean flash = (cyc < 0.14) || (cyc > 0.22 && cyc < 0.32);
                double sc = flash ? 1.05 : 0.01;
                return new BoneTransform(0, 0, 0, 0, (t * 180.0) % 360.0, 0, sc, sc, sc, flash);
            }
            case "aura_pulse" -> {
                double p = 0.88 + 0.18 * Math.sin(t * 4.2 + phase);
                double ry = (t * 48.0 * a) % 360.0;
                return new BoneTransform(0, Math.sin(t * 2.5 + phase) * 1.5, 0, 0, ry, 0, p, p, p, true);
            }
            // ---- Floating Pets ----
            case "pet_hover" -> {
                double dy = Math.sin(t * 2.8 + phase) * 1.65 * a + Math.sin(t * 5.6 + phase) * 0.22 * a;
                double dx = Math.cos(t * 1.4 + phase) * 0.65 * a;
                double dz = Math.min(3.0, speed * 9.0) + Math.sin(t * 1.9) * 0.5;
                double pitch = Math.min(18.0, speed * 42.0) + Math.sin(t * 2.1) * 4.5;
                double roll = Math.sin(t * 1.6 + phase) * 5.5;
                double breathe = 1.0 + Math.sin(t * 3.1 + phase) * 0.025;
                return new BoneTransform(dx, dy, dz, pitch, Math.sin(t * 1.1) * 11.0, roll, 1.0 / Math.sqrt(breathe), breathe, 1.0 / Math.sqrt(breathe), true);
            }
            case "pet_wing_l", "pet_wing_r" -> {
                double side = anim.equals("pet_wing_l") ? 1.0 : -1.0;
                double freq = 10.5 * a;
                double flap = Math.sin(t * freq + phase) * 36.0 + Math.sin(t * freq * 2.0 + phase) * 5.0;
                return new BoneTransform(0, 0, 0, 0, 0, side * flap, 1, 1, 1, true);
            }
            case "pet_head" -> {
                double rx = Math.sin(t * 1.7 + phase) * 7.5;
                double ry = Math.sin(t * 1.1 + phase) * 15.0;
                double rz = Math.cos(t * 1.3 + phase) * 7.5;
                return new BoneTransform(0, 0, 0, rx, ry, rz, 1, 1, 1, true);
            }
            case "pet_tail" -> {
                double ry = Math.sin(t * 6.2 + phase) * 20.0 * a;
                double rx = Math.cos(t * 4.0 + phase) * 9.0 * a;
                return new BoneTransform(0, 0, 0, rx, ry, 0, 1, 1, 1, true);
            }
            case "pet_spin" -> {
                return new BoneTransform(0, 0, 0, 0, (t * 120.0 * a) % 360.0, 0, 1, 1, 1, true);
            }
            case "pet_pendulum" -> {
                double rz = Math.sin(t * 3.2 + phase) * 20.0 * a;
                return new BoneTransform(0, 0, 0, 0, 0, rz, 1, 1, 1, true);
            }
            // ---- Ground Companions ----
            case "comp_root" -> {
                double move = Math.min(1.0, speed * 5.0);
                double walkBob = Math.abs(Math.sin(t * 9.0)) * 1.05 * move;
                double breathe = Math.sin(t * 2.6 + phase) * 0.28;
                double bScale = 1.0 + Math.sin(t * 2.6 + phase) * 0.022;
                double dz = Math.min(3.5, speed * 10.0);
                return new BoneTransform(0, walkBob + breathe, dz, 0, Math.sin(t * 0.9) * 4.5, Math.sin(t * 4.5) * 3.0 * move, 1.0 / Math.sqrt(bScale), bScale, 1.0, true);
            }
            case "comp_hop" -> {
                double freq = speed > 0.05 ? 7.5 : 2.6;
                double hop = Math.max(0.0, Math.sin(t * freq + phase)) * (speed > 0.05 ? 2.6 : 0.9) * a;
                double pitch = Math.cos(t * freq + phase) * (speed > 0.05 ? 12.0 : 4.0);
                return new BoneTransform(0, hop, Math.min(2.5, speed * 7.0), pitch, 0, 0, 1, 1, 1, true);
            }
            case "comp_leg_fl", "comp_leg_br" -> {
                double stride = (0.18 + Math.min(1.0, speed * 5.0) * 0.82) * 32.0 * a;
                double freq = speed > 0.05 ? 9.0 : 2.8;
                return new BoneTransform(0, 0, 0, Math.sin(t * freq + phase) * stride, 0, 0, 1, 1, 1, true);
            }
            case "comp_leg_fr", "comp_leg_bl" -> {
                double stride = (0.18 + Math.min(1.0, speed * 5.0) * 0.82) * 32.0 * a;
                double freq = speed > 0.05 ? 9.0 : 2.8;
                return new BoneTransform(0, 0, 0, -Math.sin(t * freq + phase) * stride, 0, 0, 1, 1, 1, true);
            }
            case "comp_mech_l", "comp_mech_r" -> {
                double dir = anim.equals("comp_mech_l") ? 1.0 : -1.0;
                double stride = (0.22 + Math.min(1.0, speed * 4.5) * 0.78) * 24.0 * a;
                double freq = speed > 0.05 ? 6.5 : 2.2;
                return new BoneTransform(0, 0, 0, dir * Math.sin(t * freq + phase) * stride, 0, 0, 1, 1, 1, true);
            }
            case "comp_arm_l", "comp_arm_r" -> {
                double dir = anim.equals("comp_arm_l") ? -1.0 : 1.0;
                double swing = (0.25 + Math.min(1.0, speed * 4.5) * 0.75) * 20.0 * a;
                double freq = speed > 0.05 ? 6.5 : 2.2;
                return new BoneTransform(0, 0, 0, dir * Math.sin(t * freq + phase) * swing, 0, 0, 1, 1, 1, true);
            }
            case "comp_head", "comp_head_2" -> {
                double offset = anim.equals("comp_head_2") ? 1.6 : 0.0;
                double ry = Math.sin(t * 1.4 + phase + offset) * 14.0 * a;
                double rx = Math.sin(t * 2.2 + phase + offset) * 6.0 * a;
                return new BoneTransform(0, 0, 0, rx, ry, 0, 1, 1, 1, true);
            }
            case "comp_tail" -> {
                double freq = 5.5 + Math.min(6.0, speed * 14.0);
                double ry = Math.sin(t * freq + phase) * 26.0 * a;
                return new BoneTransform(0, 0, 0, 0, ry, 0, 1, 1, 1, true);
            }
            default -> {}
        }

        // Indexed dynamic tags (tendril_i, aura_ribbon_i, aura_swarm_i, head_orbit_i, comp_spider_li/ri, comp_cobra_i)
        if (anim.startsWith("tendril_")) {
            int i = parseIndex(anim, "tendril_");
            double rx = Math.sin(t * 3.0 - i * 0.65 + phase) * 16.0 * a;
            double rz = Math.cos(t * 2.4 - i * 0.55 + phase) * 14.0 * a;
            return new BoneTransform(0, 0, 0, rx, 0, rz, 1, 1, 1, true);
        }
        if (anim.startsWith("aura_ribbon_")) {
            int i = parseIndex(anim, "aura_ribbon_");
            double ry = ((t * (75.0 + i * 28.0) * (i % 2 == 0 ? 1 : -1) * a) + i * 120.0) % 360.0;
            double rx = Math.sin(t * 2.1 + i * 1.3) * 16.0;
            double rz = Math.cos(t * 1.8 + i * 1.1) * 16.0;
            double dy = Math.sin(t * 2.8 + i * 1.7) * 2.0;
            return new BoneTransform(0, dy, 0, rx, ry, rz, 1, 1, 1, true);
        }
        if (anim.startsWith("aura_swarm_")) {
            int i = parseIndex(anim, "aura_swarm_");
            double dir = (i % 2 == 0) ? 1.0 : -1.0;
            double ry = (t * (55.0 + (i % 3) * 18.0) * dir * a + i * 60.0) % 360.0;
            double dy = Math.sin(t * (2.6 + (i % 3) * 0.5) + i * 1.2 + phase) * 2.4;
            double rx = Math.sin(t * 4.5 + i) * 10.0;
            return new BoneTransform(0, dy, 0, rx, ry, 0, 1, 1, 1, true);
        }
        if (anim.startsWith("head_orbit_")) {
            int i = parseIndex(anim, "head_orbit_");
            double ry = (t * 82.0 * a + i * 120.0) % 360.0;
            double dy = Math.sin(t * 3.2 + i * 2.0) * 0.9;
            return new BoneTransform(0, dy, 0, 0, ry, 0, 1, 1, 1, true);
        }
        if (anim.startsWith("comp_spider_l") || anim.startsWith("comp_spider_r")) {
            boolean left = anim.startsWith("comp_spider_l");
            int i = parseIndex(anim, left ? "comp_spider_l" : "comp_spider_r");
            double freq = speed > 0.05 ? 11.0 : 3.6;
            double ph = (i * Math.PI * 0.5) + (left ? 0.0 : Math.PI);
            double ry = Math.sin(t * freq + ph) * 16.0 * a;
            double rz = Math.max(0.0, Math.cos(t * freq + ph)) * 12.0 * (left ? 1.0 : -1.0) * a;
            return new BoneTransform(0, 0, 0, 0, ry, rz, 1, 1, 1, true);
        }
        if (anim.startsWith("comp_cobra_")) {
            int i = parseIndex(anim, "comp_cobra_");
            double freq = speed > 0.05 ? 6.5 : 2.6;
            double ry = Math.sin(t * freq - i * 0.7 + phase) * 16.0 * a;
            double rx = Math.cos(t * freq * 0.7 - i * 0.5) * 6.0 * a;
            return new BoneTransform(0, 0, 0, rx, ry, 0, 1, 1, 1, true);
        }
        return BoneTransform.IDENTITY;
    }

    private static int parseIndex(String s, String prefix) {
        try {
            return Integer.parseInt(s.substring(prefix.length()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Cape deflection pitch (radians) at a specific row (0..15) down the cape.
     * Incorporates ambient wind waves, forward movement lift, sneak posture, and vertical air drag.
     */
    public static double capePitch(double time, int row, int col, double forwardSpeed, boolean sneaking, double verticalSpeed) {
        double r = Math.max(0, Math.min(15, row));
        double fraction = r / 15.0;

        // Base drape: falls cleanly behind torso, chestplate armor, and swinging legs (zero clipping!)
        double base = Math.toRadians(11.0 + fraction * 6.5);

        // Sneaking forward tilt compensation (prevents cape clipping through legs when crouching)
        if (sneaking) {
            base += Math.toRadians(28.0 * Math.pow(fraction, 0.7));
        }

        // Aerodynamic lift from forward movement (walking / sprinting / flying)
        double speed = Math.max(0, forwardSpeed);
        double lift = Math.min(Math.toRadians(72.0), speed * Math.toRadians(170.0)) * Math.pow(fraction, 0.85);

        // Vertical air resistance (falling pulls cape upwards; jumping pushes cape slightly down)
        double vertLift = 0;
        if (verticalSpeed < -0.05) {
            vertLift = Math.min(Math.toRadians(40.0), -verticalSpeed * Math.toRadians(45.0)) * fraction;
        } else if (verticalSpeed > 0.05) {
            vertLift = -Math.min(Math.toRadians(12.0), verticalSpeed * Math.toRadians(16.0)) * fraction;
        }

        // Ambient wind waves: primary and secondary traveling sine waves propagating downwards
        double windFreq = 3.6 + speed * 4.5;
        double wavePhase = time * windFreq - r * 0.42 + col * 0.12;
        double windAmp = Math.toRadians(2.5 + fraction * 12.0 + speed * 10.0);
        double windWave = Math.sin(wavePhase) * windAmp + Math.cos(time * 2.3 - r * 0.28) * (windAmp * 0.35);

        // Never swing forward into the player's back or armor!
        return Math.max(Math.toRadians(6.5), base + lift + vertLift + windWave);
    }

    /**
     * Cape roll / sideways flutter (radians) caused by ambient cross-winds and player turning inertia.
     */
    public static double capeRoll(double time, int row, int col, double yawTurnRate) {
        double r = Math.max(0, Math.min(15, row));
        double fraction = r / 15.0;

        double crosswind = Math.sin(time * 2.8 - r * 0.35 + col * 0.4) * Math.toRadians(1.5 + fraction * 6.5);
        double turnInertia = -Math.max(-0.4, Math.min(0.4, yawTurnRate * 0.5)) * fraction;

        return crosswind + turnInertia;
    }

    /**
     * Dynamic wave displacement along the Z axis (meters) for individual voxels.
     */
    public static double capeWaveZ(double time, int row, int col, double speed) {
        double fraction = Math.max(0, Math.min(15, row)) / 15.0;
        double freq = 3.8 + speed * 3.5;
        double wave = Math.sin(time * freq - row * 0.44 + col * 0.15);
        double secondary = Math.cos(time * 2.5 - row * 0.28);
        return (wave * 0.045 + secondary * 0.015) * Math.pow(fraction, 1.2);
    }

    /**
     * Dynamic animated RGB color for a given cosmetic ID and coordinate at the specified time.
     */
    public static int animatedColor(String cosmeticId, int baseColor, int row, int col, double time) {
        if (cosmeticId == null) return baseColor;
        switch (cosmeticId) {
            case "aurora" -> {
                double phase = (row * 0.15 - time * 1.4 + col * 0.08);
                int r = (int) (85 + 71 * Math.sin(phase) + 40 * Math.cos(phase * 0.7));
                int g = (int) (180 + 54 * Math.cos(phase * 0.9));
                int b = (int) (210 + 45 * Math.sin(phase * 1.2));
                return clampRgb(r, g, b);
            }
            case "midnight" -> {
                int seed = (col * 17 + row * 29) % 23;
                if (seed == 0 || (col == 4 && row == 7) || (col == 8 && row == 3)) {
                    double twinkle = Math.sin(time * 4.5 + col * 3.0 + row * 2.0);
                    return twinkle > 0.3 ? 0xFFFFFF : 0xC7D8FF;
                }
                return (row + col) % 7 == 0 ? 0x637ABD : 0x1E273E;
            }
            case "ember" -> {
                double floatY = ((row + time * 4.0) % 16.0);
                if (floatY < 3.0 && (col * 7 + row * 11) % 5 == 0) return 0xFFF3A8;
                if (row > 11) return 0x7A2E33;
                double heat = Math.sin(time * 3.0 + col * 0.5 - row * 0.4);
                return heat > 0.2 ? 0xFFB35A : 0xD76C48;
            }
            case "prism" -> {
                double band = (row + col * 0.8 - time * 3.0);
                int idx = (int) Math.floorMod((long) Math.floor(band / 2.5), 4L);
                int[] colors = {0x5FD6CD, 0x6A9EE5, 0x9C82DB, 0xDC90BD};
                return colors[idx];
            }
            case "tide" -> {
                double wave = Math.sin(col * 0.8 + time * 2.5) * 2.5;
                double pos = (row + wave);
                if (Math.abs(Math.IEEEremainder(pos, 5.0)) < 0.7) return 0xE2F7FF;
                return pos % 5.0 < 2.5 ? 0x55B7D9 : 0x1E3A56;
            }
            case "eclipse" -> {
                double dist = Math.hypot(col - 4.5, row - 6.0);
                double pulse = Math.sin(time * 2.8) * 0.3;
                if (Math.abs(dist - (3.0 + pulse)) < 0.7) return 0xFFE082;
                return 0x212232;
            }
            case "phantom" -> {
                double wave = Math.sin(time * 3.2 + row * 0.4 + col * 0.2);
                int bright = (int) (180 + 75 * wave);
                return clampRgb(40, bright, (int) (bright * 0.9 + 25));
            }
            case "void_walker" -> {
                double energy = Math.sin(time * 3.5 - row * 0.5 + col * 0.3);
                return energy > 0.3 ? 0xC084FC : 0x581C87;
            }
            case "sakura" -> {
                double petal = (row * 0.8 + col * 1.2 - time * 2.2);
                if (Math.sin(petal * 2.0) > 0.6) return 0xFFFFFF;
                return Math.sin(petal) > 0 ? 0xFFB7C5 : 0x4A1E2A;
            }
            case "hyperion" -> {
                double pulse = (Math.sin(time * 4.0 - Math.hypot(col - 4.5, row - 7.0) * 0.7) + 1.0) * 0.5;
                return clampRgb((int) (210 + 45 * pulse), (int) (150 + 60 * pulse), 30);
            }
            case "cyberpunk" -> {
                int scanRow = (int) Math.floorMod((long) Math.floor(time * 12.0), 16L);
                if (row == scanRow || row == (scanRow + 1) % 16) return 0x38BDF8;
                return (col % 2 == 0 && row % 2 == 0) ? 0x0284C7 : 0x0F172A;
            }
            case "frostbite" -> {
                double glint = Math.sin(time * 5.0 + col * 7.0 + row * 13.0);
                return glint > 0.5 ? 0xFFFFFF : (row + col) % 3 == 0 ? 0x93C5FD : 0x1E3A8A;
            }
            case "dragon_scale" -> {
                double sheen = Math.sin(time * 2.0 + row * 0.3 - col * 0.4);
                return sheen > 0 ? 0x8B5CF6 : 0x10B981;
            }
            case "inferno" -> {
                double lava = Math.sin(row * 0.6 - time * 3.0 + col * 0.4);
                return lava > 0.4 ? 0xFCD34D : lava > -0.2 ? 0xEF4444 : 0x7F1D1D;
            }
            case "starlight" -> {
                double star = Math.sin(time * 4.0 + col * 9.0 + row * 5.0);
                return star > 0.6 ? 0xFEF08A : (row + col) % 4 == 0 ? 0x818CF8 : 0x1E1B4B;
            }
            case "royal_mantle" -> {
                if (row < 2 || col == 0 || col == 9 || row == 15) {
                    double gleam = (Math.sin(time * 3.0 + col * 0.5) + 1.0) * 0.5;
                    return gleam > 0.5 ? 0xFDE047 : 0xD97706;
                }
                return 0x991B1B;
            }
            default -> {
                return baseColor & 0xFFFFFF;
            }
        }
    }

    /** Celestial Halo vertical floating bobbing offset (meters). */
    public static double haloBob(double time) {
        return Math.sin(time * 2.4) * 0.045;
    }

    /** Celestial Halo continuous rotation angle (radians). */
    public static double haloSpin(double time, boolean reverse) {
        double speed = reverse ? -1.4 : 1.6;
        return (time * speed) % (Math.PI * 2.0);
    }

    /** Cat/Fox ear twitch angle (radians). */
    public static double earTwitch(double time, int side) {
        double breath = Math.sin(time * 1.8 + side * 0.3) * Math.toRadians(2.5);
        double period = 3.8;
        double phase = (time + (side > 0 ? 0.0 : 1.4)) % period;
        if (phase < 0.28) {
            double twitch = Math.sin(phase * Math.PI * 14.0) * Math.exp(-phase * 12.0);
            return breath + Math.toRadians(18.0) * twitch * side;
        }
        return breath;
    }

    /** Wing flapping angle (radians) based on player motion state, with organic asymmetric downstroke. */
    public static double wingFlap(double time, double speed, boolean falling, boolean flying) {
        if (falling || flying) {
            double sweep = Math.sin(time * 3.4) * Math.toRadians(9.0) + Math.sin(time * 6.8) * Math.toRadians(2.2);
            return Math.toRadians(34.0) + sweep;
        }
        if (speed > 0.08) {
            double freq = 6.5 + Math.min(6.0, speed * 12.0);
            double amplitude = Math.toRadians(19.0 + Math.min(18.0, speed * 35.0));
            double wave = Math.sin(time * freq) + 0.18 * Math.sin(time * freq * 2.0 - 0.5);
            return Math.toRadians(15.0) + wave * amplitude;
        }
        double gentle = Math.sin(time * 2.1) + 0.16 * Math.sin(time * 4.2 - 0.4);
        return Math.toRadians(11.0) + gentle * Math.toRadians(8.0);
    }

    /** Wing secondary joint lag angle (radians) for sweeping organic feather/pinion flexion. */
    public static double wingTipLag(double time, double speed) {
        double freq = speed > 0.08 ? 7.2 : 2.1;
        return Math.sin(time * freq - 0.52) * Math.toRadians(14.5);
    }

    /** Steampunk gear continuous rotation angle (radians). */
    public static double gearAngle(double time, double speedMult) {
        return (time * 2.2 * speedMult) % (Math.PI * 2.0);
    }

    private static int clampRgb(int r, int g, int b) {
        int cr = Math.max(0, Math.min(255, r));
        int cg = Math.max(0, Math.min(255, g));
        int cb = Math.max(0, Math.min(255, b));
        return (cr << 16) | (cg << 8) | cb;
    }
}
