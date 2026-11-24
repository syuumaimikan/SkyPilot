"use client";

import { useState } from "react";
import type { SnowballPlan } from "@skypilot/protocol";
import { Alert, Badge, Card, CardContent, CardHeader, CardTitle, Input, Label, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { parseCoins } from "@/components/analyze-form";
import { CopyButton, ErrorView, Freshness, Loading } from "@/components/common";
import { fmtCoins, fmtHours, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

/** Compounding AH flip plan: a diversified basket for your coins and where reinvesting leads. */
export function SnowballPanel() {
  const { t, lang } = useI18n();
  const [capitalText, setCapitalText] = useState("10m");
  const [hours, setHours] = useState("2");
  const [days, setDays] = useState("30");
  const capital = parseCoins(capitalText) ?? 10_000_000;
  const h = Math.min(24, Math.max(0, Number(hours) || 0));
  const d = Math.min(365, Math.max(1, Math.round(Number(days) || 30)));
  const q = useApi<SnowballPlan>(`/api/market/snowball?capital=${Math.round(capital)}&hoursPerDay=${h}&days=${d}`, undefined, { refetchInterval: 60_000 });
  const p = q.data;
  const last = p?.projection[p.projection.length - 1];
  const chartMax = p ? Math.max(...p.projection.map((x) => x.capital), p.capital) : 1;

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardContent className="grid gap-3 p-4 sm:grid-cols-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="sb-capital">{t("Coins to flip with", "フリップに使うコイン")}</Label>
            <Input id="sb-capital" value={capitalText} onChange={(e) => setCapitalText(e.target.value)} placeholder="10m" />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="sb-hours">{t("Hours you play per day", "1日のプレイ時間（時間）")}</Label>
            <Input id="sb-hours" inputMode="decimal" value={hours} onChange={(e) => setHours(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="sb-days">{t("Projection (days)", "見通し（日数）")}</Label>
            <Input id="sb-days" inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value)} />
          </div>
        </CardContent>
      </Card>
      {q.isLoading ? <Loading rows={4} /> : null}
      {q.error ? <ErrorView error={q.error} retry={() => q.refetch()} /> : null}
      {p ? (
        <>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <Freshness f={p.freshness} />
            <Badge tone={p.calibrationTrials > 0 ? "good" : "neutral"}>
              {p.calibrationTrials > 0
                ? t(`Calibrated on ${p.calibrationTrials} past flips (${p.calibrationHits} sold at target)`, `過去 ${p.calibrationTrials} 件のフリップ実績で補正（目標価格で売却 ${p.calibrationHits} 件）`)
                : t("Not calibrated yet (model only)", "まだ実績補正なし（モデルのみ）")}
            </Badge>
          </div>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat k={t("Expected profit per cycle", "1サイクルの期待利益")} v={fmtCoins(p.expectedProfitPerCycle)} sub={`${fmtPct(p.returnPerCycle)} ${t("of deployed coins", "（投入額比）")}`} />
            <Stat k={t("Cycle length", "1サイクルの長さ")} v={fmtHours(p.cycleHours, lang)} sub={`${p.cyclesPerDay.toFixed(1)} ${t("cycles / day", "サイクル/日")}`} />
            <Stat k={t("In the basket / reserve", "投入額 / 予備")} v={fmtCoins(p.deployed)} sub={`${t("reserve", "予備")} ${fmtCoins(p.reserve)}`} />
            <Stat k={t(`After ${p.days} days (projection)`, `${p.days}日後（見通し）`)} v={last ? fmtCoins(last.capital) : "—"} sub={last ? `+${fmtCoins(last.capital - p.capital)}` : ""} />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{t("Projected coins (reinvesting every cycle)", "資産の見通し（毎サイクル再投資）")}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex h-40 items-end gap-px" role="img" aria-label={t("Projected coins per day", "日ごとの資産の見通し")}>
                {p.projection.map((x) => (
                  <div key={x.day} className="flex-1 rounded-t bg-primary/70" style={{ height: `${Math.max(2, (x.capital / chartMax) * 100)}%` }} title={`${t("Day", "日目")} ${x.day}: ${fmtCoins(x.capital)} (+${fmtCoins(x.profit)})`} />
                ))}
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                {t("Growth slows once you can no longer buy enough of what the market offers.", "市場に出ている分を買い切れなくなると、伸びは緩やかになります。")} {t("Market per cycle", "1サイクルの市場規模")}: {fmtCoins(p.marketCapacity)}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t("Basket for your coins now", "今の資金で組むバスケット")}</CardTitle>
            </CardHeader>
            <CardContent>
              {p.picks.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("No flip fits these coins right now. Try more coins or check back after the next scan.", "今この資金に合うフリップはありません。資金を増やすか、次のスキャン後に確認してください。")}</p>
              ) : (
                <Table>
                  <THead>
                    <TR>
                      <TH>{t("Item", "アイテム")}</TH>
                      <TH>{t("Buy", "購入")}</TH>
                      <TH>{t("List at", "出品価格")}</TH>
                      <TH>{t("Net profit", "純利益")}</TH>
                      <TH>P(sale)</TH>
                      <TH>{t("Expected value", "期待値")}</TH>
                      <TH>{t("Record", "実績")}</TH>
                      <TH />
                    </TR>
                  </THead>
                  <TBody>
                    {p.picks.map((x) => (
                      <TR key={x.auctionId}>
                        <TD className="max-w-56 truncate font-medium">{x.itemName}</TD>
                        <TD>{fmtCoins(x.buyPrice)}</TD>
                        <TD>{fmtCoins(x.recommendedSell)}</TD>
                        <TD className="text-emerald-600 dark:text-emerald-400">{fmtCoins(x.expectedNetProfit)}</TD>
                        <TD>{fmtPct(x.probabilityOfSale, 0)}</TD>
                        <TD>{fmtCoins(x.expectedValue)}</TD>
                        <TD className="text-xs">{x.trackRecord ? `${x.trackRecord.hits}/${x.trackRecord.trials}` : "—"}</TD>
                        <TD>
                          <CopyButton text={x.viewCommand} />
                        </TD>
                      </TR>
                    ))}
                  </TBody>
                </Table>
              )}
            </CardContent>
          </Card>

          {p.milestones.length > 0 ? (
            <Card>
              <CardHeader>
                <CardTitle>{t("Capital ladder", "資金のステップ")}</CardTitle>
              </CardHeader>
              <CardContent className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {p.milestones.map((m) => (
                  <div key={m.capital} className="rounded-xl border border-border p-3 text-sm">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold">{fmtCoins(m.capital)}</span>
                      <Badge tone={m.day === 0 ? "good" : m.day ? "info" : "neutral"}>
                        {m.day === 0 ? t("reached", "到達済み") : m.day ? t(`~day ${m.day}`, `約${m.day}日目`) : t(`not within ${p.days} days`, `${p.days}日以内は未到達`)}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {t(`${m.affordableFlips} item types become flippable`, `${m.affordableFlips} 種類のアイテムがフリップ可能`)} · {t("best EV", "最高期待値")} {fmtCoins(m.bestExpectedValue)}
                    </p>
                  </div>
                ))}
              </CardContent>
            </Card>
          ) : null}

          <Alert>
            <ul className="list-disc space-y-1 pl-4 text-xs">
              {p.assumptions.map((a) => (
                <li key={a}>{a}</li>
              ))}
            </ul>
          </Alert>
        </>
      ) : null}
    </div>
  );
}

function Stat({ k, v, sub }: { k: string; v: string; sub?: string }) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-1 p-4">
        <span className="text-xs text-muted-foreground">{k}</span>
        <span className="text-xl font-semibold tabular-nums">{v}</span>
        {sub ? <span className="text-xs text-muted-foreground">{sub}</span> : null}
      </CardContent>
    </Card>
  );
}
