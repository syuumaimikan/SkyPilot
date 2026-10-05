import type { Metadata } from "next";
import { DailyView } from "./view";

export const metadata: Metadata = { title: "Daily & Events" };

export default function Page() {
  return <DailyView />;
}
