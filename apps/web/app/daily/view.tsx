"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@skypilot/ui";
import { PageHeader } from "@/components/common";
import { EventScheduleCard, ServerStatusCard } from "@/components/hypixel-status";
import { EmbeddedReport } from "@/components/report";
import { useI18n } from "@/lib/i18n";
import Link from "next/link";
import { Button } from "@skypilot/ui";

export function DailyView() {
  const { t } = useI18n();
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("Daily & Events", "デイリー・イベント")}
        actions={<Button asChild variant="outline"><Link href="/planner">{t("Open adventure notebook", "冒険ノートを開く")}</Link></Button>}
        description={t(
          "Hypixel's server status and maintenance, the SkyBlock event schedule, daily tasks worth doing for your profile and the current mayor's perks.",
          "Hypixel のサーバー状態とメンテナンス、SkyBlock のイベント予定、あなたのプロフィールで毎日やる価値のあること、現在の市長の特典です。",
        )}
      />
      <ServerStatusCard />
      <Tabs defaultValue="schedule">
        <TabsList>
          <TabsTrigger value="schedule">{t("Event schedule", "イベント予定")}</TabsTrigger>
          <TabsTrigger value="daily">{t("Daily", "デイリー")}</TabsTrigger>
          <TabsTrigger value="events">{t("Events & mayor", "イベント・市長")}</TabsTrigger>
        </TabsList>
        <TabsContent value="schedule">
          <EventScheduleCard />
        </TabsContent>
        <TabsContent value="daily">
          <EmbeddedReport domain="daily" />
        </TabsContent>
        <TabsContent value="events">
          <EmbeddedReport domain="events" />
        </TabsContent>
      </Tabs>
    </div>
  );
}
