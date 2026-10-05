import type { Metadata } from "next";
import { MoneyView } from "./view";

export const metadata: Metadata = { title: "Money Making" };

export default function Page() {
  return <MoneyView />;
}
