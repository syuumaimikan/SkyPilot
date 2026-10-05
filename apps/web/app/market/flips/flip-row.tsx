"use client";

import { Fragment } from "react";
import type { FlipOpportunity } from "@skypilot/protocol";
import { Badge, TD, TR, cn } from "@skypilot/ui";
import { ConfidenceBadge, CopyButton, RiskBadge } from "@/components/common";
import { fmtCoins, fmtMinutesRange, fmtNum, fmtPct, rarityClass } from "@/lib/format";
import { useI18n } from "@/lib/i18n";

/** The full decision chain for one flip: Buy → Max buy → Fair value → Sell → Fees → Profit → Sell time → Confidence → Risk. */
export function FlipBreakdown({ f }: { f: FlipOpportunity }) {
  const { t, lang } = useI18n();
  const steps: [string, React.ReactNode][] = [
    [t("1. Buy (BIN)", "1. 購入価格 (BIN)"), fmtCoins(f.buyPrice)],
    [t("2. Max buy price", "2. 最大購入価格"), fmtCoins(f.maxBuyPrice)],
    [t("3. Fair value", "3. 適正価格"), `${fmtCoins(f.fairValue)} (${f.comparables} ${t("comparables", "比較対象")})`],
    [t("4. Recommended sell", "4. 推奨売値"), fmtCoins(f.recommendedSell)],
    [t("5. AH fees", "5. AH手数料"), `${fmtCoins(f.fees.total)} (${t("listing", "出品")} ${fmtCoins(f.fees.listingFee)} + ${t("duration", "期間")} ${fmtCoins(f.fees.durationFee)} + ${t("claim tax", "受取税")} ${fmtCoins(f.fees.claimTax)})`],
    [t("6. Expected net profit", "6. 期待純利益"), `${fmtCoins(f.expectedNetProfit)} (ROI ${fmtPct(f.roi)})`],
    [t("7. Expected sell time", "7. 想定売却時間"), `${fmtMinutesRange(f.expectedSellMinutes, lang)} · P(sale) ${fmtPct(f.probabilityOfSale, 0)}`],
    [t("8. Confidence", "8. 信頼度"), <ConfidenceBadge key="c" c={f.confidence} />],
    [t("9. Risk", "9. リスク"), <RiskBadge key="r" r={f.risk} />],
  ];
  return (
    <div className="grid gap-4 p-4 md:grid-cols-[1fr_1fr]">
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
        {steps.map(([k, v]) => (
          <Fragment key={k}>
            <dt className="text-muted-foreground">{k}</dt>
            <dd className="tabular-nums">{v}</dd>
          </Fragment>
        ))}
      </dl>
      <div className="flex flex-col gap-3 text-sm">
        {f.manipulation.flags.length > 0 ? (
          <div>
            <div className="mb-1 text-xs text-muted-foreground">{t("Market checks", "市場チェック")}</div>
            <div className="flex flex-wrap gap-1">
              {f.manipulation.flags.map((x) => (
                <Badge key={x} tone={f.manipulation.suspicious ? "bad" : "warn"}>
                  {x}
                </Badge>
              ))}
            </div>
          </div>
        ) : null}
        <div className="text-xs text-muted-foreground">
          {t("Price key", "価格キー")}: <code className="font-mono">{f.priceKey}</code>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <code className="rounded bg-muted px-2 py-1 font-mono text-xs">{f.viewCommand}</code>
          <CopyButton text={f.viewCommand} />
        </div>
        <p className="text-xs text-muted-foreground">
          {t(
            "SkyPilot never buys or lists anything. Type the command in game yourself and re-check the price before buying.",
            "SkyPilot は購入・出品を一切行いません。ゲーム内で自分でコマンドを入力し、購入前に価格を再確認してください。",
          )}
        </p>
      </div>
    </div>
  );
}

export function FlipRow({ f, open, onToggle }: { f: FlipOpportunity; open: boolean; onToggle: () => void }) {
  const { t, lang } = useI18n();
  const blocked = f.manipulation.suspicious;
  return (
    <>
      <TR className={cn("cursor-pointer", blocked && "opacity-70")} onClick={onToggle} aria-expanded={open}>
        <TD className="max-w-xs">
          <div className={cn("truncate font-medium", rarityClass(f.rarity))}>{f.itemName}</div>
          {f.modifiers.length > 0 ? <div className="truncate text-[11px] text-muted-foreground">{f.modifiers.join(" · ")}</div> : null}
        </TD>
        <TD>{fmtCoins(f.buyPrice)}</TD>
        <TD className="text-muted-foreground">{fmtCoins(f.maxBuyPrice)}</TD>
        <TD>{fmtCoins(f.fairValue)}</TD>
        <TD className={f.expectedNetProfit > 0 ? "text-emerald-600 dark:text-emerald-400" : "text-red-500"}>{fmtCoins(f.expectedNetProfit)}</TD>
        <TD>{fmtPct(f.roi)}</TD>
        <TD className="whitespace-nowrap text-xs">{fmtMinutesRange(f.expectedSellMinutes, lang)}</TD>
        <TD>
          <ConfidenceBadge c={f.confidence} />
        </TD>
        <TD>{blocked ? <Badge tone="bad">{t("DO NOT FLIP", "フリップ禁止")}</Badge> : <RiskBadge r={f.risk} />}</TD>
        <TD className="text-right text-muted-foreground">{fmtNum(f.score, 2)}</TD>
      </TR>
      {open ? (
        <tr className="border-b border-border bg-muted/30">
          <td colSpan={10}>
            {blocked ? <div className="px-4 pt-3 text-sm font-semibold text-red-600 dark:text-red-400">{f.manipulation.verdict}</div> : null}
            <FlipBreakdown f={f} />
          </td>
        </tr>
      ) : null}
    </>
  );
}
