"use client";

import type { FloorReadiness, ModPuzzle } from "@skypilot/protocol";
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
      <Puzzles />
      <EmbeddedReport domain="dungeons" />
    </div>
  );
}

/** What each puzzle room asks for (curated from the wiki). Rules only - SkyPilot has no puzzle solver. */
function Puzzles() {
  const { t } = useI18n();
  const q = useApi<ModPuzzle[]>("/api/mod/puzzles");
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Puzzle rooms", "パズル部屋")}</CardTitle>
        <CardDescription>
          {t(
            "The rules of each Catacombs puzzle, in short. SkyPilot never reads a room to work out its answer and never acts for you; the mod shows these rules next to the tab list's puzzle list.",
            "カタコンベの各パズルのルール概要です。SkyPilotが部屋を読み取って答えを出したり、代わりに操作したりすることはありません。MODではタブリストのパズル一覧の横にこのルールを表示します。",
          )}
        </CardDescription>
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
                <TH>{t("Puzzle", "パズル")}</TH>
                <TH>{t("Floors", "フロア")}</TH>
                <TH>{t("What to do", "やること")}</TH>
                <TH>{t("Can fail", "失敗あり")}</TH>
              </TR>
            </THead>
            <TBody>
              {(q.data ?? []).map((p) => (
                <TR key={p.name}>
                  <TD className="font-medium">{p.name}</TD>
                  <TD>{p.floors === "all" ? t("all", "全て") : p.floors}</TD>
                  <TD className="text-sm">{p.rule}</TD>
                  <TD>{p.canFail ? <Badge tone="warn">{t("yes (-14 Skill score)", "あり（スキルスコア-14）")}</Badge> : <Badge tone="good">{t("no", "なし")}</Badge>}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {q.data?.[0] ? (
          <p className="px-5 py-3 text-xs text-muted-foreground">
            {t("Source: ", "出典: ")}
            <a className="underline" href={q.data[0].source} target="_blank" rel="noreferrer">
              Hypixel SkyBlock Wiki
            </a>
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
