"use client";

import { useMemo, useState } from "react";
import type { GoalGraph, GoalNode, NodeStatus } from "@skypilot/protocol";
import { Badge, Card, CardContent, Progress } from "@skypilot/ui";
import { fmtCoins, fmtHours, fmtNum } from "@/lib/format";
import { useI18n } from "@/lib/i18n";
import { SourceBadge, StatusBadge } from "./common";

const W = 210;
const H = 58;
const GX = 70;
const GY = 18;

const STATUS_COLOR: Record<NodeStatus, string> = {
  SATISFIED: "var(--color-emerald-500)",
  IN_PROGRESS: "var(--color-sky-500)",
  NOT_STARTED: "var(--muted-foreground)",
  BLOCKED: "var(--color-red-500)",
  UNKNOWN: "var(--color-amber-500)",
};

interface Placed {
  node: GoalNode;
  x: number;
  y: number;
}

/** Layered left-to-right layout: column = shortest depth from the goal root. */
function layout(g: GoalGraph): { placed: Placed[]; edges: [Placed, Placed][]; width: number; height: number } {
  const byId = new Map(g.nodes.map((n) => [n.id, n]));
  const depth = new Map<string, number>();
  const queue: string[] = [g.root];
  depth.set(g.root, 0);
  while (queue.length) {
    const id = queue.shift()!;
    for (const c of byId.get(id)?.children ?? []) {
      if (byId.has(c) && !depth.has(c)) {
        depth.set(c, depth.get(id)! + 1);
        queue.push(c);
      }
    }
  }
  const cols: GoalNode[][] = [];
  for (const n of g.nodes) {
    const d = depth.get(n.id);
    if (d === undefined) continue;
    (cols[d] ??= []).push(n);
  }
  const placedById = new Map<string, Placed>();
  cols.forEach((col, ci) =>
    col.forEach((n, ri) => {
      placedById.set(n.id, { node: n, x: ci * (W + GX), y: ri * (H + GY) });
    }),
  );
  const edges: [Placed, Placed][] = [];
  for (const p of placedById.values()) {
    for (const c of p.node.children) {
      const q = placedById.get(c);
      if (q) edges.push([p, q]);
    }
  }
  const rows = Math.max(1, ...cols.map((c) => c.length));
  return { placed: [...placedById.values()], edges, width: cols.length * (W + GX) - GX, height: rows * (H + GY) - GY };
}

export function GoalGraphView({ graph }: { graph: GoalGraph }) {
  const { t, lang } = useI18n();
  const { placed, edges, width, height } = useMemo(() => layout(graph), [graph]);
  const [selected, setSelected] = useState<string | null>(null);
  const sel = placed.find((p) => p.node.id === selected)?.node ?? null;

  return (
    <div className="flex flex-col gap-3">
      <div className="overflow-auto rounded-lg border border-border bg-muted/20 p-4">
        <svg width={Math.max(width, 200)} height={Math.max(height, H)} role="img" aria-label={t("Goal requirement graph", "目標の必要条件グラフ")}>
          {edges.map(([a, b], i) => {
            const x1 = a.x + W;
            const y1 = a.y + H / 2;
            const x2 = b.x;
            const y2 = b.y + H / 2;
            const mx = (x1 + x2) / 2;
            return <path key={i} d={`M${x1},${y1} C${mx},${y1} ${mx},${y2} ${x2},${y2}`} fill="none" stroke="var(--border)" strokeWidth={1.5} strokeDasharray={b.node.hardRequirement ? undefined : "4 4"} />;
          })}
          {placed.map((p) => (
            <g key={p.node.id} transform={`translate(${p.x},${p.y})`} onClick={() => setSelected(p.node.id)} className="cursor-pointer">
              <rect width={W} height={H} rx={8} fill="var(--card)" stroke={selected === p.node.id ? "var(--ring)" : "var(--border)"} strokeWidth={selected === p.node.id ? 2 : 1} />
              <rect x={0} y={0} width={4} height={H} rx={2} fill={STATUS_COLOR[p.node.status]} />
              <text x={14} y={22} fontSize={12} fontWeight={600} fill="var(--foreground)">
                {p.node.title.length > 28 ? p.node.title.slice(0, 27) + "…" : p.node.title}
              </text>
              <text x={14} y={40} fontSize={11} fill="var(--muted-foreground)">
                {p.node.current !== null && p.node.target !== null
                  ? `${fmtNum(p.node.current, 1)} / ${fmtNum(p.node.target, 1)}${p.node.unit ? " " + p.node.unit : ""}`
                  : p.node.estimatedCost !== null
                    ? fmtCoins(p.node.estimatedCost)
                    : p.node.kind}
              </text>
              <rect x={14} y={47} width={W - 28} height={3} rx={1.5} fill="var(--muted)" />
              <rect x={14} y={47} width={(W - 28) * Math.max(0, Math.min(1, p.node.progress))} height={3} rx={1.5} fill={STATUS_COLOR[p.node.status]} />
            </g>
          ))}
        </svg>
      </div>
      <div className="flex flex-wrap gap-3 text-[11px] text-muted-foreground">
        <span>— {t("hard requirement (game rule)", "必須条件（ゲーム仕様）")}</span>
        <span>- - {t("SkyPilot recommendation", "SkyPilotの推奨")}</span>
        {graph.satisfiedPruned.length > 0 ? <span>{t(`${graph.satisfiedPruned.length} already-satisfied requirements hidden`, `達成済みの条件 ${graph.satisfiedPruned.length} 件を非表示`)}</span> : null}
      </div>
      {sel ? (
        <Card>
          <CardContent className="flex flex-col gap-2 p-4 text-sm">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold">{sel.title}</span>
              <StatusBadge s={sel.status} />
              <Badge tone={sel.hardRequirement ? "bad" : "info"}>{sel.hardRequirement ? t("Required", "必須") : t("Recommended", "推奨")}</Badge>
              <SourceBadge s={sel.source} />
            </div>
            {sel.detail ? <p className="text-muted-foreground">{sel.detail}</p> : null}
            <Progress value={sel.progress} />
            <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
              {sel.current !== null ? (
                <span>
                  {t("Current", "現在")}: {fmtNum(sel.current, 2)} {sel.unit}
                </span>
              ) : null}
              {sel.target !== null ? (
                <span>
                  {t("Target", "目標")}: {fmtNum(sel.target, 2)} {sel.unit}
                </span>
              ) : null}
              <span>
                {t("Cost", "コスト")}: {sel.estimatedCost !== null ? fmtCoins(sel.estimatedCost) : "—"}
              </span>
              <span>
                {t("Time", "時間")}: {fmtHours(sel.estimatedHours, lang)}
              </span>
            </div>
          </CardContent>
        </Card>
      ) : (
        <p className="text-xs text-muted-foreground">{t("Click a node for details.", "ノードをクリックすると詳細を表示します。")}</p>
      )}
    </div>
  );
}
