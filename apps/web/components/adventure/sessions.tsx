"use client";

import { useState } from "react";
import { Button, Input, Textarea } from "@skypilot/ui";
import { Pencil } from "lucide-react";
import { useI18n } from "@/lib/i18n";
import { categorySummary, limits, periodKey, sessionSummary, type Session } from "@/lib/adventure";
import { fmtCoins, fmtDate, fmtNum } from "@/lib/format";
import { Field, Numeric, Remove, Stat, ToolCard } from "./common";
import { newId, type PlannerStore } from "./store";

export type SessionDraft = { key: string; title: string; minutes: number };
export function SessionPanel({ store, now, draft }: { store: PlannerStore; now: number; draft: SessionDraft | null }) {
  const { t, lang } = useI18n();
  const [title, setTitle] = useState(draft?.title ?? ""), [minutes, setMinutes] = useState(draft?.minutes ?? 30);
  const [category, setCategory] = useState(t("Mining", "採掘")), [income, setIncome] = useState(0), [cost, setCost] = useState(0);
  const [note, setNote] = useState(""), [editing, setEditing] = useState<Session | null>(null);
  const [search, setSearch] = useState("");
  const midnight = new Date(now); midnight.setHours(0, 0, 0, 0);
  const week = new Date(midnight); week.setDate(week.getDate() - (week.getDay() + 6) % 7);
  const today = sessionSummary(store.data.sessions, midnight.getTime(), now), weekly = sessionSummary(store.data.sessions, week.getTime(), now);
  const groups = categorySummary(store.data.sessions.filter(s => s.at <= now));
  const daily = Array.from({ length: 7 }, (_, i) => { const date = new Date(midnight); date.setDate(date.getDate() - (6 - i)); const end = new Date(date); end.setDate(end.getDate() + 1); return { label: date.toLocaleDateString(lang === "ja" ? "ja-JP" : "en-US", { month: "numeric", day: "numeric" }), ...sessionSummary(store.data.sessions, date.getTime(), Math.min(now, end.getTime() - 1)) }; });
  const largest = Math.max(1, ...daily.map(d => Math.abs(d.net)));

  function save() {
    const session: Session = { id: editing?.id ?? newId(), title: title.trim(), category: category.trim(), minutes, income, cost, note, at: editing?.at ?? Date.now() };
    if (store.update(d => ({ ...d, sessions: editing ? d.sessions.map(old => old.id === editing.id ? session : old) : [...d.sessions, session] }))) { setEditing(null); setTitle(""); setNote(""); setIncome(0); setCost(0); }
  }
  return <div className="grid items-start gap-5 lg:grid-cols-2">
    <ToolCard title={t("07–08 · Session journal & net income", "07–08 · プレイ記録・収入と経費")} help={t("Enter measured results including consumables, entry costs and sales fees. Estimates are not actual earnings.", "実測結果を記録します。消耗品・参加費・売却税も経費へ含めてください。見込み利益と実際の収入は分けて入力します。") }>
      <form className="space-y-3" onSubmit={e => { e.preventDefault(); save(); }}><Field label={t("Session title", "記録名")}><Input value={title} maxLength={160} onChange={e => setTitle(e.target.value)} placeholder={t("Today's mining session", "今日の採掘")}/></Field><Field label={t("Activity category", "活動カテゴリ")}><Input value={category} maxLength={60} onChange={e => setCategory(e.target.value)} list="activity-categories"/><datalist id="activity-categories">{[t("Mining", "採掘"), t("Farming", "農業"), t("Dungeons", "ダンジョン"), t("Slayer", "スレイヤー"), t("Fishing", "釣り"), t("Trading", "取引"), t("Other", "その他")].map(name => <option key={name} value={name}/>)}</datalist></Field><div className="grid grid-cols-2 gap-3"><Numeric label={t("Played minutes", "プレイ時間（分）")} value={minutes} onChange={setMinutes} min={1 / 60} max={1440}/><Numeric label={t("Actual income", "実際の収入（コイン）")} value={income} onChange={setIncome}/><Numeric label={t("Total expenses", "経費の合計（コイン）")} value={cost} onChange={setCost}/><Stat title={t("Net for this session", "この記録の純利益")} value={fmtCoins(income - cost)}/></div><Field label={t("Session notes", "記録メモ")}><Textarea value={note} maxLength={2000} onChange={e => setNote(e.target.value)} placeholder={t("Equipment, route, party or drops", "装備・ルート・パーティ・ドロップなど")}/></Field><div className="flex gap-2"><Button type="submit" disabled={!title.trim() || !category.trim() || (!editing && store.data.sessions.length >= limits.sessions)}>{editing ? t("Save changes", "記録を保存") : t("Add record", "プレイ記録を追加")}</Button>{editing && <Button type="button" variant="ghost" onClick={() => { setEditing(null); setTitle(""); setNote(""); }}>{t("Cancel", "キャンセル")}</Button>}</div></form>
      <Input aria-label={t("Search session records", "プレイ記録を検索")} value={search} onChange={e => setSearch(e.target.value)} placeholder={t("Search activity or notes", "活動・メモを検索")}/>
      <div className="max-h-[32rem] space-y-2 overflow-y-auto">{[...store.data.sessions].sort((a, b) => b.at - a.at).filter(s => `${s.title} ${s.category} ${s.note}`.toLowerCase().includes(search.toLowerCase())).map(session => <div key={session.id} className="rounded-xl border border-border p-3"><div className="flex gap-2"><div className="min-w-0 flex-1"><p className="break-words text-sm font-semibold">{session.title}</p><p className="mt-1 text-xs text-muted-foreground">{session.category} · {fmtNum(session.minutes, 1)}{t(" min", "分")} · {fmtDate(session.at, lang)}</p></div><Button variant="ghost" size="icon" className="size-11 shrink-0" aria-label={`${t("Edit record", "記録を編集")}: ${session.title}`} onClick={() => { setEditing(session); setTitle(session.title); setCategory(session.category); setMinutes(session.minutes); setIncome(session.income); setCost(session.cost); setNote(session.note); }}><Pencil className="size-4"/></Button><Remove label={session.title} onClick={() => store.update(d => ({ ...d, sessions: d.sessions.filter(s => s.id !== session.id) }))}/></div><p className={`mt-2 text-lg font-semibold ${session.income - session.cost < 0 ? "text-red-400" : "text-emerald-400"}`}>{fmtCoins(session.income - session.cost)} <span className="text-xs font-normal text-muted-foreground">· {fmtCoins((session.income - session.cost) * 60 / session.minutes)}/{t("h", "時")}</span></p>{session.note && <p className="mt-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">{session.note}</p>}</div>)}{!store.data.sessions.length && <p className="text-sm text-muted-foreground">{t("Your first record will appear here.", "最初のプレイ記録を追加してください。")}</p>}</div>
    </ToolCard>
    <ToolCard title={t("09–10 · Actual hourly rates & weekly review", "09–10 · 活動別の実測時給・週間レポート")} help={t("Calculated from your records only. Comparing different equipment or party conditions requires care.", "自分のプレイ記録だけから集計します。装備・パーティ・条件が違う活動を比べる場合は、記録メモも確認してください。") }>
      <div className="grid grid-cols-2 gap-3"><Stat title={t("Today's net", "今日の純利益")} value={fmtCoins(today.net)}/><Stat title={t("This week's net", "今週の純利益")} value={fmtCoins(weekly.net)}/><Stat title={t("This week's minutes", "今週のプレイ時間（分）")} value={fmtNum(weekly.minutes, 1)}/><Stat title={t("This week's net per hour", "今週の純時給")} value={fmtCoins(weekly.perHour)}/></div>
      <p className="text-xs text-muted-foreground">{t("This week", "今週")}: {periodKey("weekly", now).slice(7)} · {weekly.count}{t(" records", "件の記録")}</p>
      <div className="space-y-3"><h3 className="text-sm font-semibold">{t("Net income over the last 7 days", "過去7日間の純利益")}</h3>{daily.map(day => <div key={day.label} className="grid grid-cols-[3rem_1fr_5rem] items-center gap-2 text-xs"><span>{day.label}</span><div className="h-3 overflow-hidden rounded-full bg-muted"><div className={`h-full rounded-full ${day.net < 0 ? "bg-red-400" : "bg-primary"}`} style={{ width: `${Math.abs(day.net) * 100 / largest}%` }}/></div><span className="text-right tabular-nums">{fmtCoins(day.net)}</span></div>)}</div>
      <div className="space-y-2"><h3 className="text-sm font-semibold">{t("Activity comparison · all records", "活動別の比較 · 全記録")}</h3>{groups.map(group => <div key={group.category} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border p-3"><div className="min-w-0"><p className="break-words text-sm font-medium">{group.category}</p><p className="text-xs text-muted-foreground">{group.count}{t(" records", "件")} · {fmtNum(group.minutes, 1)}{t(" min", "分")}</p></div><span className="text-sm font-semibold tabular-nums">{fmtCoins(group.perHour)}/{t("h", "時")}</span></div>)}</div>
    </ToolCard>
  </div>;
}
