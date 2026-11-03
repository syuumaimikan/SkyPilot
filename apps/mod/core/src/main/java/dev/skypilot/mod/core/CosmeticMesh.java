package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.List;

/** Original voxel designs in player-local coordinates, with independently combinable slots. */
public final class CosmeticMesh {
    public record Voxel(double x, double y, double z, double size, int color) {}
    private CosmeticMesh() {}

    public static List<Voxel> build(SkyPilotConfig.Appearance a) {
        List<Voxel> out = new ArrayList<>();

        // ------------------------------------------------------------ Capes (16)
        if (!"none".equals(a.cape)) {
            for (int row = 0; row < 16; row++) {
                for (int col = 0; col < 10; col++) {
                    int color = switch (a.cape) {
                        case "ember" -> row > 10 ? 0x8B3D42 : (col + row * 3) % 7 < 2 ? 0xFFD58A : 0xD76C48;
                        case "midnight" -> (col * 13 + row * 7) % 23 == 0 ? 0xDCE9FF : ((row + col) % 7 == 0 ? 0x637ABD : 0x27334F);
                        case "prism" -> new int[]{0x5FD6CD, 0x6A9EE5, 0x9C82DB, 0xDC90BD}[(row + col) / 4 % 4];
                        case "tide" -> (row + Math.round(Math.sin(col * 0.8) * 2)) % 5 < 2 ? 0x55B7D9 : 0x234566;
                        case "eclipse" -> Math.abs(Math.hypot(col - 4.5, row - 6) - 3) < 0.65 ? 0xEAC574 : 0x252639;
                        case "phantom" -> (row + col) % 3 == 0 ? 0x3CE5D0 : ((row + col) % 2 == 0 ? 0x228B83 : 0x1B254B);
                        case "void_walker" -> ((col * 5 + row * 11) % 17 < 3) ? 0xC084FC : ((row + col) % 4 == 0 ? 0x7E22CE : 0x2E1065);
                        case "sakura" -> ((col * 7 + row * 13) % 19 < 3) ? 0xFFD1DC : ((row + col) % 5 == 0 ? 0xFFB7C5 : 0x4A1E2A);
                        case "hyperion" -> Math.abs(Math.hypot(col - 4.5, row - 7) - 3) < 0.8 ? 0xFBBF24 : ((col == 4 || col == 5 || row == 7) ? 0xF59E0B : 0x1F2937);
                        case "cyberpunk" -> (row % 4 == 0 || col % 3 == 0) ? 0x06B6D4 : ((row + col) % 2 == 0 ? 0x0369A1 : 0x0F172A);
                        case "frostbite" -> ((col * 11 + row * 17) % 13 == 0) ? 0xFFFFFF : ((row + col) % 3 == 0 ? 0x93C5FD : 0x1E3A8A);
                        case "dragon_scale" -> (row + col / 2) % 3 == 0 ? 0xA855F7 : ((row + col) % 2 == 0 ? 0x7C3AED : 0x3B0764);
                        case "inferno" -> (col == 4 || col == 5 || (row + col) % 4 == 0) ? 0xFCD34D : (row > 10 ? 0x991B1B : 0xDC2626);
                        case "starlight" -> ((col * 9 + row * 5) % 11 == 0) ? 0xFEF08A : ((row + col) % 4 == 0 ? 0x818CF8 : 0x1E1B4B);
                        case "royal_mantle" -> (row < 2 || col == 0 || col == 9 || row == 15) ? 0xFDE047 : 0x991B1B;
                        default -> (col + row / 2) % 6 < 2 ? 0x55D6D2 : (row + col) % 4 == 0 ? 0x6976CD : a.color;
                    };
                    out.add(new Voxel((col - 4.5) * 0.052, 1.46 - row * 0.057, -0.24 - row * 0.008, 0.055, color));
                }
            }
        }

        // ------------------------------------------------------------ Head / Hats (16)
        String hat = "none".equals(a.hat) ? a.accessory : a.hat;
        if ("crown".equals(hat)) {
            int n = 20;
            for (int i = 0; i < n; i++) {
                double angle = i * Math.PI * 2 / n;
                out.add(new Voxel(Math.cos(angle) * 0.28, 1.92 + (i % 5 == 0 ? 0.12 : 0), Math.sin(angle) * 0.28, 0.055, 0xF5CD75));
                if (i % 5 == 0) out.add(new Voxel(Math.cos(angle) * 0.28, 1.96, Math.sin(angle) * 0.28, 0.04, 0x6DCFCB));
            }
        } else if ("halo".equals(hat)) {
            int n = 28;
            for (int i = 0; i < n; i++) {
                double angle = i * Math.PI * 2 / n;
                out.add(new Voxel(Math.cos(angle) * 0.28, 2.07, Math.sin(angle) * 0.28, 0.055, 0xF5CD75));
            }
        } else if ("dark_halo".equals(hat)) {
            int n = 28;
            for (int i = 0; i < n; i++) {
                double angle = i * Math.PI * 2 / n;
                int col = i % 3 == 0 ? 0x8B5CF6 : 0x1E112A;
                out.add(new Voxel(Math.cos(angle) * 0.28, 2.07, Math.sin(angle) * 0.28, 0.055, col));
            }
        } else if ("angel_halo".equals(hat)) {
            // Concentric double halo with floating central star
            for (int i = 0; i < 20; i++) {
                double a1 = i * Math.PI * 2 / 20;
                out.add(new Voxel(Math.cos(a1) * 0.22, 2.05, Math.sin(a1) * 0.22, 0.045, 0xFEF08A));
            }
            for (int i = 0; i < 26; i++) {
                double a2 = i * Math.PI * 2 / 26;
                out.add(new Voxel(Math.cos(a2) * 0.32, 2.08, Math.sin(a2) * 0.32, 0.045, 0xFDE047));
            }
            out.add(new Voxel(0, 2.065, 0, 0.055, 0xFFFFFF));
        } else if ("ears".equals(hat)) {
            for (int side : new int[]{-1, 1}) {
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 4 - row; col++) {
                        out.add(new Voxel(side * (0.12 + col * 0.043), 1.88 + row * 0.048, 0, 0.05, col == 1 ? 0xC799E6 : 0x30384F));
                    }
                }
            }
        } else if ("horns".equals(hat)) {
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < 9; i++) {
                    out.add(new Voxel(side * (0.22 + Math.sin(i * 0.23) * 0.11), 1.86 + i * 0.035, -i * 0.018, 0.066 - i * 0.004, i > 5 ? 0xB39CF5 : 0x454259));
                }
            }
        } else if ("dragon_horns".equals(hat)) {
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < 11; i++) {
                    double angle = i * 0.28;
                    out.add(new Voxel(side * (0.24 + Math.sin(angle) * 0.14), 1.86 + i * 0.038, -i * 0.024 + Math.cos(angle) * 0.04, 0.07 - i * 0.0035, i > 7 ? 0xC084FC : 0x581C87));
                }
            }
        } else if ("valkyrie".equals(hat)) {
            // Golden circlet
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI * 2 / 16;
                out.add(new Voxel(Math.cos(angle) * 0.27, 1.90, Math.sin(angle) * 0.27, 0.045, 0xFBBF24));
            }
            // Winged side feathers
            for (int side : new int[]{-1, 1}) {
                for (int f = 0; f < 5; f++) {
                    out.add(new Voxel(side * (0.28 + f * 0.025), 1.92 + f * 0.04, -f * 0.02, 0.05, f == 4 ? 0xFFFBEB : 0xF59E0B));
                }
            }
        } else if ("wizard".equals(hat)) {
            // Brim
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI * 2 / 20;
                out.add(new Voxel(Math.cos(angle) * 0.32, 1.89, Math.sin(angle) * 0.32, 0.05, 0x312E81));
            }
            // Cone stack
            for (int level = 0; level < 7; level++) {
                int count = Math.max(4, 14 - level * 2);
                double r = (0.24 - level * 0.032);
                for (int i = 0; i < count; i++) {
                    double angle = i * Math.PI * 2 / count;
                    out.add(new Voxel(Math.cos(angle) * r, 1.93 + level * 0.045, Math.sin(angle) * r, 0.045, level == 3 ? 0xFDE047 : 0x4338CA));
                }
            }
            out.add(new Voxel(0, 2.25, 0, 0.05, 0xFDE047));
        } else if ("kitsune".equals(hat)) {
            // Slanted spirit fox mask on side of head
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 4; c++) {
                    int col = (r == 2 && c == 2) ? 0xEF4444 : ((r == 0 && c == 3) ? 0xDC2626 : 0xFFFFFF);
                    out.add(new Voxel(0.24 + c * 0.03, 1.85 + r * 0.04, 0.15 - c * 0.02, 0.045, col));
                }
            }
            // Floating spirit flame
            out.add(new Voxel(0.38, 2.05, 0.12, 0.06, 0x38BDF8));
        } else if ("cyber_visor".equals(hat)) {
            for (int c = -4; c <= 4; c++) {
                for (int r = 0; r < 3; r++) {
                    int col = (r == 1) ? 0x38BDF8 : 0x0284C7;
                    out.add(new Voxel(c * 0.048, 1.80 + r * 0.035, 0.26, 0.042, col));
                }
            }
        } else if ("tophat".equals(hat)) {
            // Brim
            for (int i = 0; i < 22; i++) {
                double angle = i * Math.PI * 2 / 22;
                out.add(new Voxel(Math.cos(angle) * 0.33, 1.89, Math.sin(angle) * 0.33, 0.05, 0x27272A));
            }
            // Cylinder body
            for (int h = 0; h < 5; h++) {
                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI * 2 / 16;
                    int col = (h == 1) ? 0xD97706 : 0x18181B; // brass ribbon
                    out.add(new Voxel(Math.cos(angle) * 0.24, 1.93 + h * 0.042, Math.sin(angle) * 0.24, 0.045, col));
                }
            }
            // Gear accent
            out.add(new Voxel(0.26, 1.97, 0, 0.045, 0xF59E0B));
        } else if ("sakura_blossom".equals(hat)) {
            for (int i = 0; i < 5; i++) {
                double angle = i * Math.PI * 2 / 5;
                out.add(new Voxel(0.26 + Math.cos(angle) * 0.06, 1.94 + Math.sin(angle) * 0.06, 0.08, 0.045, 0xF472B6));
            }
            out.add(new Voxel(0.26, 1.94, 0.08, 0.04, 0xFFF1F2));
            // Orbiting petal
            out.add(new Voxel(-0.25, 2.05, 0.15, 0.038, 0xFBCFE8));
        } else if ("wither_gaze".equals(hat)) {
            for (int s = 0; s < 3; s++) {
                double angle = s * Math.PI * 2 / 3;
                double sx = Math.cos(angle) * 0.38, sz = Math.sin(angle) * 0.38;
                out.add(new Voxel(sx, 2.06, sz, 0.07, 0x18181B));
                out.add(new Voxel(sx + Math.cos(angle) * 0.03, 2.08, sz + Math.sin(angle) * 0.03, 0.035, 0xA855F7));
            }
        } else if ("flower_crown".equals(hat)) {
            int n = 18;
            for (int i = 0; i < n; i++) {
                double angle = i * Math.PI * 2 / n;
                int col = (i % 3 == 0) ? 0xF43F5E : ((i % 3 == 1) ? 0xFBBF24 : 0x10B981);
                out.add(new Voxel(Math.cos(angle) * 0.27, 1.90, Math.sin(angle) * 0.27, 0.045, col));
            }
        } else if ("demon_horns".equals(hat)) {
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < 9; i++) {
                    out.add(new Voxel(side * (0.20 + Math.sin(i * 0.22) * 0.12), 1.86 + i * 0.038, -i * 0.02, 0.068 - i * 0.004, i > 5 ? 0xF97316 : 0x7F1D1D));
                }
            }
        }

        // ------------------------------------------------------------ Wings & Backwear (16)
        String back = "none".equals(a.back) ? a.accessory : a.back;
        if (List.of("wings", "dragon", "phoenix").contains(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < 18; i++) {
                    double x = 0.22 + i * 0.04, y = 1.4 + Math.sin(i * Math.PI / 22) * 0.5;
                    int base = "phoenix".equals(back) ? 0xF09055 : a.color;
                    out.add(new Voxel(side * x, y, -0.29, 0.072, i % 4 == 0 ? 0xE0D4FC : base));
                    int length = "dragon".equals(back) ? (int) (5 + Math.sin(i * Math.PI / 18) * 8) : 5 + (i % 5);
                    for (int j = 1; j <= length; j++) {
                        if ("dragon".equals(back) && i > 12 && j > length - (i - 12)) continue;
                        int color = "dragon".equals(back) ? (j == length ? 0xC7AFF6 : 0x61518A) : ("phoenix".equals(back) ? (j > 5 ? 0xF9CB73 : 0xB95748) : (j % 3 == 0 ? 0x8071B8 : 0xA38BFA));
                        out.add(new Voxel(side * (x + j * 0.015), y - j * 0.043, -0.3 - j * 0.003, 0.047, color));
                    }
                }
            }
        } else if ("fairy".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                // Upper wing
                for (int i = 0; i < 12; i++) {
                    double x = 0.18 + i * 0.038, y = 1.45 + Math.sin(i * Math.PI / 14) * 0.42;
                    out.add(new Voxel(side * x, y, -0.28, 0.055, 0x38BDF8));
                    for (int j = 1; j <= 4; j++) {
                        out.add(new Voxel(side * (x + j * 0.012), y - j * 0.038, -0.29, 0.042, 0xBAE6FD));
                    }
                }
                // Lower wing
                for (int i = 0; i < 8; i++) {
                    double x = 0.15 + i * 0.03, y = 1.25 - i * 0.032;
                    out.add(new Voxel(side * x, y, -0.28, 0.048, 0x7DD3FC));
                }
            }
        } else if ("cyber_wings".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int b = 0; b < 3; b++) {
                    for (int s = 0; s < 7; s++) {
                        double x = 0.20 + b * 0.06 + s * 0.04;
                        double y = 1.50 - b * 0.12 + s * 0.025;
                        out.add(new Voxel(side * x, y, -0.29, 0.052, s == 6 ? 0x38BDF8 : 0x1E293B));
                    }
                }
                // Thruster cone
                out.add(new Voxel(side * 0.18, 1.25, -0.30, 0.065, 0x06B6D4));
                out.add(new Voxel(side * 0.18, 1.18, -0.30, 0.05, 0x38BDF8));
            }
        } else if ("angel_wings".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int layer = 0; layer < 3; layer++) {
                    int count = 10 - layer * 2;
                    for (int i = 0; i < count; i++) {
                        double x = 0.18 + i * 0.038;
                        double y = 1.55 - layer * 0.18 + Math.sin(i * Math.PI / count) * 0.35;
                        out.add(new Voxel(side * x, y, -0.28 - layer * 0.02, 0.058, (i + layer) % 3 == 0 ? 0xFEF08A : 0xFFFFFF));
                    }
                }
            }
        } else if ("demon_wings".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int f = 0; f < 4; f++) {
                    for (int s = 0; s < 8; s++) {
                        double x = 0.18 + f * 0.07 + s * 0.035;
                        double y = 1.60 - f * 0.12 - s * 0.04;
                        int col = (s == 7) ? 0xEF4444 : ((s == 0) ? 0x18181B : 0x7F1D1D);
                        out.add(new Voxel(side * x, y, -0.29, 0.052, col));
                    }
                }
            }
        } else if ("crystal_wings".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int c = 0; c < 7; c++) {
                    double x = 0.22 + c * 0.05, y = 1.55 - c * 0.05 + Math.sin(c) * 0.12;
                    out.add(new Voxel(side * x, y, -0.29, 0.065, (c % 2 == 0) ? 0xC084FC : 0xE9D5FF));
                }
            }
        } else if ("steampunk".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                // Brass framework
                for (int i = 0; i < 11; i++) {
                    double x = 0.18 + i * 0.042, y = 1.48 + Math.sin(i * 0.28) * 0.22;
                    out.add(new Voxel(side * x, y, -0.29, 0.055, 0xB45309));
                    out.add(new Voxel(side * x, y - 0.14, -0.30, 0.045, 0xD97706));
                }
                out.add(new Voxel(side * 0.18, 1.48, -0.31, 0.07, 0xF59E0B)); // Central gear
            }
        } else if ("void_tendrils".equals(back)) {
            for (int t = 0; t < 6; t++) {
                int side = (t % 2 == 0) ? -1 : 1;
                for (int s = 0; s < 9; s++) {
                    double x = side * (0.12 + Math.sin(s * 0.4 + t) * 0.18);
                    double y = 1.45 - (t / 2) * 0.16 - s * 0.042;
                    double z = -0.28 - s * 0.024;
                    out.add(new Voxel(x, y, z, 0.058 - s * 0.003, s > 6 ? 0xC084FC : 0x3B0764));
                }
            }
        } else if ("katana_scabbard".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < 14; i++) {
                    double x = side * (-0.22 + i * 0.035);
                    double y = 1.62 - i * 0.048;
                    int col = (i < 3) ? 0xD97706 : ((i > 11) ? 0x38BDF8 : 0x1E293B);
                    out.add(new Voxel(x, y, -0.28, 0.048, col));
                }
            }
        } else if ("quiver".equals(back)) {
            // Quiver cylinder
            for (int h = 0; h < 8; h++) {
                out.add(new Voxel(0.12, 1.15 + h * 0.045, -0.28, 0.065, 0x78350F));
            }
            // Arrow fletchings protruding from top
            out.add(new Voxel(0.10, 1.54, -0.28, 0.045, 0x14B8A6));
            out.add(new Voxel(0.14, 1.56, -0.27, 0.045, 0x2DD4BF));
            out.add(new Voxel(0.12, 1.58, -0.29, 0.045, 0x5EEAD4));
        } else if ("jetpack".equals(back)) {
            for (int side : new int[]{-1, 1}) {
                for (int h = 0; h < 6; h++) {
                    out.add(new Voxel(side * 0.16, 1.20 + h * 0.05, -0.30, 0.075, 0x475569));
                }
                // Thruster flame
                out.add(new Voxel(side * 0.16, 1.13, -0.30, 0.055, 0xF97316));
                out.add(new Voxel(side * 0.16, 1.07, -0.30, 0.04, 0xFDE047));
            }
            out.add(new Voxel(0, 1.35, -0.29, 0.07, 0x334155)); // Cross brace
        } else if ("backpack".equals(back)) {
            for (int r = 0; r < 5; r++) {
                for (int c = -2; c <= 2; c++) {
                    out.add(new Voxel(c * 0.052, 1.18 + r * 0.055, -0.32, 0.062, (r == 4) ? 0x1E3A8A : 0x78350F));
                }
            }
            // Lit lantern hanging on left
            out.add(new Voxel(-0.18, 1.18, -0.32, 0.055, 0xFDE047));
        } else if ("shield_crest".equals(back)) {
            for (int r = 0; r < 7; r++) {
                int width = (r < 4) ? 5 : (7 - r);
                for (int c = -width / 2; c <= width / 2; c++) {
                    int col = (c == 0 || r == 2) ? 0xF59E0B : 0x2563EB;
                    out.add(new Voxel(c * 0.055, 1.48 - r * 0.055, -0.30, 0.058, col));
                }
            }
        } else if ("solar_cloak".equals(back)) {
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI * 2 / 16;
                double r = 0.25 + (i % 2 == 0 ? 0.10 : 0.04);
                out.add(new Voxel(Math.cos(angle) * r, 1.40 + Math.sin(angle) * r, -0.29, 0.052, i % 2 == 0 ? 0xFDE047 : 0xEA580C));
            }
        }

        return List.copyOf(out);
    }
}
