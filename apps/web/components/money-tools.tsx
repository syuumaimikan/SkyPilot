"use client";
import { useState } from "react";
import Link from "next/link";
import type { MoneyMethodEstimate } from "@skypilot/protocol";
import { Card, CardContent, CardHeader, CardTitle } from "@skypilot/ui";
import { useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { fmtCoins } from "@/lib/format";

export function MoneyTools({ methods }: { methods: MoneyMethodEstimate[] }) {
  const { t } = useI18n(); const { data } = useWorkspace(); const a = data?.lastAnalysis?.analysis;
  const [reserve, setReserve] = useState(0), [target, setTarget] = useState(100e6), [hours, setHours] = useState(2);
  const [buy, setBuy] = useState(0), [sell, setSell] = useState(0), [count, setCount] = useState(1);
  const [tax, setTax] = useState(1.25), [fee, setFee] = useState(0), [minutes, setMinutes] = useState(30);
  const knownBalance = !!a && a.economy.bank.status === "known" && a.economy.personalBank.status === "known";
  const balance = a ? a.economy.purse + (a.economy.bank.status === "known" ? a.economy.bank.value : 0) + (a.economy.personalBank.status === "known" ? a.economy.personalBank.value : 0) : 0;
  const budget = Math.max(0, balance - reserve);
  const candidates = methods.filter((m) => m.requirementsMet && (m.capitalRequired ?? 0) + (m.setupCost ?? 0) <= budget);
  const active = candidates.filter((m) => m.coinsPerHour && m.category !== "Passive" && !["forge_flipping", "kat_flipping", "minions"].includes(m.id));
  const best = [...active].sort((x, y) => (y.coinsPerHour?.low ?? 0) - (x.coinsPerHour?.low ?? 0))[0];
  const daily = (best?.coinsPerHour?.low ?? 0) * hours;
  const required = Math.max(0, target - balance);
  const gross = (sell - buy) * count, net = gross - sell * count * tax / 100 - fee;
  const roi = buy * count + fee > 0 ? net / (buy * count + fee) * 100 : null;
  const field = (en: string, ja: string, value: number, set: (v: number) => void, max = 1e15) => <label className="text-sm">{t(en, ja)}<input type="number" min="0" max={max} step="any" value={value} onChange={(e) => set(Math.max(0, Math.min(max, Number(e.target.value) || 0)))} className="mt-1 w-full rounded border border-border bg-background px-3 py-2" /></label>;
  return <div className="grid gap-4 lg:grid-cols-2"><Card><CardHeader><CardTitle>{t("Keep a reserve & plan your target", "残す元手と目標到達プラン")}</CardTitle></CardHeader><CardContent className="space-y-4">
    <div className="grid grid-cols-3 gap-3">{field("Reserve", "残すコイン", reserve, setReserve)}{field("Target", "目標コイン", target, setTarget)}{field("Hours/day", "時間/日", hours, setHours, 24)}</div>
    <p>{t("Available capital", "使える元手")}: <b>{fmtCoins(budget)}</b></p>
    {!knownBalance && <p className="text-xs text-amber-500">{t("Some balances are unknown; available capital is only the known subtotal.", "一部残高が不明です。使える元手は取得済み残高の小計です。")}</p>}
    <p className="text-sm">{best ? <>{t("Best conservative hourly estimate within this capital", "この元手で可能な保守的な時給が高い候補")}: <Link className="underline" href={best.link ?? "/money"}>{best.name}</Link></> : t("No eligible method with a priced hourly estimate. Reanalyse or check the assumptions.", "実行可能で時給を推定できる候補がありません。再分析・前提条件の確認をしてください。")}</p>
    <p className="text-sm">{t("Target time at the lower estimate", "収益下限での目標到達時間")}: {required === 0 ? t("Already reached", "達成済み") : daily > 0 ? `${Math.ceil(required / daily)} ${t("days", "日")}` : t("Not estimable", "推定不可")}</p>
    <p className="text-xs text-muted-foreground">{t("Assumes unchanged prices and execution at the estimate. Spending, setup purchases and interest are not included here; use the forecast for those. Unknown setup costs require manual checking.", "価格一定・推定どおりの作業を仮定。支出・装備購入・利息はこの目安には含まず、下の資産予測で設定します。初期費用が不明の候補は手動確認が必要です。")}</p>
    <div className="flex flex-wrap gap-3 text-sm">{[["/market/orders","Order tracking","注文・出品管理"],["/market/craft","Craft / Forge / Kat","クラフト・フォージ・Kat"],["/minions","Passive income","放置収入"],["/networth","Sell unused items","不要品の売却候補"],["/daily","Daily tasks","デイリー"]].map(([url,en,ja]) => <Link key={url} href={url} className="underline">{t(en,ja)}</Link>)}</div>
  </CardContent></Card><Card><CardHeader><CardTitle>{t("Trade profit & capital efficiency", "取引の純利益と元手効率")}</CardTitle></CardHeader><CardContent className="space-y-4">
    <div className="grid grid-cols-2 gap-3">{field("Buy / item", "仕入れ/個", buy, setBuy)}{field("Sell / item", "売却/個", sell, setSell)}{field("Quantity", "個数", count, setCount, 1e6)}{field("Sale tax % (manual)", "売却税率%（手動）", tax, setTax, 100)}{field("Other fees (total)", "その他の総手数料", fee, setFee)}{field("Minutes to complete", "完了までの分数", minutes, setMinutes, 1e6)}</div>
    <p>{t("Net profit", "純利益")}: <b className={net < 0 ? "text-red-500" : "text-emerald-500"}>{fmtCoins(net)}</b></p>
    <p className="text-sm">ROI: {roi === null ? "—" : `${roi.toFixed(1)}%`} · {t("Hourly equivalent", "時給換算")}: {minutes > 0 ? fmtCoins(net * 60 / minutes) : "—"}</p>
    <p className="text-sm">{t("Break-even sell / item", "損益分岐の売却単価")}: {count > 0 && tax < 100 ? fmtCoins((buy * count + fee) / (count * (1 - tax / 100))) : "—"}</p>
    <p className="text-xs text-muted-foreground">{t("Use the actual tax, listing fee and fill time shown in-game. The default 1.25% is an editable Bazaar scenario; AH taxes vary. Partial fills, price changes and relisting can reduce profit.", "ゲーム内の税率・出品料・約定時間を入力してください。初期の1.25%は変更可能な Bazaar の仮定値です。AH の税率は条件で変わります。部分約定・価格変動・再出品で利益は減ります。")}</p>
  </CardContent></Card></div>;
}
