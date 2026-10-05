import type { Metadata } from "next";
import { NetworthView } from "./view";

export const metadata: Metadata = { title: "Networth" };

export default function Page() {
  return <NetworthView />;
}
