# Mochy Music: architecture notes

Written 2026-11-18 against `main` at `c681fd11` (upstream commit, before this fork's first change).
The point is to be able to work on the Apple sync without re-reading 316k lines of Kotlin, and to
give a rebase a map when a file moves.

| Page | What it answers |
|---|---|
| [00-overview](00-overview.md) | Stack, gradle modules, package map, entry points, build and run |
| [10-data-model](10-data-model.md) | Room schema v43, DAOs, preferences, where state actually lives |
| [20-playback](20-playback.md) | Media3 service, queues, stream resolution, DRM, audio lab, downloads |
| [30-library-and-sync](30-library-and-sync.md) | Library screens, YouTube sync, import/resync, where Apple outbound attaches |
| [40-modules](40-modules.md) | Module API, registry, built-ins, JS/web/zip modules, providers |
| [50-ui](50-ui.md) | Navigation, screens, settings tree, player styles, widgets, TV, car |
| [60-integrations](60-integrations.md) | Scrobbling, Discord, lyrics, canvas, recognition, device linking, updater |
| [70-feature-inventory](70-feature-inventory.md) | Every user-facing feature with the file that implements it |
| [_scan.md](_scan.md) | Generated tree scan: packages, declarations, manifest, schema, manifest components |

`scan.py` regenerates `_scan.md`. Names and locations only; the pages above interpret them.

## Reading order for the sync work

1. `30-library-and-sync.md` for the existing two-way machinery (YouTube), because Apple's will copy it.
2. `10-data-model.md` for why new tables go in a sidecar database rather than `mochy.db`.
3. `20-playback.md` for how a song becomes a stream, since Apple playback is already one source among several.
4. `40-modules.md` for what a module cannot do, which is why this is a fork and not a module.
