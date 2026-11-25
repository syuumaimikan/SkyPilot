"""Builds & visually verifies flagship SkyPilot 3D .bbmodel cosmetics LIVE in Blockbench MCP
(http://localhost:3003/bb-mcp) using official MCP modeling tools, and stitches a 36-cloak preview sheet."""
from __future__ import annotations

import base64
import json
import math
import urllib.request
from pathlib import Path
from PIL import Image, ImageDraw

MCP_URL = "http://localhost:3003/bb-mcp"
REPO = Path(__file__).resolve().parent.parent.parent
RES_ROOT = REPO / "apps" / "mod" / "core" / "src" / "main" / "resources" / "assets" / "skypilot"
MODELS_DIR = RES_ROOT / "cosmetics" / "models"
SHOT_DIR = Path(r"C:\Users\yuang\.gemini\antigravity\brain\18f7e90b-f337-4bec-a214-a8c9ddc04be4\scratch\bb_renders")


class BlockbenchMCP:
    def __init__(self, url: str = MCP_URL):
        self.url = url
        self.sid: str | None = None
        self.req_id = 0
        self._rpc("initialize", {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "skypilot-wardrobe-studio", "version": "1.0"},
        })
        self._rpc("notifications/initialized", {}, notify=True)

    def _rpc(self, method: str, params: dict, notify: bool = False) -> dict:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        }
        if self.sid:
            headers["mcp-session-id"] = self.sid
        payload: dict = {"jsonrpc": "2.0", "method": method, "params": params}
        if not notify:
            self.req_id += 1
            payload["id"] = self.req_id
        req = urllib.request.Request(self.url, data=json.dumps(payload).encode("utf-8"), headers=headers)
        with urllib.request.urlopen(req, timeout=25) as r:
            if not self.sid and r.headers.get("mcp-session-id"):
                self.sid = r.headers.get("mcp-session-id")
            raw = r.read().decode("utf-8", "replace").strip()
            if not raw:
                return {}
            if raw.startswith("event:") or raw.startswith("data:"):
                for line in raw.splitlines():
                    if line.startswith("data:"):
                        return json.loads(line[5:].strip())
            return json.loads(raw)

    def tool(self, name: str, arguments: dict) -> dict:
        res = self._rpc("tools/call", {"name": name, "arguments": arguments})
        if "error" in res:
            raise RuntimeError(f"MCP error in {name}: {res['error']}")
        return res.get("result", {})


def build_and_render_in_blockbench(bb: BlockbenchMCP, model_id: str, cam_pos: list[float], cam_target: list[float], out_png: Path) -> None:
    model_path = MODELS_DIR / f"{model_id}.bbmodel"
    data = json.loads(model_path.read_text(encoding="utf-8"))
    sp = data["skypilot"]
    tex_path = RES_ROOT / sp["texture"]
    tex_w = data["resolution"]["width"]
    tex_h = data["resolution"]["height"]
    frames = sp.get("animFrames", 1)

    # 1. Create a fresh Bedrock Entity project in Blockbench
    bb.tool("create_project", {"name": f"SkyPilot_{model_id}", "format": "bedrock"})

    # 2. Load the first frame of the texture via base64 data URL (instant, no file dialog!)
    if frames > 1:
        im = Image.open(tex_path).crop((0, 0, tex_w, tex_h))
        import io
        buf = io.BytesIO()
        im.save(buf, format="PNG")
        tex_b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    else:
        tex_b64 = base64.b64encode(tex_path.read_bytes()).decode("ascii")
    tex_res = bb.tool("create_texture", {
        "name": f"{model_id}.png",
        "width": tex_w,
        "height": tex_h,
        "data": f"data:image/png;base64,{tex_b64}",
    })
    tex_id = None
    for item in tex_res.get("content", []):
        if item.get("type") == "text":
            try:
                p = json.loads(item["text"])
                tex_id = p.get("uuid") or p.get("id") or p.get("name")
            except Exception:
                pass
    if not tex_id:
        tex_id = f"{model_id}.png"

    # 3. Build bone hierarchy & place sculpted cubes with UV mapping via official MCP tools
    by_uuid = {e["uuid"]: e for e in data["elements"]}

    def walk_bone(b: dict, parent_name: str | None) -> None:
        bname = b["name"]
        bdata: dict = {
            "name": bname,
            "origin": b["origin"],
            "rotation": b["rotation"],
        }
        if parent_name:
            bdata["parent"] = parent_name
        bb.tool("bone_rigging", {"action": "create", "bone_data": bdata})

        cube_batch = []
        for ch in b.get("children", []):
            if isinstance(ch, str) and ch in by_uuid:
                e = by_uuid[ch]
                faces_arg = {}
                for fn, fv in e.get("faces", {}).items():
                    faces_arg[fn] = {"uv": fv["uv"], "rotation": fv.get("rotation", 0)}
                cube_batch.append({
                    "name": e["name"],
                    "from": e["from"],
                    "to": e["to"],
                    "origin": e["origin"],
                    "rotation": e["rotation"],
                    "faces": faces_arg,
                })
        if cube_batch:
            bb.tool("place_cube", {"elements": cube_batch, "group": bname, "texture": tex_id})

        for ch in b.get("children", []):
            if isinstance(ch, dict):
                walk_bone(ch, bname)

    for root_b in data["outliner"]:
        walk_bone(root_b, None)

    # 4. Capture 3D viewport screenshot via offscreen view
    view_res = bb.tool("create_offscreen_view", {"width": 512, "height": 512})
    view_id = None
    for item in view_res.get("content", []):
        if item.get("type") == "text":
            try:
                parsed = json.loads(item["text"])
                view_id = parsed.get("view_id") or parsed.get("id")
            except Exception:
                pass
    if not view_id:
        view_id = view_res.get("structuredContent", {}).get("view_id", "active")

    bb.tool("set_camera_angle", {
        "view": view_id,
        "position": cam_pos,
        "target": cam_target,
        "projection": "perspective",
    })
    shot = bb.tool("capture_screenshot", {"view": view_id, "format": "png", "max_size": 512})
    saved = False
    for item in shot.get("content", []):
        if item.get("type") == "image" and item.get("data"):
            out_png.write_bytes(base64.b64decode(item["data"]))
            saved = True
            break
    if view_id and view_id != "active":
        try:
            bb.tool("delete_offscreen_view", {"view_id": view_id})
        except Exception:
            pass
    print(f"[Blockbench MCP] Built & rendered {model_id} ({len(data['elements'])} cubes) -> {out_png.name} (ok={saved})")


def make_sheets() -> None:
    # 1. Contact sheet of all 36 Cloak artworks
    catalog = json.loads((RES_ROOT / "cosmetics" / "catalog.json").read_text(encoding="utf-8"))
    cloaks = [c for c in catalog if c["slot"] == "cape"]
    cols = 6
    rows = math.ceil(len(cloaks) / cols)
    tw, th = 120, 185
    sheet = Image.new("RGB", (cols * tw, rows * th), (15, 18, 28))
    draw = ImageDraw.Draw(sheet)
    for idx, c in enumerate(cloaks):
        r, col = divmod(idx, cols)
        tex = Image.open(RES_ROOT / "textures" / "cosmetics" / "cape" / f"{c['id']}.png").convert("RGBA")
        front = tex.crop((0, 0, 40, 64)).resize((96, 154), Image.Resampling.NEAREST)
        sheet.paste(front, (col * tw + 12, r * th + 6), front)
        draw.text((col * tw + 8, r * th + 164), c["id"][:16], fill=(220, 232, 255))
    cloak_sheet = SHOT_DIR / "sheet_all_36_cloaks.png"
    sheet.save(cloak_sheet)
    print(f"Saved 36-cloak sheet: {cloak_sheet}")

    # 2. Contact sheet of Blockbench 3D renders
    renders = sorted(SHOT_DIR.glob("bb_*.png"))
    if renders:
        rcols = 4
        rrows = math.ceil(len(renders) / rcols)
        rs = 260
        rsheet = Image.new("RGB", (rcols * rs, rrows * (rs + 24)), (16, 19, 30))
        rdraw = ImageDraw.Draw(rsheet)
        for i, rp in enumerate(renders):
            rr, rc = divmod(i, rcols)
            im = Image.open(rp).convert("RGB").resize((rs - 8, rs - 8), Image.Resampling.LANCZOS)
            rsheet.paste(im, (rc * rs + 4, rr * (rs + 24) + 4))
            rdraw.text((rc * rs + 8, rr * (rs + 24) + rs), rp.stem[3:], fill=(230, 240, 255))
        out_r = SHOT_DIR / "sheet_blockbench_3d_showcase.png"
        rsheet.save(out_r)
        print(f"Saved Blockbench 3D showcase: {out_r}")


def main() -> None:
    SHOT_DIR.mkdir(parents=True, exist_ok=True)
    bb = BlockbenchMCP()
    hero_showcase = [
        ("frost_chain_wings", [22, 26, 46], [0, 20, 4]),
        ("angel_wings", [24, 28, 48], [0, 20, 4]),
        ("dragon", [24, 28, 48], [0, 20, 4]),
        ("glacier_honeycomb_wings", [22, 26, 46], [0, 20, 4]),
        ("fuji_sakura", [14, 20, 32], [0, 15, 2.5]),
        ("samurai_kabuto", [16, 36, -18], [0, 31, 0]),
        ("thunder_cloud_aura", [26, 36, -32], [0, 26, 0]),
        ("pet_bumblebee", [26, 30, -14], [13.5, 24.5, 1.5]),
        ("pet_ender_chibi", [28, 32, -16], [13.5, 24.5, 1.5]),
        ("comp_cyber_tiger", [-2, 14, -14], [-15.5, 6.0, 2.0]),
        ("comp_cerberus", [-2, 14, -14], [-15.5, 6.0, 2.0]),
        ("comp_mech_walker", [-2, 16, -16], [-15.5, 8.0, 2.0]),
    ]
    for model_id, pos, tgt in hero_showcase:
        build_and_render_in_blockbench(bb, model_id, pos, tgt, SHOT_DIR / f"bb_{model_id}.png")
    make_sheets()


if __name__ == "__main__":
    main()
