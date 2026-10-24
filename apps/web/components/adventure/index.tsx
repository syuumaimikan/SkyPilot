"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { Bookmark, CheckCheck, ClipboardCopy, Download, RotateCcw, Upload } from "lucide-react";
import { Alert, Button, Tabs, TabsContent, TabsList, TabsTrigger, Textarea } from "@skypilot/ui";
import { PageHeader, Loading } from "@/components/common";
import { useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { decodeAdventure, isDone, type Adventure } from "@/lib/adventure";
import { useAdventure } from "./store";
import { TodayPanel } from "./today";
import { SessionPanel, type SessionDraft } from "./sessions";
import { ProgressPanel } from "./progress";
import { NotesPanel } from "./notes";
import { Field, Stat, ToolCard } from "./common";
import type { RecommendedAction } from "@skypilot/protocol";

const hydrated = () => () => {};
export function AdventurePlanner() {
  const ws = useWorkspace();
  const mounted = useSyncExternalStore(hydrated, () => true, () => false);
  const profile = ws.data?.profileId ?? "manual";
  return mounted ? <Planner key={profile} storageKey={`skypilot.adventure.${profile}`} profile={ws.data?.username ?? null} actions={ws.data?.lastAnalysis?.actions ?? []}/> : <Loading/>;
}

function Planner({ storageKey, profile, actions }: { storageKey: string; profile: string | null; actions: RecommendedAction[] }) {
  const { t } = useI18n();
  const store = useAdventure(storageKey);
  const [now, setNow] = useState(() => Date.now());
  const [tab, setTab] = useState("today"), [draft, setDraft] = useState<SessionDraft | null>(null);
  const [backup, setBackup] = useState(""), [importText, setImportText] = useState("");
  const [preview, setPreview] = useState<Adventure | null>(null), [backupMessage, setBackupMessage] = useState("");
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  const ready = store.data.reminders.filter(timer => timer.due <= now && !timer.done).length;
  const remaining = store.data.tasks.filter(task => !isDone(task, now)).length;

  function exportBackup() {
    setBackup(JSON.stringify(store.data, null, 2));
    setBackupMessage(t("Backup prepared. Copy the JSON and store it in a file or note.", "バックアップを作成しました。JSONをコピーしてファイルやメモに保管してください。"));
  }
  function inspectImport() {
    try {
      if (!importText.trim()) throw new Error("empty");
      setPreview(decodeAdventure(importText)); setBackupMessage("");
    } catch { setPreview(null); setBackupMessage(t("Invalid backup. Check its version, size, fields and entry limits.", "バックアップを読み込めません。バージョン・サイズ・項目・件数上限を確認してください。")); }
  }

  return <div className="space-y-6">
    <PageHeader title={t("Adventure notebook", "冒険ノート")} description={t("20 tools for daily planning, measured progress and your next session.", "日課・実測記録・育成・装備・メモをひとつに。次のプレイに役立つ20のツール。") } actions={<Button asChild variant="outline"><Link href="/money/workbench">{t("Money tools", "金策ツールへ")}</Link></Button>}/>
    <section className="overflow-hidden rounded-3xl border border-primary/20 bg-gradient-to-br from-primary/15 via-card to-cyan-500/10 p-5 md:p-8">
      <div className="mb-5 flex items-center gap-3"><span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/20 text-primary"><Bookmark className="size-6"/></span><div><p className="text-xs font-semibold tracking-[0.15em] text-primary">ADVENTURE CONTROL ROOM</p><h2 className="mt-1 text-2xl font-semibold">{t("Make every session count.", "今日の一歩を、積み重ねよう。")}</h2></div></div>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4"><Stat title={t("Tasks remaining", "残りのタスク")} value={String(remaining)}/><Stat title={t("Session records", "プレイ記録")} value={String(store.data.sessions.length)}/><Stat title={t("Milestones achieved", "達成した育成目標")} value={`${store.data.milestones.filter(goal => goal.current >= goal.target).length}/${store.data.milestones.length}`}/><Stat title={t("Timers ready", "確認できる生産タイマー")} value={String(ready)}/></div>
      <p className="mt-4 text-xs leading-relaxed text-muted-foreground">{profile ? `${profile} · ` : ""}{t("Saved on this device, separately for each analyzed profile. It is not automatically synced to your other devices.", "端末内にプロフィール別で保存します。他の端末には自動同期しません。")}</p>
      {ready > 0 && <Button className="mt-3" variant="outline" onClick={() => setTab("notes")}><CheckCheck/>{t(`${ready} timers ready to check`, `${ready}件のタイマーが完了時刻です`)}</Button>}
    </section>
    {!store.valid && <Alert tone="bad">{t("Local storage is unavailable or the stored data is invalid. Existing data will not be silently overwritten. You can restore a validated backup below.", "端末保存が利用できないか、保存データを読み込めません。既存データを自動で上書きせず保持しています。下のバックアップ復元を利用できます。")}</Alert>}
    {store.error && <Alert tone="bad">{store.error}</Alert>}
    {store.canUndo && <Button variant="outline" size="sm" onClick={store.undoChange}><RotateCcw/>{t("Undo the last change", "直前の変更を戻す")}</Button>}
    <Tabs value={tab} onValueChange={setTab}><TabsList className="grid h-auto w-full grid-cols-2 gap-1 sm:grid-cols-4"><TabsTrigger value="today">{t("Today & focus", "今日・集中")}</TabsTrigger><TabsTrigger value="sessions">{t("Journal & review", "記録・振り返り")}</TabsTrigger><TabsTrigger value="progress">{t("Progress & gear", "育成・装備")}</TabsTrigger><TabsTrigger value="notes">{t("Notes & timers", "メモ・タイマー")}</TabsTrigger></TabsList>
      <TabsContent value="today" forceMount className="data-[state=inactive]:hidden"><TodayPanel store={store} now={now} actions={actions} record={(title, minutes) => { setDraft({ key: crypto.randomUUID(), title, minutes }); setTab("sessions"); }}/></TabsContent>
      <TabsContent value="sessions" forceMount className="data-[state=inactive]:hidden"><SessionPanel key={draft?.key ?? "manual"} store={store} now={now} draft={draft}/></TabsContent>
      <TabsContent value="progress" forceMount className="data-[state=inactive]:hidden"><ProgressPanel store={store}/></TabsContent>
      <TabsContent value="notes" forceMount className="data-[state=inactive]:hidden"><NotesPanel store={store} now={now}/></TabsContent>
    </Tabs>
    <ToolCard title={t("20 · Backup & restore", "20 · バックアップ・復元")} help={t("Copy a JSON backup to transfer your notebook to another device. Restore replaces this profile's notebook; inspect the preview before proceeding.", "JSONをコピーして別の端末へノートを移せます。復元はこのプロフィールのノートを置き換えます。プレビューを確認してから実行してください。") }>
      <div className="flex flex-wrap gap-2"><Button variant="outline" disabled={!store.valid} onClick={exportBackup}><Download/>{t("Prepare backup", "バックアップを作成")}</Button>{backup && <Button variant="outline" onClick={async () => { try { await navigator.clipboard.writeText(backup); setBackupMessage(t("Backup copied.", "バックアップをコピーしました。")); } catch { setBackupMessage(t("Select and copy the JSON below.", "下のJSONを選択してコピーしてください。")); } }}><ClipboardCopy/>{t("Copy backup", "バックアップをコピー")}</Button>}</div>
      {backup && <Field label={t("Backup JSON", "バックアップJSON")}><Textarea value={backup} readOnly rows={5} className="font-mono text-xs" onFocus={e => e.target.select()}/></Field>}
      <Field label={t("Paste backup to restore", "復元するJSONを貼り付け")}><Textarea value={importText} maxLength={2000000} rows={4} onChange={e => { setImportText(e.target.value); setPreview(null); }} placeholder='{"version":1,...}' className="font-mono text-xs"/></Field>
      <Button variant="outline" disabled={!importText.trim()} onClick={inspectImport}><Upload/>{t("Inspect backup", "復元内容を確認")}</Button>
      {preview && <div className="space-y-3 rounded-2xl border border-primary/25 bg-primary/10 p-4"><p className="text-sm">{t("Preview", "復元プレビュー")}: {t("Tasks", "タスク")} {preview.tasks.length} · {t("Records", "記録")} {preview.sessions.length} · {t("Milestones", "育成目標")} {preview.milestones.length} · {t("Wishlist", "欲しい物")} {preview.wishes.length} · {t("Loadouts", "装備")} {preview.loadouts.length} · {t("Notes", "メモ")} {preview.notes.length} · {t("Timers", "タイマー")} {preview.reminders.length}</p><Button onClick={() => { if (store.update(() => preview, true)) { setPreview(null); setImportText(""); setBackupMessage(t("Notebook restored.", "冒険ノートを復元しました。")); } }}>{t("Replace this profile's notebook", "このプロフィールのノートを置き換える")}</Button></div>}
      <p role="status" className="text-sm text-primary">{backupMessage}</p>
    </ToolCard>
  </div>;
}
