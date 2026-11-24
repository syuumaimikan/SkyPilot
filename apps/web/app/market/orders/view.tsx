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
type Snapshot = { orders: Order[]; observedAtMs: number; source: string; partial?: boolean; claimedHidden?: number };
type Filter = "listed" | "claim" | "all" | "ACTIVE" | "SOLD" | "EXPIRED" | "FILLED";

/** Same rules as the server, re-evaluated every second so a listing moves when it ends. */
function orderStatus(order: Order, now: number): string {
  if (order.claimed || order.status === "CLAIMED") return "CLAIMED";
  if (order.bin && (order.highestBid ?? 0) > 0) return "SOLD";
  if (order.endsAtMs !== null && order.endsAtMs !== undefined) return order.endsAtMs <= now ? (order.highestBid ?? 0) > 0 ? "SOLD" : "EXPIRED" : "ACTIVE";
  return order.status;
}

/** Still on the market (shown as a listing) vs. finished and waiting to be collected in game. */
const LISTED = new Set(["ACTIVE", "UNKNOWN"]);
const TO_CLAIM = new Set(["SOLD", "EXPIRED", "FILLED"]);

function matches(s: string, filter: Filter): boolean {
  if (filter === "listed") return LISTED.has(s);
  if (filter === "claim") return TO_CLAIM.has(s);
  if (filter === "all") return s !== "CLAIMED";
  return s === filter;
}

export function OrdersView() {
  const { t, lang } = useI18n();
  const ws = useWorkspace();
  const q = useApi<{ ah: Snapshot | null; bazaar: Snapshot | null; ahError: string | null }>("/api/market/orders", undefined, { refetchInterval: 30_000 });
  const [now, setNow] = useState(() => Date.now());
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<Filter>("listed");
  const [message, setMessage] = useState("");
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const status = (s: string) =>
    ({
      ACTIVE: t("Listed / ordered", "出品中・注文中"),
      FILLED: t("Filled · ready to claim", "約定済み・受取待ち"),
      SOLD: t("Sold · collect in game", "売却済み・ゲーム内で回収"),
      EXPIRED: t("Expired · collect in game", "期限切れ・ゲーム内で回収"),
      CLAIMED: t("Claimed", "回収済み"),
      UNKNOWN: t("Unknown", "不明"),
    })[s] ?? s;
  const ah = q.data?.ah;
  const ahOrders = ah?.orders.filter((o) => orderStatus(o, now) !== "CLAIMED") ?? [];
  const active = ahOrders.filter((o) => orderStatus(o, now) === "ACTIVE");
  const claimable = ahOrders.filter((o) => TO_CLAIM.has(orderStatus(o, now)));
  const sold = ahOrders.filter((o) => orderStatus(o, now) === "SOLD");
  const fresh = !!ah && now - ah.observedAtMs <= 120000 && !q.data?.ahError;

  const card = (order: Order, i: number, kind: "ah" | "bazaar") => {
    const s = orderStatus(order, now);
    const command = order.auctionId && /^[0-9a-f]{32}$/i.test(order.auctionId) ? `/viewauction ${order.auctionId}` : null;
    const done = TO_CLAIM.has(s);
    return (
      <article key={order.auctionId ?? `${order.name}-${i}`} className={`min-w-0 space-y-3 rounded-2xl border border-border p-4 ${done ? "bg-muted/30" : "bg-background/40"}`}>
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h3 className="min-w-0 flex-1 break-words text-sm font-semibold">{order.name}</h3>
          <Badge tone={s === "ACTIVE" ? "good" : s === "EXPIRED" ? "warn" : s === "SOLD" || s === "FILLED" ? "info" : "neutral"}>{status(s)}</Badge>
        </div>
        <div className="flex flex-wrap justify-between gap-2">
          <p className="text-xl font-semibold tabular-nums">{fmtCoins(s === "SOLD" && order.highestBid ? order.highestBid : order.price)}</p>
          <span className="text-xs text-muted-foreground">
            {kind === "ah" ? (order.bin === undefined ? "AH" : order.bin ? "BIN" : t("Auction", "入札式")) : order.side === "BUY" ? t("Buy / unit", "買い注文・単価") : t("Sell / unit", "売り注文・単価")}
          </span>
        </div>
        {order.highestBid !== undefined && order.highestBid > 0 && s !== "SOLD" ? (
          <p className="text-xs text-muted-foreground">
            {t("Highest bid", "最高入札")}: {fmtCoins(order.highestBid)}
          </p>
        ) : null}
        {s === "SOLD" && order.highestBid ? (
          <p className="text-xs text-muted-foreground">
            {t("Listed at", "出品価格")} {fmtCoins(order.price)} · {t("collect the coins in /ah → Manage Auctions", "/ah →「Manage Auctions」でコインを回収")}
          </p>
        ) : null}
        {order.endsAtMs !== null && order.endsAtMs !== undefined ? (
          <p className="text-xs text-muted-foreground">
            {t("Ends", "終了時刻")}: {fmtDate(order.endsAtMs, lang)}
            {s === "ACTIVE" && order.endsAtMs > now ? ` · ${t("Remaining", "残り")} ${fmtHours((order.endsAtMs - now) / 3600000, lang)}` : ""}
          </p>
        ) : null}
        {command && !done ? (
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(command);
                  setMessage(t("Command copied. Paste it in Minecraft.", "コマンドをコピーしました。Minecraftで貼り付けてください。"));
                } catch {
                  setMessage(t("Select and copy the command shown on the card.", "カードのコマンドを選択してコピーしてください。"));
                }
              }}
            >
              <Copy />
              {t("Copy auction command", "出品を見るコマンドをコピー")}
            </Button>
            <p className="break-all font-mono text-[10px] text-muted-foreground">{command}</p>
          </>
        ) : null}
      </article>
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={t("My listings & orders", "自分の出品・注文")}
        description={`${ws.data?.username ? `${ws.data.username} · ` : ""}${t(
          "AH from the API; Bazaar from your linked mod's last observation. Sold or expired listings move to “Collect in game”; collected ones disappear.",
          "AHはAPI、バザーは連携MODの最終確認から表示。売却済み・期限切れの出品は「ゲーム内で回収」に移り、回収済みのものは表示しません。",
        )}`}
        actions={
          <Button variant="outline" disabled={q.isFetching} onClick={() => q.refetch()}>
            <RefreshCw />
            {t("Refresh listings", "出品を更新")}
          </Button>
        }
      />
      {q.isLoading ? (
        <Loading />
      ) : q.error ? (
        <ErrorView error={q.error} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat title={fresh ? t("Active AH listings", "現在のAH出品") : t("Last observed active listings", "最終確認時の出品")} value={ah ? `${active.length}${t(" listings", "件")}` : "—"} />
            <Stat title={t("Waiting to be collected", "ゲーム内で回収待ち")} value={ah ? `${claimable.length}${t(" listings", "件")}` : "—"} />
            <Stat title={t("Active listing prices (before tax)", "出品中の表示価格合計（税引前）")} value={ah ? fmtCoins(active.reduce((sum, order) => sum + order.price, 0)) : "—"} />
            <Stat title={t("Sold, coins not collected yet", "売却済みの未回収額")} value={ah ? fmtCoins(sold.reduce((sum, o) => sum + (o.highestBid ?? 0), 0)) : "—"} />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Input aria-label={t("Search listings", "出品アイテムを検索")} value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t("Search item name", "アイテム名を検索")} />
            <NativeSelect aria-label={t("Listing status filter", "出品状態の絞り込み")} value={filter} onChange={(e) => setFilter(e.target.value as Filter)}>
              <option value="listed">{t("Still listed (default)", "出品中のみ（標準）")}</option>
              <option value="claim">{t("Collect in game", "ゲーム内で回収待ち")}</option>
              <option value="all">{t("All not yet collected", "未回収すべて")}</option>
              {(["ACTIVE", "SOLD", "EXPIRED", "FILLED"] as const).map((s) => (
                <option key={s} value={s}>
                  {status(s)}
                </option>
              ))}
            </NativeSelect>
          </div>
          <p role="status" className="text-sm text-primary">
            {message}
          </p>
          {(["ah", "bazaar"] as const).map((kind) => {
            const snapshot = q.data?.[kind];
            const stale = !snapshot || now - snapshot.observedAtMs > 120000;
            const named = snapshot?.orders.filter((o) => o.name.toLowerCase().includes(search.toLowerCase())) ?? [];
            const rows = named.filter((o) => matches(orderStatus(o, now), filter));
            // in the default view, finished rows sit in their own collapsed list instead of mixing with listings
            const finished = filter === "listed" ? named.filter((o) => TO_CLAIM.has(orderStatus(o, now))) : [];
            return (
              <Card key={kind}>
                <CardContent className="space-y-4 p-4 md:p-6">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h2 className="text-lg font-semibold">{kind === "ah" ? "Auction House" : "Bazaar"}</h2>
                    {snapshot && (
                      <Badge tone={stale || (kind === "ah" && q.data?.ahError) ? "warn" : "good"}>
                        {snapshot.source}
                        {snapshot.partial ? t(" · Partial observation", " · 一部の観測") : ""}
                      </Badge>
                    )}
                  </div>
                  {kind === "ah" && q.data?.ahError && <p className="text-sm text-amber-500">{t("API unavailable; showing the last observation.", "APIに接続できません。前回確認した内容を表示しています。")}</p>}
                  {snapshot ? (
                    <>
                      <p className={`text-xs ${stale ? "text-amber-500" : "text-muted-foreground"}`}>
                        {t("Observed", "最終確認")}: {fmtDate(snapshot.observedAtMs, lang)}
                        {stale ? t(" · Outdated", " · 古い情報") : ""}
                        {snapshot.claimedHidden ? ` · ${t(`${snapshot.claimedHidden} collected auction(s) hidden`, `回収済み ${snapshot.claimedHidden} 件は非表示`)}` : ""}
                      </p>
                      <div className="grid gap-3 xl:grid-cols-2">{rows.map((o, i) => card(o, i, kind))}</div>
                      {rows.length === 0 && (
                        <p className="text-sm text-muted-foreground">
                          {snapshot.orders.length === 0
                            ? t("No listings returned by the API.", "APIから返された出品はありません。")
                            : filter === "listed"
                              ? t("Nothing is listed right now.", "現在出品中のものはありません。")
                              : t("No listings match this filter.", "条件に一致する出品はありません。")}
                        </p>
                      )}
                      {finished.length > 0 && (
                        <details className="rounded-xl border border-border p-3">
                          <summary className="cursor-pointer text-sm font-semibold">
                            {kind === "ah" ? t("Sold / expired — collect in game", "売却済み・期限切れ（ゲーム内で回収）") : t("Filled — collect in game", "約定済み（ゲーム内で受け取り）")} · {finished.length}
                          </summary>
                          <p className="mt-2 text-xs text-muted-foreground">
                            {kind === "ah"
                              ? t(
                                  "Hypixel keeps a finished auction until you collect its coins or item in /ah → Manage Auctions. After you collect it, it disappears here within a few minutes.",
                                  "終了した出品は、ゲーム内の /ah →「Manage Auctions」でコイン（またはアイテム）を回収するまでHypixelに残ります。回収すると数分以内にここから消えます。",
                                )
                              : t("Collect filled orders in /bz → Manage Orders.", "約定した注文は /bz →「Manage Orders」で受け取ってください。")}
                          </p>
                          <div className="mt-3 grid gap-3 xl:grid-cols-2">{finished.map((o, i) => card(o, i, kind))}</div>
                        </details>
                      )}
                    </>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      {kind === "bazaar"
                        ? t("Link the mod, then open Your Bazaar Orders in-game.", "MODをこのWebアカウントと連携し、ゲーム内でYour Bazaar Ordersを開いてください。")
                        : t("Analyze your profile to connect the player UUID.", "プロフィールを分析してプレイヤーUUIDを連携してください。")}
                    </p>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </>
      )}
    </div>
  );
}
