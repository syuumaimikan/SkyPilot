import assert from "node:assert/strict";
import { rustDeskTarget, rustDeskLink } from "../apps/web/lib/rustdesk.ts";
assert.equal(rustDeskTarget(" PC.TAIL123.TS.NET "), "pc.tail123.ts.net:21118");
assert.equal(rustDeskTarget("100.64.0.0:65535"), "100.64.0.0:65535");
assert.equal(rustDeskTarget("100.127.255.255"), "100.127.255.255:21118");
assert.equal(rustDeskLink("100.67.188.35"), "rustdesk://connection/new/100.67.188.35:21118");
for (const target of ["", "123456789", "8.8.8.8", "192.168.1.1", "100.63.1.1", "100.128.1.1", "100.64.0.256", "100.064.0.1", "evil.ts.net.attacker.test", "pc.ts.net", "-pc.tail.ts.net", "https://pc.tail.ts.net", "100.64.0.1:0", "100.64.0.1:65536", "100.64.0.1:", "100.64.0.1:21118?password=secret", "user@pc.tail.ts.net", "pc.tail.ts.net/path"]) {
  assert.equal(rustDeskTarget(target), null, target);
  assert.equal(rustDeskLink(target), null, target);
}
console.log("RustDesk private-target validation passed.");
