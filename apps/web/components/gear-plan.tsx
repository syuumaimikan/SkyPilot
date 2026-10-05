"use client";

import { useState } from "react";
import type { GearPlan, UpgradeOption } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, NativeSelect, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { parseCoins } from "./analyze-form";
import { Empty, ErrorView, Loading } from "./common";
import { fmtCoins, fmtNum } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

function UpgradeTable({ rows, muted = false }: { rows: UpgradeOption[]; muted?: boolean }) {
  const { t } = useI18n();
  return (
    <Table>
      <THead>
        <TR>
          <TH>{t("Slot", "部位")}</TH>
          <TH>{t("Upgrade", "アップグレード")}</TH>
          <TH>{t("Tier", "段階")}</TH>
          <TH>{t("Cost", "コスト")}</TH>
          <TH>{t("Gain", "上昇")}</TH>
          <TH>{t("Gain per 1M coins", "100万コインあたり")}</TH>
          <TH>{t("Resale", "売却時")}</TH>
        </TR>
      </THead>
      <TBody>
        {rows.map((u) => (
          <TR key={u.id + u.slot} className={muted ? "opacity-70" : undefined}>
            <TD className="text-xs">{u.slot}</TD>
            <TD>
              <div className="font-medium">
                {u.name} {u.owned ? <Badge tone="good">{t("Owned", "所持")}</Badge> : null}
              </div>
              {u.notes.length > 0 ? <div className="text-[11px] text-muted-foreground">{u.notes.join(" · ")}</div> : null}
            </TD>
            <TD>
              <Badge tone={u.tier === "REQUIRED" ? "bad" : u.tier === "LUXURY" ? "info" : "neutral"}>{u.tier}</Badge>
            </TD>
            <TD>{u.cost !== null ? fmtCoins(u.cost) : t("No price", "価格なし")}</TD>
            <TD>
              +{fmtNum(u.gain, 1)} {u.stat}
            </TD>
            <TD>{u.gainPerMillion !== null ? fmtNum(u.gainPerMillion, 2) : "—"}</TD>
            <TD>{u.resaleValue !== null ? fmtCoins(u.resaleValue) : "—"}</TD>
          </TR>
        ))}
      </TBody>
    </Table>
  );
}

/** Budget-constrained gear optimizer (performance per coin) for a purpose. */
export function GearPlanner({ purposes, defaultPurpose }: { purposes: { id: string; en: string; ja: string }[]; defaultPurpose: string }) {
  const { t } = useI18n();
  const [purpose, setPurpose] = useState(defaultPurpose);
  const [budgetText, setBudgetText] = useState("");
  const budget = budgetText.trim() ? parseCoins(budgetText) : null;
  const q = useApi<GearPlan>("/api/advisor/gear", { purpose, budget: budget ?? undefined });
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Gear optimizer", "装備オプティマイザー")}</CardTitle>
        <CardDescription>{t("Upgrades ranked by stat gain per coin within your budget. Owned items cost nothing; resale value is shown for planning only.", "予算内でコインあたりのステータス上昇が大きい順に並べます。所持品はコスト0として扱い、売却価格は参考表示です。")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="gear-purpose">{t("Purpose", "用途")}</Label>
            <NativeSelect id="gear-purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)}>
              {purposes.map((p) => (
                <option key={p.id} value={p.id}>
                  {t(p.en, p.ja)}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="gear-budget">{t("Budget (blank = your profile budget)", "予算（空欄=プロフィールの予算）")}</Label>
            <Input id="gear-budget" value={budgetText} onChange={(e) => setBudgetText(e.target.value)} placeholder="100m" aria-invalid={budgetText.trim() !== "" && budget === null} />
          </div>
        </div>
        {q.isLoading ? (
          <Loading />
        ) : q.error ? (
          <ErrorView error={q.error} retry={() => q.refetch()} />
        ) : q.data ? (
          <>
            <div className="flex flex-wrap gap-4 text-sm">
              <span>
                {t("Total cost", "合計コスト")}: <b>{fmtCoins(q.data.totalCost)}</b>
              </span>
              {Object.entries(q.data.totalGain).map(([k, v]) => (
                <span key={k}>
                  {k}: <b>+{fmtNum(v ?? 0, 1)}</b>
                </span>
              ))}
            </div>
            {q.data.selected.length === 0 ? <Empty>{t("No affordable upgrades found for this purpose and budget.", "この用途・予算で購入できるアップグレードはありません。")}</Empty> : <UpgradeTable rows={q.data.selected} />}
            {q.data.rejected.length > 0 ? (
              <details>
                <summary className="cursor-pointer text-xs text-muted-foreground">{t(`${q.data.rejected.length} options outside budget or dominated`, `予算外・非効率な選択肢 ${q.data.rejected.length} 件`)}</summary>
                <UpgradeTable rows={q.data.rejected} muted />
              </details>
            ) : null}
            {q.data.notes.length > 0 ? (
              <ul className="list-disc pl-5 text-xs text-muted-foreground">
                {q.data.notes.map((n, i) => (
                  <li key={i}>{n}</li>
                ))}
              </ul>
            ) : null}
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
