import type { Metadata } from "next";
import { CraftView } from "./view";

export const metadata: Metadata = { title: "Craft & Forge" };

export default function Page() {
  return <CraftView />;
}
