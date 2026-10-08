"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, Moon, Plane, Sun, X } from "lucide-react";
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
                  "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] transition-colors",
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
  const [open, setOpen] = useState(false);
  const ws = useWorkspace();

  return (
    <div className="flex min-h-dvh">
      <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 flex-col overflow-y-auto border-r border-border bg-sidebar md:flex">
        <Link href="/" className="flex items-center gap-2 px-5 pt-5 pb-1 text-[15px] font-semibold tracking-tight">
          <span className="flex size-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Plane className="size-3.5" />
          </span>
          SkyPilot
        </Link>
        <NavLinks />
      </aside>

      {open ? (
        <div className="fixed inset-0 z-40 md:hidden">
          <button aria-label="Close menu" className="absolute inset-0 bg-black/50" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-64 overflow-y-auto border-r border-border bg-sidebar">
            <div className="flex items-center justify-between px-5 pt-4">
              <span className="font-semibold">SkyPilot</span>
              <Button variant="ghost" size="icon" onClick={() => setOpen(false)} aria-label="Close menu">
                <X />
              </Button>
            </div>
            <NavLinks onNavigate={() => setOpen(false)} />
          </aside>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-12 items-center gap-3 border-b border-border bg-background/85 px-4 backdrop-blur">
          <Button variant="ghost" size="icon" className="md:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu />
          </Button>
          {ws.data?.username ? (
            <Link href="/profile" className="flex items-center gap-2 text-sm">
              <span className="font-medium">{ws.data.username}</span>
              {ws.data.lastAnalysis ? <Badge>{ws.data.lastAnalysis.analysis.cuteName}</Badge> : null}
            </Link>
          ) : (
            <span className="text-sm text-muted-foreground">{t("No profile analysed yet", "まだプロフィールを分析していません")}</span>
          )}
          <div className="ml-auto flex items-center gap-2">
            <MarketPill />
            <div className="flex rounded-md border border-border p-0.5 text-xs" role="group" aria-label="Language">
              {(["en", "ja"] as const).map((l) => (
                <button
                  key={l}
                  onClick={() => setLang(l)}
                  aria-pressed={lang === l}
                  className={cn("cursor-pointer rounded px-2 py-0.5", lang === l ? "bg-accent font-medium" : "text-muted-foreground")}
                >
                  {l === "en" ? "EN" : "日本語"}
                </button>
              ))}
            </div>
            <ThemeToggle initialDark={theme === "dark"} />
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 md:px-8"><ApiHelp access={ws.data?.lastAnalysis?.analysis.apiAccess} />{children}</main>
        <footer className="border-t border-border px-8 py-4 text-[11px] text-muted-foreground">
          {t(
            "SkyPilot is an information tool. It never plays, buys, sells or moves for you, and is not affiliated with Hypixel or Mojang.",
            "SkyPilot は情報ツールです。プレイ・売買・移動を代行することはなく、Hypixel および Mojang とは無関係です。",
          )}
        </footer>
      </div>
    </div>
  );
}
