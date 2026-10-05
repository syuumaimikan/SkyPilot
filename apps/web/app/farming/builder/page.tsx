import type { Metadata } from "next";
import { Suspense } from "react";
import { BuilderView } from "./view";

export const metadata: Metadata = { title: "Farm Builder" };

export default function Page() {
  return (
    <Suspense>
      <BuilderView />
    </Suspense>
  );
}
