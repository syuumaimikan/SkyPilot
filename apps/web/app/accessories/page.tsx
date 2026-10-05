import type { Metadata } from "next";
import { ReportRoute } from "@/components/report-route";

export const metadata: Metadata = { title: "Accessories" };

export default function Page() {
  return <ReportRoute domain="accessories" />;
}
