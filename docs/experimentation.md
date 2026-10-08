# Experimentation Table helpers (0.1.3)

Open SkyPilot → Features → Inventory and enable the following switches (search `experiments` or `神経衰弱`). Each is off initially.

- **Superpairs memory**: renders a copy of a previously revealed reward on its closed card.
- **Matching pairs**: green outlines identify equal observed rewards; hovering a known card highlights its matching partner in yellow.
- **Remembered reward names**: shows the hovered remembered reward name above the board.
- **Ultrasequencer numbers**: records numbered items during “Remember the pattern!” and displays their numbers during “Timer:”.
- **Chronomatron sequence**: records enchanted terracotta flashes during observation and displays their first position during input. A `+` means the cell occurs again; hover it to read every recorded position.

The helper reads only stacks already sent to the open container. It does not know unopened cards, send clicks or block player input. Memories reset when a new window opens; sequence and number memories reset at the start of a new observation phase. Unknown titles/layouts are ignored. Supports the English server titles and six-row experiment layouts; server UI changes may require an update.

The existing Puzzle Lab (`/skypilot puzzle`) also provides manual-input tic-tac-toe, maze and Sudoku helpers.

Verification: unit tests cover unknown cards, unequal rewards, matched partners, repeated flashes, round resets and independent window memories. Minecraft client game tests cover revealed/hidden Superpairs rewards, Ultrasequencer numbers and Chronomatron glints, with screenshots for all three games. These are simulated server menus; a live Hypixel experiment has not been tested automatically.
