"use client";
import * as React from "react";
import { Dialog as DialogPrimitive } from "radix-ui";
import { cn } from "../lib";

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

export function DialogContent({ className, children, title, description, ...props }: React.ComponentProps<typeof DialogPrimitive.Content> & { title: string; description?: string }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50" />
      <DialogPrimitive.Content
        className={cn("fixed top-1/2 left-1/2 z-50 grid max-h-[85vh] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 overflow-y-auto rounded-xl border border-border bg-background p-6 shadow-lg", className)}
        {...props}
      >
        <div className="flex flex-col gap-1">
          <DialogPrimitive.Title className="text-base font-semibold">{title}</DialogPrimitive.Title>
          {description ? <DialogPrimitive.Description className="text-sm text-muted-foreground">{description}</DialogPrimitive.Description> : null}
        </div>
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}
