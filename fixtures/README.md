# Test fixtures

Real-world data used by the Rust and Java test suites. Nothing here is fabricated:
every file is a snapshot (or subset) of a real upstream response, anonymised.

| File | Source | Notes |
|---|---|---|
| `hypixel/auctions_page0.json` | `GET https://api.hypixel.net/v2/skyblock/auctions` (2026-10-05) | 158 auctions selected to cover pets, books, gemstones, stars, recombobulators, runes and repeated BIN items. UUIDs replaced by `sha256("skypilot-fixture:"+uuid)[..32]`. `item_bytes` untouched (hybrid `components` + legacy `ExtraAttributes` NBT). |
| `hypixel/auctions_ended.json` | `GET /v2/skyblock/auctions_ended` (2026-10-05) | Full response, UUIDs anonymised. |
| `hypixel/bazaar.json` | `GET /v2/skyblock/bazaar` (2026-10-05) | 322 products relevant to the tests (crops, gems, slayer drops, enchantments, ...). |
| `hypixel/resources_items.json.gz` | `GET /v2/resources/skyblock/items` (SkyBlock v0.27.1) | Complete item list, gzipped. |
| `hypixel/resources_skills.json`, `resources_collections.json`, `resources_election.json`, `firesales.json`, `news.json` | Hypixel resources endpoints | Unmodified. |
| `hypixel/profiles_sample.json`, `museum_sample.json` | `tests/test_profile.json` / `test_museum.json` of [SkyHelper-Networth-Go](https://github.com/SkyCryptWebsite/SkyHelper-Networth-Go) (MIT, see `LICENSE-skyhelper-networth-go.md`) | Wrapped as a `/v2/skyblock/profiles` response. All UUIDs hashed, player names replaced (`SampleUser`), inventory NBT re-encoded after anonymisation. |
| `neu/` | [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) (MIT, see `neu/LICENSE`) | Subset: constants + 320 item files (slayer drop tables, forge/kat recipes, crafting chains for Hyperion, crops, drills, minions). |
| `skyhanni/island_graphs/` | [SkyHanni-REPO](https://github.com/hannibal2/SkyHanni-REPO) `constants/island_graphs` (MIT, see `skyhanni/LICENSE`) | 6 islands, named nodes only (as `sync_skyhanni` stores them). |

Regenerate with the scripts described in `docs/data-sources.md`.
