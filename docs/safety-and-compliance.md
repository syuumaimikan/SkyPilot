# Safety and Hypixel compliance

## Never implemented

Auto Mine, Auto Farm, Auto Slayer, Auto Attack, Auto Aim, Auto Click, Auto Fishing,
Auto Dungeon, Auto Puzzle, Auto Pathfinding, Auto Walking, Auto Bazaar Buy/Sell,
Auto AH Buy/Sell, Auto Warp, Auto GUI, macros, packet manipulation, movement automation,
combat automation. Every in-game action is performed by the player.

## What the mod does

| Feature | Reads | Writes | Default |
|---|---|---|---|
| Roadmap / progress HUD, context advisor | sidebar scoreboard (area, purse) | its own overlay | on |
| Price check | the hovered/held item's own `custom_data` | tooltip lines, chat (command) | on |
| Session tracker | sidebar purse, action-bar XP messages | local statistics; upload only if enabled | tracking on, **upload off** |
| Chat translation | incoming chat lines | an extra client-side chat line below the original | **off** (needs consent) |
| Click to translate | the line the player clicks | a `[訳]` button on chat lines; the clicked line is shown translated in place (`[原文]` restores it); the button runs a client-side command only | **off** (consent on first use) |
| SkyPilot menu (J) | the SkyPilot server (plan, report, guides, gear, flips) | its own screen; buttons only open screens or put a command into the chat box (never sent) | on (opens on key press) |
| AH / Bazaar side panel | the open container's title | a panel beside Hypixel's window; nothing is clicked | **off** |
| Flip alerts | the SkyPilot server's alert feed (your rules) | a toast + chat line; never buys | **off** |
| Item browser | the SkyPilot item registry | a list right of inventory windows; recipes/drops/uses screen | on |
| Chest value | the open container's item stacks (as rendered) | a value list left of the window; clicking a name outlines that slot | on |
| Guides and waypoints | the SkyPilot server (public SkyHanni place names) | a marker, a straight line and a HUD arrow to a fixed public place; no routes, no collectibles, the player walks | started by the player |
| Farm build guide | blocks the player can see near the chosen origin | coloured outlines of blocks still to place; nothing is placed | started by the player |
| Chat filters / timestamps / stacking / copy | incoming chat lines | hides the chosen groups from this client's chat, or adds a time, (x2), copy-on-click, a mention marker or a [Waypoint] button to the client's copy; the server message is never blocked or changed | **off** |
| Slot decorations | items shown in an open window (lore, custom_data) | rarity colour, small numbers, search dimming; slot handling untouched | **off** |
| Info HUD (coordinates, FPS, timers, bars, skill/powder/BPS/slayer/drop trackers) | own position, action bar, tab list widgets, sidebar, chat, blocks the player breaks | a second HUD box, titles, toasts and sounds | **off** |
| R / U lookup keys | the hovered item | opens SkyPilot's item page (the window stays open) | **off** |
| Garden visitor cost | the visitor menu's offer lore | a cost panel; accepting stays the player's click | **off** |
| Watchlist / reminders / calculator / own waypoints | SkyPilot prices, the player's input | toasts, chat lines, a static marker at a saved spot | **off** |
| Fishing / dungeon / mining trackers and alerts | chat lines, the sidebar, tab list widgets, the player's own fishing hook | HUD lines, titles and sounds; nothing about other players or hidden entities | **off** |
| Auction deal / valuable item outlines | listings and items in the open window | coloured outlines; buying stays the player's click | **off** |
| Ctrl+C item ID, to-do, stopwatch, lookup, chat search | the hovered item, the player's input, the chat lines already received | clipboard, chat lines, HUD lines | **off** |
| Write in Japanese (`/skypilot en`) | the text the player typed | JA→EN translation put into the chat box; the player sends it | **off** (consent) |
| Translation provider (settings) | - | sends the chosen provider and, if entered, the player's own DeepL/Anthropic key to the SkyPilot server only (stored encrypted, shown back as `…last4`) | server's provider |
| Commands `/skypilot …` | - | client-side chat output, settings | - |

Hypixel has no list of explicitly allowed mods; its disallowed list (Fr0z3n, 2021-03-26:
showing invisible entities, changing player movement, cancelling vanilla actions, automatically
interacting with inventories or menus) is respected by every feature, and every optional feature
is off until the player turns it on (Settings → Features).

The mod sends no packets to the game server, simulates no input, never opens or clicks
container GUIs, does not inspect hidden blocks or world data beyond what is rendered, and
contacts only the configured SkyPilot server. It never handles Microsoft/Minecraft passwords or
tokens; linking uses the device-authorization flow and a revocable SkyPilot token.

## Hypixel API usage

- Player data only on explicit user action; no background polling of players.
- One server-side API key, never exposed; no key rotation.
- Responses cached (CDN-aligned TTLs), `RateLimit-Limit/Remaining/Reset` honoured, a 429
  blocks further keyed requests until the reset instead of retrying, retries with jitter only
  for 5xx/network errors, circuit breaker on repeated failures, request metrics at `/api/status`.
- When Hypixel is unavailable the last valid snapshot is served with an explicit age banner.

## Market features

Flips, bazaar flips and alerts are information only. Fair value never relies on the lowest
BIN alone; suspected manipulation is marked **DO NOT FLIP** and never triggers alerts. Profit is
always shown after fees, with probability of sale, expected time and confidence - never as a
guarantee.
