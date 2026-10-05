import * as React from "react";
import { cn } from "../lib";

export interface ProgressProps extends React.ComponentProps<"div"> {
  /** 0..1 */
  value: number;
  tone?: "primary" | "good" | "warn" | "bad";
}

const tones = { primary: "bg-primary", good: "bg-emerald-500", warn: "bg-amber-500", bad: "bg-red-500" };

export function Progress({ value, tone = "primary", className, ...props }: ProgressProps) {
  const pct = Number.isFinite(value) ? Math.max(0, Math.min(1, value)) * 100 : 0;
  return (
    <div role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(pct)} className={cn("h-1.5 w-full overflow-hidden rounded-full bg-muted", className)} {...props}>
      <div className={cn("h-full rounded-full transition-[width]", tones[tone])} style={{ width: `${pct}%` }} />
    </div>
  );
}
