import type { Metadata } from "next";
import { ReportRoute } from "@/components/report-route";

export const metadata: Metadata = { title: "Minions" };

export default function Page() {
  return <ReportRoute domain="minions" />;
}
