"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import type { MoneyMethodEstimate } from "@skypilot/protocol";
import { Card, CardContent, CardHeader, CardTitle } from "@skypilot/ui";
import { fmtCoins } from "@/lib/format";
import { useI18n } from "@/lib/i18n";
import { useWorkspace } from "@/lib/hooks";
import { deadlineRate, upgradePayback, sessionRate } from "@/lib/money-plan";

type Session = { net: number; minutes: number; at: string };
const subscribeHydration = () => () => {};
export function MoneyLab({methods}:{methods:MoneyMethodEstimate[]}) {
  const mounted=useSyncExternalStore(subscribeHydration,()=>true,()=>false);
  const ws=useWorkspace();
  const key=`skypilot.money.sessions.${ws.data?.lastAnalysis?.analysis.profileId ?? "manual"}`;
  return mounted?<MoneyLabClient key={key} storageKey={key} methods={methods}/>:null;
}
function MoneyLabClient({ methods, storageKey: key }: { methods: MoneyMethodEstimate[]; storageKey:string }) {
  const {t}=useI18n(),ws=useWorkspace(),profile=ws.data?.lastAnalysis?.analysis;
  const [balance,setBalance]=useState(0),[target,setTarget]=useState(100e6),[days,setDays]=useState(7),[hours,setHours]=useState(2),[passive,setPassive]=useState(0),[expense,setExpense]=useState(0),[capital,setCapital]=useState(0),[lowRisk,setLowRisk]=useState(true);
  const [cost,setCost]=useState(0),[before,setBefore]=useState(0),[after,setAfter]=useState(0);
  const [start,setStart]=useState(0),[end,setEnd]=useState(0),[external,setExternal]=useState(0),[minutes,setMinutes]=useState(60);
  const [sessions,setSessions]=useState<Session[]>(()=>{
    try {
      const raw:unknown=JSON.parse(localStorage.getItem(key)??"[]");
      return Array.isArray(raw)?raw.filter((v):v is Session=>!!v&&typeof v==="object"&&typeof v.at==="string"&&Number.isFinite(v.net)&&Number.isFinite(v.minutes)&&v.minutes>0).slice(-100):[];
    } catch {return [];}
  });
  useEffect(()=>{try{localStorage.setItem(key,JSON.stringify(sessions));}catch{/* Calculator works without browser storage. */}},[sessions,key]);
  const deadline=deadlineRate(balance,target,days,hours,passive,expense),payback=upgradePayback(cost,before,after,hours),session=sessionRate(start,end,external,minutes);
  const eligible=methods.filter(m=>m.requirementsMet&&m.coinsPerHour&&m.coinsPerHour.low>0&&m.setupCost!==null&&m.capitalRequired!==null&&m.setupCost+m.capitalRequired<=capital&&m.category!=="Passive"&&!["forge_flipping","kat_flipping","minions"].includes(m.id)&&(!lowRisk||["LOW","VERY_LOW"].includes(m.risk)));
  const ranked=eligible.map(m=>({m,time:(deadline.remaining+(m.setupCost??0))/m.coinsPerHour!.low})).sort((a,b)=>a.time-b.time).slice(0,5);
  const measuredMinutes=sessions.reduce((sum,s)=>sum+s.minutes,0),measuredNet=sessions.reduce((sum,s)=>sum+s.net,0);
  const field=(en:string,ja:string,value:number,set:(v:number)=>void,max=1e15,signed=false)=><label className="text-sm">{t(en,ja)}<input className="mt-1 w-full rounded border border-border bg-background px-3 py-2" type="number" min={signed?-1e15:0} max={max} value={value} onChange={e=>{const n=Number(e.target.value);set(Number.isFinite(n)?Math.max(signed?-1e15:0,Math.min(max,n)):0);}}/></label>;
  return <div className="grid gap-4 lg:grid-cols-2">
    <Card><CardHeader><CardTitle>{t("Deadline & fastest eligible routes","期限から逆算・到達が早い候補")}</CardTitle></CardHeader><CardContent className="space-y-4">
      <div className="grid grid-cols-2 gap-3">{field("Current liquid coins","現在の現金資産",balance,setBalance)}{field("Target","目標額",target,setTarget)}{field("Days left","残り日数",days,setDays,365)}{field("Hours/day","金策時間/日",hours,setHours,24)}{field("Passive net/day","放置の純収入/日",passive,setPassive)}{field("Expenses/day","支出/日",expense,setExpense)}{field("Spendable capital after reserve","残す分を除いた元手",capital,setCapital)}</div>
      <button className="rounded border px-3 py-2 text-sm disabled:opacity-50" disabled={!profile||profile.economy.bank.status!=="known"||profile.economy.personalBank.status!=="known"} onClick={()=>{if(profile){const b=profile.economy.bank,p=profile.economy.personalBank;setBalance(profile.economy.purse+(b.status==="known"?b.value:0)+(p.status==="known"?p.value:0));}}}>{t("Use analysed liquid balances","分析済みの現金残高を使用")}</button>
      <p>{t("Required active net/hour","期限に必要な純時給")}: <b>{deadline.hourly===null?t("Set days and hours","日数と時間を設定してください"):fmtCoins(deadline.hourly)}</b></p>
      <label className="flex gap-2 text-sm"><input type="checkbox" checked={lowRisk} onChange={e=>setLowRisk(e.target.checked)}/>{t("Low-risk candidates only","低リスクの候補のみ")}</label>
      {ranked.length===0?<p className="text-sm text-muted-foreground">{t("No priced eligible route within capital. Check requirements or enter your available capital.","元手内で費用と時給が判明した実行可能な候補がありません。条件と元手を確認してください。")}</p>:<ol className="space-y-3">{ranked.map(({m,time},i)=><li key={m.id} className="rounded border p-3 text-sm"><Link href={m.link??"/money"} className="underline">{i+1}. {m.name}</Link><p>{t("Lower hourly estimate","時給下限")}: {fmtCoins(m.coinsPerHour!.low)} · {t("Work hours incl. setup recovery","初期費用回収を含む作業時間")}: {time.toFixed(1)}h</p><p className="text-xs text-muted-foreground">{t("Confidence","信頼度")}: {Math.round(m.confidence.score*100)}% · {m.risk} · {m.why}</p></li>)}</ol>}
      <p className="text-xs text-muted-foreground">{t("Route hours exclude passive income and setup time. Prices, sales and skill vary; the deadline is a scenario, not a guarantee.","候補の作業時間は放置収入・初期準備時間を含みません。価格・約定・腕前で変動するため、期限内達成の保証ではありません。")}</p>
    </CardContent></Card>
    <Card><CardHeader><CardTitle>{t("Upgrade cost recovery","装備投資の回収時間")}</CardTitle></CardHeader><CardContent className="space-y-4"><div className="grid grid-cols-2 gap-3">{field("Upgrade cost","買い替えの純費用",cost,setCost)}{field("Current net/hour","現在の純時給",before,setBefore)}{field("After upgrade net/hour","更新後の純時給",after,setAfter)}</div><p>{t("Additional coins/hour","増える純時給")}: {fmtCoins(payback.gain)}</p><p>{t("Break even","回収まで")}: {payback.hours===null?t("No positive income improvement","収益改善がないため回収不可"):`${payback.hours.toFixed(1)}h${payback.days===null?"":` / ${payback.days.toFixed(1)} ${t("days","日")}`}`}</p><p className="text-xs text-muted-foreground">{t("Enter measured net rates. Upgrade abilities are not simulated; avoid a purchase whose recovery exceeds your deadline.","実測した純時給を入力してください。特殊能力の効果は自動計算しません。回収が期限を超える投資を見分けられます。")}</p>
      <CardTitle>{t("Measured sessions & income journal","実測時給・収益記録")}</CardTitle><div className="grid grid-cols-2 gap-3">{field("Starting total coins","開始時の現金合計",start,setStart)}{field("Ending total coins","終了時の現金合計",end,setEnd)}{field("Net external transfers (+in / −out)","金策外の移動・受取（入＋/出−）",external,setExternal,1e15,true)}{field("Minutes worked","作業時間（分）",minutes,setMinutes,1440)}</div>
      <p>{t("Session net","今回の純収入")}: {fmtCoins(session.net)} · {t("Measured hourly","実測時給")}: {session.hourly===null?"—":fmtCoins(session.hourly)}</p>
      <button className="rounded border px-3 py-2 text-sm disabled:opacity-50" disabled={minutes<=0} onClick={()=>setSessions(s=>[...s,{net:session.net,minutes,at:new Date().toISOString()}].slice(-100))}>{t("Save session locally","今回をこのブラウザーに記録")}</button>
      <p className="text-sm">{sessions.length} {t("sessions","件")} · {t("Net total","純収入合計")}: {fmtCoins(measuredNet)} · {t("Weighted hourly","時間加重の実測時給")}: {measuredMinutes?fmtCoins(measuredNet*60/measuredMinutes):"—"}</p>
      <div className="flex gap-3 text-sm"><button className="underline" disabled={measuredMinutes<=0} onClick={()=>setBefore(Math.max(0,measuredNet*60/measuredMinutes))}>{t("Use measured rate for payback","実測時給を投資回収に使用")}</button><button className="underline" disabled={!sessions.length} onClick={()=>setSessions(s=>s.slice(0,-1))}>{t("Undo last record","最後の記録を取り消す")}</button></div>
      <p className="text-xs text-muted-foreground">{t("Use purse + all banks consistently. Transfers between your own balances do not count as profit. Subtract gifts and unrelated sales; include supplies and repair spending. Up to 100 sessions per profile are stored locally.","財布と銀行の合計を同じ基準で入力。自分の銀行移動は利益に含めません。贈与や金策外の売却は差し引き、消耗品費を含めます。プロフィール別に最新100件をローカル保存します。")}</p>
    </CardContent></Card>
  </div>;
}
