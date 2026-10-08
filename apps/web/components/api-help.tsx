"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { ApiAccess } from "@skypilot/protocol";
import { Alert, Button, Card, CardContent, CardHeader, CardTitle } from "@skypilot/ui";
import { useI18n } from "@/lib/i18n";

export function ApiHelp({ access, always = false }: { access?: ApiAccess; always?: boolean }) {
  const { t } = useI18n();
  const [step, setStep] = useState(0);
  const [playing, setPlaying] = useState(false);
  useEffect(() => {
    if (!playing) return;
    const timer = setTimeout(() => { if (step === 3) setPlaying(false); else setStep(step + 1); }, 3500);
    return () => clearTimeout(timer);
  }, [playing, step]);
  const missing = access ? [
    !access.inventory && t("Inventory", "インベントリ"), !access.banking && t("Banking", "銀行"),
    !access.collections && t("Collections", "コレクション"), !access.skills && t("Skills", "スキル"),
  ].filter(Boolean) : [];
  if (!always && missing.length === 0) return null;
  const steps = [
    ["SkyBlock Menu", "SkyBlock メニュー", "In SkyBlock, open the Nether Star in your hotbar (or /sbmenu).", "SkyBlock に入り、ホットバーのネザースターを開きます（または /sbmenu）。"],
    ["Settings", "設定", "Select Settings (the redstone torch).", "Settings（設定・レッドストーントーチ）を選びます。"],
    ["API Settings", "API 設定", "Open API Settings, then enable the data you want to share: Inventory, Banking, Collections and Skills where offered.", "API Settings を開き、共有したいデータをオンにします：Inventory・Banking・Collections・Skills（表示される項目）。"],
    ["Refresh SkyPilot", "SkyPilot を再分析", "Wait for Hypixel to update, then reanalyse the same profile on the dashboard. Missing data can also mean a new profile or a temporary API issue.", "Hypixel の反映を待ち、ダッシュボードで同じプロフィールを再分析します。新規プロフィールや API 障害でもデータが欠ける場合があります。"],
  ];
  return <Card className="mb-6">
    <CardHeader><CardTitle>{t("Enable profile data: visual walkthrough", "プロフィール API：図解で有効化")}</CardTitle></CardHeader>
    <CardContent className="space-y-4">
      {missing.length > 0 && <Alert tone="warn">{t("Data not returned (may be disabled)", "取得できていないデータ（無効の可能性）")}: {missing.join(" / ")}</Alert>}
      <p className="text-xs text-muted-foreground">{t("Animated illustration, not recorded game footage. Menu names and positions may change.", "操作説明用の図解アニメーションです。実際の録画映像ではありません。項目の名称・位置は更新で変わる場合があります。")}</p>
      <div className="grid gap-4 sm:grid-cols-2">
        <svg viewBox="0 0 360 210" role="img" aria-label={t(steps[step][0], steps[step][1])} className="w-full rounded-lg bg-slate-950">
          <text x="20" y="27" fill="#e2e8f0" fontSize="15">{steps[step][0]}</text>
          {Array.from({ length: 27 }, (_, i) => <rect key={i} x={20 + i % 9 * 35} y={45 + Math.floor(i / 9) * 35} width="31" height="31" fill={i === [22, 13, 11, 4][step] ? "#7c3aed" : "#334155"} stroke="#64748b" />)}
          <text x="177" y="173" textAnchor="middle" fill="#86efac" fontSize="15">{["✦ SkyBlock Menu", "Settings →", "API: ON ✓", "SkyPilot ↻"][step]}</text>
          <text x="20" y="198" fill="#94a3b8" fontSize="12">{step + 1} / 4 · {t("illustration", "図解")}</text>
        </svg>
        <div aria-live="polite"><h3 className="font-semibold">{step + 1}. {t(steps[step][0], steps[step][1])}</h3><p className="mt-2 text-sm">{t(steps[step][2], steps[step][3])}</p></div>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={() => { if (step === 3) setStep(0); setPlaying(!playing); }}>{playing ? t("Pause", "一時停止") : t("Play walkthrough", "手順を再生")}</Button>
        {steps.map((s, i) => <Button key={s[0]} size="sm" variant="outline" aria-pressed={step === i} onClick={() => { setPlaying(false); setStep(i); }}>{i + 1}</Button>)}
        <Button asChild size="sm" variant="outline"><Link href="/">{t("Reanalyse on dashboard", "ダッシュボードで再分析")}</Link></Button>
      </div>
      <p className="text-xs text-muted-foreground">{t("These toggles make selected game data available to third-party tools. No password or Microsoft token is required. A missing server API key is a separate issue: the server owner configures HYPIXEL_API_KEY from the Hypixel developer dashboard.", "これらの設定は選んだゲームデータを外部ツールへ公開します。パスワードや Microsoft トークンの入力は不要です。サーバーの API キー不足は別の問題です：管理者が Hypixel Developer Dashboard のキーを HYPIXEL_API_KEY に設定します。")}</p>
      <a className="text-xs underline" href="https://hypixel.net/threads/account-wiped-on-skyblock.6085198/" target="_blank" rel="noreferrer">{t("Menu reference (April 2026)", "メニュー手順の参考（2026年4月）")}</a>
    </CardContent>
  </Card>;
}
