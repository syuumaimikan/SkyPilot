#!/usr/bin/env node
// End-to-end smoke test against a running SkyPilot stack (no dependencies, Node 20+).
//
//   node tests/e2e/smoke.mjs http://localhost:3000
//
// Covers: web pages render, API health/status, market data, item search & price detail,
// farm designs, session + CSRF, device authorization (mod linking) and the mod endpoints,
// translation, and the market WebSocket. Profile analysis is checked only when the server
// has a Hypixel API key (it reports keyConfigured in /api/status).

const base = (process.argv[2] ?? "http://localhost:3000").replace(/\/$/, "");
let failures = 0;
let cookie = "";

function ok(name, cond, detail = "") {
  if (cond) {
    console.log(`  ok   ${name}`);
  } else {
    failures++;
    console.log(`  FAIL ${name} ${detail}`);
  }
}

async function req(method, path, body, headers = {}) {
  const res = await fetch(base + path, {
    method,
    headers: { Accept: "application/json", ...(body ? { "Content-Type": "application/json" } : {}), ...(cookie ? { Cookie: cookie } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
    redirect: "manual",
  });
  const setCookie = res.headers.getSetCookie?.() ?? [];
  for (const c of setCookie) if (c.startsWith("sp_session=")) cookie = c.split(";")[0];
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    // HTML page or empty body
  }
  return { status: res.status, json, text, headers: res.headers };
}

async function main() {
  console.log(`SkyPilot smoke test → ${base}`);

  console.log("web");
  for (const page of ["/", "/market/flips", "/market/bazaar", "/market/price", "/farming/builder", "/link", "/settings"]) {
    const r = await req("GET", page);
    ok(`GET ${page}`, r.status === 200 && r.text.includes("SkyPilot"), `status ${r.status}`);
  }
  const home = await fetch(base + "/");
  ok("CSP header with nonce", (home.headers.get("content-security-policy") ?? "").includes("'nonce-"));

  console.log("api");
  const health = await req("GET", "/api/health");
  ok("health", health.json?.ok === true);
  const status = await req("GET", "/api/status");
  ok("status", typeof status.json?.registry?.items === "number" && status.json.registry.items > 1000, JSON.stringify(status.json?.registry));
  const market = await req("GET", "/api/market/status");
  ok("market status", market.json && "auctions" in market.json);
  const bazaar = await req("GET", "/api/market/bazaar?capital=10000000");
  ok("bazaar products", (bazaar.json?.products?.length ?? 0) > 100, `products=${bazaar.json?.products?.length}`);
  const flips = await req("GET", "/api/market/flips?limit=20");
  ok("flip list", Array.isArray(flips.json?.flips));
  const search = await req("GET", "/api/market/search?q=hyperion");
  ok("item search", search.json?.[0]?.itemId === "HYPERION", JSON.stringify(search.json?.[0]));
  const price = await req("GET", "/api/market/items/ENCHANTED_DIAMOND?range=24H");
  ok("price detail", price.json?.itemId === "ENCHANTED_DIAMOND" && price.json?.npcSellPrice > 0);
  const farms = await req("GET", "/api/farms");
  ok("farm designs", (farms.json?.length ?? 0) === 8);
  const farm = await req("GET", "/api/farms/wheat_horizontal");
  ok("farm design detail", (farm.json?.blocks?.length ?? 0) > 1000 && (farm.json?.buildSteps?.length ?? 0) > 2);

  console.log("session & csrf");
  const me = await req("GET", "/api/auth/me");
  ok("anonymous workspace", typeof me.json?.csrfToken === "string" && cookie.startsWith("sp_session="));
  const csrf = me.json?.csrfToken;
  const noCsrf = await req("PUT", "/api/workspace/prefs", { playStyle: "SAFE" });
  ok("state change without CSRF token is rejected", noCsrf.status === 403, `status ${noCsrf.status}`);
  const prefs = await req("PUT", "/api/workspace/prefs", { playStyle: "SAFE", lang: "ja" }, { "x-csrf-token": csrf });
  ok("prefs with CSRF token", prefs.status === 200);
  const noAnalysis = await req("GET", "/api/advisor/report/mining");
  ok("advisor without analysis explains", noAnalysis.status === 409 && noAnalysis.json?.code === "NO_ANALYSIS");

  console.log("device authorization (mod linking)");
  const savedCookie = cookie;
  cookie = "";
  const code = await req("POST", "/api/device/code", { deviceName: "smoke test" });
  ok("device code", /^[A-Z]{4}-[A-Z]{4}$/.test(code.json?.userCode ?? ""), JSON.stringify(code.json));
  const pending = await req("POST", "/api/device/token", { deviceCode: code.json.deviceCode });
  ok("token pending before approval", pending.json?.status === "authorization_pending" || pending.json?.status === "slow_down", pending.json?.status);
  cookie = savedCookie;
  const lookup = await req("GET", `/api/device/lookup?user_code=${code.json.userCode}`);
  ok("web shows requesting device", lookup.json?.deviceName === "smoke test");
  const approve = await req("POST", "/api/device/approve", { userCode: code.json.userCode, approve: true }, { "x-csrf-token": csrf });
  ok("approve", approve.json?.approved === true);
  cookie = "";
  await new Promise((r) => setTimeout(r, 5200));
  const token = await req("POST", "/api/device/token", { deviceCode: code.json.deviceCode });
  ok("token issued", token.json?.status === "ok" && token.json?.accessToken?.startsWith("spm_"), JSON.stringify(token.json));
  const bearer = { Authorization: `Bearer ${token.json?.accessToken}` };
  const hud = await req("GET", "/api/mod/hud?area=Dwarven%20Mines&lang=en", undefined, bearer);
  ok("mod HUD", hud.json?.linked === true);
  const panel = await req("GET", "/api/mod/text/market?lang=en", undefined, bearer);
  ok("mod market panel", Array.isArray(panel.json?.lines) && panel.json.lines.length > 0);
  const modPrice = await req("POST", "/api/mod/price", { skyblockId: "ENCHANTED_DIAMOND", customData: { id: "ENCHANTED_DIAMOND" } }, bearer);
  ok("mod price check", modPrice.json?.skyblockId === "ENCHANTED_DIAMOND" && modPrice.json?.bazaarBuy > 0);
  const ahPanel = await req("GET", "/api/mod/market?kind=ah&limit=10&lang=ja");
  ok("mod AH flips (public)", ahPanel.json?.kind === "AH" && Array.isArray(ahPanel.json?.rows) && ahPanel.json.rows.every((r) => r.icon?.item && r.command?.startsWith("/viewauction ")));
  const bzPanel = await req("GET", "/api/mod/market?kind=bazaar&limit=10&lang=ja");
  ok("mod Bazaar flips (public)", bzPanel.json?.kind === "BAZAAR" && Array.isArray(bzPanel.json?.rows));
  const icons = await req("GET", "/api/mod/icons?ids=HYPERION,LAPIS_ARMOR_LEGGINGS,PET:ENDER_DRAGON:LEGENDARY");
  ok("mod item icons", icons.json?.HYPERION?.item === "minecraft:iron_sword" && icons.json?.LAPIS_ARMOR_LEGGINGS?.color === 255 && !!icons.json?.["PET:ENDER_DRAGON:LEGENDARY"]?.texture);
  const modPlan = await req("GET", "/api/mod/plan?lang=ja", undefined, bearer);
  ok("mod plan", modPlan.status === 200 && Array.isArray(modPlan.json?.actions));
  const modAlerts = await req("GET", `/api/mod/alerts?since=${Date.now() - 60000}&lang=ja`, undefined, bearer);
  ok("mod flip alerts", modAlerts.status === 200 && Array.isArray(modAlerts.json?.rows) && modAlerts.json.rules.length > 0);
  const items = await req("GET", "/api/mod/items?q=hyperion&limit=5");
  ok("mod item browser search", items.json?.items?.some((i) => i.id === "HYPERION"), JSON.stringify(items.json)?.slice(0, 200));
  const item = await req("GET", "/api/mod/item/HYPERION?lang=ja");
  ok("mod item detail with recipe", item.json?.item?.id === "HYPERION" && item.json.recipes.length > 0);
  const values = await req("POST", "/api/mod/values", { items: [{ customData: { id: "ENCHANTED_DIAMOND" }, count: 64 }, { customData: {}, count: 1 }] });
  ok("mod chest values", values.json?.values?.length === 2 && values.json.total > 0 && values.json.values[1].value === null);
  const guides = await req("GET", "/api/mod/guides?lang=ja", undefined, bearer);
  ok("mod guides with waypoints", guides.json?.guides?.length >= 3 && guides.json.guides.every((g) => g.steps.some((s) => s.waypoint)));
  const cal = await req("GET", "/api/mod/calendar");
  const da = cal.json?.events?.find((e) => e.id === "dark_auction");
  ok(
    "mod calendar (Dark Auction every 3 SkyBlock days at :55)",
    cal.json?.dayMs === 1200000 && cal.json.months.length === 12 && da?.everyDays === 3 && new Date(da.startMs).getUTCMinutes() === 55 && da.startMs > Date.now() - 1000,
    JSON.stringify(da),
  );
  ok("price has NPC sell price", typeof modPrice.json?.npcSell === "number", JSON.stringify(modPrice.json));
  const gear = await req("GET", "/api/mod/gear?lang=ja", undefined, bearer);
  ok("mod gear", gear.status === 200 && Array.isArray(gear.json?.sections));
  // Per-user translation provider: keys are stored encrypted and only "…last4" comes back.
  const settings0 = await req("GET", "/api/me/settings", undefined, bearer);
  ok("user settings (default provider)", settings0.json?.translation?.provider === "server" && settings0.json.translation.providers.includes("deepl"));
  const fakeKey = "smoketest-0000-1111-2222:fx";
  const setKey = await req("PUT", "/api/me/settings/translation", { provider: "deepl", deeplKey: fakeKey, anthropicKey: null }, bearer);
  ok(
    "save own provider key (masked)",
    setKey.json?.translation?.provider === "deepl" && setKey.json.translation.deepl.configured && setKey.json.translation.deepl.hint === "…2:fx" && !setKey.text.includes(fakeKey),
  );
  const badKey = await req("PUT", "/api/me/settings/translation", { provider: "deepl", deeplKey: "not a key", anthropicKey: null }, bearer);
  ok("invalid key rejected", badKey.status === 400);
  const badProvider = await req("PUT", "/api/me/settings/translation", { provider: "google", deeplKey: null, anthropicKey: null }, bearer);
  ok("unknown provider rejected", badProvider.status === 400);
  const noneTr = await req("PUT", "/api/me/settings/translation", { provider: "none", deeplKey: "", anthropicKey: null }, bearer);
  ok("dictionary only + key removed", noneTr.json?.translation?.provider === "none" && !noneTr.json.translation.deepl.configured);
  const dictOnly = await req("POST", "/api/translate", { text: "is anyone selling a cheap sword here", sourceLang: "en", targetLang: "ja", channel: "PARTY" }, bearer);
  ok("dictionary-only user is not sent to a provider", dictOnly.json?.unchanged === true || dictOnly.json?.provider === "dictionary", JSON.stringify(dictOnly.json));
  await req("PUT", "/api/me/settings/translation", { provider: "server", deeplKey: null, anthropicKey: null }, bearer);
  const bad = await req("GET", "/api/mod/hud", undefined, { Authorization: "Bearer spm_" + "0".repeat(64) });
  ok("invalid token rejected", bad.status === 401);
  const tr = await req("POST", "/api/translate", { text: "good luck", sourceLang: "en", targetLang: "ja", channel: "PARTY" }, bearer);
  ok("translate", typeof tr.json?.translated === "string", JSON.stringify(tr.json));
  cookie = savedCookie;
  const meAfter = await req("GET", "/api/auth/me");
  ok("device listed in workspace", (meAfter.json?.linkedDevices ?? []).some((d) => d.name === "smoke test"));

  console.log("websocket");
  const wsUrl = base.replace(/^http/, "ws") + "/api/ws/market";
  const first = await new Promise((resolve) => {
    const ws = new WebSocket(wsUrl);
    const timer = setTimeout(() => {
      ws.close();
      resolve(null);
    }, 8000);
    ws.onmessage = (ev) => {
      clearTimeout(timer);
      ws.close();
      resolve(String(ev.data));
    };
    ws.onerror = () => {
      clearTimeout(timer);
      resolve(null);
    };
  });
  ok("market WebSocket pushes flips", first !== null && JSON.parse(first).kind === "flips");

  if (status.json?.hypixel?.keyConfigured) {
    console.log("analysis (Hypixel key configured)");
    const r = await req("POST", "/api/analyze?lang=en", { username: process.env.SKYPILOT_E2E_PLAYER ?? "Technoblade", goal: "Enderman Slayer 7 and Terminator", secondaryGoals: [], playStyle: "FASTEST", profileId: null, budget: null }, { "x-csrf-token": csrf });
    ok("analyze", r.status === 200 && Array.isArray(r.json?.actions), `status ${r.status} ${r.json?.code ?? ""}`);
  } else {
    console.log("analysis skipped (server has no HYPIXEL_API_KEY)");
  }

  console.log(failures === 0 ? "ALL PASSED" : `${failures} FAILED`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
