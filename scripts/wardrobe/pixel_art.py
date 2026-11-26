"""High-craft 128x128 pixel-art texture & emissive glow mask generator for SkyPilot's 180+ wardrobe cosmetics."""
from __future__ import annotations

import math
from pathlib import Path
from PIL import Image


def hex_rgb(c: int) -> tuple[int, int, int]:
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def lerp_rgb(c1: tuple[int, int, int], c2: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    t = max(0.0, min(1.0, t))
    return (
        int(c1[0] + (c2[0] - c1[0]) * t),
        int(c1[1] + (c2[1] - c1[1]) * t),
        int(c1[2] + (c2[2] - c1[2]) * t),
    )


def shade(rgb: tuple[int, int, int], f: float) -> tuple[int, int, int]:
    return (
        max(0, min(255, int(rgb[0] * f))),
        max(0, min(255, int(rgb[1] * f))),
        max(0, min(255, int(rgb[2] * f))),
    )


def hash2(x: int, y: int, seed: int = 0) -> float:
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# ==============================================================================
# 1. CLOAK TEXTURES (128x128 per frame: front art 80x128 at x=0..80, lining 80..120, trim 120..128)
# ==============================================================================
def paint_cloak_texture(
    motif: str,
    primary: int,
    secondary: int,
    accent: int,
    frames: int,
    out_path: Path,
    glow_path: Path | None,
) -> bool:
    """Renders a crisp 80x128 high-res illustrated cloak texture (+ animated strip & glow mask)."""
    w, h = 128, 128 * frames
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0)) if glow_path else None
    base_px = base.load()
    glow_px = glow.load() if glow is not None else None

    p_rgb = hex_rgb(primary)
    s_rgb = hex_rgb(secondary)
    a_rgb = hex_rgb(accent)
    has_glow = False

    lining_dark = shade(s_rgb, 0.40)
    lining_mid = shade(s_rgb, 0.56)
    trim_col = lerp_rgb(p_rgb, a_rgb, 0.52)

    for fi in range(frames):
        oy = fi * 128
        phase = (fi / max(1, frames)) * math.tau

        # Inner quilted silk lining (x=80..120) & metallic stitched trim (x=120..128)
        for y in range(128):
            shimmer = 0.84 + 0.16 * math.sin(y * 0.14 + phase)
            tc = shade(trim_col, shimmer)
            for x in range(80, 120):
                edge = (x <= 81 or x >= 118 or y < 4 or y > 123)
                diamond = ((x + y) % 8 == 0) or ((x - y) % 8 == 0)
                c = tc if edge else (lining_mid if diamond else lining_dark)
                base_px[x, oy + y] = (*c, 255)
            for x in range(120, 128):
                bevel = 1.12 if x in (122, 123) else (0.82 if x in (120, 127) else 1.0)
                base_px[x, oy + y] = (*shade(tc, bevel), 255)

        # Front illustrated 80x128 tapestry canvas (x=0..79, y=0..127 -> virtual 40x64 coords at 0.5px precision!)
        for y in range(128):
            ny = y / 127.0
            vy = y * 0.5
            for x in range(80):
                nx = x / 79.0
                vx = x * 0.5
                cx, cy = (vx - 19.5) / 19.5, (vy - 31.5) / 31.5
                col, gcol = _cloak_pixel(motif, vx, vy, nx, ny, cx, cy, phase, p_rgb, s_rgb, a_rgb)

                # Rich velvet vertical drapery folds + subtle woven micro-grain
                fold = 0.93 + 0.08 * math.sin(nx * math.pi * 3.0) + 0.03 * math.cos(nx * math.pi * 6.0) - 0.06 * ny
                weave = 0.985 + 0.03 * (((x & 1) ^ (y & 1)))
                # Subtle ornate stitched corner border on eligible motifs
                in_edge = (x <= 1 or x >= 78 or y <= 1 or y >= 126)
                in_filigree = ((x in (3, 76) and 4 <= y <= 123) or (y in (3, 124) and 3 <= x <= 76)) and ((x + y) % 4 < 2)
                if in_edge and motif not in ("ivory_serpent", "fallen_seraph", "fuji_sakura", "strawberry_milk"):
                    col = lerp_rgb(col, shade(trim_col, 0.92), 0.45)
                elif in_filigree and motif in ("royal_mantle", "monarch_crown", "hyperion", "eclipse", "gothic_cross", "starlight"):
                    col = lerp_rgb(col, a_rgb, 0.42)
                    if glow_px is not None:
                        gcol = (*a_rgb, 115)
                else:
                    col = shade(col, fold * weave)

                base_px[x, oy + y] = (*col, 255)
                if glow_px is not None and gcol is not None:
                    glow_px[x, oy + y] = gcol
                    has_glow = True

    out_path.parent.mkdir(parents=True, exist_ok=True)
    base.save(out_path)
    if glow_path is not None and glow is not None and has_glow:
        glow.save(glow_path)
        return True
    if glow_path is not None and glow_path.exists():
        glow_path.unlink()
    return False


def _cloak_pixel(
    motif: str, x: float, y: float, nx: float, ny: float, cx: float, cy: float,
    phase: float, p: tuple[int, int, int], s: tuple[int, int, int], a: tuple[int, int, int],
) -> tuple[tuple[int, int, int], tuple[int, int, int, int] | None]:
    g: tuple[int, int, int, int] | None = None
    ix, iy = int(x * 2), int(y * 2)

    if motif == "strawberry_milk":
        # Ultra-cute pastel strawberry-milk gingham + scalloped cream lace hem + floating glowing hearts & satin ribbon!
        bg = lerp_rgb((255, 224, 236), (255, 186, 212), ny)
        # Soft pastel gingham plaid weave
        if (ix // 6) % 2 == 0:
            bg = lerp_rgb(bg, (255, 240, 246), 0.35)
        if (iy // 6) % 2 == 0:
            bg = lerp_rgb(bg, (255, 204, 224), 0.28)
        col = bg
        # Cream scalloped lace trim along bottom & sides
        scallop = 56.5 + abs(math.sin(x * 0.95)) * 2.8
        if y >= scallop:
            col = (255, 250, 252)
            if y < scallop + 1.2:
                col = (255, 168, 198)
            elif ((ix + iy) % 4 == 0) and y > scallop + 1.8:
                col = (255, 224, 236)
        if x < 2.2 or x > 37.8:
            col = (255, 248, 252)
        # Central satin pink ribbon bow near top (y=12..22)
        bow_l = math.hypot((x - 14.2) / 5.2, (y - 16.5) / 3.4)
        bow_r = math.hypot((x - 25.3) / 5.2, (y - 16.5) / 3.4)
        knot = math.hypot((x - 19.75) / 2.2, (y - 16.5) / 2.0)
        tail_l = _dist_seg(x, y, 18.0, 18.0, 13.0, 29.0) < 1.35
        tail_r = _dist_seg(x, y, 21.5, 18.0, 26.5, 29.0) < 1.35
        if bow_l < 1.0 or bow_r < 1.0 or tail_l or tail_r:
            col = lerp_rgb((244, 114, 182), (251, 207, 232), 0.35 if (bow_l < 0.55 or bow_r < 0.55) else 0.0)
            g = (255, 175, 215, 145)
        if knot < 1.0:
            col = (255, 245, 185)
            g = (255, 240, 175, 210)
        # Cute animated floating pixel hearts + starlight sparkles!
        hearts = [(11.5, 34.0, 3.2), (28.0, 38.0, 3.0), (19.75, 44.5, 4.2), (10.5, 49.5, 2.4), (29.5, 27.5, 2.2)]
        for idx, (hx, hy, hr) in enumerate(hearts):
            hy_a = hy + math.sin(phase + idx * 1.3) * 1.4
            dx = (x - hx) / hr
            dy = -(y - hy_a) / hr
            hval = (dx * dx + dy * dy - 1.0) ** 3 - dx * dx * (dy ** 3)
            if hval < 0.0:
                col = (255, 248, 252) if hval < -0.45 else (244, 63, 132)
                g = (255, 140, 195, 210)
        # Twinkling cream-gold star dots
        if hash2(ix // 2, iy // 2, 311) > 0.965:
            tw = 0.5 + 0.5 * math.sin(phase + ix + iy)
            if tw > 0.45:
                col = (255, 255, 240)
                g = (255, 245, 210, int(140 + 110 * tw))
        return col, g

    if motif == "aurora":
        bg = lerp_rgb((8, 14, 34), (16, 26, 56), ny)
        w1 = math.sin(nx * 5.2 + ny * 3.1 - phase) * 0.5 + 0.5
        w2 = math.cos(nx * 3.4 - ny * 4.2 + phase) * 0.5 + 0.5
        ribbon = math.exp(-((nx - 0.5 + 0.24 * math.sin(ny * 4.5 - phase)) ** 2) * 14.0)
        ribbon2 = math.exp(-((nx - 0.5 - 0.22 * math.cos(ny * 3.8 + phase)) ** 2) * 18.0)
        c_aur = lerp_rgb(p, a, w1)
        c_aur = lerp_rgb(c_aur, s, w2 * 0.45)
        col = lerp_rgb(bg, c_aur, min(1.0, ribbon * 0.95 + ribbon2 * 0.65))
        if ribbon > 0.38:
            g = (*c_aur, int(min(255, ribbon * 225)))
        if hash2(ix // 2, iy // 2, 7) > 0.982:
            col = (242, 252, 255)
            g = (220, 255, 255, 230)
        return col, g

    if motif == "midnight":
        col = lerp_rgb((10, 14, 36), (26, 34, 74), ny)
        stars = [(10, 14), (24, 10), (30, 22), (16, 28), (12, 42), (26, 48), (20, 36), (8, 52), (32, 40)]
        for i in range(len(stars) - 1):
            x1, y1 = stars[i]
            x2, y2 = stars[i + 1]
            if _dist_seg(x, y, x1, y1, x2, y2) < 0.65:
                col = lerp_rgb(col, s, 0.58)
        for idx, (sx, sy) in enumerate(stars):
            d = math.hypot(x - sx, y - sy)
            tw = 0.55 + 0.45 * math.sin(phase + idx * 1.1)
            if d < 1.25 or (abs(x - sx) <= 2.2 and abs(y - sy) < 0.55) or (abs(y - sy) <= 2.2 and abs(x - sx) < 0.55):
                col = lerp_rgb(a, (255, 255, 255), tw)
                g = (*col, int(175 + 80 * tw))
        if hash2(ix // 2, iy // 2, 13) > 0.965:
            tw = 0.5 + 0.5 * math.sin(phase + x + y)
            col = lerp_rgb(col, (225, 240, 255), tw)
            if tw > 0.65:
                g = (205, 230, 255, 175)
        return col, g

    if motif == "ember":
        bg = lerp_rgb((24, 10, 16), (92, 24, 22), ny)
        flame = math.sin(nx * 8.0 + math.sin(ny * 5.0 + phase)) * 0.14
        heat = max(0.0, (ny + flame - 0.34) / 0.66)
        col = lerp_rgb(bg, p, min(1.0, heat * 1.2))
        if heat > 0.62:
            col = lerp_rgb(col, a, (heat - 0.62) / 0.38)
            g = (*col, int(min(255, (heat - 0.52) * 340)))
        ry = int((iy + phase / math.tau * 128) % 128)
        if hash2(ix // 3, ry // 3, 21) > 0.945:
            col = (255, 246, 185)
            g = (255, 225, 115, 245)
        return col, g

    if motif == "prism":
        t = (nx * 0.75 + ny * 0.95 - phase / math.tau) % 1.0
        col = _rainbow(t)
        shimmer = 0.82 + 0.2 * math.sin((nx - ny) * 18.0 + phase)
        col = shade(col, shimmer)
        if abs(math.sin((nx + ny) * 10.0 - phase)) > 0.85:
            col = lerp_rgb(col, (255, 255, 255), 0.48)
            g = (*col, 185)
        return col, g

    if motif == "tide":
        bg = lerp_rgb((8, 28, 64), (16, 70, 118), ny)
        wy = (ny * 5.0 + 0.32 * math.sin(nx * 6.5 + phase) + 0.16 * math.cos(nx * 11.0 - phase)) % 1.0
        if wy < 0.16:
            col = lerp_rgb((215, 248, 255), (255, 255, 255), 1.0 - wy / 0.16)
            g = (185, 248, 255, 145)
        elif wy < 0.42:
            col = lerp_rgb(p, s, (wy - 0.16) / 0.26)
        else:
            col = lerp_rgb(bg, s, 0.35)
        return col, g

    if motif == "eclipse":
        bg = lerp_rgb((12, 13, 24), (24, 20, 38), ny)
        d = math.hypot(x - 19.5, y - 26.0)
        ang = math.atan2(y - 26.0, x - 19.5)
        flare = 1.6 * math.sin(ang * 5.0 + phase) + 0.9 * math.cos(ang * 9.0 - phase * 2.0)
        ring = abs(d - (11.0 + 0.45 * math.sin(phase)))
        if d < 9.5:
            col = (7, 7, 13)
        elif ring < 2.2 + max(0.0, flare) * 0.8:
            t = max(0.0, 1.0 - ring / (2.4 + max(0.0, flare)))
            col = lerp_rgb(s, a, t)
            if ring < 1.2:
                col = lerp_rgb(col, (255, 252, 215), 0.68)
            g = (*col, int(min(255, 120 + t * 135)))
        else:
            glow_fall = math.exp(-max(0.0, d - 11.0) * 0.18)
            col = lerp_rgb(bg, s, glow_fall * 0.58)
        return col, g

    if motif == "phantom":
        bg = lerp_rgb((10, 18, 40), (16, 42, 64), ny)
        w = 0.5 + 0.5 * math.sin(nx * 7.0 + math.sin(ny * 5.0 - phase) * 2.0 + phase)
        wisp = math.exp(-((nx - 0.5 + 0.25 * math.sin(ny * 5.0 - phase)) ** 2) * 10.0)
        col = lerp_rgb(bg, p, min(1.0, w * 0.55 + wisp * 0.5))
        if wisp > 0.42:
            col = lerp_rgb(col, a, 0.52)
            g = (*p, int(min(255, wisp * 220)))
        return col, g

    if motif == "void_walker":
        bg = lerp_rgb((12, 5, 28), (30, 9, 62), ny)
        gx = (x + phase * 1.5) % 10.0
        gy = (y - phase * 2.2) % 12.0
        diamond = abs(gx - 5.0) + abs(gy - 6.0)
        if abs(diamond - 3.0) < 0.65 or (diamond < 1.3 and (int(x // 10) + int(y // 12)) % 2 == 0):
            pulse = 0.55 + 0.45 * math.sin(phase + x * 0.3 + y * 0.2)
            col = lerp_rgb(s, a, pulse)
            g = (*col, int(160 + 95 * pulse))
        else:
            col = bg
        return col, g

    if motif == "sakura":
        bg = lerp_rgb((46, 18, 36), (92, 34, 62), ny)
        col = bg
        if _dist_seg(x, y, 39, 6, 12, 26) < 1.55 or _dist_seg(x, y, 26, 15, 18, 9) < 1.1:
            col = (64, 38, 36)
        petals = [(10, 12), (28, 18), (16, 26), (31, 34), (9, 40), (22, 46), (14, 54), (30, 56), (20, 10), (26, 30)]
        for i, (px, py) in enumerate(petals):
            dy = (py + (phase / math.tau) * 64.0 + i * 7.0) % 64.0
            dx = (px + math.sin(phase + i) * 3.0) % 40.0
            d = math.hypot(x - dx, y - dy)
            if d < 2.2:
                col = (255, 236, 244) if d < 0.95 else p
                if d < 1.1:
                    g = (255, 205, 226, 145)
        return col, g

    if motif == "hyperion":
        bg = lerp_rgb((16, 20, 32), (30, 24, 40), ny)
        d = math.hypot(x - 19.5, y - 30.0)
        ring = abs(d - ((phase / math.tau * 22.0) % 22.0))
        col = bg
        if ring < 1.35:
            col = lerp_rgb(col, a, 0.78)
            g = (*a, 205)
        if (abs(x - 19.5) < 2.0 and 10 <= y <= 46) or (abs(y - 38) <= 1.8 and 11 <= x <= 28):
            col = a if abs(x - 19.5) < 0.9 else p
            g = (*col, 230)
        if abs(x - 19.5) < 1.35 and 46 < y <= 54:
            col = (128, 78, 32)
        return col, g

    if motif == "cyberpunk":
        col = (9, 15, 28)
        scan = (phase / math.tau) * 64.0 % 64.0
        if (ix % 12 < 1) or (iy % 12 < 1):
            col = shade(s, 0.75)
        if abs((y - scan) % 64.0) <= 1.8:
            col = a
            g = (*a, 235)
        elif hash2(ix // 4, int((iy - scan * 2) // 8), 33) > 0.85 and (ix % 4 < 2):
            col = p
            g = (*p, 190)
        return col, g

    if motif == "frostbite":
        col = lerp_rgb((16, 40, 86), (48, 112, 184), ny)
        dx, dy = abs(x - 19.5), abs(y - 30.0)
        if (dx < 1.35 and dy < 18) or (dy < 1.35 and dx < 14) or (abs(dx - dy) < 1.25 and dx < 11):
            glint = 0.65 + 0.35 * math.sin(phase + dx * 0.4 + dy * 0.3)
            col = lerp_rgb(p, (255, 255, 255), glint)
            g = (*col, int(160 + 90 * glint))
        elif (ix + iy) % 13 == 0 and hash2(ix // 2, iy // 2, 41) > 0.74:
            col = (238, 250, 255)
            g = (205, 242, 255, 175)
        return col, g

    if motif == "dragon_scale":
        row = int(y // 6)
        sx = (x + (row % 2) * 4.0) % 8.0
        sy = y % 6.0
        d = math.hypot(sx - 4.0, sy - 1.0)
        sheen = 0.5 + 0.5 * math.sin(nx * 4.0 + ny * 3.0 - phase)
        base_c = lerp_rgb(p, s, sheen)
        if d > 3.85:
            col = shade(base_c, 0.34)
        elif d > 3.05:
            col = lerp_rgb(base_c, a, 0.78)
            if sheen > 0.62:
                g = (*a, 175)
        else:
            col = shade(base_c, 0.68 + (3.1 - d) * 0.11)
        return col, g

    if motif == "inferno":
        n = math.sin(nx * 9.0 + math.cos(ny * 7.0 - phase) * 1.8) + math.cos(ny * 8.0 - phase * 1.5)
        if n > 0.82:
            col = a
            g = (*a, 245)
        elif n > 0.14:
            col = p
            g = (*p, 175)
        else:
            col = lerp_rgb((26, 10, 12), s, (n + 2.0) / 2.15)
        return col, g

    if motif == "starlight":
        bg = lerp_rgb((12, 10, 40), (38, 20, 76), ny)
        neb = math.sin(nx * 4.5 + ny * 2.5 + phase) * math.cos(nx * 2.8 - ny * 4.0)
        col = lerp_rgb(bg, s, max(0.0, neb * 0.75))
        if hash2(ix // 2, iy // 2, 55) > 0.945:
            tw = 0.45 + 0.55 * math.sin(phase + x * 1.7 + y * 2.3)
            col = lerp_rgb(a, (255, 255, 255), tw)
            g = (*col, int(145 + 110 * tw))
        return col, g

    if motif == "royal_mantle":
        border = (x < 3.8 or x > 35.7 or y < 3.8 or y > 59.7)
        if border:
            gleam = 0.78 + 0.22 * math.sin(phase + (x + y) * 0.3)
            col = shade(a, gleam)
            if abs(x - 2.0) < 0.6 or abs(x - 37.5) < 0.6 or abs(y - 2.0) < 0.6 or abs(y - 61.5) < 0.6:
                g = (*a, 165)
        else:
            col = lerp_rgb((118, 16, 28), (172, 24, 40), 1.0 - abs(cx) * 0.45)
            if _in_crown(x, y, 19.5, 28.0, 1.05):
                col = a
                g = (*a, 185)
        return col, g

    if motif == "snow_leopard":
        col = (14, 15, 20)
        dx, dy = (x - 19.5) / 13.0, (y - 36.0) / 16.0
        head_r = math.hypot(dx, dy)
        ear_l = math.hypot((x - 10.5) / 4.0, (y - 21.0) / 5.0)
        ear_r = math.hypot((x - 28.5) / 4.0, (y - 21.0) / 5.0)
        if head_r < 1.0 or ear_l < 1.0 or ear_r < 1.0:
            fur = 188 + int(45 * (1.0 - min(1.0, head_r)))
            spot = hash2(ix // 4, iy // 4, 71)
            if spot > 0.76 and y > 23:
                fur = int(fur * 0.45)
            col = (fur, fur, min(255, fur + 6))
            if (abs(x - 14.0) <= 2.0 and abs(y - 33.0) <= 1.2) or (abs(x - 25.0) <= 2.0 and abs(y - 33.0) <= 1.2):
                col = (240, 248, 255)
                if (abs(x - 14.0) < 0.7 or abs(x - 25.0) < 0.7) and abs(y - 33.0) < 0.7:
                    col = (20, 24, 30)
                else:
                    g = (195, 235, 255, 210)
            if abs(x - 19.5) < 2.2 and 39 <= y <= 41:
                col = (55, 52, 60)
        return col, g

    if motif in ("obsidian_serpent", "ivory_serpent"):
        dark = motif == "obsidian_serpent"
        col = (12, 13, 18) if dark else (244, 246, 250)
        fg = (234, 240, 250) if dark else (26, 28, 36)
        if (abs(x - 19.5) < 1.55 and 9 <= y <= 55) or (abs(y - 20) <= 1.1 and 13 <= x <= 26):
            col = fg
        sx = 19.5 + math.sin((y - 14) * 0.28) * 5.2
        if 12 <= y <= 46 and abs(x - sx) < 2.0:
            col = lerp_rgb(fg, a, 0.32)
        if math.hypot(x - 22.0, y - 14.0) < 3.1:
            col = fg
            if math.hypot(x - 23.0, y - 13.0) < 0.9:
                col = a
                g = (*a, 245)
        return col, g

    if motif == "mochi_kitten":
        col = (14, 14, 22)
        if math.hypot((x - 19.5) / 11.5, (y - 32.0) / 10.5) < 1.0 or math.hypot((x - 19.5) / 9.5, (y - 46.0) / 10.0) < 1.0:
            col = (242, 236, 232) if y > 26 else (202, 194, 188)
            if y < 29 and abs(x - 19.5) < 4:
                col = (154, 144, 140)
        if (10 <= x <= 15.5 and 18 <= y <= 26 and (x - 10) >= (26 - y) * 0.5) or (23.5 <= x <= 29 and 18 <= y <= 26 and (29 - x) >= (26 - y) * 0.5):
            col = (218, 206, 202)
        # Rosy kitten cheeks!
        if math.hypot((x - 11.5) / 2.2, (y - 35.0) / 1.3) < 1.0 or math.hypot((x - 27.5) / 2.2, (y - 35.0) / 1.3) < 1.0:
            col = (255, 175, 198)
        for ex in (15.0, 24.0):
            if math.hypot(x - ex, y - 32.0) < 2.4:
                col = (22, 20, 28)
            if math.hypot(x - (ex - 0.8), y - 31.1) < 0.85:
                col = (255, 255, 255)
        if abs(x - 19.5) < 1.2 and abs(y - 35.0) < 0.7:
            col = (246, 152, 176)
        return col, g

    if motif == "lucky_clover":
        col = (14, 16, 20)
        leaves = [(16, 26), (23, 26), (16, 34), (23, 34)]
        for lx, ly in leaves:
            d = math.hypot(x - lx, y - ly)
            if d < 4.5:
                col = lerp_rgb(p, a, max(0.0, 1.0 - d / 4.5))
                if d < 2.2:
                    g = (*a, 165)
        if abs(x - (19.5 + (y - 34) * 0.16)) < 1.05 and 34 <= y <= 48:
            col = p
        return col, g

    if motif == "grim_reaper":
        col = lerp_rgb((46, 50, 62), (16, 18, 24), ny)
        if hash2(ix // 6, iy // 6, 91) > 0.65 and y < 34:
            col = lerp_rgb(col, (148, 156, 170), 0.35)
        if y > 44 + math.sin(x * 0.25) * 3.0:
            col = (18, 20, 26)
        if abs(x - 17) < 4 + max(0.0, (y - 24) * 0.25) and 22 <= y <= 46:
            col = (10, 10, 14)
        if _dist_seg(x, y, 24, 16, 29, 46) < 1.05:
            col = (176, 182, 196)
        if 14 <= y <= 24 and 24 <= x <= 35 and abs(math.hypot(x - 24, y - 24) - 9.5) < 1.5:
            col = (238, 245, 255)
            g = (205, 230, 255, 210)
        return col, g

    if motif == "gothic_cross":
        col = (12, 12, 16)
        if (abs(x - 3.2) < 0.6 or abs(x - 36.3) < 0.6) and 4 <= y <= 59:
            col = (212, 216, 228)
        if (abs(y - 4.2) < 0.6 or abs(y - 59.3) < 0.6) and 3 <= x <= 36.5:
            col = (212, 216, 228)
        if (abs(x - 19.5) < 2.3 and 12 <= y <= 52) or (abs(y - 26) < 2.3 and 8 <= x <= 31):
            col = (242, 244, 252)
            if abs(x - 19.5) < 0.9 or abs(y - 26) < 0.9:
                col = (40, 42, 54)
        if abs(x - 19.5) < 1.5 and abs(y - 26) < 1.5:
            col = a
            g = (*a, 220)
        return col, g

    if motif == "ace_of_clubs":
        col = (16, 17, 22)
        if (ix + iy) % 12 == 0 and 4 < x < 35 and 4 < y < 59:
            col = (26, 28, 38)
        if ((abs(x - 2.2) < 0.6 or abs(x - 37.2) < 0.6) and 3 <= y <= 60) or ((abs(y - 3.2) < 0.6 or abs(y - 60.2) < 0.6) and 2 <= x <= 37.5):
            col = (228, 230, 240)
        if (math.hypot(x - 19.5, y - 24.5) < 4.5 or math.hypot(x - 15.0, y - 31.5) < 4.5 or math.hypot(x - 24.0, y - 31.5) < 4.5
                or (abs(x - 19.5) < 2.1 and 32 <= y <= 41)):
            col = (246, 248, 254)
        if (math.hypot(x - 7, y - 9) < 2.0) or (math.hypot(x - 32, y - 54) < 2.0):
            col = (246, 248, 254)
        return col, g

    if motif == "midnight_rose":
        col = lerp_rgb((16, 16, 24), (36, 36, 48), (math.sin(nx * 5.0 + ny * 3.0) + 1) * 0.5)
        d = math.hypot(x - 19.5, y - 24.0)
        ang = math.atan2(y - 24.0, x - 19.5)
        if d < 10.5:
            spiral = math.sin(d * 1.45 + ang * 3.0)
            v = 118 + int(118 * max(0.0, spiral))
            col = (v, v, min(255, v + 8))
        if abs(x - 19.5) < 1.2 and 34 <= y <= 56:
            col = (142, 150, 158)
        if math.hypot((x - 13.5) / 5.0, (y - 44.0) / 2.6) < 1.0 or math.hypot((x - 25.5) / 5.0, (y - 47.0) / 2.6) < 1.0:
            col = (168, 176, 186)
        return col, g

    if motif == "all_seeing_eye":
        col = lerp_rgb((20, 16, 28), (44, 34, 54), ny)
        ex, ey = (x - 19.5) / 14.5, (y - 31.0) / 8.0
        almond = abs(ey) + 0.55 * (ex ** 2)
        if almond < 0.95:
            col = (236, 228, 216)
            ir = math.hypot(x - 19.5, y - 31.0)
            if ir < 6.2:
                col = lerp_rgb(p, a, 1.0 - ir / 6.2)
                g = (*col, 205)
            if ir < 2.5:
                col = (14, 12, 20)
            if math.hypot(x - 17.8, y - 29.0) < 1.1:
                col = (255, 255, 255)
        elif almond < 1.12:
            col = (18, 14, 24)
        return col, g

    if motif == "blaze_spirit":
        col = lerp_rgb((18, 8, 12), (48, 14, 14), ny)
        wx = math.sin(ny * 12.0 - phase) * 2.4 + math.cos(ny * 7.0 + phase) * 1.1
        crown = 10.0 + abs(math.sin(nx * 14.0 + phase)) * 5.0
        in_flame_hair = (y >= crown and y <= 24 and abs(x - 19.5 - wx * 0.6) < (y - crown + 3.5) * 0.55)
        head = math.hypot((x - 19.5) / 5.2, (y - 25.0) / 5.8)
        torso = math.hypot((x - 19.5 - wx * 0.3) / (6.8 - max(0.0, (y - 36) * 0.14)), (y - 42.0) / 14.0)
        arm_l = _dist_seg(x, y, 14, 34, 6, 22 + math.sin(phase) * 2) < 2.1
        arm_r = _dist_seg(x, y, 25, 34, 33, 22 + math.cos(phase) * 2) < 2.1
        if head < 1.0 or torso < 1.0 or in_flame_hair or arm_l or arm_r:
            t = min(head, torso) if (head < 1.0 or torso < 1.0) else 0.55
            col = lerp_rgb((255, 252, 205), p, min(1.0, t * 1.15))
            if t > 0.72:
                col = lerp_rgb(col, s, (t - 0.72) / 0.28)
            g = (*col, int(min(255, 180 + (1.0 - min(1.0, t)) * 75)))
            if (abs(x - 17) <= 1.1 and abs(y - 25) <= 1.1) or (abs(x - 22) <= 1.1 and abs(y - 25) <= 1.1):
                col = (255, 255, 255)
                g = (255, 255, 225, 255)
        elif hash2(ix // 4, int((iy + phase * 12) % 128) // 4, 107) > 0.92:
            col = a
            g = (*a, 225)
        return col, g

    if motif == "monarch_crown":
        col = lerp_rgb((152, 16, 28), (188, 22, 34), 1.0 - abs(cx) * 0.4)
        if _in_crown(x, y, 19.5, 31.0, 1.25):
            col = a
            g = (*a, 225)
        elif hash2(ix // 2, iy // 2, 111) > 0.968:
            col = (255, 228, 115)
            g = (255, 228, 115, 185)
        return col, g

    if motif in ("fallen_seraph", "celestial_seraph"):
        light_bg = motif == "fallen_seraph"
        col = lerp_rgb((244, 246, 252), (208, 216, 232), ny) if light_bg else lerp_rgb((8, 10, 22), (22, 24, 44), ny)
        sil = (14, 16, 22) if light_bg else (250, 252, 255)
        if abs(math.hypot(x - 19.5, y - 22.0) - 10.5) < 0.85:
            col = lerp_rgb(col, sil, 0.5)
        dx = abs(x - 19.5)
        in_body = (dx < 2.0 and 20 <= y <= 46) or (math.hypot(x - 19.5, y - 18.0) < 2.35)
        u = min(1.0, max(0.0, (dx - 1.5) / 17.0))
        top_edge = 26.0 - math.sin(u * math.pi * 0.85) * 14.5
        bot_edge = top_edge + (16.0 - u * 11.5) + math.sin(dx * 1.9) * 2.2
        in_wing = (1.8 <= dx <= 18.5) and (top_edge <= y <= bot_edge)
        if in_body or in_wing:
            col = sil
            if not light_bg:
                g = (228, 240, 255, 210)
        return col, g

    if motif == "fuji_sakura":
        col = lerp_rgb((250, 248, 252), (224, 236, 250), ny)
        if math.hypot(x - 11.0, y - 16.0) < 5.2:
            col = (228, 48, 74)
        mtn_y = 33.0 + abs(x - 19.5) * 1.18
        if y >= mtn_y:
            snow_line = 39.5 + math.sin(x * 0.9) * 1.8
            if y < snow_line:
                col = (248, 252, 255)
            else:
                col = lerp_rgb((54, 88, 156), (32, 56, 118), (y - snow_line) / 24.0)
        if y > 54:
            col = lerp_rgb((175, 205, 238), (232, 244, 254), (math.sin(y * 1.8) + 1) * 0.5)
        if _dist_seg(x, y, 39, 12, 13, 28) < 1.35 or _dist_seg(x, y, 28, 19, 20, 12) < 0.95:
            col = (72, 42, 40)
        for bx, by in [(34, 15), (27, 17), (21, 13), (22, 23), (15, 26), (31, 22), (18, 20)]:
            d = math.hypot(x - bx, y - by)
            if d < 2.6:
                col = (255, 182, 204) if d > 1.0 else (255, 238, 245)
        return col, g

    if motif == "cyber_chains":
        col = lerp_rgb((8, 12, 24), (14, 24, 50), ny)
        for cx0, cy0, r in [(19, 26, 7), (14, 34, 5), (25, 33, 5), (20, 42, 4)]:
            d = abs(x - cx0) + abs(y - cy0) * 0.85
            if d < r:
                pulse = 0.65 + 0.35 * math.sin(phase + cx0)
                col = lerp_rgb(p, a, (1.0 - d / r) * pulse)
                g = (*col, int(175 + 80 * pulse))
        for c_off in (-10, 12):
            if abs((y - x * 1.15 - c_off)) < 1.4 and (ix + iy) % 8 >= 2:
                col = (188, 202, 220)
        return col, g

    if motif == "crescent_moon":
        col = (12, 13, 19)
        arch = abs(math.hypot((x - 19.5) / 13.0, (y - 36.0) / 19.0) - 1.0)
        if arch < 0.10 and y < 52:
            col = (244, 246, 252)
            g = (220, 238, 255, 180)
        if math.hypot(x - 18.5, y - 33.0) < 7.8 and math.hypot(x - 22.5, y - 31.0) > 6.8:
            col = (252, 253, 255)
            g = (225, 242, 255, 240)
        if hash2(ix // 2, iy // 2, 131) > 0.975:
            col = (242, 248, 255)
            g = (220, 238, 255, 195)
        return col, g

    if motif == "starlit_deer":
        col = (10, 12, 19)
        if (x < 9 or x > 30) and y > 18 + (int(x) % 5) * 3:
            col = (20, 22, 32)
        if math.hypot(x - 28, y - 13) < 4.2 and math.hypot(x - 30, y - 12) > 3.6:
            col = (248, 250, 255)
            g = (232, 244, 255, 225)
        in_deer = (
            (15 <= x <= 23 and 39 <= y <= 44)
            or (15 <= x <= 17 and 44 <= y <= 52)
            or (21 <= x <= 23 and 44 <= y <= 52)
            or (21 <= x <= 24 and 32 <= y <= 40)
            or (_dist_seg(x, y, 22, 32, 18, 26) < 0.85)
            or (_dist_seg(x, y, 23, 32, 27, 26) < 0.85)
        )
        if in_deer:
            col = (248, 252, 255)
            g = (210, 238, 255, 225)
        return col, g

    if motif == "candle_skull":
        col = lerp_rgb((28, 12, 18), (14, 10, 16), ny)
        if math.hypot((x - 19.5) / 8.5, (y - 37.0) / 9.0) < 1.0 or (15 <= x <= 24 and 43 <= y <= 49):
            col = (236, 230, 224)
            if (math.hypot(x - 16, y - 37) < 2.2) or (math.hypot(x - 23, y - 37) < 2.2):
                col = (238, 42, 68)
                g = (255, 68, 94, 240)
        if 18 <= x <= 21 and 20 <= y <= 29:
            col = (246, 238, 212)
        if math.hypot((x - 19.5) / 2.4, (y - 16.0) / 4.2) < 1.0:
            col = (255, 228, 98)
            g = (255, 205, 78, 248)
        return col, g

    if motif == "pastel_nebula":
        t = (math.sin(nx * 3.8 + phase) * 0.5 + 0.5) * 0.55 + (math.cos(ny * 3.2 - phase) * 0.5 + 0.5) * 0.45
        c1 = lerp_rgb((255, 194, 226), (178, 230, 255), t)
        c2 = lerp_rgb((255, 240, 182), (208, 186, 255), (nx + ny) * 0.5)
        col = lerp_rgb(c1, c2, 0.45)
        if hash2(ix // 2, iy // 2, 149) > 0.955:
            col = (255, 255, 255)
            g = (255, 242, 255, 210)
        return col, g

    from pixel_art_v2 import cloak_pixel_v2
    v2_res = cloak_pixel_v2(motif, x, y, nx, ny, cx, cy, phase, p, s, a)
    if v2_res is not None:
        return v2_res

    return lerp_rgb(s, p, ny), g


def _dist_seg(px: float, py: float, x1: float, y1: float, x2: float, y2: float) -> float:
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    if l2 <= 1e-6:
        return math.hypot(px - x1, py - y1)
    t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2))
    return math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))


def _in_crown(x: float, y: float, cx: float, cy: float, sc: float) -> bool:
    dx = (x - cx) / sc
    dy = (y - cy) / sc
    if -8 <= dx <= 8 and 1 <= dy <= 5:
        return True
    if -8 <= dx <= 8 and -6 <= dy < 1:
        peak = max(
            1.0 - abs(dx + 6.0) / 3.0,
            1.0 - abs(dx) / 3.5,
            1.0 - abs(dx - 6.0) / 3.0,
        )
        return dy >= 1.0 - peak * 7.0
    return False


def _rainbow(t: float) -> tuple[int, int, int]:
    h = (t % 1.0) * 6.0
    i = int(h) % 6
    f = h - int(h)
    q = int(255 * (1.0 - f))
    v = int(255 * f)
    return [
        (255, v, 76),
        (q, 255, 76),
        (64, 255, v),
        (64, q, 255),
        (v, 86, 255),
        (255, 76, q),
    ][i]


# ==============================================================================
# 2. HIGH-CRAFT 128x128 4-ZONE MATERIAL ATLAS (WINGS, HATS, AURAS, PETS, COMPANIONS)
#    Zone A (0..64,   0..64): Primary sculpted material (V-barbed feathers, plush fur, faceted crystal, veins...)
#    Zone B (64..128, 0..64): Secondary trim / soft belly / inner ear / brushed alloy
#    Zone C (0..64,  64..128): Luminous crystal / energy core / glowing runes / satin bow
#    Zone D (64..128,64..128):
#      - D_EYE   (68..92, 68..92): Sparkling kawaii anime iris + dual white catchlights + lash rim!
#      - D_DARK  (98..124, 68..92): Sleek obsidian / nose / claws / dark contour
#      - D_GOLD  (68..92, 98..124): Shimmering beveled royal gold / ivory highlight
#      - D_BLUSH (98..124, 98..124): Soft rosy pastel cheek blush / cute pink inner ear!
# ==============================================================================
def paint_rig_atlas(
    style: str,
    primary: int,
    secondary: int,
    accent: int,
    eye_color: int,
    translucent_alpha: int,
    emissive_zones: str,
    out_path: Path,
    glow_path: Path | None,
) -> bool:
    base = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 128), (0, 0, 0, 0)) if glow_path else None
    base_px = base.load()
    glow_px = glow.load() if glow is not None else None

    p = hex_rgb(primary)
    s = hex_rgb(secondary)
    a = hex_rgb(accent)
    e = hex_rgb(eye_color)
    has_glow = False

    # Dark kawaii eyes (like axolotl, shiba, bunny, lamb) shouldn't wash out into neon glow
    eye_luma = e[0] * 0.299 + e[1] * 0.587 + e[2] * 0.114
    eye_emissive = eye_luma > 68

    for y in range(128):
        for x in range(128):
            zone = ("A" if x < 64 else "B") if y < 64 else ("C" if x < 64 else "D")
            lx, ly = x & 63, y & 63
            nx, ny = lx / 63.0, ly / 63.0

            if zone == "A":
                col, al = _zone_a(style, lx, ly, nx, ny, p, s, a, translucent_alpha)
            elif zone == "B":
                col, al = _zone_b(style, lx, ly, nx, ny, p, s, a, translucent_alpha)
            elif zone == "C":
                col, al = _zone_c(style, lx, ly, nx, ny, p, s, a, translucent_alpha)
            else:
                col, al = _zone_d(style, lx, ly, nx, ny, p, s, a, e)

            base_px[x, y] = (*col, al)

            if glow_px is not None:
                is_em = zone in emissive_zones
                g_col = col
                if style in ("rune_fur", "magma_membrane", "honeycomb", "galaxy", "feathers") and zone == "A":
                    if _is_pattern_glow(style, lx, ly, nx, ny):
                        is_em = True
                        g_col = a if style != "feathers" else lerp_rgb(a, (255, 255, 255), 0.35)
                if zone == "D" and eye_emissive and _is_eye_glow(lx, ly):
                    is_em = True
                if is_em and al > 20:
                    glow_px[x, y] = (*g_col, min(255, max(165, al)))
                    has_glow = True

    out_path.parent.mkdir(parents=True, exist_ok=True)
    base.save(out_path)
    if glow_path is not None and glow is not None and has_glow:
        glow.save(glow_path)
        return True
    if glow_path is not None and glow_path.exists():
        glow_path.unlink()
    return False


def _is_pattern_glow(style: str, lx: int, ly: int, nx: float, ny: float) -> bool:
    if style == "rune_fur":
        # Crisp glowing geometric arcane circuitry lines!
        d1 = (lx + ly * 2) % 18
        d2 = (lx * 2 - ly) % 22
        return (d1 <= 1) or (d2 <= 1 and (ly % 6 < 3))
    if style == "magma_membrane":
        v = abs(math.sin(lx * 0.22 + math.cos(ly * 0.20) * 1.6))
        return v > 0.90
    if style == "honeycomb":
        return _hex_edge(lx, ly, 12.0)
    if style == "galaxy":
        return hash2(lx // 2, ly // 2, 211) > 0.935
    if style == "feathers":
        return ny > 0.86 and abs(nx - 0.5) < 0.38
    return False


def _hex_edge(lx: int, ly: int, size: float) -> bool:
    row_h = size * 0.866
    row = int(ly / row_h)
    fx = ((lx + (row % 2) * (size * 0.5)) % size) / size - 0.5
    fy = ((ly % row_h) / row_h) - 0.5
    d = max(abs(fx), abs(fy) * 0.866 + abs(fx) * 0.5)
    return d > 0.43


def _is_eye_glow(lx: int, ly: int) -> bool:
    return 6 <= lx <= 26 and 8 <= ly <= 26


def _soft_bevel(nx: float, ny: float) -> float:
    edge = min(nx, 1.0 - nx, ny, 1.0 - ny)
    # Crisp commercial edge definition + rich top-down studio key lighting + soft bottom AO
    ao = 0.78 + 0.22 * min(1.0, edge / 0.11)
    rim = 1.08 if 0.04 <= edge <= 0.09 and ny < 0.55 else 1.0
    key_light = 1.10 - 0.22 * ny
    return ao * key_light * rim


def _zone_a(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, alpha: int):
    bev = _soft_bevel(nx, ny)
    if style == "feathers":
        # Sculpted overlapping V-shaped feather barbules + luminous central quill shaft + glowing tip!
        dx = abs(nx - 0.5)
        quill = math.exp(-((dx / 0.045) ** 2))
        barb = 0.86 + 0.15 * math.sin((ny * 18.0 - dx * 14.0) * math.pi * 0.5)
        grad = lerp_rgb(s, p, min(1.0, ny * 0.65 + 0.35))
        if ny > 0.72:
            grad = lerp_rgb(grad, a, (ny - 0.72) / 0.28)
        grad = lerp_rgb(grad, (255, 255, 255), quill * 0.28)
        return shade(grad, barb * bev), alpha

    if style == "rainbow_feathers":
        dx = abs(nx - 0.5)
        col = _rainbow((nx * 0.45 + ny * 0.60) % 1.0)
        col = lerp_rgb(col, (255, 255, 255), 0.18 + 0.22 * math.exp(-((dx / 0.05) ** 2)))
        barb = 0.88 + 0.12 * math.sin((ny * 16.0 - dx * 12.0) * math.pi * 0.5)
        return shade(col, barb * bev), alpha

    if style == "magma_membrane":
        if _is_pattern_glow(style, lx, ly, nx, ny):
            return lerp_rgb(a, (255, 250, 200), 0.35), 255
        # Leathery dark wing membrane with subtle branching heat glow near cracks
        v = abs(math.sin(lx * 0.22 + math.cos(ly * 0.20) * 1.6))
        grad = lerp_rgb(p, s, ny * 0.75)
        if v > 0.76:
            grad = lerp_rgb(grad, a, (v - 0.76) / 0.16 * 0.5)
        grain = 0.90 + 0.12 * hash2(lx // 2, ly // 2, 3)
        return shade(grad, grain * bev), alpha

    if style == "honeycomb":
        if _hex_edge(lx, ly, 12.0):
            return lerp_rgb(a, (255, 255, 255), 0.55), 248
        facet = 0.86 + 0.20 * math.sin((nx * 3.0 + ny * 2.5) * math.pi)
        grad = lerp_rgb(p, s, (nx + ny) * 0.5)
        return shade(grad, facet * bev), alpha

    if style == "butterfly":
        # Stained-glass butterfly wing venation, iridescent gradient scales & pearly border dots
        border = (nx < 0.10 or nx > 0.90 or ny < 0.10 or ny > 0.90)
        if border:
            if ((lx + ly) % 10 < 3) and (0.03 < min(nx, 1 - nx, ny, 1 - ny) < 0.08):
                return (255, 250, 240), 255
            return (22, 18, 28), 255
        vein = abs(math.sin((nx - 0.5) * 8.5 + ny * 2.8)) < 0.09 or abs(math.cos(ny * 7.5 - nx * 2.0)) < 0.06
        if vein:
            return (28, 22, 36), 245
        shimmer = 0.92 + 0.12 * math.sin((nx * 6.0 - ny * 4.0) * math.pi)
        grad = lerp_rgb(p, a, ny * 0.72 + nx * 0.28)
        return shade(grad, shimmer), alpha

    if style == "galaxy":
        swirl = (math.sin(nx * 5.0 + math.cos(ny * 4.0) * 1.4) + 1.0) * 0.5
        col = lerp_rgb(s, p, swirl)
        neb = math.exp(-(((nx - 0.5) * 1.6) ** 2 + ((ny - 0.45) * 1.6) ** 2))
        col = lerp_rgb(col, a, neb * 0.35)
        if _is_pattern_glow(style, lx, ly, nx, ny):
            return lerp_rgb(a, (255, 255, 255), 0.65), 255
        return shade(col, bev), alpha

    if style == "rune_fur":
        if _is_pattern_glow(style, lx, ly, nx, ny):
            return lerp_rgb(a, (255, 255, 255), 0.25), 255
        tuft = 0.88 + 0.14 * math.sin(lx * 0.65 + (ly // 4) * 1.7) * 0.5 + 0.06 * hash2(lx // 2, ly // 3, 17)
        return shade(p, tuft * bev), 255

    if style == "fur":
        # Soft plush painterly fur with subtle vertical hair strands & warm top-lit shading (zero boxy tiling!)
        strand = (hash2(lx // 2, ly // 5, 23) - 0.5) * 0.11 + (hash2(lx, ly // 3, 29) - 0.5) * 0.06
        grad = lerp_rgb(lerp_rgb(p, (255, 252, 248), 0.14), lerp_rgb(p, s, 0.20), ny)
        return shade(grad, (0.98 + strand) * bev), 255

    if style == "pastel_plush":
        # Dreamy glossy pastel velvet/jelly sheen with tiny pearly sparkle highlights!
        gloss = math.exp(-(((nx - 0.32) / 0.28) ** 2 + ((ny - 0.28) / 0.24) ** 2))
        col = lerp_rgb(p, a, ny * 0.38)
        col = lerp_rgb(col, (255, 255, 255), gloss * 0.42)
        return shade(col, bev), alpha

    if style == "bee_stripes":
        stripe = (ly // 11) % 2 == 0
        col = p if stripe else (26, 20, 18)
        fluff = 0.92 + 0.10 * math.sin(lx * 0.7)
        return shade(col, fluff * bev), 255

    if style == "metal":
        # Sleek brushed alloy with crisp diagonal specular gleam & beveled panel trim!
        diag = math.sin((nx + ny) * math.pi * 1.35)
        sheen = 0.80 + 0.28 * max(0.0, diag)
        col = lerp_rgb(p, s, 0.22)
        if diag > 0.86:
            col = lerp_rgb(col, (255, 255, 255), (diag - 0.86) / 0.14 * 0.35)
        if lx in (4, 59) or ly in (4, 59):
            col = shade(col, 0.72)
        return shade(col, sheen * bev), 255

    if style == "crystal":
        # Sharp multifaceted prism geometry with bright caustic edges!
        f1 = ((lx + ly) // 12) % 3
        f2 = ((lx - ly + 64) // 14) % 2
        facet = f1 * 0.14 + f2 * 0.10
        col = lerp_rgb(p, a, min(1.0, ny * 0.55 + facet))
        if (lx + ly) % 12 == 0 or lx <= 2 or ly <= 2:
            col = lerp_rgb(col, (255, 255, 255), 0.62)
        return col, alpha

    if style == "flame":
        w = 0.5 + 0.5 * math.sin(lx * 0.28 + math.cos(ly * 0.22) * 1.8)
        col = lerp_rgb(s, p, min(1.0, ny * 1.1))
        col = lerp_rgb(col, a, w * 0.68)
        if ny > 0.75 and w > 0.65:
            col = lerp_rgb(col, (255, 255, 235), 0.55)
        return col, alpha

    n = 0.92 + 0.10 * hash2(lx // 3, ly // 3, 31)
    return shade(lerp_rgb(p, s, ny * 0.32), n * bev), alpha


def _zone_b(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, alpha: int):
    bev = _soft_bevel(nx, ny)
    sheen = 0.88 + 0.18 * math.sin((nx + ny) * math.pi)
    if style in ("feathers", "rainbow_feathers", "butterfly"):
        return shade(lerp_rgb(s, p, 0.22), sheen * bev), 255
    if style in ("fur", "rune_fur", "bee_stripes", "pastel_plush"):
        strand = (hash2(lx // 2, ly // 5, 43) - 0.5) * 0.09
        return shade(s, (0.98 + strand) * bev), 255
    return shade(s, sheen * bev), 255


def _zone_c(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, alpha: int):
    # Radiant glowing gemstone / satin bow / energy core with luminous specular center
    d = math.hypot(nx - 0.5, ny - 0.5)
    col = lerp_rgb(a, (255, 255, 255), max(0.0, 0.52 - d * 0.95))
    if style == "rainbow_feathers":
        col = lerp_rgb(_rainbow((nx + ny) * 0.5), (255, 255, 255), 0.22)
    elif style == "pastel_plush":
        col = lerp_rgb(a, (255, 245, 252), max(0.0, 0.45 - d * 0.8))
    pulse = 0.90 + 0.18 * (1.0 - min(1.0, d * 1.4))
    return shade(col, min(1.15, pulse)), min(255, max(190, alpha))


def _zone_d(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, e):
    # Top-left quadrant (0..32, 0..32): EXPRESSIVE KAWAII SPARKLING EYE!
    # Mapped by ZONES["D_EYE"] = [68, 68, 92, 92] -> lx=4..28, ly=4..28
    if lx < 32 and ly < 32:
        ex = (lx - 16.0) / 11.0
        ey = (ly - 16.0) / 11.0
        # Subtle glossy dark upper eyelash rim
        if ly <= 7:
            return (16, 14, 22), 255
        # Rich shaded iris (deeper at top, luminous highlight crescent at bottom!)
        top_iris = shade(e, 0.55)
        bot_iris = lerp_rgb(e, (255, 255, 255), 0.48)
        iris_t = max(0.0, min(1.0, (ly - 7) / 19.0))
        col = lerp_rgb(top_iris, bot_iris, iris_t)
        # Soft dark pupil core in middle-upper center
        if math.hypot(ex, (ey + 0.08) * 1.1) < 0.48:
            col = lerp_rgb(col, (14, 14, 22), 0.72)
        # Crisp bright main upper-left anime catchlight sparkle!
        if math.hypot(lx - 11.0, ly - 11.5) < 3.4:
            return (255, 255, 255), 255
        # Cute secondary lower-right twinkle dot!
        if math.hypot(lx - 21.0, ly - 21.0) < 1.9:
            return (255, 255, 255), 255
        # Clean crisp dark rim on sides/bottom
        if lx <= 5 or lx >= 27 or ly >= 27:
            return (22, 20, 30), 255
        return col, 255

    # Top-right quadrant (32..64, 0..32): Sleek obsidian / nose / claws / dark contour
    if lx >= 32 and ly < 32:
        spec = 1.0 + 0.18 * math.exp(-(((lx - 42) / 7.0) ** 2 + ((ly - 10) / 7.0) ** 2))
        return shade((28, 28, 38), spec), 255

    # Bottom-left quadrant (0..32, 32..64): Shimmering metallic royal gold / ivory highlight
    if lx < 32 and ly >= 32:
        gx, gy = lx / 31.0, (ly - 32) / 31.0
        gleam = 0.85 + 0.25 * math.sin((gx + gy) * math.pi)
        base_g = lerp_rgb((245, 178, 38), (255, 246, 195), 0.45)
        col = lerp_rgb(base_g, a, 0.25)
        return shade(col, min(1.18, gleam)), 255

    # Bottom-right quadrant (32..64, 32..64): Soft rosy pastel cheek blush / cute pink inner ear!
    bx = (lx - 48.0) / 14.0
    by = (ly - 48.0) / 14.0
    r = math.hypot(bx, by)
    blush_core = (255, 146, 182)
    blush_edge = lerp_rgb(p, (255, 192, 212), 0.65)
    return lerp_rgb(blush_core, blush_edge, min(1.0, r * 0.75)), 255
