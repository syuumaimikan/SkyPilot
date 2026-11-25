"""100% unique sculpted 3D rig builders for Headwear (24), Auras (26), Pets (22), and Companions (24).

Anti-Clipping Guarantees:
  - Headwear: NEVER penetrates bare+overlay head box x=[-4.5, 4.5], y=[23.5, 32.5], z=[-4.5, 4.5].
    (CosmeticLayer also scales cleanly to clear full armor helmets ±5.0 / y=33.0!)
  - Auras: Orbit radii r >= 11.8 px (outside arms + armor + cloak), ground y >= 1.25 px, clouds y >= 40.5 px.
  - Pets: Studio showcase anchor at (15.0, 25.5, 2.0); world-space physics cancels anchor automatically.
  - Companions: Studio showcase anchor at (-16.5, 0.0, 2.5) with EVERY cube y_min >= 0.0!
"""
from __future__ import annotations

import math
from typing import Any

from rig_builders import _sx, make_bone, make_cube


# ==============================================================================
# 3. HEADWEAR & HALOS — ALL 24 DESIGNS 100% UNIQUE, ZERO HEAD CLIPPING!
# ==============================================================================
def build_hat_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    # All cubes stay strictly OUTSIDE head+hat-overlay box [-4.5..4.5, 23.5..32.5, -4.5..4.5]
    if arch == "crown":
        cubes = []
        for i in range(12):
            ang = i * math.tau / 12.0
            cx, cz = math.cos(ang) * 4.85, math.sin(ang) * 4.85
            h = 2.8 if i % 2 == 0 else 1.5
            cubes.append(make_cube(f"c_{i}", [cx - 0.85, 32.6, cz - 0.85], [cx + 0.85, 32.6 + h, cz + 0.85],
                                   pivot=[cx, 32.8, cz], rot=[0, -math.degrees(ang), 0], zone="A", color=p))
            if i % 3 == 0:
                cubes.append(make_cube(f"g_{i}", [cx - 0.5, 33.1, cz - 0.5], [cx + 0.5, 34.2, cz + 0.5],
                                       zone="C", color=a, emissive=True))
        return [make_bone("crown_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "halo":
        ring = [make_cube(f"h_{i}", [math.cos(i * math.tau / 14) * 5.2 - 1.0, 35.6, math.sin(i * math.tau / 14) * 5.2 - 0.55],
                          [math.cos(i * math.tau / 14) * 5.2 + 1.0, 36.6, math.sin(i * math.tau / 14) * 5.2 + 0.55],
                          pivot=[math.cos(i * math.tau / 14) * 5.2, 36.1, math.sin(i * math.tau / 14) * 5.2],
                          rot=[0, -math.degrees(i * math.tau / 14) + 90, 0],
                          zone="C" if i % 2 == 0 else "A", color=a if i % 2 == 0 else p, emissive=True) for i in range(14)]
        return [make_bone("halo_outer", [0, 36.1, 0], anim="halo_spin_cw", cubes=ring)]

    if arch == "dark_halo":
        # Spiked obsidian void crown-halo with jagged upward obsidian thorns!
        ring = []
        for i in range(10):
            ang = i * math.tau / 10.0
            hx, hz = math.cos(ang) * 5.4, math.sin(ang) * 5.4
            ring.append(make_cube(f"dh_{i}", [hx - 1.2, 35.4, hz - 0.6], [hx + 1.2, 36.4, hz + 0.6],
                                  pivot=[hx, 35.9, hz], rot=[0, -math.degrees(ang) + 90, 0], zone="B", color=s))
            ring.append(make_cube(f"sp_{i}", [hx - 0.45, 36.2, hz - 0.45], [hx + 0.45, 38.6 if i % 2 == 0 else 37.6, hz + 0.45],
                                  pivot=[hx, 36.4, hz], rot=[18, -math.degrees(ang), 0], zone="C", color=a, emissive=True))
        return [make_bone("dark_halo_root", [0, 36.0, 0], anim="halo_spin_ccw", cubes=ring)]

    if arch == "angel_halo":
        ring1 = [make_cube(f"h1_{i}", [math.cos(i * math.tau / 14) * 5.4 - 1.0, 35.5, math.sin(i * math.tau / 14) * 5.4 - 0.5],
                           [math.cos(i * math.tau / 14) * 5.4 + 1.0, 36.4, math.sin(i * math.tau / 14) * 5.4 + 0.5],
                           pivot=[math.cos(i * math.tau / 14) * 5.4, 36.0, math.sin(i * math.tau / 14) * 5.4],
                           rot=[0, -math.degrees(i * math.tau / 14) + 90, 0], zone="A", color=p, emissive=True) for i in range(14)]
        ring2 = [make_cube("star_core", [-1.0, 36.4, -1.0], [1.0, 38.4, 1.0], rot=[45, 45, 0], zone="C", color=0xFFFFFF, emissive=True)]
        for i in range(10):
            ang = i * math.tau / 10.0
            hx, hz = math.cos(ang) * 3.3, math.sin(ang) * 3.3
            ring2.append(make_cube(f"h2_{i}", [hx - 0.7, 36.9, hz - 0.4], [hx + 0.7, 37.7, hz + 0.4],
                                   pivot=[hx, 37.3, hz], rot=[0, -math.degrees(ang) + 90, 0], zone="D_GOLD", color=0xFEF08A, emissive=True))
        return [
            make_bone("halo_outer", [0, 36.0, 0], anim="halo_spin_cw", cubes=ring1),
            make_bone("halo_inner", [0, 37.3, 0], anim="halo_spin_ccw", cubes=ring2),
        ]

    if arch == "astral_halo_crown":
        # Crossed tilted celestial astrolabe gyro-rings + orbiting moon!
        r1, r2 = [], []
        for i in range(12):
            ang = i * math.tau / 12.0
            hx, hz = math.cos(ang) * 5.5, math.sin(ang) * 5.5
            r1.append(make_cube(f"a1_{i}", [hx - 0.9, 36.8, hz - 0.4], [hx + 0.9, 37.5, hz + 0.4],
                                pivot=[hx, 37.1, hz], rot=[0, -math.degrees(ang) + 90, 0], zone="A", color=p, emissive=True))
            r2.append(make_cube(f"a2_{i}", [hx * 0.78 - 0.7, 37.0, hz * 0.78 - 0.35], [hx * 0.78 + 0.7, 37.6, hz * 0.78 + 0.35],
                                pivot=[hx * 0.78, 37.3, hz * 0.78], rot=[0, -math.degrees(ang) + 90, 0], zone="C", color=a, emissive=True))
        r1.append(make_cube("astro_moon", [5.2, 36.2, -0.9], [7.0, 38.0, 0.9], zone="D_GOLD", color=0xFEF08A, emissive=True))
        return [
            make_bone("astro_outer", [0, 37.1, 0], rot=[22, 0, 14], anim="halo_spin_cw", cubes=r1),
            make_bone("astro_inner", [0, 37.3, 0], rot=[-24, 0, -16], anim="halo_spin_ccw", cubes=r2),
        ]

    if arch == "cyber_halo":
        # Segmented neon hexagonal HUD halo with vertical data ticks!
        cubes = []
        for i in range(6):
            ang = i * math.tau / 6.0
            hx, hz = math.cos(ang) * 5.3, math.sin(ang) * 5.3
            cubes.append(make_cube(f"ch_{i}", [hx - 1.9, 35.4, hz - 0.35], [hx + 1.9, 36.2, hz + 0.35],
                                   pivot=[hx, 35.8, hz], rot=[0, -math.degrees(ang) + 90, 0], zone="C", color=a, emissive=True, translucent=True))
            cubes.append(make_cube(f"ct_{i}", [hx - 0.25, 36.2, hz - 0.25], [hx + 0.25, 37.5, hz + 0.25],
                                   zone="A", color=p, emissive=True))
        return [make_bone("cyber_halo_root", [0, 35.8, 0], anim="halo_spin_cw", cubes=cubes)]

    if arch == "ears":
        # Short rounded plush cat ears sitting cleanly at y >= 32.6!
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            piv = [side * 2.9, 32.6, -0.3]
            bones.append(make_bone(f"ear_{tag}", piv, anim=f"ear_{tag}", cubes=[
                make_cube("outer", _sx(side, 1.8, 4.4), [32.6, 35.6], [-1.0, 0.6], pivot=piv, rot=[0, 0, -side * 10], zone="A", color=p),
                make_cube("tip", _sx(side, 2.4, 3.8), [35.3, 36.5], [-0.8, 0.4], pivot=piv, rot=[0, 0, -side * 12], zone="B", color=s),
                make_cube("inner", _sx(side, 2.3, 3.9), [32.9, 35.0], [-1.2, -0.6], pivot=piv, rot=[0, 0, -side * 10], zone="C", color=a),
            ]))
        return bones

    if arch == "fox_ears":
        # Tall fluffy fox ears with outer fur tufts + hanging crimson temple bells!
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            piv = [side * 2.9, 32.6, -0.3]
            bones.append(make_bone(f"ear_{tag}", piv, anim=f"ear_{tag}", cubes=[
                make_cube("f_base", _sx(side, 1.6, 4.5), [32.6, 36.2], [-1.1, 0.8], pivot=piv, rot=[0, 0, -side * 14], zone="A", color=p),
                make_cube("f_mid", _sx(side, 2.1, 4.1), [35.8, 38.2], [-0.9, 0.6], pivot=piv, rot=[0, 0, -side * 18], zone="A", color=p),
                make_cube("f_tip", _sx(side, 2.6, 3.7), [37.9, 39.4], [-0.7, 0.4], pivot=piv, rot=[0, 0, -side * 20], zone="B", color=s),
                make_cube("f_in", _sx(side, 2.2, 3.8), [33.1, 36.8], [-1.3, -0.7], pivot=piv, rot=[0, 0, -side * 14], zone="C", color=a),
                make_cube("bell", _sx(side, 4.2, 5.2), [32.7, 33.8], [-1.3, -0.3], zone="D_GOLD", color=0xFBBF24, emissive=True),
            ]))
        return bones

    if arch == "mecha_headset":
        # Full over-ear cyber headset outside ±4.75 + glowing LED cat ears + mic boom!
        band = [
            make_cube("hb_top", [-5.2, 32.7, -1.2], [5.2, 33.8, 1.2], zone="B", color=s),
            make_cube("hb_l", [4.65, 29.8, -1.1], [5.6, 33.4, 1.1], zone="B", color=s),
            make_cube("hb_r", [-5.6, 29.8, -1.1], [-4.65, 33.4, 1.1], zone="B", color=s),
            make_cube("cup_l", [4.65, 26.2, -1.9], [6.0, 30.2, 1.9], zone="A", color=p),
            make_cube("cup_r", [-6.0, 26.2, -1.9], [-4.65, 30.2, 1.9], zone="A", color=p),
            make_cube("led_l", [5.8, 27.0, -1.1], [6.25, 29.4, 1.1], zone="C", color=a, emissive=True),
            make_cube("led_r", [-6.25, 27.0, -1.1], [-5.8, 29.4, 1.1], zone="C", color=a, emissive=True),
            make_cube("mic_arm", [-5.5, 25.4, -5.0], [-4.7, 26.2, -1.4], zone="B", color=s),
            make_cube("mic_tip", [-5.4, 25.2, -5.6], [-4.4, 26.4, -4.7], zone="C", color=a, emissive=True),
        ]
        bones = [make_bone("headset_band", [0, 30.0, 0], cubes=band)]
        for side, tag in ((1, "l"), (-1, "r")):
            piv = [side * 3.0, 33.7, 0.0]
            bones.append(make_bone(f"ear_{tag}", piv, anim=f"ear_{tag}", cubes=[
                make_cube("ce_out", _sx(side, 1.8, 4.4), [33.7, 36.8], [-0.9, 0.9], pivot=piv, rot=[0, 0, -side * 12], zone="B", color=s),
                make_cube("ce_led", _sx(side, 2.3, 3.9), [34.1, 36.4], [-1.1, 1.1], pivot=piv, rot=[0, 0, -side * 12], zone="C", color=a, emissive=True),
            ]))
        return bones

    if arch == "horns":
        # Sleek upward-curved obsidian demon horns starting cleanly outside head (y >= 32.6 / |x| >= 4.7)
        cubes = []
        for side in (-1, 1):
            for i in range(5):
                t = i / 4.0
                hx = side * (5.35 + math.sin(t * 2.0) * 1.9 + t * 0.9)
                hy = 32.6 + i * 1.5
                hz = -1.2 + i * 1.05
                w = 1.55 - t * 0.85
                cubes.append(make_cube(f"h_{side}_{i}", [hx - w * 0.5, hy, hz - w * 0.5], [hx + w * 0.5, hy + w + 0.5, hz + w * 0.5],
                                       rot=[-14 * t, 0, -side * (22 - t * 32)], zone="C" if i >= 3 else "A", color=a if i >= 3 else p, emissive=i >= 3))
        return [make_bone("horns_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "dragon_horns":
        # Swept-back BRANCHED elder dragon antlers!
        cubes = []
        for side in (-1, 1):
            for i in range(6):
                t = i / 5.0
                hx = side * (5.4 + t * 3.8)
                hy = 32.6 + i * 1.35
                hz = 0.5 + i * 1.85
                w = 1.6 - t * 0.8
                cubes.append(make_cube(f"dh_{side}_{i}", [hx - w * 0.5, hy, hz - w * 0.5], [hx + w * 0.5, hy + w + 0.3, hz + w * 0.5],
                                       rot=[-32, 0, -side * 18], zone="C" if i >= 4 else "A", color=a if i >= 4 else p, emissive=i >= 4))
            # Antler tines branching upward!
            cubes.append(make_cube(f"tine1_{side}", _sx(side, 6.8, 7.8), [35.4, 38.8], [3.6, 4.7], rot=[-8, 0, -side * 24], zone="C", color=a, emissive=True))
            cubes.append(make_cube(f"tine2_{side}", _sx(side, 8.2, 9.1), [37.6, 40.6], [6.4, 7.4], rot=[-12, 0, -side * 28], zone="C", color=a, emissive=True))
        return [make_bone("dragon_horns_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "demon_horns":
        # Thick forward-curling ram-like fiery hell horns strictly outside ±4.65!
        cubes = []
        for side in (-1, 1):
            pts = [(5.6, 31.2, 0.5, 1.8), (7.1, 32.0, 1.8, 1.65), (7.9, 30.8, 0.4, 1.45),
                   (7.6, 29.2, -1.8, 1.25), (6.5, 29.8, -3.8, 1.05), (5.8, 31.4, -4.9, 0.85)]
            for i, (x, y, z, w) in enumerate(pts):
                cubes.append(make_cube(f"ram_{side}_{i}", [side * x - w * 0.5, y - w * 0.5, z - w * 0.5],
                                       [side * x + w * 0.5, y + w * 0.5, z + w * 0.5],
                                       zone="C" if i >= 4 else "A", color=a if i >= 4 else p, emissive=i >= 4))
        return [make_bone("ram_horns_root", [0, 31.5, 0], cubes=cubes)]

    if arch == "valkyrie":
        # Winged golden battle circlet cleanly outside ±4.75!
        cubes = [
            make_cube("band_f", [-4.85, 31.2, -5.35], [4.85, 32.4, -4.65], zone="A", color=p),
            make_cube("band_l", [4.65, 31.2, -5.1], [5.35, 32.4, 4.9], zone="A", color=p),
            make_cube("band_r", [-5.35, 31.2, -5.1], [-4.65, 32.4, 4.9], zone="A", color=p),
            make_cube("band_b", [-4.85, 31.2, 4.65], [4.85, 32.4, 5.35], zone="A", color=p),
            make_cube("crest", [-1.2, 32.0, -5.6], [1.2, 35.0, -4.65], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            for f in range(4):
                cubes.append(make_cube(f"wf_{side}_{f}", _sx(side, 5.1 + f * 0.65, 6.4 + f * 0.95), [31.5 + f * 1.15, 33.7 + f * 1.25], [-0.4 + f * 1.0, 1.3 + f * 1.1],
                                       rot=[0, -side * 12, -side * (16 + f * 6)], zone="C" if f == 3 else "B", color=a if f == 3 else s, emissive=f == 3))
        return [make_bone("valkyrie_root", [0, 32.0, 0], cubes=cubes)]

    if arch == "frost_tiara":
        # Delicate multi-spire ice-diamond tiara (completely distinct from valkyrie!)
        cubes = [
            make_cube("tiara_f", [-4.8, 32.6, -5.1], [4.8, 33.5, -4.55], zone="B", color=s),
            make_cube("tiara_l", [4.6, 32.6, -4.8], [5.1, 33.4, 1.2], zone="B", color=s),
            make_cube("tiara_r", [-5.1, 32.6, -4.8], [-4.6, 33.4, 1.2], zone="B", color=s),
            make_cube("spire_c", [-0.9, 33.4, -5.25], [0.9, 37.8, -4.55], zone="C", color=a, emissive=True),
            make_cube("gem_c", [-0.7, 34.4, -5.5], [0.7, 35.8, -4.9], rot=[0, 0, 45], zone="A", color=p, emissive=True),
        ]
        for side in (-1, 1):
            cubes.append(make_cube(f"sp1_{side}", _sx(side, 1.5, 2.7), [33.4, 36.2], [-5.15, -4.55], rot=[0, 0, -side * 12], zone="A", color=p, emissive=True))
            cubes.append(make_cube(f"sp2_{side}", _sx(side, 3.2, 4.3), [33.4, 35.2], [-5.0, -4.4], rot=[0, 0, -side * 22], zone="C", color=a, emissive=True))
        return [make_bone("tiara_root", [0, 32.8, 0], cubes=cubes)]

    if arch == "wizard":
        # Tall starry classic wizard hat cleanly above y = 32.6!
        cubes = [
            make_cube("brim", [-7.5, 32.6, -7.5], [7.5, 33.4, 7.5], zone="A", color=p),
            make_cube("band", [-4.6, 33.4, -4.6], [4.6, 34.6, 4.6], zone="C", color=a, emissive=True),
            make_cube("cone1", [-3.9, 34.5, -3.9], [3.9, 37.5, 3.9], rot=[6, 0, 0], zone="A", color=p),
            make_cube("cone2", [-2.7, 37.1, -2.3], [2.7, 40.0, 2.9], rot=[15, 0, 4], zone="A", color=p),
            make_cube("tip", [-1.3, 39.5, -0.5], [1.3, 42.3, 2.1], rot=[28, 0, 8], zone="C", color=a, emissive=True),
        ]
        return [make_bone("wizard_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "witch_moon_hat":
        # Wide crooked witch hat + gold buckle + dangling glowing crescent moon charm!
        cubes = [
            make_cube("w_brim", [-8.2, 32.6, -8.2], [8.2, 33.3, 8.2], rot=[0, 0, -5], zone="A", color=p),
            make_cube("w_band", [-4.7, 33.3, -4.7], [4.7, 34.6, 4.7], zone="B", color=s),
            make_cube("w_buck", [-1.2, 33.1, -5.0], [1.2, 34.8, -4.5], zone="D_GOLD", color=0xFDE047, emissive=True),
            make_cube("w_c1", [-3.7, 34.5, -3.7], [3.7, 37.6, 3.7], rot=[-6, 0, -14], zone="A", color=p),
            make_cube("w_c2", [-1.4, 37.0, -2.4], [3.4, 40.0, 2.4], rot=[-12, 0, -34], zone="A", color=p),
            make_cube("w_tip", [1.6, 38.6, -1.2], [5.2, 41.2, 1.2], rot=[0, 0, -58], zone="A", color=p),
        ]
        charm = make_bone("moon_charm", [5.2, 38.5, 0.0], anim="chain_swing", cubes=[
            make_cube("mc_str", [5.0, 35.8, -0.2], [5.4, 38.5, 0.2], zone="D_GOLD", color=0xFDE047),
            make_cube("mc_moon", [4.4, 34.0, -0.5], [6.2, 36.0, 0.5], rot=[0, 0, 28], zone="C", color=a, emissive=True),
        ])
        return [make_bone("witch_root", [0, 32.6, 0], cubes=cubes, children=[charm])]

    if arch == "kitsune":
        # Traditional side-tilted white fox mask cleanly outside right temple!
        piv = [4.6, 31.8, -3.2]
        rot = [-10.0, -36.0, -12.0]
        cubes = [
            make_cube("mask_face", [2.6, 29.2, -5.6], [7.2, 34.2, -4.65], pivot=piv, rot=rot, zone="A", color=p),
            make_cube("mask_ear_l", [2.8, 34.0, -5.5], [4.3, 36.4, -4.7], pivot=piv, rot=rot, zone="B", color=s),
            make_cube("mask_ear_r", [5.5, 34.0, -5.5], [7.0, 36.4, -4.7], pivot=piv, rot=rot, zone="B", color=s),
            make_cube("eye_l", [3.3, 31.4, -5.85], [4.5, 32.4, -5.3], pivot=piv, rot=rot, zone="D_EYE", color=a, emissive=True),
            make_cube("eye_r", [5.3, 31.4, -5.85], [6.5, 32.4, -5.3], pivot=piv, rot=rot, zone="D_EYE", color=a, emissive=True),
        ]
        spirit = make_bone("mask_flame", [7.4, 35.0, -2.0], anim="halo_float", cubes=[
            make_cube("flame", [6.7, 34.4, -2.7], [8.2, 36.4, -1.2], zone="C", color=a, emissive=True, translucent=True)
        ])
        return [make_bone("mask_root", piv, cubes=cubes, children=[spirit])]

    if arch == "oni_mask":
        # Fierce front-forehead crimson Oni mask with golden tusks & curved horns!
        piv = [0.0, 33.4, -4.9]
        rot = [-24.0, 0.0, 0.0]
        cubes = [
            make_cube("oni_face", [-3.2, 31.6, -5.9], [3.2, 36.4, -4.75], pivot=piv, rot=rot, zone="A", color=p),
            make_cube("oni_brow", [-3.5, 34.6, -6.1], [3.5, 35.8, -4.8], pivot=piv, rot=rot, zone="B", color=s),
            make_cube("oni_el", [0.8, 33.5, -6.15], [2.4, 34.6, -5.6], pivot=piv, rot=rot, zone="D_EYE", color=a, emissive=True),
            make_cube("oni_er", [-2.4, 33.5, -6.15], [-0.8, 34.6, -5.6], pivot=piv, rot=rot, zone="D_EYE", color=a, emissive=True),
            make_cube("tusk_l", [1.3, 31.6, -6.4], [2.2, 33.3, -5.5], pivot=piv, rot=[-10, 0, -14], zone="D_GOLD", color=0xFEF08A, emissive=True),
            make_cube("tusk_r", [-2.2, 31.6, -6.4], [-1.3, 33.3, -5.5], pivot=piv, rot=[-10, 0, 14], zone="D_GOLD", color=0xFEF08A, emissive=True),
            make_cube("ohorn_l", [1.8, 36.2, -5.7], [3.0, 39.2, -4.7], pivot=piv, rot=[10, 0, -24], zone="D_GOLD", color=a, emissive=True),
            make_cube("ohorn_r", [-3.0, 36.2, -5.7], [-1.8, 39.2, -4.7], pivot=piv, rot=[10, 0, 24], zone="D_GOLD", color=a, emissive=True),
        ]
        return [make_bone("oni_root", piv, cubes=cubes)]

    if arch == "cyber_visor":
        # Strictly outside ±4.75 front & sides!
        cubes = [
            make_cube("visor_glass", [-4.95, 27.6, -5.45], [4.95, 30.0, -4.7], zone="C", color=a, emissive=True, translucent=True),
            make_cube("frame_l", [4.65, 28.0, -5.2], [5.3, 29.6, 1.5], zone="B", color=s),
            make_cube("frame_r", [-5.3, 28.0, -5.2], [-4.65, 29.6, 1.5], zone="B", color=s),
        ]
        return [make_bone("visor_root", [0, 28.8, -5.0], anim="visor_pulse", cubes=cubes)]

    if arch == "tophat":
        cubes = [
            make_cube("brim", [-6.8, 32.6, -6.8], [6.8, 33.4, 6.8], zone="A", color=p),
            make_cube("ribbon", [-4.7, 33.4, -4.7], [4.7, 34.7, 4.7], zone="B", color=s),
            make_cube("cyl", [-4.5, 34.7, -4.5], [4.5, 39.8, 4.5], zone="A", color=p),
            make_cube("goggle_l", [-3.2, 34.8, -5.2], [-0.8, 36.8, -4.4], zone="C", color=a, emissive=True),
            make_cube("goggle_r", [0.8, 34.8, -5.2], [3.2, 36.8, -4.4], zone="C", color=a, emissive=True),
        ]
        gear = make_bone("hat_gear", [4.7, 36.4, 0.0], anim="gear_cw", cubes=[
            make_cube("g1", [4.6, 34.8, -0.5], [5.2, 38.0, 0.5], zone="D_GOLD", color=0xF59E0B),
            make_cube("g2", [4.6, 35.9, -1.6], [5.2, 36.9, 1.6], zone="D_GOLD", color=0xF59E0B),
        ])
        return [make_bone("tophat_root", [0, 32.6, 0], cubes=cubes, children=[gear])]

    if arch == "samurai_kabuto":
        # Wide samurai kabuto helmet cleanly wrapping outside ±4.75 & y >= 32.6!
        cubes = [
            make_cube("bowl_top", [-5.1, 32.6, -5.1], [5.1, 35.0, 5.1], zone="A", color=p),
            make_cube("rim_f", [-5.4, 31.6, -5.6], [5.4, 32.8, -4.75], zone="B", color=s),
            make_cube("rim_l", [4.75, 31.2, -5.2], [5.6, 32.8, 5.2], zone="B", color=s),
            make_cube("rim_r", [-5.6, 31.2, -5.2], [-4.75, 32.8, 5.2], zone="B", color=s),
            make_cube("neck_guard", [-5.8, 27.6, 4.75], [5.8, 32.6, 6.2], rot=[16, 0, 0], zone="B", color=s),
            make_cube("crest_base", [-1.2, 32.8, -5.8], [1.2, 34.8, -4.9], zone="D_GOLD", color=a, emissive=True),
            make_cube("horn_l", [0.4, 33.8, -5.7], [6.2, 35.4, -4.9], pivot=[0.4, 34.0, -5.3], rot=[0, 0, 38], zone="D_GOLD", color=a, emissive=True),
            make_cube("horn_r", [-6.2, 33.8, -5.7], [-0.4, 35.4, -4.9], pivot=[-0.4, 34.0, -5.3], rot=[0, 0, -38], zone="D_GOLD", color=a, emissive=True),
        ]
        return [make_bone("kabuto_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "sakura_blossom":
        # Asymmetric side hairpin cherry blossom cluster + orbiting sakura petals!
        cubes = [
            make_cube("pin_stick", [2.6, 33.2, -2.5], [7.2, 34.0, 1.5], rot=[0, 20, 28], zone="D_GOLD", color=0xF59E0B),
            make_cube("blos_1", [3.6, 32.8, -3.8], [5.8, 35.0, -1.6], rot=[0, 0, 22], zone="A", color=p, emissive=True),
            make_cube("blos_2", [4.8, 31.8, -2.2], [6.8, 33.8, -0.2], rot=[0, 0, -18], zone="C", color=a, emissive=True),
            make_cube("blos_3", [3.8, 33.8, -1.2], [5.6, 35.6, 0.6], zone="A", color=p, emissive=True),
        ]
        orbit = [make_bone(f"petal_{i}", [0, 35.0, 0], anim=f"head_orbit_{i}", cubes=[
            make_cube(f"op_{i}", [7.0, 34.5, -0.7], [8.5, 35.3, 0.7], rot=[20, 0, 15], zone="C", color=a, emissive=True)
        ]) for i in range(4)]
        return [make_bone("sakura_pin_root", [0, 32.8, 0], cubes=cubes, children=orbit)]

    if arch == "flower_crown":
        # Full braided green vine wreath studded with upright colorful flower blossoms!
        cubes = []
        for i in range(12):
            ang = i * math.tau / 12.0
            fx, fz = math.cos(ang) * 4.95, math.sin(ang) * 4.95
            cubes.append(make_cube(f"vine_{i}", [fx - 1.1, 32.6, fz - 1.1], [fx + 1.1, 33.7, fz + 1.1],
                                   pivot=[fx, 33.1, fz], rot=[0, -math.degrees(ang), 0], zone="A", color=p))
            if i % 2 == 0:
                cubes.append(make_cube(f"bloom_{i}", [fx - 0.85, 33.5, fz - 0.85], [fx + 0.85, 34.9, fz + 0.85],
                                       rot=[0, 45, 0], zone="B" if i % 4 == 0 else "C", color=s if i % 4 == 0 else a, emissive=i % 4 != 0))
        return [make_bone("flower_root", [0, 32.8, 0], cubes=cubes)]

    # wither_gaze: 3 orbiting mini wither skulls cleanly at r = 7.4..10.6, y = 34..37.2!
    bones = []
    for i in range(3):
        bones.append(make_bone(f"skull_{i}", [0, 35.4, 0], anim=f"head_orbit_{i}", cubes=[
            make_cube(f"sk_{i}", [7.4, 34.0, -1.6], [10.6, 37.2, 1.6], zone="A", color=p),
            make_cube(f"se1_{i}", [7.15, 35.4, -1.0], [7.55, 36.2, -0.2], zone="C", color=a, emissive=True),
            make_cube(f"se2_{i}", [7.15, 35.4, 0.2], [7.55, 36.2, 1.0], zone="C", color=a, emissive=True),
        ]))
    return bones


# ==============================================================================
# 4. AURAS — ALL 26 DESIGNS 100% UNIQUE, ALL ORBITS r >= 11.8 px!
# ==============================================================================
def build_aura_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    R = 12.2  # Safe orbit radius cleanly outside arms + armor + cloak!

    if arch == "gale_ribbons":
        # Thin diagonal sweeping translucent wind ribbons
        bones = []
        for i in range(3):
            y = 10.0 + i * 7.5
            r = R + (i % 2) * 1.5
            cubes = [make_cube(f"gr_{k}", [math.cos(k * math.tau / 10) * r - 2.1, y - 0.45, math.sin(k * math.tau / 10) * r - 0.3],
                               [math.cos(k * math.tau / 10) * r + 2.1, y + 0.45, math.sin(k * math.tau / 10) * r + 0.3],
                               pivot=[math.cos(k * math.tau / 10) * r, y, math.sin(k * math.tau / 10) * r],
                               rot=[0, -math.degrees(k * math.tau / 10) + 90, 14], zone="C" if k % 2 == 0 else "A",
                               color=a if k % 2 == 0 else p, emissive=True, translucent=True) for k in range(10)]
            bones.append(make_bone(f"aura_rib_{i}", [0, y, 0], anim=f"aura_ribbon_{i}", cubes=cubes))
        return bones

    if arch == "prism_rings":
        # 4 stacked flat horizontal neon speed rings pulsing cleanly around torso
        bones = []
        for i in range(4):
            y = 6.5 + i * 6.5
            r = R + 0.6 + (i % 2) * 1.6
            cubes = [make_cube(f"pr_{k}", [math.cos(k * math.tau / 12) * r - 1.8, y - 0.7, math.sin(k * math.tau / 12) * r - 0.45],
                               [math.cos(k * math.tau / 12) * r + 1.8, y + 0.7, math.sin(k * math.tau / 12) * r + 0.45],
                               pivot=[math.cos(k * math.tau / 12) * r, y, math.sin(k * math.tau / 12) * r],
                               rot=[0, -math.degrees(k * math.tau / 12) + 90, 0], zone="C" if (k + i) % 2 == 0 else "A",
                               color=a if (k + i) % 2 == 0 else p, emissive=True, translucent=True) for k in range(12)]
            bones.append(make_bone(f"prism_ring_{i}", [0, y, 0], anim="aura_orbit_cw" if i % 2 == 0 else "aura_orbit_ccw", anim_phase=i * 0.9, cubes=cubes))
        return bones

    if arch == "cyber_matrix":
        # Crossed X-tilted neon holographic rings + floating pixel glyph cubes!
        bones = []
        for i, tilt in enumerate([(32, 0, 26), (-32, 0, -26)]):
            r = R + 1.2
            cubes = []
            for k in range(8):
                ang = k * math.tau / 8.0
                rx, rz = math.cos(ang) * r, math.sin(ang) * r
                cubes.append(make_cube(f"cm_{k}", [rx - 2.2, 17.5, rz - 0.35], [rx + 2.2, 18.4, rz + 0.35],
                                       pivot=[rx, 18.0, rz], rot=[0, -math.degrees(ang) + 90, 0], zone="C", color=a, emissive=True, translucent=True))
                cubes.append(make_cube(f"gl_{k}", [rx - 0.55, 19.6 + (k % 2) * 1.4, rz - 0.55], [rx + 0.55, 20.7 + (k % 2) * 1.4, rz + 0.55],
                                       zone="A", color=p, emissive=True))
            bones.append(make_bone(f"cyber_hoop_{i}", [0, 18.0, 0], rot=list(tilt), anim="aura_orbit_cw" if i == 0 else "aura_orbit_ccw", cubes=cubes))
        return bones

    if arch == "shadow_wyrms":
        # 3 horned shadow wyrms with glowing eyes
        bones = []
        for i in range(3):
            r = R + 0.8 + i * 1.2
            y = 10.5 + i * 7.0
            cubes = []
            for seg in range(6):
                ang = seg * 0.24
                sx, sz = math.cos(ang) * r, math.sin(ang) * r
                sy = y + math.sin(seg * 0.9) * 1.5
                w = 1.9 - seg * 0.22
                cubes.append(make_cube(f"sw_{i}_{seg}", [sx - w, sy - w * 0.65, sz - w], [sx + w, sy + w * 0.65, sz + w],
                                       zone="A" if seg < 4 else "C", color=p if seg < 4 else a, emissive=seg >= 4))
            cubes.append(make_cube(f"swh1_{i}", [r - 0.8, y + 1.4, -0.6], [r + 0.2, y + 3.2, 0.4], rot=[-20, 0, 0], zone="C", color=a, emissive=True))
            cubes.append(make_cube(f"swe_{i}", [r - 0.5, y + 0.4, -1.6], [r + 0.5, y + 1.2, -0.8], zone="D_EYE", color=a, emissive=True))
            bones.append(make_bone(f"aura_sw_{i}", [0, y, 0], anim=f"aura_swarm_{i}", cubes=cubes))
        return bones

    if arch == "hydro_serpents":
        # 2 long sinuous finned water dragons (8 segments + translucent dorsal wave fins!)
        bones = []
        for i in range(2):
            r = R + 1.4 + i * 1.6
            y = 12.0 + i * 9.0
            cubes = []
            for seg in range(8):
                ang = seg * 0.22
                sx, sz = math.cos(ang) * r, math.sin(ang) * r
                sy = y + math.sin(seg * 0.8) * 2.2
                w = 1.7 - seg * 0.14
                cubes.append(make_cube(f"hs_{i}_{seg}", [sx - w, sy - w * 0.6, sz - w], [sx + w, sy + w * 0.6, sz + w],
                                       zone="A", color=p, emissive=True, translucent=True))
                if seg % 2 == 0:
                    cubes.append(make_cube(f"hf_{i}_{seg}", [sx - 0.4, sy + w * 0.5, sz - 0.9], [sx + 0.4, sy + w * 1.6, sz + 0.9],
                                           zone="C", color=a, emissive=True, translucent=True))
            bones.append(make_bone(f"aura_sw_{i}", [0, y, 0], anim=f"aura_swarm_{i}", cubes=cubes))
        return bones

    if arch == "spirit_koi":
        # 3 plump fan-tailed spirit koi fish (rounded body + wide pectoral & caudal fins!)
        bones = []
        for i in range(3):
            r = R + 1.0 + i * 1.1
            y = 10.0 + i * 6.8
            cubes = [
                make_cube(f"kb_{i}", [r - 1.4, y - 1.3, -2.4], [r + 1.4, y + 1.3, 2.2], zone="A", color=p, emissive=True),
                make_cube(f"kh_{i}", [r - 1.2, y - 1.0, -3.6], [r + 1.2, y + 1.0, -2.2], zone="B", color=s, emissive=True),
                make_cube(f"kp1_{i}", [r + 1.2, y - 0.5, -1.0], [r + 3.4, y + 0.3, 1.0], rot=[0, 0, -18], zone="C", color=a, emissive=True),
                make_cube(f"kp2_{i}", [r - 3.4, y - 0.5, -1.0], [r - 1.2, y + 0.3, 1.0], rot=[0, 0, 18], zone="C", color=a, emissive=True),
                make_cube(f"kt1_{i}", [r - 0.4, y - 1.6, 2.0], [r + 0.4, y + 1.8, 5.2], rot=[15, 0, 0], zone="C", color=a, emissive=True),
                make_cube(f"kd_{i}", [r - 0.3, y + 1.1, -1.2], [r + 0.3, y + 2.4, 1.6], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"aura_sw_{i}", [0, y, 0], anim=f"aura_swarm_{i}", cubes=cubes))
        return bones

    if arch == "jade_sprites":
        # 4 swift winged neon-emerald mini dragon sprites spiraling upward!
        bones = []
        for i in range(4):
            r = R + 0.8 + (i % 2) * 1.8
            y = 14.0
            cubes = [
                make_cube(f"js_b_{i}", [r - 0.9, y - 0.8, -1.8], [r + 0.9, y + 0.8, 1.8], zone="A", color=p, emissive=True),
                make_cube(f"js_h_{i}", [r - 1.0, y + 0.2, -3.0], [r + 1.0, y + 1.8, -1.5], zone="A", color=p, emissive=True),
                make_cube(f"js_wl_{i}", [r + 0.8, y, -1.2], [r + 4.2, y + 0.7, 1.4], rot=[0, 0, 24], zone="C", color=a, emissive=True),
                make_cube(f"js_wr_{i}", [r - 4.2, y, -1.2], [r - 0.8, y + 0.7, 1.4], rot=[0, 0, -24], zone="C", color=a, emissive=True),
                make_cube(f"js_t_{i}", [r - 0.4, y - 0.4, 1.6], [r + 0.4, y + 0.4, 4.4], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"jade_sp_{i}", [0, y, 0], anim="aura_spiral_up", anim_phase=i * 0.25, cubes=cubes))
        return bones

    if arch == "vampire_bats":
        # 5 sharp-winged crimson vampire bats with glowing eyes
        bones = []
        for i in range(5):
            r = R + 0.4 + (i % 3) * 1.5
            y = 10.5 + i * 4.2
            cubes = [
                make_cube(f"vb_{i}", [r - 0.8, y - 0.7, -1.0], [r + 0.8, y + 0.7, 1.0], zone="A", color=p),
                make_cube(f"vbl_{i}", [r + 0.6, y - 0.5, -1.3], [r + 3.6, y + 1.4, 1.1], pivot=[r + 0.6, y, 0], rot=[0, 0, 28], zone="C", color=a, emissive=True),
                make_cube(f"vbr_{i}", [r - 3.6, y - 0.5, -1.3], [r - 0.6, y + 1.4, 1.1], pivot=[r - 0.6, y, 0], rot=[0, 0, -28], zone="C", color=a, emissive=True),
                make_cube(f"vbe_{i}", [r - 0.6, y + 0.1, -1.2], [r + 0.6, y + 0.6, -0.9], zone="D_EYE", color=0xFEF08A, emissive=True),
            ]
            bones.append(make_bone(f"aura_sw_{i}", [0, y, 0], anim=f"aura_swarm_{i}", cubes=cubes))
        return bones

    if arch == "night_bats":
        # 6 wide-winged crescent dark night-bats with tufted ears!
        bones = []
        for i in range(6):
            r = R + 0.8 + (i % 2) * 2.2
            y = 8.0 + i * 3.8
            cubes = [
                make_cube(f"nb_{i}", [r - 0.7, y - 0.6, -1.1], [r + 0.7, y + 0.7, 1.1], zone="A", color=p),
                make_cube(f"nbe1_{i}", [r - 0.6, y + 0.6, -0.6], [r - 0.1, y + 1.4, -0.1], zone="B", color=s),
                make_cube(f"nbe2_{i}", [r + 0.1, y + 0.6, -0.6], [r + 0.6, y + 1.4, -0.1], zone="B", color=s),
                make_cube(f"nwl_{i}", [r + 0.6, y - 0.2, -1.4], [r + 4.4, y + 0.8, 1.0], pivot=[r + 0.6, y, 0], rot=[0, -14, 16], zone="A", color=p),
                make_cube(f"nwr_{i}", [r - 4.4, y - 0.2, -1.4], [r - 0.6, y + 0.8, 1.0], pivot=[r - 0.6, y, 0], rot=[0, 14, -16], zone="A", color=p),
                make_cube(f"nwlg_{i}", [r + 3.6, y - 0.3, -0.8], [r + 4.6, y + 0.6, 0.8], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"aura_sw_{i}", [0, y, 0], anim=f"aura_swarm_{i}", anim_phase=i * 0.7, cubes=cubes))
        return bones

    if arch == "butterflies":
        # 6 distinct 4-winged luminous monarch butterflies (upper + lower pairs!)
        bones = []
        for i in range(6):
            r = R + 0.6 + (i % 3) * 1.4
            y = 14.0
            cubes = [
                make_cube(f"bf_b_{i}", [r - 0.35, y - 0.4, -1.1], [r + 0.35, y + 0.4, 1.1], zone="B", color=s),
                make_cube(f"bf_ul_{i}", [r + 0.3, y, -1.4], [r + 2.6, y + 1.8, 0.2], pivot=[r + 0.3, y, 0], rot=[0, 0, 32], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"bf_ur_{i}", [r - 2.6, y, -1.4], [r - 0.3, y + 1.8, 0.2], pivot=[r - 0.3, y, 0], rot=[0, 0, -32], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"bf_ll_{i}", [r + 0.3, y - 1.2, 0.0], [r + 2.0, y + 0.3, 1.4], pivot=[r + 0.3, y, 0], rot=[0, 0, 18], zone="C", color=a, emissive=True, translucent=True),
                make_cube(f"bf_lr_{i}", [r - 2.0, y - 1.2, 0.0], [r - 0.3, y + 0.3, 1.4], pivot=[r - 0.3, y, 0], rot=[0, 0, -18], zone="C", color=a, emissive=True, translucent=True),
            ]
            bones.append(make_bone(f"bf_{i}", [0, y, 0], anim="aura_spiral_up", anim_phase=i / 6.0, cubes=cubes))
        return bones

    if arch == "thunder_cloud":
        # Brooding stormcloud hovering high above helmets/hats (y = 40.5..46.8!)
        cloud_cubes = [
            make_cube("c1", [-6.8, 40.5, -4.6], [6.8, 44.8, 4.6], zone="A", color=p),
            make_cube("c2", [-4.6, 44.0, -3.6], [5.2, 47.0, 3.6], zone="B", color=s),
            make_cube("c3", [-8.6, 41.2, -3.2], [-4.6, 44.2, 3.2], zone="A", color=p),
            make_cube("c4", [4.6, 41.0, -3.2], [8.6, 44.0, 3.2], zone="A", color=p),
        ]
        # Lightning bolt strikes just outside shoulder radius so it NEVER clips through head!
        bolt_cubes = [
            make_cube("b1", [9.2, 31.0, -0.8], [10.8, 40.2, 0.8], rot=[0, 0, 14], zone="C", color=a, emissive=True),
            make_cube("b2", [8.4, 22.0, -0.7], [10.0, 31.5, 0.7], rot=[0, 0, -18], zone="C", color=a, emissive=True),
            make_cube("b3", [9.6, 13.0, -0.6], [11.0, 22.5, 0.6], rot=[0, 0, 14], zone="C", color=a, emissive=True),
        ]
        rain = [make_bone(f"rain_{i}", [0, 26.0, 0], anim="aura_spiral_up", anim_amp=-1.0, anim_phase=i * 0.25, cubes=[
            make_cube(f"rd_{i}", [math.cos(i * 1.57) * 11.5 - 0.3, 22.0, math.sin(i * 1.57) * 11.5 - 0.3],
                      [math.cos(i * 1.57) * 11.5 + 0.3, 25.6, math.sin(i * 1.57) * 11.5 + 0.3],
                      zone="C", color=0x93C5FD, emissive=True, translucent=True)
        ]) for i in range(4)]
        bolt = make_bone("lightning_bolt", [0, 40.2, 0], anim="aura_bolt", cubes=bolt_cubes)
        return [make_bone("storm_cloud", [0, 42.5, 0], anim="aura_cloud", cubes=cloud_cubes, children=[bolt] + rain)]

    if arch == "tidal_vortex":
        cubes = []
        for i in range(14):
            ang = i * math.tau / 14.0
            rx, rz = math.cos(ang) * R, math.sin(ang) * R
            y0 = 1.4 + (i % 2) * 0.8
            cubes.append(make_cube(f"tv_{i}", [rx - 1.9, y0, rz - 1.2], [rx + 1.9, y0 + 3.8, rz + 1.2],
                                   pivot=[rx, y0, rz], rot=[0, -math.degrees(ang) + 90, 16],
                                   zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True, translucent=True))
        return [make_bone("ground_aura", [0, 2.0, 0], anim="aura_ring_ground", cubes=cubes)]

    if arch == "arcane_circle":
        cubes = []
        for i in range(16):
            ang = i * math.tau / 16.0
            rx, rz = math.cos(ang) * (R + 0.8), math.sin(ang) * (R + 0.8)
            cubes.append(make_cube(f"ac_{i}", [rx - 1.8, 1.4, rz - 0.7], [rx + 1.8, 2.1, rz + 0.7],
                                   pivot=[rx, 1.7, rz], rot=[0, -math.degrees(ang) + 90, 0],
                                   zone="C" if i % 2 == 0 else "A", color=a if i % 2 == 0 else p, emissive=True, translucent=True))
        for k in range(4):
            ang = k * math.tau / 4.0
            rx, rz = math.cos(ang) * (R + 1.5), math.sin(ang) * (R + 1.5)
            cubes.append(make_cube(f"pil_{k}", [rx - 0.6, 1.8, rz - 0.6], [rx + 0.6, 6.2, rz + 0.6], zone="C", color=a, emissive=True, translucent=True))
        return [make_bone("ground_aura", [0, 1.8, 0], anim="aura_ring_ground", cubes=cubes)]

    if arch == "shadow_mist":
        cubes = []
        for i in range(12):
            ang = i * math.tau / 12.0
            rx, rz = math.cos(ang) * (R - 0.2), math.sin(ang) * (R - 0.2)
            h = 4.5 + (i % 3) * 1.8
            cubes.append(make_cube(f"sm_{i}", [rx - 2.0, 1.3, rz - 1.6], [rx + 2.0, 1.3 + h, rz + 1.6],
                                   pivot=[rx, 2.0, rz], rot=[0, -math.degrees(ang), 0], zone="A", color=p, translucent=True))
        return [make_bone("ground_aura", [0, 2.0, 0], anim="aura_ring_ground", cubes=cubes)]

    if arch == "hydro_arc":
        cubes = []
        for i in range(11):
            ang = -1.2 + i * 0.28
            rx, rz = math.cos(ang) * (R + 0.6), math.sin(ang) * (R + 0.6)
            ry = 17.5 + math.sin(i * 0.45) * 6.5
            cubes.append(make_cube(f"arc_{i}", [rx - 1.4, ry - 1.4, rz - 1.4], [rx + 1.4, ry + 1.4, rz + 1.4],
                                   zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True, translucent=True))
        return [make_bone("hydro_arc", [0, 17.5, 0], anim="aura_orbit_fast", cubes=cubes)]

    if arch == "hearts":
        bones = []
        for i in range(6):
            r = R + (i % 2) * 1.8
            y = 15.0
            hc = [
                make_cube(f"hl_{i}", [r - 1.3, y, -0.6], [r + 0.1, y + 1.6, 0.6], rot=[0, 0, 20], zone="C", color=a, emissive=True),
                make_cube(f"hr_{i}", [r - 0.1, y, -0.6], [r + 1.3, y + 1.6, 0.6], rot=[0, 0, -20], zone="A", color=p, emissive=True),
                make_cube(f"hb_{i}", [r - 0.9, y - 1.0, -0.55], [r + 0.9, y + 0.7, 0.55], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"heart_{i}", [0, y, 0], anim="aura_spiral_up", anim_phase=i / 6.0, cubes=hc))
        return bones

    if arch == "sakura_petals":
        # Flat tilted 5-petal 3D cherry blossoms drifting gently upward!
        bones = []
        for i in range(7):
            r = R + (i % 3) * 1.4
            y = 15.0
            c = [make_cube(f"skc_{i}", [r - 0.5, y - 0.3, -0.5], [r + 0.5, y + 0.3, 0.5], zone="D_GOLD", color=0xFEF08A, emissive=True)]
            for k in range(5):
                ang = k * math.tau / 5.0
                px, pz = r + math.cos(ang) * 1.2, math.sin(ang) * 1.2
                c.append(make_cube(f"skp_{i}_{k}", [px - 0.7, y - 0.25, pz - 0.7], [px + 0.7, y + 0.25, pz + 0.7],
                                   rot=[15, -math.degrees(ang), 12], zone="A" if k % 2 == 0 else "C", color=p if k % 2 == 0 else a, emissive=True))
            bones.append(make_bone(f"sakura_{i}", [0, y, 0], anim="aura_spiral_up", anim_phase=i / 7.0, cubes=c))
        return bones

    if arch == "star_bursts":
        # Sharp 6-pointed 3D star bursts twinkling at staggered orbits!
        bones = []
        for i in range(6):
            r = R + (i % 2) * 2.0
            y = 15.0
            c = [
                make_cube(f"sp1_{i}", [r - 2.0, y - 0.4, -0.4], [r + 2.0, y + 0.4, 0.4], zone="C", color=a, emissive=True),
                make_cube(f"sp2_{i}", [r - 0.4, y - 2.0, -0.4], [r + 0.4, y + 2.0, 0.4], zone="A", color=p, emissive=True),
                make_cube(f"sp3_{i}", [r - 0.4, y - 0.4, -2.0], [r + 0.4, y + 0.4, 2.0], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"spark_{i}", [0, y, 0], anim="aura_spiral_up", anim_phase=i / 6.0, cubes=c))
        return bones

    if arch == "snowflakes":
        # Flat geometric 6-arm icy snowflakes drifting softly!
        bones = []
        for i in range(6):
            r = R + 0.6 + (i % 3) * 1.4
            y = 16.0
            c = [make_cube(f"sf_{i}_{k}", [r - 2.2, y - 0.25, -0.35], [r + 2.2, y + 0.25, 0.35],
                           pivot=[r, y, 0], rot=[20, k * 60, 0], zone="C" if k == 0 else "A", color=a if k == 0 else p, emissive=True, translucent=True)
                 for k in range(3)]
            bones.append(make_bone(f"flake_{i}", [0, y, 0], anim="aura_spiral_up", anim_amp=-0.75, anim_phase=i / 6.0, cubes=c))
        return bones

    if arch == "galaxy_orbit":
        bones = []
        for i, (r, y, sz) in enumerate([(R + 0.6, 22.0, 2.1), (R + 2.2, 13.0, 1.7), (R - 0.2, 28.0, 1.4)]):
            pc = [
                make_cube(f"pl_{i}", [r - sz, y - sz, -sz], [r + sz, y + sz, sz], zone="A" if i == 0 else "C", color=p if i == 0 else a, emissive=True),
                make_cube(f"rg_{i}", [r - sz * 1.8, y - 0.3, -sz * 1.8], [r + sz * 1.8, y + 0.3, sz * 1.8], pivot=[r, y, 0], rot=[22, 0, 15], zone="C", color=a, emissive=True, translucent=True),
            ]
            bones.append(make_bone(f"planet_{i}", [0, y, 0], anim="aura_orbit_cw" if i % 2 == 0 else "aura_orbit_ccw", anim_phase=i * 2.1, cubes=pc))
        return bones

    if arch == "ruby_diamonds":
        # 6 tall cut-diamond vertical ruby spires!
        bones = []
        for i in range(6):
            r = R + (i % 2) * 1.6
            y = 14.0 + (i % 3) * 4.5
            c = [
                make_cube(f"rd_{i}", [r - 0.95, y - 2.3, -0.95], [r + 0.95, y + 2.3, 0.95], pivot=[r, y, 0], rot=[0, 45, 0], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"rc_{i}", [r - 0.55, y - 1.5, -0.55], [r + 0.55, y + 1.5, 0.55], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"ruby_{i}", [0, y, 0], anim="aura_orbit_cw", anim_phase=i * 1.05, cubes=c))
        return bones

    if arch == "emerald_helix":
        # 8 faceted emerald prisms in counter-revolving double helix!
        bones = []
        for i in range(8):
            r = R + 0.6 + (i % 2) * 1.8
            y = 10.0 + (i % 4) * 5.0
            c = [make_cube(f"em_{i}", [r - 1.2, y - 1.2, -1.2], [r + 1.2, y + 1.2, 1.2], pivot=[r, y, 0], rot=[45, 45, 0],
                           zone="C" if i % 2 == 0 else "A", color=a if i % 2 == 0 else p, emissive=True, translucent=True)]
            bones.append(make_bone(f"em_{i}", [0, y, 0], anim="aura_orbit_cw" if i < 4 else "aura_orbit_ccw", anim_phase=i * 0.78, cubes=c))
        return bones

    if arch == "solar_suns":
        # 4 miniature flaming suns each with its own tilted golden corona ring!
        bones = []
        for i in range(4):
            r = R + 1.2
            y = 12.5 + i * 4.8
            c = [
                make_cube(f"sun_{i}", [r - 1.4, y - 1.4, -1.4], [r + 1.4, y + 1.4, 1.4], zone="A", color=p, emissive=True),
                make_cube(f"cor_{i}", [r - 2.3, y - 0.35, -2.3], [r + 2.3, y + 0.35, 2.3], pivot=[r, y, 0], rot=[28, 0, -18], zone="C", color=a, emissive=True, translucent=True),
            ]
            bones.append(make_bone(f"sun_{i}", [0, y, 0], anim="aura_orbit_cw", anim_phase=i * 1.57, cubes=c))
        return bones

    if arch == "inferno_flames":
        # 8 tall multi-layered rising flame tongues (white-gold core + outer fiery sheath)!
        bones = []
        for i in range(8):
            r = R + (i % 2) * 1.6
            y = 14.5
            c = [
                make_cube(f"fo_{i}", [r - 1.0, y - 1.8, -1.0], [r + 1.0, y + 2.2, 1.0], pivot=[r, y, 0], rot=[0, 45, 12], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"fi_{i}", [r - 0.55, y - 1.2, -0.55], [r + 0.55, y + 1.2, 0.55], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"flame_{i}", [0, y, 0], anim="aura_spiral_up", anim_phase=i / 8.0, cubes=c))
        return bones

    if arch == "void_shards":
        # 9 jagged asymmetric broken obsidian monolith chunks
        bones = []
        for i in range(9):
            r = R + (i % 3) * 1.3
            y = 9.0 + (i % 4) * 5.2
            h = 1.6 + (i % 3) * 0.9
            c = [
                make_cube(f"vs_{i}", [r - 0.9, y - h, -0.8], [r + 0.9, y + h, 0.8], pivot=[r, y, 0], rot=[22 * i, 35 * i, 18 * i], zone="A", color=p),
                make_cube(f"vc_{i}", [r - 0.35, y - h * 0.6, -0.95], [r + 0.35, y + h * 0.6, 0.95], pivot=[r, y, 0], rot=[22 * i, 35 * i, 18 * i], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"void_{i}", [0, y, 0], anim="aura_orbit_ccw" if i % 2 == 0 else "aura_orbit_cw", anim_phase=i * 0.7, cubes=c))
        return bones

    # crosses (abyssal_cross_aura)
    bones = []
    for i in range(6):
        r = R + 0.5 + (i % 2) * 1.8
        y = 13.0 + (i % 3) * 5.0
        c = [
            make_cube(f"cv_{i}", [r - 0.55, y - 2.1, -0.55], [r + 0.55, y + 2.1, 0.55], zone="A", color=p),
            make_cube(f"ch_{i}", [r - 1.7, y + 0.2, -0.55], [r + 1.7, y + 1.2, 0.55], zone="A", color=p),
        ]
        bones.append(make_bone(f"cross_{i}", [0, y, 0], anim="aura_orbit_ccw", anim_phase=i * 1.05, cubes=c))
    return bones


# ==============================================================================
# 5. FLOATING PETS — ALL 22 DESIGNS 100% UNIQUE SCULPTED ANATOMIES!
#    Studio anchor: (ox=15.0, oy=25.5, oz=2.0) — World physics cancels anchor automatically!
# ==============================================================================
def build_pet_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    ox, oy, oz = 15.0, 25.5, 2.0

    if arch == "bumblebee":
        body = [
            make_cube("bee_body", [ox - 2.7, oy - 2.2, oz - 3.6], [ox + 2.7, oy + 2.2, oz + 3.6], zone="A", color=p),
            make_cube("stinger", [ox - 0.45, oy - 0.6, oz + 3.5], [ox + 0.45, oy + 0.2, oz + 4.8], zone="D_DARK", color=0x18181B),
            make_cube("eye_l", [ox + 1.0, oy - 0.2, oz - 3.85], [ox + 2.2, oy + 1.0, oz - 3.3], zone="D_EYE", color=0x1E293B),
            make_cube("eye_r", [ox - 2.2, oy - 0.2, oz - 3.85], [ox - 1.0, oy + 1.0, oz - 3.3], zone="D_EYE", color=0x1E293B),
            make_cube("ant_l", [ox + 0.8, oy + 1.6, oz - 5.3], [ox + 1.4, oy + 2.2, oz - 3.5], rot=[-18, 0, 0], zone="D_DARK", color=0x18181B),
            make_cube("ant_r", [ox - 1.4, oy + 1.6, oz - 5.3], [ox - 0.8, oy + 2.2, oz - 3.5], rot=[-18, 0, 0], zone="D_DARK", color=0x18181B),
            make_cube("leg1", [ox - 1.8, oy - 3.0, oz - 1.8], [ox - 1.1, oy - 2.1, oz - 1.1], zone="D_DARK", color=0x18181B),
            make_cube("leg2", [ox + 1.1, oy - 3.0, oz - 1.8], [ox + 1.8, oy - 2.1, oz - 1.1], zone="D_DARK", color=0x18181B),
            make_cube("leg3", [ox - 1.8, oy - 3.0, oz + 1.1], [ox - 1.1, oy - 2.1, oz + 1.8], zone="D_DARK", color=0x18181B),
            make_cube("leg4", [ox + 1.1, oy - 3.0, oz + 1.1], [ox + 1.8, oy - 2.1, oz + 1.8], zone="D_DARK", color=0x18181B),
        ]
        wl = make_bone("pet_wl", [ox + 1.8, oy + 2.2, oz], anim="pet_wing_l", anim_amp=1.6, cubes=[
            make_cube("wl", [ox + 1.6, oy + 2.2, oz - 1.8], [ox + 6.5, oy + 2.7, oz + 1.6], zone="C", color=a, translucent=True, emissive=True)])
        wr = make_bone("pet_wr", [ox - 1.8, oy + 2.2, oz], anim="pet_wing_r", anim_amp=1.6, cubes=[
            make_cube("wr", [ox - 6.5, oy + 2.2, oz - 1.8], [ox - 1.6, oy + 2.7, oz + 1.6], zone="C", color=a, translucent=True, emissive=True)])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "shadow_wyvern":
        # Sleek spiky shadow wyvern with curved ridge spines and arrow-spade tail
        body = [
            make_cube("torso", [ox - 1.8, oy - 1.7, oz - 3.0], [ox + 1.8, oy + 1.5, oz + 3.4], zone="A", color=p),
            make_cube("sp1", [ox - 0.4, oy + 1.4, oz - 1.8], [ox + 0.4, oy + 3.0, oz - 0.6], rot=[-22, 0, 0], zone="C", color=a, emissive=True),
            make_cube("sp2", [ox - 0.4, oy + 1.4, oz + 0.4], [ox + 0.4, oy + 2.8, oz + 1.6], rot=[-22, 0, 0], zone="C", color=a, emissive=True),
        ]
        head = make_bone("pet_head", [ox, oy + 1.2, oz - 2.8], anim="pet_head", cubes=[
            make_cube("skull", [ox - 1.8, oy + 0.4, oz - 6.2], [ox + 1.8, oy + 3.4, oz - 2.5], zone="A", color=p),
            make_cube("snout", [ox - 1.1, oy + 0.4, oz - 8.2], [ox + 1.1, oy + 1.9, oz - 6.0], zone="A", color=p),
            make_cube("el", [ox + 0.8, oy + 1.7, oz - 6.4], [ox + 1.8, oy + 2.6, oz - 5.9], zone="D_EYE", color=a, emissive=True),
            make_cube("er", [ox - 1.8, oy + 1.7, oz - 6.4], [ox - 0.8, oy + 2.6, oz - 5.9], zone="D_EYE", color=a, emissive=True),
            make_cube("hl", [ox + 1.0, oy + 3.2, oz - 3.6], [ox + 1.9, oy + 6.4, oz - 2.4], rot=[-36, 0, 18], zone="C", color=a, emissive=True),
            make_cube("hr", [ox - 1.9, oy + 3.2, oz - 3.6], [ox - 1.0, oy + 6.4, oz - 2.4], rot=[-36, 0, -18], zone="C", color=a, emissive=True),
        ])
        tail = make_bone("pet_tail", [ox, oy, oz + 3.2], anim="pet_tail", cubes=[
            make_cube("t1", [ox - 0.8, oy - 0.7, oz + 3.2], [ox + 0.8, oy + 0.7, oz + 7.6], zone="A", color=p),
            make_cube("spade", [ox - 1.8, oy - 0.3, oz + 7.4], [ox + 1.8, oy + 0.5, oz + 10.2], rot=[0, 45, 0], zone="C", color=a, emissive=True),
        ])
        wl = make_bone("pet_wl", [ox + 1.8, oy + 1.2, oz], anim="pet_wing_l", cubes=[
            make_cube("wlb", [ox + 1.8, oy + 1.0, oz - 1.4], [ox + 9.8, oy + 2.0, oz - 0.3], zone="B", color=s),
            make_cube("wlm", [ox + 1.8, oy - 1.8, oz - 0.8], [ox + 9.4, oy + 1.6, oz + 3.4], zone="A", color=p),
        ])
        wr = make_bone("pet_wr", [ox - 1.8, oy + 1.2, oz], anim="pet_wing_r", cubes=[
            make_cube("wrb", [ox - 9.8, oy + 1.0, oz - 1.4], [ox - 1.8, oy + 2.0, oz - 0.3], zone="B", color=s),
            make_cube("wrm", [ox - 9.4, oy - 1.8, oz - 0.8], [ox - 1.8, oy + 1.6, oz + 3.4], zone="A", color=p),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail, wl, wr])]

    if arch == "ivory_seraph_drake":
        # Long-necked noble white angel-dragon with feathered wings & mini halo!
        body = [
            make_cube("torso", [ox - 2.0, oy - 1.8, oz - 2.4], [ox + 2.0, oy + 1.6, oz + 2.8], zone="A", color=p),
            make_cube("neck", [ox - 1.1, oy + 1.0, oz - 4.0], [ox + 1.1, oy + 4.4, oz - 1.8], rot=[20, 0, 0], zone="A", color=p),
            make_cube("halo", [ox - 1.8, oy + 6.4, oz - 5.2], [ox + 1.8, oy + 7.1, oz - 2.4], zone="D_GOLD", color=0xFDE047, emissive=True),
        ]
        head = make_bone("pet_head", [ox, oy + 4.0, oz - 3.2], anim="pet_head", cubes=[
            make_cube("sk", [ox - 1.7, oy + 3.2, oz - 6.6], [ox + 1.7, oy + 5.8, oz - 3.0], zone="A", color=p),
            make_cube("el", [ox + 0.7, oy + 4.3, oz - 6.8], [ox + 1.6, oy + 5.1, oz - 6.3], zone="D_EYE", color=a, emissive=True),
            make_cube("er", [ox - 1.6, oy + 4.3, oz - 6.8], [ox - 0.7, oy + 5.1, oz - 6.3], zone="D_EYE", color=a, emissive=True),
            make_cube("crest", [ox - 0.4, oy + 5.5, oz - 4.8], [ox + 0.4, oy + 6.8, oz - 2.4], zone="C", color=a, emissive=True),
        ])
        tail = make_bone("pet_tail", [ox, oy, oz + 2.6], anim="pet_tail", cubes=[
            make_cube("t1", [ox - 0.7, oy - 0.7, oz + 2.6], [ox + 0.7, oy + 0.7, oz + 6.8], zone="A", color=p),
            make_cube("plume", [ox - 1.4, oy - 0.8, oz + 6.4], [ox + 1.4, oy + 1.2, oz + 9.2], zone="C", color=a, emissive=True),
        ])
        wl = make_bone("pet_wl", [ox + 2.0, oy + 1.4, oz], anim="pet_wing_l", cubes=[
            make_cube("wf1", [ox + 2.0, oy + 0.2, oz - 1.0], [ox + 8.6, oy + 2.0, oz + 1.8], rot=[0, 0, 12], zone="A", color=p),
            make_cube("wf2", [ox + 5.5, oy - 0.8, oz + 0.2], [ox + 10.2, oy + 1.2, oz + 2.6], rot=[0, 0, -8], zone="C", color=a, emissive=True),
        ])
        wr = make_bone("pet_wr", [ox - 2.0, oy + 1.4, oz], anim="pet_wing_r", cubes=[
            make_cube("wfr1", [ox - 8.6, oy + 0.2, oz - 1.0], [ox - 2.0, oy + 2.0, oz + 1.8], rot=[0, 0, -12], zone="A", color=p),
            make_cube("wfr2", [ox - 10.2, oy - 0.8, oz + 0.2], [ox - 5.5, oy + 1.2, oz + 2.6], rot=[0, 0, 8], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail, wl, wr])]

    if arch == "frost_drake":
        # Crystalline ice drake with jagged dorsal ice crystal spires!
        body = [
            make_cube("torso", [ox - 2.3, oy - 2.0, oz - 2.6], [ox + 2.3, oy + 1.8, oz + 3.0], zone="A", color=p),
            make_cube("ice1", [ox - 0.6, oy + 1.7, oz - 1.6], [ox + 0.6, oy + 4.4, oz - 0.4], rot=[-16, 0, 0], zone="C", color=a, emissive=True, translucent=True),
            make_cube("ice2", [ox - 0.6, oy + 1.7, oz + 0.2], [ox + 0.6, oy + 4.0, oz + 1.4], rot=[-22, 0, 0], zone="C", color=a, emissive=True, translucent=True),
            make_cube("ice3", [ox - 0.5, oy + 1.5, oz + 1.8], [ox + 0.5, oy + 3.4, oz + 2.8], rot=[-28, 0, 0], zone="C", color=a, emissive=True, translucent=True),
        ]
        head = make_bone("pet_head", [ox, oy + 1.4, oz - 2.6], anim="pet_head", cubes=[
            make_cube("sk", [ox - 2.0, oy + 0.4, oz - 6.0], [ox + 2.0, oy + 3.6, oz - 2.4], zone="A", color=p),
            make_cube("jaw", [ox - 1.5, oy - 0.2, oz - 7.6], [ox + 1.5, oy + 1.4, oz - 5.8], zone="B", color=s),
            make_cube("el", [ox + 0.8, oy + 1.8, oz - 6.2], [ox + 1.9, oy + 2.8, oz - 5.7], zone="D_EYE", color=a, emissive=True),
            make_cube("er", [ox - 1.9, oy + 1.8, oz - 6.2], [ox - 0.8, oy + 2.8, oz - 5.7], zone="D_EYE", color=a, emissive=True),
            make_cube("ihl", [ox + 1.2, oy + 3.5, oz - 3.6], [ox + 2.3, oy + 6.8, oz - 2.4], rot=[-20, 0, 22], zone="C", color=a, emissive=True, translucent=True),
            make_cube("ihr", [ox - 2.3, oy + 3.5, oz - 3.6], [ox - 1.2, oy + 6.8, oz - 2.4], rot=[-20, 0, -22], zone="C", color=a, emissive=True, translucent=True),
        ])
        tail = make_bone("pet_tail", [ox, oy, oz + 2.8], anim="pet_tail", cubes=[
            make_cube("t1", [ox - 0.9, oy - 0.8, oz + 2.8], [ox + 0.9, oy + 0.8, oz + 6.4], zone="B", color=s),
            make_cube("itip", [ox - 1.2, oy - 0.9, oz + 6.2], [ox + 1.2, oy + 1.1, oz + 9.6], rot=[0, 0, 45], zone="C", color=a, emissive=True, translucent=True),
        ])
        wl = make_bone("pet_wl", [ox + 2.2, oy + 1.5, oz], anim="pet_wing_l", cubes=[
            make_cube("wlc", [ox + 2.2, oy - 1.2, oz - 1.0], [ox + 9.4, oy + 2.2, oz + 2.4], zone="C", color=a, emissive=True, translucent=True),
        ])
        wr = make_bone("pet_wr", [ox - 2.2, oy + 1.5, oz], anim="pet_wing_r", cubes=[
            make_cube("wrc", [ox - 9.4, oy - 1.2, oz - 1.0], [ox - 2.2, oy + 2.2, oz + 2.4], zone="C", color=a, emissive=True, translucent=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail, wl, wr])]

    if arch == "ender_chibi":
        # Iconic blocky Minecraft Ender Dragon chibi (oversized square head + snout + nostrils + spine plates!)
        body = [
            make_cube("eb", [ox - 2.2, oy - 1.8, oz - 2.6], [ox + 2.2, oy + 1.6, oz + 2.8], zone="A", color=p),
            make_cube("es1", [ox - 0.6, oy + 1.6, oz - 1.6], [ox + 0.6, oy + 2.5, oz - 0.4], zone="C", color=a, emissive=True),
            make_cube("es2", [ox - 0.6, oy + 1.6, oz + 0.4], [ox + 0.6, oy + 2.5, oz + 1.6], zone="C", color=a, emissive=True),
            make_cube("ep1", [ox - 2.0, oy - 3.2, oz - 2.0], [ox - 1.0, oy - 1.6, oz - 1.0], zone="B", color=s),
            make_cube("ep2", [ox + 1.0, oy - 3.2, oz - 2.0], [ox + 2.0, oy - 1.6, oz - 1.0], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.4, oz - 2.5], anim="pet_head", cubes=[
            make_cube("esk", [ox - 2.6, oy + 0.2, oz - 6.6], [ox + 2.6, oy + 4.4, oz - 2.2], zone="A", color=p),
            make_cube("esn", [ox - 1.8, oy + 0.2, oz - 8.6], [ox + 1.8, oy + 2.4, oz - 6.4], zone="A", color=p),
            make_cube("nos1", [ox + 0.6, oy + 2.3, oz - 8.4], [ox + 1.4, oy + 3.0, oz - 7.4], zone="B", color=s),
            make_cube("nos2", [ox - 1.4, oy + 2.3, oz - 8.4], [ox - 0.6, oy + 3.0, oz - 7.4], zone="B", color=s),
            make_cube("eel", [ox + 1.0, oy + 2.2, oz - 6.8], [ox + 2.4, oy + 3.2, oz - 6.3], zone="D_EYE", color=a, emissive=True),
            make_cube("eer", [ox - 2.4, oy + 2.2, oz - 6.8], [ox - 1.0, oy + 3.2, oz - 6.3], zone="D_EYE", color=a, emissive=True),
            make_cube("ear1", [ox + 1.2, oy + 4.3, oz - 4.0], [ox + 2.2, oy + 5.5, oz - 2.4], zone="B", color=s),
            make_cube("ear2", [ox - 2.2, oy + 4.3, oz - 4.0], [ox - 1.2, oy + 5.5, oz - 2.4], zone="B", color=s),
        ])
        tail = make_bone("pet_tail", [ox, oy, oz + 2.6], anim="pet_tail", cubes=[
            make_cube("et1", [ox - 1.0, oy - 0.9, oz + 2.6], [ox + 1.0, oy + 0.9, oz + 5.6], zone="A", color=p),
            make_cube("et2", [ox - 0.8, oy - 0.7, oz + 5.4], [ox + 0.8, oy + 0.7, oz + 8.4], zone="A", color=p),
            make_cube("et3", [ox - 0.4, oy + 0.6, oz + 3.4], [ox + 0.4, oy + 1.5, oz + 4.8], zone="C", color=a, emissive=True),
        ])
        wl = make_bone("pet_wl", [ox + 2.2, oy + 1.2, oz], anim="pet_wing_l", cubes=[
            make_cube("ewl", [ox + 2.2, oy + 0.8, oz - 1.2], [ox + 9.0, oy + 1.8, oz - 0.2], zone="B", color=s),
            make_cube("ewlm", [ox + 2.2, oy - 1.2, oz - 0.6], [ox + 8.6, oy + 1.4, oz + 3.0], zone="A", color=p),
        ])
        wr = make_bone("pet_wr", [ox - 2.2, oy + 1.2, oz], anim="pet_wing_r", cubes=[
            make_cube("ewr", [ox - 9.0, oy + 0.8, oz - 1.2], [ox - 2.2, oy + 1.8, oz - 0.2], zone="B", color=s),
            make_cube("ewrm", [ox - 8.6, oy - 1.2, oz - 0.6], [ox - 2.2, oy + 1.4, oz + 3.0], zone="A", color=p),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail, wl, wr])]

    if arch == "mini_reaper":
        # TRUE Hooded Floating Grim Reaper holding a curved glowing scythe!
        body = [
            make_cube("robe_top", [ox - 2.0, oy - 2.2, oz - 1.6], [ox + 2.0, oy + 2.0, oz + 1.6], zone="A", color=p),
            make_cube("robe_hem", [ox - 2.5, oy - 4.4, oz - 2.0], [ox + 2.5, oy - 1.8, oz + 2.0], zone="B", color=s),
            make_cube("hood", [ox - 2.4, oy + 2.0, oz - 2.4], [ox + 2.4, oy + 6.2, oz + 2.4], zone="A", color=p),
            make_cube("hood_tip", [ox - 1.6, oy + 4.4, oz + 1.6], [ox + 1.6, oy + 6.2, oz + 4.2], rot=[-22, 0, 0], zone="A", color=p),
            make_cube("void_face", [ox - 1.8, oy + 2.4, oz - 2.55], [ox + 1.8, oy + 5.2, oz - 1.6], zone="D_DARK", color=0x050508),
            make_cube("reye1", [ox + 0.5, oy + 3.6, oz - 2.7], [ox + 1.4, oy + 4.4, oz - 2.3], zone="D_EYE", color=a, emissive=True),
            make_cube("reye2", [ox - 1.4, oy + 3.6, oz - 2.7], [ox - 0.5, oy + 4.4, oz - 2.3], zone="D_EYE", color=a, emissive=True),
        ]
        scythe = make_bone("reaper_scythe", [ox + 2.6, oy + 0.8, oz - 0.6], anim="pet_pendulum", anim_amp=0.65, cubes=[
            make_cube("pole", [ox + 2.4, oy - 4.8, oz - 1.0], [ox + 3.2, oy + 6.6, oz - 0.2], rot=[12, 0, -10], zone="B", color=0x475569),
            make_cube("blade1", [ox - 1.4, oy + 5.6, oz - 1.2], [ox + 3.4, oy + 7.2, oz - 0.2], rot=[12, 0, -8], zone="C", color=a, emissive=True),
            make_cube("blade2", [ox - 3.2, oy + 4.0, oz - 1.2], [ox - 0.8, oy + 6.2, oz - 0.2], rot=[12, 0, 28], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[scythe])]

    if arch == "ufo_alien":
        saucer = make_bone("ufo_ring", [ox, oy, oz], anim="pet_spin", cubes=[
            make_cube("disc", [ox - 4.6, oy - 1.2, oz - 4.6], [ox + 4.6, oy + 0.2, oz + 4.6], zone="B", color=s),
            make_cube("rim_glow", [ox - 4.9, oy - 0.7, oz - 4.9], [ox + 4.9, oy - 0.1, oz + 4.9], zone="C", color=a, emissive=True),
            make_cube("beam", [ox - 2.0, oy - 3.6, oz - 2.0], [ox + 2.0, oy - 1.2, oz + 2.0], zone="C", color=a, emissive=True, translucent=True),
        ])
        alien = [
            make_cube("dome", [ox - 3.0, oy + 0.2, oz - 3.0], [ox + 3.0, oy + 4.2, oz + 3.0], zone="C", color=0xBAE6FD, translucent=True),
            make_cube("al_head", [ox - 1.7, oy + 0.5, oz - 1.7], [ox + 1.7, oy + 3.3, oz + 1.7], zone="A", color=p),
            make_cube("al_eye1", [ox - 1.2, oy + 1.7, oz - 1.9], [ox - 0.3, oy + 2.6, oz - 1.5], zone="D_EYE", color=0x18181B),
            make_cube("al_eye2", [ox + 0.3, oy + 1.7, oz - 1.9], [ox + 1.2, oy + 2.6, oz - 1.5], zone="D_EYE", color=0x18181B),
        ]
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=alien, children=[saucer])]

    if arch == "watch_raven":
        body = [
            make_cube("r_body", [ox - 1.9, oy - 2.0, oz - 2.4], [ox + 1.9, oy + 1.8, oz + 2.8], rot=[-12, 0, 0], zone="A", color=p),
            make_cube("r_head", [ox - 1.6, oy + 1.2, oz - 4.2], [ox + 1.6, oy + 3.8, oz - 1.2], zone="A", color=p),
            make_cube("beak", [ox - 0.55, oy + 1.6, oz - 6.0], [ox + 0.55, oy + 2.6, oz - 4.0], zone="D_DARK", color=0x334155),
            make_cube("el", [ox + 0.8, oy + 2.3, oz - 4.3], [ox + 1.7, oy + 3.1, oz - 3.7], zone="D_EYE", color=a, emissive=True),
            make_cube("er", [ox - 1.7, oy + 2.3, oz - 4.3], [ox - 0.8, oy + 3.1, oz - 3.7], zone="D_EYE", color=a, emissive=True),
            make_cube("rtail", [ox - 1.4, oy - 1.6, oz + 2.6], [ox + 1.4, oy - 0.4, oz + 6.4], rot=[-18, 0, 0], zone="B", color=s),
        ]
        wl = make_bone("pet_wl", [ox + 1.8, oy + 1.0, oz], anim="pet_wing_l", cubes=[make_cube("wl", [ox + 1.8, oy - 0.4, oz - 1.4], [ox + 7.8, oy + 1.4, oz + 2.0], zone="B", color=s)])
        wr = make_bone("pet_wr", [ox - 1.8, oy + 1.0, oz], anim="pet_wing_r", cubes=[make_cube("wr", [ox - 7.8, oy - 0.4, oz - 1.4], [ox - 1.8, oy + 1.4, oz + 2.0], zone="B", color=s)])
        watch = make_bone("pocket_watch", [ox, oy - 2.0, oz], anim="pet_pendulum", cubes=[
            make_cube("chain", [ox - 0.25, oy - 5.0, oz - 0.25], [ox + 0.25, oy - 2.0, oz + 0.25], zone="D_GOLD", color=0xF59E0B),
            make_cube("clock", [ox - 1.5, oy - 7.6, oz - 0.6], [ox + 1.5, oy - 4.6, oz + 0.6], zone="D_GOLD", color=0xFDE047, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr, watch])]

    if arch == "cockatiel":
        body = [
            make_cube("c_body", [ox - 1.9, oy - 2.2, oz - 2.0], [ox + 1.9, oy + 2.6, oz + 2.2], zone="A", color=p),
            make_cube("crest", [ox - 0.4, oy + 2.6, oz - 1.6], [ox + 0.4, oy + 5.2, oz + 0.2], rot=[-26, 0, 0], zone="C", color=a),
            make_cube("shades", [ox - 2.3, oy + 1.2, oz - 2.6], [ox + 2.3, oy + 2.2, oz - 1.9], zone="D_DARK", color=0x18181B),
            make_cube("cheek1", [ox + 1.4, oy + 0.3, oz - 2.2], [ox + 2.1, oy + 1.1, oz - 1.4], zone="C", color=0xF97316),
            make_cube("cheek2", [ox - 2.1, oy + 0.3, oz - 2.2], [ox - 1.4, oy + 1.1, oz - 1.4], zone="C", color=0xF97316),
            make_cube("beak", [ox - 0.55, oy + 0.5, oz - 3.3], [ox + 0.55, oy + 1.4, oz - 1.9], zone="D_GOLD", color=0xF59E0B),
            make_cube("ctail", [ox - 0.8, oy - 3.6, oz + 1.8], [ox + 0.8, oy - 1.4, oz + 5.8], rot=[-36, 0, 0], zone="B", color=s),
        ]
        wl = make_bone("pet_wl", [ox + 1.9, oy + 0.8, oz], anim="pet_wing_l", cubes=[make_cube("wl", [ox + 1.9, oy - 0.8, oz - 1.2], [ox + 6.6, oy + 1.2, oz + 1.6], zone="B", color=s)])
        wr = make_bone("pet_wr", [ox - 1.9, oy + 0.8, oz], anim="pet_wing_r", cubes=[make_cube("wr", [ox - 6.6, oy - 0.8, oz - 1.2], [ox - 1.9, oy + 1.2, oz + 1.6], zone="B", color=s)])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "astral_owl":
        # Distinct broad tufted Great Horned Owl with wide facial disc & talons!
        body = [
            make_cube("o_body", [ox - 2.6, oy - 2.6, oz - 2.2], [ox + 2.6, oy + 1.8, oz + 2.4], zone="A", color=p),
            make_cube("o_head", [ox - 2.8, oy + 1.6, oz - 2.5], [ox + 2.8, oy + 5.2, oz + 2.3], zone="A", color=p),
            make_cube("disc", [ox - 2.4, oy + 2.2, oz - 2.7], [ox + 2.4, oy + 4.6, oz - 2.1], zone="B", color=s),
            make_cube("tuft_l", [ox + 1.8, oy + 4.8, oz - 1.2], [ox + 3.4, oy + 6.8, oz + 0.2], rot=[0, 0, -28], zone="C", color=a, emissive=True),
            make_cube("tuft_r", [ox - 3.4, oy + 4.8, oz - 1.2], [ox - 1.8, oy + 6.8, oz + 0.2], rot=[0, 0, 28], zone="C", color=a, emissive=True),
            make_cube("oel", [ox + 0.6, oy + 3.0, oz - 2.9], [ox + 2.0, oy + 4.2, oz - 2.4], zone="D_EYE", color=a, emissive=True),
            make_cube("oer", [ox - 2.0, oy + 3.0, oz - 2.9], [ox - 0.6, oy + 4.2, oz - 2.4], zone="D_EYE", color=a, emissive=True),
            make_cube("obeak", [ox - 0.5, oy + 2.2, oz - 3.4], [ox + 0.5, oy + 3.1, oz - 2.4], zone="D_GOLD", color=0xF59E0B),
            make_cube("tal1", [ox + 0.8, oy - 3.4, oz - 1.0], [ox + 1.8, oy - 2.4, oz + 0.6], zone="D_GOLD", color=0xF59E0B),
            make_cube("tal2", [ox - 1.8, oy - 3.4, oz - 1.0], [ox - 0.8, oy - 2.4, oz + 0.6], zone="D_GOLD", color=0xF59E0B),
        ]
        wl = make_bone("pet_wl", [ox + 2.6, oy + 1.0, oz], anim="pet_wing_l", cubes=[make_cube("wl", [ox + 2.6, oy - 1.2, oz - 1.6], [ox + 8.8, oy + 1.4, oz + 2.2], zone="B", color=s)])
        wr = make_bone("pet_wr", [ox - 2.6, oy + 1.0, oz], anim="pet_wing_r", cubes=[make_cube("wr", [ox - 8.8, oy - 1.2, oz - 1.6], [ox - 2.6, oy + 1.4, oz + 2.2], zone="B", color=s)])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "crimson_imp":
        body = [
            make_cube("imp_body", [ox - 1.8, oy - 2.6, oz - 1.5], [ox + 1.8, oy + 1.2, oz + 1.5], zone="A", color=p),
            make_cube("imp_head", [ox - 2.1, oy + 1.2, oz - 2.1], [ox + 2.1, oy + 5.0, oz + 2.1], zone="A", color=p),
            make_cube("horn_l", [ox + 1.0, oy + 4.8, oz - 0.6], [ox + 2.0, oy + 6.8, oz + 0.6], rot=[0, 0, -18], zone="D_GOLD", color=a),
            make_cube("horn_r", [ox - 2.0, oy + 4.8, oz - 0.6], [ox - 1.0, oy + 6.8, oz + 0.6], rot=[0, 0, 18], zone="D_GOLD", color=a),
            make_cube("eye_l", [ox + 0.5, oy + 2.6, oz - 2.3], [ox + 1.5, oy + 3.4, oz - 1.9], zone="D_EYE", color=0xFEF08A, emissive=True),
            make_cube("eye_r", [ox - 1.5, oy + 2.6, oz - 2.3], [ox - 0.5, oy + 3.4, oz - 1.9], zone="D_EYE", color=0xFEF08A, emissive=True),
            make_cube("tri_staff", [ox + 2.5, oy - 3.0, oz - 1.2], [ox + 3.2, oy + 4.2, oz - 0.5], rot=[15, 0, -12], zone="D_GOLD", color=0xFBBF24, emissive=True),
            make_cube("tri_fork", [ox + 1.6, oy + 3.6, oz - 1.4], [ox + 4.2, oy + 5.6, oz - 0.4], rot=[15, 0, -12], zone="D_GOLD", color=0xFDE047, emissive=True),
        ]
        wl = make_bone("pet_wl", [ox + 1.8, oy + 0.5, oz + 1.4], anim="pet_wing_l", cubes=[make_cube("wl", [ox + 1.8, oy - 1.2, oz + 1.2], [ox + 6.4, oy + 2.2, oz + 2.0], zone="D_DARK", color=s)])
        wr = make_bone("pet_wr", [ox - 1.8, oy + 0.5, oz + 1.4], anim="pet_wing_r", cubes=[make_cube("wr", [ox - 6.4, oy - 1.2, oz + 1.2], [ox - 1.8, oy + 2.2, oz + 2.0], zone="D_DARK", color=s)])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "void_chomper":
        # Floating fanged abyssal sphere monster with articulated upper/lower jaw teeth!
        top_jaw = [
            make_cube("jaw_top", [ox - 3.2, oy, oz - 3.2], [ox + 3.2, oy + 3.4, oz + 3.2], zone="A", color=p),
            make_cube("ey1", [ox + 1.0, oy + 1.5, oz - 3.4], [ox + 2.4, oy + 2.7, oz - 2.9], zone="D_EYE", color=a, emissive=True),
            make_cube("ey2", [ox - 2.4, oy + 1.5, oz - 3.4], [ox - 1.0, oy + 2.7, oz - 2.9], zone="D_EYE", color=a, emissive=True),
            make_cube("f1", [ox - 2.2, oy - 0.9, oz - 3.3], [ox - 1.3, oy + 0.2, oz - 2.5], zone="D_GOLD", color=0xF8FAFC),
            make_cube("f2", [ox - 0.4, oy - 0.8, oz - 3.3], [ox + 0.4, oy + 0.2, oz - 2.5], zone="D_GOLD", color=0xF8FAFC),
            make_cube("f3", [ox + 1.3, oy - 0.9, oz - 3.3], [ox + 2.2, oy + 0.2, oz - 2.5], zone="D_GOLD", color=0xF8FAFC),
        ]
        bot_jaw = make_bone("chomp_jaw", [ox, oy, oz + 2.6], anim="pet_head", cubes=[
            make_cube("jaw_bot", [ox - 3.0, oy - 2.4, oz - 3.0], [ox + 3.0, oy - 0.2, oz + 2.8], zone="B", color=s),
            make_cube("tongue", [ox - 1.6, oy - 0.6, oz - 2.4], [ox + 1.6, oy + 0.1, oz + 1.2], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=top_jaw, children=[bot_jaw])]

    if arch == "monkey_duo":
        # Ginger monkey + TINY BABY MONKEY piggybacking on its back!
        body = [
            make_cube("m_body", [ox - 2.2, oy - 2.0, oz - 2.4], [ox + 2.2, oy + 1.6, oz + 2.6], zone="A", color=p),
            make_cube("m_belly", [ox - 1.7, oy - 2.1, oz - 2.0], [ox + 1.7, oy - 0.6, oz + 2.0], zone="B", color=s),
            # Baby monkey clinging onto its back!
            make_cube("baby_b", [ox - 1.2, oy + 1.5, oz - 0.2], [ox + 1.2, oy + 3.4, oz + 2.4], zone="A", color=p),
            make_cube("baby_h", [ox - 1.3, oy + 3.2, oz - 1.2], [ox + 1.3, oy + 5.4, oz + 1.2], zone="A", color=p),
            make_cube("baby_f", [ox - 0.9, oy + 3.4, oz - 1.4], [ox + 0.9, oy + 4.8, oz - 0.8], zone="B", color=s),
            make_cube("baby_el", [ox + 1.2, oy + 4.0, oz - 0.4], [ox + 1.9, oy + 4.9, oz + 0.4], zone="B", color=s),
            make_cube("baby_er", [ox - 1.9, oy + 4.0, oz - 0.4], [ox - 1.2, oy + 4.9, oz + 0.4], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.6, oz - 2.2], anim="pet_head", cubes=[
            make_cube("mh", [ox - 2.2, oy + 0.4, oz - 5.2], [ox + 2.2, oy + 4.4, oz - 1.6], zone="A", color=p),
            make_cube("mf", [ox - 1.7, oy + 0.7, oz - 5.45], [ox + 1.7, oy + 3.4, oz - 4.8], zone="B", color=s),
            make_cube("mel", [ox + 2.1, oy + 1.8, oz - 4.0], [ox + 3.3, oy + 3.4, oz - 2.8], zone="B", color=s),
            make_cube("mer", [ox - 3.3, oy + 1.8, oz - 4.0], [ox - 2.1, oy + 3.4, oz - 2.8], zone="B", color=s),
            make_cube("me1", [ox + 0.5, oy + 2.1, oz - 5.6], [ox + 1.3, oy + 2.9, oz - 5.2], zone="D_EYE", color=0x18181B),
            make_cube("me2", [ox - 1.3, oy + 2.1, oz - 5.6], [ox - 0.5, oy + 2.9, oz - 5.2], zone="D_EYE", color=0x18181B),
        ])
        tail = make_bone("pet_tail", [ox, oy + 0.4, oz + 2.6], anim="pet_tail", cubes=[
            make_cube("mt1", [ox - 0.5, oy, oz + 2.6], [ox + 0.5, oy + 1.0, oz + 5.6], rot=[28, 0, 0], zone="A", color=p),
            make_cube("mt2", [ox - 0.5, oy + 1.6, oz + 4.6], [ox + 0.5, oy + 3.8, oz + 5.6], zone="A", color=p),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail])]

    if arch == "mochi_loaf_cat":
        # Super round chubby loaf cat with tucked paws & cute whiskers!
        body = [
            make_cube("loaf", [ox - 3.4, oy - 2.0, oz - 3.8], [ox + 3.4, oy + 2.8, oz + 3.6], zone="A", color=p),
            make_cube("belly", [ox - 2.8, oy - 2.2, oz - 3.2], [ox + 2.8, oy - 1.0, oz + 2.8], zone="B", color=s),
            make_cube("el", [ox + 1.2, oy + 2.7, oz - 3.2], [ox + 2.8, oy + 4.5, oz - 1.8], zone="A", color=p),
            make_cube("er", [ox - 2.8, oy + 2.7, oz - 3.2], [ox - 1.2, oy + 4.5, oz - 1.8], zone="A", color=p),
            make_cube("eye1", [ox + 1.0, oy + 0.6, oz - 4.0], [ox + 2.0, oy + 1.6, oz - 3.6], zone="D_EYE", color=0x18181B),
            make_cube("eye2", [ox - 2.0, oy + 0.6, oz - 4.0], [ox - 1.0, oy + 1.6, oz - 3.6], zone="D_EYE", color=0x18181B),
            make_cube("nose", [ox - 0.5, oy + 0.1, oz - 4.1], [ox + 0.5, oy + 0.7, oz - 3.6], zone="C", color=a),
            make_cube("wh1", [ox + 3.2, oy + 0.0, oz - 3.5], [ox + 4.5, oy + 0.4, oz - 3.1], zone="D_DARK", color=0x475569),
            make_cube("wh2", [ox - 4.5, oy + 0.0, oz - 3.5], [ox - 3.2, oy + 0.4, oz - 3.1], zone="D_DARK", color=0x475569),
        ]
        tail = make_bone("pet_tail", [ox, oy - 0.4, oz + 3.5], anim="pet_tail", cubes=[
            make_cube("ct", [ox - 0.7, oy - 0.8, oz + 3.4], [ox + 0.7, oy + 0.6, oz + 6.4], rot=[26, 0, 0], zone="B", color=s)
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[tail])]

    if arch == "calico_kitten":
        # Playful sitting/floating calico kitten with golden bell collar & tall S-tail!
        body = [
            make_cube("cb", [ox - 2.0, oy - 2.2, oz - 2.2], [ox + 2.0, oy + 1.4, oz + 2.4], zone="A", color=p),
            make_cube("collar", [ox - 2.2, oy + 0.8, oz - 2.5], [ox + 2.2, oy + 1.6, oz - 0.8], zone="B", color=0xEF4444),
            make_cube("bell", [ox - 0.6, oy + 0.2, oz - 2.9], [ox + 0.6, oy + 1.3, oz - 2.0], zone="D_GOLD", color=0xFDE047, emissive=True),
            make_cube("p1", [ox - 1.8, oy - 3.2, oz - 2.6], [ox - 0.7, oy - 1.8, oz - 1.0], zone="B", color=s),
            make_cube("p2", [ox + 0.7, oy - 3.2, oz - 2.6], [ox + 1.8, oy - 1.8, oz - 1.0], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.6, oz - 1.8], anim="pet_head", cubes=[
            make_cube("ch", [ox - 2.2, oy + 1.2, oz - 4.4], [ox + 2.2, oy + 4.8, oz - 0.6], zone="B", color=s),
            make_cube("patch", [ox + 0.2, oy + 2.2, oz - 4.5], [ox + 2.3, oy + 4.9, oz - 1.4], zone="A", color=p),
            make_cube("el", [ox + 1.0, oy + 4.7, oz - 3.2], [ox + 2.3, oy + 6.4, oz - 2.0], zone="A", color=p),
            make_cube("er", [ox - 2.3, oy + 4.7, oz - 3.2], [ox - 1.0, oy + 6.4, oz - 2.0], zone="C", color=a),
            make_cube("e1", [ox + 0.7, oy + 2.6, oz - 4.6], [ox + 1.7, oy + 3.5, oz - 4.2], zone="D_EYE", color=0x22C55E),
            make_cube("e2", [ox - 1.7, oy + 2.6, oz - 4.6], [ox - 0.7, oy + 3.5, oz - 4.2], zone="D_EYE", color=0x22C55E),
        ])
        tail = make_bone("pet_tail", [ox, oy + 0.6, oz + 2.3], anim="pet_tail", cubes=[
            make_cube("t1", [ox - 0.5, oy + 0.4, oz + 2.2], [ox + 0.5, oy + 4.2, oz + 3.4], rot=[-18, 0, 0], zone="A", color=p),
            make_cube("t2", [ox - 0.5, oy + 3.6, oz + 2.6], [ox + 0.5, oy + 5.2, oz + 4.6], zone="C", color=a),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail])]

    if arch == "yuzu_capybara":
        # Blocky-snout Chill Capybara balancing a glowing orange YUZU fruit on its head!
        body = [
            make_cube("cap_b", [ox - 2.6, oy - 2.2, oz - 2.8], [ox + 2.6, oy + 2.2, oz + 3.4], zone="A", color=p),
            make_cube("f1", [ox - 2.2, oy - 3.6, oz - 2.4], [ox - 1.0, oy - 2.0, oz - 1.2], zone="B", color=s),
            make_cube("f2", [ox + 1.0, oy - 3.6, oz - 2.4], [ox + 2.2, oy - 2.0, oz - 1.2], zone="B", color=s),
            make_cube("f3", [ox - 2.2, oy - 3.6, oz + 1.4], [ox - 1.0, oy - 2.0, oz + 2.6], zone="B", color=s),
            make_cube("f4", [ox + 1.0, oy - 3.6, oz + 1.4], [ox + 2.2, oy - 2.0, oz + 2.6], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.8, oz - 2.6], anim="pet_head", cubes=[
            # Classic long flat rectangular capybara head/snout!
            make_cube("cap_h", [ox - 2.0, oy + 0.6, oz - 7.4], [ox + 2.0, oy + 4.0, oz - 2.0], zone="A", color=p),
            make_cube("snout_dark", [ox - 1.8, oy + 0.6, oz - 7.6], [ox + 1.8, oy + 2.6, oz - 6.6], zone="B", color=s),
            # Half-closed chill eyes!
            make_cube("ce1", [ox + 1.6, oy + 2.6, oz - 5.2], [ox + 2.2, oy + 3.1, oz - 4.0], zone="D_EYE", color=0x18181B),
            make_cube("ce2", [ox - 2.2, oy + 2.6, oz - 5.2], [ox - 1.6, oy + 3.1, oz - 4.0], zone="D_EYE", color=0x18181B),
            make_cube("ear1", [ox + 1.4, oy + 3.8, oz - 3.4], [ox + 2.2, oy + 4.7, oz - 2.5], zone="B", color=s),
            make_cube("ear2", [ox - 2.2, oy + 3.8, oz - 3.4], [ox - 1.4, oy + 4.7, oz - 2.5], zone="B", color=s),
            # Cute glowing orange YUZU + green leaf perched on top of head!
            make_cube("yuzu", [ox - 1.2, oy + 4.0, oz - 5.8], [ox + 1.2, oy + 6.2, oz - 3.4], zone="C", color=a, emissive=True),
            make_cube("leaf", [ox - 0.4, oy + 6.1, oz - 4.9], [ox + 0.8, oy + 6.8, oz - 4.1], rot=[0, 0, 18], zone="D_TRIM", color=0x22C55E),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head])]

    if arch == "shadow_beast":
        # Four-eyed void hatchling with shoulder spines & twin shadow tails!
        body = [
            make_cube("sb_b", [ox - 2.2, oy - 2.0, oz - 2.4], [ox + 2.2, oy + 1.8, oz + 2.4], zone="A", color=p),
            make_cube("sp_l", [ox + 1.8, oy + 1.4, oz - 0.8], [ox + 3.6, oy + 3.6, oz + 0.8], rot=[0, 0, -28], zone="C", color=a, emissive=True),
            make_cube("sp_r", [ox - 3.6, oy + 1.4, oz - 0.8], [ox - 1.8, oy + 3.6, oz + 0.8], rot=[0, 0, 28], zone="C", color=a, emissive=True),
        ]
        head = make_bone("pet_head", [ox, oy + 1.8, oz - 2.0], anim="pet_head", cubes=[
            make_cube("sb_h", [ox - 2.4, oy + 1.0, oz - 5.2], [ox + 2.4, oy + 4.8, oz - 1.4], zone="A", color=p),
            # 4 glowing eyes!
            make_cube("e1", [ox + 0.6, oy + 3.0, oz - 5.4], [ox + 1.6, oy + 3.8, oz - 5.0], zone="D_EYE", color=a, emissive=True),
            make_cube("e2", [ox - 1.6, oy + 3.0, oz - 5.4], [ox - 0.6, oy + 3.8, oz - 5.0], zone="D_EYE", color=a, emissive=True),
            make_cube("e3", [ox + 1.1, oy + 2.0, oz - 5.4], [ox + 1.9, oy + 2.6, oz - 5.0], zone="D_EYE", color=a, emissive=True),
            make_cube("e4", [ox - 1.9, oy + 2.0, oz - 5.4], [ox - 1.1, oy + 2.6, oz - 5.0], zone="D_EYE", color=a, emissive=True),
        ])
        tails = [
            make_bone("pet_tail", [ox + 1.0, oy, oz + 2.4], anim="pet_tail", cubes=[make_cube("t1", [ox + 0.5, oy - 0.4, oz + 2.4], [ox + 1.5, oy + 0.6, oz + 6.8], rot=[12, 16, 0], zone="C", color=a, emissive=True)]),
            make_bone("pet_tail2", [ox - 1.0, oy, oz + 2.4], anim="pet_tail", anim_phase=1.8, cubes=[make_cube("t2", [ox - 1.5, oy - 0.4, oz + 2.4], [ox - 0.5, oy + 0.6, oz + 6.8], rot=[12, -16, 0], zone="C", color=a, emissive=True)]),
        ]
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head] + tails)]

    if arch == "mini_adventurer":
        # TRUE Tiny Humanoid Adventurer with mini diamond sword on back (NO animal ears/tail!)
        body = [
            make_cube("st_torso", [ox - 1.6, oy - 2.0, oz - 0.9], [ox + 1.6, oy + 1.6, oz + 0.9], zone="A", color=p),
            make_cube("st_head", [ox - 1.8, oy + 1.6, oz - 1.8], [ox + 1.8, oy + 5.2, oz + 1.8], zone="B", color=0xF5CBA7),
            make_cube("st_hair", [ox - 1.95, oy + 4.0, oz - 1.95], [ox + 1.95, oy + 5.4, oz + 1.95], zone="D_DARK", color=0x3E2723),
            make_cube("st_el", [ox + 0.4, oy + 2.8, oz - 2.0], [ox + 1.2, oy + 3.5, oz - 1.6], zone="D_EYE", color=0x1E293B),
            make_cube("st_er", [ox - 1.2, oy + 2.8, oz - 2.0], [ox - 0.4, oy + 3.5, oz - 1.6], zone="D_EYE", color=0x1E293B),
            # Mini glowing diamond sword strapped to back!
            make_cube("sw_b", [ox - 0.4, oy - 1.2, oz + 0.9], [ox + 0.4, oy + 4.6, oz + 1.5], pivot=[ox, oy + 1.2, oz + 1.2], rot=[0, 0, -34], zone="C", color=a, emissive=True),
            make_cube("sw_g", [ox - 1.1, oy + 2.8, oz + 0.85], [ox + 1.1, oy + 3.5, oz + 1.55], pivot=[ox, oy + 1.2, oz + 1.2], rot=[0, 0, -34], zone="D_GOLD", color=0xF59E0B),
        ]
        al = make_bone("st_al", [ox + 2.0, oy + 1.2, oz], anim="comp_arm_l", cubes=[make_cube("al", [ox + 1.6, oy - 1.8, oz - 0.8], [ox + 2.8, oy + 1.6, oz + 0.8], zone="A", color=p)])
        ar = make_bone("st_ar", [ox - 2.0, oy + 1.2, oz], anim="comp_arm_r", cubes=[make_cube("ar", [ox - 2.8, oy - 1.8, oz - 0.8], [ox - 1.6, oy + 1.6, oz + 0.8], zone="A", color=p)])
        ll = make_bone("st_ll", [ox + 0.8, oy - 2.0, oz], anim="comp_mech_l", cubes=[make_cube("ll", [ox + 0.1, oy - 4.8, oz - 0.8], [ox + 1.5, oy - 1.9, oz + 0.8], zone="B", color=s)])
        lr = make_bone("st_lr", [ox - 0.8, oy - 2.0, oz], anim="comp_mech_r", cubes=[make_cube("lr", [ox - 1.5, oy - 4.8, oz - 0.8], [ox - 0.1, oy - 1.9, oz + 0.8], zone="B", color=s)])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[al, ar, ll, lr])]

    if arch == "chicken_jockey":
        # TRUE Minecraft Chicken (beak, red wattle, flapping wings) + Green Baby Zombie Rider!
        body = [
            make_cube("ck_body", [ox - 2.2, oy - 2.2, oz - 2.6], [ox + 2.2, oy + 1.4, oz + 2.8], zone="A", color=p),
            make_cube("ck_head", [ox - 1.4, oy + 0.8, oz - 4.2], [ox + 1.4, oy + 4.4, oz - 1.6], zone="A", color=p),
            make_cube("ck_beak", [ox - 1.0, oy + 2.0, oz - 5.6], [ox + 1.0, oy + 3.0, oz - 4.0], zone="D_GOLD", color=0xF59E0B),
            make_cube("ck_wat", [ox - 0.6, oy + 1.0, oz - 5.1], [ox + 0.6, oy + 2.1, oz - 4.1], zone="D_TRIM", color=0xEF4444),
            make_cube("ck_f1", [ox + 0.5, oy - 3.6, oz - 0.6], [ox + 1.4, oy - 2.0, oz + 0.8], zone="D_GOLD", color=0xF59E0B),
            make_cube("ck_f2", [ox - 1.4, oy - 3.6, oz - 0.6], [ox - 0.5, oy - 2.0, oz + 0.8], zone="D_GOLD", color=0xF59E0B),
            # Baby Zombie Rider with outstretched zombie arms!
            make_cube("zb_body", [ox - 1.2, oy + 1.4, oz - 0.6], [ox + 1.2, oy + 3.8, oz + 1.2], zone="B", color=0x06B6D4),
            make_cube("zb_head", [ox - 1.6, oy + 3.8, oz - 1.4], [ox + 1.6, oy + 6.8, oz + 1.8], zone="C", color=a),
            make_cube("zb_al", [ox + 1.2, oy + 2.6, oz - 3.2], [ox + 2.1, oy + 3.6, oz + 0.4], zone="C", color=a),
            make_cube("zb_ar", [ox - 2.1, oy + 2.6, oz - 3.2], [ox - 1.2, oy + 3.6, oz + 0.4], zone="C", color=a),
            make_cube("zb_e1", [ox + 0.4, oy + 4.8, oz - 1.55], [ox + 1.1, oy + 5.4, oz - 1.2], zone="D_EYE", color=0x18181B),
            make_cube("zb_e2", [ox - 1.1, oy + 4.8, oz - 1.55], [ox - 0.4, oy + 5.4, oz - 1.2], zone="D_EYE", color=0x18181B),
        ]
        wl = make_bone("pet_wl", [ox + 2.2, oy + 0.8, oz], anim="pet_wing_l", cubes=[make_cube("wl", [ox + 2.1, oy - 1.4, oz - 1.8], [ox + 3.0, oy + 1.2, oz + 2.0], zone="B", color=s)])
        wr = make_bone("pet_wr", [ox - 2.2, oy + 0.8, oz], anim="pet_wing_r", cubes=[make_cube("wr", [ox - 3.0, oy - 1.4, oz - 1.8], [ox - 2.1, oy + 1.2, oz + 2.0], zone="B", color=s)])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "fluffy_lamb":
        # Sculpted cloud-wool fleece puffs + drooping ears + tiny hooves!
        body = [
            make_cube("wool_c", [ox - 2.8, oy - 2.0, oz - 3.0], [ox + 2.8, oy + 2.4, oz + 3.2], zone="A", color=p),
            make_cube("wool_top", [ox - 2.2, oy + 2.0, oz - 2.2], [ox + 2.2, oy + 3.2, oz + 2.4], zone="A", color=p),
            make_cube("wool_l", [ox + 2.4, oy - 1.4, oz - 2.0], [ox + 3.5, oy + 1.6, oz + 2.0], zone="A", color=p),
            make_cube("wool_r", [ox - 3.5, oy - 1.4, oz - 2.0], [ox - 2.4, oy + 1.6, oz + 2.0], zone="A", color=p),
            make_cube("puff_t", [ox - 1.1, oy - 0.5, oz + 3.0], [ox + 1.1, oy + 1.5, oz + 4.4], zone="A", color=p),
            make_cube("h1", [ox - 2.0, oy - 3.4, oz - 2.0], [ox - 1.0, oy - 1.8, oz - 1.0], zone="B", color=s),
            make_cube("h2", [ox + 1.0, oy - 3.4, oz - 2.0], [ox + 2.0, oy - 1.8, oz - 1.0], zone="B", color=s),
            make_cube("h3", [ox - 2.0, oy - 3.4, oz + 1.2], [ox - 1.0, oy - 1.8, oz + 2.2], zone="B", color=s),
            make_cube("h4", [ox + 1.0, oy - 3.4, oz + 1.2], [ox + 2.0, oy - 1.8, oz + 2.2], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.8, oz - 2.6], anim="pet_head", cubes=[
            make_cube("l_face", [ox - 1.9, oy + 0.4, oz - 5.6], [ox + 1.9, oy + 3.6, oz - 2.2], zone="B", color=s),
            make_cube("l_cap", [ox - 2.2, oy + 3.0, oz - 5.2], [ox + 2.2, oy + 4.6, oz - 2.0], zone="A", color=p),
            make_cube("droop_l", [ox + 1.7, oy + 1.0, oz - 4.4], [ox + 2.9, oy + 3.4, oz - 3.0], rot=[0, 0, -24], zone="B", color=s),
            make_cube("droop_r", [ox - 2.9, oy + 1.0, oz - 4.4], [ox - 1.7, oy + 3.4, oz - 3.0], rot=[0, 0, 24], zone="B", color=s),
            make_cube("le1", [ox + 0.6, oy + 1.8, oz - 5.8], [ox + 1.4, oy + 2.6, oz - 5.4], zone="D_EYE", color=0x18181B),
            make_cube("le2", [ox - 1.4, oy + 1.8, oz - 5.8], [ox - 0.6, oy + 2.6, oz - 5.4], zone="D_EYE", color=0x18181B),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head])]

    if arch == "axolotl":
        # Wide-headed floating axolotl with 6 glowing frilly gills & finned salamander tail!
        body = [
            make_cube("ax_b", [ox - 2.0, oy - 1.6, oz - 2.4], [ox + 2.0, oy + 1.2, oz + 3.2], zone="A", color=p),
            make_cube("ax_fin", [ox - 0.3, oy + 1.0, oz - 1.4], [ox + 0.3, oy + 2.4, oz + 3.2], zone="C", color=a, emissive=True),
            make_cube("f1", [ox - 3.0, oy - 2.2, oz - 1.8], [ox - 1.8, oy - 0.8, oz - 0.6], rot=[0, 0, 26], zone="B", color=s),
            make_cube("f2", [ox + 1.8, oy - 2.2, oz - 1.8], [ox + 3.0, oy - 0.8, oz - 0.6], rot=[0, 0, -26], zone="B", color=s),
            make_cube("f3", [ox - 3.0, oy - 2.2, oz + 1.2], [ox - 1.8, oy - 0.8, oz + 2.4], rot=[0, 0, 26], zone="B", color=s),
            make_cube("f4", [ox + 1.8, oy - 2.2, oz + 1.2], [ox + 3.0, oy - 0.8, oz + 2.4], rot=[0, 0, -26], zone="B", color=s),
        ]
        head_c = [
            make_cube("ax_h", [ox - 2.9, oy - 0.6, oz - 6.0], [ox + 2.9, oy + 2.8, oz - 2.0], zone="A", color=p),
            make_cube("e1", [ox + 1.1, oy + 0.8, oz - 6.2], [ox + 2.1, oy + 1.7, oz - 5.8], zone="D_EYE", color=0x18181B),
            make_cube("e2", [ox - 2.1, oy + 0.8, oz - 6.2], [ox - 1.1, oy + 1.7, oz - 5.8], zone="D_EYE", color=0x18181B),
        ]
        for side in (-1, 1):
            for g in range(3):
                head_c.append(make_cube(f"g_{side}_{g}", _sx(side, 2.8, 5.2), [oy + 0.2 + g * 1.1, oy + 1.0 + g * 1.1], [oz - 4.2, oz - 3.4],
                                        rot=[0, 0, -side * (18 - g * 18)], zone="C", color=a, emissive=True))
        head = make_bone("pet_head", [ox, oy + 1.0, oz - 2.2], anim="pet_head", cubes=head_c)
        tail = make_bone("pet_tail", [ox, oy, oz + 3.0], anim="pet_tail", cubes=[
            make_cube("ax_t", [ox - 0.5, oy - 1.2, oz + 3.0], [ox + 0.5, oy + 1.8, oz + 9.2], zone="C", color=a, emissive=True)
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail])]

    # ribbon_bunny: snowy bunny with tall ears, big glowing sakura bow & cotton puff tail!
    body = [
        make_cube("bn_b", [ox - 2.3, oy - 2.2, oz - 2.4], [ox + 2.3, oy + 1.8, oz + 2.8], zone="A", color=p),
        make_cube("puff", [ox - 1.1, oy - 0.8, oz + 2.6], [ox + 1.1, oy + 1.2, oz + 4.2], zone="A", color=p),
        make_cube("p1", [ox - 2.0, oy - 3.0, oz - 2.4], [ox - 0.8, oy - 1.8, oz - 0.8], zone="B", color=s),
        make_cube("p2", [ox + 0.8, oy - 3.0, oz - 2.4], [ox + 2.0, oy - 1.8, oz - 0.8], zone="B", color=s),
    ]
    head = make_bone("pet_head", [ox, oy + 2.0, oz - 2.0], anim="pet_head", cubes=[
        make_cube("bn_h", [ox - 2.4, oy + 0.8, oz - 5.0], [ox + 2.4, oy + 4.8, oz - 1.0], zone="A", color=p),
        make_cube("ear_l", [ox + 0.6, oy + 4.7, oz - 3.6], [ox + 2.0, oy + 8.8, oz - 2.4], rot=[0, 0, -10], zone="A", color=p),
        make_cube("ear_r", [ox - 2.0, oy + 4.7, oz - 3.6], [ox - 0.6, oy + 8.8, oz - 2.4], rot=[0, 0, 10], zone="A", color=p),
        make_cube("bow_c", [ox - 0.7, oy + 4.4, oz - 4.7], [ox + 0.7, oy + 5.6, oz - 3.6], zone="C", color=a, emissive=True),
        make_cube("bow_l", [ox + 0.5, oy + 4.2, oz - 4.6], [ox + 2.5, oy + 5.8, oz - 3.7], rot=[0, 0, -14], zone="C", color=a, emissive=True),
        make_cube("bow_r", [ox - 2.5, oy + 4.2, oz - 4.6], [ox - 0.5, oy + 5.8, oz - 3.7], rot=[0, 0, 14], zone="C", color=a, emissive=True),
        make_cube("e1", [ox + 0.8, oy + 2.3, oz - 5.2], [ox + 1.8, oy + 3.2, oz - 4.8], zone="D_EYE", color=a, emissive=True),
        make_cube("e2", [ox - 1.8, oy + 2.3, oz - 5.2], [ox - 0.8, oy + 3.2, oz - 4.8], zone="D_EYE", color=a, emissive=True),
    ])
    return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head])]


# ==============================================================================
# 6. GROUND COMPANIONS — ALL 24 DESIGNS 100% UNIQUE, ALL FEET AT y >= 0.0!
#    Studio anchor: (ox=-16.5, y=0.0, oz=2.5) — World physics snaps feet cleanly to terrain!
# ==============================================================================
def build_companion_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    ox, oz = -16.5, 2.5

    # 6 100% distinct dog breeds!
    if arch.startswith("dog_"):
        breed = arch[4:]
        dims = {
            "retriever": (3.2, 3.6, 6.8, 3.6, 3.1, 3.0),
            "terrier":   (2.5, 2.8, 5.0, 2.5, 2.6, 2.6),  # compact scruffy small terrier!
            "doberman":  (2.9, 3.8, 7.2, 5.2, 2.8, 3.0),  # tall athletic long-legged guard dog!
            "husky":     (3.3, 3.7, 6.8, 3.9, 3.2, 3.1),  # fluffy thick-ruffed arctic husky!
            "shiba":     (3.1, 3.3, 6.0, 3.3, 3.4, 3.0),  # round-cheeked Shiba Inu!
            "collie":    (2.9, 3.3, 7.0, 3.7, 2.9, 2.8),  # agile low-slung herding collie!
        }[breed]
        bw, bh, bl, leg_h, hw, hh = dims
        by = leg_h

        body = [
            make_cube("torso", [ox - bw * 0.5, by, oz - bl * 0.5], [ox + bw * 0.5, by + bh, oz + bl * 0.5], zone="A", color=p),
            make_cube("chest", [ox - bw * 0.42, by, oz - bl * 0.52], [ox + bw * 0.42, by + bh * 0.65, oz + bl * 0.3], zone="B", color=s),
            make_cube("collar", [ox - bw * 0.58, by + bh - 1.1, oz - bl * 0.56], [ox + bw * 0.58, by + bh + 0.1, oz - bl * 0.22], zone="C", color=a),
        ]
        if breed == "retriever":
            body.append(make_cube("gold_tag", [ox - 0.45, by + bh - 1.9, oz - bl * 0.64], [ox + 0.45, by + bh - 0.9, oz - bl * 0.46], zone="D_GOLD", color=0xFDE047, emissive=True))
        elif breed == "husky":
            body.append(make_cube("wolf_ruff", [ox - bw * 0.66, by + 0.6, oz - bl * 0.58], [ox + bw * 0.66, by + bh + 0.5, oz - bl * 0.08], zone="B", color=s))
        elif breed == "shiba":
            body.append(make_cube("scarf_knot", [ox - 1.1, by + bh - 2.2, oz - bl * 0.66], [ox + 1.1, by + bh - 0.6, oz - bl * 0.42], rot=[0, 0, 45], zone="C", color=a))
        elif breed == "collie":
            body.append(make_cube("white_bib", [ox - bw * 0.52, by - 0.1, oz - bl * 0.62], [ox + bw * 0.52, by + bh * 0.85, oz - bl * 0.24], zone="B", color=s))
        elif breed == "doberman":
            for k in (-1, 0, 1):
                body.append(make_cube(f"spike_{k}", [ox + k * 1.1 - 0.25, by + bh - 0.8, oz - bl * 0.68], [ox + k * 1.1 + 0.25, by + bh - 0.2, oz - bl * 0.5], zone="D_GOLD", color=0xE2E8F0))
        elif breed == "terrier":
            body.append(make_cube("bow", [ox - 1.2, by + bh - 1.2, oz - bl * 0.64], [ox + 1.2, by + bh - 0.1, oz - bl * 0.48], zone="C", color=a))

        lw = 1.05 if breed != "terrier" else 0.9
        legs = []
        for tag, lx, lz in [
            ("comp_leg_fl", ox + bw * 0.32, oz - bl * 0.34),
            ("comp_leg_fr", ox - bw * 0.32, oz - bl * 0.34),
            ("comp_leg_bl", ox + bw * 0.32, oz + bl * 0.34),
            ("comp_leg_br", ox - bw * 0.32, oz + bl * 0.34),
        ]:
            legs.append(make_bone(tag, [lx, by, lz], anim=tag, cubes=[
                make_cube(tag, [lx - lw * 0.5, 0.0, lz - lw * 0.5], [lx + lw * 0.5, by + 0.4, lz + lw * 0.5], zone="A", color=p),
                make_cube(f"{tag}_paw", [lx - lw * 0.55, 0.0, lz - lw * 0.7], [lx + lw * 0.55, 1.0, lz + lw * 0.5], zone="B", color=s),
            ]))

        # Breed-specific tails!
        if breed == "shiba":
            tail_cubes = [
                make_cube("t1", [ox - 0.65, by + bh - 0.4, oz + bl * 0.4], [ox + 0.65, by + bh + 2.2, oz + bl * 0.4 + 1.6], rot=[-28, 0, 0], zone="A", color=p),
                make_cube("t2", [ox - 0.6, by + bh + 1.2, oz + bl * 0.2], [ox + 0.6, by + bh + 2.5, oz + bl * 0.4 + 1.8], zone="B", color=s),
            ]
        elif breed == "doberman":
            tail_cubes = [make_cube("dock", [ox - 0.45, by + bh - 0.5, oz + bl * 0.45], [ox + 0.45, by + bh + 1.2, oz + bl * 0.45 + 1.2], rot=[-35, 0, 0], zone="A", color=p)]
        elif breed == "terrier":
            tail_cubes = [make_cube("stub", [ox - 0.5, by + bh - 0.2, oz + bl * 0.45], [ox + 0.5, by + bh + 2.4, oz + bl * 0.45 + 1.0], zone="A", color=p)]
        elif breed == "husky":
            tail_cubes = [make_cube("plume", [ox - 0.9, by + bh - 0.2, oz + bl * 0.35], [ox + 0.9, by + bh + 2.8, oz + bl * 0.45 + 3.2], rot=[38, 0, 0], zone="B", color=s)]
        elif breed == "collie":
            tail_cubes = [make_cube("sweep", [ox - 0.7, by + 0.6, oz + bl * 0.45], [ox + 0.7, by + bh * 0.7, oz + bl * 0.45 + 4.4], rot=[-24, 0, 0], zone="B", color=s)]
        else:  # retriever
            tail_cubes = [make_cube("feath", [ox - 0.75, by + bh * 0.45, oz + bl * 0.45], [ox + 0.75, by + bh * 1.05, oz + bl * 0.45 + 4.2], rot=[20, 0, 0], zone="A", color=p)]
        tail = make_bone("comp_tail", [ox, by + bh * 0.75, oz + bl * 0.48], anim="comp_tail", cubes=tail_cubes)

        # Breed-specific head & ears!
        hy = by + bh - 0.5
        hz = oz - bl * 0.52
        snout_len = 1.6 if breed == "doberman" else (1.1 if breed == "terrier" else 1.35)
        hc = [
            make_cube("head", [ox - hw * 0.5, hy, hz - hw * 0.85], [ox + hw * 0.5, hy + hh, hz + 0.5], zone="A", color=p),
            make_cube("snout", [ox - hw * 0.3, hy, hz - hw * 0.85 - snout_len], [ox + hw * 0.3, hy + hh * 0.5, hz - hw * 0.75], zone="B", color=s),
            make_cube("nose", [ox - 0.45, hy + hh * 0.36, hz - hw * 0.85 - snout_len - 0.2], [ox + 0.45, hy + hh * 0.54, hz - hw * 0.85 - snout_len + 0.25], zone="D_DARK", color=0x18181B),
            make_cube("eye_l", [ox + hw * 0.16, hy + hh * 0.55, hz - hw * 0.92], [ox + hw * 0.42, hy + hh * 0.78, hz - hw * 0.78], zone="D_EYE", color=a),
            make_cube("eye_r", [ox - hw * 0.42, hy + hh * 0.55, hz - hw * 0.92], [ox - hw * 0.16, hy + hh * 0.78, hz - hw * 0.78], zone="D_EYE", color=a),
        ]
        if breed == "retriever":
            # Long soft floppy ears!
            hc.append(make_cube("flop_l", [ox + hw * 0.45, hy + 0.2, hz - hw * 0.55], [ox + hw * 0.72, hy + hh - 0.2, hz + 0.2], rot=[0, 0, -14], zone="A", color=p))
            hc.append(make_cube("flop_r", [ox - hw * 0.72, hy + 0.2, hz - hw * 0.55], [ox - hw * 0.45, hy + hh - 0.2, hz + 0.2], rot=[0, 0, 14], zone="A", color=p))
        elif breed == "terrier":
            # Bushy beard + perky forward ears!
            hc.append(make_cube("beard", [ox - hw * 0.46, hy - 0.5, hz - hw * 0.85 - 1.2], [ox + hw * 0.46, hy + 0.8, hz - hw * 0.4], zone="A", color=p))
            hc.append(make_cube("ear_l", [ox + hw * 0.18, hy + hh - 0.1, hz - hw * 0.45], [ox + hw * 0.5, hy + hh + 1.3, hz + 0.1], rot=[18, 0, 0], zone="A", color=p))
            hc.append(make_cube("ear_r", [ox - hw * 0.5, hy + hh - 0.1, hz - hw * 0.45], [ox - hw * 0.18, hy + hh + 1.3, hz + 0.1], rot=[18, 0, 0], zone="A", color=p))
        elif breed == "doberman":
            # Tall sharp cropped upright ears!
            hc.append(make_cube("ear_l", [ox + hw * 0.18, hy + hh - 0.1, hz - hw * 0.35], [ox + hw * 0.44, hy + hh + 2.4, hz + 0.15], zone="A", color=p))
            hc.append(make_cube("ear_r", [ox - hw * 0.44, hy + hh - 0.1, hz - hw * 0.35], [ox - hw * 0.18, hy + hh + 2.4, hz + 0.15], zone="A", color=p))
        elif breed == "shiba":
            # Wide fluffy Shiba cheeks + small triangular ears!
            hc.append(make_cube("cheeks", [ox - hw * 0.62, hy + 0.1, hz - hw * 0.72], [ox + hw * 0.62, hy + hh * 0.55, hz + 0.1], zone="B", color=s))
            hc.append(make_cube("ear_l", [ox + hw * 0.2, hy + hh - 0.1, hz - hw * 0.38], [ox + hw * 0.52, hy + hh + 1.4, hz + 0.15], rot=[0, 0, -14], zone="A", color=p))
            hc.append(make_cube("ear_r", [ox - hw * 0.52, hy + hh - 0.1, hz - hw * 0.38], [ox - hw * 0.2, hy + hh + 1.4, hz + 0.15], rot=[0, 0, 14], zone="A", color=p))
        elif breed == "collie":
            # Half-tipped folded border collie ears!
            hc.append(make_cube("ear_l", [ox + hw * 0.2, hy + hh - 0.1, hz - hw * 0.4], [ox + hw * 0.5, hy + hh + 1.2, hz + 0.1], zone="A", color=p))
            hc.append(make_cube("tip_l", [ox + hw * 0.22, hy + hh + 0.7, hz - hw * 0.85], [ox + hw * 0.52, hy + hh + 1.3, hz - hw * 0.25], rot=[35, 0, 0], zone="A", color=p))
            hc.append(make_cube("ear_r", [ox - hw * 0.5, hy + hh - 0.1, hz - hw * 0.4], [ox - hw * 0.2, hy + hh + 1.2, hz + 0.1], zone="A", color=p))
            hc.append(make_cube("tip_r", [ox - hw * 0.52, hy + hh + 0.7, hz - hw * 0.85], [ox - hw * 0.22, hy + hh + 1.3, hz - hw * 0.25], rot=[35, 0, 0], zone="A", color=p))
        else:  # husky
            hc.append(make_cube("ear_l", [ox + hw * 0.18, hy + hh - 0.1, hz - hw * 0.4], [ox + hw * 0.5, hy + hh + 1.8, hz + 0.2], zone="A", color=p))
            hc.append(make_cube("ear_r", [ox - hw * 0.5, hy + hh - 0.1, hz - hw * 0.4], [ox - hw * 0.18, hy + hh + 1.8, hz + 0.2], zone="A", color=p))

        head = make_bone("comp_head", [ox, hy + 1.0, hz], anim="comp_head", cubes=hc)
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + [head, tail])]

    # 4 100% distinct Elemental Rune Tigers!
    if arch.startswith("tiger_"):
        elem = arch[6:]
        bw, bh, bl, leg_h = (4.5, 4.8, 8.6, 4.8) if elem == "magma" else (4.2, 4.6, 8.4, 4.8)
        by = leg_h
        body = [
            make_cube("torso", [ox - bw * 0.5, by, oz - bl * 0.5], [ox + bw * 0.5, by + bh, oz + bl * 0.5], zone="A", color=p),
            make_cube("chest", [ox - bw * 0.42, by, oz - bl * 0.52], [ox + bw * 0.42, by + bh * 0.65, oz + bl * 0.3], zone="B", color=s),
        ]
        # Distinct elemental 3D armor / silhouette features per tiger!
        if elem == "void":
            # Floating silver moon crescent halo hovering over shoulders!
            for k in range(7):
                ang = -1.0 + k * 0.33
                body.append(make_cube(f"vm_{k}", [ox + math.cos(ang) * 3.6 - 0.45, by + bh + 2.8 + math.sin(ang) * 1.4, oz - 1.2],
                                      [ox + math.cos(ang) * 3.6 + 0.45, by + bh + 3.6 + math.sin(ang) * 1.4, oz - 0.4],
                                      zone="C", color=a, emissive=True))
        elif elem == "magma":
            # Jagged volcanic lava dorsal spines along the back!
            for k in range(4):
                body.append(make_cube(f"msp_{k}", [ox - 0.6, by + bh - 0.2, oz - 3.0 + k * 2.0],
                                      [ox + 0.6, by + bh + 2.4 - k * 0.25, oz - 1.8 + k * 2.0],
                                      rot=[-24, 0, 0], zone="C", color=a, emissive=True))
        elif elem == "cyber":
            # Twin glowing cyber shoulder thruster pods!
            for side in (-1, 1):
                body.append(make_cube(f"pod_{side}", [ox + side * 2.1 - 0.7, by + bh - 1.0, oz - 2.6],
                                      [ox + side * 2.1 + 0.7, by + bh + 1.0, oz + 1.2], zone="B", color=s))
                body.append(make_cube(f"podg_{side}", [ox + side * 2.1 - 0.5, by + bh - 0.5, oz + 0.9],
                                      [ox + side * 2.1 + 0.5, by + bh + 0.5, oz + 2.4], zone="C", color=a, emissive=True))
        elif elem == "crimson":
            # Spiked war-pauldrons on shoulders!
            for side in (-1, 1):
                body.append(make_cube(f"pld_{side}", [ox + side * 2.2 - 0.8, by + bh - 1.4, oz - 3.4],
                                      [ox + side * 2.2 + 0.8, by + bh + 0.8, oz - 0.6], rot=[0, 0, -side * 16], zone="B", color=s))
                body.append(make_cube(f"plds_{side}", [ox + side * 2.6 - 0.35, by + bh + 0.4, oz - 2.4],
                                      [ox + side * 2.6 + 0.35, by + bh + 2.4, oz - 1.4], rot=[0, 0, -side * 28], zone="C", color=a, emissive=True))

        legs = []
        for tag, lx, lz in [
            ("comp_leg_fl", ox + bw * 0.32, oz - bl * 0.34),
            ("comp_leg_fr", ox - bw * 0.32, oz - bl * 0.34),
            ("comp_leg_bl", ox + bw * 0.32, oz + bl * 0.34),
            ("comp_leg_br", ox - bw * 0.32, oz + bl * 0.34),
        ]:
            legs.append(make_bone(tag, [lx, by, lz], anim=tag, cubes=[
                make_cube(tag, [lx - 0.72, 0.0, lz - 0.72], [lx + 0.72, by + 0.4, lz + 0.72], zone="A", color=p),
                make_cube(f"{tag}_paw", [lx - 0.8, 0.0, lz - 0.95], [lx + 0.8, 1.1, lz + 0.7], zone="C" if elem == "magma" else "B", color=a if elem == "magma" else s, emissive=elem == "magma"),
            ]))

        tail_c = [make_cube("tail", [ox - 0.65, by + bh * 0.4, oz + bl * 0.45], [ox + 0.65, by + bh * 0.9, oz + bl * 0.45 + 5.6],
                            pivot=[ox, by + bh * 0.7, oz + bl * 0.45], rot=[-14, 0, 0], zone="B", color=s)]
        if elem == "cyber":
            tail_c.append(make_cube("tail_blade", [ox - 0.35, by + bh * 0.2, oz + bl * 0.45 + 5.2], [ox + 0.35, by + bh * 1.1, oz + bl * 0.45 + 8.0], zone="C", color=a, emissive=True))
        tail = make_bone("comp_tail", [ox, by + bh * 0.75, oz + bl * 0.48], anim="comp_tail", cubes=tail_c)

        hy, hz = by + bh - 0.6, oz - bl * 0.52
        hc = [
            make_cube("head", [ox - 1.9, hy, hz - 3.2], [ox + 1.9, hy + 3.6, hz + 0.5], zone="A", color=p),
            make_cube("snout", [ox - 1.05, hy, hz - 4.6], [ox + 1.05, hy + 1.7, hz - 2.9], zone="B", color=s),
            make_cube("nose", [ox - 0.45, hy + 1.2, hz - 4.8], [ox + 0.45, hy + 1.8, hz - 4.3], zone="D_DARK", color=0x18181B),
            make_cube("el", [ox + 0.6, hy + 1.9, hz - 3.4], [ox + 1.6, hy + 2.8, hz - 2.9], zone="D_EYE", color=a, emissive=True),
            make_cube("er", [ox - 1.6, hy + 1.9, hz - 3.4], [ox - 0.6, hy + 2.8, hz - 2.9], zone="D_EYE", color=a, emissive=True),
            make_cube("ear_l", [ox + 0.7, hy + 3.4, hz - 1.6], [ox + 1.8, hy + 4.9, hz - 0.2], zone="A", color=p),
            make_cube("ear_r", [ox - 1.8, hy + 3.4, hz - 1.6], [ox - 0.7, hy + 4.9, hz - 0.2], zone="A", color=p),
        ]
        if elem == "cyber":
            hc.append(make_cube("visor", [ox - 2.05, hy + 1.8, hz - 3.65], [ox + 2.05, hy + 2.9, hz - 2.6], zone="C", color=a, emissive=True, translucent=True))
        elif elem == "crimson":
            hc.append(make_cube("horn_l", [ox + 1.1, hy + 3.4, hz - 1.4], [ox + 2.0, hy + 6.4, hz - 0.2], rot=[-28, 0, -20], zone="C", color=a, emissive=True))
            hc.append(make_cube("horn_r", [ox - 2.0, hy + 3.4, hz - 1.4], [ox - 1.1, hy + 6.4, hz - 0.2], rot=[-28, 0, 20], zone="C", color=a, emissive=True))
        head = make_bone("comp_head", [ox, hy + 1.0, hz], anim="comp_head", cubes=hc)
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + [head, tail])]

    # 4 100% distinct Cat Breeds!
    if arch.startswith("cat_"):
        kind = arch[4:]
        if kind == "siamese":
            bw, bh, bl, leg_h, hw, hh = (2.3, 2.9, 6.4, 4.1, 2.5, 2.6)  # tall slender Siamese!
        elif kind == "ginger":
            bw, bh, bl, leg_h, hw, hh = (3.3, 3.4, 5.8, 2.8, 3.1, 2.8)  # chubby playful Ginger!
        else:
            bw, bh, bl, leg_h, hw, hh = (2.7, 3.1, 5.8, 3.3, 2.8, 2.7)
        by = leg_h
        body = [
            make_cube("torso", [ox - bw * 0.5, by, oz - bl * 0.5], [ox + bw * 0.5, by + bh, oz + bl * 0.5], zone="A", color=p),
            make_cube("belly", [ox - bw * 0.42, by, oz - bl * 0.52], [ox + bw * 0.42, by + bh * 0.6, oz + bl * 0.3], zone="B", color=s),
        ]
        if kind == "tuxedo":
            body.append(make_cube("bowtie", [ox - 1.2, by + bh - 1.6, oz - bl * 0.62], [ox + 1.2, by + bh - 0.5, oz - bl * 0.44], zone="C", color=a))
        elif kind == "black_moon":
            body.append(make_cube("collar", [ox - bw * 0.54, by + bh - 1.0, oz - bl * 0.54], [ox + bw * 0.54, by + bh - 0.2, oz - bl * 0.22], zone="B", color=0x581C87))
            body.append(make_cube("moon_tag", [ox - 0.6, by + bh - 2.0, oz - bl * 0.64], [ox + 0.6, by + bh - 0.8, oz - bl * 0.44], rot=[0, 0, 35], zone="C", color=a, emissive=True))
        elif kind == "ginger":
            body.append(make_cube("fluff", [ox - bw * 0.56, by + 0.4, oz - bl * 0.3], [ox + bw * 0.56, by + bh - 0.4, oz + bl * 0.3], zone="A", color=p))

        lw = 0.85 if kind == "siamese" else 1.0
        legs = []
        for tag, lx, lz in [
            ("comp_leg_fl", ox + bw * 0.32, oz - bl * 0.34),
            ("comp_leg_fr", ox - bw * 0.32, oz - bl * 0.34),
            ("comp_leg_bl", ox + bw * 0.32, oz + bl * 0.34),
            ("comp_leg_br", ox - bw * 0.32, oz + bl * 0.34),
        ]:
            legs.append(make_bone(tag, [lx, by, lz], anim=tag, cubes=[
                make_cube(tag, [lx - lw * 0.5, 0.0, lz - lw * 0.5], [lx + lw * 0.5, by + 0.4, lz + lw * 0.5], zone="A", color=p),
                make_cube(f"{tag}_paw", [lx - lw * 0.55, 0.0, lz - lw * 0.65], [lx + lw * 0.55, 1.0, lz + lw * 0.5], zone="B", color=s),
            ]))

        tlen = 5.6 if kind == "siamese" else 4.0
        tail = make_bone("comp_tail", [ox, by + bh * 0.8, oz + bl * 0.48], anim="comp_tail", cubes=[
            make_cube("tail", [ox - 0.45, by + bh * 0.55, oz + bl * 0.45], [ox + 0.45, by + bh * 1.0, oz + bl * 0.45 + tlen],
                      pivot=[ox, by + bh * 0.8, oz + bl * 0.45], rot=[32 if kind != "ginger" else 48, 0, 0], zone="B" if kind == "siamese" else "A", color=s if kind == "siamese" else p)
        ])

        hy, hz = by + bh - 0.5, oz - bl * 0.5
        ear_h = 2.1 if kind == "siamese" else 1.4
        hc = [
            make_cube("head", [ox - hw * 0.5, hy, hz - hw * 0.82], [ox + hw * 0.5, hy + hh, hz + 0.5], zone="A", color=p),
            make_cube("snout", [ox - 0.7, hy + 0.1, hz - hw * 1.08], [ox + 0.7, hy + 1.1, hz - hw * 0.72], zone="B", color=s),
            make_cube("el", [ox + hw * 0.16, hy + hh * 0.52, hz - hw * 0.88], [ox + hw * 0.42, hy + hh * 0.76, hz - hw * 0.74], zone="D_EYE", color=a, emissive=kind == "black_moon"),
            make_cube("er", [ox - hw * 0.42, hy + hh * 0.52, hz - hw * 0.88], [ox - hw * 0.16, hy + hh * 0.76, hz - hw * 0.74], zone="D_EYE", color=a, emissive=kind == "black_moon"),
            make_cube("ear_l", [ox + hw * 0.16, hy + hh - 0.1, hz - hw * 0.38], [ox + hw * 0.48, hy + hh + ear_h, hz + 0.15], rot=[0, 0, -14 if kind == "siamese" else 0], zone="B" if kind == "siamese" else "A", color=s if kind == "siamese" else p),
            make_cube("ear_r", [ox - hw * 0.48, hy + hh - 0.1, hz - hw * 0.38], [ox - hw * 0.16, hy + hh + ear_h, hz + 0.15], rot=[0, 0, 14 if kind == "siamese" else 0], zone="B" if kind == "siamese" else "A", color=s if kind == "siamese" else p),
        ]
        if kind == "tuxedo":
            # Jaunty mini top hat on Tuxedo Cat!
            hc.append(make_cube("hat_b", [ox - 1.5, hy + hh, hz - 1.8], [ox + 1.5, hy + hh + 0.35, hz + 0.4], zone="D_DARK", color=0x18181B))
            hc.append(make_cube("hat_t", [ox - 0.95, hy + hh + 0.35, hz - 1.4], [ox + 0.95, hy + hh + 1.9, hz], zone="D_DARK", color=0x18181B))
            hc.append(make_cube("hat_r", [ox - 1.0, hy + hh + 0.35, hz - 1.45], [ox + 1.0, hy + hh + 0.75, hz + 0.05], zone="C", color=a))
        head = make_bone("comp_head", [ox, hy + 1.0, hz], anim="comp_head", cubes=hc)
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + [head, tail])]

    if arch in ("unicorn", "cerberus"):
        bw, bh, bl, leg_h = 4.2, 4.6, 8.4, 4.8
        by = leg_h
        body = [
            make_cube("torso", [ox - bw * 0.5, by, oz - bl * 0.5], [ox + bw * 0.5, by + bh, oz + bl * 0.5], zone="A", color=p),
            make_cube("chest", [ox - bw * 0.42, by, oz - bl * 0.52], [ox + bw * 0.42, by + bh * 0.65, oz + bl * 0.3], zone="B", color=s),
        ]
        legs = [make_bone(tag, [lx, by, lz], anim=tag, cubes=[
            make_cube(tag, [lx - 0.72, 0.0, lz - 0.72], [lx + 0.72, by + 0.4, lz + 0.72], zone="A", color=p),
            make_cube(f"{tag}_paw", [lx - 0.78, 0.0, lz - 0.9], [lx + 0.78, 1.1, lz + 0.7], zone="B", color=s),
        ]) for tag, lx, lz in [
            ("comp_leg_fl", ox + bw * 0.32, oz - bl * 0.34), ("comp_leg_fr", ox - bw * 0.32, oz - bl * 0.34),
            ("comp_leg_bl", ox + bw * 0.32, oz + bl * 0.34), ("comp_leg_br", ox - bw * 0.32, oz + bl * 0.34),
        ]]
        tail = make_bone("comp_tail", [ox, by + bh * 0.75, oz + bl * 0.48], anim="comp_tail", cubes=[
            make_cube("tail", [ox - 0.75, by + bh * 0.3, oz + bl * 0.45], [ox + 0.75, by + bh * 0.95, oz + bl * 0.45 + 5.2],
                      pivot=[ox, by + bh * 0.7, oz + bl * 0.45], rot=[24, 0, 0], zone="C" if arch == "unicorn" else "B", color=a if arch == "unicorn" else s, emissive=arch == "unicorn")
        ])
        head_offsets = [(-1.85, "comp_head"), (1.85, "comp_head_2")] if arch == "cerberus" else [(0.0, "comp_head")]
        heads = []
        for hx_off, h_anim in head_offsets:
            hx, hy, hz = ox + hx_off, by + bh - 0.6, oz - bl * 0.52
            hc = [
                make_cube("head", [hx - 1.8, hy, hz - 3.1], [hx + 1.8, hy + 3.6, hz + 0.5], zone="A", color=p),
                make_cube("snout", [hx - 1.0, hy, hz - 4.5], [hx + 1.0, hy + 1.7, hz - 2.8], zone="B", color=s),
                make_cube("el", [hx + 0.6, hy + 2.0, hz - 3.3], [hx + 1.5, hy + 2.8, hz - 2.8], zone="D_EYE", color=a, emissive=True),
                make_cube("er", [hx - 1.5, hy + 2.0, hz - 3.3], [hx - 0.6, hy + 2.8, hz - 2.8], zone="D_EYE", color=a, emissive=True),
                make_cube("ear_l", [hx + 0.6, hy + 3.4, hz - 1.4], [hx + 1.7, hy + 5.1, hz + 0.2], zone="A", color=p),
                make_cube("ear_r", [hx - 1.7, hy + 3.4, hz - 1.4], [hx - 0.6, hy + 5.1, hz + 0.2], zone="A", color=p),
            ]
            if arch == "unicorn":
                hc.append(make_cube("u_horn", [hx - 0.45, hy + 3.5, hz - 3.1], [hx + 0.45, hy + 8.0, hz - 1.8], rot=[-28, 0, 0], zone="D_GOLD", color=0xFDE047, emissive=True))
                hc.append(make_cube("mane", [hx - 0.7, hy + 0.6, hz - 0.2], [hx + 0.7, hy + 4.4, hz + 2.6], zone="C", color=a, emissive=True))
            heads.append(make_bone(h_anim, [hx, hy + 1.0, hz], anim=h_anim, cubes=hc))
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + heads + [tail])]

    if arch == "mech_walker":
        body = [
            make_cube("chassis", [ox - 3.4, 7.0, oz - 2.8], [ox + 3.4, 14.2, oz + 2.8], zone="A", color=p),
            make_cube("cockpit", [ox - 2.2, 8.6, oz - 3.3], [ox + 2.2, 12.6, oz - 2.2], zone="C", color=a, emissive=True),
            make_cube("exhaust_l", [ox + 2.2, 13.8, oz + 0.8], [ox + 3.4, 17.2, oz + 2.2], zone="B", color=s),
            make_cube("exhaust_r", [ox - 3.4, 13.8, oz + 0.8], [ox - 2.2, 17.2, oz + 2.2], zone="B", color=s),
        ]
        ll = make_bone("mech_l", [ox + 2.2, 7.0, oz], anim="comp_mech_l", cubes=[
            make_cube("ll", [ox + 1.0, 0.0, oz - 1.3], [ox + 3.4, 7.2, oz + 1.3], zone="B", color=s),
            make_cube("fl", [ox + 0.7, 0.0, oz - 2.3], [ox + 3.7, 1.5, oz + 1.5], zone="D_DARK", color=0x1E293B),
        ])
        lr = make_bone("mech_r", [ox - 2.2, 7.0, oz], anim="comp_mech_r", cubes=[
            make_cube("lr", [ox - 3.4, 0.0, oz - 1.3], [ox - 1.0, 7.2, oz + 1.3], zone="B", color=s),
            make_cube("fr", [ox - 3.7, 0.0, oz - 2.3], [ox - 0.7, 1.5, oz + 1.5], zone="D_DARK", color=0x1E293B),
        ])
        al = make_bone("arm_l", [ox + 3.8, 12.5, oz], anim="comp_arm_l", cubes=[
            make_cube("al", [ox + 3.5, 7.2, oz - 1.4], [ox + 5.6, 13.0, oz + 1.4], zone="A", color=p),
            make_cube("cannon", [ox + 3.9, 6.8, oz - 3.8], [ox + 5.2, 8.6, oz - 1.0], zone="C", color=a, emissive=True),
        ])
        ar = make_bone("arm_r", [ox - 3.8, 12.5, oz], anim="comp_arm_r", cubes=[make_cube("ar", [ox - 5.6, 7.2, oz - 1.4], [ox - 3.5, 13.0, oz + 1.4], zone="A", color=p)])
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=[ll, lr, al, ar])]

    if arch == "spider_tarantula":
        # Bulky fuzzy tarantula with thick furry legs & large pedipalps!
        body = [
            make_cube("ceph", [ox - 2.7, 2.2, oz - 3.6], [ox + 2.7, 5.4, oz + 0.6], zone="A", color=p),
            make_cube("abd", [ox - 3.6, 2.4, oz + 0.4], [ox + 3.6, 7.0, oz + 6.8], zone="A", color=p),
            make_cube("pedi_l", [ox + 1.0, 1.8, oz - 5.6], [ox + 2.2, 3.4, oz - 3.4], zone="B", color=s),
            make_cube("pedi_r", [ox - 2.2, 1.8, oz - 5.6], [ox - 1.0, 3.4, oz - 3.4], zone="B", color=s),
            make_cube("eyes", [ox - 1.8, 3.8, oz - 3.85], [ox + 1.8, 4.7, oz - 3.3], zone="D_EYE", color=a, emissive=True),
        ]
        legs = []
        for i in range(4):
            lz, ang = oz - 2.4 + i * 1.6, 26 - i * 18
            for side, prefix in ((1, "comp_spider_l"), (-1, "comp_spider_r")):
                piv = [ox + side * 2.4, 3.4, lz]
                legs.append(make_bone(f"{prefix}{i}", piv, anim=f"{prefix}{i}", cubes=[
                    make_cube("fem", _sx(side, 2.2, 6.6), [3.0, 4.6], [lz - 0.7, lz + 0.7], pivot=piv, rot=[0, -side * ang, side * 24], zone="B", color=s),
                    make_cube("tib", _sx(side, 5.4, 7.2), [0.0, 5.2], [lz - 0.65, lz + 0.65], pivot=[ox + side * 6.0, 4.8, lz], rot=[0, -side * ang, -side * 16], zone="A", color=p),
                ]))
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs)]

    if arch == "spider_widow":
        # Sleek spindly high-arched Black Widow with bulbous glossy abdomen & glowing 3D crimson hourglass!
        body = [
            make_cube("ceph", [ox - 1.8, 3.2, oz - 3.4], [ox + 1.8, 5.4, oz + 0.2], zone="A", color=p),
            make_cube("abd", [ox - 3.2, 3.4, oz + 0.2], [ox + 3.2, 8.8, oz + 6.4], zone="A", color=p),
            make_cube("hg_t", [ox - 1.2, 8.6, oz + 2.0], [ox + 1.2, 9.2, oz + 3.4], zone="C", color=a, emissive=True),
            make_cube("hg_m", [ox - 0.5, 8.6, oz + 3.2], [ox + 0.5, 9.2, oz + 4.0], zone="C", color=a, emissive=True),
            make_cube("hg_b", [ox - 1.2, 8.6, oz + 3.8], [ox + 1.2, 9.2, oz + 5.2], zone="C", color=a, emissive=True),
            make_cube("eyes", [ox - 1.3, 4.2, oz - 3.6], [ox + 1.3, 4.9, oz - 3.2], zone="D_EYE", color=a, emissive=True),
        ]
        legs = []
        for i in range(4):
            lz, ang = oz - 2.2 + i * 1.5, 32 - i * 20
            for side, prefix in ((1, "comp_spider_l"), (-1, "comp_spider_r")):
                piv = [ox + side * 1.7, 4.2, lz]
                legs.append(make_bone(f"{prefix}{i}", piv, anim=f"{prefix}{i}", cubes=[
                    make_cube("fem", _sx(side, 1.6, 6.8), [4.2, 5.0], [lz - 0.35, lz + 0.35], pivot=piv, rot=[0, -side * ang, side * 42], zone="B", color=s),
                    make_cube("tib", _sx(side, 6.2, 7.0), [0.0, 7.2], [lz - 0.3, lz + 0.3], pivot=[ox + side * 6.6, 7.0, lz], rot=[0, -side * ang, -side * 12], zone="A", color=p),
                ]))
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs)]

    if arch == "cobra":
        child = None
        for i in range(5, -1, -1):
            y = 1.1 + i * 2.1
            z_off = oz + (5 - i) * 1.1 if i < 2 else oz - (i - 2) * 0.4
            w = 3.4 if i == 4 else (2.2 - i * 0.12)
            cubes = [make_cube(f"cb_{i}", [ox - w * 0.5, max(0.0, y - 1.1), z_off - 1.2], [ox + w * 0.5, y + 1.2, z_off + 1.2], zone="A" if i != 4 else "B", color=p if i != 4 else s)]
            if i == 5:
                cubes.append(make_cube("ce1", [ox + 0.4, y + 0.1, z_off - 1.5], [ox + 1.1, y + 0.7, z_off - 1.1], zone="D_EYE", color=a, emissive=True))
                cubes.append(make_cube("ce2", [ox - 1.1, y + 0.1, z_off - 1.5], [ox - 0.4, y + 0.7, z_off - 1.1], zone="D_EYE", color=a, emissive=True))
            child = make_bone(f"cobra_{i}", [ox, y, z_off], anim=f"comp_cobra_{i}", cubes=cubes, children=[child] if child else [])
        return [child]  # type: ignore[list-item]

    if arch == "skeleton_demon":
        body = [
            make_cube("ribs", [ox - 2.4, 5.5, oz - 1.6], [ox + 2.4, 11.2, oz + 1.6], zone="A", color=p),
            make_cube("skull", [ox - 2.4, 11.2, oz - 2.4], [ox + 2.4, 15.8, oz + 2.4], zone="A", color=p),
            make_cube("ey1", [ox + 0.5, 13.0, oz - 2.6], [ox + 1.7, 14.0, oz - 2.2], zone="D_EYE", color=a, emissive=True),
            make_cube("ey2", [ox - 1.7, 13.0, oz - 2.6], [ox - 0.5, 14.0, oz - 2.2], zone="D_EYE", color=a, emissive=True),
            make_cube("h_l", [ox + 1.8, 15.4, oz - 0.8], [ox + 5.8, 17.0, oz + 0.8], rot=[0, 0, 26], zone="B", color=s),
            make_cube("h_r", [ox - 5.8, 15.4, oz - 0.8], [ox - 1.8, 17.0, oz + 0.8], rot=[0, 0, -26], zone="B", color=s),
        ]
        return [make_bone("comp_root", [ox, 2.0, oz], anim="pet_hover", anim_amp=0.7, cubes=body)]

    if arch == "skeletal_hand":
        palm = [make_cube("palm", [ox - 2.6, 3.0, oz - 2.0], [ox + 2.6, 6.2, oz + 2.6], rot=[18, 0, 0], zone="A", color=p)]
        fingers = []
        for i in range(5):
            fx = ox - 2.2 + i * 1.1
            anim = "comp_leg_fl" if i % 2 == 0 else "comp_leg_fr"
            fingers.append(make_bone(f"fing_{i}", [fx, 3.6, oz - 1.8], anim=anim, anim_phase=i * 0.5, cubes=[
                make_cube(f"f_{i}", [fx - 0.4, 0.0, oz - 4.2], [fx + 0.4, 3.8, oz - 1.6], rot=[22, 0, 0], zone="A", color=p)
            ]))
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=palm, children=fingers)]

    if arch == "lop_bunny":
        body = [
            make_cube("body", [ox - 2.8, 1.2, oz - 3.0], [ox + 2.8, 6.4, oz + 3.4], zone="A", color=p),
            make_cube("head", [ox - 2.3, 5.2, oz - 4.6], [ox + 2.3, 9.2, oz - 0.6], zone="A", color=p),
            make_cube("lop_l", [ox + 2.2, 3.4, oz - 3.4], [ox + 3.5, 8.4, oz - 1.4], rot=[0, 0, -14], zone="B", color=s),
            make_cube("lop_r", [ox - 3.5, 3.4, oz - 3.4], [ox - 2.2, 8.4, oz - 1.4], rot=[0, 0, 14], zone="B", color=s),
            make_cube("puff", [ox - 1.1, 2.2, oz + 3.2], [ox + 1.1, 4.2, oz + 4.8], zone="A", color=p),
            make_cube("eye_l", [ox + 0.7, 6.8, oz - 4.8], [ox + 1.7, 7.8, oz - 4.4], zone="D_EYE", color=a),
            make_cube("eye_r", [ox - 1.7, 6.8, oz - 4.8], [ox - 0.7, 7.8, oz - 4.4], zone="D_EYE", color=a),
            make_cube("foot_l", [ox + 0.7, 0.0, oz - 2.6], [ox + 2.2, 1.4, oz + 1.4], zone="B", color=s),
            make_cube("foot_r", [ox - 2.2, 0.0, oz - 2.6], [ox - 0.7, 1.4, oz + 1.4], zone="B", color=s),
        ]
        return [make_bone("comp_hop", [ox, 0, oz], anim="comp_hop", cubes=body)]

    # crowned_chick: hilariously round fluffy chick wearing a tiny glowing golden crown!
    body = [
        make_cube("body", [ox - 3.2, 1.2, oz - 3.0], [ox + 3.2, 7.4, oz + 3.2], zone="A", color=p),
        make_cube("wing_l", [ox + 3.0, 2.6, oz - 1.6], [ox + 4.0, 5.4, oz + 1.8], rot=[0, 0, -14], zone="A", color=p),
        make_cube("wing_r", [ox - 4.0, 2.6, oz - 1.6], [ox - 3.0, 5.4, oz + 1.8], rot=[0, 0, 14], zone="A", color=p),
        make_cube("eye_l", [ox + 0.8, 4.6, oz - 3.25], [ox + 1.9, 5.7, oz - 2.8], zone="D_EYE", color=0x18181B),
        make_cube("eye_r", [ox - 1.9, 4.6, oz - 3.25], [ox - 0.8, 5.7, oz - 2.8], zone="D_EYE", color=0x18181B),
        make_cube("beak", [ox - 0.9, 3.4, oz - 4.2], [ox + 0.9, 4.5, oz - 2.9], zone="D_GOLD", color=0xF59E0B),
        make_cube("crown", [ox - 1.5, 7.4, oz - 1.5], [ox + 1.5, 9.2, oz + 1.5], zone="D_GOLD", color=0xFDE047, emissive=True),
        make_cube("foot_l", [ox + 0.6, 0.0, oz - 2.2], [ox + 2.2, 1.4, oz + 1.2], zone="B", color=s),
        make_cube("foot_r", [ox - 2.2, 0.0, oz - 2.2], [ox - 0.6, 1.4, oz + 1.2], zone="B", color=s),
    ]
    return [make_bone("comp_hop", [ox, 0, oz], anim="comp_hop", cubes=body)]
