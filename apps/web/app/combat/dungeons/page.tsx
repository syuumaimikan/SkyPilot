import type { Metadata } from "next";
import { DungeonsView } from "./view";

export const metadata: Metadata = { title: "Dungeons" };

export default function Page() {
  return <DungeonsView />;
}
