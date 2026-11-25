"""High-craft pixel-art texture & emissive glow mask generator for SkyPilot's 168+ wardrobe cosmetics."""
from __future__ import annotations

import math
from pathlib import Path
from typing import Callable
from PIL import Image, ImageDraw


def hex_rgb(c: int) -> tuple[int, int, int]:
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def rgba(c: int, a: int = 255) -> tuple[int, int, int, int]:
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, max(0, min(255, a)))


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


def set_px(img: Image.Image, x: int, y: int, col: tuple[int, int, int, int]) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), col)


def fill_rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, col: tuple[int, int, int, int]) -> None:
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=col)


# ==============================================================================
# 1. CLOAK TEXTURES (64x64 per frame; front art 40x64 at x=0..40, lining x=40..60, trim x=60..64)
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
    """Renders a crisp 40x64 high-res illustrated cloak texture (+ optional animated strip & glow mask)."""
    w, h = 64, 64 * frames
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0)) if glow_path else None

    p_rgb = hex_rgb(primary)
    s_rgb = hex_rgb(secondary)
    a_rgb = hex_rgb(accent)
    has_glow = False

    for fi in range(frames):
        oy = fi * 64
        phase = (fi / max(1, frames)) * math.tau

        # Inner lining (x=40..60) & stitched border trim (x=60..64)
        lining_dark = shade(s_rgb, 0.42)
        lining_mid = shade(s_rgb, 0.56)
        trim_col = lerp_rgb(p_rgb, a_rgb, 0.5)
        for y in range(64):
            for x in range(40, 60):
                edge = (x == 40 or x == 59 or y < 3 or y > 60)
                c = trim_col if edge else (lining_mid if (x + y) % 4 == 0 else lining_dark)
                base.putpixel((x, oy + y), (*c, 255))
            for x in range(60, 64):
                shimmer = 0.85 + 0.15 * math.sin(y * 0.25 + phase)
                base.putpixel((x, oy + y), (*shade(trim_col, shimmer), 255))

        # Front illustrated 40x64 canvas (x=0..39, y=0..63)
        for y in range(64):
            ny = y / 63.0
            for x in range(40):
                nx = x / 39.0
                cx, cy = (x - 19.5) / 19.5, (y - 31.5) / 31.5
                col, gcol = _cloak_pixel(motif, x, y, nx, ny, cx, cy, phase, p_rgb, s_rgb, a_rgb)
                # Subtle cloth weave micro-shading + vertical fold highlight
                fold = 0.94 + 0.08 * math.sin(nx * math.pi * 2.5) - 0.06 * ny
                if (x == 0 or x == 39 or y == 0 or y == 63) and motif not in ("ivory_serpent", "fallen_seraph", "fuji_sakura"):
                    col = lerp_rgb(col, shade(trim_col, 0.85), 0.35)
                else:
                    col = shade(col, fold)
                base.putpixel((x, oy + y), (*col, 255))
                if glow is not None and gcol is not None:
                    glow.putpixel((x, oy + y), gcol)
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
    motif: str, x: int, y: int, nx: float, ny: float, cx: float, cy: float,
    phase: float, p: tuple[int, int, int], s: tuple[int, int, int], a: tuple[int, int, int],
) -> tuple[tuple[int, int, int], tuple[int, int, int, int] | None]:
    g: tuple[int, int, int, int] | None = None

    if motif == "aurora":
        bg = lerp_rgb((10, 16, 36), (18, 28, 58), ny)
        w1 = math.sin(nx * 5.2 + ny * 3.1 - phase) * 0.5 + 0.5
        w2 = math.cos(nx * 3.4 - ny * 4.2 + phase) * 0.5 + 0.5
        ribbon = math.exp(-((nx - 0.5 + 0.24 * math.sin(ny * 4.5 - phase)) ** 2) * 14.0)
        ribbon2 = math.exp(-((nx - 0.5 - 0.22 * math.cos(ny * 3.8 + phase)) ** 2) * 18.0)
        c_aur = lerp_rgb(p, a, w1)
        c_aur = lerp_rgb(c_aur, s, w2 * 0.45)
        col = lerp_rgb(bg, c_aur, min(1.0, ribbon * 0.95 + ribbon2 * 0.65))
        if ribbon > 0.42:
            g = (*c_aur, int(min(255, ribbon * 210)))
        if hash2(x, y, 7) > 0.985:
            col = (240, 250, 255)
            g = (220, 255, 255, 220)
        return col, g

    if motif == "midnight":
        col = lerp_rgb((12, 16, 38), (28, 36, 76), ny)
        # Constellation lines & twinkling stars
        stars = [(10, 14), (24, 10), (30, 22), (16, 28), (12, 42), (26, 48), (20, 36), (8, 52), (32, 40)]
        for i in range(len(stars) - 1):
            x1, y1 = stars[i]
            x2, y2 = stars[i + 1]
            if _dist_seg(x, y, x1, y1, x2, y2) < 0.75:
                col = lerp_rgb(col, s, 0.55)
        for idx, (sx, sy) in enumerate(stars):
            d = math.hypot(x - sx, y - sy)
            tw = 0.55 + 0.45 * math.sin(phase + idx * 1.1)
            if d < 1.3 or (abs(x - sx) <= 2 and y == sy) or (abs(y - sy) <= 2 and x == sx):
                col = lerp_rgb(a, (255, 255, 255), tw)
                g = (*col, int(170 + 85 * tw))
        if hash2(x, y, 13) > 0.96:
            tw = 0.5 + 0.5 * math.sin(phase + x + y)
            col = lerp_rgb(col, (220, 235, 255), tw)
            if tw > 0.7:
                g = (200, 225, 255, 160)
        return col, g

    if motif == "ember":
        bg = lerp_rgb((28, 12, 18), (92, 24, 22), ny)
        flame = math.sin(nx * 8.0 + math.sin(ny * 5.0 + phase)) * 0.14
        heat = max(0.0, (ny + flame - 0.35) / 0.65)
        col = lerp_rgb(bg, p, min(1.0, heat * 1.2))
        if heat > 0.65:
            col = lerp_rgb(col, a, (heat - 0.65) / 0.35)
            g = (*col, int(min(255, (heat - 0.55) * 340)))
        # Rising incandescent sparks
        ry = int((y + phase / math.tau * 64) % 64)
        if hash2(x // 2, ry // 2, 21) > 0.945 and (x % 2 == 0 or y % 2 == 0):
            col = (255, 244, 175)
            g = (255, 220, 110, 240)
        return col, g

    if motif == "prism":
        t = (nx * 0.75 + ny * 0.95 - phase / math.tau) % 1.0
        col = _rainbow(t)
        shimmer = 0.8 + 0.2 * math.sin((nx - ny) * 18.0 + phase)
        col = shade(col, shimmer)
        if abs(math.sin((nx + ny) * 10.0 - phase)) > 0.86:
            col = lerp_rgb(col, (255, 255, 255), 0.45)
            g = (*col, 175)
        return col, g

    if motif == "tide":
        bg = lerp_rgb((10, 32, 68), (18, 72, 118), ny)
        wy = (ny * 5.0 + 0.32 * math.sin(nx * 6.5 + phase) + 0.16 * math.cos(nx * 11.0 - phase)) % 1.0
        if wy < 0.16:
            col = lerp_rgb((215, 248, 255), (255, 255, 255), 1.0 - wy / 0.16)
            g = (180, 245, 255, 130)
        elif wy < 0.42:
            col = lerp_rgb(p, s, (wy - 0.16) / 0.26)
        else:
            col = lerp_rgb(bg, s, 0.35)
        return col, g

    if motif == "eclipse":
        bg = lerp_rgb((14, 15, 26), (26, 22, 38), ny)
        d = math.hypot(x - 19.5, y - 26.0)
        ang = math.atan2(y - 26.0, x - 19.5)
        flare = 1.6 * math.sin(ang * 5.0 + phase) + 0.9 * math.cos(ang * 9.0 - phase * 2.0)
        ring = abs(d - (11.0 + 0.45 * math.sin(phase)))
        if d < 9.5:
            col = (8, 8, 14)
        elif ring < 2.2 + max(0.0, flare) * 0.8:
            t = max(0.0, 1.0 - ring / (2.4 + max(0.0, flare)))
            col = lerp_rgb(s, a, t)
            if ring < 1.2:
                col = lerp_rgb(col, (255, 250, 210), 0.65)
            g = (*col, int(min(255, 110 + t * 145)))
        else:
            glow_fall = math.exp(-max(0.0, d - 11.0) * 0.18)
            col = lerp_rgb(bg, s, glow_fall * 0.55)
        return col, g

    if motif == "phantom":
        bg = lerp_rgb((12, 20, 42), (18, 42, 62), ny)
        w = 0.5 + 0.5 * math.sin(nx * 7.0 + math.sin(ny * 5.0 - phase) * 2.0 + phase)
        wisp = math.exp(-((nx - 0.5 + 0.25 * math.sin(ny * 5.0 - phase)) ** 2) * 10.0)
        col = lerp_rgb(bg, p, min(1.0, w * 0.55 + wisp * 0.5))
        if wisp > 0.45:
            col = lerp_rgb(col, a, 0.5)
            g = (*p, int(min(255, wisp * 210)))
        return col, g

    if motif == "void_walker":
        bg = lerp_rgb((14, 6, 30), (32, 10, 62), ny)
        # End portal diamond runes
        gx = (x + int(phase * 2)) % 10
        gy = (y - int(phase * 3)) % 12
        diamond = abs(gx - 5) + abs(gy - 6)
        if diamond == 3 or (diamond == 1 and (x // 10 + y // 12) % 2 == 0):
            pulse = 0.55 + 0.45 * math.sin(phase + x * 0.3 + y * 0.2)
            col = lerp_rgb(s, a, pulse)
            g = (*col, int(150 + 100 * pulse))
        else:
            col = bg
        return col, g

    if motif == "sakura":
        bg = lerp_rgb((48, 20, 36), (88, 34, 58), ny)
        col = bg
        # Diagonal cherry branch at top-right + drifting petals
        if _dist_seg(x, y, 39, 6, 12, 26) < 1.7 or _dist_seg(x, y, 26, 15, 18, 9) < 1.2:
            col = (64, 38, 36)
        petals = [(10, 12), (28, 18), (16, 26), (31, 34), (9, 40), (22, 46), (14, 54), (30, 56), (20, 10)]
        for i, (px, py) in enumerate(petals):
            dy = (py + int(phase / math.tau * 64) + i * 7) % 64
            dx = (px + int(math.sin(phase + i) * 3.0)) % 40
            d = math.hypot(x - dx, y - dy)
            if d < 2.2:
                col = (255, 232, 240) if d < 1.0 else p
                if d < 1.0:
                    g = (255, 200, 220, 120)
        return col, g

    if motif == "hyperion":
        bg = lerp_rgb((18, 22, 34), (32, 26, 40), ny)
        # Golden Wither Blade silhouette + radiant shockwave rings
        d = math.hypot(x - 19.5, y - 30.0)
        ring = abs(d - ((phase / math.tau * 22.0) % 22.0))
        col = bg
        if ring < 1.5:
            col = lerp_rgb(col, a, 0.75)
            g = (*a, 190)
        # Vertical blade + crossguard + pommel
        if (abs(x - 19.5) < 2.1 and 10 <= y <= 46) or (abs(y - 38) <= 2 and 11 <= x <= 28):
            col = a if abs(x - 19.5) < 1.0 else p
            g = (*col, 220)
        if abs(x - 19.5) < 1.5 and 46 < y <= 54:
            col = (120, 74, 30)
        return col, g

    if motif == "cyberpunk":
        col = (10, 16, 30)
        scan = int((phase / math.tau) * 64) % 64
        if x % 6 == 0 or y % 6 == 0:
            col = shade(s, 0.7)
        if abs((y - scan) % 64) <= 2:
            col = a
            g = (*a, 230)
        elif hash2(x // 2, (y - scan) // 4, 33) > 0.86 and x % 2 == 0:
            col = p
            g = (*p, 180)
        return col, g

    if motif == "frostbite":
        col = lerp_rgb((18, 42, 86), (48, 108, 178), ny)
        # Faceted ice crystal star in center
        dx, dy = abs(x - 19.5), abs(y - 30.0)
        if dx < 1.5 and dy < 18 or dy < 1.5 and dx < 14 or (abs(dx - dy) < 1.4 and dx < 11):
            glint = 0.65 + 0.35 * math.sin(phase + dx * 0.4 + dy * 0.3)
            col = lerp_rgb(p, (255, 255, 255), glint)
            g = (*col, int(150 + 90 * glint))
        elif (x + y) % 7 == 0 and hash2(x, y, 41) > 0.75:
            col = (235, 248, 255)
            g = (200, 240, 255, 160)
        return col, g

    if motif == "dragon_scale":
        row = y // 6
        sx = (x + (row % 2) * 4) % 8
        sy = y % 6
        d = math.hypot(sx - 4.0, sy - 1.0)
        sheen = 0.5 + 0.5 * math.sin(nx * 4.0 + ny * 3.0 - phase)
        base_c = lerp_rgb(p, s, sheen)
        if d > 3.9:
            col = shade(base_c, 0.35)
        elif d > 3.1:
            col = lerp_rgb(base_c, a, 0.75)
            if sheen > 0.65:
                g = (*a, 160)
        else:
            col = shade(base_c, 0.68 + (3.2 - d) * 0.1)
        return col, g

    if motif == "inferno":
        n = math.sin(nx * 9.0 + math.cos(ny * 7.0 - phase) * 1.8) + math.cos(ny * 8.0 - phase * 1.5)
        if n > 0.85:
            col = a
            g = (*a, 240)
        elif n > 0.15:
            col = p
            g = (*p, 165)
        else:
            col = lerp_rgb((28, 12, 14), s, (n + 2.0) / 2.15)
        return col, g

    if motif == "starlight":
        bg = lerp_rgb((14, 12, 42), (38, 22, 74), ny)
        neb = math.sin(nx * 4.5 + ny * 2.5 + phase) * math.cos(nx * 2.8 - ny * 4.0)
        col = lerp_rgb(bg, s, max(0.0, neb * 0.7))
        if hash2(x, y, 55) > 0.94:
            tw = 0.4 + 0.6 * math.sin(phase + x * 1.7 + y * 2.3)
            col = lerp_rgb(a, (255, 255, 255), tw)
            g = (*col, int(140 + 110 * tw))
        return col, g

    if motif == "royal_mantle":
        border = (x < 4 or x > 35 or y < 4 or y > 59)
        if border:
            gleam = 0.75 + 0.25 * math.sin(phase + (x + y) * 0.3)
            col = shade(a, gleam)
            if (x == 2 or x == 37 or y == 2 or y == 61):
                g = (*a, 150)
        else:
            col = lerp_rgb((118, 16, 28), (168, 24, 38), 1.0 - abs(cx) * 0.5)
            # Royal fleur/crown motif in center
            if _in_crown(x, y, 19.5, 28.0, 1.0):
                col = a
                g = (*a, 170)
        return col, g

    if motif == "snow_leopard":
        # Monochrome high-contrast snow leopard on obsidian cloak (Lunar Cloak #0)
        col = (14, 15, 20)
        dx, dy = (x - 19.5) / 13.0, (y - 36.0) / 16.0
        head_r = math.hypot(dx, dy)
        ear_l = math.hypot((x - 10.5) / 4.0, (y - 21.0) / 5.0)
        ear_r = math.hypot((x - 28.5) / 4.0, (y - 21.0) / 5.0)
        if head_r < 1.0 or ear_l < 1.0 or ear_r < 1.0:
            fur = 185 + int(45 * (1.0 - min(1.0, head_r)))
            spot = hash2(x // 2, y // 2, 71)
            if spot > 0.76 and y > 23:
                fur = int(fur * 0.45)
            col = (fur, fur, min(255, fur + 6))
            # Piercing glowing ice-amber eyes
            if (abs(x - 14) <= 2 and abs(y - 33) <= 1) or (abs(x - 25) <= 2 and abs(y - 33) <= 1):
                col = (240, 248, 255)
                if x in (14, 25) and y == 33:
                    col = (20, 24, 30)
                else:
                    g = (195, 235, 255, 200)
            # Nose & muzzle
            if abs(x - 19.5) < 2.2 and 39 <= y <= 41:
                col = (55, 52, 60)
        return col, g

    if motif in ("obsidian_serpent", "ivory_serpent"):
        dark = motif == "obsidian_serpent"
        col = (12, 13, 18) if dark else (242, 244, 248)
        fg = (232, 238, 248) if dark else (28, 30, 38)
        # Vertical longsword
        if (abs(x - 19.5) < 1.6 and 9 <= y <= 55) or (abs(y - 20) <= 1 and 13 <= x <= 26):
            col = fg
        # Coiled serpent weaving S-curve around blade
        sx = 19.5 + math.sin((y - 14) * 0.28) * 5.2
        if 12 <= y <= 46 and abs(x - sx) < 2.1:
            col = lerp_rgb(fg, a, 0.3)
        # Serpent head + glowing eye
        if math.hypot(x - 22.0, y - 14.0) < 3.2:
            col = fg
            if x == 23 and y == 13:
                col = a
                g = (*a, 240)
        return col, g

    if motif == "mochi_kitten":
        col = (14, 14, 20)
        # Fluffy kitten head + ears + body (Lunar Cloak #2)
        if math.hypot((x - 19.5) / 11.5, (y - 32.0) / 10.5) < 1.0 or math.hypot((x - 19.5) / 9.5, (y - 46.0) / 10.0) < 1.0:
            col = (236, 232, 228) if y > 26 else (196, 188, 182)
            if y < 29 and abs(x - 19.5) < 4:
                col = (148, 140, 136)
        if (10 <= x <= 15 and 18 <= y <= 26 and (x - 10) >= (26 - y) // 2) or (24 <= x <= 29 and 18 <= y <= 26 and (29 - x) >= (26 - y) // 2):
            col = (212, 200, 196)
        # Big glossy cute eyes
        for ex in (15, 24):
            if math.hypot(x - ex, y - 32) < 2.5:
                col = (22, 20, 28)
            if x == ex - 1 and y == 31:
                col = (255, 255, 255)
        if abs(x - 19.5) < 1.3 and y == 35:
            col = (242, 160, 178)
        return col, g

    if motif == "lucky_clover":
        col = (14, 16, 20)
        leaves = [(16, 26), (23, 26), (16, 34), (23, 34)]
        for lx, ly in leaves:
            d = math.hypot(x - lx, y - ly)
            if d < 4.5:
                col = lerp_rgb(p, a, max(0.0, 1.0 - d / 4.5))
                if d < 2.2:
                    g = (*a, 150)
        if abs(x - (19.5 + (y - 34) * 0.16)) < 1.1 and 34 <= y <= 48:
            col = p
        return col, g

    if motif == "grim_reaper":
        # Stormy monochrome sky + cliff + hooded reaper with glowing scythe
        col = lerp_rgb((46, 50, 60), (16, 18, 24), ny)
        if hash2(x // 3, y // 3, 91) > 0.65 and y < 34:
            col = lerp_rgb(col, (145, 152, 166), 0.35)
        # Cliff silhouette
        if y > 44 + int(math.sin(x * 0.25) * 3):
            col = (18, 20, 26)
        # Cloaked reaper figure
        if abs(x - 17) < 4 + max(0, (y - 24) * 0.25) and 22 <= y <= 46:
            col = (10, 10, 14)
        # Scythe staff & curved gleaming blade
        if _dist_seg(x, y, 24, 16, 29, 46) < 1.1:
            col = (170, 176, 190)
        if 14 <= y <= 24 and 24 <= x <= 35 and abs(math.hypot(x - 24, y - 24) - 9.5) < 1.6:
            col = (235, 242, 255)
            g = (200, 225, 255, 200)
        return col, g

    if motif == "gothic_cross":
        col = (12, 12, 16)
        # Inner framed border
        if (x in (3, 36) and 4 <= y <= 59) or (y in (4, 59) and 3 <= x <= 36):
            col = (210, 214, 224)
        # Ornate flared gothic cross
        if (abs(x - 19.5) < 2.4 and 12 <= y <= 52) or (abs(y - 26) < 2.4 and 8 <= x <= 31):
            col = (240, 242, 250)
            if abs(x - 19.5) < 1.0 or abs(y - 26) < 1.0:
                col = (40, 42, 52)
        if abs(x - 19.5) < 1.5 and abs(y - 26) < 1.5:
            col = a
            g = (*a, 210)
        return col, g

    if motif == "ace_of_clubs":
        col = (16, 17, 22)
        # Ornate card frame & subtle background damask
        if (x + y) % 6 == 0 and 4 < x < 35 and 4 < y < 59:
            col = (26, 28, 36)
        if (x in (2, 37) and 3 <= y <= 60) or (y in (3, 60) and 2 <= x <= 37):
            col = (225, 228, 236)
        # Center club trefoil
        if (math.hypot(x - 19.5, y - 24.5) < 4.5 or math.hypot(x - 15.0, y - 31.5) < 4.5 or math.hypot(x - 24.0, y - 31.5) < 4.5
                or (abs(x - 19.5) < 2.2 and 32 <= y <= 41)):
            col = (244, 246, 252)
        # Corner pips
        if (math.hypot(x - 7, y - 9) < 2.1) or (math.hypot(x - 32, y - 54) < 2.1):
            col = (244, 246, 252)
        return col, g

    if motif == "midnight_rose":
        col = lerp_rgb((18, 18, 24), (36, 36, 46), (math.sin(nx * 5.0 + ny * 3.0) + 1) * 0.5)
        d = math.hypot(x - 19.5, y - 24.0)
        ang = math.atan2(y - 24.0, x - 19.5)
        if d < 10.5:
            spiral = math.sin(d * 1.45 + ang * 3.0)
            v = 115 + int(115 * max(0.0, spiral))
            col = (v, v, min(255, v + 8))
        if abs(x - 19.5) < 1.3 and 34 <= y <= 56:
            col = (140, 148, 155)
        if math.hypot((x - 13.5) / 5.0, (y - 44.0) / 2.6) < 1.0 or math.hypot((x - 25.5) / 5.0, (y - 47.0) / 2.6) < 1.0:
            col = (165, 172, 182)
        return col, g

    if motif == "all_seeing_eye":
        col = lerp_rgb((22, 18, 28), (44, 34, 52), ny)
        ex, ey = (x - 19.5) / 14.5, (y - 31.0) / 8.0
        almond = abs(ey) + 0.55 * (ex ** 2)
        if almond < 0.95:
            col = (232, 224, 212)
            ir = math.hypot(x - 19.5, y - 31.0)
            if ir < 6.2:
                col = lerp_rgb(p, a, 1.0 - ir / 6.2)
                g = (*col, 195)
            if ir < 2.6:
                col = (14, 12, 20)
            if x == 18 and y == 29:
                col = (255, 255, 255)
        elif almond < 1.12:
            col = (18, 14, 24)
        return col, g

    if motif == "blaze_spirit":
        col = lerp_rgb((18, 8, 12), (46, 14, 14), ny)
        # Dramatic fiery elemental spirit with jagged flame crown, glowing eyes & raised arms
        wx = math.sin(ny * 12.0 - phase) * 2.4 + math.cos(ny * 7.0 + phase) * 1.1
        crown = 10.0 + abs(math.sin(nx * 14.0 + phase)) * 5.0
        in_flame_hair = (y >= crown and y <= 24 and abs(x - 19.5 - wx * 0.6) < (y - crown + 3.5) * 0.55)
        head = math.hypot((x - 19.5) / 5.2, (y - 25.0) / 5.8)
        torso = math.hypot((x - 19.5 - wx * 0.3) / (6.8 - max(0.0, (y - 36) * 0.14)), (y - 42.0) / 14.0)
        arm_l = _dist_seg(x, y, 14, 34, 6, 22 + int(math.sin(phase) * 2)) < 2.2
        arm_r = _dist_seg(x, y, 25, 34, 33, 22 + int(math.cos(phase) * 2)) < 2.2
        if head < 1.0 or torso < 1.0 or in_flame_hair or arm_l or arm_r:
            t = min(head, torso) if (head < 1.0 or torso < 1.0) else 0.55
            col = lerp_rgb((255, 252, 200), p, min(1.0, t * 1.15))
            if t > 0.72:
                col = lerp_rgb(col, s, (t - 0.72) / 0.28)
            g = (*col, int(min(255, 175 + (1.0 - min(1.0, t)) * 80)))
            if (abs(x - 17) <= 1 and abs(y - 25) <= 1) or (abs(x - 22) <= 1 and abs(y - 25) <= 1):
                col = (255, 255, 255)
                g = (255, 255, 220, 255)
        elif hash2(x // 2, int((y + phase * 6) % 64) // 2, 107) > 0.92:
            col = a
            g = (*a, 220)
        return col, g

    if motif == "monarch_crown":
        col = lerp_rgb((152, 16, 28), (186, 22, 34), 1.0 - abs(cx) * 0.4)
        if _in_crown(x, y, 19.5, 31.0, 1.25):
            col = a
            g = (*a, 220)
        elif hash2(x, y, 111) > 0.965:
            col = (255, 226, 110)
            g = (255, 226, 110, 180)
        return col, g

    if motif in ("fallen_seraph", "celestial_seraph"):
        light_bg = motif == "fallen_seraph"
        col = lerp_rgb((242, 244, 250), (208, 214, 228), ny) if light_bg else lerp_rgb((8, 10, 20), (22, 24, 42), ny)
        sil = (14, 16, 22) if light_bg else (248, 250, 255)
        # Halo ring behind soaring angel
        if abs(math.hypot(x - 19.5, y - 22.0) - 10.5) < 0.9:
            col = lerp_rgb(col, sil, 0.48)
        dx = abs(x - 19.5)
        in_body = (dx < 2.1 and 20 <= y <= 46) or (math.hypot(x - 19.5, y - 18.0) < 2.4)
        # Wide dramatic swept-up multi-feather wings spanning full width!
        u = min(1.0, max(0.0, (dx - 1.5) / 17.0))
        top_edge = 26.0 - math.sin(u * math.pi * 0.85) * 14.5
        bot_edge = top_edge + (16.0 - u * 11.5) + math.sin(dx * 1.9) * 2.2
        in_wing = (1.8 <= dx <= 18.5) and (top_edge <= y <= bot_edge)
        if in_body or in_wing:
            col = sil
            if not light_bg:
                g = (225, 238, 255, 205)
        return col, g

    if motif == "fuji_sakura":
        # Japanese Mt. Fuji, crimson sun, and cherry blossom branch (Lunar Cloak #16!)
        col = lerp_rgb((248, 248, 252), (226, 236, 248), ny)
        # Crimson rising sun
        if math.hypot(x - 11.0, y - 16.0) < 5.2:
            col = (224, 48, 72)
        # Mt. Fuji silhouette
        mtn_y = 33.0 + abs(x - 19.5) * 1.18
        if y >= mtn_y:
            snow_line = 39.5 + math.sin(x * 0.9) * 1.8
            if y < snow_line:
                col = (248, 252, 255)
            else:
                col = lerp_rgb((54, 88, 152), (32, 56, 114), (y - snow_line) / 24.0)
        # Water reflection at bottom
        if y > 54:
            col = lerp_rgb((175, 205, 235), (230, 242, 252), (math.sin(y * 1.8) + 1) * 0.5)
        # Cherry branch & blossoms across top right
        if _dist_seg(x, y, 39, 12, 13, 28) < 1.4 or _dist_seg(x, y, 28, 19, 20, 12) < 1.0:
            col = (72, 42, 40)
        for bx, by in [(34, 15), (27, 17), (21, 13), (22, 23), (15, 26), (31, 22), (18, 20)]:
            if math.hypot(x - bx, y - by) < 2.6:
                col = (255, 182, 202) if math.hypot(x - bx, y - by) > 1.0 else (255, 235, 242)
        return col, g

    if motif == "cyber_chains":
        # Electric cyan crystal shards + steel diagonal chains on obsidian (Lunar Cloak #19)
        col = lerp_rgb((8, 12, 24), (14, 24, 48), ny)
        # Glowing fractured crystal cluster in center
        for cx0, cy0, r in [(19, 26, 7), (14, 34, 5), (25, 33, 5), (20, 42, 4)]:
            d = abs(x - cx0) + abs(y - cy0) * 0.85
            if d < r:
                pulse = 0.65 + 0.35 * math.sin(phase + cx0)
                col = lerp_rgb(p, a, (1.0 - d / r) * pulse)
                g = (*col, int(170 + 85 * pulse))
        # Diagonal steel chain links
        for c_off in (-10, 12):
            if abs((y - x * 1.15 - c_off)) < 1.5 and (x + y) % 4 != 0:
                col = (182, 196, 214)
        return col, g

    if motif == "crescent_moon":
        col = (12, 13, 18)
        # Pointed gothic arch + glowing crescent moon (Lunar Cloak #25)
        arch = abs(math.hypot((x - 19.5) / 13.0, (y - 36.0) / 19.0) - 1.0)
        if arch < 0.11 and y < 52:
            col = (242, 244, 250)
            g = (220, 235, 255, 175)
        if math.hypot(x - 18.5, y - 33.0) < 7.8 and math.hypot(x - 22.5, y - 31.0) > 6.8:
            col = (250, 252, 255)
            g = (220, 240, 255, 235)
        if hash2(x, y, 131) > 0.975:
            col = (240, 245, 255)
            g = (220, 235, 255, 190)
        return col, g

    if motif == "starlit_deer":
        # Glowing white spirit stag under crescent moon & dark forest (Lunar Cloak #17)
        col = (10, 12, 18)
        # Forest pine silhouettes on left & right edges
        if (x < 9 or x > 30) and y > 18 + (x % 5) * 3:
            col = (20, 22, 30)
        # Glowing crescent moon top right
        if math.hypot(x - 28, y - 13) < 4.2 and math.hypot(x - 30, y - 12) > 3.6:
            col = (248, 250, 255)
            g = (230, 242, 255, 220)
        # Spirit deer silhouette center bottom
        in_deer = (
            (15 <= x <= 23 and 39 <= y <= 44)  # torso
            or (15 <= x <= 17 and 44 <= y <= 52)  # back legs
            or (21 <= x <= 23 and 44 <= y <= 52)  # front legs
            or (21 <= x <= 24 and 32 <= y <= 40)  # neck & head
            or (_dist_seg(x, y, 22, 32, 18, 26) < 0.9)  # left antler
            or (_dist_seg(x, y, 23, 32, 27, 26) < 0.9)  # right antler
        )
        if in_deer:
            col = (245, 250, 255)
            g = (205, 235, 255, 220)
        return col, g

    if motif == "candle_skull":
        col = lerp_rgb((28, 12, 18), (14, 10, 16), ny)
        # Skull cranium & jaw + ruby eyes + flickering candle flame on top
        if math.hypot((x - 19.5) / 8.5, (y - 37.0) / 9.0) < 1.0 or (15 <= x <= 24 and 43 <= y <= 49):
            col = (234, 228, 222)
            if (math.hypot(x - 16, y - 37) < 2.2) or (math.hypot(x - 23, y - 37) < 2.2):
                col = (235, 42, 68)
                g = (255, 65, 90, 235)
        # Candle & flickering flame
        if 18 <= x <= 21 and 20 <= y <= 29:
            col = (245, 236, 210)
        if math.hypot((x - 19.5) / 2.4, (y - 16.0) / 4.2) < 1.0:
            col = (255, 225, 95)
            g = (255, 200, 75, 245)
        return col, g

    if motif == "pastel_nebula":
        t = (math.sin(nx * 3.8 + phase) * 0.5 + 0.5) * 0.55 + (math.cos(ny * 3.2 - phase) * 0.5 + 0.5) * 0.45
        c1 = lerp_rgb((255, 192, 224), (178, 228, 255), t)
        c2 = lerp_rgb((255, 238, 180), (206, 185, 255), (nx + ny) * 0.5)
        col = lerp_rgb(c1, c2, 0.45)
        if hash2(x, y, 149) > 0.955:
            col = (255, 255, 255)
            g = (255, 240, 255, 200)
        return col, g

    # Fallback rich gradient
    col = lerp_rgb(s, p, ny)
    return col, g


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
        # Three crown spikes
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
        (255, v, 70),
        (q, 255, 70),
        (60, 255, v),
        (60, q, 255),
        (v, 80, 255),
        (255, 70, q),
    ][i]


# ==============================================================================
# 2. PALETTE-ATLAS 64x64 TEXTURES FOR WINGS, HEADWEAR, AURAS, PETS, COMPANIONS
# ==============================================================================
# Every non-cloak cosmetic uses a 64x64 4-zone high-detail shaded material atlas:
#   Zone A (0..32,  0..32): Primary material (feathers/fur/armor/membrane with pixel-art shading & veins)
#   Zone B (32..64, 0..32): Secondary material (trim/belly/inner wing/bone struts/metal)
#   Zone C (0..32, 32..64): Accent & Glowing energy / crystals / runes / flame gradient
#   Zone D (32..64,32..64): Eyes / Face details / Badges / Dark outline / Metallic highlights
def paint_rig_atlas(
    style: str,
    primary: int,
    secondary: int,
    accent: int,
    eye_color: int,
    translucent_alpha: int,
    emissive_zones: str,  # e.g. "C", "CD", "AC", "ABCD", ""
    out_path: Path,
    glow_path: Path | None,
) -> bool:
    base = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0)) if glow_path else None
    p = hex_rgb(primary)
    s = hex_rgb(secondary)
    a = hex_rgb(accent)
    e = hex_rgb(eye_color)
    has_glow = False

    for y in range(64):
        for x in range(64):
            zone = ("A" if x < 32 else "B") if y < 32 else ("C" if x < 32 else "D")
            lx, ly = x % 32, y % 32
            nx, ny = lx / 31.0, ly / 31.0

            if zone == "A":
                col, al = _zone_a(style, lx, ly, nx, ny, p, s, a, translucent_alpha)
            elif zone == "B":
                col, al = _zone_b(style, lx, ly, nx, ny, p, s, a, translucent_alpha)
            elif zone == "C":
                col, al = _zone_c(style, lx, ly, nx, ny, p, s, a, translucent_alpha)
            else:
                col, al = _zone_d(style, lx, ly, nx, ny, p, s, a, e)

            base.putpixel((x, y), (*col, al))

            if glow is not None:
                is_em = zone in emissive_zones
                # Also glow veins/runes/eyes inside Zone A/D for rune-tigers, magma-wings, etc.
                if style in ("rune_fur", "magma_membrane", "honeycomb", "galaxy") and zone == "A":
                    if _is_pattern_glow(style, lx, ly):
                        is_em = True
                        col = a
                if zone == "D" and _is_eye_glow(lx, ly):
                    is_em = True
                if is_em and al > 20:
                    glow.putpixel((x, y), (*col, min(255, max(160, al))))
                    has_glow = True

    out_path.parent.mkdir(parents=True, exist_ok=True)
    base.save(out_path)
    if glow_path is not None and glow is not None and has_glow:
        glow.save(glow_path)
        return True
    if glow_path is not None and glow_path.exists():
        glow_path.unlink()
    return False


def _is_pattern_glow(style: str, lx: int, ly: int) -> bool:
    if style == "rune_fur":
        return ((lx + ly * 2) % 9 == 0) or ((lx * 2 - ly) % 11 == 0 and ly % 3 == 0)
    if style == "magma_membrane":
        return abs(math.sin(lx * 0.45 + math.cos(ly * 0.4) * 1.4)) > 0.88
    if style == "honeycomb":
        hx, hy = lx % 6, ly % 6
        return hx == 0 or hy == 0 or (hx == 3 and hy == 3)
    if style == "galaxy":
        return hash2(lx, ly, 211) > 0.92
    return False


def _is_eye_glow(lx: int, ly: int) -> bool:
    # Eyes region in Zone D: (4..12, 4..12)
    return 4 <= lx <= 12 and 4 <= ly <= 12


def _zone_a(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, alpha: int):
    if style == "feathers":
        # Layered feathered barbs with bright tips & shaded quills
        barb = 0.86 + 0.14 * math.sin(lx * 1.2 + ly * 0.45)
        grad = lerp_rgb(s, p, ny * 0.7 + 0.3)
        if ny > 0.78:
            grad = lerp_rgb(grad, a, (ny - 0.78) / 0.22)
        if lx in (0, 31) or ly == 0:
            grad = shade(grad, 0.78)
        return shade(grad, barb), alpha
    if style == "rainbow_feathers":
        col = _rainbow((nx * 0.55 + ny * 0.45) % 1.0)
        barb = 0.88 + 0.12 * math.sin(lx * 1.3 + ly * 0.4)
        return shade(col, barb), alpha
    if style == "magma_membrane":
        if _is_pattern_glow(style, lx, ly):
            return a, 255
        grad = lerp_rgb(p, s, ny)
        return shade(grad, 0.85 + 0.15 * hash2(lx, ly, 3)), alpha
    if style == "honeycomb":
        hx, hy = lx % 6, ly % 6
        if hx == 0 or hy == 0:
            return lerp_rgb(a, (255, 255, 255), 0.5), 245
        grad = lerp_rgb(p, s, (nx + ny) * 0.5)
        return shade(grad, 0.85 + 0.15 * ((hx + hy) / 10.0)), alpha
    if style == "butterfly":
        # Rich stained-glass butterfly wing veining + edge dots
        border = (nx < 0.12 or nx > 0.88 or ny < 0.12 or ny > 0.88)
        if border:
            if (lx + ly) % 5 == 0:
                return (255, 250, 240), 255
            return (22, 18, 28), 255
        vein = abs(math.sin((nx - 0.5) * 9.0 + ny * 3.0)) < 0.12
        if vein:
            return (28, 22, 34), 240
        grad = lerp_rgb(p, a, ny * 0.75 + nx * 0.25)
        return grad, alpha
    if style == "galaxy":
        col = lerp_rgb(p, s, (math.sin(nx * 4.0 + ny * 3.0) + 1) * 0.5)
        if _is_pattern_glow(style, lx, ly):
            return lerp_rgb(a, (255, 255, 255), 0.6), 255
        return col, alpha
    if style == "rune_fur":
        if _is_pattern_glow(style, lx, ly):
            return a, 255
        n = 0.84 + 0.16 * hash2(lx // 2, ly // 2, 17)
        return shade(p, n), 255
    if style == "fur":
        n = 0.86 + 0.18 * hash2(lx // 2, ly // 2, 23)
        grad = lerp_rgb(p, s, ny * 0.35)
        return shade(grad, n), 255
    if style == "bee_stripes":
        stripe = (ly // 6) % 2 == 0
        col = p if stripe else (26, 20, 18)
        return shade(col, 0.92 + 0.12 * (1.0 - abs(nx - 0.5))), 255
    if style == "metal":
        sheen = 0.78 + 0.28 * math.sin((nx + ny) * math.pi)
        col = lerp_rgb(p, s, 0.25)
        if lx in (2, 29) and ly in (2, 29):
            col = shade(col, 0.55)
        return shade(col, sheen), 255
    if style == "crystal":
        facet = ((lx // 6 + ly // 6) % 3) * 0.12
        col = lerp_rgb(p, a, min(1.0, ny * 0.65 + facet))
        if lx == 1 or ly == 1:
            col = lerp_rgb(col, (255, 255, 255), 0.6)
        return col, alpha
    if style == "flame":
        w = 0.5 + 0.5 * math.sin(lx * 0.5 + math.cos(ly * 0.35) * 1.6)
        col = lerp_rgb(s, p, ny)
        col = lerp_rgb(col, a, w * 0.6)
        return col, alpha
    # Default smooth shaded pixel material
    n = 0.90 + 0.12 * hash2(lx // 2, ly // 2, 31)
    return shade(lerp_rgb(p, s, ny * 0.35), n), alpha


def _zone_b(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, alpha: int):
    sheen = 0.84 + 0.18 * math.sin((nx + ny) * math.pi)
    if style in ("feathers", "rainbow_feathers", "butterfly"):
        return shade(lerp_rgb(s, p, 0.25), sheen), 255
    if style in ("fur", "rune_fur", "bee_stripes"):
        n = 0.88 + 0.14 * hash2(lx // 2, ly // 2, 43)
        return shade(s, n), 255
    return shade(s, sheen), 255


def _zone_c(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, alpha: int):
    # Glowing crystal / energy / core / rune gradient
    pulse = 0.85 + 0.2 * (1.0 - math.hypot(nx - 0.5, ny - 0.5))
    col = lerp_rgb(a, (255, 255, 255), max(0.0, 0.45 - math.hypot(nx - 0.5, ny - 0.5)))
    if style == "rainbow_feathers":
        col = _rainbow((nx + ny) * 0.5)
    return shade(col, min(1.15, pulse)), min(255, max(185, alpha))


def _zone_d(style: str, lx: int, ly: int, nx: float, ny: float, p, s, a, e):
    # Top-left sub-quadrant (0..16, 0..16): glowing eye iris + white glint
    if lx < 16 and ly < 16:
        if 4 <= lx <= 12 and 4 <= ly <= 12:
            if lx <= 6 and ly <= 6:
                return (255, 255, 255), 255
            return e, 255
        return (18, 18, 24), 255
    # Top-right sub-quadrant (16..32, 0..16): dark outline / obsidian / claws / nose
    if lx >= 16 and ly < 16:
        return shade((28, 28, 36), 0.85 + 0.2 * hash2(lx, ly, 5)), 255
    # Bottom-left sub-quadrant (0..16, 16..32): bright metallic gold / ivory highlight
    if lx < 16 and ly >= 16:
        return lerp_rgb(a, (255, 248, 220), 0.45), 255
    # Bottom-right sub-quadrant (16..32, 16..32): warm blush / collar / trim
    return lerp_rgb(p, a, 0.55), 255
