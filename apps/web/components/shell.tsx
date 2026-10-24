"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, Map, Menu, Moon, Plane, Sun, TrendingUp, User, X } from "lucide-react";
import type { MarketStatus } from "@skypilot/protocol";
import { Badge, Button, cn } from "@skypilot/ui";
import { NAV } from "./nav";
import { useI18n } from "@/lib/i18n";
import { useApi, useWorkspace } from "@/lib/hooks";
import { Freshness } from "./common";
import { ApiHelp } from "./api-help";

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const { t } = useI18n();
  return (
    <nav className="flex flex-col gap-5 px-3 py-4 text-sm">
      {NAV.map((g) => (
        <div key={g.en} className="flex flex-col gap-0.5">
          <div className="px-2 pb-1 text-[11px] font-medium tracking-wide text-muted-foreground uppercase">{t(g.en, g.ja)}</div>
          {g.items.map((item) => {
            const active = item.href === "/" ? pathname === "/" : pathname === item.href || pathname.startsWith(item.href + "/");
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "sp-nav-link flex min-h-11 items-center gap-2.5 rounded-xl px-3 py-2 text-[13px] transition-colors md:min-h-9",
                  active ? "bg-accent font-medium text-accent-foreground" : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                )}
              >
                <Icon className="size-4 shrink-0" />
                {t(item.en, item.ja)}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}

function MobileNavigation({ onMenu, menuOpen }: { onMenu: () => void; menuOpen: boolean }) {
  const pathname = usePathname();
  const { t } = useI18n();
  const items = [
    { href: "/", en: "Home", ja: "ホーム", icon: LayoutDashboard },
    { href: "/roadmap", en: "Roadmap", ja: "目標", icon: Map },
    { href: "/market/flips", en: "Market", ja: "相場", icon: TrendingUp },
    { href: "/profile", en: "Profile", ja: "自分", icon: User },
  ];
  return (
    <nav inert={menuOpen} aria-label={t("Main navigation", "メインナビゲーション")} className="sp-mobile-dock fixed inset-x-3 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] z-30 grid grid-cols-5 rounded-3xl border border-border px-1.5 py-1.5 md:hidden">
      {items.map(({ href, en, ja, icon: Icon }) => {
        const active = href === "/" ? pathname === "/" : href === "/market/flips" ? pathname.startsWith("/market/") : pathname === href || pathname.startsWith(href + "/");
        return (
          <Link key={href} href={href} aria-current={active ? "page" : undefined} className={cn("flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl text-[10px] font-medium transition-colors", active ? "bg-primary/15 text-primary" : "text-muted-foreground")}>
            <Icon className="size-5" strokeWidth={active ? 2.3 : 1.7} aria-hidden="true" />
            {t(en, ja)}
          </Link>
        );
      })}
      <button onClick={onMenu} aria-expanded={menuOpen} aria-controls="mobile-menu" className="flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl text-[10px] font-medium text-muted-foreground">
        <Menu className="size-5" aria-hidden="true" />
        {t("More", "その他")}
      </button>
    </nav>
  );
}

function ThemeToggle({ initialDark }: { initialDark: boolean }) {
  const [dark, setDark] = useState(initialDark);
  const toggle = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    document.cookie = `sp_theme=${next ? "dark" : "light"}; path=/; max-age=31536000; samesite=lax`;
  };
  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label="Toggle theme">
      {dark ? <Sun /> : <Moon />}
    </Button>
  );
}

function MarketPill() {
  const { t } = useI18n();
  const { data } = useApi<MarketStatus>("/api/market/status", undefined, { refetchInterval: 30_000 });
  if (!data) return null;
  return (
    <div className="hidden items-center gap-2 text-xs text-muted-foreground lg:flex">
      <span>{t("Auction House", "オークション")}</span>
      <Freshness f={data.auctions} compact />
      <span className="ml-2">{t("Bazaar", "バザー")}</span>
      <Freshness f={data.bazaar} compact />
      {data.hypixelCircuit.toLowerCase() !== "closed" ? <Badge tone="bad">{t("Hypixel API degraded", "Hypixel API 障害中")}</Badge> : null}
    </div>
  );
}

export function Shell({ children, theme }: { children: ReactNode; theme: "dark" | "light" }) {
  const { lang, setLang, t } = useI18n();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const drawer = useRef<HTMLElement>(null);
  const ws = useWorkspace();

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    drawer.current?.querySelector<HTMLButtonElement>("button")?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); setOpen(false); }
      if (event.key !== "Tab") return;
      const elements = drawer.current?.querySelectorAll<HTMLElement>("a[href], button:not([disabled])");
      if (!elements?.length) return;
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    const desktop = window.matchMedia("(min-width: 768px)");
    const onResize = () => { if (desktop.matches) setOpen(false); };
    document.addEventListener("keydown", onKeyDown);
    desktop.addEventListener("change", onResize);
    onResize();
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKeyDown);
      desktop.removeEventListener("change", onResize);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [open]);

  return (
    <div className="sp-shell flex min-h-dvh">
      <aside inert={open} className="sp-sidebar sticky top-0 hidden h-dvh w-64 shrink-0 flex-col overflow-y-auto border-r border-border md:flex">
        <Link href="/" className="flex items-center gap-3 px-5 pt-7 pb-5 text-xl font-semibold tracking-tight">
          <span className="sp-brand-mark flex size-10 items-center justify-center rounded-2xl text-primary-foreground">
            <Plane className="size-5" />
          </span>
          <span>SkyPilot<span className="mt-1 block text-[9px] font-medium tracking-[0.15em] text-muted-foreground">SKYBLOCK COMPANION</span></span>
        </Link>
        <NavLinks />
      </aside>

      {open ? (
        <div className="fixed inset-0 z-40 md:hidden">
          <button tabIndex={-1} aria-label={t("Close menu", "メニューを閉じる")} className="absolute inset-0 bg-black/50" onClick={() => setOpen(false)} />
          <aside ref={drawer} id="mobile-menu" role="dialog" aria-modal="true" aria-label={t("All features", "すべての機能")} className="sp-sidebar absolute inset-y-0 left-0 w-72 overflow-y-auto border-r border-border">
            <div className="flex items-center justify-between px-5 pt-4">
              <span className="font-semibold">SkyPilot</span>
              <Button variant="ghost" size="icon" className="min-h-11 min-w-11" onClick={() => setOpen(false)} aria-label={t("Close menu", "メニューを閉じる")}>
                <X />
              </Button>
            </div>
            <NavLinks onNavigate={() => setOpen(false)} />
          </aside>
        </div>
      ) : null}

      <div inert={open} className="flex min-w-0 flex-1 flex-col">
        <header className="sp-topbar sticky top-0 z-30 flex min-h-16 items-center gap-2 border-b border-border px-3 backdrop-blur-xl md:gap-3 md:px-8">
          <Button variant="ghost" size="icon" className="min-h-11 min-w-11 md:hidden" onClick={() => setOpen(true)} aria-label={t("Open menu", "メニューを開く")} aria-expanded={open} aria-controls="mobile-menu">
            <Menu />
          </Button>
          {ws.data?.username ? (
            <Link href="/profile" className="flex min-w-0 items-center gap-2 text-sm">
              <span className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-xs font-semibold text-primary">{ws.data.username.slice(0, 2).toUpperCase()}</span>
              <span className="truncate font-medium">{ws.data.username}</span>
              {ws.data.lastAnalysis ? <Badge className="hidden sm:inline-flex">{ws.data.lastAnalysis.analysis.cuteName}</Badge> : null}
            </Link>
          ) : (
            <span className="min-w-0 truncate text-sm font-semibold tracking-tight">{t("Your adventure", "あなたの冒険")}</span>
          )}
          <div className="ml-auto flex shrink-0 items-center gap-1 md:gap-2">
            <MarketPill />
            <div className="flex rounded-md border border-border p-0.5 text-xs" role="group" aria-label="Language">
              {(["en", "ja"] as const).map((l) => (
                <button
                  key={l}
                  onClick={() => setLang(l)}
                  aria-pressed={lang === l}
                  className={cn("min-h-9 cursor-pointer rounded px-2 py-0.5 md:min-h-0", lang === l ? "bg-accent font-medium" : "text-muted-foreground")}
                >
                  {l === "en" ? "EN" : "日本語"}
                </button>
              ))}
            </div>
            <ThemeToggle initialDark={theme === "dark"} />
          </div>
        </header>
        <main className="sp-content mx-auto w-full max-w-7xl flex-1 px-4 py-7 md:px-8 md:py-8">{(pathname === "/" || pathname === "/profile") && <ApiHelp access={ws.data?.lastAnalysis?.analysis.apiAccess} />}{children}</main>
        <footer className="border-t border-border px-4 pt-4 pb-[calc(5rem+env(safe-area-inset-bottom))] text-[11px] text-muted-foreground md:px-8 md:pb-4">
          {t(
            "SkyPilot is an information tool. It never plays, buys, sells or moves for you, and is not affiliated with Hypixel or Mojang.",
            "SkyPilot は情報ツールです。プレイ・売買・移動を代行することはなく、Hypixel および Mojang とは無関係です。",
          )}
        </footer>
      </div>
      <MobileNavigation onMenu={() => setOpen(true)} menuOpen={open} />
    </div>
  );
}
