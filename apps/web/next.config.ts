import path from "node:path";
import type { NextConfig } from "next";

// Where the Rust API listens. In Docker Compose a Caddy front proxy routes /api to the API
// directly; this rewrite makes `pnpm dev` / `next start` work without a proxy.
const API_URL = (process.env.SKYPILOT_API_URL ?? "http://localhost:8080").replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  transpilePackages: ["@skypilot/ui", "@skypilot/protocol"],
  poweredByHeader: false,
  reactStrictMode: true,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_URL}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
        ],
      },
      {
        source: "/sw.js",
        headers: [
          { key: "Cache-Control", value: "no-cache" },
          { key: "Service-Worker-Allowed", value: "/" },
        ],
      },
    ];
  },
};

export default nextConfig;
