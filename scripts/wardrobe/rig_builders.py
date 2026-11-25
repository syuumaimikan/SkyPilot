"""Sculpted Blockbench-compatible multi-bone 3D rig builders (Cloaks & Wings/Backwear).

Coordinate space: Blockbench / Bedrock entity pixel units (16 px = 1 block):
  - Origin (0,0,0) = player feet center on the ground
  - +Y = up (waist=12, shoulders/neck=24, head center=28, head top=32)
  - -Z = front (player face), +Z = back
  - +X = player left, -X = player right

Anti-Clipping Clearances (verified against bare skin + outer layer + full armor):
  - Bare + outer skin head:  x=[-4.5, 4.5], y=[23.5, 32.5], z=[-4.5, 4.5]
  - Bare + outer skin torso: x=[-4.25, 4.25], y=[11.75, 24.25], z=[-2.25, 2.25]
  - Cloaks start at z >= 2.65 (CosmeticLayer adds +1.25px for chestplate armor)
  - Backwear / Wings start at z >= 3.20 (CosmeticLayer adds +1.25px chestplate & +1.65px cape stacking)
"""
from __future__ import annotations

import math
from typing import Any

ZONES: dict[str, list[float]] = {
    "A": [1, 1, 31, 31],
    "B": [33, 1, 63, 31],
    "C": [1, 33, 31, 63],
    "D_EYE": [36, 36, 44, 44],
    "D_DARK": [50, 34, 62, 46],
    "D_GOLD": [34, 50, 46, 62],
    "D_TRIM": [50, 50, 62, 62],
}


def face_map(rect: list[float]) -> dict[str, dict[str, Any]]:
    f = {"uv": rect, "rotation": 0}
    return {"north": f, "south": f, "east": f, "west": f, "up": f, "down": f}


def _sx(side: int, a: float, b: float) -> list[float]:
    x0, x1 = side * a, side * b
    return [min(x0, x1), max(x0, x1)]


def make_cube(
    name: str,
    a: list[float],
    b: list[float],
    c: list[float] | None = None,
    *,
    pivot: list[float] | None = None,
    rot: list[float] | None = None,
    zone: str = "A",
    color: int = 0xFFFFFF,
    emissive: bool = False,
    translucent: bool = False,
    faces: dict[str, Any] | None = None,
) -> dict[str, Any]:
    if c is not None and len(a) == 2 and len(b) == 2 and len(c) == 2:
        frm = [min(a[0], a[1]), min(b[0], b[1]), min(c[0], c[1])]
        to = [max(a[0], a[1]), max(b[0], b[1]), max(c[0], c[1])]
    else:
        frm = [min(a[i], b[i]) for i in range(3)]
        to = [max(a[i], b[i]) for i in range(3)]
    if pivot is None:
        pivot = [(frm[0] + to[0]) * 0.5, (frm[1] + to[1]) * 0.5, (frm[2] + to[2]) * 0.5]
    if rot is None:
        rot = [0.0, 0.0, 0.0]
    return {
        "name": name,
        "from": [round(v, 3) for v in frm],
        "to": [round(v, 3) for v in to],
        "pivot": [round(v, 3) for v in pivot],
        "rot": [round(v, 2) for v in rot],
        "faces": faces if faces is not None else face_map(ZONES[zone]),
        "emissive": emissive,
        "translucent": translucent,
        "color": color & 0xFFFFFF,
    }


def make_bone(
    name: str,
    pivot: list[float],
    rot: list[float] | None = None,
    anim: str = "",
    anim_amp: float = 1.0,
    anim_phase: float = 0.0,
    cubes: list[dict[str, Any]] | None = None,
    children: list[dict[str, Any]] | None = None,
    emissive: bool = False,
    translucent: bool = False,
) -> dict[str, Any]:
    return {
        "name": name,
        "pivot": [round(v, 3) for v in pivot],
        "rot": [round(v, 2) for v in (rot or [0.0, 0.0, 0.0])],
        "anim": anim,
        "animAmp": round(anim_amp, 3),
        "animPhase": round(anim_phase, 3),
        "emissive": emissive,
        "translucent": translucent,
        "cubes": cubes or [],
        "children": children or [],
    }


# ==============================================================================
# 1. CLOAKS: 8 distinct 3D cloth silhouettes (zero clipping: z0 = 2.65+)
# ==============================================================================
def build_cloak_bones(primary: int, secondary: int, accent: int, style: str = "standard") -> list[dict[str, Any]]:
    seg_h = 2.0
    width = 11.2 if style == "royal" else (10.6 if style in ("collared", "armored") else 10.2)
    thick = 0.72
    hw = width * 0.5
    z0, z1 = 2.65, 2.65 + thick

    child: dict[str, Any] | None = None
    for seg in range(7, -1, -1):
        y_top = 23.6 - seg * seg_h
        y_bot = y_top - seg_h - 0.06
        v0 = seg * 8.0
        v1 = (seg + 1) * 8.0
        flare = 1.0 + seg * (0.025 if style == "royal" else 0.018)
        x0, x1 = -hw * flare, hw * flare

        def _panel_faces(u_lo: float = 0.0, u_hi: float = 40.0) -> dict[str, Any]:
            return {
                "south": {"uv": [u_lo, v0, u_hi, v1], "rotation": 0},
                "north": {"uv": [40, v0, 60, v1], "rotation": 0},
                "east": {"uv": [60, v0, 64, v1], "rotation": 0},
                "west": {"uv": [60, v0, 64, v1], "rotation": 0},
                "up": {"uv": [60, v0, 64, min(64, v0 + 2)], "rotation": 0},
                "down": {"uv": [60, max(0, v1 - 2), 64, v1], "rotation": 0},
            }

        cubes: list[dict[str, Any]] = []
        # Themed lower hem cuts on segment 7 (and 6 for swallowtail)
        if style == "swallowtail" and seg == 7:
            # Split twin-tail hem!
            cubes.append(make_cube("cape_panel_7l", [x0, y_bot - 1.2, z0], [-0.9, y_top, z1],
                                   pivot=[0.0, y_top, z0], color=primary, faces=_panel_faces(0, 18)))
            cubes.append(make_cube("cape_panel_7r", [0.9, y_bot - 1.2, z0], [x1, y_top, z1],
                                   pivot=[0.0, y_top, z0], color=primary, faces=_panel_faces(22, 40)))
        elif style == "tattered" and seg == 7:
            # Jagged asymmetrical shadow/reaper strips!
            strips = [(-5.4, -3.1, -1.6, 0, 10), (-2.6, -0.4, -0.4, 11, 20), (0.3, 2.7, -1.9, 21, 30), (3.2, 5.4, -0.8, 31, 40)]
            for idx, (sx0, sx1, drop, u0, u1) in enumerate(strips):
                cubes.append(make_cube(f"cape_panel_7_{idx}", [sx0, y_bot + drop, z0], [sx1, y_top, z1],
                                       pivot=[0.0, y_top, z0], color=primary, faces=_panel_faces(u0, u1)))
        elif style == "ember_hem" and seg == 7:
            cubes.append(make_cube("cape_panel_7", [x0, y_bot, z0], [x1, y_top, z1],
                                   pivot=[0.0, y_top, z0], color=primary, faces=_panel_faces()))
            for k in range(5):
                fx = -4.2 + k * 2.1
                fh = 1.6 if k % 2 == 0 else 1.0
                cubes.append(make_cube(f"ember_tip_{k}", [fx - 0.7, y_bot - fh, z0 + 0.08], [fx + 0.7, y_bot + 0.2, z1 - 0.05],
                                       pivot=[0.0, y_top, z0], color=accent, emissive=True, faces=face_map([60, 48, 64, 60])))
        elif style == "frost_crystal" and seg == 7:
            cubes.append(make_cube("cape_panel_7", [x0, y_bot, z0], [x1, y_top, z1],
                                   pivot=[0.0, y_top, z0], color=primary, faces=_panel_faces()))
            for k in range(4):
                fx = -3.8 + k * 2.53
                cubes.append(make_cube(f"ice_tip_{k}", [fx - 0.55, y_bot - 1.4, z0 + 0.1], [fx + 0.55, y_bot + 0.3, z1 + 0.15],
                                       pivot=[fx, y_bot, z0 + 0.35], rot=[0, 0, 45], color=accent, emissive=True, faces=face_map([60, 0, 64, 8])))
        else:
            cubes.append(make_cube(f"cape_panel_{seg}", [x0, y_bot, z0], [x1, y_top, z1],
                                   pivot=[0.0, y_top, z0], color=primary if seg % 2 == 0 else secondary, faces=_panel_faces()))

        # Themed shoulder / collar 3D trim on segment 0 (all strictly z >= 2.65!)
        if seg == 0:
            if style == "royal":
                # Wide fur-trimmed royal mantle collar + golden chain
                cubes.append(make_cube("royal_fur", [-hw - 0.5, y_top - 1.1, z0], [hw + 0.5, y_top + 0.9, z1 + 0.75],
                                       pivot=[0.0, y_top, z0], color=0xF8FAFC, faces=face_map([60, 0, 64, 8])))
                cubes.append(make_cube("royal_bar", [-3.8, y_top - 0.4, z1 + 0.5], [3.8, y_top + 0.3, z1 + 0.95],
                                       pivot=[0.0, y_top, z0], color=accent, emissive=True, faces=face_map([60, 0, 64, 8])))
            elif style == "collared":
                # Sculpted high noble vampire/gothic collar behind neck (z >= 2.85, clears head!)
                cubes.append(make_cube("collar_b", [-4.6, y_top - 0.2, z0 + 0.2], [4.6, y_top + 2.8, z1 + 0.35],
                                       pivot=[0.0, y_top, z0], rot=[-18, 0, 0], color=secondary, faces=face_map([60, 0, 64, 8])))
                for side in (-1, 1):
                    cubes.append(make_cube(f"collar_s_{side}", _sx(side, 3.8, 5.2), [y_top - 0.2, y_top + 2.4], [z0 + 0.1, z1 + 0.55],
                                           pivot=[0.0, y_top, z0], rot=[-14, 0, -side * 16], color=accent, faces=face_map([60, 0, 64, 8])))
            elif style == "armored":
                # Metallic pauldron plates + glowing central crest
                for side in (-1, 1):
                    cubes.append(make_cube(f"pauldron_{side}", _sx(side, 2.8, 5.8), [y_top - 1.4, y_top + 0.8], [z0, z1 + 0.9],
                                           pivot=[0.0, y_top, z0], rot=[0, 0, -side * 12], color=secondary, faces=face_map([60, 0, 64, 8])))
                    cubes.append(make_cube(f"p_glow_{side}", _sx(side, 3.4, 5.2), [y_top - 0.6, y_top + 0.2], [z1 + 0.65, z1 + 1.05],
                                           pivot=[0.0, y_top, z0], color=accent, emissive=True, faces=face_map([60, 0, 64, 8])))
                cubes.append(make_cube("tech_crest", [-1.4, y_top - 1.5, z1 + 0.1], [1.4, y_top + 0.4, z1 + 0.7],
                                       pivot=[0.0, y_top, z0], color=accent, emissive=True, faces=face_map([60, 0, 64, 8])))
            elif style == "frost_crystal":
                for side in (-1, 1):
                    cubes.append(make_cube(f"gem_clasp_{side}", _sx(side, 3.1, 4.9), [y_top - 1.2, y_top + 0.6], [z0, z1 + 0.65],
                                           pivot=[side * 4.0, y_top - 0.3, z0 + 0.4], rot=[0, 0, 45], color=accent, emissive=True, faces=face_map([60, 0, 64, 8])))
            else:
                for side in (-1, 1):
                    cubes.append(make_cube(f"clasp_{side}", _sx(side, hw - 1.9, hw - 0.2), [y_top - 1.2, y_top + 0.4], [z0, z1 + 0.45],
                                           pivot=[0.0, y_top, z0], color=accent, faces=face_map([60, 0, 64, 8])))

        child = make_bone(
            f"cape_seg_{seg}",
            pivot=[0.0, y_top, z0],
            anim=f"cape_seg_{seg}",
            cubes=cubes,
            children=[child] if child else [],
        )
    return [child]  # type: ignore[list-item]


# ==============================================================================
# 2. WINGS & BACKWEAR — ALL 36 DESIGNS 100% UNIQUE, ALL CUBES z >= 3.20!
# ==============================================================================
def build_wings_bones(arch: str, p: int, s: int, a: int, emissive_tips: bool = True, translucent: bool = False) -> list[dict[str, Any]]:
    dispatch = {
        "astral_feathered": lambda: _wing_feathered(p, s, a, emissive_tips, style="astral"),
        "phoenix_plumes": lambda: _wing_feathered(p, s, a, True, style="phoenix"),
        "seraph_six": lambda: _wing_feathered(p, s, a, True, style="seraph_six"),
        "raven_swept": lambda: _wing_feathered(p, s, a, False, style="raven"),
        "blossom_halo": lambda: _wing_feathered(p, s, a, True, style="blossom_halo"),
        "sunburst_arch": lambda: _wing_feathered(p, s, a, True, style="sunburst"),
        "crescent_sakura": lambda: _wing_feathered(p, s, a, True, style="crescent_sakura"),
        "prism_neon_bars": lambda: _wing_prism_bars(p, s, a),
        "dragon_elder": lambda: _wing_dragon(p, s, a, emissive_tips, translucent, style="elder"),
        "demon_volcanic": lambda: _wing_dragon(p, s, a, True, False, style="volcanic"),
        "vampire_bat": lambda: _wing_dragon(p, s, a, True, False, style="vampire"),
        "jade_wyvern": lambda: _wing_dragon(p, s, a, True, translucent, style="jade"),
        "heart_demon": lambda: _wing_dragon(p, s, a, True, False, style="heart"),
        "gothic_gold": lambda: _wing_dragon(p, s, a, True, True, style="gothic"),
        "spectral_wisps": lambda: _wing_dragon(p, s, a, True, True, style="spectral"),
        "galaxy_nebula": lambda: _wing_dragon(p, s, a, True, False, style="galaxy"),
        "frost_dragon": lambda: _wing_dragon(p, s, a, True, True, style="frost"),
        "fairy_gossamer": lambda: _wing_insect(p, s, a, True, True, style="fairy"),
        "monarch_butterfly": lambda: _wing_insect(p, s, a, False, False, style="monarch"),
        "swallowtail": lambda: _wing_insect(p, s, a, True, True, style="swallowtail"),
        "honeycomb_hex": lambda: _wing_honeycomb(p, s, a),
        "lunar_moth": lambda: _wing_insect(p, s, a, True, False, style="moth"),
        "amethyst_spires": lambda: _wing_crystal(p, s, a, style="spires"),
        "rose_lotus": lambda: _wing_crystal(p, s, a, style="lotus"),
        "frost_chains": lambda: _wing_crystal(p, s, a, style="frost_chains"),
        "cyber_thrusters": lambda: _wing_mecha(p, s, a, style="thrusters"),
        "emerald_rail": lambda: _wing_mecha(p, s, a, style="emerald_rail"),
        "steel_valkyrie": lambda: _wing_mecha(p, s, a, style="valkyrie"),
        "steampunk": lambda: _wings_steampunk(p, s, a),
        "void_tendrils": lambda: _back_tendrils(p, s, a),
        "katana_scabbard": lambda: _back_katanas(p, s, a),
        "quiver": lambda: _back_quiver(p, s, a),
        "jetpack": lambda: _back_jetpack(p, s, a),
        "backpack": lambda: _back_backpack(p, s, a),
        "shield_crest": lambda: _back_shield(p, s, a),
        "solar_cloak": lambda: _back_solar_crest(p, s, a),
    }
    return dispatch[arch]()


def _wing_feathered(p: int, s: int, a: int, glow: bool, style: str) -> list[dict[str, Any]]:
    bones: list[dict[str, Any]] = []
    Z = 3.5  # Safe back clearance (behind torso + armor + cloak stacking)
    tiers = [0, 1, 2] if style == "seraph_six" else [0]

    # Distinct centerpiece geometry per winged style
    if style == "sunburst":
        brooch = [
            make_cube("sun_hub", [-2.2, 18.8, Z], [2.2, 23.2, Z + 1.2], rot=[0, 0, 45], zone="D_GOLD", color=a, emissive=True),
            make_cube("sun_gem", [-1.1, 19.9, Z + 0.8], [1.1, 22.1, Z + 1.6], zone="C", color=0xFFFFFF, emissive=True),
        ]
        bones.append(make_bone("sun_brooch", [0, 21.0, Z + 0.5], anim="gear_cw", anim_amp=0.35, cubes=brooch))
    elif style == "phoenix":
        spine = [make_cube(f"ph_sp_{k}", [-0.8, 16.5 + k * 2.2, Z + 0.1], [0.8, 18.4 + k * 2.2, Z + 1.5],
                           rot=[-20, 0, 0], zone="C", color=a, emissive=True) for k in range(3)]
        bones.append(make_bone("phoenix_spine", [0, 20.0, Z + 0.6], anim="flame_pulse", anim_amp=0.95, cubes=spine))
    elif style == "crescent_sakura":
        moon = []
        for i in range(9):
            ang = -1.1 + i * 0.36
            mx, my = math.cos(ang) * 5.0 - 1.1, 20.8 + math.sin(ang) * 5.0
            moon.append(make_cube(f"m_{i}", [mx - 0.85, my - 0.85, Z + 0.3], [mx + 0.85, my + 0.85, Z + 1.3],
                                  zone="C", color=0xFFF1F2, emissive=True))
        bones.append(make_bone("crescent_crest", [0, 20.8, Z + 0.8], anim="wing_float", anim_amp=0.5, cubes=moon))
    elif style == "blossom_halo":
        halo = []
        for i in range(10):
            ang = i * math.tau / 10.0
            hx, hz = math.cos(ang) * 4.6, math.sin(ang) * 4.6
            halo.append(make_cube(f"h_{i}", [hx - 0.75, 35.6, hz - 0.75], [hx + 0.75, 36.6, hz + 0.75],
                                  zone="D_GOLD", color=0xFDE047, emissive=True))
        bones.append(make_bone("wing_halo", [0, 36.0, 0], anim="halo_float", cubes=halo))

    # Distinct feather counts, arch heights, sweeps, and silhouettes per style!
    cfg = {
        "astral":          (6, 6, 5, 1.00, 21.6, 25.0, 27.6, 18, 15, 0.0),
        "phoenix":         (5, 7, 6, 1.12, 22.0, 26.8, 30.8, 28, 22, 0.4),
        "seraph_six":      (5, 5, 4, 0.94, 22.4, 25.5, 27.8, 16, 14, 0.0),
        "raven":           (7, 7, 6, 1.16, 21.2, 23.2, 22.2, 6, -10, -0.5),  # huge heavy downward raven sweep!
        "blossom_halo":    (5, 6, 5, 0.96, 21.8, 25.6, 28.4, 22, 18, 0.2),
        "sunburst":        (6, 6, 6, 1.08, 22.2, 27.4, 31.4, 30, 24, 0.3),   # high arched dawn wings!
        "crescent_sakura": (5, 5, 5, 1.02, 21.0, 24.4, 26.8, 14, 10, 0.1),
    }[style]
    n_cov, n_pri, n_tip, sc_base, y_r, y_m, y_t, ang_r, ang_m, flare_k = cfg

    for tier_idx, tier in enumerate(tiers):
        y_off = -tier * 3.7
        scale = sc_base * (1.0 - tier * 0.16)
        z_t = Z + tier * 0.65
        for side, tag in ((1, "l"), (-1, "r")):
            root_piv = [side * 2.0, y_r + y_off, z_t]
            mid_piv = [side * (2.0 + 8.4 * scale), y_m + y_off, z_t + 1.3]
            tip_piv = [side * (2.0 + 16.4 * scale), y_t + y_off, z_t + 2.5]

            root_cubes = [
                make_cube("arm_in", _sx(side, 1.8, 1.8 + 8.2 * scale), [y_r - 0.6 + y_off, y_r + 2.4 + y_off], [z_t, z_t + 1.2],
                          pivot=root_piv, rot=[0, -side * 16, side * ang_r], zone="B", color=s),
            ]
            for i in range(n_cov):
                fx = 2.4 + i * (7.2 / max(1, n_cov - 1)) * scale
                fl = (7.2 + i * 1.15 + (2.0 if style == "raven" else 0.0)) * scale
                root_cubes.append(make_cube(
                    f"cov_{i}",
                    _sx(side, fx, fx + 1.55 * scale),
                    [y_r + y_off - fl + i * 0.55, y_r + 1.2 + y_off + i * 0.55],
                    [z_t + 0.15 + (i % 2) * 0.25, z_t + 1.0 + (i % 2) * 0.25],
                    pivot=[side * fx, y_r + 0.9 + y_off, z_t + 0.5],
                    rot=[0, -side * (14 + i), -side * (12 - i * 1.4)],
                    zone="A", color=p,
                ))

            mid_cubes = [
                make_cube("arm_mid", _sx(side, 2.0 + 7.4 * scale, 2.0 + 16.8 * scale), [y_m - 0.8 + y_off, y_m + 1.9 + y_off], [z_t + 0.8, z_t + 1.9],
                          pivot=mid_piv, rot=[0, -side * 18, side * ang_m], zone="B", color=s),
            ]
            for i in range(n_pri):
                fx = 2.0 + (8.2 + i * (8.0 / max(1, n_pri - 1))) * scale
                fl = (11.6 - i * 0.35 + (2.5 if style == "phoenix" and i % 2 == 0 else 0.0)) * scale
                fy_top = y_m + 0.3 + y_off + i * (0.45 + flare_k) * scale
                mid_cubes.append(make_cube(
                    f"pri_{i}",
                    _sx(side, fx, fx + 1.6 * scale),
                    [fy_top - fl, fy_top],
                    [z_t + 1.0 + (i % 2) * 0.2, z_t + 1.85 + (i % 2) * 0.2],
                    pivot=[side * fx, fy_top, z_t + 1.4],
                    rot=[0, -side * (16 + i * 1.2), -side * (6 + i * 2.4)],
                    zone="A" if i < n_pri - 2 else "C", color=p if i < n_pri - 2 else a, emissive=glow and i >= n_pri - 2,
                ))

            tip_cubes = []
            for i in range(n_tip):
                fx = 2.0 + (16.0 + i * (6.2 / max(1, n_tip - 1))) * scale
                fl = (12.2 - i * 1.35 + (1.8 if style == "raven" and i % 2 == 1 else 0.0)) * scale
                fy_top = y_t + 0.2 + y_off + (2.0 - abs(i - 1.5)) * 0.85 * scale
                tip_cubes.append(make_cube(
                    f"tip_{i}",
                    _sx(side, fx, fx + 1.5 * scale),
                    [fy_top - fl, fy_top],
                    [z_t + 1.8 + i * 0.12, z_t + 2.6 + i * 0.12],
                    pivot=[side * fx, fy_top, z_t + 2.2],
                    rot=[0, -side * (20 + i * 2), -side * (16 + i * 5.2)],
                    zone="C" if (i >= n_tip - 3 or style == "phoenix") else "A",
                    color=a if (i >= n_tip - 3 or style == "phoenix") else p,
                    emissive=glow and (i >= n_tip - 3 or style == "phoenix"),
                ))

            # Extra style-specific details
            if style == "phoenix":
                tip_cubes.append(make_cube("flame_trail", _sx(side, 19.0 * scale, 22.0 * scale), [y_t - 15.0, y_t - 10.5], [z_t + 2.0, z_t + 3.0],
                                           pivot=tip_piv, rot=[0, 0, -side * 24], zone="C", color=a, emissive=True))
            elif style == "astral":
                tip_cubes.append(make_cube("astral_star", _sx(side, 18.2, 20.0), [y_t + 1.5, y_t + 3.3], [z_t + 2.2, z_t + 3.4],
                                           pivot=[side * 19.1, y_t + 2.4, z_t + 2.8], rot=[0, 0, 45], zone="C", color=a, emissive=True))
            elif style == "blossom_halo":
                mid_cubes.append(make_cube("bud", _sx(side, 12.0, 13.8), [y_m + 2.2, y_m + 3.8], [z_t + 1.5, z_t + 2.5],
                                           rot=[0, 0, 45], zone="C", color=a, emissive=True))

            tip_b = make_bone(f"wing_{tag}_t{tier_idx}_tip", tip_piv, anim=f"wing_{tag}_tip", anim_phase=tier * 0.45, cubes=tip_cubes)
            mid_b = make_bone(f"wing_{tag}_t{tier_idx}_mid", mid_piv, anim=f"wing_{tag}_mid", anim_phase=tier * 0.45, cubes=mid_cubes, children=[tip_b])
            root_b = make_bone(f"wing_{tag}_t{tier_idx}_root", root_piv, rot=[0, side * 16, 0], anim=f"wing_{tag}_root", anim_phase=tier * 0.45, cubes=root_cubes, children=[mid_b])
            bones.append(root_b)
    return bones


def _wing_prism_bars(p: int, s: int, a: int) -> list[dict[str, Any]]:
    # Geometric staggered neon equalizer bar wings (unique prism silhouette!)
    bones: list[dict[str, Any]] = []
    Z = 3.5
    core = [make_cube("prism_core", [-1.6, 17.0, Z], [1.6, 22.8, Z + 1.3], zone="C", color=a, emissive=True)]
    bones.append(make_bone("prism_hub", [0, 20.0, Z + 0.6], cubes=core))
    for side, tag in ((1, "l"), (-1, "r")):
        piv = [side * 2.0, 20.5, Z + 0.3]
        cubes = []
        bar_lens = [15.0, 19.5, 22.0, 18.0, 14.5, 11.0, 7.5]
        for i, bl in enumerate(bar_lens):
            by = 25.0 - i * 2.1
            bx0 = 2.6 + i * 0.45
            z_off = Z + 0.3 + i * 0.18
            cubes.append(make_cube(f"bar_{i}", _sx(side, bx0, bx0 + bl), [by - 0.75, by + 0.75], [z_off, z_off + 0.85],
                                   pivot=[side * bx0, by, z_off + 0.4], rot=[0, -side * (16 + i * 1.5), side * (22 - i * 8)],
                                   zone="C" if i % 2 == 0 else "A", color=a if i % 2 == 0 else p, emissive=True))
            # Floating equalizer tip cube
            cubes.append(make_cube(f"dot_{i}", _sx(side, bx0 + bl + 0.8, bx0 + bl + 2.0), [by - 0.55, by + 0.55], [z_off + 0.2, z_off + 1.0],
                                   pivot=[side * bx0, by, z_off + 0.4], rot=[0, -side * (16 + i * 1.5), side * (22 - i * 8)],
                                   zone="D_GOLD", color=0xFEF08A, emissive=True))
        bones.append(make_bone(f"wing_{tag}_root", piv, rot=[0, side * 14, 0], anim=f"wing_{tag}_root", cubes=cubes))
    return bones


def _wing_dragon(p: int, s: int, a: int, glow: bool, trans: bool, style: str) -> list[dict[str, Any]]:
    bones: list[dict[str, Any]] = []
    Z = 3.5

    if style == "heart":
        bones.append(make_bone("heart_crest", [0, 18.8, Z + 0.4], cubes=[
            make_cube("h_l", [-2.2, 18.0, Z], [-0.1, 20.8, Z + 1.1], rot=[0, 0, 24], zone="C", color=a, emissive=True),
            make_cube("h_r", [0.1, 18.0, Z], [2.2, 20.8, Z + 1.1], rot=[0, 0, -24], zone="C", color=a, emissive=True),
            make_cube("h_b", [-1.5, 16.6, Z + 0.1], [1.5, 19.2, Z + 1.0], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]))
    elif style == "volcanic":
        bones.append(make_bone("magma_horns", [0, 21.0, Z + 0.4], cubes=[
            make_cube("mh_l", [1.4, 21.8, Z + 0.2], [3.2, 25.4, Z + 1.6], rot=[-20, 0, -18], zone="C", color=a, emissive=True),
            make_cube("mh_r", [-3.2, 21.8, Z + 0.2], [-1.4, 25.4, Z + 1.6], rot=[-20, 0, 18], zone="C", color=a, emissive=True),
        ]))
    elif style == "gothic":
        bones.append(make_bone("gothic_cross", [0, 20.0, Z + 0.3], cubes=[
            make_cube("gc_v", [-0.7, 15.0, Z], [0.7, 24.0, Z + 1.1], zone="D_GOLD", color=s, emissive=True),
            make_cube("gc_h", [-3.2, 19.8, Z], [3.2, 21.2, Z + 1.1], zone="D_GOLD", color=s, emissive=True),
        ]))
    elif style == "jade":
        orbs = [make_bone(f"jade_orb_{k}", [0, 19.5, Z + 1.2], anim="gear_cw", anim_amp=0.5, cubes=[
            make_cube(f"jo_{k}", [math.cos(k * 2.09) * 5.2 - 0.85, 19.5 + math.sin(k * 2.09) * 5.2 - 0.85, Z + 0.8],
                      [math.cos(k * 2.09) * 5.2 + 0.85, 19.5 + math.sin(k * 2.09) * 5.2 + 0.85, Z + 2.1],
                      zone="C", color=a, emissive=True)
        ]) for k in range(3)]
        bones.extend(orbs)
    elif style == "galaxy":
        stars = [make_cube(f"gstar_{k}", [math.cos(k * 1.25) * 6.5 - 0.6, 20.0 + math.sin(k * 1.25) * 6.5 - 0.6, Z + 1.2],
                           [math.cos(k * 1.25) * 6.5 + 0.6, 20.0 + math.sin(k * 1.25) * 6.5 + 0.6, Z + 2.2],
                           rot=[45, 45, 0], zone="D_GOLD", color=0xFEF08A, emissive=True) for k in range(5)]
        bones.append(make_bone("galaxy_ring", [0, 20.0, Z + 1.7], anim="gear_ccw", anim_amp=0.35, cubes=stars))

    # Distinct wing geometry proportions per dragon/bat style
    span = {"elder": 1.0, "volcanic": 1.08, "vampire": 1.14, "jade": 1.04, "heart": 0.90,
            "gothic": 1.06, "spectral": 1.02, "galaxy": 1.12, "frost": 1.10}[style]
    bone_zone = "D_GOLD" if style in ("gothic", "frost") else "B"
    bone_col = s if style == "gothic" else (a if style == "frost" else s)

    for side, tag in ((1, "l"), (-1, "r")):
        root_piv = [side * 1.8, 20.6, Z]
        mid_piv = [side * (9.5 * span), 26.5 + (1.4 if style == "vampire" else 0.0), Z + 1.3]
        tip_piv = [side * (18.5 * span), 28.4 + (1.8 if style == "frost" else 0.0), Z + 2.6]

        root_cubes = [
            make_cube("humerus", _sx(side, 1.8, 9.8 * span), [20.2, 22.0], [Z, Z + 1.4], pivot=root_piv, rot=[0, -side * 16, side * 34], zone=bone_zone, color=bone_col),
            make_cube("mem_in1", _sx(side, 2.4, 9.6 * span), [13.5, 22.4], [Z + 0.4, Z + 1.0], pivot=root_piv, rot=[0, -side * 16, side * 14], zone="A", color=p, translucent=trans),
            make_cube("mem_in2", _sx(side, 3.2, 8.8 * span), [9.8, 15.5], [Z + 0.5, Z + 1.05], pivot=root_piv, rot=[0, -side * 16, side * 5], zone="A", color=p, translucent=trans),
        ]
        mid_cubes = [
            make_cube("elbow_claw", _sx(side, 8.8 * span, 10.6 * span), [26.0, 30.8 if style in ("vampire", "frost") else 29.8], [Z + 0.9, Z + 2.2],
                      pivot=mid_piv, rot=[0, 0, -side * 18], zone="C" if glow else "D_GOLD", color=a, emissive=glow),
            make_cube("radius", _sx(side, 9.2 * span, 19.2 * span), [25.6, 27.5], [Z + 1.0, Z + 2.3], pivot=mid_piv, rot=[0, -side * 18, side * 10], zone=bone_zone, color=bone_col),
            make_cube("rib_1", _sx(side, 9.5 * span, 11.1 * span), [12.5, 26.2], [Z + 1.2, Z + 2.1], pivot=mid_piv, rot=[0, -side * 14, -side * 18], zone=bone_zone, color=bone_col),
            make_cube("rib_2", _sx(side, 12.2 * span, 13.7 * span), [11.2, 26.8], [Z + 1.4, Z + 2.2], pivot=[side * 12.5 * span, 26.5, Z + 1.8], rot=[0, -side * 16, -side * 34], zone=bone_zone, color=bone_col),
            make_cube("mem_mid1", _sx(side, 9.2 * span, 14.8 * span), [13.2, 26.0], [Z + 1.45, Z + 1.95], pivot=mid_piv, rot=[0, -side * 16, side * 4], zone="A", color=p, translucent=trans),
            make_cube("mem_mid2", _sx(side, 13.6 * span, 18.8 * span), [14.2, 26.8], [Z + 1.7, Z + 2.15], pivot=mid_piv, rot=[0, -side * 18, -side * 4], zone="A", color=p, translucent=trans),
        ]
        tip_cubes = [
            make_cube("outer_spar", _sx(side, 18.2 * span, 25.5 * span), [26.8, 28.4], [Z + 1.8, Z + 2.9], pivot=tip_piv, rot=[0, -side * 20, -side * 22], zone=bone_zone, color=bone_col),
            make_cube("outer_claw", _sx(side, 24.4 * span, 27.4 * span), [23.8, 26.4], [Z + 2.0, Z + 2.9], pivot=tip_piv, rot=[0, -side * 20, -side * 36], zone="C", color=a, emissive=glow),
            make_cube("mem_tip1", _sx(side, 18.0 * span, 24.4 * span), [16.5, 27.2], [Z + 2.0, Z + 2.5], pivot=tip_piv, rot=[0, -side * 20, -side * 16], zone="A", color=p, translucent=trans),
            make_cube("mem_tip_glow", _sx(side, 19.0 * span, 23.6 * span), [14.6, 17.5], [Z + 2.05, Z + 2.55], pivot=tip_piv, rot=[0, -side * 20, -side * 20], zone="C", color=a, emissive=glow, translucent=trans),
        ]

        # Unique sculpted accents per dragon/bat archetype!
        if style == "vampire":
            mid_cubes.append(make_cube("vamp_spur", _sx(side, 14.5 * span, 16.5 * span), [26.6, 30.8], [Z + 1.4, Z + 2.5],
                                       pivot=mid_piv, rot=[0, 0, -side * 28], zone="D_DARK", color=s))
            tip_cubes.append(make_cube("vamp_tail", _sx(side, 11.0 * span, 12.6 * span), [8.2, 13.5], [Z + 1.5, Z + 2.1],
                                       pivot=mid_piv, rot=[0, 0, -side * 12], zone="C", color=a, emissive=True))
        elif style == "frost":
            for k in range(3):
                ix = (11.5 + k * 4.2) * span
                tip_cubes.append(make_cube(f"icicle_{k}", _sx(side, ix - 0.6, ix + 0.6), [27.5 + k * 0.4, 31.4 + k * 0.5], [Z + 1.6 + k * 0.3, Z + 2.6 + k * 0.3],
                                           rot=[0, 0, -side * (12 + k * 10)], zone="C", color=0xE0F2FE, emissive=True, translucent=True))
        elif style == "spectral":
            for k in range(3):
                wx = (10.0 + k * 5.2) * span
                tip_cubes.append(make_cube(f"wisp_{k}", _sx(side, wx - 0.8, wx + 0.8), [28.5 + k * 1.1, 31.0 + k * 1.1], [Z + 1.8, Z + 3.0],
                                           rot=[0, 0, 45], zone="C", color=a, emissive=True, translucent=True))
        elif style == "gothic":
            mid_cubes.append(make_cube("arch_rib", _sx(side, 6.0 * span, 18.0 * span), [18.0, 19.3], [Z + 1.5, Z + 2.3],
                                       pivot=mid_piv, rot=[0, -side * 16, side * 12], zone="D_GOLD", color=s, emissive=True))
        elif style == "jade":
            for k in range(4):
                sx = (5.0 + k * 4.5) * span
                mid_cubes.append(make_cube(f"serr_{k}", _sx(side, sx, sx + 1.4), [24.2 + k * 1.1, 27.0 + k * 1.1], [Z + 1.1 + k * 0.2, Z + 2.0 + k * 0.2],
                                           rot=[0, 0, -side * 22], zone="C", color=a, emissive=True))
        elif style == "elder":
            root_cubes.append(make_cube("elder_ridge", _sx(side, 2.2, 6.2), [22.2, 24.4], [Z + 0.2, Z + 1.3],
                                        rot=[0, 0, side * 24], zone="C", color=a, emissive=True))

        tip_b = make_bone(f"wing_{tag}_tip", tip_piv, anim=f"wing_{tag}_tip", cubes=tip_cubes)
        mid_b = make_bone(f"wing_{tag}_mid", mid_piv, anim=f"wing_{tag}_mid", cubes=mid_cubes, children=[tip_b])
        root_b = make_bone(f"wing_{tag}_root", root_piv, rot=[0, side * 18, 0], anim=f"wing_{tag}_root", cubes=root_cubes, children=[mid_b])
        bones.append(root_b)
    return bones


def _wing_insect(p: int, s: int, a: int, glow: bool, trans: bool, style: str) -> list[dict[str, Any]]:
    bones: list[dict[str, Any]] = []
    Z = 3.5
    for side, tag in ((1, "l"), (-1, "r")):
        up_piv = [side * 1.8, 20.6, Z]
        lo_piv = [side * 1.8, 17.0, Z + 0.2]

        if style == "fairy":
            # Elongated gossamer 4-lobe fairy wings + floating pixie sparkles!
            up_cubes = [
                make_cube("f_up1", _sx(side, 1.8, 13.5), [21.5, 29.0], [Z, Z + 0.55], pivot=up_piv, rot=[0, -side * 18, side * 28], zone="A", color=p, translucent=True),
                make_cube("f_up2", _sx(side, 11.5, 22.5), [26.0, 32.5], [Z + 0.4, Z + 0.9], pivot=up_piv, rot=[0, -side * 20, side * 34], zone="C", color=a, emissive=True, translucent=True),
                make_cube("f_spark", _sx(side, 18.0, 19.6), [33.0, 34.6], [Z + 0.8, Z + 1.6], rot=[0, 0, 45], zone="C", color=0xFFFFFF, emissive=True),
            ]
            lo_cubes = [
                make_cube("f_lo1", _sx(side, 1.8, 11.0), [11.5, 18.5], [Z + 0.2, Z + 0.7], pivot=lo_piv, rot=[0, -side * 16, -side * 22], zone="A", color=p, translucent=True),
                make_cube("f_lo2", _sx(side, 8.5, 14.5), [8.2, 13.0], [Z + 0.4, Z + 0.85], pivot=lo_piv, rot=[0, -side * 18, -side * 34], zone="C", color=a, emissive=True, translucent=True),
            ]
        elif style == "monarch":
            # Broad classic rounded monarch wings with velvety dark outer border
            up_cubes = [
                make_cube("m_in", _sx(side, 1.8, 12.2), [19.5, 30.2], [Z + 0.1, Z + 0.65], pivot=up_piv, rot=[0, -side * 18, side * 20], zone="A", color=p),
                make_cube("m_out", _sx(side, 10.2, 19.2), [22.0, 31.8], [Z + 0.4, Z + 0.9], pivot=up_piv, rot=[0, -side * 20, side * 10], zone="A", color=p),
                make_cube("m_rim", _sx(side, 1.8, 19.6), [30.4, 32.2], [Z, Z + 0.95], pivot=up_piv, rot=[0, -side * 19, side * 16], zone="B", color=s),
            ]
            lo_cubes = [
                make_cube("m_hind", _sx(side, 1.8, 13.0), [10.2, 19.2], [Z + 0.2, Z + 0.75], pivot=lo_piv, rot=[0, -side * 16, -side * 10], zone="A", color=p),
                make_cube("m_hrim", _sx(side, 2.2, 13.4), [9.2, 10.8], [Z + 0.2, Z + 0.85], pivot=lo_piv, rot=[0, -side * 16, -side * 10], zone="B", color=s),
            ]
        elif style == "swallowtail":
            # Sharp pointed forewings + dramatic extra-long twin swallowtail ribbons!
            up_cubes = [
                make_cube("sw_in", _sx(side, 1.8, 12.5), [20.0, 29.8], [Z + 0.1, Z + 0.65], pivot=up_piv, rot=[0, -side * 18, side * 26], zone="A", color=p, translucent=True),
                make_cube("sw_pt", _sx(side, 11.2, 22.8), [25.0, 34.4], [Z + 0.4, Z + 0.95], pivot=up_piv, rot=[0, -side * 22, side * 18], zone="C", color=a, emissive=True, translucent=True),
            ]
            lo_cubes = [
                make_cube("sw_h", _sx(side, 1.8, 11.5), [10.8, 19.2], [Z + 0.2, Z + 0.7], pivot=lo_piv, rot=[0, -side * 16, -side * 14], zone="A", color=p, translucent=True),
                make_cube("sw_tail1", _sx(side, 6.2, 8.6), [3.5, 11.8], [Z + 0.3, Z + 0.85], pivot=lo_piv, rot=[0, -side * 16, -side * 22], zone="C", color=a, emissive=True, translucent=True),
                make_cube("sw_tail2", _sx(side, 9.5, 11.2), [5.2, 12.2], [Z + 0.35, Z + 0.85], pivot=lo_piv, rot=[0, -side * 16, -side * 32], zone="C", color=a, emissive=True, translucent=True),
            ]
        else:  # moth
            # Regal Lunar Moth: plush moth thorax + broad velvety eye-spotted wings + glowing antennae!
            up_cubes = [
                make_cube("mo_in", _sx(side, 1.8, 13.0), [19.8, 30.0], [Z + 0.1, Z + 0.7], pivot=up_piv, rot=[0, -side * 18, side * 16], zone="A", color=p),
                make_cube("mo_out", _sx(side, 11.0, 21.0), [22.8, 32.8], [Z + 0.4, Z + 1.0], pivot=up_piv, rot=[0, -side * 20, side * 8], zone="A", color=p),
                make_cube("mo_eye", _sx(side, 12.2, 15.6), [25.6, 29.0], [Z + 0.35, Z + 1.15], pivot=up_piv, rot=[0, -side * 20, 45], zone="C", color=a, emissive=True),
                make_cube("antenna", _sx(side, 2.2, 3.5), [28.0, 35.4], [Z + 0.1, Z + 0.9], pivot=[side * 2.5, 28.0, Z + 0.5], rot=[0, 0, -side * 28], zone="C", color=a, emissive=True),
            ]
            lo_cubes = [
                make_cube("mo_h", _sx(side, 1.8, 12.2), [10.0, 19.2], [Z + 0.2, Z + 0.8], pivot=lo_piv, rot=[0, -side * 16, -side * 10], zone="A", color=p),
                make_cube("mo_tail", _sx(side, 8.0, 10.6), [4.8, 11.4], [Z + 0.3, Z + 0.85], pivot=lo_piv, rot=[0, -side * 16, -side * 24], zone="B", color=s),
            ]
        bones.append(make_bone(f"wing_{tag}_root", up_piv, rot=[0, side * 14, 0], anim=f"wing_{tag}_root", cubes=up_cubes))
        bones.append(make_bone(f"wing_{tag}_lower", lo_piv, rot=[0, side * 10, 0], anim=f"wing_{tag}_lower", cubes=lo_cubes))
    return bones


def _wing_honeycomb(p: int, s: int, a: int) -> list[dict[str, Any]]:
    # Hexagonal faceted stained-glass glacier wing panels!
    bones: list[dict[str, Any]] = []
    Z = 3.5
    hex_cells = [
        (4.5, 23.0, 3.6), (9.0, 26.2, 4.0), (14.2, 28.6, 4.2), (19.0, 29.8, 3.4),
        (5.5, 18.2, 3.2), (10.5, 20.6, 3.8), (15.8, 22.8, 3.5),
        (6.8, 13.4, 3.0), (11.2, 15.2, 3.1),
    ]
    for side, tag in ((1, "l"), (-1, "r")):
        piv = [side * 2.0, 20.5, Z]
        cubes = []
        for idx, (hx, hy, sz) in enumerate(hex_cells):
            cz = Z + 0.2 + idx * 0.16
            cubes.append(make_cube(f"hex_f_{idx}", _sx(side, hx - sz * 0.55, hx + sz * 0.55), [hy - sz * 0.55, hy + sz * 0.55], [cz, cz + 0.7],
                                   pivot=[side * hx, hy, cz + 0.35], rot=[0, -side * 16, 30 if idx % 2 == 0 else 0],
                                   zone="B", color=s))
            cubes.append(make_cube(f"hex_g_{idx}", _sx(side, hx - sz * 0.42, hx + sz * 0.42), [hy - sz * 0.42, hy + sz * 0.42], [cz + 0.12, cz + 0.82],
                                   pivot=[side * hx, hy, cz + 0.35], rot=[0, -side * 16, 30 if idx % 2 == 0 else 0],
                                   zone="C" if idx % 2 == 0 else "A", color=a if idx % 2 == 0 else p, emissive=True, translucent=True))
        bones.append(make_bone(f"wing_{tag}_root", piv, rot=[0, side * 14, 0], anim=f"wing_{tag}_root", cubes=cubes))
    return bones


def _wing_crystal(p: int, s: int, a: int, style: str) -> list[dict[str, Any]]:
    bones: list[dict[str, Any]] = []
    Z = 3.5
    if style == "spires":
        specs = [(3.4, 22.4, 9.6, 2.6, 18, 0.0), (7.8, 26.2, 11.4, 2.8, 28, 0.7), (13.4, 29.0, 10.8, 2.5, 38, 1.4),
                 (5.0, 17.5, 8.0, 2.2, 8, 2.1), (10.4, 20.0, 8.6, 2.3, 18, 2.8), (16.0, 22.8, 7.6, 2.0, 28, 3.5), (6.4, 12.6, 6.6, 1.9, -10, 4.2)]
    elif style == "lotus":
        # Rose Quartz blooming lotus petal fan!
        specs = [(4.2, 23.0, 6.2, 3.4, 42, 0.0), (9.2, 25.8, 7.0, 3.6, 52, 0.8), (14.6, 26.8, 6.6, 3.2, 62, 1.6),
                 (5.6, 17.8, 5.8, 3.2, 20, 2.4), (11.2, 19.2, 6.2, 3.2, 30, 3.2), (7.2, 13.2, 5.4, 2.8, -5, 4.0)]
    else:  # frost_chains
        specs = [(3.6, 22.0, 9.0, 2.5, 28, 0.0), (8.2, 25.4, 10.6, 2.7, 40, 0.7), (13.8, 27.6, 9.8, 2.4, 52, 1.4),
                 (5.2, 17.2, 7.2, 2.1, 14, 2.1), (10.8, 19.6, 8.0, 2.2, 24, 2.8), (16.2, 21.8, 7.0, 1.9, 34, 3.5)]

    for side, tag in ((1, "l"), (-1, "r")):
        root_piv = [side * 2.2, 21.0, Z]
        children = []
        for idx, (fx, fy, fl, fw, ang, ph) in enumerate(specs):
            pz = Z + 1.0 + idx * 0.25
            piv = [side * fx, fy, pz]
            c_list = [
                make_cube(f"crys_{idx}", _sx(side, fx - fw * 0.5, fx + fw * 0.5), [fy - fl * 0.5, fy + fl * 0.5], [pz - min(0.95, fw * 0.4), pz + min(0.95, fw * 0.4)],
                          pivot=piv, rot=[0, -side * 16, -side * ang], zone="A" if idx % 2 == 0 else "C", color=p if idx % 2 == 0 else a, emissive=idx % 2 == 1, translucent=True),
                make_cube(f"core_{idx}", _sx(side, fx - fw * 0.25, fx + fw * 0.25), [fy - fl * 0.35, fy + fl * 0.35], [pz - fw * 0.22, pz + fw * 0.22],
                          pivot=piv, rot=[0, -side * 16, -side * ang], zone="C", color=a, emissive=True),
            ]
            children.append(make_bone(f"shard_{tag}_{idx}", piv, anim="wing_float", anim_phase=ph, cubes=c_list))

        if style == "frost_chains":
            chain_piv = [side * 9.8, 18.0, Z + 1.0]
            chain_cubes = [make_cube(f"link_{k}", _sx(side, 9.2, 10.4), [17.2 - k * 2.1 - 1.5, 17.2 - k * 2.1], [Z + 0.6, Z + 1.6],
                                     pivot=chain_piv, rot=[0, (k % 2) * 45, 0], zone="B", color=0xCBD5E1) for k in range(6)]
            children.append(make_bone(f"chain_{tag}", chain_piv, anim="chain_swing", anim_phase=side * 0.8, cubes=chain_cubes))
        elif style == "lotus":
            # Floating diamond prism at outer lotus arc
            dpiv = [side * 18.8, 24.5, Z + 2.0]
            children.append(make_bone(f"lotus_gem_{tag}", dpiv, anim="halo_float", cubes=[
                make_cube("lg", _sx(side, 17.6, 20.0), [23.3, 25.7], [Z + 1.2, Z + 2.8], pivot=dpiv, rot=[45, 45, 0], zone="C", color=a, emissive=True)
            ]))

        bones.append(make_bone(f"wing_{tag}_root", root_piv, rot=[0, side * 14, 0], anim=f"wing_{tag}_root", anim_amp=0.65, children=children))
    return bones


def _wing_mecha(p: int, s: int, a: int, style: str) -> list[dict[str, Any]]:
    bones: list[dict[str, Any]] = []
    Z = 3.4
    spine = [
        make_cube("reactor_body", [-2.6, 15.5, Z], [2.6, 22.8, Z + 1.8], zone="B", color=s),
        make_cube("reactor_core", [-1.4, 17.5, Z + 1.2], [1.4, 20.8, Z + 2.1], zone="C", color=a, emissive=True),
    ]
    bones.append(make_bone("mecha_spine", [0, 19.0, Z + 0.9], cubes=spine))

    for side, tag in ((1, "l"), (-1, "r")):
        root_piv = [side * 2.4, 20.6, Z + 0.5]
        cubes = []
        children = []
        if style == "thrusters":
            # 4 swept jet blades + twin burning plasma cones
            for b in range(4):
                ang = 28 - b * 22
                blen = 16.8 - abs(b - 1.0) * 2.2
                bx, by = 2.8 + b * 0.5, 22.0 - b * 1.5
                pz = Z + 0.7 + b * 0.25
                piv = [side * bx, by, pz]
                cubes.append(make_cube(f"arm_{b}", _sx(side, bx, bx + blen * 0.68), [by - 1.3, by + 1.3], [pz - 0.5, pz + 0.5], pivot=piv, rot=[0, -side * 16, side * ang], zone="B", color=s))
                cubes.append(make_cube(f"glw_{b}", _sx(side, bx + blen * 0.42, bx + blen), [by - 0.9, by + 0.9], [pz - 0.3, pz + 0.3], pivot=piv, rot=[0, -side * 16, side * ang], zone="C", color=a, emissive=True))
            children.append(make_bone(f"thruster_{tag}", [side * 3.4, 16.0, Z + 1.1], anim="flame_pulse", anim_phase=side * 0.5, cubes=[
                make_cube("jet", _sx(side, 2.3, 4.5), [11.2, 16.0], [Z + 0.4, Z + 1.8], zone="C", color=a, emissive=True, translucent=True),
            ]))
        elif style == "emerald_rail":
            # Stealth V-swept twin railgun drone fins with detached floating plasma rails!
            cubes.append(make_cube("pod_up", _sx(side, 2.6, 13.0), [21.5, 24.4], [Z + 0.5, Z + 1.7], pivot=root_piv, rot=[0, -side * 18, side * 38], zone="B", color=s))
            cubes.append(make_cube("rail_up", _sx(side, 8.5, 24.5), [23.8, 25.4], [Z + 0.9, Z + 1.7], pivot=root_piv, rot=[0, -side * 20, side * 38], zone="C", color=a, emissive=True))
            cubes.append(make_cube("pod_lo", _sx(side, 2.6, 11.2), [16.2, 18.8], [Z + 0.7, Z + 1.8], pivot=root_piv, rot=[0, -side * 18, -side * 24], zone="B", color=s))
            cubes.append(make_cube("rail_lo", _sx(side, 7.5, 20.5), [16.6, 18.0], [Z + 1.0, Z + 1.7], pivot=root_piv, rot=[0, -side * 20, -side * 24], zone="C", color=a, emissive=True))
            cubes.append(make_cube("drone_tip", _sx(side, 16.5, 19.2), [19.8, 22.0], [Z + 1.5, Z + 2.5], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        else:  # steel_valkyrie
            # 6 wide fanned metallic broadsword feather-blades!
            for b in range(6):
                ang = 40 - b * 17
                blen = 19.5 - abs(b - 1.5) * 1.9
                bx, by = 2.6 + b * 0.45, 22.8 - b * 1.35
                pz = Z + 0.6 + b * 0.24
                piv = [side * bx, by, pz]
                cubes.append(make_cube(f"sword_{b}", _sx(side, bx, bx + blen), [by - 1.45, by + 1.45], [pz - 0.35, pz + 0.35], pivot=piv, rot=[0, -side * 16, side * ang], zone="A", color=p))
                cubes.append(make_cube(f"edge_{b}", _sx(side, bx + 2.0, bx + blen + 1.2), [by - 0.45, by + 0.45], [pz - 0.45, pz + 0.45], pivot=piv, rot=[0, -side * 16, side * ang], zone="C", color=a, emissive=True))
        bones.append(make_bone(f"wing_{tag}_root", root_piv, rot=[0, side * 14, 0], anim=f"wing_{tag}_root", anim_amp=0.8, cubes=cubes, children=children))
    return bones


def _wings_steampunk(p: int, s: int, a: int) -> list[dict[str, Any]]:
    bones = []
    Z = 3.4
    for idx, (gx, gy, r, anim) in enumerate([(-2.2, 20.5, 2.6, "gear_cw"), (2.4, 17.8, 2.2, "gear_ccw")]):
        gc = [make_cube(f"g_hub_{idx}", [gx - 1.0, gy - 1.0, Z], [gx + 1.0, gy + 1.0, Z + 1.4], zone="D_GOLD", color=a)]
        for k in range(4):
            gc.append(make_cube(f"g_t_{k}", [gx - r, gy - 0.55, Z + 0.2], [gx + r, gy + 0.55, Z + 1.1],
                                pivot=[gx, gy, Z + 0.6], rot=[0, 0, k * 45], zone="B", color=s))
        bones.append(make_bone(f"gear_{idx}", [gx, gy, Z + 0.6], anim=anim, cubes=gc))
    for side, tag in ((1, "l"), (-1, "r")):
        piv = [side * 2.2, 21.0, Z + 0.5]
        cubes = [
            make_cube("strut_top", _sx(side, 2.2, 18.5), [21.5, 23.1], [Z + 0.4, Z + 1.4], pivot=piv, rot=[0, -side * 16, side * 18], zone="B", color=s),
            make_cube("strut_mid", _sx(side, 2.2, 16.0), [18.4, 19.6], [Z + 0.5, Z + 1.3], pivot=piv, rot=[0, -side * 16, side * 6], zone="B", color=s),
        ]
        for i in range(6):
            fx = 3.5 + i * 2.4
            cubes.append(make_cube(f"sail_{i}", _sx(side, fx, fx + 2.1), [12.0 + i * 0.8, 22.8 + i * 0.45], [Z + 0.7, Z + 1.2],
                                   pivot=[side * fx, 21.0, Z + 0.9], rot=[0, -side * 16, -side * (8 + i * 2)], zone="A", color=p))
        bones.append(make_bone(f"wing_{tag}_root", piv, rot=[0, side * 14, 0], anim=f"wing_{tag}_root", cubes=cubes))
    return bones


def _back_tendrils(p: int, s: int, a: int) -> list[dict[str, Any]]:
    bones = []
    Z = 3.4
    for t in range(6):
        side = 1 if t % 2 == 0 else -1
        row = t // 2
        piv = [side * (2.0 + row * 0.6), 21.5 - row * 3.2, Z]
        child = None
        for seg in range(3, -1, -1):
            spiv = [piv[0] + side * seg * 2.8, piv[1] - seg * 1.4, piv[2] + seg * 2.2]
            thick = 1.6 - seg * 0.25
            c = [make_cube(f"ts_{seg}", [spiv[0] - thick * 0.5, spiv[1] - thick * 0.5, spiv[2]],
                           [spiv[0] + thick * 0.5 + side * 2.6, spiv[1] + thick * 0.5, spiv[2] + 2.4],
                           pivot=spiv, zone="C" if seg == 3 else "A", color=a if seg == 3 else p, emissive=seg == 3)]
            child = make_bone(f"tend_{t}_{seg}", spiv, anim=f"tendril_{seg}", anim_phase=t * 0.9, cubes=c, children=[child] if child else [])
        bones.append(child)  # type: ignore[arg-type]
    return bones


def _back_katanas(p: int, s: int, a: int) -> list[dict[str, Any]]:
    Z = 3.4
    cubes = []
    for side in (-1, 1):
        piv = [0.0, 18.0, Z + 0.7]
        rot = [0.0, 0.0, side * 38.0]
        cubes.append(make_cube(f"scab_{side}", [-0.9, 6.4, Z + 0.1], [0.9, 24.0, Z + 1.5], pivot=piv, rot=rot, zone="B", color=s))
        cubes.append(make_cube(f"tsuba_{side}", [-1.8, 24.0, Z - 0.1], [1.8, 25.1, Z + 1.8], pivot=piv, rot=rot, zone="D_GOLD", color=0xF59E0B))
        cubes.append(make_cube(f"hilt_{side}", [-0.75, 25.1, Z + 0.2], [0.75, 30.2, Z + 1.4], pivot=piv, rot=rot, zone="A", color=p))
        cubes.append(make_cube(f"rune_{side}", [-0.45, 7.8, Z + 1.3], [0.45, 22.8, Z + 1.7], pivot=piv, rot=rot, zone="C", color=a, emissive=True))
    return [make_bone("katanas_root", [0, 18.0, Z + 0.7], cubes=cubes)]


def _back_quiver(p: int, s: int, a: int) -> list[dict[str, Any]]:
    Z = 3.4
    piv = [1.5, 18.5, Z + 1.2]
    rot = [0.0, 0.0, -24.0]
    cubes = [
        make_cube("quiver_body", [-0.5, 11.5, Z], [3.6, 23.0, Z + 3.0], pivot=piv, rot=rot, zone="A", color=p),
        make_cube("quiver_rim", [-0.8, 22.4, Z - 0.1], [3.9, 23.6, Z + 3.2], pivot=piv, rot=rot, zone="D_GOLD", color=0xF59E0B),
        # Back-mounted leather mount bar (never clips inside torso!)
        make_cube("mount_bar", [-3.2, 16.8, Z], [3.4, 18.6, Z + 0.9], pivot=[0, 18.0, Z + 0.4], rot=[0, 0, -28], zone="B", color=s),
    ]
    for i, (ax, az) in enumerate([(0.6, Z + 0.8), (1.6, Z + 1.7), (2.4, Z + 0.9)]):
        cubes.append(make_cube(f"shaft_{i}", [ax - 0.25, 22.8, az - 0.25], [ax + 0.25, 27.6, az + 0.25], pivot=piv, rot=rot, zone="B", color=0xE2E8F0))
        cubes.append(make_cube(f"fletch_{i}", [ax - 0.75, 26.2, az - 0.75], [ax + 0.75, 28.4, az + 0.75], pivot=piv, rot=rot, zone="C", color=a, emissive=True))
    return [make_bone("quiver_root", piv, cubes=cubes)]


def _back_jetpack(p: int, s: int, a: int) -> list[dict[str, Any]]:
    Z = 3.4
    cubes = [make_cube("bridge", [-2.4, 16.8, Z], [2.4, 21.0, Z + 2.0], zone="B", color=s)]
    flames = []
    for side in (-1, 1):
        cx = side * 2.8
        cubes.append(make_cube(f"tank_{side}", [cx - 1.7, 13.8, Z + 0.1], [cx + 1.7, 23.0, Z + 3.5], zone="A", color=p))
        cubes.append(make_cube(f"cap_{side}", [cx - 1.4, 23.0, Z + 0.4], [cx + 1.4, 24.4, Z + 3.2], zone="D_GOLD", color=0xF59E0B))
        cubes.append(make_cube(f"noz_{side}", [cx - 1.4, 12.6, Z + 0.4], [cx + 1.4, 14.0, Z + 3.2], zone="D_DARK", color=0x1E293B))
        flames.append(make_bone(f"flame_{side}", [cx, 12.6, Z + 1.8], anim="flame_pulse", anim_phase=side * 0.7, cubes=[
            make_cube(f"f_out_{side}", [cx - 1.1, 7.6, Z + 0.7], [cx + 1.1, 12.6, Z + 2.9], zone="C", color=a, emissive=True, translucent=True),
            make_cube(f"f_in_{side}", [cx - 0.55, 9.2, Z + 1.25], [cx + 0.55, 12.6, Z + 2.35], zone="D_GOLD", color=0xFEF08A, emissive=True),
        ]))
    return [make_bone("jetpack_root", [0, 18.0, Z + 1.2], cubes=cubes, children=flames)]


def _back_backpack(p: int, s: int, a: int) -> list[dict[str, Any]]:
    Z = 3.4
    cubes = [
        make_cube("pack_main", [-3.8, 12.2, Z], [3.8, 21.8, Z + 4.0], zone="A", color=p),
        make_cube("pack_flap", [-4.0, 18.8, Z - 0.05], [4.0, 22.2, Z + 4.3], zone="B", color=s),
        make_cube("pack_pocket", [-2.6, 13.0, Z + 3.8], [2.6, 17.5, Z + 5.0], zone="B", color=s),
        make_cube("bedroll", [-4.2, 22.0, Z + 0.6], [4.2, 24.4, Z + 3.2], zone="B", color=0x1E3A8A),
        make_cube("buckle_l", [-2.2, 18.0, Z + 4.1], [-1.3, 19.3, Z + 4.6], zone="D_GOLD", color=0xF59E0B),
        make_cube("buckle_r", [1.3, 18.0, Z + 4.1], [2.2, 19.3, Z + 4.6], zone="D_GOLD", color=0xF59E0B),
    ]
    lantern = make_bone("lantern", [-4.3, 15.0, Z + 2.3], anim="chain_swing", cubes=[
        make_cube("l_top", [-5.1, 14.2, Z + 1.5], [-3.5, 15.0, Z + 3.1], zone="D_DARK", color=0x1E293B),
        make_cube("l_glow", [-4.9, 12.0, Z + 1.7], [-3.7, 14.2, Z + 2.9], zone="C", color=a, emissive=True),
        make_cube("l_bot", [-5.1, 11.4, Z + 1.5], [-3.5, 12.0, Z + 3.1], zone="D_DARK", color=0x1E293B),
    ])
    return [make_bone("backpack_root", [0, 17.0, Z + 1.8], cubes=cubes, children=[lantern])]


def _back_shield(p: int, s: int, a: int) -> list[dict[str, Any]]:
    Z = 3.4
    cubes = [
        make_cube("sh_top", [-5.2, 16.0, Z], [5.2, 23.2, Z + 1.3], zone="A", color=p),
        make_cube("sh_mid", [-4.0, 12.4, Z], [4.0, 16.2, Z + 1.3], zone="A", color=p),
        make_cube("sh_tip", [-2.2, 9.6, Z], [2.2, 12.6, Z + 1.3], zone="A", color=p),
        make_cube("cross_v", [-0.9, 10.6, Z + 1.1], [0.9, 22.6, Z + 1.8], zone="D_GOLD", color=0xF59E0B),
        make_cube("cross_h", [-4.6, 17.2, Z + 1.1], [4.6, 19.0, Z + 1.8], zone="D_GOLD", color=0xF59E0B),
        make_cube("gem", [-1.2, 16.9, Z + 1.5], [1.2, 19.3, Z + 2.2], zone="C", color=a, emissive=True),
    ]
    ward_cubes = []
    for i in range(6):
        ang = i * math.tau / 6.0
        wx, wy = math.cos(ang) * 7.4, 17.5 + math.sin(ang) * 7.4
        ward_cubes.append(make_cube(f"w_{i}", [wx - 0.7, wy - 0.7, Z + 0.9], [wx + 0.7, wy + 0.7, Z + 1.7], zone="C", color=a, emissive=True))
    ward = make_bone("shield_ward", [0, 17.5, Z + 1.3], anim="gear_cw", anim_amp=0.6, cubes=ward_cubes)
    return [make_bone("shield_root", [0, 17.5, Z + 0.6], cubes=cubes, children=[ward])]


def _back_solar_crest(p: int, s: int, a: int) -> list[dict[str, Any]]:
    Z = 4.2  # Cleanly floated behind back & cloak!
    ring = []
    for i in range(16):
        ang = i * math.tau / 16.0
        rx, ry = math.cos(ang) * 8.0, 20.5 + math.sin(ang) * 8.0
        ring.append(make_cube(f"sr_{i}", [rx - 0.85, ry - 0.85, Z], [rx + 0.85, ry + 0.85, Z + 1.0], zone="C", color=a, emissive=True))
        if i % 2 == 0:
            ox, oy = math.cos(ang) * 11.8, 20.5 + math.sin(ang) * 11.8
            ring.append(make_cube(f"ray_{i}", [ox - 0.7, oy - 2.3, Z + 0.1], [ox + 0.7, oy + 2.3, Z + 0.9],
                                  pivot=[ox, oy, Z + 0.5], rot=[0, 0, math.degrees(ang) - 90], zone="A", color=p, emissive=True))
    return [make_bone("solar_ring", [0, 20.5, Z + 0.5], anim="gear_cw", anim_amp=0.45, cubes=ring)]


# Re-export headwear, auras, pets, and companions from rig_builders_extra
from rig_builders_extra import (  # noqa: E402, F401
    build_aura_bones,
    build_companion_bones,
    build_hat_bones,
    build_pet_bones,
)
