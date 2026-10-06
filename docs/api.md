# HTTP API

Base path `/api`. JSON in and out; errors are `{ code, message, lastValidSnapshotAgeSeconds }`.
Types: `packages/protocol` (Rust) → `packages/protocol/ts` (TypeScript, generated).

Authentication: browsers get an anonymous workspace cookie (`sp_session`, HttpOnly) from
`GET /api/auth/me`, which also returns the CSRF token to send as `x-csrf-token` on every
state-changing request. The mod uses `Authorization: Bearer spm_…` from the device flow.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health`, `/status` | - | Liveness; registry/Hypixel/collector/store status |
| GET | `/auth/me` | cookie | Session bootstrap (CSRF token, linked devices) |
| POST | `/auth/register`, `/auth/login`, `/auth/logout` | CSRF | Optional SkyPilot login for the workspace |
| DELETE | `/devices/{id}` | CSRF | Revoke a linked mod |
| POST | `/device/code` | - | Mod: start device authorization |
| POST | `/device/token` | - | Mod: poll (`authorization_pending` / `slow_down` / `expired_token` / `access_denied` / `ok`) |
| GET | `/device/lookup?user_code=` | cookie | Web: which device asks |
| POST | `/device/approve` | CSRF | Web: approve / deny |
| GET | `/players/{name}/profiles` | - | Profile list for the selector |
| POST | `/analyze?lang=` | CSRF | Fetch + analyse + plan (`AnalyzeRequest` → `AnalyzeResponse`) |
| GET | `/workspace` | cookie | Saved goals, prefs and last analysis |
| PUT | `/workspace/goals?lang=` | CSRF | Replace goals (≤6) and re-plan from stored data |
| PUT | `/workspace/prefs` | CSRF | Play style, budget, language |
| GET | `/snapshots` | cookie/bearer | Progress history |
| GET | `/advisor` | - | Advisor domains |
| GET | `/advisor/report/{domain}?lang=&budget=` | cookie/bearer | `AdvisorReport` for a domain |
| GET | `/advisor/mining/calc`, `/advisor/mining/hotm?preset=`, `/advisor/mining/powder` | cookie/bearer | Mining calculator, HOTM planner, powder planner |
| GET | `/advisor/gear?purpose=&budget=` | cookie/bearer | Gear optimizer |
| GET | `/advisor/slayer/plan?slayer=&target=` | cookie/bearer | Slayer planner |
| GET | `/advisor/money`, `/advisor/dungeons/floors`, `/advisor/farming/crops?bps=`, `/advisor/liquidation` | cookie/bearer | Rankings and tables |
| GET | `/farms`, `/farms/{id}` | - | Farm designs (blocks, materials, build steps) |
| GET | `/market/status`, `/market/flips`, `/market/bazaar?capital=`, `/market/craft`, `/market/time`, `/market/search?q=`, `/market/items/{id}?range=1H…90D` | - | Market data |
| POST | `/market/backtest` | - | Backtest flip parameters |
| GET (WS) | `/ws/market` | - | Live `{kind:"flips"|"status", data}` frames |
| GET/PUT/DELETE | `/notifications/rules[/{id}]` | cookie (+CSRF) | Flip alert rules |
| GET | `/notifications/push/key` | - | VAPID public key |
| POST | `/notifications/push/subscribe`, `/unsubscribe`, `/test` | CSRF | Web Push |
| POST | `/assistant/chat` | CSRF | Grounded assistant |
| POST | `/translate` | cookie/bearer | EN→JA translation (rate limited) |
| GET | `/translate/dictionary`, `/translate/status` | - | Dictionary, provider + privacy notice |
| GET | `/mod/hud?area=&lang=`, `/mod/text/{goal|next|roadmap|market|mining|slayer|farming}` | bearer | Mod HUD and panels |
| POST | `/mod/price`, `/mod/session` | bearer | Price check, session statistics upload |
| GET | `/mod/market?kind=ah\|bazaar&limit=&sort=score\|profit\|roi\|hour&budget=&flipper=0..2` | - | Flip panel rows (budget = the player's purse; flipper = Bazaar Flipper level for the tax) |
| GET | `/mod/items?q=&category=&offset=&limit≤600`, `/mod/item/{id}[?brief=true]` | - | Item browser pages (categories: all, weapons, armor, equipment, accessories, tools, materials, minions, pet_items, reforge_stones, cosmetics, other); item page or hover card |
| GET | `/mod/bazaar/quotes?names=A\|B` | - | Live instant buy / sell by product name (≤40 names) for the order helper |
| GET | `/mod/news` | - | Hypixel SkyBlock news (cached 30 min) |
| GET | `/mod/server-status` | - | Hypixel's status page (status.hypixel.net): SkyBlock and core services, maintenance in progress / scheduled (with Hypixel's own text, e.g. the version), open incidents, the last 3 finished SkyBlock maintenances. Cached 2 min; the history is read at most every 30 min in the background; errors keep the last answer (`available: false` when there is none) |
| GET | `/launcher/latest`, `/launcher/files/{name}` | - | Launcher update feed from `LAUNCHER_RELEASES_DIR`: signed `latest.json` + installer (the launcher checks the Ed25519 signature and SHA-256) |

Limits: request body 256 KB; general 240 req/min/IP; analyze 6/min/IP; auth 10/min/IP;
translate 240/min/user.
