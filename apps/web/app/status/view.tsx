"use client";

import type { MarketStatus } from "@skypilot/protocol";
import { Badge, Card, CardContent, CardHeader, CardTitle } from "@skypilot/ui";
import { ErrorView, Freshness, Loading, PageHeader, StatCard } from "@/components/common";
import { fmtDate, fmtNum } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import { useI18n } from "@/lib/i18n";

interface SystemStatus {
  hypixel: { keyConfigured: boolean; circuit: string; rateLimitRemaining: number | null; rateLimitLimit: number | null; metrics: Record<string, number> };
  registry: { items: number; neuFiles: number; skyblockVersion: string | null; itemsLastUpdated: number | null; loadedAtMs: number };
  translation: { externalProvider: string | null; externalEnabled: boolean; circuit: string };
  llm: { enabled: boolean; model: string | null };
  store: string;
  collector: string;
  eventBus: string;
  webPush: boolean;
}

export function StatusView() {
  const { t, lang } = useI18n();
  const sys = useApi<SystemStatus>("/api/status", undefined, { refetchInterval: 15_000 });
  const market = useApi<MarketStatus>("/api/market/status", undefined, { refetchInterval: 15_000 });
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("System status", "システム状態")} description={t("Data freshness, Hypixel API health and server configuration.", "データの鮮度、Hypixel API の状態、サーバー構成です。")} />
      {market.isLoading || sys.isLoading ? <Loading /> : null}
      {market.error ? <ErrorView error={market.error} /> : null}
      {sys.error ? <ErrorView error={sys.error} /> : null}
      {market.data ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("Market data", "マーケットデータ")}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="grid gap-2 text-sm sm:grid-cols-3">
              <div className="flex items-center justify-between gap-2">
                {t("Auction House", "オークション")} <Freshness f={market.data.auctions} />
              </div>
              <div className="flex items-center justify-between gap-2">
                {t("Ended auctions", "終了オークション")} <Freshness f={market.data.endedAuctions} />
              </div>
              <div className="flex items-center justify-between gap-2">
                {t("Bazaar", "バザー")} <Freshness f={market.data.bazaar} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label={t("Active listings", "出品中")} value={fmtNum(market.data.activeListings)} hint={`BIN ${fmtNum(market.data.activeBinListings)}`} />
              <StatCard label={t("Tracked price keys", "追跡中の価格キー")} value={fmtNum(market.data.trackedPriceKeys)} />
              <StatCard label={t("Sales recorded", "記録済み売買")} value={fmtNum(market.data.salesRecorded)} />
              <StatCard label={t("Hypixel circuit", "Hypixel 回路")} value={<Badge tone={market.data.hypixelCircuit.toLowerCase() === "closed" ? "good" : "bad"}>{market.data.hypixelCircuit}</Badge>} />
            </div>
            {market.data.lastError ? <p className="text-xs text-red-500">{market.data.lastError}</p> : null}
          </CardContent>
        </Card>
      ) : null}
      {sys.data ? (
        <Card>
          <CardHeader>
            <CardTitle>{t("Server", "サーバー")}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
            <Row k={t("Hypixel API key", "Hypixel API キー")} v={sys.data.hypixel.keyConfigured ? <Badge tone="good">{t("configured", "設定済み")}</Badge> : <Badge tone="warn">{t("not set - profile analysis disabled", "未設定 - プロフィール分析は無効")}</Badge>} />
            <Row k={t("Key rate limit", "キーのレート制限")} v={sys.data.hypixel.rateLimitRemaining !== null ? `${sys.data.hypixel.rateLimitRemaining} / ${sys.data.hypixel.rateLimitLimit}` : "—"} />
            <Row k={t("Items in registry", "レジストリのアイテム数")} v={fmtNum(sys.data.registry.items)} />
            <Row k={t("NEU-REPO files", "NEU-REPO ファイル数")} v={fmtNum(sys.data.registry.neuFiles)} />
            <Row k="SkyBlock" v={sys.data.registry.skyblockVersion ?? "—"} />
            <Row k={t("Registry loaded", "レジストリ読込")} v={fmtDate(sys.data.registry.loadedAtMs, lang)} />
            <Row k={t("Storage", "ストレージ")} v={sys.data.store} />
            <Row k={t("Event bus", "イベントバス")} v={sys.data.eventBus} />
            <Row k={t("Collector", "コレクター")} v={sys.data.collector} />
            <Row k="Web Push" v={sys.data.webPush ? t("enabled", "有効") : t("disabled", "無効")} />
            <Row k={t("Translation", "翻訳")} v={`${sys.data.translation.externalProvider ?? t("dictionary only", "辞書のみ")} (${sys.data.translation.circuit})`} />
            <Row k={t("Assistant LLM", "アシスタント LLM")} v={sys.data.llm.enabled ? sys.data.llm.model ?? "on" : t("off (deterministic answers)", "無効（決定的な回答のみ）")} />
            {Object.entries(sys.data.hypixel.metrics).map(([k, v]) => (
              <Row key={k} k={`hypixel.${k}`} v={fmtNum(v)} />
            ))}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border/50 py-1">
      <span className="text-muted-foreground">{k}</span>
      <span className="text-right">{v}</span>
    </div>
  );
}
