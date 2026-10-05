import type { Metadata } from "next";
import { Suspense } from "react";
import { LinkView } from "./view";

export const metadata: Metadata = { title: "Link Mod" };

export default function Page() {
  return (
    <Suspense>
      <LinkView />
    </Suspense>
  );
}
