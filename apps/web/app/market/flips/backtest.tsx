"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import type { BacktestRequest, BacktestResult } from "@skypilot/protocol";
import { Alert, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { parseCoins } from "@/components/analyze-form";
import { ErrorView, StatCard } from "@/components/common";
import { api, type ApiError } from "@/lib/api";
import { fmtCoins, fmtDate, fmtHours, fmtNum, fmtPct } from "@/lib/format";
import { useI18n } from "@/lib/i18n";

export function BacktestPanel() {
  const { t, lang } = useI18n();
  const [minRoi, setMinRoi] = useState("10");
  const [minProfit, setMinProfit] = useState("500k");
  const [minSpd, setMinSpd] = useState("2");
  const [maxBuy, setMaxBuy] = useState("");
  const [lookback, setLookback] = useState("72");
  const [hold, setHold] = useState("24");
  const run = useMutation<BacktestResult, ApiError, BacktestRequest>({ mutationFn: (r) => api.post<BacktestResult>("/api/market/backtest", r) });

  const req: BacktestRequest | null = (() => {
    const profit = parseCoins(minProfit);
    const mb = maxBuy.trim() ? parseCoins(maxBuy) : null;
    const lb = Number(lookback);
    const h = Number(hold);
    if (profit === null || (maxBuy.trim() && mb === null) || !(lb >= 1 && lb <= 2160) || !(h >= 1 && h <= 336) || !Number.isFinite(Number(minRoi)) || !Number.isFinite(Number(minSpd))) return null;
    return { minRoi: Number(minRoi) / 100, minProfit: profit, minSalesPerDay: Number(minSpd), maxBuyPrice: mb, minConfidence: null, lookbackHours: Math.round(lb), maxHoldHours: Math.round(h) };
  })();

  const r = run.data;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Backtest", "バックテスト")}</CardTitle>
        <CardDescription>
          {t(
            "Replays flip candidates the collector actually saw against later recorded sales. Results only cover the history this server has collected.",
            "コレクターが実際に検出したフリップ候補を、その後に記録された売買と照合します。結果はこのサーバーが収集した履歴の範囲に限られます。",
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {(
            [
              ["bt-roi", t("Min ROI %", "最低ROI %"), minRoi, setMinRoi],
              ["bt-profit", t("Min profit", "最低利益"), minProfit, setMinProfit],
              ["bt-spd", t("Min sales/day", "最低販売数/日"), minSpd, setMinSpd],
              ["bt-max", t("Max buy (optional)", "最大購入額（任意）"), maxBuy, setMaxBuy],
              ["bt-lb", t("Lookback hours", "期間（時間）"), lookback, setLookback],
              ["bt-hold", t("Max hold hours", "最大保有時間"), hold, setHold],
            ] as const
          ).map(([id, label, v, set]) => (
            <div key={id} className="flex flex-col gap-1">
              <Label htmlFor={id}>{label}</Label>
              <Input id={id} value={v} onChange={(e) => set(e.target.value)} />
            </div>
          ))}
        </div>
        <Button className="w-fit" disabled={!req || run.isPending} onClick={() => req && run.mutate(req)}>
          {run.isPending ? t("Running…", "実行中…") : t("Run backtest", "バックテストを実行")}
        </Button>
        {run.error ? <ErrorView error={run.error} /> : null}
        {r ? (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label={t("Trades", "取引数")} value={fmtNum(r.trades)} hint={`${t("data window", "データ期間")} ${fmtHours(r.dataWindowHours, lang)}`} />
              <StatCard label={t("Win rate", "勝率")} value={fmtPct(r.winRate)} />
              <StatCard label={t("Total / median profit", "合計 / 中央値利益")} value={`${fmtCoins(r.totalProfit)} / ${fmtCoins(r.medianProfit)}`} />
              <StatCard label={t("Max drawdown", "最大ドローダウン")} value={fmtCoins(r.maxDrawdown)} hint={`${t("capital", "必要資金")} ${fmtCoins(r.capitalRequired)}`} />
            </div>
            {r.notes.length > 0 ? (
              <Alert tone="info">
                {r.notes.map((n, i) => (
                  <p key={i}>{n}</p>
                ))}
              </Alert>
            ) : null}
            {r.sample.length > 0 ? (
              <Table>
                <THead>
                  <TR>
                    <TH>{t("Item", "アイテム")}</TH>
                    <TH>{t("Bought", "購入")}</TH>
                    <TH>{t("Buy", "購入額")}</TH>
                    <TH>{t("Sell", "売却額")}</TH>
                    <TH>{t("Profit", "利益")}</TH>
                    <TH>{t("Hold", "保有")}</TH>
                  </TR>
                </THead>
                <TBody>
                  {r.sample.map((x, i) => (
                    <TR key={i}>
                      <TD>
                        {x.itemId}
                        {x.forcedExit ? <span className="ml-1 text-xs text-amber-600">({t("forced exit", "強制決済")})</span> : null}
                      </TD>
                      <TD className="text-xs">{fmtDate(x.boughtAtMs, lang)}</TD>
                      <TD>{fmtCoins(x.buyPrice)}</TD>
                      <TD>{fmtCoins(x.sellPrice)}</TD>
                      <TD className={x.profit >= 0 ? "text-emerald-600 dark:text-emerald-400" : "text-red-500"}>{fmtCoins(x.profit)}</TD>
                      <TD>{fmtHours(x.holdMinutes / 60, lang)}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
