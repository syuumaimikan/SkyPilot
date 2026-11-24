"use client";

import type { CompletionPart, CompletionReport } from "@skypilot/protocol";
import { Badge, Card, CardContent, Progress } from "@skypilot/ui";
import { ErrorView, Loading } from "@/components/common";
import { fmtDate } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

const NAMES: Record<string, [string, string]> = {
  skills: ["Skills", "スキル"],
  catacombs: ["Catacombs", "カタコンベ"],
  classes: ["Dungeon classes", "ダンジョンクラス"],
  slayers: ["Slayers", "スレイヤー"],
  hotm: ["Heart of the Mountain", "HOTM"],
  garden: ["Garden", "ガーデン"],
  collections: ["Collections", "コレクション"],
  minions: ["Minion tiers", "ミニオン（全Tier）"],
  museum: ["Museum", "博物館"],
  fairy_souls: ["Fairy souls", "フェアリーソウル"],
};

const SOURCE: Record<string, [string, string]> = {
  HYPIXEL_PROFILE: ["Hypixel profile", "Hypixel プロフィール"],
  HYPIXEL_RESOURCES: ["Hypixel resources", "Hypixel リソース"],
  NEU_REPO: ["NEU-REPO", "NEU-REPO"],
};

function tone(p: number): "good" | "primary" | "warn" {
  return p >= 90 ? "good" : p >= 50 ? "primary" : "warn";
}

/** Profile completion in %: each part against a maximum from Hypixel / NEU-REPO data. */
export function CompletionCard({ compact = false }: { compact?: boolean }) {
  const { t, lang } = useI18n();
  const q = useApi<CompletionReport>("/api/me/completion");
  const name = (id: string, fallback: string) => (NAMES[id] ? t(...NAMES[id]) : fallback);
  if (q.isLoading) return <Loading rows={3} />;
  if (q.error) return <ErrorView error={q.error} retry={() => q.refetch()} />;
  const r = q.data;
  if (!r || r.overall === null) {
    return <p className="text-sm text-muted-foreground">{t("Analyze your profile to see how complete it is.", "プロフィールを分析すると達成率が表示されます。")}</p>;
  }
  const parts = [...r.parts].sort((a, b) => a.percent - b.percent);
  const next: CompletionPart | undefined = parts.find((p) => p.percent < 100);
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <span className="text-sm text-muted-foreground">{t("Profile completion", "プロフィール達成率")}</span>
          <span className="text-3xl font-semibold tabular-nums">{r.overall.toFixed(1)}%</span>
        </div>
        <Progress value={r.overall / 100} tone={tone(r.overall)} />
        {next ? (
          <p className="text-xs text-muted-foreground">
            {t("Lowest: ", "いちばん低い分野: ")}
            <span className="font-medium text-foreground">{name(next.id, next.name)}</span> {next.percent.toFixed(1)}%
          </p>
        ) : null}
        <div className="flex flex-col gap-2">
          {(compact ? parts.slice(0, 5) : parts).map((p) => (
            <div key={p.id} className="grid grid-cols-[8.5rem_1fr_3.5rem] items-center gap-2 text-xs" title={`${p.basis} · ${SOURCE[p.source] ? t(...SOURCE[p.source]) : p.source}`}>
              <span className="truncate">{name(p.id, p.name)}</span>
              <Progress value={p.percent / 100} tone={tone(p.percent)} />
              <span className="text-right tabular-nums">{p.percent.toFixed(1)}%</span>
            </div>
          ))}
        </div>
        {!compact && r.unknown.length > 0 ? (
          <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
            {r.unknown.map((u) => (
              <Badge key={u.id} tone="neutral" title={u.reason}>
                {name(u.id, u.name)}: {t("Not available from API", "API から取得できません")}
              </Badge>
            ))}
          </div>
        ) : null}
        <p className="text-[11px] text-muted-foreground">
          {t(
            "Each part counts equally. Maximums come from Hypixel's resources and NEU-REPO constants; parts without a known maximum are not counted.",
            "各分野を同じ重みで平均しています。上限は Hypixel のリソースと NEU-REPO の定数から取得し、上限が分からない分野は計算に含めません。",
          )}
          {r.analyzedAtMs ? ` · ${t("Analysis", "分析")}: ${fmtDate(r.analyzedAtMs, lang)}` : ""}
        </p>
      </CardContent>
    </Card>
  );
}
