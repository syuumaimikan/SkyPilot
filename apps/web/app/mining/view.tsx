"use client";

import { useState } from "react";
import type { HotmPlan, MiningCalcInput, MiningCalcResult, PowderPlan } from "@skypilot/protocol";
import { Alert, Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, NativeSelect, Table, TBody, TD, TH, THead, TR, Tabs, TabsContent, TabsList, TabsTrigger } from "@skypilot/ui";
import { ConfidenceBadge, ErrorView, Freshness, Loading, PageHeader, StatCard } from "@/components/common";
import { GearPlanner } from "@/components/gear-plan";
import { EmbeddedReport } from "@/components/report";
import { fmtCoins, fmtHours, fmtNum } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

function HotmPlanner() {
  const { t } = useI18n();
  const [preset, setPreset] = useState("progression");
  const q = useApi<HotmPlan>("/api/advisor/mining/hotm", { preset });
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="preset">{t("Goal preset", "目的プリセット")}</Label>
          <NativeSelect id="preset" value={preset} onChange={(e) => setPreset(e.target.value)}>
            {(q.data?.presets ?? [{ id: preset, name: preset, description: "" }]).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </NativeSelect>
        </div>
        {q.data ? <p className="max-w-xl text-xs text-muted-foreground">{q.data.presets.find((p) => p.id === preset)?.description}</p> : null}
      </div>
      {q.isLoading ? (
        <Loading />
      ) : q.error ? (
        <ErrorView error={q.error} retry={() => q.refetch()} />
      ) : q.data ? (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="HOTM" value={q.data.hotmLevel === 0 ? t("Locked", "未解放") : q.data.hotmLevel} />
            {Object.entries(q.data.requiredPowder).map(([k, v]) => (
              <StatCard key={k} label={t(`${k} powder still needed`, `${k}パウダー必要量`)} value={fmtNum(v ?? 0)} hint={`${t("available", "所持")} ${fmtNum(q.data!.availablePowder[k] ?? 0)}`} />
            ))}
          </div>
          {q.data.blockedByHotmLevel.length > 0 ? (
            <Alert tone="warn">
              {t("Needs a higher HOTM level first", "先にHOTMレベルが必要")}: {q.data.blockedByHotmLevel.join(", ")}
            </Alert>
          ) : null}
          <Card>
            <CardContent className="p-0">
              <Table>
                <THead>
                  <TR>
                    <TH>#</TH>
                    <TH>{t("Perk", "パーク")}</TH>
                    <TH>{t("Levels", "レベル")}</TH>
                    <TH>{t("Powder cost", "パウダー費用")}</TH>
                    <TH>{t("Why", "理由")}</TH>
                  </TR>
                </THead>
                <TBody>
                  {q.data.upgrades.map((u, i) => (
                    <TR key={`${u.perkId}-${i}`}>
                      <TD className="text-muted-foreground">{i + 1}</TD>
                      <TD className="font-medium">
                        {u.name} {u.affordableNow ? <Badge tone="good">{t("affordable now", "今すぐ可能")}</Badge> : null}
                      </TD>
                      <TD className="tabular-nums">
                        {u.fromLevel} → {u.toLevel}
                      </TD>
                      <TD className="tabular-nums">
                        {fmtNum(u.cost)} {u.powder}
                      </TD>
                      <TD className="text-xs text-muted-foreground">{u.reason}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </CardContent>
          </Card>
          <p className="text-xs text-muted-foreground">{t("Costs are evaluated from the NEU-REPO HOTM cost formulas for every level.", "費用は NEU-REPO の HOTM コスト式から各レベルごとに計算しています。")}</p>
        </>
      ) : null}
    </div>
  );
}

function PowderPlanner() {
  const { t, lang } = useI18n();
  const [targets, setTargets] = useState({ mithril: "", gemstone: "", glacite: "" });
  const [applied, setApplied] = useState(targets);
  const num = (s: string) => (s.trim() && Number.isFinite(Number(s)) ? Number(s) : undefined);
  const q = useApi<PowderPlan[]>("/api/advisor/mining/powder", { mithril: num(applied.mithril), gemstone: num(applied.gemstone), glacite: num(applied.glacite) });
  return (
    <div className="flex flex-col gap-4">
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          setApplied(targets);
        }}
      >
        {(["mithril", "gemstone", "glacite"] as const).map((k) => (
          <div key={k} className="flex flex-col gap-1">
            <Label htmlFor={`pw-${k}`}>{t(`${k} target (blank = HOTM preset)`, `${k} 目標（空欄=HOTMプリセット）`)}</Label>
            <Input id={`pw-${k}`} value={targets[k]} inputMode="numeric" onChange={(e) => setTargets({ ...targets, [k]: e.target.value })} />
          </div>
        ))}
        <Button type="submit">{t("Plan", "計画")}</Button>
      </form>
      {q.isLoading ? (
        <Loading />
      ) : q.error ? (
        <ErrorView error={q.error} retry={() => q.refetch()} />
      ) : (
        <div className="grid gap-4 md:grid-cols-3">
          {(q.data ?? []).map((p) => (
            <Card key={p.powder}>
              <CardHeader>
                <CardTitle>{p.powder}</CardTitle>
                <CardDescription>{p.method}</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t("Current / target", "現在 / 目標")}</span>
                  <span className="tabular-nums">
                    {fmtNum(p.current)} / {fmtNum(p.target)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t("Remaining", "残り")}</span>
                  <span className="tabular-nums">{fmtNum(p.remaining)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t("Rate", "効率")}</span>
                  <span className="tabular-nums">{p.perHour !== null ? `${fmtNum(p.perHour)}/h` : t("Unknown", "不明")}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t("Time", "時間")}</span>
                  <span className="tabular-nums">{fmtHours(p.hoursRemaining, lang)}</span>
                </div>
                {p.assumptions.length > 0 ? (
                  <ul className="mt-1 list-disc pl-4 text-xs text-muted-foreground">
                    {p.assumptions.map((a, i) => (
                      <li key={i}>{a}</li>
                    ))}
                  </ul>
                ) : null}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

const CALC_FIELDS: { key: keyof MiningCalcInput; param: string; en: string; ja: string }[] = [
  { key: "miningSpeed", param: "speed", en: "Mining Speed", ja: "採掘速度" },
  { key: "miningFortune", param: "fortune", en: "Mining Fortune", ja: "採掘幸運" },
  { key: "gemstoneFortune", param: "gemstone_fortune", en: "Gemstone Fortune", ja: "宝石幸運" },
  { key: "dwarvenMetalFortune", param: "dwarven_metal_fortune", en: "Dwarven Metal Fortune", ja: "ドワーフ金属幸運" },
  { key: "blockFortune", param: "block_fortune", en: "Block Fortune", ja: "ブロック幸運" },
  { key: "pristine", param: "pristine", en: "Pristine", ja: "Pristine" },
  { key: "uptime", param: "uptime", en: "Uptime (0-1)", ja: "稼働率 (0-1)" },
];

function Calculator() {
  const { t } = useI18n();
  const [params, setParams] = useState<Record<string, number>>({});
  const [edits, setEdits] = useState<Record<string, string>>({});
  const q = useApi<MiningCalcResult>("/api/advisor/mining/calc", params);
  // Inputs default to the values the server derived from the profile; edits override them.
  const base: Record<string, string> = { fuel: "0" };
  if (q.data) for (const c of CALC_FIELDS) base[c.param] = String(q.data.input[c.key]);
  const form: Record<string, string> = { ...base, ...edits };
  const setForm = (f: Record<string, string>) => setEdits(Object.fromEntries(Object.entries(f).filter(([k, v]) => v !== base[k])));
  const apply = () => {
    const p: Record<string, number> = {};
    for (const [k, v] of Object.entries(form)) if (v.trim() !== "" && Number.isFinite(Number(v))) p[k] = Number(v);
    setParams(p);
  };
  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>{t("Your stats", "あなたのステータス")}</CardTitle>
          <CardDescription>{t("Prefilled from your best mining gear and HOTM perks. Change any value to compare setups.", "所持している最良の採掘装備とHOTMパークから自動入力しています。値を変えて構成を比較できます。")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-3 sm:grid-cols-4"
            onSubmit={(e) => {
              e.preventDefault();
              apply();
            }}
          >
            {CALC_FIELDS.map((c) => (
              <div key={c.param} className="flex flex-col gap-1">
                <Label htmlFor={`mc-${c.param}`}>{t(c.en, c.ja)}</Label>
                <Input id={`mc-${c.param}`} inputMode="decimal" value={form[c.param] ?? ""} onChange={(e) => setForm({ ...form, [c.param]: e.target.value })} />
              </div>
            ))}
            <div className="flex flex-col gap-1">
              <Label htmlFor="mc-fuel">{t("Fuel & consumables per hour (coins)", "燃料・消耗品（コイン/時）")}</Label>
              <Input id="mc-fuel" inputMode="decimal" value={form.fuel ?? ""} onChange={(e) => setForm({ ...form, fuel: e.target.value })} />
            </div>
            <div className="flex items-end sm:col-span-4">
              <Button type="submit">{t("Calculate", "計算")}</Button>
            </div>
          </form>
        </CardContent>
      </Card>
      {q.isLoading ? (
        <Loading />
      ) : q.error ? (
        <ErrorView error={q.error} retry={() => q.refetch()} />
      ) : q.data ? (
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle>{t("Money per hour by target", "対象別の時給")}</CardTitle>
            <Freshness f={q.data.freshness} />
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <THead>
                <TR>
                  <TH>{t("Target", "対象")}</TH>
                  <TH>{t("Ticks / block", "tick/ブロック")}</TH>
                  <TH>{t("Blocks / h", "ブロック/時")}</TH>
                  <TH>{t("Drops / h", "ドロップ/時")}</TH>
                  <TH>{t("NPC / h", "NPC/時")}</TH>
                  <TH>{t("Bazaar / h", "バザー/時")}</TH>
                  <TH>{t("Net / h", "純益/時")}</TH>
                  <TH>{t("Confidence", "信頼度")}</TH>
                </TR>
              </THead>
              <TBody>
                {q.data.targets.map((r) => (
                  <TR key={r.targetId} className={r.requirementsMet ? undefined : "opacity-60"}>
                    <TD>
                      <div className="font-medium">{r.name}</div>
                      {!r.requirementsMet ? <div className="text-[11px] text-amber-600 dark:text-amber-400">{r.requirements.join(", ")}</div> : null}
                      {r.assumptions.length > 0 ? <div className="text-[11px] text-muted-foreground">{r.assumptions.join(" · ")}</div> : null}
                    </TD>
                    <TD>{fmtNum(r.ticksPerBlock)}</TD>
                    <TD>{fmtNum(r.blocksPerHour)}</TD>
                    <TD>
                      {fmtNum(r.dropsPerHour)} <span className="text-xs text-muted-foreground">{r.dropItem}</span>
                    </TD>
                    <TD>{fmtCoins(r.npcCoinsPerHour)}</TD>
                    <TD>{fmtCoins(r.bazaarCoinsPerHour)}</TD>
                    <TD className="font-medium">{fmtCoins(r.effectiveCoinsPerHour)}</TD>
                    <TD>
                      <ConfidenceBadge c={r.confidence} />
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}
      <p className="text-xs text-muted-foreground">
        {t(
          "Route guide: SkyPilot explains where to mine, but never detects hidden blocks or moves for you.",
          "ルートガイド：どこで掘るべきかは説明しますが、隠れたブロックの検出や自動移動は一切行いません。",
        )}
      </p>
    </div>
  );
}

const MINING_PURPOSES = [
  { id: "progression", en: "Progression", ja: "進行" },
  { id: "powder", en: "Powder grinding", ja: "パウダー稼ぎ" },
  { id: "gemstone", en: "Gemstones", ja: "宝石" },
  { id: "commission", en: "Commissions", ja: "コミッション" },
  { id: "money", en: "Money", ja: "金策" },
];

export function MiningView() {
  const { t } = useI18n();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Mining", "採掘")} description={t("HOTM → Powder → Tree → Gear → Drill → Gemstones → Method → Profit.", "HOTM → パウダー → ツリー → 装備 → ドリル → 宝石 → 方法 → 利益。")} />
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">{t("Overview", "概要")}</TabsTrigger>
          <TabsTrigger value="hotm">{t("HOTM planner", "HOTMプランナー")}</TabsTrigger>
          <TabsTrigger value="powder">{t("Powder", "パウダー")}</TabsTrigger>
          <TabsTrigger value="calc">{t("Money calculator", "時給計算")}</TabsTrigger>
          <TabsTrigger value="gear">{t("Gear & drill", "装備・ドリル")}</TabsTrigger>
        </TabsList>
        <TabsContent value="overview">
          <EmbeddedReport domain="mining" />
        </TabsContent>
        <TabsContent value="hotm">
          <HotmPlanner />
        </TabsContent>
        <TabsContent value="powder">
          <PowderPlanner />
        </TabsContent>
        <TabsContent value="calc">
          <Calculator />
        </TabsContent>
        <TabsContent value="gear">
          <GearPlanner purposes={MINING_PURPOSES} defaultPurpose="progression" />
        </TabsContent>
      </Tabs>
    </div>
  );
}
