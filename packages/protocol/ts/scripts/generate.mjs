// Regenerates src/generated from the Rust protocol crate (ts-rs) and post-processes it.
import { spawnSync } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "..", "..", "..", "..");
const out = join(here, "..", "src", "generated");
const r = spawnSync("cargo", ["test", "-p", "skypilot-protocol", "--quiet"], {
  cwd: repo,
  stdio: "inherit",
  env: { ...process.env, TS_RS_EXPORT_DIR: out },
});
if (r.status !== 0) process.exit(r.status ?? 1);
await import("./postprocess.mjs");
