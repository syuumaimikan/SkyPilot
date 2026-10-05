import type { Metadata } from "next";
import { ReportRoute } from "@/components/report-route";

export const metadata: Metadata = { title: "Kuudra & Crimson Isle" };

export default function Page() {
  return <ReportRoute domain="kuudra" />;
}
