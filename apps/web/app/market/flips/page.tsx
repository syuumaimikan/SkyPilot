import type { Metadata } from "next";
import { Suspense } from "react";
import { FlipsView } from "./view";

export const metadata: Metadata = { title: "Live Flips" };

export default function Page() {
  return (
    <Suspense>
      <FlipsView />
    </Suspense>
  );
}
