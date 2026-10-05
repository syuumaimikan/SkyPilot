"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@skypilot/ui";
import { PageHeader } from "@/components/common";
import { EmbeddedReport } from "@/components/report";
import { useI18n } from "@/lib/i18n";

export function DailyView() {
  const { t } = useI18n();
  return (
    <div>
      <PageHeader
        title={t("Daily & Events", "デイリー・イベント")}
        description={t("Daily tasks worth doing for your profile, upcoming calendar events and the current mayor's perks.", "あなたのプロフィールで毎日やる価値のあること、今後のイベント、現在の市長の特典です。")}
      />
      <Tabs defaultValue="daily">
        <TabsList>
          <TabsTrigger value="daily">{t("Daily", "デイリー")}</TabsTrigger>
          <TabsTrigger value="events">{t("Events & mayor", "イベント・市長")}</TabsTrigger>
        </TabsList>
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
