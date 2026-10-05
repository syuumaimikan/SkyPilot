import * as React from "react";
import { cn } from "../lib";

export function Separator({ className, vertical = false }: { className?: string; vertical?: boolean }) {
  return <div role="separator" className={cn("shrink-0 bg-border", vertical ? "h-full w-px" : "h-px w-full", className)} />;
}

export function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("animate-pulse rounded-md bg-muted", className)} {...props} />;
}

const alertTones = {
  neutral: "border-border bg-muted/40",
  warn: "border-amber-500/40 bg-amber-500/10 text-amber-800 dark:text-amber-200",
  bad: "border-red-500/40 bg-red-500/10 text-red-700 dark:text-red-200",
  info: "border-sky-500/40 bg-sky-500/10 text-sky-800 dark:text-sky-200",
  good: "border-emerald-500/40 bg-emerald-500/10 text-emerald-800 dark:text-emerald-200",
};

export function Alert({ className, tone = "neutral", ...props }: React.ComponentProps<"div"> & { tone?: keyof typeof alertTones }) {
  return <div role="status" className={cn("rounded-lg border px-4 py-3 text-sm", alertTones[tone], className)} {...props} />;
}

export function Kbd({ className, ...props }: React.ComponentProps<"kbd">) {
  return <kbd className={cn("rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[11px]", className)} {...props} />;
}
