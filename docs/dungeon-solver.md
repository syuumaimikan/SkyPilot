# Dungeon Solver (SkyPilot 0.1.7)

Minecraft 26.2 / Fabric 0.19.5. Open `/skypilot dungeon` for the master switch, detailed settings and HUD positioning. `/skypilot dungeon on` and `off` also work. The guide is enabled by default. Players perform every click, movement and shot themselves.

SkyPilot includes the **unmodified Skyblocker 6.10.4+26.2** and Fabric Language Kotlin jars as nested Fabric modules; no additional mod installation is required. Room recognition and the following native overlays are provided by Skyblocker:

| Puzzle/device | Guidance |
| --- | --- |
| Blaze | Correct health order, next target and connecting lines |
| Boulder | Push route and next button |
| Creeper Beams | Paired endpoints and connecting beams |
| Ice Fill | Floor route |
| Ice Path / Silverfish | Push route |
| Tic Tac Toe | Best next cell |
| Three Weirdos | Correct chest after NPC dialogue |
| Quiz | Correct answer button after the question/options arrive |
| Teleport Maze | Used pads and final pad when it can be determined |
| Water Board | Ordered lever sequence, flow previews and server-tick countdowns |
| Simon Says | Recorded sequence and next button |
| Lights On | Unpowered levers |
| Arrow Align | Remaining rotations per frame |
| Target Practice | Active target and previously hit targets |

SkyPilot adds cursor/world guide lines, a movable next-operation HUD, and guides for all six terminal types: numbers, matching color, starting letter, same-color cycles, red panes and Melody. Same-color guidance minimizes total clicks using both mouse buttons. Plans are recomputed from visible server stacks, never from simulated successful clicks. The terminal view suppresses SkyPilot's unrelated inventory panels. Skyblocker's incorrect-click blocking and automatic dungeon party announcements are disabled by this integration.

Water Board shows seconds based on the backend's **server progression**, including when to start water. Melody shows “click now” only when the displayed marker matches the target column; after sufficient observations it estimates the next alignment time. Its estimate is affected by network latency and is withdrawn when updates are stale. Neither an unknown future teleport destination nor exact future packet-arrival times can be determined before the server supplies them. Unknown states are shown as waiting rather than invented solutions.

Validation includes terminal planning unit tests and a real Minecraft client game test with both embedded modules, adapter compatibility checks and a synthetic numbered terminal. Live Hypixel runs, every room variant and network conditions have not been exhaustively verified.

## Third-party notices and corresponding source

Skyblocker is separately licensed under LGPL-3.0-or-later (as declared by its packaged `fabric.mod.json`); its own license and notices are retained inside the nested jar. SkyPilot's original code keeps its existing license. The integration uses reflection and does not copy Skyblocker's solver implementation.

- Exact upstream source: https://github.com/SkyblockerMod/Skyblocker/tree/v6.10.4%2B26.2 (commit `e364eb8`); source archive accompanies the Windows installer.
- License: https://github.com/SkyblockerMod/Skyblocker/blob/v6.10.4%2B26.2/LICENSE
- Fabric Language Kotlin (Apache-2.0): https://github.com/FabricMC/fabric-language-kotlin
- Both distribution files are pinned by SHA-512 and size in `apps/launcher/src/main/resources/companion-mods.json`; build-time downloads verify both before packaging. The game does not download executable code.

To replace the solver, edit or replace its nested jar in `META-INF/jars` in a local copy of the SkyPilot jar, or use a compatible external Fabric module. Fabric resolves embedded modules by mod ID/version. Rebuild the upstream source with its Gradle wrapper to make changes; the SkyPilot adapter requires compatibility with the pinned fields, and reports an unsupported adapter while retaining native overlays if the contract changes.
