# Garden, dungeon and HUD expansion — 0.1.5

## Garden

Enable the visitor panel in Features → Farming. It shows required-item cost, the value of sellable rewards, estimated net, copper and coins/copper. XP and copper are not assigned a fictional sale price. Unknown reward prices make net unknown. Estimates use SkyPilot's price service; taxes, order filling time and liquidity are not included.

Enable `garden.pest_map` to show a north-up 5×5 Garden grid. Red cells are the pest plots reported in the tab list, cyan is your current plot. World labels and guide lines point to those plot centres. It identifies the plot, not an unseen pest's exact entity position. The Barn is `B`.

Item tooltip prices remain enabled by the existing Item value switch. Price lines are grouped below the original lore; items without a SkyBlock ID can use an exact display-name price match. Unknown prices are never treated as zero.

## Readability and layout

Information HUD defaults to a compact maximum of 18 lines; the item browser displays at most 12 rows in compact mode. SkyPilot's main and info HUDs hide behind open inventory windows by default. Both behaviours can be switched in `/skypilot hud edit`.

HUD editing previews actual vanilla HUD data, plus Garden and map previews. Cycle the **選択** button to choose the scoreboard, action bar, boss bar, hotbar/health, status effects, media, Garden map or dungeon map. Drag its outlined area to move it and scroll over it to resize. SkyPilot's existing HUD boxes also remain draggable. Done saves positions; Reset restores them. Empty vanilla elements retain a labelled selection outline. Layout controls can overlap HUD elements at small GUI resolutions; move the selected element into view.

## Dungeons

Each feature is individually enabled in Features → Dungeons.

- **Minimap** shows the filled map already held in your inventory, with the server's markers. It waits when no map is present; it does not invent unexplored rooms. The editor can preview any received held map.
- **Blaze order guide** reads nearby visible Blaze HP labels and displays numbers and connecting lines. Choose ascending or descending in the HUD editor to match the room. The order is a user setting, not automatic room detection.
- **Tic-tac-toe guide**: assign the **三目並べの盤面中央を記録** key in SkyPilot key settings. Aim at the wall block in the centre of the 3×3 board and press it once per room. The helper reads revealed item-frame maps and outlines the best next O move when the board indicates O's turn.
- **Creeper beam guide** looks for a visible Creeper above a sea-lantern base and nearby loaded sea-lantern/prismarine blocks. It draws candidate opposite block pairs that cross the Creeper. This is geometric detection, not a complete room-template solver.
- **Maze route** in `/skypilot puzzle`: enter a maze using `S`, `G`, `.`, `#` and `/`; stand at S and choose **現在地から迷路ガイド**. One entered cell corresponds to one block, north is ↑. Lines show the calculated route in the world. The maze is manually entered, not automatically scanned.

## Windows media

Enable **Windowsの再生中メディア** in Features → Tools. Assign play/pause, previous and next in `/skypilot keys`; they start unbound. The HUD prefers a playing Windows media session and otherwise shows a valid paused session's title, artist, cover and playback state. Controls target that session; the media app determines which commands it supports. Apps that do not expose a Windows media session cannot be displayed.

The mod includes a self-contained Windows x64 WinRT bridge compiled from `apps/media-bridge`. It is extracted under the game directory's `.skypilot-tools/<SHA-256>/` and launched only when media support is enabled. Polling occurs off the game thread every two seconds. Metadata and covers stay local; disabling the feature or quitting stops the helper. Windows builds require the .NET 10 SDK; users do not need to install .NET separately. Other operating systems show Windows-only status.

## Verification

Core tests check visitor reward classification, pest plot parsing, grid mapping and existing puzzle algorithms. Minecraft client tests exercise the HUD mixins, visitor profit panel, map preview and media rendering. Windows media metadata and cover acquisition were also checked against a real playing session. Live Hypixel puzzle rooms and playback-control key actions have not been automatically tested.
