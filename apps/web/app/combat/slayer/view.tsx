"use client";

import { useState } from "react";
import type { SlayerPlan } from "@skypilot/protocol";
import { Alert, Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, Label, NativeSelect, Progress, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { ErrorView, Loading, PageHeader, StatCard } from "@/components/common";
import { EmbeddedReport } from "@/components/report";
import { fmtCoins, fmtHours, fmtNum, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

const SLAYERS: { id: string; en: string; ja: string; max?: number }[] = [
  { id: "zombie", en: "Revenant Horror (Zombie)", ja: "Revenant Horror（ゾンビ）" },
  { id: "spider", en: "Tarantula Broodfather (Spider)", ja: "Tarantula Broodfather（クモ）" },
  { id: "wolf", en: "Sven Packmaster (Wolf)", ja: "Sven Packmaster（オオカミ）" },
  { id: "enderman", en: "Voidgloom Seraph (Enderman)", ja: "Voidgloom Seraph（エンダーマン）" },
  { id: "blaze", en: "Inferno Demonlord (Blaze)", ja: "Inferno Demonlord（ブレイズ）" },
  { id: "vampire", en: "Riftstalker Bloodfiend (Vampire)", ja: "Riftstalker Bloodfiend（ヴァンパイア）", max: 5 },
];

function Plan({ p }: { p: SlayerPlan }) {
  const { t, lang } = useI18n();
  const [tier, setTier] = useState<number>(p.recommendedTier);
  const tierEco = p.tiers.find((x) => x.tier === tier) ?? p.tiers[0];
  return (
    <div className="flex flex-col gap-5">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <StatCard label={t("Level", "レベル")} value={`${p.currentLevel} → ${p.targetLevel}`} hint={`${fmtNum(p.currentXp)} / ${fmtNum(p.targetXp)} XP`} />
        <StatCard label={t("XP remaining", "残りXP")} value={fmtNum(p.xpRemaining)} />
        <StatCard label={t("Recommended tier", "推奨ティア")} value={`T${p.recommendedTier}`} hint={p.highestPracticalTier !== null ? t(`highest practical: T${p.highestPracticalTier}`, `実用上の最高: T${p.highestPracticalTier}`) : undefined} />
        <StatCard label={t("Bosses needed", "必要討伐数")} value={p.bossesNeeded !== null ? fmtNum(p.bossesNeeded) : "—"} />
        <StatCard label={t("Estimated time", "推定時間")} value={p.estimatedHours ? `${fmtHours(p.estimatedHours.low, lang)} – ${fmtHours(p.estimatedHours.high, lang)}` : "—"} />
        <StatCard label={t("Spawn cost / drop EV", "召喚費用 / ドロップ期待値")} value={`${fmtCoins(p.estimatedCost)} / ${fmtCoins(p.expectedDropValue)}`} hint={t("Expected value, not guaranteed", "期待値であり保証ではありません")} />
      </div>
      {p.projection ? (
        <Card>
          <CardHeader>
            <CardTitle>{t(`Long-goal outlook: move up to T${p.projection.tier}`, `長期の見通し: T${p.projection.tier}へ移行`)}</CardTitle>
            <CardDescription>
              {t(
                "The figures above use your current gear. With Recommended gear (see below) you unlock the higher tiers with one kill each and earn most of the XP there:",
                "上の数値は現在の装備での計算です。推奨装備（下記）に揃えると、上位ティアを1体ずつ倒して解放し、XPの大半をそこで稼げます:",
              )}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              {p.projection.stages.map((s, i) => (
                <span key={s.tier} className="flex items-center gap-2">
                  {i > 0 ? <span className="text-muted-foreground">→</span> : null}
                  <Badge tone={s.tier === p.projection!.tier ? "primary" : "neutral"}>
                    T{s.tier} × {fmtNum(s.bosses)}
                  </Badge>
                </span>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label={t("Bosses", "討伐数")} value={fmtNum(p.projection.bosses)} />
              <StatCard label={t("Estimated time", "推定時間")} value={p.projection.hours ? `${fmtHours(p.projection.hours.low, lang)} – ${fmtHours(p.projection.hours.high, lang)}` : "—"} />
              <StatCard label={t("Spawn cost", "召喚費用")} value={fmtCoins(p.projection.cost)} />
              <StatCard label={t("Drop EV", "ドロップ期待値")} value={fmtCoins(p.projection.expectedDropValue)} hint={t("Expected value, not guaranteed", "期待値であり保証ではありません")} />
            </div>
          </CardContent>
        </Card>
      ) : null}
      <Card>
        <CardHeader>
          <CardTitle>{t("Tier economics", "ティア別の収支")}</CardTitle>
          <CardDescription>{t("Expected values use NEU-REPO drop chances and live prices. Rare drops are probabilistic - profit is never guaranteed.", "ドロップ期待値は NEU-REPO のドロップ率と最新価格で計算しています。レアドロップは確率であり、利益は保証されません。")}</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <THead>
              <TR>
                <TH>{t("Tier", "ティア")}</TH>
                <TH>{t("Spawn cost", "召喚費用")}</TH>
                <TH>XP</TH>
                <TH>{t("Drop EV", "ドロップ期待値")}</TH>
                <TH>{t("Net / boss", "純益/体")}</TH>
                <TH>{t("Bosses / h", "討伐/時")}</TH>
                <TH>{t("XP / h", "XP/時")}</TH>
                <TH>{t("Net / h", "純益/時")}</TH>
                <TH>{t("Readiness", "準備度")}</TH>
              </TR>
            </THead>
            <TBody>
              {p.tiers.map((x) => (
                <TR key={x.tier} className={x.tier === tier ? "bg-primary/5" : "cursor-pointer"} onClick={() => setTier(x.tier)}>
                  <TD>
                    T{x.tier} {x.tier === p.recommendedTier ? <Badge tone="primary">{t("best", "推奨")}</Badge> : null} {!x.unlocked ? <Badge tone="warn">{t("locked", "未解放")}</Badge> : null}
                  </TD>
                  <TD>{fmtCoins(x.bossCost)}</TD>
                  <TD>{fmtNum(x.xpPerBoss)}</TD>
                  <TD>{fmtCoins(x.expectedDropValue)}</TD>
                  <TD className={x.expectedNetPerBoss !== null && x.expectedNetPerBoss < 0 ? "text-red-500" : ""}>{fmtCoins(x.expectedNetPerBoss)}</TD>
                  <TD>{x.bossesPerHour !== null ? fmtNum(x.bossesPerHour, 1) : "—"}</TD>
                  <TD>{x.xpPerHour !== null ? fmtNum(x.xpPerHour) : "—"}</TD>
                  <TD>{fmtCoins(x.netPerHour)}</TD>
                  <TD className="w-32">{x.readiness !== null ? <Progress value={x.readiness} tone={x.readiness >= 0.8 ? "good" : x.readiness >= 0.5 ? "warn" : "bad"} /> : "—"}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </CardContent>
      </Card>
      {tierEco && tierEco.drops.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>{t(`Drops at T${tierEco.tier}`, `T${tierEco.tier} のドロップ`)}</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <THead>
                <TR>
                  <TH>{t("Item", "アイテム")}</TH>
                  <TH>{t("Chance", "確率")}</TH>
                  <TH>{t("Amount", "数量")}</TH>
                  <TH>{t("Unit value", "単価")}</TH>
                  <TH>{t("EV / boss", "期待値/体")}</TH>
                </TR>
              </THead>
              <TBody>
                {tierEco.drops.map((d) => (
                  <TR key={d.itemId}>
                    <TD>
                      {d.name} {d.rngMeterEligible ? <Badge tone="info">RNG meter</Badge> : null}
                    </TD>
                    <TD>{d.chance >= 0.01 ? fmtPct(d.chance, 2) : d.chance > 0 ? `1/${fmtNum(Math.round(1 / d.chance))}` : "—"}</TD>
                    <TD>{fmtNum(d.amount, 1)}</TD>
                    <TD>{d.unitValue !== null ? fmtCoins(d.unitValue) : t("No price", "価格なし")}</TD>
                    <TD>{fmtCoins(d.expectedValue)}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}
      <Card>
        <CardHeader>
          <CardTitle>{t("Gear by tier", "段階別の装備")}</CardTitle>
          <CardDescription>{t("Required → Recommended → Luxury. Required means you will likely fail without it; the rest improves speed.", "必須 → 推奨 → 贅沢。必須がないと失敗しやすく、それ以外は速度を上げます。")}</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <THead>
              <TR>
                <TH>{t("Slot", "部位")}</TH>
                <TH>{t("Tier", "段階")}</TH>
                <TH>{t("Items", "アイテム")}</TH>
                <TH>{t("Owned", "所持")}</TH>
                <TH>{t("Price", "価格")}</TH>
              </TR>
            </THead>
            <TBody>
              {p.gear.map((g, i) => (
                <TR key={i}>
                  <TD>{g.slot}</TD>
                  <TD>
                    <Badge tone={g.tier === "REQUIRED" ? "bad" : g.tier === "LUXURY" ? "info" : "neutral"}>{g.tier === "REQUIRED" ? t("Required", "必須") : g.tier === "LUXURY" ? t("Luxury", "贅沢") : t("Recommended", "推奨")}</Badge>
                  </TD>
                  <TD>{g.items.join(" / ")}</TD>
                  <TD>{g.owned ? <Badge tone="good">✓</Badge> : <Badge>—</Badge>}</TD>
                  <TD>{fmtCoins(g.price)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </CardContent>
      </Card>
      {Object.keys(p.readiness).length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("Readiness", "準備度")}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 sm:grid-cols-2">
            {Object.entries(p.readiness).map(([k, v]) => (
              <div key={k} className="grid grid-cols-[10rem_1fr_3rem] items-center gap-2 text-xs">
                <span>{k}</span>
                <Progress value={v ?? 0} tone={(v ?? 0) >= 0.8 ? "good" : (v ?? 0) >= 0.5 ? "warn" : "bad"} />
                <span className="text-right tabular-nums">{fmtPct(v ?? 0, 0)}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      ) : null}
      {p.notes.length > 0 ? (
        <Alert>
          <ul className="list-disc pl-5">
            {p.notes.map((n, i) => (
              <li key={i}>{n}</li>
            ))}
          </ul>
        </Alert>
      ) : null}
    </div>
  );
}

export function SlayerView() {
  const { t } = useI18n();
  const [slayer, setSlayer] = useState("enderman");
  const [target, setTarget] = useState<number | null>(null);
  const q = useApi<SlayerPlan>("/api/advisor/slayer/plan", { slayer, target: target ?? undefined });
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Slayer", "スレイヤー")} description={t("Plan any slayer level: XP, cost, best tier, expected drops and the gear you need.", "スレイヤーのレベル計画：必要XP・費用・最適ティア・期待ドロップ・必要装備。")} />
      <div className="flex flex-wrap gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="slayer">{t("Slayer", "スレイヤー")}</Label>
          <NativeSelect
            id="slayer"
            value={slayer}
            onChange={(e) => {
              setSlayer(e.target.value);
              setTarget(null);
            }}
          >
            {SLAYERS.map((s) => (
              <option key={s.id} value={s.id}>
                {t(s.en, s.ja)}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="target">{t("Target level", "目標レベル")}</Label>
          <NativeSelect id="target" value={target ?? q.data?.targetLevel ?? ""} onChange={(e) => setTarget(Number(e.target.value))}>
            {Array.from({ length: SLAYERS.find((x) => x.id === slayer)?.max ?? 9 }, (_, i) => i + 1).map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </NativeSelect>
        </div>
      </div>
      {q.isLoading ? <Loading rows={4} /> : q.error ? <ErrorView error={q.error} retry={() => q.refetch()} /> : q.data ? <Plan key={`${q.data.slayerId}-${q.data.targetLevel}`} p={q.data} /> : null}
      <EmbeddedReport domain="slayer" />
    </div>
  );
}
