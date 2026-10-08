"use client";
import Link from "next/link";
import { Card, CardContent } from "@skypilot/ui";
import { Freshness, PageHeader } from "@/components/common";
import { useWorkspace } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

const GUIDES = [
  ["/combat/gear", "Armor & equipment", "防具・装備", "Compare owned slots and upgrades within your budget.", "所持装備をスロット別に比較し、予算内の強化候補を表示。"],
  ["/combat/weapons", "Weapons & classes", "武器・クラス", "Melee, archer, mage and survival loadouts with use requirements.", "近接・弓・魔法・生存向けの構成と使用条件を確認。"],
  ["/pets", "Pets", "ペット", "Owned levels, active pet, activity shortlists and market quotes.", "所持レベル・使用中ペット・用途別の候補・市場価格を比較。"],
  ["/combat/dungeons", "Dungeons", "ダンジョン", "Entry rules, equipped armor, usable weapons and upgrade gaps.", "入場条件・装備中防具・使用可能な武器・不足する強化を確認。"],
  ["/combat/slayer", "Slayers", "スレイヤー", "Boss tiers, required gear and expected drop value.", "ボスのティア・必要装備・ドロップ期待値。"],
  ["/combat/kuudra", "Kuudra", "クードラ", "Reputation and progression prerequisites.", "評判や攻略段階の前提条件。"],
  ["/mining", "Mining", "採掘", "HOTM, powder, tools and target profitability.", "HOTM・パウダー・ツール・採掘対象の収益。"],
  ["/farming", "Farming", "農業", "Crops, fortune and Garden upgrades.", "作物・Fortune・Garden の強化。"],
  ["/fishing", "Fishing", "釣り", "Fishing progression and owned gear.", "釣りの進行と所持装備。"],
  ["/foraging", "Foraging", "伐採", "Progression and tools.", "進行とツール。"],
  ["/accessories", "Accessories", "アクセサリー", "Magical Power and cost-efficient additions.", "Magical Power と費用対効果の高い追加候補。"],
  ["/minions", "Minions", "ミニオン", "Passive income and upgrades at current prices.", "現在価格での放置収入と強化候補。"],
  ["/money", "Money plan", "金策プラン", "Eligible methods, capital planning and wealth projections.", "今できる方法・元手の計画・資産予測。"],
  ["/rift", "Rift", "リフト", "Rift progression.", "リフトの進行。"],
  ["/help/api", "Enable API data", "API 有効化", "A playable visual walkthrough for missing profile data.", "プロフィールデータ不足を解消する再生可能な図解。"],
];
export function GuidesView() {
  const { t } = useI18n(); const { data } = useWorkspace(); const a = data?.lastAnalysis?.analysis;
  return <div className="space-y-6"><PageHeader title={t("Your SkyBlock guides", "あなたの SkyBlock ガイド")} description={t("Guides use your analysed profile, owned items, use requirements and available market quotes. Reanalyse after changing your equipment.", "分析済みプロフィール・所持アイテム・使用条件・取得できた市場価格を使います。装備を変更したら再分析してください。")} actions={a ? <Freshness f={a.fetched} /> : null} />
    <p className="text-sm text-muted-foreground">{t("Prices and item definitions follow the server's resource sync. Readiness and activity shortlists are labelled heuristics, not guarantees; missing data remains unknown.", "価格・アイテム定義はサーバーの同期データを参照します。準備度や用途別候補は明示した推定であり、保証ではありません。欠損データは不明のまま表示します。")}</p>
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{GUIDES.map(([url, en, ja, desc, descJa]) => <Link key={url} href={url}><Card className="h-full transition-colors hover:border-primary"><CardContent className="space-y-2 p-5"><h2 className="font-semibold">{t(en, ja)}</h2><p className="text-sm text-muted-foreground">{t(desc, descJa)}</p></CardContent></Card></Link>)}</div>
  </div>;
}
