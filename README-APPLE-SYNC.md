# Mocha Apple Sync — start here

This repo is the fork. The Apple Music work lives on the `apple-sync` branch.

**Read in this order:**

1. `apple-sync/STATE.md` — what is proven, what is left, and the traps already hit. Start here every session.
2. `apple-sync/PLAN.md` — the original design and why each decision was made.
3. `apple-sync/architecture/` — a map of upstream Mocha: data model, playback, modules, UI.
4. `apple-sync/ENVIRONMENT.md` — JDK/SDK paths, build command, emulator notes.

**The short version:**

- Apple is the library of record; Mocha imports it as `AM_<catalogueId>` songs.
- Writes go through an embedded music.apple.com page (tokens) and the documented REST endpoints (data), behind `AppleGuard`, which refuses anything pre-existing or unowned.
- The epoch in `apple_sync.db` (1516 rows) is the promise that nothing of yours changes by accident.
- Remotes: `origin` = your fork (`aethux/Mochy-Music`), `upstream` = `realidkroo/Mochy-Music`.

**Before any commit:** `./scripts/seams.sh` must exit 0.
