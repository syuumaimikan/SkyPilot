import type { Metadata } from "next";
import { SlayerView } from "./view";

export const metadata: Metadata = { title: "Slayer" };

export default function Page() {
  return <SlayerView />;
}
