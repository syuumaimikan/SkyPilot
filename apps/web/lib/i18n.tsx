"use client";

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

export type Lang = "en" | "ja";

interface I18n {
  lang: Lang;
  setLang: (l: Lang) => void;
  /** Pick the string for the current language: t("Updated", "更新"). */
  t: (en: string, ja: string) => string;
}

const Ctx = createContext<I18n | null>(null);

export function I18nProvider({ initial, children }: { initial: Lang; children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initial);
  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    document.cookie = `sp_lang=${l}; path=/; max-age=31536000; samesite=lax`;
    document.documentElement.lang = l;
  }, []);
  const t = useCallback((en: string, ja: string) => (lang === "ja" ? ja : en), [lang]);
  const value = useMemo(() => ({ lang, setLang, t }), [lang, setLang, t]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useI18n(): I18n {
  const v = useContext(Ctx);
  if (!v) throw new Error("useI18n outside I18nProvider");
  return v;
}
