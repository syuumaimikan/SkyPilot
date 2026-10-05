"use client";

import { useState } from "react";
import { Plus, X } from "lucide-react";
import type { GoalPriority, GoalResult } from "@skypilot/protocol";
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, NativeSelect, Progress, Tabs, TabsContent, TabsList, TabsTrigger, cn } from "@skypilot/ui";
import { ErrorView, Loading, NeedsAnalysis, PageHeader, Section, SourceBadge, StatusBadge } from "@/components/common";
import { GoalGraphView } from "@/components/goal-graph";
import { fmtCoins, fmtHours, fmtNum } from "@/lib/format";
import { useSetGoals, useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

function GoalEditor({ initial }: { initial: { text: string; priority: GoalPriority }[] }) {
  const { t } = useI18n();
  const [goals, setGoals] = useState(initial);
  const save = useSetGoals();
  const valid = goals.length > 0 && goals.every((g) => g.text.trim() && g.text.length <= 300);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Goals (up to 6)", "目標（最大6件）")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        {goals.map((g, i) => (
          <div key={i} className="flex items-center gap-2">
            <NativeSelect
              className="w-32 shrink-0"
              value={g.priority}
              onChange={(e) => setGoals(goals.map((x, j) => (j === i ? { ...x, priority: e.target.value as GoalPriority } : x)))}
              aria-label={t("Priority", "優先度")}
            >
              <option value="PRIMARY">{t("Primary", "メイン")}</option>
              <option value="SECONDARY">{t("Secondary", "サブ")}</option>
            </NativeSelect>
            <Input value={g.text} maxLength={300} onChange={(e) => setGoals(goals.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))} aria-label={t("Goal", "目標")} />
            <Button variant="ghost" size="icon" onClick={() => setGoals(goals.filter((_, j) => j !== i))} aria-label={t("Remove goal", "目標を削除")} disabled={goals.length === 1}>
              <X />
            </Button>
          </div>
        ))}
        <div className="flex flex-wrap items-center gap-2 pt-1">
          {goals.length < 6 ? (
            <Button variant="ghost" size="sm" onClick={() => setGoals([...goals, { text: "", priority: "SECONDARY" }])}>
              <Plus /> {t("Add goal", "目標を追加")}
            </Button>
          ) : null}
          <Button size="sm" className="ml-auto" disabled={!valid || save.isPending} onClick={() => save.mutate(goals.map((g) => ({ text: g.text.trim(), priority: g.priority })))}>
            {save.isPending ? t("Re-planning…", "再計画中…") : t("Save & re-plan", "保存して再計画")}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">{t("Re-planning uses your last fetched profile; it does not call Hypixel again.", "再計画は最後に取得したプロフィールを使い、Hypixel への再取得は行いません。")}</p>
        {save.error ? <ErrorView error={save.error} /> : null}
      </CardContent>
    </Card>
  );
}

function RoadmapSteps({ g }: { g: GoalResult }) {
  const { t, lang } = useI18n();
  return (
    <ol className="flex flex-col gap-2">
      {g.roadmap.steps.map((s) => (
        <li key={s.nodeId} className={cn("rounded-lg border border-border p-3", g.roadmap.currentStep === s.index && "border-primary/60 bg-primary/5")}>
          <div className="flex flex-wrap items-center gap-2">
            <span className="flex size-5 items-center justify-center rounded bg-muted text-[11px] font-semibold tabular-nums">{s.index}</span>
            <span className="text-sm font-medium">{s.title}</span>
            <StatusBadge s={s.status} />
            <Badge tone={s.hardRequirement ? "bad" : "info"}>{s.hardRequirement ? t("Required", "必須") : t("Recommended", "推奨")}</Badge>
            <SourceBadge s={s.source} />
            {g.roadmap.currentStep === s.index ? <Badge tone="primary">{t("You are here", "現在地")}</Badge> : null}
          </div>
          {s.detail ? <p className="mt-1.5 text-xs text-muted-foreground">{s.detail}</p> : null}
          <div className="mt-2 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
            <div className="flex w-48 items-center gap-2">
              <Progress value={s.progress} />
              <span className="tabular-nums">{Math.round(s.progress * 100)}%</span>
            </div>
            {s.current !== null && s.target !== null ? (
              <span className="tabular-nums">
                {fmtNum(s.current, 2)} / {fmtNum(s.target, 2)} {s.unit}
              </span>
            ) : null}
            <span>
              {t("Cost", "コスト")}: {s.estimatedCost !== null ? fmtCoins(s.estimatedCost) : "—"}
            </span>
            <span>
              {t("Time", "時間")}: {fmtHours(s.estimatedHours, lang)}
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}

export function RoadmapView() {
  const { t, lang } = useI18n();
  const ws = useWorkspace();
  if (ws.isLoading) return <Loading rows={4} />;
  if (ws.error) return <ErrorView error={ws.error} retry={() => ws.refetch()} />;
  const r = ws.data?.lastAnalysis;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Roadmap", "ロードマップ")} description={t("Every requirement between you and your goals, ordered so hard requirements come first.", "目標までのすべての条件を、必須条件を優先して並べています。")} />
      {ws.data && ws.data.goals.length > 0 ? <GoalEditor key={ws.data.goals.map((g) => g.id + g.priority).join(",")} initial={ws.data.goals.map((g) => ({ text: g.text, priority: g.priority }))} /> : null}
      {!r ? (
        <NeedsAnalysis />
      ) : (
        r.goals.map((g) => (
          <Section
            key={g.id}
            title={g.roadmap.title}
            description={`${Math.round(g.roadmap.overallProgress * 100)}% · ${t("remaining", "残り")} ${g.roadmap.remainingCost !== null ? fmtCoins(g.roadmap.remainingCost) : "—"} · ${fmtHours(g.roadmap.remainingHours, lang)}`}
          >
            <Tabs defaultValue="steps">
              <TabsList>
                <TabsTrigger value="steps">{t("Steps", "ステップ")}</TabsTrigger>
                <TabsTrigger value="graph">{t("Goal graph", "目標グラフ")}</TabsTrigger>
              </TabsList>
              <TabsContent value="steps">
                <RoadmapSteps g={g} />
              </TabsContent>
              <TabsContent value="graph">
                <GoalGraphView graph={g.graph} />
              </TabsContent>
            </Tabs>
          </Section>
        ))
      )}
    </div>
  );
}
