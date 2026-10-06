"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import type { BazaarFlipKind, BazaarList } from "@skypilot/protocol";
import { Badge, Card, CardContent, Input, Label, NativeSelect, Table, TBody, TD, TH, THead, TR, Tabs, TabsContent, TabsList, TabsTrigger } from "@skypilot/ui";
import { parseCoins } from "@/components/analyze-form";
import { ConfidenceBadge, Empty, ErrorView, Freshness, Loading, PageHeader, RiskBadge } from "@/components/common";
import { fmtCoins, fmtMinutesRange, fmtNum, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

const KIND: Record<BazaarFlipKind, [string, string]> = {
  ORDER_FLIP: ["Order flip", "注文フリップ"],
  INSTANT_FLIP: ["NPC / instant", "NPC・即時"],
  CRAFT_FLIP: ["Craft", "クラフト"],
};

export function BazaarView() {
  const { t, lang } = useI18n();
  const [capitalText, setCapitalText] = useState("50m");
  const capital = parseCoins(capitalText) ?? 50_000_000;
  const [flipper, setFlipper] = useState("0");
  const [hideRisky, setHideRisky] = useState(true);
  const q = useApi<BazaarList>("/api/market/bazaar", { capital, flipper }, { refetchInterval: 30_000 });
  const flips = useMemo(() => (q.data?.flips ?? []).filter((f) => !hideRisky || !f.manipulated), [q.data, hideRisky]);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<"spread" | "volume" | "trend">("volume");

  const products = useMemo(() => {
    const s = search.trim().toLowerCase();
    const list = (q.data?.products ?? []).filter((p) => !s || p.name.toLowerCase().includes(s) || p.productId.toLowerCase().includes(s));
    const key = { spread: (p: (typeof list)[number]) => p.spreadPct ?? -1, volume: (p: (typeof list)[number]) => p.buyMovingWeek + p.sellMovingWeek, trend: (p: (typeof list)[number]) => p.trend ?? -9 };
    return list.sort((a, b) => key[sort](b) - key[sort](a)).slice(0, 300);
  }, [q.data, search, sort]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("Bazaar", "バザー")}
        description={t(
          `Order flips and NPC flips after the ${q.data ? fmtPct(q.data.taxRate, 2) : ""} sell tax, sized to your capital and real weekly volume. Fill times are estimates from moving-week volume.`,
          `売却税 ${q.data ? fmtPct(q.data.taxRate, 2) : ""} を差し引いた注文フリップ・NPCフリップです。資金と実際の週間取引量に合わせて数量を提案します。約定時間は週間取引量からの推定です。`,
        )}
        actions={q.data ? <Freshness f={q.data.freshness} /> : null}
      />
      <div className="flex flex-wrap items-end gap-4">
        <div className="flex w-48 flex-col gap-1">
          <Label htmlFor="capital">{t("Your capital", "使える資金")}</Label>
          <Input id="capital" value={capitalText} onChange={(e) => setCapitalText(e.target.value)} placeholder="50m" />
        </div>
        <div className="flex w-56 flex-col gap-1">
          <Label htmlFor="flipper">{t("Bazaar Flipper upgrade", "Bazaar Flipper アップグレード")}</Label>
          <NativeSelect id="flipper" value={flipper} onChange={(e) => setFlipper(e.target.value)}>
            <option value="0">{t("None (tax 1.25%)", "なし（税 1.25%）")}</option>
            <option value="1">{t("Level 1 (tax 1.125%)", "レベル1（税 1.125%）")}</option>
            <option value="2">{t("Level 2 (tax 1%)", "レベル2（税 1%）")}</option>
          </NativeSelect>
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input type="checkbox" checked={hideRisky} onChange={(e) => setHideRisky(e.target.checked)} />
          {t("Hide possible manipulation", "操作の疑いがあるものを隠す")}
        </label>
      </div>
      {q.isLoading ? (
        <Loading rows={5} />
      ) : q.error ? (
        <ErrorView error={q.error} retry={() => q.refetch()} />
      ) : q.data ? (
        <Tabs defaultValue="flips">
          <TabsList>
            <TabsTrigger value="flips">{t("Flips", "フリップ")}</TabsTrigger>
            <TabsTrigger value="products">{t("All products", "全商品")}</TabsTrigger>
          </TabsList>
          <TabsContent value="flips">
            {flips.length === 0 ? (
              <Empty>{t("No profitable bazaar flips for this capital right now.", "この資金で利益の出るバザーフリップは現在ありません。")}</Empty>
            ) : (
              <Card>
                <CardContent className="p-0">
                  <Table>
                    <THead>
                      <TR>
                        <TH>{t("Product", "商品")}</TH>
                        <TH>{t("Type", "種類")}</TH>
                        <TH>{t("Buy at", "購入")}</TH>
                        <TH>{t("Sell at", "売却")}</TH>
                        <TH>{t("Profit/unit", "1個あたり利益")}</TH>
                        <TH>ROI</TH>
                        <TH>{t("Amount", "数量")}</TH>
                        <TH>{t("Expected profit", "期待利益")}</TH>
                        <TH>{t("Fill time (buy / sell)", "約定時間（買/売）")}</TH>
                        <TH>{t("Risk", "リスク")}</TH>
                        <TH>{t("Confidence", "信頼度")}</TH>
                      </TR>
                    </THead>
                    <TBody>
                      {flips.map((f) => (
                        <TR key={`${f.kind}-${f.productId}`}>
                          <TD className="max-w-xs">
                            <Link href={`/market/price?item=${encodeURIComponent(f.productId)}`} className="font-medium hover:underline">
                              {f.name}
                            </Link>
                            {f.manipulated ? <Badge className="ml-1" tone="bad">{t("Possible manipulation", "操作の疑い")}</Badge> : null}
                            {f.notes.length > 0 ? <div className="text-[11px] text-muted-foreground">{f.notes.join(" · ")}</div> : null}
                          </TD>
                          <TD>
                            <Badge>{t(...KIND[f.kind])}</Badge>
                          </TD>
                          <TD>{fmtCoins(f.buyPrice)}</TD>
                          <TD>{fmtCoins(f.sellPrice)}</TD>
                          <TD>{fmtCoins(f.profitPerUnit)}</TD>
                          <TD>{fmtPct(f.roi)}</TD>
                          <TD>{fmtNum(f.suggestedAmount)}</TD>
                          <TD className="text-emerald-600 dark:text-emerald-400">
                            {fmtCoins(f.expectedProfit)}
                            <div className="text-[11px] text-muted-foreground">
                              {t("capital", "資金")} {fmtCoins(f.capitalRequired)}
                              {f.profitPerHour !== null ? ` · ${fmtCoins(f.profitPerHour)}/h` : ""}
                            </div>
                          </TD>
                          <TD className="text-xs whitespace-nowrap">
                            {fmtMinutesRange(f.buyFillMinutes, lang)} / {fmtMinutesRange(f.sellFillMinutes, lang)}
                          </TD>
                          <TD>
                            <RiskBadge r={f.risk} />
                          </TD>
                          <TD>
                            <ConfidenceBadge c={f.confidence} />
                          </TD>
                        </TR>
                      ))}
                    </TBody>
                  </Table>
                </CardContent>
              </Card>
            )}
          </TabsContent>
          <TabsContent value="products" className="flex flex-col gap-3">
            <div className="flex flex-wrap gap-3">
              <Input className="max-w-xs" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t("Search products", "商品を検索")} aria-label={t("Search products", "商品を検索")} />
              <NativeSelect className="max-w-48" value={sort} onChange={(e) => setSort(e.target.value as typeof sort)} aria-label={t("Sort by", "並び順")}>
                <option value="volume">{t("Weekly volume", "週間取引量")}</option>
                <option value="spread">{t("Spread %", "スプレッド %")}</option>
                <option value="trend">{t("Trend", "トレンド")}</option>
              </NativeSelect>
            </div>
            <Card>
              <CardContent className="p-0">
                <Table>
                  <THead>
                    <TR>
                      <TH>{t("Product", "商品")}</TH>
                      <TH>{t("Instant buy", "即時購入")}</TH>
                      <TH>{t("Instant sell", "即時売却")}</TH>
                      <TH>{t("Spread", "スプレッド")}</TH>
                      <TH>{t("Weekly buy / sell", "週間 買/売")}</TH>
                      <TH>{t("Trend", "トレンド")}</TH>
                    </TR>
                  </THead>
                  <TBody>
                    {products.map((p) => (
                      <TR key={p.productId}>
                        <TD>
                          <Link href={`/market/price?item=${encodeURIComponent(p.productId)}`} className="hover:underline">
                            {p.name}
                          </Link>
                        </TD>
                        <TD>{fmtCoins(p.instantBuy)}</TD>
                        <TD>{fmtCoins(p.instantSell)}</TD>
                        <TD>{p.spreadPct !== null ? fmtPct(p.spreadPct) : "—"}</TD>
                        <TD className="text-xs">
                          {fmtNum(p.buyMovingWeek)} / {fmtNum(p.sellMovingWeek)}
                        </TD>
                        <TD className={p.trend !== null && p.trend > 0 ? "text-emerald-600 dark:text-emerald-400" : p.trend !== null && p.trend < 0 ? "text-red-500" : ""}>{p.trend !== null ? fmtPct(p.trend) : "—"}</TD>
                      </TR>
                    ))}
                  </TBody>
                </Table>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      ) : null}
    </div>
  );
}
