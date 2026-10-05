"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { PlayStyle, TranslateResponse, TranslationStatus } from "@skypilot/protocol";
import { Alert, Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, NativeSelect, Textarea } from "@skypilot/ui";
import { parseCoins, PLAY_STYLES } from "@/components/analyze-form";
import { ErrorView, Loading, PageHeader } from "@/components/common";
import { api, fetchMe, type ApiError } from "@/lib/api";
import { fmtDate } from "@/lib/format";
import { useApi, useInvalidateProfile, useMe, useWorkspace } from "@/lib/hooks";
import { useI18n, type Lang } from "@/lib/i18n";

function Preferences() {
  const ws = useWorkspace();
  if (ws.isLoading) return <Loading rows={1} />;
  if (ws.error) return <ErrorView error={ws.error} />;
  return <PreferencesForm key={`${ws.data?.playStyle}-${ws.data?.budget}`} initialStyle={ws.data?.playStyle ?? "FASTEST"} initialBudget={ws.data?.budget ?? null} />;
}

function PreferencesForm({ initialStyle, initialBudget }: { initialStyle: PlayStyle; initialBudget: number | null }) {
  const { t, lang, setLang } = useI18n();
  const invalidate = useInvalidateProfile();
  const [style, setStyle] = useState<PlayStyle>(initialStyle);
  const [budget, setBudget] = useState(initialBudget !== null ? String(initialBudget) : "");
  const save = useMutation<unknown, ApiError, { playStyle: PlayStyle; budget: number | null; lang: Lang }>({
    mutationFn: (p) => api.put("/api/workspace/prefs", p),
    onSuccess: () => invalidate(),
  });
  const budgetV = budget.trim() ? parseCoins(budget) : null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Preferences", "設定")}</CardTitle>
        <CardDescription>{t("Used for every recommendation. Re-plan from the Roadmap page to apply a new play style to existing goals.", "すべての推奨に使われます。既存の目標に反映するにはロードマップで再計画してください。")}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="pref-lang">{t("Language", "言語")}</Label>
          <NativeSelect id="pref-lang" value={lang} onChange={(e) => setLang(e.target.value as Lang)}>
            <option value="en">English</option>
            <option value="ja">日本語</option>
          </NativeSelect>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="pref-style">{t("Play style", "プレイスタイル")}</Label>
          <NativeSelect id="pref-style" value={style} onChange={(e) => setStyle(e.target.value as PlayStyle)}>
            {PLAY_STYLES.map((p) => (
              <option key={p.id} value={p.id}>
                {t(p.en, p.ja)}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="pref-budget">{t("Budget (blank = none)", "予算（空欄=なし）")}</Label>
          <Input id="pref-budget" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="250m" aria-invalid={budget.trim() !== "" && budgetV === null} />
        </div>
        <div className="flex items-center gap-3 sm:col-span-3">
          <Button disabled={save.isPending || (budget.trim() !== "" && budgetV === null)} onClick={() => save.mutate({ playStyle: style, budget: budgetV, lang })}>
            {t("Save", "保存")}
          </Button>
          {save.isSuccess ? <span className="text-xs text-emerald-600">{t("Saved", "保存しました")}</span> : null}
          {save.error ? <ErrorView error={save.error} /> : null}
        </div>
      </CardContent>
    </Card>
  );
}

function Account() {
  const { t, lang } = useI18n();
  const me = useMe();
  const qc = useQueryClient();
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const done = async () => {
    await fetchMe(true);
    await qc.invalidateQueries();
    setPassword("");
  };
  const register = useMutation<unknown, ApiError, void>({ mutationFn: () => api.post("/api/auth/register", { login, password }), onSuccess: done });
  const signIn = useMutation<unknown, ApiError, void>({ mutationFn: () => api.post("/api/auth/login", { login, password }), onSuccess: done });
  const signOut = useMutation<unknown, ApiError, void>({ mutationFn: () => api.post("/api/auth/logout"), onSuccess: done });
  const revoke = useMutation<unknown, ApiError, string>({ mutationFn: (id) => api.del(`/api/devices/${encodeURIComponent(id)}`), onSuccess: () => qc.invalidateQueries({ queryKey: ["me"] }) });
  if (me.isLoading) return <Loading rows={1} />;
  if (me.error) return <ErrorView error={me.error} />;
  const m = me.data!;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Account & devices", "アカウント・デバイス")}</CardTitle>
        <CardDescription>
          {t(
            "SkyPilot works without an account (an anonymous workspace in this browser). Add a SkyPilot login to use it on other browsers. This is never your Microsoft or Minecraft password.",
            "SkyPilot はアカウントなしでも使えます（このブラウザの匿名ワークスペース）。他のブラウザでも使う場合は SkyPilot 用のログインを追加してください。Microsoft / Minecraft のパスワードは絶対に入力しないでください。",
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {m.anonymous ? (
          <form className="grid gap-3 sm:grid-cols-[1fr_1fr_auto_auto]" onSubmit={(e) => e.preventDefault()}>
            <Input value={login} onChange={(e) => setLogin(e.target.value)} placeholder={t("SkyPilot login name", "SkyPilot のログイン名")} autoComplete="username" aria-label={t("Login", "ログイン名")} />
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={t("New SkyPilot password (10+ chars)", "SkyPilot 用パスワード（10文字以上）")} autoComplete="new-password" aria-label={t("Password", "パスワード")} />
            <Button type="button" onClick={() => register.mutate()} disabled={!login || password.length < 10 || register.isPending}>
              {t("Save workspace", "ワークスペースを保存")}
            </Button>
            <Button type="button" variant="outline" onClick={() => signIn.mutate()} disabled={!login || !password || signIn.isPending}>
              {t("Sign in", "ログイン")}
            </Button>
          </form>
        ) : (
          <div className="flex items-center gap-3 text-sm">
            <span>
              {t("Signed in as", "ログイン中")}: <b>{m.login}</b>
            </span>
            <Button size="sm" variant="outline" onClick={() => signOut.mutate()}>
              {t("Sign out", "ログアウト")}
            </Button>
          </div>
        )}
        {register.error ? <ErrorView error={register.error} /> : null}
        {signIn.error ? <ErrorView error={signIn.error} /> : null}
        <div>
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm font-medium">{t("Linked Minecraft clients", "連携中のMinecraftクライアント")}</span>
            <Link href="/link" className="text-xs text-primary hover:underline">
              {t("Link a client →", "クライアントを連携 →")}
            </Link>
          </div>
          {m.linkedDevices.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("No linked devices.", "連携中のデバイスはありません。")}</p>
          ) : (
            <div className="flex flex-col divide-y divide-border rounded-lg border border-border">
              {m.linkedDevices.map((d) => (
                <div key={d.id} className="flex flex-wrap items-center gap-3 px-3 py-2 text-sm">
                  <span className="font-medium">{d.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {t("linked", "連携")} {fmtDate(d.createdAtMs, lang)}
                    {d.lastUsedAtMs ? ` · ${t("last used", "最終利用")} ${fmtDate(d.lastUsedAtMs, lang)}` : ""}
                  </span>
                  <Button size="sm" variant="destructive" className="ml-auto" onClick={() => revoke.mutate(d.id)}>
                    {t("Revoke", "解除")}
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function Translation() {
  const { t } = useI18n();
  const st = useApi<TranslationStatus>("/api/translate/status");
  const [text, setText] = useState("anyone want to do f7? need mage");
  const run = useMutation<TranslateResponse, ApiError, string>({ mutationFn: (s) => api.post<TranslateResponse>("/api/translate", { text: s, sourceLang: "en", targetLang: "ja", channel: "PARTY" }) });
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Chat translation (mod)", "チャット翻訳（MOD）")}</CardTitle>
        <CardDescription>{t("English → Japanese translation used by the SkyPilot mod. It is OFF by default in the mod.", "SkyPilot MOD が使う英語→日本語翻訳です。MOD では初期状態で OFF です。")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {st.data ? (
          <>
            <Alert tone="info">{st.data.privacyNotice}</Alert>
            <div className="flex flex-wrap gap-2 text-xs">
              <Badge>{t("External provider", "外部プロバイダ")}: {st.data.externalProvider ?? t("none (dictionary only)", "なし（辞書のみ）")}</Badge>
              <Badge tone={st.data.circuit.toLowerCase() === "closed" ? "good" : "warn"}>circuit: {st.data.circuit}</Badge>
            </div>
          </>
        ) : st.error ? (
          <ErrorView error={st.error} />
        ) : null}
        <div className="flex flex-col gap-2">
          <Label htmlFor="tr-text">{t("Try it", "試す")}</Label>
          <Textarea id="tr-text" value={text} maxLength={300} onChange={(e) => setText(e.target.value)} />
          <Button className="w-fit" variant="secondary" disabled={!text.trim() || run.isPending} onClick={() => run.mutate(text)}>
            {t("Translate", "翻訳")}
          </Button>
          {run.data ? (
            <div className="rounded-md bg-muted p-3 text-sm">
              {run.data.translated}
              <div className="mt-1 text-xs text-muted-foreground">
                {run.data.provider}
                {run.data.cached ? " · cached" : ""}
                {run.data.unchanged ? ` · ${t("unchanged", "変更なし")}` : ""}
              </div>
            </div>
          ) : null}
          {run.error ? <ErrorView error={run.error} /> : null}
        </div>
      </CardContent>
    </Card>
  );
}

export function SettingsView() {
  const { t } = useI18n();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Settings", "設定")} />
      <Preferences />
      <Account />
      <Translation />
      <Card>
        <CardHeader>
          <CardTitle>{t("Notifications", "通知")}</CardTitle>
        </CardHeader>
        <CardContent className="text-sm">
          {t("Flip alert rules and Web Push are managed on the ", "フリップ通知のルールと Web Push は ")}
          <Link href="/market/flips" className="text-primary hover:underline">
            {t("Live Flips → Alerts", "ライブフリップ → 通知")}
          </Link>
          {t(" tab.", " タブで管理します。")}
        </CardContent>
      </Card>
    </div>
  );
}
