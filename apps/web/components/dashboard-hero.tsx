"use client";

import { useId } from "react";
import Link from "next/link";
import { ArrowDown, ArrowUpRight, CalendarDays, Coins, Gamepad2, Map, Sparkles, TrendingUp } from "lucide-react";
import { Button } from "@skypilot/ui";
import { useI18n } from "@/lib/i18n";

function FlightIllustration() {
  const id = useId();
  return (
    <svg viewBox="0 0 340 180" className="h-40 w-full max-w-xs" aria-hidden="true">
      <defs>
        <radialGradient id={`${id}-glow`}><stop stopColor="#9280ff" stopOpacity=".3" /><stop offset="1" stopColor="#9280ff" stopOpacity="0" /></radialGradient>
        <linearGradient id={`${id}-planet`} x2="1" y2="1"><stop stopColor="#7976ca" /><stop offset="1" stopColor="#24365e" /></linearGradient>
        <linearGradient id={`${id}-plane`} x2="1" y2="1"><stop stopColor="#f4f6ff" /><stop offset="1" stopColor="#a5b3ff" /></linearGradient>
      </defs>
      <circle cx="175" cy="88" r="90" fill={`url(#${id}-glow)`} />
      <g transform="rotate(-18 170 90)" fill="none" stroke="#8295cb" strokeOpacity=".3"><ellipse cx="170" cy="90" rx="145" ry="65" /><ellipse cx="170" cy="90" rx="115" ry="48" /></g>
      <circle cx="170" cy="88" r="53" fill={`url(#${id}-planet)`} />
      <circle cx="53" cy="112" r="5" fill="#80dae9" /><circle cx="280" cy="64" r="4" fill="#c3b4ff" />
      <g fill="#a7b7d4"><circle cx="49" cy="32" r="1.5" /><circle cx="291" cy="143" r="1.5" /><circle cx="248" cy="20" r="1" /><circle cx="92" cy="158" r="1" /><circle cx="308" cy="101" r="1" /></g>
      <g className="sp-orbit-plane"><path d="M143 97 222 54 198 126 179 101Z" fill={`url(#${id}-plane)`} /><path d="M179 101 222 54 165 93Z" fill="#7574bb" /></g>
    </svg>
  );
}

export function DashboardHero({ username, goals, actions }: { username?: string; goals?: number; actions?: number }) {
  const { t } = useI18n();
  return (
    <section className="sp-hero grid items-center gap-3 p-6 sm:grid-cols-[1fr_180px] md:p-8 lg:grid-cols-[1fr_240px]">
      <div className="min-w-0">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-primary/25 bg-primary/10 px-3 py-1.5 text-[10px] font-semibold tracking-[0.13em] text-primary"><Sparkles className="size-3.5" /> YOUR SKYBLOCK COMPANION</div>
        <h1 className="text-3xl leading-tight font-semibold tracking-tight md:text-4xl">
          {username ? t(`Welcome back, ${username}.`, `おかえり、${username}。`) : t("Your next adventure starts here.", "次の冒険を、ここから。")}
        </h1>
        <p className="mt-3 max-w-lg text-sm leading-relaxed text-muted-foreground">
          {username ? t("Find your next step, build your plan, and make every session count.", "今やるべきことを見つけて、目標までの一歩を進めよう。") : t("Your profile, your goals, your path. Turn SkyBlock progress into a plan you can follow.", "プロフィールも、目標も、相場も。あなたのSkyBlockを、ひとつの場所で。")}
        </p>
        {username ? (
          <div className="mt-5 flex flex-wrap gap-3 text-xs text-muted-foreground"><span className="rounded-full bg-background/60 px-3 py-2">{t("Goals", "目標")} <strong className="ml-1 text-foreground">{goals ?? 0}</strong></span><span className="rounded-full bg-background/60 px-3 py-2">{t("Next actions", "次のアクション")} <strong className="ml-1 text-foreground">{actions ?? 0}</strong></span></div>
        ) : (
          <div className="mt-5 flex flex-wrap gap-2">
            <Button asChild><a href="#profile-analysis">{t("Plan my adventure", "冒険のプランを作る")}<ArrowDown /></a></Button>
            <Button asChild variant="outline"><Link href="/market/flips">{t("Explore the market", "相場を見る")}<ArrowUpRight /></Link></Button>
          </div>
        )}
      </div>
      <div className="hidden justify-center sm:flex"><FlightIllustration /></div>
    </section>
  );
}

export function QuickAccess() {
  const { t } = useI18n();
  const items = [
    { href: "/roadmap", icon: Map, en: "Roadmap", ja: "ロードマップ", detailEn: "Find your next milestone", detailJa: "次のマイルストーンへ", color: "text-violet-400 bg-violet-400/10" },
    { href: "/market/flips", icon: TrendingUp, en: "Market", ja: "マーケット", detailEn: "Spot opportunities", detailJa: "チャンスを見つける", color: "text-cyan-500 dark:text-cyan-300 bg-cyan-400/10" },
    { href: "/money", icon: Coins, en: "Money making", ja: "金策プラン", detailEn: "Make your coins count", detailJa: "自分に合う稼ぎ方", color: "text-amber-600 dark:text-amber-300 bg-amber-400/10" },
    { href: "/daily", icon: CalendarDays, en: "Daily & events", ja: "デイリー・イベント", detailEn: "Keep up with your world", detailJa: "今日の予定を確認", color: "text-emerald-600 dark:text-emerald-300 bg-emerald-400/10" },
    { href: "/play", icon: Gamepad2, en: "Play on phone", ja: "スマホでプレイ", detailEn: "Connect to your PC", detailJa: "PCのMinecraftへ接続", color: "text-violet-400 bg-violet-400/10" },
    { href: "/tools", icon: Sparkles, en: "Play lab", ja: "プレイラボ", detailEn: "Prepare your next run", detailJa: "周回と補給を計画", color: "text-cyan-500 dark:text-cyan-300 bg-cyan-400/10" },
  ];
  return (
    <nav aria-label={t("Quick access", "クイックアクセス")} className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {items.map(({ href, icon: Icon, en, ja, detailEn, detailJa, color }) => (
        <Link key={href} href={href} className="sp-feature-link group flex flex-col rounded-2xl p-4">
          <span className="mb-4 flex items-start justify-between"><span className={`flex size-10 items-center justify-center rounded-xl ${color}`}><Icon className="size-5" strokeWidth={1.7} /></span><ArrowUpRight className="size-4 text-muted-foreground" /></span>
          <span className="text-sm font-semibold">{t(en, ja)}</span><span className="mt-1 text-[11px] text-muted-foreground">{t(detailEn, detailJa)}</span>
        </Link>
      ))}
    </nav>
  );
}
