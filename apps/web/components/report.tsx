"use client";

import type { AdvisorReport, AdvisorSection, Table as TableT } from "@skypilot/protocol";
import { Alert, Card, CardContent, CardDescription, CardHeader, CardTitle, Table, TBody, TD, TH, THead, TR } from "@skypilot/ui";
import { useReport } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";
import { ActionCard } from "./action-card";
import { CellView, Empty, ErrorView, Freshness, Loading, MetricView, PageHeader, SourceBadge } from "./common";

export function DataTable({ table, max }: { table: TableT; max?: number }) {
  const rows = max ? table.rows.slice(0, max) : table.rows;
  return (
    <Table>
      <THead>
        <TR>
          {table.columns.map((c) => (
            <TH key={c}>{c}</TH>
          ))}
        </TR>
      </THead>
      <TBody>
        {rows.map((r, i) => (
          <TR key={i}>
            {r.map((c, j) => (
              <TD key={j}>
                <CellView c={c} />
              </TD>
            ))}
          </TR>
        ))}
      </TBody>
    </Table>
  );
}

export function SectionCard({ s }: { s: AdvisorSection }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle>{s.title}</CardTitle>
          <div className="flex gap-1">
            {s.sources.map((src) => (
              <SourceBadge key={src} s={src} />
            ))}
          </div>
        </div>
        {s.description ? <CardDescription>{s.description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {s.metrics.length > 0 ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {s.metrics.map((m, i) => (
              <MetricView key={i} m={m} />
            ))}
          </div>
        ) : null}
        {s.table && s.table.rows.length > 0 ? <DataTable table={s.table} /> : null}
        {s.bullets.length > 0 ? (
          <ul className="list-disc space-y-1 pl-5 text-sm">
            {s.bullets.map((b, i) => (
              <li key={i}>{b}</li>
            ))}
          </ul>
        ) : null}
        {s.notes.length > 0 ? (
          <div className="flex flex-col gap-1 text-xs text-muted-foreground">
            {s.notes.map((n, i) => (
              <p key={i}>{n}</p>
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function ReportBody({ r }: { r: AdvisorReport }) {
  const { t } = useI18n();
  return (
    <div className="flex flex-col gap-6">
      {r.freshness.length > 0 ? (
        <div className="flex flex-wrap gap-4">
          {r.freshness.map((f) => (
            <span key={f.source} className="flex items-center gap-2 text-xs text-muted-foreground">
              {f.source}: <Freshness f={f} />
            </span>
          ))}
        </div>
      ) : null}
      {r.summary.length > 0 ? (
        <Card>
          <CardContent className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-3 lg:grid-cols-6">
            {r.summary.map((m, i) => (
              <MetricView key={i} m={m} />
            ))}
          </CardContent>
        </Card>
      ) : null}
      {r.unknowns.length > 0 ? (
        <Alert tone="warn">
          <div className="mb-1 font-medium">{t("Not available from the API", "APIから取得できない情報")}</div>
          <ul className="list-disc pl-5">
            {r.unknowns.map((u, i) => (
              <li key={i}>{u}</li>
            ))}
          </ul>
        </Alert>
      ) : null}
      {r.actions.length > 0 ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold">{t("Recommended next actions", "おすすめの次の行動")}</h2>
          <div className="grid gap-4 lg:grid-cols-2">
            {r.actions.map((a, i) => (
              <ActionCard key={a.id} a={a} rank={i + 1} />
            ))}
          </div>
        </div>
      ) : null}
      {r.sections.map((s) => (
        <SectionCard key={s.id} s={s} />
      ))}
      {r.sections.length === 0 && r.actions.length === 0 ? <Empty>{t("Nothing to report for this profile yet.", "このプロフィールではまだ表示する内容がありません。")}</Empty> : null}
    </div>
  );
}

/** Whole page for a domain advisor (fishing, pets, minions...). */
export function ReportPage({ domain, title, description }: { domain: string; title: [string, string]; description?: [string, string] }) {
  const { t } = useI18n();
  const q = useReport(domain);
  return (
    <div>
      <PageHeader title={t(title[0], title[1])} description={description ? t(description[0], description[1]) : undefined} />
      {q.isLoading ? <Loading /> : q.error ? <ErrorView error={q.error} retry={() => q.refetch()} /> : q.data ? <ReportBody r={q.data} /> : null}
    </div>
  );
}

/** Report embedded inside a richer page (no page header). */
export function EmbeddedReport({ domain }: { domain: string }) {
  const q = useReport(domain);
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorView error={q.error} retry={() => q.refetch()} />;
  return q.data ? <ReportBody r={q.data} /> : null;
}
