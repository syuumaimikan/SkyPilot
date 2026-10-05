"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { FarmDesign, FarmDesignSummary } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, NativeSelect, Label, Table, TBody, TD, TH, THead, TR, Tabs, TabsContent, TabsList, TabsTrigger, cn } from "@skypilot/ui";
import { ErrorView, Loading, PageHeader, StatCard } from "@/components/common";
import { Legend, SideView, ThreeView, TopView } from "@/components/farm-views";
import { fmtCoins, fmtHours, fmtNum } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

function useChecklist(id: string) {
  const key = `sp_farm_steps_${id}`;
  // Design is keyed by id and only renders client-side after the design has loaded.
  const [done, setDone] = useState<number[]>(() => {
    try {
      return typeof window === "undefined" ? [] : (JSON.parse(localStorage.getItem(key) ?? "[]") as number[]);
    } catch {
      return [];
    }
  });
  const toggle = (i: number) => {
    const next = done.includes(i) ? done.filter((x) => x !== i) : [...done, i];
    setDone(next);
    try {
      localStorage.setItem(key, JSON.stringify(next));
    } catch {
      // storage unavailable (private mode): progress is kept for this visit only
    }
  };
  return { done, toggle };
}

function Design({ d }: { d: FarmDesign }) {
  const { t, lang } = useI18n();
  const [layer, setLayer] = useState(d.layers[d.layers.length - 1] ?? 0);
  const { done, toggle } = useChecklist(d.summary.id);
  const blockTypes = Object.keys(d.blockCounts);
  return (
    <div className="flex flex-col gap-5">
      <p className="text-sm text-muted-foreground">{d.summary.description}</p>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label={t("Size (X × Y × Z)", "サイズ (X × Y × Z)")} value={d.summary.size.join(" × ")} hint={`${t("plot", "プロット")} ${d.plotBoundary.join(" × ")}`} />
        <StatCard label={t("Blocks", "ブロック数")} value={fmtNum(Object.values(d.blockCounts).reduce<number>((a, b) => a + (b ?? 0), 0))} />
        <StatCard label={t("Build time (by hand)", "建築時間（手作業）")} value={fmtHours(d.estimatedBuildMinutes / 60, lang)} />
        <StatCard
          label={t("Material cost", "材料費")}
          value={d.estimatedMaterialCost !== null ? fmtCoins(d.estimatedMaterialCost) : t("Not available", "取得不可")}
          hint={d.estimatedMaterialCost === null ? t("Some blocks have no market price", "市場価格のないブロックがあります") : undefined}
        />
      </div>
      <Tabs defaultValue="top">
        <TabsList>
          <TabsTrigger value="top">{t("Top view", "上面図")}</TabsTrigger>
          <TabsTrigger value="side">{t("Side view", "側面図")}</TabsTrigger>
          <TabsTrigger value="3d">3D</TabsTrigger>
        </TabsList>
        <TabsContent value="top" className="flex flex-col gap-3">
          <div className="flex items-center gap-2">
            <Label htmlFor="layer">{t("Layer", "層")}</Label>
            <NativeSelect id="layer" className="w-28" value={layer} onChange={(e) => setLayer(Number(e.target.value))}>
              {d.layers.map((y) => (
                <option key={y} value={y}>
                  Y+{y}
                </option>
              ))}
            </NativeSelect>
          </div>
          <TopView blocks={d.blocks} y={layer} size={[d.summary.size[0], d.summary.size[2]]} />
        </TabsContent>
        <TabsContent value="side" className="flex flex-col gap-2">
          <SideView blocks={d.blocks} />
          <p className="text-xs text-muted-foreground">{t("Cross-section at X = 0; it repeats along the whole length.", "X = 0 の断面図です。全長にわたって同じ構造が続きます。")}</p>
        </TabsContent>
        <TabsContent value="3d">
          <ThreeView blocks={d.blocks} />
        </TabsContent>
      </Tabs>
      <Legend blocks={blockTypes} />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("Build guide", "建築ガイド")}</CardTitle>
            <CardDescription>{t("Tick steps as you go; progress is saved in this browser.", "進めたステップにチェックを付けると、このブラウザに保存されます。")}</CardDescription>
          </CardHeader>
          <CardContent>
            <ol className="flex flex-col gap-2">
              {d.buildSteps.map((s) => (
                <li key={s.index}>
                  <label className={cn("flex cursor-pointer gap-3 rounded-md border border-border p-3 text-sm", done.includes(s.index) && "opacity-60")}>
                    <input type="checkbox" className="mt-0.5 size-4 accent-[var(--primary)]" checked={done.includes(s.index)} onChange={() => toggle(s.index)} />
                    <span>
                      <span className={cn("font-medium", done.includes(s.index) && "line-through")}>
                        {s.index}. {s.title}
                      </span>
                      {s.detail !== s.title ? <span className="block text-xs text-muted-foreground">{s.detail}</span> : null}
                    </span>
                  </label>
                </li>
              ))}
            </ol>
          </CardContent>
        </Card>
        <div className="flex flex-col gap-5">
          <Card>
            <CardHeader>
              <CardTitle>{t("Materials", "材料")}</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <Table>
                <THead>
                  <TR>
                    <TH>{t("Block", "ブロック")}</TH>
                    <TH className="text-right">{t("Count", "個数")}</TH>
                    <TH className="text-right">{t("Stacks", "スタック")}</TH>
                  </TR>
                </THead>
                <TBody>
                  {Object.entries(d.blockCounts).map(([b, n]) => (
                    <TR key={b}>
                      <TD>{b}</TD>
                      <TD className="text-right">{fmtNum(n ?? 0)}</TD>
                      <TD className="text-right">{fmtNum((n ?? 0) / 64, 1)}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>{t("How to farm it", "収穫方法")}</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-2 text-sm">
              <div>
                <span className="text-muted-foreground">{t("Harvest direction", "収穫方向")}: </span>
                {d.harvestDirection}
              </div>
              <ol className="list-decimal pl-5">
                {d.movement.map((m, i) => (
                  <li key={i}>{m}</li>
                ))}
              </ol>
              {[...d.waterNotes, ...d.lightingNotes].map((n, i) => (
                <p key={i} className="text-xs text-muted-foreground">
                  {n}
                </p>
              ))}
              <p className="text-xs text-muted-foreground">{t("You walk and harvest yourself - SkyPilot does not automate movement or farming.", "移動と収穫はご自身で行ってください。SkyPilot は移動や農業を自動化しません。")}</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

export function BuilderView() {
  const { t } = useI18n();
  const router = useRouter();
  const list = useApi<FarmDesignSummary[]>("/api/farms");
  const selected = useSearchParams().get("design") ?? list.data?.[0]?.id ?? null;
  const design = useApi<FarmDesign>(selected ? `/api/farms/${encodeURIComponent(selected)}` : null);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Farm Builder", "ファームビルダー")} description={t("Reference layouts for Garden plots with top, side and 3D views, a material list and a step-by-step build guide.", "ガーデンのプロット向けのレイアウトを上面・側面・3Dで表示し、材料リストと手順付きの建築ガイドを提供します。")} />
      {list.isLoading ? (
        <Loading />
      ) : list.error ? (
        <ErrorView error={list.error} retry={() => list.refetch()} />
      ) : (
        <div className="flex flex-wrap gap-2">
          {(list.data ?? []).map((f) => (
            <button
              key={f.id}
              onClick={() => router.replace(`/farming/builder?design=${f.id}`)}
              className={cn("cursor-pointer rounded-lg border px-3 py-2 text-left text-sm transition-colors", f.id === selected ? "border-primary bg-primary/5" : "border-border hover:bg-accent")}
            >
              <div className="font-medium">{f.name}</div>
              <div className="mt-1 flex gap-1">
                <Badge>{f.size.join("×")}</Badge>
              </div>
            </button>
          ))}
        </div>
      )}
      {design.isLoading ? <Loading rows={4} /> : design.error ? <ErrorView error={design.error} /> : design.data ? <Design key={design.data.summary.id} d={design.data} /> : null}
    </div>
  );
}
