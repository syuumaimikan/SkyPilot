"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Area, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis, Bar } from "recharts";
import type { ItemSearchResult, PriceDetail } from "@skypilot/protocol";
import { Alert, Badge, Button, Card, CardContent, CardHeader, CardTitle, Input } from "@skypilot/ui";
import { ConfidenceBadge, Empty, ErrorView, Freshness, Loading, PageHeader, StatCard } from "@/components/common";
import { IngredientTree } from "@/components/ingredient-tree";
import { fmtCoins, fmtDate, fmtMinutesRange, fmtNum, fmtPct, rarityClass } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

const RANGES = ["1H", "6H", "24H", "7D", "30D", "90D"] as const;

function useDebounced<T>(v: T, ms: number): T {
  const [d, setD] = useState(v);
  useEffect(() => {
    const id = setTimeout(() => setD(v), ms);
    return () => clearTimeout(id);
  }, [v, ms]);
  return d;
}

function Chart({ d }: { d: PriceDetail }) {
  const { lang, t } = useI18n();
  const points = d.history.filter((p) => p.median !== null || p.low !== null);
  if (points.length < 2) {
    return <Empty>{t("Not enough price history for this range yet. History accumulates while the server collects market data.", "この期間の価格履歴はまだ十分にありません。サーバーがデータを収集するにつれて蓄積されます。")}</Empty>;
  }
  const short = d.range === "1H" || d.range === "6H" || d.range === "24H";
  return (
    <div className="h-72 w-full">
      <ResponsiveContainer>
        <ComposedChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="t"
            type="number"
            domain={["dataMin", "dataMax"]}
            tickFormatter={(v: number) => new Date(v).toLocaleString(lang === "ja" ? "ja-JP" : "en-US", short ? { hour: "2-digit", minute: "2-digit" } : { month: "short", day: "numeric" })}
            stroke="var(--muted-foreground)"
            fontSize={11}
          />
          <YAxis yAxisId="p" tickFormatter={(v: number) => fmtCoins(v)} stroke="var(--muted-foreground)" fontSize={11} width={60} />
          <YAxis yAxisId="v" orientation="right" hide />
          <ChartTooltip
            contentStyle={{ background: "var(--popover)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
            labelFormatter={(v) => fmtDate(Number(v), lang)}
            formatter={(value, name) => [name === "volume" ? fmtNum(Number(value)) : fmtCoins(Number(value)), String(name)]}
          />
          <Bar yAxisId="v" dataKey="volume" fill="var(--muted)" />
          <Area yAxisId="p" dataKey="high" stroke="none" fill="var(--chart-1)" fillOpacity={0.08} />
          <Line yAxisId="p" dataKey="low" stroke="var(--chart-2)" dot={false} strokeWidth={1} />
          <Line yAxisId="p" dataKey="median" stroke="var(--chart-1)" dot={false} strokeWidth={2} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

function Detail({ id }: { id: string }) {
  const { t, lang } = useI18n();
  const [range, setRange] = useState<(typeof RANGES)[number]>("24H");
  const q = useApi<PriceDetail>(`/api/market/items/${encodeURIComponent(id)}`, { range }, { refetchInterval: 60_000 });
  if (q.isLoading) return <Loading rows={4} />;
  if (q.error) return <ErrorView error={q.error} retry={() => q.refetch()} />;
  const d = q.data!;
  const fv = d.fairValue;
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className={`text-lg font-semibold ${rarityClass(d.tier)}`}>{d.name}</h2>
          {d.tier ? <Badge>{d.tier}</Badge> : null}
          <code className="text-xs text-muted-foreground">{d.itemId}</code>
        </div>
        <Freshness f={d.freshness} />
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {d.bazaar ? (
          <>
            <StatCard label={t("Instant buy", "即時購入")} value={fmtCoins(d.bazaar.instantBuy)} />
            <StatCard label={t("Instant sell", "即時売却")} value={fmtCoins(d.bazaar.instantSell)} />
            <StatCard label={t("Spread", "スプレッド")} value={d.bazaar.spreadPct !== null ? fmtPct(d.bazaar.spreadPct) : "—"} />
            <StatCard label={t("Weekly volume (buy / sell)", "週間取引量（買/売）")} value={`${fmtNum(d.bazaar.buyMovingWeek)} / ${fmtNum(d.bazaar.sellMovingWeek)}`} />
          </>
        ) : fv ? (
          <>
            <StatCard label={t("Fair value", "適正価格")} value={fv.value !== null ? fmtCoins(fv.value) : t("Not enough data", "データ不足")} hint={fv.method} />
            <StatCard label={t("Lowest BIN", "最安BIN")} value={fmtCoins(fv.lowestBin)} hint={`${fmtNum(fv.activeListings)} ${t("listings", "件出品中")}`} />
            <StatCard label={t("Sales median", "売買中央値")} value={fmtCoins(fv.salesMedian)} hint={`${fmtNum(fv.salesPerDay, 1)} ${t("sales/day", "件/日")}`} />
            <StatCard label={t("Expected sell time", "想定売却時間")} value={fmtMinutesRange(fv.expectedSellMinutes, lang)} hint={`${t("liquidity", "流動性")} ${fmtPct(fv.liquidity, 0)}`} />
          </>
        ) : (
          <Alert className="col-span-full">{t("No market data for this item (not traded on the Bazaar or Auction House recently).", "このアイテムの市場データはありません（最近バザー・AHで取引されていません）。")}</Alert>
        )}
      </div>
      {fv ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <ConfidenceBadge c={fv.confidence} />
          {fv.trend !== null ? <span>{t("Trend", "トレンド")} {fmtPct(fv.trend)}</span> : null}
          {fv.volatility !== null ? <span>{t("Volatility", "ボラティリティ")} {fmtPct(fv.volatility)}</span> : null}
          <span>
            {t("2nd lowest BIN", "2番目に安いBIN")} {fmtCoins(fv.secondLowestBin)} · {t("trimmed mean", "トリム平均")} {fmtCoins(fv.salesTrimmedMean)} · {fmtNum(fv.sales24h)} {t("sales in 24h", "件（24時間）")}
          </span>
        </div>
      ) : null}
      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>{t("Price history", "価格履歴")}</CardTitle>
          <div className="flex gap-1" role="group" aria-label={t("Range", "期間")}>
            {RANGES.map((r) => (
              <Button key={r} size="sm" variant={r === range ? "secondary" : "ghost"} onClick={() => setRange(r)} aria-pressed={r === range}>
                {r}
              </Button>
            ))}
          </div>
        </CardHeader>
        <CardContent>
          <Chart d={d} />
        </CardContent>
      </Card>
      {d.craftTree || d.craftCost !== null || d.npcSellPrice !== null ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("Craft & NPC", "クラフト・NPC")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            <div className="flex flex-wrap gap-6">
              <span>
                {t("Craft cost", "クラフト費用")}: <b className="tabular-nums">{fmtCoins(d.craftCost)}</b>
              </span>
              <span>
                {t("NPC sell price", "NPC売却価格")}: <b className="tabular-nums">{fmtCoins(d.npcSellPrice)}</b>
              </span>
            </div>
            {d.craftTree ? <IngredientTree items={d.craftTree.children.length ? d.craftTree.children : [d.craftTree]} /> : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}

export function PriceView() {
  const { t } = useI18n();
  const router = useRouter();
  const selected = useSearchParams().get("item");
  const [text, setText] = useState("");
  const query = useDebounced(text.trim(), 250);
  const results = useApi<ItemSearchResult[]>(query.length >= 2 ? "/api/market/search" : null, { q: query });
  const pick = (id: string) => {
    setText("");
    router.replace(`/market/price?item=${encodeURIComponent(id)}`);
  };
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Price Explorer", "価格エクスプローラー")} description={t("Search any SkyBlock item: Bazaar prices, robust AH fair value, history (1H–90D) and craft cost.", "SkyBlockのアイテムを検索：バザー価格、AH適正価格、価格履歴（1時間〜90日）、クラフト費用。")} />
      <div className="relative max-w-lg">
        <Input value={text} onChange={(e) => setText(e.target.value)} placeholder={t("Search items (e.g. Hyperion, enchanted diamond)", "アイテムを検索（例: Hyperion）")} aria-label={t("Search items", "アイテムを検索")} />
        {query.length >= 2 && results.data && text ? (
          <div className="absolute z-20 mt-1 w-full overflow-hidden rounded-lg border border-border bg-popover shadow-lg">
            {results.data.length === 0 ? (
              <div className="px-3 py-2 text-sm text-muted-foreground">{t("No items found", "見つかりません")}</div>
            ) : (
              results.data.map((r) => (
                <button key={r.itemId} onClick={() => pick(r.itemId)} className="flex w-full cursor-pointer items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent">
                  <span className={rarityClass(r.tier)}>{r.name}</span>
                  <span className="ml-auto flex gap-1">
                    {r.bazaar ? <Badge>Bazaar</Badge> : null}
                    {r.auctionable ? <Badge>AH</Badge> : null}
                  </span>
                </button>
              ))
            )}
          </div>
        ) : null}
      </div>
      {selected ? <Detail id={selected} /> : <Empty>{t("Search for an item to see its prices.", "アイテムを検索すると価格を表示します。")}</Empty>}
    </div>
  );
}
