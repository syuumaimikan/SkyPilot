"use client";

import type { LiquidationEntry } from "@skypilot/protocol";
import { Alert, Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { ErrorView, Freshness, KnownView, Loading, NeedsAnalysis, PageHeader, StatCard } from "@/components/common";
import { fmtCoins, fmtPct, rarityClass } from "@/lib/format";
import { useApi, useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { WealthForecast } from "@/components/wealth-forecast";

function Liquidation() {
  const { t } = useI18n();
  const q = useApi<LiquidationEntry[]>("/api/advisor/liquidation");
  const tone = (v: string) => (v === "SAFE_TO_SELL" ? "good" : v === "CONSIDER_SELLING" ? "warn" : "bad");
  const label = (v: string) => (v === "SAFE_TO_SELL" ? t("Safe to sell", "売却可") : v === "CONSIDER_SELLING" ? t("Consider selling", "売却を検討") : t("Do not sell", "売らない"));
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Liquidation advisor", "売却アドバイザー")}</CardTitle>
        <CardDescription>{t("Which items you can sell without hurting your goals. Items your roadmap needs are never marked safe.", "目標に影響せずに売れるアイテムです。ロードマップで必要なアイテムは売却可にしません。")}</CardDescription>
      </CardHeader>
      <CardContent className="p-0">
        {q.isLoading ? (
          <div className="p-5">
            <Loading />
          </div>
        ) : q.error ? (
          <div className="p-5">
            <ErrorView error={q.error} retry={() => q.refetch()} />
          </div>
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>{t("Item", "アイテム")}</TH>
                <TH>{t("Value", "価値")}</TH>
                <TH>{t("Verdict", "判定")}</TH>
                <TH>{t("Reason", "理由")}</TH>
              </TR>
            </THead>
            <TBody>
              {(q.data ?? []).map((e, i) => (
                <TR key={`${e.itemId}-${i}`}>
                  <TD className="font-medium">{e.item}</TD>
                  <TD>{fmtCoins(e.value)}</TD>
                  <TD>
                    <Badge tone={tone(e.verdict)}>{label(e.verdict)}</Badge>
                  </TD>
                  <TD className="text-xs text-muted-foreground">{e.reason}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

export function NetworthView() {
  const { t } = useI18n();
  const ws = useWorkspace();
  if (ws.isLoading) return <Loading />;
  if (ws.error) return <ErrorView error={ws.error} />;
  const a = ws.data?.lastAnalysis?.analysis;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("Networth", "資産")}
        description={t("Valued with robust fair values, split by how easily each part can be turned into coins.", "外れ値に強い適正価格で評価し、換金しやすさ別に分けています。")}
        actions={a ? <Freshness f={a.fetched} /> : null}
      />
      <WealthForecast />
      {!a ? (
        <NeedsAnalysis />
      ) : (
        <KnownView
          v={a.networth}
          render={(n) => (
            <div className="flex flex-col gap-6">
              <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
                <StatCard label={t("Total", "合計")} value={fmtCoins(n.total)} hint={`${t("confidence", "信頼度")} ${fmtPct(n.confidence, 0)}`} />
                <StatCard label={t("Liquid (coins, Bazaar)", "流動資産（コイン・バザー）")} value={fmtCoins(n.liquid)} />
                <StatCard label={t("Semi-liquid (AH, sells fast)", "準流動（AHで早く売れる）")} value={fmtCoins(n.semiLiquid)} />
                <StatCard label={t("Illiquid (slow to sell)", "非流動（売りにくい）")} value={fmtCoins(n.illiquid)} />
                <StatCard label={t("Soulbound (cannot sell)", "ソウルバウンド（売却不可）")} value={fmtCoins(n.soulbound)} />
              </div>
              <div className="grid gap-5 lg:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle>{t("By category", "カテゴリ別")}</CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-2 text-sm">
                    {Object.entries(n.byCategory)
                      .sort((x, y) => (y[1] ?? 0) - (x[1] ?? 0))
                      .map(([k, v]) => (
                        <div key={k} className="flex justify-between">
                          <span>{k}</span>
                          <span className="tabular-nums">{fmtCoins(v ?? 0)}</span>
                        </div>
                      ))}
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader>
                    <CardTitle>{t("Most valuable items", "高価なアイテム")}</CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-1.5 text-sm">
                    {n.topItems.map((it, i) => (
                      <div key={i} className="flex justify-between gap-2">
                        <span className={rarityClass(it.rarity)}>
                          {it.name}
                          {it.soulbound ? <Badge className="ml-1">{t("soulbound", "ソウルバウンド")}</Badge> : null}
                        </span>
                        <span className="tabular-nums">{fmtCoins(it.estimatedValue)}</span>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              </div>
              {n.unpriced.length > 0 ? (
                <Alert tone="warn">
                  {t("No market price (excluded from the total)", "市場価格なし（合計から除外）")}: {n.unpriced.slice(0, 30).join(", ")}
                  {n.unpriced.length > 30 ? ` +${n.unpriced.length - 30}` : ""}
                </Alert>
              ) : null}
              <Liquidation />
            </div>
          )}
        />
      )}
    </div>
  );
}
