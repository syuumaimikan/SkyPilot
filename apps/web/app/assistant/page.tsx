import type { Metadata } from "next";
import { AssistantView } from "./view";

export const metadata: Metadata = { title: "Assistant" };

export default function Page() {
  return <AssistantView />;
}
