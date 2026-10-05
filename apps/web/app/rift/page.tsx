import type { Metadata } from "next";
import { ReportRoute } from "@/components/report-route";

export const metadata: Metadata = { title: "Rift" };

export default function Page() {
  return <ReportRoute domain="rift" />;
}
