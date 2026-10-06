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
- The SkyPilot Launcher (`apps/launcher`, a subproject of this build) bundles the mod jar and
  sets up a Fabric 26.2 profile for the official Minecraft Launcher; see [launcher.md](launcher.md).
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

- **Menu** (`J`, `/skypilot menu`): Now / Roadmap / Fit / Guides / Advice / Builds / Gear / Money /
  AH / Bazaar / Alerts tabs with item icons (`/api/mod/{plan,fit,builds,money,market,alerts,guides,gear,icons}`).
  Actions and money methods with a known place have a "▶ Guide" link: it draws the guide line to
  that public place (curated `places.toml`, SkyHanni-REPO place names) and, off the island, puts
  the closest warp landing (graph nodes tagged `warp`) into the chat box for the player to send.
- **Fit**: dungeon class fit (class level + the most valuable owned weapon of that class, from
  Hypixel's item category/stats; labelled heuristic), floor readiness, slayer readiness, and per
  goal the remaining steps, cost and time with the action that moves it most.
- **Builds**: each slayer's Required / Recommended / Luxury loadout (curated `slayers.toml`) with
  what the player owns and the cost to complete every tier; items open their recipe page.
- **Money**: money methods ranked for the player (coins/h ranges from live prices), missing
  requirements, and where to do them.
- **Side panels** (`ScreenOverlays`):
  - AH/Bazaar flips beside Hypixel's market windows (`market.containerPanel`): sort chip
    (best / profit / ROI / per hour), "Purse only" (the sidebar purse becomes the budget: AH flips
    above it are left out, Bazaar amounts fit it), Bazaar Flipper level (Settings → Market) for
    the tax. Clicking a row puts `/viewauction …` or `/bz …` into the chat box.
  - The item browser right of inventory windows (`inventory.itemBrowser`): the grid fills the
    whole panel (pages of up to 600 cells), category chips (weapons, armor, equipment,
    accessories, tools, materials, minions, pet items, reforge stones, cosmetics, other - grouped
    from the items resource's `category`), a hover card with the in-game description and the
    known prices (Bazaar buy / sell, lowest BIN with sales per day, NPC sell, craft cost; unknown
    prices are left out), click or R for recipes, U for uses, Ctrl+F for search. The left edge
    can be dragged to resize the list or push it into the screen edge (a tab brings it back);
    width, category and the hidden state are saved (`inventory.itemBrowserWidth / …Category /
    …Collapsed`).
  - The chest value list left of chests, backpacks and the ender chest
    (`inventory.chestValue`: total and items by value; clicking a name outlines the slot).
  - Bazaar order helper in "Your Bazaar Orders" (`market.bazaar_order_helper`): green frame =
    best price, red = outbid (tooltip: by how much), gold = filled; a summary line above the
    window. It reads the order items' name and "Price per unit" lore and compares them with
    `/api/mod/bazaar/quotes`; nothing is clicked.
  - Tooltips of these panels are drawn right away on top (`Ui.overlayTooltip`): a deferred
    tooltip set in a screen's after-extract callback would be dropped with the frame in 26.2.
  Item tooltips get a value line (`priceTooltip`).
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

## Other mods (`/skypilot mods`, Settings → General → Mods…)

- Lists every mod Fabric Loader loaded (mod files, bundled libraries, built-ins) plus
  `*.jar.disabled` files and jars that are not loaded, with name, version, authors, license,
  description, file, what it needs and what needs it, and its icon.
- **Settings** opens the mod's own config screen through Mod Menu's public API (the `modmenu`
  entrypoint, called by reflection - no dependency on Mod Menu; mods without one have no button).
- **Disable / Enable** renames the file in the mods folder (`x.jar` ↔ `x.jar.disabled`); it takes
  effect after a restart. Disabling a mod others need asks for a second click and names them. If
  the OS refuses the rename while the game runs, the screen says so (close the game and rename).
- **Homepage / Source / Issues** open the mod's https links after Minecraft's link confirmation.
- **Check updates** (feature `tools.mod_updates`, off by default): the SHA-1 of each mod file goes
  to the SkyPilot server (`POST /api/mod/mod-updates`), which asks Modrinth
  (`/v2/version_files/update`, cached 1 h, a 429 is not retried) and returns version info and a
  Modrinth link. SkyPilot never downloads, installs or runs other mods' files.

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
| Dungeons (helpers) | puzzle list with each puzzle's rules ☁ (wiki text; never a solver), boss / phase splits from [BOSS] lines, reward chest profit (Wood … Bedrock, Paid / Free: contents value − "… Coins" / "FREE" cost) ☁, hide the solo-class and fairy revive lines |
| More chat filters | welcome line, cheap RARE DROPs (Enchanted Ender Pearl / End Stone, Crystal Fragment), minor notices (0-coin interest, power orb, Mining Speed Boost used/expired, tips), repeated warnings, Sky Mall / Lottery, fire sale banners, achievements, parkour, teleport pads |
| Garden | pest alert ("GROSS!" / "YUCK!" lines, with the plot), visitor arrival toast, pest traps (count, full, no bait; toast when one fills), crop milestones |
| Tab widgets | active effects, timers, daily quests, essence, collection, skills, slayer, bestiary |
| World | mayor and minister with perks ☁ (Hypixel election), fire sale toasts ☁ (Hypixel firesales, cached 5 min) |
| Sacks | value of everything stored in a sack window ("Stored:" × Bazaar value by name) ☁ |
| Rift | Rift time (action bar) and Motes (sidebar), low Rift time titles, Rift guide on entry ☁ |
| Guides | next action on the info HUD ☁, automatic guide line to where the next action is done ☁ |
| Market | flip panel ☁, flip alerts ☁, alert sound, price watchlist ☁, Dark Auction toast ☁ |
| Tools | calculator (`calc 1.5m*3+200k`), reminders (`remind 30m …`), own waypoints (`wp add/show/remove/list`), to-do list (`todo`), stopwatch (`timer start/lap/stop`), item lookup (`lookup <name>`) ☁, session tracking, session upload ☁ |
| Market (more) | Bazaar order helper ☁ |
| Hypixel status | menu tab "Status & events" (`/skypilot hypixel`, `/skypilot events`): SkyBlock / server / login / API state, maintenance in progress or scheduled in your local time with countdowns and Hypixel's text, incidents, recent SkyBlock maintenance, every upcoming SkyBlock event, mayor and minister, fire sales, news. Switches: status line on the info HUD ☁ (`hud.server_status`), maintenance alerts ☁ (`hud.maintenance_alerts`: scheduled, 30 minutes before, started, SkyBlock back, login / server incidents - toast, chat line and sound, each once) |
| Performance | background / menu FPS limits, hide far mobs / far players / damage numbers / falling blocks / fireworks / lightning / explosion, hit and block-break particles, particle limit per tick, SkyPilot lite mode, memory and entity / particle counts on the info HUD; `/skypilot perf` (switches, and a lightweight video preset that saves your values and restores them exactly) |
| AFK guard | AFK detection (no keyboard / mouse input for `afk.minutes`, default 3), alerts while away (private messages, party invites, your name, Limbo / AFK notice, restart warning, death, big health drop, disconnect: sound, toast, flashing taskbar button), welcome-back summary, low FPS while AFK (`afk.fps`, default 10); `/skypilot afk [minutes <n> \| fps <n>]` |

Notes on the newer groups:

- Performance features only draw less: hidden entities still exist, can be hit and react as
  usual; nothing the player does is cancelled. The video preset changes Minecraft's own options
  (render distance ≥ 8, entity distance, particles, shadows, clouds, biome blend, leaves,
  transparency, menu blur) and "Restore" puts back exactly what was saved.
- The AFK guard only watches: input is noted by `KeyboardInputMixin` / `MouseInputMixin`
  (observing, never cancelling). There is **no anti-AFK**: nothing is pressed or moved to avoid
  Hypixel's AFK handling (that would be movement automation, which SkyPilot never does).
- Click to translate plays a short "ding" on click and a bell when the translation is in
  (`translation.sound`, on by default, Settings → Translate).

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
