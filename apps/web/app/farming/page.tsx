import type { Metadata } from "next";
import { FarmingView } from "./view";

export const metadata: Metadata = { title: "Farming" };

export default function Page() {
  return <FarmingView />;
}
