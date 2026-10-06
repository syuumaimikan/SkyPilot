"use client";

import type { ModCalendar, ModIncident, ModMaintenance, ModServerStatus } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardHeader, CardTitle } from "@skypilot/ui";
import { ExternalLink } from "lucide-react";
import { useEffect, useState } from "react";
import { ErrorView, Loading } from "@/components/common";
import { fmtDate } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

type Tone = "good" | "warn" | "bad" | "neutral";

/** Re-renders every `ms` so countdowns stay current. */
function useNow(ms = 15_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(id);
  }, [ms]);
  return now;
}

function fmtSpan(ms: number): string {
  const m = Math.max(0, Math.round(ms / 60_000));
  const d = Math.floor(m / 1440);
  const h = Math.floor((m % 1440) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${String(m % 60).padStart(2, "0")}m`;
  return `${m}m`;
}

function serviceLabel(status: string, t: (en: string, ja: string) => string): [string, Tone] {
  switch (status) {
    case "operational":
      return [t("Online", "稼働中"), "good"];
    case "degraded_performance":
      return [t("Slow", "遅延"), "warn"];
    case "under_maintenance":
      return [t("Maintenance", "メンテナンス中"), "warn"];
    case "partial_outage":
      return [t("Partial outage", "一部障害"), "bad"];
    case "major_outage":
      return [t("Outage", "障害"), "bad"];
    default:
      return [t("Unknown", "不明"), "neutral"];
  }
}

const running = (m: ModMaintenance) => m.status === "in_progress" || m.status === "verifying";

function StatusLink({ href }: { href: string }) {
  const { t } = useI18n();
  if (!href.startsWith("https://status.hypixel.net/")) return null;
  return (
    <a href={href} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-primary hover:underline">
      {t("Details", "詳細")} <ExternalLink className="size-3" />
    </a>
  );
}

function MaintenanceRow({ m, now }: { m: ModMaintenance; now: number }) {
  const { t, lang } = useI18n();
  const live = running(m);
  return (
    <li className="flex flex-col gap-1 rounded-md border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{m.name}</span>
        <Badge tone={live ? "bad" : m.status === "completed" ? "neutral" : "warn"}>
          {live ? t("In progress", "実施中") : m.status === "completed" ? t("Finished", "終了") : t("Scheduled", "予定")}
        </Badge>
        {!m.skyblock && m.services.length > 0 ? <span className="text-xs text-muted-foreground">{m.services.join(", ")}</span> : null}
      </div>
      <div className="text-sm">
        {m.startsMs !== null ? fmtDate(m.startsMs, lang) : "?"} – {m.endsMs !== null ? fmtDate(m.endsMs, lang) : "?"}
        {live && m.endsMs !== null && m.endsMs > now ? <span className="ml-2 text-red-600 dark:text-red-400">{t("ends in ", "終了まで ") + fmtSpan(m.endsMs - now)}</span> : null}
        {!live && m.status !== "completed" && m.startsMs !== null && m.startsMs > now ? (
          <span className="ml-2 text-amber-700 dark:text-amber-400">{t("starts in ", "開始まで ") + fmtSpan(m.startsMs - now)}</span>
        ) : null}
      </div>
      {m.update ? <p className="text-xs text-muted-foreground">{m.update}</p> : null}
      <StatusLink href={m.link} />
    </li>
  );
}

function IncidentRow({ i }: { i: ModIncident }) {
  const { t, lang } = useI18n();
  return (
    <li className="flex flex-col gap-1 rounded-md border border-red-500/30 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{i.name}</span>
        <Badge tone="bad">{i.impact}</Badge>
        <span className="text-xs text-muted-foreground">
          {i.status}
          {i.startedMs !== null ? ` · ${t("since", "発生")} ${fmtDate(i.startedMs, lang)}` : ""}
          {i.services.length > 0 ? ` · ${i.services.join(", ")}` : ""}
        </span>
      </div>
      {i.update ? <p className="text-xs text-muted-foreground">{i.update}</p> : null}
      <StatusLink href={i.link} />
    </li>
  );
}

/** Hypixel's status page: services, maintenance, incidents (via the SkyPilot server, cached 2 min). */
export function ServerStatusCard() {
  const { t } = useI18n();
  const now = useNow();
  const q = useApi<ModServerStatus>("/api/mod/server-status", undefined, { refetchInterval: 120_000 });
  const s = q.data;
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <CardTitle>{t("Hypixel server status", "Hypixel サーバーの状態")}</CardTitle>
        <a href="https://status.hypixel.net" target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-primary hover:underline">
          status.hypixel.net <ExternalLink className="size-3" />
        </a>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {q.isLoading ? <Loading rows={2} /> : null}
        {q.error ? <ErrorView error={q.error} retry={() => q.refetch()} /> : null}
        {s && !s.available ? <p className="text-sm text-muted-foreground">{t("Not available right now.", "今は取得できません。")}</p> : null}
        {s && s.available ? (
          <>
            <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
              {s.services.map((v) => {
                const [label, tone] = serviceLabel(v.status, t);
                return (
                  <span key={v.name} className="inline-flex items-center gap-2">
                    {v.name} <Badge tone={tone}>{label}</Badge>
                  </span>
                );
              })}
            </div>
            <div className="flex flex-col gap-2">
              <h3 className="text-sm font-semibold">{t("Maintenance", "メンテナンス")}</h3>
              {s.maintenances.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("No maintenance announced.", "予定されているメンテナンスはありません。")}</p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {s.maintenances.map((m) => (
                    <MaintenanceRow key={m.id} m={m} now={now} />
                  ))}
                </ul>
              )}
            </div>
            {s.incidents.length > 0 ? (
              <div className="flex flex-col gap-2">
                <h3 className="text-sm font-semibold text-red-600 dark:text-red-400">{t("Incidents", "障害")}</h3>
                <ul className="flex flex-col gap-2">
                  {s.incidents.map((i) => (
                    <IncidentRow key={i.id} i={i} />
                  ))}
                </ul>
              </div>
            ) : null}
            {s.recentMaintenances.length > 0 ? (
              <details className="text-sm">
                <summary className="cursor-pointer font-semibold">{t("Recent SkyBlock maintenance", "最近の SkyBlock メンテナンス")}</summary>
                <ul className="mt-2 flex flex-col gap-2">
                  {s.recentMaintenances.map((m) => (
                    <MaintenanceRow key={m.id} m={m} now={now} />
                  ))}
                </ul>
              </details>
            ) : null}
            <p className="text-xs text-muted-foreground">
              {t("Source: status.hypixel.net · checked ", "出典: status.hypixel.net · 確認 ")}
              {fmtSpan(now - s.checkedAtMs)}
              {t(" ago", " 前")}
            </p>
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}

/** SkyBlock calendar: the next occurrence of each event (curated registry, via the SkyPilot server). */
export function EventScheduleCard() {
  const { t, lang } = useI18n();
  const now = useNow();
  const q = useApi<ModCalendar>("/api/mod/calendar", undefined, { refetchInterval: 300_000 });
  const events = (q.data?.events ?? []).filter((e) => e.endMs > now || e.startMs > now).sort((a, b) => a.startMs - b.startMs);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("SkyBlock event schedule", "SkyBlock イベント予定")}</CardTitle>
      </CardHeader>
      <CardContent>
        {q.isLoading ? <Loading rows={4} /> : null}
        {q.error ? <ErrorView error={q.error} retry={() => q.refetch()} /> : null}
        {q.data && events.length === 0 ? <p className="text-sm text-muted-foreground">{t("No events known.", "予定はありません。")}</p> : null}
        {events.length > 0 ? (
          <ul className="divide-y text-sm">
            {events.map((e) => {
              const active = e.startMs <= now && now < e.endMs;
              return (
                <li key={`${e.id}-${e.startMs}`} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span className="font-medium">{e.name}</span>
                  <span className="flex items-center gap-2 text-muted-foreground">
                    {active ? (
                      <>
                        <Badge tone="good">{t("Now", "開催中")}</Badge>
                        {t("ends in ", "残り ") + fmtSpan(e.endMs - now)}
                      </>
                    ) : (
                      <>
                        {fmtDate(e.startMs, lang)}
                        <Badge tone="info">{t("in ", "あと ") + fmtSpan(e.startMs - now)}</Badge>
                      </>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        ) : null}
      </CardContent>
    </Card>
  );
}
