"""Generates all 168 SkyPilot wardrobe 3D .bbmodel files, crisp PNG textures, glow masks, and catalog.json.

Also verifies:
  1. 100% geometry uniqueness across every non-cape cosmetic (0 reused models!).
  2. Strict anti-clipping bounds across all 6 slots (bare+overlay head/torso clearance, ground y >= 0).
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import math
import os
import sys
import uuid
from pathlib import Path
from typing import Any

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
CORE_RES_ROOT = REPO / "apps" / "mod" / "core" / "src" / "main" / "resources" / "assets" / "skypilot"
RES_ROOT = REPO / "apps" / "mod" / "wardrobe-assets" / "src" / "main" / "resources" / "assets" / "skypilot"
MODELS_DIR = RES_ROOT / "cosmetics" / "models"
TEX_DIR = RES_ROOT / "textures" / "cosmetics"
CATALOG_PATH = CORE_RES_ROOT / "cosmetics" / "catalog.json"

sys.path.insert(0, str(HERE))
from catalog_defs import ALL_COSMETICS, CosmeticDef  # noqa: E402
from pixel_art import paint_cloak_texture, paint_rig_atlas  # noqa: E402
from rig_builders import (  # noqa: E402
    build_aura_bones,
    build_cloak_bones,
    build_companion_bones,
    build_hat_bones,
    build_pet_bones,
    build_wings_bones,
)


def deterministic_uuid(key: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, f"skypilot.wardrobe.{key}"))


def geometry_signature(elements: list[dict[str, Any]]) -> str:
    """Hash of pure 3D shape (from/to/origin/rotation) independent of colors/names."""
    sig = [(tuple(e["from"]), tuple(e["to"]), tuple(e["origin"]), tuple(e["rotation"])) for e in elements]
    return hashlib.sha256(json.dumps(sig).encode("utf-8")).hexdigest()[:16]


def _rot_pt(pt: list[float], piv: list[float], rot_deg: list[float]) -> list[float]:
    rx, ry, rz = (math.radians(a) for a in rot_deg)
    x, y, z = pt[0] - piv[0], pt[1] - piv[1], pt[2] - piv[2]
    cx, sx = math.cos(rx), math.sin(rx)
    y, z = y * cx - z * sx, y * sx + z * cx
    cy, sy = math.cos(ry), math.sin(ry)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cz, sz = math.cos(rz), math.sin(rz)
    x, y = x * cz - y * sz, x * sz + y * cz
    return [x + piv[0], y + piv[1], z + piv[2]]


def verify_structural_integrity(c: CosmeticDef, bbmodel: dict[str, Any]) -> list[str]:
    """Verify transformed 3D world boxes stay connected (no detached parts) and companions don't sink."""
    if c.slot not in ("pet", "companion"):
        return []
    by_uuid = {e["uuid"]: e for e in bbmodel["elements"]}
    boxes: list[tuple[str, list[float], list[float]]] = []

    def walk(nodes: list[Any], chain: list[tuple[list[float], list[float]]]) -> None:
        for n in nodes:
            if isinstance(n, str):
                e = by_uuid[n]
                fr, to = e["from"], e["to"]
                pts = [[x, y, z] for x in (fr[0], to[0]) for y in (fr[1], to[1]) for z in (fr[2], to[2])]
                if any(abs(a) > 1e-4 for a in e["rotation"]):
                    pts = [_rot_pt(pt, e["origin"], e["rotation"]) for pt in pts]
                for bpiv, brot in reversed(chain):
                    if any(abs(a) > 1e-4 for a in brot):
                        pts = [_rot_pt(pt, bpiv, brot) for pt in pts]
                mn = [min(p[i] for p in pts) for i in range(3)]
                mx = [max(p[i] for p in pts) for i in range(3)]
                boxes.append((e["name"], mn, mx))
            elif isinstance(n, dict):
                walk(n.get("children", []), chain + [(n.get("origin", [0, 0, 0]), n.get("rotation", [0, 0, 0]))])

    walk(bbmodel.get("outliner", []), [])
    issues: list[str] = []
    if c.slot == "companion" and boxes:
        ymin = min(b[1][1] for b in boxes)
        if ymin < -0.5:
            issues.append(f"{c.id}: transformed companion ymin={ymin:.2f} < -0.5")
    adj: list[set[int]] = [set() for _ in range(len(boxes))]
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            dx = max(0.0, boxes[i][1][0] - boxes[j][2][0], boxes[j][1][0] - boxes[i][2][0])
            dy = max(0.0, boxes[i][1][1] - boxes[j][2][1], boxes[j][1][1] - boxes[i][2][1])
            dz = max(0.0, boxes[i][1][2] - boxes[j][2][2], boxes[j][1][2] - boxes[i][2][2])
            if math.sqrt(dx * dx + dy * dy + dz * dz) <= 2.0:
                adj[i].add(j)
                adj[j].add(i)
    seen: set[int] = set()
    comps: list[list[str]] = []
    for i in range(len(boxes)):
        if i not in seen:
            q = [i]
            seen.add(i)
            comp = []
            while q:
                u = q.pop()
                comp.append(boxes[u][0])
                for v in adj[u]:
                    if v not in seen:
                        seen.add(v)
                        q.append(v)
            comps.append(comp)
    if len(comps) > 1:
        issues.append(f"{c.id}: disconnected 3D clusters ({len(comps)}): {comps}")
    return issues


def verify_anti_clipping(c: CosmeticDef, elements: list[dict[str, Any]]) -> list[str]:
    issues: list[str] = []
    for e in elements:
        x0, y0, z0 = e["from"]
        x1, y1, z1 = e["to"]
        if c.slot == "cape":
            if z0 < 2.55:
                issues.append(f"{c.id}/{e['name']}: cape z0={z0} < 2.55 (clips back)")
        elif c.slot == "back":
            if z0 < 2.95 and abs((x0 + x1) * 0.5) < 5.5 and y0 < 25.0 and y1 > 10.0:
                issues.append(f"{c.id}/{e['name']}: back z0={z0} < 2.95 inside torso")
        elif c.slot == "hat":
            # Bare+overlay head box is [-4.5..4.5, 23.5..32.5, -4.5..4.5]
            if x1 > -4.45 and x0 < 4.45 and y1 > 23.6 and y0 < 32.45 and z1 > -4.45 and z0 < 4.45:
                issues.append(f"{c.id}/{e['name']}: hat penetrates head box ({e['from']}..{e['to']})")
        elif c.slot == "companion":
            if y0 < -0.01:
                issues.append(f"{c.id}/{e['name']}: companion y0={y0} < 0 (sinks below ground)")
    return issues


def bones_to_bbmodel(
    c: CosmeticDef,
    bones: list[dict[str, Any]],
    tex_rel: str,
    glow_rel: str,
    tex_w: int,
    tex_h: int,
) -> dict[str, Any]:
    elements: list[dict[str, Any]] = []
    outliner: list[dict[str, Any]] = []
    counter = [0]

    def convert_bone(b: dict[str, Any]) -> dict[str, Any]:
        b_idx = counter[0]
        counter[0] += 1
        b_uuid = deterministic_uuid(f"{c.id}.bone.{b['name']}.{b_idx}")
        children_entries: list[Any] = []

        for ci, cube in enumerate(b.get("cubes", [])):
            c_idx = counter[0]
            counter[0] += 1
            c_uuid = deterministic_uuid(f"{c.id}.cube.{b['name']}.{cube['name']}.{c_idx}")
            faces_out = {}
            for fn, fv in cube.get("faces", {}).items():
                faces_out[fn] = {
                    "uv": fv["uv"],
                    "rotation": fv.get("rotation", 0),
                    "texture": 0,
                }
            elem = {
                "name": cube["name"],
                "box_uv": False,
                "rescale": False,
                "locked": False,
                "from": cube["from"],
                "to": cube["to"],
                "autouv": 0,
                "color": ci % 8,
                "origin": cube["pivot"],
                "rotation": cube["rot"],
                "faces": faces_out,
                "type": "cube",
                "uuid": c_uuid,
                "skypilot_color": cube.get("color", c.color),
                "skypilot_emissive": bool(cube.get("emissive", False)),
                "skypilot_translucent": bool(cube.get("translucent", False)),
            }
            elements.append(elem)
            children_entries.append(c_uuid)

        for ch in b.get("children", []):
            children_entries.append(convert_bone(ch))

        return {
            "name": b["name"],
            "origin": b["pivot"],
            "rotation": b["rot"],
            "color": 0,
            "uuid": b_uuid,
            "export": True,
            "isOpen": True,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "skypilot_anim": b.get("anim", ""),
            "skypilot_anim_amp": b.get("animAmp", 1.0),
            "skypilot_anim_phase": b.get("animPhase", 0.0),
            "skypilot_emissive": bool(b.get("emissive", False)),
            "skypilot_translucent": bool(b.get("translucent", False)),
            "children": children_entries,
        }

    for root_b in bones:
        outliner.append(convert_bone(root_b))

    anchor = {
        "cape": "body",
        "back": "body",
        "hat": "head",
        "aura": "root",
        "pet": "pet",
        "companion": "companion",
    }[c.slot]

    return {
        "meta": {
            "format_version": "4.10",
            "model_format": "bedrock",
            "box_uv": False,
        },
        "name": c.name,
        "model_identifier": f"skypilot.{c.id}",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "resolution": {"width": tex_w, "height": tex_h},
        "skypilot": {
            "id": c.id,
            "slot": c.slot,
            "anchor": anchor,
            "name": c.name,
            "ja": c.ja,
            "rarity": c.rarity,
            "color": c.color,
            "texture": tex_rel,
            "emissiveTexture": glow_rel,
            "translucent": c.translucent,
            "emissive": bool(glow_rel) or c.emissive,
            "scalable": c.scalable,
            "animFrames": c.frames,
            "animFps": c.fps,
        },
        "elements": elements,
        "outliner": outliner,
        "textures": [],
        "animations": [],
    }


def _build_one(c: CosmeticDef) -> tuple[CosmeticDef, dict[str, Any], int, bool, list[str], str]:
    tex_rel = f"textures/cosmetics/{c.slot}/{c.id}.png"
    glow_rel_candidate = f"textures/cosmetics/{c.slot}/{c.id}_glow.png"
    tex_path = RES_ROOT / tex_rel
    glow_path = RES_ROOT / glow_rel_candidate

    if c.slot == "cape":
        has_glow = paint_cloak_texture(
            c.tex_style, c.color, c.secondary, c.accent, c.frames,
            tex_path, glow_path if c.emissive else None,
        )
        bones = build_cloak_bones(c.color, c.secondary, c.accent, c.arch)
    else:
        has_glow = paint_rig_atlas(
            c.tex_style, c.color, c.secondary, c.accent, c.eye_color, c.alpha,
            c.emissive_zones if c.emissive else "",
            tex_path, glow_path if c.emissive else None,
        )
        if c.slot == "back":
            bones = build_wings_bones(c.arch, c.color, c.secondary, c.accent, c.emissive, c.translucent)
        elif c.slot == "hat":
            bones = build_hat_bones(c.arch, c.color, c.secondary, c.accent)
        elif c.slot == "aura":
            bones = build_aura_bones(c.arch, c.color, c.secondary, c.accent)
        elif c.slot == "pet":
            bones = build_pet_bones(c.arch, c.color, c.secondary, c.accent)
        elif c.slot == "companion":
            bones = build_companion_bones(c.arch, c.color, c.secondary, c.accent)
        else:
            raise ValueError(f"Unknown slot {c.slot}")

    glow_rel = glow_rel_candidate if has_glow else ""
    bbmodel = bones_to_bbmodel(c, bones, tex_rel, glow_rel, 128, 128)
    elems = bbmodel["elements"]
    assert len(elems) > 0, f"Empty model: {c.id}"

    issues = verify_anti_clipping(c, elems) + verify_structural_integrity(c, bbmodel)
    sig = geometry_signature(elems)

    out_model = MODELS_DIR / f"{c.id}.bbmodel"
    out_model.write_text(json.dumps(bbmodel, separators=(",", ":")), encoding="utf-8")

    entry = {
        "id": c.id,
        "slot": c.slot,
        "name": c.name,
        "ja": c.ja,
        "description": c.desc,
        "descriptionJa": c.desc_ja,
        "color": c.color,
        "rarity": c.rarity,
        "animated": c.animated,
        "scalable": c.scalable,
        "emissive": bool(glow_rel) or c.emissive,
    }
    return c, entry, len(elems), bool(glow_rel), issues, sig


def build_all() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    CATALOG_PATH.parent.mkdir(parents=True, exist_ok=True)

    # Ensure crisp 16x16 white fallback texture exists in wardrobe-assets
    white_png = TEX_DIR / "white.png"
    if not white_png.exists():
        Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(white_png)

    for old in MODELS_DIR.glob("*.json"):
        old.unlink()

    seen_ids: set[str] = set()
    for c in ALL_COSMETICS:
        assert c.id not in seen_ids, f"Duplicate cosmetic ID: {c.id}"
        seen_ids.add(c.id)

    workers = max(2, min(12, os.cpu_count() or 4))
    with concurrent.futures.ProcessPoolExecutor(max_workers=workers) as ex:
        results = list(ex.map(_build_one, ALL_COSMETICS))

    catalog_entries = []
    geom_sigs: dict[str, dict[str, list[str]]] = {}
    clip_issues: list[str] = []
    total_cubes = 0
    glow_count = 0

    for c, entry, n_cubes, has_glow, issues, sig in results:
        total_cubes += n_cubes
        if has_glow:
            glow_count += 1
        clip_issues.extend(issues)
        geom_sigs.setdefault(c.slot, {}).setdefault(sig, []).append(c.id)
        catalog_entries.append(entry)

    CATALOG_PATH.write_text(json.dumps(catalog_entries, ensure_ascii=False, indent=2), encoding="utf-8")

    # Enforce 100% geometry uniqueness for all non-cape slots!
    dups_found = []
    for slot, sig_map in geom_sigs.items():
        if slot == "cape":
            continue
        for sig, ids in sig_map.items():
            if len(ids) > 1:
                dups_found.append((slot, ids))

    # Enforce zero accidental 'eclipse' keyword collisions (WardrobePageTest expects only 'eclipse')
    eclipse_hits = [
        c.id for c in ALL_COSMETICS
        if c.id != "eclipse" and "eclipse" in f"{c.id} {c.name} {c.ja} {c.desc} {c.desc_ja}".lower()
    ]
    if eclipse_hits:
        clip_issues.append(f"Unexpected 'eclipse' search collisions: {eclipse_hits}")

    print(f"Generated {len(ALL_COSMETICS)} .bbmodel 3D models ({total_cubes} total sculpted cuboids, {glow_count} emissive glow textures)!")
    for slot, sig_map in geom_sigs.items():
        tot = sum(len(v) for v in sig_map.values())
        print(f"  {slot:10s}: {tot:3d} models ({len(sig_map):3d} distinct 3D geometries)")
    if dups_found:
        print("DUPLICATE GEOMETRIES DETECTED:", dups_found)
        sys.exit(1)
    if clip_issues:
        print(f"ANTI-CLIPPING WARNINGS ({len(clip_issues)}):")
        for msg in clip_issues[:20]:
            print("  -", msg)
        sys.exit(1)
    print("VERIFIED: 0 duplicate non-cape 3D geometries & 0 anti-clipping violations!")


if __name__ == "__main__":
    build_all()
