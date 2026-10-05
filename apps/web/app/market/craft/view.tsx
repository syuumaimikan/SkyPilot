"use client";

import { Fragment, useMemo, useState } from "react";
import type { CraftFlip, Freshness as FreshnessT, TimeFlip } from "@skypilot/protocol";
import { Badge, Card, CardContent, Input, Table, TBody, TD, TH, THead, TR, Tabs, TabsContent, TabsList, TabsTrigger } from "@skypilot/ui";
import { ConfidenceBadge, Empty, ErrorView, Freshness, Loading, PageHeader } from "@/components/common";
import { IngredientTree } from "@/components/ingredient-tree";
import { fmtCoins, fmtHours, fmtNum, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

interface CraftResp {
  flips: CraftFlip[];
  computedAtMs: number;
  freshness: FreshnessT;
}
interface TimeResp {
  flips: TimeFlip[];
  computedAtMs: number;
  freshness: FreshnessT;
}

function CraftTable({ rows }: { rows: CraftFlip[] }) {
  const { t } = useI18n();
  const [open, setOpen] = useState<string | null>(null);
  return (
    <Table>
      <THead>
        <TR>
          <TH>{t("Item", "アイテム")}</TH>
          <TH>{t("Craft cost", "クラフト費用")}</TH>
          <TH>{t("Sells for", "売値")}</TH>
          <TH>{t("Fees", "手数料")}</TH>
          <TH>{t("Profit", "利益")}</TH>
          <TH>ROI</TH>
          <TH>{t("Sales/day", "販売数/日")}</TH>
          <TH>{t("Confidence", "信頼度")}</TH>
        </TR>
      </THead>
      <TBody>
        {rows.map((f) => (
          <Fragment key={f.itemId}>
            <TR className="cursor-pointer" onClick={() => setOpen(open === f.itemId ? null : f.itemId)}>
              <TD className="max-w-xs">
                <div className="font-medium">
                  {f.outputCount > 1 ? `${f.outputCount}× ` : ""}
                  {f.name}
                </div>
                {f.requirement ? <div className="text-[11px] text-amber-600 dark:text-amber-400">{f.requirement}</div> : null}
              </TD>
              <TD>
                {fmtCoins(f.materialCost + f.craftCost)}
                {f.craftCost > 0 ? <div className="text-[11px] text-muted-foreground">{t("incl. crafting fee", "クラフト手数料込み")} {fmtCoins(f.craftCost)}</div> : null}
              </TD>
              <TD>
                {fmtCoins(f.salePrice)} <Badge>{f.saleSource}</Badge>
              </TD>
              <TD>{fmtCoins(f.fees)}</TD>
              <TD className="text-emerald-600 dark:text-emerald-400">{fmtCoins(f.expectedProfit)}</TD>
              <TD>{fmtPct(f.roi)}</TD>
              <TD>{fmtNum(f.salesPerDay, 1)}</TD>
              <TD>
                <ConfidenceBadge c={f.confidence} />
              </TD>
            </TR>
            {open === f.itemId ? (
              <tr className="bg-muted/30">
                <td colSpan={8} className="px-4 py-3">
                  <IngredientTree items={f.ingredients} />
                </td>
              </tr>
            ) : null}
          </Fragment>
        ))}
      </TBody>
    </Table>
  );
}

function TimeTable({ rows }: { rows: TimeFlip[] }) {
  const { t, lang } = useI18n();
  const [open, setOpen] = useState<string | null>(null);
  return (
    <Table>
      <THead>
        <TR>
          <TH>{t("Output", "完成品")}</TH>
          <TH>{t("Type", "種類")}</TH>
          <TH>{t("Capital", "元手")}</TH>
          <TH>{t("Duration", "所要時間")}</TH>
          <TH>{t("Profit", "利益")}</TH>
          <TH>{t("Profit/day", "利益/日")}</TH>
          <TH>ROI</TH>
          <TH>{t("Confidence", "信頼度")}</TH>
        </TR>
      </THead>
      <TBody>
        {rows.map((f) => {
          const key = `${f.kind}-${f.outputId}`;
          return (
            <Fragment key={key}>
              <TR className="cursor-pointer" onClick={() => setOpen(open === key ? null : key)}>
                <TD className="max-w-xs">
                  <div className="font-medium">{f.name}</div>
                  <div className="text-[11px] text-muted-foreground">{f.inputSummary}</div>
                  {f.requirement ? <div className="text-[11px] text-amber-600 dark:text-amber-400">{f.requirement}</div> : null}
                </TD>
                <TD>
                  <Badge>{f.kind === "FORGE" ? t("Forge", "フォージ") : "Kat"}</Badge>
                </TD>
                <TD>{fmtCoins(f.capital)}</TD>
                <TD>{fmtHours(f.processingHours, lang)}</TD>
                <TD className="text-emerald-600 dark:text-emerald-400">{fmtCoins(f.profit)}</TD>
                <TD>{fmtCoins(f.profitPerDay)}</TD>
                <TD>{fmtPct(f.roi)}</TD>
                <TD>
                  <ConfidenceBadge c={f.confidence} />
                </TD>
              </TR>
              {open === key ? (
                <tr className="bg-muted/30">
                  <td colSpan={8} className="px-4 py-3">
                    <IngredientTree items={f.ingredients} />
                  </td>
                </tr>
              ) : null}
            </Fragment>
          );
        })}
      </TBody>
    </Table>
  );
}

export function CraftView() {
  const { t } = useI18n();
  const craft = useApi<CraftResp>("/api/market/craft", undefined, { refetchInterval: 60_000 });
  const time = useApi<TimeResp>("/api/market/time", undefined, { refetchInterval: 60_000 });
  const [search, setSearch] = useState("");
  const s = search.trim().toLowerCase();
  const craftRows = useMemo(() => (craft.data?.flips ?? []).filter((f) => !s || f.name.toLowerCase().includes(s)), [craft.data, s]);
  const timeRows = useMemo(() => (time.data?.flips ?? []).filter((f) => !s || f.name.toLowerCase().includes(s)), [time.data, s]);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("Craft, Forge & Kat flips", "クラフト・フォージ・Katフリップ")}
        description={t(
          "Recipes from NEU-REPO priced with live Bazaar / fair values. Requirements (collections, HOTM) are shown - you need them to craft or forge.",
          "NEU-REPO のレシピを最新のバザー価格・適正価格で計算しています。クラフトやフォージに必要な条件（コレクション・HOTM）も表示します。",
        )}
        actions={craft.data ? <Freshness f={craft.data.freshness} /> : null}
      />
      <Input className="max-w-xs" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t("Search", "検索")} aria-label={t("Search", "検索")} />
      <Tabs defaultValue="craft">
        <TabsList>
          <TabsTrigger value="craft">{t("Craft flips", "クラフトフリップ")}</TabsTrigger>
          <TabsTrigger value="time">{t("Forge & Kat", "フォージ・Kat")}</TabsTrigger>
        </TabsList>
        <TabsContent value="craft">
          {craft.isLoading ? <Loading /> : craft.error ? <ErrorView error={craft.error} /> : craftRows.length === 0 ? <Empty>{t("No profitable crafts right now.", "現在利益の出るクラフトはありません。")}</Empty> : (
            <Card>
              <CardContent className="p-0">
                <CraftTable rows={craftRows} />
              </CardContent>
            </Card>
          )}
        </TabsContent>
        <TabsContent value="time">
          {time.isLoading ? <Loading /> : time.error ? <ErrorView error={time.error} /> : timeRows.length === 0 ? <Empty>{t("No profitable forge or Kat flips right now.", "現在利益の出るフォージ・Katフリップはありません。")}</Empty> : (
            <Card>
              <CardContent className="p-0">
                <TimeTable rows={timeRows} />
              </CardContent>
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
