"use client";
import { useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button, Card, CardContent, CardHeader, CardTitle } from "@skypilot/ui";
import { useWorkspace } from "@/lib/hooks";
import { useApi } from "@/lib/hooks";
import type { MoneyMethodEstimate } from "@skypilot/protocol";
import { useI18n } from "@/lib/i18n";
import { fmtCoins } from "@/lib/format";
import { BANK_TIERS, bankInterest, projectWealth } from "@/lib/wealth";

export function WealthForecast() {
  const { data } = useWorkspace();
  const a = data?.lastAnalysis?.analysis;
  return <Forecast key={a?.profileId ?? "manual"} bank={a?.economy.bank.status === "known" ? a.economy.bank.value : null} purse={a?.economy.purse ?? null} mode={a?.gameMode ?? null} />;
}
function Forecast({ bank, purse, mode }: { bank: number | null; purse: number | null; mode: string | null }) {
  const { t } = useI18n();
  const methods = useApi<MoneyMethodEstimate[]>("/api/advisor/money");
  const [balance, setBalance] = useState(bank ?? 0), [cash, setCash] = useState(purse ?? 0);
  const [tier, setTier] = useState(0), [museum, setMuseum] = useState(0), [days, setDays] = useState(30);
  const [passive, setPassive] = useState(0), [rate, setRate] = useState(0), [hours, setHours] = useState(0);
  const [expenses, setExpenses] = useState(0), [next, setNext] = useState(31), [reinvest, setReinvest] = useState(true), [target, setTarget] = useState(100e6);
  const points = projectWealth({ bank: balance, purse: cash, tier, museum, days, passivePerDay: passive, activePerDay: rate * hours, expensesPerDay: expenses, firstPayoutHours: next, reinvest });
  const last = points[points.length - 1], reached = points.find((p) => p.total >= target);
  const field = (en: string, ja: string, value: number, set: (v: number) => void, max = 1e15) => <label className="space-y-1 text-sm">{t(en, ja)}<input className="w-full rounded border border-border bg-background px-3 py-2" type="number" min="0" max={max} step="any" value={value} onChange={(e) => set(Math.max(0, Math.min(max, Number(e.target.value) || 0)))} /></label>;
  return <Card><CardHeader><CardTitle>{t("Future liquid assets", "将来のコイン資産を予測")}</CardTitle></CardHeader><CardContent className="space-y-5">
    <p className="text-sm text-muted-foreground">{t("Bank interest + passive income + your active earnings − expenses. Bank tier, museum milestone and earnings are manual assumptions; item prices and co-op withdrawals are excluded.", "銀行利息＋放置収入＋手動金策−支出を計算します。銀行ランク・博物館マイルストーン・収益は手動設定です。アイテム価格変動や Co-op の引き出しは含みません。")}</p>
    {mode && <p className="text-xs">{t("Profile mode", "プロフィールのモード")}: {mode} · {t("Check which income methods your mode allows.", "利用可能な金策を確認してください。")}</p>}
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {field("Bank balance", "銀行残高", balance, setBalance)}{field("Purse", "財布", cash, setCash)}
      <label className="text-sm">{t("Bank tier (manual)", "銀行ランク（手動）")}<select className="mt-1 w-full rounded border border-border bg-background p-2" value={tier} onChange={(e) => setTier(Number(e.target.value))}>{BANK_TIERS.map((b, i) => <option key={b.name} value={i}>{b.name}</option>)}</select></label>
      {field("Museum milestone", "博物館マイルストーン", museum, setMuseum, 30)}
      {field("Days", "予測日数", days, setDays, 365)}{field("Passive coins/day", "放置収入/日", passive, setPassive)}
      {field("Active net coins/hour", "手動金策の純利益/時", rate, setRate)}{field("Play hours/day", "プレイ時間/日", hours, setHours, 24)}
      {field("Expenses/day", "支出/日", expenses, setExpenses)}{field("Hours to next interest", "次の利息まで（時間）", next, setNext, 31)}{field("Target coins", "目標コイン", target, setTarget)}
    </div>
    <div className="flex flex-wrap items-center gap-4"><label className="text-sm"><input type="checkbox" checked={reinvest} onChange={(e) => setReinvest(e.target.checked)} /> {t("Deposit earnings and reinvest interest", "収益を預金し利息を再投資")}</label><Button size="sm" variant="outline" disabled={bank === null && purse === null} onClick={() => { setBalance(bank ?? 0); setCash(purse ?? 0); }}>{t("Use profile balances", "プロフィール残高を使用")}</Button></div>
    <label className="block text-sm">{t("Use an eligible method's lower hourly estimate", "実行可能な金策の収益下限を使用")}<select className="ml-2 rounded border border-border bg-background p-2" defaultValue="" onChange={(e) => { const m = methods.data?.find((v) => v.id === e.target.value); if (m?.coinsPerHour) setRate(m.coinsPerHour.low); }}><option value="">{t("Select method", "金策を選択")}</option>{methods.data?.filter((m) => m.requirementsMet && m.coinsPerHour && m.category !== "Passive" && !["forge_flipping", "kat_flipping", "minions"].includes(m.id)).map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}</select></label>
    {bank === null && <p className="text-xs text-amber-500">{t("Bank data unavailable. Enter your balance; zero is a scenario, not a detected balance.", "銀行データを取得できません。残高を入力してください。0 は仮定値です。")}</p>}
    <div className="grid grid-cols-2 gap-3 text-sm"><p>{t("Projected total", "予測合計")}: <b>{fmtCoins(last.total)}</b></p><p>{t("Interest earned", "利息合計")}: <b>{fmtCoins(last.interest)}</b></p><p>{t("Next payout estimate", "次回利息の見積り")}: {fmtCoins(bankInterest(balance, tier, museum))}</p><p>{t("Target reached", "目標到達")}: {reached ? `${Math.ceil(reached.day)} ${t("days", "日後")}` : t("Outside this horizon", "予測期間内では未達")}</p></div>
    <div className="h-72" role="img" aria-label={t("Projected coins over time, with and without interest", "利息あり・なしの資産推移") }><ResponsiveContainer width="100%" height="100%"><LineChart data={points}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="day" type="number" domain={[0, Math.max(1, days)]} tickFormatter={(v) => `${Math.round(v)}d`} /><YAxis tickFormatter={fmtCoins} width={85} /><Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #475569" }} labelStyle={{ color: "#e2e8f0" }} formatter={(v) => typeof v === "number" ? fmtCoins(v) : String(v)} labelFormatter={(v) => `${Number(v).toFixed(1)} ${t("days", "日後")}`} /><Legend /><Line name={t("With interest", "利息あり")} dataKey="total" stroke="#8b5cf6" dot={false} type="stepAfter" isAnimationActive={false} /><Line name={t("Without interest", "利息なし")} dataKey="baseline" stroke="#94a3b8" dot={false} strokeDasharray="4 4" isAnimationActive={false} /></LineChart></ResponsiveContainer></div>
    <details className="text-xs"><summary>{t("Daily values / assumptions", "日ごとの値・前提")}</summary><p className="py-2">{t("31-hour payouts; regular logins assumed. Bank capacity and progressive interest caps are respected. Overflow stays in the purse. Rates may change; confirm at the Banker. This is a scenario, not guaranteed income.", "31時間ごとの利息を毎回受け取るログインを想定。銀行容量と段階利率の上限を反映し、超過分は財布に残します。仕様は変更されるため Banker で確認してください。収益保証ではなく条件付き予測です。")}</p><table className="w-full"><thead><tr><th>{t("Day", "日数")}</th><th>{t("Coins", "コイン")}</th><th>{t("Interest", "利息")}</th></tr></thead><tbody>{points.filter((p) => Number.isInteger(p.day)).map((p) => <tr key={p.day}><td>{p.day}</td><td>{fmtCoins(p.total)}</td><td>{fmtCoins(p.interest)}</td></tr>)}</tbody></table></details>
    <a className="text-xs underline" href="https://hypixelskyblock.minecraft.wiki/w/Banking" target="_blank" rel="noreferrer">{t("Banking reference; archived rates reviewed 2026-10-08", "銀行仕様の参考・保存版利率を2026-10-08に確認")}</a>
  </CardContent></Card>;
}
