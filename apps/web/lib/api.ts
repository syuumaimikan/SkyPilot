import type { Me } from "@skypilot/protocol";

/** Error returned by the SkyPilot API (`ApiError` body) or by the network layer. */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public lastValidSnapshotAgeSeconds: number | null = null,
  ) {
    super(message);
  }
}

let csrfToken: string | null = null;
let mePromise: Promise<Me> | null = null;

/** Session bootstrap: creates an anonymous workspace on first visit and returns the CSRF token. */
export function fetchMe(force = false): Promise<Me> {
  if (!mePromise || force) {
    mePromise = request<Me>("GET", "/api/auth/me").then((me) => {
      csrfToken = me.csrfToken;
      return me;
    });
    mePromise.catch(() => {
      mePromise = null;
    });
  }
  return mePromise;
}

type Query = Record<string, string | number | boolean | null | undefined>;

function buildUrl(path: string, query?: Query) {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== undefined && v !== null && v !== "") params.set(k, String(v));
  }
  const qs = params.toString();
  return qs ? `${path}${path.includes("?") ? "&" : "?"}${qs}` : path;
}

async function request<T>(method: string, path: string, body?: unknown, query?: Query, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  // Make sure the (anonymous) session cookie exists before the first real request, so a
  // visitor who opens any page directly is not answered with 401.
  if (path !== "/api/auth/me" && !csrfToken) {
    try {
      await fetchMe();
    } catch {
      // API unreachable: the request below reports the network error
    }
  }
  if (method !== "GET" && csrfToken) headers["x-csrf-token"] = csrfToken;
  let res: Response;
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw new ApiError(0, "NETWORK", "SkyPilot API is unreachable");
  }
  if (res.ok) {
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  }
  let code = "HTTP_" + res.status;
  let message = res.statusText || "request failed";
  let age: number | null = null;
  try {
    const err = await res.json();
    code = err.code ?? code;
    message = err.message ?? message;
    age = err.lastValidSnapshotAgeSeconds ?? null;
  } catch {
    // non-JSON error body (e.g. proxy error page)
  }
  // Session expired or CSRF token rotated: refresh the session once and retry.
  if (!retried && ((res.status === 401 && code === "UNAUTHORIZED") || (method !== "GET" && res.status === 403 && code === "FORBIDDEN"))) {
    await fetchMe(true);
    return request<T>(method, path, body, query, true);
  }
  throw new ApiError(res.status, code, message, age);
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>("GET", path, undefined, query),
  post: <T>(path: string, body?: unknown, query?: Query) => request<T>("POST", path, body ?? {}, query),
  put: <T>(path: string, body?: unknown, query?: Query) => request<T>("PUT", path, body ?? {}, query),
  del: <T>(path: string, query?: Query) => request<T>("DELETE", path, undefined, query),
};

export function wsUrl(path: string): string {
  const base = process.env.NEXT_PUBLIC_API_WS_URL;
  if (base) return base.replace(/\/$/, "") + path;
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}${path}`;
}
