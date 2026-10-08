# Personalized guides, money planning and media controls

## Entry points

- Web: `/guides` links armour, weapons, pets, dungeon, slayer, Kuudra, skill, accessory,
  minion and money guides. `/combat/weapons` provides role-specific loadouts.
- Launcher Home: equipment/pet guides and an API setup walkthrough.
- Minecraft: `/skypilot guides`, `/skypilot apihelp`, `/skypilot media`; the J menu has
  Web and Media buttons. Web pages use the configured SkyPilot server.

## Profile and freshness

Owned slots and usable weapons come from the analysed profile. Reanalyse after equipment
changes. Definitions and use requirements come from the server's periodically refreshed
Hypixel resources and NEU data; quotes use the market collector. Existing freshness
indicators describe snapshot ages. These are snapshot-based recommendations, not a live
read of the item currently in the player's hand.

The armour, equipment and weapon optimizer compares purpose-specific lore scores with
candidate base stats, preserves slot exclusivity and budget, and excludes purchases whose
requirements are unknown. It does not select purchases when inventory data is unavailable.
It includes mining drills/pickaxes and damage weights for melee/archer weapons. Owned setup
tables list the best usable owned piece per slot. Abilities, set bonuses, enchantments of
candidate purchases and full damage simulation are not modelled.

Pet guides cover melee, archer, mage, survival, mining, farming, fishing and foraging,
showing owned rarity/level, active status and budget/quotes. Shortlists are explicit
SkyPilot activity heuristics, not authoritative best-in-slot claims. Quotes are not
level-matched. Check ability descriptions in-game before purchasing.

Dungeon entry rules remain separate from suitability. The equipment component checks four
usable equipped armour pieces, usable owned weapons and normal stars (3-star guidance for
F4/F5, 5 for F6/F7 and Master Mode). Missing gear or readiness configuration caps the estimate
below the recommendation thresholds. Set effects, pet abilities, party composition and
player skill are not simulated. Class fit uses weapon lore instead of market value.

## API setup

Missing inventory, banking, collection or skill data shows a playable, four-step illustrated
walkthrough. It is not recorded game footage. Missing data can also be a new profile or
an upstream issue; it is not conclusive evidence that the user disabled a setting.
Server `HYPIXEL_API_KEY` configuration is explained separately from in-game API switches.

Menu path reference (April 2026):
<https://hypixel.net/threads/account-wiped-on-skyblock.6085198/>.
API format and authentication reference: <https://api.hypixel.net/>.

## Wealth forecasts and money tools

Money and Networth show projected liquid coins, the no-interest comparison, total interest,
daily values and a target date. Inputs include bank/purse, tier, museum milestone, income,
expenses, horizon, next payout and reinvestment. Unknown balances remain visibly unknown;
manual zero is a scenario. Tier and museum milestone are manual because the current
analysis model does not expose them. Co-op withdrawals, personal bank interest and item
price changes are excluded. Ironman/Bingo/Stranded do not receive market-trading eligibility
or market-sale hourly estimates.

Interest uses progressive brackets, tier caps, the museum multiplier and a 31-hour cycle,
assuming regular logins to collect every payout. Deposits cannot exceed bank capacity;
overflow stays in the purse. Expenses cannot reduce balances below zero. Money tools add
capital reserves, an eligible-method target plan, a manual tax/fee profit calculator,
ROI, break-even selling price and hourly equivalent.

Banking rates were checked against the archived Banking result on 2026-10-08:
<https://wiki.hypixel.net/Personal_Bank>.
The official wiki is closed, so this is an archived reference, not a claim of current
official endorsement. Confirm rates at the Banker before relying on the scenario.
Closure notice: <https://hypixel.net/threads/end-of-the-official-hypixel-wiki-july-2026.6112020/>.
Community reference: <https://hypixelskyblock.minecraft.wiki/w/Banking>.

## Windows media

The bundled WinRT bridge supports play, pause, toggle, previous and next. MediaScreen shows
the title/artist/status and disables controls the player does not support. Rejected
commands are displayed. No media history or account data leaves the PC. Native operations
require Windows and a player exposing a system media session.

## Validation

- `cargo test -p skypilot-reco`
- `cargo check -p skypilot-api`
- `node scripts/test-wealth.mjs`
- `pnpm --filter @skypilot/web typecheck`, `lint`, `build`
- `apps/mod/gradlew :launcher:test :platform:mc26_2:compileJava`

Regression tests cover unknown inventory, empty equipped gear, role-specific candidates,
pet coverage and restricted-mode money methods. Forecast tests cover progressive caps,
museum bonuses, payout timing, compounding, capacity overflow, income and depletion.
Browser checks use manual example values, not the user's live account. Native media
bridge compilation is verified; actual playback depends on the user's media app.
