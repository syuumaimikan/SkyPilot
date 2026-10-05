"use client";

import { useState } from "react";
import Link from "next/link";
import type { CropRate } from "@skypilot/protocol";
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { ErrorView, Loading, PageHeader } from "@/components/common";
import { EmbeddedReport } from "@/components/report";
import { fmtCoins, fmtNum } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

export function FarmingView() {
  const { t } = useI18n();
  const [bpsText, setBpsText] = useState("");
  const [bps, setBps] = useState<number | undefined>(undefined);
  const q = useApi<CropRate[]>("/api/advisor/farming/crops", { bps });
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("Farming", "農業")}
        description={t("Crop → Design → Build guide → Tool → Armor → Pet → Income. Rates use your farming fortune and live Bazaar prices.", "作物 → 設計 → 建築ガイド → ツール → 防具 → ペット → 収入。あなたのファーミング幸運と最新のバザー価格で計算します。")}
        actions={
          <Button asChild variant="outline">
            <Link href="/farming/builder">{t("Open Farm Builder →", "ファームビルダーを開く →")}</Link>
          </Button>
        }
      />
      <Card>
        <CardHeader>
          <CardTitle>{t("Income by crop", "作物別の収入")}</CardTitle>
          <CardDescription>{t("Blocks per second defaults to the game's cap with your uptime; adjust it to match how you actually farm.", "1秒あたりのブロック数はゲームの上限と稼働率から計算します。実際の収穫ペースに合わせて調整してください。")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form
            className="flex items-end gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              const v = Number(bpsText);
              setBps(bpsText.trim() && Number.isFinite(v) && v > 0 ? Math.min(20, v) : undefined);
            }}
          >
            <div className="flex flex-col gap-1">
              <Label htmlFor="bps">{t("Blocks per second", "ブロック/秒")}</Label>
              <Input id="bps" className="w-32" inputMode="decimal" value={bpsText} onChange={(e) => setBpsText(e.target.value)} placeholder="20" />
            </div>
            <Button type="submit" variant="secondary">
              {t("Recalculate", "再計算")}
            </Button>
          </form>
          {q.isLoading ? (
            <Loading />
          ) : q.error ? (
            <ErrorView error={q.error} retry={() => q.refetch()} />
          ) : (
            <Table>
              <THead>
                <TR>
                  <TH>{t("Crop", "作物")}</TH>
                  <TH>{t("Fortune", "幸運")}</TH>
                  <TH>{t("Crops / h", "作物/時")}</TH>
                  <TH>{t("NPC / h", "NPC/時")}</TH>
                  <TH>{t("Bazaar / h", "バザー/時")}</TH>
                  <TH>{t("Best form", "最適な形態")}</TH>
                  <TH>{t("Assumptions", "前提")}</TH>
                </TR>
              </THead>
              <TBody>
                {(q.data ?? []).map((c) => (
                  <TR key={c.crop}>
                    <TD className="font-medium">{c.name}</TD>
                    <TD>{fmtNum(c.fortune)}</TD>
                    <TD>{fmtNum(c.cropsPerHour)}</TD>
                    <TD>{fmtCoins(c.npcCoinsPerHour)}</TD>
                    <TD className="font-medium">{fmtCoins(c.bazaarCoinsPerHour)}</TD>
                    <TD className="text-xs">{c.bestBazaarForm ?? "—"}</TD>
                    <TD className="max-w-xs text-[11px] text-muted-foreground">{c.assumptions.join(" · ")}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
        </CardContent>
      </Card>
      <EmbeddedReport domain="farming" />
    </div>
  );
}
