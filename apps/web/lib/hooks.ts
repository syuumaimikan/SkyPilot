"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient, type UseQueryOptions } from "@tanstack/react-query";
import type { AdvisorReport, AnalyzeRequest, AnalyzeResponse, GoalPriority, Me, WorkspaceState } from "@skypilot/protocol";
import { api, fetchMe, type ApiError } from "./api";
import { useI18n } from "./i18n";

type Q = Record<string, string | number | boolean | null | undefined>;

/** GET helper bound to the current language (the API localises advisor text). */
export function useApi<T>(path: string | null, query?: Q, opts?: Omit<UseQueryOptions<T, ApiError>, "queryKey" | "queryFn">) {
  const { lang } = useI18n();
  return useQuery<T, ApiError>({
    queryKey: [path, lang, query ?? {}],
    queryFn: () => api.get<T>(path as string, { lang, ...query }),
    enabled: path !== null && (opts?.enabled ?? true),
    ...opts,
  });
}

export function useMe() {
  return useQuery<Me, ApiError>({ queryKey: ["me"], queryFn: () => fetchMe(true), staleTime: 60_000 });
}

export function useWorkspace() {
  return useQuery<WorkspaceState, ApiError>({ queryKey: ["workspace"], queryFn: () => api.get<WorkspaceState>("/api/workspace"), staleTime: 30_000 });
}

export function useReport(domain: string, query?: Q) {
  return useApi<AdvisorReport>(`/api/advisor/report/${domain}`, query, { staleTime: 30_000 });
}

/** Invalidate everything that depends on the analysed profile or goals. */
export function useInvalidateProfile() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ predicate: (q) => q.queryKey[0] !== "me" });
}

export function useAnalyze() {
  const { lang } = useI18n();
  const invalidate = useInvalidateProfile();
  return useMutation<AnalyzeResponse, ApiError, AnalyzeRequest>({
    mutationFn: (req) => api.post<AnalyzeResponse>("/api/analyze", req, { lang }),
    onSuccess: () => invalidate(),
  });
}

export function useSetGoals() {
  const { lang } = useI18n();
  const invalidate = useInvalidateProfile();
  return useMutation<AnalyzeResponse, ApiError, { text: string; priority: GoalPriority }[]>({
    mutationFn: (goals) => api.put<AnalyzeResponse>("/api/workspace/goals", goals, { lang }),
    onSuccess: () => invalidate(),
  });
}

/** Seconds since `ms`, re-rendering every second (for "Updated N seconds ago"). */
export function useAgeSeconds(ms: number | null | undefined): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  if (!ms) return null;
  return Math.max(0, (now - ms) / 1000);
}
