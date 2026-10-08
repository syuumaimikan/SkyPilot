"use client";

import { useState } from "react";
import Link from "next/link";
import type { MoneyMethodEstimate } from "@skypilot/protocol";
import { Badge, Card, CardContent, Switch, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { ConfidenceBadge, ErrorView, Loading, PageHeader, RiskBadge, Section } from "@/components/common";
import { EmbeddedReport } from "@/components/report";
import { fmtCoins } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { WealthForecast } from "@/components/wealth-forecast";
import { MoneyTools } from "@/components/money-tools";
import { MoneyLab } from "@/components/money-lab";

export function MoneyView() {
  const { t } = useI18n();
  const [onlyEligible, setOnlyEligible] = useState(false);
  const q = useApi<MoneyMethodEstimate[]>("/api/advisor/money", undefined, { refetchInterval: 60_000 });
  const rows = (q.data ?? []).filter((m) => !onlyEligible || m.requirementsMet);
  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        title={t("Money Making", "金策")}
        description={t(
          "Methods ranked for your profile with live market prices. Rates are estimates with a range, never fixed numbers - check the assumptions.",
          "あなたのプロフィールと最新の市場価格で順位付けした金策です。収益は幅のある推定値で、固定値ではありません。前提条件を確認してください。",
        )}
      />
      <Section
        title={t("Ranking", "ランキング")}
        actions={
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <Switch checked={onlyEligible} onCheckedChange={setOnlyEligible} />
            {t("Only methods I can do now", "今できる方法のみ")}
          </label>
        }
      >
        {q.isLoading ? (
          <Loading />
        ) : q.error ? (
          <ErrorView error={q.error} retry={() => q.refetch()} />
        ) : (
          <Card>
            <CardContent className="p-0">
              <Table>
                <THead>
                  <TR>
                    <TH>#</TH>
                    <TH>{t("Method", "方法")}</TH>
                    <TH>{t("Coins / hour", "コイン/時")}</TH>
                    <TH>{t("Setup / capital", "初期費用・元手")}</TH>
                    <TH>{t("Risk", "リスク")}</TH>
                    <TH>{t("Requirements", "条件")}</TH>
                    <TH>{t("Confidence", "信頼度")}</TH>
                  </TR>
                </THead>
                <TBody>
                  {rows.map((m, i) => (
                    <TR key={m.id}>
                      <TD className="text-muted-foreground">{i + 1}</TD>
                      <TD className="max-w-sm">
                        <div className="font-medium">{m.link ? <Link href={m.link} className="hover:underline">{m.name}</Link> : m.name}</div>
                        <div className="text-xs text-muted-foreground">{m.why}</div>
                      </TD>
                      <TD className="whitespace-nowrap">
                        {m.coinsPerHour ? `${fmtCoins(m.coinsPerHour.low)} – ${fmtCoins(m.coinsPerHour.high)}` : t("Not estimable", "推定不可")}
                        {m.variable ? <Badge className="ml-1">{t("variable", "変動大")}</Badge> : null}
                      </TD>
                      <TD className="whitespace-nowrap text-xs">
                        {m.setupCost !== null ? fmtCoins(m.setupCost) : "—"} / {m.capitalRequired !== null ? fmtCoins(m.capitalRequired) : "—"}
                      </TD>
                      <TD>
                        <RiskBadge r={m.risk} />
                      </TD>
                      <TD className="text-xs">
                        {m.requirementsMet ? (
                          <Badge tone="good">{t("Met", "達成")}</Badge>
                        ) : (
                          <span className="text-amber-600 dark:text-amber-400">{m.missingRequirements.join(", ")}</span>
                        )}
                      </TD>
                      <TD>
                        <ConfidenceBadge c={m.confidence} />
                      </TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </Section>
      <MoneyTools methods={q.data ?? []} />
      <MoneyLab methods={q.data ?? []} />
      <WealthForecast />
      <EmbeddedReport domain="money" />
    </div>
  );
}
