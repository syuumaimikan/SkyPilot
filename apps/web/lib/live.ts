"use client";

import { useEffect, useRef, useState } from "react";
import type { FlipList } from "@skypilot/protocol";
import { api, wsUrl } from "./api";

export type LiveState = "connecting" | "live" | "polling";

/**
 * Live flip list: WebSocket push from the API, falling back to polling every 20 s when the
 * socket cannot be opened (e.g. a proxy without WebSocket support).
 */
export function useLiveFlips(): { list: FlipList | null; state: LiveState; error: unknown } {
  const [list, setList] = useState<FlipList | null>(null);
  const [state, setState] = useState<LiveState>("connecting");
  const [error, setError] = useState<unknown>(null);
  const retries = useRef(0);

  useEffect(() => {
    let ws: WebSocket | null = null;
    let poll: ReturnType<typeof setInterval> | null = null;
    let reconnect: ReturnType<typeof setTimeout> | null = null;
    let closed = false;

    const fetchOnce = () =>
      api
        .get<FlipList>("/api/market/flips", { limit: 200 })
        .then((l) => {
          setList(l);
          setError(null);
        })
        .catch(setError);

    const startPolling = () => {
      setState("polling");
      if (!poll) {
        void fetchOnce();
        poll = setInterval(fetchOnce, 20_000);
      }
    };

    const connect = () => {
      if (closed) return;
      setState("connecting");
      try {
        ws = new WebSocket(wsUrl("/api/ws/market"));
      } catch {
        startPolling();
        return;
      }
      ws.onopen = () => {
        retries.current = 0;
        setState("live");
        if (poll) {
          clearInterval(poll);
          poll = null;
        }
        // The socket's first frame only carries the top flips; load the full list once.
        void fetchOnce();
      };
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data as string) as { kind: string; data: unknown };
          if (msg.kind === "flips") setList(msg.data as FlipList);
        } catch {
          // ignore malformed frames
        }
      };
      ws.onclose = () => {
        if (closed) return;
        startPolling();
        retries.current += 1;
        const delay = Math.min(60_000, 2_000 * 2 ** Math.min(retries.current, 5));
        reconnect = setTimeout(connect, delay);
      };
      ws.onerror = () => ws?.close();
    };

    connect();
    return () => {
      closed = true;
      ws?.close();
      if (poll) clearInterval(poll);
      if (reconnect) clearTimeout(reconnect);
    };
  }, []);

  return { list, state, error };
}
