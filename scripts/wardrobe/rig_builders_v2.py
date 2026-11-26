"""101 brand-new 100% unique sculpted 3D Blockbench rig builders for the V2 expansion pack.

All anti-clipping and connectivity invariants strictly upheld:
- Back/Wings: Z >= 3.30 inside torso box
- Headwear: strictly OUTSIDE [-4.45..4.45, 23.6..32.45, -4.45..4.45]
- Auras: orbit radius R >= 12.2
- Pets: anchored at (ox=15.0, oy=25.5, oz=2.0), single connected 3D cluster
- Companions: anchored at (ox=-16.5, y>=0.0, oz=2.5), single connected 3D cluster
"""
from __future__ import annotations

import math
from typing import Any

from rig_builders import _sx, _sx_at, make_bone, make_cube


# ==============================================================================
# 1. +23 NEW UNIQUE 3D WINGS & BACKWEAR
# ==============================================================================
def build_wings_v2(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]] | None:
    Z = 3.55

    if arch == "v2_neon_katana_pack":
        cubes = [
            make_cube("nk_core", [-2.4, 15.6, Z], [2.4, 20.6, Z + 1.5], zone="D_DARK", color=p),
            make_cube("nk_led", [-1.0, 17.2, Z + 1.3], [1.0, 19.2, Z + 1.9], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            ang = side * 38.0
            cubes.append(make_cube(f"nk_sh_{side}", _sx(side, 0.2, 2.0), [10.0, 25.5], [Z + 0.6, Z + 1.8],
                                   pivot=[0, 18.2, Z + 1.2], rot=[0, 0, ang], zone="A", color=p))
            cubes.append(make_cube(f"nk_edge_{side}", _sx(side, 1.5, 2.4), [10.4, 26.8], [Z + 0.85, Z + 1.65],
                                   pivot=[0, 18.2, Z + 1.2], rot=[0, 0, ang], zone="C", color=a, emissive=True))
            cubes.append(make_cube(f"nk_hilt_{side}", _sx(side, 0.3, 1.9), [25.5, 30.8], [Z + 0.7, Z + 1.7],
                                   pivot=[0, 18.2, Z + 1.2], rot=[0, 0, ang], zone="B", color=s))
            cubes.append(make_cube(f"nk_guard_{side}", _sx(side, -0.4, 2.6), [25.0, 26.0], [Z + 0.3, Z + 2.1],
                                   pivot=[0, 18.2, Z + 1.2], rot=[0, 0, ang], zone="D_GOLD", color=a, emissive=True))
        return [make_bone("nk_root", [0, 18.2, Z + 1.0], cubes=cubes)]

    if arch == "v2_celestial_harp":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            inner = [
                make_cube(f"hp_bot_{tag}", _sx(side, 1.8, 12.2), [14.2, 16.0], [Z + 0.4, Z + 1.6], rot=[0, -side * 12, side * 14], zone="D_GOLD", color=p, emissive=True),
                make_cube(f"hp_arc_{tag}", _sx(side, 2.0, 13.8), [23.8, 25.8], [Z + 0.3, Z + 1.6], rot=[0, -side * 14, -side * 22], zone="D_GOLD", color=p, emissive=True),
                make_cube(f"hp_col_{tag}", _sx(side, 11.4, 13.2), [15.0, 27.6], [Z + 0.4, Z + 1.5], rot=[0, -side * 16, side * 8], zone="D_GOLD", color=p, emissive=True),
            ]
            for k in range(5):
                sx = 3.2 + k * 1.9
                inner.append(make_cube(f"hp_str_{tag}_{k}", _sx(side, sx, sx + 0.45), [15.4 + k * 0.25, 24.4 + k * 0.55], [Z + 0.75, Z + 1.2], zone="C", color=a, emissive=True))
            tip = make_bone(f"wing_{tag}_tip", [side * 12.6, 23.0, Z + 1.0], anim=f"wing_{tag}_tip", cubes=[
                make_cube(f"hp_p1_{tag}", _sx(side, 12.8, 22.0), [23.0, 26.2], [Z + 0.6, Z + 1.4], rot=[0, -side * 16, -side * 18], zone="A", color=s, emissive=True),
                make_cube(f"hp_p2_{tag}", _sx(side, 12.4, 19.5), [18.5, 22.2], [Z + 0.6, Z + 1.4], rot=[0, -side * 16, -side * 6], zone="A", color=s),
                make_cube(f"hp_p3_{tag}", _sx(side, 11.8, 16.8), [14.5, 18.0], [Z + 0.6, Z + 1.4], rot=[0, -side * 16, side * 8], zone="C", color=a, emissive=True),
            ])
            bones.append(make_bone(f"wing_{tag}_root", [side * 1.8, 20.5, Z + 0.8], anim=f"wing_{tag}_root", cubes=inner, children=[tip]))
        return bones

    if arch == "v2_sakura_torii_wings":
        torii = [
            make_cube("tr_top", [-11.8, 26.4, Z + 0.8], [11.8, 28.2, Z + 2.3], zone="A", color=p),
            make_cube("tr_cap", [-12.6, 28.0, Z + 0.6], [12.6, 29.0, Z + 2.5], zone="D_DARK", color=0x18181B),
            make_cube("tr_mid", [-9.2, 23.4, Z + 0.9], [9.2, 24.8, Z + 2.1], zone="A", color=p),
            make_cube("tr_pl", [6.2, 12.2, Z + 0.9], [7.8, 26.8, Z + 2.2], zone="A", color=p),
            make_cube("tr_pr", [-7.8, 12.2, Z + 0.9], [-6.2, 26.8, Z + 2.2], zone="A", color=p),
            make_cube("tr_plq", [-1.4, 24.0, Z + 0.75], [1.4, 27.0, Z + 2.45], zone="D_GOLD", color=0xFACC15, emissive=True),
        ]
        wings = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            w = [
                make_cube(f"st_f1_{tag}", _sx(side, 7.6, 17.8), [20.5, 25.0], [Z + 0.9, Z + 1.7], rot=[0, -side * 18, -side * 14], zone="B", color=s, emissive=True),
                make_cube(f"st_f2_{tag}", _sx(side, 7.2, 15.2), [15.5, 20.2], [Z + 1.0, Z + 1.8], rot=[0, -side * 18, side * 6], zone="B", color=s),
                make_cube(f"st_bl_{tag}", _sx(side, 14.5, 17.5), [22.5, 25.5], [Z + 0.7, Z + 1.9], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            wings.append(make_bone(f"wing_{tag}_root", [side * 7.4, 21.0, Z + 1.3], anim=f"wing_{tag}_root", anim_amp=0.65, cubes=w))
        return [make_bone("torii_root", [0, 20.5, Z + 1.4], cubes=torii, children=wings)]

    if arch == "v2_twin_phoenix":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            up = [make_cube(f"tp_u{i}_{tag}", _sx(side, 2.2 + i * 2.8, 6.2 + i * 3.0), [21.0 + i * 1.4, 24.2 + i * 1.8], [Z + 0.4, Z + 1.3],
                            rot=[0, -side * (12 + i * 4), -side * (20 + i * 5)], zone="C" if i >= 3 else "A", color=a if i >= 3 else p, emissive=True) for i in range(5)]
            lo = [make_cube(f"tp_l{i}_{tag}", _sx(side, 2.4 + i * 2.3, 5.6 + i * 2.4), [14.6 - i * 1.0, 18.2 - i * 0.7], [Z + 0.5, Z + 1.4],
                            rot=[0, -side * 16, side * (16 + i * 6)], zone="B" if i < 2 else "C", color=s if i < 2 else a, emissive=True) for i in range(4)]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 21.8, Z + 0.7], anim=f"wing_{tag}_root", cubes=up))
            bones.append(make_bone(f"wing_{tag}_lower", [side * 2.0, 17.0, Z + 0.9], anim=f"wing_{tag}_lower", cubes=lo))
        return bones

    if arch == "v2_snowflake_lace":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = []
            for k in range(6):
                ang = math.radians(-50 + k * 24)
                ln = 15.5 if k in (1, 3) else 11.8
                mx = side * (3.0 + math.cos(ang) * ln * 0.5)
                my = 20.5 + math.sin(ang) * ln * 0.5
                cubes.append(make_cube(f"sf_arm_{tag}_{k}", [mx - ln * 0.5, my - 0.55, Z + 0.4], [mx + ln * 0.5, my + 0.55, Z + 1.2],
                                       pivot=[mx, my, Z + 0.8], rot=[0, -side * 14, side * math.degrees(ang)], zone="A", color=p, emissive=True, translucent=True))
                tx = side * (3.0 + math.cos(ang) * ln)
                ty = 20.5 + math.sin(ang) * ln
                cubes.append(make_cube(f"sf_tip_{tag}_{k}", [tx - 1.2, ty - 1.2, Z + 0.25], [tx + 1.2, ty + 1.2, Z + 1.35],
                                       rot=[0, 0, 45], zone="C", color=a, emissive=True))
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.2, 20.5, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_crystal_orchid":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"co_p1_{tag}", _sx(side, 2.2, 13.5), [22.0, 27.6], [Z + 0.3, Z + 1.1], rot=[0, -side * 16, -side * 24], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"co_p2_{tag}", _sx(side, 3.0, 17.2), [18.0, 22.8], [Z + 0.5, Z + 1.3], rot=[0, -side * 18, -side * 6], zone="B", color=s, emissive=True, translucent=True),
                make_cube(f"co_p3_{tag}", _sx(side, 2.5, 11.8), [12.8, 17.5], [Z + 0.6, Z + 1.4], rot=[0, -side * 16, side * 18], zone="A", color=p, translucent=True),
                make_cube(f"co_gem_{tag}", _sx(side, 6.2, 8.8), [20.4, 23.0], [Z + 0.2, Z + 1.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 20.5, Z + 0.7], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_vampire_cloak_wings":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            arm = [
                make_cube(f"vc_sp_{tag}", _sx(side, 1.8, 11.5), [23.8, 25.2], [Z + 0.3, Z + 1.5], rot=[0, -side * 14, -side * 22], zone="A", color=p),
                make_cube(f"vc_clw_{tag}", _sx(side, 10.5, 13.2), [26.8, 30.2], [Z + 0.2, Z + 1.4], rot=[0, 0, -side * 12], zone="C", color=a, emissive=True),
                make_cube(f"vc_mem1_{tag}", _sx(side, 2.2, 11.2), [12.5, 24.0], [Z + 0.55, Z + 1.15], zone="B", color=s),
                make_cube(f"vc_ch_{tag}", _sx(side, 5.2, 6.2), [10.0, 14.2], [Z + 0.4, Z + 1.4], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            tip = make_bone(f"wing_{tag}_tip", [side * 11.2, 24.5, Z + 0.9], anim=f"wing_{tag}_tip", cubes=[
                make_cube(f"vc_sp2_{tag}", _sx(side, 11.2, 22.4), [24.2, 25.5], [Z + 0.4, Z + 1.4], rot=[0, -side * 18, side * 14], zone="A", color=p),
                make_cube(f"vc_mem2_{tag}", _sx(side, 11.0, 21.0), [14.0, 24.4], [Z + 0.6, Z + 1.15], rot=[0, -side * 18, 0], zone="B", color=s),
                make_cube(f"vc_ruby_{tag}", _sx(side, 17.0, 19.2), [17.0, 19.2], [Z + 0.3, Z + 1.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ])
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 22.8, Z + 0.8], anim=f"wing_{tag}_root", cubes=arm, children=[tip]))
        return bones

    if arch == "v2_mecha_thruster_pack":
        core = [
            make_cube("mt_body", [-3.8, 15.0, Z + 0.1], [3.8, 22.8, Z + 2.8], zone="B", color=s),
            make_cube("mt_reactor", [-1.6, 17.4, Z + 2.4], [1.6, 20.6, Z + 3.4], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        children = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            pod = [
                make_cube(f"mt_arm_{tag}", _sx(side, 3.6, 8.8), [19.4, 21.6], [Z + 0.8, Z + 2.4], rot=[0, -side * 14, -side * 18], zone="A", color=p),
                make_cube(f"mt_pod_{tag}", _sx(side, 8.0, 12.2), [15.6, 25.8], [Z + 0.6, Z + 3.4], rot=[0, -side * 16, -side * 10], zone="A", color=p),
                make_cube(f"mt_fin_{tag}", _sx(side, 11.6, 19.5), [21.4, 24.4], [Z + 1.2, Z + 2.1], rot=[0, -side * 18, -side * 22], zone="C", color=a, emissive=True),
            ]
            flame = make_bone(f"mt_jet_{tag}", [side * 10.1, 15.6, Z + 2.0], anim="flame_pulse", anim_phase=side * 0.6, cubes=[
                make_cube(f"mt_fl_{tag}", _sx(side, 8.8, 11.4), [9.8, 15.8], [Z + 1.0, Z + 3.0], zone="C", color=a, emissive=True, translucent=True),
            ])
            children.append(make_bone(f"wing_{tag}_root", [side * 3.8, 20.5, Z + 1.5], anim=f"wing_{tag}_root", anim_amp=0.4, cubes=pod, children=[flame]))
        return [make_bone("mecha_root", [0, 19.0, Z + 1.4], cubes=core, children=children)]

    if arch == "v2_cathedral_glass":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"cg_fr1_{tag}", _sx(side, 2.0, 17.5), [25.0, 26.2], [Z + 0.3, Z + 1.3], rot=[0, -side * 15, -side * 16], zone="B", color=s),
                make_cube(f"cg_fr2_{tag}", _sx(side, 2.0, 15.8), [13.2, 14.4], [Z + 0.4, Z + 1.4], rot=[0, -side * 15, side * 14], zone="B", color=s),
                make_cube(f"cg_p1_{tag}", _sx(side, 2.6, 9.6), [14.8, 25.0], [Z + 0.55, Z + 1.05], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"cg_p2_{tag}", _sx(side, 9.8, 16.5), [16.2, 24.4], [Z + 0.55, Z + 1.05], zone="C", color=a, emissive=True, translucent=True),
                make_cube(f"cg_bar_{tag}", _sx(side, 9.2, 10.2), [14.0, 25.4], [Z + 0.35, Z + 1.25], zone="B", color=s),
                make_cube(f"cg_rose_{tag}", _sx(side, 7.6, 11.6), [18.6, 22.6], [Z + 0.25, Z + 1.35], rot=[0, 0, 45], zone="D_GOLD", color=0xFDE047, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 20.4, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_origami_crane_wings":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"oc_t1_{tag}", _sx(side, 2.0, 11.0), [18.0, 24.8], [Z + 0.4, Z + 1.1], rot=[10, -side * 18, -side * 26], zone="A", color=p),
                make_cube(f"oc_t2_{tag}", _sx(side, 8.8, 18.6), [21.5, 27.2], [Z + 0.7, Z + 1.3], rot=[-12, -side * 24, -side * 14], zone="B", color=s),
                make_cube(f"oc_t3_{tag}", _sx(side, 4.0, 13.8), [13.0, 18.4], [Z + 0.6, Z + 1.2], rot=[12, -side * 16, side * 18], zone="A", color=p),
                make_cube(f"oc_cr_{tag}", _sx(side, 9.0, 18.0), [24.2, 24.9], [Z + 0.5, Z + 1.45], rot=[0, -side * 22, -side * 14], zone="D_GOLD", color=a, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 20.5, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_sunburst_halo_wings":
        wheel = []
        for i in range(12):
            ang = i * math.tau / 12.0
            rx, ry = math.cos(ang) * 6.5, 21.0 + math.sin(ang) * 6.5
            wheel.append(make_cube(f"sh_r_{i}", [rx - 0.7, ry - 0.7, Z + 0.9], [rx + 0.7, ry + 0.7, Z + 1.7], zone="D_GOLD", color=p, emissive=True))
        ring_b = make_bone("sh_wheel", [0, 21.0, Z + 1.3], anim="gear_cw", anim_amp=0.55, cubes=wheel)
        wings = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            rays = [make_cube(f"sh_w{k}_{tag}", _sx(side, 6.2, 16.5 + (k % 2) * 3.8), [16.0 + k * 2.4, 17.8 + k * 2.4], [Z + 0.4, Z + 1.2],
                              rot=[0, -side * 15, side * (22 - k * 12)], zone="C" if k % 2 == 0 else "A", color=a if k % 2 == 0 else p, emissive=True) for k in range(5)]
            wings.append(make_bone(f"wing_{tag}_root", [side * 5.2, 21.0, Z + 0.8], anim=f"wing_{tag}_root", cubes=rays))
        return [ring_b] + wings

    if arch == "v2_rainbow_ribbon_bows":
        knot = [make_cube("rb_knot", [-2.2, 18.0, Z + 0.3], [2.2, 22.0, Z + 2.4], zone="D_GOLD", color=0xFEF08A, emissive=True)]
        children = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            loop = [
                make_cube(f"rb_lp1_{tag}", _sx(side, 1.8, 13.4), [19.4, 26.2], [Z + 0.4, Z + 2.0], rot=[0, -side * 14, -side * 14], zone="A", color=p, emissive=True),
                make_cube(f"rb_lp2_{tag}", _sx(side, 3.0, 11.4), [20.6, 24.8], [Z + 0.2, Z + 2.2], rot=[0, -side * 14, -side * 14], zone="B", color=s),
                make_cube(f"rb_tail_{tag}", _sx(side, 2.2, 6.4), [8.2, 18.8], [Z + 0.6, Z + 1.5], rot=[8, -side * 10, side * 14], zone="C", color=a, emissive=True),
            ]
            children.append(make_bone(f"wing_{tag}_root", [side * 2.0, 20.0, Z + 1.2], anim=f"wing_{tag}_root", anim_amp=0.55, cubes=loop))
        return [make_bone("rb_root", [0, 20.0, Z + 1.2], cubes=knot, children=children)]

    if arch == "v2_candy_macaron_wings":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"cm_up_{tag}", _sx(side, 2.4, 13.8), [20.5, 27.8], [Z + 0.4, Z + 1.3], rot=[0, -side * 16, -side * 14], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"cm_lo_{tag}", _sx(side, 2.6, 10.6), [13.4, 20.2], [Z + 0.5, Z + 1.4], rot=[0, -side * 16, side * 14], zone="B", color=s, emissive=True, translucent=True),
                make_cube(f"cm_st1_{tag}", _sx(side, 7.6, 9.8), [23.0, 25.2], [Z + 0.2, Z + 1.6], rot=[0, 0, 45], zone="C", color=a, emissive=True),
                make_cube(f"cm_st2_{tag}", _sx(side, 6.2, 7.8), [15.8, 17.4], [Z + 0.3, Z + 1.6], rot=[0, 0, 45], zone="D_GOLD", color=0xFEF08A, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.2, 20.5, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_starlight_constellation":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            nodes = [(3.8, 21.5), (9.5, 25.2), (16.8, 27.6), (13.4, 19.6), (18.8, 16.8), (8.2, 14.4)]
            cubes = []
            for idx, (nx, ny) in enumerate(nodes):
                cubes.append(make_cube(f"sc_n{idx}_{tag}", _sx(side, nx - 1.1, nx + 1.1), [ny - 1.1, ny + 1.1], [Z + 0.3, Z + 1.5], rot=[0, 0, 45], zone="C", color=a, emissive=True))
            edges = [(0, 1), (1, 2), (1, 3), (3, 4), (0, 5), (5, 3)]
            for ei, (u, v) in enumerate(edges):
                x1, y1 = side * nodes[u][0], nodes[u][1]
                x2, y2 = side * nodes[v][0], nodes[v][1]
                mx, my = (x1 + x2) * 0.5, (y1 + y2) * 0.5
                ln = math.hypot(x2 - x1, y2 - y1)
                ang = math.degrees(math.atan2(y2 - y1, x2 - x1))
                cubes.append(make_cube(f"sc_e{ei}_{tag}", [mx - ln * 0.5, my - 0.32, Z + 0.65], [mx + ln * 0.5, my + 0.32, Z + 1.15],
                                       pivot=[mx, my, Z + 0.9], rot=[0, 0, ang], zone="B", color=s, emissive=True))
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.4, 21.0, Z + 0.9], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_moonlit_moth_wings":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"mm_u1_{tag}", _sx(side, 2.0, 14.8), [20.0, 27.4], [Z + 0.35, Z + 1.15], rot=[0, -side * 16, -side * 16], zone="A", color=p),
                make_cube(f"mm_u2_{tag}", _sx(side, 12.2, 19.6), [23.8, 28.4], [Z + 0.45, Z + 1.2], rot=[0, -side * 20, -side * 24], zone="B", color=s),
                make_cube(f"mm_l1_{tag}", _sx(side, 2.4, 12.4), [12.0, 19.8], [Z + 0.45, Z + 1.2], rot=[0, -side * 16, side * 12], zone="A", color=p),
                make_cube(f"mm_tail_{tag}", _sx(side, 7.0, 9.0), [6.4, 12.6], [Z + 0.5, Z + 1.2], rot=[0, -side * 16, side * 14], zone="B", color=s),
                make_cube(f"mm_moon_{tag}", _sx(side, 9.2, 12.4), [22.2, 25.4], [Z + 0.2, Z + 1.45], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 20.5, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_raijin_drums":
        cubes = []
        R = 11.4
        for i in range(12):
            ang = i * math.tau / 12.0
            rx, ry = math.cos(ang) * R, 21.5 + math.sin(ang) * R
            cubes.append(make_cube(f"rj_r_{i}", [rx - 0.65, ry - 0.65, Z + 0.8], [rx + 0.65, ry + 0.65, Z + 1.6], zone="D_GOLD", color=a, emissive=True))
        for k in range(6):
            ang = k * math.tau / 6.0 + math.pi / 6.0
            dx, dy = math.cos(ang) * R, 21.5 + math.sin(ang) * R
            cubes.append(make_cube(f"rj_d_{k}", [dx - 2.1, dy - 2.1, Z + 0.3], [dx + 2.1, dy + 2.1, Z + 2.2], zone="A", color=p))
            cubes.append(make_cube(f"rj_f_{k}", [dx - 1.4, dy - 1.4, Z + 0.1], [dx + 1.4, dy + 1.4, Z + 2.4], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        return [make_bone("raijin_ring", [0, 21.5, Z + 1.2], anim="gear_cw", anim_amp=0.35, cubes=cubes)]

    if arch == "v2_jade_dragon_spine":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"jd_b1_{tag}", _sx(side, 2.0, 12.5), [22.2, 24.4], [Z + 0.4, Z + 1.6], rot=[0, -side * 16, -side * 20], zone="D_GOLD", color=a, emissive=True),
                make_cube(f"jd_b2_{tag}", _sx(side, 11.4, 21.5), [24.0, 26.0], [Z + 0.5, Z + 1.5], rot=[0, -side * 22, side * 10], zone="D_GOLD", color=a, emissive=True),
            ]
            for k in range(5):
                fx = 3.6 + k * 3.4
                fy = 15.6 + k * 1.4
                cubes.append(make_cube(f"jd_f{k}_{tag}", _sx(side, fx, fx + 2.7), [fy, fy + 7.2], [Z + 0.6, Z + 1.35],
                                       rot=[0, -side * 18, side * (16 - k * 4)], zone="A" if k % 2 == 0 else "B", color=p if k % 2 == 0 else s, emissive=k % 2 == 0))
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 21.5, Z + 0.9], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_gothic_iron_lace":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"gi_a1_{tag}", _sx(side, 2.0, 17.0), [24.2, 25.3], [Z + 0.35, Z + 1.25], rot=[0, -side * 16, -side * 16], zone="A", color=p),
                make_cube(f"gi_a2_{tag}", _sx(side, 2.5, 14.5), [19.0, 20.0], [Z + 0.4, Z + 1.25], rot=[0, -side * 16, 0], zone="A", color=p),
                make_cube(f"gi_a3_{tag}", _sx(side, 2.2, 11.8), [13.5, 14.5], [Z + 0.45, Z + 1.25], rot=[0, -side * 16, side * 16], zone="A", color=p),
                make_cube(f"gi_v1_{tag}", _sx(side, 7.0, 7.9), [14.0, 24.8], [Z + 0.4, Z + 1.2], zone="B", color=s),
                make_cube(f"gi_v2_{tag}", _sx(side, 12.6, 13.5), [15.5, 25.6], [Z + 0.4, Z + 1.2], zone="B", color=s),
                make_cube(f"gi_r1_{tag}", _sx(side, 6.2, 8.8), [20.2, 22.8], [Z + 0.2, Z + 1.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
                make_cube(f"gi_r2_{tag}", _sx(side, 11.8, 14.2), [17.0, 19.4], [Z + 0.2, Z + 1.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 20.2, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_ocean_kelp_fins":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [
                make_cube(f"ok_f1_{tag}", _sx(side, 2.0, 16.0), [18.4, 24.4], [Z + 0.4, Z + 1.2], rot=[0, -side * 18, -side * 10], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"ok_f2_{tag}", _sx(side, 2.6, 12.2), [12.0, 18.2], [Z + 0.5, Z + 1.3], rot=[0, -side * 18, side * 16], zone="B", color=s, translucent=True),
                make_cube(f"ok_p1_{tag}", _sx(side, 8.2, 10.2), [20.4, 22.4], [Z + 0.2, Z + 1.5], zone="C", color=a, emissive=True),
                make_cube(f"ok_p2_{tag}", _sx(side, 13.6, 15.2), [22.2, 23.8], [Z + 0.2, Z + 1.5], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.0, 19.6, Z + 0.8], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    if arch == "v2_arcane_grimoires":
        bones = []
        for idx, (bx, by, bz, ph) in enumerate([(-8.8, 23.0, Z + 1.8, 0.0), (8.8, 23.0, Z + 1.8, 2.1), (0.0, 27.6, Z + 2.4, 4.2)]):
            bk = [
                make_cube(f"ag_cl_{idx}", [bx - 3.0, by - 2.0, bz], [bx - 0.1, by + 2.0, bz + 0.7], rot=[0, 22, 0], zone="A", color=p),
                make_cube(f"ag_cr_{idx}", [bx + 0.1, by - 2.0, bz], [bx + 3.0, by + 2.0, bz + 0.7], rot=[0, -22, 0], zone="A", color=p),
                make_cube(f"ag_pl_{idx}", [bx - 2.6, by - 1.6, bz - 0.45], [bx - 0.1, by + 1.6, bz + 0.15], rot=[0, 22, 0], zone="B", color=0xFFFBEB),
                make_cube(f"ag_pr_{idx}", [bx + 0.1, by - 1.6, bz - 0.45], [bx + 2.6, by + 1.6, bz + 0.15], rot=[0, -22, 0], zone="B", color=0xFFFBEB),
                make_cube(f"ag_rn_{idx}", [bx - 0.85, by - 0.85, bz - 1.1], [bx + 0.85, by + 0.85, bz - 0.4], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"ag_book_{idx}", [bx, by, bz], anim="wing_float", anim_phase=ph, cubes=bk))
        return bones

    if arch == "v2_plush_bear_pack":
        cubes = [
            make_cube("pb_body", [-3.4, 13.0, Z + 0.2], [3.4, 20.0, Z + 4.4], zone="A", color=p),
            make_cube("pb_belly", [-2.2, 13.8, Z + 3.9], [2.2, 18.4, Z + 4.7], zone="B", color=s),
            make_cube("pb_head", [-3.8, 19.6, Z + 0.0], [3.8, 26.2, Z + 4.8], zone="A", color=p),
            make_cube("pb_snout", [-1.6, 20.6, Z + 4.5], [1.6, 22.8, Z + 5.7], zone="B", color=s),
            make_cube("pb_nose", [-0.65, 21.8, Z + 5.5], [0.65, 22.6, Z + 6.0], zone="D_DARK", color=0x18181B),
            make_cube("pb_ear_l", [2.2, 25.4, Z + 1.6], [4.4, 27.6, Z + 3.4], zone="A", color=p),
            make_cube("pb_ear_r", [-4.4, 25.4, Z + 1.6], [-2.2, 27.6, Z + 3.4], zone="A", color=p),
            make_cube("pb_el", [1.0, 22.8, Z + 4.6], [2.2, 24.0, Z + 5.05], zone="D_EYE", color=0x18181B),
            make_cube("pb_er", [-2.2, 22.8, Z + 4.6], [-1.0, 24.0, Z + 5.05], zone="D_EYE", color=0x18181B),
            make_cube("pb_bl1", [2.0, 21.6, Z + 4.55], [3.1, 22.3, Z + 4.95], zone="D_BLUSH", color=0xF472B6),
            make_cube("pb_bl2", [-3.1, 21.6, Z + 4.55], [-2.0, 22.3, Z + 4.95], zone="D_BLUSH", color=0xF472B6),
            make_cube("pb_bow", [-2.4, 18.6, Z + 4.2], [2.4, 20.1, Z + 5.1], zone="C", color=a),
            make_cube("pb_arm_l", [3.2, 16.0, Z + 1.2], [5.2, 19.6, Z + 3.4], rot=[0, 0, -24], zone="A", color=p),
            make_cube("pb_arm_r", [-5.2, 16.0, Z + 1.2], [-3.2, 19.6, Z + 3.4], rot=[0, 0, 24], zone="A", color=p),
            make_cube("pb_leg_l", [1.0, 10.6, Z + 1.2], [3.2, 13.4, Z + 4.6], zone="A", color=p),
            make_cube("pb_leg_r", [-3.2, 10.6, Z + 1.2], [-1.0, 13.4, Z + 4.6], zone="A", color=p),
        ]
        return [make_bone("plush_bear_root", [0, 18.5, Z + 2.2], cubes=cubes)]

    if arch == "v2_astral_astrolabe":
        outer = [make_cube(f"aa_o_{i}", [math.cos(i * math.tau / 14) * 10.6 - 0.7, 20.5 + math.sin(i * math.tau / 14) * 10.6 - 0.7, Z + 1.0],
                           [math.cos(i * math.tau / 14) * 10.6 + 0.7, 20.5 + math.sin(i * math.tau / 14) * 10.6 + 0.7, Z + 1.9],
                           zone="D_GOLD", color=p, emissive=True) for i in range(14)]
        inner = [make_cube(f"aa_i_{i}", [math.cos(i * math.tau / 10) * 6.8 - 0.6, 20.5 + math.sin(i * math.tau / 10) * 6.8 - 0.6, Z + 1.2],
                           [math.cos(i * math.tau / 10) * 6.8 + 0.6, 20.5 + math.sin(i * math.tau / 10) * 6.8 + 0.6, Z + 2.0],
                           zone="C", color=a, emissive=True) for i in range(10)]
        core = [make_cube("aa_core", [-2.0, 18.5, Z + 0.8], [2.0, 22.5, Z + 2.6], rot=[0, 0, 45], zone="C", color=a, emissive=True)]
        return [
            make_bone("aa_outer", [0, 20.5, Z + 1.45], anim="gear_cw", anim_amp=0.45, cubes=outer),
            make_bone("aa_inner", [0, 20.5, Z + 1.6], anim="gear_ccw", anim_amp=0.75, cubes=inner),
            make_bone("aa_mid", [0, 20.5, Z + 1.6], cubes=core),
        ]

    if arch == "v2_cyber_rail_wings":
        bones = []
        for side in (-1, 1):
            tag = "l" if side > 0 else "r"
            cubes = [make_cube(f"cr_hub_{tag}", _sx(side, 1.6, 5.2), [19.4, 22.8], [Z + 0.3, Z + 2.0], zone="A", color=p)]
            for k in range(5):
                ln = 16.5 - k * 1.8
                ang = side * (-28 + k * 15)
                cubes.append(make_cube(f"cr_bl_{tag}_{k}", _sx(side, 4.8, 4.8 + ln), [21.8 - k * 2.2, 23.3 - k * 2.2], [Z + 0.7, Z + 1.5],
                                       rot=[0, -side * 16, ang], zone="C", color=a, emissive=True))
            bones.append(make_bone(f"wing_{tag}_root", [side * 2.2, 21.0, Z + 1.1], anim=f"wing_{tag}_root", cubes=cubes))
        return bones

    return None


# ==============================================================================
# 2. +22 NEW UNIQUE 3D HEADWEAR — ZERO head-box clipping!
# ==============================================================================
def build_hat_v2(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]] | None:
    if arch == "v2_sakura_kitsune_mask":
        # Tilted on top-right brow cleanly above y >= 32.6!
        cubes = [
            make_cube("km_face", [-3.4, 32.8, -5.8], [3.4, 37.8, -4.6], rot=[-18, 12, -8], zone="A", color=p),
            make_cube("km_snout", [-1.6, 32.8, -6.5], [1.6, 34.8, -5.4], rot=[-18, 12, -8], zone="A", color=p),
            make_cube("km_el", [0.8, 35.0, -6.0], [2.6, 36.2, -5.5], rot=[-18, 12, -8], zone="B", color=s, emissive=True),
            make_cube("km_er", [-2.6, 35.0, -6.0], [-0.8, 36.2, -5.5], rot=[-18, 12, -8], zone="B", color=s, emissive=True),
            make_cube("km_ear_l", [1.4, 37.4, -5.6], [3.4, 41.2, -4.6], rot=[-18, 12, -16], zone="A", color=p),
            make_cube("km_ear_r", [-3.4, 37.4, -5.6], [-1.4, 41.2, -4.6], rot=[-18, 12, 4], zone="A", color=p),
            make_cube("km_blos", [2.6, 36.8, -6.0], [4.4, 38.6, -4.8], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        return [make_bone("kitsune_mask_root", [0, 33.0, -4.8], cubes=cubes)]

    if arch == "v2_shortcake_hat":
        cubes = [
            make_cube("sc_plate", [-4.8, 32.6, -4.8], [4.8, 33.2, 4.8], zone="A", color=p),
            make_cube("sc_base", [-3.8, 33.2, -3.8], [3.8, 35.4, 3.8], zone="B", color=s),
            make_cube("sc_jam", [-3.9, 35.2, -3.9], [3.9, 36.0, 3.9], zone="C", color=a),
            make_cube("sc_top", [-3.8, 36.0, -3.8], [3.8, 37.6, 3.8], zone="A", color=p),
            make_cube("sc_berry", [-1.3, 37.6, -1.3], [1.3, 40.3, 1.3], zone="C", color=a, emissive=True),
            make_cube("sc_leaf", [-1.5, 37.5, -1.5], [1.5, 38.1, 1.5], rot=[0, 45, 0], zone="D_GOLD", color=0x22C55E),
        ]
        return [make_bone("shortcake_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_cozy_beanie":
        cubes = [
            make_cube("cb_cuff_f", [-4.9, 32.6, -5.1], [4.9, 34.4, -4.55], zone="B", color=s),
            make_cube("cb_cuff_b", [-4.9, 32.6, 4.55], [4.9, 34.4, 5.1], zone="B", color=s),
            make_cube("cb_cuff_l", [4.55, 32.6, -4.9], [5.1, 34.4, 4.9], zone="B", color=s),
            make_cube("cb_cuff_r", [-5.1, 32.6, -4.9], [-4.55, 34.4, 4.9], zone="B", color=s),
            make_cube("cb_dome", [-4.6, 32.6, -4.6], [4.6, 36.8, 4.6], zone="A", color=p),
            make_cube("cb_top", [-3.6, 36.6, -3.6], [3.6, 38.2, 3.6], zone="A", color=p),
            make_cube("cb_pom", [-1.7, 38.0, -1.7], [1.7, 41.2, 1.7], rot=[0, 45, 0], zone="C", color=a),
        ]
        return [make_bone("beanie_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_wizard_star_hat":
        cubes = [
            make_cube("wz_brim", [-7.6, 32.6, -7.6], [7.6, 33.5, 7.6], zone="A", color=p),
            make_cube("wz_band", [-4.7, 33.5, -4.7], [4.7, 34.8, 4.7], zone="C", color=a, emissive=True),
            make_cube("wz_c1", [-4.0, 34.8, -4.0], [4.0, 38.0, 4.0], zone="A", color=p),
            make_cube("wz_c2", [-2.8, 37.8, -2.8], [2.8, 41.0, 2.8], rot=[12, 0, -10], zone="A", color=p),
            make_cube("wz_c3", [-1.6, 40.6, -1.2], [1.6, 44.0, 2.0], rot=[26, 0, -18], zone="B", color=s),
            make_cube("wz_star", [-1.2, 43.2, 1.2], [1.2, 45.6, 3.6], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        return [make_bone("wizard_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_shiba_hood":
        band = [make_cube("sh_band", [-4.6, 32.6, -1.0], [4.6, 33.4, 1.0], zone="C", color=a)]
        el = make_bone("ear_l", [3.0, 33.0, 0.0], anim="ear_l", cubes=[
            make_cube("sh_el", [1.6, 33.0, -1.2], [4.6, 37.4, 1.2], rot=[0, 0, -14], zone="A", color=p),
            make_cube("sh_eli", [2.2, 33.4, -1.45], [4.0, 36.4, -0.7], rot=[0, 0, -14], zone="B", color=s),
        ])
        er = make_bone("ear_r", [-3.0, 33.0, 0.0], anim="ear_r", cubes=[
            make_cube("sh_er", [-4.6, 33.0, -1.2], [-1.6, 37.4, 1.2], rot=[0, 0, 14], zone="A", color=p),
            make_cube("sh_eri", [-4.0, 33.4, -1.45], [-2.2, 36.4, -0.7], rot=[0, 0, 14], zone="B", color=s),
        ])
        return [make_bone("shiba_hat_root", [0, 32.8, 0], cubes=band, children=[el, er])]

    if arch == "v2_butterfly_hairpin":
        cubes = [make_cube("bh_band", [-4.6, 32.6, -0.8], [4.6, 33.3, 0.8], zone="B", color=s)]
        for side in (-1, 1):
            cx = side * 3.6
            cubes.append(make_cube(f"bh_b_{side}", [cx - 0.35, 33.4, -1.6], [cx + 0.35, 34.4, 1.2], zone="D_DARK", color=0x18181B))
            cubes.append(make_cube(f"bh_wl_{side}", [cx + 0.2, 34.2, -2.0], [cx + 3.2, 37.0, 1.0], rot=[0, 0, -24], zone="A", color=p, emissive=True))
            cubes.append(make_cube(f"bh_wr_{side}", [cx - 3.2, 34.2, -2.0], [cx - 0.2, 37.0, 1.0], rot=[0, 0, 24], zone="C", color=a, emissive=True))
        return [make_bone("butterfly_hat_root", [0, 32.8, 0], cubes=cubes)]

    if arch == "v2_pastel_cat_phones":
        cubes = [
            make_cube("cp_arc", [-5.4, 32.6, -1.1], [5.4, 33.8, 1.1], zone="B", color=s),
            make_cube("cp_cup_l", [4.6, 26.2, -2.0], [6.2, 30.8, 2.0], zone="A", color=p),
            make_cube("cp_cup_r", [-6.2, 26.2, -2.0], [-4.6, 30.8, 2.0], zone="A", color=p),
            make_cube("cp_ring_l", [5.9, 27.0, -1.4], [6.5, 30.0, 1.4], zone="C", color=a, emissive=True),
            make_cube("cp_ring_r", [-6.5, 27.0, -1.4], [-5.9, 30.0, 1.4], zone="C", color=a, emissive=True),
            make_cube("cp_arm_l", [4.6, 30.6, -0.8], [5.5, 33.2, 0.8], zone="B", color=s),
            make_cube("cp_arm_r", [-5.5, 30.6, -0.8], [-4.6, 33.2, 0.8], zone="B", color=s),
            make_cube("cp_el", [1.8, 33.6, -0.8], [4.4, 37.2, 0.8], rot=[0, 0, -14], zone="A", color=p),
            make_cube("cp_er", [-4.4, 33.6, -0.8], [-1.8, 37.2, 0.8], rot=[0, 0, 14], zone="A", color=p),
            make_cube("cp_eli", [2.4, 34.1, -1.05], [3.8, 36.2, -0.4], rot=[0, 0, -14], zone="C", color=a, emissive=True),
            make_cube("cp_eri", [-3.8, 34.1, -1.05], [-2.4, 36.2, -0.4], rot=[0, 0, 14], zone="C", color=a, emissive=True),
        ]
        return [make_bone("catphones_root", [0, 32.8, 0], cubes=cubes)]

    if arch == "v2_angel_wing_band":
        band = [make_cube("aw_band", [-4.6, 32.6, -0.7], [4.6, 33.3, 0.7], zone="D_GOLD", color=a, emissive=True)]
        wl = make_bone("ear_l", [4.7, 30.5, 0.0], anim="ear_l", cubes=[
            make_cube("aw_l1", [4.8, 30.2, -1.0], [9.2, 33.0, 1.8], rot=[0, -18, -22], zone="A", color=p, emissive=True),
            make_cube("aw_l2", [4.8, 28.4, 0.0], [8.0, 30.4, 2.0], rot=[0, -18, -6], zone="B", color=s),
        ])
        wr = make_bone("ear_r", [-4.7, 30.5, 0.0], anim="ear_r", cubes=[
            make_cube("aw_r1", [-9.2, 30.2, -1.0], [-4.8, 33.0, 1.8], rot=[0, 18, 22], zone="A", color=p, emissive=True),
            make_cube("aw_r2", [-8.0, 28.4, 0.0], [-4.8, 30.4, 2.0], rot=[0, 18, 6], zone="B", color=s),
        ])
        return [make_bone("aw_root", [0, 32.6, 0], cubes=band, children=[wl, wr])]

    if arch == "v2_frost_tiara":
        cubes = [
            make_cube("ft_base", [-4.7, 32.6, -5.2], [4.7, 33.5, -4.55], zone="A", color=p, emissive=True, translucent=True),
            make_cube("ft_c", [-0.9, 33.4, -5.3], [0.9, 38.4, -4.55], zone="C", color=a, emissive=True),
            make_cube("ft_l1", [1.4, 33.4, -5.2], [2.8, 36.8, -4.55], rot=[0, 0, -14], zone="A", color=p, emissive=True, translucent=True),
            make_cube("ft_r1", [-2.8, 33.4, -5.2], [-1.4, 36.8, -4.55], rot=[0, 0, 14], zone="A", color=p, emissive=True, translucent=True),
            make_cube("ft_l2", [3.2, 33.2, -5.0], [4.4, 35.4, -4.55], rot=[0, 0, -24], zone="B", color=s, emissive=True),
            make_cube("ft_r2", [-4.4, 33.2, -5.0], [-3.2, 35.4, -4.55], rot=[0, 0, 24], zone="B", color=s, emissive=True),
        ]
        return [make_bone("frost_tiara_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_demon_lord_crown":
        cubes = [
            make_cube("dl_f", [-4.8, 32.6, -5.3], [4.8, 34.0, -4.6], zone="A", color=p),
            make_cube("dl_b", [-4.8, 32.6, 4.6], [4.8, 34.0, 5.3], zone="A", color=p),
            make_cube("dl_l", [4.6, 32.6, -4.8], [5.3, 34.0, 4.8], zone="A", color=p),
            make_cube("dl_r", [-5.3, 32.6, -4.8], [-4.6, 34.0, 4.8], zone="A", color=p),
            make_cube("dl_gem", [-1.1, 33.8, -5.6], [1.1, 36.2, -4.7], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            cubes.append(make_cube(f"dl_h1_{side}", _sx(side, 3.8, 6.2), [33.6, 36.6], [-2.0, 0.8], rot=[0, 0, -side * 28], zone="B", color=s))
            cubes.append(make_cube(f"dl_h2_{side}", _sx(side, 5.4, 7.6), [36.2, 41.2], [-1.4, 1.0], rot=[-12, 0, -side * 12], zone="C", color=a, emissive=True))
        return [make_bone("demon_crown_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_sailor_beret":
        cubes = [
            make_cube("sb_rim", [-4.7, 32.6, -4.7], [4.7, 33.8, 4.7], zone="B", color=s),
            make_cube("sb_top", [-5.4, 33.7, -5.4], [5.4, 35.8, 5.4], zone="A", color=p),
            make_cube("sb_anc", [-1.0, 33.4, -5.6], [1.0, 35.2, -4.8], zone="D_GOLD", color=a, emissive=True),
            make_cube("sb_rib1", [-1.8, 32.6, 4.7], [-0.4, 34.0, 6.8], rot=[-20, 0, 10], zone="B", color=s),
            make_cube("sb_rib2", [0.4, 32.6, 4.7], [1.8, 34.0, 6.8], rot=[-20, 0, -10], zone="B", color=s),
        ]
        return [make_bone("sailor_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_astro_visor":
        cubes = [
            make_cube("av_top", [-4.9, 32.6, -4.9], [4.9, 34.4, 4.9], zone="A", color=p),
            make_cube("av_vis", [-4.6, 27.6, -5.4], [4.6, 31.6, -4.65], zone="C", color=a, emissive=True),
            make_cube("av_side_l", [4.65, 28.0, -4.6], [5.4, 33.2, 2.0], zone="B", color=s),
            make_cube("av_side_r", [-5.4, 28.0, -4.6], [-4.65, 33.2, 2.0], zone="B", color=s),
            make_cube("av_ant", [4.7, 33.0, 0.2], [5.3, 39.2, 0.8], zone="B", color=s),
            make_cube("av_orb", [4.4, 39.0, -0.1], [5.6, 40.2, 1.1], zone="C", color=a, emissive=True),
        ]
        return [make_bone("astro_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_meadow_wreath":
        cubes = []
        for i in range(10):
            ang = i * math.tau / 10.0
            fx, fz = math.cos(ang) * 5.05, math.sin(ang) * 5.05
            cubes.append(make_cube(f"mw_l_{i}", [fx - 1.3, 32.65, fz - 1.3], [fx + 1.3, 33.55, fz + 1.3], rot=[0, -math.degrees(ang), 0], zone="A", color=p))
            cubes.append(make_cube(f"mw_f_{i}", [fx - 0.8, 33.5, fz - 0.8], [fx + 0.8, 34.7, fz + 0.8], rot=[0, 45, 0], zone="B" if i % 2 == 0 else "C", color=s if i % 2 == 0 else a, emissive=i % 2 != 0))
        return [make_bone("meadow_root", [0, 32.8, 0], cubes=cubes)]

    if arch == "v2_twin_hairbows":
        cubes = []
        for side in (-1, 1):
            bx = side * 4.8
            cubes.append(make_cube(f"th_k_{side}", [bx - 0.7, 32.8, -1.2], [bx + 0.7, 34.4, 0.8], zone="D_GOLD", color=a, emissive=True))
            cubes.append(make_cube(f"th_u_{side}", _sx(side, 4.6, 8.2), [33.4, 36.8], [-1.4, 0.8], rot=[0, 0, -side * 18], zone="A", color=p, emissive=True))
            cubes.append(make_cube(f"th_d_{side}", _sx(side, 4.6, 7.6), [29.4, 33.2], [-1.2, 0.6], rot=[0, 0, side * 12], zone="B", color=s))
        return [make_bone("twinbows_root", [0, 32.8, 0], cubes=cubes)]

    if arch == "v2_golden_laurel":
        cubes = []
        for i in range(12):
            ang = math.radians(-140 + i * (280.0 / 11.0))
            lx, lz = math.sin(ang) * 5.0, math.cos(ang) * 5.0
            cubes.append(make_cube(f"gl_l_{i}", [lx - 0.9, 32.7, lz - 0.55], [lx + 0.9, 34.6, lz + 0.55],
                                   pivot=[lx, 33.2, lz], rot=[16, math.degrees(ang), 18 if i < 6 else -18], zone="D_GOLD", color=p, emissive=True))
        return [make_bone("laurel_root", [0, 32.8, 0], cubes=cubes)]

    if arch == "v2_steampunk_tophat":
        cubes = [
            make_cube("st_brim", [-6.2, 32.6, -6.2], [6.2, 33.5, 6.2], zone="A", color=p),
            make_cube("st_cyl", [-4.3, 33.5, -4.3], [4.3, 40.6, 4.3], zone="A", color=p),
            make_cube("st_band", [-4.5, 33.6, -4.5], [4.5, 35.4, 4.5], zone="B", color=s),
            make_cube("st_gl", [0.8, 34.0, -5.3], [3.2, 36.4, -4.3], zone="C", color=a, emissive=True),
            make_cube("st_gr", [-3.2, 34.0, -5.3], [-0.8, 36.4, -4.3], zone="C", color=a, emissive=True),
        ]
        gear = make_bone("st_gear", [4.6, 36.8, 0.0], anim="gear_cw", cubes=[
            make_cube("st_g1", [4.4, 35.2, -1.6], [5.0, 38.4, 1.6], zone="D_GOLD", color=0xF59E0B),
            make_cube("st_g2", [4.4, 35.2, -1.6], [5.0, 38.4, 1.6], rot=[45, 0, 0], zone="D_GOLD", color=0xF59E0B),
        ])
        return [make_bone("tophat_root", [0, 32.6, 0], cubes=cubes, children=[gear])]

    if arch == "v2_slime_hat":
        cubes = [
            make_cube("sl_out", [-3.8, 32.6, -3.8], [3.8, 38.6, 3.8], zone="A", color=p, translucent=True),
            make_cube("sl_in", [-2.6, 33.4, -2.6], [2.6, 37.4, 2.6], zone="B", color=s, emissive=True),
            make_cube("sl_el", [0.8, 35.2, -4.05], [2.0, 36.4, -3.65], zone="D_EYE", color=0x18181B),
            make_cube("sl_er", [-2.0, 35.2, -4.05], [-0.8, 36.4, -3.65], zone="D_EYE", color=0x18181B),
            make_cube("sl_bl1", [1.8, 34.2, -4.0], [2.8, 34.9, -3.65], zone="D_BLUSH", color=0xF472B6),
            make_cube("sl_bl2", [-2.8, 34.2, -4.0], [-1.8, 34.9, -3.65], zone="D_BLUSH", color=0xF472B6),
        ]
        return [make_bone("slime_hat_root", [0, 32.6, 0], anim="halo_float", anim_amp=0.25, cubes=cubes)]

    if arch == "v2_panda_hood":
        cubes = [
            make_cube("pd_band", [-4.6, 32.6, -0.9], [4.6, 33.3, 0.9], zone="B", color=s),
            make_cube("pd_el", [2.4, 32.8, -1.2], [5.4, 36.0, 1.2], zone="A", color=p),
            make_cube("pd_er", [-5.4, 32.8, -1.2], [-2.4, 36.0, 1.2], zone="A", color=p),
            make_cube("pd_stem", [-0.35, 33.2, -0.35], [0.35, 36.2, 0.35], zone="C", color=a),
            make_cube("pd_lf1", [0.2, 35.4, -0.6], [2.6, 36.6, 0.6], rot=[0, 0, 22], zone="C", color=a),
            make_cube("pd_lf2", [-2.6, 35.4, -0.6], [-0.2, 36.6, 0.6], rot=[0, 0, -22], zone="C", color=a),
        ]
        return [make_bone("panda_hat_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_crescent_diadem":
        cubes = [
            make_cube("cd_m1", [-2.2, 34.6, -5.2], [2.2, 36.0, -4.6], zone="D_GOLD", color=p, emissive=True),
            make_cube("cd_m2", [1.6, 35.6, -5.2], [3.0, 38.8, -4.6], rot=[0, 0, -18], zone="D_GOLD", color=p, emissive=True),
            make_cube("cd_m3", [-3.0, 35.6, -5.2], [-1.6, 38.8, -4.6], rot=[0, 0, 18], zone="D_GOLD", color=p, emissive=True),
            make_cube("cd_gem", [-0.8, 36.2, -5.3], [0.8, 37.8, -4.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        ring = [make_cube(f"cd_r_{i}", [math.cos(i * math.tau / 10) * 6.0 - 0.55, 37.8, math.sin(i * math.tau / 10) * 6.0 - 0.55],
                          [math.cos(i * math.tau / 10) * 6.0 + 0.55, 38.6, math.sin(i * math.tau / 10) * 6.0 + 0.55],
                          zone="C", color=a, emissive=True) for i in range(10)]
        return [make_bone("cd_root", [0, 33.0, 0], cubes=cubes), make_bone("cd_halo", [0, 38.2, 0], anim="halo_spin_cw", cubes=ring)]

    if arch == "v2_holo_visor":
        cubes = [
            make_cube("hv_lens", [-4.6, 27.2, -5.5], [4.6, 30.2, -4.75], zone="C", color=a, emissive=True, translucent=True),
            make_cube("hv_bar", [-4.9, 30.0, -5.65], [4.9, 30.7, -4.65], zone="B", color=s),
            make_cube("hv_s_l", [4.65, 28.4, -4.9], [5.3, 29.8, 1.8], zone="B", color=s),
            make_cube("hv_s_r", [-5.3, 28.4, -4.9], [-4.65, 29.8, 1.8], zone="B", color=s),
        ]
        return [make_bone("holo_visor_root", [0, 29.0, 0], anim="visor_pulse", cubes=cubes)]

    if arch == "v2_frog_bucket_hat":
        cubes = [
            make_cube("fb_brim", [-6.2, 32.6, -6.2], [6.2, 33.4, 6.2], zone="A", color=p),
            make_cube("fb_top", [-4.6, 33.4, -4.6], [4.6, 36.2, 4.6], zone="A", color=p),
            make_cube("fb_e1", [1.4, 35.8, -3.6], [4.0, 38.4, -1.0], zone="A", color=p),
            make_cube("fb_e2", [-4.0, 35.8, -3.6], [-1.4, 38.4, -1.0], zone="A", color=p),
            make_cube("fb_p1", [1.9, 36.4, -3.85], [3.5, 38.0, -3.3], zone="D_EYE", color=0x18181B),
            make_cube("fb_p2", [-3.5, 36.4, -3.85], [-1.9, 38.0, -3.3], zone="D_EYE", color=0x18181B),
            make_cube("fb_bl1", [2.6, 34.4, -4.85], [3.8, 35.2, -4.45], zone="D_BLUSH", color=a),
            make_cube("fb_bl2", [-3.8, 34.4, -4.85], [-2.6, 35.2, -4.45], zone="D_BLUSH", color=a),
        ]
        return [make_bone("frog_hat_root", [0, 32.6, 0], cubes=cubes)]

    if arch == "v2_celestial_wing_halo":
        r1 = [make_cube(f"cw_r1_{i}", [math.cos(i * math.tau / 12) * 5.2 - 0.55, 36.2, math.sin(i * math.tau / 12) * 5.2 - 0.55],
                        [math.cos(i * math.tau / 12) * 5.2 + 0.55, 37.0, math.sin(i * math.tau / 12) * 5.2 + 0.55],
                        zone="D_GOLD", color=p, emissive=True) for i in range(12)]
        r2 = [make_cube(f"cw_r2_{i}", [math.cos(i * math.tau / 8) * 7.6 - 0.45, 37.6, math.sin(i * math.tau / 8) * 7.6 - 0.45],
                        [math.cos(i * math.tau / 8) * 7.6 + 0.45, 38.2, math.sin(i * math.tau / 8) * 7.6 + 0.45],
                        zone="C", color=a, emissive=True) for i in range(8)]
        w = [
            make_cube("cw_wl", [5.4, 35.8, -0.8], [9.8, 38.4, 0.8], rot=[0, 0, -18], zone="B", color=s, emissive=True),
            make_cube("cw_wr", [-9.8, 35.8, -0.8], [-5.4, 38.4, 0.8], rot=[0, 0, 18], zone="B", color=s, emissive=True),
        ]
        return [
            make_bone("cw_h1", [0, 36.6, 0], anim="halo_spin_cw", cubes=r1 + w),
            make_bone("cw_h2", [0, 37.9, 0], anim="halo_spin_ccw", cubes=r2),
        ]

    return None


# ==============================================================================
# 3. +20 NEW UNIQUE 3D AURAS — ALL ORBITS r >= 12.2!
# ==============================================================================
def build_aura_v2(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]] | None:
    R = 12.6

    if arch == "v2_sakura_whirlwind":
        bones = []
        for i in range(12):
            y = 6.0 + (i % 6) * 4.2
            r = R + (i % 3) * 1.4
            c = [
                make_cube(f"sw_p1_{i}", [r - 1.2, y - 0.5, -0.7], [r + 1.2, y + 0.5, 0.7], rot=[20, 0, 35], zone="A", color=p, emissive=True),
                make_cube(f"sw_p2_{i}", [r - 0.6, y + 0.3, -1.0], [r + 0.6, y + 1.1, 0.2], rot=[-15, 30, 0], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"sw_pet_{i}", [0, y, 0], anim="aura_spiral_up", anim_amp=0.85, anim_phase=i / 12.0, cubes=c))
        return bones

    if arch == "v2_floating_lanterns":
        bones = []
        for i in range(6):
            r = R + 1.4 + (i % 2) * 1.8
            y = 14.0 + (i % 3) * 4.5
            c = [
                make_cube(f"fl_t_{i}", [r - 1.3, y + 2.0, -1.3], [r + 1.3, y + 2.7, 1.3], zone="D_DARK", color=s),
                make_cube(f"fl_b_{i}", [r - 1.7, y - 1.8, -1.7], [r + 1.7, y + 2.0, 1.7], zone="A", color=p, emissive=True),
                make_cube(f"fl_c_{i}", [r - 1.0, y - 1.0, -1.0], [r + 1.0, y + 1.2, 1.0], zone="C", color=a, emissive=True),
                make_cube(f"fl_d_{i}", [r - 1.3, y - 2.4, -1.3], [r + 1.3, y - 1.8, 1.3], zone="D_DARK", color=s),
            ]
            bones.append(make_bone(f"fl_lan_{i}", [0, y, 0], anim="aura_orbit_cw", anim_amp=0.55, anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_mini_planets":
        specs = [(13.4, 21.0, 2.2, "A", p, True), (15.8, 15.0, 1.8, "B", s, False), (12.8, 11.0, 1.4, "C", a, False), (14.6, 26.5, 1.5, "D_GOLD", 0xFDE047, False)]
        bones = []
        for i, (r, y, sz, zn, col, ringed) in enumerate(specs):
            c = [make_cube(f"mp_p_{i}", [r - sz, y - sz, -sz], [r + sz, y + sz, sz], zone=zn, color=col, emissive=True)]
            if ringed:
                c.append(make_cube(f"mp_rg_{i}", [r - sz * 1.85, y - 0.35, -sz * 1.85], [r + sz * 1.85, y + 0.35, sz * 1.85], rot=[18, 0, 14], zone="D_GOLD", color=0xFDE047, emissive=True))
            bones.append(make_bone(f"mp_orb_{i}", [0, y, 0], anim="aura_orbit_cw" if i % 2 == 0 else "aura_orbit_ccw", anim_amp=0.7 + i * 0.2, anim_phase=i * 1.6, cubes=c))
        return bones

    if arch == "v2_heart_ribbons":
        bones = []
        for i in range(6):
            r = R + 0.8 + (i % 2) * 1.2
            y = 16.0 + (i % 3) * 3.8
            c = [
                make_cube(f"hr_b_{i}", [r - 1.2, y - 1.2, -0.6], [r + 1.2, y + 1.2, 0.6], rot=[0, 0, 45], zone="A", color=p, emissive=True),
                make_cube(f"hr_l_{i}", [r - 1.7, y + 0.2, -0.6], [r - 0.1, y + 1.8, 0.6], zone="A", color=p, emissive=True),
                make_cube(f"hr_r_{i}", [r + 0.1, y + 0.2, -0.6], [r + 1.7, y + 1.8, 0.6], zone="A", color=p, emissive=True),
            ]
            bones.append(make_bone(f"hr_h_{i}", [0, y, 0], anim="aura_orbit_cw", anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_crystal_shards":
        bones = []
        for i in range(8):
            r = R + 0.8 + (i % 2) * 2.0
            y = 11.0 + (i % 4) * 4.5
            c = [
                make_cube(f"cs_m_{i}", [r - 0.9, y - 3.0, -0.9], [r + 0.9, y + 3.0, 0.9], rot=[12, 45, 0], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"cs_t_{i}", [r - 0.5, y + 2.8, -0.5], [r + 0.5, y + 4.4, 0.5], rot=[12, 45, 0], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"cs_sh_{i}", [0, y, 0], anim="aura_orbit_cw" if i % 2 == 0 else "aura_orbit_ccw", anim_phase=i * 0.8, cubes=c))
        return bones

    if arch == "v2_data_matrix":
        bones = []
        for i in range(6):
            r = R + 1.2
            y = 13.0 + (i % 2) * 8.0
            c = [
                make_cube(f"dm_scr_{i}", [r - 0.25, y - 2.2, -2.8], [r + 0.25, y + 2.2, 2.8], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"dm_ln1_{i}", [r - 0.4, y + 0.6, -2.2], [r + 0.4, y + 1.2, 1.8], zone="C", color=a, emissive=True),
                make_cube(f"dm_ln2_{i}", [r - 0.4, y - 1.0, -2.2], [r + 0.4, y - 0.4, 0.8], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"dm_p_{i}", [0, y, 0], anim="aura_orbit_ccw", anim_amp=0.8, anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_snowfall_flurry":
        bones = []
        for i in range(10):
            r = R + 0.6 + (i % 3) * 1.4
            y = 9.0 + (i % 5) * 4.2
            c = [
                make_cube(f"sf_a_{i}", [r - 1.5, y - 0.35, -0.35], [r + 1.5, y + 0.35, 0.35], zone="C", color=a, emissive=True),
                make_cube(f"sf_b_{i}", [r - 0.35, y - 1.5, -0.35], [r + 0.35, y + 1.5, 0.35], zone="C", color=a, emissive=True),
                make_cube(f"sf_c_{i}", [r - 1.1, y - 0.35, -0.35], [r + 1.1, y + 0.35, 0.35], rot=[0, 0, 45], zone="A", color=p, emissive=True),
            ]
            bones.append(make_bone(f"sf_fl_{i}", [0, y, 0], anim="aura_spiral_up", anim_amp=-0.65, anim_phase=i / 10.0, cubes=c))
        return bones

    if arch == "v2_musical_melody":
        bones = []
        for i in range(6):
            r = R + 1.0 + (i % 2) * 1.5
            y = 14.0 + (i % 3) * 5.0
            c = [
                make_cube(f"mm_hd_{i}", [r - 1.0, y - 1.0, -0.5], [r + 1.0, y + 0.6, 0.5], zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True),
                make_cube(f"mm_st_{i}", [r + 0.4, y, -0.35], [r + 1.0, y + 3.8, 0.35], zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True),
                make_cube(f"mm_fl_{i}", [r + 0.8, y + 2.4, -0.35], [r + 2.4, y + 3.8, 0.35], rot=[0, 0, -20], zone="B", color=s, emissive=True),
            ]
            bones.append(make_bone(f"mm_nt_{i}", [0, y, 0], anim="aura_orbit_cw", anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_golden_koi_orbit":
        bones = []
        for i in range(2):
            r = R + 1.6
            y = 13.0 + i * 7.5
            zn = "A" if i == 0 else "B"
            col = p if i == 0 else s
            c = [
                make_cube(f"gk_b_{i}", [r - 1.4, y - 1.2, -3.2], [r + 1.4, y + 1.2, 2.2], zone=zn, color=col, emissive=True),
                make_cube(f"gk_t_{i}", [r - 0.5, y - 1.5, 2.0], [r + 0.5, y + 1.5, 5.0], rot=[0, 18, 0], zone="C", color=a, emissive=True),
                make_cube(f"gk_fl_{i}", [r + 1.2, y - 0.5, -1.5], [r + 3.0, y + 0.3, 0.5], rot=[0, -24, 0], zone=zn, color=col, emissive=True),
                make_cube(f"gk_fr_{i}", [r - 3.0, y - 0.5, -1.5], [r - 1.2, y + 0.3, 0.5], rot=[0, 24, 0], zone=zn, color=col, emissive=True),
            ]
            bones.append(make_bone(f"gk_fish_{i}", [0, y, 0], anim="aura_orbit_cw", anim_amp=0.85, anim_phase=i * math.pi, cubes=c))
        return bones

    if arch == "v2_firefly_meadow":
        bones = []
        for i in range(12):
            r = R + (i % 4) * 1.3
            y = 8.0 + (i % 6) * 3.6
            c = [make_cube(f"ff_{i}", [r - 0.65, y - 0.65, -0.65], [r + 0.65, y + 0.65, 0.65], zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True)]
            bones.append(make_bone(f"ff_b_{i}", [0, y, 0], anim=f"aura_swarm_{i}", anim_amp=0.6, anim_phase=i * 0.7, cubes=c))
        return bones

    if arch == "v2_arcane_tarot":
        bones = []
        for i in range(8):
            r = R + 1.2
            y = 16.5 + (i % 2) * 3.0
            c = [
                make_cube(f"at_c_{i}", [r - 0.35, y - 2.4, -1.5], [r + 0.35, y + 2.4, 1.5], zone="A", color=p),
                make_cube(f"at_g_{i}", [r - 0.45, y - 2.6, -1.7], [r + 0.45, y + 2.6, 1.7], zone="B", color=s, emissive=True),
                make_cube(f"at_s_{i}", [r - 0.55, y - 0.8, -0.8], [r + 0.55, y + 0.8, 0.8], rot=[45, 0, 0], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"at_card_{i}", [0, y, 0], anim="aura_orbit_cw", anim_amp=0.6, anim_phase=i * math.tau / 8.0, cubes=c))
        return bones

    if arch == "v2_phoenix_spiral":
        bones = []
        for i in range(10):
            r = R + 0.8 + (i % 2) * 1.5
            y = 10.0 + (i % 5) * 4.0
            c = [
                make_cube(f"ps_f_{i}", [r - 0.6, y - 2.4, -1.1], [r + 0.6, y + 2.4, 1.1], rot=[28, 0, 14], zone="A", color=p, emissive=True),
                make_cube(f"ps_t_{i}", [r - 0.4, y + 1.8, -0.7], [r + 0.4, y + 3.4, 0.7], rot=[28, 0, 14], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"ps_pl_{i}", [0, y, 0], anim="aura_spiral_up", anim_amp=1.0, anim_phase=i / 10.0, cubes=c))
        return bones

    if arch == "v2_lightning_orbs":
        bones = []
        for i in range(4):
            r = R + 1.4
            y = 17.0 + (i % 2) * 4.5
            c = [
                make_cube(f"lo_o_{i}", [r - 1.5, y - 1.5, -1.5], [r + 1.5, y + 1.5, 1.5], zone="A", color=p, emissive=True),
                make_cube(f"lo_z1_{i}", [r - 0.4, y + 1.4, -2.4], [r + 0.4, y + 2.6, 2.4], rot=[30, 0, 25], zone="C", color=a, emissive=True),
                make_cube(f"lo_z2_{i}", [r - 2.4, y - 2.4, -0.4], [r + 2.4, y - 1.4, 0.4], rot=[0, 25, -30], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"lo_orb_{i}", [0, y, 0], anim="aura_orbit_fast", anim_amp=0.95, anim_phase=i * math.tau / 4.0, cubes=c))
        return bones

    if arch == "v2_rainbow_stars":
        bones = []
        for i in range(8):
            r = R + 1.0 + (i % 3) * 1.1
            y = 12.0 + (i % 4) * 4.8
            c = [
                make_cube(f"rs_1_{i}", [r - 1.3, y - 1.3, -0.5], [r + 1.3, y + 1.3, 0.5], zone="A", color=p, emissive=True),
                make_cube(f"rs_2_{i}", [r - 1.3, y - 1.3, -0.5], [r + 1.3, y + 1.3, 0.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"rs_st_{i}", [0, y, 0], anim="aura_orbit_cw", anim_amp=0.9, anim_phase=i * math.tau / 8.0, cubes=c))
        return bones

    if arch == "v2_torii_spirits":
        bones = []
        for i in range(6):
            r = R + 1.2
            y = 13.0 + (i % 3) * 5.0
            c = [
                make_cube(f"ts_f_{i}", [r - 1.1, y - 1.1, -1.1], [r + 1.1, y + 1.5, 1.1], zone="A", color=p, emissive=True),
                make_cube(f"ts_t_{i}", [r - 0.6, y + 1.4, -0.6], [r + 0.6, y + 3.2, 0.6], zone="C", color=a, emissive=True),
                make_cube(f"ts_tg_{i}", [r - 0.2, y - 3.6, -1.0], [r + 0.2, y - 1.0, 1.0], zone="B", color=s),
            ]
            bones.append(make_bone(f"ts_sp_{i}", [0, y, 0], anim="aura_orbit_ccw", anim_amp=0.7, anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_raven_feathers":
        bones = []
        for i in range(10):
            r = R + 0.9 + (i % 3) * 1.2
            y = 9.0 + (i % 5) * 4.4
            c = [
                make_cube(f"rf_b_{i}", [r - 0.5, y - 2.6, -1.0], [r + 0.5, y + 2.6, 1.0], rot=[22, 18, -16], zone="A", color=p),
                make_cube(f"rf_e_{i}", [r - 0.6, y + 1.6, -0.7], [r + 0.6, y + 2.8, 0.7], rot=[22, 18, -16], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"rf_f_{i}", [0, y, 0], anim="aura_orbit_ccw", anim_amp=0.85, anim_phase=i * math.tau / 10.0, cubes=c))
        return bones

    if arch == "v2_snowflake_ring":
        cubes = []
        for i in range(12):
            ang = i * math.tau / 12.0
            rx, rz = math.cos(ang) * 13.6, math.sin(ang) * 13.6
            cubes.append(make_cube(f"sr_s_{i}", [rx - 2.0, 0.8, rz - 0.45], [rx + 2.0, 1.5, rz + 0.45],
                                   pivot=[rx, 1.15, rz], rot=[0, -math.degrees(ang) + 90, 0], zone="A", color=p, emissive=True, translucent=True))
            if i % 2 == 0:
                tx, tz = math.cos(ang) * 16.0, math.sin(ang) * 16.0
                cubes.append(make_cube(f"sr_p_{i}", [tx - 1.0, 0.8, tz - 1.0], [tx + 1.0, 1.7, tz + 1.0], rot=[0, 45, 0], zone="C", color=a, emissive=True))
        return [make_bone("frost_circle", [0, 1.15, 0], anim="aura_ring_ground", anim_amp=0.55, cubes=cubes)]

    if arch == "v2_origami_cranes":
        bones = []
        for i in range(6):
            r = R + 1.4
            y = 16.0 + (i % 3) * 4.5
            c = [
                make_cube(f"oc_bd_{i}", [r - 0.8, y - 0.6, -1.8], [r + 0.8, y + 0.6, 1.8], zone="A", color=p, emissive=True),
                make_cube(f"oc_wl_{i}", [r + 0.6, y + 0.2, -1.2], [r + 3.4, y + 1.0, 1.0], rot=[0, 0, 20], zone="B", color=s, emissive=True),
                make_cube(f"oc_wr_{i}", [r - 3.4, y + 0.2, -1.2], [r - 0.6, y + 1.0, 1.0], rot=[0, 0, -20], zone="B", color=s, emissive=True),
                make_cube(f"oc_nk_{i}", [r - 0.4, y + 0.4, -2.8], [r + 0.4, y + 2.2, -1.5], rot=[-22, 0, 0], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"oc_cr_{i}", [0, y, 0], anim="aura_orbit_cw", anim_amp=0.75, anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_pastel_puffs":
        bones = []
        for i in range(6):
            r = R + 1.0 + (i % 2) * 1.4
            y = 12.0 + (i % 3) * 5.0
            c = [
                make_cube(f"pp_c1_{i}", [r - 2.0, y - 1.2, -1.5], [r + 2.0, y + 1.2, 1.5], zone="A" if i % 2 == 0 else "B", color=p if i % 2 == 0 else s, emissive=True),
                make_cube(f"pp_c2_{i}", [r - 1.2, y + 0.6, -1.1], [r + 1.2, y + 2.2, 1.1], zone="A" if i % 2 == 0 else "B", color=p if i % 2 == 0 else s, emissive=True),
                make_cube(f"pp_st_{i}", [r - 0.5, y - 2.6, -0.5], [r + 0.5, y - 1.6, 0.5], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            ]
            bones.append(make_bone(f"pp_cl_{i}", [0, y, 0], anim="aura_orbit_cw", anim_amp=0.5, anim_phase=i * math.tau / 6.0, cubes=c))
        return bones

    if arch == "v2_constellation_ring":
        cubes = []
        for i in range(12):
            ang = i * math.tau / 12.0
            rx, rz = math.cos(ang) * 14.4, math.sin(ang) * 14.4
            cubes.append(make_cube(f"zr_b_{i}", [rx - 2.1, 17.5, rz - 0.4], [rx + 2.1, 18.5, rz + 0.4],
                                   pivot=[rx, 18.0, rz], rot=[0, -math.degrees(ang) + 90, 0], zone="D_GOLD", color=p, emissive=True))
            cubes.append(make_cube(f"zr_g_{i}", [rx - 0.85, 17.2, rz - 0.85], [rx + 0.85, 18.9, rz + 0.85],
                                   rot=[0, 45, 0], zone="C", color=a, emissive=True))
        return [make_bone("zodiac_ring", [0, 18.0, 0], rot=[26, 0, 18], anim="aura_orbit_cw", anim_amp=0.55, cubes=cubes)]

    return None


# ==============================================================================
# 4. +18 NEW UNIQUE FLOATING PETS — 100% connected single cluster, cute + cool!
# ==============================================================================
def build_pet_v2(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]] | None:
    ox, oy, oz = 15.0, 25.5, 2.0

    def cute_eyes(cx: float, cy: float, cz: float, sep: float = 1.25, sz: float = 1.05, blush: bool = True) -> list[dict[str, Any]]:
        out = [
            make_cube("eye_l", [cx + sep - sz * 0.5, cy, cz - 0.35], [cx + sep + sz * 0.5, cy + sz, cz + 0.15], zone="D_EYE", color=0x18181B),
            make_cube("eye_r", [cx - sep - sz * 0.5, cy, cz - 0.35], [cx - sep + sz * 0.5, cy + sz, cz + 0.15], zone="D_EYE", color=0x18181B),
        ]
        if blush:
            out.append(make_cube("blush_l", [cx + sep + 0.25, cy - 0.65, cz - 0.3], [cx + sep + 1.05, cy - 0.1, cz + 0.15], zone="D_BLUSH", color=0xF472B6))
            out.append(make_cube("blush_r", [cx - sep - 1.05, cy - 0.65, cz - 0.3], [cx - sep - 0.25, cy - 0.1, cz + 0.15], zone="D_BLUSH", color=0xF472B6))
        return out

    if arch == "v2_sakura_fennec":
        body = [
            make_cube("fn_body", [ox - 2.0, oy - 1.8, oz - 2.0], [ox + 2.0, oy + 1.4, oz + 2.8], zone="A", color=p),
            make_cube("fn_belly", [ox - 1.5, oy - 1.9, oz - 1.6], [ox + 1.5, oy - 0.4, oz + 2.2], zone="B", color=s),
            make_cube("fn_p1", [ox + 0.6, oy - 2.8, oz - 1.6], [ox + 1.7, oy - 1.4, oz - 0.4], zone="B", color=s),
            make_cube("fn_p2", [ox - 1.7, oy - 2.8, oz - 1.6], [ox - 0.6, oy - 1.4, oz - 0.4], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.2, oz - 1.8], anim="pet_head", cubes=[
            make_cube("fn_h", [ox - 2.4, oy + 0.4, oz - 5.2], [ox + 2.4, oy + 4.4, oz - 1.0], zone="A", color=p),
            make_cube("fn_sn", [ox - 1.1, oy + 0.6, oz - 6.4], [ox + 1.1, oy + 2.1, oz - 5.0], zone="B", color=s),
            make_cube("fn_ns", [ox - 0.4, oy + 1.5, oz - 6.6], [ox + 0.4, oy + 2.1, oz - 6.1], zone="D_DARK", color=0x18181B),
            make_cube("fn_el", [ox + 1.2, oy + 3.4, oz - 3.6], [ox + 5.6, oy + 8.6, oz - 2.2], rot=[0, 0, -28], zone="A", color=p),
            make_cube("fn_er", [ox - 5.6, oy + 3.4, oz - 3.6], [ox - 1.2, oy + 8.6, oz - 2.2], rot=[0, 0, 28], zone="A", color=p),
            make_cube("fn_eli", [ox + 1.8, oy + 4.0, oz - 3.85], [ox + 4.8, oy + 7.6, oz - 3.0], rot=[0, 0, -28], zone="D_BLUSH", color=a),
            make_cube("fn_eri", [ox - 4.8, oy + 4.0, oz - 3.85], [ox - 1.8, oy + 7.6, oz - 3.0], rot=[0, 0, 28], zone="D_BLUSH", color=a),
            make_cube("fn_flw", [ox - 0.85, oy + 4.3, oz - 4.0], [ox + 0.85, oy + 5.7, oz - 2.4], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 2.1, oz - 5.2))
        tail = make_bone("pet_tail", [ox, oy, oz + 2.6], anim="pet_tail", cubes=[
            make_cube("fn_t", [ox - 1.6, oy - 1.0, oz + 2.4], [ox + 1.6, oy + 2.2, oz + 7.2], rot=[20, 0, 0], zone="A", color=p),
            make_cube("fn_tt", [ox - 1.2, oy + 0.4, oz + 6.4], [ox + 1.2, oy + 2.6, oz + 8.4], rot=[20, 0, 0], zone="B", color=s),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail])]

    if arch == "v2_mochi_bunny":
        body = [
            make_cube("mb_b", [ox - 3.2, oy - 2.2, oz - 3.2], [ox + 3.2, oy + 2.4, oz + 3.2], zone="A", color=p),
            make_cube("mb_t", [ox - 1.0, oy - 1.4, oz + 3.0], [ox + 1.0, oy + 0.6, oz + 4.6], zone="B", color=s),
            make_cube("mb_el", [ox + 0.8, oy + 2.2, oz - 1.8], [ox + 2.4, oy + 6.2, oz - 0.4], rot=[0, 0, -14], zone="A", color=p),
            make_cube("mb_er", [ox - 2.4, oy + 2.2, oz - 1.8], [ox - 0.8, oy + 6.2, oz - 0.4], rot=[0, 0, 14], zone="A", color=p),
            make_cube("mb_lf", [ox - 0.8, oy + 2.4, oz - 0.8], [ox + 0.8, oy + 3.8, oz + 0.8], rot=[12, 0, 20], zone="C", color=a),
        ] + cute_eyes(ox, oy + 0.2, oz - 3.2, 1.5, 1.1)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body)]

    if arch == "v2_celestial_whale":
        body = [
            make_cube("cw_hd", [ox - 3.0, oy - 1.8, oz - 4.4], [ox + 3.0, oy + 2.6, oz + 1.8], zone="A", color=p),
            make_cube("cw_bel", [ox - 2.6, oy - 2.1, oz - 4.0], [ox + 2.6, oy - 0.6, oz + 1.4], zone="B", color=s),
            make_cube("cw_fl", [ox + 2.8, oy - 1.2, oz - 1.8], [ox + 6.6, oy - 0.3, oz + 0.8], rot=[0, -16, -12], zone="C", color=a, emissive=True),
            make_cube("cw_fr", [ox - 6.6, oy - 1.2, oz - 1.8], [ox - 2.8, oy - 0.3, oz + 0.8], rot=[0, 16, 12], zone="C", color=a, emissive=True),
            make_cube("cw_cr", [ox - 0.8, oy + 2.5, oz - 2.6], [ox + 0.8, oy + 4.1, oz - 1.0], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 0.2, oz - 4.4, 1.7, 1.0)
        tail = make_bone("pet_tail", [ox, oy, oz + 1.6], anim="pet_tail", cubes=[
            make_cube("cw_t1", [ox - 1.8, oy - 1.2, oz + 1.4], [ox + 1.8, oy + 1.6, oz + 4.8], zone="A", color=p),
            make_cube("cw_flk", [ox - 4.2, oy - 0.4, oz + 4.4], [ox + 4.2, oy + 0.6, oz + 7.2], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[tail])]

    if arch == "v2_phoenix_chick":
        body = [
            make_cube("pc_bd", [ox - 2.4, oy - 2.0, oz - 2.6], [ox + 2.4, oy + 2.2, oz + 2.6], zone="A", color=p),
            make_cube("pc_cr1", [ox - 0.6, oy + 2.0, oz - 1.4], [ox + 0.6, oy + 4.8, oz + 0.2], rot=[-18, 0, 0], zone="C", color=a, emissive=True),
            make_cube("pc_cr2", [ox - 0.5, oy + 2.0, oz + 0.2], [ox + 0.5, oy + 4.2, oz + 1.6], rot=[-28, 0, 0], zone="B", color=s, emissive=True),
            make_cube("pc_bk", [ox - 0.7, oy - 0.2, oz - 3.6], [ox + 0.7, oy + 0.8, oz - 2.4], zone="D_GOLD", color=0xF59E0B),
            make_cube("pc_tl", [ox - 1.4, oy - 0.4, oz + 2.4], [ox + 1.4, oy + 1.8, oz + 5.6], rot=[22, 0, 0], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 0.4, oz - 2.6, 1.35, 1.0)
        wl = make_bone("pet_wl", [ox + 2.4, oy + 0.6, oz], anim="pet_wing_l", cubes=[
            make_cube("pc_wl", [ox + 2.2, oy - 0.4, oz - 1.4], [ox + 6.4, oy + 1.6, oz + 1.4], zone="B", color=s, emissive=True),
        ])
        wr = make_bone("pet_wr", [ox - 2.4, oy + 0.6, oz], anim="pet_wing_r", cubes=[
            make_cube("pc_wr", [ox - 6.4, oy - 0.4, oz - 1.4], [ox - 2.2, oy + 1.6, oz + 1.4], zone="B", color=s, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "v2_chibi_kitsune":
        body = [
            make_cube("ck_bd", [ox - 1.9, oy - 1.6, oz - 2.2], [ox + 1.9, oy + 1.4, oz + 2.4], zone="A", color=p),
            make_cube("ck_rf", [ox - 2.2, oy - 1.4, oz - 2.6], [ox + 2.2, oy + 1.6, oz - 0.8], zone="B", color=s),
        ]
        head = make_bone("pet_head", [ox, oy + 1.2, oz - 2.0], anim="pet_head", cubes=[
            make_cube("ck_hd", [ox - 2.3, oy + 0.6, oz - 5.2], [ox + 2.3, oy + 4.4, oz - 1.2], zone="A", color=p),
            make_cube("ck_sn", [ox - 1.0, oy + 0.7, oz - 6.4], [ox + 1.0, oy + 2.0, oz - 5.0], zone="A", color=p),
            make_cube("ck_el", [ox + 0.9, oy + 4.2, oz - 3.6], [ox + 2.7, oy + 7.2, oz - 2.2], rot=[0, 0, -14], zone="A", color=p),
            make_cube("ck_er", [ox - 2.7, oy + 4.2, oz - 3.6], [ox - 0.9, oy + 7.2, oz - 2.2], rot=[0, 0, 14], zone="A", color=p),
            make_cube("ck_mk", [ox - 0.5, oy + 3.0, oz - 5.4], [ox + 0.5, oy + 4.0, oz - 5.0], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 2.0, oz - 5.2, 1.25, 1.0))
        tails = []
        for k in (-2, -1, 0, 1, 2):
            tails.append(make_cube(f"ck_t_{k}", [ox + k * 0.9 - 0.7, oy - 0.6, oz + 2.2], [ox + k * 0.9 + 0.7, oy + 2.2, oz + 6.4],
                                   pivot=[ox, oy, oz + 2.2], rot=[26, k * 18, 0], zone="A", color=p))
            tails.append(make_cube(f"ck_tt_{k}", [ox + k * 0.9 - 0.6, oy + 0.8, oz + 5.6], [ox + k * 0.9 + 0.6, oy + 2.4, oz + 7.2],
                                   pivot=[ox, oy, oz + 2.2], rot=[26, k * 18, 0], zone="C", color=a, emissive=True))
        tail_b = make_bone("pet_tail", [ox, oy, oz + 2.2], anim="pet_tail", anim_amp=0.5, cubes=tails)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, tail_b])]

    if arch == "v2_glow_jellyfish":
        body = [
            make_cube("gj_bell", [ox - 3.0, oy - 0.4, oz - 3.0], [ox + 3.0, oy + 3.2, oz + 3.0], zone="A", color=p, emissive=True, translucent=True),
            make_cube("gj_top", [ox - 2.2, oy + 3.0, oz - 2.2], [ox + 2.2, oy + 4.2, oz + 2.2], zone="B", color=s, emissive=True, translucent=True),
            make_cube("gj_core", [ox - 1.5, oy + 0.4, oz - 1.5], [ox + 1.5, oy + 2.6, oz + 1.5], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 1.0, oz - 3.0, 1.35, 1.0)
        tents = []
        for tx, tz in [(-1.6, -1.2), (1.6, -1.2), (-1.4, 1.4), (1.4, 1.4), (0.0, 0.0)]:
            tents.append(make_cube(f"gj_t_{tx}_{tz}", [ox + tx - 0.4, oy - 4.8, oz + tz - 0.4], [ox + tx + 0.4, oy - 0.2, oz + tz + 0.4], zone="C", color=a, emissive=True, translucent=True))
        tb = make_bone("pet_tail", [ox, oy - 0.4, oz], anim="pet_pendulum", anim_amp=0.6, cubes=tents)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[tb])]

    if arch == "v2_paper_plane_sprite":
        body = [
            make_cube("pp_keel", [ox - 0.5, oy - 1.4, oz - 4.4], [ox + 0.5, oy + 0.8, oz + 3.8], zone="B", color=s),
            make_cube("pp_wl", [ox + 0.3, oy + 0.2, oz - 3.6], [ox + 5.2, oy + 0.9, oz + 3.6], rot=[0, 14, -8], zone="A", color=p),
            make_cube("pp_wr", [ox - 5.2, oy + 0.2, oz - 3.6], [ox - 0.3, oy + 0.9, oz + 3.6], rot=[0, -14, 8], zone="A", color=p),
            make_cube("pp_star", [ox - 1.2, oy + 0.8, oz - 1.2], [ox + 1.2, oy + 3.2, oz + 1.2], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body)]

    if arch == "v2_ghost_lantern":
        body = [
            make_cube("gl_top", [ox - 2.0, oy + 2.6, oz - 2.0], [ox + 2.0, oy + 3.5, oz + 2.0], zone="D_DARK", color=0x18181B),
            make_cube("gl_b", [ox - 2.6, oy - 2.2, oz - 2.6], [ox + 2.6, oy + 2.7, oz + 2.6], zone="A", color=p, emissive=True),
            make_cube("gl_bot", [ox - 2.0, oy - 3.0, oz - 2.0], [ox + 2.0, oy - 2.1, oz + 2.0], zone="D_DARK", color=0x18181B),
            make_cube("gl_tag", [ox - 0.8, oy + 0.2, oz - 3.1], [ox + 0.8, oy + 3.4, oz - 2.4], rot=[-10, 0, 8], zone="B", color=s),
            make_cube("gl_tng", [ox - 0.7, oy - 1.6, oz - 3.4], [ox + 0.7, oy - 0.4, oz - 2.4], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 0.4, oz - 2.6, 1.4, 1.0, False)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body)]

    if arch == "v2_baby_ice_dragon":
        body = [
            make_cube("id_bd", [ox - 2.0, oy - 1.6, oz - 2.4], [ox + 2.0, oy + 1.6, oz + 3.0], zone="A", color=p),
            make_cube("id_sp1", [ox - 0.4, oy + 1.5, oz - 1.2], [ox + 0.4, oy + 3.2, oz + 0.0], zone="C", color=a, emissive=True),
            make_cube("id_sp2", [ox - 0.4, oy + 1.5, oz + 0.8], [ox + 0.4, oy + 3.0, oz + 2.0], zone="C", color=a, emissive=True),
        ]
        head = make_bone("pet_head", [ox, oy + 1.4, oz - 2.2], anim="pet_head", cubes=[
            make_cube("id_hd", [ox - 2.1, oy + 0.6, oz - 5.4], [ox + 2.1, oy + 3.8, oz - 1.8], zone="A", color=p),
            make_cube("id_sn", [ox - 1.2, oy + 0.6, oz - 6.8], [ox + 1.2, oy + 2.0, oz - 5.2], zone="B", color=s),
            make_cube("id_hl", [ox + 1.0, oy + 3.6, oz - 3.8], [ox + 2.0, oy + 6.4, oz - 2.6], rot=[-22, 0, -14], zone="C", color=a, emissive=True),
            make_cube("id_hr", [ox - 2.0, oy + 3.6, oz - 3.8], [ox - 1.0, oy + 6.4, oz - 2.6], rot=[-22, 0, 14], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 2.0, oz - 5.4, 1.2, 1.0))
        wl = make_bone("pet_wl", [ox + 2.0, oy + 1.2, oz], anim="pet_wing_l", cubes=[
            make_cube("id_wl", [ox + 1.8, oy + 0.6, oz - 1.6], [ox + 7.4, oy + 2.0, oz + 2.4], zone="C", color=a, emissive=True, translucent=True),
        ])
        wr = make_bone("pet_wr", [ox - 2.0, oy + 1.2, oz], anim="pet_wing_r", cubes=[
            make_cube("id_wr", [ox - 7.4, oy + 0.6, oz - 1.6], [ox - 1.8, oy + 2.0, oz + 2.4], zone="C", color=a, emissive=True, translucent=True),
        ])
        tail = make_bone("pet_tail", [ox, oy, oz + 2.8], anim="pet_tail", cubes=[
            make_cube("id_t", [ox - 0.9, oy - 0.7, oz + 2.6], [ox + 0.9, oy + 0.9, oz + 6.6], zone="A", color=p),
            make_cube("id_tt", [ox - 1.4, oy - 0.8, oz + 6.0], [ox + 1.4, oy + 1.4, oz + 8.0], rot=[0, 45, 0], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[head, wl, wr, tail])]

    if arch == "v2_bee_queen":
        body = [
            make_cube("bq_b", [ox - 3.0, oy - 2.4, oz - 3.8], [ox + 3.0, oy + 2.6, oz + 3.8], zone="A", color=p),
            make_cube("bq_cr", [ox - 1.4, oy + 2.5, oz - 2.6], [ox + 1.4, oy + 3.8, oz - 0.6], zone="D_GOLD", color=0xFDE047, emissive=True),
            make_cube("bq_sp", [ox - 0.4, oy + 3.7, oz - 2.2], [ox + 0.4, oy + 4.8, oz - 1.0], zone="C", color=a, emissive=True),
            make_cube("bq_dr", [ox - 0.8, oy - 3.6, oz - 1.2], [ox + 0.8, oy - 2.0, oz + 0.4], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 0.4, oz - 3.8, 1.5, 1.1)
        wl = make_bone("pet_wl", [ox + 2.2, oy + 2.4, oz], anim="pet_wing_l", anim_amp=1.5, cubes=[
            make_cube("bq_wl", [ox + 2.0, oy + 2.3, oz - 2.0], [ox + 7.6, oy + 2.9, oz + 2.0], zone="B", color=s, translucent=True, emissive=True),
        ])
        wr = make_bone("pet_wr", [ox - 2.2, oy + 2.4, oz], anim="pet_wing_r", anim_amp=1.5, cubes=[
            make_cube("bq_wr", [ox - 7.6, oy + 2.3, oz - 2.0], [ox - 2.0, oy + 2.9, oz + 2.0], zone="B", color=s, translucent=True, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "v2_strawberry_fairy":
        body = [
            make_cube("sf_b", [ox - 2.4, oy - 2.2, oz - 2.4], [ox + 2.4, oy + 2.2, oz + 2.4], zone="A", color=p),
            make_cube("sf_b2", [ox - 1.7, oy - 3.2, oz - 1.7], [ox + 1.7, oy - 1.8, oz + 1.7], zone="A", color=p),
            make_cube("sf_cap", [ox - 2.8, oy + 2.0, oz - 2.8], [ox + 2.8, oy + 3.0, oz + 2.8], rot=[0, 45, 0], zone="B", color=s),
            make_cube("sf_st", [ox - 0.4, oy + 2.8, oz - 0.4], [ox + 0.4, oy + 4.4, oz + 0.4], zone="B", color=s),
        ] + cute_eyes(ox, oy + 0.0, oz - 2.4, 1.25, 1.0)
        wl = make_bone("pet_wl", [ox + 2.2, oy + 0.6, oz + 0.8], anim="pet_wing_l", cubes=[
            make_cube("sf_wl", [ox + 2.0, oy - 0.6, oz + 0.6], [ox + 6.4, oy + 2.4, oz + 1.4], zone="C", color=a, emissive=True, translucent=True),
        ])
        wr = make_bone("pet_wr", [ox - 2.2, oy + 0.6, oz + 0.8], anim="pet_wing_r", cubes=[
            make_cube("sf_wr", [ox - 6.4, oy - 0.6, oz + 0.6], [ox - 2.0, oy + 2.4, oz + 1.4], zone="C", color=a, emissive=True, translucent=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "v2_mecha_drone":
        body = [
            make_cube("md_core", [ox - 2.4, oy - 1.6, oz - 2.6], [ox + 2.4, oy + 1.8, oz + 2.6], zone="A", color=p),
            make_cube("md_eye", [ox - 1.2, oy - 0.4, oz - 3.0], [ox + 1.2, oy + 1.0, oz - 2.3], zone="C", color=a, emissive=True),
            make_cube("md_ant", [ox - 0.3, oy + 1.6, oz + 0.5], [ox + 0.3, oy + 4.2, oz + 1.1], zone="B", color=s),
        ]
        rotors = []
        for sx, sz in [(-3.4, -2.6), (3.4, -2.6), (-3.4, 2.6), (3.4, 2.6)]:
            body.append(make_cube(f"md_arm_{sx}_{sz}", [min(ox, ox + sx), oy + 0.4, min(oz, oz + sz)], [max(ox, ox + sx), oy + 1.1, max(oz, oz + sz)], zone="B", color=s))
            rotors.append(make_bone(f"md_rot_{sx}_{sz}", [ox + sx, oy + 1.4, oz + sz], anim="pet_spin", anim_amp=2.6, cubes=[
                make_cube(f"md_bl_{sx}_{sz}", [ox + sx - 1.8, oy + 1.2, oz + sz - 0.3], [ox + sx + 1.8, oy + 1.6, oz + sz + 0.3], zone="C", color=a, emissive=True),
            ]))
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=rotors)]

    if arch == "v2_cloud_sheep":
        body = [
            make_cube("cs_w1", [ox - 2.8, oy - 2.0, oz - 2.8], [ox + 2.8, oy + 2.4, oz + 2.8], zone="A", color=p),
            make_cube("cs_w2", [ox - 3.5, oy - 1.2, oz - 1.8], [ox + 3.5, oy + 1.6, oz + 2.0], zone="A", color=p),
            make_cube("cs_w3", [ox - 2.0, oy + 1.8, oz - 2.0], [ox + 2.0, oy + 3.2, oz + 2.0], zone="A", color=p),
            make_cube("cs_fc", [ox - 1.8, oy - 1.2, oz - 3.9], [ox + 1.8, oy + 1.6, oz - 2.4], zone="B", color=s),
            make_cube("cs_el", [ox + 1.6, oy + 0.0, oz - 3.6], [ox + 3.2, oy + 1.0, oz - 2.4], rot=[0, 0, -24], zone="B", color=s),
            make_cube("cs_er", [ox - 3.2, oy + 0.0, oz - 3.6], [ox - 1.6, oy + 1.0, oz - 2.4], rot=[0, 0, 24], zone="B", color=s),
            make_cube("cs_hl", [ox - 1.6, oy + 4.0, oz - 1.6], [ox + 1.6, oy + 4.5, oz + 1.6], zone="D_GOLD", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 0.1, oz - 3.9, 1.0, 0.9)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body)]

    if arch == "v2_crystal_butterfly":
        body = [
            make_cube("cb_bd", [ox - 0.7, oy - 1.4, oz - 2.4], [ox + 0.7, oy + 1.4, oz + 2.4], zone="D_DARK", color=0x18181B),
            make_cube("cb_al", [ox + 0.4, oy + 1.2, oz - 3.8], [ox + 1.0, oy + 2.6, oz - 2.0], rot=[-18, 0, -14], zone="C", color=a, emissive=True),
            make_cube("cb_ar", [ox - 1.0, oy + 1.2, oz - 3.8], [ox - 0.4, oy + 2.6, oz - 2.0], rot=[-18, 0, 14], zone="C", color=a, emissive=True),
        ]
        wl = make_bone("pet_wl", [ox + 0.7, oy + 0.6, oz], anim="pet_wing_l", cubes=[
            make_cube("cb_wl1", [ox + 0.6, oy + 0.4, oz - 2.6], [ox + 6.4, oy + 4.8, oz + 0.6], rot=[0, 0, -10], zone="A", color=p, emissive=True),
            make_cube("cb_wl2", [ox + 0.6, oy - 2.4, oz + 0.2], [ox + 5.0, oy + 0.8, oz + 3.0], rot=[0, 0, 10], zone="C", color=a, emissive=True),
        ])
        wr = make_bone("pet_wr", [ox - 0.7, oy + 0.6, oz], anim="pet_wing_r", cubes=[
            make_cube("cb_wr1", [ox - 6.4, oy + 0.4, oz - 2.6], [ox - 0.6, oy + 4.8, oz + 0.6], rot=[0, 0, 10], zone="A", color=p, emissive=True),
            make_cube("cb_wr2", [ox - 5.0, oy - 2.4, oz + 0.2], [ox - 0.6, oy + 0.8, oz + 3.0], rot=[0, 0, -10], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[wl, wr])]

    if arch == "v2_moon_rabbit":
        body = [
            # Glowing golden crescent cradle beneath cute floating rabbit!
            make_cube("mr_c1", [ox - 2.8, oy - 3.2, oz - 1.2], [ox + 2.8, oy - 1.8, oz + 1.2], zone="D_GOLD", color=a, emissive=True),
            make_cube("mr_c2", [ox + 2.0, oy - 2.4, oz - 1.1], [ox + 4.4, oy + 1.6, oz + 1.1], rot=[0, 0, -24], zone="D_GOLD", color=a, emissive=True),
            make_cube("mr_c3", [ox - 4.4, oy - 2.4, oz - 1.1], [ox - 2.0, oy + 1.6, oz + 1.1], rot=[0, 0, 24], zone="D_GOLD", color=a, emissive=True),
            make_cube("mr_bd", [ox - 2.0, oy - 1.9, oz - 2.0], [ox + 2.0, oy + 1.8, oz + 2.0], zone="A", color=p),
            make_cube("mr_el", [ox + 0.5, oy + 1.6, oz - 1.0], [ox + 1.8, oy + 5.6, oz + 0.4], rot=[0, 0, -12], zone="A", color=p),
            make_cube("mr_er", [ox - 1.8, oy + 1.6, oz - 1.0], [ox - 0.5, oy + 5.6, oz + 0.4], rot=[0, 0, 12], zone="A", color=p),
        ] + cute_eyes(ox, oy + 0.2, oz - 2.0, 1.05, 0.9)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body)]

    if arch == "v2_cosmic_axolotl":
        # Properly connected single-cluster starry axolotl using _sx_at(ox, ...)
        body = [
            make_cube("ca_bd", [ox - 2.0, oy - 1.4, oz - 1.4], [ox + 2.0, oy + 1.4, oz + 3.4], zone="A", color=p),
            make_cube("ca_hd", [ox - 2.6, oy - 1.5, oz - 4.8], [ox + 2.6, oy + 2.3, oz - 1.0], zone="A", color=p),
            make_cube("ca_fn", [ox - 0.35, oy + 1.2, oz - 1.0], [ox + 0.35, oy + 2.9, oz + 3.4], zone="C", color=a, emissive=True),
        ] + cute_eyes(ox, oy + 0.2, oz - 4.8, 1.5, 1.0)
        for side in (-1, 1):
            body.append(make_cube(f"ca_g1_{side}", _sx_at(ox, side, 2.3, 4.9), [oy + 0.9, oy + 2.1], [oz - 3.0, oz - 2.0], rot=[0, 0, -side * 16], zone="C", color=a, emissive=True))
            body.append(make_cube(f"ca_g2_{side}", _sx_at(ox, side, 2.3, 5.2), [oy - 0.2, oy + 0.9], [oz - 3.0, oz - 2.0], zone="C", color=a, emissive=True))
            body.append(make_cube(f"ca_g3_{side}", _sx_at(ox, side, 2.3, 4.5), [oy - 1.2, oy - 0.1], [oz - 3.0, oz - 2.0], rot=[0, 0, side * 16], zone="C", color=a, emissive=True))
        tail = make_bone("pet_tail", [ox, oy, oz + 3.2], anim="pet_tail", cubes=[
            make_cube("ca_t1", [ox - 0.5, oy - 1.2, oz + 3.0], [ox + 0.5, oy + 1.6, oz + 7.2], zone="B", color=s),
            make_cube("ca_t2", [ox - 0.3, oy - 1.6, oz + 5.2], [ox + 0.3, oy + 2.1, oz + 8.6], zone="C", color=a, emissive=True),
        ])
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body, children=[tail])]

    if arch == "v2_teacup_kitten":
        body = [
            make_cube("tk_cup", [ox - 2.8, oy - 3.0, oz - 2.8], [ox + 2.8, oy + 0.4, oz + 2.8], zone="B", color=s),
            make_cube("tk_sau", [ox - 3.8, oy - 3.6, oz - 3.8], [ox + 3.8, oy - 2.8, oz + 3.8], zone="B", color=s),
            make_cube("tk_hnd", [ox + 2.6, oy - 2.2, oz - 0.6], [ox + 4.0, oy - 0.2, oz + 0.6], zone="C", color=a, emissive=True),
            make_cube("tk_hd", [ox - 2.2, oy + 0.2, oz - 2.2], [ox + 2.2, oy + 3.8, oz + 2.0], zone="A", color=p),
            make_cube("tk_el", [ox + 0.9, oy + 3.6, oz - 0.8], [ox + 2.3, oy + 5.4, oz + 0.6], zone="A", color=p),
            make_cube("tk_er", [ox - 2.3, oy + 3.6, oz - 0.8], [ox - 0.9, oy + 5.4, oz + 0.6], zone="A", color=p),
            make_cube("tk_p1", [ox + 1.0, oy + 0.0, oz - 3.1], [ox + 2.1, oy + 1.0, oz - 1.9], zone="A", color=p),
            make_cube("tk_p2", [ox - 2.1, oy + 0.0, oz - 3.1], [ox - 1.0, oy + 1.0, oz - 1.9], zone="A", color=p),
        ] + cute_eyes(ox, oy + 1.6, oz - 2.2, 1.15, 0.95)
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body)]

    if arch == "v2_sun_sprite":
        body = [make_cube("ss_core", [ox - 2.5, oy - 2.5, oz - 2.5], [ox + 2.5, oy + 2.5, oz + 2.5], zone="A", color=p, emissive=True)] + cute_eyes(ox, oy - 0.2, oz - 2.5, 1.25, 1.0)
        rays = []
        for i in range(8):
            ang = i * math.tau / 8.0
            rx, ry = ox + math.cos(ang) * 3.8, oy + math.sin(ang) * 3.8
            rays.append(make_cube(f"ss_r_{i}", [rx - 0.8, ry - 0.8, oz - 0.5], [rx + 0.8, ry + 0.8, oz + 0.5], rot=[0, 0, math.degrees(ang)], zone="C", color=a, emissive=True))
        return [make_bone("pet_root", [ox, oy, oz], anim="pet_hover", cubes=body + rays)]

    return None


# ==============================================================================
# 5. +18 NEW UNIQUE GROUND COMPANIONS — y0 >= 0, 100% connected single cluster!
# ==============================================================================
def build_companion_v2(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]] | None:
    ox, oz = -16.5, 2.5

    def quad_animal(
        tag: str, bw: float, bh: float, bl: float, leg_h: float, hw: float, hh: float, hl: float,
        ear_h: float, ear_w: float, tail_len: float, tail_up: bool = True,
        extra_body: list[dict[str, Any]] | None = None,
        extra_head: list[dict[str, Any]] | None = None,
        blush: bool = True,
    ) -> list[dict[str, Any]]:
        by = leg_h
        body = [
            make_cube(f"{tag}_bd", [ox - bw * 0.5, by, oz - bl * 0.5], [ox + bw * 0.5, by + bh, oz + bl * 0.5], zone="A", color=p),
            make_cube(f"{tag}_bl", [ox - bw * 0.42, by, oz - bl * 0.52], [ox + bw * 0.42, by + bh * 0.55, oz + bl * 0.35], zone="B", color=s),
        ] + (extra_body or [])
        lw = max(0.85, bw * 0.28)
        legs = []
        for ltag, lx, lz in [
            ("comp_leg_fl", ox + bw * 0.32, oz - bl * 0.34),
            ("comp_leg_fr", ox - bw * 0.32, oz - bl * 0.34),
            ("comp_leg_bl", ox + bw * 0.32, oz + bl * 0.34),
            ("comp_leg_br", ox - bw * 0.32, oz + bl * 0.34),
        ]:
            legs.append(make_bone(ltag, [lx, by + 0.4, lz], anim=ltag, cubes=[
                make_cube(f"{tag}_{ltag}", [lx - lw * 0.5, 0.0, lz - lw * 0.5], [lx + lw * 0.5, by + 0.6, lz + lw * 0.5], zone="A", color=p),
                make_cube(f"{tag}_{ltag}_p", [lx - lw * 0.55, 0.0, lz - lw * 0.6], [lx + lw * 0.55, 0.85, lz + lw * 0.5], zone="B", color=s),
            ]))
        hy = by + bh - 0.6
        hz = oz - bl * 0.5
        hc = [
            make_cube(f"{tag}_hd", [ox - hw * 0.5, hy, hz - hl], [ox + hw * 0.5, hy + hh, hz + 0.6], zone="A", color=p),
            make_cube(f"{tag}_sn", [ox - hw * 0.28, hy + 0.2, hz - hl - 1.1], [ox + hw * 0.28, hy + hh * 0.45, hz - hl + 0.3], zone="B", color=s),
            make_cube(f"{tag}_ns", [ox - 0.42, hy + hh * 0.28, hz - hl - 1.3], [ox + 0.42, hy + hh * 0.46, hz - hl - 0.85], zone="D_DARK", color=0x18181B),
            make_cube(f"{tag}_el", [ox + hw * 0.22, hy + hh - 0.2, hz - hl * 0.55], [ox + hw * 0.22 + ear_w, hy + hh + ear_h, hz - hl * 0.25], zone="A", color=p),
            make_cube(f"{tag}_er", [ox - hw * 0.22 - ear_w, hy + hh - 0.2, hz - hl * 0.55], [ox - hw * 0.22, hy + hh + ear_h, hz - hl * 0.25], zone="A", color=p),
            make_cube(f"{tag}_eye_l", [ox + hw * 0.16, hy + hh * 0.46, hz - hl - 0.3], [ox + hw * 0.16 + 1.0, hy + hh * 0.46 + 1.0, hz - hl + 0.15], zone="D_EYE", color=0x18181B),
            make_cube(f"{tag}_eye_r", [ox - hw * 0.16 - 1.0, hy + hh * 0.46, hz - hl - 0.3], [ox - hw * 0.16, hy + hh * 0.46 + 1.0, hz - hl + 0.15], zone="D_EYE", color=0x18181B),
        ]
        if blush:
            hc.append(make_cube(f"{tag}_bl1", [ox + hw * 0.26, hy + hh * 0.26, hz - hl - 0.26], [ox + hw * 0.26 + 0.9, hy + hh * 0.26 + 0.55, hz - hl + 0.15], zone="D_BLUSH", color=0xF472B6))
            hc.append(make_cube(f"{tag}_bl2", [ox - hw * 0.26 - 0.9, hy + hh * 0.26, hz - hl - 0.26], [ox - hw * 0.26, hy + hh * 0.26 + 0.55, hz - hl + 0.15], zone="D_BLUSH", color=0xF472B6))
        if extra_head:
            hc.extend(extra_head)
        head = make_bone("comp_head", [ox, hy + hh * 0.3, hz], anim="comp_head", cubes=hc)
        tail = make_bone("comp_tail", [ox, by + bh * 0.65, oz + bl * 0.48], anim="comp_tail", cubes=[
            make_cube(f"{tag}_tl", [ox - 0.85, by + bh * 0.5, oz + bl * 0.42], [ox + 0.85, by + bh * 0.5 + (tail_len if tail_up else 1.5), oz + bl * 0.42 + (1.8 if tail_up else tail_len)],
                      rot=[24 if tail_up else -18, 0, 0], zone="B", color=s),
        ])
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + [head, tail])]

    if arch == "v2_pastel_corgi":
        return quad_animal("cg", 3.6, 3.2, 7.6, 2.0, 3.8, 3.4, 3.4, 2.6, 1.4, 1.9, True,
                           extra_body=[make_cube("cg_bow", [ox - 1.4, 4.4, oz - 4.2], [ox + 1.4, 5.6, oz - 3.4], zone="C", color=a)])

    if arch == "v2_calico_kitten":
        return quad_animal("ck", 2.8, 2.7, 5.2, 2.4, 3.3, 2.9, 2.8, 1.6, 1.1, 4.4, True,
                           extra_body=[
                               make_cube("ck_pt1", [ox + 0.4, 3.5, oz - 1.2], [ox + 1.55, 5.2, oz + 1.4], zone="B", color=s),
                               make_cube("ck_bell", [ox - 0.55, 4.0, oz - 3.1], [ox + 0.55, 5.0, oz - 2.3], zone="D_GOLD", color=0xFDE047),
                           ])

    if arch == "v2_polar_cub":
        return quad_animal("pl", 4.2, 4.0, 6.8, 2.8, 4.2, 3.8, 3.4, 1.2, 1.3, 1.4, False,
                           extra_body=[
                               make_cube("pl_sc", [ox - 2.3, 5.6, oz - 3.8], [ox + 2.3, 6.9, oz - 2.4], zone="C", color=a),
                               make_cube("pl_sct", [ox + 1.2, 3.4, oz - 4.1], [ox + 2.4, 6.2, oz - 3.1], zone="C", color=a),
                           ])

    if arch == "v2_sakura_fawn":
        return quad_animal("sf", 2.7, 3.2, 6.4, 4.6, 3.0, 2.8, 3.0, 1.8, 1.0, 1.6, True,
                           extra_head=[
                               make_cube("sf_al", [ox + 0.9, 9.8, oz - 5.2], [ox + 1.6, 12.8, oz - 4.4], rot=[0, 0, -18], zone="B", color=s),
                               make_cube("sf_ar", [ox - 1.6, 9.8, oz - 5.2], [ox - 0.9, 12.8, oz - 4.4], rot=[0, 0, 18], zone="B", color=s),
                               make_cube("sf_bl1", [ox + 1.1, 11.4, oz - 5.4], [ox + 2.5, 12.8, oz - 4.1], rot=[0, 0, 45], zone="C", color=a, emissive=True),
                               make_cube("sf_bl2", [ox - 2.5, 11.4, oz - 5.4], [ox - 1.1, 12.8, oz - 4.1], rot=[0, 0, 45], zone="C", color=a, emissive=True),
                           ])

    if arch == "v2_white_tiger":
        return quad_animal("wt", 4.0, 4.2, 8.0, 4.0, 4.4, 4.0, 3.8, 1.3, 1.3, 5.6, False,
                           extra_body=[make_cube("wt_sp", [ox - 0.5, 8.0, oz - 2.5], [ox + 0.5, 9.2, oz + 2.5], zone="C", color=a, emissive=True)],
                           extra_head=[make_cube("wt_ch", [ox - 2.8, 7.8, oz - 6.8], [ox + 2.8, 9.4, oz - 4.8], zone="B", color=s)], blush=False)

    if arch == "v2_crystal_golem":
        body = [
            make_cube("cg_t", [ox - 3.6, 3.6, oz - 2.4], [ox + 3.6, 10.0, oz + 2.4], zone="A", color=p),
            make_cube("cg_core", [ox - 1.4, 5.6, oz - 2.8], [ox + 1.4, 8.4, oz - 1.8], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            make_cube("cg_cr1", [ox + 2.2, 9.6, oz - 1.4], [ox + 4.2, 13.0, oz + 0.8], rot=[0, 0, -16], zone="C", color=a, emissive=True),
            make_cube("cg_cr2", [ox - 4.2, 9.6, oz - 1.4], [ox - 2.2, 13.0, oz + 0.8], rot=[0, 0, 16], zone="C", color=a, emissive=True),
        ]
        head = make_bone("comp_head", [ox, 10.0, oz], anim="comp_head", cubes=[
            make_cube("cg_h", [ox - 2.4, 9.8, oz - 2.4], [ox + 2.4, 13.4, oz + 2.0], zone="B", color=s),
            make_cube("cg_e1", [ox + 0.5, 11.2, oz - 2.7], [ox + 1.7, 12.2, oz - 2.2], zone="C", color=a, emissive=True),
            make_cube("cg_e2", [ox - 1.7, 11.2, oz - 2.7], [ox - 0.5, 12.2, oz - 2.2], zone="C", color=a, emissive=True),
        ])
        ll = make_bone("comp_mech_l", [ox + 1.8, 3.8, oz], anim="comp_mech_l", cubes=[make_cube("cg_ll", [ox + 0.6, 0.0, oz - 1.5], [ox + 3.0, 3.8, oz + 1.5], zone="B", color=s)])
        lr = make_bone("comp_mech_r", [ox - 1.8, 3.8, oz], anim="comp_mech_r", cubes=[make_cube("cg_lr", [ox - 3.0, 0.0, oz - 1.5], [ox - 0.6, 3.8, oz + 1.5], zone="B", color=s)])
        al = make_bone("comp_arm_l", [ox + 4.4, 9.0, oz], anim="comp_arm_l", cubes=[make_cube("cg_al", [ox + 3.4, 2.2, oz - 1.4], [ox + 5.6, 9.4, oz + 1.4], zone="A", color=p)])
        ar = make_bone("comp_arm_r", [ox - 4.4, 9.0, oz], anim="comp_arm_r", cubes=[make_cube("cg_ar", [ox - 5.6, 2.2, oz - 1.4], [ox - 3.4, 9.4, oz + 1.4], zone="A", color=p)])
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=[head, ll, lr, al, ar])]

    if arch == "v2_chibi_penguin":
        body = [
            make_cube("pg_b", [ox - 2.8, 1.2, oz - 2.6], [ox + 2.8, 8.2, oz + 2.6], zone="A", color=p),
            make_cube("pg_bel", [ox - 2.1, 1.6, oz - 2.9], [ox + 2.1, 6.6, oz - 1.8], zone="B", color=s),
            make_cube("pg_bk", [ox - 0.9, 5.2, oz - 4.0], [ox + 0.9, 6.2, oz - 2.5], zone="C", color=a),
            make_cube("pg_bow", [ox - 1.4, 4.2, oz - 3.3], [ox + 1.4, 5.1, oz - 2.6], zone="D_BLUSH", color=0xEF4444),
            make_cube("pg_fl", [ox + 2.6, 2.6, oz - 1.2], [ox + 3.8, 6.4, oz + 1.2], rot=[0, 0, -16], zone="A", color=p),
            make_cube("pg_fr", [ox - 3.8, 2.6, oz - 1.2], [ox - 2.6, 6.4, oz + 1.2], rot=[0, 0, 16], zone="A", color=p),
            make_cube("pg_el", [ox + 0.7, 6.1, oz - 2.95], [ox + 1.8, 7.2, oz - 2.5], zone="D_EYE", color=0x18181B),
            make_cube("pg_er", [ox - 1.8, 6.1, oz - 2.95], [ox - 0.7, 7.2, oz - 2.5], zone="D_EYE", color=0x18181B),
            make_cube("pg_bl1", [ox + 1.5, 5.3, oz - 2.9], [ox + 2.4, 5.9, oz - 2.5], zone="D_BLUSH", color=0xF472B6),
            make_cube("pg_bl2", [ox - 2.4, 5.3, oz - 2.9], [ox - 1.5, 5.9, oz - 2.5], zone="D_BLUSH", color=0xF472B6),
            make_cube("pg_ft1", [ox + 0.5, 0.0, oz - 2.4], [ox + 2.3, 1.3, oz + 1.0], zone="C", color=a),
            make_cube("pg_ft2", [ox - 2.3, 0.0, oz - 2.4], [ox - 0.5, 1.3, oz + 1.0], zone="C", color=a),
        ]
        return [make_bone("comp_hop", [ox, 0, oz], anim="comp_hop", cubes=body)]

    if arch == "v2_fluffy_samoyed":
        return quad_animal("sm", 4.0, 4.2, 7.0, 3.6, 4.2, 3.8, 3.5, 1.9, 1.3, 4.2, True,
                           extra_body=[make_cube("sm_rf", [ox - 2.5, 4.0, oz - 4.0], [ox + 2.5, 8.2, oz - 1.0], zone="A", color=p)])

    if arch == "v2_mint_slime_king":
        body = [
            make_cube("ms_out", [ox - 4.0, 0.0, oz - 4.0], [ox + 4.0, 7.4, oz + 4.0], zone="A", color=p, translucent=True),
            make_cube("ms_in", [ox - 2.8, 1.0, oz - 2.8], [ox + 2.8, 5.8, oz + 2.8], zone="B", color=s, emissive=True),
            make_cube("ms_el", [ox + 1.0, 3.6, oz - 4.3], [ox + 2.4, 5.0, oz - 3.8], zone="D_EYE", color=0x18181B),
            make_cube("ms_er", [ox - 2.4, 3.6, oz - 4.3], [ox - 1.0, 5.0, oz - 3.8], zone="D_EYE", color=0x18181B),
            make_cube("ms_bl1", [ox + 2.1, 2.5, oz - 4.25], [ox + 3.2, 3.2, oz - 3.8], zone="D_BLUSH", color=0xF472B6),
            make_cube("ms_bl2", [ox - 3.2, 2.5, oz - 4.25], [ox - 2.1, 3.2, oz - 3.8], zone="D_BLUSH", color=0xF472B6),
            make_cube("ms_cr", [ox - 2.2, 7.3, oz - 2.2], [ox + 2.2, 8.8, oz + 2.2], zone="D_GOLD", color=a, emissive=True),
            make_cube("ms_cs1", [ox - 2.2, 8.7, oz - 2.2], [ox - 1.0, 10.4, oz - 1.0], zone="D_GOLD", color=a, emissive=True),
            make_cube("ms_cs2", [ox + 1.0, 8.7, oz - 2.2], [ox + 2.2, 10.4, oz - 1.0], zone="D_GOLD", color=a, emissive=True),
            make_cube("ms_cs3", [ox - 0.6, 8.7, oz + 1.0], [ox + 0.6, 10.4, oz + 2.2], zone="D_GOLD", color=a, emissive=True),
        ]
        return [make_bone("comp_hop", [ox, 0, oz], anim="comp_hop", cubes=body)]

    if arch == "v2_caramel_pom":
        return quad_animal("pm", 3.8, 3.8, 4.8, 1.9, 4.0, 3.6, 3.2, 1.5, 1.2, 3.4, True,
                           extra_body=[make_cube("pm_flf", [ox - 2.3, 2.2, oz - 2.9], [ox + 2.3, 6.2, oz + 2.0], zone="A", color=p)])

    if arch == "v2_mecha_spider":
        body = [
            make_cube("ms_ch", [ox - 2.6, 2.6, oz - 3.2], [ox + 2.6, 5.4, oz + 3.2], zone="A", color=p),
            make_cube("ms_ab", [ox - 3.2, 2.8, oz + 2.8], [ox + 3.2, 6.4, oz + 7.4], zone="B", color=s),
            make_cube("ms_ey", [ox - 1.8, 3.4, oz - 3.6], [ox + 1.8, 4.8, oz - 3.0], zone="C", color=a, emissive=True),
        ]
        legs = []
        for side in (-1, 1):
            for idx, (lz, yaw_deg) in enumerate([(-2.2, 28), (0.2, 0), (2.6, -28)]):
                ltag = f"comp_spider_{'l' if side > 0 else 'r'}{idx}"
                lx0 = ox + side * 2.4
                lc = [
                    make_cube(f"ms_u_{side}_{idx}", _sx_at(ox, side, 2.2, 5.6), [3.8, 4.8], [oz + lz - 0.45, oz + lz + 0.45],
                              pivot=[lx0, 4.2, oz + lz], rot=[0, -side * yaw_deg, side * 14], zone="A", color=p),
                    make_cube(f"ms_d_{side}_{idx}", _sx_at(ox, side, 5.0, 6.0), [0.0, 4.6], [oz + lz - 0.4, oz + lz + 0.4],
                              pivot=[lx0, 4.2, oz + lz], rot=[0, -side * yaw_deg, 0], zone="C", color=a, emissive=True),
                ]
                legs.append(make_bone(ltag, [lx0, 4.2, oz + lz], anim=ltag, cubes=lc))
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs)]

    if arch == "v2_grizzly_cub":
        return quad_animal("gz", 4.0, 3.8, 6.4, 2.6, 4.0, 3.6, 3.4, 1.3, 1.3, 1.3, False,
                           extra_body=[make_cube("gz_pot", [ox - 1.6, 6.2, oz - 0.8], [ox + 1.6, 8.8, oz + 2.4], zone="C", color=a, emissive=True)])

    if arch == "v2_arctic_fox":
        return quad_animal("af", 2.9, 3.0, 6.6, 3.4, 3.4, 3.0, 3.2, 2.2, 1.3, 6.2, False,
                           extra_body=[make_cube("af_rf", [ox - 2.0, 4.0, oz - 3.6], [ox + 2.0, 6.8, oz - 1.2], zone="B", color=s)])

    if arch == "v2_moon_cat":
        return quad_animal("mc", 2.8, 3.0, 5.8, 3.0, 3.4, 3.0, 3.0, 1.8, 1.2, 5.0, True,
                           extra_head=[
                               make_cube("mc_br", [ox - 2.4, 8.2, oz - 6.8], [ox + 2.4, 8.8, oz - 2.2], zone="B", color=s),
                               make_cube("mc_cn", [ox - 1.4, 8.8, oz - 5.8], [ox + 1.4, 11.4, oz - 3.2], rot=[14, 0, -10], zone="B", color=s),
                               make_cube("mc_bd", [ox - 1.5, 8.7, oz - 5.9], [ox + 1.5, 9.4, oz - 3.1], zone="C", color=a, emissive=True),
                           ])

    if arch == "v2_golden_duckling":
        body = [
            make_cube("dk_b", [ox - 2.6, 1.4, oz - 3.0], [ox + 2.6, 5.8, oz + 3.4], zone="A", color=p),
            make_cube("dk_h", [ox - 2.2, 5.4, oz - 3.6], [ox + 2.2, 8.8, oz + 0.6], zone="A", color=p),
            make_cube("dk_bk", [ox - 1.4, 5.8, oz - 5.2], [ox + 1.4, 6.8, oz - 3.4], zone="B", color=s),
            make_cube("dk_el", [ox + 0.7, 7.0, oz - 3.9], [ox + 1.8, 8.1, oz - 3.45], zone="D_EYE", color=0x18181B),
            make_cube("dk_er", [ox - 1.8, 7.0, oz - 3.9], [ox - 0.7, 8.1, oz - 3.45], zone="D_EYE", color=0x18181B),
            make_cube("dk_bl1", [ox + 1.4, 6.2, oz - 3.85], [ox + 2.2, 6.8, oz - 3.45], zone="D_BLUSH", color=0xF472B6),
            make_cube("dk_bl2", [ox - 2.2, 6.2, oz - 3.85], [ox - 1.4, 6.8, oz - 3.45], zone="D_BLUSH", color=0xF472B6),
            make_cube("dk_lf", [ox - 1.2, 8.7, oz - 2.4], [ox + 1.2, 9.8, oz - 0.4], rot=[0, 45, 0], zone="C", color=a),
            make_cube("dk_wl", [ox + 2.4, 2.4, oz - 1.4], [ox + 3.4, 5.0, oz + 2.0], zone="A", color=p),
            make_cube("dk_wr", [ox - 3.4, 2.4, oz - 1.4], [ox - 2.4, 5.0, oz + 2.0], zone="A", color=p),
            make_cube("dk_f1", [ox + 0.5, 0.0, oz - 2.2], [ox + 2.2, 1.5, oz + 1.0], zone="B", color=s),
            make_cube("dk_f2", [ox - 2.2, 0.0, oz - 2.2], [ox - 0.5, 1.5, oz + 1.0], zone="B", color=s),
        ]
        return [make_bone("comp_hop", [ox, 0, oz], anim="comp_hop", cubes=body)]

    if arch == "v2_chibi_tortoise":
        body = [
            make_cube("tt_sh", [ox - 3.8, 1.6, oz - 4.2], [ox + 3.8, 5.6, oz + 4.2], zone="A", color=p),
            make_cube("tt_top", [ox - 2.8, 5.4, oz - 3.0], [ox + 2.8, 6.8, oz + 3.0], zone="B", color=s),
            make_cube("tt_g1", [ox - 1.0, 6.6, oz - 1.2], [ox + 1.0, 9.4, oz + 0.8], rot=[10, 45, 0], zone="C", color=a, emissive=True),
            make_cube("tt_g2", [ox + 1.4, 6.2, oz + 0.4], [ox + 2.6, 8.4, oz + 1.8], rot=[-12, 20, -14], zone="C", color=a, emissive=True),
            make_cube("tt_g3", [ox - 2.6, 6.2, oz + 0.4], [ox - 1.4, 8.4, oz + 1.8], rot=[-12, -20, 14], zone="C", color=a, emissive=True),
        ]
        head = make_bone("comp_head", [ox, 2.8, oz - 4.0], anim="comp_head", cubes=[
            make_cube("tt_hd", [ox - 1.8, 1.8, oz - 6.8], [ox + 1.8, 4.6, oz - 3.8], zone="B", color=s),
            make_cube("tt_el", [ox + 0.5, 3.1, oz - 7.1], [ox + 1.5, 4.1, oz - 6.6], zone="D_EYE", color=0x18181B),
            make_cube("tt_er", [ox - 1.5, 3.1, oz - 7.1], [ox - 0.5, 4.1, oz - 6.6], zone="D_EYE", color=0x18181B),
        ])
        legs = []
        for ltag, lx, lz in [("comp_leg_fl", ox + 2.8, oz - 2.8), ("comp_leg_fr", ox - 2.8, oz - 2.8), ("comp_leg_bl", ox + 2.8, oz + 2.8), ("comp_leg_br", ox - 2.8, oz + 2.8)]:
            legs.append(make_bone(ltag, [lx, 1.8, lz], anim=ltag, cubes=[make_cube(f"tt_{ltag}", [lx - 1.0, 0.0, lz - 1.0], [lx + 1.0, 2.0, lz + 1.0], zone="B", color=s)]))
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + [head])]

    if arch == "v2_cozy_hedgehog":
        body = [
            make_cube("hh_q1", [ox - 3.2, 1.2, oz - 2.0], [ox + 3.2, 5.8, oz + 4.0], zone="A", color=p),
            make_cube("hh_q2", [ox - 2.6, 5.4, oz - 1.2], [ox + 2.6, 6.8, oz + 3.2], zone="A", color=p),
            make_cube("hh_fc", [ox - 2.4, 1.4, oz - 4.4], [ox + 2.4, 4.8, oz - 1.8], zone="B", color=s),
            make_cube("hh_ns", [ox - 0.6, 2.2, oz - 5.2], [ox + 0.6, 3.2, oz - 4.2], zone="C", color=a),
            make_cube("hh_el", [ox + 0.7, 3.2, oz - 4.65], [ox + 1.7, 4.2, oz - 4.2], zone="D_EYE", color=0x18181B),
            make_cube("hh_er", [ox - 1.7, 3.2, oz - 4.65], [ox - 0.7, 4.2, oz - 4.2], zone="D_EYE", color=0x18181B),
            make_cube("hh_bl1", [ox + 1.4, 2.3, oz - 4.6], [ox + 2.2, 2.9, oz - 4.2], zone="D_BLUSH", color=a),
            make_cube("hh_bl2", [ox - 2.2, 2.3, oz - 4.6], [ox - 1.4, 2.9, oz - 4.2], zone="D_BLUSH", color=a),
            make_cube("hh_f1", [ox + 0.8, 0.0, oz - 2.4], [ox + 2.2, 1.4, oz - 0.8], zone="B", color=s),
            make_cube("hh_f2", [ox - 2.2, 0.0, oz - 2.4], [ox - 0.8, 1.4, oz - 0.8], zone="B", color=s),
            make_cube("hh_f3", [ox + 0.8, 0.0, oz + 1.2], [ox + 2.2, 1.4, oz + 2.8], zone="B", color=s),
            make_cube("hh_f4", [ox - 2.2, 0.0, oz + 1.2], [ox - 0.8, 1.4, oz + 2.8], zone="B", color=s),
        ]
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body)]

    if arch == "v2_mini_alpaca":
        body = [
            make_cube("al_bd", [ox - 3.2, 4.4, oz - 3.6], [ox + 3.2, 8.6, oz + 4.0], zone="A", color=p),
            make_cube("al_bl", [ox - 3.45, 5.2, oz - 1.8], [ox + 3.45, 8.9, oz + 2.4], zone="C", color=a),
            make_cube("al_nk", [ox - 1.8, 7.8, oz - 4.4], [ox + 1.8, 12.6, oz - 1.6], zone="A", color=p),
        ]
        head = make_bone("comp_head", [ox, 12.2, oz - 3.0], anim="comp_head", cubes=[
            make_cube("al_hd", [ox - 2.2, 11.8, oz - 6.0], [ox + 2.2, 15.0, oz - 1.6], zone="A", color=p),
            make_cube("al_sn", [ox - 1.3, 12.0, oz - 7.4], [ox + 1.3, 13.8, oz - 5.8], zone="B", color=s),
            make_cube("al_el", [ox + 0.8, 14.8, oz - 3.6], [ox + 2.0, 17.6, oz - 2.4], zone="A", color=p),
            make_cube("al_er", [ox - 2.0, 14.8, oz - 3.6], [ox - 0.8, 17.6, oz - 2.4], zone="A", color=p),
            make_cube("al_eyl", [ox + 0.6, 13.4, oz - 6.3], [ox + 1.6, 14.4, oz - 5.8], zone="D_EYE", color=0x18181B),
            make_cube("al_eyr", [ox - 1.6, 13.4, oz - 6.3], [ox - 0.6, 14.4, oz - 5.8], zone="D_EYE", color=0x18181B),
        ])
        legs = []
        for ltag, lx, lz in [("comp_leg_fl", ox + 2.0, oz - 2.4), ("comp_leg_fr", ox - 2.0, oz - 2.4), ("comp_leg_bl", ox + 2.0, oz + 2.6), ("comp_leg_br", ox - 2.0, oz + 2.6)]:
            legs.append(make_bone(ltag, [lx, 4.6, lz], anim=ltag, cubes=[make_cube(f"al_{ltag}", [lx - 0.9, 0.0, lz - 0.9], [lx + 0.9, 4.8, lz + 0.9], zone="A", color=p)]))
        tail = make_bone("comp_tail", [ox, 7.4, oz + 4.0], anim="comp_tail", cubes=[make_cube("al_tl", [ox - 1.1, 6.2, oz + 3.8], [ox + 1.1, 8.6, oz + 5.6], zone="B", color=s)])
        return [make_bone("comp_root", [ox, 0, oz], anim="comp_root", cubes=body, children=legs + [head, tail])]

    return None

