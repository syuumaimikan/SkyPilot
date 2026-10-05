"use client";

import { Button } from "@skypilot/ui";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 py-16">
      <h1 className="text-xl font-semibold">Something went wrong / エラーが発生しました</h1>
      <p className="max-w-xl text-sm text-muted-foreground">{error.message}</p>
      <Button onClick={reset}>Retry / 再試行</Button>
    </div>
  );
}
