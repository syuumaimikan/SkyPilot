import type { Metadata, Viewport } from "next";
import { cookies, headers } from "next/headers";
import type { ReactNode } from "react";
import { Providers } from "@/components/providers";
import { Shell } from "@/components/shell";
import type { Lang } from "@/lib/i18n";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "SkyPilot", template: "%s · SkyPilot" },
  description: "Personal progression operating system for Hypixel SkyBlock: goals, roadmaps, market intelligence and what to do next.",
  applicationName: "SkyPilot",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#14151c" },
    { media: "(prefers-color-scheme: light)", color: "#fbfbfd" },
  ],
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const c = await cookies();
  const h = await headers();
  const cookieLang = c.get("sp_lang")?.value;
  const lang: Lang = cookieLang === "ja" || cookieLang === "en" ? cookieLang : (h.get("accept-language") ?? "").toLowerCase().startsWith("ja") ? "ja" : "en";
  const theme = c.get("sp_theme")?.value === "light" ? "light" : "dark";
  return (
    <html lang={lang} className={theme === "dark" ? "dark" : undefined} suppressHydrationWarning>
      <body>
        <Providers lang={lang}>
          <Shell theme={theme}>{children}</Shell>
        </Providers>
      </body>
    </html>
  );
}
