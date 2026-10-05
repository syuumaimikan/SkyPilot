"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { Alert, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label } from "@skypilot/ui";
import { ErrorView, PageHeader } from "@/components/common";
import { api, fetchMe, type ApiError } from "@/lib/api";
import { fmtDate } from "@/lib/format";
import { useI18n } from "@/lib/i18n";

const CODE_RE = /^[BCDFGHJKLMNPQRSTVWXZ]{4}-?[BCDFGHJKLMNPQRSTVWXZ]{4}$/;

export function LinkView() {
  const { t, lang } = useI18n();
  const initial = (useSearchParams().get("code") ?? "").toUpperCase();
  const [code, setCode] = useState(initial);
  const normalized = code.trim().toUpperCase().replace(/^([A-Z]{4})([A-Z]{4})$/, "$1-$2");
  const lookup = useMutation<{ deviceName: string; expiresAtMs: number }, ApiError, string>({
    mutationFn: async (c) => {
      await fetchMe();
      return api.get("/api/device/lookup", { user_code: c });
    },
  });
  const decide = useMutation<{ ok: boolean; approved: boolean }, ApiError, boolean>({ mutationFn: (approve) => api.post("/api/device/approve", { userCode: normalized, approve }) });

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6">
      <PageHeader
        title={t("Link the SkyPilot mod", "SkyPilot MOD を連携")}
        description={t(
          "Run /skypilot link in Minecraft. The mod shows a code - enter it here to connect the mod to this workspace.",
          "Minecraft で /skypilot link を実行すると MOD にコードが表示されます。そのコードをここに入力すると、このワークスペースと MOD が連携します。",
        )}
      />
      <Alert tone="info" className="flex gap-2">
        <ShieldCheck className="mt-0.5 size-4 shrink-0" />
        <span>
          {t(
            "Linking never involves your Microsoft or Minecraft password or session token. The mod only receives a SkyPilot token you can revoke at any time in Settings.",
            "連携で Microsoft / Minecraft のパスワードやセッショントークンを使うことはありません。MOD が受け取るのは SkyPilot 専用のトークンだけで、設定からいつでも解除できます。",
          )}
        </span>
      </Alert>
      <Card>
        <CardHeader>
          <CardTitle>{t("Enter code", "コードを入力")}</CardTitle>
          <CardDescription>{t("Only approve codes from a Minecraft client you are using yourself.", "自分で操作している Minecraft クライアントのコードだけを承認してください。")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form
            className="flex items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              decide.reset();
              if (CODE_RE.test(normalized)) lookup.mutate(normalized);
            }}
          >
            <div className="flex flex-1 flex-col gap-1">
              <Label htmlFor="code">{t("Code", "コード")}</Label>
              <Input id="code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="WDJB-MJHT" className="font-mono tracking-widest uppercase" autoComplete="off" maxLength={9} />
            </div>
            <Button type="submit" disabled={!CODE_RE.test(normalized) || lookup.isPending}>
              {t("Continue", "次へ")}
            </Button>
          </form>
          {lookup.error ? <ErrorView error={lookup.error} /> : null}
          {lookup.data && !decide.data ? (
            <div className="flex flex-col gap-3 rounded-lg border border-border p-4">
              <p className="text-sm">
                {t("Device requesting access", "アクセスを求めているデバイス")}: <b>{lookup.data.deviceName}</b>
              </p>
              <p className="text-xs text-muted-foreground">
                {t("Code expires", "有効期限")}: {fmtDate(lookup.data.expiresAtMs, lang)}
              </p>
              <p className="text-xs text-muted-foreground">{t("The mod will be able to read your roadmap and recommendations, check prices, translate chat (if you enable it) and upload session statistics.", "MOD はロードマップと推奨の閲覧、価格チェック、チャット翻訳（有効にした場合）、セッション統計のアップロードができるようになります。")}</p>
              <div className="flex gap-2">
                <Button onClick={() => decide.mutate(true)} disabled={decide.isPending}>
                  {t("Approve", "承認")}
                </Button>
                <Button variant="outline" onClick={() => decide.mutate(false)} disabled={decide.isPending}>
                  {t("Deny", "拒否")}
                </Button>
              </div>
            </div>
          ) : null}
          {decide.error ? <ErrorView error={decide.error} /> : null}
          {decide.data ? (
            <Alert tone={decide.data.approved ? "good" : "neutral"}>
              {decide.data.approved ? t("Approved. Return to Minecraft - the mod will finish linking within a few seconds.", "承認しました。Minecraft に戻ると数秒で連携が完了します。") : t("Denied. The mod will not be linked.", "拒否しました。MOD は連携されません。")}
            </Alert>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
