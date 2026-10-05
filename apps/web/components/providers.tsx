"use client";

import { useState, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TooltipProvider } from "@skypilot/ui";
import { I18nProvider, type Lang } from "@/lib/i18n";
import type { ApiError } from "@/lib/api";

export function Providers({ lang, children }: { lang: Lang; children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            // Do not hammer the API on deterministic client errors (no analysis, bad input...).
            retry: (count, err) => {
              const status = (err as unknown as ApiError).status ?? 0;
              return count < 2 && (status === 0 || status >= 500);
            },
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <I18nProvider initial={lang}>
        <TooltipProvider>{children}</TooltipProvider>
      </I18nProvider>
    </QueryClientProvider>
  );
}
