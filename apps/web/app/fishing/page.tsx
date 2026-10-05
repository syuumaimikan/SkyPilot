import type { Metadata } from "next";
import { ReportRoute } from "@/components/report-route";

export const metadata: Metadata = { title: "Fishing" };

export default function Page() {
  return <ReportRoute domain="fishing" />;
}
