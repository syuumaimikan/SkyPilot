"use client";

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import type { FlipOpportunity, RiskLevel } from "@skypilot/protocol";
import { Alert, Badge, Card, CardContent, Input, Label, NativeSelect, Switch, Table, TBody, TH, THead, TR, Tabs, TabsContent, TabsList, TabsTrigger } from "@skypilot/ui";
import { parseCoins } from "@/components/analyze-form";
import { Empty, ErrorView, Freshness, Loading, PageHeader } from "@/components/common";
import { matchesFilter, RISK_ORDER } from "@/lib/flips";
import { fmtNum } from "@/lib/format";
import { useI18n } from "@/lib/i18n";
import { useLiveFlips } from "@/lib/live";
import { AlertsPanel } from "./alerts";
import { BacktestPanel } from "./backtest";
import { FlipRow } from "./flip-row";

type SortKey = "score" | "profit" | "roi" | "confidence";

export function FlipsView() {
  const { t } = useI18n();
  const focus = useSearchParams().get("focus");
  const { list, state, error } = useLiveFlips();
  const [budget, setBudget] = useState("");
  const [minProfit, setMinProfit] = useState("");
  const [minRoi, setMinRoi] = useState("");
  const [maxRisk, setMaxRisk] = useState<RiskLevel | "">("");
  const [search, setSearch] = useState("");
  const [hideSuspicious, setHideSuspicious] = useState(true);
  const [sort, setSort] = useState<SortKey>("score");
  const [open, setOpen] = useState<string | null>(focus);

  const flips = useMemo(() => {
    const all = list?.flips ?? [];
    const filtered = all.filter((f) =>
      matchesFilter(f, {
        maxBudget: budget.trim() ? parseCoins(budget) : null,
        minProfit: minProfit.trim() ? parseCoins(minProfit) : null,
        minRoi: minRoi.trim() && Number.isFinite(Number(minRoi)) ? Number(minRoi) / 100 : null,
        minConfidence: null,
        maxRisk: maxRisk || null,
        search,
        hideSuspicious,
      }),
    );
    const key: Record<SortKey, (f: FlipOpportunity) => number> = { score: (f) => f.score, profit: (f) => f.expectedNetProfit, roi: (f) => f.roi, confidence: (f) => f.confidence.score };
    return filtered.sort((a, b) => key[sort](b) - key[sort](a));
  }, [list, budget, minProfit, minRoi, maxRisk, search, hideSuspicious, sort]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("Live AH Flips", "ライブ AH フリップ")}
        description={t(
          "BIN listings priced below a robust fair value (sales + listings, outliers removed), after Auction House fees. Never based on lowest BIN alone.",
          "外れ値を除いた売買・出品データから算出した適正価格より安いBIN出品を、AH手数料を差し引いて表示します。最安BINだけで判断することはありません。",
        )}
        actions={
          <div className="flex items-center gap-3 text-xs">
            <Badge tone={state === "live" ? "good" : state === "polling" ? "warn" : "neutral"}>{state === "live" ? t("Live", "ライブ") : state === "polling" ? t("Polling every 20s", "20秒ごとに更新") : t("Connecting…", "接続中…")}</Badge>
            {list ? <Freshness f={list.freshness} /> : null}
          </div>
        }
      />
      {list && !list.collectorHealthy ? <Alert tone="warn">{t("The market collector is not healthy - data may be outdated.", "マーケットコレクターが正常ではありません。データが古い可能性があります。")}</Alert> : null}
      <Tabs defaultValue="flips">
        <TabsList>
          <TabsTrigger value="flips">{t("Flips", "フリップ")}</TabsTrigger>
          <TabsTrigger value="alerts">{t("Alerts", "通知")}</TabsTrigger>
          <TabsTrigger value="backtest">{t("Backtest", "バックテスト")}</TabsTrigger>
        </TabsList>
        <TabsContent value="flips" className="flex flex-col gap-4">
          <Card>
            <CardContent className="grid gap-3 p-4 sm:grid-cols-3 lg:grid-cols-6">
              <div className="flex flex-col gap-1">
                <Label htmlFor="f-search">{t("Search", "検索")}</Label>
                <Input id="f-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Hyperion" />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="f-budget">{t("Max buy price", "最大購入価格")}</Label>
                <Input id="f-budget" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="50m" />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="f-profit">{t("Min net profit", "最低純利益")}</Label>
                <Input id="f-profit" value={minProfit} onChange={(e) => setMinProfit(e.target.value)} placeholder="1m" />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="f-roi">{t("Min ROI %", "最低ROI %")}</Label>
                <Input id="f-roi" value={minRoi} onChange={(e) => setMinRoi(e.target.value)} placeholder="5" inputMode="decimal" />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="f-risk">{t("Max risk", "最大リスク")}</Label>
                <NativeSelect id="f-risk" value={maxRisk} onChange={(e) => setMaxRisk(e.target.value as RiskLevel | "")}>
                  <option value="">{t("Any", "指定なし")}</option>
                  {RISK_ORDER.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </NativeSelect>
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="f-sort">{t("Sort by", "並び順")}</Label>
                <NativeSelect id="f-sort" value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
                  <option value="score">{t("Score (profit × probability × confidence / capital × time × risk)", "スコア")}</option>
                  <option value="profit">{t("Net profit", "純利益")}</option>
                  <option value="roi">ROI</option>
                  <option value="confidence">{t("Confidence", "信頼度")}</option>
                </NativeSelect>
              </div>
              <label className="flex items-center gap-2 text-xs text-muted-foreground sm:col-span-3 lg:col-span-6">
                <Switch checked={hideSuspicious} onCheckedChange={setHideSuspicious} />
                {t("Hide flips flagged as possible market manipulation", "相場操作の疑いがあるフリップを隠す")}
              </label>
            </CardContent>
          </Card>
          {error && !list ? <ErrorView error={error} /> : null}
          {!list && !error ? (
            <Loading rows={6} />
          ) : list && flips.length === 0 ? (
            <Empty>
              {list.flips.length === 0
                ? t("No flips right now. Fair values need sales history - a freshly started server shows few flips until it has collected sales.", "現在フリップはありません。適正価格には売買履歴が必要なため、起動直後のサーバーでは売買データが集まるまでフリップは少なくなります。")
                : hideSuspicious && list.flips.every((f) => f.manipulation.suspicious)
                  ? t(`All ${list.flips.length} candidates are flagged as possible manipulation (DO NOT FLIP) and are hidden. Turn off the switch above to inspect them.`, `候補 ${list.flips.length} 件はすべて相場操作の疑い（フリップ禁止）として非表示です。上のスイッチをオフにすると確認できます。`)
                  : t("No flips match your filters.", "条件に合うフリップはありません。")}
            </Empty>
          ) : list ? (
            <Card>
              <CardContent className="p-0">
                <div className="px-4 pt-3 text-xs text-muted-foreground">
                  {t(`${flips.length} shown of ${list.flips.length} · ${fmtNum(list.scannedListings)} BIN listings scanned`, `${list.flips.length}件中${flips.length}件を表示 · BIN出品 ${fmtNum(list.scannedListings)}件をスキャン`)}
                </div>
                <Table>
                  <THead>
                    <TR>
                      <TH>{t("Item", "アイテム")}</TH>
                      <TH>{t("Buy", "購入")}</TH>
                      <TH>{t("Max buy", "最大購入")}</TH>
                      <TH>{t("Fair value", "適正価格")}</TH>
                      <TH>{t("Net profit", "純利益")}</TH>
                      <TH>ROI</TH>
                      <TH>{t("Sell time", "売却時間")}</TH>
                      <TH>{t("Confidence", "信頼度")}</TH>
                      <TH>{t("Risk", "リスク")}</TH>
                      <TH className="text-right">{t("Score", "スコア")}</TH>
                    </TR>
                  </THead>
                  <TBody>
                    {flips.map((f) => (
                      <FlipRow key={f.auctionId} f={f} open={open === f.auctionId} onToggle={() => setOpen(open === f.auctionId ? null : f.auctionId)} />
                    ))}
                  </TBody>
                </Table>
              </CardContent>
            </Card>
          ) : null}
        </TabsContent>
        <TabsContent value="alerts">
          <AlertsPanel flips={list?.flips ?? []} />
        </TabsContent>
        <TabsContent value="backtest">
          <BacktestPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}
