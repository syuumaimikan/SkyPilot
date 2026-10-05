import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../lib";

export const badgeVariants = cva("inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[11px] font-medium leading-none whitespace-nowrap", {
  variants: {
    tone: {
      neutral: "border-border bg-muted text-muted-foreground",
      good: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
      warn: "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400",
      bad: "border-red-500/30 bg-red-500/10 text-red-600 dark:text-red-400",
      info: "border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-400",
      primary: "border-primary/30 bg-primary/10 text-primary",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export interface BadgeProps extends React.ComponentProps<"span">, VariantProps<typeof badgeVariants> {}

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}
