import type { Metadata } from "next";
import { BazaarView } from "./view";

export const metadata: Metadata = { title: "Bazaar" };

export default function Page() {
  return <BazaarView />;
}
