import type { Metadata } from "next";
import { ReportRoute } from "@/components/report-route";

export const metadata: Metadata = { title: "Foraging" };

export default function Page() {
  return <ReportRoute domain="foraging" />;
}
