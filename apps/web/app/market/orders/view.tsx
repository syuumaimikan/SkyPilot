"use client";

import { useEffect, useState } from "react";
import { Badge, Button, Card, CardContent, Input, NativeSelect } from "@skypilot/ui";
import { Copy, RefreshCw } from "lucide-react";
import { PageHeader, Loading, ErrorView } from "@/components/common";
import { Stat } from "@/components/adventure/common";
import { useApi, useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { fmtCoins, fmtDate, fmtHours } from "@/lib/format";

type Order = { name: string; side: string; price: number; status: string; auctionId?: string | null; endsAtMs?: number | null; highestBid?: number; bin?: boolean; claimed?: boolean };
type Snapshot = { orders: Order[]; observedAtMs: number; source: string; partial?: boolean };

function orderStatus(order: Order, now: number): string {
  if (order.claimed || order.status === "CLAIMED") return "CLAIMED";
  if (order.bin && (order.highestBid ?? 0) > 0) return "SOLD";
  if (order.endsAtMs !== null && order.endsAtMs !== undefined) return order.endsAtMs <= now ? (order.highestBid ?? 0) > 0 ? "SOLD" : "EXPIRED" : "ACTIVE";
  return order.status;
}

export function OrdersView() {
  const { t, lang } = useI18n(); const ws = useWorkspace();
  const q = useApi<{ ah: Snapshot | null; bazaar: Snapshot | null; ahError: string | null }>("/api/market/orders", undefined, { refetchInterval: 30_000 });
  const [now, setNow] = useState(() => Date.now()), [search, setSearch] = useState(""), [filter, setFilter] = useState("all"), [message, setMessage] = useState("");
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(id); }, []);
  const status = (s: string) => ({ ACTIVE: t("Listed / ordered", "出品中・注文中"), FILLED: t("Filled · ready to claim", "約定済み・受取待ち"), SOLD: t("Sold · unclaimed", "売却済み・未回収"), EXPIRED: t("Expired · unclaimed", "期限切れ・未回収"), CLAIMED: t("Claimed", "回収済み"), UNKNOWN: t("Unknown", "不明") }[s] ?? s);
  const ah = q.data?.ah, active = ah?.orders.filter(o => orderStatus(o, now) === "ACTIVE") ?? [];
  const claimable = ah?.orders.filter(o => ["SOLD", "EXPIRED"].includes(orderStatus(o, now))) ?? [];
  const fresh = !!ah && now - ah.observedAtMs <= 120000 && !q.data?.ahError;
  return <div className="space-y-6">
    <PageHeader title={t("My listings & orders", "自分の出品・注文")} description={`${ws.data?.username ? `${ws.data.username} · ` : ""}${t("AH from the API; Bazaar from your linked mod's last observation. Missing rows do not prove a sale.", "AHはAPI、バザーは連携MODの最終確認から表示。行が消えても売却済みとは判定しません。")}`} actions={<Button variant="outline" disabled={q.isFetching} onClick={() => q.refetch()}><RefreshCw/>{t("Refresh listings", "出品を更新")}</Button>}/>
    {q.isLoading ? <Loading/> : q.error ? <ErrorView error={q.error}/> : <>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4"><Stat title={fresh ? t("Active AH listings", "現在のAH出品") : t("Last observed active listings", "最終確認時の出品")} value={ah ? `${active.length}${t(" listings", "件")}` : "—"}/><Stat title={t("Items ready to claim", "回収できる出品")} value={ah ? `${claimable.length}${t(" listings", "件")}` : "—"}/><Stat title={t("Active listing prices (before tax)", "出品中の表示価格合計（税引前）")} value={ah ? fmtCoins(active.reduce((sum, order) => sum + order.price, 0)) : "—"}/><Stat title={t("Unclaimed sold amount", "売却済みの未回収額")} value={ah && ah.orders.every(o => orderStatus(o, now) !== "SOLD" || o.highestBid !== undefined) ? fmtCoins(ah.orders.filter(o => orderStatus(o, now) === "SOLD").reduce((sum, o) => sum + (o.highestBid ?? 0), 0)) : "—"}/></div>
      <div className="grid gap-3 sm:grid-cols-2"><Input aria-label={t("Search listings", "出品アイテムを検索")} value={search} onChange={e => setSearch(e.target.value)} placeholder={t("Search item name", "アイテム名を検索")}/><NativeSelect aria-label={t("Listing status filter", "出品状態の絞り込み")} value={filter} onChange={e => setFilter(e.target.value)}><option value="all">{t("All states", "すべての状態")}</option>{["ACTIVE", "SOLD", "EXPIRED", "CLAIMED", "FILLED"].map(s => <option key={s} value={s}>{status(s)}</option>)}</NativeSelect></div>
      <p role="status" className="text-sm text-primary">{message}</p>
      {(["ah", "bazaar"] as const).map(kind => {
        const snapshot = q.data?.[kind], stale = !snapshot || now - snapshot.observedAtMs > 120000;
        const rows = snapshot?.orders.filter(o => o.name.toLowerCase().includes(search.toLowerCase()) && (filter === "all" || orderStatus(o, now) === filter)) ?? [];
        return <Card key={kind}><CardContent className="space-y-4 p-4 md:p-6"><div className="flex flex-wrap items-center justify-between gap-2"><h2 className="text-lg font-semibold">{kind === "ah" ? "Auction House" : "Bazaar"}</h2>{snapshot && <Badge tone={stale || (kind === "ah" && q.data?.ahError) ? "warn" : "good"}>{snapshot.source}{snapshot.partial ? t(" · Partial observation", " · 一部の観測") : ""}</Badge>}</div>
          {kind === "ah" && q.data?.ahError && <p className="text-sm text-amber-500">{t("API unavailable; showing the last observation.", "APIに接続できません。前回確認した内容を表示しています。")}</p>}
          {snapshot ? <><p className={`text-xs ${stale ? "text-amber-500" : "text-muted-foreground"}`}>{t("Observed", "最終確認")}: {fmtDate(snapshot.observedAtMs, lang)}{stale ? t(" · Outdated", " · 古い情報") : ""}</p><div className="grid gap-3 xl:grid-cols-2">{rows.map((order, i) => {
            const s = orderStatus(order, now), command = order.auctionId && /^[0-9a-f]{32}$/i.test(order.auctionId) ? `/viewauction ${order.auctionId}` : null;
            return <article key={order.auctionId ?? i} className="min-w-0 space-y-3 rounded-2xl border border-border bg-background/40 p-4"><div className="flex flex-wrap items-start justify-between gap-2"><h3 className="min-w-0 flex-1 break-words text-sm font-semibold">{order.name}</h3><Badge tone={s === "ACTIVE" ? "good" : s === "EXPIRED" ? "warn" : "neutral"}>{status(s)}</Badge></div><div className="flex flex-wrap justify-between gap-2"><p className="text-xl font-semibold tabular-nums">{fmtCoins(order.price)}</p><span className="text-xs text-muted-foreground">{kind === "ah" ? order.bin === undefined ? "AH" : order.bin ? "BIN" : t("Auction", "入札式") : order.side === "BUY" ? t("Buy / unit", "買い注文・単価") : t("Sell / unit", "売り注文・単価")}</span></div>
              {order.highestBid !== undefined && <p className="text-xs text-muted-foreground">{t("Highest bid / paid amount", "最高入札・支払額")}: {fmtCoins(order.highestBid)}</p>}
              {order.endsAtMs !== null && order.endsAtMs !== undefined && <p className="text-xs text-muted-foreground">{t("Ends", "終了時刻")}: {fmtDate(order.endsAtMs, lang)}{s === "ACTIVE" && order.endsAtMs > now ? ` · ${t("Remaining", "残り")} ${fmtHours((order.endsAtMs - now) / 3600000, lang)}` : ""}</p>}
              {command && <><Button variant="outline" size="sm" onClick={async () => { try { await navigator.clipboard.writeText(command); setMessage(t("Command copied. Paste it in Minecraft.", "コマンドをコピーしました。Minecraftで貼り付けてください。")); } catch { setMessage(t("Select and copy the command shown on the card.", "カードのコマンドを選択してコピーしてください。")); } }}><Copy/>{t("Copy auction command", "出品を見るコマンドをコピー")}</Button><p className="break-all font-mono text-[10px] text-muted-foreground">{command}</p></>}
            </article>;
          })}</div>{rows.length === 0 && <p className="text-sm text-muted-foreground">{snapshot.orders.length === 0 ? t("No listings returned by the API.", "APIから返された出品はありません。") : t("No listings match this filter.", "条件に一致する出品はありません。")}</p>}</> : <p className="text-sm text-muted-foreground">{kind === "bazaar" ? t("Link the mod, then open Your Bazaar Orders in-game.", "MODをこのWebアカウントと連携し、ゲーム内でYour Bazaar Ordersを開いてください。") : t("Analyze your profile to connect the player UUID.", "プロフィールを分析してプレイヤーUUIDを連携してください。")}</p>}
        </CardContent></Card>;
      })}
    </>}
  </div>;
}
