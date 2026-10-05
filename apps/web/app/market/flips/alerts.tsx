"use client";

import { useEffect, useRef, useState } from "react";
import { Bell, BellOff, Trash2 } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { FlipOpportunity, NotificationRule, RiskLevel } from "@skypilot/protocol";
import { Alert, Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label, NativeSelect, Switch } from "@skypilot/ui";
import { parseCoins } from "@/components/analyze-form";
import { ErrorView } from "@/components/common";
import { api, type ApiError } from "@/lib/api";
import { RISK_ORDER, ruleMatches } from "@/lib/flips";
import { fmtCoins, fmtPct } from "@/lib/format";
import { useI18n } from "@/lib/i18n";

function urlBase64ToUint8Array(b64: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (b64.length % 4)) % 4);
  const raw = atob((b64 + padding).replace(/-/g, "+").replace(/_/g, "/"));
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

function useRules() {
  return useQuery<NotificationRule[], ApiError>({ queryKey: ["rules"], queryFn: () => api.get<NotificationRule[]>("/api/notifications/rules") });
}

function RuleForm() {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [budget, setBudget] = useState("");
  const [profit, setProfit] = useState("1m");
  const [roi, setRoi] = useState("10");
  const [conf, setConf] = useState("0.55");
  const [risk, setRisk] = useState<RiskLevel>("MEDIUM");
  const save = useMutation<NotificationRule, ApiError, NotificationRule>({
    mutationFn: (r) => api.put<NotificationRule>("/api/notifications/rules", r),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["rules"] });
      setName("");
    },
  });
  const budgetV = budget.trim() ? parseCoins(budget) : null;
  const profitV = profit.trim() ? parseCoins(profit) : null;
  const roiV = roi.trim() ? Number(roi) / 100 : null;
  const confV = conf.trim() ? Number(conf) : null;
  const invalid = (budget.trim() !== "" && budgetV === null) || (profit.trim() !== "" && profitV === null) || (roiV !== null && !Number.isFinite(roiV)) || (confV !== null && !(confV >= 0 && confV <= 1));
  return (
    <form
      className="grid gap-3 sm:grid-cols-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (invalid || !name.trim()) return;
        save.mutate({ id: "", name: name.trim(), maxBudget: budgetV, minProfit: profitV, minRoi: roiV, minConfidence: confV, maxRisk: risk, enabled: true });
      }}
    >
      <div className="flex flex-col gap-1 sm:col-span-3">
        <Label htmlFor="rule-name">{t("Rule name", "ルール名")}</Label>
        <Input id="rule-name" value={name} maxLength={64} onChange={(e) => setName(e.target.value)} placeholder={t("e.g. Cheap safe flips", "例: 安全な少額フリップ")} />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="rule-budget">{t("Max buy price", "最大購入価格")}</Label>
        <Input id="rule-budget" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="50m" />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="rule-profit">{t("Min net profit", "最低純利益")}</Label>
        <Input id="rule-profit" value={profit} onChange={(e) => setProfit(e.target.value)} />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="rule-roi">{t("Min ROI %", "最低ROI %")}</Label>
        <Input id="rule-roi" value={roi} inputMode="decimal" onChange={(e) => setRoi(e.target.value)} />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="rule-conf">{t("Min confidence (0-1)", "最低信頼度 (0-1)")}</Label>
        <Input id="rule-conf" value={conf} inputMode="decimal" onChange={(e) => setConf(e.target.value)} />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="rule-risk">{t("Max risk", "最大リスク")}</Label>
        <NativeSelect id="rule-risk" value={risk} onChange={(e) => setRisk(e.target.value as RiskLevel)}>
          {RISK_ORDER.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </NativeSelect>
      </div>
      <div className="flex items-end">
        <Button type="submit" disabled={invalid || !name.trim() || save.isPending} className="w-full">
          {t("Add rule", "ルールを追加")}
        </Button>
      </div>
      {save.error ? (
        <div className="sm:col-span-3">
          <ErrorView error={save.error} />
        </div>
      ) : null}
    </form>
  );
}

function PushControls() {
  const { t } = useI18n();
  // Rendered only inside the client-side Alerts tab, so browser APIs are available here.
  const [status, setStatus] = useState<"unsupported" | "off" | "on" | "denied" | "server-off">(() => {
    if (typeof window === "undefined" || !("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) return "unsupported";
    return Notification.permission === "denied" ? "denied" : "off";
  });
  const [msg, setMsg] = useState<string | null>(null);
  const key = useQuery({ queryKey: ["push-key"], queryFn: () => api.get<{ enabled: boolean; publicKey: string | null }>("/api/notifications/push/key") });
  const [initialStatus] = useState(status);

  useEffect(() => {
    if (initialStatus !== "off") return;
    void navigator.serviceWorker.getRegistration("/").then(async (reg) => {
      const sub = await reg?.pushManager.getSubscription();
      if (sub) setStatus("on");
    });
  }, [initialStatus]);

  const enable = async () => {
    setMsg(null);
    if (!key.data?.enabled || !key.data.publicKey) {
      setStatus("server-off");
      return;
    }
    const perm = await Notification.requestPermission();
    if (perm !== "granted") {
      setStatus(perm === "denied" ? "denied" : "off");
      return;
    }
    const reg = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
    await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(key.data.publicKey) });
    const json = sub.toJSON();
    try {
      await api.post("/api/notifications/push/subscribe", { endpoint: json.endpoint, keys: json.keys });
      setStatus("on");
    } catch (e) {
      await sub.unsubscribe();
      setMsg((e as Error).message);
    }
  };

  const disable = async () => {
    const reg = await navigator.serviceWorker.getRegistration("/");
    const sub = await reg?.pushManager.getSubscription();
    if (sub) {
      await api.post("/api/notifications/push/unsubscribe", { endpoint: sub.endpoint }).catch(() => undefined);
      await sub.unsubscribe();
    }
    setStatus("off");
  };

  const test = async () => {
    try {
      const r = await api.post<{ delivered: number }>("/api/notifications/push/test");
      setMsg(t(`Sent to ${r.delivered} device(s).`, `${r.delivered} 台のデバイスに送信しました。`));
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {status === "on" ? (
          <>
            <Badge tone="good">{t("Web Push on", "Web Push 有効")}</Badge>
            <Button size="sm" variant="outline" onClick={test}>
              {t("Send test", "テスト送信")}
            </Button>
            <Button size="sm" variant="ghost" onClick={disable}>
              <BellOff /> {t("Turn off", "無効にする")}
            </Button>
          </>
        ) : status === "unsupported" ? (
          <Badge>{t("This browser does not support Web Push", "このブラウザは Web Push に対応していません")}</Badge>
        ) : status === "denied" ? (
          <Badge tone="warn">{t("Notifications are blocked in browser settings", "ブラウザ設定で通知がブロックされています")}</Badge>
        ) : status === "server-off" ? (
          <Badge tone="warn">{t("Web Push is disabled on this server", "このサーバーでは Web Push が無効です")}</Badge>
        ) : (
          <Button size="sm" onClick={() => void enable().catch((e: Error) => setMsg(e.message))} disabled={key.isLoading}>
            <Bell /> {t("Enable Web Push", "Web Push を有効化")}
          </Button>
        )}
      </div>
      {msg ? <p className="text-xs text-muted-foreground">{msg}</p> : null}
    </div>
  );
}

/** Rule list + in-tab alerts (Notification API) + Web Push subscription. Alerts only inform; nothing is bought. */
export function AlertsPanel({ flips }: { flips: FlipOpportunity[] }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const rules = useRules();
  const seen = useRef<Set<string>>(new Set());
  const [inTab, setInTab] = useState(false);

  const toggle = useMutation<NotificationRule, ApiError, NotificationRule>({
    mutationFn: (r) => api.put<NotificationRule>("/api/notifications/rules", r),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rules"] }),
  });
  const remove = useMutation<unknown, ApiError, string>({
    mutationFn: (id) => api.del(`/api/notifications/rules/${encodeURIComponent(id)}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rules"] }),
  });

  useEffect(() => {
    if (!inTab || !rules.data || typeof Notification === "undefined" || Notification.permission !== "granted") return;
    for (const rule of rules.data) {
      for (const f of flips) {
        const key = `${rule.id}:${f.auctionId}`;
        if (seen.current.has(key) || !ruleMatches(rule, f)) continue;
        seen.current.add(key);
        new Notification(`SkyPilot · ${rule.name}`, { body: `${f.itemName}: ${fmtCoins(f.buyPrice)} → +${fmtCoins(f.expectedNetProfit)} (ROI ${fmtPct(f.roi, 0)}) · ${f.viewCommand}`, tag: key });
      }
    }
  }, [flips, rules.data, inTab]);

  const enableInTab = async () => {
    if (typeof Notification === "undefined") return;
    const p = await Notification.requestPermission();
    if (p === "granted") {
      // Do not alert for flips that were already on screen when alerts were turned on.
      for (const rule of rules.data ?? []) for (const f of flips) seen.current.add(`${rule.id}:${f.auctionId}`);
      setInTab(true);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Flip alerts", "フリップ通知")}</CardTitle>
        <CardDescription>
          {t(
            "Get notified when a flip matches your rule. Alerts are information only - SkyPilot never buys, bids or lists for you. Manipulation-flagged flips never alert.",
            "条件に合うフリップを通知します。通知は情報提供のみで、SkyPilot が購入・入札・出品することはありません。相場操作の疑いがあるフリップは通知しません。",
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm">
            <Switch checked={inTab} onCheckedChange={(v) => (v ? void enableInTab() : setInTab(false))} />
            {t("Alerts while this tab is open", "このタブを開いている間の通知")}
          </label>
          <PushControls />
        </div>
        {rules.error ? <ErrorView error={rules.error} /> : null}
        {rules.data && rules.data.length > 0 ? (
          <div className="flex flex-col divide-y divide-border rounded-lg border border-border">
            {rules.data.map((r) => (
              <div key={r.id} className="flex flex-wrap items-center gap-3 px-3 py-2 text-sm">
                <Switch checked={r.enabled} onCheckedChange={(v) => toggle.mutate({ ...r, enabled: v })} aria-label={t("Enabled", "有効")} />
                <span className="font-medium">{r.name}</span>
                <span className="text-xs text-muted-foreground">
                  {r.maxBudget !== null ? `≤ ${fmtCoins(r.maxBudget)} · ` : ""}
                  {r.minProfit !== null ? `+${fmtCoins(r.minProfit)} · ` : ""}
                  {r.minRoi !== null ? `ROI ≥ ${fmtPct(r.minRoi, 0)} · ` : ""}
                  {r.minConfidence !== null ? `conf ≥ ${r.minConfidence} · ` : ""}
                  {r.maxRisk ?? ""}
                </span>
                <Badge className="ml-auto">{flips.filter((f) => ruleMatches(r, f)).length} {t("matching now", "件が該当")}</Badge>
                <Button size="icon" variant="ghost" onClick={() => remove.mutate(r.id)} aria-label={t("Delete rule", "ルールを削除")}>
                  <Trash2 />
                </Button>
              </div>
            ))}
          </div>
        ) : (
          <Alert>{t("No rules yet - add one below.", "ルールはまだありません。下から追加してください。")}</Alert>
        )}
        <RuleForm />
      </CardContent>
    </Card>
  );
}
