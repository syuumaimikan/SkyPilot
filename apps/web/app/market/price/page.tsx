import type { Metadata } from "next";
import { Suspense } from "react";
import { PriceView } from "./view";

export const metadata: Metadata = { title: "Price Explorer" };

export default function Page() {
  return (
    <Suspense>
      <PriceView />
    </Suspense>
  );
}
