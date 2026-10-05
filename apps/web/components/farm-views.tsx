"use client";

import { useEffect, useMemo, useRef } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import type { FarmBlock } from "@skypilot/protocol";

const COLORS: Record<string, string> = {
  water: "#3b82f6",
  farmland: "#7c4a26",
  dirt: "#8b5a2b",
  sand: "#e3d18c",
  soul_sand: "#5b4636",
  mycelium: "#7b6a86",
  stone_bricks: "#8d8d8d",
  glass: "#bfe3f2",
  jungle_log: "#6b5a2f",
  wheat: "#e3c14f",
  carrots: "#f08a24",
  nether_wart: "#a1233a",
  sugar_cane: "#8fd16a",
  cactus: "#2f8f3a",
  cocoa: "#a0592b",
  red_mushroom: "#d23b3b",
  melon: "#6bbf3a",
  melon_stem: "#9ccc65",
};

function maxOf(list: FarmBlock[], f: (b: FarmBlock) => number, init: number): number {
  let m = init;
  for (const b of list) m = Math.max(m, f(b));
  return m;
}

function minOf(list: FarmBlock[], f: (b: FarmBlock) => number, init: number): number {
  let m = init;
  for (const b of list) m = Math.min(m, f(b));
  return m;
}

export function blockColor(b: string): string {
  return COLORS[b] ?? "#a855f7";
}

export function Legend({ blocks }: { blocks: string[] }) {
  return (
    <div className="flex flex-wrap gap-3 text-xs">
      {blocks.map((b) => (
        <span key={b} className="flex items-center gap-1.5">
          <span className="size-3 rounded-sm border border-border" style={{ background: blockColor(b) }} />
          {b}
        </span>
      ))}
    </div>
  );
}

/** Top view of one Y layer (X right, Z down). */
export function TopView({ blocks, y, size }: { blocks: FarmBlock[]; y: number; size: [number, number] }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const cell = Math.max(3, Math.floor(576 / Math.max(size[0], size[1])));
  useEffect(() => {
    const c = ref.current;
    const ctx = c?.getContext("2d");
    if (!c || !ctx) return;
    ctx.clearRect(0, 0, c.width, c.height);
    ctx.fillStyle = "rgba(127,127,127,0.08)";
    ctx.fillRect(0, 0, c.width, c.height);
    for (const b of blocks) {
      if (b.y !== y) continue;
      ctx.fillStyle = blockColor(b.block);
      ctx.fillRect(b.x * cell, b.z * cell, cell - (cell > 4 ? 1 : 0), cell - (cell > 4 ? 1 : 0));
    }
  }, [blocks, y, cell]);
  return <canvas ref={ref} width={size[0] * cell} height={size[1] * cell} className="max-w-full rounded-md border border-border" style={{ imageRendering: "pixelated" }} aria-label={`Top view of layer Y+${y}`} />;
}

/** Side view: the Z-Y cross-section at x = 0 (repeats along X). */
export function SideView({ blocks }: { blocks: FarmBlock[] }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const slice = useMemo(() => blocks.filter((b) => b.x === 0), [blocks]);
  const maxZ = maxOf(slice, (b) => b.z, 0);
  const minY = minOf(slice, (b) => b.y, 0);
  const maxY = maxOf(slice, (b) => b.y, 0);
  const cols = Math.min(maxZ + 1, 24);
  const rows = maxY - minY + 1;
  const cell = 22;
  useEffect(() => {
    const c = ref.current;
    const ctx = c?.getContext("2d");
    if (!c || !ctx) return;
    ctx.clearRect(0, 0, c.width, c.height);
    for (const b of slice) {
      if (b.z >= cols) continue;
      ctx.fillStyle = blockColor(b.block);
      ctx.fillRect(b.z * cell, (maxY - b.y) * cell, cell - 1, cell - 1);
    }
  }, [slice, cols, maxY]);
  return <canvas ref={ref} width={cols * cell} height={rows * cell} className="max-w-full rounded-md border border-border" aria-label="Side view cross-section" />;
}

/** Interactive 3D view: one InstancedMesh per block type, orbit controls. */
export function ThreeView({ blocks }: { blocks: FarmBlock[] }) {
  const host = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = host.current;
    if (!el) return;
    const width = el.clientWidth;
    const height = 460;
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(width, height);
    el.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    scene.add(new THREE.AmbientLight(0xffffff, 0.75));
    const sun = new THREE.DirectionalLight(0xffffff, 0.9);
    sun.position.set(60, 120, 40);
    scene.add(sun);

    const maxX = maxOf(blocks, (b) => b.x, 1);
    const maxZ = maxOf(blocks, (b) => b.z, 1);
    const center = new THREE.Vector3(maxX / 2, 0, maxZ / 2);
    const camera = new THREE.PerspectiveCamera(45, width / height, 0.5, 2000);
    camera.position.set(center.x + maxX * 0.6, Math.max(maxX, maxZ) * 0.7, center.z + maxZ * 0.9);
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.target.copy(center);
    controls.enableDamping = true;
    controls.update();

    const geo = new THREE.BoxGeometry(1, 1, 1);
    const byType = new Map<string, FarmBlock[]>();
    for (const b of blocks) {
      const arr = byType.get(b.block) ?? [];
      arr.push(b);
      byType.set(b.block, arr);
    }
    const materials: THREE.Material[] = [];
    const m = new THREE.Matrix4();
    for (const [type, list] of byType) {
      const transparent = type === "water" || type === "glass";
      const mat = new THREE.MeshLambertMaterial({ color: blockColor(type), transparent, opacity: transparent ? 0.45 : 1 });
      materials.push(mat);
      const mesh = new THREE.InstancedMesh(geo, mat, list.length);
      list.forEach((b, i) => {
        m.makeTranslation(b.x + 0.5, b.y + 0.5, b.z + 0.5);
        mesh.setMatrixAt(i, m);
      });
      scene.add(mesh);
    }
    const grid = new THREE.GridHelper(Math.max(maxX, maxZ) + 2, Math.max(maxX, maxZ) + 2, 0x888888, 0x444444);
    grid.position.set(center.x, -0.01, center.z);
    scene.add(grid);

    let raf = 0;
    const loop = () => {
      controls.update();
      renderer.render(scene, camera);
      raf = requestAnimationFrame(loop);
    };
    loop();
    const onResize = () => {
      const w = el.clientWidth;
      renderer.setSize(w, height);
      camera.aspect = w / height;
      camera.updateProjectionMatrix();
    };
    window.addEventListener("resize", onResize);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", onResize);
      controls.dispose();
      geo.dispose();
      materials.forEach((x) => x.dispose());
      renderer.dispose();
      el.removeChild(renderer.domElement);
    };
  }, [blocks]);
  return <div ref={host} className="w-full overflow-hidden rounded-md border border-border" role="img" aria-label="3D farm preview (drag to rotate, scroll to zoom)" />;
}
