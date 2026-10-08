"use client";

import { PageHeader } from "@/components/common";
import { GearPlanner } from "@/components/gear-plan";
import { EmbeddedReport } from "@/components/report";
import { useI18n } from "@/lib/i18n";

export const COMBAT_PURPOSES = [
  { id: "combat", en: "General combat", ja: "戦闘全般" },
  { id: "slayer", en: "Slayer", ja: "スレイヤー" },
  { id: "dungeon", en: "Dungeons (melee/archer)", ja: "ダンジョン（近接・弓）" },
  { id: "f7_mage", en: "Dungeons (mage)", ja: "ダンジョン（メイジ）" },
  { id: "archer", en: "Weapons: archer", ja: "武器：アーチャー" },
  { id: "berserker", en: "Weapons: berserker", ja: "武器：バーサーカー" },
  { id: "tank", en: "Tank / healer", ja: "タンク・ヒーラー" },
  { id: "mining", en: "Mining tools", ja: "採掘ツール" },
  { id: "kuudra", en: "Kuudra", ja: "クードラ" },
  { id: "fishing", en: "Fishing", ja: "釣り" },
  { id: "foraging", en: "Foraging", ja: "伐採" },
  { id: "farming", en: "Farming", ja: "農業" },
];

export function GearView() {
  const { t } = useI18n();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("Gear", "装備")} description={t("Your best owned setups and the upgrades with the most performance per coin.", "所持装備の最適な組み合わせと、コストあたりの性能が高いアップグレードです。")} />
      <GearPlanner purposes={COMBAT_PURPOSES} defaultPurpose="combat" />
      <EmbeddedReport domain="gear" />
      <EmbeddedReport domain="combat" />
    </div>
  );
}
