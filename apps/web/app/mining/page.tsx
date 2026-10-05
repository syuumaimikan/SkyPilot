import type { Metadata } from "next";
import { MiningView } from "./view";

export const metadata: Metadata = { title: "Mining" };

export default function Page() {
  return <MiningView />;
}
