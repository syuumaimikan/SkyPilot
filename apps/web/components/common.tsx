"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import type { Cell, Confidence, DataSource, Freshness as FreshnessT, Known, Metric, NodeStatus, RiskLevel } from "@skypilot/protocol";
import { Alert, Badge, Button, Card, CardContent, Progress, Skeleton, Tooltip, cn } from "@skypilot/ui";
import { ApiError } from "@/lib/api";
import { fmtAge, fmtCoins, fmtHours, fmtNum, fmtPct, rarityClass } from "@/lib/format";
import { useAgeSeconds } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

/** "Updated N seconds ago" that ticks live, plus a STALE DATA badge. */
export function Freshness({ f, compact = false, staleAfterSeconds }: { f: FreshnessT | null | undefined; compact?: boolean; staleAfterSeconds?: number }) {
  const { lang, t } = useI18n();
  // Server ageSeconds is relative to when the response was produced; recompute from the timestamp.
  const age = useAgeSeconds(f && f.updatedAtMs > 0 ? f.updatedAtMs : null);
  if (!f || f.updatedAtMs <= 0 || age === null) {
    return <Badge tone="neutral">{t("No data yet", "データなし")}</Badge>;
  }
  const stale = f.stale || (staleAfterSeconds !== undefined && age > staleAfterSeconds);
  return (
    <span className={cn("inline-flex items-center gap-1.5", compact ? "text-xs" : "text-xs text-muted-foreground")}>
      <span className={cn("size-1.5 rounded-full", stale ? "bg-red-500" : "bg-emerald-500")} aria-hidden />
      {compact ? null : <span>{fmtAge(age, lang)}</span>}
      {compact ? <span className="tabular-nums">{fmtAge(age, lang).replace(/^Updated /, "")}</span> : null}
      {stale ? <Badge tone="bad">STALE DATA</Badge> : null}
    </span>
  );
}

export function UnknownValue({ reason }: { reason?: string }) {
  const { t } = useI18n();
  return (
    <Tooltip content={reason ?? t("Not available from API", "APIから取得できません")}>
      <span className="cursor-help text-muted-foreground italic underline decoration-dotted">{t("Unknown", "不明")}</span>
    </Tooltip>
  );
}

/** Render a `Known<T>`; unknown values are labelled, never guessed. */
export function KnownView<T>({ v, render }: { v: Known<T>; render: (value: T) => ReactNode }) {
  if (v.status === "known") return <>{render(v.value)}</>;
  return <UnknownValue reason={v.reason} />;
}

export function known<T>(v: Known<T>): T | null {
  return v.status === "known" ? v.value : null;
}

export function ConfidenceBadge({ c }: { c: Confidence }) {
  const { t } = useI18n();
  const tone = c.level === "HIGH" ? "good" : c.level === "MEDIUM" ? "warn" : "bad";
  const label = c.level === "HIGH" ? t("High confidence", "信頼度 高") : c.level === "MEDIUM" ? t("Medium confidence", "信頼度 中") : t("Low confidence", "信頼度 低");
  const badge = (
    <Badge tone={tone} className="cursor-help">
      {label} · {Math.round(c.score * 100)}
    </Badge>
  );
  if (c.reasons.length === 0) return badge;
  return (
    <Tooltip
      content={
        <ul className="list-disc pl-4">
          {c.reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      }
    >
      {badge}
    </Tooltip>
  );
}

export function RiskBadge({ r }: { r: RiskLevel }) {
  const { t } = useI18n();
  const map: Record<RiskLevel, ["good" | "warn" | "bad" | "neutral", string, string]> = {
    VERY_LOW: ["good", "Very low risk", "リスク 極小"],
    LOW: ["good", "Low risk", "リスク 低"],
    MEDIUM: ["warn", "Medium risk", "リスク 中"],
    HIGH: ["bad", "High risk", "リスク 高"],
    VERY_HIGH: ["bad", "Very high risk", "リスク 極大"],
  };
  const [tone, en, ja] = map[r];
  return <Badge tone={tone}>{t(en, ja)}</Badge>;
}

export function StatusBadge({ s }: { s: NodeStatus }) {
  const { t } = useI18n();
  const map: Record<NodeStatus, ["good" | "warn" | "bad" | "neutral" | "info", string, string]> = {
    SATISFIED: ["good", "Done", "達成"],
    IN_PROGRESS: ["info", "In progress", "進行中"],
    NOT_STARTED: ["neutral", "Not started", "未着手"],
    BLOCKED: ["bad", "Blocked", "ブロック中"],
    UNKNOWN: ["warn", "Unknown", "不明"],
  };
  const [tone, en, ja] = map[s];
  return <Badge tone={tone}>{t(en, ja)}</Badge>;
}

export function SourceBadge({ s }: { s: DataSource }) {
  const { t } = useI18n();
  const map: Record<DataSource, [string, string, string]> = {
    HYPIXEL_PROFILE: ["Profile", "プロフィール", "Hypixel API profile data"],
    HYPIXEL_RESOURCES: ["Hypixel", "Hypixel", "Hypixel resources API (items, skills, collections)"],
    NEU_REPO: ["NEU-REPO", "NEU-REPO", "NotEnoughUpdates community repository (MIT)"],
    SKY_PILOT_REGISTRY: ["Registry", "レジストリ", "SkyPilot curated registry with cited sources"],
    SKY_PILOT_HEURISTIC: ["Heuristic", "推定", "SkyPilot heuristic estimate - not an official value"],
    MARKET: ["Market", "マーケット", "Live Auction House / Bazaar data"],
    CLIENT_MOD: ["Mod", "MOD", "Reported by the SkyPilot client mod"],
  };
  const [en, ja, tip] = map[s];
  return (
    <Tooltip content={tip}>
      <Badge tone={s === "SKY_PILOT_HEURISTIC" ? "warn" : "neutral"} className="cursor-help">
        {t(en, ja)}
      </Badge>
    </Tooltip>
  );
}

export function MetricView({ m }: { m: Metric }) {
  const { lang } = useI18n();
  const fmt = (v: number | null) => {
    if (v === null) return "—";
    if (m.unit === "coins") return fmtCoins(v);
    if (m.unit === "%") return fmtPct(v);
    if (m.unit === "h") return fmtHours(v, lang);
    return fmtNum(v, Math.abs(v) < 10 ? 2 : 0) + (m.unit ? ` ${m.unit}` : "");
  };
  let body: ReactNode;
  if (m.before !== null && m.after !== null) {
    body = (
      <span>
        <span className="text-muted-foreground">{fmt(m.before)}</span> → <span className="font-semibold">{fmt(m.after)}</span>
      </span>
    );
  } else if (m.value !== null) {
    body = <span className="font-semibold">{fmt(m.value)}</span>;
  } else {
    body = <span className="font-medium">{m.text ?? "—"}</span>;
  }
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-[11px] text-muted-foreground">{m.label}</span>
      <span className="text-sm tabular-nums">{body}</span>
    </div>
  );
}

export function StatCard({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-1 p-4">
        <span className="text-[11px] text-muted-foreground">{label}</span>
        <span className="text-lg font-semibold tabular-nums">{value}</span>
        {hint ? <span className="text-[11px] text-muted-foreground">{hint}</span> : null}
      </CardContent>
    </Card>
  );
}

export function CellView({ c }: { c: Cell }) {
  const { lang } = useI18n();
  switch (c.kind) {
    case "text":
      return <span>{c.text}</span>;
    case "number":
      return (
        <span className="tabular-nums">
          {fmtNum(c.value, c.digits ?? 0)}
          {c.unit ? <span className="text-muted-foreground"> {c.unit}</span> : null}
        </span>
      );
    case "coins":
      return <span className="tabular-nums">{fmtCoins(c.value)}</span>;
    case "percent":
      return <span className="tabular-nums">{fmtPct(c.value)}</span>;
    case "duration":
      return <span className="tabular-nums">{fmtHours(c.hours, lang)}</span>;
    case "progress":
      return (
        <div className="flex min-w-24 items-center gap-2">
          <Progress value={c.value} className="w-20" />
          <span className="text-xs text-muted-foreground tabular-nums">{c.label ?? fmtPct(c.value, 0)}</span>
        </div>
      );
    case "badge":
      return <Badge tone={c.tone === "neutral" ? "neutral" : c.tone}>{c.text}</Badge>;
    case "item":
      return <span className={cn("font-medium", rarityClass(c.rarity))}>{c.name}</span>;
    case "unknown":
      return <UnknownValue reason={c.reason} />;
  }
}

export function Loading({ rows = 3 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-16 w-full" />
      ))}
    </div>
  );
}

export function NeedsAnalysis() {
  const { t } = useI18n();
  return (
    <Alert tone="info" className="flex flex-wrap items-center justify-between gap-3">
      <span>{t("Analyse your profile first: enter your Minecraft username and a goal on the dashboard.", "まずダッシュボードでMinecraftユーザー名と目標を入力してプロフィールを分析してください。")}</span>
      <Button asChild size="sm">
        <Link href="/">{t("Go to dashboard", "ダッシュボードへ")}</Link>
      </Button>
    </Alert>
  );
}

/** Explains API errors in plain language; "no analysis yet" links to the dashboard form. */
export function ErrorView({ error, retry }: { error: unknown; retry?: () => void }) {
  const { t } = useI18n();
  const e = error instanceof ApiError ? error : null;
  if (e?.code === "NO_ANALYSIS") return <NeedsAnalysis />;
  let msg = e?.message ?? String(error);
  if (e?.code === "NETWORK") msg = t("The SkyPilot API is unreachable. Is the server running?", "SkyPilot API に接続できません。サーバーは起動していますか？");
  if (e?.code === "RATE_LIMITED") msg = t("Too many requests - please wait a moment.", "リクエストが多すぎます。少し待ってください。");
  if (e?.code === "HYPIXEL_KEY_MISSING") msg = t("The server has no Hypixel API key. Its owner must create an application on developer.hypixel.net and configure HYPIXEL_API_KEY, then restart the API server. Your in-game API switches cannot fix this.", "サーバーに Hypixel API キーが設定されていません。管理者が developer.hypixel.net でアプリを登録し、HYPIXEL_API_KEY を設定して API サーバーを再起動してください。ゲーム内の API 設定では解消できません。");
  return (
    <Alert tone={e?.code === "HYPIXEL_UNAVAILABLE" || e?.code === "HYPIXEL_KEY_MISSING" ? "warn" : "bad"} className="flex flex-wrap items-center justify-between gap-3">
      <span>
        {msg}
        {e?.lastValidSnapshotAgeSeconds != null ? ` (${t("last valid snapshot", "最後の有効なスナップショット")}: ${Math.round(e.lastValidSnapshotAgeSeconds)}s)` : null}
      </span>
      {retry ? (
        <Button size="sm" variant="outline" onClick={retry}>
          {t("Retry", "再試行")}
        </Button>
      ) : null}
    </Alert>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="rounded-lg border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">{children}</div>;
}

export function Section({ title, description, children, actions }: { title: string; description?: ReactNode; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {actions}
      </div>
      {children}
    </section>
  );
}

export function CopyButton({ text, label }: { text: string; label?: string }) {
  const { t } = useI18n();
  return (
    <Button
      size="sm"
      variant="outline"
      onClick={() => {
        void navigator.clipboard?.writeText(text);
      }}
      title={text}
    >
      {label ?? t("Copy", "コピー")}
    </Button>
  );
}
