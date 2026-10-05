// Analysis warnings are stored in English with the analysis (the UI language can change
// without re-analysing); known ones are shown in Japanese here. Unknown texts pass through.

type Rule = [RegExp, (m: RegExpMatchArray) => string];

const JA: Rule[] = [
  [/^Inventory API is disabled/, () => "インベントリAPIが無効のため、装備・アクセサリー・資産は表示できません。SkyBlock の Settings > API Settings で有効にしてください。"],
  [/^(\d+) inventory blob\(s\) could not be decoded$/, (m) => `${m[1]}件のインベントリデータを読み取れませんでした`],
  [/^This member was removed from the profile\.$/, () => "このメンバーはプロフィールから外れています。"],
  [/^Skills API is disabled\.$/, () => "スキルAPIが無効です。"],
  [/^Banking API is disabled/, () => "銀行APIが無効のため、銀行残高は資産に含まれていません。"],
  [
    /^(\d+)% of networth \(([^)]+)\) is valued from auction listings with few or no recorded sales \((.*)\); treat it as an upper bound\.$/,
    (m) => `資産の${m[1]}%（${m[2]}）は取引実績がほとんどない出品価格で評価しています（${m[3]}）。上限値として扱ってください。`,
  ],
  [/^Hypixel API temporarily unavailable\. Last valid snapshot: (\d+)m (\d+)s ago$/, (m) => `Hypixel APIが一時的に利用できません。最後の有効なデータ: ${m[1]}分${m[2]}秒前`],
];

export function localizeWarning(w: string, lang: string): string {
  if (lang !== "ja") return w;
  for (const [re, f] of JA) {
    const m = w.match(re);
    if (m) return f(m);
  }
  return w;
}
