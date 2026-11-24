# Personalized guides, money planning and media controls

## Entry points

- Web: `/guides` links armour, weapons, pets, dungeon, slayer, Kuudra, skill, accessory,
  minion and money guides. `/combat/weapons` provides role-specific loadouts.
- Launcher Home: equipment/pet guides and an API setup walkthrough.
- Minecraft: `/skypilot guides`, `/skypilot apihelp`, `/skypilot media`; the J menu has
  Web and Media buttons. Web pages use the configured SkyPilot server.

## Today's guide

`/daily` opens on "今日のガイド" (also `/api/advisor/report/today` and the mod's "Today" tab).
It combines what is true right now and ranks it for your profile:

| Section | Source |
|---|---|
| Alerts | Hypixel's status page (maintenance in progress / within 24 h, SkyBlock incidents), the server key's state |
| Focus | your goals, your weakest completion parts, the best action from every other advisor |
| Events | the SkyBlock calendar (event start / end in your local time) |
| Mayor | the elected mayor's and minister's perks, matched to activities by keywords at word starts |
| Money | the snowball flip basket for your purse (expected value per cycle, calibrated on the record) |
| Completion | the overall % and the three lowest parts |
| News | the latest SkyBlock news posts |

A section whose live source does not answer is listed under "unknown" with the reason; nothing
is guessed. The other advisors' actions are not counted twice in the roadmap.

## Completion

`/api/me/completion` (web dashboard "達成率", mod NOW tab, optional HUD lines) gives each part
as done / total and %. Maxima come only from data: skill and dungeon caps from the level tables,
collection tiers and minion tiers from NEU-REPO, museum items without the "special" ones, fairy
souls from NEU's constants. Parts without a known maximum (or not in the API answer, such as
disabled API settings) are listed as unknown and are not part of the overall %.

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

## Snowball flipping

Market → Live Flips → "雪だるまプラン" (`/market/flips?tab=snowball`, `/api/market/snowball`).

- **Calibration**: every recorded flip candidate older than 24 h (up to 14 days) is checked against
  comparable sales at or above its unit target within 24 h. Per item, the observed hit rate is
  blended with the model's P(sale) (4 prior trials), and the whole model is scaled by
  observed / expected hits once 20 hits are expected. Confidence and the sell-time range follow
  the record (p25–p75 of the sell minutes with at least 3 hits). Refreshed every 15 minutes.
  Each flip shows its record as hits / trials.
- **Basket**: flips that are not suspicious, risk at most High, confidence ≥ 0.45 and positive
  expected value (P × net profit − (1 − P) × (listing and duration fees + a 3% markdown to
  relist)). One per item, at most 50% of the coins in one flip, 10% kept for listing fees.
  Ordered by expected value per coin-hour (sell time + 0.5 h to buy and list).
- **Projection**: cycles per day = hours played / cycle length + one overnight cycle (at most
  24 h / cycle length). Each day reinvests the profit; the coins put to work per day are capped
  at 15% of what the market offers, and coins beyond the basket earn the smaller rate. The
  capital ladder shows from which amount more item types become flippable.
- The money ranking (`/money`) uses the same basket for AH flipping: coins per hour you play =
  expected profit per cycle × cycles per day / 2 h, range 50–100%.

This is a model of past market behaviour, not a guarantee. Prices move, other players buy the
same listings, and every purchase, listing and claim is done by you in game.

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
