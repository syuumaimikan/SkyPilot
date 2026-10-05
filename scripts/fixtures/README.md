# Fixture and data generators

These scripts produced the committed files in `fixtures/` and `data/farms/`. They are kept so
the fixtures can be refreshed from real upstream data; nothing in `fixtures/` is hand-written.

| Script | Inputs (in `$SKYPILOT_FIXTURE_INPUT`, default `./input`) | Output |
|---|---|---|
| `mkfix.py` | `live/skyblock_auctions.json`, `live/skyblock_auctions_ended.json`, `live/skyblock_bazaar.json`, other `live/*.json` resource responses saved from `https://api.hypixel.net/v2/...` | `fixtures/hypixel/*` (UUIDs anonymised) |
| `mkneu.py` | `neu/` = extracted [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) (MIT), `live/resources_skyblock_items.json` | `fixtures/neu/*`, `fixtures/hypixel/resources_items.json.gz` |
| `mkprofile.py` | `snw/` = checkout of [SkyHelper-Networth-Go](https://github.com/SkyCryptWebsite/SkyHelper-Networth-Go) (MIT) | `fixtures/hypixel/profiles_sample.json`, `museum_sample.json` (anonymised) |
| `mkfarms.py` | none | `data/farms/*.json` reference farm layouts |

```bash
SKYPILOT_FIXTURE_INPUT=/path/to/input python scripts/fixtures/mkfix.py
```

After regenerating, run `cargo test --workspace`: the registry test validates every curated
item id against the fixture item list.
