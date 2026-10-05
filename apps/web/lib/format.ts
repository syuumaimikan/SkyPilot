import type { Lang } from "./i18n";

export function fmtCoins(v: number | null | undefined): string {
  if (v === null || v === undefined || !Number.isFinite(v)) return "—";
  const a = Math.abs(v);
  const sign = v < 0 ? "-" : "";
  if (a >= 1e12) return `${sign}${(a / 1e12).toFixed(2)}T`;
  if (a >= 1e9) return `${sign}${(a / 1e9).toFixed(2)}B`;
  if (a >= 1e6) return `${sign}${(a / 1e6).toFixed(a >= 1e8 ? 0 : a >= 1e7 ? 1 : 2)}M`;
  if (a >= 1e3) return `${sign}${(a / 1e3).toFixed(a >= 1e5 ? 0 : 1)}k`;
  return `${sign}${a.toFixed(a >= 100 ? 0 : 1)}`;
}

export function fmtNum(v: number | null | undefined, digits = 0): string {
  if (v === null || v === undefined || !Number.isFinite(v)) return "—";
  return v.toLocaleString("en-US", { maximumFractionDigits: digits, minimumFractionDigits: 0 });
}

export function fmtPct(v: number | null | undefined, digits = 1): string {
  if (v === null || v === undefined || !Number.isFinite(v)) return "—";
  return `${(v * 100).toFixed(digits)}%`;
}

export function fmtHours(h: number | null | undefined, lang: Lang): string {
  if (h === null || h === undefined || !Number.isFinite(h)) return "—";
  if (h < 1 / 60) return lang === "ja" ? "1分未満" : "<1 min";
  if (h < 1) return lang === "ja" ? `${Math.round(h * 60)}分` : `${Math.round(h * 60)} min`;
  if (h < 48) return lang === "ja" ? `${h.toFixed(h < 10 ? 1 : 0)}時間` : `${h.toFixed(h < 10 ? 1 : 0)} h`;
  return lang === "ja" ? `${(h / 24).toFixed(1)}日` : `${(h / 24).toFixed(1)} days`;
}

export function fmtMinutesRange(r: { low: number; high: number } | null | undefined, lang: Lang): string {
  if (!r) return "—";
  return `${fmtHours(r.low / 60, lang)} – ${fmtHours(r.high / 60, lang)}`;
}

export function fmtAge(seconds: number, lang: Lang): string {
  const s = Math.max(0, Math.round(seconds));
  if (lang === "ja") {
    if (s < 60) return `${s}秒前に更新`;
    if (s < 3600) return `${Math.floor(s / 60)}分${s % 60}秒前に更新`;
    if (s < 86400) return `${Math.floor(s / 3600)}時間前に更新`;
    return `${Math.floor(s / 86400)}日前に更新`;
  }
  if (s < 60) return `Updated ${s} second${s === 1 ? "" : "s"} ago`;
  if (s < 3600) return `Updated ${Math.floor(s / 60)}m ${s % 60}s ago`;
  if (s < 86400) return `Updated ${Math.floor(s / 3600)}h ago`;
  return `Updated ${Math.floor(s / 86400)}d ago`;
}

export function fmtDate(ms: number, lang: Lang): string {
  return new Date(ms).toLocaleString(lang === "ja" ? "ja-JP" : "en-US", { dateStyle: "medium", timeStyle: "short" });
}

export function titleCase(s: string): string {
  return s
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}

/** Minecraft-style rarity colour (Tailwind classes). */
export function rarityClass(rarity: string | null | undefined): string {
  switch ((rarity ?? "").toUpperCase()) {
    case "COMMON":
      return "text-zinc-500 dark:text-zinc-300";
    case "UNCOMMON":
      return "text-emerald-600 dark:text-emerald-400";
    case "RARE":
      return "text-blue-600 dark:text-blue-400";
    case "EPIC":
      return "text-purple-600 dark:text-purple-400";
    case "LEGENDARY":
      return "text-amber-600 dark:text-amber-400";
    case "MYTHIC":
      return "text-pink-600 dark:text-pink-400";
    case "DIVINE":
      return "text-cyan-600 dark:text-cyan-300";
    case "SPECIAL":
    case "VERY_SPECIAL":
      return "text-red-600 dark:text-red-400";
    default:
      return "";
  }
}
