"use client";

import type { IngredientCost } from "@skypilot/protocol";
import { Badge } from "@skypilot/ui";
import { fmtCoins, fmtNum } from "@/lib/format";
import { useI18n } from "@/lib/i18n";

export function IngredientTree({ items, depth = 0 }: { items: IngredientCost[]; depth?: number }) {
  const { t } = useI18n();
  return (
    <ul className={depth === 0 ? "flex flex-col gap-1" : "mt-1 flex flex-col gap-1 border-l border-border pl-3"}>
      {items.map((i, idx) => (
        <li key={`${i.itemId}-${idx}`} className="text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="tabular-nums text-muted-foreground">{fmtNum(i.amount, 2)}×</span>
            <span>{i.name}</span>
            <Badge tone={i.source === "Unknown" ? "warn" : "neutral"}>{i.source === "Unknown" ? t("No price", "価格なし") : i.source}</Badge>
            <span className="ml-auto tabular-nums text-muted-foreground">
              {i.unitPrice !== null ? `${fmtCoins(i.unitPrice)} ${t("each", "/個")}` : ""} {i.total !== null ? `= ${fmtCoins(i.total)}` : ""}
            </span>
          </div>
          {i.children.length > 0 ? <IngredientTree items={i.children} depth={depth + 1} /> : null}
        </li>
      ))}
    </ul>
  );
}
