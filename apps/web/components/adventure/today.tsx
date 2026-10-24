"use client";

import { useState } from "react";
import { Button, Input, NativeSelect, Progress } from "@skypilot/ui";
import { CheckCheck, Pause, Play, Plus, Pencil } from "lucide-react";
import type { RecommendedAction } from "@skypilot/protocol";
import { useI18n } from "@/lib/i18n";
import { focusRemaining, isDone, limits, periodKey, taskPlan, type Repeat, type Task } from "@/lib/adventure";
import { Field, Numeric, Remove, Stat, ToolCard } from "./common";
import { newId, type PlannerStore } from "./store";

export function TodayPanel({ store, now, actions, record }: { store: PlannerStore; now: number; actions: RecommendedAction[]; record: (title: string, minutes: number) => void }) {
  const { t } = useI18n();
  const [title, setTitle] = useState(""); const [minutes, setMinutes] = useState(15);
  const [repeat, setRepeat] = useState<Repeat>("daily"); const [priority, setPriority] = useState(2);
  const [editing, setEditing] = useState<string | null>(null); const [budget, setBudget] = useState(60);
  const [focusTitle, setFocusTitle] = useState(""); const [focusMinutes, setFocusMinutes] = useState(25);
  const [filter, setFilter] = useState("all");
  const data = store.data, plan = taskPlan(data.tasks, budget, now);
  const done = data.tasks.filter(task => isDone(task, now)).length;
  const remaining = focusRemaining(data.focus, now);
  const seconds = Math.ceil(remaining / 1000);
  const display = `${Math.floor(seconds / 3600) ? `${Math.floor(seconds / 3600)}:` : ""}${String(Math.floor(seconds / 60) % 60).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;

  function saveTask() {
    if (!title.trim()) return;
    const task: Task = { id: editing ?? newId(), title: title.trim(), minutes, priority, repeat, completed: editing ? data.tasks.find(task => task.id === editing)?.completed ?? null : null };
    if (store.update(d => ({ ...d, tasks: editing ? d.tasks.map(old => old.id === editing ? task : old) : [...d.tasks, task] }))) { setTitle(""); setEditing(null); }
  }
  function addRecommendations() {
    store.update(d => ({ ...d, tasks: [...d.tasks, ...actions.filter(action => !d.tasks.some(task => task.title === action.title)).slice(0, Math.min(10, limits.tasks - d.tasks.length)).map(action => ({ id: newId(), title: action.title.slice(0, 160), minutes: Math.min(1440, Math.max(1, Math.ceil((action.estimatedHours ?? 0.25) * 60))), priority: 2, repeat: "once" as const, completed: null }))] }));
  }

  return <div className="grid items-start gap-5 lg:grid-cols-2">
    <ToolCard title={t("01–04 · Daily, weekly & session plan", "01–04 · 日課・週課・今日の時間配分")} help={t("Your own checklist. Daily resets at device midnight; weekly resets on Monday. These are not Hypixel reset times.", "自分用のチェックリスト。日課は端末の日付変更、週課は月曜にリセットします。Hypixelのリセット時刻とは別です。") }>
      <div className="grid grid-cols-2 gap-3"><Stat title={t("Completed", "チェック済み")} value={`${done} / ${data.tasks.length}`}/><Stat title={t("Fits today's budget", "今日の枠に収まる予定")} value={`${plan.selected.length} ${t("tasks", "件")}`}/></div>
      <Progress value={data.tasks.length ? done * 100 / data.tasks.length : 0} aria-label={t("Checklist completion", "チェックリストの完了率")}/>
      <div className="flex flex-wrap gap-2"><Button variant="outline" size="sm" onClick={() => store.update(d => ({ ...d, tasks: [...d.tasks, ...[t("Check market prices", "相場を確認"), t("Update goal progress", "目標の進捗更新"), t("Record today's session", "今日のプレイを記録")].filter(name => !d.tasks.some(task => task.title === name)).map(name => ({ id: newId(), title: name, minutes: 5, priority: 2, repeat: "daily" as const, completed: null }))] }))}><Plus/>{t("Starter checklist", "日課テンプレート")}</Button><Button variant="outline" size="sm" disabled={!actions.length} onClick={addRecommendations}>{t("Import up to 10 analysis actions", "分析の推奨を10件まで取り込む")}</Button></div>
      <form className="space-y-3 rounded-2xl border border-border p-3" onSubmit={event => { event.preventDefault(); saveTask(); }}>
        <Field label={t("Task title", "タスク名")}><Input maxLength={160} value={title} onChange={e => setTitle(e.target.value)} placeholder={t("My next step", "今日やること")}/></Field>
        <div className="grid grid-cols-2 gap-3"><Numeric label={t("Estimated minutes", "予定時間（分）")} value={minutes} onChange={setMinutes} min={1} max={1440}/><Field label={t("Repeat", "繰り返し")}><NativeSelect value={repeat} onChange={e => setRepeat(e.target.value as Repeat)}><option value="once">{t("One-time", "単発")}</option><option value="daily">{t("Daily", "毎日")}</option><option value="weekly">{t("Weekly", "毎週")}</option></NativeSelect></Field><Field label={t("Priority", "優先度")}><NativeSelect value={priority} onChange={e => setPriority(Number(e.target.value))}><option value={3}>{t("High", "高")}</option><option value={2}>{t("Medium", "中")}</option><option value={1}>{t("Low", "低")}</option></NativeSelect></Field></div>
        <div className="flex gap-2"><Button type="submit" disabled={!title.trim() || (!editing && data.tasks.length >= limits.tasks)}>{editing ? t("Save task", "タスクを保存") : t("Add task", "タスクを追加")}</Button>{editing && <Button type="button" variant="ghost" onClick={() => { setEditing(null); setTitle(""); }}>{t("Cancel", "キャンセル")}</Button>}</div>
      </form>
      <Field label={t("Checklist filter", "タスクの表示")}><NativeSelect value={filter} onChange={e => setFilter(e.target.value)}><option value="all">{t("All", "すべて")}</option><option value="pending">{t("Incomplete", "未完了")}</option><option value="daily">{t("Daily", "日課")}</option><option value="weekly">{t("Weekly", "週課")}</option><option value="once">{t("One-time", "単発")}</option></NativeSelect></Field>
      <div className="space-y-2">{data.tasks.filter(task => filter === "all" || (filter === "pending" ? !isDone(task, now) : task.repeat === filter)).map(task => <div key={task.id} className="flex items-start gap-2 rounded-xl border border-border p-2"><label className="flex min-w-0 flex-1 cursor-pointer gap-3 py-2"><input type="checkbox" className="mt-1 size-4 shrink-0 accent-primary" checked={isDone(task, now)} onChange={() => store.update(d => ({ ...d, tasks: d.tasks.map(old => old.id === task.id ? { ...old, completed: isDone(old, now) ? null : periodKey(old.repeat, now) } : old) }))}/><span className="min-w-0"><span className={`block break-words text-sm ${isDone(task, now) ? "text-muted-foreground line-through" : ""}`}>{task.title}</span><span className="mt-1 block text-xs text-muted-foreground">{task.minutes} {t("min", "分")} · {task.repeat === "daily" ? t("Daily", "日課") : task.repeat === "weekly" ? t("Weekly", "週課") : t("Once", "単発")} · {t("Priority", "優先度")} {task.priority}</span></span></label><Button variant="ghost" size="icon" className="size-11 shrink-0" aria-label={`${t("Edit", "編集")}: ${task.title}`} onClick={() => { setEditing(task.id); setTitle(task.title); setMinutes(task.minutes); setPriority(task.priority); setRepeat(task.repeat); }}><Pencil className="size-4"/></Button><Remove label={task.title} onClick={() => store.update(d => ({ ...d, tasks: d.tasks.filter(old => old.id !== task.id) }))}/></div>)}{!data.tasks.length && <p className="text-sm text-muted-foreground">{t("Add tasks or a starter checklist.", "タスクや日課テンプレートを追加してください。")}</p>}</div>
      <Numeric label={t("Available play minutes", "今日プレイできる時間（分）")} value={budget} onChange={setBudget} min={0} max={1440}/>
      <div className="rounded-2xl bg-primary/10 p-4"><p className="mb-2 text-sm font-semibold">{t("Suggested order", "優先度から組んだ予定")} · {plan.used}/{budget} {t("min", "分")}</p><ol className="list-decimal space-y-2 pl-5 text-sm">{plan.selected.map(task => <li key={task.id} className="break-words">{task.title} · {task.minutes}{t(" min", "分")}</li>)}</ol><p className="mt-3 text-xs text-muted-foreground">{t("Deferred", "今回の枠外")}: {plan.deferred.length} · {t("Spare minutes", "余り時間")}: {plan.remaining}</p></div>
    </ToolCard>
    <ToolCard title={t("05–06 · Focus timer & resume", "05–06 · 集中タイマー・一時停止と再開")} help={t("A saved countdown continues after reload. Alerts are shown while this page is open, not as background OS notifications.", "タイマーは再読み込み後も継続します。完了表示はこのページを開いている間に確認でき、OSのバックグラウンド通知は行いません。") }>
      <div className="rounded-3xl border border-primary/20 bg-gradient-to-br from-primary/15 to-cyan-500/10 p-6 text-center"><p className="break-words text-sm text-muted-foreground">{data.focus?.title ?? t("One step at a time", "次の一歩に集中")}</p><p role="timer" aria-label={t("Focus time remaining", "集中タイマーの残り時間")} className="my-5 text-5xl font-semibold tabular-nums">{data.focus ? display : "25:00"}</p>{data.focus && remaining === 0 ? <p role="status" className="text-sm text-emerald-400"><CheckCheck className="mr-2 inline size-5"/>{t("Time complete. Record your results.", "時間になりました。プレイ結果を記録しましょう。")}</p> : <p className="text-xs text-muted-foreground">{data.focus?.end ? t("Running", "計測中") : data.focus ? t("Paused", "一時停止中") : t("Ready to start", "準備完了")}</p>}</div>
      {!data.focus ? <><Field label={t("Focus activity", "集中する活動")}><Input maxLength={160} value={focusTitle} onChange={e => setFocusTitle(e.target.value)} placeholder={t("Mining, dungeon runs…", "採掘・ダンジョン周回など")}/></Field><Numeric label={t("Focus duration minutes", "集中時間（分）")} value={focusMinutes} onChange={setFocusMinutes} min={1} max={1440}/><Button onClick={() => store.update(d => ({ ...d, focus: { title: focusTitle.trim() || t("Focus session", "集中プレイ"), total: focusMinutes * 60000, remaining: focusMinutes * 60000, end: Date.now() + focusMinutes * 60000 } }))}><Play/>{t("Start timer", "計測を開始")}</Button></> : <div className="flex flex-wrap gap-2">{remaining > 0 && <Button onClick={() => store.update(d => d.focus ? { ...d, focus: { ...d.focus, remaining: focusRemaining(d.focus, Date.now()), end: d.focus.end === null ? Date.now() + d.focus.remaining : null } } : d)}>{data.focus.end ? <Pause/> : <Play/>}{data.focus.end ? t("Pause", "一時停止") : t("Resume", "再開")}</Button>}<Button variant="outline" disabled={data.focus.total - remaining < 1000} onClick={() => record(data.focus!.title, Math.max(1 / 60, (data.focus!.total - remaining) / 60000))}>{t("Send elapsed time to journal", "計測時間を記録へ")}</Button><Button variant="ghost" onClick={() => store.update(d => ({ ...d, focus: null }))}>{t("Finish & reset", "終了・リセット")}</Button></div>}
    </ToolCard>
  </div>;
}
