import * as React from "react";
import { cn } from "../lib";

export function Table({ className, ...props }: React.ComponentProps<"table">) {
  return (
    <div className="relative w-full overflow-x-auto">
      <table className={cn("w-full caption-bottom text-sm", className)} {...props} />
    </div>
  );
}
export function THead({ className, ...props }: React.ComponentProps<"thead">) {
  return <thead className={cn("[&_tr]:border-b [&_tr]:border-border", className)} {...props} />;
}
export function TBody({ className, ...props }: React.ComponentProps<"tbody">) {
  return <tbody className={cn("[&_tr:last-child]:border-0", className)} {...props} />;
}
export function TR({ className, ...props }: React.ComponentProps<"tr">) {
  return <tr className={cn("border-b border-border/60 transition-colors hover:bg-muted/40", className)} {...props} />;
}
export function TH({ className, ...props }: React.ComponentProps<"th">) {
  return <th className={cn("h-9 whitespace-nowrap px-3 text-left align-middle text-xs font-medium text-muted-foreground", className)} {...props} />;
}
export function TD({ className, ...props }: React.ComponentProps<"td">) {
  return <td className={cn("px-3 py-2 align-middle tabular-nums", className)} {...props} />;
}
