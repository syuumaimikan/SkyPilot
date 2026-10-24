"use client";

import type { ReactNode } from "react";
import { Button, Card, CardContent, CardHeader, CardTitle, Input } from "@skypilot/ui";
import { Trash2 } from "lucide-react";
import { useI18n } from "@/lib/i18n";

export function ToolCard({ title, help, children }: { title: string; help: string; children: ReactNode }) {
  return <Card><CardHeader><CardTitle className="text-base">{title}</CardTitle><p className="text-xs leading-relaxed text-muted-foreground">{help}</p></CardHeader><CardContent className="space-y-4">{children}</CardContent></Card>;
}
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="flex min-w-0 flex-col gap-1.5 text-xs text-muted-foreground">{label}{children}</label>;
}
export function Numeric({ label, value, onChange, min = 0, max = 1e15, integer = false }: { label: string; value: number; onChange: (v: number) => void; min?: number; max?: number; integer?: boolean }) {
  return <Field label={label}><Input type="number" inputMode="decimal" min={min} max={max} step={integer ? 1 : "any"} value={value} onChange={e => { const n = Number(e.target.value); if (Number.isFinite(n)) onChange(Math.min(max, Math.max(min, integer ? Math.floor(n) : n))); }} /></Field>;
}
export function Stat({ title, value }: { title: string; value: string }) {
  return <div className="min-w-0 rounded-2xl border border-border bg-background/45 p-3"><p className="text-[11px] text-muted-foreground">{title}</p><p className="mt-1 break-words text-xl font-semibold tabular-nums">{value}</p></div>;
}
export function Remove({ label, onClick }: { label: string; onClick: () => void }) {
  const { t } = useI18n();
  return <Button variant="ghost" size="icon" className="size-11 shrink-0" aria-label={`${t("Remove", "削除")}: ${label}`} onClick={onClick}><Trash2 className="size-4"/></Button>;
}
