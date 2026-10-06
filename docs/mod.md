# SkyPilot Fabric mod (Minecraft 26.2)

## Layout

```
apps/mod/
  core/          config (JSON, sanitised), language, text helpers, sidebar/tab parser
  network/       async HTTP client (timeouts, TTL cache, circuit breaker), device flow, token store, DTOs
  translation/   channel classifier, translation modes, styled-segment model, placeholder protection, async service
  session/       session tracker, action-bar XP parser
  hud/           HUD rows and layout maths
  platform/mc26_2/  Fabric entrypoint, HUD renderer, commands, screens, chat/tooltip hooks (26.2 API)
```

Only `platform/mc26_2` touches Minecraft classes. The other modules are plain Java 25,
unit-tested without the game, and nested into the mod jar (jar-in-jar).

## Build

- Gradle 9.7.1 wrapper (checksum-pinned), Fabric Loom 1.18.2, Fabric Loader 0.19.5,
  Fabric API 0.161.0+26.2.
- Loom 1.18 needs Gradle to run on Java 25: `gradle/gradle-daemon-jvm.properties` makes the
  wrapper download a JDK 25 automatically (foojay), so any JDK ≥ 17 can start the build.
- `./gradlew build` → `platform/mc26_2/build/libs/skypilot-<version>.jar` + `.sha256`
  (and a jarsigner signature when `SKYPILOT_SIGN_*` are set).
- `./gradlew :platform:mc26_2:runClient` starts a development client;
  `./gradlew :platform:mc26_2:runClientGameTest` runs the in-game test (`src/gametest`).

## Runtime behaviour

- Every network call is asynchronous with a 6 s timeout; results are applied on the game
  thread with `Minecraft.execute`. A circuit breaker pauses requests to a failing server.
- HUD state refreshes every 60 s, or 5 s after the area changes, and only while linked.
- Price tooltips: at most one request per 250 ms; results kept in an LRU of 256 items and refreshed after 5 minutes.
- Translation: dictionary phrases are answered locally; other lines go through a bounded
  worker (4 in flight, further lines skipped), 4 s timeout, LRU cache of 512, circuit breaker.
  Player names, SkyBlock terms, numbers and commands are replaced by `⟦n⟧` placeholders and
  restored; a translation that loses a placeholder is discarded. The original chat line is
  never changed - the translation is added below it with the original prefix components
  (rank, name with its hover/click events) preserved.
- Click to translate (`translation.clickToTranslate`, off by default; `/skypilot translate click on`
  or the settings screen): single-line messages with text worth translating get a ` [訳]` button
  appended after the server's component (`ClientReceiveMessageEvents.MODIFY_GAME`), so the
  message's own hover/click events keep working. The button runs the client command
  `/skypilot tr <id>`; Fabric executes it locally (chat clicks go through
  `ClientPacketListener.sendUnattendedCommand`, which Fabric's command API intercepts), so nothing
  reaches the game server. The clicked line is translated (after the one-time disclosure) and
  replaced in the chat history in place (`ChatComponentAccessor`: `allMessages` +
  `refreshTrimmedMessages`, scroll position kept); `[原文]` switches back. Up to 300 recent
  lines are kept in memory for this and cleared on disconnect.
- Config: `config/skypilot/config.json`; token: `config/skypilot/token.json` (SkyPilot token only).

## In-game GUI

Everything is display only: no clicks, purchases, movement or packets on the player's behalf.
Server commands are only put into the chat box (`ChatScreen` prefill / `SuggestCommand`).

- **Menu** (`J`, `/skypilot menu`): Now / Roadmap / Guides / Advice / Gear / AH / Bazaar / Alerts
  tabs with item icons (`/api/mod/{plan,market,alerts,guides,gear,icons}`).
- **Side panels** (`ScreenOverlays`): AH/Bazaar flips beside Hypixel's market windows
  (`market.containerPanel`), the item browser right of inventory windows
  (`inventory.itemBrowser`: search, click for value / recipes with a 3×3 grid / drop sources /
  uses), and the chest value list left of chests, backpacks and the ender chest
  (`inventory.chestValue`: total and items by value; clicking a name outlines the slot). Item
  tooltips get a value line (`priceTooltip`).
- **Guides**: steps from `/api/mod/guides`; following one draws a box, a straight line and a
  HUD arrow to the step's public place (Gizmos in `LevelRenderEvents.BEFORE_GIZMOS`). Only on the
  matching island; no pathfinding.
- **Farm build guide** (`/skypilot farm start <id>`): outlines the next layer's blocks that are
  not placed yet (checked every 5 ticks).
- **Settings** (`/skypilot settings`, Mod Menu-free): pages General (language, server URL —
  changing it deletes the token — link/unlink), HUD (toggles, scale, opacity, position),
  Translate (mode, click to translate, provider: SkyPilot server / own DeepL key / own
  Claude key / dictionary only, a masked key field, connection test), Market, Inventory, and
  Session & guides (tracking, upload, stop guide / farm guide). The provider and key are stored
  per user on the SkyPilot server (`/api/me/settings`); the key is never written to the config
  or shown again.

## Feature switches

Every optional feature has its own switch (`core/Features.java`, stored in `features` in the
config; older features keep their own fields). Settings → **Features** lists them by category
with search, "Recommended" (turns on the `recommended` set, never anything that sends chat text)
and "All off"; `/skypilot feature <id> on|off` does the same from chat. Hypixel publishes no list
of allowed mods, so every feature added with the registry is **off by default**. ☁ marks
features that use the SkyPilot server (never Hypixel), ✉ those that send chat text (consent).

| Category | Features |
|---|---|
| Chat | click to translate ✉, timestamps, stack repeated messages (x2), click to copy, mention highlight + ping, keyword alerts (`keyword add`), coordinates → [Waypoint], chat search (`find`), hide lobby joins / warp lines / profile ID / kill combo / ability spam / GEXP / [Sacks] / separator lines, write in Japanese (`/skypilot en`, JA→EN into the chat box) ✉ |
| Tooltips | value ☁, NPC sell price ☁, craft cost ☁, stack value ☁, Bazaar flip margin after tax ☁, SkyBlock ID, BIN vs value on auction listings ☁, hide filler glass panes |
| Inventory | item list ☁, chest value ☁, inventory value ☁, rarity background, slot numbers (book level, pet level, minion tier, stars, skill/collection levels), search highlight, R / U lookup keys ☁, auction deal outlines ☁, valuable item outlines (`highlight <price>`) ☁, Ctrl+C copies the item ID |
| HUD | SkyPilot HUD ☁, area advice ☁, session, coordinates, speed, compass bar, FPS + ping, clock + SkyBlock date ☁, event timers ☁, play time, purse changes, health/mana bars, skill progress (XP/h, time to next level), active pet (tab), profile info (tab), full inventory warning, low-health border |
| Mining | commissions, powder + powder/h, forges, crystals (tab list), ability ready title, hide treasure chest spam, treasure chest tracker (chests/h, powder) |
| Farming | blocks per second, Garden visitor cost ☁, Garden info (level, copper, sowdust, composter, pests), Jacob's contest widget, Jacob's contest toast ☁ |
| Fishing | rare sea creature alert ☁ (SkyHanni-REPO names), fishing tracker (sea creatures by name, treasure, trophy tiers, per hour) ☁, bobber timer, trophy fish alert, trophy fish widget |
| Combat | slayer tracker, boss spawned title, rare drop title, drop tracker |
| Dungeons | chat filter (healing, damage taken, orbs, tethers, cooldowns; never player chat), run timer (floor, time, boss room split, cleared % from the sidebar), boss room title |
| Market | flip panel ☁, flip alerts ☁, alert sound, price watchlist ☁, Dark Auction toast ☁ |
| Tools | calculator (`calc 1.5m*3+200k`), reminders (`remind 30m …`), own waypoints (`wp add/show/remove/list`), to-do list (`todo`), stopwatch (`timer start/lap/stop`), item lookup (`lookup <name>`) ☁, session tracking, session upload ☁ |

Implementation notes:

- Chat: `ALLOW_GAME` sees every server line first (trackers count hidden lines too) and hides the chosen groups from this client's chat only;
  `ChatFeatures.modify` (`MODIFY_GAME`) applies stacking, copy, mention, the waypoint button,
  then the [訳] button, then the timestamp, and tells click-to-translate about the wrapped line
  so translation still replaces it in place. Message texts follow SkyHanni's current filters.
- Slots: `SlotRenderMixin` wraps `AbstractContainerScreen.extractSlot` (draw only) for the rarity
  colour, numbers and search dimming.
- Info HUD: a second box (`InfoHud`, `infoHud` in the config) below the SkyPilot HUD by
  default; both boxes are moved and resized in the HUD editor.
- Tab list widgets ("Commissions:", "Powders:") are read in the order the tab list draws them
  (`PlayerTabOverlayAccessor`).
- SkyBlock time and the Dark Auction / Jacob's contest schedule come from `/api/mod/calendar`
  (curated `calendar.toml`), recomputed locally every second.

## Adding a Minecraft version

Create `platform/mcXX_Y` with its own Loom configuration and copy the platform classes,
adapting renamed Minecraft APIs. The shared modules need no changes.
