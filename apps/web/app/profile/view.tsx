"use client";

import { useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis } from "recharts";
import type { ItemSummary, LevelInfo, PlayerAnalysis, ProgressSnapshot } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardHeader, CardTitle, NativeSelect, Progress, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { Empty, ErrorView, Freshness, KnownView, Loading, NeedsAnalysis, PageHeader } from "@/components/common";
import { fmtCoins, fmtDate, fmtNum, rarityClass, titleCase } from "@/lib/format";
import { useApi, useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

function Levels({ rows }: { rows: LevelInfo[] }) {
  return (
    <div className="flex flex-col gap-2">
      {rows.map((l) => (
        <div key={l.id} className="grid grid-cols-[8rem_1fr_6rem] items-center gap-3 text-sm">
          <span>{l.name}</span>
          <Progress value={l.level >= l.maxLevel ? 1 : l.progress} tone={l.level >= l.maxLevel ? "good" : "primary"} />
          <span className="text-right tabular-nums">
            {fmtNum(l.levelWithProgress, 2)} <span className="text-xs text-muted-foreground">/ {l.maxLevel}</span>
          </span>
        </div>
      ))}
    </div>
  );
}

function Items({ items }: { items: ItemSummary[] }) {
  const { t } = useI18n();
  if (items.length === 0) return <span className="text-sm text-muted-foreground">—</span>;
  return (
    <div className="flex flex-col gap-1.5 text-sm">
      {items.map((it, i) => (
        <div key={i} className="flex flex-wrap items-center gap-2">
          <span className={rarityClass(it.rarity)}>{it.name}</span>
          {it.stars > 0 ? <Badge>{"✪".repeat(Math.min(5, it.stars))}{it.masterStars > 0 ? ` +${it.masterStars}` : ""}</Badge> : null}
          {it.recombobulated ? <Badge tone="info">{t("recomb", "リコム")}</Badge> : null}
          {it.estimatedValue !== null ? <span className="ml-auto text-xs text-muted-foreground tabular-nums">{fmtCoins(it.estimatedValue)}</span> : null}
        </div>
      ))}
    </div>
  );
}

const SNAP_METRICS: { key: keyof ProgressSnapshot; en: string; ja: string }[] = [
  { key: "networth", en: "Networth", ja: "資産" },
  { key: "skyblockLevel", en: "SkyBlock level", ja: "SkyBlockレベル" },
  { key: "skillAverage", en: "Skill average", ja: "スキル平均" },
  { key: "magicalPower", en: "Magical Power", ja: "魔力" },
  { key: "catacombs", en: "Catacombs", ja: "カタコンベ" },
  { key: "slayerXpTotal", en: "Slayer XP", ja: "スレイヤーXP" },
  { key: "hotm", en: "HOTM", ja: "HOTM" },
  { key: "mithrilPowderTotal", en: "Mithril powder", ja: "ミスリルパウダー" },
  { key: "gemstonePowderTotal", en: "Gemstone powder", ja: "宝石パウダー" },
  { key: "gardenLevel", en: "Garden level", ja: "ガーデンレベル" },
];

function ProgressChart() {
  const { t, lang } = useI18n();
  const [metric, setMetric] = useState<keyof ProgressSnapshot>("networth");
  const q = useApi<ProgressSnapshot[]>("/api/snapshots");
  const data = (q.data ?? []).filter((s) => s[metric] !== null).map((s) => ({ t: s.takenAtMs, v: s[metric] as number }));
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>{t("Progress over time", "進捗の推移")}</CardTitle>
        <NativeSelect className="w-44" value={metric} onChange={(e) => setMetric(e.target.value as keyof ProgressSnapshot)} aria-label={t("Metric", "指標")}>
          {SNAP_METRICS.map((m) => (
            <option key={m.key} value={m.key}>
              {t(m.en, m.ja)}
            </option>
          ))}
        </NativeSelect>
      </CardHeader>
      <CardContent>
        {q.isLoading ? (
          <Loading rows={1} />
        ) : q.error ? (
          <ErrorView error={q.error} />
        ) : data.length < 2 ? (
          <Empty>{t("A snapshot is saved each time you analyse your profile. Analyse again later to see your progress here.", "プロフィールを分析するたびにスナップショットが保存されます。後でもう一度分析すると、ここに推移が表示されます。")}</Empty>
        ) : (
          <div className="h-64">
            <ResponsiveContainer>
              <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
                <XAxis dataKey="t" type="number" domain={["dataMin", "dataMax"]} tickFormatter={(v: number) => new Date(v).toLocaleDateString(lang === "ja" ? "ja-JP" : "en-US")} stroke="var(--muted-foreground)" fontSize={11} />
                <YAxis tickFormatter={(v: number) => (metric === "networth" ? fmtCoins(v) : fmtNum(v, 1))} stroke="var(--muted-foreground)" fontSize={11} width={60} />
                <ChartTooltip contentStyle={{ background: "var(--popover)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }} labelFormatter={(v) => fmtDate(Number(v), lang)} formatter={(v) => [metric === "networth" ? fmtCoins(Number(v)) : fmtNum(Number(v), 2), ""]} />
                <Line dataKey="v" stroke="var(--chart-1)" strokeWidth={2} dot />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Body({ a }: { a: PlayerAnalysis }) {
  const { t } = useI18n();
  const access = a.apiAccess;
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("API access", "API アクセス")}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {(Object.entries(access) as [keyof typeof access, boolean][]).map(([k, v]) => (
            <Badge key={k} tone={v ? "good" : "warn"}>
              {titleCase(k)}: {v ? t("on", "有効") : t("off", "無効")}
            </Badge>
          ))}
          {Object.values(access).some((v) => !v) ? (
            <p className="w-full text-xs text-muted-foreground">
              {t("Disabled sections are shown as Unknown. Enable them in SkyBlock → Settings → API Settings, then analyse again.", "無効な項目は「不明」と表示されます。SkyBlock の設定 → API Settings で有効にしてから再分析してください。")}
            </p>
          ) : null}
        </CardContent>
      </Card>
      <ProgressChart />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("Skills", "スキル")}</CardTitle>
          </CardHeader>
          <CardContent>
            <KnownView v={a.skills} render={(s) => <Levels rows={s} />} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Slayers", "スレイヤー")}</CardTitle>
          </CardHeader>
          <CardContent>
            <KnownView
              v={a.slayers}
              render={(s) => (
                <div className="flex flex-col gap-3">
                  <Levels rows={s.map((x) => ({ ...x.level, name: x.bossName }))} />
                  <p className="text-xs text-muted-foreground">
                    {s.map((x) => `${x.bossName}: ${x.killsByTier.map((k, i) => `T${i + 1} ${fmtNum(k)}`).join(" / ")}`).join(" · ")}
                  </p>
                </div>
              )}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Dungeons", "ダンジョン")}</CardTitle>
          </CardHeader>
          <CardContent>
            <KnownView
              v={a.dungeons}
              render={(d) => (
                <div className="flex flex-col gap-4">
                  <Levels rows={[d.catacombs, ...d.classes]} />
                  <Table>
                    <THead>
                      <TR>
                        <TH>{t("Floor", "フロア")}</TH>
                        <TH>{t("Completions", "クリア数")}</TH>
                        <TH>{t("Best score", "最高スコア")}</TH>
                      </TR>
                    </THead>
                    <TBody>
                      {d.floors.filter((f) => f.completions > 0).map((f) => (
                        <TR key={f.floor}>
                          <TD>{f.floor}</TD>
                          <TD>{fmtNum(f.completions)}</TD>
                          <TD>{f.bestScore !== null ? fmtNum(f.bestScore) : "—"}</TD>
                        </TR>
                      ))}
                    </TBody>
                  </Table>
                </div>
              )}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Mining", "採掘")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            <KnownView
              v={a.mining}
              render={(m) => (
                <>
                  <div className="flex justify-between">
                    <span>HOTM</span>
                    <KnownView v={m.hotm} render={(h) => <span className="tabular-nums">{fmtNum(h.levelWithProgress, 2)}</span>} />
                  </div>
                  {(
                    [
                      ["Mithril", m.mithrilPowder],
                      ["Gemstone", m.gemstonePowder],
                      ["Glacite", m.glacitePowder],
                    ] as const
                  ).map(([n, p]) => (
                    <div key={n} className="flex justify-between">
                      <span>
                        {n} {t("powder", "パウダー")}
                      </span>
                      <KnownView v={p} render={(x) => <span className="tabular-nums">{fmtNum(x.available)} / {fmtNum(x.total)}</span>} />
                    </div>
                  ))}
                  {m.bestTool ? (
                    <div className="flex justify-between">
                      <span>{t("Best tool", "最良ツール")}</span>
                      <span className={rarityClass(m.bestTool.rarity)}>{m.bestTool.name}</span>
                    </div>
                  ) : null}
                </>
              )}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Gear", "装備")}</CardTitle>
          </CardHeader>
          <CardContent>
            <KnownView
              v={a.gear}
              render={(g) => (
                <div className="flex flex-col gap-4">
                  <div>
                    <div className="mb-1 text-xs text-muted-foreground">{t("Armor", "防具")}</div>
                    <Items items={g.armor} />
                  </div>
                  <div>
                    <div className="mb-1 text-xs text-muted-foreground">{t("Equipment", "装飾品")}</div>
                    <Items items={g.equipment} />
                  </div>
                  <div>
                    <div className="mb-1 text-xs text-muted-foreground">{t("Weapons", "武器")}</div>
                    <Items items={g.weapons.slice(0, 8)} />
                  </div>
                </div>
              )}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Pets", "ペット")}</CardTitle>
          </CardHeader>
          <CardContent>
            <KnownView
              v={a.pets}
              render={(ps) => (
                <div className="flex flex-col gap-1.5 text-sm">
                  {ps.slice(0, 15).map((p, i) => (
                    <div key={i} className="flex items-center justify-between gap-2">
                      <span className={rarityClass(p.tier)}>
                        {p.name} {p.active ? <Badge tone="good">{t("active", "使用中")}</Badge> : null}
                      </span>
                      <span className="text-xs text-muted-foreground tabular-nums">
                        Lv {p.level}/{p.maxLevel}
                        {p.heldItem ? ` · ${p.heldItem}` : ""}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Economy", "経済")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            <div className="flex justify-between">
              <span>{t("Purse", "財布")}</span>
              <span className="tabular-nums">{fmtCoins(a.economy.purse)}</span>
            </div>
            <div className="flex justify-between">
              <span>{t("Bank", "銀行")}</span>
              <KnownView v={a.economy.bank} render={(v) => <span className="tabular-nums">{fmtCoins(v)}</span>} />
            </div>
            <div className="flex justify-between">
              <span>{t("Personal bank", "個人銀行")}</span>
              <KnownView v={a.economy.personalBank} render={(v) => <span className="tabular-nums">{fmtCoins(v)}</span>} />
            </div>
            {Object.entries(a.economy.essence).length > 0 ? (
              <div className="pt-2 text-xs text-muted-foreground">
                {t("Essence", "エッセンス")}: {Object.entries(a.economy.essence).map(([k, v]) => `${titleCase(k)} ${fmtNum(v ?? 0)}`).join(" · ")}
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export function ProfileView() {
  const { t } = useI18n();
  const ws = useWorkspace();
  if (ws.isLoading) return <Loading />;
  if (ws.error) return <ErrorView error={ws.error} />;
  const a = ws.data?.lastAnalysis?.analysis;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={a ? `${a.player.username} · ${a.cuteName}` : t("Profile", "プロフィール")} description={a ? <Freshness f={a.fetched} /> : undefined} />
      {a ? <Body a={a} /> : <NeedsAnalysis />}
    </div>
  );
}
