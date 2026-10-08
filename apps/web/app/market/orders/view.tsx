"use client";
import { Card, CardContent, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { PageHeader, Loading, ErrorView } from "@/components/common";
import { useApi, useAgeSeconds } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { fmtCoins } from "@/lib/format";
type Order={name:string;side:string;price:number;status:string};
type Snapshot={orders:Order[];observedAtMs:number;source:string};
export function OrdersView(){
  const {t}=useI18n();
  const q=useApi<{ah:Snapshot|null;bazaar:Snapshot|null;ahError:string|null}>("/api/market/orders",undefined,{refetchInterval:30_000});
  const ahAge=useAgeSeconds(q.data?.ah?.observedAtMs);
  const bazaarAge=useAgeSeconds(q.data?.bazaar?.observedAtMs);
  const status=(s:string)=>({ACTIVE:t("Active","注文中・出品中"),FILLED:t("Filled — ready to claim","約定済み・受取待ち"),SOLD:t("Sold","売却済み"),EXPIRED:t("Expired, unsold","期限切れ・未売却"),UNKNOWN:t("Unknown","不明")}[s]??s);
  return <div className="flex flex-col gap-6">
    <PageHeader title={t("My listings & orders","自分の出品・注文")} description={t("AH updates from the API. Bazaar shows the last order page observed by your linked mod. Missing rows do not prove a sale.","AHはAPIで更新。バザーは連携したMODで最後に開いた注文ページを表示します。行が消えても売却済みとは判定しません。")}/>
    {q.isLoading?<Loading/>:q.error?<ErrorView error={q.error}/>:(["ah","bazaar"] as const).map(kind=>{
      const s=q.data?.[kind];const stale=(kind==="ah"?ahAge:bazaarAge)===null||(kind==="ah"?ahAge!:bazaarAge!)>120;
      return <Card key={kind}><CardContent className="p-4 space-y-3">
        <h2 className="font-semibold">{kind==="ah"?"Auction House":"Bazaar"}</h2>
        {kind==="ah"&&q.data?.ahError?<p className="text-amber-500">{t("API unavailable; showing previous observation","APIに接続できません。前回確認した内容です。")}</p>:null}
        {s?<><p className={stale?"text-amber-500":"text-muted-foreground"}>{t("Last observed: ","最終確認: ")}{new Date(s.observedAtMs).toLocaleString()}{stale?t(" (outdated)","（古い情報）"):""}</p>
          <Table><THead><TR><TH>{t("Item","アイテム")}</TH><TH>{t("Side","種類")}</TH><TH>{kind==="bazaar"?t("Price/unit","単価"):t("Listing price","出品価格")}</TH><TH>{t("Status","状態")}</TH></TR></THead><TBody>{s.orders.map((o,i)=><TR key={i}><TD>{o.name}</TD><TD>{o.side==="BUY"?t("Buy","買い注文"):t("Sell","売り")}</TD><TD>{fmtCoins(o.price)}</TD><TD>{status(o.status)}</TD></TR>)}</TBody></Table>
          {s.orders.length===0?<p>{t("No listings returned by the API.","APIから返された出品はありません。")}</p>:null}</>:<p>{kind==="bazaar"?t("Link the mod to this Web account, then open Your Bazaar Orders in game.","MODをこのWebアカウントと連携し、ゲーム内でYour Bazaar Ordersを開いてください。"):t("Analyze your Minecraft profile to connect your UUID.","Minecraftプロフィールを分析してUUIDを連携してください。")}</p>}
      </CardContent></Card>;
    })}
  </div>;
}
