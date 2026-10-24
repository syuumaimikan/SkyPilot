"use client";

import { useCallback, useMemo, useState, useSyncExternalStore } from "react";
import { blankAdventure, decodeAdventure, validateAdventure, type Adventure } from "@/lib/adventure";

function subscribe(listener: () => void) {
  window.addEventListener("storage", listener); window.addEventListener("adventure-change", listener);
  return () => { window.removeEventListener("storage", listener); window.removeEventListener("adventure-change", listener); };
}
export function useAdventure(key: string) {
  const raw = useSyncExternalStore(subscribe, useCallback(() => {
    try { return window.localStorage.getItem(key) ?? ""; } catch { return "!unavailable"; }
  }, [key]), () => "");
  const parsed = useMemo(() => {
    try { return { data: decodeAdventure(raw), valid: true }; } catch { return { data: blankAdventure(), valid: false }; }
  }, [raw]);
  const [error, setError] = useState("");
  const [undo, setUndo] = useState<{ before: string; after: string } | null>(null);
  const update = useCallback((change: (previous: Adventure) => Adventure, replace = false) => {
    try {
      const before = window.localStorage.getItem(key) ?? "";
      const next = validateAdventure(change(replace ? blankAdventure() : decodeAdventure(before)));
      const after = JSON.stringify(next);
      window.localStorage.setItem(key, after);
      setUndo({ before, after }); setError(""); window.dispatchEvent(new Event("adventure-change"));
      return true;
    } catch { setError("保存できませんでした。入力件数・値・端末の空き容量を確認してください。既存のデータは保持されています。"); return false; }
  }, [key]);
  function undoChange() {
    if (!undo) return;
    try {
      if (window.localStorage.getItem(key) !== undo.after) { setError("別の画面で変更されています。再読み込みして内容を確認してください。"); return; }
      if (undo.before) window.localStorage.setItem(key, undo.before); else window.localStorage.removeItem(key);
      setUndo(null); setError(""); window.dispatchEvent(new Event("adventure-change"));
    } catch { setError("変更を戻せませんでした。端末の保存設定を確認してください。"); }
  }
  return { ...parsed, error, update, undoChange, canUndo: undo !== null };
}
export const newId = () => crypto.randomUUID();
export type PlannerStore = ReturnType<typeof useAdventure>;
