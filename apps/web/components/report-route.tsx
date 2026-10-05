"use client";

import { ReportPage } from "./report";

/** Domain advisor pages that are fully described by an `AdvisorReport`. */
export const REPORT_PAGES = {
  fishing: { title: ["Fishing", "釣り"], description: ["Rods, trophy fish, sea creatures and fishing gear for your profile.", "ロッド・トロフィーフィッシュ・シークリーチャー・釣り装備の分析です。"] },
  foraging: { title: ["Foraging", "伐採"], description: ["Heart of the Forest, axes and foraging gear.", "Heart of the Forest・斧・伐採装備の分析です。"] },
  rift: { title: ["Rift", "リフト"], description: ["Motes, timecharms, Enigma souls and Rift progression.", "Motes・タイムチャーム・エニグマソウルなどリフトの進行度です。"] },
  accessories: { title: ["Accessories & Magical Power", "アクセサリー・魔力"], description: ["Cheapest Magical Power upgrades (coins per MP), recombobulation and missing accessories.", "1MPあたりのコストが安い順に、魔力を上げる方法を表示します。"] },
  pets: { title: ["Pets", "ペット"], description: ["Your pets, levels, held items and what to level next.", "所持ペット・レベル・持ち物と、次に育てるべきペットです。"] },
  minions: { title: ["Minions", "ミニオン"], description: ["Unique minion crafts, slots and payback periods at current prices.", "ユニーククラフト・スロット数・現在価格での回収期間です。"] },
  collections: { title: ["Collections", "コレクション"], description: ["Collection tiers close to their next unlock.", "次の解放が近いコレクションです。"] },
  museum: { title: ["Museum", "博物館"], description: ["Donations, missing items and museum value.", "寄贈状況・未寄贈アイテム・博物館の価値です。"] },
  daily: { title: ["Daily tasks", "デイリー"], description: ["Things worth doing every day for your profile.", "毎日やる価値のあることです。"] },
  events: { title: ["Events", "イベント"], description: ["Upcoming SkyBlock calendar events and the current mayor.", "今後のSkyBlockカレンダーイベントと現在の市長です。"] },
  kuudra: { title: ["Kuudra & Crimson Isle", "クードラ・クリムゾンアイル"], description: ["Faction reputation, tier unlocks and readiness.", "派閥の評判・ティア解放・準備度です。"] },
  combat: { title: ["Combat", "戦闘"], description: ["Effective health from your gear and combat priorities.", "装備から計算した実効HPと戦闘の優先事項です。"] },
} as const satisfies Record<string, { title: [string, string]; description: [string, string] }>;

export function ReportRoute({ domain }: { domain: keyof typeof REPORT_PAGES }) {
  const p = REPORT_PAGES[domain];
  return <ReportPage domain={domain} title={[...p.title]} description={[...p.description]} />;
}
