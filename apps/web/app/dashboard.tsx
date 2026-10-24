"use client";

import { useState } from "react";
import Link from "next/link";
import { RefreshCw } from "lucide-react";
import type { AnalyzeResponse, GameStage, WorkspaceState } from "@skypilot/protocol";
import { Alert, Badge, Button, Card, CardContent, CardHeader, CardTitle, Progress } from "@skypilot/ui";
import { ActionCard } from "@/components/action-card";
import { AnalyzeForm } from "@/components/analyze-form";
import { ErrorView, Freshness, KnownView, Loading, PageHeader, Section, StatCard, StatusBadge } from "@/components/common";
import { fmtCoins, fmtHours, fmtNum } from "@/lib/format";
import { useAnalyze, useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { localizeWarning } from "@/lib/warnings";
import { DashboardHero, QuickAccess } from "@/components/dashboard-hero";

const STAGES: Record<GameStage, [string, string]> = {
  EARLY_GAME: ["Early game", "序盤"],
  MID_GAME: ["Mid game", "中盤"],
  LATE_GAME: ["Late game", "終盤"],
  END_GAME: ["End game", "エンドゲーム"],
};

function Overview({ r, ws }: { r: AnalyzeResponse; ws: WorkspaceState }) {
  const { t, lang } = useI18n();
  const [editing, setEditing] = useState(false);
  const reanalyze = useAnalyze();
  const a = r.analysis;
  const primary = ws.goals.find((g) => g.priority === "PRIMARY");
  const redo = () => {
    if (!ws.username || !primary) return;
    reanalyze.mutate({
      username: ws.username,
      goal: primary.text,
      secondaryGoals: ws.goals.filter((g) => g.priority === "SECONDARY").map((g) => g.text),
      playStyle: ws.playStyle,
      profileId: ws.profileId,
      budget: ws.budget,
    });
  };
  const bank = a.economy.bank.status === "known" ? a.economy.bank.value : null;

  return (
    <div className="flex flex-col gap-8">
      <DashboardHero username={a.player.username} goals={r.goals.length} actions={r.actions.length} />
      <QuickAccess />
      <PageHeader
        title={`${a.player.username} · ${a.cuteName}`}
        description={
          <span className="flex flex-wrap items-center gap-2">
            {a.gameMode ? <Badge>{a.gameMode}</Badge> : null}
            <Badge tone="primary">{t(...STAGES[a.progression.stage])}</Badge>
            <Freshness f={a.fetched} />
          </span>
        }
        actions={
          <>
            <Button variant="outline" onClick={() => setEditing((v) => !v)}>
              {editing ? t("Close", "閉じる") : t("Change goal / profile", "目標・プロフィールを変更")}
            </Button>
            <Button onClick={redo} disabled={reanalyze.isPending || !primary}>
              <RefreshCw className={reanalyze.isPending ? "animate-spin" : undefined} />
              {t("Refresh from Hypixel", "Hypixelから再取得")}
            </Button>
          </>
        }
      />
      {reanalyze.error ? <ErrorView error={reanalyze.error} /> : null}
      {editing ? <AnalyzeForm ws={ws} compact onDone={() => setEditing(false)} /> : null}

      {r.warnings.length > 0 ? (
        <Alert tone="warn">
          <ul className="list-disc pl-5">
            {r.warnings.map((w, i) => (
              <li key={i}>{localizeWarning(w, lang)}</li>
            ))}
          </ul>
        </Alert>
      ) : null}
      {r.parse.unmatched.length > 0 ? (
        <Alert tone="info">
          {t("Could not understand", "解釈できなかった部分")}: {r.parse.unmatched.map((u) => `“${u}”`).join(", ")}
          {r.parse.suggestions.length > 0 ? ` — ${t("did you mean", "もしかして")}: ${r.parse.suggestions.map((s) => s.title).join(", ")}?` : ""}
        </Alert>
      ) : null}

      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <StatCard label={t("SkyBlock level", "SkyBlockレベル")} value={<KnownView v={a.skyblockLevel} render={(v) => fmtNum(v, 1)} />} />
        <StatCard label={t("Skill average", "スキル平均")} value={<KnownView v={a.skillAverage} render={(v) => fmtNum(v, 2)} />} />
        <StatCard label={t("Catacombs", "カタコンベ")} value={<KnownView v={a.dungeons} render={(d) => fmtNum(d.catacombs.levelWithProgress, 2)} />} />
        <StatCard label={t("Magical Power", "魔力 (MP)")} value={<KnownView v={a.accessories} render={(x) => fmtNum(x.magicalPower)} />} />
        <StatCard label={t("Networth", "資産")} value={<KnownView v={a.networth} render={(n) => fmtCoins(n.total)} />} hint={t("Market-priced estimate", "市場価格による推定")} />
        <StatCard label={t("Purse + bank", "財布 + 銀行")} value={fmtCoins(a.economy.purse + (bank ?? 0))} hint={bank === null ? t("Bank API disabled", "銀行APIが無効") : undefined} />
      </div>

      <Section title={t("WHAT SHOULD I DO NOW?", "今やるべきこと")} description={t("Ranked by goal impact, cost, time, risk and your play style.", "目標への影響・コスト・時間・リスク・プレイスタイルで順位付けしています。")}>
        {r.actions.length === 0 ? (
          <Alert tone="good">{t("Your goals are already satisfied - set a new goal.", "目標はすでに達成済みです。新しい目標を設定してください。")}</Alert>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {r.actions.map((x, i) => (
              <ActionCard key={x.id} a={x} rank={i + 1} />
            ))}
          </div>
        )}
      </Section>

      <div className="grid gap-6 lg:grid-cols-[3fr_2fr]">
        <Section title={t("Goals", "目標")} actions={<Link href="/roadmap" className="text-xs text-primary hover:underline">{t("Open roadmap →", "ロードマップを開く →")}</Link>}>
          <div className="flex flex-col gap-3">
            {r.goals.map((g) => {
              const cur = g.roadmap.currentStep !== null ? g.roadmap.steps.find((s) => s.index === g.roadmap.currentStep) : null;
              return (
                <Card key={g.id}>
                  <CardHeader className="pb-2">
                    <div className="flex items-center justify-between gap-2">
                      <CardTitle>{g.roadmap.title}</CardTitle>
                      <Badge tone={g.priority === "PRIMARY" ? "primary" : "neutral"}>{g.priority === "PRIMARY" ? t("Primary", "メイン") : t("Secondary", "サブ")}</Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-2">
                    <div className="flex items-center gap-3">
                      <Progress value={g.roadmap.overallProgress} />
                      <span className="text-xs tabular-nums">{Math.round(g.roadmap.overallProgress * 100)}%</span>
                    </div>
                    {cur ? (
                      <div className="flex flex-wrap items-center gap-2 text-sm">
                        <span className="text-muted-foreground">{t("Current step", "現在のステップ")}:</span>
                        <span>{cur.title}</span>
                        <StatusBadge s={cur.status} />
                      </div>
                    ) : null}
                    <div className="flex gap-4 text-xs text-muted-foreground">
                      <span>
                        {t("Remaining cost", "残りコスト")}: <span className="text-foreground">{g.roadmap.remainingCost !== null ? fmtCoins(g.roadmap.remainingCost) : "—"}</span>
                      </span>
                      <span>
                        {t("Remaining time", "残り時間")}: <span className="text-foreground">{fmtHours(g.roadmap.remainingHours, lang)}</span>
                      </span>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </Section>

        <Section title={t("Progression", "進行度")} description={t("Not networth-only: each area is scored from your actual progress.", "資産だけでなく、各分野の実際の進行度からスコア化しています。")}>
          <Card>
            <CardContent className="flex flex-col gap-2.5 p-5">
              <div className="mb-1 flex items-baseline justify-between">
                <span className="text-sm text-muted-foreground">{t("Overall", "総合")}</span>
                <span className="text-xl font-semibold tabular-nums">{fmtNum(a.progression.overall)}</span>
              </div>
              {a.progression.areas.map((ar) => (
                <div key={ar.id} className="grid grid-cols-[7rem_1fr_2.5rem] items-center gap-2 text-xs" title={ar.basis.join("\n")}>
                  <span className="truncate">{ar.name}</span>
                  {ar.known ? <Progress value={ar.score / 100} tone={a.progression.weakAreas.includes(ar.id) ? "warn" : a.progression.strongAreas.includes(ar.id) ? "good" : "primary"} /> : <span className="text-muted-foreground italic">{t("Unknown", "不明")}</span>}
                  <span className="text-right tabular-nums">{ar.known ? fmtNum(ar.score) : "—"}</span>
                </div>
              ))}
            </CardContent>
          </Card>
        </Section>
      </div>
    </div>
  );
}

export function Dashboard() {
  const ws = useWorkspace();
  if (ws.isLoading) return <Loading rows={4} />;
  if (ws.error) return <ErrorView error={ws.error} retry={() => ws.refetch()} />;
  const data = ws.data!;
  if (!data.lastAnalysis) {
    return (
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <DashboardHero />
        <QuickAccess />
        <div id="profile-analysis" className="scroll-mt-24"><AnalyzeForm ws={data} /></div>
      </div>
    );
  }
  return <Overview r={data.lastAnalysis} ws={data} />;
}
