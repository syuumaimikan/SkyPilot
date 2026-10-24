"use client";

import Link from "next/link";
import type { RecommendedAction } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardHeader, CardTitle, Tooltip } from "@skypilot/ui";
import { fmtCoins, fmtHours, fmtNum } from "@/lib/format";
import { useI18n } from "@/lib/i18n";
import { ConfidenceBadge, MetricView, SourceBadge } from "./common";

export function ActionCard({ a, rank }: { a: RecommendedAction; rank?: number }) {
  const { t, lang } = useI18n();
  const s = a.score;
  return (
    <Card className="flex flex-col" data-featured={rank === 1 ? "true" : undefined}>
      <CardHeader className="gap-2">
        <div className="flex items-start gap-3">
          {rank !== undefined ? <span className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-2xl bg-primary/15 text-sm font-semibold text-primary">{String(rank).padStart(2, "0")}</span> : null}
          <div className="min-w-0 flex-1">
            <CardTitle className="text-[15px]">{a.title}</CardTitle>
            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
              <Badge>{a.category}</Badge>
              <ConfidenceBadge c={a.confidence} />
              {a.sources.map((src) => (
                <SourceBadge key={src} s={src} />
              ))}
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-3">
        <p className="text-sm text-muted-foreground">{a.why}</p>
        {a.steps.length > 0 ? (
          <ol className="list-decimal space-y-1 pl-5 text-sm">
            {a.steps.map((st, i) => (
              <li key={i}>{st}</li>
            ))}
          </ol>
        ) : null}
        {a.metrics.length > 0 ? (
          <div className="grid grid-cols-2 gap-3 rounded-lg bg-muted/40 p-3 sm:grid-cols-3">
            {a.metrics.map((m, i) => (
              <MetricView key={i} m={m} />
            ))}
          </div>
        ) : null}
        {a.purchases.length > 0 ? (
          <div className="flex flex-col gap-1 text-sm">
            <span className="text-[11px] text-muted-foreground">{t("Buy (you decide; nothing is bought automatically)", "購入候補（購入はご自身で判断してください）")}</span>
            {a.purchases.map((p) => (
              <div key={p.itemId} className="flex flex-wrap items-center justify-between gap-2">
                <span>{p.name}</span>
                <span className="tabular-nums text-muted-foreground">
                  {p.price !== null ? fmtCoins(p.price) : t("No price data", "価格データなし")}
                  {p.priceSource ? ` · ${p.priceSource}` : ""}
                  {p.coinsPerUnit !== null && p.gainUnit ? ` · ${fmtCoins(p.coinsPerUnit)}/${p.gainUnit}` : ""}
                  {p.maxBuyPrice !== null ? ` · ${t("max", "上限")} ${fmtCoins(p.maxBuyPrice)}` : ""}
                </span>
              </div>
            ))}
          </div>
        ) : null}
        <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border pt-3 text-xs text-muted-foreground">
          <span>
            {t("Cost", "コスト")}: <span className="text-foreground tabular-nums">{a.estimatedCost !== null ? fmtCoins(a.estimatedCost) : "—"}</span>
          </span>
          <span>
            {t("Time", "時間")}: <span className="text-foreground tabular-nums">{fmtHours(a.estimatedHours, lang)}</span>
          </span>
          <Tooltip
            content={
              <div className="grid grid-cols-2 gap-x-3 gap-y-0.5">
                <span>{t("Goal impact", "目標への影響")}</span>
                <span className="tabular-nums">{fmtNum(s.goalImpact * 100)}%</span>
                <span>{t("Difficulty", "難易度")}</span>
                <span className="tabular-nums">{fmtNum(s.difficulty, 1)}/5</span>
                <span>{t("Risk", "リスク")}</span>
                <span className="tabular-nums">{fmtNum(s.risk, 1)}/5</span>
                <span>{t("Coins/h", "コイン/時")}</span>
                <span className="tabular-nums">{fmtCoins(s.coinReturnPerHour)}</span>
                <span>{t("Unlock value", "解放価値")}</span>
                <span className="tabular-nums">{fmtNum(s.unlockValue * 100)}%</span>
                <span>{t("Strategy", "戦略")}</span>
                <span>{s.strategy}</span>
              </div>
            }
          >
            <span className="cursor-help underline decoration-dotted">
              {t("Priority", "優先度")} {fmtNum(s.priority, 2)}
            </span>
          </Tooltip>
          {a.link ? (
            <Link href={a.link} className="ml-auto text-primary hover:underline">
              {t("Details →", "詳細 →")}
            </Link>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
