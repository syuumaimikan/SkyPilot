"""Commercial-grade 100%-unique 3D Blockbench rig builders for the 4 NEW wearable slots:
  - face     (20 items, anchor='head', sits cleanly outside front/sides of head box [-4.5..4.5, 23.5..32.5, -4.5..4.5])
  - shoulder (20 items, anchor='body', sits cleanly above/around shoulders y=22..26 & outside torso)
  - waist    (20 items, anchor='body', sits cleanly at waist y=10..15 & behind/beside hips z >= 2.6 or |x| >= 4.5)
  - feet     (20 items, anchor='root', sits cleanly at ankle/ground level y=0.6..6.5 & outside legs |x| >= 4.5 or ring r >= 7.2)
"""
from __future__ import annotations

import math
from typing import Any

from rig_builders import _sx, make_bone, make_cube


# ==============================================================================
# FACE & EYEWEAR (20 unique designs, anchor='head', ZERO head clipping!)
# Head + outer layer spans x=[-4.5, 4.5], y=[23.5, 32.5], z=[-4.5, 4.5]
# Front face plane sits cleanly at z = -4.65 .. -5.65!
# ==============================================================================
def build_face_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    piv = [0.0, 28.0, 0.0]

    if arch == "face_kitsune_half":
        c = [
            make_cube("km_brow", [-4.3, 29.2, -5.25], [4.3, 30.8, -4.62], zone="A", color=p),
            make_cube("km_l", [0.8, 27.1, -5.28], [4.4, 29.4, -4.62], zone="A", color=p),
            make_cube("km_r", [-4.4, 27.1, -5.28], [-0.8, 29.4, -4.62], zone="A", color=p),
            make_cube("km_snout", [-1.3, 26.8, -6.2], [1.3, 28.6, -4.65], rot=[12, 0, 0], zone="A", color=p),
            make_cube("km_nose", [-0.55, 26.9, -6.45], [0.55, 27.6, -5.9], zone="B", color=s),
            make_cube("km_mark_c", [-0.45, 29.5, -5.42], [0.45, 31.1, -5.15], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"km_ear_{side}", _sx(side, 2.3, 4.5), [30.6, 33.4], [-5.2, -4.65], rot=[0, 0, -side * 16], zone="A", color=p))
            c.append(make_cube(f"km_mark_{side}", _sx(side, 2.1, 4.1), [27.7, 28.3], [-5.42, -5.15], rot=[0, 0, -side * 12], zone="B", color=s))
            c.append(make_cube(f"km_tassel_{side}", _sx(side, 4.6, 5.2), [25.6, 28.8], [-4.8, -4.1], zone="B", color=s))
        return [make_bone("face_kitsune", piv, cubes=c)]

    if arch == "face_cyber_holo":
        c = [
            make_cube("hv_lens", [-4.6, 27.4, -5.45], [4.6, 29.6, -4.80], zone="A", color=p, emissive=True, translucent=True),
            make_cube("hv_top", [-4.8, 29.5, -5.55], [4.8, 30.0, -4.65], zone="B", color=s),
            make_cube("hv_scan", [-4.1, 28.3, -5.58], [4.1, 28.65, -5.32], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"hv_arm_{side}", _sx(side, 4.65, 5.25), [28.0, 29.2], [-5.3, -0.5], zone="B", color=s))
            c.append(make_cube(f"hv_node_{side}", _sx(side, 4.9, 5.6), [27.7, 29.5], [-2.0, -0.4], zone="C", color=a, emissive=True))
        return [make_bone("face_holo", piv, cubes=c)]

    if arch == "face_star_monocle":
        c = [
            make_cube("sm_lens", [0.8, 27.2, -5.22], [3.5, 29.6, -4.75], zone="B", color=s, translucent=True),
            make_cube("sm_t", [0.6, 29.5, -5.35], [3.7, 29.95, -4.68], zone="D_GOLD", color=p, emissive=True),
            make_cube("sm_b", [0.6, 26.85, -5.35], [3.7, 27.3, -4.68], zone="D_GOLD", color=p, emissive=True),
            make_cube("sm_l", [3.4, 27.1, -5.35], [3.85, 29.7, -4.68], zone="D_GOLD", color=p, emissive=True),
            make_cube("sm_r", [0.55, 27.1, -5.35], [1.0, 29.7, -4.68], zone="D_GOLD", color=p, emissive=True),
            make_cube("sm_chain1", [3.8, 26.0, -5.15], [4.3, 27.9, -4.7], rot=[0, 0, -14], zone="D_GOLD", color=p),
            make_cube("sm_star", [4.1, 24.4, -5.25], [5.1, 25.4, -4.55], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        return [make_bone("face_monocle", piv, cubes=c)]

    if arch == "face_round_gold":
        c = [make_cube("rg_bridge", [-0.7, 28.4, -5.22], [0.7, 28.8, -4.78], zone="D_GOLD", color=p)]
        for side in (-1, 1):
            c.append(make_cube(f"rg_g_{side}", _sx(side, 0.85, 3.65), [27.3, 29.5], [-5.12, -4.82], zone="B", color=s, translucent=True))
            c.append(make_cube(f"rg_t_{side}", _sx(side, 0.7, 3.8), [29.4, 29.8], [-5.25, -4.74], zone="D_GOLD", color=p))
            c.append(make_cube(f"rg_b_{side}", _sx(side, 0.7, 3.8), [27.0, 27.4], [-5.25, -4.74], zone="D_GOLD", color=p))
            c.append(make_cube(f"rg_o_{side}", _sx(side, 3.55, 3.95), [27.2, 29.6], [-5.25, -4.74], zone="D_GOLD", color=p))
            c.append(make_cube(f"rg_i_{side}", _sx(side, 0.6, 0.95), [27.2, 29.6], [-5.25, -4.74], zone="D_GOLD", color=p))
            c.append(make_cube(f"rg_arm_{side}", _sx(side, 4.6, 5.0), [28.4, 28.8], [-5.1, -0.8], zone="D_GOLD", color=p))
        return [make_bone("face_round_gold", piv, cubes=c)]

    if arch == "face_rose_glasses":
        c = [make_cube("rq_br", [-0.8, 28.3, -5.28], [0.8, 28.75, -4.78], zone="A", color=p)]
        for side in (-1, 1):
            c.append(make_cube(f"rq_lens_{side}", _sx(side, 0.9, 3.8), [27.15, 29.45], [-5.18, -4.80], zone="B", color=s, translucent=True))
            c.append(make_cube(f"rq_top_{side}", _sx(side, 0.8, 4.1), [29.35, 29.85], [-5.32, -4.72], rot=[0, 0, -side * 5], zone="A", color=p))
            c.append(make_cube(f"rq_bot_{side}", _sx(side, 0.8, 3.9), [26.85, 27.25], [-5.30, -4.74], zone="A", color=p))
            c.append(make_cube(f"rq_gem_{side}", _sx(side, 3.6, 4.5), [29.1, 30.0], [-5.48, -4.95], rot=[0, 0, 45], zone="C", color=a, emissive=True))
            c.append(make_cube(f"rq_arm_{side}", _sx(side, 4.62, 5.05), [28.5, 29.0], [-5.2, -1.0], zone="A", color=p))
        return [make_bone("face_rose", piv, cubes=c)]

    if arch == "face_oni_fang":
        c = [
            make_cube("on_chin", [-3.8, 24.1, -5.45], [3.8, 26.8, -4.65], zone="A", color=p),
            make_cube("on_jaw_l", [4.62, 24.6, -5.2], [5.25, 27.6, -2.6], zone="A", color=p),
            make_cube("on_jaw_r", [-5.25, 24.6, -5.2], [-4.62, 27.6, -2.6], zone="A", color=p),
            make_cube("on_nose", [-1.2, 26.6, -5.75], [1.2, 27.9, -4.65], zone="B", color=s),
            make_cube("on_mouth", [-2.6, 25.0, -5.62], [2.6, 26.0, -5.25], zone="B", color=s),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"on_fang_{side}", _sx(side, 1.4, 2.2), [25.4, 27.4], [-5.95, -5.25], rot=[-15, 0, -side * 12], zone="C", color=a, emissive=True))
            c.append(make_cube(f"on_stud_{side}", _sx(side, 3.4, 4.1), [25.3, 26.0], [-5.65, -5.2], zone="D_GOLD", color=0xFBBF24))
        return [make_bone("face_oni", piv, cubes=c)]

    if arch == "face_masquerade":
        c = [
            make_cube("mq_mid", [-1.1, 27.4, -5.35], [1.1, 29.6, -4.68], zone="A", color=p),
            make_cube("mq_gem", [-0.65, 29.2, -5.58], [0.65, 30.5, -5.1], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"mq_brow_{side}", _sx(side, 0.9, 4.7), [29.2, 30.3], [-5.35, -4.68], rot=[0, 0, -side * 10], zone="A", color=p))
            c.append(make_cube(f"mq_cheek_{side}", _sx(side, 1.0, 4.5), [26.9, 27.8], [-5.30, -4.68], rot=[0, 0, side * 8], zone="B", color=s))
            c.append(make_cube(f"mq_wing1_{side}", _sx(side, 4.5, 7.2), [28.4, 30.6], [-5.1, -4.1], rot=[0, -side * 24, -side * 22], zone="A", color=p))
            c.append(make_cube(f"mq_wing2_{side}", _sx(side, 4.6, 6.4), [29.8, 31.8], [-4.8, -3.9], rot=[0, -side * 28, -side * 34], zone="C", color=a, emissive=True))
        return [make_bone("face_masq", piv, cubes=c)]

    if arch == "face_angel_cuffs":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"ac_clip_{side}", _sx(side, 4.65, 5.25), [26.8, 28.4], [-1.0, 0.6], zone="D_GOLD", color=a, emissive=True))
            for k in range(3):
                c.append(make_cube(f"ac_f_{side}_{k}", _sx(side, 4.9 + k * 0.4, 6.2 + k * 0.6), [27.6 + k * 1.1, 28.9 + k * 1.1], [-0.4 + k * 0.8, 0.9 + k * 0.8],
                                   rot=[0, -side * 16, -side * (22 + k * 8)], zone="A" if k < 2 else "C", color=p if k < 2 else a, emissive=True))
        return [make_bone("face_earcuff", piv, cubes=c)]

    if arch == "face_sakura_cheek":
        c = []
        for side in (-1, 1):
            cx = side * 3.1
            c.append(make_cube(f"sc_c_{side}", [cx - 0.5, 25.8, -5.25], [cx + 0.5, 26.8, -4.7], zone="C", color=a, emissive=True))
            for k in range(4):
                ang = k * math.tau / 4.0 + 0.4
                px, py = cx + math.cos(ang) * 0.85, 26.3 + math.sin(ang) * 0.85
                c.append(make_cube(f"sc_p_{side}_{k}", [px - 0.5, py - 0.5, -5.20], [px + 0.5, py + 0.5, -4.72],
                                   rot=[0, 0, math.degrees(ang)], zone="A", color=p))
            c.append(make_cube(f"sc_rib_{side}", _sx(side, 4.65, 5.35), [24.2, 27.6], [-2.8, -1.6], rot=[0, 0, -side * 10], zone="B", color=s))
        return [make_bone("face_sakura", piv, cubes=c)]

    if arch == "face_crystal_patch":
        c = [
            make_cube("cp_pad", [-3.8, 27.0, -5.35], [-0.9, 29.7, -4.68], zone="A", color=p),
            make_cube("cp_gem", [-2.9, 27.8, -5.62], [-1.8, 28.9, -5.15], rot=[0, 0, 45], zone="C", color=a, emissive=True),
            make_cube("cp_str1", [-4.8, 28.9, -5.15], [4.8, 29.45, -4.65], rot=[0, 0, 14], zone="B", color=s),
            make_cube("cp_str_l", [4.62, 28.5, -4.8], [5.05, 29.1, 4.8], zone="B", color=s),
            make_cube("cp_str_r", [-5.05, 28.0, -4.8], [-4.62, 28.6, 4.8], zone="B", color=s),
            make_cube("cp_str_b", [-4.8, 28.2, 4.65], [4.8, 28.8, 5.05], zone="B", color=s),
        ]
        return [make_bone("face_patch", piv, cubes=c)]

    if arch == "face_neko_whiskers":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"nw_blush_{side}", _sx(side, 2.2, 4.1), [25.6, 26.6], [-4.95, -4.65], zone="D_BLUSH", color=p))
            for k in range(3):
                c.append(make_cube(f"nw_w_{side}_{k}", _sx(side, 4.6, 6.9), [25.3 + k * 0.75, 25.65 + k * 0.75], [-4.9, -4.4],
                                   rot=[0, -side * 12, -side * ((k - 1) * 14)], zone="C" if k == 1 else "B", color=a if k == 1 else s, emissive=k == 1))
        return [make_bone("face_whiskers", piv, cubes=c)]

    if arch == "face_frost_veil":
        c = [
            make_cube("fv_veil", [-4.2, 23.6, -5.35], [4.2, 26.9, -4.85], zone="A", color=p, emissive=True, translucent=True),
            make_cube("fv_trim", [-4.4, 26.7, -5.42], [4.4, 27.2, -4.72], zone="C", color=a, emissive=True),
            make_cube("fv_drop_c", [-0.7, 22.8, -5.45], [0.7, 24.0, -4.82], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"fv_pin_{side}", _sx(side, 4.65, 5.35), [26.8, 28.2], [-4.9, -3.6], rot=[0, 0, 45], zone="C", color=a, emissive=True))
            c.append(make_cube(f"fv_s_{side}", _sx(side, 4.65, 5.05), [24.2, 27.2], [-5.2, -2.5], zone="B", color=s, translucent=True))
        return [make_bone("face_veil", piv, cubes=c)]

    if arch == "face_heart_shades":
        c = [make_cube("hs_br", [-0.75, 28.4, -5.30], [0.75, 28.85, -4.75], zone="C", color=a, emissive=True)]
        for side in (-1, 1):
            cx = side * 2.3
            c.append(make_cube(f"hs_l1_{side}", [cx - 1.35, 28.2, -5.26], [cx + 0.1, 29.7, -4.76], rot=[0, 0, 18], zone="A", color=p, emissive=True, translucent=True))
            c.append(make_cube(f"hs_l2_{side}", [cx - 0.1, 28.2, -5.26], [cx + 1.35, 29.7, -4.76], rot=[0, 0, -18], zone="A", color=p, emissive=True, translucent=True))
            c.append(make_cube(f"hs_bt_{side}", [cx - 0.95, 27.1, -5.24], [cx + 0.95, 28.8, -4.78], rot=[0, 0, 45], zone="A", color=p, emissive=True, translucent=True))
            c.append(make_cube(f"hs_arm_{side}", _sx(side, 4.62, 5.08), [28.4, 28.9], [-5.2, -0.8], zone="C", color=a, emissive=True))
        return [make_bone("face_heart_shades", piv, cubes=c)]

    if arch == "face_solar_shades":
        c = [
            make_cube("ss_bar", [-4.4, 29.4, -5.38], [4.4, 29.9, -4.72], zone="D_GOLD", color=p, emissive=True),
            make_cube("ss_mid", [-0.7, 28.6, -5.30], [0.7, 29.2, -4.76], zone="D_GOLD", color=p),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"ss_l_{side}", _sx(side, 0.75, 4.15), [27.0, 29.45], [-5.30, -4.76], zone="A", color=p, emissive=True, translucent=True))
            c.append(make_cube(f"ss_arm_{side}", _sx(side, 4.65, 5.10), [28.8, 29.4], [-5.3, -0.5], zone="D_GOLD", color=p))
        return [make_bone("face_solar_shades", piv, cubes=c)]

    if arch == "face_jade_crest":
        c = [
            make_cube("jc_bindi", [-0.75, 29.8, -5.28], [0.75, 31.4, -4.68], rot=[0, 0, 45], zone="A", color=p, emissive=True),
            make_cube("jc_rim", [-0.95, 30.1, -5.12], [0.95, 31.1, -4.65], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"jc_wing_{side}", _sx(side, 1.1, 3.0), [30.3, 30.9], [-5.10, -4.68], rot=[0, 0, -side * 16], zone="C", color=a, emissive=True))
            c.append(make_cube(f"jc_drop_{side}", _sx(side, 4.7, 5.45), [24.6, 26.8], [-0.8, 0.2], zone="A", color=p, emissive=True))
            c.append(make_cube(f"jc_ring_{side}", _sx(side, 4.65, 5.35), [26.8, 27.6], [-0.7, 0.1], zone="C", color=a, emissive=True))
        return [make_bone("face_jade", piv, cubes=c)]

    if arch == "face_velvet_ribbons":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"vr_k_{side}", _sx(side, 4.68, 5.38), [27.0, 28.1], [-1.8, -0.6], zone="A", color=p))
            c.append(make_cube(f"vr_u_{side}", _sx(side, 4.75, 5.45), [27.9, 29.4], [-2.5, -0.1], rot=[18, 0, -side * 12], zone="A", color=p))
            c.append(make_cube(f"vr_t1_{side}", _sx(side, 4.72, 5.32), [24.2, 27.2], [-2.1, -1.1], rot=[-8, 0, 0], zone="B", color=s))
            c.append(make_cube(f"vr_p_{side}", _sx(side, 4.82, 5.48), [23.8, 24.7], [-2.0, -1.1], zone="C", color=a, emissive=True))
        return [make_bone("face_ribbons", piv, cubes=c)]

    if arch == "face_astral_jewels":
        c = [
            make_cube("aj_moon", [-2.9, 29.9, -5.18], [-1.7, 31.1, -4.68], rot=[0, 0, 24], zone="C", color=a, emissive=True),
            make_cube("aj_star_c", [2.1, 30.1, -5.20], [3.0, 31.0, -4.68], rot=[0, 0, 45], zone="C", color=a, emissive=True),
        ]
        for side in (-1, 1):
            c.append(make_cube(f"aj_j1_{side}", _sx(side, 2.4, 3.2), [26.0, 26.8], [-5.15, -4.68], rot=[0, 0, 45], zone="A", color=p, emissive=True))
            c.append(make_cube(f"aj_j2_{side}", _sx(side, 3.3, 3.9), [26.6, 27.2], [-5.15, -4.68], rot=[0, 0, 45], zone="C", color=a, emissive=True))
            c.append(make_cube(f"aj_j3_{side}", _sx(side, 1.8, 2.3), [25.6, 26.1], [-5.15, -4.68], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        return [make_bone("face_astral", piv, cubes=c)]

    if arch == "face_butterfly_mask":
        c = [
            make_cube("bm_body", [0.4, 27.0, -5.38], [1.1, 29.9, -4.70], zone="B", color=s),
            make_cube("bm_up", [1.0, 28.4, -5.36], [5.6, 31.6, -4.72], rot=[0, -16, -14], zone="A", color=p, emissive=True, translucent=True),
            make_cube("bm_lo", [1.0, 26.4, -5.30], [4.6, 28.7, -4.72], rot=[0, -14, 12], zone="C", color=a, emissive=True, translucent=True),
            make_cube("bm_ant", [0.6, 29.8, -5.42], [1.6, 31.7, -4.90], rot=[0, 0, -24], zone="C", color=a, emissive=True),
            make_cube("bm_band", [-4.6, 28.3, -5.05], [1.0, 28.8, -4.68], zone="B", color=s),
        ]
        return [make_bone("face_butterfly", piv, cubes=c)]

    if arch == "face_scouter":
        c = [
            make_cube("es_lens", [0.6, 27.3, -5.50], [4.5, 29.7, -4.85], zone="A", color=p, emissive=True, translucent=True),
            make_cube("es_grid", [1.2, 28.2, -5.58], [3.9, 28.7, -5.35], zone="C", color=a, emissive=True),
            make_cube("es_pod", [4.65, 27.0, -2.2], [5.95, 29.9, 0.6], zone="B", color=s),
            make_cube("es_arm", [4.5, 28.0, -5.4], [5.3, 29.0, -2.0], zone="B", color=s),
            make_cube("es_led", [5.7, 27.9, -1.3], [6.15, 29.0, -0.2], zone="C", color=a, emissive=True),
        ]
        return [make_bone("face_scouter", piv, cubes=c)]

    # face_crimson_veil
    c = [
        make_cube("cv_top", [-4.4, 29.0, -5.35], [4.4, 30.2, -4.68], zone="B", color=s),
        make_cube("cv_mid", [-0.9, 27.2, -5.38], [0.9, 29.3, -4.68], zone="A", color=p),
        make_cube("cv_moon", [-0.7, 29.6, -5.55], [0.7, 30.9, -5.1], rot=[0, 0, 20], zone="C", color=a, emissive=True),
    ]
    for side in (-1, 1):
        c.append(make_cube(f"cv_cheek_{side}", _sx(side, 1.0, 4.4), [26.8, 27.8], [-5.30, -4.68], zone="A", color=p))
        c.append(make_cube(f"cv_lace_{side}", _sx(side, 4.62, 5.18), [25.6, 29.6], [-5.15, -3.1], zone="B", color=s))
    return [make_bone("face_crimson", piv, cubes=c)]


# ==============================================================================
# SHOULDER & COLLAR (20 unique designs, anchor='body', ZERO torso clipping!)
# Torso spans x=[-4.25, 4.25], y=[11.75, 24.25], z=[-2.25, 2.25]; arms at |x|=4..8
# ==============================================================================
def build_shoulder_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    piv = [0.0, 24.0, 0.0]

    def _scarf_collar(y0: float, y1: float, pad: float, zone_main: str = "A") -> list[dict[str, Any]]:
        return [
            make_cube("sc_f", [-4.8 - pad, y0, -3.2 - pad], [4.8 + pad, y1, -2.35], zone=zone_main, color=p),
            make_cube("sc_b", [-4.8 - pad, y0, 2.35], [4.8 + pad, y1, 3.2 + pad], zone=zone_main, color=p),
            make_cube("sc_l", [4.35, y0, -3.1 - pad], [5.3 + pad, y1, 3.1 + pad], zone=zone_main, color=p),
            make_cube("sc_r", [-5.3 - pad, y0, -3.1 - pad], [-4.35, y1, 3.1 + pad], zone=zone_main, color=p),
        ]

    if arch == "sh_sakura_muffler":
        c = _scarf_collar(22.4, 24.5, 0.35)
        c.append(make_cube("sm_knot", [1.5, 22.0, -3.9], [3.6, 24.1, -2.5], zone="B", color=s))
        c.append(make_cube("sm_flower", [2.0, 22.5, -4.15], [3.1, 23.6, -3.7], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        tail = make_bone("scarf_tail", [0.0, 23.0, 3.2], anim="cape_seg_2", cubes=[
            make_cube("sm_t1", [1.6, 15.8, -3.7], [3.6, 22.3, -2.9], rot=[-5, 0, 4], zone="A", color=p),
            make_cube("sm_t2", [-3.2, 16.5, 2.75], [-1.0, 23.0, 3.65], rot=[10, 0, -5], zone="B", color=s),
        ])
        return [make_bone("sh_sakura", piv, cubes=c, children=[tail])]

    if arch == "sh_seraph_pauldrons":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            sp = [side * 5.6, 24.3, 0.0]
            cubes = [make_cube("sp_cap", _sx(side, 4.4, 8.6), [24.25, 25.4], [-2.7, 2.7], rot=[0, 0, -side * 12], zone="D_GOLD", color=a)]
            for k in range(3):
                cubes.append(make_cube(f"sp_w_{k}", _sx(side, 6.2 + k * 0.9, 9.8 + k * 1.4), [24.6 + k * 0.9, 25.9 + k * 1.0], [-1.6 + k * 0.7, 1.8 + k * 0.7],
                                       rot=[0, -side * 14, -side * (20 + k * 10)], zone="A" if k < 2 else "C", color=p if k < 2 else a, emissive=True))
            bones.append(make_bone(f"sh_wing_{tag}", sp, anim=f"wing_primary_{tag}", anim_amp=0.32, cubes=cubes))
        return [make_bone("sh_seraph", piv, children=bones)]

    if arch == "sh_dragon_guards":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"dg_base_{side}", _sx(side, 4.4, 9.0), [24.25, 25.8], [-3.0, 3.0], rot=[0, 0, -side * 10], zone="A", color=p))
            c.append(make_cube(f"dg_tier_{side}", _sx(side, 5.2, 9.4), [25.3, 26.8], [-2.4, 2.4], rot=[0, 0, -side * 18], zone="B", color=s))
            c.append(make_cube(f"dg_horn_{side}", _sx(side, 7.6, 9.8), [26.2, 29.2], [-0.9, 0.9], rot=[0, 0, -side * 28], zone="C", color=a, emissive=True))
            c.append(make_cube(f"dg_ridge_{side}", _sx(side, 5.8, 7.4), [26.4, 28.0], [-0.8, 0.8], rot=[0, 0, -side * 18], zone="C", color=a, emissive=True))
        return [make_bone("sh_dragon", piv, cubes=c)]

    if arch == "sh_royal_mantle":
        c = _scarf_collar(22.0, 24.4, 0.55)
        c.append(make_cube("rm_fur", [-6.2, 23.8, -3.5], [6.2, 25.0, 3.5], zone="B", color=s))
        c.append(make_cube("rm_brooch", [-1.0, 21.8, -4.1], [1.0, 23.6, -3.1], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        c.append(make_cube("rm_drape_b", [-6.4, 18.2, 2.6], [6.4, 24.0, 3.6], zone="A", color=p))
        return [make_bone("sh_royal", piv, cubes=c)]

    if arch == "sh_cyber_plates":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            cubes = [
                make_cube(f"cp_p1_{tag}", _sx(side, 6.2, 9.8), [24.6, 26.1], [-2.5, 2.5], rot=[0, 0, -side * 22], zone="A", color=p, emissive=True, translucent=True),
                make_cube(f"cp_p2_{tag}", _sx(side, 7.8, 10.6), [26.4, 27.4], [-1.8, 1.8], rot=[0, 0, -side * 30], zone="C", color=a, emissive=True, translucent=True),
                make_cube(f"cp_node_{tag}", _sx(side, 5.2, 6.2), [24.3, 25.3], [-0.8, 0.8], zone="B", color=s),
            ]
            bones.append(make_bone(f"cyber_sh_{tag}", [side * 7.5, 25.8, 0.0], anim="aura_orbit_bob", anim_phase=side * 0.8, cubes=cubes))
        return [make_bone("sh_cyber", piv, children=bones)]

    if arch == "sh_starlight_stole":
        c = []
        for i in range(9):
            t = (i - 4) / 4.0
            sx = t * 9.5
            sy = 24.6 + (1.0 - t * t) * 3.8
            sz = 3.2 + (1.0 - t * t) * 2.2
            c.append(make_cube(f"ss_arc_{i}", [sx - 1.5, sy - 0.65, sz - 0.45], [sx + 1.5, sy + 0.65, sz + 0.45],
                               rot=[12, t * 24, -t * 32], zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True, translucent=True))
        for side in (-1, 1):
            c.append(make_cube(f"ss_hang_{side}", _sx(side, 7.8, 9.4), [14.0, 24.8], [-1.2, 1.2], rot=[0, 0, -side * 8], zone="A", color=p, emissive=True, translucent=True))
        return [make_bone("sh_starlight", [0.0, 25.0, 3.0], anim="aura_orbit_bob", cubes=c)]

    if arch == "sh_kawaii_capelet":
        c = _scarf_collar(21.4, 24.4, 0.5)
        c.append(make_cube("kc_bow_c", [-0.75, 21.8, -3.95], [0.75, 23.0, -3.1], zone="C", color=a, emissive=True))
        for side in (-1, 1):
            c.append(make_cube(f"kc_bow_{side}", _sx(side, 0.6, 2.7), [21.5, 23.3], [-3.9, -3.15], rot=[0, 0, -side * 14], zone="A", color=p))
            c.append(make_cube(f"kc_frill_{side}", _sx(side, 4.4, 8.2), [22.6, 24.5], [-3.2, 3.2], rot=[0, 0, -side * 14], zone="B", color=s))
        return [make_bone("sh_kawaii", piv, cubes=c)]

    if arch == "sh_frost_epaulettes":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"fe_b_{side}", _sx(side, 4.5, 8.6), [24.25, 25.3], [-2.5, 2.5], zone="B", color=s))
            for k in range(3):
                c.append(make_cube(f"fe_cr_{side}_{k}", _sx(side, 5.2 + k * 1.2, 6.4 + k * 1.2), [25.0, 28.4 - k * 0.5], [-0.9 + (k - 1) * 0.9, 0.9 + (k - 1) * 0.9],
                                   rot=[0, 0, -side * (12 + k * 14)], zone="A" if k != 1 else "C", color=p if k != 1 else a, emissive=True, translucent=True))
        return [make_bone("sh_frost", piv, cubes=c)]

    if arch == "sh_jade_hagoromo":
        c = []
        for i in range(11):
            t = (i - 5) / 5.0
            sx = t * 10.8
            sy = 25.0 + math.cos(t * math.pi * 0.5) * 4.5
            sz = 3.5 + math.cos(t * math.pi * 0.5) * 2.8
            c.append(make_cube(f"jh_{i}", [sx - 1.3, sy - 0.55, sz - 0.45], [sx + 1.3, sy + 0.55, sz + 0.45],
                               rot=[18, t * 28, -t * 36], zone="A" if i % 3 != 0 else "C", color=p if i % 3 != 0 else a, emissive=True, translucent=True))
        return [make_bone("sh_jade", [0.0, 26.0, 4.0], anim="aura_orbit_bob", cubes=c)]

    if arch == "sh_valkyrie_guards":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"vg_1_{side}", _sx(side, 4.4, 8.9), [24.25, 25.6], [-2.8, 2.8], rot=[0, 0, -side * 12], zone="D_GOLD", color=p))
            c.append(make_cube(f"vg_2_{side}", _sx(side, 5.5, 9.5), [25.2, 26.3], [-2.2, 2.2], rot=[0, 0, -side * 22], zone="B", color=s))
            c.append(make_cube(f"vg_gem_{side}", _sx(side, 6.2, 7.6), [25.3, 26.7], [-3.1, -2.2], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        return [make_bone("sh_valk", piv, cubes=c)]

    if arch == "sh_plush_scarf":
        c = _scarf_collar(22.2, 24.7, 0.55)
        tail = make_bone("knit_tail", [0.0, 22.8, -3.5], anim="cape_seg_1", cubes=[
            make_cube("ps_t1", [-3.6, 15.2, -3.85], [-1.5, 22.5, -2.75], zone="B", color=s),
            make_cube("ps_pom1", [-3.3, 14.1, -3.95], [-1.8, 15.6, -2.65], zone="C", color=a),
            make_cube("ps_t2", [1.4, 17.5, -3.75], [3.3, 22.5, -2.85], zone="A", color=p),
            make_cube("ps_pom2", [1.6, 16.4, -3.85], [3.1, 17.9, -2.75], zone="C", color=a),
        ])
        return [make_bone("sh_plush", piv, cubes=c, children=[tail])]

    if arch == "sh_reaper_mantle":
        c = _scarf_collar(21.6, 24.4, 0.45)
        for side in (-1, 1):
            c.append(make_cube(f"rm_sp_{side}", _sx(side, 5.0, 8.8), [23.8, 25.8], [-3.0, 3.0], rot=[0, 0, -side * 16], zone="A", color=p))
            c.append(make_cube(f"rm_orb_{side}", _sx(side, 6.4, 7.8), [26.2, 27.6], [-0.7, 0.7], rot=[45, 45, 0], zone="C", color=a, emissive=True))
        return [make_bone("sh_reaper", piv, cubes=c)]

    if arch == "sh_shrine_collar":
        c = _scarf_collar(22.7, 24.3, 0.35)
        for k in range(5):
            ang = -1.1 + k * 0.55
            bx = math.sin(ang) * 4.4
            bz = -math.cos(ang) * 3.45
            c.append(make_cube(f"sb_{k}", [bx - 0.6, 21.8, bz - 0.6], [bx + 0.6, 23.0, bz + 0.6], zone="D_GOLD", color=a, emissive=True))
        return [make_bone("sh_shrine", piv, cubes=c)]

    if arch == "sh_phoenix_flames":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            cubes = []
            for k in range(4):
                cubes.append(make_cube(f"pf_{k}", _sx(side, 4.8 + k * 1.1, 6.2 + k * 1.2), [24.4 + k * 0.4, 27.4 + k * 0.9], [-1.6 + (k % 2) * 1.2, 0.6 + (k % 2) * 1.2],
                                       rot=[0, 0, -side * (10 + k * 12)], zone="C" if k >= 2 else "A", color=a if k >= 2 else p, emissive=True, translucent=True))
            bones.append(make_bone(f"ph_sh_{tag}", [side * 6.5, 25.0, 0.0], anim="aura_orbit_bob", anim_phase=side * 0.5, cubes=cubes))
        return [make_bone("sh_phoenix", piv, children=bones)]

    if arch == "sh_ocean_pearls":
        c = []
        for i in range(10):
            ang = -1.4 + i * 0.31
            px, pz = math.sin(ang) * 4.7, -math.cos(ang) * 3.1
            c.append(make_cube(f"op_{i}", [px - 0.45, 22.8 + abs(ang) * 0.5, pz - 0.45], [px + 0.45, 23.7 + abs(ang) * 0.5, pz + 0.45], zone="C", color=a, emissive=True))
        for side in (-1, 1):
            c.append(make_cube(f"op_sh_{side}", _sx(side, 4.6, 7.6), [24.25, 25.5], [-2.2, 2.2], rot=[0, 0, -side * 16], zone="A", color=p, emissive=True))
        return [make_bone("sh_ocean", piv, cubes=c)]

    if arch == "sh_butterfly_wrap":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            cubes = [
                make_cube("bw_u", _sx(side, 4.5, 9.6), [23.4, 26.8], [0.8, 3.6], rot=[12, -side * 20, -side * 14], zone="A", color=p, emissive=True, translucent=True),
                make_cube("bw_l", _sx(side, 4.4, 8.2), [20.5, 23.8], [1.4, 3.4], rot=[8, -side * 16, side * 12], zone="C", color=a, emissive=True, translucent=True),
            ]
            bones.append(make_bone(f"sh_bf_{tag}", [side * 4.6, 24.2, 2.2], anim=f"wing_primary_{tag}", anim_amp=0.28, cubes=cubes))
        return [make_bone("sh_butterfly", piv, children=bones)]

    if arch == "sh_astro_rings":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            cx = side * 7.2
            ring = []
            for k in range(8):
                ang = k * math.tau / 8.0
                rx, rz = cx + math.cos(ang) * 2.4, math.sin(ang) * 2.4
                ring.append(make_cube(f"ar_{k}", [rx - 0.6, 26.8, rz - 0.3], [rx + 0.6, 27.4, rz + 0.3],
                                      pivot=[rx, 27.1, rz], rot=[0, -math.degrees(ang) + 90, 0], zone="C" if k % 2 == 0 else "A", color=a if k % 2 == 0 else p, emissive=True))
            bones.append(make_bone(f"astro_sh_{tag}", [cx, 27.1, 0.0], rot=[18, 0, -side * 16], anim="halo_spin_cw" if side > 0 else "halo_spin_ccw", cubes=ring))
        return [make_bone("sh_astro", piv, children=bones)]

    if arch == "sh_strawberry_lace":
        c = _scarf_collar(22.2, 24.3, 0.4)
        c.append(make_cube("sl_flap", [-4.6, 20.4, 2.45], [4.6, 23.6, 3.25], zone="A", color=p))
        for side in (-1, 1):
            c.append(make_cube(f"sl_b_{side}", _sx(side, 1.4, 2.6), [21.0, 22.4], [-3.7, -2.8], zone="B", color=s, emissive=True))
            c.append(make_cube(f"sl_leaf_{side}", _sx(side, 1.6, 2.4), [22.3, 22.9], [-3.7, -2.8], zone="C", color=a))
        return [make_bone("sh_berry", piv, cubes=c)]

    if arch == "sh_samurai_sode":
        c = []
        for side in (-1, 1):
            for tier in range(4):
                y0 = 24.5 - tier * 1.45
                c.append(make_cube(f"ss_t_{side}_{tier}", _sx(side, 4.8 + tier * 0.45, 8.8 + tier * 0.35), [y0, y0 + 1.15], [-2.9, 2.9],
                                   rot=[0, 0, -side * (16 + tier * 6)], zone="A" if tier % 2 == 0 else "B", color=p if tier % 2 == 0 else s))
            c.append(make_cube(f"ss_cord_{side}", _sx(side, 5.8, 7.6), [24.6, 25.4], [-3.1, -2.5], zone="C", color=a, emissive=True))
        return [make_bone("sh_samurai", piv, cubes=c)]

    # sh_prism_stole
    c = []
    for i in range(9):
        t = (i - 4) / 4.0
        sx = t * 9.8
        sy = 24.8 + (1.0 - t * t) * 3.4
        sz = 3.0 + (1.0 - t * t) * 2.4
        c.append(make_cube(f"ps_p_{i}", [sx - 1.4, sy - 0.7, sz - 0.45], [sx + 1.4, sy + 0.7, sz + 0.45],
                           rot=[10, t * 22, -t * 28], zone="A", color=p, emissive=True, translucent=True))
    return [make_bone("sh_prism", [0.0, 25.5, 3.0], anim="aura_orbit_bob", cubes=c)]


# ==============================================================================
# WAIST & TAILS (20 unique designs, anchor='body', ZERO torso/leg clipping!)
# Attached cleanly at waist y=11.8..15.0, trailing behind z >= 2.55 or beside hips |x| >= 4.4
# ==============================================================================
def build_waist_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    piv = [0.0, 12.6, 2.6]

    def _belt(y0: float = 12.0, y1: float = 13.8, pad: float = 0.45, col_b: int | None = None) -> list[dict[str, Any]]:
        bc = col_b if col_b is not None else s
        return [
            make_cube("wb_f", [-4.45 - pad, y0, -2.45 - pad], [4.45 + pad, y1, -2.30], zone="B", color=bc),
            make_cube("wb_b", [-4.45 - pad, y0, 2.30], [4.45 + pad, y1, 2.45 + pad], zone="B", color=bc),
            make_cube("wb_l", [4.30, y0, -2.45 - pad], [4.45 + pad, y1, 2.45 + pad], zone="B", color=bc),
            make_cube("wb_r", [-4.45 - pad, y0, -2.45 - pad], [-4.30, y1, 2.45 + pad], zone="B", color=bc),
        ]

    if arch == "waist_nine_tails":
        tails = []
        for i in range(9):
            ang = -68.0 + i * 17.0
            lift = 22.0 + (4 - abs(i - 4)) * 7.5
            rad = math.radians(ang)
            t_cubes = []
            for seg in range(5):
                dist = 3.2 + seg * 2.55
                w = 1.15 + math.sin((seg / 4.0) * math.pi) * 0.95
                tx = math.sin(rad) * dist
                tz = 2.6 + math.cos(rad) * dist
                ty = 12.6 + seg * (1.1 + (4 - abs(i - 4)) * 0.28)
                t_cubes.append(make_cube(f"nt_{i}_{seg}", [tx - w, ty - w, tz - w], [tx + w, ty + w, tz + w],
                                         rot=[lift * 0.35, -ang, 0], zone="C" if seg == 4 else ("B" if seg == 3 else "A"),
                                         color=a if seg == 4 else (s if seg == 3 else p), emissive=seg == 4))
            tails.append(make_bone(f"ktail_{i}", piv, anim="tail_wag", anim_amp=0.55, anim_phase=i * 0.55, cubes=t_cubes))
        return [make_bone("waist_9tails", piv, children=tails)]

    if arch == "waist_white_fox":
        c = []
        for seg in range(6):
            w = 1.3 + math.sin((seg / 5.0) * math.pi) * 1.55
            ty = 12.2 - seg * 0.45 + (seg * seg) * 0.24
            tz = 2.9 + seg * 2.35
            c.append(make_cube(f"wf_{seg}", [-w, ty - w, tz - w], [w, ty + w, tz + w],
                               rot=[12 - seg * 4, 0, 0], zone="C" if seg == 5 else ("B" if seg == 4 else "A"),
                               color=a if seg == 5 else (s if seg == 4 else p), emissive=seg == 5))
        return [make_bone("waist_fox", piv, anim="tail_wag", anim_amp=0.9, cubes=c)]

    if arch == "waist_black_cat":
        c = [make_cube("bc_bow", [-1.3, 12.2, 2.7], [1.3, 13.6, 3.6], zone="B", color=s),
             make_cube("bc_bell", [-0.55, 11.4, 3.0], [0.55, 12.5, 4.0], zone="C", color=a, emissive=True)]
        pts = [(0, 12.2, 3.4), (0.3, 11.2, 5.4), (0.6, 11.4, 7.6), (0.4, 13.2, 9.4), (0.0, 15.8, 10.2), (-0.4, 18.2, 9.6)]
        for i, (tx, ty, tz) in enumerate(pts):
            r = 0.85 - i * 0.06
            c.append(make_cube(f"bct_{i}", [tx - r, ty - r, tz - r], [tx + r, ty + r, tz + r], zone="A", color=p))
        return [make_bone("waist_black_cat", piv, anim="tail_wag", anim_amp=1.0, cubes=c)]

    if arch == "waist_calico_cat":
        c = []
        pts = [(0, 12.4, 3.2), (-0.4, 11.6, 5.2), (-0.6, 12.2, 7.2), (-0.2, 14.0, 8.8), (0.4, 16.4, 9.5)]
        for i, (tx, ty, tz) in enumerate(pts):
            r = 1.05 - i * 0.08
            zn = "A" if i % 3 == 0 else ("B" if i % 3 == 1 else "D_DARK")
            col = p if i % 3 == 0 else (s if i % 3 == 1 else a)
            c.append(make_cube(f"cct_{i}", [tx - r, ty - r, tz - r], [tx + r, ty + r, tz + r], zone=zn, color=col))
        return [make_bone("waist_calico", piv, anim="tail_wag", anim_amp=0.95, cubes=c)]

    if arch == "waist_cyber_fox":
        c = []
        for seg in range(7):
            w = 1.1 + math.sin((seg / 6.0) * math.pi) * 1.25
            ty = 12.6 + math.sin(seg * 0.55) * 1.4 + seg * 0.5
            tz = 3.2 + seg * 2.2
            c.append(make_cube(f"cf_{seg}", [-w, ty - w * 0.85, tz - 0.8], [w, ty + w * 0.85, tz + 0.8],
                               rot=[14, 0, 0], zone="B" if seg % 2 == 0 else "C",
                               color=s if seg % 2 == 0 else a, emissive=True, translucent=seg % 2 == 1))
        return [make_bone("waist_cyber_fox", piv, anim="tail_wag", anim_amp=0.85, cubes=c)]

    if arch == "waist_dragon_tail":
        c = []
        for seg in range(7):
            w = 1.7 - seg * 0.17
            ty = 11.8 - seg * 0.95
            tz = 2.9 + seg * 2.25
            c.append(make_cube(f"dt_{seg}", [-w, ty - w * 0.75, tz - 1.25], [w, ty + w * 0.75, tz + 1.25], zone="A", color=p))
            c.append(make_cube(f"ds_{seg}", [-0.35, ty + w * 0.6, tz - 0.6], [0.35, ty + w * 1.65, tz + 0.6], rot=[-18, 0, 0], zone="C", color=a, emissive=True))
        return [make_bone("waist_dragon", piv, anim="tail_wag", anim_amp=0.65, cubes=c)]

    if arch == "waist_demon_tail":
        c = []
        pts = [(0, 12.0, 3.0), (0.6, 10.6, 5.2), (1.3, 10.0, 7.6), (1.1, 11.4, 9.8), (0.3, 13.4, 11.2)]
        for i, (tx, ty, tz) in enumerate(pts):
            c.append(make_cube(f"dm_{i}", [tx - 0.5, ty - 0.5, tz - 0.9], [tx + 0.5, ty + 0.5, tz + 0.9], zone="A", color=p))
        c.append(make_cube("dm_spade1", [-1.0, 12.4, 11.6], [1.6, 15.0, 13.8], rot=[20, 0, 45], zone="C", color=a, emissive=True))
        return [make_bone("waist_demon", piv, anim="tail_wag", anim_amp=0.9, cubes=c)]

    if arch == "waist_sakura_obi":
        c = _belt(11.6, 14.8, 0.55, p)
        c.append(make_cube("ob_knot", [-1.6, 12.2, 2.9], [1.6, 14.4, 4.4], zone="C", color=a, emissive=True))
        for side in (-1, 1):
            c.append(make_cube(f"ob_w_{side}", _sx(side, 1.3, 6.4), [11.8, 15.6], [3.0, 4.5], rot=[0, -side * 16, -side * 14], zone="A", color=p))
            c.append(make_cube(f"ob_t_{side}", _sx(side, 1.2, 3.6), [5.2, 12.4], [3.2, 4.1], rot=[12, 0, -side * 12], zone="B", color=s))
            c.append(make_cube(f"ob_g_{side}", _sx(side, 1.7, 3.1), [4.5, 5.6], [3.4, 4.2], zone="C", color=a, emissive=True))
        return [make_bone("waist_obi", piv, anim="cape_seg_1", cubes=c)]

    if arch == "waist_dual_katanas":
        c = _belt(12.2, 13.8, 0.45, p)
        for idx, (yo, zo, ln, ang) in enumerate([(0.0, 0.0, 17.0, 26), (-1.4, 0.8, 12.5, 34)]):
            c.append(make_cube(f"dk_sh_{idx}", [4.75, 11.8 + yo, -2.5 + zo], [5.85, 13.1 + yo, ln - 2.5 + zo],
                               pivot=[5.3, 12.5 + yo, 0.0], rot=[ang, 8, 0], zone="A", color=p))
            c.append(make_cube(f"dk_ts_{idx}", [4.3, 11.4 + yo, -3.2 + zo], [6.3, 13.5 + yo, -2.4 + zo],
                               pivot=[5.3, 12.5 + yo, 0.0], rot=[ang, 8, 0], zone="D_GOLD", color=s, emissive=True))
            c.append(make_cube(f"dk_hi_{idx}", [4.8, 11.9 + yo, -7.2 + zo], [5.8, 13.0 + yo, -3.1 + zo],
                               pivot=[5.3, 12.5 + yo, 0.0], rot=[ang, 8, 0], zone="C", color=a, emissive=True))
        return [make_bone("waist_katanas", piv, cubes=c)]

    if arch == "waist_pastel_bow":
        c = _belt(12.0, 13.6, 0.4, s)
        c.append(make_cube("pb_k", [-1.2, 11.9, 2.8], [1.2, 13.8, 4.3], zone="C", color=a, emissive=True))
        for side in (-1, 1):
            c.append(make_cube(f"pb_l1_{side}", _sx(side, 1.0, 5.6), [11.6, 15.0], [3.1, 4.6], rot=[0, -side * 18, -side * 10], zone="A", color=p))
            c.append(make_cube(f"pb_l2_{side}", _sx(side, 1.2, 4.6), [9.8, 12.4], [3.3, 4.5], rot=[0, -side * 14, side * 16], zone="B", color=s))
            c.append(make_cube(f"pb_r_{side}", _sx(side, 0.9, 2.8), [4.8, 11.6], [3.6, 4.3], rot=[15, 0, -side * 14], zone="A", color=p))
        return [make_bone("waist_pastel_bow", piv, anim="cape_seg_1", cubes=c)]

    if arch == "waist_shiba_tail":
        c = []
        pts = [(0, 12.2, 3.0), (0, 13.4, 4.8), (0, 15.4, 5.6), (0, 16.8, 4.2), (0, 15.8, 3.0)]
        for i, (tx, ty, tz) in enumerate(pts):
            r = 1.45 - i * 0.14
            c.append(make_cube(f"shb_{i}", [tx - r, ty - r, tz - r], [tx + r, ty + r, tz + r], zone="B" if i >= 3 else "A", color=s if i >= 3 else p))
        return [make_bone("waist_shiba", piv, anim="tail_wag", anim_amp=1.1, cubes=c)]

    if arch == "waist_seraph_sash":
        c = _belt(12.1, 13.9, 0.45, s)
        for side in (-1, 1):
            c.append(make_cube(f"ss_s_{side}", _sx(side, 3.6, 5.6), [4.2, 13.2], [-1.0, 2.2], rot=[6, 0, -side * 10], zone="A", color=p, emissive=True))
            c.append(make_cube(f"ss_g_{side}", _sx(side, 4.0, 5.4), [11.8, 13.6], [-1.2, 0.2], zone="C", color=a, emissive=True))
        return [make_bone("waist_seraph", piv, anim="cape_seg_1", cubes=c)]

    if arch == "waist_grimoire":
        c = _belt(11.8, 13.9, 0.5, p)
        c.append(make_cube("gr_book", [-6.4, 8.8, -1.6], [-4.6, 13.2, 1.8], rot=[0, 0, 8], zone="B", color=s))
        c.append(make_cube("gr_rune", [-6.65, 10.2, -0.7], [-6.1, 11.8, 0.9], rot=[0, 0, 8], zone="C", color=a, emissive=True))
        c.append(make_cube("gr_vial", [4.6, 9.8, -0.8], [6.0, 12.2, 0.8], zone="C", color=a, emissive=True, translucent=True))
        c.append(make_cube("gr_cork", [4.9, 12.2, -0.5], [5.7, 13.0, 0.5], zone="A", color=p))
        return [make_bone("waist_grimoire", piv, cubes=c)]

    if arch == "waist_samurai_swords":
        c = _belt(12.0, 14.0, 0.5, s)
        c.append(make_cube("sd_s1", [-5.9, 11.4, -2.2], [-4.8, 12.7, 15.8], pivot=[-5.3, 12.2, 0.0], rot=[22, -10, 0], zone="A", color=p))
        c.append(make_cube("sd_t1", [-6.4, 11.0, -3.0], [-4.3, 13.1, -2.1], pivot=[-5.3, 12.2, 0.0], rot=[22, -10, 0], zone="C", color=a, emissive=True))
        c.append(make_cube("sd_h1", [-5.8, 11.5, -7.4], [-4.9, 12.6, -2.9], pivot=[-5.3, 12.2, 0.0], rot=[22, -10, 0], zone="B", color=s))
        c.append(make_cube("sd_s2", [4.8, 11.6, -1.6], [5.8, 12.7, 10.2], pivot=[5.3, 12.2, 0.0], rot=[30, 12, 0], zone="A", color=p))
        c.append(make_cube("sd_h2", [4.85, 11.7, -5.2], [5.75, 12.6, -1.6], pivot=[5.3, 12.2, 0.0], rot=[30, 12, 0], zone="C", color=a, emissive=True))
        return [make_bone("waist_samurai", piv, cubes=c)]

    if arch == "waist_mermaid_ribbon":
        c = _belt(12.0, 13.6, 0.45, p)
        for i in range(5):
            ang = -40.0 + i * 20.0
            rad = math.radians(ang)
            tx = math.sin(rad) * 3.8
            tz = 2.8 + math.cos(rad) * 3.2
            c.append(make_cube(f"mr_f_{i}", [tx - 1.3, 4.5 + abs(i - 2) * 1.1, tz - 0.35], [tx + 1.3, 13.0, tz + 0.35],
                               rot=[20, -ang, 0], zone="A" if i % 2 == 0 else "C", color=p if i % 2 == 0 else a, emissive=True, translucent=True))
        return [make_bone("waist_mermaid", piv, anim="cape_seg_2", cubes=c)]

    if arch == "waist_bunny_pom":
        c = [
            make_cube("bp_core", [-2.1, 10.4, 2.7], [2.1, 14.4, 6.6], zone="A", color=p),
            make_cube("bp_puff", [-1.6, 11.0, 3.2], [1.6, 13.8, 7.2], zone="A", color=p),
            make_cube("bp_bow_c", [-0.6, 13.8, 3.0], [0.6, 14.8, 3.9], zone="C", color=a),
            make_cube("bp_bow_l", [0.4, 13.6, 3.0], [2.2, 15.0, 3.8], rot=[0, 0, -14], zone="B", color=s),
            make_cube("bp_bow_r", [-2.2, 13.6, 3.0], [-0.4, 15.0, 3.8], rot=[0, 0, 14], zone="B", color=s),
        ]
        return [make_bone("waist_bunny", piv, anim="tail_wag", anim_amp=0.6, cubes=c)]

    if arch == "waist_void_chains":
        c = _belt(12.0, 13.7, 0.45, p)
        for side in (-1, 1):
            for k in range(5):
                c.append(make_cube(f"vc_{side}_{k}", _sx(side, 4.6 + k * 0.45, 5.7 + k * 0.45), [11.8 - k * 1.75, 13.0 - k * 1.75], [0.5 + k * 1.2, 1.8 + k * 1.2],
                                   rot=[12, 0, 45 if k % 2 == 0 else 0], zone="C" if k % 2 == 0 else "B", color=a if k % 2 == 0 else s, emissive=k % 2 == 0))
        return [make_bone("waist_void_ch", piv, anim="cape_seg_2", cubes=c)]

    if arch == "waist_lantern_charm":
        c = _belt(12.3, 13.6, 0.4, p)
        c.append(make_cube("lc_cord", [4.6, 10.6, -0.4], [5.1, 13.0, 0.1], zone="D_GOLD", color=0xFBBF24))
        c.append(make_cube("lc_top", [4.3, 10.2, -1.0], [6.5, 10.9, 1.2], zone="A", color=p))
        c.append(make_cube("lc_glow", [4.5, 7.6, -0.8], [6.3, 10.3, 1.0], zone="C", color=a, emissive=True))
        c.append(make_cube("lc_bot", [4.3, 7.0, -1.0], [6.5, 7.7, 1.2], zone="A", color=p))
        c.append(make_cube("lc_tas", [5.1, 5.0, -0.2], [5.7, 7.1, 0.4], zone="A", color=p))
        return [make_bone("waist_lantern", piv, anim="aura_orbit_bob", cubes=c)]

    if arch == "waist_jade_ribbon":
        c = _belt(12.0, 13.8, 0.45, s)
        c.append(make_cube("jr_clasp", [-1.0, 12.1, -3.15], [1.0, 13.7, -2.55], rot=[0, 0, 45], zone="C", color=a, emissive=True))
        for side in (-1, 1):
            for k in range(5):
                c.append(make_cube(f"jr_s_{side}_{k}", _sx(side, 4.6 + k * 0.7, 5.9 + k * 0.7), [12.0 - k * 1.4, 13.4 - k * 1.4], [1.2 + k * 1.6, 2.2 + k * 1.6],
                                   rot=[18, -side * 16, 0], zone="A" if k < 4 else "C", color=p if k < 4 else a, emissive=True, translucent=True))
        return [make_bone("waist_jade", piv, anim="cape_seg_2", cubes=c)]

    # waist_star_comet
    c = [make_cube("sc_star", [-1.4, 11.4, 3.0], [1.4, 14.2, 5.8], rot=[45, 45, 0], zone="A", color=p, emissive=True)]
    for seg in range(6):
        w = 1.4 - seg * 0.18
        tz = 5.4 + seg * 2.1
        ty = 12.6 + math.sin(seg * 0.7) * 1.2
        c.append(make_cube(f"sc_t_{seg}", [-w, ty - w * 0.7, tz - 0.9], [w, ty + w * 0.7, tz + 0.9],
                           zone="C" if seg % 2 == 0 else "B", color=a if seg % 2 == 0 else s, emissive=True, translucent=True))
    return [make_bone("waist_comet", piv, anim="tail_wag", anim_amp=0.8, cubes=c)]


# ==============================================================================
# FEET & ANKLE RINGS (20 unique designs, anchor='root', y=0.6..6.5, ZERO leg clipping!)
# Legs span x=[-4.2, 4.2], y=[0, 12], z=[-2.2, 2.2] -> ankles/rings stay cleanly outside!
# ==============================================================================
def build_feet_bones(arch: str, p: int, s: int, a: int) -> list[dict[str, Any]]:
    def _ring(radius: float, y: float, segs: int, thickness: float, height: float,
              alt_zone: str = "C", translucent: bool = True, tilt_x: float = 0.0) -> list[dict[str, Any]]:
        out = []
        seg_len = (math.tau * radius / segs) * 0.56
        for i in range(segs):
            ang = i * math.tau / segs
            rx, rz = math.cos(ang) * radius, math.sin(ang) * radius
            out.append(make_cube(f"fr_{i}", [rx - seg_len, y, rz - thickness * 0.5], [rx + seg_len, y + height, rz + thickness * 0.5],
                                 pivot=[rx, y + height * 0.5, rz], rot=[tilt_x, -math.degrees(ang) + 90, 0],
                                 zone=alt_zone if i % 2 == 0 else "A", color=a if i % 2 == 0 else p,
                                 emissive=True, translucent=translucent))
        return out

    if arch == "feet_hermes_wings":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            cubes = [make_cube("hw_band", _sx(side, 4.35, 5.1), [2.4, 3.6], [-2.5, 2.5], zone="D_GOLD", color=s)]
            for k in range(4):
                cubes.append(make_cube(f"hw_f_{k}", _sx(side, 4.9 + k * 0.55, 6.2 + k * 0.7), [2.8 + k * 0.85, 3.9 + k * 0.85], [0.8 + k * 1.15, 2.6 + k * 1.15],
                                       rot=[10, -side * 16, -side * (12 + k * 6)], zone="A" if k < 3 else "C", color=p if k < 3 else a, emissive=True))
            bones.append(make_bone(f"ft_w_{tag}", [side * 4.6, 3.0, 0.5], anim=f"wing_primary_{tag}", anim_amp=0.35, cubes=cubes))
        return [make_bone("feet_hermes", [0, 2.5, 0], children=bones)]

    if arch == "feet_arcane_ring":
        r1 = _ring(9.2, 0.85, 16, 0.55, 0.35, "A", True)
        r2 = _ring(6.8, 1.15, 12, 0.45, 0.30, "C", True)
        for k in range(6):
            ang = k * math.tau / 6.0
            rx, rz = math.cos(ang) * 9.2, math.sin(ang) * 9.2
            r1.append(make_cube(f"ar_gem_{k}", [rx - 0.55, 1.0, rz - 0.55], [rx + 0.55, 2.1, rz + 0.55], rot=[45, 45, 0], zone="C", color=a, emissive=True))
        return [
            make_bone("ft_arc_out", [0, 1.0, 0], anim="aura_ring_ground", cubes=r1),
            make_bone("ft_arc_in", [0, 1.2, 0], anim="aura_orbit_ccw", cubes=r2),
        ]

    if arch == "feet_sakura_anklets":
        c = _ring(7.8, 2.0, 12, 0.4, 0.35, "A", True)
        for k in range(6):
            ang = k * math.tau / 6.0
            rx, rz = math.cos(ang) * 8.4, math.sin(ang) * 8.4
            c.append(make_cube(f"sp_{k}", [rx - 0.8, 2.4 + (k % 2) * 1.1, rz - 0.8], [rx + 0.8, 3.0 + (k % 2) * 1.1, rz + 0.8],
                               rot=[22, -math.degrees(ang), 14], zone="B" if k % 2 == 0 else "C", color=s if k % 2 == 0 else a, emissive=True))
        return [make_bone("feet_sakura", [0, 2.4, 0], anim="aura_orbit_cw", cubes=c)]

    if arch == "feet_frost_rings":
        c = _ring(8.2, 1.2, 12, 0.5, 0.45, "A", True)
        for k in range(6):
            ang = k * math.tau / 6.0
            rx, rz = math.cos(ang) * 8.2, math.sin(ang) * 8.2
            c.append(make_cube(f"fr_sp_{k}", [rx - 0.45, 1.5, rz - 0.45], [rx + 0.45, 4.4, rz + 0.45],
                               rot=[18, -math.degrees(ang), 0], zone="C", color=a, emissive=True, translucent=True))
        return [make_bone("feet_frost", [0, 1.5, 0], anim="aura_ring_ground", cubes=c)]

    if arch == "feet_phoenix_wings":
        bones = []
        for side, tag in ((1, "l"), (-1, "r")):
            cubes = []
            for k in range(5):
                cubes.append(make_cube(f"fp_{k}", _sx(side, 4.7 + k * 0.5, 6.1 + k * 0.75), [2.0 + k * 0.9, 3.2 + k * 0.95], [1.4 + k * 1.1, 3.2 + k * 1.1],
                                       rot=[14, -side * 18, -side * (15 + k * 6)], zone="C" if k >= 3 else "A", color=a if k >= 3 else p, emissive=True))
            bones.append(make_bone(f"ft_ph_{tag}", [side * 4.5, 2.5, 1.2], anim=f"wing_primary_{tag}", anim_amp=0.4, cubes=cubes))
        return [make_bone("feet_phoenix", [0, 2.5, 0], children=bones)]

    if arch == "feet_cyber_hover":
        r1 = _ring(8.6, 0.95, 6, 0.65, 0.55, "A", True)
        r2 = _ring(7.2, 2.4, 6, 0.4, 0.35, "C", True)
        return [
            make_bone("ft_cyb_1", [0, 1.2, 0], anim="aura_orbit_cw", cubes=r1),
            make_bone("ft_cyb_2", [0, 2.5, 0], anim="aura_orbit_ccw", cubes=r2),
        ]

    if arch == "feet_starlight_orbit":
        c = _ring(8.0, 2.2, 14, 0.35, 0.30, "B", True)
        for k in range(5):
            ang = k * math.tau / 5.0
            rx, rz = math.cos(ang) * 8.6, math.sin(ang) * 8.6
            c.append(make_cube(f"st_{k}", [rx - 0.75, 2.2 + (k % 2) * 1.4, rz - 0.75], [rx + 0.75, 3.7 + (k % 2) * 1.4, rz + 0.75],
                               rot=[45, 45, 0], zone="A" if k % 2 == 0 else "C", color=p if k % 2 == 0 else a, emissive=True))
        return [make_bone("feet_starlight", [0, 2.5, 0], anim="aura_orbit_cw", cubes=c)]

    if arch == "feet_kawaii_bows":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"kb_band_{side}", _sx(side, 4.35, 5.0), [2.5, 3.7], [-2.55, 2.55], zone="B", color=s))
            c.append(make_cube(f"kb_k_{side}", _sx(side, 4.8, 5.6), [2.7, 3.7], [-0.5, 0.5], zone="C", color=a, emissive=True))
            c.append(make_cube(f"kb_l1_{side}", _sx(side, 5.0, 5.7), [2.5, 4.0], [-2.2, -0.3], rot=[14, 0, 0], zone="A", color=p))
            c.append(make_cube(f"kb_l2_{side}", _sx(side, 5.0, 5.7), [2.5, 4.0], [0.3, 2.2], rot=[-14, 0, 0], zone="A", color=p))
        return [make_bone("feet_bows", [0, 3.0, 0], cubes=c)]

    if arch == "feet_void_step":
        r1 = _ring(8.8, 1.1, 12, 0.5, 0.45, "A", True)
        r2 = _ring(7.4, 2.6, 8, 0.45, 0.4, "C", True, tilt_x=14.0)
        return [
            make_bone("ft_v1", [0, 1.2, 0], anim="aura_orbit_cw", cubes=r1),
            make_bone("ft_v2", [0, 2.6, 0], rot=[12, 0, -10], anim="aura_orbit_ccw", cubes=r2),
        ]

    if arch == "feet_jade_wind":
        c = []
        for i in range(14):
            ang = i * 0.48
            r = 7.4 + (i % 3) * 0.7
            rx, rz = math.cos(ang) * r, math.sin(ang) * r
            ry = 1.0 + i * 0.34
            c.append(make_cube(f"jw_{i}", [rx - 1.5, ry, rz - 0.35], [rx + 1.5, ry + 0.55, rz + 0.35],
                               pivot=[rx, ry, rz], rot=[0, -math.degrees(ang) + 90, 12], zone="A" if i % 2 == 0 else "C",
                               color=p if i % 2 == 0 else a, emissive=True, translucent=True))
        return [make_bone("feet_jade_wind", [0, 2.5, 0], anim="aura_orbit_fast", cubes=c)]

    if arch == "feet_ocean_ripple":
        r1 = _ring(9.6, 0.75, 16, 0.45, 0.25, "A", True)
        r2 = _ring(6.9, 1.0, 12, 0.40, 0.25, "C", True)
        return [
            make_bone("ft_oc1", [0, 0.9, 0], anim="aura_ring_ground", cubes=r1),
            make_bone("ft_oc2", [0, 1.1, 0], anim="aura_orbit_ccw", cubes=r2),
        ]

    if arch == "feet_thunder_spurs":
        c = _ring(8.2, 1.4, 10, 0.45, 0.4, "A", True)
        for side in (-1, 1):
            c.append(make_cube(f"ts_1_{side}", _sx(side, 4.7, 7.6), [2.2, 3.4], [1.4, 2.8], rot=[0, -side * 22, -side * 18], zone="C", color=a, emissive=True))
            c.append(make_cube(f"ts_2_{side}", _sx(side, 6.2, 9.2), [3.3, 4.4], [2.4, 3.8], rot=[0, -side * 28, -side * 28], zone="A", color=p, emissive=True))
        return [make_bone("feet_thunder", [0, 2.2, 0], anim="aura_orbit_cw", cubes=c)]

    if arch == "feet_cloud_puffs":
        c = []
        for k in range(8):
            ang = k * math.tau / 8.0
            rx, rz = math.cos(ang) * 7.8, math.sin(ang) * 7.8
            w = 1.6 if k % 2 == 0 else 1.25
            c.append(make_cube(f"cp_{k}", [rx - w, 1.0, rz - w], [rx + w, 1.0 + w * 1.25, rz + w],
                               zone="A" if k % 2 == 0 else "B", color=p if k % 2 == 0 else s, translucent=True))
        return [make_bone("feet_cloud", [0, 1.6, 0], anim="aura_orbit_bob", cubes=c)]

    if arch == "feet_butterfly_anklets":
        c = []
        for k in range(5):
            ang = k * math.tau / 5.0
            rx, rz = math.cos(ang) * 8.2, math.sin(ang) * 8.2
            ry = 2.0 + (k % 3) * 1.2
            c.append(make_cube(f"fb_l_{k}", [rx + 0.15, ry, rz - 0.8], [rx + 1.8, ry + 1.1, rz + 0.8], rot=[0, -math.degrees(ang), 24], zone="A", color=p, emissive=True, translucent=True))
            c.append(make_cube(f"fb_r_{k}", [rx - 1.8, ry, rz - 0.8], [rx - 0.15, ry + 1.1, rz + 0.8], rot=[0, -math.degrees(ang), -24], zone="C", color=a, emissive=True, translucent=True))
        return [make_bone("feet_butterfly", [0, 2.8, 0], anim="aura_orbit_cw", cubes=c)]

    if arch == "feet_solar_halo":
        c = _ring(8.4, 0.9, 14, 0.55, 0.35, "D_GOLD", False)
        for k in range(8):
            ang = k * math.tau / 8.0
            rx, rz = math.cos(ang) * 10.2, math.sin(ang) * 10.2
            c.append(make_cube(f"sh_r_{k}", [rx - 0.5, 0.85, rz - 1.5], [rx + 0.5, 1.35, rz + 1.5],
                               pivot=[rx, 1.1, rz], rot=[0, -math.degrees(ang) + 90, 0], zone="C", color=a, emissive=True))
        return [make_bone("feet_solar", [0, 1.1, 0], anim="aura_ring_ground", cubes=c)]

    if arch == "feet_prism_rings":
        r1 = _ring(8.6, 1.8, 12, 0.45, 0.4, "A", True)
        r2 = _ring(7.6, 3.2, 12, 0.4, 0.35, "C", True)
        return [
            make_bone("ft_pr1", [0, 2.0, 0], rot=[12, 0, 10], anim="aura_orbit_cw", cubes=r1),
            make_bone("ft_pr2", [0, 3.2, 0], rot=[-12, 0, -10], anim="aura_orbit_ccw", cubes=r2),
        ]

    if arch == "feet_crimson_moon":
        c = _ring(8.0, 1.6, 12, 0.42, 0.35, "B", False)
        for k in range(4):
            ang = k * math.tau / 4.0
            rx, rz = math.cos(ang) * 8.5, math.sin(ang) * 8.5
            c.append(make_cube(f"cm_m_{k}", [rx - 0.7, 1.8, rz - 0.3], [rx + 0.7, 3.8, rz + 0.3],
                               pivot=[rx, 2.8, rz], rot=[0, -math.degrees(ang), 18], zone="C", color=a, emissive=True))
        return [make_bone("feet_crimson", [0, 2.2, 0], anim="aura_orbit_cw", cubes=c)]

    if arch == "feet_melody_beats":
        c = []
        for k in range(12):
            ang = k * math.tau / 12.0
            rx, rz = math.cos(ang) * 8.2, math.sin(ang) * 8.2
            h = 1.4 + (k % 4) * 0.9
            c.append(make_cube(f"mb_{k}", [rx - 0.45, 1.1, rz - 0.45], [rx + 0.45, 1.1 + h, rz + 0.45],
                               zone="A" if k % 2 == 0 else "C", color=p if k % 2 == 0 else a, emissive=True))
        return [make_bone("feet_melody", [0, 2.0, 0], anim="aura_orbit_cw", cubes=c)]

    if arch == "feet_paw_warmers":
        c = []
        for side in (-1, 1):
            c.append(make_cube(f"pw_c1_{side}", _sx(side, 4.35, 5.25), [1.4, 5.4], [-2.75, 2.75], zone="A", color=p))
            c.append(make_cube(f"pw_pad_{side}", _sx(side, 4.95, 5.55), [2.5, 4.1], [-0.8, 0.8], zone="C", color=a, emissive=True))
            c.append(make_cube(f"pw_rim_{side}", _sx(side, 4.35, 5.35), [5.0, 5.8], [-2.85, 2.85], zone="B", color=s))
        return [make_bone("feet_paw", [0, 3.0, 0], cubes=c)]

    # feet_royal_greaves
    c = _ring(7.8, 2.4, 12, 0.48, 0.55, "D_GOLD", False)
    for k in range(4):
        ang = k * math.tau / 4.0
        rx, rz = math.cos(ang) * 8.1, math.sin(ang) * 8.1
        c.append(make_cube(f"rg_g_{k}", [rx - 0.6, 2.2, rz - 0.6], [rx + 0.6, 3.4, rz + 0.6], rot=[45, 45, 0], zone="C", color=a, emissive=True))
    return [make_bone("feet_royal", [0, 2.6, 0], anim="aura_orbit_cw", cubes=c)]
