package dev.skypilot.mod.core;

/**
 * Procedural animation and physics equations for cosmetics.
 * Pure mathematical functions decoupled from Minecraft rendering classes,
 * enabling rigorous testing and shared simulation across world and studio preview.
 */
public final class CosmeticAnimation {
    private CosmeticAnimation() {}

    /**
     * Cape deflection pitch (radians) at a specific row (0..15) down the cape.
     * Incorporates ambient wind waves, forward movement lift, sneak posture, and vertical air drag.
     */
    public static double capePitch(double time, int row, int col, double forwardSpeed, boolean sneaking, double verticalSpeed) {
        double r = Math.max(0, Math.min(15, row));
        double fraction = r / 15.0;

        // Base drape: naturally falls slightly away from shoulders
        double base = Math.toRadians(6.0 + fraction * 4.0);

        // Sneaking forward tilt compensation (prevents cape clipping through legs when crouching)
        if (sneaking) {
            base += Math.toRadians(24.0 * Math.pow(fraction, 0.7));
        }

        // Aerodynamic lift from forward movement (walking / sprinting / flying)
        double speed = Math.max(0, forwardSpeed);
        double lift = Math.min(Math.toRadians(75.0), speed * Math.toRadians(170.0)) * Math.pow(fraction, 0.85);

        // Vertical air resistance (falling pulls cape upwards; jumping pushes cape slightly down)
        double vertLift = 0;
        if (verticalSpeed < -0.05) {
            vertLift = Math.min(Math.toRadians(40.0), -verticalSpeed * Math.toRadians(45.0)) * fraction;
        } else if (verticalSpeed > 0.05) {
            vertLift = -Math.min(Math.toRadians(20.0), verticalSpeed * Math.toRadians(25.0)) * fraction;
        }

        // Ambient wind waves: primary and secondary traveling sine waves propagating downwards
        double windFreq = 3.6 + speed * 4.5;
        double wavePhase = time * windFreq - r * 0.42 + col * 0.12;
        double windAmp = Math.toRadians(3.5 + fraction * 18.0 + speed * 12.0);
        double windWave = Math.sin(wavePhase) * windAmp + Math.cos(time * 2.3 - r * 0.28) * (windAmp * 0.35);

        return base + lift + vertLift + windWave;
    }

    /**
     * Cape roll / sideways flutter (radians) caused by ambient cross-winds and player turning inertia.
     */
    public static double capeRoll(double time, int row, int col, double yawTurnRate) {
        double r = Math.max(0, Math.min(15, row));
        double fraction = r / 15.0;

        // Ambient crosswind flutter
        double crosswind = Math.sin(time * 2.8 - r * 0.35 + col * 0.4) * Math.toRadians(1.5 + fraction * 6.5);

        // Centrifugal roll when player turns their body
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
                // Shimmering aurora borealis gradient flowing upwards
                double phase = (row * 0.15 - time * 1.4 + col * 0.08);
                double t = (Math.sin(phase) + 1.0) * 0.5;
                // Transition: Cyan (0x55D6D2) -> Emerald (0x55EAA6) -> Violet (0x9C82DB) -> Royal Blue (0x4E76E8)
                int r = (int) (85 + 71 * Math.sin(phase) + 40 * Math.cos(phase * 0.7));
                int g = (int) (180 + 54 * Math.cos(phase * 0.9));
                int b = (int) (210 + 45 * Math.sin(phase * 1.2));
                return clampRgb(r, g, b);
            }
            case "midnight" -> {
                // Constellation background with sparkling, pulsating stars
                int seed = (col * 17 + row * 29) % 23;
                if (seed == 0 || (col == 4 && row == 7) || (col == 8 && row == 3)) {
                    double twinkle = Math.sin(time * 4.5 + col * 3.0 + row * 2.0);
                    return twinkle > 0.3 ? 0xFFFFFF : 0xC7D8FF;
                }
                return (row + col) % 7 == 0 ? 0x637ABD : 0x1E273E;
            }
            case "ember" -> {
                // Rising incandescent sparks & heat glow
                double floatY = ((row + time * 4.0) % 16.0);
                if (floatY < 3.0 && (col * 7 + row * 11) % 5 == 0) {
                    return 0xFFF3A8; // white-hot spark
                }
                if (row > 11) return 0x7A2E33; // dark cooling base
                double heat = Math.sin(time * 3.0 + col * 0.5 - row * 0.4);
                return heat > 0.2 ? 0xFFB35A : 0xD76C48;
            }
            case "prism" -> {
                // Diagonal flowing chromatic rainbow ribbon
                double band = (row + col * 0.8 - time * 3.0);
                int idx = Math.floorMod((int) Math.floor(band / 2.5), 4);
                int[] colors = {0x5FD6CD, 0x6A9EE5, 0x9C82DB, 0xDC90BD};
                return colors[idx];
            }
            case "tide" -> {
                // Surging ocean waves with white crest foam
                double wave = Math.sin(col * 0.8 + time * 2.5) * 2.5;
                double pos = (row + wave);
                if (Math.abs(Math.IEEEremainder(pos, 5.0)) < 0.7) {
                    return 0xE2F7FF; // foamy wave crest
                }
                return pos % 5.0 < 2.5 ? 0x55B7D9 : 0x1E3A56;
            }
            case "eclipse" -> {
                // Solar corona flare pulsing with warmth
                double dist = Math.hypot(col - 4.5, row - 6.0);
                double pulse = Math.sin(time * 2.8) * 0.3;
                if (Math.abs(dist - (3.0 + pulse)) < 0.7) {
                    return 0xFFE082; // glowing corona
                }
                return 0x212232; // deep charcoal void
            }
            case "phantom" -> {
                // Ghostly spectral translucent shimmering
                double wave = Math.sin(time * 3.2 + row * 0.4 + col * 0.2);
                int bright = (int) (180 + 75 * wave);
                return clampRgb(40, bright, (int) (bright * 0.9 + 25));
            }
            case "void_walker" -> {
                // Pulsing ender portal void energy
                double energy = Math.sin(time * 3.5 - row * 0.5 + col * 0.3);
                return energy > 0.3 ? 0xC084FC : 0x581C87;
            }
            case "sakura" -> {
                // Falling pink cherry blossom petals
                double petal = (row * 0.8 + col * 1.2 - time * 2.2);
                if (Math.sin(petal * 2.0) > 0.6) return 0xFFFFFF; // white petal edge
                return Math.sin(petal) > 0 ? 0xFFB7C5 : 0x4A1E2A;
            }
            case "hyperion" -> {
                // Radiant golden wither blade explosion crest
                double pulse = (Math.sin(time * 4.0 - Math.hypot(col - 4.5, row - 7.0) * 0.7) + 1.0) * 0.5;
                int red = (int) (210 + 45 * pulse);
                int green = (int) (150 + 60 * pulse);
                return clampRgb(red, green, 30);
            }
            case "cyberpunk" -> {
                // Neon digital grid scanline cascading downward
                int scanRow = (int) (time * 12.0) % 16;
                if (row == scanRow || row == (scanRow + 1) % 16) return 0x38BDF8; // bright cyan beam
                return (col % 2 == 0 && row % 2 == 0) ? 0x0284C7 : 0x0F172A;
            }
            case "frostbite" -> {
                // Glacial ice crystals glittering like diamonds
                double glint = Math.sin(time * 5.0 + col * 7.0 + row * 13.0);
                return glint > 0.5 ? 0xFFFFFF : (row + col) % 3 == 0 ? 0x93C5FD : 0x1E3A8A;
            }
            case "dragon_scale" -> {
                // Shifting reptilian violet-green iridescent sheen
                double sheen = Math.sin(time * 2.0 + row * 0.3 - col * 0.4);
                return sheen > 0 ? 0x8B5CF6 : 0x10B981;
            }
            case "inferno" -> {
                // Molten lava streams flowing and glowing
                double lava = Math.sin(row * 0.6 - time * 3.0 + col * 0.4);
                return lava > 0.4 ? 0xFCD34D : lava > -0.2 ? 0xEF4444 : 0x7F1D1D;
            }
            case "starlight" -> {
                // Cosmic nebulae and stardust
                double star = Math.sin(time * 4.0 + col * 9.0 + row * 5.0);
                return star > 0.6 ? 0xFEF08A : (row + col) % 4 == 0 ? 0x818CF8 : 0x1E1B4B;
            }
            case "royal_mantle" -> {
                // Regal crimson velvet with shimmering gold trim
                if (row < 2 || col == 0 || col == 9 || row == 15) {
                    double gleam = (Math.sin(time * 3.0 + col * 0.5) + 1.0) * 0.5;
                    return gleam > 0.5 ? 0xFDE047 : 0xD97706; // shimmering gold
                }
                return 0x991B1B; // rich crimson velvet
            }
            default -> {
                return baseColor;
            }
        }
    }

    /**
     * Celestial Halo vertical floating bobbing offset (meters).
     */
    public static double haloBob(double time) {
        return Math.sin(time * 2.4) * 0.045;
    }

    /**
     * Celestial Halo continuous rotation angle (radians).
     */
    public static double haloSpin(double time, boolean reverse) {
        double speed = reverse ? -1.4 : 1.6;
        return (time * speed) % (Math.PI * 2.0);
    }

    /**
     * Cat ear twitch angle (radians).
     * Naturally relaxed most of the time, with realistic organic twitches every ~3.5 seconds.
     */
    public static double earTwitch(double time, int side) {
        // Natural gentle breathing sway
        double breath = Math.sin(time * 1.8 + side * 0.3) * Math.toRadians(2.5);

        // Periodic twitch impulse
        double period = 3.8;
        double phase = (time + (side > 0 ? 0.0 : 1.4)) % period;
        if (phase < 0.28) {
            double twitch = Math.sin(phase * Math.PI * 14.0) * Math.exp(-phase * 12.0);
            return breath + Math.toRadians(18.0) * twitch * side;
        }
        return breath;
    }

    /**
     * Wing flapping angle (radians) based on player motion state.
     * Gentle floating beat while idle, rapid powerful flap when running/jumping, wide soar when gliding.
     */
    public static double wingFlap(double time, double speed, boolean falling, boolean flying) {
        if (falling || flying) {
            // Soaring posture: extended wide with majestic slow beat
            return Math.toRadians(32.0) + Math.sin(time * 3.2) * Math.toRadians(8.0);
        }
        if (speed > 0.08) {
            // Active movement: dynamic fast wing beat proportional to speed
            double freq = 6.5 + Math.min(6.0, speed * 12.0);
            double amplitude = Math.toRadians(18.0 + Math.min(18.0, speed * 35.0));
            return Math.toRadians(14.0) + Math.sin(time * freq) * amplitude;
        }
        // Idle gentle breathing flap
        return Math.toRadians(10.0) + Math.sin(time * 2.0) * Math.toRadians(7.5);
    }

    /**
     * Wing secondary joint lag angle (radians) for organic feather/pinion flexion.
     */
    public static double wingTipLag(double time, double speed) {
        double freq = speed > 0.08 ? 7.0 : 2.0;
        return Math.sin(time * freq - 0.45) * Math.toRadians(12.0);
    }

    /**
     * Steampunk gear continuous rotation angle (radians).
     */
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

