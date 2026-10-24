"use client";

import { useState } from "react";
import { Loader2, Plus, X } from "lucide-react";
import type { PlayStyle, ProfileListEntry, WorkspaceState } from "@skypilot/protocol";
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, NativeSelect, Textarea } from "@skypilot/ui";
import { api } from "@/lib/api";
import { useAnalyze } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { ErrorView } from "./common";

export const PLAY_STYLES: { id: PlayStyle; en: string; ja: string }[] = [
  { id: "FASTEST", en: "Fastest", ja: "最短" },
  { id: "SAFE", en: "Safe", ja: "安全重視" },
  { id: "MONEY_FOCUS", en: "Money focus", ja: "金策重視" },
  { id: "COMBAT", en: "Combat focus", ja: "戦闘中心" },
  { id: "FARMING", en: "Farming focus", ja: "Farming中心" },
  { id: "MINING", en: "Mining focus", ja: "Mining中心" },
];

const EXAMPLES: [string, string][] = [
  ["I want Enderman Slayer 7 and a Terminator", "Enderman Slayer 7とTerminatorが欲しい"],
  ["HOTM 10 and Mining 60", "HOTM 10と採掘60"],
  ["Complete Floor 7", "F7をクリアしたい"],
  ["Earn 1B coins", "10億コイン稼ぎたい"],
  ["Magical Power 1000", "魔力1000にしたい"],
];

/** Parses "250m", "1.5b", "300k", "12,000,000" into coins. */
export function parseCoins(s: string): number | null {
  const m = s.trim().toLowerCase().replace(/,/g, "").match(/^(\d+(?:\.\d+)?)\s*([kmbt]?)$/);
  if (!m) return null;
  const mult = { "": 1, k: 1e3, m: 1e6, b: 1e9, t: 1e12 }[m[2] as "" | "k" | "m" | "b" | "t"];
  return Number(m[1]) * mult;
}

const USERNAME_RE = /^[A-Za-z0-9_]{1,16}$/;

export function AnalyzeForm({ ws, onDone, compact = false }: { ws?: WorkspaceState; onDone?: () => void; compact?: boolean }) {
  const { t, lang } = useI18n();
  const analyze = useAnalyze();
  const [username, setUsername] = useState(ws?.username ?? "");
  const [goal, setGoal] = useState(ws?.goals.find((g) => g.priority === "PRIMARY")?.text ?? "");
  const [secondary, setSecondary] = useState<string[]>(ws?.goals.filter((g) => g.priority === "SECONDARY").map((g) => g.text) ?? []);
  const [style, setStyle] = useState<PlayStyle>(ws?.playStyle ?? "FASTEST");
  const [budget, setBudget] = useState(ws?.budget ? String(ws.budget) : "");
  const [profiles, setProfiles] = useState<ProfileListEntry[] | null>(null);
  const [profileId, setProfileId] = useState<string>(ws?.profileId ?? "");
  const [profileError, setProfileError] = useState<unknown>(null);
  const [loadingProfiles, setLoadingProfiles] = useState(false);

  const budgetValue = budget.trim() ? parseCoins(budget) : null;
  const budgetInvalid = budget.trim() !== "" && budgetValue === null;
  const usernameInvalid = username !== "" && !USERNAME_RE.test(username);
  const canSubmit = USERNAME_RE.test(username) && goal.trim().length > 0 && goal.length <= 300 && !budgetInvalid && !analyze.isPending;

  const loadProfiles = async () => {
    if (!USERNAME_RE.test(username)) return;
    setLoadingProfiles(true);
    setProfileError(null);
    try {
      const list = await api.get<ProfileListEntry[]>(`/api/players/${encodeURIComponent(username)}/profiles`);
      setProfiles(list);
      const sel = list.find((p) => p.selected) ?? list[0];
      if (sel && !list.some((p) => p.profileId === profileId)) setProfileId(sel.profileId);
    } catch (e) {
      setProfileError(e);
    } finally {
      setLoadingProfiles(false);
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    analyze.mutate(
      {
        username,
        goal: goal.trim(),
        secondaryGoals: secondary.map((s) => s.trim()).filter(Boolean),
        playStyle: style,
        profileId: profileId || null,
        budget: budgetValue,
      },
      { onSuccess: () => onDone?.() },
    );
  };

  return (
    <Card>
      {!compact ? (
        <CardHeader>
          <CardTitle className="text-base">{t("Analyse your profile", "プロフィールを分析")}</CardTitle>
          <CardDescription>
            {t(
              "Only your public Hypixel API data is read. SkyPilot never asks for Microsoft or Minecraft passwords or tokens.",
              "読み取るのは公開されている Hypixel API のデータのみです。Microsoft / Minecraft のパスワードやトークンは一切要求しません。",
            )}
          </CardDescription>
        </CardHeader>
      ) : null}
      <CardContent className={compact ? "p-5" : undefined}>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="username">{t("Minecraft username", "Minecraftユーザー名")}</Label>
              <Input
                id="username"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value.trim());
                  setProfiles(null);
                  setProfileError(null);
                }} placeholder="Notch" autoComplete="off" maxLength={16} aria-invalid={usernameInvalid} />
              {usernameInvalid ? <span className="text-xs text-destructive">{t("1-16 letters, digits or _", "1〜16文字の英数字または _")}</span> : null}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="profile">{t("Profile", "プロフィール")}</Label>
              {profiles ? (
                <NativeSelect id="profile" value={profileId} onChange={(e) => setProfileId(e.target.value)}>
                  {profiles.map((p) => (
                    <option key={p.profileId} value={p.profileId}>
                      {p.cuteName}
                      {p.gameMode ? ` (${p.gameMode})` : ""}
                      {p.selected ? ` · ${t("active", "使用中")}` : ""}
                    </option>
                  ))}
                </NativeSelect>
              ) : (
                <Button type="button" variant="outline" onClick={loadProfiles} disabled={!USERNAME_RE.test(username) || loadingProfiles}>
                  {loadingProfiles ? <Loader2 className="animate-spin" /> : null}
                  {t("Choose profile", "プロフィールを選択")}
                </Button>
              )}
            </div>
          </div>
          {profileError ? <ErrorView error={profileError} /> : null}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="goal">{t("Your goal (English or Japanese)", "目標（日本語・英語どちらでも）")}</Label>
            <Textarea id="goal" value={goal} onChange={(e) => setGoal(e.target.value)} maxLength={300} placeholder={t("e.g. I want Enderman Slayer 7 and a Terminator", "例: Enderman Slayer 7とTerminatorが欲しい")} />
            <div className="flex flex-wrap gap-1.5">
              {EXAMPLES.map(([en, ja]) => (
                <button key={en} type="button" onClick={() => setGoal(lang === "ja" ? ja : en)} className="cursor-pointer rounded-md border border-border px-2 py-0.5 text-xs text-muted-foreground hover:bg-accent">
                  {lang === "ja" ? ja : en}
                </button>
              ))}
            </div>
          </div>

          {secondary.map((s, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input value={s} maxLength={300} onChange={(e) => setSecondary(secondary.map((x, j) => (j === i ? e.target.value : x)))} placeholder={t("Secondary goal", "サブ目標")} aria-label={t("Secondary goal", "サブ目標")} />
              <Button type="button" variant="ghost" size="icon" onClick={() => setSecondary(secondary.filter((_, j) => j !== i))} aria-label={t("Remove goal", "目標を削除")}>
                <X />
              </Button>
            </div>
          ))}
          {secondary.length < 5 ? (
            <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={() => setSecondary([...secondary, ""])}>
              <Plus /> {t("Add secondary goal", "サブ目標を追加")}
            </Button>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="style">{t("Play style", "プレイスタイル")}</Label>
              <NativeSelect id="style" value={style} onChange={(e) => setStyle(e.target.value as PlayStyle)}>
                {PLAY_STYLES.map((p) => (
                  <option key={p.id} value={p.id}>
                    {t(p.en, p.ja)}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="budget">{t("Budget (optional, e.g. 250m)", "予算（任意、例: 250m）")}</Label>
              <Input id="budget" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="250m" aria-invalid={budgetInvalid} />
            </div>
          </div>

          {analyze.error ? <ErrorView error={analyze.error} /> : null}

          <div className="flex items-center gap-3">
            <Button type="submit" disabled={!canSubmit}>
              {analyze.isPending ? <Loader2 className="animate-spin" /> : null}
              {analyze.isPending ? t("Analysing…", "分析中…") : t("Analyse & plan", "分析して計画")}
            </Button>
            <span className="text-xs text-muted-foreground">{t("Fetches your profile once; nothing is polled in the background.", "プロフィールは1回だけ取得し、バックグラウンドでの定期取得はしません。")}</span>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
