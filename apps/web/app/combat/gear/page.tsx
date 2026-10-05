import type { Metadata } from "next";
import { GearView } from "./view";

export const metadata: Metadata = { title: "Gear" };

export default function Page() {
  return <GearView />;
}
