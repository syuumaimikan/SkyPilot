"""Generates commercial AAA 256x256 HDR-style custom procedural RGBA emissive VFX textures
PLUS 18 pre-composited 256x256 full-color studio preview thumbnails for WardrobeVfxScreen!

Zero vanilla Minecraft sprites — 100% bespoke antialiased vector/SDF glow artwork.
"""
from __future__ import annotations

import concurrent.futures
import math
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

REPO = Path(__file__).resolve().parent.parent.parent
VFX_DIR = REPO / "apps" / "mod" / "wardrobe-assets" / "src" / "main" / "resources" / "assets" / "skypilot" / "textures" / "vfx"
PREVIEW_DIR = REPO / "apps" / "mod" / "wardrobe-assets" / "src" / "main" / "resources" / "assets" / "skypilot" / "textures" / "vfx_preview"

SIZE = 256


def _clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, v))


def _dist_seg(px: float, py: float, x1: float, y1: float, x2: float, y2: float) -> float:
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    if l2 <= 1e-6:
        return math.hypot(px - x1, py - y1)
    t = _clamp(((px - x1) * dx + (py - y1) * dy) / l2)
    return math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))


def _white_hdr(alpha: float, core: float = 0.0) -> tuple[int, int, int, int]:
    """Luminous HDR-like sprite: bright incandescent white core with silky antialiased glow falloff."""
    a = int(_clamp(alpha) * 255.0)
    if a <= 1:
        return (0, 0, 0, 0)
    w = int(218.0 + 37.0 * _clamp(core))
    return (w, w, 255, a)


def _render_sdf(name: str, fn) -> tuple[str, Image.Image]:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    half = (SIZE - 1) * 0.5
    inv = 1.0 / half
    for y in range(SIZE):
        ny = (y - half) * inv
        for x in range(SIZE):
            nx = (x - half) * inv
            r = math.hypot(nx, ny)
            ang = math.atan2(ny, nx)
            res = fn(nx, ny, r, ang)
            if res is not None and res[3] > 1:
                px[x, y] = res
    VFX_DIR.mkdir(parents=True, exist_ok=True)
    img.save(VFX_DIR / f"{name}.png")
    return name, img


# ==============================================================================
# 26 COMMERCIAL-GRADE 256x256 PROCEDURAL EMISSIVE VFX TEXTURES
# ==============================================================================

def _f_slash_arc(nx, ny, r, ang):
    # Sleek anime katana crescent plasma slash: razor-bright leading edge + wispy trailing streaks
    u = (nx + 1.0) * 0.5
    taper = math.sin(u * math.pi) ** 0.65
    if taper < 0.02:
        return None
    # Subtle forward curve along leading edge (ny < 0)
    y_shift = ny + 0.12 * (1.0 - taper)
    w = 0.68 * taper
    if abs(y_shift) > w:
        return None
    # Leading razor-sharp blade edge near y_shift = -0.18 * taper
    lead_d = abs(y_shift + 0.16 * taper) / max(0.03, taper)
    razor = math.exp(-((lead_d / 0.11) ** 2))
    # Silky trailing wake behind the leading edge
    wake_t = (y_shift + 0.18 * taper) / max(0.06, 0.72 * taper)
    wake = 0.0
    if 0.0 <= wake_t <= 1.0:
        wisps = 0.72 + 0.28 * math.sin(u * 22.0 - wake_t * 9.0) * math.cos(u * 13.0 + wake_t * 5.0)
        wake = ((1.0 - wake_t) ** 1.65) * 0.78 * wisps
    aura = math.exp(-((lead_d / 0.42) ** 2)) * 0.48
    v = min(1.0, (razor * 1.05 + wake + aura) * (0.25 + 0.75 * taper))
    return _white_hdr(v, razor)


def _f_slash_core(nx, ny, r, ang):
    # Pure razor-thin incandescent inner slash highlight (layered over slash_arc for HDR bloom!)
    u = (nx + 1.0) * 0.5
    taper = math.sin(u * math.pi) ** 0.85
    if taper < 0.02:
        return None
    d = abs(ny + 0.16 * taper) / max(0.02, taper)
    core = math.exp(-((d / 0.065) ** 2)) * taper
    bloom = math.exp(-((d / 0.18) ** 2)) * 0.35 * taper
    return _white_hdr(min(1.0, core + bloom), core)


def _f_magic_circle(nx, ny, r, ang):
    # Ultra-delicate commercial fantasy arcane spell circle: fine triple filigree rings,
    # inscribed 6-point + 12-point star geometry, crisp circular rune glyphs & glowing center!
    if r > 0.99:
        return None
    r_out1 = math.exp(-(((r - 0.93) / 0.016) ** 2))
    r_out2 = math.exp(-(((r - 0.87) / 0.011) ** 2)) * 0.85
    r_mid1 = math.exp(-(((r - 0.72) / 0.014) ** 2)) * 0.92
    r_mid2 = math.exp(-(((r - 0.67) / 0.009) ** 2)) * 0.65
    r_in = math.exp(-(((r - 0.36) / 0.012) ** 2)) * 0.82

    # Crisp arcane rune glyphs in outer band (0.74..0.85)
    rune = 0.0
    if 0.75 <= r <= 0.84:
        seg = ((ang + math.pi) / math.tau) * 24.0
        fl = seg - math.floor(seg)
        idx = int(seg)
        radial_t = (r - 0.75) / 0.09
        bar = math.exp(-(((fl - 0.5) / 0.11) ** 2))
        cross = math.exp(-(((radial_t - 0.5) / 0.16) ** 2)) if (idx % 2 == 0 and 0.25 < fl < 0.75) else 0.0
        diag = math.exp(-(((abs((fl - 0.5) - (radial_t - 0.5) * 0.7)) / 0.12) ** 2)) if (idx % 3 == 1 and 0.2 < fl < 0.8) else 0.0
        rune = max(bar * 0.85, cross * 0.8, diag * 0.85)

    # Fine inscribed hexagram (2 rotated triangles)
    star = 0.0
    for rot in (0.0, math.pi / 3.0):
        for k in range(3):
            a1 = rot + k * math.tau / 3.0
            a2 = rot + (k + 1) * math.tau / 3.0
            d = _dist_seg(nx, ny, math.cos(a1) * 0.71, math.sin(a1) * 0.71, math.cos(a2) * 0.71, math.sin(a2) * 0.71)
            star = max(star, math.exp(-((d / 0.014) ** 2)))

    # 6 small glowing node orbs at star vertices
    nodes = 0.0
    for k in range(6):
        ak = k * math.tau / 6.0
        dn = math.hypot(nx - math.cos(ak) * 0.72, ny - math.sin(ak) * 0.72)
        nodes = max(nodes, math.exp(-((dn / 0.042) ** 2)))

    # Delicate inner 4-point star
    ax, ay = abs(nx), abs(ny)
    inner_star = (math.exp(-((ax * 14.0) ** 1.3 + (ay * 3.0) ** 1.3))
                  + math.exp(-((ay * 14.0) ** 1.3 + (ax * 3.0) ** 1.3))) * 0.85
    soft_halo = math.exp(-(((r - 0.72) / 0.12) ** 2)) * 0.16 + math.exp(-((r / 0.22) ** 2)) * 0.28

    sharp = max(r_out1, r_out2, r_mid1, r_mid2, r_in, star * 0.92, rune * 0.82, nodes, inner_star)
    v = min(1.0, sharp + soft_halo)
    return _white_hdr(v, max(r_out1, star, nodes, inner_star)) if v > 0.02 else None


def _f_shockwave_ring(nx, ny, r, ang):
    # Razor-crisp commercial kinetic shockwave: sharp glowing crest + silky directional ripple wake + outer halo!
    if r > 0.99:
        return None
    sharp_crest = math.exp(-(((r - 0.84) / 0.022) ** 2))
    mid_ring = math.exp(-(((r - 0.75) / 0.012) ** 2)) * 0.55
    wake = math.exp(-(((r - 0.72) / 0.16) ** 2)) * 0.44 if r < 0.84 else math.exp(-(((r - 0.84) / 0.07) ** 2)) * 0.35
    shimmer = 0.86 + 0.14 * math.sin(ang * 16.0) * math.cos(r * 12.0)
    v = min(1.0, (sharp_crest + mid_ring + wake) * shimmer)
    return _white_hdr(v, sharp_crest) if v > 0.02 else None


def _f_vortex_spiral(nx, ny, r, ang):
    # 3-arm luminous swirling galaxy/wind spiral vortex for Kill, Dash & Hydro effects!
    if r > 0.98 or r < 0.04:
        return None
    arm = 0.5 + 0.5 * math.cos(ang * 3.0 - r * 11.5)
    ribbon = (arm ** 5.0) * math.sin(r * math.pi) ** 0.7
    thin_arc = (arm ** 16.0) * math.sin(r * math.pi) ** 0.6
    core = math.exp(-((r / 0.18) ** 2)) * 0.55
    v = min(1.0, ribbon * 0.78 + thin_arc * 0.55 + core)
    return _white_hdr(v, max(thin_arc, core)) if v > 0.02 else None


def _f_glow_soft(nx, ny, r, ang):
    # Pure silky HDR bloom orb (incandescent center + smooth exponential atmospheric glow)
    if r > 0.99:
        return None
    core = math.exp(-((r / 0.18) ** 2))
    mid = math.exp(-((r / 0.44) ** 2)) * 0.58
    outer = math.exp(-((r / 0.78) ** 2)) * 0.22 * (1.0 - r)
    v = min(1.0, core + mid + outer)
    return _white_hdr(v, core) if v > 0.015 else None


def _f_sakura_petal(nx, ny, r, ang):
    # Painterly sculpted sakura petal with delicate curved notch, soft translucency & glowing edge rim
    x = nx * 1.18 + ny * 0.08
    y = ny * 1.06
    d = math.hypot(x / (0.64 - y * 0.18), (y + 0.04) / 0.80)
    if d > 1.08:
        return None
    if y < -0.44 and abs(x) < (-y - 0.44) * 0.62:
        return None
    if d > 1.0:
        aa = max(0.0, 1.0 - (d - 1.0) / 0.08)
        return _white_hdr(aa * 0.45, 0.2)
    rim = math.exp(-(((1.0 - d) / 0.14) ** 2))
    vein = math.exp(-((abs(x) / 0.06) ** 2)) * 0.35 + math.exp(-(((abs(x - y * 0.28)) / 0.05) ** 2)) * 0.18
    grad = 0.70 + 0.28 * (1.0 - (y + 0.8) / 1.6)
    return _white_hdr(min(1.0, grad + rim * 0.32), max(rim, vein))


def _f_star_spark(nx, ny, r, ang):
    # Ultra-crisp anime 4-point diamond lens-flare sparkle + delicate diagonal micro-rays!
    ax, ay = abs(nx), abs(ny)
    h_ray = math.exp(-((ay / 0.045) ** 1.4 + (ax / 0.82) ** 1.3))
    v_ray = math.exp(-((ax / 0.045) ** 1.4 + (ay / 0.82) ** 1.3))
    u = (nx + ny) * 0.7071
    v_ = (nx - ny) * 0.7071
    d_rays = (math.exp(-((abs(u) / 0.04) ** 1.5 + (abs(v_) / 0.40) ** 1.5))
              + math.exp(-((abs(v_) / 0.04) ** 1.5 + (abs(u) / 0.40) ** 1.5))) * 0.55
    core = math.exp(-((r / 0.16) ** 2))
    halo = math.exp(-((r / 0.45) ** 2)) * 0.32
    val = min(1.0, h_ray + v_ray + d_rays + core + halo)
    return _white_hdr(val, max(core, h_ray, v_ray)) if val > 0.02 else None


def _f_heart_glow(nx, ny, r, ang):
    # Glossy crystal-glass kawaii heart with inner rim light & crisp specular droplet
    x = nx * 1.28
    y = -ny * 1.28 - 0.10
    hv = (x * x + y * y - 0.56) ** 3 - x * x * (y ** 3) * 1.38
    if hv > 0.0:
        bloom = math.exp(-(hv * 22.0)) * 0.52
        return _white_hdr(bloom, 0.0) if bloom > 0.02 else None
    depth = _clamp(-hv * 10.0)
    rim = math.exp(-((depth / 0.38) ** 2))
    spec = math.exp(-(((nx + 0.26) / 0.17) ** 2 + ((ny + 0.28) / 0.12) ** 2))
    spec2 = math.exp(-(((nx + 0.12) / 0.07) ** 2 + ((ny + 0.14) / 0.07) ** 2)) * 0.75
    return _white_hdr(min(1.0, 0.78 + rim * 0.25 + spec * 0.3), max(spec, spec2, rim * 0.8))


def _f_snowflake(nx, ny, r, ang):
    # Intricate crystal frost snowflake with 6 feathery dendritic arms & faceted hex core
    if r > 0.96:
        return None
    fold = abs(((ang + math.pi) % (math.tau / 6.0)) - math.tau / 12.0)
    d_arm = r * math.sin(fold)
    arm = math.exp(-((d_arm / 0.028) ** 2)) * (1.0 - r * 0.12)
    b1 = math.exp(-(((abs(r - 0.38 - fold * 0.52)) / 0.022) ** 2)) if 0.26 < r < 0.62 else 0.0
    b2 = math.exp(-(((abs(r - 0.62 - fold * 0.48)) / 0.020) ** 2)) if 0.50 < r < 0.84 else 0.0
    b3 = math.exp(-(((abs(r - 0.80 - fold * 0.42)) / 0.018) ** 2)) if 0.70 < r < 0.94 else 0.0
    hd = max(abs(nx) * 0.866 + abs(ny) * 0.5, abs(ny))
    hex_ring = math.exp(-(((hd - 0.22) / 0.024) ** 2)) * 0.95
    center = math.exp(-((r / 0.11) ** 2)) * 0.85
    bloom = math.exp(-((d_arm / 0.10) ** 2)) * 0.22 * (1.0 - r * 0.5)
    sharp = max(arm, b1 * 0.88, b2 * 0.85, b3 * 0.78, hex_ring, center)
    v = min(1.0, sharp + bloom)
    return _white_hdr(v, max(arm, hex_ring, center)) if v > 0.02 else None


def _f_lightning_bolt(nx, ny, r, ang):
    # High-voltage forked plasma lightning with incandescent thin white core + crackling secondary forks!
    main_pts = [(-0.04, -0.96), (0.16, -0.58), (-0.14, -0.22), (0.20, 0.12), (-0.12, 0.48), (0.08, 0.96)]
    d = min(_dist_seg(nx, ny, main_pts[i][0], main_pts[i][1], main_pts[i + 1][0], main_pts[i + 1][1]) for i in range(len(main_pts) - 1))
    f1 = _dist_seg(nx, ny, -0.14, -0.22, -0.56, 0.10)
    f2 = _dist_seg(nx, ny, 0.20, 0.12, 0.58, 0.44)
    f3 = _dist_seg(nx, ny, 0.16, -0.58, 0.44, -0.30)
    core = math.exp(-((d / 0.032) ** 2))
    fork = max(math.exp(-((f1 / 0.024) ** 2)), math.exp(-((f2 / 0.022) ** 2)), math.exp(-((f3 / 0.020) ** 2))) * 0.82
    plasma = math.exp(-((min(d, f1 * 1.2, f2 * 1.2) / 0.14) ** 2)) * 0.55
    v = min(1.0, core + fork + plasma)
    return _white_hdr(v, max(core, fork)) if v > 0.02 else None


def _f_butterfly(nx, ny, r, ang):
    # Delicate iridescent morpho butterfly with scalloped wing curves, fine lace venation & glowing dust rim
    ax = abs(nx)
    body = math.exp(-((ax / 0.042) ** 2 + ((ny - 0.04) / 0.42) ** 2))
    ant = _dist_seg(ax, ny, 0.02, -0.32, 0.20, -0.62)
    ant_v = math.exp(-((ant / 0.018) ** 2)) * 0.8
    # Upper swept wing & lower rounded swallowtail wing
    u_x = (ax - 0.44) / 0.46
    u_y = (ny + 0.22 + ax * 0.16) / 0.42
    upper = math.hypot(u_x, u_y)
    l_x = (ax - 0.30) / 0.30
    l_y = (ny - 0.32) / 0.34
    lower = math.hypot(l_x, l_y)
    d = min(upper, lower)
    if d < 1.04:
        aa = 1.0 if d < 0.96 else max(0.0, (1.04 - d) / 0.08)
        vein = 0.68 + 0.32 * (abs(math.sin(ang * 9.0 + r * 3.0)) ** 0.5)
        rim = math.exp(-(((0.94 - d) / 0.08) ** 2))
        spot = math.exp(-(((ax - 0.46) / 0.13) ** 2 + ((ny + 0.22) / 0.13) ** 2)) * 0.55
        v = min(1.0, (0.76 * vein + rim * 0.42 + spot) * aa)
        return _white_hdr(v, max(rim, spot))
    v = max(body, ant_v)
    return _white_hdr(v, body) if v > 0.03 else None


def _f_feather(nx, ny, r, ang):
    # Ethereal sculpted seraph plume with curved quill, fine soft barbs & luminous tip
    u = (nx - ny) * 0.7071
    v = (nx + ny) * 0.7071 + 0.08 * math.sin(u * 2.4)
    if abs(u) > 0.90:
        return None
    w = 0.30 * math.sin(((u + 0.90) / 1.80) * math.pi) ** 0.72
    if abs(v) > max(0.02, w * 1.15):
        return None
    env = _clamp(1.0 - (abs(v) / max(0.02, w)) ** 1.8)
    quill = math.exp(-((abs(v) / 0.024) ** 2)) * (1.0 - abs(u) * 0.3)
    barb = 0.72 + 0.28 * math.sin(u * 28.0 - abs(v) * 24.0)
    tip_glow = math.exp(-(((u - 0.55) / 0.38) ** 2)) * 0.35
    val = min(1.0, max(quill, env * barb * 0.92 + tip_glow * env))
    return _white_hdr(val, max(quill, tip_glow)) if val > 0.02 else None


def _f_music_note(nx, ny, r, ang):
    # Sleek glowing beamed musical eighth notes with soft neon bloom aura
    h1 = math.hypot((nx + 0.34) / 0.20, (ny - 0.44) / 0.15)
    h2 = math.hypot((nx - 0.26) / 0.20, (ny - 0.30) / 0.15)
    s1 = _dist_seg(nx, ny, -0.17, -0.42, -0.17, 0.42)
    s2 = _dist_seg(nx, ny, 0.43, -0.56, 0.43, 0.28)
    bm = _dist_seg(nx, ny, -0.17, -0.40, 0.43, -0.54)
    bm2 = _dist_seg(nx, ny, -0.17, -0.25, 0.43, -0.39)
    d_shape = min(max(0.0, h1 - 1.0) * 0.18, max(0.0, h2 - 1.0) * 0.18,
                  max(0.0, s1 - 0.048), max(0.0, s2 - 0.048), max(0.0, bm - 0.055), max(0.0, bm2 - 0.036))
    core = math.exp(-((d_shape / 0.024) ** 2))
    bloom = math.exp(-((d_shape / 0.11) ** 2)) * 0.42
    v = min(1.0, core + bloom)
    return _white_hdr(v, core) if v > 0.02 else None


def _f_pawprint(nx, ny, r, ang):
    # Soft glossy kawaii paw pad + 4 rounded toe beans with delicate glow rim
    main = math.hypot(nx / 0.35, (ny - 0.22) / 0.27)
    toes = [(-0.46, -0.14), (-0.17, -0.40), (0.17, -0.40), (0.46, -0.14)]
    d = min([main] + [math.hypot((nx - tx) / 0.145, (ny - ty) / 0.175) for tx, ty in toes])
    if d > 1.35:
        return None
    if d <= 1.0:
        gloss = math.exp(-(((d - 0.35) / 0.45) ** 2)) * 0.45
        return _white_hdr(0.95, gloss + (1.0 - d) * 0.35)
    bloom = math.exp(-(((d - 1.0) / 0.18) ** 2)) * 0.45
    return _white_hdr(bloom, 0.0) if bloom > 0.02 else None


def _f_cyber_hex(nx, ny, r, ang):
    # Sleek triple-nested sci-fi hologram hex reticle with corner brackets & scan ticks
    ax, ay = abs(nx), abs(ny)
    hd = max(ax * 0.866 + ay * 0.5, ay)
    if hd > 0.98:
        return None
    out_r = math.exp(-(((hd - 0.84) / 0.022) ** 2))
    mid_r = math.exp(-(((hd - 0.58) / 0.015) ** 2)) * 0.82
    in_r = math.exp(-(((hd - 0.30) / 0.013) ** 2)) * 0.65
    spokes = math.exp(-((abs(math.sin(ang * 3.0)) / 0.028) ** 2)) * 0.72 if 0.30 < hd < 0.84 else 0.0
    bloom = math.exp(-(((hd - 0.84) / 0.085) ** 2)) * 0.28
    v = min(1.0, max(out_r, mid_r, in_r, spokes) + bloom)
    return _white_hdr(v, max(out_r, mid_r)) if v > 0.02 else None


def _f_crescent_moon(nx, ny, r, ang):
    # Razor-sharp luminous lunar crescent + twinkling 4-point companion star
    d1 = math.hypot(nx + 0.06, ny)
    d2 = math.hypot(nx - 0.22, ny + 0.06)
    val = 0.0
    core = 0.0
    if d1 < 0.86 and d2 > 0.54:
        in_moon = _clamp((0.76 - d1) * 16.0) * _clamp((d2 - 0.61) * 16.0)
        bloom = math.exp(-((max(0.0, d1 - 0.76) / 0.08) ** 2)) * _clamp((d2 - 0.54) * 8.0) * 0.38
        rim = math.exp(-(((d2 - 0.65) / 0.08) ** 2)) * in_moon
        val = max(in_moon * 0.96, bloom)
        core = max(rim, in_moon * 0.5)
    # 4-point star companion at (0.34, -0.16)
    sx, sy = abs(nx - 0.34), abs(ny + 0.16)
    star = math.exp(-((sy / 0.032) ** 1.4 + (sx / 0.26) ** 1.3)) + math.exp(-((sx / 0.032) ** 1.4 + (sy / 0.26) ** 1.3))
    val = min(1.0, max(val, star))
    core = max(core, star)
    return _white_hdr(val, core) if val > 0.02 else None


def _f_pillar_beam(nx, ny, r, ang):
    # Commercial celestial light shaft: blazing narrow white core + silky vertical energy streaks!
    horiz_core = math.exp(-((abs(nx) / 0.11) ** 2))
    horiz_aura = math.exp(-((abs(nx) / 0.42) ** 2)) * 0.68
    streaks = 0.82 + 0.18 * math.sin(nx * 24.0) * math.cos(ny * 8.0)
    vert = math.sin(((ny + 1.0) * 0.5) * math.pi) ** 0.45
    v = min(1.0, (horiz_core + horiz_aura * streaks) * vert)
    return _white_hdr(v, horiz_core * vert) if v > 0.02 else None


def _f_crystal_shard(nx, ny, r, ang):
    # Razor-faceted luminous diamond crystal prism with internal specular edge refractions
    d = abs(nx) / 0.42 + abs(ny) / 0.88
    if d > 1.15:
        return None
    if d > 1.0:
        return _white_hdr(math.exp(-(((d - 1.0) / 0.08) ** 2)) * 0.42, 0.0)
    facet = 0.62 + 0.34 * (1.0 if nx < 0 else 0.58) * (1.0 if ny < 0 else 0.72)
    edge = math.exp(-(((1.0 - d) / 0.09) ** 2))
    spine = math.exp(-((abs(nx) / 0.04) ** 2)) * 0.65
    return _white_hdr(min(1.0, max(facet, edge)), max(edge, spine))


def _f_flame_wisp(nx, ny, r, ang):
    # Sculpted dancing anime flame tongue with white-hot core and curling tip
    t_h = (ny + 1.0) * 0.5  # 0 top .. 1 bottom
    wob = math.sin(t_h * 5.2) * 0.11 * (1.0 - t_h)
    w = max(0.04, 0.50 * (t_h ** 0.58) * math.sin(min(1.0, t_h * 1.12) * math.pi * 0.92 + 0.12))
    d = math.hypot((nx - wob) / max(0.04, w), (ny - 0.16) / 0.80)
    if d > 1.25:
        return None
    core = math.exp(-((d / 0.44) ** 2))
    body = _clamp(1.0 - d) ** 0.65 if d < 1.0 else 0.0
    bloom = math.exp(-(((max(0.0, d - 0.85)) / 0.24) ** 2)) * 0.35
    return _white_hdr(min(1.0, max(body, bloom)), core)


def _f_bubble_orb(nx, ny, r, ang):
    # Delicate crystal-clear glass mana bubble: ultra-thin luminous Fresnel rim + curved specular crescent!
    if r > 0.97:
        return None
    fresnel = math.exp(-(((r - 0.87) / 0.042) ** 2)) * 0.92
    inner_sheen = (r ** 3.2) * 0.26 if r < 0.88 else 0.0
    # Curved specular crescent highlight top-left
    spec_r = math.exp(-(((r - 0.62) / 0.085) ** 2)) * math.exp(-(((ang + 2.35) / 0.45) ** 2))
    dot_spec = math.exp(-(((nx - 0.36) / 0.08) ** 2 + ((ny - 0.38) / 0.08) ** 2)) * 0.65
    v = min(1.0, fresnel + inner_sheen + spec_r + dot_spec)
    return _white_hdr(v, max(spec_r, dot_spec, fresnel * 0.85)) if v > 0.015 else None


def _f_ribbon_trail(nx, ny, r, ang):
    # Silky connected 3D energy ribbon: hot incandescent core + feathered atmospheric trail glow!
    core = math.exp(-((abs(ny) / 0.12) ** 2))
    glow = math.exp(-((abs(ny) / 0.46) ** 2)) * 0.72
    along = math.sin(((nx + 1.0) * 0.5) * math.pi) ** 0.32
    v = min(1.0, (core + glow) * along)
    return _white_hdr(v, core * along) if v > 0.015 else None


def _f_sun_flare(nx, ny, r, ang):
    # Radiant 16-ray celestial starburst flare + bright solar halo ring
    if r > 0.99:
        return None
    rays_main = (0.5 + 0.5 * math.cos(ang * 8.0)) ** 7 * math.exp(-((r / 0.78) ** 1.3))
    rays_sub = (0.5 + 0.5 * math.cos(ang * 16.0 + 0.2)) ** 5 * math.exp(-((r / 0.56) ** 1.4)) * 0.55
    core = math.exp(-((r / 0.20) ** 2))
    ring = math.exp(-(((r - 0.34) / 0.025) ** 2)) * 0.55
    v = min(1.0, core + rays_main + rays_sub + ring)
    return _white_hdr(v, max(core, rays_main)) if v > 0.02 else None


def _f_pet_beam(nx, ny, r, ang):
    # High-energy focused plasma/laser beam texture for dragon breath & orbital cyber lasers!
    core = math.exp(-((abs(ny) / 0.14) ** 2))
    sheath = math.exp(-((abs(ny) / 0.48) ** 2)) * 0.78
    pulse = 0.82 + 0.18 * math.sin(nx * 18.0)
    ends = math.sin(((nx + 1.0) * 0.5) * math.pi) ** 0.25
    v = min(1.0, (core + sheath * pulse) * ends)
    return _white_hdr(v, core * ends) if v > 0.02 else None


def _f_magic_sigil_outer(nx, ny, r, ang):
    # Secondary outer counter-rotating delicate constellation ring for multi-layer Kill/Aura circles
    if r > 0.99:
        return None
    r1 = math.exp(-(((r - 0.92) / 0.012) ** 2))
    r2 = math.exp(-(((r - 0.82) / 0.010) ** 2)) * 0.78
    ticks = math.exp(-((abs(math.sin(ang * 18.0)) / 0.06) ** 2)) * 0.75 if 0.82 < r < 0.92 else 0.0
    orbs = 0.0
    for k in range(8):
        ak = k * math.tau / 8.0
        orbs = max(orbs, math.exp(-((math.hypot(nx - math.cos(ak) * 0.87, ny - math.sin(ak) * 0.87) / 0.036) ** 2)))
    v = min(1.0, max(r1, r2, ticks, orbs))
    return _white_hdr(v, max(r1, orbs)) if v > 0.02 else None


VFX_SPECS = [
    ("slash_arc", _f_slash_arc),
    ("slash_core", _f_slash_core),
    ("magic_circle", _f_magic_circle),
    ("magic_sigil_outer", _f_magic_sigil_outer),
    ("shockwave_ring", _f_shockwave_ring),
    ("vortex_spiral", _f_vortex_spiral),
    ("glow_soft", _f_glow_soft),
    ("sakura_petal", _f_sakura_petal),
    ("star_spark", _f_star_spark),
    ("heart_glow", _f_heart_glow),
    ("snowflake", _f_snowflake),
    ("lightning_bolt", _f_lightning_bolt),
    ("butterfly", _f_butterfly),
    ("feather", _f_feather),
    ("music_note", _f_music_note),
    ("pawprint", _f_pawprint),
    ("cyber_hex", _f_cyber_hex),
    ("crescent_moon", _f_crescent_moon),
    ("pillar_beam", _f_pillar_beam),
    ("crystal_shard", _f_crystal_shard),
    ("flame_wisp", _f_flame_wisp),
    ("bubble_orb", _f_bubble_orb),
    ("ribbon_trail", _f_ribbon_trail),
    ("sun_flare", _f_sun_flare),
    ("pet_beam", _f_pet_beam),
]

# Matches WardrobeVfxCatalog.BUILTIN exactly for full-color AAA studio thumbnails!
PREVIEW_THEMES = [
    ("sakura", 0xFFB7D5, 0xFF5CA8, "sakura_petal", "magic_circle", False),
    ("phoenix", 0xF97316, 0xFEF08A, "flame_wisp", "shockwave_ring", False),
    ("frost", 0x7DD3FC, 0xE0F2FE, "snowflake", "magic_circle", False),
    ("thunder", 0x38BDF8, 0xFEF08A, "lightning_bolt", "shockwave_ring", False),
    ("void", 0xA855F7, 0xF0ABFC, "crystal_shard", "magic_circle", False),
    ("starlight", 0xFDE047, 0x818CF8, "star_spark", "magic_circle", False),
    ("kawaii_heart", 0xF472B6, 0x67E8F9, "heart_glow", "shockwave_ring", False),
    ("prism", 0xEC4899, 0x22D3EE, "crystal_shard", "shockwave_ring", True),
    ("cyber", 0x22D3EE, 0xEC4899, "cyber_hex", "cyber_hex", False),
    ("jade_dragon", 0x10B981, 0xFDE047, "flame_wisp", "magic_circle", False),
    ("seraph", 0xFEF9C3, 0xFFFFFF, "feather", "magic_circle", False),
    ("ocean", 0x06B6D4, 0xA5F3FC, "bubble_orb", "shockwave_ring", False),
    ("blood_moon", 0xEF4444, 0xFCA5A5, "crescent_moon", "magic_circle", False),
    ("melody", 0xEC4899, 0x38BDF8, "music_note", "shockwave_ring", False),
    ("butterfly", 0x3B82F6, 0xC084FC, "butterfly", "magic_circle", False),
    ("solar", 0xF59E0B, 0xFEF08A, "sun_flare", "shockwave_ring", False),
    ("reaper", 0x6366F1, 0xA855F7, "crescent_moon", "shockwave_ring", False),
    ("paw", 0xFDA4AF, 0xFEF08A, "pawprint", "shockwave_ring", False),
]


def _hex(c: int) -> tuple[int, int, int]:
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def _tint_add(canvas: Image.Image, sprite: Image.Image, rgb: tuple[int, int, int],
              pos: tuple[int, int], size: tuple[int, int], angle: float = 0.0,
              strength: float = 1.0, white_core: float = 0.35) -> None:
    """True additive HDR-like compositing onto RGB canvas so VFX previews genuinely GLOW!"""
    sp = sprite.resize(size, Image.Resampling.BICUBIC)
    if abs(angle) > 0.1:
        sp = sp.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    w, h = sp.size
    cx, cy = pos
    x0, y0 = cx - w // 2, cy - h // 2
    cp = canvas.load()
    spp = sp.load()
    cw, ch = canvas.size
    tr, tg, tb = rgb
    for y in range(h):
        dy = y0 + y
        if dy < 0 or dy >= ch:
            continue
        for x in range(w):
            dx = x0 + x
            if dx < 0 or dx >= cw:
                continue
            r0, g0, b0, a0 = spp[x, y]
            if a0 < 3:
                continue
            af = (a0 / 255.0) * strength
            # Incandescent hot core lifts toward white where sprite brightness is peak
            core = max(0.0, (r0 - 218) / 37.0) * white_core
            ar = (tr * (1.0 - core) + 255.0 * core) * af
            ag = (tg * (1.0 - core) + 255.0 * core) * af
            ab = (tb * (1.0 - core) + 255.0 * core) * af
            br, bg, bb = cp[dx, dy]
            cp[dx, dy] = (
                min(255, int(br + ar)),
                min(255, int(bg + ag)),
                min(255, int(bb + ab)),
            )


def build_previews(imgs: dict[str, Image.Image]) -> None:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    for style_id, c1_hex, c2_hex, p_tex, r_tex, is_prism in PREVIEW_THEMES:
        c1 = _hex(c1_hex)
        c2 = _hex(c2_hex)
        # Sleek dark radial studio canvas with subtle colored vignette
        bg = Image.new("RGB", (256, 256), (11, 15, 26))
        bp = bg.load()
        for y in range(256):
            ny = (y - 127.5) / 127.5
            for x in range(256):
                nx = (x - 127.5) / 127.5
                d = math.hypot(nx, ny)
                glow = math.exp(-((d / 0.85) ** 2))
                bp[x, y] = (
                    min(255, int(10 + c1[0] * 0.14 * glow + c2[0] * 0.06 * glow)),
                    min(255, int(14 + c1[1] * 0.14 * glow + c2[1] * 0.06 * glow)),
                    min(255, int(25 + c1[2] * 0.16 * glow + c2[2] * 0.08 * glow)),
                )

        # 1. Soft atmospheric backdrop bloom
        _tint_add(bg, imgs["glow_soft"], c1, (128, 128), (220, 220), strength=0.45, white_core=0.1)
        # 2. Tilted swirling magic circle / ring
        ring_img = imgs[r_tex]
        _tint_add(bg, ring_img, c2 if not is_prism else (80, 220, 255), (128, 134), (198, 198), angle=18, strength=0.68, white_core=0.25)
        if is_prism:
            _tint_add(bg, imgs["magic_sigil_outer"], (255, 220, 80), (128, 134), (218, 218), angle=-24, strength=0.52)
        # 3. Sweeping glowing slash arc across stage
        _tint_add(bg, imgs["slash_arc"], c1, (128, 124), (235, 118), angle=-22, strength=0.82, white_core=0.4)
        _tint_add(bg, imgs["slash_core"], c2, (128, 124), (225, 102), angle=-22, strength=0.72, white_core=0.85)
        # 4. Hero theme motif dead-center + orbiting smaller companion motifs!
        hero = imgs[p_tex]
        _tint_add(bg, hero, c1, (128, 124), (118, 118), angle=-6, strength=0.98, white_core=0.35)
        satellites = [
            (62, 68, 44, 18, c2), (194, 72, 40, -24, c1),
            (68, 188, 36, -15, c1), (190, 184, 42, 26, c2),
        ]
        if is_prism:
            satellites = [
                (62, 68, 44, 18, (255, 90, 160)), (194, 72, 42, -24, (70, 230, 255)),
                (68, 188, 38, -15, (255, 220, 70)), (190, 184, 42, 26, (150, 110, 255)),
            ]
        for sx, sy, sz, sang, scol in satellites:
            _tint_add(bg, hero, scol, (sx, sy), (sz, sz), angle=sang, strength=0.82, white_core=0.28)
        # 5. Delicate micro-starfield sparkles!
        sparks = [(44, 124, 24), (212, 122, 22), (126, 44, 28), (156, 206, 22), (96, 198, 18)]
        for sx, sy, sz in sparks:
            _tint_add(bg, imgs["star_spark"], c2, (sx, sy), (sz, sz), strength=0.88, white_core=0.75)

        bg.save(PREVIEW_DIR / f"{style_id}.png")


def build_all_vfx() -> dict[str, Image.Image]:
    workers = max(2, min(12, os.cpu_count() or 4))
    imgs: dict[str, Image.Image] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as ex:
        futs = [ex.submit(_render_sdf, name, fn) for name, fn in VFX_SPECS]
        for f in futs:
            name, im = f.result()
            imgs[name] = im

    build_previews(imgs)

    # Save visual contact sheet of all 18 full-color studio previews for immediate inspection!
    scratch = Path(r"C:\Users\yuang\.gemini\antigravity\brain\18f7e90b-f337-4bec-a214-a8c9ddc04be4\scratch")
    scratch.mkdir(parents=True, exist_ok=True)
    cols = 6
    rows = (len(PREVIEW_THEMES) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 144, rows * 156), (9, 13, 22))
    draw = ImageDraw.Draw(sheet)
    for i, (sid, *_) in enumerate(PREVIEW_THEMES):
        pim = Image.open(PREVIEW_DIR / f"{sid}.png").resize((128, 128), Image.Resampling.BICUBIC)
        gx, gy = (i % cols) * 144 + 8, (i // cols) * 156 + 20
        sheet.paste(pim, (gx, gy))
        draw.text((gx + 2, gy - 14), sid, fill=(225, 235, 250))
    sheet.save(scratch / "vfx_studio_previews.png")
    print(f"Generated {len(imgs)} HD 256x256 VFX textures + {len(PREVIEW_THEMES)} full-color studio previews!")
    return imgs


if __name__ == "__main__":
    build_all_vfx()
