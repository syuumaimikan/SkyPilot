import * as React from "react";
import { cn } from "../lib";

const field =
  "w-full min-w-0 rounded-md border border-input bg-background px-3 text-sm shadow-xs outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive";

export function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return <input type={type} data-slot="input" className={cn(field, "h-9 py-1", className)} {...props} />;
}

export function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return <textarea data-slot="textarea" className={cn(field, "min-h-20 py-2", className)} {...props} />;
}

export function NativeSelect({ className, children, ...props }: React.ComponentProps<"select">) {
  return (
    <select data-slot="select" className={cn(field, "h-9 cursor-pointer py-1 pr-8", className)} {...props}>
      {children}
    </select>
  );
}

export function Label({ className, ...props }: React.ComponentProps<"label">) {
  return <label className={cn("text-xs font-medium text-muted-foreground", className)} {...props} />;
}
