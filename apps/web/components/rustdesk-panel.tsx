"use client";

import { useState, useSyncExternalStore } from "react";
import { ArrowUpRight, Check, Copy, Download, Monitor, ShieldCheck, Wifi } from "lucide-react";
import { Button, Card, CardContent, Input, Label } from "@skypilot/ui";
import { PageHeader } from "@/components/common";
import { useI18n } from "@/lib/i18n";
import { rustDeskLink, rustDeskTarget } from "@/lib/rustdesk";

const key = "skypilot:rustdesk:target";
function subscribe(listener: () => void) {
  window.addEventListener("storage", listener);
  window.addEventListener("rustdesk-settings", listener);
  return () => { window.removeEventListener("storage", listener); window.removeEventListener("rustdesk-settings", listener); };
}
function snapshot() {
  let saved = "";
  try { saved = window.localStorage.getItem(key) ?? ""; } catch {}
  return JSON.stringify({ host: window.location.hostname, saved, native: navigator.userAgent.includes("SkyPilotAndroid/") });
}

export function RustDeskPanel() {
  const { t } = useI18n();
  const context = JSON.parse(useSyncExternalStore(subscribe, snapshot, () => '{"host":"","saved":"","native":false}')) as { host: string; saved: string; native: boolean };
  const [edited, setEdited] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const value = edited ?? (rustDeskTarget(context.saved) ? context.saved : rustDeskTarget(context.host) ?? "");
  const target = rustDeskTarget(value);
  const link = rustDeskLink(value);

  function save() {
    if (!target) return;
    try {
      window.localStorage.setItem(key, target);
      window.dispatchEvent(new Event("rustdesk-settings"));
      setEdited(null);
      setMessage(t("Connection saved on this device.", "この端末に接続先を保存しました。"));
    } catch { setMessage(t("Storage is unavailable; you can still connect.", "端末への保存ができません。接続ボタンは利用できます。")); }
  }

  async function copy() {
    if (!target) return;
    try { await navigator.clipboard.writeText(target); setMessage(t("Address copied.", "接続先をコピーしました。")); }
    catch { setMessage(t("Select and copy the address in the field.", "入力欄の接続先を選択してコピーしてください。")); }
  }

  return <>
    <PageHeader title={t("Remote PC · RustDesk", "PCリモート操作 · RustDesk")} description={t("Open your PC through your private Tailscale connection.", "Tailscaleのプライベート接続で、自分のPCをスマホから操作。")}/>
    <section className="relative overflow-hidden rounded-3xl border border-primary/20 bg-gradient-to-br from-primary/15 via-card to-cyan-500/10 p-6 md:p-9">
      <div className="mb-6 flex items-center gap-4"><span className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-primary/15 text-primary"><Monitor className="size-8"/></span><div><p className="text-xs font-semibold tracking-[0.15em] text-primary">YOUR PRIVATE DESKTOP</p><h2 className="mt-2 text-2xl font-semibold">{t("Your PC, within reach.", "いつものPCを、手元に。")}</h2></div></div>
      <div className="mb-6 flex flex-wrap gap-2 text-xs text-muted-foreground"><span className="flex items-center gap-2 rounded-full border border-border bg-background/50 px-3 py-2"><Wifi className="size-4"/>Tailscale</span><span className="flex items-center gap-2 rounded-full border border-border bg-background/50 px-3 py-2"><ShieldCheck className="size-4"/>{t("Direct private connection", "プライベートIPへ直接接続")}</span></div>
      <div className="max-w-xl space-y-3"><Label htmlFor="rustdesk-target">{t("PC Tailscale address", "PCのTailscale接続先")}</Label><Input id="rustdesk-target" value={value} onChange={(event) => { setEdited(event.target.value); setMessage(""); }} placeholder="100.x.x.x:21118" autoCapitalize="none" autoCorrect="off" spellCheck={false} className="min-h-12" aria-describedby="rustdesk-target-help"/>
        <p id="rustdesk-target-help" className="text-xs leading-relaxed text-muted-foreground">{t("Use a Tailscale IPv4 or full PC.tailnet.ts.net name. Default port: 21118.", "100.x.x.xのTailscale IPv4、またはPC名.tailnet.ts.netを入力。標準ポートは21118です。")}</p>
        {value && !target ? <p role="alert" className="text-sm text-red-400">{t("Enter a valid private Tailscale address and port.", "Tailscaleの接続先と有効なポートを入力してください。")}</p> : null}
        <div className="flex flex-wrap gap-2 pt-2">{link ? <Button asChild className="min-h-12"><a href={link}><ArrowUpRight/>{t("Connect in RustDesk", "RustDeskでPCに接続")}</a></Button> : <Button disabled className="min-h-12">{t("Connect in RustDesk", "RustDeskでPCに接続")}</Button>}<Button variant="outline" disabled={!target} onClick={save} className="min-h-12"><Check/>{t("Save", "保存")}</Button><Button variant="outline" disabled={!target} onClick={copy} className="min-h-12"><Copy/>{t("Copy", "コピー")}</Button></div>
        <p aria-live="polite" role="status" className="min-h-5 text-sm text-primary">{message}</p>
      </div>
      <p className="mt-4 max-w-2xl text-sm leading-relaxed text-muted-foreground">{t("The RustDesk app opens for remote control. Enter the PC password or approve the connection in RustDesk.", "接続ボタンでRustDeskアプリに切り替わります。PCのパスワード入力・接続承認はRustDesk側で行います。")}</p>
      {context.native ? <p className="mt-2 text-xs text-muted-foreground">{t("Requires SkyPilot Android 0.4.0 or newer. You can also connect from the top-right menu.", "SkyPilot Android 0.4.0以降が必要です。右上メニューからも接続できます。")}</p> : null}
    </section>
    <div className="mt-5 grid gap-4 md:grid-cols-2"><Card><CardContent className="space-y-4 pt-6"><h3 className="font-semibold">{t("Before connecting", "接続する前に")}</h3><ol className="list-decimal space-y-3 pl-5 text-sm leading-relaxed text-muted-foreground"><li>{t("Connect PC and phone to Tailscale using the same account.", "PCとスマホを同じTailscaleアカウントで接続します。")}</li><li>{t("Keep RustDesk running on the PC, with direct IP access enabled.", "PCのRustDeskを起動し、直接IPアクセスを有効にします。")}</li><li>{t("Restrict the PC's RustDesk IP whitelist to your own devices' Tailscale IPv4 addresses.", "PCのRustDeskのIPホワイトリストを、自分の端末のTailscale IPv4に限定します。")}</li><li>{t("Install Android RustDesk and press Connect above.", "Android版RustDeskをインストールし、上の接続ボタンを押します。")}</li></ol></CardContent></Card><Card><CardContent className="space-y-4 pt-6"><h3 className="font-semibold">{t("RustDesk for Android", "Android版RustDesk")}</h3><p className="text-sm leading-relaxed text-muted-foreground">{t("If the app does not open, install the official Android version. You can paste the copied PC address into RustDesk as well.", "アプリが開かない場合は公式Android版を導入してください。コピーしたPCの接続先をRustDeskに貼り付けても接続できます。")}</p><Button asChild variant="outline" className="min-h-11"><a href="https://github.com/rustdesk/rustdesk/releases/latest" target="_blank" rel="noopener noreferrer"><Download/>{t("Official downloads", "公式ダウンロード")}</a></Button><p className="text-xs leading-relaxed text-muted-foreground">{t("RustDesk passwords are not stored in SkyPilot. The private desktop connection is separate from the SkyPilot website.", "RustDeskのパスワードはSkyPilotに保存しません。PC操作にはSkyPilotのWeb接続とは別にRustDeskの接続設定が必要です。")}</p></CardContent></Card></div>
  </>;
}
