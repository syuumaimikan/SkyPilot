"use client";
import { GearPlanner } from "@/components/gear-plan";
import { EmbeddedReport } from "@/components/report";
import { PageHeader } from "@/components/common";
import { useI18n } from "@/lib/i18n";
export function WeaponsView() {
  const { t } = useI18n();
  return <div className="space-y-6"><PageHeader title={t("Weapons & loadouts", "武器・構成ガイド")} description={t("Compare usable owned weapons and armour by role. Suggested purchases respect your budget and verified requirements; synergy and abilities need in-game comparison.", "役割ごとに使用可能な所持武器と防具を比較。購入候補は予算と確認済みの使用条件を考慮します。相性や能力はゲーム内で比較してください。")}/><GearPlanner defaultPurpose="berserker" purposes={[{id:"berserker",en:"Melee",ja:"近接"},{id:"archer",en:"Archer",ja:"弓"},{id:"f7_mage",en:"Mage",ja:"魔法"},{id:"tank",en:"Survival",ja:"生存"}]} /><EmbeddedReport domain="combat"/><EmbeddedReport domain="gear"/></div>;
}
