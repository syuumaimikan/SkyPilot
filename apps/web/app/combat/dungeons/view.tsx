"use client";

import type { FloorReadiness } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, Progress, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { ErrorView, Loading, MetricView, PageHeader } from "@/components/common";
import { EmbeddedReport } from "@/components/report";
import { fmtNum, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

export function DungeonsView() {
  const { t } = useI18n();
  const q = useApi<FloorReadiness[]>("/api/advisor/dungeons/floors");
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Dungeons", "ダンジョン")} description={t("Floor readiness from your Catacombs level, class levels, gear and Magical Power, plus upgrade priorities.", "カタコンベレベル・クラスレベル・装備・魔力から各フロアの準備度と、強化の優先順位を表示します。")} />
      <Card>
        <CardHeader>
          <CardTitle>{t("Floor readiness", "フロア準備度")}</CardTitle>
          <CardDescription>{t("Readiness is a SkyPilot heuristic (not a game rule). Entry requirements come from the game.", "準備度はSkyPilotの推定値です（ゲーム仕様ではありません）。入場条件はゲームの仕様に基づきます。")}</CardDescription>
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
                  <TH>{t("Floor", "フロア")}</TH>
                  <TH>{t("Readiness", "準備度")}</TH>
                  <TH>{t("Completions", "クリア数")}</TH>
                  <TH>{t("What is missing", "不足しているもの")}</TH>
                </TR>
              </THead>
              <TBody>
                {(q.data ?? []).map((f) => (
                  <TR key={f.floor}>
                    <TD className="font-medium">
                      {f.floor} {f.recommended ? <Badge tone="primary">{t("recommended", "おすすめ")}</Badge> : null} {!f.unlocked ? <Badge tone="warn">{t("locked", "未解放")}</Badge> : null}
                    </TD>
                    <TD className="w-48">
                      <div className="flex items-center gap-2">
                        <Progress value={f.readiness} tone={f.readiness >= 0.8 ? "good" : f.readiness >= 0.5 ? "warn" : "bad"} />
                        <span className="text-xs tabular-nums">{fmtPct(f.readiness, 0)}</span>
                      </div>
                    </TD>
                    <TD>{fmtNum(f.completions)}</TD>
                    <TD>
                      <div className="flex flex-wrap gap-4">
                        {f.missing.map((m, i) => (
                          <MetricView key={i} m={m} />
                        ))}
                      </div>
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          )}
        </CardContent>
      </Card>
      <EmbeddedReport domain="dungeons" />
    </div>
  );
}
