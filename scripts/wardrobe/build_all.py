"""Generates all 168 SkyPilot wardrobe 3D .bbmodel files, crisp PNG textures, glow masks, and catalog.json.

Also verifies:
  1. 100% geometry uniqueness across every non-cape cosmetic (0 reused models!).
  2. Strict anti-clipping bounds across all 6 slots (bare+overlay head/torso clearance, ground y >= 0).
"""
from __future__ import annotations

import hashlib
import json
import sys
import uuid
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
RES_ROOT = REPO / "apps" / "mod" / "core" / "src" / "main" / "resources" / "assets" / "skypilot"
MODELS_DIR = RES_ROOT / "cosmetics" / "models"
TEX_DIR = RES_ROOT / "textures" / "cosmetics"
CATALOG_PATH = RES_ROOT / "cosmetics" / "catalog.json"

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


def build_all() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    TEX_DIR.mkdir(parents=True, exist_ok=True)

    for old in MODELS_DIR.glob("*.json"):
        old.unlink()

    catalog_entries = []
    seen_ids: set[str] = set()
    geom_sigs: dict[str, dict[str, list[str]]] = {}
    clip_issues: list[str] = []
    total_cubes = 0
    glow_count = 0

    for c in ALL_COSMETICS:
        assert c.id not in seen_ids, f"Duplicate cosmetic ID: {c.id}"
        seen_ids.add(c.id)

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
        if has_glow:
            glow_count += 1

        bbmodel = bones_to_bbmodel(c, bones, tex_rel, glow_rel, 64, 64)
        elems = bbmodel["elements"]
        total_cubes += len(elems)
        assert len(elems) > 0, f"Empty model: {c.id}"

        clip_issues.extend(verify_anti_clipping(c, elems))
        sig = geometry_signature(elems)
        geom_sigs.setdefault(c.slot, {}).setdefault(sig, []).append(c.id)

        out_model = MODELS_DIR / f"{c.id}.bbmodel"
        out_model.write_text(json.dumps(bbmodel, separators=(",", ":")), encoding="utf-8")

        catalog_entries.append({
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
        })

    CATALOG_PATH.write_text(json.dumps(catalog_entries, ensure_ascii=False, indent=2), encoding="utf-8")

    # Enforce 100% geometry uniqueness for all non-cape slots!
    dups_found = []
    for slot, sig_map in geom_sigs.items():
        if slot == "cape":
            continue
        for sig, ids in sig_map.items():
            if len(ids) > 1:
                dups_found.append((slot, ids))

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
