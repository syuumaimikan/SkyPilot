"""23 brand-new high-res 128x128 animated cloak pixel-art motifs (smooth looping phase)."""
from __future__ import annotations

import math


def _lerp(c1: tuple[int, int, int], c2: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, t))
    return (
        int(c1[0] + (c2[0] - c1[0]) * t),
        int(c1[1] + (c2[1] - c1[1]) * t),
        int(c1[2] + (c2[2] - c1[2]) * t),
    )


def _hash2(x: int, y: int, seed: int = 0) -> float:
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def _rainbow(t: float) -> tuple[int, int, int]:
    h = (t % 1.0) * 6.0
    i = int(h) % 6
    f = h - int(h)
    q, v = int(255 * (1.0 - f)), int(255 * f)
    return [(255, v, 76), (q, 255, 76), (64, 255, v), (64, q, 255), (v, 86, 255), (255, 76, q)][i]


def _dist_seg(px: float, py: float, x1: float, y1: float, x2: float, y2: float) -> float:
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    if l2 <= 1e-6:
        return math.hypot(px - x1, py - y1)
    t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2))
    return math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))


def cloak_pixel_v2(
    motif: str,
    x: float, y: float, nx: float, ny: float, cx: float, cy: float,
    phase: float,
    p: tuple[int, int, int], s: tuple[int, int, int], a: tuple[int, int, int],
) -> tuple[tuple[int, int, int], tuple[int, int, int, int] | None] | None:
    g: tuple[int, int, int, int] | None = None
    ix, iy = int(x * 2), int(y * 2)
    t_cyc = phase / math.tau

    if motif == "sakura_waterfall":
        # Flowing turquoise-indigo cascade + animated falling luminous pink sakura petals!
        stream = 0.5 + 0.5 * math.sin((ny * 6.0 - t_cyc * 2.0) * math.tau + math.sin(nx * 9.0) * 1.1)
        col = _lerp((18, 46, 86), (92, 198, 232), stream * (0.45 + 0.55 * (1.0 - abs(cx) * 0.7)))
        if stream > 0.78 and abs(cx) < 0.72:
            col = _lerp(col, (236, 252, 255), 0.55)
            g = (190, 245, 255, 130)
        for k in range(11):
            px_p = (k * 7.3 + math.sin(phase + k) * 3.8) % 38.0 + 1.0
            py_p = (k * 11.7 + t_cyc * 64.0) % 64.0
            d = math.hypot((x - px_p) / 2.2, (y - py_p) / 1.5)
            if d < 1.0:
                col = _lerp((255, 168, 206), (255, 244, 250), 1.0 - d)
                g = (255, 190, 220, 210)
        return col, g

    if motif == "galaxy_vortex":
        dx, dy = (x - 19.5) / 18.0, (y - 31.5) / 24.0
        r = math.hypot(dx, dy)
        ang = math.atan2(dy, dx)
        arm = math.sin(ang * 3.0 - r * 7.5 + phase) * 0.5 + 0.5
        col = _lerp((8, 7, 26), p, arm * math.exp(-r * 0.85))
        col = _lerp(col, a, (arm ** 3) * math.exp(-r * 0.65) * 0.85)
        if r < 0.24:
            col = _lerp(col, (255, 252, 255), 1.0 - r / 0.24)
            g = (235, 240, 255, 230)
        elif arm > 0.72 and r < 0.95:
            g = (*col, int(190 * (arm - 0.5)))
        if _hash2(ix // 2, iy // 2, 501) > 0.968:
            tw = 0.5 + 0.5 * math.sin(phase + ix * 0.7 + iy * 0.5)
            col = (255, 255, 255)
            g = (220, 245, 255, int(130 + 120 * tw))
        return col, g

    if motif == "neon_pulse":
        col = _lerp((10, 12, 28), (22, 16, 46), ny)
        band = int(nx * 8.0)
        h_bar = 0.28 + 0.55 * (0.5 + 0.5 * math.sin(phase * 2.0 + band * 1.15))
        in_bar = (1.0 - ny) < h_bar and ((ix % 10) >= 2)
        if in_bar:
            c_bar = _lerp(p, a, 1.0 - ny)
            seg = (iy % 6) < 4
            if seg:
                col = c_bar
                g = (*c_bar, 225)
        # Running laser scanline
        scan = (t_cyc * 64.0) % 64.0
        if abs(y - scan) < 1.3:
            col = (224, 252, 255)
            g = (120, 240, 255, 240)
        return col, g

    if motif == "koi_pond":
        # Rippling jade water + 2 animated swimming koi!
        rip = math.sin(math.hypot(x - 19.5, y - 32.0) * 0.8 - phase * 2.0) * 0.5 + 0.5
        col = _lerp((6, 44, 48), (16, 108, 96), rip * 0.5 + 0.25)
        if rip > 0.88:
            col = _lerp(col, (167, 243, 208), 0.45)
        for k, (base_col, spot_col) in enumerate([((255, 112, 42), (255, 248, 240)), ((255, 248, 244), (238, 48, 56))]):
            ang = phase + k * math.pi
            kx = 19.5 + math.cos(ang) * 9.5
            ky = 32.0 + math.sin(ang) * 16.0
            tx, ty = -math.sin(ang), math.cos(ang)
            rx, ry = x - kx, y - ky
            along = rx * tx + ry * ty
            across = -rx * ty + ry * tx + math.sin(along * 0.5 - phase * 2.0) * 1.1
            width = 3.2 * max(0.0, 1.0 - abs(along) / 9.5)
            if -9.5 <= along <= 7.5 and abs(across) < width:
                col = spot_col if (along > 1.0 and abs(across) < 1.6) else base_col
                g = (*col, 175)
        return col, g

    if motif == "crimson_moon":
        col = _lerp((14, 6, 20), (42, 10, 24), ny)
        d = math.hypot(x - 19.5, y - 22.0)
        pulse = 0.6 * math.sin(phase)
        if d < 10.5 + pulse:
            t = d / (10.5 + pulse)
            col = _lerp((255, 210, 180), (210, 26, 42), t ** 0.7)
            g = (255, 76, 86, int(235 - t * 80))
        elif d < 14.5:
            glow_f = max(0.0, 1.0 - (d - 10.5) / 4.0)
            col = _lerp(col, (220, 38, 38), glow_f * 0.65)
            g = (220, 38, 38, int(140 * glow_f))
        # Animated mist layer near bottom
        mist = math.sin(nx * 5.0 + phase) * 3.0 + 51.0
        if y > mist:
            col = _lerp(col, (148, 120, 148), min(0.55, (y - mist) / 14.0))
        return col, g

    if motif == "crystal_cathedral":
        # Glowing leaded stained-glass rose window with rotating prism rays!
        dx, dy = x - 19.5, y - 28.0
        r = math.hypot(dx, dy)
        ang = math.atan2(dy, dx)
        spokes = abs(math.sin((ang + phase * 0.25) * 6.0))
        rings = abs(math.sin(r * 0.45))
        if spokes < 0.12 or rings < 0.11 or x < 3.0 or x > 36.5:
            col = (24, 24, 34)
        else:
            hue = (ang / math.tau + r * 0.03 - t_cyc) % 1.0
            col = _rainbow(hue)
            shimmer = 0.78 + 0.25 * math.sin(phase + r * 0.3)
            col = (min(255, int(col[0] * shimmer)), min(255, int(col[1] * shimmer)), min(255, int(col[2] * shimmer)))
            g = (*col, 195)
        return col, g

    if motif == "pastel_clouds":
        col = _lerp((186, 214, 255), (255, 198, 228), ny)
        # Animated drifting puffy clouds
        for k in range(5):
            cx_c = (k * 13.0 + t_cyc * 40.0) % 48.0 - 4.0
            cy_c = 12.0 + k * 11.0
            d1 = math.hypot((x - cx_c) / 7.5, (y - cy_c) / 3.8)
            d2 = math.hypot((x - (cx_c + 4.5)) / 5.5, (y - (cy_c - 1.5)) / 3.2)
            if d1 < 1.0 or d2 < 1.0:
                col = _lerp((255, 252, 255), (255, 232, 246), ny * 0.35)
                g = (255, 245, 252, 130)
        if _hash2(ix // 2, iy // 2, 509) > 0.962:
            tw = 0.5 + 0.5 * math.sin(phase + ix + iy)
            col = (255, 252, 210)
            g = (255, 248, 195, int(130 + 115 * tw))
        return col, g

    if motif == "thunder_storm":
        col = _lerp((12, 16, 36), (26, 36, 68), ny)
        # Forked zigzag lightning bolt scrolling phase!
        bolt_x = 19.5 + math.sin(y * 0.42 + phase * 2.0) * 6.5 + math.cos(y * 0.9 - phase) * 2.8
        dist = abs(x - bolt_x)
        flash = 0.65 + 0.35 * math.sin(phase * 3.0)
        if dist < 1.4:
            col = (255, 255, 240)
            g = (185, 242, 255, int(250 * flash))
        elif dist < 4.5:
            f = max(0.0, 1.0 - (dist - 1.4) / 3.1) * flash
            col = _lerp(col, (56, 189, 248), f)
            g = (56, 189, 248, int(190 * f))
        return col, g

    if motif == "matcha_gold":
        col = _lerp((12, 62, 34), (28, 114, 62), ny * 0.8 + 0.1 * math.sin(nx * 6.0))
        # Traditional Seigaiha subtle waves
        wx = (x % 10.0) - 5.0
        wy = (y % 8.0) - 4.0
        if abs(math.hypot(wx, wy) - 4.2) < 0.6:
            col = _lerp(col, (52, 168, 98), 0.38)
        # Drifting shimmering gold leaf flakes!
        for k in range(12):
            gx = (k * 9.7 + math.sin(phase + k) * 2.5) % 36.0 + 2.0
            gy = (k * 13.3 + t_cyc * 64.0) % 64.0
            if abs(x - gx) + abs(y - gy) < 2.2:
                col = (253, 224, 71)
                g = (253, 224, 71, 215)
        return col, g

    if motif == "ocean_jellyfish":
        col = _lerp((4, 24, 52), (10, 68, 108), ny)
        # Floating jellyfish bell & waving tentacles
        jy = 24.0 + math.sin(phase) * 4.5
        bell = math.hypot((x - 19.5) / 10.5, (y - jy) / 7.5)
        if bell < 1.0 and y <= jy + 1.5:
            col = _lerp((224, 248, 255), (34, 211, 238), bell)
            g = (110, 242, 255, int(235 - bell * 90))
        elif y > jy + 1.0 and y < jy + 26.0:
            for ti in (-6.5, -2.5, 2.5, 6.5):
                tx = 19.5 + ti + math.sin(y * 0.45 - phase * 2.0 + ti) * 1.9
                if abs(x - tx) < 1.05:
                    col = (165, 243, 252)
                    g = (103, 232, 249, 210)
        return col, g

    if motif == "phoenix_rising":
        col = _lerp((38, 8, 12), (120, 20, 14), ny)
        # Symmetrical sweeping rising flame wings on cloak canvas!
        ax = abs(x - 19.5)
        wing_y = 26.0 + ax * 0.42 - math.sin(ax * 0.35 - phase * 2.0) * 3.5
        d_wing = abs(y - wing_y)
        if d_wing < 7.5 - ax * 0.18:
            t = max(0.0, 1.0 - d_wing / max(1.0, 7.5 - ax * 0.18))
            col = _lerp((239, 68, 68), (254, 240, 138), t)
            g = (*col, int(245 * t))
        # Central phoenix crest
        if ax < 3.2 and 14.0 <= y <= 46.0:
            col = _lerp((249, 115, 22), (255, 250, 205), 1.0 - ax / 3.2)
            g = (255, 215, 95, 235)
        return col, g

    if motif == "pixel_hearts":
        bg = _lerp((255, 234, 244), (255, 204, 226), ny)
        if ((ix // 8) + (iy // 8)) % 2 == 0:
            bg = _lerp(bg, (255, 246, 250), 0.4)
        col = bg
        for k in range(7):
            hx = 8.0 + (k % 3) * 11.5
            hy = (k * 16.5 - t_cyc * 64.0) % 64.0
            dx, dy = (x - hx) / 4.2, -(y - hy) / 4.2
            hv = (dx * dx + dy * dy - 1.0) ** 3 - dx * dx * (dy ** 3)
            if hv < 0.0:
                col = (255, 240, 246) if hv < -0.45 else (244, 63, 94)
                g = (255, 130, 175, 210)
        return col, g

    if motif == "emerald_circuit":
        col = (6, 20, 24)
        col_i = ix // 6
        spd = 1.0 + (col_i % 3)
        ry = (iy - int(t_cyc * 128.0 * spd) + col_i * 23) % 128
        if (ix % 6) in (1, 2, 3) and ry < 42:
            bright = 1.0 - ry / 42.0
            if (ry % 4) < 3:
                col = _lerp((16, 185, 129), (209, 250, 229), bright ** 2)
                g = (*col, int(235 * bright))
        return col, g

    if motif == "sunset_torii":
        col = _lerp((49, 46, 129), (251, 113, 133), min(1.0, ny * 1.15))
        if ny > 0.55:
            col = _lerp(col, (253, 224, 71), (ny - 0.55) / 0.45 * 0.7)
        # Glowing sunset sun behind torii
        d_sun = math.hypot(x - 19.5, y - 34.0)
        if d_sun < 11.5:
            col = _lerp((255, 248, 200), (251, 146, 60), d_sun / 11.5)
            g = (255, 215, 125, int(225 - d_sun * 12))
        # Vermilion Torii gate silhouette
        in_torii = (
            (5.0 <= x <= 34.0 and 20.0 <= y <= 23.0)
            or (8.0 <= x <= 31.0 and 26.5 <= y <= 29.0)
            or (11.0 <= x <= 14.0 and 21.0 <= y <= 54.0)
            or (25.0 <= x <= 28.0 and 21.0 <= y <= 54.0)
        )
        if in_torii:
            col = (220, 38, 38)
            g = (244, 63, 94, 150)
        # Floating golden firefly sparks
        if _hash2(ix // 2, int((iy + t_cyc * 128) % 128) // 2, 521) > 0.972:
            col = (255, 250, 200)
            g = (255, 240, 160, 230)
        return col, g

    if motif == "starlight_ribbon":
        col = _lerp((22, 18, 56), (52, 34, 96), ny)
        r1 = abs(x - (19.5 + math.sin(ny * math.tau * 1.5 - phase) * 11.0))
        r2 = abs(x - (19.5 - math.sin(ny * math.tau * 1.5 + phase) * 11.0))
        if r1 < 3.0:
            col = _lerp((196, 181, 253), (255, 255, 255), 1.0 - r1 / 3.0)
            g = (216, 195, 255, 225)
        elif r2 < 2.6:
            col = _lerp((244, 114, 182), (254, 240, 138), 1.0 - r2 / 2.6)
            g = (255, 195, 235, 210)
        return col, g

    if motif == "abyssal_void":
        col = (9, 5, 20)
        # Pulsing vertical dimensional rift + orbiting violet energy waves
        rift_x = 19.5 + math.sin(ny * 8.0 + phase * 2.0) * 2.2
        d = abs(x - rift_x)
        rift_w = 5.5 * math.sin(ny * math.pi)
        if d < rift_w * 0.45:
            col = (255, 240, 255)
            g = (240, 171, 252, 255)
        elif d < rift_w:
            t = 1.0 - (d - rift_w * 0.45) / max(0.5, rift_w * 0.55)
            col = _lerp((126, 34, 206), (232, 121, 249), t)
            g = (*col, int(235 * t))
        else:
            ring = math.sin(math.hypot(x - 19.5, (y - 32.0) * 0.65) * 0.9 - phase * 2.0)
            if ring > 0.72:
                col = _lerp(col, (168, 85, 247), 0.6)
                g = (192, 132, 252, 160)
        return col, g

    if motif == "honey_drip":
        col = _lerp((255, 247, 214), (253, 230, 138), ny)
        # Subtle honeycomb grid on lower half
        if ((ix // 6) + (iy // 6)) % 3 == 0:
            col = _lerp(col, (245, 158, 11), 0.22)
        # Animated dripping thick golden honey canopy from top!
        drip = 22.0 + 13.0 * math.sin(x * 0.45) + 5.0 * math.cos(x * 0.95 + phase) + 4.0 * math.sin(phase)
        if y < drip:
            col = _lerp((245, 158, 11), (253, 224, 71), 1.0 - y / max(1.0, drip))
            if y > drip - 2.5:
                col = (255, 250, 205)
                g = (253, 224, 71, 225)
        return col, g

    if motif == "frost_aurora":
        col = _lerp((10, 44, 78), (24, 86, 132), ny)
        rib = math.exp(-((nx - 0.5 + 0.25 * math.sin(ny * 4.0 - phase)) ** 2) * 12.0)
        col = _lerp(col, (125, 211, 252), rib * 0.75)
        if rib > 0.45:
            g = (165, 243, 252, int(rib * 200))
        # Animated falling snowflakes
        for k in range(14):
            sx = (k * 8.3 + math.sin(phase + k * 0.9) * 3.0) % 38.0 + 1.0
            sy = (k * 10.7 + t_cyc * 64.0) % 64.0
            if (abs(x - sx) < 1.6 and abs(y - sy) < 0.6) or (abs(y - sy) < 1.6 and abs(x - sx) < 0.6):
                col = (255, 255, 255)
                g = (224, 242, 254, 240)
        return col, g

    if motif == "royal_butterfly":
        col = _lerp((12, 16, 36), (24, 28, 64), ny)
        ax = abs(x - 19.5)
        # Upper & lower butterfly wing silhouette centered at y=30
        u_d = math.hypot((ax - 9.0) / 8.8, (y - 23.0) / 9.5)
        l_d = math.hypot((ax - 6.8) / 6.8, (y - 38.5) / 8.5)
        if u_d < 1.0 or l_d < 1.0:
            shim = 0.5 + 0.5 * math.sin(ax * 0.45 - phase * 2.0 + y * 0.15)
            col = _lerp((29, 78, 216), (147, 197, 253), shim)
            if u_d > 0.82 or l_d > 0.82:
                col = (15, 23, 42)
            else:
                g = (*col, int(160 + 80 * shim))
        return col, g

    if motif == "lantern_night":
        col = _lerp((10, 18, 52), (30, 42, 92), ny)
        for k in range(9):
            lx = 5.0 + (k * 11.3) % 30.0 + math.sin(phase + k) * 1.4
            ly = (k * 13.7 - t_cyc * 64.0) % 64.0
            if abs(x - lx) < 2.3 and abs(y - ly) < 3.0:
                col = _lerp((255, 246, 175), (249, 115, 22), abs(y - ly) / 3.0)
                g = (255, 210, 100, 240)
            elif math.hypot(x - lx, y - ly) < 5.2:
                f = max(0.0, 1.0 - math.hypot(x - lx, y - ly) / 5.2)
                col = _lerp(col, (251, 191, 36), f * 0.45)
        return col, g

    if motif == "vaporwave_grid":
        if ny < 0.56:
            col = _lerp((30, 20, 72), (190, 48, 128), ny / 0.56)
            d = math.hypot(x - 19.5, y - 22.0)
            if d < 11.5:
                stripe = ((y + t_cyc * 8.0) % 3.2) < 2.2 or y < 22.0
                if stripe:
                    col = _lerp((254, 240, 138), (244, 63, 148), max(0.0, (y - 10.5) / 23.0))
                    g = (*col, 225)
        else:
            # Scrolling perspective neon horizon grid!
            col = (18, 12, 42)
            depth = max(0.05, ny - 0.54)
            gx = abs(((x - 19.5) / (depth * 18.0)) % 1.0 - 0.5)
            gy = abs((0.35 / depth - t_cyc * 3.0) % 1.0 - 0.5)
            if gx > 0.44 or gy > 0.43:
                col = (34, 211, 238)
                g = (34, 211, 238, 225)
        return col, g

    if motif == "amethyst_geode":
        col = _lerp((36, 14, 68), (88, 28, 135), ny)
        facet = math.sin(x * 0.55 + y * 0.35 + phase) * 0.5 + 0.5
        facet2 = math.cos(x * 0.4 - y * 0.6 - phase) * 0.5 + 0.5
        v = facet * 0.6 + facet2 * 0.4
        col = _lerp((59, 7, 100), (232, 121, 249), v)
        if v > 0.78:
            col = _lerp(col, (255, 250, 255), (v - 0.78) / 0.22)
            g = (233, 213, 255, 235)
        return col, g

    if motif == "celestial_compass":
        col = _lerp((10, 16, 40), (20, 30, 68), ny)
        dx, dy = x - 19.5, y - 31.5
        r = math.hypot(dx, dy)
        ang = math.atan2(dy, dx) + phase * 0.5
        # Concentric golden astrolabe rings + 8-point rotating star compass!
        if abs(r - 13.5) < 1.05 or abs(r - 8.5) < 0.85:
            col = (250, 204, 21)
            g = (253, 224, 71, 220)
        star_r = 12.5 * (0.42 + 0.58 * (abs(math.cos(ang * 4.0)) ** 3))
        if r < star_r and r > 1.8:
            col = _lerp((254, 240, 138), (56, 189, 248), r / 12.5)
            g = (*col, 210)
        elif r <= 1.8:
            col = (255, 255, 255)
            g = (255, 250, 200, 255)
        return col, g

    return None

