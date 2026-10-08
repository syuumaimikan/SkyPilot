# Night vision, pest tracking, fonts and target money tools

## In Minecraft

- `/skypilot nightvision` toggles night vision; `on` and `off` set it explicitly.
  Visual Studio also has a switch; the Night Vision action in key settings can be bound.
  This changes the local lightmap, not potion effects or server state. Vanilla darkness
  remains active. The existing rendering implementation is reused.
- `/skypilot pests` toggles **Follow visible pests**, also available under Farming in
  feature settings. Green boxes, names and distance follow the current entity coordinates;
  the HUD points toward the nearest visible detected pest and shows XYZ. The existing
  red plot indicators remain separate. Recognition uses exact public pest health labels
  (including Field Mouse and Lunar Moth), and can associate an invisible label with a
  nearby visible body. It never draws hidden bodies or retains despawned targets. Walk
  toward an infested plot if nothing is detected. Unknown names/models need an update.
- `/skypilot font` opens the game font screen; Visual Studio also links it. Enter a local
  `.ttf` path, or select Segoe UI / Arial on Windows, set 6–12 px, then Apply. A local
  resource pack changes default menu, chat and HUD text using Minecraft's native TTF
  renderer with 2× oversampling. This is not the Modern UI rendering engine. Other
  selected packs remain selected; the restore button disables only SkyPilot's font pack.
  Vanilla references provide missing Japanese glyphs and private-use game icons.
  TTFs are checked before replacing the generated pack; TTC/OTF are unsupported.
  The font is copied to `resourcepacks/skypilot-local-font.zip`; no upload occurs.
- `/skypilot moneytools` opens `/money` on the configured SkyPilot Web server.

Pest-name reference checked 2026-10-08:
https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/garden/pests/PestType.kt
No SkyHanni implementation is copied. Font provider fields and version metadata are
verified against the local Minecraft 26.2 classes and resources.

## Money page additions

1. **Deadline calculator**: current liquid coins, target, days and daily play time,
   passive income and expenses give the net active hourly rate needed.
2. **Fastest eligible candidates**: ranks up to five active methods by conservative
   work hours to recover their setup spending and reach the target. Requires known
   setup/capital costs, sufficient entered capital and met profile requirements.
   Low-risk-only filtering is enabled initially. Rates, reasons and confidence are shown.
   Passive jobs with waiting cycles do not become misleading active hourly candidates.
3. **Upgrade payback**: cost and measured before/after net rates give recovery hours/days.
   No positive improvement means no recoverable investment.
4. **Measured session calculator**: beginning/end combined liquid balances, external
   inflows/outflows and duration give net profit and hourly income, including losses.
5. **Income journal**: latest 100 sessions per profile, total profit and time-weighted
   hourly income. Save locally, undo the latest record, or use measured income in the
   payback calculator. Browser storage may be unavailable; the calculator still works.

Keep purse and bank accounting consistent. Internal transfers are not income; gifts,
unrelated sales and external withdrawals must be adjusted. Forecasts cannot guarantee
a fast target: execution, setup time, market changes and successful sales still matter.

## Validation

- `node scripts/test-money-plan.mjs`: deadlines, passive income, expenses, zero time,
  payback, external transfers and negative income.
- `pnpm --filter @skypilot/web typecheck`, `lint`, `build`.
- `apps/mod/gradlew :core:test`: pest names, invalid font preservation and generated
  pack contents/fallbacks.
- `apps/mod/gradlew :platform:mc26_2:runClientGameTest`: actual font resource loading,
  changed/restored text metrics, night vision on/off, visible pest recognition,
  moving entity references, hidden bodies, traps and despawned targets.

Game tests use a local test world, not live Hypixel pest models. Custom font IDs used
by another mod are outside the default-font replacement.
